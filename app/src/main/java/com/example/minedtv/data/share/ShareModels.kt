package com.example.minedtv.data.share

import androidx.compose.ui.graphics.Color
import com.example.minedtv.data.Clip

/** Short-form platforms we can post to, with the specs the AI editor targets. */
enum class SharePlatform(
    val displayName: String,
    val maxSeconds: Int,
    /** Length that currently performs best for gaming clips (would come from the trends API). */
    val idealSeconds: Int,
    val aspectRatio: String,
    val colors: List<Color>
) {
    TikTok("TikTok", 600, 25, "9:16", listOf(Color(0xFF25F4EE), Color(0xFF000000), Color(0xFFFE2C55))),
    InstagramReels("Instagram Reels", 180, 20, "9:16", listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF))),
    /** MinedIt's own short-video feed: the AI edit is uploaded as a reel of the clip and published. */
    MinedIt("MinedIt Nuggets", 60, 25, "9:16", listOf(Color(0xFFFFC53D), Color(0xFFFF9F1C)))
}

enum class ShareMode { Direct, AiEnhanced }

enum class CaptionStyle(val label: String) { Bold("Bold"), Minimal("Minimal"), Gamer("Gamer") }

/** What the user asked the AI editor to do. */
data class EnhanceOptions(
    val reframe: Boolean = true,
    val captions: Boolean = true,
    val captionStyle: CaptionStyle = CaptionStyle.Bold,
    val autoTrim: Boolean = true,
    val targetSeconds: Int = 25,
    val trendHashtags: Boolean = true,
    /** Server-picked licensed background music (mood-matched, ducked under speech). */
    val music: Boolean = true,
    /** Server cut: false = the single best moment, true = a montage of the best moments. */
    val montage: Boolean = false
)

enum class EnhanceStage(val label: String) {
    AnalyzingTrends("Watching the clip with AI"),
    DetectingHighlights("Finding the best moment"),
    Reframing("Reframing to 9:16"),
    Captioning("Generating captions"),
    Trimming("Trimming to the sweet spot"),
    Rendering("Rendering final video")
}

/** Pipeline stages the server runs for a given set of options, in order. */
fun EnhanceOptions.stages(): List<EnhanceStage> = buildList {
    add(EnhanceStage.AnalyzingTrends)
    add(EnhanceStage.DetectingHighlights)
    if (reframe) add(EnhanceStage.Reframing)
    if (captions) add(EnhanceStage.Captioning)
    if (autoTrim) add(EnhanceStage.Trimming)
    add(EnhanceStage.Rendering)
}

data class EnhanceRequest(
    val clipId: Int,
    val sourceUrl: String,
    val title: String,
    val game: String,
    val sourceSeconds: Int,
    val platform: SharePlatform,
    val options: EnhanceOptions,
    /** Real video sources (any one); none = sample clip, simulated edit. */
    val remoteClipId: String? = null,
    val localPath: String? = null,
    val sessionId: String? = null,
    val startMs: Long = 0,
    val endMs: Long = 0,
    /** Handle shown on the MinedIt end card. */
    val creator: String = ""
)

data class CaptionSegment(val startMs: Long, val endMs: Long, val text: String)

data class EnhanceResult(
    val jobId: String,
    val videoUrl: String,
    val durationSeconds: Int,
    val aspectRatio: String,
    val captions: List<CaptionSegment>,
    val suggestedCaption: String,
    val hashtags: List<String>,
    /** Human-readable reasons for the edits, shown on the review screen. */
    val trendInsights: List<String>,
    /** CC BY credit line for the mixed-in music track; must be shown and posted with the video. */
    val musicCredit: String? = null
)

sealed interface EnhanceJobStatus {
    data class Uploading(val progress: Float) : EnhanceJobStatus
    data class Processing(val stage: EnhanceStage, val progress: Float) : EnhanceJobStatus
    data class Completed(val result: EnhanceResult) : EnhanceJobStatus
    data class Failed(val message: String) : EnhanceJobStatus
}

data class PublishRequest(
    val platform: SharePlatform,
    val videoUrl: String,
    val caption: String,
    val hashtags: List<String>,
    /** Backend clip to export as MP4; null for sample clips. */
    val remoteClipId: String? = null,
    /** A session range to save as a clip first (e.g. a search moment), then export. */
    val sessionId: String? = null,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val title: String = "",
    /** A clip recorded on this phone: shared directly from this file. */
    val localPath: String? = null
)

sealed interface PublishStatus {
    data class Uploading(val progress: Float) : PublishStatus
    data class Published(val postUrl: String) : PublishStatus
    /** Video was handed to the platform's app; the user finishes posting there. */
    data class OpenedInApp(val appName: String, val captionCopied: Boolean) : PublishStatus
    data class Failed(val message: String) : PublishStatus
}

val Clip.durationSeconds: Int
    get() = duration.split(":").let { it[0].toInt() * 60 + it[1].toInt() }

fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
