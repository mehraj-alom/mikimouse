package com.mikimouse.app.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File

object FrameGrabber {
    fun extract(video: File, outDir: File, count: Int = 6): List<File> {
        outDir.mkdirs()
        val r = MediaMetadataRetriever()
        val saved = mutableListOf<File>()
        try {
            r.setDataSource(video.absolutePath)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durMs <= 0) throw IllegalStateException("no duration in ${video.name}")
            repeat(count) { i ->
                val us = (durMs * 1000L * (i + 1)) / (count + 1)
                val bmp: Bitmap = r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@repeat
                val f = File(outDir, "frame-${i + 1}.jpg")
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                if (!bmp.isRecycled) bmp.recycle()
                saved += f
            }
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        if (saved.isEmpty()) throw IllegalStateException("no frames from ${video.name}")
        return saved
    }
}
