package com.mikimouse.app.outputs

import android.content.Context
import android.content.Intent
import com.mikimouse.app.data.TaskRepository
import com.mikimouse.operator.TaskManager
import com.mikimouse.operator.TaskSpec
import com.mikimouse.operator.TaskState
import com.mikimouse.operator.WorkspaceManager
import java.io.File

object TaskImportExport {
    fun exportTask(ctx: Context, operatorRoot: File, taskId: String): File? {
        val tm = TaskManager(operatorRoot)
        val task = tm.load(taskId) ?: return null
        val wm = WorkspaceManager(operatorRoot)
        val ws = wm.create(taskId)
        val inputs = ws.input.listFiles()?.toList() ?: emptyList<File>()
        val outputs = ws.output.listFiles()?.toList() ?: emptyList<File>()

        val exportDir = File(operatorRoot, "exports")
        exportDir.mkdirs()
        val exportFile = File(exportDir, "task-$taskId-export.json")

        val json = buildString {
            appendLine("{")
            appendLine("  \"taskId\": \"${task.spec.taskId}\",")
            appendLine("  \"instruction\": \"${task.spec.instruction}\",")
            appendLine("  \"state\": \"${task.state}\",")
            appendLine("  \"plan\": ${task.plan?.let { it.steps.size } ?: 0} steps,")
            appendLine("  \"inputs\": [${inputs.joinToString { "\"${it.name}\"" }}],")
            appendLine("  \"outputs\": [${outputs.joinToString { "\"${it.name}\"" }}]")
            appendLine("}")
        }

        exportFile.writeText(json)
        return exportFile
    }

    fun importTask(ctx: Context, operatorRoot: File, importFile: File): String? {
        return try {
            val content = importFile.readText()
            val taskId = "imported-${System.currentTimeMillis()}"
            val instruction = extractInstruction(content)
            if (instruction.isNullOrEmpty()) return null

            val wm = WorkspaceManager(operatorRoot)
            val ws = wm.create(taskId)

            // Copy input files from export directory if they exist
            val exportDir = importFile.parentFile
            exportDir?.listFiles()?.forEach { file ->
                if (file.name.startsWith("input-")) {
                    file.copyTo(File(ws.input, file.name), overwrite = true)
                }
            }

            val tm = TaskManager(operatorRoot)
            tm.create(TaskSpec(taskId, instruction))
            tm.transition(taskId, TaskState.CREATED)

            return taskId
        } catch (_: Exception) {
            null
        }
    }

    private fun extractInstruction(json: String): String? {
        val pattern = Regex("\"instruction\"\\s*:\\s*\"([^\"]+)\"")
        return pattern.find(json)?.groupValues?.get(1)
    }

    fun shareTask(ctx: Context, taskId: String, operatorRoot: File) {
        val file = exportTask(ctx, operatorRoot, taskId) ?: return
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "com.mikimouse.app.fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Share task"))
    }
}
