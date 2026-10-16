package com.mikimouse.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mikimouse.app.data.SettingsStore
import com.mikimouse.app.data.TaskRepository
import com.mikimouse.app.intents.DeviceActions
import com.mikimouse.app.intents.OperatorIntents
import com.mikimouse.app.media.AndroidMediaRenderer
import com.mikimouse.app.media.AndroidPdfRenderer
import com.mikimouse.app.media.FrameGrabber
import com.mikimouse.app.media.ImageTools
import com.mikimouse.app.media.OcrReader
import com.mikimouse.app.media.SlideshowEncoder
import com.mikimouse.app.media.VideoTrim
import com.mikimouse.app.models.SecureKeyStore
import com.mikimouse.app.outputs.DdgSearch
import com.mikimouse.app.outputs.Notifications
import com.mikimouse.operator.*
import com.mikimouse.operator.ChatMessage
import com.mikimouse.operator.ChatSource
import com.mikimouse.operator.ConversationStore
import com.mikimouse.operator.FollowUps
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OperatorScreen(operatorRoot: File, initialInstruction: String = "Create a professional 30-second video from these files.") {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(ctx) }
    val tasks = remember { TaskManager(operatorRoot) }
    var instruction by remember { mutableStateOf(initialInstruction) }
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<TaskPlan?>(null) }
    var currentStep by remember { mutableStateOf<String?>(null) }
    var pendingContactUri by remember { mutableStateOf<Uri?>(null) }
    var pendingStep by remember { mutableStateOf<PlanStep?>(null) }
    var pendingGate by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var incomplete by remember { mutableStateOf<List<PersistedTask>>(emptyList()) }
    var crashGuard by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf<List<Pair<String, List<Uri>>>>(emptyList()) }
    var thread by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var lastOutputDir by remember { mutableStateOf<File?>(null) }
    var lastTaskId by remember { mutableStateOf<String?>(null) }
    var exportName by remember { mutableStateOf<String?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var speaking by remember { mutableStateOf(false) }
    val chatStore = remember { ConversationStore(File(operatorRoot, "chats/session.json")) }

    fun refreshIncomplete() {
        incomplete = tasks.listTasks().filter {
            it.state == TaskState.PAUSED || it.state == TaskState.FAILED ||
                it.state == TaskState.WAITING_FOR_USER
        }.sortedByDescending { it.spec.taskId }
        // Crash-loop guard (K06): 3 consecutive failures → warn instead of silent retry loops.
        val recent = tasks.listTasks().sortedByDescending { it.spec.taskId }.take(3)
        crashGuard = recent.size == 3 && recent.all { it.state == TaskState.FAILED }
        thread = chatStore.load().takeLast(20)
    }

    fun previewPlan() {
        plan = Planner().plan(TaskSpec("preview", instruction))
    }
    LaunchedEffect(Unit) {
        previewPlan()
        refreshIncomplete()
        tts = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        }
    }

    fun speak(text: String) {
        val e = tts ?: return
        try {
            e.stop()
            e.speak(text.take(2000), TextToSpeech.QUEUE_FLUSH, null, "op-answer")
            speaking = true
        } catch (_: Exception) {}
    }

    fun stopSpeak() {
        try { tts?.stop() } catch (_: Exception) {}
        speaking = false
    }

    fun exportDebug(taskId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val taskDir = File(operatorRoot, "tasks/$taskId")
                val bundle = File(taskDir, "cache/debug-$taskId.zip")
                ArchiveTool.zip(
                    listOf(File(taskDir, "logs"), File(taskDir, "metadata")).filter { it.exists() },
                    bundle,
                )
                withContext(Dispatchers.Main) { OperatorIntents.shareFile(ctx, bundle) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { log = log + "Debug bundle failed: ${t.message?.take(140)}" }
            }
        }
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach {
            try { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
        picked = uris
        previewPlan()
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun fillContact(uri: Uri) {
        try {
            var name = "contact"
            var number: String? = null
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    val idIdx = c.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                    val id = if (idIdx >= 0) c.getString(idIdx) else null
                    if (id != null) {
                        ctx.contentResolver.query(
                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                            arrayOf(id), null,
                        )?.use { p ->
                            if (p.moveToFirst()) number = p.getString(0)
                        }
                    }
                }
            }
            instruction = if (number != null) "Message $number ($name): " else "Message $name: "
            previewPlan()
        } catch (_: Exception) {}
    }

    val micListen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) {
                instruction = heard
                previewPlan()
            }
        }
    }
    val contactPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val uri = pendingContactUri
        pendingContactUri = null
        if (granted && uri != null) fillContact(uri)
        else log = log + "Contacts permission needed to read the number"
    }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                contactPerm.launch(android.Manifest.permission.READ_CONTACTS)
                pendingContactUri = uri
            } else {
                fillContact(uri)
            }
        }
    }
    val exportDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val dir = lastOutputDir
        val name = exportName
        if (uri != null && dir != null && name != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    TaskRepository(ctx, WorkspaceManager(operatorRoot)).exportToUri(File(dir, name), uri)
                    withContext(Dispatchers.Main) { log = log + "Exported $name ✓" }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) { log = log + "Export failed: ${t.message?.take(140)}" }
                }
            }
        }
        exportName = null
    }

    suspend fun approveStep(step: PlanStep): Boolean {
        withContext(Dispatchers.Main) { currentStep = step.id }
        val d = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            pendingStep = step
            pendingGate = d
        }
        return d.await()
    }

    suspend fun execute(taskId: String, spec: TaskSpec, uris: List<Uri>?) {
        var stage = "starting"
        try {
            val lines = mutableListOf<String>()
            val srcs = mutableListOf<ChatSource>()
            val wm = WorkspaceManager(operatorRoot)
            stage = "opening workspace"
            val ws = wm.create(taskId)
            val imported = if (uris != null) {
                stage = "importing ${uris.size} file(s)"
                TaskRepository(ctx, wm).importUris(ws, uris)
            } else {
                stage = "reusing imported files"
                wm.listInputs(ws)
            }
            lines += "Inputs (${imported.size}): " + imported.joinToString { "${it.name} (${it.length()}b)" }
            val budget = settings.budget()
            stage = "configuring model (${settings.providerId})"
            val router = try {
                settings.router(SecureKeyStore())
            } catch (t: Throwable) {
                lines += "Model config issue (${t.message?.take(100)}); using local stub."
                ModelRouter()
            }
            lines += "Model: ${settings.modelStatus(SecureKeyStore())}"
            val rt = OperatorRuntime(operatorRoot, resources = ResourceManager(budget), models = router)
            val p = Planner().plan(spec.copy(budget = budget))
            withContext(Dispatchers.Main) { plan = p }
            lines += "Skills needed: ${p.requiredSkills.ifEmpty { listOf("none") }.joinToString()}"
            // Real OCR: screenshots are actually read (ML Kit on-device).
            var ocrTexts: Map<String, String> = emptyMap()
            val imagesEarly = imported.filter {
                it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
            }
            if ("ocr_image" in p.requiredTools && imagesEarly.isNotEmpty()) {
                stage = "reading text from images"
                val raw = OcrReader.readAll(imagesEarly)
                // Redact PII before anything leaves the device (L04).
                ocrTexts = raw.mapValues { Redactor.mask(it.value) }
                val redacted = raw.any { Redactor.looksSensitive(it.value) }
                val chars = ocrTexts.values.sumOf { it.length }
                lines += "OCR: ${ocrTexts.size} image(s), $chars chars extracted" +
                    if (redacted) " (PII masked before any upload)" else ""
            }
            // Real downloads: URLs in the instruction are fetched (capped) into the workspace.
            if ("download_to_workspace" in p.requiredTools || "web_search" in p.requiredTools) {
                val urls = Regex("https?://[^\\s\"']+").findAll(spec.instruction).map { it.value }.toList()
                if (urls.isEmpty()) {
                    if ("web_search" in p.requiredTools && budget.network != NetworkPolicy.OFFLINE_ONLY) {
                        stage = "searching the web (keyless, cached 24h)"
                        try {
                            val ans = DdgSearch.askCached(spec.instruction.take(300), ws.cache)
                            lines += "Web answer: ${ans.abstract.take(400)}"
                            ans.sources.forEach { srcs += it }
                        } catch (t: Throwable) {
                            lines += "Web search failed (${t.message?.take(100)}); answer uses on-device knowledge."
                        }
                    } else {
                        lines += "No URLs in instruction — nothing to download. Add links to fetch."
                    }
                } else if (budget.network == NetworkPolicy.OFFLINE_ONLY) {
                    lines += "Blocked: offline mode refuses network fetch."
                } else {
                    stage = "downloading ${urls.size} file(s)"
                    urls.forEach { u ->
                        try {
                            val cap = if (u.lowercase().endsWith(".mp4") || u.lowercase().endsWith(".zip")) 100 * 1024 * 1024 else 10 * 1024 * 1024
                            val bytes = WebFetch.get(u, maxBytes = cap)
                            val name = u.substringAfterLast('/').take(60).ifBlank { "download-${System.currentTimeMillis()}" }
                            File(ws.input, "net-$name").writeBytes(bytes)
                            lines += "Downloaded $name (${bytes.size} bytes)"
                            try {
                                val page = WebFetch.extractText(bytes)
                                if (page.title.isNotBlank() || page.text.isNotBlank()) {
                                    srcs += ChatSource(page.title.ifBlank { name }, u, page.text.take(300))
                                    // Reader view (I02): clean text saved for offline reading.
                                    if (page.text.length > 200) {
                                        File(ws.output, "reader-$name.txt").writeText("${page.title}\n\n${page.text.take(15000)}")
                                        lines += "Reader view saved for $name"
                                    }
                                } else {
                                    srcs += ChatSource(name, u, "${bytes.size} bytes saved")
                                }
                            } catch (_: Exception) {
                                srcs += ChatSource(name, u, "${bytes.size} bytes saved")
                            }
                        } catch (t: Throwable) {
                            lines += "Download failed for $u: ${t.message?.take(120)}"
                        }
                    }
                }
            }
            stage = "running ${p.steps.size} steps"
            val res = rt.run(TaskSpec(taskId, spec.instruction, spec.inputNames, budget), approve = ::approveStep)
            withContext(Dispatchers.Main) { currentStep = null }
            lines += "Task ${res.state}: ${res.summary}"
            val wantVideo = "video" in spec.instruction.lowercase() || "mp4" in spec.instruction.lowercase()
            val wantPres = listOf("presentation", "pptx", "slide").any { it in spec.instruction.lowercase() }
            val images = imported.filter {
                it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
            }
            if (wantVideo) {
                stage = "rendering video"
                // Battery-aware deferral (K04): heavy renders pause under 20% instead of dying mid-encode.
                val bm = ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
                val pct = (bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY))
                if (pct in 1..19) {
                    lines += "Battery ${pct}% — render deferred. Plug in, then Resume."
                    tasks.transition(taskId, TaskState.PAUSED)
                    withContext(Dispatchers.Main) {
                        log = lines
                        lastTaskId = taskId
                        refreshIncomplete()
                    }
                    Notifications.notify(ctx, taskId.hashCode(), "Render deferred", "Battery ${pct}% — plug in and Resume.")
                    return
                }
                if (images.isNotEmpty()) {
                    val secs = Regex("(\\d{1,3})\\s*-?\\s*second").find(spec.instruction.lowercase())
                        ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(5, 180) ?: 30
                    val low = spec.instruction.lowercase()
                    // Compress presets: smaller bitrate + resolution, still playable.
                    val (w, h, br) = when {
                        "tiny" in low || "small" in low -> Triple(640, 360, 1_000_000)
                        "compress" in low || "compact" in low -> Triple(960, 540, 2_000_000)
                        else -> Triple(1280, 720, 4_000_000)
                    }
                    val mp4 = File(ws.output, "final_video.mp4")
                    val srtInputs = imported.filter { it.extension.lowercase() == "srt" }
                    val cues = srtInputs.flatMap {
                        runCatching { SrtParser.parse(it) }.getOrDefault(emptyList())
                    }
                    if (srtInputs.isNotEmpty()) {
                        lines += "Subtitles: ${cues.size} cue(s) from ${srtInputs.map { it.name }} (burned per time slice)"
                    }
                    val enc = SlideshowEncoder.encode(images, mp4, width = w, height = h, totalSeconds = secs, bitrate = br, cues = cues)
                    val probe = AndroidMediaRenderer.probe(mp4)
                    lines += "Rendered final_video.mp4: ${mp4.length()} bytes, " +
                        "${enc.frames} frames, probe=${probe?.mime ?: "unreadable"}"
                    if ("music" in spec.instruction.lowercase() || "audio" in spec.instruction.lowercase()) {
                        val audios = imported.filter {
                            it.extension.lowercase() in setOf("mp3", "m4a", "wav", "ogg", "aac", "flac")
                        }
                        if (audios.isEmpty()) {
                            lines += "Music requested but no audio file picked — add one (not faked)."
                        } else {
                            stage = "adding music"
                            val withMusic = File(ws.output, "final_video_music.mp4")
                            SlideshowEncoder.addAudio(mp4, audios.first(), withMusic)
                            mp4.delete()
                            withMusic.renameTo(mp4)
                            lines += "Music added from ${audios.first().name} (${mp4.length()} bytes)"
                        }
                    }
                } else {
                    lines += "Blocked: video needs an image; got ${imported.map { it.extension }}. Nothing faked."
                }
            }
            if (wantPres) {
                stage = "rendering presentation"
                val pptx = File(ws.output, "presentation.pptx")
                if (!pptx.exists()) {
                    val texts = (ocrTexts.values.filter { it.isNotBlank() }.ifEmpty { imported.map { it.name } }) + spec.instruction
                    val slides = DocumentPipeline.outlineFromTexts("Operator Report", texts)
                    AndroidPdfRenderer.renderSlides(
                        "Operator Report", slides, File(ws.output, "presentation.pdf"),
                    )
                    lines += "PPTX unavailable on-device (no java.awt); rendered real PDF + Markdown instead."
                } else {
                    lines += "Rendered presentation.pptx (${pptx.length()} bytes)."
                }
            }
            if ("torch" in spec.instruction.lowercase() || "flashlight" in spec.instruction.lowercase()) {
                stage = "device action"
                val on = "off" !in spec.instruction.lowercase()
                val r = DeviceActions.setTorch(ctx, on)
                lines += "Torch: $r"
                if (r.startsWith("needs")) lines += "Grant Camera in system Settings, then run again."
            }
            val lowMsg = spec.instruction.lowercase()
            val phone = Regex("\\+?\\d[\\d\\s-]{7,}\\d").find(spec.instruction)?.value
            if (("message" in lowMsg || "sms" in lowMsg || "text " in lowMsg) && phone != null) {
                stage = "message draft"
                withContext(Dispatchers.Main) {
                    OperatorIntents.smsDraft(ctx, phone.replace(Regex("[\\s-]"), ""), spec.instruction.take(300))
                }
                lines += "Message draft opened for $phone (you send — never auto-sent)."
            }
            if (("map of" in lowMsg || "directions to" in lowMsg || "where is" in lowMsg)) {
                stage = "map"
                val q = spec.instruction.substringAfter("map of", spec.instruction.substringAfter("directions to", spec.instruction.substringAfter("where is", spec.instruction))).take(120)
                withContext(Dispatchers.Main) { OperatorIntents.mapSearch(ctx, q) }
                lines += "Map opened for: ${q.take(80)}"
            }
            if (("alarm" in lowMsg || "wake me" in lowMsg) && "clock" !in lowMsg) {
                stage = "alarm"
                withContext(Dispatchers.Main) { OperatorIntents.setAlarm(ctx, spec.instruction.take(120)) }
                lines += "Alarm screen opened (you confirm the time)."
            }
            if (("volume" in lowMsg || "mute" in lowMsg) && "video" !in lowMsg) {
                stage = "volume"
                lines += withContext(Dispatchers.Main) { OperatorIntents.adjustVolume(ctx, lowMsg) }
            }
            if ("brightness" in lowMsg) {
                stage = "display"
                withContext(Dispatchers.Main) { OperatorIntents.openDisplaySettings(ctx) }
                lines += "Display settings opened (brightness needs a system setting — change it there)."
            }
            val videoInputs = imported.filter {
                it.extension.lowercase() in setOf("mp4", "mov", "mkv", "3gp")
            }
            if (("trim" in lowMsg || ("cut" in lowMsg && "shortcut" !in lowMsg)) && videoInputs.isNotEmpty()) {
                stage = "trimming video"
                try {
                    val fromStart = Regex("first (\\d{1,3})\\s*sec").find(lowMsg)?.groupValues?.get(1)?.toLongOrNull()
                    val skipFirst = Regex("(?:trim|skip|cut)(?: the)? (?:first|beginning)(?: (\\d{1,3}))?").find(lowMsg)?.groupValues?.get(1)?.toLongOrNull()
                    val startUs = ((skipFirst ?: 0L) * 1_000_000L)
                    val endUs = if (fromStart != null) fromStart * 1_000_000L else Long.MAX_VALUE
                    val trimmed = VideoTrim.trim(videoInputs.first(), File(ws.output, "trimmed.mp4"), startUs, endUs)
                    lines += "Trimmed: ${trimmed.length()} bytes (kept ${videoInputs.first().name} → trimmed.mp4)"
                } catch (t: Throwable) {
                    lines += "Trim failed: ${t.message?.take(120)}"
                }
            }
            if (("frame" in spec.instruction.lowercase() || "thumbnail" in spec.instruction.lowercase()) && videoInputs.isNotEmpty()) {
                stage = "extracting frames"
                try {
                    val frames = FrameGrabber.extract(videoInputs.first(), File(ws.output, "frames"))
                    lines += "Frames: ${frames.size} saved (${frames.sumOf { it.length() }} bytes)"
                } catch (t: Throwable) {
                    lines += "Frame extract failed: ${t.message?.take(120)}"
                }
            }
            if (("watermark" in lowMsg || "copyright" in lowMsg) && images.isNotEmpty()) {
                stage = "watermarking"
                val mark = Regex("(?:watermark|copyright)\\s+(?:as\\s+|[:=]\\s*)?(.{1,48})").find(lowMsg)?.groupValues?.getOrNull(1)?.trim().takeIf { !it.isNullOrBlank() } ?: "MikiMouse"
                images.forEach { img ->
                    try {
                        ImageTools.watermark(img, mark, File(ws.output, "${img.nameWithoutExtension}-wm.jpg"))
                        lines += "Watermarked ${img.name}"
                    } catch (t: Throwable) {
                        lines += "Watermark failed for ${img.name}: ${t.message?.take(100)}"
                    }
                }
            }
            val pdfInputs = imported.filter { it.extension.lowercase() == "pdf" }
            if ("csv" in lowMsg || "spreadsheet" in lowMsg || "table" in lowMsg || "sheet" in lowMsg) {
                stage = "building spreadsheet"
                try {
                    val rows = mutableListOf<List<String>>()
                    File(ws.input, "").listFiles()
                        ?.filter { it.name.startsWith("net-") && it.length() < 2 * 1024 * 1024 }
                        ?.forEach { f ->
                            try {
                                WebFetch.extractTables(f.readBytes()).firstOrNull()?.let { rows += it }
                            } catch (_: Exception) {}
                        }
                    if (rows.isEmpty() && ocrTexts.isNotEmpty()) {
                        rows += listOf(listOf("source", "text"))
                        ocrTexts.forEach { (n, t) -> t.split("\n").filter { it.isNotBlank() }.forEach { rows += listOf(n, it.take(200)) } }
                    }
                    if (rows.isEmpty()) {
                        rows += listOf(listOf("item"))
                        spec.instruction.split("\n").filter { it.isNotBlank() }.forEach { rows += listOf(it.take(200)) }
                    }
                    val csv = DocumentPipeline.renderCsv(rows.take(2000), File(ws.output, "data.csv"))
                    lines += "Spreadsheet: ${rows.size} rows → data.csv (${csv.length()} bytes)"
                    if ("xlsx" in lowMsg || "excel" in lowMsg) {
                        try {
                            val x = DocumentPipeline.renderXlsx(rows.take(5000), File(ws.output, "data.xlsx"))
                            lines += "Excel: data.xlsx (${x.length()} bytes)"
                        } catch (t: Throwable) {
                            lines += "XLSX unavailable here (${t.message?.take(100)}); CSV kept."
                        }
                    }
                } catch (t: Throwable) {
                    lines += "Spreadsheet failed: ${t.message?.take(120)}"
                }
            }
            if (("rotat" in lowMsg || "flip" in lowMsg || "convert" in lowMsg || "resize" in lowMsg) && images.isNotEmpty()) {
                stage = "transforming images"
                val deg = Regex("(\\d{2,3})\\s*deg").find(lowMsg)?.groupValues?.get(1)?.toFloatOrNull()
                    ?: if ("90" in lowMsg) 90f else 0f
                val fmt = if ("png" in lowMsg) "png" else if ("webp" in lowMsg) "webp" else "jpg"
                images.forEach { img ->
                    try {
                        val outName = img.nameWithoutExtension + "-edit.$fmt"
                        ImageTools.transform(img, File(ws.output, outName), rotateDeg = deg, flip = "flip" in lowMsg, format = fmt)
                        lines += "Transformed ${img.name} → $outName"
                    } catch (t: Throwable) {
                        lines += "Transform failed for ${img.name}: ${t.message?.take(100)}"
                    }
                }
            }
            if ("merge" in spec.instruction.lowercase() && pdfInputs.size >= 2) {
                stage = "merging PDFs"
                try {
                    val merged = AndroidPdfRenderer.merge(pdfInputs, File(ws.output, "merged.pdf"))
                    lines += "Merged PDF: ${merged.length()} bytes"
                } catch (t: Throwable) {
                    lines += "PDF merge failed: ${t.message?.take(120)}"
                }
            }
            if ("docx" in lowMsg && ocrTexts.isNotEmpty()) {
                stage = "writing DOCX from OCR text"
                try {
                    val paras = ocrTexts.flatMap { (name, text) -> listOf("— $name —") + text.split("\n").filter { it.isNotBlank() } }
                    DocumentPipeline.renderDocx("OCR Report", paras, File(ws.output, "report.docx"))
                    lines += "DOCX written from OCR text (${paras.size} paragraphs)"
                } catch (t: Throwable) {
                    lines += "DOCX unavailable on-device (${t.message?.take(100)}); Markdown kept instead."
                }
            }
            withContext(Dispatchers.Main) {
                log = lines
                lastOutputDir = ws.output
                lastTaskId = taskId
                refreshIncomplete()
            }
            // Conversational answer with sources + follow-ups (chat history persists).
            stage = "answering"
            val done = ws.output.listFiles()?.maxByOrNull { it.length() }
            val mailTo = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(spec.instruction)?.value
            if (("email" in lowMsg || "mail " in lowMsg) && mailTo != null && done != null) {
                withContext(Dispatchers.Main) { OperatorIntents.emailFile(ctx, mailTo, "MikiMouse: ${done.name}", done) }
                lines += "Email draft to $mailTo with ${done.name} (you send)."
            }
            val answerText = buildString {
                val outs = ws.output.listFiles()?.joinToString { "${it.name} (${it.length()}b)" } ?: "no files"
                if (srcs.isNotEmpty()) {
                    appendLine("Collected ${srcs.size} source(s):")
                    srcs.take(5).forEach { appendLine("• ${it.title}") }
                    appendLine()
                }
                if ("research" in spec.instruction.lowercase() && budget.network != NetworkPolicy.OFFLINE_ONLY) {
                    try {
                        val draft = router.chat(ChatRequest(user = "Summarize in 5 lines: ${spec.instruction}", maxTokens = 300))
                        appendLine(draft.take(1200))
                    } catch (t: Throwable) {
                        appendLine("Summary unavailable (${t.message?.take(100)}); sources above hold the content.")
                    }
                }
                append("Finished ${res.state}: $outs")
            }
            val followUps = FollowUps.suggest(spec.instruction, answerText)
            chatStore.append(ChatMessage("user", spec.instruction))
            chatStore.append(ChatMessage("assistant", answerText, srcs.toList(), followUps))
            // Per-task conversation file (F05): full Q/A + sources live with the task.
            val taskChat = ConversationStore(File(operatorRoot, "chats/$taskId.json"))
            taskChat.append(ChatMessage("user", spec.instruction))
            taskChat.append(ChatMessage("assistant", answerText, srcs.toList(), followUps))
            withContext(Dispatchers.Main) { log = lines; refreshIncomplete() }
            stage = "notifying"
            try {
                val vib = ctx.getSystemService(android.os.Vibrator::class.java)
                vib?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {}
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
                log = listOf("failed during $stage: ${t.message ?: t.javaClass.simpleName}")
                refreshIncomplete()
            }
        }
    }

    fun startFresh() {
        running = true
        currentStep = null
        scope.launch(Dispatchers.IO) {
            val id = "task-${System.currentTimeMillis()}"
            try {
                execute(id, TaskSpec(id, instruction), picked)
            } finally {
                withContext(Dispatchers.Main) {
                    running = false
                    // Batch queue (C16): run queued jobs sequentially, one at a time.
                    val next = queue.firstOrNull()
                    if (next != null) {
                        queue = queue.drop(1)
                        instruction = next.first
                        picked = next.second
                        previewPlan()
                        startFresh()
                    }
                }
            }
        }
    }

    fun resume(t: PersistedTask) {
        running = true
        scope.launch(Dispatchers.IO) {
            try {
                execute(t.spec.taskId, t.spec, null)
            } finally {
                withContext(Dispatchers.Main) { running = false }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        GlassBackground()
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MOBILE OPERATOR", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "RAM target ${settings.ramTargetMB} MB · quota ${settings.storageQuotaMB} MB · " +
                                "${settings.cpu} · net ${settings.network}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Model: ${settings.modelStatus(SecureKeyStore())}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = instruction, onValueChange = { instruction = it },
                            label = { Text("What do you want me to do?") },
                            modifier = Modifier.fillMaxWidth(), minLines = 2,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pickFiles.launch(arrayOf("*/*")) }) { Text("Pick files (${picked.size})") }
                            TextButton(onClick = { pickContact.launch(null) }) { Text("👤") }
                            Button(onClick = {
                                try {
                                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell MikiMouse what to do")
                                    }
                                    micListen.launch(i)
                                } catch (_: Exception) {
                                    log = log + "No speech recognizer on this device"
                                }
                            }) { Text("🎤") }
                            Button(enabled = !running && picked.isNotEmpty(), onClick = ::startFresh) {
                                Text(if (running) "Working…" else "Run")
                            }
                            TextButton(enabled = picked.isNotEmpty(), onClick = {
                                queue = queue + (instruction to picked)
                            }) { Text("Queue (${queue.size})") }
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Conversation", style = MaterialTheme.typography.titleMedium)
                            if (thread.isNotEmpty()) {
                                TextButton(onClick = {
                                    chatStore.clear()
                                    thread = emptyList()
                                }) { Text("Clear") }
                            }
                        }
                        if (thread.isEmpty()) {
                            Text(
                                "Ask anything — answers arrive with sources, follow-ups, and your files as real outputs.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            listOf(
                                "Create a 30-second video from these files",
                                "Research this topic and file a report",
                                "Compress this video smaller",
                            ).forEach { ex ->
                                TextButton(onClick = { instruction = ex; previewPlan() }) {
                                    Text("Try: $ex")
                                }
                            }
                        }
                        thread.takeLast(6).forEachIndexed { i, m ->
                            val isLast = i == thread.takeLast(6).lastIndex
                            if (m.role == "user") {
                                Text("You: ${m.text}", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                var shown by remember(m.ts) { mutableStateOf(if (isLast) 0 else m.text.length) }
                                if (isLast && shown < m.text.length) {
                                    LaunchedEffect(m.ts) {
                                        while (shown < m.text.length) {
                                            kotlinx.coroutines.delay(24)
                                            shown = (shown + 160).coerceAtMost(m.text.length)
                                        }
                                    }
                                }
                                Text(
                                    m.text.take(if (isLast) shown else m.text.length),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                m.sources.take(4).forEach { s ->
                                    Text(
                                        "↗ ${s.title.take(60)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                m.followUps.forEach { f ->
                                    TextButton(onClick = { instruction = f; previewPlan() }) {
                                        Text("↳ $f")
                                    }
                                }
                                if (isLast) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { speak(m.text) }) {
                                            Text(if (speaking) "🔊…" else "🔊 Listen")
                                        }
                                        if (speaking) TextButton(onClick = { stopSpeak() }) { Text("Stop") }
                                        TextButton(onClick = {
                                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            cm.setPrimaryClip(android.content.ClipData.newPlainText("answer", m.text))
                                        }) { Text("Copy") }
                                    }
                                }
                            }
                        }
                        if (lastOutputDir?.listFiles()?.isNotEmpty() == true) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    val f = lastOutputDir!!.listFiles()!!.maxByOrNull { it.length() }!!
                                    exportName = f.name
                                    exportDoc.launch(f.name)
                                }) { Text("Export latest") }
                                TextButton(onClick = {
                                    instruction = "Make a presentation from these files"
                                    previewPlan()
                                }) { Text("Present this") }
                            }
                        }
                    }
                }
            }
            if (incomplete.isNotEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Unfinished work (${incomplete.size})", style = MaterialTheme.typography.titleMedium)
                            incomplete.forEach { t ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "${t.spec.taskId}\n${t.state} · ${t.spec.instruction.take(60)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(enabled = !running, onClick = { resume(t) }) { Text("Resume") }
                                    TextButton(onClick = { exportDebug(t.spec.taskId) }) { Text("Logs") }
                                    TextButton(onClick = {
                                        File(operatorRoot, "tasks/${t.spec.taskId}").deleteRecursively()
                                        refreshIncomplete()
                                    }) { Text("Discard") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Task timeline", style = MaterialTheme.typography.titleMedium)
                        plan?.steps?.forEach { s ->
                            val mark = if (s.id == currentStep) "◉" else "○"
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
                        if (crashGuard) {
                            Text(
                                "⚠ 3 consecutive failures — export a debug bundle and check logs before retrying.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (lastTaskId != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { exportDebug(lastTaskId!!) }) { Text("Export debug bundle") }
                                TextButton(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val lines = runCatching {
                                            File(operatorRoot, "tasks/$lastTaskId/logs/audit.log").readLines().takeLast(40)
                                        }.getOrDefault(listOf("(no audit yet)"))
                                        withContext(Dispatchers.Main) { log = lines }
                                    }
                                }) { Text("View audit") }
                                TextButton(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val dir = File(operatorRoot, "tasks/$lastTaskId")
                                            val bundle = File(dir, "cache/project-$lastTaskId.zip")
                                            ArchiveTool.zip(dir.listFiles()?.toList().orEmpty().filter { it.exists() }, bundle)
                                            withContext(Dispatchers.Main) { OperatorIntents.shareFile(ctx, bundle) }
                                        } catch (t: Throwable) {
                                            withContext(Dispatchers.Main) { log = log + "Bundle failed: ${t.message?.take(120)}" }
                                        }
                                    }
                                }) { Text("Share project bundle") }
                            }
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
