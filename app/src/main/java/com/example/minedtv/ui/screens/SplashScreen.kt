package com.example.minedtv.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minedtv.ui.components.BRAIN_PATH
import com.example.minedtv.ui.theme.Background
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.TextSecondary
import com.example.minedtv.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated launch screen: the gold tile pops in, the brain draws itself stroke by stroke,
 * then "MinedTv" and the tagline slide in. Calls [onFinished] when done (~2.6s).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val tileScale = remember { Animatable(0f) }
    val draw = remember { Animatable(0f) }
    val wordmark = remember { Animatable(0f) }
    val tvScale = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    // Each "M" in the path is one stroke; draw them one after another.
    val strokes = remember {
        BRAIN_PATH.split("M").filter { it.isNotBlank() }.map { PathParser().parsePathString("M$it").toPath() }
    }

    LaunchedEffect(Unit) {
        Logger.d("Splash started")
        launch { tileScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
        delay(200)
        draw.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        launch { wordmark.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        delay(250)
        launch { tvScale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 400f)) }
        delay(200)
        tagline.animateTo(1f, tween(400))
        delay(500)
        Logger.d("Splash finished")
        currentOnFinished()
    }

    val rings = rememberInfiniteTransition(label = "rings").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "ringProgress"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            // Soft gold glow behind the logo.
            .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.16f), Color.Transparent), radius = 900f))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(128.dp)
                    // Pulsing rings radiating from the tile.
                    .drawBehind {
                        if (tileScale.value < 0.9f) return@drawBehind
                        for (phase in listOf(0f, 0.5f)) {
                            val t = (rings.value + phase) % 1f
                            drawCircle(
                                color = Gold.copy(alpha = (1f - t) * 0.35f),
                                radius = size.minDimension / 2 * (1f + t * 0.9f),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                    .graphicsLayer {
                        scaleX = tileScale.value
                        scaleY = tileScale.value
                        rotationZ = (1f - tileScale.value) * -30f
                    }
                    .background(Gold, RoundedCornerShape(32.dp))
                    .padding(20.dp)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val unit = size.minDimension / 24f
                    val stagger = 0.06f
                    val span = 1f - stagger * (strokes.size - 1)
                    scale(unit, unit, pivot = Offset.Zero) {
                        strokes.forEachIndexed { i, path ->
                            val p = ((draw.value - i * stagger) / span).coerceIn(0f, 1f)
                            if (p <= 0f) return@forEachIndexed
                            val measure = PathMeasure().apply { setPath(path, false) }
                            val segment = Path()
                            measure.getSegment(0f, measure.length * p, segment, true)
                            drawPath(
                                segment,
                                color = Color.Black,
                                style = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(36.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Mined",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.graphicsLayer {
                        alpha = wordmark.value
                        translationY = (1f - wordmark.value) * 40.dp.toPx()
                    }
                )
                Text(
                    "Tv",
                    color = Gold,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.graphicsLayer {
                        alpha = tvScale.value.coerceIn(0f, 1f)
                        scaleX = tvScale.value
                        scaleY = tvScale.value
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Clip the plays that blow your mind",
                color = TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = tagline.value
                    translationY = (1f - tagline.value) * 12.dp.toPx()
                }
            )
        }
    }
}
