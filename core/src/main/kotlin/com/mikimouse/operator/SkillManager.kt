package com.mikimouse.operator

import java.io.File
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class SkillKind { TEMP, PERMANENT }

@Serializable
data class SkillRecord(
    val id: String,
    val source: String,
    val kind: SkillKind,
    val installedAt: Long = System.currentTimeMillis(),
    val files: List<String> = emptyList(),
)

interface SkillInstaller {
    fun install(id: String, source: String, destDir: File): List<String>
}

/** Downloads a skill's SKILL.md (+ known references) from a raw base URL. */
class HttpSkillInstaller : SkillInstaller {
    override fun install(id: String, source: String, destDir: File): List<String> {
        val paths = listOf("SKILL.md", "references/for-you.md", "references/platform.md", "references/errors.md")
        val saved = mutableListOf<String>()
        destDir.mkdirs()
        for (p in paths) {
            try {
                val bytes = URL("$source/$p").openStream().use { it.readBytes() }
                if (bytes.isEmpty()) continue
                val f = File(destDir, p)
                f.parentFile?.mkdirs()
                f.writeBytes(bytes)
                saved += p
            } catch (_: Exception) {
                // Optional references may 404; SKILL.md absence is checked below.
            }
        }
        if (saved.none { it == "SKILL.md" }) {
            destDir.deleteRecursively()
            throw IllegalStateException("skill $id has no SKILL.md at $source")
        }
        return saved
    }
}

class SkillManager(
    operatorRoot: File,
    private val installer: SkillInstaller = HttpSkillInstaller(),
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true },
) {
    private val dir = File(operatorRoot, "skills")
    private val registryFile = File(dir, "registry.json")

    companion object {
        const val COMPOSIO_SRC = "https://raw.githubusercontent.com/elie222/rakazo/main/.agents/skills/composio"
        const val PR_WATCH_SRC = "https://raw.githubusercontent.com/elie222/rakazo/main/.agents/skills/pr-watch"
        fun knownSource(id: String): String? = when (id) {
            "composio" -> COMPOSIO_SRC
            "pr-watch" -> PR_WATCH_SRC
            else -> null
        }
    }

    fun list(): List<SkillRecord> {
        if (!registryFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<SkillRecord>>(registryFile.readText())
        } catch (_: Exception) { emptyList() }
    }

    fun get(id: String): SkillRecord? = list().find { it.id == id }

    fun skillDir(id: String): File = File(dir, id)

    /** Ensure a skill exists; installs at least as TEMP so work never blocks. */
    fun ensure(id: String, source: String? = null, permanent: Boolean = false): SkillRecord {
        get(id)?.let { return it }
        val src = source ?: knownSource(id)
            ?: throw IllegalArgumentException("unknown skill $id and no source given")
        val dest = if (permanent) skillDir(id) else File(dir, "tmp/$id")
        val files = installer.install(id, src, dest)
        val rec = SkillRecord(id, src, if (permanent) SkillKind.PERMANENT else SkillKind.TEMP, files = files)
        save(list() + rec)
        return rec
    }

    fun promote(id: String): SkillRecord {
        val rec = get(id) ?: throw IllegalArgumentException("no skill $id")
        if (rec.kind == SkillKind.PERMANENT) return rec
        val tmp = File(dir, "tmp/$id")
        val dest = skillDir(id)
        if (tmp.exists()) {
            dest.deleteRecursively()
            tmp.copyRecursively(dest)
            tmp.deleteRecursively()
        }
        val updated = rec.copy(kind = SkillKind.PERMANENT)
        save(list().map { if (it.id == id) updated else it })
        return updated
    }

    fun teach(id: String, trigger: String, notes: String): SkillRecord {
        val clean = id.lowercase().replace(Regex("[^a-z0-9_-]"), "-").take(40)
        require(clean.length >= 3) { "teach: name too short" }
        require(get(clean) == null) { "teach: $clean already exists" }
        val dest = skillDir(clean)
        dest.mkdirs()
        File(dest, "SKILL.md").writeText(
            "---\nname: $clean\ndescription: Taught: $trigger\n---\n\n# $clean\n\n" +
                "Trigger: $trigger\n\nNotes:\n$notes\n",
        )
        val rec = SkillRecord(clean, "local:teach", SkillKind.PERMANENT, files = listOf("SKILL.md"))
        save(list() + rec)
        return rec
    }

    fun remove(id: String) {
        skillDir(id).deleteRecursively()
        File(dir, "tmp/$id").deleteRecursively()
        save(list().filter { it.id != id })
    }

    private fun save(records: List<SkillRecord>) {
        dir.mkdirs()
        registryFile.writeText(json.encodeToString(records))
    }
}
