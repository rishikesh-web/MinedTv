package com.example.minedtv.data.dashboard

import androidx.compose.runtime.mutableStateMapOf
import com.example.minedtv.data.Clip
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.data.api.MinedTvApi.str
import com.example.minedtv.util.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The real MinedIt backend: the user's clips by publish state, live view/like/comment totals,
 * publish/unpublish, and virality from the backend's frame scorer (every upload is scored moment
 * by moment when it's processed; any clip you own, no options).
 * Not available yet: daily history, shares, traffic sources.
 */
object LiveDashboardRepository : DashboardRepository {
    /** Old uploads get scored on demand in a few seconds. */
    private const val MAX_WAIT_MS = 2 * 60 * 1000L
    // Snapshot state so clip rows recompose as soon as a score arrives.
    private val reports = mutableStateMapOf<String, ViralityReport>()

    override val isLive = true

    override suspend fun clips(): List<DashboardClip> {
        val clips = MinedTvApi.myClips("all").map { c ->
            val published = c.publishState == "published"
            DashboardClip(
                clip = c,
                publication = if (published) Publication(c.publishedAt?.let(::localDate) ?: LocalDate.now()) else null,
                history = emptyList(),
                totals = mapOf(Metric.Views to c.views.toLong(), Metric.Likes to c.likes.toLong(), Metric.Comments to c.comments.toLong())
            )
        }
        // Every upload is already scored, so pick the scores up for all of the user's clips.
        coroutineScope {
            clips.map { d -> async { runCatching { viralityState(d.clip, ViralityPlatform.General) } } }.awaitAll()
        }
        return clips
    }

    private fun localDate(iso: String): LocalDate? =
        runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()

    // The frame score doesn't depend on a platform; the parameter stays for the demo repository.
    override fun cachedReport(clip: Clip, platform: ViralityPlatform) = clip.remoteId?.let { reports[it] }

    override suspend fun viralityState(clip: Clip, platform: ViralityPlatform): ViralityState {
        val id = clip.remoteId ?: return ViralityState.None
        return parseState(id, MinedTvApi.get("/clips/$id/virality"))
    }

    /** Scores an upload from before the frame scorer existed (POST), then polls until ready. */
    override suspend fun simulate(clip: Clip, platform: ViralityPlatform, onProgress: (String) -> Unit): ViralityReport {
        val id = clip.remoteId ?: error("This clip isn't on MinedIt")
        val started = System.currentTimeMillis()
        var state = parseState(id, MinedTvApi.post("/clips/$id/virality"))
        while (true) {
            when (val s = state) {
                is ViralityState.Ready -> return s.report
                is ViralityState.Failed -> error(s.message)
                else -> Unit
            }
            val elapsed = (System.currentTimeMillis() - started) / 1000
            if (elapsed * 1000 > MAX_WAIT_MS) error("Still scoring on the server. Check back in a minute.")
            onProgress("Scoring every frame of the upload · %d:%02d".format(elapsed / 60, elapsed % 60))
            delay(2_000)
            state = viralityState(clip, platform)
        }
    }

    /** Daily views, likes and comments for the acting user over the last [days] days (owner only). */
    suspend fun analytics(days: Int): Analytics {
        val o = MinedTvApi.get("/users/${MinedTvApi.userId}/analytics?days=$days")
        fun totals(t: JSONObject?) = mapOf(
            Metric.Views to (t?.optLong("views") ?: 0L),
            Metric.Likes to (t?.optLong("likes") ?: 0L),
            Metric.Comments to (t?.optLong("comments") ?: 0L)
        )
        val daily = o.getJSONArray("daily").let { a ->
            (0 until a.length()).map { i ->
                val d = a.getJSONObject(i)
                DailyStat(LocalDate.parse(d.getString("day")), d.optLong("views"), d.optLong("likes"), d.optLong("comments"), 0)
            }
        }
        return Analytics(
            daily = daily,
            period = totals(o.optJSONObject("period")),
            previous = totals(o.optJSONObject("previous_period")),
            trackingSince = o.str("tracking_since")?.let(LocalDate::parse)
        ).also { Logger.d("Analytics: ${daily.size} days, period views ${it.period[Metric.Views]}") }
    }

    override suspend fun publish(clip: Clip) {
        MinedTvApi.publish(clip.remoteId ?: error("This clip isn't on MinedIt"))
    }

    override suspend fun unpublish(clip: Clip) {
        MinedTvApi.unpublish(clip.remoteId ?: error("This clip isn't on MinedIt"))
    }

    private fun parseState(id: String, o: JSONObject): ViralityState =
        when (o.str("status")) {
            "ready" -> ViralityState.Ready(parseReport(o).also { reports[id] = it })
            "processing" -> ViralityState.Processing
            "failed" -> ViralityState.Failed(o.str("error") ?: "Virality scoring failed")
            else -> ViralityState.None
        }

    /** Maps the frame scorer's result to the report the UI draws (no persona panel any more). */
    private fun parseReport(o: JSONObject): ViralityReport {
        val score = o.optDouble("score", 0.0).toFloat()
        val c = o.optJSONObject("components")
        val components = if (c == null) emptyList() else
            listOf("visual", "scene", "novelty", "visibility", "event", "speech").map { it to c.optDouble(it, 0.0).toFloat() }
        val curve = o.optJSONArray("curve")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it).let { p -> p.getLong("offset_ms") to p.getDouble("score").toFloat() } }
        }.orEmpty()
        val report = ViralityReport(
            platform = ViralityPlatform.General,
            score = score,
            verdict = Verdict.entries.firstOrNull { it.name.equals(o.str("verdict"), ignoreCase = true) } ?: verdictFor(score),
            inferredAudience = "",
            cascadeSummary = "",
            watchRate = 0f, completionRate = 0f, likeRate = 0f, commentRate = 0f, shareRate = 0f,
            layers = emptyList(),
            personas = emptyList(),
            strengths = emptyList(),
            risks = emptyList(),
            changes = emptyList(),
            disclaimer = "Scored from the video's own frames, detected events and speech. Scores compare moments within the same upload, not real-world reach.",
            peakOffsetMs = if (o.isNull("peak_offset_ms")) null else o.optLong("peak_offset_ms"),
            components = components,
            curve = curve
        )
        Logger.d("Virality (frame scorer): ${report.score.toInt()} ${report.verdict}, ${curve.size} curve points")
        return report
    }
}
