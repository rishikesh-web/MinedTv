package com.example.minedtv.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.minedtv.data.Clip
import com.example.minedtv.data.SampleData
import com.example.minedtv.ui.components.ClipCard
import com.example.minedtv.ui.components.ClipCardSkeleton
import com.example.minedtv.ui.components.MinedTvLogo
import com.example.minedtv.ui.components.PillRow
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.LiveRed
import com.example.minedtv.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.example.minedtv.data.api.MinedTvApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

// Only show the skeleton on the very first load, not every time the tab is revisited.
private var feedLoadedOnce = false

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(onClipClick: (Clip) -> Unit, onSearch: () -> Unit, onNotifications: () -> Unit) {
    val feeds = listOf("For You", "Following", "Trending", "New")
    var feed by rememberSaveable { mutableIntStateOf(0) }
    var seed by rememberSaveable { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(!feedLoadedOnce) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        if (loading) {
            delay(1200)
            loading = false
            feedLoadedOnce = true
        }
    }

    // Live: every tab is built from real published clips (sample clips only without a backend).
    var remote by remember { mutableStateOf<List<Clip>>(emptyList()) }
    LaunchedEffect(seed) {
        if (!MinedTvApi.isConfigured) return@LaunchedEffect
        runCatching { MinedTvApi.feed(limit = 50).first }
            .onSuccess {
                remote = it
                Logger.d("Feed: ${it.size} clips from backend")
            }
            .onFailure { Logger.e("Feed failed", it) }
    }
    // "Following" tab: real clips from creators the user follows (no sample filler).
    val live = MinedTvApi.isConfigured
    var following by remember { mutableStateOf<List<Clip>?>(null) }
    LaunchedEffect(feed, seed) {
        if (!live || feed != 1) return@LaunchedEffect
        runCatching { MinedTvApi.followingFeed() }
            .onSuccess {
                following = it
                Logger.d("Following feed: ${it.size} clips")
            }
            .onFailure { Logger.e("Following feed failed", it) }
    }
    // Unread notifications for the bell badge.
    var unread by remember { mutableIntStateOf(0) }
    LaunchedEffect(seed) {
        if (live) runCatching { MinedTvApi.unreadCount() }.onSuccess { unread = it }
    }
    val clips = remember(feed, seed, remote, following) {
        if (live && feed == 1) following.orEmpty()
        else if (live && remote.isNotEmpty()) when (feed) {
            // Trending: engagement score; comments and likes count more than views.
            2 -> remote.sortedByDescending { it.views + it.likes * 3 + it.comments * 5 }
            // New: most recently published first.
            3 -> remote.sortedByDescending { it.publishedAt ?: "" }
            // For You: a fresh mix on every refresh.
            else -> if (seed == 0) remote else remote.shuffled(Random(seed))
        }
        else remote + if (feed == 0 && seed == 0) SampleData.clips else SampleData.clips.shuffled(Random(feed * 31 + seed))
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            Logger.d("Home pull to refresh")
            scope.launch {
                refreshing = true
                delay(1200)
                seed++
                refreshing = false
            }
        },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                color = Gold
            )
        }
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MinedTvLogo(Modifier.weight(1f))
                    IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search") }
                    IconButton(onClick = onNotifications) {
                        Box {
                            Icon(Icons.Outlined.Notifications, if (unread > 0) "$unread unread notifications" else "Notifications")
                            if (unread > 0 || !live) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                        .background(LiveRed, CircleShape)
                                        .padding(horizontal = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (live) Text(if (unread > 9) "9+" else "$unread", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
            stickyHeader {
                PillRow(
                    feeds, feed, {
                        Logger.d("Home feed selected: ${feeds[it]}")
                        feed = it
                    },
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (loading || (live && feed == 1 && following == null)) {
                items(3) { ClipCardSkeleton() }
            } else if (live && feed == 1 && clips.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet. Follow creators from their clips and their new clips will show up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                items(clips, key = { it.id }) { clip ->
                    Column(Modifier.animateItem()) {
                        ClipCard(clip) { onClipClick(clip) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
