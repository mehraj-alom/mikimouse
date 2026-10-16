package com.mikimouse.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mikimouse.app.data.TaskRepository
import com.mikimouse.app.outputs.Notifications
import com.mikimouse.app.outputs.OutputFile
import com.mikimouse.app.outputs.OutputStore
import com.mikimouse.operator.WorkspaceManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun OutputsScreen(operatorRoot: File) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var outputs by remember { mutableStateOf(OutputStore.listAll(operatorRoot)) }
    var exportFile by remember { mutableStateOf<File?>(null) }
    var msg by remember { mutableStateOf("") }

    fun refresh() { outputs = OutputStore.listAll(operatorRoot) }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val src = exportFile
        if (uri != null && src != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    TaskRepository(ctx, WorkspaceManager(operatorRoot)).exportToUri(src, uri)
                    launch(Dispatchers.Main) { msg = "Exported ${src.name} ✓" }
                } catch (t: Throwable) {
                    launch(Dispatchers.Main) { msg = "Export failed: ${t.message}" }
                }
            }
        }
        exportFile = null
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("My outputs", style = MaterialTheme.typography.titleMedium)
                    Text("${outputs.size} artifact(s) kept in-app. Exports survive cleanup.", style = MaterialTheme.typography.bodySmall)
                    if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(outputs, key = { it.taskId + it.file.name }) { o ->
            OutputCard(o, onOpen = {
                try {
                    val uri = FileProvider.getUriForFile(ctx, "com.mikimouse.app.fileprovider", o.file)
                    val i = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, OutputStore.mimeFor(o.file.name))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(i, "Open ${o.file.name}"))
                } catch (_: Exception) { msg = "No app can open ${o.file.name}" }
            }, onExport = { exportFile = o.file; createDoc.launch(o.file.name) }, onRemind = {
                Notifications.remindInOneHour(ctx, o.file.name)
                msg = "Reminder set for ${o.file.name} (1 hour)"
            }, onDelete = { o.file.delete(); refresh() })
        }
    }
}

@Composable
fun OutputCard(o: OutputFile, onOpen: () -> Unit, onExport: () -> Unit, onRemind: () -> Unit, onDelete: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column {
            Text(o.file.name, style = MaterialTheme.typography.bodyMedium)
            Text("${o.sizeBytes} bytes · task ${o.taskId}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpen) { Text("Open") }
                TextButton(onClick = onExport) { Text("Export") }
                TextButton(onClick = onRemind) { Text("Remind 1h") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
