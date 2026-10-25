package com.example.minedtv.recorder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Joins keyframe-aligned MP4 segments into one MP4 without re-encoding. */
object ClipJoiner {

    /** Returns the joined clip's duration in microseconds. */
    fun join(inputs: List<File>, out: File): Long {
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        var videoOut = -1
        var audioOut = -1
        var started = false
        var offsetUs = 0L
        try {
            for (input in inputs) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(input.path)
                    var videoIn = -1
                    var audioIn = -1
                    for (i in 0 until extractor.trackCount) {
                        val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                        if (mime.startsWith("video/") && videoIn < 0) videoIn = i
                        else if (mime.startsWith("audio/") && audioIn < 0) audioIn = i
                    }
                    if (videoIn < 0) continue
                    if (!started) {
                        videoOut = muxer.addTrack(extractor.getTrackFormat(videoIn))
                        if (audioIn >= 0) audioOut = muxer.addTrack(extractor.getTrackFormat(audioIn))
                        muxer.start()
                        started = true
                    }
                    extractor.selectTrack(videoIn)
                    if (audioIn >= 0 && audioOut >= 0) extractor.selectTrack(audioIn)

                    var firstUs = -1L
                    var endUs = 0L
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val time = extractor.sampleTime
                        if (firstUs < 0) firstUs = time
                        val relative = time - firstUs
                        if (relative >= 0) {
                            val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            info.set(0, size, offsetUs + relative, flags)
                            val track = if (extractor.sampleTrackIndex == videoIn) videoOut else audioOut
                            muxer.writeSampleData(track, buffer, info)
                            endUs = maxOf(endUs, relative)
                        }
                        extractor.advance()
                    }
                    offsetUs += endUs + 33_333 // one frame at 30 fps
                } finally {
                    extractor.release()
                }
            }
        } finally {
            if (started) runCatching { muxer.stop() }
            muxer.release()
        }
        check(started) { "Nothing to save yet" }
        return offsetUs
    }
}
