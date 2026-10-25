package com.example.minedtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minedtv.data.Clip
import com.example.minedtv.data.Comment
import com.example.minedtv.data.SampleData
import com.example.minedtv.data.formatCount
import com.example.minedtv.ui.components.Avatar
import com.example.minedtv.ui.components.ClipGridItem
import com.example.minedtv.ui.components.GameChip
import com.example.minedtv.ui.components.SectionHeader
import com.example.minedtv.ui.components.StatAction
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.LiveRed
import kotlinx.coroutines.delay
import com.example.minedtv.util.Logger
import com.example.minedtv.ui.components.LikeButton
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.ui.components.HlsPlayer
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Flag
import com.example.minedtv.ui.moderation.ContentCheckScreen
import com.example.minedtv.data.moderation.ContentCheck
import com.example.minedtv.recorder.LocalClips
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Lock

@Composable
fun ClipDetailScreen(clip: Clip, onBack: () -> Unit, onClipClick: (Clip) -> Unit, onShare: (Clip) -> Unit) {
    var playing by remember(clip.id) { mutableStateOf(true) }
    var progress by remember(clip.id) { mutableFloatStateOf(0f) }
    var liked by remember(clip.id) { mutableStateOf(clip.likedByMe) }
    val scope = rememberCoroutineScope()
    var publishState by remember(clip.id) { mutableStateOf(clip.publishState) }
    var publishBusy by remember(clip.id) { mutableStateOf(false) }

    // Count a view on the backend for real clips.
    LaunchedEffect(clip.id) {
        clip.remoteId?.let { id -> runCatching { MinedTvApi.view(id) }.onFailure { Logger.e("View failed", it) } }
    }
    var saved by remember(clip.id) { mutableStateOf(false) }
    var following by remember(clip.id) { mutableStateOf(false) }
    var followerCount by remember(clip.id) { mutableStateOf<Int?>(null) }
    var draft by remember(clip.id) { mutableStateOf("") }
    // Backend clips use real comments and follows; sample clips keep the local demo behaviour.
    val isRemote = clip.remoteId != null && MinedTvApi.isConfigured
    val comments = remember(clip.id) { (if (isRemote) emptyList() else SampleData.comments).toMutableStateList() }
    var commentCount by remember(clip.id) { mutableIntStateOf(clip.comments) }
    var replyingTo by remember(clip.id) { mutableStateOf<Comment?>(null) }
    val isOwn = clip.localPath != null || (clip.userId != null && clip.userId == MinedTvApi.userId)
    // Content check (moderation verdict + flagged moments) for the owner's upload.
    var showCheck by remember(clip.id) { mutableStateOf(false) }
    var confirmPublish by remember(clip.id) { mutableStateOf(false) }
    // Phone recordings: upload to the backend, where they're processed into a draft.
    var uploadSession by remember(clip.id) { mutableStateOf(clip.localPath?.let { LocalClips.uploadedSession(it) }) }
    var uploadProgress by remember(clip.id) { mutableStateOf<Float?>(null) }
    var uploadStatus by remember(clip.id) { mutableStateOf<String?>(null) }
    var uploadReason by remember(clip.id) { mutableStateOf<String?>(null) }
    var uploadError by remember(clip.id) { mutableStateOf<String?>(null) }
    val checkSession = uploadSession ?: clip.sessionId?.takeIf { isOwn && clip.remoteId != null }
    if (showCheck && checkSession != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCheck = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ContentCheckScreen(
                load = {
                    ContentCheck.fromSession(
                        MinedTvApi.get("/sessions/$checkSession"), clip.title,
                        fallbackPlayUrl = clip.playlistUrl
                    )
                },
                onClose = { showCheck = false }
            )
        }
    }

    LaunchedEffect(uploadSession) {
        val sid = uploadSession ?: return@LaunchedEffect
        while (true) {
            runCatching { MinedTvApi.get("/sessions/$sid") }.onSuccess { s ->
                uploadStatus = s.optString("status")
                uploadReason = s.optString("moderation_reason").takeIf { it.isNotBlank() && it != "null" }
                    ?: s.optString("error").takeIf { it.isNotBlank() && it != "null" }
            }
            // blocked = moderation stopped processing (severe content); it's finished, not pending.
            if (uploadStatus == "ready" || uploadStatus == "failed" || uploadStatus == "blocked") break
            kotlinx.coroutines.delay(10_000)
        }
        Logger.d("Upload session $sid: $uploadStatus")
    }
    // "More from this game": real published clips when the backend is on.
    var moreClips by remember(clip.id) { mutableStateOf<List<Clip>?>(null) }
    LaunchedEffect(clip.id) {
        if (MinedTvApi.isConfigured) runCatching { MinedTvApi.gameFeed(clip.game) }
            .onSuccess { list -> moreClips = list.filter { it.remoteId != clip.remoteId } }
            .onFailure { Logger.e("Game feed failed", it) }
    }
    val totalSeconds = clip.duration.substringAfter(":").toInt()

    LaunchedEffect(clip.id) {
        if (!isRemote) return@LaunchedEffect
        runCatching { MinedTvApi.comments(clip.remoteId!!) }
            .onSuccess {
                comments.clear()
                comments.addAll(it)
                Logger.d("Loaded ${it.size} comments for ${clip.remoteId}")
            }
            .onFailure { Logger.e("Comments failed", it) }
        clip.userId?.takeIf { !isOwn }?.let { uid ->
            runCatching { MinedTvApi.profile(uid) }.onSuccess {
                following = it.followedByMe
                followerCount = it.followerCount
            }
        }
    }

    fun toggleFollow(follow: Boolean) {
        following = follow
        Logger.d("${if (follow) "Followed" else "Unfollowed"} ${clip.user}")
        val uid = clip.userId
        if (isRemote && uid != null) scope.launch {
            runCatching { MinedTvApi.follow(uid, follow) }
                .onSuccess { (now, count) ->
                    following = now
                    followerCount = count
                }
                .onFailure {
                    following = !follow
                    Logger.e("Follow failed", it)
                }
        }
    }

    fun deleteComment(c: Comment) {
        val id = c.id ?: return
        scope.launch {
            runCatching { MinedTvApi.deleteComment(id) }
                .onSuccess {
                    if (c.parentId == null) {
                        comments.remove(c)
                        commentCount -= 1 + c.replies.size
                    } else {
                        val i = comments.indexOfFirst { it.id == c.parentId }
                        if (i >= 0) comments[i] = comments[i].copy(replies = comments[i].replies - c)
                        commentCount -= 1
                    }
                    Logger.d("Deleted comment $id")
                }
                .onFailure { Logger.e("Delete comment failed", it) }
        }
    }

    // Fake playback progress.
    LaunchedEffect(playing, clip.id) {
        while (playing) {
            delay(100)
            progress = (progress + 0.1f / totalSeconds).let { if (it >= 1f) 0f else it }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow touches so they don't reach the screen underneath.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
            .imePadding()
    ) {
        // Player: real HLS video for backend clips, placeholder for sample data.
        if (clip.playlistUrl != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                HlsPlayer(clip.playlistUrl, Modifier.fillMaxSize())
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            }
        } else {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Brush.linearGradient(clip.colors))
                .clickable {
                    playing = !playing
                    Logger.d("Player ${if (playing) "play" else "pause"} (tap video)")
                }
        ) {
            Icon(
                Icons.Filled.SportsEsports, null,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(120.dp)
            )
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            if (!playing) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            GameChip(clip.game, Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                playing = !playing
                Logger.d("Player ${if (playing) "play" else "pause"} (button)")
            }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play")
            }
            Slider(
                value = progress,
                onValueChange = { progress = it },
                onValueChangeFinished = { Logger.d("Seeked to ${(progress * totalSeconds).toInt()}s") },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold)
            )
            Spacer(Modifier.width(8.dp))
            val elapsed = (progress * totalSeconds).toInt()
            Text("0:%02d / %s".format(elapsed, clip.duration), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }

        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(clip.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${formatCount(clip.views)} views · ${clip.age} ago",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
            // Owner controls: publish state of your own backend clip.
            if (clip.localPath != null && MinedTvApi.isConfigured) item {
                val sid = uploadSession
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (sid != null) Icons.Filled.CloudDone else Icons.Filled.PhoneAndroid, null,
                        tint = if (sid != null) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(
                            when {
                                uploadProgress != null -> "Uploading… ${((uploadProgress ?: 0f) * 100).toInt()}%"
                                sid == null -> "Only on this phone"
                                uploadStatus == "ready" -> "Uploaded · in your drafts on MinedIt"
                                uploadStatus == "failed" -> "Uploaded, but processing failed"
                                uploadStatus == "blocked" -> "Blocked by moderation" + (uploadReason?.let { ": $it" } ?: "")
                                else -> "Uploaded · processing (about a minute)…"
                            },
                            fontSize = 13.sp
                        )
                        uploadError?.let { Text(it, fontSize = 11.sp, color = LiveRed) }
                        uploadProgress?.let {
                            LinearProgressIndicator(
                                progress = { it }, color = Gold,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                    }
                    if (sid == null && uploadProgress == null) {
                        TextButton(onClick = {
                            uploadProgress = 0f
                            uploadError = null
                            scope.launch {
                                runCatching {
                                    MinedTvApi.uploadRecording(java.io.File(clip.localPath), clip.game, clip.title) { uploadProgress = it }
                                }.onSuccess {
                                    LocalClips.markUploaded(clip.localPath, it)
                                    uploadSession = it
                                }.onFailure {
                                    Logger.e("Upload failed", it)
                                    uploadError = it.message ?: "Upload failed"
                                }
                                uploadProgress = null
                            }
                        }) { Text("Upload", color = Gold, fontWeight = FontWeight.Bold) }
                    }
                    if (sid != null) {
                        TextButton(onClick = {
                            Logger.d("Open content check for upload $sid")
                            showCheck = true
                        }) { Text("Content check", color = Gold, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            if (clip.remoteId != null && clip.publishState != null && isOwn) item {
                val published = publishState == "published"
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (published) Icons.Filled.Public else Icons.Filled.Lock, null,
                        tint = if (published) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (published) "Published on MinedIt" else "Draft · only you can see this",
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    fun togglePublish() {
                        publishBusy = true
                        scope.launch {
                            runCatching {
                                if (published) MinedTvApi.unpublish(clip.remoteId) else MinedTvApi.publish(clip.remoteId)
                            }.onSuccess {
                                publishState = it.publishState
                                Logger.i("Clip ${clip.remoteId} is now ${it.publishState}")
                            }.onFailure { Logger.e("Publish toggle failed", it) }
                            publishBusy = false
                        }
                    }
                    TextButton(
                        enabled = !publishBusy,
                        // 18+ clips need an explicit second step, like the web app.
                        onClick = { if (!published && clip.ageRestricted) confirmPublish = true else togglePublish() }
                    ) { Text(if (published) "Unpublish" else "Publish", color = Gold, fontWeight = FontWeight.Bold) }
                    if (confirmPublish) AlertDialog(
                        onDismissRequest = { confirmPublish = false },
                        title = { Text("Publish as 18+?") },
                        text = {
                            Text(
                                "This clip was tagged 18+" + (clip.moderationReason?.let { ": $it" } ?: ".") +
                                    " Only signed-in adults will see it in feeds and be able to open it."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmPublish = false
                                Logger.i("Publishing 18+ clip ${clip.remoteId} after confirmation")
                                togglePublish()
                            }) { Text("Publish 18+", color = Gold) }
                        },
                        dismissButton = { TextButton(onClick = { confirmPublish = false }) { Text("Keep private") } }
                    )
                }
            }
            // Owner's view of the moderation verdict, with the flagged moments.
            if (clip.remoteId != null && clip.sessionId != null && isOwn) item {
                val (label, color) = when (clip.moderationSeverity) {
                    "mild" -> "Tagged 18+" + (if (clip.moderationViolationCount > 0) " · ${clip.moderationViolationCount} flagged moments" else "") to Color(0xFFFAB219)
                    "severe" -> "Blocked by moderation" to LiveRed
                    else -> "Content check: clean" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { showCheck = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Flag, null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontSize = 13.sp, color = if (clip.moderationSeverity == null || clip.moderationSeverity == "none") MaterialTheme.colorScheme.onSurfaceVariant else Color.White, modifier = Modifier.weight(1f))
                    Text("View", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    val baseLikes = clip.likes - if (clip.likedByMe) 1 else 0
                    LikeButton(liked, baseLikes + if (liked) 1 else 0) {
                        liked = !liked
                        clip.remoteId?.let { id ->
                            val now = liked
                            scope.launch { runCatching { MinedTvApi.like(id, now) }.onFailure { Logger.e("Like failed", it) } }
                        }
                        Logger.d("Clip ${if (liked) "liked" else "unliked"}: id=${clip.id}")
                    }
                    StatAction(Icons.Outlined.ChatBubbleOutline, formatCount(if (isRemote) commentCount else clip.comments + comments.size - SampleData.comments.size))
                    // Export and AI edits are owner-only on the backend.
                    if (MinedTvApi.owns(clip)) StatAction(Icons.Outlined.Share, "Share") { onShare(clip) }
                    StatAction(
                        if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        if (saved) "Saved" else "Save",
                        tint = if (saved) Gold else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        saved = !saved
                        Logger.d("Clip ${if (saved) "saved" else "unsaved"}: id=${clip.id}")
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(clip.user, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(clip.user, fontWeight = FontWeight.Bold)
                        val followers = followerCount?.let { "${formatCount(it)} follower${if (it == 1) "" else "s"}" }
                            ?: if (isRemote) "" else "12.4K followers"
                        if (isOwn) Text("You", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        else if (followers.isNotEmpty()) Text(followers, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    if (!isOwn) {
                        if (following) {
                            OutlinedButton(onClick = { toggleFollow(false) }) { Text("Following", color = Color.White) }
                        } else {
                            Button(onClick = { toggleFollow(true) }) { Text("Follow", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            // Live: only real clips from this game (hidden when there are none); demo: sample clips.
            val more = if (MinedTvApi.isConfigured) moreClips.orEmpty()
            else SampleData.clips.filter { it.id != clip.id && it.game == clip.game }.ifEmpty { SampleData.clips.filter { it.id != clip.id } }.take(6)
            if (more.isNotEmpty()) item {
                SectionHeader("More ${clip.game} clips", Modifier.padding(16.dp), action = null)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(more, key = { it.id }) { other ->
                        ClipGridItem(other, Modifier.width(170.dp)) { onClipClick(other) }
                    }
                }
            }
            item {
                SectionHeader(if (isRemote) "Comments ($commentCount)" else "Comments", Modifier.padding(16.dp), action = null)
                if (isRemote && comments.isEmpty()) {
                    Text(
                        "No comments yet. Be the first.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(comments) { c ->
                CommentRow(c, onReply = if (isRemote) ({ replyingTo = it }) else null, onDelete = ::deleteComment)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // Comment input
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(SampleData.currentUser, 32.dp)
            Spacer(Modifier.width(8.dp))
            replyingTo?.let {
                IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "Cancel reply", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(replyingTo?.let { "Reply to ${it.user}…" } ?: "Add a comment…") },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Gold,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            IconButton(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty() && isRemote) {
                        val parent = replyingTo
                        draft = ""
                        replyingTo = null
                        scope.launch {
                            runCatching { MinedTvApi.postComment(clip.remoteId!!, text, parent?.id) }
                                .onSuccess { posted ->
                                    if (parent == null) comments.add(0, posted)
                                    else {
                                        val i = comments.indexOfFirst { it.id == parent.id }
                                        if (i >= 0) comments[i] = comments[i].copy(replies = comments[i].replies + posted)
                                    }
                                    commentCount++
                                    Logger.d("Comment posted on clip ${clip.remoteId}${if (parent != null) " (reply)" else ""}")
                                }
                                .onFailure {
                                    draft = text
                                    Logger.e("Comment failed", it)
                                }
                        }
                    } else if (text.isNotEmpty()) {
                        comments.add(0, Comment(SampleData.currentUser, text, 0, "now"))
                        Logger.d("Comment posted on clip ${clip.id}: \"$text\"")
                        draft = ""
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Gold)
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    onReply: ((Comment) -> Unit)? = null,
    onDelete: (Comment) -> Unit = {},
    isReply: Boolean = false
) {
    var liked by remember { mutableStateOf(false) }
    Column {
    Row(Modifier.padding(start = if (isReply) 58.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
        Avatar(comment.user, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(comment.user, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(comment.age, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Text(comment.text, fontSize = 14.sp)
            if (onReply != null || comment.canDelete) {
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (onReply != null && !isReply) {
                        Text(
                            "Reply", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onReply(comment) }
                        )
                    }
                    if (comment.canDelete) {
                        Text(
                            "Delete", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = LiveRed.copy(alpha = 0.8f),
                            modifier = Modifier.clickable { onDelete(comment) }
                        )
                    }
                }
            }
        }
        // Backend comments have no likes (likes = -1).
        if (comment.likes >= 0) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                "Like",
                tint = if (liked) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { liked = !liked }
            )
            Text(
                formatCount(comment.likes + if (liked) 1 else 0),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    comment.replies.forEach { CommentRow(it, onReply, onDelete, isReply = true) }
    }
}
