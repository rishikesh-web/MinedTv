package com.example.minedtv.data.share

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Real AI edit using what the backend already offers:
 *  1. Gemini creator pack (caption, hashtags, transcript, key moments) via /clips/{id}/creator-pack,
 *     or /creator/analyze for a recording that only exists on this phone.
 *  2. The edit plan is derived here: the best window around the key moments, captions from the
 *     dialogue (or key moments when there's no speech) plus a hook line.
 *  3. [AiVideoEditor] renders it on the phone (trim, 9:16 crop, burned-in captions).
 * Sample clips with no real video fall back to the simulated editor.
 */
class RemoteEnhanceRepository(
    private val context: Context,
    private val fallback: EnhanceRepository = FakeEnhanceRepository()
) : EnhanceRepository {
    private val jobs = ConcurrentHashMap<String, EnhanceRequest>()

    private fun EnhanceRequest.isReal() =
        MinedTvApi.isConfigured && (localPath != null || remoteClipId != null || (sessionId != null && endMs > startMs))

    override suspend fun submit(request: EnhanceRequest): String {
        if (!request.isReal()) return "fake:" + fallback.submit(request)
        val jobId = "ai_${System.currentTimeMillis()}"
        jobs[jobId] = request
        return jobId
    }

    override fun observe(jobId: String): Flow<EnhanceJobStatus> {
        if (jobId.startsWith("fake:")) return fallback.observe(jobId.removePrefix("fake:"))
        return channelFlow {
            val request = jobs[jobId] ?: run {
                send(EnhanceJobStatus.Failed("Unknown job $jobId"))
                return@channelFlow
            }
            val stages = request.options.stages()
            fun processing(stage: EnhanceStage, fraction: Float) =
                EnhanceJobStatus.Processing(stage, (stages.indexOf(stage).coerceAtLeast(0) + fraction.coerceIn(0f, 1f)) / stages.size)

            try {
                send(EnhanceJobStatus.Uploading(0.02f))
                val source: File
                val pack: JSONObject
                if (request.localPath != null) {
                    // Recorded on this phone: Gemini watches the uploaded file directly.
                    source = File(request.localPath)
                    var uploaded = false
                    pack = MinedTvApi.analyzeVideo(source) { p ->
                        if (!uploaded) trySend(EnhanceJobStatus.Uploading(p))
                        if (p >= 1f && !uploaded) {
                            uploaded = true
                            trySend(processing(EnhanceStage.AnalyzingTrends, 0.2f))
                        }
                    }
                } else {
                    val clipId = request.remoteClipId
                        ?: MinedTvApi.createClip(request.sessionId!!, request.startMs, request.endMs, request.title).remoteId!!
                    source = File(context.cacheDir, "share").apply { mkdirs() }.resolve("source_$clipId.mp4")
                    pack = coroutineScope {
                        // Gemini analysis and the MP4 export run on the server at the same time.
                        val packJob = async { MinedTvApi.creatorPack(clipId) }
                        if (!source.exists() || source.length() == 0L) {
                            val url = MinedTvApi.exportMp4(clipId) { polls -> send(EnhanceJobStatus.Uploading((0.05f + polls * 0.04f).coerceAtMost(0.5f))) }
                            MinedTvApi.download(url, source) { send(EnhanceJobStatus.Uploading(0.5f + it * 0.5f)) }
                        }
                        var tick = 0
                        while (!packJob.isCompleted) {
                            send(processing(EnhanceStage.AnalyzingTrends, (tick++ / 24f).coerceAtMost(0.95f)))
                            kotlinx.coroutines.delay(1_000)
                        }
                        packJob.await()
                    }
                }
                send(processing(EnhanceStage.DetectingHighlights, 0.5f))

                val plan = plan(request, pack, durationMs(source))
                Logger.i("AI plan: ${plan.edit.startMs}-${plan.edit.endMs}ms, ${plan.edit.captions.size} captions")
                val renderStages = stages.dropWhile { it != EnhanceStage.DetectingHighlights }.drop(1)
                val out = AiVideoEditor.render(context, source, plan.edit) { p ->
                    val i = (p * renderStages.size).toInt().coerceAtMost(renderStages.size - 1)
                    trySend(processing(renderStages[i], p * renderStages.size - i))
                }
                send(EnhanceJobStatus.Completed(plan.result(jobId, out)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("AI enhance failed", e)
                send(EnhanceJobStatus.Failed(e.message ?: "AI edit failed"))
            }
        }
    }

    private class Plan(val edit: AiVideoEditor.Edit, val result: (String, File) -> EnhanceResult)

    private fun plan(request: EnhanceRequest, pack: JSONObject, sourceMs: Long): Plan {
        val options = request.options
        val platform = request.platform
        val cues = pack.optJSONArray("transcript").cues("text")
        val moments = pack.optJSONArray("key_moments").cues("label")

        // Trim: the window (starting just before a key moment) that holds the most dialogue and moments.
        val targetMs = options.targetSeconds * 1000L
        val trim = options.autoTrim && sourceMs > targetMs + 1000
        var anchor: Pair<Long, String>? = null
        val start = if (!trim) 0L else {
            val events = (cues + moments).map { it.first }
            val candidates = moments.ifEmpty { listOf(0L to "") }
            val best = candidates.maxBy { (t, _) ->
                val s = (t - 2000).coerceIn(0, sourceMs - targetMs)
                events.count { it in s until s + targetMs } * 1000 - t / 1000 // prefer earlier on ties
            }
            anchor = best.takeIf { it.second.isNotEmpty() }
            (best.first - 2000).coerceIn(0, sourceMs - targetMs)
        }
        val end = if (trim) start + targetMs else sourceMs

        // Captions in output time: a hook line, then dialogue (or key moments when nobody talks).
        val captions = if (!options.captions) emptyList() else buildList {
            val inWindow = { list: List<Pair<Long, String>> -> list.filter { it.first in start until end } }
            val lines = inWindow(cues).ifEmpty { inWindow(moments) }
            val hook = pack.optJSONArray("title_options")?.optString(0)?.takeIf { it.isNotBlank() } ?: request.title
            val firstLine = lines.firstOrNull()?.let { it.first - start } ?: Long.MAX_VALUE
            if (firstLine > 1500) add(CaptionSegment(0, minOf(2500, firstLine), hook))
            lines.forEachIndexed { i, (t, text) ->
                val from = t - start
                val next = lines.getOrNull(i + 1)?.let { it.first - start } ?: (end - start)
                add(CaptionSegment(from, minOf(next, from + 3500), text))
            }
        }

        val tags = pack.optJSONArray("hashtags")?.let { a -> (0 until a.length()).map { "#" + a.getString(it).trimStart('#').replace(" ", "") } }.orEmpty()
        val hashtags = if (options.trendHashtags) {
            (tags.take(6) + (if (platform == SharePlatform.TikTok) "#fyp" else "#reels") + "#minedtv").distinct()
        } else tags.take(2)

        val seconds = ((end - start) / 1000).toInt() + 2 // + the end card (2.5 s)
        val insights = buildList {
            pack.optString("summary").takeIf { it.isNotBlank() }?.let { add("AI watched the clip: $it") }
            if (trim) {
                val at = anchor?.let { " just before “${it.second}” (${formatDuration((it.first / 1000).toInt())})" } ?: ""
                add("Trimmed ${formatDuration((sourceMs / 1000).toInt())} to ${seconds}s$at, the stretch with the most action and dialogue")
            } else add("Kept the full ${seconds}s: already short enough for ${platform.displayName}")
            if (options.reframe) add("Center-cropped to ${platform.aspectRatio} (720×1280) so it fills the screen")
            add("Ends with a 2.5s MinedTv end card crediting @${request.creator.lowercase()}")
            if (captions.isNotEmpty()) {
                val from = if (cues.any { it.first in start until end }) "the clip's dialogue" else "the key moments"
                add("Burned in ${captions.size} caption lines from $from, opening with a hook")
            }
        }

        val edit = AiVideoEditor.Edit(start, end, options.reframe, captions, options.captionStyle, endCardHandle = request.creator)
        return Plan(edit) { jobId, out ->
            EnhanceResult(
                jobId = jobId,
                videoUrl = out.absolutePath,
                durationSeconds = seconds,
                aspectRatio = if (options.reframe) platform.aspectRatio else "16:9",
                captions = captions,
                suggestedCaption = pack.optString("caption").ifBlank { request.title },
                hashtags = hashtags,
                trendInsights = insights
            )
        }
    }

    /** "mm:ss" / "hh:mm:ss" cues -> (ms, text), sorted. */
    private fun JSONArray?.cues(textKey: String): List<Pair<Long, String>> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            val o = getJSONObject(i)
            val parts = o.optString("timestamp").split(":").mapNotNull { it.trim().toLongOrNull() }
            if (parts.isEmpty()) return@mapNotNull null
            val seconds = parts.fold(0L) { acc, v -> acc * 60 + v }
            seconds * 1000 to o.optString(textKey)
        }.filter { it.second.isNotBlank() }.sortedBy { it.first }
    }

    private fun durationMs(file: File): Long = MediaMetadataRetriever().run {
        try {
            setDataSource(file.absolutePath)
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            release()
        }
    }
}
