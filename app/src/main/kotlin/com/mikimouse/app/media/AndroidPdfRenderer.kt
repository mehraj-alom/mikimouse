package com.mikimouse.app.media

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.mikimouse.operator.Slide
import java.io.File

object AndroidPdfRenderer {
    fun renderSlides(title: String, slides: List<Slide>, out: File, images: List<File> = emptyList()): File {
        out.parentFile?.mkdirs()
        val doc = PdfDocument()
        val w = 1280
        val h = 720
        val titlePaint = Paint().apply { textSize = 54f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val bodyPaint = Paint().apply { textSize = 30f }
        val pageNo = Paint().apply { textSize = 22f }
        slides.forEachIndexed { i, s ->
            val info = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
            val page = doc.startPage(info)
            val c = page.canvas
            c.drawColor(0xFF0B1020.toInt())
            // Slide art: decode the matching input image sampled down (never full-RAM).
            val art = images.getOrNull(i)?.let { f ->
                try {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(f.absolutePath, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= 560 && bounds.outHeight / (sample * 2) >= 400) sample *= 2
                    android.graphics.BitmapFactory.decodeFile(f.absolutePath,
                        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                } catch (_: Exception) { null }
            }
            if (art != null) {
                val dest = android.graphics.RectF(64f, 150f, 600f, 560f)
                c.drawBitmap(art, null, dest, null)
                if (!art.isRecycled) art.recycle()
                titlePaint.color = 0xFFFFFFFF.toInt()
                c.drawText(s.title.take(40), 640f, 220f, titlePaint)
                bodyPaint.color = 0xFFCBD5E1.toInt()
                var y = 290f
                s.bullets.take(5).forEach { b ->
                    c.drawText(("• " + b).take(40), 640f, y, bodyPaint)
                    y += 56f
                }
            } else {
                titlePaint.color = 0xFFFFFFFF.toInt()
                c.drawText(s.title.take(60), 64f, 120f, titlePaint)
                bodyPaint.color = 0xFFCBD5E1.toInt()
                var y = 210f
                s.bullets.take(6).forEach { b ->
                    c.drawText(("• " + b).take(72), 80f, y, bodyPaint)
                    y += 56f
                }
            }
            pageNo.color = 0xFF64748B.toInt()
            c.drawText("$title — ${i + 1}/${slides.size}", 64f, (h - 40).toFloat(), pageNo)
            doc.finishPage(page)
        }
        doc.writeTo(out.outputStream())
        doc.close()
        return out
    }

    fun merge(files: List<File>, out: File): File {
        require(files.isNotEmpty()) { "nothing to merge" }
        out.parentFile?.mkdirs()
        val doc = PdfDocument()
        files.forEach { f ->
            val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                for (i in 0 until renderer.pageCount) {
                    val src = renderer.openPage(i)
                    val w = (src.width * 1.2f).toInt().coerceAtMost(2000)
                    val h = (src.height * 1.2f).toInt().coerceAtMost(2000)
                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(0xFFFFFFFF.toInt())
                    src.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val info = PdfDocument.PageInfo.Builder(w, h, i).create()
                    val page = doc.startPage(info)
                    page.canvas.drawBitmap(bmp, 0f, 0f, null)
                    doc.finishPage(page)
                    if (!bmp.isRecycled) bmp.recycle()
                    src.close()
                }
            } finally {
                renderer.close()
                pfd.close()
            }
        }
        doc.writeTo(out.outputStream())
        doc.close()
        return out
    }
}
