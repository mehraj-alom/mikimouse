package com.mikimouse.app.outputs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mikimouse.operator.TaskManager
import com.mikimouse.operator.TaskSpec
import com.mikimouse.operator.TaskState
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupManager {
    fun backup(ctx: Context, operatorRoot: File): File {
        val backupDir = File(operatorRoot, "backups")
        backupDir.mkdirs()
        val backupFile = File(backupDir, "backup-${System.currentTimeMillis()}.zip")
        ZipOutputStream(backupFile.outputStream().buffered()).use { z ->
            // Backup tasks directory
            val tasksDir = File(operatorRoot, "tasks")
            if (tasksDir.exists()) {
                tasksDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relPath = tasksDir.relativeTo(file).toString()
                        z.putNextEntry(ZipEntry("tasks/$relPath"))
                        file.inputStream().use { z.write(it.readBytes()) }
                        z.closeEntry()
                    }
                }
            }
            // Backup memory
            val memoryDir = File(operatorRoot, "memory")
            if (memoryDir.exists()) {
                memoryDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relPath = memoryDir.relativeTo(file).toString()
                        z.putNextEntry(ZipEntry("memory/$relPath"))
                        file.inputStream().use { z.write(it.readBytes()) }
                        z.closeEntry()
                    }
                }
            }
            // Backup chats
            val chatsDir = File(operatorRoot, "chats")
            if (chatsDir.exists()) {
                chatsDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relPath = chatsDir.relativeTo(file).toString()
                        z.putNextEntry(ZipEntry("chats/$relPath"))
                        file.inputStream().use { z.write(it.readBytes()) }
                        z.closeEntry()
                    }
                }
            }
            // Backup skills registry
            val skillsDir = File(operatorRoot, "skills")
            if (skillsDir.exists()) {
                skillsDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relPath = skillsDir.relativeTo(file).toString()
                        z.putNextEntry(ZipEntry("skills/$relPath"))
                        file.inputStream().use { z.write(it.readBytes()) }
                        z.closeEntry()
                    }
                }
            }
        }
        return backupFile
    }

    fun restore(ctx: Context, operatorRoot: File, backupFile: File): Boolean {
        return try {
            java.util.zip.ZipFile(backupFile).use { z ->
                z.entries().asSequence().forEach { entry ->
                    val entryFile = File(operatorRoot, entry.name)
                    entryFile.parentFile?.mkdirs()
                    if (!entry.isDirectory) {
                        z.getInputStream(entry).use { ins ->
                            entryFile.outputStream().use { ins.copyTo(it) }
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareBackup(ctx: Context, backupFile: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "com.mikimouse.app.fileprovider", backupFile)
        val i = android.content.Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(android.content.Intent.createChooser(i, "Share backup"))
    }
}
