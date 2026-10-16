package com.mikimouse.operator

import java.io.File

data class Cue(val startMs: Long, val endMs: Long, val text: String)

object SrtParser {
    fun parse(file: File): List<Cue> = parseText(file.readText())

    fun parseText(srt: String): List<Cue> {
        val cues = mutableListOf<Cue>()
        val blocks = srt.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
        for (b in blocks) {
            val lines = b.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) continue
            val timeLine = lines.firstOrNull { "-->" in it } ?: continue
            val parts = timeLine.split("-->").map { it.trim() }
            if (parts.size != 2) continue
            val s = stamp(parts[0]) ?: continue
            val e = stamp(parts[1]) ?: continue
            val text = lines.dropWhile { "-->" !in it }.drop(1).joinToString("\n")
            if (text.isNotBlank()) cues += Cue(s, e, text)
        }
        return cues.sortedBy { it.startMs }
    }

    private fun stamp(t: String): Long? {
        val m = Regex("(\\d+):(\\d{2}):(\\d{2})[,.](\\d{3})").find(t.trim()) ?: return null
        val (h, mi, s, ms) = m.destructured
        return h.toLong() * 3600_000 + mi.toLong() * 60_000 + s.toLong() * 1000 + ms.toLong()
    }
}
