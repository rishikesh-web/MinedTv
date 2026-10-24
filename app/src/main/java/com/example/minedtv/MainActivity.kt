package com.example.minedtv

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.minedtv.data.Clip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.minedtv.ui.screens.*
import com.example.minedtv.ui.share.ShareScreen
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.MinedTvTheme
import com.example.minedtv.util.Logger

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.i("MainActivity created")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(TRANSPARENT)
        )
        setContent {
            MinedTvTheme {
                MinedTvApp()
            }
        }
    }
}

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Explore("Explore", Icons.Outlined.Explore, Icons.Filled.Explore),
    Clipper("Clip", Icons.Outlined.Videocam, Icons.Filled.Videocam),
    Inbox("Inbox", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    Profile("Profile", Icons.Outlined.Person, Icons.Filled.Person)
}

@Composable
fun MinedTvApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var openClip by remember { mutableStateOf<Clip?>(null) }
    // Keeps the last clip around so the exit animation has something to draw.
    var lastClip by remember { mutableStateOf<Clip?>(null) }
    if (openClip != null) lastClip = openClip
    var shareClip by remember { mutableStateOf<Clip?>(null) }
    var lastShareClip by remember { mutableStateOf<Clip?>(null) }
    if (shareClip != null) lastShareClip = shareClip
    var shareSession by remember { mutableIntStateOf(0) }
    // Saveable so rotating the phone doesn't replay the splash.
    var showSplash by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(tab) { Logger.d("Screen: ${tab.label}") }
    LaunchedEffect(openClip) {
        openClip?.let { Logger.d("Clip opened: id=${it.id} \"${it.title}\"") }
            ?: lastClip?.let { Logger.d("Clip closed: id=${it.id}") }
    }

    BackHandler(enabled = openClip == null && tab != Tab.Home) {
        Logger.d("Back pressed: ${tab.label} -> Home")
        tab = Tab.Home
    }
    BackHandler(enabled = openClip != null && shareClip == null) {
        Logger.d("Back pressed: closing clip")
        openClip = null
    }

    val onClipClick: (Clip) -> Unit = { openClip = it }

    Box(Modifier.fillMaxSize()) {
        // The main UI is composed once the splash starts leaving, so the feed's loading skeleton plays after it.
        if (!showSplash) Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AnimatedBottomBar(tab) { t ->
                    Logger.d("Nav tab tapped: ${t.label}")
                    tab = t
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.padding(padding),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "tabContent"
            ) { current ->
                when (current) {
                    Tab.Home -> HomeScreen(
                        onClipClick = onClipClick,
                        onSearch = { tab = Tab.Explore },
                        onNotifications = { tab = Tab.Inbox }
                    )
                    Tab.Explore -> ExploreScreen(onClipClick)
                    Tab.Clipper -> ClipperScreen(onClipClick)
                    Tab.Inbox -> InboxScreen(onClipClick)
                    Tab.Profile -> ProfileScreen(onClipClick)
                }
            }
        }

        AnimatedVisibility(
            visible = openClip != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            lastClip?.let { clip ->
                ClipDetailScreen(
                    clip,
                    onBack = { openClip = null },
                    onClipClick = onClipClick,
                    onShare = {
                        Logger.d("Share opened: clip=${it.id}")
                        shareSession++
                        shareClip = it
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = shareClip != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            lastShareClip?.let { clip ->
                ShareScreen(clip, shareSession, onClose = {
                    Logger.d("Share closed")
                    shareClip = null
                })
            }
        }

        AnimatedVisibility(
            visible = showSplash,
            enter = EnterTransition.None,
            exit = fadeOut(tween(450)) + scaleOut(tween(450), targetScale = 1.15f)
        ) {
            SplashScreen(onFinished = { showSplash = false })
        }
    }
}

/** Pill-style bottom bar: the selected tab expands to show its label (AnimatedBottomBar-style). */
@Composable
private fun AnimatedBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.entries.forEach { t ->
            val isSelected = t == selected
            if (t == Tab.Clipper) {
                // Highlighted center record action, like Medal's.
                val scale by animateFloatAsState(
                    if (isSelected) 1.12f else 1f,
                    spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                    label = "clipScale"
                )
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .size(width = 56.dp, height = 40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Gold, Color(0xFFFF9F1C))))
                        .clickable { onSelect(t) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(t.selectedIcon, t.label, tint = Color.Black, modifier = Modifier.size(24.dp))
                }
            } else {
                val bg by animateColorAsState(if (isSelected) Gold.copy(alpha = 0.15f) else Color.Transparent, label = "tabBg")
                val tint by animateColorAsState(
                    if (isSelected) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabTint"
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(t) }
                        .animateContentSize(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (isSelected) t.selectedIcon else t.icon, t.label, tint = tint, modifier = Modifier.size(22.dp))
                    if (isSelected) {
                        Spacer(Modifier.width(6.dp))
                        Text(t.label, color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}
