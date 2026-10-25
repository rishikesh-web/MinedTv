package com.example.minedtv.data.search

import com.example.minedtv.data.Clip
import com.example.minedtv.data.share.formatDuration

/** Where to search: everyone's public clips, or the user's own clip library. */
enum class SearchScope(val apiName: String) {
    Global("global"),
    Library("library")
}

data class SearchQuery(val text: String, val scope: SearchScope, val limit: Int = 50)

/** A moment inside a clip where the server detected something matching the query. */
data class Moment(
    val clipId: Int,
    val startMs: Long,
    val endMs: Long,
    /** Short event name from the server's video understanding, e.g. "Headshot". */
    val label: String,
    val description: String,
    /** 0..1 */
    val confidence: Float,
    /** Frame at the moment, from the backend; null for sample data. */
    val thumbnailUrl: String? = null
)

data class SearchHit(
    val clip: Clip,
    val score: Float,
    /** Matching moments sorted by time. Global search typically returns just the best one. */
    val moments: List<Moment>
)

/** How the server understood the query; shown back to the user as chips. */
data class SearchInterpretation(val games: List<String>, val events: List<String>)

data class SearchResponse(
    val query: String,
    val scope: SearchScope,
    val interpretation: SearchInterpretation,
    val hits: List<SearchHit>,
    val tookMs: Long
) {
    val totalMoments: Int get() = hits.sumOf { it.moments.size }
}

fun formatMs(ms: Long): String = formatDuration((ms / 1000).toInt())
