package com.example.minedtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minedtv.data.Clip
import com.example.minedtv.data.SampleData
import com.example.minedtv.ui.components.Avatar
import com.example.minedtv.ui.components.PillRow
import com.example.minedtv.ui.components.ScreenTitle
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.util.Logger
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.data.api.RemoteNotification
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch

@Composable
fun InboxScreen(onClipClick: (Clip) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Real activity from the backend; sample activity when no backend is configured.
    val live = MinedTvApi.isConfigured
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<RemoteNotification>?>(null) }
    LaunchedEffect(Unit) {
        if (!live) return@LaunchedEffect
        runCatching { MinedTvApi.notifications() }
            .onSuccess { list ->
                notifications = list
                Logger.d("Inbox: ${list.size} notifications, ${list.count { !it.read }} unread")
                // Opening the Inbox marks everything read (clears the bell badge).
                if (list.any { !it.read }) runCatching { MinedTvApi.markAllRead() }
            }
            .onFailure {
                Logger.e("Notifications failed", it)
                notifications = emptyList()
            }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenTitle("Inbox") {
                IconButton(onClick = {}) { Icon(Icons.Outlined.Edit, "New message") }
            }
        }
        item {
            PillRow(
                listOf("Activity", "Messages"), tab, {
                    Logger.d("Inbox tab: ${if (it == 0) "Activity" else "Messages"}")
                    tab = it
                },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (tab == 0 && live) {
            val list = notifications
            when {
                list == null -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
                }
                list.isEmpty() -> item {
                    Text(
                        "No activity yet. Likes, comments, follows and finished virality checks show up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                else -> items(list, key = { it.id }) { n ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                Logger.d("Notification tapped: ${n.type} ${n.clipId}")
                                n.clipId?.let { id ->
                                    scope.launch { runCatching { MinedTvApi.clip(id) }.onSuccess(onClipClick) }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(n.actor ?: "MinedIt", 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            buildAnnotatedString {
                                append(n.message)
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("  ${n.age}") }
                            },
                            fontSize = 14.sp,
                            fontWeight = if (n.read) FontWeight.Normal else FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (!n.read) {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(8.dp).background(Gold, CircleShape))
                        }
                        n.clipThumbnail?.let {
                            Spacer(Modifier.width(12.dp))
                            AsyncImage(
                                it, null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 64.dp, height = 36.dp).clip(RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
            }
        } else if (tab == 0) {
            items(SampleData.notifications) { n ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            Logger.d("Notification tapped: ${n.user} ${n.text}")
                            n.clip?.let(onClipClick)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(n.user, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(n.user) }
                            append(" ${n.text} ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(n.time) }
                        },
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    if (n.clip != null) {
                        Box(
                            Modifier
                                .size(width = 64.dp, height = 36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(n.clip.colors))
                        )
                    } else {
                        Button(
                            onClick = {},
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.height(32.dp)
                        ) { Text("Follow back", fontSize = 12.sp) }
                    }
                }
            }
        } else {
            items(SampleData.conversations) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { Logger.d("Conversation tapped: ${c.user}") }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(c.user, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.user, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            c.lastMessage,
                            color = if (c.unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(c.time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        if (c.unread) {
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.size(8.dp).background(Gold, CircleShape))
                        }
                    }
                }
            }
        }
    }
}
