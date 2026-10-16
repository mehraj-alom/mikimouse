package com.mikimouse.operator

import java.io.File
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xwpf.usermodel.XWPFDocument

data class Slide(val title: String, val bullets: List<String>)

object DocumentPipeline {
    fun outlineFromTexts(title: String, texts: List<String>, maxSlides: Int = 8): List<Slide> {
        val chunks = texts.flatMap { it.split("\n") }.map { it.trim() }.filter { it.length > 2 }.distinct()
        if (chunks.isEmpty()) {
            return listOf(
                Slide(title, listOf("Overview", "Key points", "Next steps")),
                Slide("Next steps", listOf("Review", "Decide", "Act")),
            )
        }
        val slides = mutableListOf(Slide(title, chunks.take(4)))
        chunks.drop(4).chunked(4).take(maxSlides - 1).forEachIndexed { i, c ->
            slides += Slide("Section ${i + 1}", c)
        }
        return slides
    }

    fun renderPptx(slides: List<Slide>, out: File): File {
        out.parentFile?.mkdirs()
        XMLSlideShow().use { ppt ->
            val w = ppt.pageSize
            slides.forEach { s ->
                val slide = ppt.createSlide()
                val titleBox = slide.createTextBox()
                titleBox.setAnchor(java.awt.Rectangle(32, 16, w.width - 64, 64))
                titleBox.text = s.title.take(120)
                var y = 100
                s.bullets.take(5).forEach { b ->
                    val box = slide.createTextBox()
                    box.setAnchor(java.awt.Rectangle(48, y, w.width - 96, 44))
                    box.text = "• " + b.take(160)
                    y += 52
                }
            }
            ppt.write(out.outputStream())
        }
        return out
    }

    fun renderDocx(title: String, paras: List<String>, out: File): File {
        out.parentFile?.mkdirs()
        XWPFDocument().use { doc ->
            val t = doc.createParagraph()
            val r = t.createRun()
            r.setText(title.take(200))
            r.isBold = true
            r.fontSize = 18
            paras.forEach { p ->
                val para = doc.createParagraph()
                para.createRun().setText(p.take(2000))
            }
            out.outputStream().use { doc.write(it) }
        }
        return out
    }

    fun renderMarkdown(slides: List<Slide>, out: File): File {
        out.parentFile?.mkdirs()
        out.writeText(buildString {
            slides.forEach { s ->
                appendLine("# ${s.title}")
                s.bullets.forEach { appendLine("- $it") }
                appendLine()
            }
        })
        return out
    }

    fun renderTextReport(title: String, body: String, out: File): File {
        out.parentFile?.mkdirs()
        out.writeText("$title\n${"=".repeat(title.length.coerceAtMost(60))}\n\n$body\n")
        return out
    }

    fun renderCsv(rows: List<List<String>>, out: File): File {
        out.parentFile?.mkdirs()
        out.writeText(rows.joinToString("\n") { r -> r.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } })
        return out
    }
}
