package com.mikimouse.operator

import java.net.HttpURLConnection
import java.net.URL

object WebFetch {
    const val MAX_BYTES = 10 * 1024 * 1024

    data class Page(val url: String, val title: String, val text: String)

    fun get(url: String, timeoutMs: Int = 20_000, maxBytes: Int = MAX_BYTES): ByteArray {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "mikimouse-operator/1.0")
        }
        c.connect()
        val code = c.responseCode
        if (code !in 200..299) throw IllegalStateException("http $code for $url")
        val len = c.contentLengthLong
        if (len > maxBytes) throw IllegalStateException("refusing ${len}b download (cap ${maxBytes}b)")
        val out = mutableListOf<Byte>()
        c.inputStream.use { ins ->
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw IllegalStateException("download exceeded cap")
                out.addAll(buf.take(n))
            }
        }
        return out.toByteArray()
    }

    fun extractText(html: ByteArray): Page {
        val s = html.decodeToString().take(200_000)
        val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.trim() ?: ""
        val noScripts = s.replace(Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        val text = noScripts.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(20_000)
        return Page("", title, text)
    }

    fun extractTables(html: ByteArray): List<List<List<String>>> {
        val s = html.decodeToString().take(500_000)
        val cell = { h: String ->
            h.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        }
        return Regex("<table[\\s>].*?</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(s).map { tm ->
                Regex("<tr[\\s>].*?</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .findAll(tm.value).map { rm ->
                        Regex("<t[dh][\\s>].*?</t[dh]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                            .findAll(rm.value).map { cell(it.value) }.toList()
                    }.filter { it.isNotEmpty() }.toList()
            }.filter { it.isNotEmpty() }.toList()
    }
}
