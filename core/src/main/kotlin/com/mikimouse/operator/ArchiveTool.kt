package com.mikimouse.operator

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ArchiveTool {
    fun zip(files: List<File>, out: File): File {
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            files.forEach { f ->
                z.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return out
    }

    fun unzip(zip: File, destDir: File): List<File> {
        destDir.mkdirs()
        val out = mutableListOf<File>()
        ZipFile(zip).use { z ->
            z.entries().asSequence().forEach { e ->
                require(!e.name.contains("..")) { "zip path escape: ${e.name}" }
                val f = File(destDir, e.name)
                if (e.isDirectory) f.mkdirs() else {
                    f.parentFile?.mkdirs()
                    z.getInputStream(e).use { ins -> f.outputStream().use { o -> ins.copyTo(o) } }
                    out += f
                }
            }
        }
        return out
    }
}
