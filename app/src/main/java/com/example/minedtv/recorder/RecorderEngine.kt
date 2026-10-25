package com.example.minedtv.recorder

import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Display
import com.example.minedtv.util.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Continuous screen + mic recording into rolling segments, with "clip the last N seconds".
 * Screen -> VirtualDisplay -> VideoEncoder -> SegmentWriter <- MicEncoder.
 */
class RecorderEngine(
    private val context: Context,
    private val projection: MediaProjection,
    private val settings: RecorderSettings
) {
    private val segmentDir = File(context.cacheDir, "replay").apply { deleteRecursively(); mkdirs() }
    private val writer = SegmentWriter(segmentDir, SEGMENT_US)
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clipExecutor = Executors.newSingleThreadExecutor()

    private var video: VideoEncoder? = null
    private var audio: AudioEncoder? = null
    private var display: VirtualDisplay? = null
    private var generation = 0

    var width = 0; private set
    var height = 0; private set
    val hasMic get() = audio?.hasMic == true
    val hasGameAudio get() = audio?.hasGameAudio == true

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) onRotation()
        }
    }

    /** Both audio sources need RECORD_AUDIO; the caller passes false for each when it isn't granted. */
    fun start(micEnabled: Boolean, gameAudioEnabled: Boolean) {
        updateKeep()
        if (micEnabled || gameAudioEnabled) {
            audio = runCatching {
                AudioEncoder(projection.takeIf { gameAudioEnabled }, micEnabled, writer::setAudioFormat, writer::writeAudio)
            }.onFailure { Logger.w("No audio, recording video only: ${it.message}") }.getOrNull()
        }
        writer.wantAudio = audio != null
        val (w, h, dpi) = captureSize()
        val encoder = newVideoEncoder(w, h)
        display = projection.createVirtualDisplay(
            "MinedTv-recorder", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, encoder.surface, null, null
        )
        audio?.start()
        displayManager.registerDisplayListener(displayListener, mainHandler)
        Logger.i("Recording started: ${w}x$h, mic=$hasMic, gameAudio=$hasGameAudio, keep=${writer.keep} segments")
    }

    private fun updateKeep() {
        writer.keep = (Recorder.settings.value.clipSeconds * 1_000_000L / SEGMENT_US).toInt() + 2
    }

    private fun newVideoEncoder(w: Int, h: Int): VideoEncoder {
        val gen = ++generation
        val encoder = VideoEncoder(w, h, settings.bitrate, { writer.setVideoFormat(gen, it) }, { b, i -> writer.writeVideo(gen, b, i) })
        video = encoder
        width = w
        height = h
        return encoder
    }

    /** Games often switch to landscape: swap in an encoder of the new shape (Android 14 allows one virtual display per projection, so resize it). */
    private fun onRotation() {
        val (w, h, dpi) = captureSize()
        if (w == width && h == height) return
        Logger.i("Display rotated: ${width}x$height -> ${w}x$h")
        val old = video
        val encoder = runCatching { newVideoEncoder(w, h) }.getOrElse {
            Logger.e("Couldn't restart encoder after rotation", it)
            return
        }
        display?.resize(w, h, dpi)
        display?.surface = encoder.surface
        Thread { old?.stop() }.start()
        (Recorder.status.value as? RecorderStatus.Recording)?.let { Recorder.status.value = it.copy(width = w, height = h) }
    }

    private fun captureSize(): Triple<Int, Int, Int> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        displayManager.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        val scale = minOf(1f, settings.longEdge.toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels))
        fun even16(v: Float) = (v.toInt() / 16) * 16
        return Triple(even16(metrics.widthPixels * scale), even16(metrics.heightPixels * scale), metrics.densityDpi)
    }

    fun requestClip() {
        clipExecutor.execute {
            Recorder.events.tryEmit(RecorderEvent.ClipStarted)
            try {
                updateKeep()
                val clipUs = Recorder.settings.value.clipSeconds * 1_000_000L
                writer.rotateNow().await(3, TimeUnit.SECONDS)
                val segments = writer.pinLatest(clipUs)
                try {
                    check(segments.isNotEmpty()) { "Nothing recorded yet, give it a few seconds" }
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val out = File(LocalClips.dir(context), "MinedTv_$stamp.mp4")
                    val durationUs = ClipJoiner.join(segments.map { it.file }, out)
                    val game = Recorder.appLabel(context, Recorder.lastApp.value) ?: "Gameplay"
                    LocalClips.saveMeta(out, game, durationUs / 1_000)
                    runCatching { copyToGallery(out) }.onFailure { Logger.w("Gallery copy failed: ${it.message}") }
                    LocalClips.refresh(context)
                    val seconds = (durationUs / 1_000_000).toInt()
                    Logger.i("Clip saved: ${out.name} (${seconds}s, ${out.length() / 1024} KB, game=$game)")
                    Recorder.events.tryEmit(RecorderEvent.ClipSaved(out, seconds))
                } finally {
                    writer.unpin(segments)
                }
            } catch (e: Exception) {
                Logger.e("Clip failed", e)
                Recorder.events.tryEmit(RecorderEvent.ClipFailed(e.message ?: "Clip failed"))
            }
        }
    }

    private fun copyToGallery(file: File) {
        if (Build.VERSION.SDK_INT < 29) return
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MinedTv")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    /** Blocking; call off the main thread. */
    fun stop() {
        displayManager.unregisterDisplayListener(displayListener)
        display?.release()
        display = null
        video?.stop()
        audio?.stop()
        writer.closeAll()
        clipExecutor.shutdown()
        clipExecutor.awaitTermination(10, TimeUnit.SECONDS)
        segmentDir.deleteRecursively()
        Logger.i("Recording stopped")
    }

    companion object {
        const val SEGMENT_US = 5_000_000L
    }
}
