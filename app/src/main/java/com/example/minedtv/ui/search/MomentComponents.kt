package com.example.minedtv.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minedtv.data.Clip
import com.example.minedtv.data.search.Moment
import com.example.minedtv.data.search.SearchHit
import com.example.minedtv.data.search.formatMs
import com.example.minedtv.data.share.durationSeconds
import com.example.minedtv.ui.components.ClipThumbnail
import com.example.minedtv.ui.theme.Gold
import kotlin.math.abs
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

/** Gradient "frame" for a moment; shifts per timestamp so moments look like different frames. */
fun momentBrush(clip: Clip, moment: Moment): Brush {
    val shift = (moment.startMs / 1000 % 7) * 60f
    return Brush.linearGradient(
        if (moment.startMs / 1000 % 2 == 0L) clip.colors else clip.colors.reversed(),
        start = Offset(shift, 0f),
        end = Offset(shift + 400f, 300f)
    )
}

/**
 * The clip's full length as a track, with every matching moment marked in gold.
 * Tapping near a marker selects that moment.
 */
@Composable
fun MomentTimeline(
    durationMs: Long,
    moments: List<Moment>,
    modifier: Modifier = Modifier,
    highlighted: Moment? = null,
    positionMs: Long? = null,
    height: Dp = 10.dp,
    onMomentClick: ((Moment) -> Unit)? = null
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(moments, onMomentClick) {
                if (onMomentClick == null) return@pointerInput
                detectTapGestures { tap ->
                    val tappedMs = (tap.x / size.width * durationMs).toLong()
                    moments.minByOrNull { abs((it.startMs + it.endMs) / 2 - tappedMs) }?.let(onMomentClick)
                }
            }
    ) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        moments.forEach { m ->
            val x = size.width * m.startMs / durationMs
            val w = (size.width * (m.endMs - m.startMs) / durationMs).coerceAtLeast(6f)
            val isHighlighted = highlighted == null || m == highlighted
            drawRoundRect(
                Gold.copy(alpha = if (isHighlighted) 1f else 0.35f),
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
                cornerRadius = r
            )
        }
        positionMs?.let {
            val x = size.width * it / durationMs
            drawRect(Color.White, topLeft = Offset(x - 1.5f, -4f), size = Size(3f, size.height + 8f))
        }
    }
}

@Composable
fun MomentChip(clip: Clip, moment: Moment, onClick: () -> Unit) {
    Column(
        Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(momentBrush(clip, moment))
        ) {
            if (moment.thumbnailUrl != null) {
                AsyncImage(moment.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else Icon(
                Icons.Filled.SportsEsports, null,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.align(Alignment.Center).size(44.dp)
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                formatMs(moment.startMs),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .background(Gold, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "${(moment.confidence * 100).toInt()}%",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(moment.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        Text(
            moment.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One clip from the library with its timeline and the list of matching moments. */
@Composable
fun ClipMomentsCard(hit: SearchHit, onMomentClick: (Moment) -> Unit) {
    val clip = hit.clip
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClipThumbnail(clip, Modifier.width(112.dp), compact = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(clip.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${clip.game} · ${clip.duration} · ${clip.age}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${hit.moments.size} moment${if (hit.moments.size == 1) "" else "s"} found",
                        color = Gold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MomentTimeline(clip.durationSeconds * 1000L, hit.moments, onMomentClick = onMomentClick)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("0:00", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(clip.duration, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(hit.moments) { moment ->
                MomentChip(clip, moment) { onMomentClick(moment) }
            }
        }
    }
}

/** Flat list row used by the "All moments" view. */
@Composable
fun MomentRow(clip: Clip, moment: Moment, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 104.dp, height = 58.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(momentBrush(clip, moment))
        ) {
            moment.thumbnailUrl?.let {
                AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Text(
                formatMs(moment.startMs),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Gold, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(moment.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text("${(moment.confidence * 100).toInt()}% match", color = Gold, fontSize = 11.sp)
            }
            Text(
                "${clip.title} · at ${formatMs(moment.startMs)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            MomentTimeline(clip.durationSeconds * 1000L, listOf(moment), height = 4.dp)
        }
    }
}
