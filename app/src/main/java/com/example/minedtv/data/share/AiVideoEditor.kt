package com.example.minedtv.data.share

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.minedtv.util.Logger
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Renders the AI edit on the phone with Media3 Transformer: trims to the chosen window,
 * center-crops to 9:16 and burns in the captions. The server decides *what* to do (Gemini
 * creator pack); the phone does the pixel work, so no server-side render is needed.
 * Every edit ends with a short MinedTv end card ([EndCard]), like TikTok's outro.
 */
@OptIn(UnstableApi::class)
object AiVideoEditor {

    data class Edit(
        val startMs: Long,
        val endMs: Long,
        val vertical: Boolean,
        /** Caption lines in output time (0 = first frame of the trimmed video). */
        val captions: List<CaptionSegment>,
        val style: CaptionStyle,
        /** Creator shown on the end card; null = no end card. */
        val endCardHandle: String? = null
    )

    private const val END_CARD_MS = 2_500L

    suspend fun render(context: Context, source: File, edit: Edit, onProgress: (Float) -> Unit): File {
        val out = File(context.cacheDir, "share").apply { mkdirs() }.resolve("minedtv_ai_${System.currentTimeMillis()}.mp4")
        val mediaItem = MediaItem.Builder()
            .setUri(android.net.Uri.fromFile(source))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(edit.startMs)
                    .setEndPositionMs(edit.endMs)
                    .build()
            )
            .build()
        // Both parts must come out the same size: 720x1280 when reframed, else the source's shape at 720p.
        val (outW, outH) = if (edit.vertical) 720 to 1280 else outputSize(source)
        val videoEffects = buildList {
            add(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            if (edit.captions.isNotEmpty()) add(OverlayEffect(ImmutableList.of<androidx.media3.effect.TextureOverlay>(CaptionOverlay(edit.captions, edit.style))))
        }
        val clip = EditedMediaItem.Builder(mediaItem).setEffects(Effects(listOf(), videoEffects)).build()
        val items = mutableListOf(clip)
        edit.endCardHandle?.let { handle ->
            val card = EndCard.render(context, outW, outH, handle)
            items += EditedMediaItem.Builder(
                MediaItem.Builder().setUri(android.net.Uri.fromFile(card)).setImageDurationMs(END_CARD_MS).build()
            )
                .setFrameRate(30)
                .setEffects(Effects(listOf(), listOf(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT), slowPushIn())))
                .build()
        }
        // The end card is a still image with no sound; force an audio track so it gets silence.
        val composition = Composition.Builder(EditedMediaItemSequence(items)).experimentalSetForceAudioTrack(true).build()
        Logger.i("AI render: ${edit.startMs}-${edit.endMs}ms ${outW}x$outH captions=${edit.captions.size} endCard=${edit.endCardHandle != null} -> ${out.name}")

        // Transformer must be driven from a Looper thread.
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            Logger.i("AI render done: ${result.durationMs}ms, ${out.length() / 1024} KB")
                            if (cont.isActive) cont.resume(out)
                        }

                        override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                            Logger.e("AI render failed", exception)
                            if (cont.isActive) cont.resumeWithException(exception)
                        }
                    })
                    .build()
                transformer.start(composition, out.absolutePath)
                val holder = ProgressHolder()
                val poller = kotlinx.coroutines.MainScope().launch {
                    while (cont.isActive) {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                        delay(250)
                    }
                }
                cont.invokeOnCancellation {
                    poller.cancel()
                    transformer.cancel()
                }
            }
        }
    }

    /** Output size for an un-reframed edit: the source's display shape, 720 on the short edge. */
    private fun outputSize(source: File): Pair<Int, Int> = android.media.MediaMetadataRetriever().run {
        try {
            setDataSource(source.absolutePath)
            var w = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            var h = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val rotation = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation % 180 != 0) w = h.also { h = w }
            val scale = 720f / minOf(w, h)
            (Math.round(w * scale / 2) * 2) to (Math.round(h * scale / 2) * 2)
        } finally {
            release()
        }
    }

    /** End card motion: a slow 6% push-in over its duration (zooming in never shows empty edges). */
    private fun slowPushIn() = object : androidx.media3.effect.MatrixTransformation {
        private var firstUs = -1L
        override fun getMatrix(presentationTimeUs: Long): android.graphics.Matrix {
            if (firstUs < 0) firstUs = presentationTimeUs
            val t = ((presentationTimeUs - firstUs) / (END_CARD_MS * 1000f)).coerceIn(0f, 1f)
            val eased = 1 - (1 - t) * (1 - t)
            return android.graphics.Matrix().apply { setScale(1f + 0.06f * eased, 1f + 0.06f * eased) }
        }
    }

    /** Timed caption lines drawn in the lower third of each frame. */
    private class CaptionOverlay(private val captions: List<CaptionSegment>, private val style: CaptionStyle) : TextOverlay() {
        private var firstFrameUs = -1L
        private val blank = SpannableString(" ")

        override fun getText(presentationTimeUs: Long): SpannableString {
            // Clipped items may not start at 0, so measure from the first frame we see.
            if (firstFrameUs < 0) firstFrameUs = presentationTimeUs
            val ms = (presentationTimeUs - firstFrameUs) / 1000
            val line = captions.firstOrNull { ms >= it.startMs && ms < it.endMs } ?: return blank
            return styled(wrap(line.text))
        }

        // TextOverlay renders at a fixed 100px and sizes its bitmap from that, so size spans would
        // clip long lines; scale the finished bitmap instead.
        private val scale = when (style) {
            CaptionStyle.Bold -> 0.6f
            CaptionStyle.Minimal -> 0.45f
            CaptionStyle.Gamer -> 0.62f
        }

        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
            OverlaySettings.Builder().setBackgroundFrameAnchor(0f, -0.45f).setScale(scale, scale).build()

        private fun styled(text: String): SpannableString {
            val s = SpannableString(if (style == CaptionStyle.Gamer) text.uppercase() else text)
            val all = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            s.setSpan(android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER), 0, s.length, all)
            when (style) {
                CaptionStyle.Bold -> {
                    s.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, s.length, all)
                    s.setSpan(ForegroundColorSpan(Color.WHITE), 0, s.length, all)
                    s.setSpan(BackgroundColorSpan(Color.argb(170, 0, 0, 0)), 0, s.length, all)
                }
                CaptionStyle.Minimal -> {
                    s.setSpan(ForegroundColorSpan(Color.WHITE), 0, s.length, all)
                    s.setSpan(BackgroundColorSpan(Color.argb(110, 0, 0, 0)), 0, s.length, all)
                }
                CaptionStyle.Gamer -> {
                    s.setSpan(StyleSpan(android.graphics.Typeface.BOLD_ITALIC), 0, s.length, all)
                    s.setSpan(ForegroundColorSpan(Color.rgb(255, 196, 46)), 0, s.length, all)
                    s.setSpan(BackgroundColorSpan(Color.argb(190, 0, 0, 0)), 0, s.length, all)
                }
            }
            return s
        }

        /** TextOverlay doesn't wrap, so break long lines ourselves (~20 chars fits a 720px frame at this scale). */
        private fun wrap(text: String, max: Int = 20): String {
            val lines = mutableListOf<String>()
            var line = ""
            for (word in text.split(" ")) {
                line = if (line.isEmpty()) word
                else if (line.length + 1 + word.length <= max) "$line $word"
                else { lines += line; word }
            }
            if (line.isNotEmpty()) lines += line
            return lines.take(3).joinToString("\n")
        }
    }
}
