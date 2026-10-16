package com.mikimouse.operator

import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class Artifact(
    val id: String,
    val type: String,
    val filename: String,
    val size: Long,
    val checksum: String,
    val sourceTask: String,
    val createdAt: Long = System.currentTimeMillis(),
)

class ArtifactManager(private val ws: WorkspaceManager) {
    fun register(taskId: String, file: File, type: String): Artifact {
        require(file.exists() && file.length() > 0) { "empty artifact: ${file.name}" }
        val tw = TaskWorkspace(taskId, ws.taskDir(taskId))
        return Artifact(
            id = "$taskId-${file.name}",
            type = type, filename = file.name, size = file.length(),
            checksum = ws.sha256(file), sourceTask = taskId,
        )
    }

    fun validateVideoMp4(file: File): Boolean {
        if (!file.exists() || file.length() < 64) return false
        val head = ByteArray(64)
        file.inputStream().use { it.read(head) }
        val s = String(head)
        return s.contains("ftyp") || s.contains("mp4") || file.extension.lowercase() == "mp4" && file.length() > 1024
    }

    fun validatePptx(file: File): Boolean {
        if (!file.exists() || file.length() < 100) return false
        return try {
            java.util.zip.ZipFile(file).use { z ->
                z.getEntry("[Content_Types].xml") != null && z.getEntry("ppt/presentation.xml") != null
            }
        } catch (_: Exception) { false }
    }

    fun verifiedCopy(taskId: String, src: File, destDir: File): File {
        require(src.absolutePath.startsWith(ws.taskDir(taskId).absolutePath)) { "export must come from task dir" }
        destDir.mkdirs()
        val dest = File(destDir, src.name)
        src.copyTo(dest, overwrite = true)
        require(dest.length() == src.length()) { "export verify failed: size mismatch" }
        return dest
    }
}
