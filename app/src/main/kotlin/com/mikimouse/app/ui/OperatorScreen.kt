package com.mikimouse.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mikimouse.app.data.TaskRepository
import com.mikimouse.app.media.AndroidMediaRenderer
import com.mikimouse.app.media.SlideshowEncoder
import com.mikimouse.app.outputs.Notifications
import com.mikimouse.app.outputs.OutputFile
import com.mikimouse.app.outputs.OutputStore
import com.mikimouse.operator.*
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OperatorScreen(operatorRoot: File) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var instruction by remember { mutableStateOf("Create a professional 30-second video from these files.") }
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var outputs by remember { mutableStateOf<List<OutputFile>>(emptyList()) }
    var pendingStep by remember { mutableStateOf<PlanStep?>(null) }
    var pendingGate by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var exportFile by remember { mutableStateOf<File?>(null) }

    fun refreshOutputs() { outputs = OutputStore.listAll(operatorRoot) }
    LaunchedEffect(Unit) { refreshOutputs() }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach {
            try { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
        picked = uris
    }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val src = exportFile
        if (uri != null && src != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    TaskRepository(ctx, WorkspaceManager(operatorRoot)).exportToUri(src, uri)
                    withContext(Dispatchers.Main) { log = log + "Exported ${src.name} ✓" }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) { log = log + "Export failed: ${t.message}" }
                }
            }
        }
        exportFile = null
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun openOutput(o: OutputFile) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "com.mikimouse.app.fileprovider", o.file)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, OutputStore.mimeFor(o.file.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "Open ${o.file.name}"))
        } catch (t: Throwable) {
            log = log + "No app can open ${o.file.name}"
        }
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
                    scope.launch(Dispatchers.IO) {
                        try {
                            val lines = mutableListOf<String>()
                            val taskId = "task-${System.currentTimeMillis()}"
                            val wm = WorkspaceManager(operatorRoot)
                            val ws = wm.create(taskId)
                            // 1. Real import (streaming copy, originals untouched).
                            val imported = TaskRepository(ctx, wm).importUris(ws, picked)
                            lines += "Imported ${imported.size}: " + imported.joinToString { "${it.name} (${it.length()}b)" }
                            // 2. Plan + approve MEDIUM/HIGH via dialog.
                            val rt = OperatorRuntime(operatorRoot)
                            val images = imported.filter {
                                it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
                            }
                            val videos = imported.filter { it.extension.lowercase() in setOf("mp4", "mov", "mkv", "3gp") }
                            val res = rt.run(
                                TaskSpec(taskId, instruction),
                                approve = { step ->
                                    val d = CompletableDeferred<Boolean>()
                                    withContext(Dispatchers.Main) {
                                        pendingStep = step
                                        pendingGate = d
                                    }
                                    d.await()
                                },
                            )
                            lines += "Task ${res.state}: ${res.summary}"
                            // 3. Real playable mp4 from the picked images (hardware AVC).
                            val wantVideo = "video" in instruction.lowercase() || "mp4" in instruction.lowercase()
                            if (wantVideo) {
                                if (images.isNotEmpty()) {
                                    val secs = Regex("(\\d{1,3})\\s*-?\\s*second").find(instruction.lowercase())
                                        ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(5, 180) ?: 30
                                    val mp4 = File(ws.output, "final_video.mp4")
                                    val enc = SlideshowEncoder.encode(images, mp4, totalSeconds = secs)
                                    val probe = AndroidMediaRenderer.probe(mp4)
                                    lines += "Rendered final_video.mp4: ${mp4.length()} bytes, " +
                                        "${enc.frames} frames, probe=${probe?.mime ?: "unreadable"}"
                                    if (videos.isNotEmpty()) {
                                        lines += "Note: ${videos.size} video clip(s) catalogued; clip remixing " +
                                            "is P1 — slideshow used your ${images.size} image(s)."
                                    }
                                } else {
                                    lines += "Blocked: video needs at least one image; " +
                                        "only ${imported.map { it.extension }} provided. Nothing faked."
                                }
                            }
                            withContext(Dispatchers.Main) {
                                log = lines
                                refreshOutputs()
                            }
                            // 4. Reminder: completion notification.
                            val done = File(ws.output, "final_video.mp4").takeIf { it.exists() }
                                ?: ws.output.listFiles()?.firstOrNull()
                            if (Build.VERSION.SDK_INT < 33 ||
                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                Notifications.notify(ctx, taskId.hashCode(), "Task done", "${done?.name ?: "task"} ready in My outputs.")
                            } else {
                                withContext(Dispatchers.Main) { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            }
                        } catch (t: Throwable) {
                            withContext(Dispatchers.Main) { log = listOf("failed: ${t.message}") }
                        } finally {
                            withContext(Dispatchers.Main) { running = false }
                        }
                    }
                },
            ) { Text(if (running) "Working…" else "Run") }
        }

        Text("MY OUTPUTS (in-app)", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(log) { Text(it, style = MaterialTheme.typography.bodySmall) }
            items(outputs, key = { it.taskId + it.file.name }) { o ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("${o.file.name} (${o.sizeBytes} bytes)", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { openOutput(o) }) { Text("Open") }
                            TextButton(onClick = { exportFile = o.file; createDoc.launch(o.file.name) }) { Text("Export") }
                            TextButton(onClick = {
                                Notifications.remindInOneHour(ctx, o.file.name)
                                log = log + "Reminder set for ${o.file.name} (1 hour)"
                            }) { Text("Remind 1h") }
                            TextButton(onClick = {
                                o.file.delete()
                                refreshOutputs()
                            }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    pendingStep?.let { step ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Approval needed") },
            text = { Text("${step.description}\nTool: ${step.tool} / risk: ${step.risk}") },
            confirmButton = {
                TextButton(onClick = { pendingGate?.complete(true); pendingStep = null }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { pendingGate?.complete(false); pendingStep = null }) { Text("Deny") }
            },
        )
    }
}
