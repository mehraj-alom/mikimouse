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
import com.mikimouse.app.data.SettingsStore
import com.mikimouse.app.data.TaskRepository
import com.mikimouse.app.media.AndroidMediaRenderer
import com.mikimouse.app.media.AndroidPdfRenderer
import com.mikimouse.app.media.SlideshowEncoder
import com.mikimouse.app.models.SecureKeyStore
import com.mikimouse.app.outputs.Notifications
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
    val settings = remember { SettingsStore(ctx) }
    var instruction by remember { mutableStateOf("Create a professional 30-second video from these files.") }
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<TaskPlan?>(null) }
    var currentStep by remember { mutableStateOf<String?>(null) }
    var pendingStep by remember { mutableStateOf<PlanStep?>(null) }
    var pendingGate by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }

    fun previewPlan() {
        plan = Planner().plan(TaskSpec("preview", instruction))
    }
    LaunchedEffect(Unit) { previewPlan() }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach {
            try { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
        picked = uris
        previewPlan()
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Box(Modifier.fillMaxSize()) {
        GlassBackground()
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MOBILE OPERATOR", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "RAM target ${settings.ramTargetMB} MB · quota ${settings.storageQuotaMB} MB · " +
                                "${settings.cpu} · net ${settings.network} · ${settings.providerId}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = instruction, onValueChange = { instruction = it },
                            label = { Text("What do you want me to do?") },
                            modifier = Modifier.fillMaxWidth(), minLines = 2,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pickFiles.launch(arrayOf("*/*")) }) { Text("Pick files (${picked.size})") }
                            Button(
                                enabled = !running && picked.isNotEmpty(),
                                onClick = {
                                    running = true
                                    currentStep = null
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val lines = mutableListOf<String>()
                                            val taskId = "task-${System.currentTimeMillis()}"
                                            val wm = WorkspaceManager(operatorRoot)
                                            val ws = wm.create(taskId)
                                            val imported = TaskRepository(ctx, wm).importUris(ws, picked)
                                            lines += "Imported ${imported.size}: " + imported.joinToString { "${it.name} (${it.length()}b)" }
                                            val budget = settings.budget()
                                            val router = try {
                                                settings.router(SecureKeyStore())
                                            } catch (t: Throwable) {
                                                lines += "Model config issue (${t.message?.take(100)}); using local stub."
                                                ModelRouter()
                                            }
                                            val rt = OperatorRuntime(
                                                operatorRoot, resources = ResourceManager(budget), models = router,
                                            )
                                            val p = Planner().plan(TaskSpec(taskId, instruction, budget = budget))
                                            withContext(Dispatchers.Main) { plan = p }
                                            val res = rt.run(
                                                TaskSpec(taskId, instruction, budget = budget),
                                                approve = { step ->
                                                    withContext(Dispatchers.Main) { currentStep = step.id }
                                                    val d = CompletableDeferred<Boolean>()
                                                    withContext(Dispatchers.Main) {
                                                        pendingStep = step
                                                        pendingGate = d
                                                    }
                                                    d.await()
                                                },
                                            )
                                            withContext(Dispatchers.Main) { currentStep = null }
                                            lines += "Task ${res.state}: ${res.summary}"
                                            val wantVideo = "video" in instruction.lowercase() || "mp4" in instruction.lowercase()
                                            val wantPres = listOf("presentation", "pptx", "slide").any { it in instruction.lowercase() }
                                            val images = imported.filter {
                                                it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
                                            }
                                            if (wantVideo) {
                                                if (images.isNotEmpty()) {
                                                    val secs = Regex("(\\d{1,3})\\s*-?\\s*second").find(instruction.lowercase())
                                                        ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(5, 180) ?: 30
                                                    val mp4 = File(ws.output, "final_video.mp4")
                                                    val enc = SlideshowEncoder.encode(images, mp4, totalSeconds = secs)
                                                    val probe = AndroidMediaRenderer.probe(mp4)
                                                    lines += "Rendered final_video.mp4: ${mp4.length()} bytes, " +
                                                        "${enc.frames} frames, probe=${probe?.mime ?: "unreadable"}"
                                                } else {
                                                    lines += "Blocked: video needs an image; got ${imported.map { it.extension }}. Nothing faked."
                                                }
                                            }
                                            if (wantPres) {
                                                val pptx = File(ws.output, "presentation.pptx")
                                                if (!pptx.exists()) {
                                                    val texts = imported.map { it.name } + instruction
                                                    val slides = DocumentPipeline.outlineFromTexts("Operator Report", texts)
                                                    AndroidPdfRenderer.renderSlides(
                                                        "Operator Report", slides, File(ws.output, "presentation.pdf"),
                                                    )
                                                    lines += "PPTX unavailable on-device (no java.awt); rendered real PDF + Markdown instead."
                                                } else {
                                                    lines += "Rendered presentation.pptx (${pptx.length()} bytes)."
                                                }
                                            }
                                            withContext(Dispatchers.Main) { log = lines }
                                            val done = ws.output.listFiles()?.maxByOrNull { it.length() }
                                            if (Build.VERSION.SDK_INT < 33 ||
                                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                                                PackageManager.PERMISSION_GRANTED
                                            ) {
                                                Notifications.notify(ctx, taskId.hashCode(), "Task done", "${done?.name ?: "task"} ready in My outputs.")
                                            } else {
                                                withContext(Dispatchers.Main) { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                            }
                                        } catch (t: Throwable) {
                                            withContext(Dispatchers.Main) {
                                                currentStep = null
                                                log = listOf("failed: ${t.message}")
                                            }
                                        } finally {
                                            withContext(Dispatchers.Main) { running = false }
                                        }
                                    }
                                },
                            ) { Text(if (running) "Working…" else "Run") }
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Task timeline", style = MaterialTheme.typography.titleMedium)
                        plan?.steps?.forEach { s ->
                            val mark = when {
                                currentStep == null -> "○"
                                s.id == currentStep -> "◉"
                                else -> "○"
                            }
                            Text(
                                "$mark ${s.description} [${s.tool}/${s.risk}]",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } ?: Text("Pick files to preview the plan…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Active task", style = MaterialTheme.typography.titleMedium)
                        (log.ifEmpty { listOf("Plan → collect → process → validate → export → clean") }).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall)
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
