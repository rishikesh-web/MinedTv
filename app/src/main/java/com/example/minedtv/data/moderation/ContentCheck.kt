package com.example.minedtv.data.moderation

import org.json.JSONObject

/** One thing moderation flagged, at a point in the upload (null = applies to the whole video). */
data class Violation(val category: String, val timestampMs: Long?, val note: String)

/**
 * The backend's content moderation for one upload (Jasper's step: transcript + sampled frames,
 * judged by Gemini). `none` = clean, `mild` = published as 18+, `severe` = blocked (no clip made),
 * `pending` = still processing.
 */
data class ContentCheck(
    val sessionId: String?,
    val title: String,
    val durationMs: Long,
    val severity: String,
    val reason: String?,
    val violations: List<Violation>,
    /** HLS playlist (or a local file URI) to play flagged moments. */
    val playUrl: String?,
    /** Storage base for the session's per-second frames ("…/sessions/{id}"), if known. */
    val sessionBase: String?,
    /** Processing step while pending (e.g. "moderate"). */
    val step: String? = null
) {
    val isPending get() = severity == "pending"

    /** The sampled frame at [ms] (frames are stored every second as frames/{ms:09d}.jpg). */
    fun frameUrl(ms: Long): String? = sessionBase?.let { "%s/frames/%09d.jpg".format(it, ms / 1000 * 1000) }

    companion object {
        fun violations(o: JSONObject): List<Violation> {
            val a = o.optJSONArray("moderation_violations") ?: return emptyList()
            return (0 until a.length()).map { i ->
                val v = a.getJSONObject(i)
                Violation(
                    category = v.optString("category"),
                    timestampMs = if (v.isNull("timestamp_ms")) null else v.optLong("timestamp_ms"),
                    note = v.optString("note")
                )
            }.sortedBy { it.timestampMs ?: Long.MAX_VALUE }
        }

        private fun JSONObject.text(key: String) = optString(key).takeIf { it.isNotBlank() && it != "null" }

        /** From `GET /sessions/{id}` (the owner's view of an upload). */
        fun fromSession(o: JSONObject, title: String, fallbackPlayUrl: String? = null): ContentCheck {
            val status = o.optString("status")
            val severity = when {
                status == "blocked" -> "severe"
                status == "ready" || status == "failed" -> o.text("moderation_severity") ?: "none"
                else -> "pending"
            }
            val poster = o.text("poster_url")
            return ContentCheck(
                sessionId = o.optString("id"),
                title = o.text("title") ?: title,
                durationMs = o.optLong("duration_ms"),
                severity = severity,
                reason = o.text("moderation_reason") ?: o.text("error").takeIf { status == "blocked" },
                violations = violations(o),
                playUrl = o.text("playlist_url") ?: fallbackPlayUrl,
                sessionBase = poster?.substringBeforeLast("/"),
                step = o.text("last_step")
            )
        }
    }
}

/** Plain-language names for the moderator's short category tags. */
fun categoryLabel(tag: String): String = when (tag.lowercase()) {
    "profanity" -> "Profanity"
    "crude_humor", "crude_humour" -> "Crude humour"
    "sexual_content" -> "Sexual content"
    "suggestive", "innuendo" -> "Suggestive content"
    "threats", "threat" -> "Threat of violence"
    "hate_speech" -> "Hate speech"
    "hate_symbol" -> "Hate symbol"
    "self_harm" -> "Self-harm"
    "visual_gore", "gore", "graphic_violence" -> "Graphic violence"
    "on_screen_text" -> "On-screen text"
    else -> tag.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
