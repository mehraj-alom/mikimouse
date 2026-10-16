package com.mikimouse.app.media

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object VideoTrim {
    fun trim(input: File, out: File, startUs: Long = 0, endUs: Long = Long.MAX_VALUE): File {
        out.parentFile?.mkdirs()
        if (out.exists()) out.delete()
        val ex = MediaExtractor()
        try {
            ex.setDataSource(input.absolutePath)
            val selected = mutableListOf<Int>()
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/")) selected += i
            }
            if (selected.isEmpty()) throw IllegalStateException("no A/V tracks in ${input.name}")
            selected.forEach { ex.selectTrack(it) }
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val outTracks = selected.associateWith { muxer.addTrack(ex.getTrackFormat(it)) }
                muxer.start()
                val buf = ByteBuffer.allocate(512 * 1024)
                val info = android.media.MediaCodec.BufferInfo()
                var done = false
                while (!done) {
                    val sampleTrack = ex.sampleTrackIndex
                    if (sampleTrack < 0) break
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) break
                    val pts = ex.sampleTime
                    if (pts > endUs) break
                    if (pts >= startUs && sampleTrack in outTracks) {
                        info.set(0, n, pts - startUs, ex.sampleFlags)
                        muxer.writeSampleData(outTracks.getValue(sampleTrack), buf, info)
                    }
                    ex.advance()
                }
                muxer.stop()
            } finally {
                try { muxer.release() } catch (_: Exception) {}
            }
            if (!out.exists() || out.length() == 0L) {
                out.delete()
                throw IllegalStateException("trim produced nothing — check the time range")
            }
            return out
        } finally {
            ex.release()
        }
    }
}
