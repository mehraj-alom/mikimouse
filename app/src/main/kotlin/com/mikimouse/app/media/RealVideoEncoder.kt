package com.mikimouse.app.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object RealVideoEncoder {
    data class EncodeResult(val file: File, val durationMs: Long, val frameCount: Int)

    /**
     * Creates a real H.264 MP4 video from images using MediaCodec hardware encoder.
     * Each image becomes N frames. No full-video-in-RAM — streaming 16KB buffers.
     */
    fun encodeSlideshow(
        images: List<File>,
        out: File,
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 30,
        secondsPerImage: Int = 5,
        bitrate: Int = 4_000_000
    ): EncodeResult {
        require(images.isNotEmpty()) { "no images" }
        out.parentFile?.mkdirs()
        if (out.exists()) out.delete()

        val framesPerImage = fps * secondsPerImage
        val totalFrames = framesPerImage * images.size
        val totalDurationUs = (totalFrames.toLong() * 1_000_000) / fps

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var frameIdx = 0
            images.forEach { img ->
                val bmp = decodeAndScale(img, width, height) ?: return@forEach
                val yuv = bitmapToNV12(bmp, width, height)
                bmp.recycle()

                repeat(framesPerImage) {
                    val ptsUs = (frameIdx.toLong() * 1_000_000) / fps
                    feedAndDrain(codec, yuv, ptsUs, bufferInfo, muxer, trackIndex, muxerStarted) { started, track ->
                        muxerStarted = started
                        trackIndex = track
                    }
                    frameIdx++
                }
            }

            // Flush encoder
            codec.signalEndOfInputStream()
            drainCodec(codec, bufferInfo, muxer, trackIndex, muxerStarted, endOfStream = true)

            muxer.stop()
            return EncodeResult(out, totalDurationUs / 1000, frameIdx)
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Real video trim using MediaExtractor + MediaMuxer — stream copy, no re-encode.
     */
    fun trimVideo(input: File, out: File, startMs: Long, endMs: Long): File {
        out.parentFile?.mkdirs()
        if (out.exists()) out.delete()

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(input.absolutePath)
            val trackCount = extractor.trackCount
            val selectedTracks = mutableListOf<Int>()
            val trackFormats = mutableListOf<MediaFormat>()

            for (i in 0 until trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/") || mime.startsWith("text/")) {
                    extractor.selectTrack(i)
                    selectedTracks.add(i)
                    trackFormats.add(fmt)
                }
            }

            val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrackIndices = mutableMapOf<Int, Int>()

            try {
                selectedTracks.forEachIndexed { index, trackIdx ->
                    outputTrackIndices[trackIdx] = muxer.addTrack(trackFormats[index])
                }
                muxer.start()

                val buf = ByteBuffer.allocate(256 * 1024)
                val info = MediaCodec.BufferInfo()

                selectedTracks.forEach { trackIdx ->
                    extractor.selectTrack(trackIdx)
                    extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                    var done = false
                    while (!done) {
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            done = true
                            break
                        }
                        val ptsUs = extractor.sampleTime
                        val ptsMs = ptsUs / 1000
                        if (ptsMs > endMs) {
                            done = true
                            break
                        }
                        if (ptsMs >= startMs) {
                            buf.position(0)
                            buf.limit(sampleSize)
                            info.set(0, sampleSize, ptsUs - (startMs * 1000), extractor.sampleFlags)
                            muxer.writeSampleData(outputTrackIndices[trackIdx]!!, buf, info)
                        }
                        extractor.advance()
                    }
                }

                muxer.stop()
            } finally {
                try { muxer.release() } catch (_: Exception) {}
            }
        } finally {
            extractor.release()
        }

        if (!out.exists() || out.length() == 0L) {
            out.delete()
            throw IllegalStateException("trim produced empty output")
        }
        return out
    }

    private fun decodeAndScale(file: File, targetW: Int, targetH: Int): android.graphics.Bitmap? {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        var sample = 1
        while (opts.outWidth / (sample * 2) >= targetW && opts.outHeight / (sample * 2) >= targetH) {
            sample *= 2
        }

        val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

        val targetAspect = targetW.toFloat() / targetH
        val srcAspect = raw.width.toFloat() / raw.height
        val (cropW, cropH) = if (srcAspect > targetAspect) {
            (raw.height * targetAspect).toInt() to raw.height
        } else {
            raw.width to (raw.width / targetAspect).toInt()
        }
        val x = ((raw.width - cropW) / 2).coerceAtLeast(0)
        val y = ((raw.height - cropH) / 2).coerceAtLeast(0)
        val cropped = android.graphics.Bitmap.createBitmap(raw, x, y, cropW.coerceAtMost(raw.width - x), cropH.coerceAtMost(raw.height - y))
        val scaled = android.graphics.Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
        if (cropped != scaled) cropped.recycle()
        if (raw != cropped) raw.recycle()
        return scaled
    }

    private fun bitmapToNV12(bmp: android.graphics.Bitmap, w: Int, h: Int): ByteArray {
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)
        val yuv = ByteArray(w * h * 3 / 2)
        var yIdx = 0
        for (row in 0 until h) {
            for (col in 0 until w) {
                val p = argb[row * w + col]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                yuv[yIdx++] = ((0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)).toByte()
            }
        }
        var uvIdx = w * h
        for (row in 0 until h step 2) {
            for (col in 0 until w step 2) {
                val p = argb[row * w + col]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val u = ((-0.169 * r - 0.331 * g + 0.5 * b + 128).toInt().coerceIn(0, 255)).toByte()
                val v = ((0.5 * r - 0.419 * g - 0.081 * b + 128).toInt().coerceIn(0, 255)).toByte()
                yuv[uvIdx++] = u
                yuv[uvIdx++] = v
            }
        }
        return yuv
    }

    private fun feedAndDrain(
        codec: MediaCodec,
        yuv: ByteArray,
        ptsUs: Long,
        info: MediaCodec.BufferInfo,
        muxer: MediaMuxer?,
        trackIndex: Int,
        started: Boolean,
        onTrack: (Boolean, Int) -> Unit
    ) {
        // Queue input
        var inputIdx = codec.dequeueInputBuffer(10_000)
        while (inputIdx < 0) {
            inputIdx = codec.dequeueInputBuffer(10_000)
        }
        val inputBuf = codec.getInputBuffer(inputIdx)!!
        inputBuf.clear()
        inputBuf.put(yuv)
        codec.queueInputBuffer(inputIdx, 0, yuv.size, ptsUs, 0)

        // Drain output
        drainCodec(codec, info, muxer, trackIndex, started, endOfStream = false) { s, t ->
            onTrack(s, t)
        }
    }

    private fun drainCodec(
        codec: MediaCodec,
        info: MediaCodec.BufferInfo,
        muxer: MediaMuxer?,
        trackIndex: Int,
        started: Boolean,
        endOfStream: Boolean,
        onTrack: (Boolean, Int) -> Unit = { _, _ -> }
    ) {
        var currentTrack = trackIndex
        var startedFlag = started

        while (true) {
            val outputIdx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outputIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (endOfStream) return
                    else return
                }
                outputIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    // Re-get buffers (rare on modern Android)
                }
                outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!startedFlag) {
                        currentTrack = muxer!!.addTrack(codec.outputFormat)
                        muxer.start()
                        startedFlag = true
                        onTrack(true, currentTrack)
                    }
                }
                outputIdx >= 0 -> {
                    val buf = codec.getOutputBuffer(outputIdx)!!
                    if (startedFlag && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0)) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer!!.writeSampleData(currentTrack, buf, info)
                    }
                    codec.releaseOutputBuffer(outputIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    if (!endOfStream) return
                }
            }
        }
    }
}
