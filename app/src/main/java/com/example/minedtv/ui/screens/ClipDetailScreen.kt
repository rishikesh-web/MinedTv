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

@Composable
fun ClipDetailScreen(clip: Clip, onBack: () -> Unit, onClipClick: (Clip) -> Unit, onShare: (Clip) -> Unit) {
    var playing by remember(clip.id) { mutableStateOf(true) }
    var progress by remember(clip.id) { mutableFloatStateOf(0f) }
    var liked by remember(clip.id) { mutableStateOf(false) }
    var saved by remember(clip.id) { mutableStateOf(false) }
    var following by remember(clip.id) { mutableStateOf(false) }
    var draft by remember(clip.id) { mutableStateOf("") }
    val comments = remember(clip.id) { SampleData.comments.toMutableStateList() }
    val totalSeconds = clip.duration.substringAfter(":").toInt()

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
        // Player
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
            item {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    LikeButton(liked, clip.likes + if (liked) 1 else 0) {
                        liked = !liked
                        Logger.d("Clip ${if (liked) "liked" else "unliked"}: id=${clip.id}")
                    }
                    StatAction(Icons.Outlined.ChatBubbleOutline, formatCount(clip.comments + comments.size - SampleData.comments.size))
                    StatAction(Icons.Outlined.Share, "Share") { onShare(clip) }
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
                        Text("12.4K followers", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    if (following) {
                        OutlinedButton(onClick = {
                            following = false
                            Logger.d("Unfollowed ${clip.user}")
                        }) { Text("Following", color = Color.White) }
                    } else {
                        Button(onClick = {
                            following = true
                            Logger.d("Followed ${clip.user}")
                        }) { Text("Follow", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item {
                SectionHeader("More ${clip.game} clips", Modifier.padding(16.dp), action = null)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(SampleData.clips.filter { it.id != clip.id }.take(6)) { other ->
                        ClipGridItem(other, Modifier.width(170.dp)) { onClipClick(other) }
                    }
                }
            }
            item {
                SectionHeader("Comments", Modifier.padding(16.dp), action = null)
            }
            items(comments) { CommentRow(it) }
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
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add a comment…") },
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
                    if (draft.isNotBlank()) {
                        comments.add(0, Comment(SampleData.currentUser, draft.trim(), 0, "now"))
                        Logger.d("Comment posted on clip ${clip.id}: \"${draft.trim()}\"")
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
private fun CommentRow(comment: Comment) {
    var liked by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Avatar(comment.user, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(comment.user, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(comment.age, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Text(comment.text, fontSize = 14.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
}
