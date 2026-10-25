package com.example.minedtv.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minedtv.data.dashboard.TrafficSource
import com.example.minedtv.data.formatCount
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.Surface
import com.example.minedtv.ui.theme.SurfaceHigh
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Chart tokens from the data-viz reference palette, validated on the app's dark card surface (#15161C). */
object DashboardColors {
    val Grid = Color(0xFF2C2C2A)
    val Baseline = Color(0xFF383835)
    val AxisText = Color(0xFF898781)
    val Good = Color(0xFF0CA30C)
    val Warning = Color(0xFFFAB219)
    val Serious = Color(0xFFEC835A)
    val Critical = Color(0xFFD03B3B)

    /** Categorical slots 1-4 (dark steps), validated on #15161C: fixed per source, never re-assigned by rank. */
    fun source(s: TrafficSource) = when (s) {
        TrafficSource.Feed -> Color(0xFF3987E5)
        TrafficSource.Search -> Color(0xFFD95926)
        TrafficSource.Profile -> Color(0xFF199E70)
        TrafficSource.SharedLink -> Color(0xFFC98500)
    }
}

private val DayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** Single-series sparkline for stat tiles and list rows. */
@Composable
fun Sparkline(values: List<Long>, modifier: Modifier = Modifier, color: Color = Gold) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().toFloat()
        val min = values.min().toFloat()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height - (v - min) / range * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Rounds up to a "nice" axis maximum split into 3 steps. */
private fun niceMax(max: Long): Long {
    if (max <= 0) return 3
    val rawStep = max / 3.0
    val mag = 10.0.pow(floor(log10(rawStep)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * mag >= rawStep } * mag
    return (step * 3).toLong()
}

/**
 * One measure over time: area + 2dp line, recessive grid, 4 y-ticks, first/middle/last dates.
 * Tap or drag horizontally for a crosshair and tooltip.
 */
@Composable
fun TrendChart(
    days: List<LocalDate>,
    values: List<Long>,
    unit: String,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val axisStyle = TextStyle(color = DashboardColors.AxisText, fontSize = 11.sp)
    val tipTitle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val tipBody = TextStyle(color = Color(0xFFC3C2B7), fontSize = 11.sp)
    val leftPadDp = 44.dp
    val bottomPadDp = 22.dp

    fun indexAt(x: Float, width: Float, left: Float): Int =
        (((x - left) / (width - left)) * (values.size - 1)).roundToInt().coerceIn(0, values.lastIndex)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(values) {
                detectTapGestures { selected = indexAt(it.x, size.width.toFloat(), leftPadDp.toPx()) }
            }
            .pointerInput(values) {
                detectHorizontalDragGestures(
                    onDragEnd = {},
                    onHorizontalDrag = { change, _ ->
                        selected = indexAt(change.position.x, size.width.toFloat(), leftPadDp.toPx())
                        change.consume()
                    }
                )
            }
    ) {
        if (values.size < 2) return@Canvas
        val left = leftPadDp.toPx()
        val bottom = size.height - bottomPadDp.toPx()
        val top = 8.dp.toPx()
        val yMax = niceMax(values.max())
        fun xOf(i: Int) = left + (size.width - left) * i / (values.size - 1)
        fun yOf(v: Long) = bottom - (bottom - top) * v / yMax.toFloat()

        // Grid + y labels
        for (t in 0..3) {
            val v = yMax * t / 3
            val y = yOf(v)
            drawLine(if (t == 0) DashboardColors.Baseline else DashboardColors.Grid, Offset(left, y), Offset(size.width, y), 1.dp.toPx())
            val label = measurer.measure(formatCount(v.toInt()), axisStyle)
            drawText(label, topLeft = Offset(left - 8.dp.toPx() - label.size.width, y - label.size.height / 2))
        }
        // X labels: first, middle, last
        listOf(0, values.size / 2, values.lastIndex).forEach { i ->
            val label = measurer.measure(days[i].format(DayFormat), axisStyle)
            val x = (xOf(i) - label.size.width / 2).coerceIn(left, size.width - label.size.width)
            drawText(label, topLeft = Offset(x, bottom + 6.dp.toPx()))
        }
        // Area + line
        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(xOf(i), yOf(v)) else line.lineTo(xOf(i), yOf(v)) }
        val area = Path().apply {
            addPath(line)
            lineTo(xOf(values.lastIndex), bottom)
            lineTo(xOf(0), bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(Gold.copy(alpha = 0.28f), Gold.copy(alpha = 0f)), startY = top, endY = bottom))
        drawPath(line, Gold, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Crosshair + tooltip
        selected?.let { i ->
            val x = xOf(i)
            val y = yOf(values[i])
            drawLine(DashboardColors.AxisText, Offset(x, top), Offset(x, bottom), 1.dp.toPx())
            drawCircle(Surface, 6.dp.toPx(), Offset(x, y))
            drawCircle(Gold, 4.dp.toPx(), Offset(x, y))
            val title = measurer.measure("%,d %s".format(values[i], unit), tipTitle)
            val body = measurer.measure(days[i].format(DayFormat), tipBody)
            val pad = 8.dp.toPx()
            val w = maxOf(title.size.width, body.size.width) + pad * 2
            val h = title.size.height + body.size.height + pad * 2
            val tx = (x - w / 2).coerceIn(left, size.width - w)
            val ty = (y - h - 12.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(SurfaceHigh, Offset(tx, ty), Size(w, h), CornerRadius(8.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(tx, ty), Size(w, h), CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawText(title, topLeft = Offset(tx + pad, ty + pad))
            drawText(body, topLeft = Offset(tx + pad, ty + pad + title.size.height))
        }
    }
}

private fun clock(ms: Long) = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

/**
 * Virality moment by moment: one 0-100 score per sampled frame over the clip's time, fixed
 * 0/50/100 axis, the peak always marked. Tap or drag horizontally for a crosshair and tooltip.
 */
@Composable
fun MomentCurve(points: List<Pair<Long, Float>>, peakMs: Long?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val axisStyle = TextStyle(color = DashboardColors.AxisText, fontSize = 11.sp)
    val peakStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val tipTitle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val tipBody = TextStyle(color = Color(0xFFC3C2B7), fontSize = 11.sp)
    val leftPadDp = 34.dp
    val bottomPadDp = 22.dp
    val endMs = points.lastOrNull()?.first?.coerceAtLeast(1) ?: 1

    fun indexAt(x: Float, width: Float, left: Float): Int {
        val t = ((x - left) / (width - left)).coerceIn(0f, 1f) * endMs
        return points.indices.minByOrNull { kotlin.math.abs(points[it].first - t) } ?: 0
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(points) { detectTapGestures { selected = indexAt(it.x, size.width.toFloat(), leftPadDp.toPx()) } }
            .pointerInput(points) {
                detectHorizontalDragGestures(onDragEnd = {}, onHorizontalDrag = { change, _ ->
                    selected = indexAt(change.position.x, size.width.toFloat(), leftPadDp.toPx())
                    change.consume()
                })
            }
    ) {
        if (points.size < 2) return@Canvas
        val left = leftPadDp.toPx()
        val bottom = size.height - bottomPadDp.toPx()
        val top = 22.dp.toPx() // room for the peak label
        fun xOf(ms: Long) = left + (size.width - left) * ms / endMs.toFloat()
        fun yOf(v: Float) = bottom - (bottom - top) * v.coerceIn(0f, 100f) / 100f

        listOf(0f, 50f, 100f).forEach { v ->
            val y = yOf(v)
            drawLine(if (v == 0f) DashboardColors.Baseline else DashboardColors.Grid, Offset(left, y), Offset(size.width, y), 1.dp.toPx())
            val label = measurer.measure(v.toInt().toString(), axisStyle)
            drawText(label, topLeft = Offset(left - 8.dp.toPx() - label.size.width, y - label.size.height / 2))
        }
        listOf(0L, endMs / 2, endMs).forEach { ms ->
            val label = measurer.measure(clock(ms), axisStyle)
            val x = (xOf(ms) - label.size.width / 2).coerceIn(left, size.width - label.size.width)
            drawText(label, topLeft = Offset(x, bottom + 6.dp.toPx()))
        }
        val line = Path()
        points.forEachIndexed { i, (ms, v) -> if (i == 0) line.moveTo(xOf(ms), yOf(v)) else line.lineTo(xOf(ms), yOf(v)) }
        val area = Path().apply {
            addPath(line)
            lineTo(xOf(points.last().first), bottom)
            lineTo(xOf(points.first().first), bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(Gold.copy(alpha = 0.24f), Gold.copy(alpha = 0f)), startY = top, endY = bottom))
        drawPath(line, Gold, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Peak: ringed marker + direct label (the one point worth naming).
        val peak = peakMs?.let { p -> points.minByOrNull { kotlin.math.abs(it.first - p) } } ?: points.maxBy { it.second }
        val px = xOf(peak.first); val py = yOf(peak.second)
        drawCircle(Surface, 6.dp.toPx(), Offset(px, py))
        drawCircle(Gold, 4.dp.toPx(), Offset(px, py))
        val pl = measurer.measure("Peak ${clock(peak.first)}", peakStyle)
        drawText(pl, topLeft = Offset((px - pl.size.width / 2).coerceIn(left, size.width - pl.size.width), (py - pl.size.height - 8.dp.toPx()).coerceAtLeast(0f)))

        selected?.let { i ->
            val (ms, v) = points[i]
            val x = xOf(ms); val y = yOf(v)
            drawLine(DashboardColors.AxisText, Offset(x, top), Offset(x, bottom), 1.dp.toPx())
            drawCircle(Surface, 6.dp.toPx(), Offset(x, y))
            drawCircle(Gold, 4.dp.toPx(), Offset(x, y))
            val title = measurer.measure("Score ${v.roundToInt()}", tipTitle)
            val body = measurer.measure("at ${clock(ms)}", tipBody)
            val pad = 8.dp.toPx()
            val w = maxOf(title.size.width, body.size.width) + pad * 2
            val h = title.size.height + body.size.height + pad * 2
            val tx = (x - w / 2).coerceIn(left, size.width - w)
            val ty = (y - h - 12.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(SurfaceHigh, Offset(tx, ty), Size(w, h), CornerRadius(8.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(tx, ty), Size(w, h), CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawText(title, topLeft = Offset(tx + pad, ty + pad))
            drawText(body, topLeft = Offset(tx + pad, ty + pad + title.size.height))
        }
    }
}

/** Part-to-whole: one horizontal stacked bar with 2dp surface gaps, plus a labelled legend. */
@Composable
fun SourceSplitBar(parts: List<Pair<TrafficSource, Long>>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val gap = 2.dp.toPx()
            var x = 0f
            parts.forEachIndexed { i, (p, v) ->
                val w = size.width * v / total
                val drawn = (w - if (i < parts.lastIndex) gap else 0f).coerceAtLeast(0f)
                drawRoundRect(DashboardColors.source(p), Offset(x, 0f), Size(drawn, size.height), CornerRadius(4.dp.toPx()))
                x += w
            }
        }
        Spacer(Modifier.height(10.dp))
        parts.forEach { (p, v) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(DashboardColors.source(p), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(p.label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(formatCount(v.toInt()), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "  ${(v * 100 / total)}%",
                    fontSize = 12.sp,
                    color = DashboardColors.AxisText,
                    modifier = Modifier.width(44.dp)
                )
            }
        }
    }
}

/** A value against a limit: track + fill in one hue, optional threshold tick. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, threshold: Float? = null, color: Color = Gold) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(SurfaceHigh, size = size, cornerRadius = r)
        drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
        threshold?.let {
            val x = size.width * it.coerceIn(0f, 1f)
            drawLine(Color.White, Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), 2.dp.toPx())
        }
    }
}
