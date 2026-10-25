package com.example.minedtv.data.api

import com.example.minedtv.BuildConfig
import com.example.minedtv.data.Clip
import com.example.minedtv.data.SampleData
import com.example.minedtv.data.share.formatDuration
import com.example.minedtv.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import com.example.minedtv.data.Comment

class ApiException(val code: Int, message: String) : Exception(message)

data class RemoteProfile(
    val id: String,
    val username: String,
    val followerCount: Int,
    val followingCount: Int,
    val publishedClipCount: Int,
    val followedByMe: Boolean
)

/** An Inbox item; `message` is ready to show as-is. */
data class RemoteNotification(
    val id: String,
    val type: String,
    val message: String,
    val actor: String?,
    val clipId: String?,
    val clipThumbnail: String?,
    val read: Boolean,
    val age: String
)

/** Thin client for the MinedTV backend (virajchogle/minedtv). Docs: <base>/docs */
object MinedTvApi {
    private val base = BuildConfig.API_BASE_URL.trimEnd('/')
    var userId: String = BuildConfig.DEMO_USER_ID
    val isConfigured: Boolean get() = BuildConfig.API_KEY.isNotEmpty()

    suspend fun get(path: String): JSONObject = JSONObject(request("GET", path))
    suspend fun post(path: String, body: JSONObject? = null): JSONObject = JSONObject(request("POST", path, body))
    suspend fun delete(path: String): JSONObject = JSONObject(request("DELETE", path))

    private suspend fun request(method: String, path: String, body: JSONObject? = null): String =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 60_000 // first search after idle can take a while (GPU cold start)
                setRequestProperty("X-API-Key", BuildConfig.API_KEY)
                if (userId.isNotEmpty()) setRequestProperty("X-User-Id", userId)
                setRequestProperty("Accept", "application/json")
                // Cloud Run's front end rejects a POST without Content-Length (411), so always send one.
                val bytes = body?.toString()?.toByteArray() ?: if (method == "POST") ByteArray(0) else null
                if (bytes != null) {
                    doOutput = true
                    if (body != null) setRequestProperty("Content-Type", "application/json")
                    setFixedLengthStreamingMode(bytes.size)
                    outputStream.use { it.write(bytes) }
                }
            }
            try {
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                Logger.d("API $method ${path.substringBefore('?')} -> $code (${System.currentTimeMillis() - started}ms)")
                if (code !in 200..299) throw ApiException(code, "HTTP $code: $text")
                text.ifEmpty { "{}" }
            } finally {
                conn.disconnect()
            }
        }

    fun q(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** org.json returns the string "null" for JSON nulls from optString; this doesn't. */
    fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)

    // --- JSON -> app models -------------------------------------------------

    /** The app keys lists by Int id; derive a stable one from the UUID. */
    fun intId(uuid: String): Int = uuid.hashCode() and 0x7fffffff

    fun colorsFor(game: String) =
        SampleData.games.firstOrNull { it.name.equals(game, ignoreCase = true) }?.colors
            ?: SampleData.palettes[(game.hashCode() and 0x7fffffff) % SampleData.palettes.size]

    fun clipFrom(o: JSONObject): Clip {
        val game = o.str("game") ?: "Unknown"
        val durationMs = o.getLong("duration_ms")
        return Clip(
            id = intId(o.getString("id")),
            title = o.str("title") ?: "Untitled",
            game = game,
            user = o.getJSONObject("user").getString("username"),
            views = o.optLong("view_count").toInt(),
            likes = o.optLong("like_count").toInt(),
            comments = o.optInt("comment_count"),
            duration = formatDuration((durationMs / 1000).toInt()),
            age = o.str("created_at")?.let(::ageOf) ?: "",
            colors = colorsFor(game),
            remoteId = o.getString("id"),
            sessionId = o.str("session_id"),
            playlistUrl = o.str("playlist_url"),
            thumbnailUrl = o.str("thumbnail_url"),
            startMs = o.optLong("start_ms"),
            endMs = o.optLong("end_ms"),
            likedByMe = !o.isNull("liked_by_me") && o.optBoolean("liked_by_me", false),
            publishState = o.str("publish_state"),
            publishedAt = o.str("published_at"),
            userId = o.getJSONObject("user").str("id")
        )
    }

    fun ageOf(iso: String): String = runCatching {
        val d = Duration.between(Instant.parse(iso), Instant.now())
        when {
            d.toMinutes() < 1 -> "now"
            d.toHours() < 1 -> "${d.toMinutes()}m"
            d.toDays() < 1 -> "${d.toHours()}h"
            d.toDays() < 7 -> "${d.toDays()}d"
            else -> "${d.toDays() / 7}w"
        }
    }.getOrDefault("")

    private fun clips(items: JSONArray) = (0 until items.length()).map { clipFrom(items.getJSONObject(it)) }

    // --- endpoints ----------------------------------------------------------

    suspend fun feed(cursor: String? = null, limit: Int = 20): Pair<List<Clip>, String?> {
        val o = get("/feed?limit=$limit" + (cursor?.let { "&cursor=${q(it)}" } ?: ""))
        return clips(o.getJSONArray("items")) to o.str("next_cursor")
    }

    /** Published clips from one game, newest first. */
    suspend fun gameFeed(game: String, limit: Int = 12): List<Clip> =
        clips(get("/feed?limit=$limit&game=${q(game)}").getJSONArray("items"))

    suspend fun userClips(userId: String = this.userId): List<Clip> =
        clips(get("/users/$userId/clips?limit=50").getJSONArray("items"))

    suspend fun like(remoteId: String, liked: Boolean): JSONObject =
        if (liked) post("/clips/$remoteId/like") else delete("/clips/$remoteId/like")

    suspend fun view(remoteId: String) {
        post("/clips/$remoteId/view")
    }

    /** The current user's own clips by publish state: draft, ready, published, unpublished or all. */
    suspend fun myClips(state: String = "all"): List<Clip> =
        clips(get("/users/$userId/clips?state=$state&limit=100").getJSONArray("items"))

    /** Owner only. Draft -> published (shows in feed and search). */
    suspend fun publish(remoteId: String): Clip = clipFrom(post("/clips/$remoteId/publish"))

    /** Owner only. Back to draft (hidden from everyone else). */
    suspend fun unpublish(remoteId: String): Clip = clipFrom(post("/clips/$remoteId/unpublish"))

    /** Drafts only (published -> 409). Starts or reuses the server-side persona panel. */
    suspend fun startVirality(remoteId: String, platform: String, personas: Int): JSONObject =
        post("/clips/$remoteId/virality", JSONObject().put("platform", platform).put("persona_count", personas))

    /** Status/result of the virality check for the same options. */
    suspend fun virality(remoteId: String, platform: String, personas: Int): JSONObject =
        get("/clips/$remoteId/virality?platform=$platform&persona_count=$personas")

    suspend fun clip(remoteId: String): Clip = clipFrom(get("/clips/$remoteId"))

    // --- comments ---

    private fun commentFrom(o: JSONObject): Comment {
        val replies = o.optJSONArray("replies")
        return Comment(
            user = o.getJSONObject("user").getString("username"),
            text = o.getString("body"),
            likes = -1, // no comment likes on the backend
            age = o.str("created_at")?.let(::ageOf) ?: "",
            id = o.getString("id"),
            parentId = o.str("parent_id"),
            canDelete = o.optBoolean("can_delete", false),
            replies = if (replies == null) emptyList() else (0 until replies.length()).map { commentFrom(replies.getJSONObject(it)) }
        )
    }

    /** Top-level comments newest first, each with its replies (oldest first). */
    suspend fun comments(clipId: String): List<Comment> {
        val items = get("/clips/$clipId/comments?limit=50").getJSONArray("items")
        return (0 until items.length()).map { commentFrom(items.getJSONObject(it)) }
    }

    suspend fun postComment(clipId: String, body: String, parentId: String? = null): Comment =
        commentFrom(post("/clips/$clipId/comments", JSONObject().put("body", body).put("parent_id", parentId ?: JSONObject.NULL)))

    suspend fun deleteComment(commentId: String) {
        request("DELETE", "/comments/$commentId")
    }

    // --- follows & profiles ---

    suspend fun profile(id: String = userId): RemoteProfile {
        val o = get("/users/$id")
        return RemoteProfile(
            id = o.getString("id"),
            username = o.getString("username"),
            followerCount = o.optInt("follower_count"),
            followingCount = o.optInt("following_count"),
            publishedClipCount = o.optInt("published_clip_count"),
            followedByMe = !o.isNull("followed_by_me") && o.optBoolean("followed_by_me")
        )
    }

    /** Returns (following, follower_count). */
    suspend fun follow(targetId: String, follow: Boolean): Pair<Boolean, Int> {
        val o = if (follow) post("/users/$targetId/follow") else JSONObject(request("DELETE", "/users/$targetId/follow"))
        return o.optBoolean("following") to o.optInt("follower_count")
    }

    /** Clips from people the user follows (Home "Following" tab). */
    suspend fun followingFeed(limit: Int = 30): List<Clip> = clips(get("/feed/following?limit=$limit").getJSONArray("items"))

    // --- notifications ---

    suspend fun notifications(limit: Int = 50): List<RemoteNotification> {
        val items = get("/notifications?limit=$limit").getJSONArray("items")
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            val clip = o.optJSONObject("clip")
            RemoteNotification(
                id = o.getString("id"),
                type = o.getString("type"),
                message = o.getString("message"),
                actor = o.optJSONObject("actor")?.str("username"),
                clipId = clip?.str("id"),
                clipThumbnail = clip?.str("thumbnail_url"),
                read = o.optBoolean("read"),
                age = o.str("created_at")?.let(::ageOf) ?: ""
            )
        }
    }

    suspend fun unreadCount(): Int = get("/notifications/unread-count").optInt("unread_count")

    suspend fun markAllRead() {
        post("/notifications/read", JSONObject().put("all", true))
    }

    // --- AI creator pack (Gemini) ---

    /**
     * Starts (or reuses) the Gemini creator pack for a clip and polls until it's ready (~1-2 min
     * the first time, instant when cached). Returns the `pack` object.
     */
    suspend fun creatorPack(clipId: String, onPoll: suspend (Int) -> Unit = {}): JSONObject {
        var o = post("/clips/$clipId/creator-pack")
        var polls = 0
        while (o.str("status") != "ready") {
            if (o.str("status") == "failed") throw ApiException(500, "AI analysis failed: ${o.str("error") ?: "unknown error"}")
            if (polls >= 60) throw ApiException(504, "AI analysis is taking too long, try again in a minute")
            onPoll(polls)
            delay(5_000)
            polls++
            o = get("/clips/$clipId/creator-pack")
        }
        Logger.d("Creator pack ready for clip $clipId after $polls polls")
        return o.getJSONObject("pack")
    }

    /** Runs the same Gemini analysis on a local video file (a phone recording). Blocks ~1 min. */
    suspend fun analyzeVideo(file: File, onUpload: (Float) -> Unit = {}): JSONObject =
        withContext(Dispatchers.IO) {
            val boundary = "minedtv${System.currentTimeMillis()}"
            val head = "--$boundary\r\nContent-Disposition: form-data; name=\"video\"; filename=\"${file.name}\"\r\nContent-Type: video/mp4\r\n\r\n".toByteArray()
            val tail = "\r\n--$boundary--\r\n".toByteArray()
            val started = System.currentTimeMillis()
            val conn = (URL("$base/creator/analyze").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 300_000
                doOutput = true
                setRequestProperty("X-API-Key", BuildConfig.API_KEY)
                setRequestProperty("X-User-Id", userId)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setFixedLengthStreamingMode(head.size + file.length() + tail.size)
            }
            try {
                conn.outputStream.use { out ->
                    out.write(head)
                    streamFile(file, out, onUpload)
                    out.write(tail)
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                Logger.d("API POST /creator/analyze -> $code (${System.currentTimeMillis() - started}ms)")
                if (code !in 200..299) throw ApiException(code, "HTTP $code: $text")
                JSONObject(text)
            } finally {
                conn.disconnect()
            }
        }

    // --- uploading phone recordings ---

    /**
     * Uploads a recording as a new session: create upload -> PUT the file to storage -> complete.
     * The worker then processes it (about a minute) and it shows up in the user's clips as a draft.
     */
    suspend fun uploadRecording(file: File, game: String, title: String, onProgress: (Float) -> Unit = {}): String {
        val up = post(
            "/uploads",
            JSONObject()
                .put("filename", file.name)
                .put("content_type", "video/mp4")
                .put("game", game)
                .put("title", title.take(200))
                .put("size_bytes", file.length())
        )
        val sessionId = up.getString("session_id")
        withContext(Dispatchers.IO) {
            val conn = (URL(up.getString("upload_url")).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 300_000
                up.optJSONObject("headers")?.let { h -> h.keys().forEach { setRequestProperty(it, h.getString(it)) } }
                setFixedLengthStreamingMode(file.length())
            }
            try {
                conn.outputStream.use { streamFile(file, it, onProgress) }
                val code = conn.responseCode
                if (code !in 200..299) throw ApiException(code, "Upload failed: HTTP $code")
            } finally {
                conn.disconnect()
            }
        }
        post("/uploads/$sessionId/complete")
        Logger.i("Uploaded ${file.name} (${file.length() / 1024} KB) -> session $sessionId")
        return sessionId
    }

    /** Session processing status: uploading, queued, ..., ready, failed. */
    suspend fun sessionStatus(sessionId: String): String = get("/sessions/$sessionId").getString("status")

    private fun streamFile(file: File, out: java.io.OutputStream, onProgress: (Float) -> Unit) {
        val total = file.length().coerceAtLeast(1)
        var done = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                done += n
                onProgress(done.toFloat() / total)
            }
        }
    }

    /** Downloads a public URL (e.g. an MP4 export) to [target]. */
    suspend fun download(url: String, target: File, onProgress: suspend (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total != null) onProgress(done.toFloat() / total)
                    }
                }
            }
            onProgress(1f)
        } finally {
            conn.disconnect()
        }
    }

    /** Wakes the search GPU so the first search is fast (~12 s when cold). Fire and forget. */
    suspend fun warmup() {
        post("/warmup")
    }

    /** Saves a time range of a session as a clip the user owns. */
    suspend fun createClip(sessionId: String, startMs: Long, endMs: Long, title: String): Clip =
        clipFrom(
            post(
                "/clips",
                JSONObject()
                    .put("session_id", sessionId)
                    .put("start_ms", startMs)
                    .put("end_ms", endMs)
                    .put("title", title.take(200))
            )
        )

    /**
     * Starts (or reuses) a server-side MP4 export of a clip and polls until it's ready, returning
     * the MP4 URL. About a minute the first time; cached exports return immediately.
     */
    suspend fun exportMp4(clipId: String, onPoll: suspend (Int) -> Unit = {}): String {
        var o = post("/clips/$clipId/export")
        var polls = 0
        while (o.str("status") != "ready") {
            if (o.str("status") == "failed") throw ApiException(500, "Export failed: ${o.str("error") ?: "unknown error"}")
            if (polls >= 36) throw ApiException(504, "Export is taking too long, try again in a minute")
            onPoll(polls)
            delay(5_000)
            polls++
            o = get("/clips/$clipId/export")
        }
        Logger.d("Export ready for clip $clipId after $polls polls")
        return o.getString("url")
    }
}
