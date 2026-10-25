package com.example.minedtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minedtv.data.Clip
import com.example.minedtv.data.SampleData
import com.example.minedtv.ui.components.Avatar
import com.example.minedtv.ui.components.ClipGridItem
import com.example.minedtv.ui.components.PillRow
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.util.Logger
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ChevronRight
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.data.api.RemoteProfile
import com.example.minedtv.data.formatCount
import androidx.compose.ui.platform.LocalContext
import com.example.minedtv.recorder.LocalClips

@Composable
fun ProfileScreen(onClipClick: (Clip) -> Unit, onSearchClips: () -> Unit, onDashboard: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Live: your real published clips, drafts, and recordings still only on this phone.
    val live = MinedTvApi.isConfigured
    val context = LocalContext.current
    var remote by remember { mutableStateOf<List<Clip>?>(null) }
    val local by LocalClips.clips.collectAsState()
    LaunchedEffect(Unit) {
        LocalClips.refresh(context)
        if (live) runCatching { MinedTvApi.myClips("all") }
            .onSuccess { remote = it.distinctBy { c -> c.id } }
            .onFailure {
                Logger.e("My clips failed", it)
                remote = emptyList()
            }
    }
    val tabs = if (live) listOf("Published", "Drafts", "Nuggets", "Phone") else listOf("Clips", "Highlights", "Liked")
    val clips = if (live) when (tab) {
        // Videos and reels are split like YouTube: reels get their own tab (published and drafts).
        0 -> remote.orEmpty().filter { it.publishState == "published" && !it.isReel }
        1 -> remote.orEmpty().filter { it.publishState != "published" && !it.isReel }
        2 -> remote.orEmpty().filter { it.isReel }
        else -> local
    } else when (tab) {
        0 -> SampleData.myClips
        1 -> SampleData.myClips.sortedByDescending { it.likes }.take(4)
        else -> SampleData.clips.filter { it.user != SampleData.currentUser }.take(6)
    }

    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ProfileHeader(scrollOffset = {
                if (gridState.firstVisibleItemIndex == 0) gridState.firstVisibleItemScrollOffset else 0
            })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            // Entry to the creator dashboard (published vs drafts, stats, virality).
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        Logger.d("Profile: open creator dashboard")
                        onDashboard()
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Insights, null, tint = Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Creator dashboard", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Views over time, drafts and virality predictions",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            // Entry to semantic moment search over the user's own clips.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable {
                        Logger.d("Profile: open clip search")
                        onSearchClips()
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Search inside your clips", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Try \"fortnite headshot\" to jump to the moment",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            PillRow(tabs, tab, scrollable = true, onSelect = {
                Logger.d("Profile tab: ${tabs[it]}")
                tab = it
            })
        }
        if (live && (tab < 3 && remote == null)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
            }
        } else if (live && clips.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    when (tab) {
                        0 -> "Nothing published yet. Publish a draft to share it on MinedIt."
                        1 -> "No drafts. Recordings you upload land here first."
                        2 -> "No nuggets yet. Share a clip, pick MinedIt Nuggets, and post it."
                        else -> "No recordings on this phone yet. Start one from the Clip tab."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        items(clips, key = { "${tab}_${it.id}" }) { clip ->
            ClipGridItem(clip) { onClipClick(clip) }
        }
    }
}

@Composable
private fun ProfileHeader(scrollOffset: () -> Int) {
    val user = SampleData.currentUser
    // Real counts from the backend when configured; sample numbers otherwise.
    var profile by remember { mutableStateOf<RemoteProfile?>(null) }
    var totalViews by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        if (!MinedTvApi.isConfigured) return@LaunchedEffect
        runCatching { MinedTvApi.profile() }.onSuccess { profile = it }.onFailure { Logger.e("Profile failed", it) }
        runCatching { MinedTvApi.myClips("published") }.onSuccess { list -> totalViews = list.sumOf { it.views } }
    }
    Column {
        Box(Modifier.fillMaxWidth().height(176.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    // Parallax: banner drifts down at half speed and fades as you scroll.
                    .graphicsLayer {
                        val offset = scrollOffset().toFloat()
                        translationY = offset * 0.5f
                        alpha = 1f - (offset / 600f).coerceIn(0f, 0.7f)
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFFC53D), Color(0xFFB2361E), Color(0xFF3B0A45))))
            ) {
                Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Share, "Share", tint = Color.White) }
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Settings, "Settings", tint = Color.White) }
                }
            }
            Avatar(
                user, 92.dp,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp)
                    .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(user, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Verified, "Verified", tint = Gold, modifier = Modifier.size(20.dp))
        }
        Text("@${user.lowercase()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Text("Valorant & Apex grinder 🎯 Clipping the cracked moments only.", fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val p = profile
            Stat(p?.let { formatCount(it.publishedClipCount) } ?: "128", "Clips")
            Stat(p?.let { formatCount(it.followerCount) } ?: "24.5K", "Followers")
            Stat(p?.let { formatCount(it.followingCount) } ?: "312", "Following")
            Stat(totalViews?.let { formatCount(it) } ?: if (p != null) "…" else "1.2M", "Views")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {}, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Text("Edit Profile", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                Text("Share Profile", color = Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}
