package com.example.minedtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minedtv.data.Clip
import com.example.minedtv.data.SampleData
import com.example.minedtv.data.formatCount
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.LiveRed
import com.example.minedtv.util.Logger
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.minedtv.data.api.MinedTvApi

@Composable
fun MinedTvLogo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Gold),
            contentAlignment = Alignment.Center
        ) {
            Icon(BrainIcon, null, tint = Color.Black, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                append("Mined")
                withStyle(SpanStyle(color = Gold)) { append("It") }
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun Avatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val colors = SampleData.palettes[(name.hashCode() and 0x7fffffff) % SampleData.palettes.size]
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.first().uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

@Composable
fun GameChip(game: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.SportsEsports, null, tint = Gold, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(game, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Gradient stand-in for a clip thumbnail. */
@Composable
fun ClipThumbnail(item: Clip, modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(if (compact) 10.dp else 14.dp))
            .background(Brush.linearGradient(item.colors))
    ) {
        if (item.thumbnailUrl != null) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else Icon(
            Icons.Filled.SportsEsports, null,
            tint = Color.White.copy(alpha = 0.12f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(if (compact) 56.dp else 110.dp)
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(if (compact) 32.dp else 52.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
        }
        if (!compact) GameChip(item.game, Modifier.align(Alignment.TopStart).padding(8.dp))
        if (item.ageRestricted) {
            Text(
                "18+",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (compact) 4.dp else 8.dp)
                    .background(Color(0xFFD03B3B), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Text(
            item.duration,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StatAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit = {}
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

/** Full-width feed card. */
@Composable
fun ClipCard(item: Clip, onClick: () -> Unit) {
    var liked by remember(item.id) { mutableStateOf(false) }
    var heartTrigger by remember(item.id) { mutableIntStateOf(0) }
    val currentOnClick by rememberUpdatedState(onClick)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(item.user, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.user, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "${item.game} · ${item.age} ago",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        // Tap opens the clip, double-tap likes it (TikTok-style).
        Box(
            Modifier
                .fillMaxWidth()
                .pointerInput(item.id) {
                    detectTapGestures(
                        onTap = { currentOnClick() },
                        onDoubleTap = {
                            if (!liked) {
                                liked = true
                                Logger.d("Feed clip double-tap liked: id=${item.id}")
                            }
                            heartTrigger++
                        }
                    )
                }
        ) {
            ClipThumbnail(item, Modifier.fillMaxWidth())
            HeartPop(heartTrigger, Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(10.dp))
        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LikeButton(liked, item.likes + if (liked) 1 else 0) {
                liked = !liked
                Logger.d("Feed clip ${if (liked) "liked" else "unliked"}: id=${item.id}")
            }
            StatAction(Icons.Outlined.ChatBubbleOutline, formatCount(item.comments), onClick = onClick)
            if (MinedTvApi.owns(item)) StatAction(Icons.Outlined.Share, "Share", onClick = onClick)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.Visibility, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(formatCount(item.views), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

/** Small card for grids and rows. */
@Composable
fun ClipGridItem(item: Clip, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        ClipThumbnail(item, Modifier.fillMaxWidth(), compact = true)
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2
        )
        Text(
            "${item.user} · ${formatCount(item.views)} views",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Gold else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (selected) Color.Black else Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun PillRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, scrollable: Boolean = false) {
    // scrollable: scroll sideways instead of squeezing labels onto two lines. Only where the row
    // isn't already inside a horizontally scrolling parent (that nesting crashes Compose).
    Row(if (scrollable) modifier.horizontalScroll(rememberScrollState()) else modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label -> Pill(label, i == selected) { onSelect(i) } }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = "See all", onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = Gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        actions()
    }
}
