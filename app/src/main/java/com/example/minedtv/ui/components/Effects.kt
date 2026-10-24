package com.example.minedtv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minedtv.data.formatCount
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.LiveRed
import com.example.minedtv.ui.theme.OnlineGreen
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Compose takes on ideas from awesome-android-ui (Shimmer, ShineButton, ExplosionField, ...).

private val ShimmerHighlight = Color(0xFF2E313C)

/** Animated loading sheen, like Facebook's Shimmer. */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val progress = rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "shimmerProgress"
    )
    drawBehind {
        val w = size.width
        val x = -w + progress.value * 3 * w
        drawRect(
            Brush.linearGradient(
                listOf(base, ShimmerHighlight, base),
                start = Offset(x, 0f),
                end = Offset(x + w, size.height)
            )
        )
    }
}

@Composable
fun SkeletonBox(modifier: Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.clip(shape).shimmer())
}

/** Placeholder shaped like [ClipCard] while the feed loads. */
@Composable
fun ClipCardSkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(36.dp), CircleShape)
            Spacer(Modifier.width(10.dp))
            Column {
                SkeletonBox(Modifier.size(width = 110.dp, height = 12.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBox(Modifier.size(width = 70.dp, height = 10.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(14.dp))
        Spacer(Modifier.height(10.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) { SkeletonBox(Modifier.size(width = 48.dp, height = 12.dp)) }
        }
    }
}

/** Heart that bounces and throws off a ring of sparks when liked (ShineButton-style). */
@Composable
fun LikeButton(liked: Boolean, count: Int, onToggle: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }
    val sparkDistance = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
    val sparkRadius = with(androidx.compose.ui.platform.LocalDensity.current) { 2.5.dp.toPx() }

    LaunchedEffect(liked) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        if (liked) {
            launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
            }
            scale.snapTo(0.5f)
            scale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f))
        } else {
            scale.snapTo(0.8f)
            scale.animateTo(1f, spring())
        }
    }

    Row(
        Modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .drawBehind {
                    val p = burst.value
                    if (p < 1f) {
                        for (i in 0 until 8) {
                            val angle = Math.toRadians(i * 45.0 - 90)
                            val r = size.minDimension / 2 + p * sparkDistance
                            drawCircle(
                                color = (if (i % 2 == 0) LiveRed else Gold).copy(alpha = 1f - p),
                                radius = sparkRadius * (1f - p * 0.6f),
                                center = center + Offset((cos(angle) * r).toFloat(), (sin(angle) * r).toFloat())
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                if (liked) "Unlike" else "Like",
                tint = if (liked) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
            )
        }
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (up) -it else it } + fadeOut())
            },
            label = "likeCount"
        ) { value ->
            Text(formatCount(value), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

/** Big heart that pops over a video on double-tap. Increment [trigger] to play it. */
@Composable
fun HeartPop(trigger: Int, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        p.snapTo(0.001f)
        p.animateTo(1f, tween(800, easing = LinearEasing))
        p.snapTo(0f)
    }
    Icon(
        Icons.Filled.Favorite, null,
        tint = LiveRed,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val v = p.value
                val s = when {
                    v < 0.25f -> 0.3f + (v / 0.25f) * 0.9f
                    v < 0.4f -> 1.2f - ((v - 0.25f) / 0.15f) * 0.2f
                    else -> 1f
                }
                scaleX = s
                scaleY = s
                alpha = when {
                    v <= 0f -> 0f
                    v < 0.7f -> 1f
                    else -> (1f - v) / 0.3f
                }
                rotationZ = -12f
            }
    )
}

private class ConfettiPiece(
    val originX: Float,
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val w: Float,
    val h: Float,
    val color: Color
)

private val ConfettiColors = listOf(Gold, LiveRed, OnlineGreen, Color(0xFF25F4EE), Color(0xFFDD2A7B), Color.White)

/** One-shot confetti explosion that fills its bounds. Doesn't consume touches. */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier, pieces: Int = 90) {
    val confetti = remember {
        List(pieces) {
            ConfettiPiece(
                originX = 0.5f + (Random.nextFloat() - 0.5f) * 0.2f,
                vx = (Random.nextFloat() - 0.5f) * 0.9f,
                vy = -(0.5f + Random.nextFloat() * 0.8f),
                spin = (Random.nextFloat() - 0.5f) * 4f,
                w = 6f + Random.nextFloat() * 6f,
                h = 10f + Random.nextFloat() * 8f,
                color = ConfettiColors.random()
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(3000, easing = LinearEasing)) }

    Canvas(modifier) {
        val progress = t.value
        if (progress >= 1f) return@Canvas
        val time = progress * 3f
        val alpha = if (progress < 0.7f) 1f else (1f - progress) / 0.3f
        confetti.forEach { c ->
            val x = size.width * (c.originX + c.vx * time)
            val y = size.height * (0.28f + c.vy * time * 0.6f + 0.35f * time * time)
            val w = c.w * density
            val h = c.h * density
            rotate(c.spin * time * 360f, pivot = Offset(x, y)) {
                drawRect(c.color.copy(alpha = alpha), topLeft = Offset(x - w / 2, y - h / 2), size = Size(w, h))
            }
        }
    }
}
