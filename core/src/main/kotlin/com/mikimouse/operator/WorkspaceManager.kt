package com.mikimouse.operator

import java.io.File
import java.security.MessageDigest

data class TaskWorkspace(val taskId: String, val root: File) {
    val input: File get() = File(root, "input")
    val work: File get() = File(root, "workspace")
    val output: File get() = File(root, "output")
    val cache: File get() = File(root, "cache")
    val logs: File get() = File(root, "logs")
    val metadata: File get() = File(root, "metadata")
}

class WorkspaceManager(private val operatorRoot: File) {
    fun taskDir(taskId: String): File = File(operatorRoot, "tasks/$taskId")

    fun create(taskId: String): TaskWorkspace {
        require(taskId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "bad task id" }
        val ws = TaskWorkspace(taskId, taskDir(taskId))
        listOf(ws.input, ws.work, ws.output, ws.cache, ws.logs, ws.metadata).forEach { it.mkdirs() }
        return ws
    }

    fun resolve(ws: TaskWorkspace, relative: String): File {
        val f = File(ws.root, relative).normalize()
        require(f.absolutePath.startsWith(ws.root.absolutePath + File.separator)) { "path escape denied: $relative" }
        return f
    }

    fun importBytes(ws: TaskWorkspace, name: String, bytes: ByteArray): File {
        require(!name.contains("/") && !name.contains("..")) { "bad name" }
        val dest = File(ws.input, name)
        dest.writeBytes(bytes)
        return dest
    }

    fun writeWork(ws: TaskWorkspace, name: String, bytes: ByteArray): File {
        val dest = resolve(ws, "workspace/$name")
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        return dest
    }

    fun writeOutput(ws: TaskWorkspace, name: String, bytes: ByteArray): File {
        val dest = resolve(ws, "output/$name")
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        return dest
    }

    fun listInputs(ws: TaskWorkspace): List<File> = ws.input.listFiles()?.toList() ?: emptyList()

    fun sha256(file: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                d.update(buf, 0, n)
            }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    fun cleanupTemp(ws: TaskWorkspace) {
        // Delete cache + workspace intermediates. Never delete input (originals).
        ws.cache.deleteRecursively()
        ws.work.deleteRecursively()
        ws.cache.mkdirs()
        ws.work.mkdirs()
    }
}
