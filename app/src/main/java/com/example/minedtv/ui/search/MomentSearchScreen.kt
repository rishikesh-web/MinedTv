package com.example.minedtv.ui.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.minedtv.data.Clip
import com.example.minedtv.data.MyLibrary
import com.example.minedtv.data.search.Moment
import com.example.minedtv.data.search.SearchScope
import com.example.minedtv.data.search.formatMs
import com.example.minedtv.data.share.durationSeconds
import com.example.minedtv.data.share.formatDuration
import com.example.minedtv.ui.components.ClipThumbnail
import com.example.minedtv.ui.components.Pill
import com.example.minedtv.ui.components.PillRow
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.OnlineGreen
import com.example.minedtv.util.Logger
import kotlinx.coroutines.delay
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.ui.components.HlsPlayer
import kotlinx.coroutines.launch

private val Suggestions = listOf(
    "fortnite headshot", "valorant clutch", "sniper shots", "apex revive",
    "cs2 ace", "funny moments", "victory", "minecraft boss fight", "rocket league goals"
)

/** Semantic search over the user's own clips; results are clips + timestamps of matching moments. */
@Composable
fun MomentSearchScreen(onClose: () -> Unit, onShareMoment: (Clip) -> Unit) {
    val vm: SearchViewModel = viewModel(key = "search-library") { SearchViewModel(SearchScope.Library) }
    val state by vm.state.collectAsState()
    val response = state.response
    var view by remember { mutableIntStateOf(0) }
    // Flat, ordered list of every result moment, so the player can step through all of them.
    val playlist = remember(response) {
        response?.hits.orEmpty().flatMap { hit -> hit.moments.map { hit.clip to it } }
    }
    var playing by remember { mutableStateOf<Int?>(null) }
    // Keeps the last shown moment so the player doesn't jump to moment 1 while sliding out.
    var lastPlaying by remember { mutableIntStateOf(0) }
    playing?.let { lastPlaying = it }
    val focus = remember { FocusRequester() }
    // The user's real clips from the backend (search runs against these); sample library as fallback.
    var library by remember { mutableStateOf(if (MinedTvApi.isConfigured) emptyList() else MyLibrary.clips) }
    LaunchedEffect(Unit) {
        if (!MinedTvApi.isConfigured) return@LaunchedEffect
        runCatching { MinedTvApi.userClips() }
            .onSuccess { library = it }
            .onFailure {
                Logger.e("Library load failed", it)
                library = MyLibrary.clips
            }
    }

    LaunchedEffect(Unit) { if (state.query.isEmpty()) focus.requestFocus() }
    BackHandler {
        if (playing != null) playing = null else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Search your clips", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${library.size} clips indexed · finds moments inside videos",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focus),
                placeholder = { Text("Try \"fortnite headshot\"") },
                leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, tint = Gold) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
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
            Box(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 16.dp)) {
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold, trackColor = Color.Transparent)
                }
            }

            when {
                state.error != null -> CenteredMessage(Icons.Filled.ErrorOutline, "Search failed", state.error ?: "") {
                    TextButton(onClick = vm::retry) { Text("Retry", color = Gold) }
                }
                state.query.isBlank() -> IdleContent(state.recent, library) { vm.commit(it) }
                response == null -> Unit
                response.hits.isEmpty() && !state.loading -> CenteredMessage(
                    Icons.Filled.SearchOff,
                    "No moments found",
                    "Nothing in your clips matched \"${response.query}\". Try an event like headshot, clutch or revive."
                ) {}
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    "${response.totalMoments} moments in ${response.hits.size} clips",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                InterpretationRow(response.interpretation.games, response.interpretation.events)
                                Spacer(Modifier.height(10.dp))
                                PillRow(listOf("By clip", "All moments"), view, {
                                    Logger.d("Moment search view: ${if (it == 0) "by clip" else "all moments"}")
                                    view = it
                                })
                            }
                        }
                        if (view == 0) {
                            items(response.hits, key = { it.clip.id }) { hit ->
                                Box(Modifier.padding(horizontal = 16.dp)) {
                                    ClipMomentsCard(hit) { moment ->
                                        playing = playlist.indexOfFirst { it.second == moment }
                                    }
                                }
                            }
                        } else {
                            val byConfidence = playlist.withIndex().sortedByDescending { it.value.second.confidence }
                            items(byConfidence, key = { it.index }) { (index, pair) ->
                                MomentRow(pair.first, pair.second) { playing = index }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = playing != null && playlist.isNotEmpty(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            val index = lastPlaying.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
            if (playlist.isNotEmpty()) {
                val (clip, moment) = playlist[index]
                LaunchedEffect(clip.id, moment.startMs) {
                    if (playing != null) Logger.d("Moment opened: clip=${clip.id} ${moment.label} at ${formatMs(moment.startMs)}")
                }
                MomentPlayer(
                    clip = clip,
                    moment = moment,
                    allMoments = response?.hits?.firstOrNull { it.clip.id == clip.id }?.moments.orEmpty(),
                    index = index,
                    total = playlist.size,
                    onPrev = { playing = (index - 1).coerceAtLeast(0) },
                    onNext = { playing = (index + 1).coerceAtMost(playlist.size - 1) },
                    onClose = { playing = null },
                    onShare = onShareMoment
                )
            }
        }
    }
}

@Composable
private fun InterpretationRow(games: List<String>, events: List<String>) {
    if (games.isEmpty() && events.isEmpty()) return
    Row(
        Modifier
            .padding(top = 6.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = Gold, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Understood as", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        (games + events).forEach { term ->
            Spacer(Modifier.width(6.dp))
            Text(
                term,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun IdleContent(recent: List<String>, library: List<Clip>, onPick: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Try searching for what happens in a clip", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            // Two scrollable rows of suggestion pills.
            Suggestions.chunked((Suggestions.size + 1) / 2).forEach { row ->
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { s -> Pill(s, false) { Logger.d("Moment search suggestion: $s"); onPick(s) } }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { Text("Recent", fontWeight = FontWeight.Bold) }
            items(recent) { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(r) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(r)
                }
            }
        }
        item { Text("Your library", fontWeight = FontWeight.Bold) }
        items(library, key = { it.id }) { clip ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClipThumbnail(clip, Modifier.width(96.dp), compact = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(clip.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${clip.game} · ${clip.duration}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Icon(Icons.Filled.CheckCircle, "Indexed", tint = OnlineGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Indexed", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
        action()
    }
}

/** Plays a clip starting at the moment's timestamp, with prev/next through all results. */
@Composable
private fun MomentPlayer(
    clip: Clip,
    moment: Moment,
    allMoments: List<Moment>,
    index: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onShare: (Clip) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val durationMs = clip.durationSeconds * 1000L
    // Start a second early so the action has context.
    val startMs = (moment.startMs - 1000).coerceAtLeast(0)
    var positionMs by remember(clip.id, moment.startMs) { mutableLongStateOf(startMs) }
    var isPlaying by remember(clip.id, moment.startMs) { mutableStateOf(true) }

    LaunchedEffect(clip.id, moment.startMs, isPlaying) {
        while (isPlaying) {
            delay(100)
            positionMs += 100
            if (positionMs >= moment.endMs + 1000 || positionMs >= durationMs) isPlaying = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Text("Moment ${index + 1} of $total", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        if (clip.playlistUrl != null) {
            // Real video, starting just before the matched second.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                HlsPlayer(clip.playlistUrl, Modifier.fillMaxSize(), seekToMs = startMs)
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Gold, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(moment.label, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        } else
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(momentBrush(clip, moment))
                .clickable {
                    if (!isPlaying && positionMs >= moment.endMs + 1000) positionMs = startMs
                    isPlaying = !isPlaying
                }
        ) {
            Icon(
                Icons.Filled.SportsEsports, null,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.align(Alignment.Center).size(120.dp)
            )
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Gold, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${moment.label} · ${(moment.confidence * 100).toInt()}%",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (!isPlaying) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (positionMs >= moment.endMs + 1000) Icons.Filled.Replay else Icons.Filled.PlayArrow,
                        "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Text(
                "${formatMs(positionMs)} / ${clip.duration}",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 12.sp
            )
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MomentTimeline(
                durationMs,
                allMoments.ifEmpty { listOf(moment) },
                highlighted = moment,
                positionMs = if (clip.playlistUrl != null) null else positionMs,
                height = 12.dp
            )
            Text(
                "Jumped to ${formatMs(moment.startMs)} – ${formatMs(moment.endMs)}",
                color = Gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Column {
                Text(moment.description, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${clip.title} · ${clip.game}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onPrev,
                    enabled = index > 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Previous", color = Color.White)
                }
                OutlinedButton(
                    onClick = onNext,
                    enabled = index < total - 1,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next", color = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White)
                }
            }
            Button(
                onClick = {
                    val momentClip = momentAsClip(clip, moment, index)
                    Logger.i("Share moment: clip=${clip.id} ${moment.label} at ${formatMs(moment.startMs)}")
                    onShare(momentClip)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share this moment", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    val momentClip = momentAsClip(clip, moment, index)
                    Logger.i("Save moment as clip: clip=${clip.id} ${formatMs(moment.startMs)}-${formatMs(moment.endMs)}")
                    val sessionId = momentClip.sessionId
                    if (sessionId == null || !MinedTvApi.isConfigured) {
                        Toast.makeText(context, "Saved ${moment.label} as a new clip", Toast.LENGTH_SHORT).show()
                    } else scope.launch {
                        runCatching { MinedTvApi.createClip(sessionId, momentClip.startMs, momentClip.endMs, momentClip.title) }
                            .onSuccess {
                                Logger.i("Saved clip ${it.remoteId} (${it.duration})")
                                Toast.makeText(context, "Saved to your clips", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Logger.e("Save clip failed", it)
                                Toast.makeText(context, "Couldn't save the clip", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.ContentCut, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save as new clip", color = Color.White)
            }
        }
    }
}

/**
 * A short clip cut around a moment (with 2s of padding each side), ready for the share flow.
 * For backend results it carries the session range, so sharing saves and exports exactly that.
 */
private fun momentAsClip(clip: Clip, moment: Moment, index: Int): Clip {
    val seconds = (((moment.endMs - moment.startMs) / 1000) + 4).toInt().coerceAtLeast(5)
    val rangeStart = (clip.startMs + moment.startMs - 2_000).coerceAtLeast(0)
    return clip.copy(
        remoteId = null,
        playlistUrl = null,
        thumbnailUrl = moment.thumbnailUrl ?: clip.thumbnailUrl,
        startMs = rangeStart,
        endMs = rangeStart + seconds * 1000L,
        id = 10_000 + clip.id * 100 + index,
        title = "${moment.description} - ${clip.title}",
        views = 0,
        likes = 0,
        comments = 0,
        duration = formatDuration(seconds),
        age = "now"
    )
}
