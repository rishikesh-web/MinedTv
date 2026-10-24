package com.example.minedtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Search
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
import com.example.minedtv.data.Game
import com.example.minedtv.data.SampleData
import com.example.minedtv.data.formatCount
import com.example.minedtv.ui.components.ClipGridItem
import com.example.minedtv.ui.components.ScreenTitle
import com.example.minedtv.ui.components.SectionHeader
import com.example.minedtv.ui.theme.Gold
import kotlinx.coroutines.delay
import com.example.minedtv.util.Logger

@Composable
fun ExploreScreen(onClipClick: (Clip) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    // Debounced so we log the query, not every keystroke.
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        delay(500)
        Logger.d("Explore search: \"$query\"")
    }
    val results = SampleData.clips.filter {
        query.isBlank() ||
            it.title.contains(query, ignoreCase = true) ||
            it.game.contains(query, ignoreCase = true) ||
            it.user.contains(query, ignoreCase = true)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle("Explore", Modifier.offset(x = (-16).dp))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search clips, games, creators") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Gold
                )
            )
        }
        if (query.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Popular Games") }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(SampleData.games) { game -> GameTile(game) {
                        Logger.d("Explore game tapped: ${game.name}")
                        query = game.name
                    } }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(
                if (query.isBlank()) "Trending Clips" else "${results.size} results",
                action = null
            )
        }
        items(results, key = { it.id }) { clip ->
            ClipGridItem(clip) { onClipClick(clip) }
        }
    }
}

@Composable
private fun GameTile(game: Game, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 120.dp, height = 160.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(game.colors))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Icon(
            Icons.Filled.SportsEsports, null,
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(56.dp)
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(10.dp)
        ) {
            Text(game.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text("${formatCount(game.clips)} clips", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}
