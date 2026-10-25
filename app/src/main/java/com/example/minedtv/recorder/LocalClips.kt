package com.example.minedtv.recorder

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.minedtv.data.Clip
import com.example.minedtv.data.SampleData
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.data.share.formatDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Clips recorded on this phone: MP4 + a .json sidecar (game, duration) + a .jpg thumbnail. */
object LocalClips {
    private val _clips = MutableStateFlow<List<Clip>>(emptyList())
    val clips: StateFlow<List<Clip>> = _clips.asStateFlow()

    fun dir(context: Context) = File(context.filesDir, "clips").apply { mkdirs() }

    fun refresh(context: Context) {
        _clips.value = dir(context).listFiles { f -> f.extension == "mp4" }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map(::toClip)
    }

    fun saveMeta(mp4: File, game: String, durationMs: Long) {
        File(mp4.path.removeSuffix(".mp4") + ".json")
            .writeText(JSONObject().put("game", game).put("durationMs", durationMs).toString())
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(mp4.path)
                val frame = retriever.getFrameAtTime(durationMs * 500, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return
                val scale = 480f / maxOf(frame.width, frame.height)
                val small = Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
                File(mp4.path.removeSuffix(".mp4") + ".jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            } finally {
                retriever.release()
            }
        }
    }

    private fun sidecar(mp4Path: String) = File(mp4Path.removeSuffix(".mp4") + ".json")

    /** Backend session this recording was uploaded as, if any. */
    fun uploadedSession(mp4Path: String): String? =
        runCatching { JSONObject(sidecar(mp4Path).readText()).optString("sessionId").ifEmpty { null } }.getOrNull()

    fun markUploaded(mp4Path: String, sessionId: String) {
        val file = sidecar(mp4Path)
        val meta = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
        file.writeText(meta.put("sessionId", sessionId).toString())
    }

    private fun toClip(file: File): Clip {
        val base = file.path.removeSuffix(".mp4")
        val meta = runCatching { JSONObject(File("$base.json").readText()) }.getOrNull()
        val game = meta?.optString("game")?.ifEmpty { null } ?: "Gameplay"
        val seconds = ((meta?.optLong("durationMs") ?: 0L) / 1000).toInt()
        val thumb = File("$base.jpg")
        return Clip(
            id = file.name.hashCode() and 0x7fffffff,
            title = "$game clip · ${SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(file.lastModified()))}",
            game = game,
            user = SampleData.currentUser,
            views = 0,
            likes = 0,
            comments = 0,
            duration = formatDuration(seconds),
            age = "now",
            colors = MinedTvApi.colorsFor(game),
            playlistUrl = Uri.fromFile(file).toString(),
            thumbnailUrl = if (thumb.exists()) Uri.fromFile(thumb).toString() else null,
            localPath = file.path
        )
    }
}
