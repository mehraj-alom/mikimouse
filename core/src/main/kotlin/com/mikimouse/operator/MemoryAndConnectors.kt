package com.mikimouse.operator

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Connector(
    val id: String,
    val label: String,
    val deepLink: String,
    val available: Boolean,
    val note: String = "",
)

object ConnectorRegistry {
    val all = listOf(
        Connector("browser", "Browser", "https://", true, "Opens URLs via VIEW intent."),
        Connector("email", "Email", "mailto:", true, "Composes via SENDTO intent; never sends without approval."),
        Connector("drive", "Drive", "content://", true, "Via Storage Access Framework; no vendor SDK needed."),
        Connector("youtube", "YouTube", "vnd.youtube:", false, "No public upload API on-device; share-link only."),
        Connector("whatsapp", "WhatsApp", "https://wa.me/", false, "No safe on-device send API; text-share via chooser only with approval."),
        Connector("telegram", "Telegram", "https://t.me/", false, "Needs bot token + network approval; per-chat send is HIGH risk."),
        Connector("canva", "Canva", "canva://", false, "No public Android authoring API; export files and open manually."),
    )
}

class MemoryStore(private val file: File, private val json: Json = Json { prettyPrint = true }) {
    var enabled: Boolean = true

    private fun read(): MutableMap<String, String> {
        if (!enabled || !file.exists()) return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, String>>(file.readText()).toMutableMap()
        } catch (_: Exception) { mutableMapOf() }
    }

    fun get(key: String): String? = read()[key]

    fun set(key: String, value: String) {
        if (!enabled) return
        val m = read()
        m[key] = value.take(4000)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(m))
    }

    fun all(): Map<String, String> = read()

    fun exportTo(dest: File): File {
        dest.parentFile?.mkdirs()
        dest.writeText(json.encodeToString(read()))
        return dest
    }

    fun deleteAll() {
        file.delete()
    }
}
