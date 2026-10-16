package com.mikimouse.app.media

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

object AudioTrim {
    data class Result(val file: File, val bytes: Long)

    fun trim(input: File, out: File, startUs: Long = 0, endUs: Long = Long.MAX_VALUE): Result {
        out.parentFile?.mkdirs()
        if (out.exists()) out.delete()
        val ex = MediaExtractor()
        try {
            ex.setDataSource(input.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) throw IllegalStateException("no audio track in ${input.name}")
            ex.selectTrack(track)
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val outTrack = muxer.addTrack(format)
                muxer.start()
                val buf = java.nio.ByteBuffer.allocate(256 * 1024)
                val info = android.media.MediaCodec.BufferInfo()
                while (true) {
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) break
                    val pts = ex.sampleTime
                    if (pts > endUs) break
                    if (pts < startUs) {
                        ex.advance()
                        continue
                    }
                    info.set(0, n, pts, ex.sampleFlags)
                    muxer.writeSampleData(outTrack, buf, info)
                    ex.advance()
                }
                muxer.stop()
            } finally {
                try { muxer.release() } catch (_: Exception) {}
            }
            if (!out.exists() || out.length() == 0L) {
                out.delete()
                throw IllegalStateException("trim produced nothing")
            }
            return Result(out, out.length())
        } finally {
            ex.release()
        }
    }
}
