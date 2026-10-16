package com.mikimouse.app.outputs

import com.mikimouse.operator.ChatSource
import com.mikimouse.operator.WebFetch
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.net.URLEncoder

object DdgSearch {
    data class Answer(val abstract: String, val sources: List<ChatSource>, val stale: Boolean = false)

    private fun key(query: String): String {
        val d = MessageDigest.getInstance("SHA-256")
        return d.digest(query.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }

    /** Cached answers (24h). Offline: reuses stale packs, labeled. */
    fun askCached(query: String, cacheDir: File, timeoutMs: Int = 20_000, maxAgeH: Long = 24): Answer {
        cacheDir.mkdirs()
        val f = File(cacheDir, "ddg-${key(query)}.json")
        fun read(stale: Boolean): Answer? {
            if (!f.exists()) return null
            if (!stale && System.currentTimeMillis() - f.lastModified() > maxAgeH * 3600 * 1000) return null
            return try {
                val o = JSONObject(f.readText())
                val srcs = mutableListOf<ChatSource>()
                val arr = o.optJSONArray("sources")
                if (arr != null) for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    srcs += ChatSource(s.optString("title"), s.optString("url"), s.optString("snippet"))
                }
                Answer(o.optString("abstract"), srcs, stale)
            } catch (_: Exception) { null }
        }
        read(false)?.let { return it }
        try {
            val ans = ask(query, timeoutMs)
            try {
                val o = JSONObject()
                o.put("abstract", ans.abstract)
                val arr = org.json.JSONArray()
                ans.sources.forEach {
                    val s = JSONObject()
                    s.put("title", it.title); s.put("url", it.url); s.put("snippet", it.snippet)
                    arr.put(s)
                }
                o.put("sources", arr)
                f.writeText(o.toString())
            } catch (_: Exception) {}
            return ans
        } catch (_: Exception) {
            // E09: offline research pack — stale answers beat no answers, labeled as such.
            return read(true) ?: throw IllegalStateException("offline and no saved answer for this query")
        }
    }

    fun ask(query: String, timeoutMs: Int = 20_000): Answer {
        val q = URLEncoder.encode(query, "UTF-8")
        val raw = WebFetch.get("https://api.duckduckgo.com/?q=$q&format=json&no_html=1&skip_disambig=1", timeoutMs)
        val o = JSONObject(raw.decodeToString())
        val out = mutableListOf<ChatSource>()
        val abs = o.optString("AbstractText").take(1200)
        if (o.optString("AbstractURL").isNotBlank()) {
            out += ChatSource(o.optString("Heading").ifBlank { "DuckDuckGo" }, o.optString("AbstractURL"), abs.take(300))
        }
        val topics = o.optJSONArray("RelatedTopics")
        if (topics != null) {
            for (i in 0 until minOf(topics.length(), 6)) {
                val t = topics.optJSONObject(i) ?: continue
                val text = t.optString("Text")
                // RelatedTopics can nest under "Topics".
                if (text.isBlank()) {
                    val sub = t.optJSONArray("Topics")
                    if (sub != null && sub.length() > 0) {
                        val s0 = sub.optJSONObject(0)
                        if (s0 != null && s0.optString("Text").isNotBlank()) {
                            out += ChatSource(s0.optString("Text").take(80), s0.optString("FirstURL"), s0.optString("Text").take(300))
                        }
                    }
                    continue
                }
                val url = t.optString("FirstURL")
                if (url.isNotBlank()) out += ChatSource(text.take(80), url, text.take(300))
            }
        }
        return Answer(abs, out.distinctBy { it.url }.take(6))
    }
}
