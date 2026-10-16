package com.mikimouse.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlin.math.roundToInt

object SlideshowEncoder {
    data class Result(val file: File, val frames: Int, val durationUs: Long)

    fun encode(
        images: List<File>,
        out: File,
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 30,
        totalSeconds: Int = 30,
    ): Result {
        require(images.isNotEmpty()) { "no images to encode" }
        out.parentFile?.mkdirs()
        if (out.exists()) out.delete()

        val totalFrames = (totalSeconds * fps).coerceIn(fps * 5, fps * 180)
        val framesPerImage = (totalFrames / images.size).coerceAtLeast(fps)
        val actualFrames = framesPerImage * images.size

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var track = -1
        var muxerStarted = false
        val bufferInfo = android.media.MediaCodec.BufferInfo()
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var frameIndex = 0
            images.forEach { img ->
                val bmp = decodeCover(img, width, height) ?: return@forEach
                val yuv = bitmapToNv12(bmp, width, height)
                bmp.recycle()
                repeat(framesPerImage) {
                    val ptsUs = (frameIndex * 1_000_000L) / fps
                    feedFrame(codec, yuv, ptsUs, bufferInfo, muxer, track, { muxerStarted }, { started, t ->
                        muxerStarted = started; track = t
                    })
                    frameIndex++
                }
            }
            // Drain with EOS.
            codec.signalEndOfInputStream()
            drain(codec, bufferInfo, muxer, track, muxerStarted, endOfStream = true)
            muxer.stop()
            return Result(out, actualFrames, (actualFrames * 1_000_000L) / fps)
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun drain(
        codec: MediaCodec,
        info: android.media.MediaCodec.BufferInfo,
        muxer: MediaMuxer?,
        track: Int,
        started: Boolean,
        endOfStream: Boolean,
    ) {
        var t = track
        var s = started
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (endOfStream) return
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (s) throw IllegalStateException("format changed twice")
                    t = muxer!!.addTrack(codec.outputFormat)
                    muxer.start()
                    s = true
                }
                idx >= 0 -> {
                    val buf = codec.getOutputBuffer(idx)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && s) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer!!.writeSampleData(t, buf, info)
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    if (!endOfStream) return
                }
            }
        }
    }

    private fun feedFrame(
        codec: MediaCodec,
        yuv: ByteArray,
        ptsUs: Long,
        info: android.media.MediaCodec.BufferInfo,
        muxer: MediaMuxer?,
        track: Int,
        isStarted: () -> Boolean,
        onTrack: (Boolean, Int) -> Unit,
    ) {
        val idx = codec.dequeueInputBuffer(10_000)
        if (idx >= 0) {
            val buf = codec.getInputBuffer(idx)!!
            buf.clear()
            buf.put(yuv)
            codec.queueInputBuffer(idx, 0, yuv.size, ptsUs, 0)
        }
        // Drain available output without blocking for EOS.
        while (true) {
            val o = codec.dequeueOutputBuffer(info, 0)
            if (o == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val t = muxer!!.addTrack(codec.outputFormat)
                muxer.start()
                onTrack(true, t)
                continue
            }
            if (o >= 0) {
                val buf = codec.getOutputBuffer(o)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && isStarted()) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    muxer!!.writeSampleData(track, buf, info)
                }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            }
        }
    }

    private fun decodeCover(file: File, w: Int, h: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        // Center-crop to exact aspect then scale.
        val targetAspect = w.toFloat() / h
        val srcAspect = raw.width.toFloat() / raw.height
        val (cw, ch) = if (srcAspect > targetAspect) {
            (raw.height * targetAspect).roundToInt() to raw.height
        } else {
            raw.width to (raw.width / targetAspect).roundToInt()
        }
        val x = ((raw.width - cw) / 2).coerceAtLeast(0)
        val y = ((raw.height - ch) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(raw, x, y, cw.coerceAtMost(raw.width - x), ch.coerceAtMost(raw.height - y))
        val scaled = Bitmap.createScaledBitmap(cropped, w, h, true)
        if (cropped !== scaled) cropped.recycle()
        if (raw !== cropped) raw.recycle()
        return scaled
    }

    private fun bitmapToNv12(bmp: Bitmap, w: Int, h: Int): ByteArray {
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)
        val yuv = ByteArray(w * h * 3 / 2)
        var y = 0
        for (row in 0 until h) {
            for (col in 0 until w) {
                val p = argb[row * w + col]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                yuv[y++] = ((0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255)).toByte()
            }
        }
        var uv = w * h
        for (row in 0 until h step 2) {
            for (col in 0 until w step 2) {
                val p = argb[row * w + col]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val u = ((-0.169 * r - 0.331 * g + 0.5 * b + 128).roundToInt().coerceIn(0, 255)).toByte()
                val v = ((0.5 * r - 0.419 * g - 0.081 * b + 128).roundToInt().coerceIn(0, 255)).toByte()
                yuv[uv++] = u
                yuv[uv++] = v
            }
        }
        return yuv
    }
}
