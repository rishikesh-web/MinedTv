package com.example.minedtv.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minedtv.data.Clip
import com.example.minedtv.data.SampleData
import com.example.minedtv.ui.components.ClipGridItem
import com.example.minedtv.ui.components.PillRow
import com.example.minedtv.ui.components.ScreenTitle
import com.example.minedtv.ui.components.SectionHeader
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.LiveRed
import com.example.minedtv.ui.theme.OnlineGreen
import kotlinx.coroutines.delay
import com.example.minedtv.util.Logger

@Composable
fun ClipperScreen(onClipClick: (Clip) -> Unit) {
    val context = LocalContext.current
    var recording by rememberSaveable { mutableStateOf(false) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    var clipLength by rememberSaveable { mutableIntStateOf(1) }
    var quality by rememberSaveable { mutableIntStateOf(1) }
    var autoClip by rememberSaveable { mutableStateOf(true) }
    var mic by rememberSaveable { mutableStateOf(true) }
    var gameAudio by rememberSaveable { mutableStateOf(true) }
    val lengths = listOf("15s", "30s", "1m", "2m")

    LaunchedEffect(recording) {
        while (recording) {
            delay(1000)
            seconds++
        }
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val buttonColor by animateColorAsState(if (recording) LiveRed else Gold, label = "recordColor")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTitle("Clipper") {
            IconButton(onClick = {}) { Icon(Icons.Outlined.Settings, "Settings") }
        }

        // Status card
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .alpha(if (recording) pulse else 1f)
                        .background(if (recording) LiveRed else OnlineGreen, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (recording) "Recording · %d:%02d".format(seconds / 60, seconds % 60) else "Ready to record",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "Detected game: Valorant",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .size(140.dp)
                    .border(4.dp, buttonColor.copy(alpha = 0.3f), CircleShape)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .clickable {
                        recording = !recording
                        if (recording) {
                            seconds = 0
                            Logger.i("Recording started")
                        } else {
                            Logger.i("Recording stopped after ${seconds}s")
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    if (recording) "Stop" else "Record",
                    tint = if (recording) Color.White else Color.Black,
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (recording) "Tap to stop" else "Tap to start recording",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    Logger.i("Clip saved: last ${lengths[clipLength]}")
                    Toast.makeText(context, "Saved last ${lengths[clipLength]}", Toast.LENGTH_SHORT).show()
                },
                enabled = recording,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.ContentCut, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clip last ${lengths[clipLength]}", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Capture settings", Modifier.padding(horizontal = 16.dp), action = null)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Clip length", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            PillRow(lengths, clipLength, {
                Logger.d("Clip length set: ${lengths[it]}")
                clipLength = it
            })
            Text("Quality", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            val qualities = listOf("720p", "1080p", "1440p")
            PillRow(qualities, quality, {
                Logger.d("Quality set: ${qualities[it]}")
                quality = it
            })
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SwitchRow("Auto-clip highlights", "Clip kills & clutches automatically", autoClip) { autoClip = it }
            SwitchRow("Microphone", "Record your voice", mic) { mic = it }
            SwitchRow("Game audio", "Record in-game sound", gameAudio) { gameAudio = it }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Recent captures", Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(SampleData.myClips.take(6)) { clip ->
                ClipGridItem(clip, Modifier.width(180.dp)) { onClipClick(clip) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                Logger.d("Setting '$title' -> $it")
                onChange(it)
            },
            colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Color.Black)
        )
    }
}
