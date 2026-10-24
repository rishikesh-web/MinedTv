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

    val clips = remember(feed, seed) {
        if (feed == 0 && seed == 0) SampleData.clips else SampleData.clips.shuffled(Random(feed * 31 + seed))
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
                            Icon(Icons.Outlined.Notifications, "Notifications")
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .background(LiveRed, CircleShape)
                            )
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
            if (loading) {
                items(3) { ClipCardSkeleton() }
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
