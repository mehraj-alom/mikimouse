package com.mikimouse.operator

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatSource(val title: String, val url: String, val snippet: String = "")

@Serializable
data class ChatMessage(
    val role: String,
    val text: String,
    val sources: List<ChatSource> = emptyList(),
    val followUps: List<String> = emptyList(),
    val ts: Long = System.currentTimeMillis(),
)

class ConversationStore(
    private val file: File,
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true },
) {
    fun load(): List<ChatMessage> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<ChatMessage>>(file.readText())
        } catch (_: Exception) { emptyList() }
    }

    fun append(msg: ChatMessage) {
        var all = load() + msg
        // F10 compaction: keep first 5 (task roots) + last 80, drop the middle with a marker.
        if (all.size > 100) {
            val dropped = all.size - 85
            all = all.take(5) + ChatMessage("system", "…$dropped older messages compacted…", ts = 0L) + all.takeLast(80)
        } else {
            all = all.takeLast(100)
        }
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(all))
    }

    fun clear() {
        file.delete()
    }
}

object FollowUps {
    fun suggest(instruction: String, answer: String): List<String> {
        val t = (instruction + " " + answer.take(500)).lowercase()
        val out = mutableListOf<String>()
        if ("video" in t) {
            out += "Make a presentation from these files"
            out += "Compress the video to a smaller size"
        }
        if ("presentation" in t || "slide" in t || "report" in t || "pdf" in t) {
            out += "Export it as PDF as well"
            out += "Turn the key points into a short video"
        }
        if ("research" in t || "download" in t) {
            out += "Save this as a document in My outputs"
        }
        if (out.isEmpty()) out += "Summarize what was produced"
        return out.distinct().take(3)
    }
}
