package com.mikimouse.app.outputs

import com.mikimouse.operator.ChatSource
import com.mikimouse.operator.WebFetch
import org.json.JSONObject
import java.net.URLEncoder

object DdgSearch {
    data class Answer(val abstract: String, val sources: List<ChatSource>)

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
