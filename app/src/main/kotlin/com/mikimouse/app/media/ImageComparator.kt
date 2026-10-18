package com.mikimouse.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object ImageComparator {
    fun compare(img1: File, img2: File): ImageDiffResult {
        val b1 = BitmapFactory.decodeFile(img1.absolutePath) ?: return ImageDiffResult(0.0, "Cannot decode $img1")
        val b2 = BitmapFactory.decodeFile(img2.absolutePath) ?: return ImageDiffResult(0.0, "Cannot decode $img2")

        val w1 = b1.width
        val h1 = b1.height
        val w2 = b2.width
        val h2 = b2.height

        // Resize to common dimensions for comparison
        val commonW = minOf(w1, w2)
        val commonH = minOf(h1, h2)

        val scaled1 = Bitmap.createScaledBitmap(b1, commonW, commonH, true)
        val scaled2 = Bitmap.createScaledBitmap(b2, commonW, commonH, true)

        val pixels1 = IntArray(commonW * commonH)
        val pixels2 = IntArray(commonW * commonH)
        scaled1.getPixels(pixels1, 0, commonW, 0, 0, commonW, commonH)
        scaled2.getPixels(pixels2, 0, commonW, 0, 0, commonW, commonH)

        var diffCount = 0
        var totalDiff = 0.0
        for (i in pixels1.indices) {
            val p1 = pixels1[i]
            val p2 = pixels2[i]
            val r1 = (p1 shr 16) and 0xFF
            val g1 = (p1 shr 8) and 0xFF
            val b1 = p1 and 0xFF
            val r2 = (p2 shr 16) and 0xFF
            val g2 = (p2 shr 8) and 0xFF
            val b2 = p2 and 0xFF
            val diff = abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
            totalDiff += diff
            if (diff > 30) diffCount++
        }

        scaled1.recycle()
        scaled2.recycle()
        b1.recycle()
        b2.recycle()

        val totalPixels = commonW * commonH.toDouble()
        val diffPercent = (diffCount / totalPixels) * 100
        val avgDiff = totalDiff / (totalPixels * 3)

        return ImageDiffResult(
            similarity = 100.0 - diffPercent.coerceAtMost(100.0),
            description = "Images differ by ${diffCount} pixels (${"%.1f".format(diffPercent)}% of ${"%.0f".format(totalPixels)} total pixels)"
        )
    }

    private fun abs(x: Int): Int = if (x < 0) -x else x
}

data class ImageDiffResult(
    val similarity: Double,
    val description: String
)
