package com.example.minedtv.recorder

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.view.Surface
import com.example.minedtv.util.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

typealias SampleSink = (ByteBuffer, MediaCodec.BufferInfo) -> Unit

/**
 * H.264 encoder fed by a Surface (the screen's virtual display). A keyframe every second so a
 * clip can start at any segment boundary; repeats the last frame when the screen is static.
 */
class VideoEncoder(
    val width: Int,
    val height: Int,
    bitrate: Int,
    private val onFormat: (MediaFormat) -> Unit,
    private val sink: SampleSink
) {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    val surface: Surface
    private val thread: Thread

    init {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = codec.createInputSurface()
        codec.start()
        thread = Thread(::drain, "MinedIt-video").apply { start() }
    }

    private fun drain() {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = try {
                codec.dequeueOutputBuffer(info, 10_000)
            } catch (e: IllegalStateException) {
                break
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                onFormat(codec.outputFormat)
            } else if (index >= 0) {
                val buf = codec.getOutputBuffer(index)
                val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (buf != null && info.size > 0 && !isConfig) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    sink(buf, info)
                }
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
    }

    fun stop() {
        runCatching { codec.signalEndOfInputStream() }
        thread.join(2_000)
        runCatching { codec.stop() }
        codec.release()
        surface.release()
    }
}

/**
 * Game audio and/or microphone -> one AAC track. Game audio is Android's playback capture
 * (Android 10+, through the same MediaProjection): whatever other apps play as media/game sound,
 * unless an app opts out of capture (those, and call audio, come through as silence). Both
 * sources are read in equal-sized blocks and summed, so they stay in step. Timestamps use the
 * same monotonic clock as the screen frames (System.nanoTime), so audio and video line up.
 */
@SuppressLint("MissingPermission") // RECORD_AUDIO is checked before the engine starts.
class AudioEncoder(
    projection: MediaProjection?,
    mic: Boolean,
    private val onFormat: (MediaFormat) -> Unit,
    private val sink: SampleSink
) {
    private val game: AudioRecord? = projection?.let(::gameRecord)
    private val micRecord: AudioRecord? = if (mic) micRecord() else null
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    @Volatile private var running = true
    private var thread: Thread? = null

    val hasGameAudio get() = game != null
    val hasMic get() = micRecord != null

    init {
        check(game != null || micRecord != null) { "No audio source available" }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BLOCK * 2)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        Logger.i("Audio sources: game=${game != null}, mic=${micRecord != null}")
    }

    private fun bufferSize() =
        maxOf(AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4, 16_384)

    private fun pcmFormat() = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(SAMPLE_RATE)
        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
        .build()

    private fun gameRecord(projection: MediaProjection): AudioRecord? = runCatching {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(pcmFormat())
            .setBufferSizeInBytes(bufferSize())
            .build()
            .also { check(it.state == AudioRecord.STATE_INITIALIZED) { "playback capture not initialized" } }
    }.onFailure { Logger.w("Game audio unavailable: ${it.message}") }.getOrNull()

    private fun micRecord(): AudioRecord? = runCatching {
        AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize()
        ).also { check(it.state == AudioRecord.STATE_INITIALIZED) { "microphone not initialized" } }
    }.onFailure { Logger.w("Mic unavailable: ${it.message}") }.getOrNull()

    fun start() {
        game?.startRecording()
        micRecord?.startRecording()
        thread = Thread(::loop, "MinedIt-audio").apply { start() }
    }

    private fun loop() {
        val info = MediaCodec.BufferInfo()
        val gameBuf = ShortArray(BLOCK)
        val micBuf = ShortArray(BLOCK)
        var lastPts = 0L
        var eosQueued = false
        try {
            while (true) {
                if (!eosQueued) {
                    // One block from each source; the blocking reads pace this loop at real time.
                    val gameRead = game?.read(gameBuf, 0, BLOCK, AudioRecord.READ_BLOCKING) ?: 0
                    val micRead = micRecord?.read(micBuf, 0, BLOCK, AudioRecord.READ_BLOCKING) ?: 0
                    val n = maxOf(gameRead, micRead)
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        buf.clear()
                        if (!running || n < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, lastPts + 1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosQueued = true
                        } else {
                            val out = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            for (i in 0 until n) {
                                val g = if (i < gameRead) gameBuf[i].toInt() else 0
                                val m = if (i < micRead) micBuf[i].toInt() else 0
                                out.put((g + m).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                            }
                            // The block's first sample was captured n samples ago.
                            var pts = System.nanoTime() / 1_000 - n * 1_000_000L / SAMPLE_RATE
                            if (pts <= lastPts) pts = lastPts + 1
                            lastPts = pts
                            codec.queueInputBuffer(inIndex, 0, n * 2, pts, 0)
                        }
                    }
                }
                var outIndex = codec.dequeueOutputBuffer(info, if (eosQueued) 10_000 else 0)
                while (outIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        onFormat(codec.outputFormat)
                    } else if (outIndex >= 0) {
                        val buf = codec.getOutputBuffer(outIndex)
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (buf != null && info.size > 0 && !isConfig) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            sink(buf, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                    outIndex = codec.dequeueOutputBuffer(info, 0)
                }
            }
        } catch (e: IllegalStateException) {
            Logger.w("Audio encoder stopped: ${e.message}")
        }
    }

    fun stop() {
        running = false
        thread?.join(2_000)
        listOfNotNull(game, micRecord).forEach {
            runCatching { it.stop() }
            it.release()
        }
        runCatching { codec.stop() }
        codec.release()
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        /** Samples per block (~46 ms). */
        private const val BLOCK = 2_048
    }
}
