package com.mikimouse.app.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.mikimouse.operator.WorkspaceManager
import com.mikimouse.operator.TaskWorkspace
import java.io.File

class TaskRepository(private val ctx: Context, private val wm: WorkspaceManager) {
    fun importUris(ws: TaskWorkspace, uris: List<Uri>): List<File> {
        val cr = ctx.contentResolver
        return uris.mapIndexed { i, uri ->
            val name = queryName(uri) ?: "input-$i"
            val safe = "input-$i-" + name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
            val dest = File(ws.input, safe)
            // Streaming copy: 16 KB buffer, never whole file in RAM.
            cr.openInputStream(uri)?.use { ins ->
                dest.outputStream().use { os ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                    }
                }
            } ?: throw IllegalStateException("cannot open $uri")
            dest
        }
    }

    private fun queryName(uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }

    fun exportToUri(src: File, destUri: Uri) {
        require(src.exists())
        ctx.contentResolver.openOutputStream(destUri)?.use { os ->
            src.inputStream().use { ins ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    os.write(buf, 0, n)
                }
            }
        } ?: throw IllegalStateException("cannot open destination")
    }
}
