package com.example.minedtv.ui.moderation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.minedtv.data.moderation.ContentCheck
import com.example.minedtv.data.moderation.Violation
import com.example.minedtv.data.moderation.categoryLabel
import com.example.minedtv.data.search.formatMs
import com.example.minedtv.ui.components.HlsPlayer
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.util.Logger
import kotlinx.coroutines.delay
import kotlin.math.abs

private val Blocked = Color(0xFFD03B3B)
private val Restricted = Color(0xFFFAB219)
private val Clean = Color(0xFF0CA30C)

/**
 * "Content check" for an upload: why it's clean, tagged 18+ or blocked, with every flagged moment on
 * a timeline and as frame chips (like semantic search's moments). Tapping a flag plays that moment.
 * [load] fetches the current verdict; it's polled while the upload is still being checked.
 */
@Composable
fun ContentCheckScreen(load: suspend () -> ContentCheck, onClose: () -> Unit) {
    var check by remember { mutableStateOf<ContentCheck?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Violation?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching { load() }
                .onSuccess { check = it; error = null }
                .onFailure { Logger.e("Content check failed", it); error = it.message ?: "Couldn't load the check" }
            if (check?.isPending != true) break
            delay(5_000)
        }
        Logger.d("Content check: ${check?.severity}, ${check?.violations?.size ?: 0} flags")
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Column(Modifier.weight(1f)) {
                Text("Content check", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(check?.title ?: "Your upload", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val c = check
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (c == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        if (error != null) Text(error ?: "", color = Blocked) else CircularProgressIndicator(color = Gold)
                    }
                }
                return@LazyColumn
            }
            // Player: jumps to the selected flag.
            if (c.playUrl != null && c.severity != "severe") item {
                HlsPlayer(
                    c.playUrl,
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)),
                    seekToMs = selected?.timestampMs ?: 0
                )
            }
            item { VerdictCard(c) }
            if (c.violations.isNotEmpty()) item {
                FlagsCard(c, selected) { v ->
                    selected = v
                    Logger.d("Content flag tapped: ${v.category} at ${v.timestampMs}")
                }
            }
            if (c.violations.isNotEmpty()) items(c.violations) { v -> FlagRow(c, v, v == selected) { selected = v } }
            item {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Checked automatically from the spoken transcript and frames sampled across the video. It's AI-generated and can be wrong.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Card16(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface).padding(16.dp),
        content = content
    )
}

@Composable
private fun VerdictCard(c: ContentCheck) {
    data class V(val icon: ImageVector?, val color: Color, val title: String, val lines: List<String>)
    val v = when (c.severity) {
        "pending" -> V(null, Gold, "Checking your upload…", listOf("We scan what's said and what's on screen for 18+ and blocked content. This takes about a minute." + (c.step?.let { " Current step: $it." } ?: "")))
        "severe" -> V(Icons.Filled.Block, Blocked, "Blocked", listOf(
            "Processing stopped, so no clip was made. Nothing from this upload is public or searchable."
        ))
        "mild" -> V(Icons.Filled.Flag, Restricted, "Will be tagged 18+", listOf(
            "It still uploads and you can publish it, with an 18+ badge.",
            "Viewers under 18 and signed-out viewers won't see it in feeds or on your profile, and can't open it."
        ))
        else -> V(Icons.Filled.CheckCircle, Clean, "Clean", listOf("Nothing was flagged. It can be published for everyone."))
    }
    Card16 {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (v.icon == null) CircularProgressIndicator(color = v.color, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else Icon(v.icon, null, tint = v.color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(v.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (c.severity == "mild") Text(
                "18+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.background(Blocked, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        c.reason?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        v.lines.forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

/** The upload's length as a track, with each flagged second marked; tap near one to select it. */
@Composable
private fun FlagTimeline(c: ContentCheck, selected: Violation?, onSelect: (Violation) -> Unit) {
    val timed = c.violations.filter { it.timestampMs != null }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = if (c.severity == "severe") Blocked else Restricted
    val dur = c.durationMs.coerceAtLeast(1)
    Canvas(
        Modifier.fillMaxWidth().height(12.dp).pointerInput(timed) {
            detectTapGestures { tap ->
                val ms = (tap.x / size.width * dur).toLong()
                timed.minByOrNull { abs(it.timestampMs!! - ms) }?.let(onSelect)
            }
        }
    ) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        timed.forEach { v ->
            val x = size.width * v.timestampMs!! / dur
            val w = (size.width * 1000f / dur).coerceAtLeast(8f)
            drawRoundRect(
                color.copy(alpha = if (selected == null || v == selected) 1f else 0.35f),
                topLeft = Offset((x - w / 2).coerceIn(0f, size.width - w), 0f), size = Size(w, size.height), cornerRadius = r
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("0:00", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(formatMs(c.durationMs), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FlagsCard(c: ContentCheck, selected: Violation?, onSelect: (Violation) -> Unit) {
    val color = if (c.severity == "severe") Blocked else Restricted
    Card16 {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Flag, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            val n = c.violations.size
            Text("$n flagged moment${if (n == 1) "" else "s"}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(12.dp))
        FlagTimeline(c, selected, onSelect)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(c.violations) { v -> FlagChip(c, v, v == selected) { onSelect(v) } }
        }
    }
}

@Composable
private fun FlagFrame(c: ContentCheck, v: Violation, modifier: Modifier) {
    val color = if (c.severity == "severe") Blocked else Restricted
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF22242C))) {
        val url = v.timestampMs?.let { c.frameUrl(it) }
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.SportsEsports, null, tint = Color.White.copy(alpha = 0.15f), modifier = Modifier.align(Alignment.Center).size(32.dp))
        if (v.timestampMs != null) Box(
            Modifier.align(Alignment.Center).size(26.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        Text(
            v.timestampMs?.let { formatMs(it) } ?: "Whole video",
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(color, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
            color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun FlagChip(c: ContentCheck, v: Violation, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(140.dp).clip(RoundedCornerShape(10.dp))
            .border(if (isSelected) 2.dp else 0.dp, if (isSelected) Gold else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        FlagFrame(c, v, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Spacer(Modifier.height(4.dp))
        Text(categoryLabel(v.category), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
        Text(v.note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

/** Full-text row for each flag (the chip truncates the note). */
@Composable
private fun FlagRow(c: ContentCheck, v: Violation, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (isSelected) Gold.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlagFrame(c, v, Modifier.size(width = 104.dp, height = 58.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(categoryLabel(v.category), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(v.note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
