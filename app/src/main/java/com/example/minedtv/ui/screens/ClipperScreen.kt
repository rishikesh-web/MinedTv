package com.example.minedtv.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.minedtv.data.api.MinedTvApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.minedtv.data.Clip
import com.example.minedtv.data.SampleData
import com.example.minedtv.recorder.LocalClips
import com.example.minedtv.recorder.Recorder
import com.example.minedtv.recorder.RecorderEvent
import com.example.minedtv.recorder.RecorderStatus
import com.example.minedtv.recorder.ScreenRecordService
import com.example.minedtv.ui.components.ClipGridItem
import com.example.minedtv.ui.components.PillRow
import com.example.minedtv.ui.components.ScreenTitle
import com.example.minedtv.ui.components.SectionHeader
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.LiveRed
import com.example.minedtv.ui.theme.OnlineGreen
import com.example.minedtv.util.Logger
import kotlinx.coroutines.delay

private val Lengths = listOf(15 to "15s", 30 to "30s", 60 to "1m", 120 to "2m")
private val Qualities = listOf(Triple("720p", 1280, 5_000_000), Triple("1080p", 1920, 8_000_000), Triple("1440p", 2560, 12_000_000))

@Composable
fun ClipperScreen(onClipClick: (Clip) -> Unit, onSearchClips: () -> Unit) {
    val context = LocalContext.current
    val status by Recorder.status.collectAsState()
    val settings by Recorder.settings.collectAsState()
    val buttonActive by Recorder.clipButtonActive.collectAsState()
    val lastApp by Recorder.lastApp.collectAsState()
    val localClips by LocalClips.clips.collectAsState()
    var myRemote by remember { mutableStateOf<List<Clip>?>(null) }
    LaunchedEffect(Unit) {
        if (MinedTvApi.isConfigured) runCatching { MinedTvApi.myClips("all").take(8) }.onSuccess { myRemote = it }
    }
    var saving by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }
    val recording = status as? RecorderStatus.Recording

    LaunchedEffect(Unit) { LocalClips.refresh(context) }
    LaunchedEffect(Unit) {
        Recorder.events.collect { e ->
            saving = e is RecorderEvent.ClipStarted
        }
    }

    // Elapsed time while recording.
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(recording?.startedAtMs) {
        while (recording != null) {
            elapsed = ((System.currentTimeMillis() - recording.startedAtMs) / 1000).toInt()
            delay(500)
        }
        elapsed = 0
    }

    // Start = (mic / notification permission) -> screen capture consent -> foreground service.
    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            Logger.i("Screen capture allowed")
            ScreenRecordService.start(context, result.resultCode, data)
        } else {
            Logger.w("Screen capture declined")
            Recorder.status.value = RecorderStatus.Idle
            Toast.makeText(context, "Screen capture is needed to record", Toast.LENGTH_SHORT).show()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        Logger.d("Permissions: $granted")
        projectionLauncher.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
    fun startRecording() {
        fun missing(p: String) = ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED
        val needed = buildList {
            if ((settings.mic || settings.gameAudio) && missing(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && missing(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        Logger.i("Record tapped (mic=${settings.mic}, gameAudio=${settings.gameAudio}, ${settings.clipSeconds}s, ${settings.longEdge}px)")
        if (needed.isEmpty()) {
            projectionLauncher.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val buttonColor by animateColorAsState(if (recording != null) LiveRed else Gold, label = "recordColor")
    val clipLabel = Lengths.first { it.first == settings.clipSeconds }.second

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTitle("Clipper")

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
                        .alpha(if (recording != null) pulse else 1f)
                        .background(
                            when (status) {
                                is RecorderStatus.Recording -> LiveRed
                                is RecorderStatus.Error -> LiveRed
                                RecorderStatus.Starting -> Gold
                                RecorderStatus.Idle -> OnlineGreen
                            },
                            CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when (val s = status) {
                        is RecorderStatus.Recording -> "Recording · %d:%02d".format(elapsed / 60, elapsed % 60)
                        RecorderStatus.Starting -> "Starting…"
                        is RecorderStatus.Error -> s.message
                        RecorderStatus.Idle -> "Ready to record"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                when {
                    recording != null -> {
                        val ready = minOf(elapsed, settings.clipSeconds)
                        "Last ${ready}s ready to clip · ${recording.width}×${recording.height}" + listOfNotNull("game audio".takeIf { recording.gameAudio }, "mic".takeIf { recording.mic }).joinToString(" + ").let { if (it.isEmpty()) "" else " · $it" }
                    }
                    lastApp != null -> "Last game: ${Recorder.appLabel(context, lastApp)}"
                    else -> "Start, then open your game"
                },
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
                    .clickable(enabled = status != RecorderStatus.Starting) {
                        if (recording != null) {
                            Logger.i("Stop tapped after ${elapsed}s")
                            ScreenRecordService.stop(context)
                        } else {
                            startRecording()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (recording != null) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    if (recording != null) "Stop" else "Record",
                    tint = if (recording != null) Color.White else Color.Black,
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (recording != null) "Keep playing; clip any time from the floating button or the notification"
                else "Tap to start recording",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    Logger.i("Clip button tapped in app")
                    Recorder.clip()
                },
                enabled = recording != null && !saving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving clip…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.ContentCut, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clip last $clipLabel", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Floating button (accessibility service)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.TouchApp, null, tint = Gold, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Floating Clip button", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    if (buttonActive) "On · tap it over any game to save the last $clipLabel"
                    else "Clip without leaving your game",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (buttonActive) {
                Text("On", color = OnlineGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            } else {
                TextButton(onClick = {
                    Logger.d("Floating button: show disclosure")
                    showDisclosure = true
                }) { Text("Turn on", color = Gold, fontWeight = FontWeight.Bold) }
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
            PillRow(Lengths.map { it.second }, Lengths.indexOfFirst { it.first == settings.clipSeconds }, {
                Logger.d("Clip length set: ${Lengths[it].second}")
                Recorder.settings.value = settings.copy(clipSeconds = Lengths[it].first)
            })
            Text(
                "Quality" + if (recording != null) " · applies to the next recording" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            PillRow(Qualities.map { it.first }, Qualities.indexOfFirst { it.second == settings.longEdge }, {
                Logger.d("Quality set: ${Qualities[it].first}")
                Recorder.settings.value = settings.copy(longEdge = Qualities[it].second, bitrate = Qualities[it].third)
            })
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SwitchRow("Microphone", "Record your voice", settings.mic) {
                Recorder.settings.value = settings.copy(mic = it)
            }
            SwitchRow("Game audio", "Sound from the game. Some apps block capture", settings.gameAudio) {
                Recorder.settings.value = settings.copy(gameAudio = it)
            }
            SwitchRow("Auto-clip highlights", "Coming soon", checked = false, enabled = false) {}
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Recent captures", Modifier.padding(horizontal = 16.dp), action = "Search moments", onAction = onSearchClips)
        Spacer(Modifier.height(8.dp))
        // A new recording is inserted in front of the visible card; scroll back so it's seen.
        val capturesState = rememberLazyListState()
        LaunchedEffect(localClips.firstOrNull()?.id) { capturesState.animateScrollToItem(0) }
        LazyRow(
            state = capturesState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clips recorded on this phone first, then your backend clips (sample clips without a backend).
            items((localClips + (myRemote ?: if (MinedTvApi.isConfigured) emptyList() else SampleData.myClips.take(6))).distinctBy { it.id }, key = { it.id }) { clip ->
                ClipGridItem(clip, Modifier.width(180.dp)) { onClipClick(clip) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Turn on the floating Clip button") },
            text = {
                Text(
                    "MinedIt uses Android's accessibility service only to show a Clip button over your games while " +
                        "recording, and to see which app is open so clips get the game's name. It doesn't read what's " +
                        "on screen, tap anything for you, or send this data anywhere.\n\nNext: Accessibility → Installed " +
                        "apps → MinedIt Clip button → turn it on."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Logger.i("Floating button: disclosure accepted, opening Accessibility settings")
                    showDisclosure = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open settings", color = Gold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    Logger.d("Floating button: disclosure declined")
                    showDisclosure = false
                }) { Text("Not now") }
            }
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (enabled) 1f else 0.5f)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = {
                Logger.d("Setting '$title' -> $it")
                onChange(it)
            },
            colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Color.Black)
        )
    }
}
