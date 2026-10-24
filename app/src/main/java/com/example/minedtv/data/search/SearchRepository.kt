package com.example.minedtv.data.search

import com.example.minedtv.data.Clip
import com.example.minedtv.data.MyLibrary
import com.example.minedtv.data.SampleData
import com.example.minedtv.data.share.durationSeconds
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Client for the semantic search engine. The server indexes what happens *inside* each
 * video (kills, headshots, clutches, ...) so queries match content, not just titles.
 *
 * Expected server contract (to implement later):
 *  - POST /v1/search
 *      body: { "query": "fortnite headshot", "scope": "global" | "library", "limit": 50 }
 *      resp: { "interpretation": { "games": ["Fortnite"], "events": ["Headshot"] },
 *              "hits": [ { "clipId": 101, "score": 0.93,
 *                          "moments": [ { "startMs": 42000, "endMs": 45500, "label": "Headshot",
 *                                         "description": "...", "confidence": 0.94 } ] } ],
 *              "tookMs": 120 }
 *  - GET /v1/search/suggest?q=fort   -> ["fortnite headshot", ...]
 *  Library clips are indexed by the server after upload; scope=library only searches the caller's clips.
 */
interface SearchRepository {
    suspend fun search(query: SearchQuery): SearchResponse
    suspend fun suggestions(prefix: String): List<String>
}

/**
 * Simulates the semantic engine: every clip gets a deterministic set of "detected" events,
 * and queries are mapped to games + event types via synonyms, ignoring titles.
 */
class FakeSearchRepository : SearchRepository {

    private enum class Event(val label: String, val synonyms: List<String>, val descriptions: List<String>) {
        Headshot("Headshot", listOf("headshot", "headshots", "head shot", "hs", "one tap", "onetap", "tap"),
            listOf("One-tap headshot", "Headshot through smoke", "Flick headshot")),
        Sniper("Sniper shot", listOf("sniper", "snipe", "snipes", "noscope", "no scope", "quickscope", "awp", "long range"),
            listOf("Long-range sniper hit", "No-scope sniper kill", "Quickscope")),
        Clutch("Clutch", listOf("clutch", "clutches", "1v2", "1v3", "1v4", "1v5", "last alive"),
            listOf("1v3 clutch", "Last-alive clutch", "Round-winning clutch")),
        MultiKill("Multi-kill", listOf("ace", "triple", "quad", "squad wipe", "team wipe", "multi kill", "multikill", "double kill", "wipe"),
            listOf("Triple kill", "Squad wipe", "Double kill")),
        Victory("Victory", listOf("win", "wins", "won", "victory", "royale", "dub", "first place", "winner"),
            listOf("Victory screen", "Match won", "Final circle win")),
        Explosion("Explosion", listOf("explosion", "explosions", "rocket", "grenade", "nade", "boom", "c4", "tnt", "blast"),
            listOf("Grenade multi-hit", "Rocket explosion", "Big explosion")),
        Funny("Funny moment", listOf("funny", "fail", "fails", "bug", "glitch", "lol", "troll", "hilarious", "funny moment"),
            listOf("Physics glitch", "Teammate troll", "Epic fail")),
        Revive("Revive", listOf("revive", "revives", "rez", "res", "pick up"),
            listOf("Revive under fire", "Clutch revive")),
        Building("Build fight", listOf("build", "building", "edit", "box fight", "piece control", "build fight"),
            listOf("Build fight", "Fast edit play", "Box fight")),
        BossFight("Boss fight", listOf("boss", "raid", "dragon", "wither", "boss fight"),
            listOf("Boss fight", "Ender dragon hit")),
        Goal("Goal", listOf("goal", "goals", "score", "aerial", "save", "saves"),
            listOf("Aerial goal", "Last-second save", "Ceiling shot goal")),
        Death("Death", listOf("death", "deaths", "died", "die", "rip", "eliminated"),
            listOf("Got eliminated", "Fall damage death"))
    }

    private val gameEvents = mapOf(
        "Valorant" to listOf(Event.Headshot, Event.Sniper, Event.Clutch, Event.MultiKill, Event.Victory, Event.Funny),
        "Fortnite" to listOf(Event.Headshot, Event.Sniper, Event.Building, Event.Victory, Event.Explosion, Event.Funny, Event.Revive, Event.MultiKill),
        "Apex Legends" to listOf(Event.Headshot, Event.Sniper, Event.Revive, Event.MultiKill, Event.Victory, Event.Clutch),
        "CS2" to listOf(Event.Headshot, Event.Sniper, Event.Clutch, Event.MultiKill, Event.Explosion, Event.Victory),
        "Minecraft" to listOf(Event.BossFight, Event.Explosion, Event.Funny, Event.Death, Event.Victory),
        "Rocket League" to listOf(Event.Goal, Event.Funny, Event.Victory),
        "Overwatch 2" to listOf(Event.Headshot, Event.MultiKill, Event.Clutch, Event.Victory, Event.Funny),
        "Call of Duty" to listOf(Event.Headshot, Event.Sniper, Event.MultiKill, Event.Explosion, Event.Victory, Event.Funny)
    )

    private val gameAliases = mapOf(
        "Valorant" to listOf("valorant", "valo", "val"),
        "Fortnite" to listOf("fortnite", "fn", "fort"),
        "Apex Legends" to listOf("apex"),
        "CS2" to listOf("cs2", "cs", "csgo", "counter strike", "counter-strike"),
        "Minecraft" to listOf("minecraft", "mc"),
        "Rocket League" to listOf("rocket league", "rl"),
        "Overwatch 2" to listOf("overwatch", "ow", "ow2"),
        "Call of Duty" to listOf("call of duty", "cod", "warzone")
    )

    private val eventCache = HashMap<Int, List<Pair<Event, Moment>>>()

    /** Pretend video-understanding output for a clip. */
    private fun eventsFor(clip: Clip): List<Pair<Event, Moment>> = eventCache.getOrPut(clip.id) {
        val rnd = Random(clip.id * 7919 + 13)
        val durationMs = clip.durationSeconds * 1000L
        val possible = gameEvents[clip.game] ?: Event.entries
        val count = (durationMs / 20_000).toInt().coerceIn(1, 7) + rnd.nextInt(0, 2)
        // Shooters get a couple of guaranteed headshots so the demo query always lands.
        val picks = buildList {
            if (Event.Headshot in possible) repeat(if (durationMs > 60_000) 2 else 1) { add(Event.Headshot) }
            while (size < count) add(possible[rnd.nextInt(possible.size)])
        }
        picks.map { event ->
            val start = rnd.nextLong(1_000, maxOf(1_001, durationMs - 5_000))
            event to Moment(
                clipId = clip.id,
                startMs = start,
                endMs = minOf(durationMs, start + rnd.nextLong(2_500, 6_000)),
                label = event.label,
                description = event.descriptions[rnd.nextInt(event.descriptions.size)],
                confidence = 0.62f + rnd.nextFloat() * 0.36f
            )
        }.sortedBy { it.second.startMs }
    }

    private fun containsPhrase(text: String, phrase: String) =
        Regex("(^|\\W)${Regex.escape(phrase)}($|\\W)").containsMatchIn(text)

    override suspend fun search(query: SearchQuery): SearchResponse {
        val started = System.currentTimeMillis()
        delay(450L + Random.nextLong(0, 300))

        val q = query.text.lowercase().trim()
        val games = gameAliases.filter { (_, aliases) -> aliases.any { containsPhrase(q, it) } }.keys.toList()
        val events = Event.entries.filter { e -> e.synonyms.any { containsPhrase(q, it) } }
        val source = if (query.scope == SearchScope.Library) MyLibrary.clips else SampleData.clips

        val hits = source.mapNotNull { clip ->
            if (games.isNotEmpty() && clip.game !in games) return@mapNotNull null
            val all = eventsFor(clip)
            val moments = when {
                events.isNotEmpty() -> all.filter { it.first in events }.map { it.second }
                games.isNotEmpty() -> all.map { it.second }.sortedByDescending { it.confidence }.take(3).sortedBy { it.startMs }
                // Nothing understood: fall back to plain text matching.
                clip.title.lowercase().contains(q) || clip.user.lowercase().contains(q) ->
                    all.map { it.second }.sortedByDescending { it.confidence }.take(2).sortedBy { it.startMs }
                else -> emptyList()
            }
            if (moments.isEmpty()) return@mapNotNull null
            val best = moments.maxOf { it.confidence }
            val shown = if (query.scope == SearchScope.Global) listOf(moments.maxBy { it.confidence }) else moments
            SearchHit(clip, best * (1f + 0.08f * moments.size), shown)
        }.sortedByDescending { it.score }.take(query.limit)

        return SearchResponse(
            query = query.text,
            scope = query.scope,
            interpretation = SearchInterpretation(games, events.map { it.label }),
            hits = hits,
            tookMs = System.currentTimeMillis() - started
        )
    }

    override suspend fun suggestions(prefix: String): List<String> {
        val all = listOf(
            "fortnite headshot", "valorant clutch", "apex revive", "cs2 ace", "sniper shots",
            "funny moments", "victory royale", "minecraft boss fight", "rocket league goals", "warzone explosion"
        )
        return all.filter { it.startsWith(prefix.lowercase()) }
    }
}
