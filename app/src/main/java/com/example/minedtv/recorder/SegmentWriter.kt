package com.example.minedtv.recorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.minedtv.util.Logger
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

/**
 * Writes encoded video (+ optional audio) into rolling ~5 s MP4 segments, each starting on a
 * keyframe, and keeps only the newest [keep]. Called from the encoder threads, hence synchronized.
 */
class SegmentWriter(private val dir: File, private val segmentUs: Long) {

    data class Segment(val file: File, val startUs: Long, val endUs: Long, val width: Int, val height: Int) {
        val durationUs get() = endUs - startUs
    }

    @Volatile var wantAudio = false
    @Volatile var keep = 8

    private var generation = -1
    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    private var muxer: MediaMuxer? = null
    private var file: File? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var segmentStartUs = 0L
    private var lastVideoUs = 0L
    private var lastAudioUs = -1L
    private var counter = 0
    private val done = ArrayDeque<Segment>()
    private val pinned = HashSet<File>()
    private var rotateLatch: CountDownLatch? = null

    /** A new video encoder (e.g. after rotation) ends the current segment; old frames are ignored. */
    @Synchronized
    fun setVideoFormat(gen: Int, format: MediaFormat) {
        if (gen < generation) return
        if (videoFormat != null) closeSegment()
        generation = gen
        videoFormat = format
    }

    @Synchronized
    fun setAudioFormat(format: MediaFormat) {
        audioFormat = format
    }

    @Synchronized
    fun writeVideo(gen: Int, buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (gen != generation) return
        val isKey = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        if (isKey) {
            val due = muxer == null || rotateLatch != null || info.presentationTimeUs - segmentStartUs >= segmentUs
            if (due && (!wantAudio || audioFormat != null)) {
                closeSegment()
                openSegment(info.presentationTimeUs)
            }
        }
        val m = muxer ?: return
        m.writeSampleData(videoTrack, buf, info)
        lastVideoUs = info.presentationTimeUs
    }

    @Synchronized
    fun writeAudio(buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        val m = muxer ?: return
        if (audioTrack < 0 || info.presentationTimeUs < segmentStartUs || info.presentationTimeUs <= lastAudioUs) return
        m.writeSampleData(audioTrack, buf, info)
        lastAudioUs = info.presentationTimeUs
    }

    private fun openSegment(startUs: Long) {
        val f = File(dir, "seg_${counter++}.mp4")
        val m = MediaMuxer(f.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        videoTrack = m.addTrack(videoFormat!!)
        audioTrack = if (wantAudio && audioFormat != null) m.addTrack(audioFormat!!) else -1
        m.start()
        muxer = m
        file = f
        segmentStartUs = startUs
        lastVideoUs = startUs
        lastAudioUs = -1
    }

    private fun closeSegment() {
        val m = muxer ?: return
        muxer = null
        val ok = runCatching { m.stop() }.isSuccess
        m.release()
        val f = file!!
        val vf = videoFormat!!
        if (ok && lastVideoUs > segmentStartUs) {
            done.addLast(
                Segment(f, segmentStartUs, lastVideoUs, vf.getInteger(MediaFormat.KEY_WIDTH), vf.getInteger(MediaFormat.KEY_HEIGHT))
            )
        } else {
            f.delete()
        }
        while (done.size > keep && done.first().file !in pinned) {
            done.removeFirst().file.delete()
        }
        rotateLatch?.countDown()
        rotateLatch = null
    }

    /** Ends the current segment at the next keyframe (within ~1 s) so it can be clipped. */
    @Synchronized
    fun rotateNow(): CountDownLatch {
        val latch = rotateLatch ?: CountDownLatch(1).also { rotateLatch = it }
        if (muxer == null) {
            latch.countDown()
            rotateLatch = null
        }
        return latch
    }

    /** The newest finished segments covering at least [us], pinned so they aren't deleted while joining. */
    @Synchronized
    fun pinLatest(us: Long): List<Segment> {
        if (done.isEmpty()) return emptyList()
        val newest = done.last()
        val picked = ArrayList<Segment>()
        var total = 0L
        for (s in done.reversed()) {
            if (s.width != newest.width || s.height != newest.height) break // can't join across a rotation
            picked.add(0, s)
            total += s.durationUs
            if (total >= us) break
        }
        pinned.addAll(picked.map { it.file })
        Logger.d("Clip uses ${picked.size} segments (${total / 1000} ms)")
        return picked
    }

    @Synchronized
    fun unpin(segments: List<Segment>) {
        pinned.removeAll(segments.map { it.file }.toSet())
    }

    @Synchronized
    fun closeAll() {
        closeSegment()
    }
}
