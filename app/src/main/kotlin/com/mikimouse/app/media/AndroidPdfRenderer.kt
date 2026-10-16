package com.mikimouse.app.media

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.mikimouse.operator.Slide
import java.io.File

object AndroidPdfRenderer {
    fun renderSlides(title: String, slides: List<Slide>, out: File): File {
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
            titlePaint.color = 0xFFFFFFFF.toInt()
            c.drawText(s.title.take(60), 64f, 120f, titlePaint)
            bodyPaint.color = 0xFFCBD5E1.toInt()
            var y = 210f
            s.bullets.take(6).forEach { b ->
                c.drawText(("• " + b).take(72), 80f, y, bodyPaint)
                y += 56f
            }
            pageNo.color = 0xFF64748B.toInt()
            c.drawText("$title — ${i + 1}/${slides.size}", 64f, (h - 40).toFloat(), pageNo)
            doc.finishPage(page)
        }
        doc.writeTo(out.outputStream())
        doc.close()
        return out
    }
}
