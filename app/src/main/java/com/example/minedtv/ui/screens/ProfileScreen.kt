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

@Composable
fun ProfileScreen(onClipClick: (Clip) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val clips = when (tab) {
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
            val tabs = listOf("Clips", "Highlights", "Liked")
            PillRow(tabs, tab, {
                Logger.d("Profile tab: ${tabs[it]}")
                tab = it
            })
        }
        items(clips, key = { "${tab}_${it.id}" }) { clip ->
            ClipGridItem(clip) { onClipClick(clip) }
        }
    }
}

@Composable
private fun ProfileHeader(scrollOffset: () -> Int) {
    val user = SampleData.currentUser
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
            Stat("128", "Clips")
            Stat("24.5K", "Followers")
            Stat("312", "Following")
            Stat("1.2M", "Views")
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
