package com.example.minedtv.data.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import com.example.minedtv.R
import com.example.minedtv.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import com.example.minedtv.data.api.MinedTvApi

/**
 * Real sharing without any platform developer setup: downloads the final video, copies the
 * caption to the clipboard, and opens the TikTok / Instagram app with the video loaded.
 * The user taps Post in that app (share intents can't prefill captions or post silently).
 */
class IntentPlatformPublisher(private val context: Context) : PlatformPublisher {

    private val packages = mapOf(
        SharePlatform.TikTok to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        SharePlatform.InstagramReels to listOf("com.instagram.android")
    )

    override fun publish(request: PublishRequest): Flow<PublishStatus> = flow {
        val platform = request.platform
        val pkg = packages[platform].orEmpty().firstOrNull { isInstalled(it) }
        if (pkg == null) {
            emit(PublishStatus.Failed("${platform.displayName} isn't installed on this phone"))
            return@flow
        }

        val file = File(context.cacheDir, "share").apply { mkdirs() }.resolve("minedtv_${System.currentTimeMillis()}.mp4")
        val exportId = when {
            !MinedTvApi.isConfigured || request.localPath != null -> null
            request.remoteClipId != null -> request.remoteClipId
            // A moment from search: save that range as a clip first, then export it.
            request.sessionId != null && request.endMs > request.startMs ->
                MinedTvApi.createClip(request.sessionId, request.startMs, request.endMs, request.title).remoteId
            else -> null
        }
        if (request.localPath != null) {
            // Recorded on this phone: share the file itself.
            java.io.File(request.localPath).copyTo(file, overwrite = true)
            emit(PublishStatus.Uploading(1f))
        } else if (exportId != null) {
            // Real clip: the server renders an MP4 (about a minute the first time), then we download it.
            emit(PublishStatus.Uploading(0.05f))
            val url = MinedTvApi.exportMp4(exportId) { polls ->
                emit(PublishStatus.Uploading((0.1f + polls * 0.03f).coerceAtMost(0.45f)))
            }
            download(url, file) { emit(PublishStatus.Uploading(0.5f + it * 0.5f)) }
        } else if (request.videoUrl.startsWith("https://") && !request.videoUrl.contains("example.com")) {
            download(request.videoUrl, file) { emit(PublishStatus.Uploading(it)) }
        } else {
            // No backend yet: use the bundled sample so the hand-off can be demoed end to end.
            context.resources.openRawResource(R.raw.sample_clip).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            for (p in listOf(0.35f, 0.7f, 1f)) {
                delay(250)
                emit(PublishStatus.Uploading(p))
            }
        }
        Logger.d("Share video ready: ${file.name} (${file.length() / 1024} KB) -> $pkg")

        val caption = (listOf(request.caption) + request.hashtags).joinToString(" ").trim()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = shareIntents(platform, pkg, uri, caption)
            .firstOrNull { it.resolveActivity(context.packageManager) != null }
        if (intent == null) {
            emit(PublishStatus.Failed("${platform.displayName} can't receive videos on this phone"))
            return@flow
        }
        Logger.d("Share intent: action=${intent.action} target=${intent.component?.className ?: pkg}")

        val opened = withContext(Dispatchers.Main) {
            if (caption.isNotEmpty()) {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("MinedIt caption", caption))
            }
            try {
                context.startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                Logger.e("No activity for $pkg share", e)
                false
            }
        }
        if (opened) {
            Logger.i("Opened ${platform.displayName} ($pkg) with video")
            emit(PublishStatus.OpenedInApp(platform.displayName, captionCopied = caption.isNotEmpty()))
        } else {
            emit(PublishStatus.Failed("Couldn't open ${platform.displayName}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Candidate intents, most specific first. A plain ACTION_SEND to Instagram opens the
     * Direct Messages sheet, so for Reels we target its Reels share handler (what the system
     * share sheet's "Reels" entry uses), then Instagram's official ADD_TO_REEL action.
     */
    private fun shareIntents(platform: SharePlatform, pkg: String, uri: android.net.Uri, caption: String): List<Intent> {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        fun send() = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(flags)
        }
        return when (platform) {
            SharePlatform.InstagramReels -> listOf(
                send().setClassName(pkg, "com.instagram.share.handleractivity.ReelShareHandlerActivity"),
                Intent("com.instagram.share.ADD_TO_REEL").apply {
                    setDataAndType(uri, "video/mp4")
                    setPackage(pkg)
                    addFlags(flags)
                },
                send().setPackage(pkg)
            )
            SharePlatform.TikTok -> listOf(send().setPackage(pkg))
            SharePlatform.MinedIt -> emptyList() // posted through the MinedIt API, never a share intent
        }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private suspend fun download(url: String, target: File, onProgress: suspend (Float) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total != null) onProgress(done.toFloat() / total)
                    }
                }
            }
            onProgress(1f)
        } finally {
            conn.disconnect()
        }
    }
}
