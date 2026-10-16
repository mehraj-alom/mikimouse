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

    private class Session(val codec: MediaCodec, val muxer: MediaMuxer) {
        var track = -1
        var started = false
        val info = MediaCodec.BufferInfo()

        fun drain(endOfStream: Boolean) {
            while (true) {
                val idx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (started) throw IllegalStateException("encoder: format changed twice")
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    }
                    idx >= 0 -> {
                        val buf = codec.getOutputBuffer(idx)
                            ?: throw IllegalStateException("encoder: null output buffer")
                        if (started && info.size > 0 &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        if (!endOfStream) return
                    }
                }
            }
        }

        fun feed(yuv: ByteArray, ptsUs: Long) {
            var fed = false
            var tries = 0
            while (!fed && tries < 30) {
                val idx = codec.dequeueInputBuffer(10_000)
                if (idx >= 0) {
                    val buf = codec.getInputBuffer(idx)
                        ?: throw IllegalStateException("encoder: null input buffer")
                    buf.clear()
                    if (buf.remaining() < yuv.size) {
                        codec.queueInputBuffer(idx, 0, 0, 0, 0)
                        throw IllegalStateException("encoder: frame larger than input buffer")
                    }
                    buf.put(yuv)
                    codec.queueInputBuffer(idx, 0, yuv.size, ptsUs, 0)
                    fed = true
                }
                tries++
            }
            if (!fed) throw IllegalStateException("encoder: no input buffer after 30 tries")
            drain(endOfStream = false)
        }
    }

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

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = try {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        } catch (t: Throwable) {
            throw IllegalStateException("no AVC hardware encoder on this device", t)
        }
        var muxer: MediaMuxer? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val s = Session(codec, muxer)
            var frameIndex = 0
            var usedImages = 0
            images.forEach { img ->
                val bmp = decodeCover(img, width, height) ?: return@forEach
                val yuv = bitmapToNv12(bmp, width, height)
                bmp.recycle()
                usedImages++
                repeat(framesPerImage) {
                    s.feed(yuv, (frameIndex * 1_000_000L) / fps)
                    frameIndex++
                }
            }
            if (usedImages == 0) throw IllegalStateException("none of the images could be decoded")
            codec.signalEndOfInputStream()
            s.drain(endOfStream = true)
            if (!s.started) throw IllegalStateException("encoder produced no output")
            muxer.stop()
            return Result(out, frameIndex, (frameIndex * 1_000_000L) / fps)
        } catch (t: Throwable) {
            out.delete()
            throw IllegalStateException("video render failed: ${t.message}", t)
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { muxer?.release() } catch (_: Exception) {}
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
                yuv[uv++] = ((-0.169 * r - 0.331 * g + 0.5 * b + 128).roundToInt().coerceIn(0, 255)).toByte()
                yuv[uv++] = ((0.5 * r - 0.419 * g - 0.081 * b + 128).roundToInt().coerceIn(0, 255)).toByte()
            }
        }
        return yuv
    }
}
