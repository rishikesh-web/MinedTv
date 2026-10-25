package com.example.minedtv.data.dashboard

import com.example.minedtv.data.Clip
import com.example.minedtv.data.MyLibrary
import com.example.minedtv.data.SampleData
import com.example.minedtv.recorder.LocalClips
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.exp
import kotlin.random.Random
import androidx.compose.runtime.mutableStateMapOf

/**
 * Creator dashboard data. Today it's all generated locally.
 *
 * Backend mapping when it's ready:
 *  - published/unpublished on MinedIt: needs a publish state on clips (not in the API yet)
 *  - daily stats + traffic sources: needs snapshots/events on the backend (the API only has current view/like counts;
 *    no comments, shares or history yet)
 *  - simulate(): POST /clips/{id}/creator-pack (poll) -> POST /creator/simulate-analysis
 */
interface DashboardRepository {
    /** True for the real backend, false for generated demo data. */
    val isLive: Boolean
    suspend fun clips(): List<DashboardClip>
    /** Posts a draft to the MinedIt feed. */
    suspend fun publish(clip: Clip)
    /** Takes a published clip back to draft. */
    suspend fun unpublish(clip: Clip)
    fun cachedReport(clip: Clip, platform: ViralityPlatform): ViralityReport?
    /** Whether a prediction already exists or is running (live checks survive leaving the screen). */
    suspend fun viralityState(clip: Clip, platform: ViralityPlatform): ViralityState
    suspend fun simulate(clip: Clip, platform: ViralityPlatform, onProgress: (String) -> Unit = {}): ViralityReport
}

object FakeDashboardRepository : DashboardRepository {
    override val isLive = false
    private const val HISTORY_DAYS = 180
    private val today = LocalDate.now()
    private val reports = mutableStateMapOf<Pair<Int, ViralityPlatform>, ViralityReport>()

    private val published: MutableList<DashboardClip> by lazy {
        // (clip index, days since publish, peak daily views)
        listOf(
            Triple(0, 3, 42_000), Triple(1, 9, 18_000), Triple(2, 16, 64_000), Triple(4, 27, 9_000),
            Triple(6, 41, 25_000), Triple(9, 58, 31_000), Triple(12, 84, 12_000), Triple(14, 120, 22_000)
        ).map { (index, daysAgo, peak) -> publishedClip(SampleData.clips[index], daysAgo, peak) }.toMutableList()
    }
    private val publishedIds = HashSet<Int>()

    private fun publishedClip(clip: Clip, daysAgo: Int, peak: Int): DashboardClip {
        val rnd = Random(clip.id * 31 + 7)
        // Feed dominates; search and shared links vary per clip.
        val weights = mapOf(
            TrafficSource.Feed to 2.5f + rnd.nextFloat(),
            TrafficSource.Search to 0.4f + rnd.nextFloat(),
            TrafficSource.Profile to 0.2f + rnd.nextFloat() * 0.5f,
            TrafficSource.SharedLink to 0.3f + rnd.nextFloat()
        )
        val total = weights.values.sum()
        publishedIds.add(clip.id)
        return DashboardClip(
            clip = clip,
            publication = Publication(today.minusDays(daysAgo.toLong())),
            history = history(clip.id, daysAgo, peak),
            trafficShare = weights.mapValues { it.value / total }
        )
    }

    /** Views spike after publishing and decay over ~2 weeks, with daily noise. */
    private fun history(seed: Int, daysAgo: Int, peak: Int): List<DailyStat> {
        val rnd = Random(seed)
        return (0 until HISTORY_DAYS).map { i ->
            val day = today.minusDays((HISTORY_DAYS - 1 - i).toLong())
            val age = i - (HISTORY_DAYS - 1 - daysAgo)
            val views = if (age < 0) 0L else
                (peak * exp(-age / 9.0) * (0.7 + rnd.nextDouble() * 0.6) + peak * 0.02 * rnd.nextDouble()).toLong()
            DailyStat(
                day = day,
                views = views,
                likes = (views * (0.06 + rnd.nextDouble() * 0.05)).toLong(),
                comments = (views * (0.004 + rnd.nextDouble() * 0.008)).toLong(),
                shares = (views * (0.008 + rnd.nextDouble() * 0.012)).toLong()
            )
        }
    }

    override suspend fun clips(): List<DashboardClip> {
        val pub = published.sortedBy { it.publication?.publishedOn }.reversed()
        val recorded = LocalClips.clips.value.map { DashboardClip(it, null, emptyList(), isLocalRecording = true) }
        val drafts = MyLibrary.clips.take(4).map { DashboardClip(it, null, emptyList()) }
        return pub + (recorded + drafts).filter { it.clip.id !in publishedIds }
    }

    override suspend fun publish(clip: Clip) {
        if (clip.id in publishedIds) return
        published.add(publishedClip(clip, daysAgo = 0, peak = 900)) // first day's views
    }

    override suspend fun unpublish(clip: Clip) {
        published.removeAll { it.clip.id == clip.id }
        publishedIds.remove(clip.id)
    }

    override suspend fun viralityState(clip: Clip, platform: ViralityPlatform): ViralityState =
        reports[clip.id to platform]?.let { ViralityState.Ready(it) } ?: ViralityState.None

    override fun cachedReport(clip: Clip, platform: ViralityPlatform) = reports[clip.id to platform]

    override suspend fun simulate(clip: Clip, platform: ViralityPlatform, onProgress: (String) -> Unit): ViralityReport {
        delay(2_400) // the real engine takes ~8-19 s
        return report(clip, platform).also { reports[clip.id to platform] = it }
    }

    private fun report(clip: Clip, platform: ViralityPlatform): ViralityReport {
        val rnd = Random(clip.id * 13 + platform.ordinal)
        val bias = when (platform) {
            ViralityPlatform.TikTok -> 6f
            ViralityPlatform.Instagram -> 3f
            ViralityPlatform.YouTube -> -4f
            ViralityPlatform.General -> 0f
        }
        val score = (38f + rnd.nextFloat() * 44f + bias).coerceIn(5f, 97f)
        val layerScores = listOf(score + 10f, score - 6f, score - 24f).map { it.coerceIn(0f, 100f) }
        val thresholds = listOf(55f, 50f, 45f)
        val counts = listOf(4, 3, 2)
        val layers = ArrayList<LayerResult>()
        var exposed = true
        AudienceLayer.entries.forEachIndexed { i, layer ->
            val cleared = exposed && layerScores[i] >= thresholds[i]
            layers.add(LayerResult(layer, counts[i], layerScores[i], thresholds[i], exposed, cleared))
            exposed = cleared
        }
        val reach = layers.lastOrNull { it.cleared }?.layer
        // The verdict follows how far the cascade spreads, so the two never disagree.
        val verdict = when (reach) {
            null -> Verdict.Weak
            AudienceLayer.Target -> Verdict.Mixed
            AudienceLayer.Adjacent -> Verdict.Promising
            AudienceLayer.Outsider -> Verdict.Strong
        }
        val summary = when (reach) {
            null -> "Core fans weren't hooked enough, so it stayed with a small audience."
            AudienceLayer.Target -> "Core ${clip.game} fans pass it on, but adjacent viewers drop off."
            AudienceLayer.Adjacent -> "It travels from ${clip.game} fans to general gaming viewers, then stalls before outsiders."
            AudienceLayer.Outsider -> "It breaks out beyond gamers. Outsiders shared it too."
        }
        val pct = { base: Float -> (base + rnd.nextFloat() * 12f).coerceIn(0f, 100f) }
        return ViralityReport(
            platform = platform,
            score = score,
            verdict = verdict,
            // Demo: three panels a few points apart, like the real engine.
            samples = listOf(score, (score - 3 - rnd.nextFloat() * 3).coerceAtLeast(0f), (score + 2 + rnd.nextFloat() * 3).coerceAtMost(100f)),
            inferredAudience = "${clip.game} players and FPS highlight viewers",
            cascadeSummary = summary,
            watchRate = pct(score * 0.9f),
            completionRate = pct(score * 0.75f),
            likeRate = pct(score * 0.6f),
            commentRate = pct(score * 0.25f),
            shareRate = pct(score * 0.35f),
            layers = layers,
            personas = personas(clip, rnd),
            strengths = listOf(
                "The action starts within the first two seconds",
                "Clear payoff: the \"${clip.title}\" moment is easy to follow",
                "Short enough to rewatch on ${platform.label}"
            ),
            risks = listOf(
                "Non-${clip.game} players may not get what makes it impressive",
                "Game HUD is small on a phone screen",
                if (platform == ViralityPlatform.YouTube) "Too short for YouTube's long-form audience" else "No hook text in the first frame"
            ),
            changes = listOf(
                "Add a one-line caption that sets up the stakes (\"1v4, 10 HP\")",
                "Crop to 9:16 and zoom on the action",
                "Cut the last two seconds after the kill feed"
            )
        )
    }

    private fun personas(clip: Clip, rnd: Random): List<PersonaReview> {
        val pool = listOf(
            Triple("Ranked grinder", AudienceLayer.Target, "Plays ${clip.game} daily, watches pro clips"),
            Triple("Clip collector", AudienceLayer.Target, "Saves highlights for their own montages"),
            Triple("Returning player", AudienceLayer.Target, "Took a break from ${clip.game}, curious about the meta"),
            Triple("Streamer fan", AudienceLayer.Target, "Follows creators more than games"),
            Triple("Casual FPS player", AudienceLayer.Adjacent, "Plays shooters on weekends"),
            Triple("Esports watcher", AudienceLayer.Adjacent, "Watches tournaments, rarely plays"),
            Triple("Mobile gamer", AudienceLayer.Adjacent, "Plays on phone, scrolls TikTok gaming"),
            Triple("Meme scroller", AudienceLayer.Outsider, "Follows funny clips, not gaming"),
            Triple("Parent of a gamer", AudienceLayer.Outsider, "Sees what their kid watches")
        )
        val reactions = listOf(
            "Wait, how did that land? Rewatching." to "Took a second to see who got hit",
            "Clean. Sending this to my duo." to "Wanted the score at the start",
            "That rotate was smart." to "Audio is a bit quiet",
            "Okay that's actually insane." to "Ending drags slightly",
            "I don't play this but that looked hard." to "Didn't know the context",
            "Not my game, still fun to watch." to "HUD is cluttered",
            "Scrolled past at first, came back." to "Hook could be stronger"
        )
        return pool.map { (name, layer, profile) ->
            val (reaction, friction) = reactions[rnd.nextInt(reactions.size)]
            val hook = when (layer) {
                AudienceLayer.Target -> 60 + rnd.nextInt(35)
                AudienceLayer.Adjacent -> 40 + rnd.nextInt(40)
                AudienceLayer.Outsider -> 20 + rnd.nextInt(45)
            }
            val action = when {
                hook > 80 -> AudienceAction.Share
                hook > 65 -> AudienceAction.Like
                hook > 55 -> AudienceAction.Comment
                hook > 45 -> AudienceAction.Save
                else -> AudienceAction.Skip
            }
            PersonaReview(name, layer, profile, hook, action, reaction, friction)
        }
    }
}
