package com.mikimouse.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mikimouse.app.data.KeystoreKeysFile
import com.mikimouse.app.data.SettingsStore
import com.mikimouse.app.intents.OperatorIntents
import com.mikimouse.app.models.SecureKeyStore
import com.mikimouse.app.outputs.BackupManager
import com.mikimouse.app.outputs.Notifications
import com.mikimouse.app.outputs.Schedules
import com.mikimouse.operator.ConnectorRegistry
import com.mikimouse.operator.CpuPolicy
import com.mikimouse.operator.LocalLlamaCppProvider
import com.mikimouse.operator.LocalOnnxProvider
import com.mikimouse.operator.MemoryStore
import com.mikimouse.operator.NetworkPolicy
import com.mikimouse.operator.SkillKind
import com.mikimouse.operator.SkillManager
import com.mikimouse.operator.SkillRecord
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(operatorRoot: File) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore(ctx) }
    val skills = remember { SkillManager(operatorRoot) }
    val ks = remember { SecureKeyStore() }
    val keysDir = remember { File(ctx.filesDir, "keys") }
    val memFile = remember { File(operatorRoot, "memory/prefs.json") }
    var msg by remember { mutableStateOf("") }

    var ram by remember { mutableStateOf(store.ramTargetMB) }
    var quota by remember { mutableStateOf(store.storageQuotaMB) }
    var cpu by remember { mutableStateOf(store.cpu) }
    var net by remember { mutableStateOf(store.network) }
    var provider by remember { mutableStateOf(store.providerId) }
    var endpoint by remember { mutableStateOf(store.endpoint) }
    var model by remember { mutableStateOf(store.model) }
    var apiKey by remember { mutableStateOf("") }
    var memEnabled by remember { mutableStateOf(store.memoryEnabled) }
    var memView by remember { mutableStateOf<Map<String, String>?>(null) }
    var skillList by remember { mutableStateOf<List<SkillRecord>>(emptyList()) }
    var busySkill by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { skillList = skills.list() }

    fun installSkill(id: String) {
        if (store.network == NetworkPolicy.OFFLINE_ONLY) {
            msg = "Offline mode: skill install needs network"
            return
        }
        busySkill = id
        scope.launch(Dispatchers.IO) {
            try {
                skills.ensure(id)
                skillList = skills.list()
                msg = "Skill $id installed (temp) ✓"
            } catch (t: Throwable) {
                msg = "Skill $id failed: ${t.message?.take(140)}"
            } finally {
                busySkill = null
            }
        }
    }

    fun keyAlias() = when (provider) {
        "openai" -> "OPENAI_API_KEY"
        "openrouter" -> "OPENROUTER_API_KEY"
        else -> "CUSTOM_API_KEY"
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Runtime budget", style = MaterialTheme.typography.titleMedium)
                    Text("RAM target (never a reservation): $ram MB")
                    listOf(512, 1024, 2048, 3072, 4096).forEach { v ->
                        Row {
                            RadioButton(selected = ram == v, onClick = { ram = v; store.ramTargetMB = v })
                            TextButton(onClick = { ram = v; store.ramTargetMB = v }) { Text("$v MB${if (v == 2048) " (auto)" else ""}") }
                        }
                    }
                    Text("Storage quota: $quota MB")
                    listOf(1024, 2048, 5120, 10240, 20480).forEach { v ->
                        Row {
                            RadioButton(selected = quota == v, onClick = { quota = v; store.storageQuotaMB = v })
                            TextButton(onClick = { quota = v; store.storageQuotaMB = v }) { Text("${v / 1024} GB") }
                        }
                    }
                    Text("CPU policy")
                    CpuPolicy.entries.forEach { c ->
                        Row {
                            RadioButton(selected = cpu == c, onClick = { cpu = c; store.cpu = c })
                            TextButton(onClick = { cpu = c; store.cpu = c }) { Text(c.name) }
                        }
                    }
                    Text("Network")
                    NetworkPolicy.entries.forEach { n ->
                        Row {
                            RadioButton(selected = net == n, onClick = { net = n; store.network = n })
                            TextButton(onClick = { net = n; store.network = n }) { Text(n.name) }
                        }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Model provider", style = MaterialTheme.typography.titleMedium)
                    listOf("local", "openai", "openrouter", "custom", "vlm-openai", "vlm-openrouter", "vlm-custom", "local-llama-cpp", "local-onnx").forEach { p ->
                        Row {
                            RadioButton(selected = provider == p, onClick = { provider = p; store.providerId = p })
                            TextButton(onClick = { provider = p; store.providerId = p }) {
                                Text(when (p) {
                                    "local" -> "Local (offline, private)"
                                    "openai" -> "OpenAI (text)"
                                    "openrouter" -> "OpenRouter (text)"
                                    "vlm-openai" -> "OpenAI GPT-4V (vision)"
                                    "vlm-openrouter" -> "OpenRouter VLM (vision)"
                                    "vlm-custom" -> "Custom VLM (vision)"
                                    "local-llama-cpp" -> "Local llama.cpp (GGUF)"
                                    "local-onnx" -> "Local ONNX Runtime"
                                    "custom" -> "Custom API"
                                    else -> p
                                })
                            }
                        }
                    }
                    OutlinedTextField(endpoint, { endpoint = it; store.endpoint = it }, label = { Text("Endpoint (custom)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(model, { model = it; store.model = it }, label = { Text("Model (e.g. gpt-4o, claude-3-opus, gemini-pro-vision)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key (Keystore, never plaintext prefs)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            try {
                                KeystoreKeysFile.save(keyAlias(), apiKey, ks, keysDir)
                                apiKey = ""
                                msg = "Key saved for ${keyAlias()} ✓"
                            } catch (t: Throwable) { msg = "Key save failed: ${t.message}" }
                        }) { Text("Save key") }
                        TextButton(onClick = {
                            KeystoreKeysFile.clear(keysDir, keyAlias())
                            msg = "Key cleared"
                        }) { Text("Clear") }
                        TextButton(onClick = {
                            listOf("OPENAI_API_KEY", "OPENROUTER_API_KEY", "CUSTOM_API_KEY").forEach {
                                KeystoreKeysFile.clear(keysDir, it)
                            }
                            msg = "All keys wiped"
                        }) { Text("Wipe all") }
                    }
                    val has = KeystoreKeysFile.loadFrom(keysDir, keyAlias(), ks) != null
                    Text(if (has) "Key stored ✓" else "No key stored", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(store.llamaModelPath, { store.llamaModelPath = it }, label = { Text("llama.cpp model path (GGUF file)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(store.onnxModelPath, { store.onnxModelPath = it }, label = { Text("ONNX model path") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(store.telegramBotToken, { store.telegramBotToken = it }, label = { Text("Telegram Bot Token (optional)") }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Memory (local only)", style = MaterialTheme.typography.titleMedium)
                    Row {
                        Checkbox(checked = memEnabled, onCheckedChange = { memEnabled = it; store.memoryEnabled = it })
                        Text("Memory enabled")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { memView = MemoryStore(memFile).all() }) { Text("View") }
                        TextButton(onClick = {
                            MemoryStore(memFile).deleteAll()
                            memView = emptyMap()
                            msg = "Memory deleted"
                        }) { Text("Delete") }
                    }
                    memView?.forEach { (k, v) -> Text("$k: ${v.take(80)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Skills (auto-installed as temp when needed)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tasks install missing skills automatically (temp) and keep this list for later reference.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = busySkill == null, onClick = { installSkill("composio") }) {
                            Text(if (busySkill == "composio") "…" else "Get composio")
                        }
                        Button(enabled = busySkill == null, onClick = { installSkill("pr-watch") }) {
                            Text(if (busySkill == "pr-watch") "…" else "Get pr-watch")
                        }
                    }
                    var teachName by remember { mutableStateOf("") }
                    var teachTrigger by remember { mutableStateOf("") }
                    var teachNotes by remember { mutableStateOf("") }
                    OutlinedTextField(teachName, { teachName = it }, label = { Text("Teach: skill name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(teachTrigger, { teachTrigger = it }, label = { Text("Teach: when to use it") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(teachNotes, { teachNotes = it }, label = { Text("Teach: notes") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                skills.teach(teachName, teachTrigger, teachNotes)
                                teachName = ""; teachTrigger = ""; teachNotes = ""
                                skillList = skills.list()
                                msg = "Skill taught ✓"
                            } catch (t: Throwable) {
                                msg = "Teach failed: ${t.message?.take(140)}"
                            }
                        }
                    }) { Text("Teach skill") }
                    if (skillList.isEmpty()) Text("No skills installed yet", style = MaterialTheme.typography.bodySmall)
                    skillList.forEach { s ->
                        Row {
                            Text(
                                "${if (s.kind == SkillKind.PERMANENT) "●" else "○"} ${s.id} [${s.kind}] · ${s.files.size} files",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (s.kind == SkillKind.TEMP) {
                                TextButton(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        skills.promote(s.id)
                                        skillList = skills.list()
                                    }
                                }) { Text("Keep") }
                            }
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    skills.remove(s.id)
                                    skillList = skills.list()
                                }
                            }) { Text("Remove") }
                        }
                        Text(s.source, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scheduled tasks", style = MaterialTheme.typography.titleMedium)
                    Text("Runs an instruction on a timer (no inputs). Heavy renders defer under 20% battery.", style = MaterialTheme.typography.bodySmall)
                    var schedText by remember { mutableStateOf("") }
                    var schedHours by remember { mutableStateOf(24L) }
                    OutlinedTextField(schedText, { schedText = it }, label = { Text("Instruction (blank = off)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6L, 12L, 24L, 168L).forEach { h ->
                            Row {
                                RadioButton(selected = schedHours == h, onClick = { schedHours = h })
                                TextButton(onClick = { schedHours = h }) { Text(if (h < 24) "${h}h" else if (h == 24L) "daily" else "weekly") }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (schedText.isBlank()) {
                                Schedules.cancel(ctx)
                                msg = "Schedule off"
                            } else {
                                Schedules.set(ctx, schedText, schedHours)
                                msg = "Scheduled every ${schedHours}h ✓"
                            }
                        }) { Text("Save") }
                        TextButton(onClick = { Schedules.cancel(ctx); msg = "Schedule off" }) { Text("Off") }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privacy — what leaves this phone", style = MaterialTheme.typography.titleMedium)
                    Text("• Local model / offline: nothing leaves.", style = MaterialTheme.typography.bodySmall)
                    Text("• Remote model: your prompt + OCR text (PII masked) go to your configured endpoint only. Logged per task.", style = MaterialTheme.typography.bodySmall)
                    Text("• Web answers: query goes to DuckDuckGo; page fetches go to the sites you asked for.", style = MaterialTheme.typography.bodySmall)
                    Text("• Skills: fetched once from GitHub raw, then kept on-device.", style = MaterialTheme.typography.bodySmall)
                    Text("• Never: accessibility capture, banking apps, silent sends.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About this build", style = MaterialTheme.typography.titleMedium)
                    Text("MikiMouse Operator 0.9.0-mvp (8 early-access builds)", style = MaterialTheme.typography.bodySmall)
                    Text("200-phase plan: 100+ done. Full log in docs/MASTER-PLAN-200.md.", style = MaterialTheme.typography.bodySmall)
                    Text("Backup: workspace caches never backed up; outputs + memory survive.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val backupFile = BackupManager.backup(ctx, operatorRoot)
                                    withContext(Dispatchers.Main) {
                                        msg = "Backup created: ${backupFile.name}"
                                        BackupManager.shareBackup(ctx, backupFile)
                                    }
                                } catch (t: Throwable) {
                                    withContext(Dispatchers.Main) { msg = "Backup failed: ${t.message?.take(140)}" }
                                }
                            }
                        }) { Text("Backup") }
                        Button(onClick = {
                            msg = "Restore: use file picker to select backup zip"
                        }) { Text("Restore") }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connectors (intent-first)", style = MaterialTheme.typography.titleMedium)
                    ConnectorRegistry.all.forEach { c ->
                        val live = remember(c.id) { OperatorIntents.canHandle(ctx, c.deepLink) }
                        Row {
                            Text(
                                "${if (c.available || live) "✓" else "✕"} ${c.label}: ${c.note}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (live) {
                                TextButton(onClick = { OperatorIntents.openInApp(ctx, c.deepLink) }) { Text("Open") }
                            }
                        }
                    }
                    Text("No AccessibilityService. Banking apps untouched.", style = MaterialTheme.typography.bodySmall)
                    if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
