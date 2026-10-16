package com.mikimouse.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File

object ImageTools {
    fun load(file: File): Bitmap =
        BitmapFactory.decodeFile(file.absolutePath) ?: throw IllegalStateException("cannot decode ${file.name}")

    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun flipH(src: Bitmap): Bitmap {
        val m = Matrix().apply { postScale(-1f, 1f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun save(bmp: Bitmap, out: File, format: String): File {
        out.parentFile?.mkdirs()
        val fmt = when (format.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.JPEG
        }
        out.outputStream().use { bmp.compress(fmt, 90, it) }
        return out
    }

    fun transform(input: File, out: File, rotateDeg: Float = 0f, flip: Boolean = false, format: String = "jpg"): File {
        val raw = load(input)
        try {
            var bmp = raw
            if (rotateDeg != 0f) {
                val r = rotate(bmp, rotateDeg)
                if (bmp !== raw) bmp.recycle()
                bmp = r
            }
            if (flip) {
                val f = flipH(bmp)
                if (bmp !== raw) bmp.recycle()
                bmp = f
            }
            save(bmp, out, format)
            if (bmp !== raw) bmp.recycle()
            return out
        } finally {
            if (!raw.isRecycled) raw.recycle()
        }
    }
}
