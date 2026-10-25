package com.example.minedtv.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.example.minedtv.data.Clip
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.data.formatCount
import com.example.minedtv.ui.components.Avatar
import com.example.minedtv.ui.components.HeartPop
import com.example.minedtv.ui.components.PillRow
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POOL = 3

/**
 * Reels (Shorts): full-screen vertical swipe feed of short 9:16 clips, ranked by the backend
 * (`/feed?format=short&sort=top`). Three ExoPlayers are reused round-robin: the visible reel plays,
 * its neighbours are buffered so swipes start instantly, and nothing else holds a decoder.
 */
@OptIn(UnstableApi::class)
@Composable
fun ReelsScreen(onOpenClip: (Clip) -> Unit, onShare: (Clip) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feed by rememberSaveable { mutableIntStateOf(0) } // 0 = For you, 1 = Following
    var reels by remember(feed) { mutableStateOf<List<Clip>?>(null) }
    var cursor by remember(feed) { mutableStateOf<String?>(null) }
    var loadingMore by remember(feed) { mutableStateOf(false) }
    var muted by rememberSaveable { mutableStateOf(false) }

    suspend fun loadMore() {
        if (loadingMore || (reels != null && cursor == null && feed == 0)) return
        loadingMore = true
        runCatching {
            if (feed == 1) MinedTvApi.followingFeed(limit = 30, format = "short") to null
            else MinedTvApi.feed(cursor = cursor, limit = 10, format = "short", sort = "top")
        }.onSuccess { (items, next) ->
            reels = (reels.orEmpty() + items).distinctBy { it.id }
            cursor = next
            Logger.d("Reels: +${items.size} (total ${reels?.size}), more=${next != null}")
        }.onFailure {
            Logger.e("Reels load failed", it)
            if (reels == null) reels = emptyList()
        }
        loadingMore = false
    }
    LaunchedEffect(feed) { if (MinedTvApi.isConfigured) loadMore() else reels = emptyList() }

    // Player pool: player i shows pages where page % POOL == i.
    val players = remember {
        List(POOL) {
            ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
        }
    }
    val loaded = remember { arrayOfNulls<String>(POOL) }
    DisposableEffect(Unit) { onDispose { players.forEach { it.release() } } }
    LaunchedEffect(muted) { players.forEach { it.volume = if (muted) 0f else 1f } }

    val list = reels.orEmpty()
    val pager = rememberPagerState(pageCount = { list.size })
    var paused by remember { mutableStateOf(false) }

    // Pause when the app goes to the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) players.forEach { it.pause() }
            if (e == Lifecycle.Event.ON_RESUME && !paused) players.getOrNull(pager.settledPage % POOL)?.play()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // On every settled page: load current ± 1 into their players, play current, pause the rest.
    LaunchedEffect(pager.settledPage, list.size) {
        if (list.isEmpty()) return@LaunchedEffect
        val page = pager.settledPage
        paused = false
        for (p in (page - 1)..(page + 1)) {
            val clip = list.getOrNull(p) ?: continue
            val url = clip.playlistUrl ?: continue
            val slot = p % POOL
            if (loaded[slot] != url) {
                players[slot].setMediaItem(MediaItem.fromUri(url))
                players[slot].prepare()
                loaded[slot] = url
            }
        }
        players.forEachIndexed { i, pl ->
            if (i == page % POOL) { pl.playWhenReady = true; pl.play() } else { pl.pause(); pl.seekTo(0) }
        }
        Logger.d("Reel ${page + 1}/${list.size}: ${list[page].title}")
        if (page >= list.size - 3) loadMore()
        // A view counts after 2 s on screen.
        list[page].remoteId?.let { id ->
            delay(2_000)
            if (pager.settledPage == page) runCatching { MinedTvApi.view(id) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            reels == null -> CircularProgressIndicator(color = Gold, modifier = Modifier.align(Alignment.Center))
            list.isEmpty() -> EmptyReels(feed, Modifier.align(Alignment.Center))
            else -> VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { list[it].id }) { page ->
                val clip = list[page]
                ReelPage(
                    clip = clip,
                    player = players[page % POOL].takeIf { kotlin.math.abs(page - pager.currentPage) <= 1 },
                    isCurrent = page == pager.settledPage,
                    paused = paused && page == pager.settledPage,
                    onTogglePause = {
                        val pl = players[page % POOL]
                        paused = !paused
                        if (paused) pl.pause() else pl.play()
                    },
                    onOpen = { onOpenClip(clip) },
                    onShare = { onShare(clip) }
                )
            }
        }
        // Top bar: For you / Following + mute.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Nuggets", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(12.dp))
            PillRow(listOf("For you", "Following"), feed, {
                Logger.d("Reels feed: ${if (it == 0) "for you" else "following"}")
                feed = it
                scope.launch { pager.scrollToPage(0) }
            }, Modifier.weight(1f))
            IconButton(onClick = { muted = !muted }) {
                Icon(
                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    if (muted) "Unmute" else "Mute", tint = Color.White
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ReelPage(
    clip: Clip,
    player: ExoPlayer?,
    isCurrent: Boolean,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var liked by remember(clip.id) { mutableStateOf(clip.likedByMe) }
    var heart by remember(clip.id) { mutableIntStateOf(0) }
    val baseLikes = clip.likes - if (clip.likedByMe) 1 else 0

    fun setLiked(now: Boolean) {
        if (liked == now) return
        liked = now
        clip.remoteId?.let { id -> scope.launch { runCatching { MinedTvApi.like(id, now) }.onFailure { Logger.e("Reel like failed", it) } } }
        Logger.d("Reel ${if (now) "liked" else "unliked"}: ${clip.remoteId}")
    }

    Box(
        Modifier.fillMaxSize().pointerInput(clip.id) {
            detectTapGestures(
                onTap = { onTogglePause() },
                onDoubleTap = { setLiked(true); heart++ }
            )
        }
    ) {
        // Thumbnail first, the player draws over it once it has a frame.
        clip.thumbnailUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (player != null) AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    // Reels are 9:16: fill the screen; anything else is letterboxed.
                    resizeMode = if (clip.isReel) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )
        if (paused) Box(
            Modifier.align(Alignment.Center).size(72.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(44.dp)) }
        HeartPop(heart, Modifier.align(Alignment.Center), size = 120.dp)

        // Bottom fade so the text stays readable.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )
        // Right rail.
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Rail(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, formatCount(baseLikes + if (liked) 1 else 0),
                tint = if (liked) Color(0xFFFF4D6D) else Color.White) { setLiked(!liked) }
            Rail(Icons.Outlined.ChatBubbleOutline, formatCount(clip.comments)) { onOpen() }
            if (MinedTvApi.owns(clip)) Rail(Icons.Outlined.Share, "Share") { onShare() }
        }
        // Creator, title, source.
        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 80.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(clip.user, 32.dp)
                Spacer(Modifier.width(8.dp))
                Text("@" + clip.user.lowercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (clip.ageRestricted) {
                    Spacer(Modifier.width(8.dp))
                    Text("18+", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.background(Color(0xFFD03B3B), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(clip.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(clip.game.takeIf { it.isNotBlank() && it != "Unknown" }, "${formatCount(clip.views)} views").joinToString(" · "),
                color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp
            )
            if (clip.reelOf != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Gold, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI nugget · from a full clip", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Rail(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.25f), CircleShape)) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp))
        }
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyReels(feed: Int, modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.AutoAwesome, null, tint = Gold, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (feed == 1) "No nuggets from people you follow yet" else "No nuggets yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Share a clip and pick MinedIt Nuggets, or upload a short vertical video.",
            color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp
        )
    }
}
