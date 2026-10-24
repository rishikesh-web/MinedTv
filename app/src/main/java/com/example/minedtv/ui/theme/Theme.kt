package com.example.minedtv.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Medal-style dark theme with a gold accent; always dark.
private val ColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color.Black,
    secondary = Gold,
    onSecondary = Color.Black,
    background = Background,
    onBackground = Color.White,
    surface = Surface,
    onSurface = Color.White,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    error = LiveRed
)

@Composable
fun MinedTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography
    ) {
        // Default text/icon color for screens drawn outside a Scaffold/Surface (overlays).
        CompositionLocalProvider(LocalContentColor provides ColorScheme.onBackground, content = content)
    }
}
