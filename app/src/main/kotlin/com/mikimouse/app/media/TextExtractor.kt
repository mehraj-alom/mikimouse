package com.mikimouse.app.media

import java.io.File
import kotlin.text.Regex
import kotlin.text.RegexOption

object HtmlToText {
    fun extract(html: String): String {
        return html
            .replace(Regex("<(script|style)[^>]*>.*?</\\1>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#39;"), "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun extractFromFile(file: File): String {
        return extract(file.readText())
    }
}

object TextExtractor {
    fun extractFromAny(file: File): String {
        return when (file.extension.lowercase()) {
            "html", "htm" -> HtmlToText.extractFromFile(file)
            "txt", "md", "csv", "json", "xml", "yaml", "yml" -> file.readText()
            "pdf" -> extractPdf(file)
            "docx" -> extractDocx(file)
            "pptx" -> extractPptx(file)
            else -> file.readText()
        }
    }

    private fun extractPdf(file: File): String {
        // PDF text extraction would need PdfDocument or a library
        // For now, return filename-based placeholder
        return "PDF content from ${file.name}\n[PDF text extraction requires PdfDocument library]"
    }

    private fun extractDocx(file: File): String {
        return "DOCX content from ${file.name}\n[DOCX text extraction requires POI library]"
    }

    private fun extractPptx(file: File): String {
        return "PPTX content from ${file.name}\n[PPTX text extraction requires POI library]"
    }
}
