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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.minedtv.data.search.SearchScope
import com.example.minedtv.data.search.formatMs
import com.example.minedtv.ui.search.SearchViewModel

@Composable
fun ExploreScreen(onClipClick: (Clip) -> Unit) {
    // Global semantic search; the ViewModel debounces and logs queries.
    val vm: SearchViewModel = viewModel(key = "search-global") { SearchViewModel(SearchScope.Global) }
    val state by vm.state.collectAsState()
    val query = state.query
    val hits = state.response?.hits.orEmpty()

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
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search clips, games, moments") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Close, "Clear") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.commit() }),
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
                        vm.commit(game.name)
                    } }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Trending Clips", action = null) }
            items(SampleData.clips, key = { it.id }) { clip ->
                ClipGridItem(clip) { onClipClick(clip) }
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    if (state.loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold, trackColor = Color.Transparent)
                        Spacer(Modifier.height(8.dp))
                    }
                    val response = state.response
                    SectionHeader(
                        when {
                            state.error != null -> "Search failed"
                            response == null -> "Searching…"
                            else -> "${hits.size} results"
                        },
                        action = if (state.error != null) "Retry" else null,
                        onAction = vm::retry
                    )
                    val terms = response?.interpretation?.let { it.games + it.events }.orEmpty()
                    if (terms.isNotEmpty()) {
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Gold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Understood as: ${terms.joinToString(" · ")}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            items(hits, key = { it.clip.id }) { hit ->
                Column {
                    ClipGridItem(hit.clip) { onClipClick(hit.clip) }
                    // Where in the video the match happens, since titles may not mention it.
                    hit.moments.firstOrNull()?.let { m ->
                        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Gold, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${m.label} at ${formatMs(m.startMs)}",
                                color = Gold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
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
