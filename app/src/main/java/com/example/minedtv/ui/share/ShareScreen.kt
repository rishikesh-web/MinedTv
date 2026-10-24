package com.example.minedtv.ui.share

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.minedtv.data.Clip
import com.example.minedtv.data.SampleData
import com.example.minedtv.data.share.*
import com.example.minedtv.ui.components.BrainIcon
import com.example.minedtv.ui.components.ClipThumbnail
import com.example.minedtv.ui.components.Pill
import com.example.minedtv.ui.components.PillRow
import com.example.minedtv.ui.theme.Gold
import com.example.minedtv.ui.theme.LiveRed
import com.example.minedtv.ui.theme.OnlineGreen
import com.example.minedtv.util.Logger
import kotlinx.coroutines.delay
import com.example.minedtv.ui.components.ConfettiBurst

/** Full-screen "post to TikTok / Reels" flow. [sessionKey] gives each opening a fresh ViewModel. */
@Composable
fun ShareScreen(clip: Clip, sessionKey: Int, onClose: () -> Unit) {
    val vm: ShareViewModel = viewModel(key = "share-$sessionKey") { ShareViewModel(clip) }
    val state by vm.state.collectAsState()
    val goBack = { if (!vm.back()) onClose() }

    BackHandler(onBack = goBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = goBack) {
                val atRoot = state.step == ShareStep.Platform || state.step == ShareStep.Done
                Icon(if (atRoot) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(titleFor(state), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            state.platform?.let { PlatformIcon(it, 28.dp, Modifier.padding(end = 12.dp)) }
        }

        AnimatedContent(
            targetState = state.step,
            modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "shareStep"
        ) { step ->
            when (step) {
                ShareStep.Platform -> PlatformStep(state.clip, vm::selectPlatform)
                ShareStep.Mode -> ModeStep(state, vm::selectMode)
                ShareStep.Direct -> DirectStep(state, vm::setCaption, vm::publish) { vm.selectMode(ShareMode.AiEnhanced) }
                ShareStep.AiOptions -> AiOptionsStep(state, vm::updateOptions, vm::startEnhance)
                ShareStep.Processing -> ProcessingStep(state, onRetry = vm::startEnhance, onCancel = { vm.back() })
                ShareStep.Review -> ReviewStep(state, vm::setCaption, vm::toggleHashtag, vm::startEnhance, vm::publish)
                ShareStep.Publishing -> PublishingStep(state, vm::publish)
                ShareStep.Done -> DoneStep(state, onClose)
            }
        }
    }
}

private fun titleFor(state: ShareUiState): String = when (state.step) {
    ShareStep.Platform -> "Share clip"
    ShareStep.Mode -> "Share to ${state.platform?.displayName}"
    ShareStep.Direct -> "Share directly"
    ShareStep.AiOptions -> "Enhance with AI"
    ShareStep.Processing -> "Enhancing…"
    ShareStep.Review -> "Review"
    ShareStep.Publishing -> "Posting"
    ShareStep.Done -> "Posted"
}

// ---------- Steps ----------

@Composable
private fun PlatformStep(clip: Clip, onSelect: (SharePlatform) -> Unit) {
    StepColumn {
        ClipSummary(clip)
        StepHeading("Where do you want to post?")
        SharePlatform.entries.forEach { platform ->
            OptionCard(onClick = { onSelect(platform) }) {
                PlatformIcon(platform, 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(platform.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${platform.aspectRatio} vertical · up to ${formatDuration(platform.maxSeconds)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModeStep(state: ShareUiState, onSelect: (ShareMode) -> Unit) {
    val platform = state.platform ?: return
    val clipSeconds = state.clip.durationSeconds
    StepColumn {
        ClipSummary(state.clip)
        StepHeading("How should we post it?")
        ModeCard(
            icon = Icons.AutoMirrored.Filled.Send,
            title = "Share directly",
            subtitle = "Post the clip exactly as recorded (${state.clip.duration}, 16:9).",
            bullets = emptyList(),
            highlight = false
        ) { onSelect(ShareMode.Direct) }
        ModeCard(
            icon = Icons.Filled.AutoAwesome,
            title = "Enhance with AI",
            subtitle = "We check what's trending on ${platform.displayName} right now and edit your clip to match.",
            bullets = listOf(
                "Reframed to ${platform.aspectRatio} around the action",
                "Auto-generated captions",
                if (clipSeconds > platform.idealSeconds) "Trimmed to ~${platform.idealSeconds}s, the current sweet spot"
                else "Length already fits ${platform.displayName}",
                "Trending caption & hashtags"
            ),
            highlight = true
        ) { onSelect(ShareMode.AiEnhanced) }
    }
}

@Composable
private fun DirectStep(
    state: ShareUiState,
    onCaption: (String) -> Unit,
    onPost: () -> Unit,
    onTryAi: () -> Unit
) {
    val platform = state.platform ?: return
    StepColumn {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PhonePreview(state.clip, vertical = false, caption = null)
        }
        NoticeCard(
            icon = Icons.Filled.Info,
            text = "Your clip is 16:9, so ${platform.displayName} will show it with black bars.",
            action = "Fix with AI",
            onAction = onTryAi
        )
        if (state.clip.durationSeconds > platform.maxSeconds) {
            NoticeCard(
                icon = Icons.Filled.Warning,
                text = "This clip is longer than ${platform.displayName} allows (${formatDuration(platform.maxSeconds)}).",
                action = null,
                onAction = {}
            )
        }
        CaptionField(state.caption, onCaption)
        PrimaryButton("Post to ${platform.displayName}", Icons.AutoMirrored.Filled.Send, onPost)
    }
}

@Composable
private fun AiOptionsStep(state: ShareUiState, onOptions: (EnhanceOptions) -> Unit, onEnhance: () -> Unit) {
    val platform = state.platform ?: return
    val o = state.options
    val clipSeconds = state.clip.durationSeconds
    val maxTarget = maxOf(11, minOf(clipSeconds, platform.maxSeconds))

    StepColumn {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PhonePreview(state.clip, vertical = o.reframe, caption = if (o.captions) "WAIT FOR IT…" else null)
        }
        SettingsCard {
            ToggleRow(Icons.Filled.Crop, "Auto-reframe to ${platform.aspectRatio}", "Keeps the action centered", o.reframe) {
                onOptions(o.copy(reframe = it))
            }
            ToggleRow(Icons.Filled.ClosedCaption, "Auto captions", "Hook text + key moments", o.captions) {
                onOptions(o.copy(captions = it))
            }
            if (o.captions) {
                PillRow(
                    CaptionStyle.entries.map { it.label },
                    o.captionStyle.ordinal,
                    { onOptions(o.copy(captionStyle = CaptionStyle.entries[it])) },
                    Modifier.padding(start = 40.dp)
                )
            }
            ToggleRow(Icons.Filled.ContentCut, "Smart trim", "Cut to the best part", o.autoTrim) {
                onOptions(o.copy(autoTrim = it))
            }
            if (o.autoTrim) {
                Column(Modifier.padding(start = 40.dp)) {
                    Text(
                        "Target ${formatDuration(o.targetSeconds)} of ${state.clip.duration} · trending on ${platform.displayName}: ~${platform.idealSeconds}s",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = o.targetSeconds.toFloat(),
                        onValueChange = { onOptions(o.copy(targetSeconds = it.toInt())) },
                        valueRange = 10f..maxTarget.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold)
                    )
                }
            }
            ToggleRow(Icons.Filled.Tag, "Trending hashtags", "Picked from what's hot this week", o.trendHashtags) {
                onOptions(o.copy(trendHashtags = it))
            }
            ToggleRow(Icons.Filled.MusicNote, "Trending audio", "Mix a popular sound under game audio", o.trendingAudio) {
                onOptions(o.copy(trendingAudio = it))
            }
        }
        PrimaryButton("Enhance with AI", Icons.Filled.AutoAwesome, onEnhance)
    }
}

@Composable
private fun ProcessingStep(state: ShareUiState, onRetry: () -> Unit, onCancel: () -> Unit) {
    val job = state.job
    val stages = state.options.stages()
    val overall = when (job) {
        is EnhanceJobStatus.Uploading -> job.progress * 0.15f
        is EnhanceJobStatus.Processing -> 0.15f + job.progress * 0.85f
        is EnhanceJobStatus.Completed -> 1f
        else -> 0f
    }
    val currentIndex = when (job) {
        is EnhanceJobStatus.Uploading -> -1
        is EnhanceJobStatus.Processing -> stages.indexOf(job.stage)
        is EnhanceJobStatus.Completed -> stages.size
        else -> -1
    }
    val pulse by rememberInfiniteTransition(label = "brain").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "brainScale"
    )
    val animatedOverall by animateFloatAsState(overall, label = "overall")

    StepColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .size(104.dp)
                .scale(if (state.error == null) pulse else 1f)
                .background(Gold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(BrainIcon, null, tint = Color.Black, modifier = Modifier.size(64.dp))
        }
        Text(
            when (job) {
                is EnhanceJobStatus.Processing -> "${job.stage.label}…"
                is EnhanceJobStatus.Completed -> "Done!"
                else -> "Uploading clip…"
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        LinearProgressIndicator(
            progress = { animatedOverall },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            color = Gold,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text("${(animatedOverall * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        SettingsCard {
            StageRow("Uploading clip", done = job !is EnhanceJobStatus.Uploading, active = job is EnhanceJobStatus.Uploading)
            stages.forEachIndexed { i, stage ->
                StageRow(stage.label, done = i < currentIndex, active = i == currentIndex)
            }
        }
        state.error?.let { ErrorCard(it, onRetry) }
        TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ReviewStep(
    state: ShareUiState,
    onCaption: (String) -> Unit,
    onToggleHashtag: (String) -> Unit,
    onRegenerate: () -> Unit,
    onPost: () -> Unit
) {
    val result = state.result ?: return
    val platform = state.platform ?: return
    var captionIndex by remember(result.jobId) { mutableIntStateOf(0) }
    LaunchedEffect(result.jobId) {
        while (result.captions.isNotEmpty()) {
            delay(1400)
            captionIndex = (captionIndex + 1) % result.captions.size
        }
    }

    StepColumn {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PhonePreview(
                state.clip,
                vertical = result.aspectRatio != "16:9",
                caption = result.captions.getOrNull(captionIndex)?.text,
                badge = "AI"
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BeforeAfter("Format", "16:9", result.aspectRatio, Modifier.weight(1f))
            BeforeAfter("Length", state.clip.duration, formatDuration(result.durationSeconds), Modifier.weight(1f))
            BeforeAfter("Captions", "None", if (result.captions.isEmpty()) "None" else "${result.captions.size} lines", Modifier.weight(1f))
        }
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Whatshot, null, tint = Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Why these edits", fontWeight = FontWeight.Bold)
            }
            result.trendInsights.forEach { insight ->
                Row {
                    Text("•", color = Gold, modifier = Modifier.padding(end = 8.dp))
                    Text(insight, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        CaptionField(state.caption, onCaption)
        if (state.hashtags.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.hashtags.forEach { tag ->
                    Pill(tag, tag in state.selectedHashtags) { onToggleHashtag(tag) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onRegenerate,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Regenerate", color = Color.White, maxLines = 1)
            }
            Button(
                onClick = onPost,
                modifier = Modifier.weight(1.3f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Post to ${platform.displayName}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PublishingStep(state: ShareUiState, onRetry: () -> Unit) {
    val platform = state.platform ?: return
    val progress by animateFloatAsState(state.publishProgress, label = "publish")
    StepColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        PlatformIcon(platform, 88.dp)
        Text("Posting to ${platform.displayName}…", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            color = Gold,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text("${(progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.error?.let { ErrorCard(it, onRetry) }
    }
}

@Composable
private fun DoneStep(state: ShareUiState, onClose: () -> Unit) {
    val platform = state.platform ?: return
    val checkScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) { checkScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f)) }
    Box(Modifier.fillMaxSize()) {
    StepColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier
                .size(96.dp)
                .scale(checkScale.value)
                .background(OnlineGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(56.dp))
        }
        Text("Posted to ${platform.displayName}!", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (state.mode == ShareMode.AiEnhanced) "Your AI-enhanced clip is live." else "Your clip is live.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { Logger.d("View post tapped: ${state.postUrl}") },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("View on ${platform.displayName}", color = Color.White) }
        PrimaryButton("Done", Icons.Filled.Check, onClose)
    }
    ConfettiBurst(Modifier.matchParentSize())
    }
}

// ---------- Building blocks ----------

@Composable
private fun StepColumn(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}

@Composable
private fun StepHeading(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ClipSummary(clip: Clip) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClipThumbnail(clip, Modifier.width(110.dp), compact = true)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(clip.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${clip.game} · ${clip.duration} · 16:9",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun PlatformIcon(platform: SharePlatform, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(platform.colors)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (platform == SharePlatform.TikTok) Icons.Filled.MusicNote else Icons.Filled.CameraAlt,
            platform.displayName,
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
private fun OptionCard(
    onClick: () -> Unit,
    highlight: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                if (highlight) 2.dp else 1.dp,
                if (highlight) Gold else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    bullets: List<String>,
    highlight: Boolean,
    onClick: () -> Unit
) {
    OptionCard(onClick = onClick, highlight = highlight) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(if (highlight) Gold else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = if (highlight) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (highlight) {
                    Text(
                        "RECOMMENDED",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(Gold, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            bullets.forEach { bullet ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = Gold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(bullet, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Mock phone frame showing how the post will look on a 9:16 feed. */
@Composable
private fun PhonePreview(
    clip: Clip,
    vertical: Boolean,
    caption: String?,
    badge: String? = null,
    width: Dp = 190.dp
) {
    Box(
        Modifier
            .width(width)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black)
            .border(2.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
    ) {
        if (vertical) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(clip.colors))) {
                Icon(
                    Icons.Filled.SportsEsports, null,
                    tint = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.align(Alignment.Center).size(96.dp)
                )
            }
        } else {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Brush.linearGradient(clip.colors))
            ) {
                Icon(
                    Icons.Filled.SportsEsports, null,
                    tint = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.align(Alignment.Center).size(48.dp)
                )
            }
        }
        if (caption != null) {
            Text(
                caption,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 70.dp)
                    .padding(horizontal = 14.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        if (badge != null) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Gold, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(badge, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        // Fake feed UI
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            listOf(Icons.Outlined.FavoriteBorder, Icons.Outlined.ChatBubbleOutline, Icons.Outlined.Share).forEach {
                Icon(it, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 40.dp, bottom = 12.dp)) {
            Text("@${SampleData.currentUser.lowercase()}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(clip.title, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Gold, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Color.Black)
        )
    }
}

@Composable
private fun StageRow(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(Icons.Filled.CheckCircle, null, tint = OnlineGreen, modifier = Modifier.size(22.dp))
                active -> CircularProgressIndicator(Modifier.size(18.dp), color = Gold, strokeWidth = 2.dp)
                else -> Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (done || active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun BeforeAfter(label: String, before: String, after: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text(before, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Icon(Icons.Filled.ArrowDownward, null, tint = Gold, modifier = Modifier.size(14.dp))
        Text(after, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun NoticeCard(icon: ImageVector, text: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Gold.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = onAction) { Text(action, color = Gold, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LiveRed.copy(alpha = 0.15f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.ErrorOutline, null, tint = LiveRed)
        Spacer(Modifier.width(10.dp))
        Text(message, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Retry", color = LiveRed, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun CaptionField(caption: String, onCaption: (String) -> Unit) {
    OutlinedTextField(
        value = caption,
        onValueChange = onCaption,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Caption") },
        minLines = 2,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            focusedLabelColor = Gold,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun PrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
