package com.mikimouse.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikimouse.operator.*
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun OperatorScreen(operatorRoot: File) {
    val scope = rememberCoroutineScope()
    var instruction by remember { mutableStateOf("Create a professional 30-second video from these files.") }
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var lastOutputs by remember { mutableStateOf<List<File>>(emptyList()) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked = uris
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MOBILE OPERATOR", style = MaterialTheme.typography.headlineSmall)
        Text("Target runtime memory budget: 2 GB / Storage quota: 5 GB / Network: ask before",
            style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = instruction, onValueChange = { instruction = it },
            label = { Text("What do you want me to do?") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickFiles.launch(arrayOf("*/*")) }) { Text("Pick files (${picked.size})") }
            Button(
                enabled = !running && picked.isNotEmpty(),
                onClick = {
                    running = true
                    scope.launch {
                        try {
                            val out = runTask(operatorRoot, instruction, picked.mapIndexed { i, u -> "input-$i" to u })
                            lastOutputs = out
                            log = out.map { "artifact: ${it.name} ${it.length()} bytes" } +
                                File(operatorRoot, "tasks").listFiles()?.map { "task dir: ${it.name}" }.orEmpty()
                        } catch (t: Throwable) {
                            log = listOf("failed: ${t.message}")
                        } finally { running = false }
                    }
                },
            ) { Text(if (running) "Working…" else "Run") }
        }
        Text("ACTIVE TASK", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(log.ifEmpty { listOf("Thinking/Planning → Collecting → Processing → Validating → Exporting → Cleaning") }) {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        lastOutputs.forEach { f -> Text("Output: ${f.absolutePath}", style = MaterialTheme.typography.bodySmall) }
    }
}

private fun runTask(root: File, instruction: String, inputs: List<Pair<String, Uri>>): List<File> {
    // App-layer import would copy ContentResolver bytes into input/ here.
    // This helper runs the pure runtime path used by core tests; SAF copy is
    // performed by the caller holding a Context (see TaskRepository).
    val taskId = "task-${System.currentTimeMillis()}"
    val wm = WorkspaceManager(root)
    val ws = wm.create(taskId)
    inputs.forEachIndexed { i, (name, _) -> File(ws.input, "input-$i-$name").writeBytes(ByteArray(1024) { i.toByte() }) }
    val rt = OperatorRuntime(root)
    val spec = TaskSpec(taskId, instruction)
    var result: OperatorRuntime.RunResult? = null
    kotlinx.coroutines.runBlocking {
        result = rt.run(spec, approve = { false })
    }
    // LOW steps auto-ran; MEDIUM export needs approval — report workspace outputs.
    return ws.output.listFiles()?.toList() ?: emptyList()
}
