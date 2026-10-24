package com.example.minedtv.data.share

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Talks to the AI editing server. The server does the heavy lifting (trend lookup,
 * reframing, captioning, trimming, rendering); the app only submits and watches jobs.
 *
 * Expected server contract (to implement later):
 *  - POST /v1/enhance           body: EnhanceRequest           -> { "jobId": "..." }
 *  - GET  /v1/enhance/{jobId}   (poll, or a websocket/SSE)     -> EnhanceJobStatus
 */
interface EnhanceRepository {
    suspend fun submit(request: EnhanceRequest): String
    fun observe(jobId: String): Flow<EnhanceJobStatus>
}

/** Simulates the server so the UI can be built and demoed without a backend. */
class FakeEnhanceRepository : EnhanceRepository {
    private val jobs = ConcurrentHashMap<String, EnhanceRequest>()

    override suspend fun submit(request: EnhanceRequest): String {
        delay(300)
        val jobId = "job_${System.currentTimeMillis()}"
        jobs[jobId] = request
        return jobId
    }

    override fun observe(jobId: String): Flow<EnhanceJobStatus> = flow {
        val request = jobs[jobId]
        if (request == null) {
            emit(EnhanceJobStatus.Failed("Unknown job $jobId"))
            return@flow
        }
        for (i in 1..10) {
            delay(120)
            emit(EnhanceJobStatus.Uploading(i / 10f))
        }
        val stages = request.options.stages()
        stages.forEachIndexed { index, stage ->
            for (step in 1..5) {
                delay(180)
                emit(EnhanceJobStatus.Processing(stage, (index + step / 5f) / stages.size))
            }
        }
        emit(EnhanceJobStatus.Completed(fakeResult(jobId, request)))
    }

    private fun fakeResult(jobId: String, request: EnhanceRequest): EnhanceResult {
        val options = request.options
        val platform = request.platform
        val duration = if (options.autoTrim) minOf(request.sourceSeconds, options.targetSeconds) else request.sourceSeconds
        val captionLines = listOf("WAIT FOR IT…", request.title, "no way that just happened 😳", "GG 🔥")
        val segmentMs = duration * 1000L / captionLines.size
        val gameTag = "#" + request.game.replace(" ", "").lowercase()
        val platformTag = if (platform == SharePlatform.TikTok) "#fyp" else "#reels"

        return EnhanceResult(
            jobId = jobId,
            videoUrl = "https://example.com/enhanced/$jobId.mp4",
            durationSeconds = duration,
            aspectRatio = if (options.reframe) platform.aspectRatio else "16:9",
            captions = if (options.captions) {
                captionLines.mapIndexed { i, text -> CaptionSegment(i * segmentMs, (i + 1) * segmentMs, text) }
            } else emptyList(),
            suggestedCaption = "${request.title} 🤯 would you have clutched this?",
            hashtags = if (options.trendHashtags) {
                listOf(gameTag, "#gaming", "#clutch", platformTag, "#minedtv")
            } else listOf(gameTag),
            trendInsights = when (platform) {
                SharePlatform.TikTok -> listOf(
                    "Top ${request.game} clips this week hit the action within 1.5s, so we moved the highlight up front",
                    "Bold centered captions are trending in #gaming (+18% watch time)",
                    "Gaming clips around ${platform.idealSeconds}s are getting the best completion rate"
                )
                SharePlatform.InstagramReels -> listOf(
                    "Reels under ${platform.idealSeconds}s get the highest replay rate for gaming",
                    "Captions placed in the lower third avoid Reels UI overlap",
                    "Hook text in the first frame boosts shares"
                )
            }
        )
    }
}
