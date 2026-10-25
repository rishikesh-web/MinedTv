package com.example.minedtv.data.dashboard

import com.example.minedtv.data.Clip
import com.example.minedtv.data.api.ApiException
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.data.api.MinedTvApi.str
import com.example.minedtv.util.Logger
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The real MinedTv backend: the user's clips by publish state, live view/like totals,
 * publish/unpublish, and the server-side virality check (drafts only, takes a few minutes).
 * Not available yet: daily history, comments, shares, traffic sources.
 */
object LiveDashboardRepository : DashboardRepository {
    private const val PERSONAS = 9
    /** Extra audience panels run on the same AI analysis to show how much the score varies. */
    private const val EXTRA_PANELS = 2
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ranging = mutableSetOf<Pair<String, ViralityPlatform>>()
    private const val MAX_WAIT_MS = 15 * 60 * 1000L
    // Snapshot state so draft rows recompose as soon as a score arrives.
    private val reports = mutableStateMapOf<Pair<String, ViralityPlatform>, ViralityReport>()

    override val isLive = true

    override suspend fun clips(): List<DashboardClip> {
        val clips = MinedTvApi.myClips("all").map { c ->
            val published = c.publishState == "published"
            DashboardClip(
                clip = c,
                publication = if (published) Publication(c.publishedAt?.let(::localDate) ?: LocalDate.now()) else null,
                history = emptyList(),
                totals = mapOf(Metric.Views to c.views.toLong(), Metric.Likes to c.likes.toLong()),
                isReadyToPost = c.publishState == "ready"
            )
        }
        // Pick up scores the server already has for drafts (from earlier sessions or other devices).
        coroutineScope {
            clips.filterNot { it.isPublished }.map { d ->
                async { runCatching { viralityState(d.clip, ViralityPlatform.General) } }
            }.awaitAll()
        }
        return clips
    }

    private fun localDate(iso: String): LocalDate? =
        runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()

    override fun cachedReport(clip: Clip, platform: ViralityPlatform) = clip.remoteId?.let { reports[it to platform] }

    override suspend fun viralityState(clip: Clip, platform: ViralityPlatform): ViralityState {
        val id = clip.remoteId ?: return ViralityState.None
        return parseState(id, platform, MinedTvApi.virality(id, platform.apiName, PERSONAS))
    }

    override suspend fun simulate(clip: Clip, platform: ViralityPlatform, onProgress: (String) -> Unit): ViralityReport {
        val id = clip.remoteId ?: error("This clip isn't on MinedTv")
        val started = System.currentTimeMillis()
        var state = try {
            parseState(id, platform, MinedTvApi.startVirality(id, platform.apiName, PERSONAS))
        } catch (e: ApiException) {
            if (e.code == 409) error("Virality checks only run on drafts. Move it back to drafts first.")
            throw e
        }
        while (true) {
            when (val s = state) {
                is ViralityState.Ready -> return s.report
                is ViralityState.Failed -> error(s.message)
                else -> Unit
            }
            val elapsed = (System.currentTimeMillis() - started) / 1000
            if (elapsed * 1000 > MAX_WAIT_MS) error("Still running on the server. Check back in a few minutes.")
            onProgress("Exporting, analysing and running $PERSONAS personas · %d:%02d".format(elapsed / 60, elapsed % 60))
            delay(10_000)
            state = viralityState(clip, platform)
        }
    }

    override suspend fun publish(clip: Clip) {
        MinedTvApi.publish(clip.remoteId ?: error("This clip isn't on MinedTv"))
    }

    override suspend fun unpublish(clip: Clip) {
        MinedTvApi.unpublish(clip.remoteId ?: error("This clip isn't on MinedTv"))
        clip.remoteId?.let { id -> reports.keys.removeAll { it.first == id } }
    }

    private fun parseState(id: String, platform: ViralityPlatform, o: JSONObject): ViralityState =
        when (o.str("status")) {
            "ready" -> {
                val key = id to platform
                val parsed = parseReport(o.getJSONObject("result"))
                // Keep extra panel scores already gathered for this same server result.
                val old = reports[key]
                val report = if (old != null && old.score == parsed.score) parsed.copy(samples = old.samples, rangePending = old.rangePending) else parsed
                reports[key] = report
                ensureRange(id, platform)
                ViralityState.Ready(report)
            }
            "processing" -> ViralityState.Processing
            "failed" -> ViralityState.Failed(o.str("error") ?: "Virality check failed")
            else -> ViralityState.None
        }

    /**
     * The server's result is one audience panel. Run [EXTRA_PANELS] more on the clip's cached AI
     * analysis (/creator/simulate-analysis, ~15-25 s, nothing stored) and keep all scores, so the UI
     * can show the median and the spread instead of one arbitrary sample.
     */
    private fun ensureRange(id: String, platform: ViralityPlatform) {
        val key = id to platform
        val report = reports[key] ?: return
        if (report.hasRange || !ranging.add(key)) return
        reports[key] = report.copy(rangePending = true)
        background.launch {
            val extras = runCatching {
                val pack = MinedTvApi.get("/clips/$id/creator-pack").takeIf { it.str("status") == "ready" }?.getJSONObject("pack")
                    ?: error("creator pack not ready")
                val body = JSONObject().put("analysis", pack).put("platform", platform.apiName).put("persona_count", PERSONAS)
                coroutineScope {
                    (1..EXTRA_PANELS).map {
                        async { runCatching { MinedTvApi.post("/creator/simulate-analysis", body).getDouble("virality_score").toFloat() }.getOrNull() }
                    }.awaitAll().filterNotNull()
                }
            }.onFailure { Logger.e("Virality range failed for $id", it) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                reports[key]?.let { reports[key] = it.copy(samples = listOf(it.score) + extras, rangePending = false) }
                if (extras.isEmpty()) ranging.remove(key) // allow a retry next time
            }
            Logger.i("Virality range for $id: ${(listOf(report.score) + extras).joinToString { "%.1f".format(it) }}")
        }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private fun layerOf(s: String) = when (s) {
        "target" -> AudienceLayer.Target
        "adjacent" -> AudienceLayer.Adjacent
        else -> AudienceLayer.Outsider
    }

    /** Maps the backend's AudienceSimulation to the report the UI draws. */
    private fun parseReport(r: JSONObject): ViralityReport {
        val m = r.getJSONObject("metrics")
        val report = ViralityReport(
            platform = ViralityPlatform.entries.firstOrNull { it.apiName == r.str("platform") } ?: ViralityPlatform.General,
            score = r.getDouble("virality_score").toFloat(),
            verdict = Verdict.entries.firstOrNull { it.name.equals(r.str("verdict"), ignoreCase = true) } ?: Verdict.Mixed,
            inferredAudience = r.str("inferred_target_audience") ?: "",
            cascadeSummary = r.str("cascade_summary") ?: "",
            watchRate = m.getDouble("simulated_watch_rate").toFloat(),
            completionRate = m.getDouble("simulated_completion_rate").toFloat(),
            likeRate = m.getDouble("simulated_like_rate").toFloat(),
            commentRate = m.getDouble("simulated_comment_rate").toFloat(),
            shareRate = m.getDouble("simulated_share_rate").toFloat(),
            layers = r.getJSONArray("layers").objects().map {
                LayerResult(
                    layerOf(it.getString("layer")), it.getInt("persona_count"),
                    it.getDouble("score").toFloat(), it.getDouble("threshold").toFloat(),
                    it.getBoolean("exposed_in_cascade"), it.getBoolean("cleared_threshold")
                )
            },
            personas = r.getJSONArray("persona_reviews").objects().map {
                PersonaReview(
                    name = it.getString("name"),
                    layer = layerOf(it.getString("layer")),
                    profile = it.str("profile") ?: "",
                    hookScore = it.getInt("hook_score"),
                    primaryAction = AudienceAction.entries.firstOrNull { a -> a.name.equals(it.str("primary_action"), true) } ?: AudienceAction.Skip,
                    reaction = it.str("reaction") ?: "",
                    friction = it.str("friction") ?: ""
                )
            },
            strengths = r.getJSONArray("consensus_strengths").strings(),
            risks = r.getJSONArray("consensus_risks").strings(),
            changes = r.getJSONArray("recommended_changes").strings(),
            disclaimer = r.str("disclaimer") ?: "Synthetic persona feedback, not real analytics."
        )
        Logger.d("Virality report parsed: ${report.score.toInt()} ${report.verdict}, ${report.personas.size} personas")
        return report
    }
}
