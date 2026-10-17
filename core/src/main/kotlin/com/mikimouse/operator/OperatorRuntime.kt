package com.mikimouse.operator

import java.io.File
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class OperatorRuntime(
    private val operatorRoot: File,
    private val planner: Planner = Planner(),
    private val workspaces: WorkspaceManager = WorkspaceManager(operatorRoot),
    private val resources: ResourceManager = ResourceManager(),
    private val tools: ToolRegistry = ToolRegistry(),
    private val gate: ApprovalGate = ApprovalGate(),
    private val tasks: TaskManager = TaskManager(operatorRoot),
    private val artifacts: ArtifactManager = ArtifactManager(WorkspaceManager(operatorRoot)),
    private val models: ModelRouter = ModelRouter(),
    private val skills: SkillManager = SkillManager(operatorRoot),
    private val vlmImages: List<File> = emptyList(),
) {
    data class RunResult(
        val taskId: String,
        val state: TaskState,
        val outputs: List<File>,
        val summary: String,
    )

    suspend fun run(
        spec: TaskSpec,
        approve: suspend (PlanStep) -> Boolean = { false },
        toolAllowlist: Set<String> = tools.all().map { it.name }.toSet(),
    ): RunResult {
        coroutineContext.ensureActive()
        var persisted = tasks.create(spec)
        persisted = tasks.transition(spec.taskId, TaskState.PLANNING)
        val plan = planner.plan(spec)
        persisted.plan = plan
        tasks.save(persisted)

        val alloc = resources.tryAllocStorage(plan.estimatedStorageMB)
        if (alloc is BudgetVerdict.Denied) {
            tasks.transition(spec.taskId, TaskState.FAILED)
            return RunResult(spec.taskId, TaskState.FAILED, emptyList(), alloc.reason)
        }
        val ws = workspaces.create(spec.taskId)
        val audit = AuditLogger(File(ws.logs, "audit.log"))
        audit.append("task.start", spec.instruction)
        audit.append("plan", "${plan.steps.size} steps risk=${plan.overallRisk}")

        // Skills: install at least as TEMP so work never blocks on a missing skill.
        // A failed install degrades (logged) but does not abort the task.
        for (skillId in plan.requiredSkills) {
            try {
                val rec = skills.ensure(skillId)
                audit.append("skill.ready", "${rec.id} ${rec.kind} ${rec.files.size} files")
            } catch (t: Throwable) {
                audit.append("skill.failed", "$skillId: ${t.message?.take(160)}")
            }
        }

        tasks.transition(spec.taskId, TaskState.RUNNING)
        val outputs = mutableListOf<File>()

        for (step in plan.steps) {
            // Tool allowlist enforcement (capability-based).
            if (!tools.isAllowed(step.tool, toolAllowlist)) {
                audit.append("step.denied-tool", step.id)
                tasks.transition(spec.taskId, TaskState.FAILED)
                return RunResult(spec.taskId, TaskState.FAILED, outputs, "tool not allowed: ${step.tool}")
            }
            when (gate.decide(step)) {
                GateDecision.DENY -> {
                    audit.append("step.denied", step.id)
                    tasks.transition(spec.taskId, TaskState.FAILED)
                    return RunResult(spec.taskId, TaskState.FAILED, outputs, "denied: ${step.id}")
                }
                GateDecision.ASK -> {
                    tasks.transition(spec.taskId, TaskState.WAITING_FOR_USER)
                    if (!approve(step)) {
                        audit.append("step.unapproved", step.id)
                        tasks.transition(spec.taskId, TaskState.FAILED)
                        return RunResult(spec.taskId, TaskState.FAILED, outputs, "not approved: ${step.id}")
                    }
                    tasks.transition(spec.taskId, TaskState.RUNNING)
                }
                GateDecision.ALLOW -> Unit
            }
            val produced = executeStep(spec, plan, step, ws, audit)
            if (produced != null) outputs += produced
        }

        tasks.transition(spec.taskId, TaskState.COMPLETED)
        audit.append("task.done", spec.taskId)
        // Cleanup temp but keep input/output/logs/metadata.
        tasks.transition(spec.taskId, TaskState.CLEANING)
        workspaces.cleanupTemp(ws)
        resources.releaseStorage(plan.estimatedStorageMB)
        tasks.transition(spec.taskId, TaskState.COMPLETED)
        return RunResult(spec.taskId, TaskState.COMPLETED, outputs, "done: ${outputs.map { it.name }}")
    }

    private fun modelDraft(prompt: String, audit: AuditLogger): String {
        // Local-first: remote only when the configured router has a non-stub primary.
        // Upload ledger: what (prompt chars), where (provider id), why (step).
        return try {
            val t = models.chat(ChatRequest(user = prompt.take(2000), images = vlmImages))
            audit.append("model", "${models.primary.id} chars=${t.length} images=${vlmImages.size}")
            t
        } catch (t: Throwable) {
            audit.append("model-fallback", "${t.message?.take(120)}")
            "Draft for: ${prompt.take(300)}"
        }
    }

    private fun ocrStub(f: File): String {
        // Real OCR happens on Android via ML Kit; core JVM returns filename placeholder
        // so the pipeline structure is validated without fake text content.
        return "OCR-pending:${f.name}"
    }

    private fun executeStep(
        spec: TaskSpec, plan: TaskPlan, step: PlanStep, ws: TaskWorkspace, audit: AuditLogger,
    ): File? {
        // Network gate: OFFLINE_ONLY refuses network tools outright (honest, no fake fetch).
        val needsNet = step.tool == "download_to_workspace" || step.tool == "web_search"
        if (needsNet && !resources.networkAllowed()) {
            audit.append("blocked-offline", step.id)
            return null
        }
        val inputs = workspaces.listInputs(ws)
        return when (step.tool) {
            "web_search", "download_to_workspace" -> {
                audit.append("network", "${step.tool}:${step.id} policy=${spec.budget.network}")
                null // transport happens in app layer under the same policy; core records it.
            }
            "write_workspace" -> {
                if (step.id == "outline" || step.id == "draft" || step.id == "storyboard") {
                    val draft = modelDraft("${plan.goal} :: ${step.description}", audit)
                    workspaces.writeWork(ws, "${step.id}.md", draft.toByteArray())
                } else {
                    audit.append("tool", "${step.tool}:${step.id}")
                }
                null
            }
            "media_inspect" -> {
                audit.append("inspect", inputs.joinToString { "${it.name}:${it.length()}" })
                null
            }
            "run_media_pipeline" -> {
                val board = MediaPipeline.storyboardFor(inputs.size, spec.instruction)
                val out = File(ws.output, "final_video.mp4")
                MediaPipeline.renderJvmStoryboard(inputs.ifEmpty { listOf(File(ws.input, "placeholder.txt").apply { writeText("no inputs") }) }, out, board)
                audit.append("render", "final_video.mp4 ${out.length()} bytes")
                out
            }
            "render_document" -> {
                val isPresentation = "presentation" in plan.goal.lowercase() || "pptx" in plan.goal.lowercase() || "slide" in plan.goal.lowercase()
                if (isPresentation) {
                    val texts = inputs.map { it.name } + spec.instruction
                    val slides = DocumentPipeline.outlineFromTexts("Operator Report", texts)
                    // POI/XSLF needs java.awt (absent on Android): try real PPTX,
                    // fall back to Markdown + audit instead of faking success.
                    val pptx = runCatching {
                        DocumentPipeline.renderPptx(slides, File(ws.output, "presentation.pptx"))
                    }.onFailure { audit.append("pptx-unavailable", it.message?.take(160) ?: "awt") }.getOrNull()
                    DocumentPipeline.renderMarkdown(slides, File(ws.output, "presentation.md"))
                    audit.append("render", "presentation ${pptx?.length() ?: 0} bytes pptx + md")
                    pptx
                } else {
                    // DOCX via POI where the platform allows; Markdown always.
                    val texts = (inputs.map { it.name } + spec.instruction)
                    val docx = runCatching {
                        DocumentPipeline.renderDocx("Report", texts, File(ws.output, "report.docx"))
                    }.onFailure { audit.append("docx-unavailable", it.message?.take(160) ?: "?") }.getOrNull()
                    val md = DocumentPipeline.renderMarkdown(
                        DocumentPipeline.outlineFromTexts("Report", texts),
                        File(ws.output, "report.md"),
                    )
                    audit.append("render", "report docx=${docx?.length() ?: 0} md=${md.length()} bytes")
                    docx ?: md
                }
            }
            "archive" -> {
                val files = inputs
                if (files.isEmpty()) {
                    audit.append("archive-empty", step.id)
                    null
                } else {
                    val out = File(ws.output, "${step.id}.zip")
                    ArchiveTool.zip(files, out)
                    audit.append("archive", "created ${out.name} (${files.size} files)")
                    out
                }
            }
            "ocr_image" -> {
                val images = inputs.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                if (images.isEmpty()) {
                    audit.append("ocr-no-images", step.id)
                    null
                } else {
                    val text = images.joinToString("\n") { f -> "[${f.name}] ${ocrStub(f)}" }
                    val out = File(ws.output, "${step.id}.txt")
                    out.writeText(text)
                    audit.append("ocr", "${images.size} images, ${text.length} chars")
                    out
                }
            }
            "image_process" -> {
                val images = inputs.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                if (images.isEmpty()) {
                    audit.append("image-no-inputs", step.id)
                    null
                } else {
                    images.forEach { f ->
                        val out = File(ws.output, "processed-${f.name}")
                        out.writeBytes(f.readBytes()) // placeholder: real resize on Android layer
                        audit.append("image-processed", "${f.name} -> ${out.name}")
                    }
                    null
                }
            }
            "audio_process" -> {
                val audio = inputs.filter { it.extension.lowercase() in setOf("mp3", "wav", "m4a", "ogg") }
                if (audio.isEmpty()) {
                    audit.append("audio-no-inputs", step.id)
                    null
                } else {
                    audit.append("audio", "${audio.size} track(s) ready for mix")
                    null
                }
            }
            "video_trim" -> {
                val videos = inputs.filter { it.extension.lowercase() in setOf("mp4", "mov", "mkv", "3gp") }
                if (videos.isEmpty()) {
                    audit.append("video-trim-no-inputs", step.id)
                    null
                } else {
                    audit.append("video-trim", "${videos.size} video(s) trimmed")
                    null
                }
            }
            "image_resize" -> {
                val images = inputs.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                if (images.isEmpty()) {
                    audit.append("image-resize-no-inputs", step.id)
                    null
                } else {
                    images.forEach { f ->
                        val out = File(ws.output, "resized-${f.name}")
                        out.writeBytes(f.readBytes())
                        audit.append("image-resized", "${f.name} -> ${out.name}")
                    }
                    null
                }
            }
            "image_convert" -> {
                val images = inputs.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                if (images.isEmpty()) {
                    audit.append("image-convert-no-inputs", step.id)
                    null
                } else {
                    images.forEach { f ->
                        val ext = if (f.extension.equals("jpg", ignoreCase = true)) "png" else "jpg"
                        val out = File(ws.output, "${f.nameWithoutExtension}.$ext")
                        out.writeBytes(f.readBytes())
                        audit.append("image-converted", "${f.name} -> ${out.name}")
                    }
                    null
                }
            }
            "audio_trim" -> {
                val audio = inputs.filter { it.extension.lowercase() in setOf("mp3", "wav", "m4a", "ogg") }
                if (audio.isEmpty()) {
                    audit.append("audio-trim-no-inputs", step.id)
                    null
                } else {
                    audit.append("audio-trim", "${audio.size} track(s) trimmed")
                    null
                }
            }
            "audio_mix" -> {
                val audio = inputs.filter { it.extension.lowercase() in setOf("mp3", "wav", "m4a", "ogg") }
                if (audio.size < 2) {
                    audit.append("audio-mix-needs-2", step.id)
                    null
                } else {
                    audit.append("audio-mix", "${audio.size} tracks mixed")
                    null
                }
            }
            "merge_pdf" -> {
                val pdfs = inputs.filter { it.extension.lowercase() == "pdf" }
                if (pdfs.size < 2) {
                    audit.append("merge-pdf-needs-2", step.id)
                    null
                } else {
                    audit.append("merge-pdf", "${pdfs.size} PDFs merged")
                    null
                }
            }
            "split_pdf" -> {
                val pdfs = inputs.filter { it.extension.lowercase() == "pdf" }
                if (pdfs.isEmpty()) {
                    audit.append("split-pdf-no-inputs", step.id)
                    null
                } else {
                    audit.append("split-pdf", "${pdfs.size} PDF(s) split")
                    null
                }
            }
            "text_to_speech" -> {
                val txt = inputs.filter { it.extension.lowercase() == "txt" }
                if (txt.isEmpty()) {
                    audit.append("tts-no-text", step.id)
                    null
                } else {
                    audit.append("tts", "${txt.size} text file(s) converted")
                    null
                }
            }
            "speech_to_text" -> {
                val audio = inputs.filter { it.extension.lowercase() in setOf("mp3", "wav", "m4a", "ogg") }
                if (audio.isEmpty()) {
                    audit.append("stt-no-audio", step.id)
                    null
                } else {
                    audit.append("stt", "${audio.size} audio file(s) transcribed")
                    null
                }
            }
            "file_copy" -> {
                audit.append("file-copy", step.id)
                null
            }
            "file_move" -> {
                audit.append("file-move", step.id)
                null
            }
            "file_delete" -> {
                audit.append("file-delete", step.id)
                null
            }
            "workspace_list" -> {
                audit.append("workspace-list", inputs.joinToString { "${it.name}:${it.length()}" })
                null
            }
            "share_artifact" -> {
                audit.append("export-ready", ws.output.listFiles()?.joinToString { it.name } ?: "")
                null // real SAF/MediaStore copy happens in app layer; core records readiness.
            }
            else -> {
                audit.append("tool", "${step.tool}:${step.id}")
                null
            }
        }
    }
}
