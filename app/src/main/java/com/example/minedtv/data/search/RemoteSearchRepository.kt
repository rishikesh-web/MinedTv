package com.example.minedtv.data.search

import com.example.minedtv.data.Clip
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.data.api.MinedTvApi.str
import com.example.minedtv.data.share.formatDuration
import org.json.JSONObject

/**
 * Real semantic search: GET /search?q=&user_id=. One result per session, already filtered for
 * relevance, with a 0..1 `confidence` and `moments` (best first): offset into the result's
 * playlist, confidence, matched signals, event label, transcript snippet and a frame thumbnail.
 * Library scope = filter to the current user's sessions.
 */
class RemoteSearchRepository : SearchRepository {

    override suspend fun search(query: SearchQuery): SearchResponse {
        var path = "/search?q=${MinedTvApi.q(query.text)}&limit=${query.limit.coerceAtMost(50)}"
        if (query.scope == SearchScope.Library && MinedTvApi.userId.isNotEmpty()) path += "&user_id=${MinedTvApi.userId}"
        val o = MinedTvApi.get(path)
        val results = o.getJSONArray("results")
        val hits = (0 until results.length()).map { hitFrom(results.getJSONObject(it), query.scope) }
        return SearchResponse(
            query = query.text,
            scope = query.scope,
            interpretation = SearchInterpretation(
                games = hits.map { it.clip.game }.distinct().take(3),
                events = hits.flatMap { h -> h.moments.map { it.label } }.distinct().take(4)
            ),
            hits = hits,
            tookMs = o.optLong("took_ms")
        )
    }

    private fun hitFrom(r: JSONObject, scope: SearchScope): SearchHit {
        val clipId = r.str("clip_id")
        val sessionId = r.getString("session_id")
        val start = r.getLong("start_ms")
        val end = r.getLong("end_ms")
        val windowMs = end - start
        val game = r.str("game") ?: "Unknown"
        val clip = Clip(
            id = MinedTvApi.intId(clipId ?: sessionId),
            title = r.str("title") ?: "Untitled",
            game = game,
            user = r.getJSONObject("user").getString("username"),
            views = 0, likes = 0, comments = 0,
            duration = formatDuration((windowMs / 1000).toInt()),
            age = "",
            colors = MinedTvApi.colorsFor(game),
            remoteId = clipId,
            sessionId = sessionId,
            playlistUrl = r.str("playlist_url"),
            thumbnailUrl = r.str("thumbnail_url"),
            startMs = start,
            endMs = end
        )

        val moments = if (r.has("moments")) {
            val arr = r.getJSONArray("moments")
            (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                val signals = m.getJSONArray("signals").strings()
                // offset_ms is where to seek in this result's playlist.
                val offset = m.getLong("offset_ms").coerceIn(0, windowMs)
                Moment(
                    clipId = clip.id,
                    startMs = offset,
                    endMs = (offset + 4_000).coerceAtMost(windowMs),
                    label = m.str("label")?.replaceFirstChar { it.uppercase() } ?: labelFor(signals),
                    description = m.str("snippet")?.let { "“$it”" } ?: "Matched on ${signals.joinToString(" + ")}",
                    confidence = m.optDouble("confidence", 0.0).toFloat(),
                    thumbnailUrl = m.str("thumbnail_url")
                )
            }
        } else {
            // Older response shape: a single match time.
            val offset = (r.getLong("match_t_ms") - start).coerceIn(0, windowMs)
            val signals = r.getJSONArray("signals").strings()
            listOf(
                Moment(
                    clip.id, offset, (offset + 4_000).coerceAtMost(windowMs), labelFor(signals),
                    r.str("transcript_snippet")?.let { "“$it”" } ?: "Matched on ${signals.joinToString(" + ")}",
                    r.optDouble("confidence", 1.0).toFloat(), r.str("thumbnail_url")
                )
            )
        }
        // Global results show only the best moment; the library shows them all along the timeline.
        val shown = if (scope == SearchScope.Global) moments.take(1) else moments.sortedBy { it.startMs }
        val confidence = r.optDouble("confidence", moments.maxOfOrNull { it.confidence.toDouble() } ?: 0.0).toFloat()
        return SearchHit(clip, confidence, shown)
    }

    private fun org.json.JSONArray.strings() = (0 until length()).map { getString(it) }

    private fun labelFor(signals: List<String>) = when {
        "event" in signals -> "Highlight"
        "speech" in signals || "keyword" in signals -> "Voice chat"
        else -> "On screen"
    }

    override suspend fun suggestions(prefix: String): List<String> =
        listOf("the clutch", "when my friend screamed", "headshot", "squad wipe", "victory", "funny moment")
            .filter { it.startsWith(prefix.lowercase()) }
}
