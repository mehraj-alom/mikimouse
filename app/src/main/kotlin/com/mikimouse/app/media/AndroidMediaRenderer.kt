package com.mikimouse.app.media

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

object AndroidMediaRenderer {
    data class Probe(val mime: String, val width: Int, val height: Int, val durationUs: Long)

    fun probe(file: File): Probe? {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(file.absolutePath)
            val fmt: MediaFormat = ex.getTrackFormat(0)
            Probe(
                fmt.getString(MediaFormat.KEY_MIME) ?: "?",
                if (fmt.containsKey(MediaFormat.KEY_WIDTH)) fmt.getInteger(MediaFormat.KEY_WIDTH) else 0,
                if (fmt.containsKey(MediaFormat.KEY_HEIGHT)) fmt.getInteger(MediaFormat.KEY_HEIGHT) else 0,
                if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0,
            )
        } catch (_: Exception) {
            null
        } finally {
            ex.release()
        }
    }
}
