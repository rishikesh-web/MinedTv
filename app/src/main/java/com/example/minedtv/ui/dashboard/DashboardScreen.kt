package com.example.minedtv.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minedtv.data.Clip
import com.example.minedtv.data.SampleData
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.data.dashboard.*
import com.example.minedtv.data.formatCount
import com.example.minedtv.ui.components.Avatar
import com.example.minedtv.ui.components.BrainIcon
import com.example.minedtv.ui.components.ClipThumbnail
import com.example.minedtv.ui.components.PillRow
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.TextSecondary
import com.example.minedtv.util.Logger
import kotlinx.coroutines.launch

private val Periods = listOf(7 to "7D", 30 to "30D", 90 to "90D")

/**
 * Creator dashboard for MinedIt: published vs drafts, stats and virality predictions.
 * Live = the real backend (what it tracks today); Demo = generated data for presenting.
 */
@Composable
fun DashboardScreen(onClose: () -> Unit) {
    var live by rememberSaveable { mutableStateOf(MinedTvApi.isConfigured) }
    val repo: DashboardRepository = if (live) LiveDashboardRepository else FakeDashboardRepository
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var clips by remember(live) { mutableStateOf<List<DashboardClip>?>(null) }
    var loadError by remember(live) { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val all = clips.orEmpty()
    val published = all.filter { it.isPublished }
    val drafts = all.filterNot { it.isPublished }
    var period by rememberSaveable { mutableIntStateOf(1) }
    var metric by rememberSaveable { mutableStateOf(Metric.Views) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<DashboardClip?>(null) }
    var lastOpen by remember { mutableStateOf<DashboardClip?>(null) }
    open?.let { lastOpen = it }
    val days = Periods[period].first

    // Live: real per-day totals from the backend (tracked since Oct 25, 2026).
    var analytics by remember(live) { mutableStateOf<Analytics?>(null) }
    LaunchedEffect(live, refresh, period) {
        if (live) runCatching { LiveDashboardRepository.analytics(Periods[period].first) }
            .onSuccess { analytics = it }
            .onFailure { Logger.e("Analytics failed", it) }
    }

    LaunchedEffect(live, refresh) {
        loadError = null
        runCatching { repo.clips() }
            .onSuccess {
                clips = it
                Logger.d("Dashboard loaded ${it.size} clips (live=$live)")
            }
            .onFailure {
                Logger.e("Dashboard load failed", it)
                loadError = it.message ?: "Couldn't load your clips"
            }
    }

    BackHandler { if (open != null) open = null else onClose() }

    fun toast(msg: String) = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

    // Daily totals: live from /analytics; demo sums the published clips' generated history.
    val withHistory = published.filter { it.history.isNotEmpty() }
    fun series(m: Metric, d: Int, offset: Int = 0): List<Long> =
        if (live) analytics?.daily?.map { it.value(m) }.orEmpty()
        else if (withHistory.isEmpty()) emptyList()
        else (0 until d).map { k -> withHistory.sumOf { c -> c.history[c.history.size - d - offset + k].value(m) } }
    val dayAxis = if (live) analytics?.daily?.map { it.day }.orEmpty()
        else withHistory.firstOrNull()?.history?.takeLast(days)?.map { it.day }.orEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) {
                        Text("Creator dashboard", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (live) "Live from MinedIt · ${SampleData.currentUser}" else "Demo data for presenting",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (MinedTvApi.isConfigured) {
                        PillRow(listOf("Live", "Demo"), if (live) 0 else 1, {
                            live = it == 0
                            Logger.d("Dashboard mode: ${if (live) "live" else "demo"}")
                        })
                    }
                    run {
                        PillRow(Periods.map { it.second }, period, {
                            Logger.d("Dashboard period: ${Periods[it].second}")
                            period = it
                        })
                    }
                }
            }
            when {
                loadError != null -> item {
                    Card16 {
                        Text("Couldn't load your clips", fontWeight = FontWeight.Bold)
                        Text(loadError ?: "", color = TextSecondary, fontSize = 13.sp)
                        TextButton(onClick = { refresh++ }) { Text("Retry", color = Gold) }
                    }
                }
                clips == null -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold)
                    }
                }
                else -> {
                    item {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CountTile("Published", published.size, Icons.Filled.CheckCircle, DashboardColors.Good, Modifier.weight(1f)) { tab = 0 }
                            CountTile("Drafts", drafts.size, Icons.Filled.Edit, Gold, Modifier.weight(1f)) { tab = 1 }
                        }
                    }
                    // KPI tiles: tap one to chart it. Live: period totals vs the previous period from /analytics.
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Metric.entries.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { m ->
                                        val tracked = !live || m != Metric.Shares
                                        val current = series(m, days)
                                        val a = analytics
                                        StatTile(
                                            metric = m,
                                            value = when {
                                                !tracked -> null
                                                live -> a?.period?.get(m) ?: published.sumOf { it.total(m, days) }
                                                else -> current.sum()
                                            },
                                            previous = if (live) a?.previous?.get(m) else series(m, days, offset = days).sum(),
                                            spark = current,
                                            periodLabel = Periods[period].second,
                                            selected = tracked && m == metric,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (tracked) {
                                                Logger.d("Dashboard metric: ${m.label}")
                                                metric = m
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Card16 {
                            Text("${metric.label} over time", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (live && metric == Metric.Shares) {
                                Text("Not tracked yet: shares to other apps aren't recorded.", color = TextSecondary, fontSize = 13.sp)
                            } else if (dayAxis.size < 2) {
                                Text(if (live) "Loading daily totals…" else "No history yet.", color = TextSecondary, fontSize = 13.sp)
                            } else {
                                val since = analytics?.trackingSince?.let { " · tracked since " + it.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)) } ?: ""
                                Text("All your clips · last $days days$since · tap or drag for daily values", color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(12.dp))
                                TrendChart(dayAxis, series(metric, days), metric.label.lowercase())
                            }
                        }
                    }
                    item {
                        val parts = TrafficSource.entries.map { s ->
                            s to published.sumOf { c -> ((c.trafficShare[s] ?: 0f) * c.total(Metric.Views, days)).toLong() }
                        }.filter { it.second > 0 }
                        Card16 {
                            Text("Where views come from", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (parts.isEmpty()) {
                                Text(
                                    "Not tracked yet: views don't record whether they came from the feed, search, a profile or a shared link.",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            } else {
                                Text("On MinedIt · last $days days", color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(12.dp))
                                SourceSplitBar(parts)
                            }
                        }
                    }
                    item {
                        PillRow(
                            listOf("Published (${published.size})", "Drafts (${drafts.size})"), tab, { tab = it },
                            Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (tab == 0) {
                        items(published, key = { it.clip.remoteId ?: it.clip.id.toString() }) { c ->
                            PublishedRow(c, days, live) {
                                Logger.d("Dashboard open published clip ${c.clip.remoteId ?: c.clip.id}")
                                open = c
                            }
                        }
                    } else {
                        item {
                            Text(
                                if (drafts.isEmpty()) "No drafts. New uploads start as drafts, or move a published clip back to drafts to check its virality."
                                else "Only you can see these. Predict how far each one could spread before you publish.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(drafts, key = { "d" + (it.clip.remoteId ?: it.clip.id.toString()) }) { c ->
                            DraftRow(c, repo) {
                                Logger.d("Dashboard open draft ${c.clip.remoteId ?: c.clip.id}")
                                open = c
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = open != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            lastOpen?.let {
                ClipInsights(
                    it, repo,
                    onBack = { open = null },
                    onPublish = { clip ->
                        scope.launch {
                            runCatching { repo.publish(clip) }
                                .onSuccess {
                                    Logger.i("Published to MinedIt: ${clip.remoteId ?: clip.id}")
                                    toast("Published to MinedIt")
                                    open = null
                                    tab = 0
                                    refresh++
                                }
                                .onFailure {
                                    Logger.e("Publish failed", it)
                                    toast(it.message ?: "Publish failed")
                                }
                        }
                    },
                    onUnpublish = { clip ->
                        scope.launch {
                            runCatching { repo.unpublish(clip) }
                                .onSuccess {
                                    Logger.i("Moved to drafts: ${clip.remoteId ?: clip.id}")
                                    toast("Moved to drafts. Hidden from the feed")
                                    open = null
                                    tab = 1
                                    refresh++
                                }
                                .onFailure {
                                    Logger.e("Unpublish failed", it)
                                    toast(it.message ?: "Couldn't move it to drafts")
                                }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun Card16(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun CountTile(label: String, count: Int, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("$count", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatTile(
    metric: Metric,
    /** Null = this metric isn't tracked yet. */
    value: Long?,
    /** Null = no comparison period (live all-time totals). */
    previous: Long?,
    spark: List<Long>,
    periodLabel: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 1.5.dp else 0.dp, if (selected) Gold else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(metric.label, color = TextSecondary, fontSize = 12.sp)
        Text(value?.let { formatCount(it.toInt()) } ?: "—", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        when {
            value == null -> Text("Not tracked yet", color = TextSecondary, fontSize = 11.sp)
            previous == null -> Text("All time", color = TextSecondary, fontSize = 11.sp)
            else -> DeltaText(value, previous, periodLabel)
        }
        if (spark.size >= 2) {
            Spacer(Modifier.height(6.dp))
            Sparkline(spark, Modifier.fillMaxWidth().height(26.dp))
        }
    }
}

/** Change vs the previous period: arrow + text, so it never relies on colour alone. */
@Composable
private fun DeltaText(value: Long, previous: Long, periodLabel: String) {
    if (previous <= 0) {
        Text("New this period", color = TextSecondary, fontSize = 11.sp)
        return
    }
    val pct = (value - previous) * 100 / previous
    val up = pct >= 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null,
            tint = if (up) DashboardColors.Good else DashboardColors.Critical,
            modifier = Modifier.size(12.dp)
        )
        Text(
            "${if (up) "+" else ""}$pct% vs prev $periodLabel",
            color = if (up) DashboardColors.Good else DashboardColors.Critical,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private val PublishedFormat = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US)

@Composable
private fun PublishedDate(publication: Publication?) {
    publication ?: return
    val days = java.time.temporal.ChronoUnit.DAYS.between(publication.publishedOn, java.time.LocalDate.now())
    Text(
        "Published ${publication.publishedOn.format(PublishedFormat)}" + if (days == 0L) " · today" else " · ${days}d ago",
        fontSize = 11.sp,
        color = TextSecondary,
        maxLines = 1
    )
}

@Composable
private fun PublishedRow(c: DashboardClip, days: Int, live: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClipThumbnail(c.clip, Modifier.width(96.dp), compact = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.clip.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PublishedDate(c.publication)
            Text(
                "${formatCount(c.total(Metric.Views, days).toInt())} views · ${formatCount(c.total(Metric.Likes, days).toInt())} likes" +
                    " · ${formatCount(c.total(Metric.Comments, days).toInt())} comments",
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        if (c.history.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Sparkline(c.history.takeLast(days).map { it.views }, Modifier.size(width = 56.dp, height = 28.dp))
        }
    }
}

@Composable
private fun DraftRow(c: DashboardClip, repo: DashboardRepository, onClick: () -> Unit) {
    val scored = ViralityPlatform.entries.firstNotNullOfOrNull { repo.cachedReport(c.clip, it) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClipThumbnail(c.clip, Modifier.width(96.dp), compact = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.clip.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    c.isLocalRecording -> "Recorded on this phone · not posted"
                    else -> "Draft · ${c.clip.game} · ${c.clip.duration}"
                },
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        if (scored != null) {
            Column(horizontalAlignment = Alignment.End) {
                VerdictChip(scored.medianVerdict, score = scored.median.toInt())
                when {
                    scored.rangePending -> Text("checking range…", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                    scored.hasRange -> Text(
                        "range ${scored.low.toInt()}–${scored.high.toInt()}",
                        fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        } else {
            Text(
                "Predict",
                color = Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun verdictStyle(v: Verdict): Pair<Color, ImageVector> = when (v) {
    Verdict.Weak -> DashboardColors.Critical to Icons.AutoMirrored.Filled.TrendingDown
    Verdict.Mixed -> DashboardColors.Serious to Icons.AutoMirrored.Filled.TrendingFlat
    Verdict.Promising -> DashboardColors.Warning to Icons.AutoMirrored.Filled.TrendingUp
    Verdict.Strong -> DashboardColors.Good to Icons.Filled.Whatshot
}

/** Verdict as icon + label (+ optional score); colour is never the only signal. */
@Composable
fun VerdictChip(verdict: Verdict, score: Int? = null) {
    val (color, icon) = verdictStyle(verdict)
    Row(
        Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            (score?.let { "$it · " } ?: "") + verdict.label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** One clip: its stats (if published), publish controls and the virality prediction. */
@Composable
private fun ClipInsights(
    c: DashboardClip,
    repo: DashboardRepository,
    onBack: () -> Unit,
    onPublish: (Clip) -> Unit,
    onUnpublish: (Clip) -> Unit
) {
    val scope = rememberCoroutineScope()
    // MinedIt-only for now: the engine's "general" audience stands in for the MinedIt feed.
    val platform = ViralityPlatform.General
    var report by remember(c.clip.id, platform) { mutableStateOf(repo.cachedReport(c.clip, platform)) }
    var loading by remember(c.clip.id, platform) { mutableStateOf(false) }
    var progress by remember(c.clip.id) { mutableStateOf("") }
    var error by remember(c.clip.id) { mutableStateOf<String?>(null) }
    var metric by rememberSaveable(c.clip.id) { mutableStateOf(Metric.Views) }
    val sincePublish = c.history.dropWhile { it.views == 0L }.takeLast(30)

    fun runPrediction() {
        Logger.i("Predict virality: clip=${c.clip.remoteId ?: c.clip.id} platform=${platform.apiName} live=${repo.isLive}")
        loading = true
        error = null
        scope.launch {
            runCatching { repo.simulate(c.clip, platform) { progress = it } }
                .onSuccess {
                    report = it
                    Logger.i("Virality: ${it.score.toInt()} ${it.verdict}")
                }
                .onFailure {
                    Logger.e("Virality failed", it)
                    error = it.message ?: "Virality check failed"
                }
            loading = false
        }
    }

    // A live check keeps running on the server if you leave: pick it back up when the clip is reopened.
    LaunchedEffect(c.clip.id, repo) {
        if (report == null) {
            when (val s = runCatching { repo.viralityState(c.clip, platform) }.getOrDefault(ViralityState.None)) {
                is ViralityState.Ready -> report = s.report
                ViralityState.Processing -> runPrediction()
                is ViralityState.Failed -> error = s.message
                ViralityState.None -> Unit
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(c.clip.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        c.isPublished -> "Published · ${c.clip.game}"
                        else -> "Draft · only you can see this · ${c.clip.game}"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { ClipThumbnail(c.clip, Modifier.padding(horizontal = 16.dp).fillMaxWidth()) }
            if (c.isPublished) {
                item { Box(Modifier.padding(horizontal = 16.dp)) { PublishedDate(c.publication) } }
                item {
                    val shown = if (repo.isLive) listOf(Metric.Views, Metric.Likes, Metric.Comments) else Metric.entries
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEach { m ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(if (m == metric) 1.5.dp else 0.dp, if (m == metric) Gold else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { metric = m }
                                    .padding(10.dp)
                            ) {
                                Text(m.label, color = TextSecondary, fontSize = 11.sp)
                                val v = if (sincePublish.isEmpty()) c.total(m, 30) else sincePublish.sumOf { it.value(m) }
                                Text(formatCount(v.toInt()), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
                if (sincePublish.size >= 2) item {
                    Card16 {
                        Text("${metric.label} since publishing", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(12.dp))
                        TrendChart(sincePublish.map { it.day }, sincePublish.map { it.value(metric) }, metric.label.lowercase())
                    }
                }
            }

            // Virality prediction
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Gold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Virality prediction", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (repo.isLive) "Scored moment by moment from the video itself"
                        else if (c.isPublished) "How the MinedIt audience reacts to it" else "A synthetic MinedIt audience watches it before you post",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            item {
                val r = report
                when {
                    loading -> Card16 { PredictingState(if (repo.isLive) progress.ifEmpty { "Starting…" } else "Simulating MinedIt viewers", repo.isLive) }
                    r == null -> Card16 {
                        Text(if (repo.isLive) "Not scored yet" else "No prediction yet", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (repo.isLive) "This upload is from before automatic scoring. Scoring every frame takes a few seconds."
                            else "9 AI personas (core fans, adjacent viewers, outsiders) react to the clip and we estimate how far it spreads.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = DashboardColors.Critical, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = ::runPrediction,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (error != null) "Try again" else if (repo.isLive) "Score virality" else "Predict virality", fontWeight = FontWeight.Bold)
                        }
                    }
                    // Re-read the repository so extra panel scores show up as they arrive.
                    else -> ViralityReportView(repo.cachedReport(c.clip, platform)?.takeIf { it.score == r.score } ?: r)
                }
            }
            if (!c.isPublished) item {
                Button(
                    onClick = {
                        Logger.i("Publish to MinedIt from dashboard: clip=${c.clip.remoteId ?: c.clip.id}")
                        onPublish(c.clip)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Publish, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Publish to MinedIt", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PredictingState(detail: String, live: Boolean) {
    val pulse by rememberInfiniteTransition(label = "predict").animateFloat(
        0.92f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "predictPulse"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .scale(pulse)
                .background(Gold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(BrainIcon, null, tint = Color.Black, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(if (live) "Scoring every frame…" else "Running 9 audience personas…", fontWeight = FontWeight.SemiBold)
            Text(detail, color = TextSecondary, fontSize = 12.sp)
            if (live) Text("Takes a few seconds.", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

/** 0-100 track with the verdict cut-offs, the spread of panel scores as a band, and the median as a dot. */
@Composable
private fun ScoreRange(r: ViralityReport) {
    val track = Color.White.copy(alpha = 0.10f)
    val tick = Color.White.copy(alpha = 0.35f)
    val band = Gold.copy(alpha = 0.45f)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val y = size.height / 2
        val x = { v: Float -> v.coerceIn(0f, 100f) / 100f * size.width }
        drawLine(track, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // Verdict cut-offs (weak | mixed | promising | strong)
        listOf(35f, 55f, 75f).forEach {
            drawLine(tick, androidx.compose.ui.geometry.Offset(x(it), y - 6.dp.toPx()), androidx.compose.ui.geometry.Offset(x(it), y + 6.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
        if (r.hasRange) drawLine(
            band, androidx.compose.ui.geometry.Offset(x(r.low), y), androidx.compose.ui.geometry.Offset(x(r.high), y),
            strokeWidth = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawCircle(Color(0xFF15161C), radius = 7.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x(r.median), y))
        drawCircle(Gold, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x(r.median), y))
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("Weak" to 0.35f, "Mixed" to 0.20f, "Promising" to 0.20f, "Strong" to 0.25f).forEach { (label, w) ->
            Text(label, fontSize = 10.sp, color = TextSecondary, modifier = Modifier.weight(w))
        }
    }
}

private val SignalLabels = mapOf(
    "visual" to "Looks like a highlight",
    "scene" to "Sustained scene",
    "novelty" to "Visual change",
    "visibility" to "Readable frame",
    "event" to "Detected events",
    "speech" to "Reaction speech"
)

/** Live backend: the frame scorer's score, its peak, the moment-by-moment curve and the signals behind it. */
@Composable
private fun FrameViralityView(r: ViralityReport) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card16 {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${r.score.toInt()}", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 56.sp)
                Text(" /100", fontSize = 18.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(bottom = 10.dp)) { VerdictChip(r.verdict) }
            }
            Text("Virality score from every frame of the clip", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            ScoreRange(r)
            r.peakOffsetMs?.let {
                Spacer(Modifier.height(10.dp))
                Text("Strongest moment at %d:%02d".format(it / 60_000, it / 1000 % 60), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (r.curve.size >= 2) Card16 {
            Text("Moment by moment", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Score of each sampled frame. Tap or drag to inspect.", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            MomentCurve(r.curve, r.peakOffsetMs)
        }
        if (r.components.isNotEmpty()) Card16 {
            Text("What drives the score", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Average over the clip's strongest frames, 0–100", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            r.components.sortedByDescending { it.second }.forEach { (key, v) ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(SignalLabels[key] ?: key, fontSize = 13.sp, modifier = Modifier.width(150.dp))
                    Meter(v / 100f, Modifier.weight(1f))
                    Text("${v.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp).padding(start = 10.dp))
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp)) {
            Icon(Icons.Filled.Info, null, tint = DashboardColors.AxisText, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(r.disclaimer, color = DashboardColors.AxisText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ViralityReportView(r: ViralityReport) {
    if (r.isFrameScore) return FrameViralityView(r)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Hero score + verdict
        Card16 {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${r.median.toInt()}", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 56.sp)
                Text(" /100", fontSize = 18.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(bottom = 10.dp)) { VerdictChip(r.medianVerdict) }
            }
            Text(
                if (r.hasRange) "Virality score on ${r.platform.label} · median of ${r.samples.size} audience panels"
                else "Virality score on ${r.platform.label}",
                color = TextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            ScoreRange(r)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    r.rangePending -> "Running 2 more audience panels to see how consistent this score is…"
                    !r.hasRange -> "Each run uses a freshly generated AI audience, so scores vary by a few points."
                    r.borderline -> "Range ${r.low.toInt()}–${r.high.toInt()}: borderline. Panels split between " +
                        "${verdictFor(r.low).label} and ${verdictFor(r.high).label}."
                    else -> "Range ${r.low.toInt()}–${r.high.toInt()}: every panel agrees it's ${r.medianVerdict.label}."
                },
                fontSize = 12.sp,
                color = if (r.borderline && !r.rangePending) DashboardColors.Serious else TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Text(r.cascadeSummary, fontSize = 14.sp)
            Text("Audience: ${r.inferredAudience}", color = TextSecondary, fontSize = 12.sp)
        }
        // Cascade
        Card16 {
            Text("How far it spreads", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Each ring must beat its threshold (white mark) to reach the next" +
                    if (r.hasRange) ". Breakdown and personas below are from the full panel (score ${r.score.toInt()})." else "",
                color = TextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            r.layers.forEach { l ->
                Column(Modifier.padding(vertical = 6.dp).alpha(if (l.exposed) 1f else 0.45f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.layer.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${l.score.toInt()}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(10.dp))
                        val (icon, text, color) = when {
                            !l.exposed -> Triple(Icons.Filled.Block, "Not reached", DashboardColors.AxisText)
                            l.cleared -> Triple(Icons.Filled.CheckCircle, "Advanced", DashboardColors.Good)
                            else -> Triple(Icons.Filled.Cancel, "Stopped", DashboardColors.Critical)
                        }
                        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(76.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Meter(l.score / 100f, threshold = l.threshold / 100f)
                    Text("${l.personaCount} personas · needs ${l.threshold.toInt()}", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
        // Simulated engagement (already percentages: don't multiply)
        Card16 {
            Text("Simulated engagement", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Watch" to r.watchRate, "Completion" to r.completionRate, "Like" to r.likeRate,
                "Comment" to r.commentRate, "Share" to r.shareRate
            ).forEach { (label, pct) ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 13.sp, modifier = Modifier.width(88.dp))
                    Meter(pct / 100f, Modifier.weight(1f))
                    Text("${pct.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(48.dp).padding(start = 10.dp))
                }
            }
        }
        // Personas
        Column {
            Text("What the personas said", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(r.personas) { p -> PersonaCard(p) }
            }
        }
        Card16 {
            InsightList("Strengths", Icons.Filled.CheckCircle, DashboardColors.Good, r.strengths)
            Spacer(Modifier.height(12.dp))
            InsightList("Risks", Icons.Filled.Warning, DashboardColors.Serious, r.risks)
            Spacer(Modifier.height(12.dp))
            InsightList("Recommended changes", Icons.Filled.AutoAwesome, Gold, r.changes)
        }
        Row(Modifier.padding(horizontal = 16.dp)) {
            Icon(Icons.Filled.Info, null, tint = DashboardColors.AxisText, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(r.disclaimer, color = DashboardColors.AxisText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PersonaCard(p: PersonaReview) {
    Column(
        Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, 32.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Text(p.layer.label, color = TextSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Hook ${p.hookScore} · ${p.primaryAction.label}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("“${p.reaction}”", fontSize = 13.sp, minLines = 2, maxLines = 3)
        Spacer(Modifier.height(4.dp))
        Text("Friction: ${p.friction}", color = TextSecondary, fontSize = 11.sp, maxLines = 2)
    }
}

@Composable
private fun InsightList(title: String, icon: ImageVector, tint: Color, items: List<String>) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    items.forEach {
        Row(Modifier.padding(top = 6.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp).padding(top = 1.dp))
            Spacer(Modifier.width(8.dp))
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
