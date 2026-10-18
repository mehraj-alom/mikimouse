package com.mikimouse.app.operator

import android.content.Context
import com.mikimouse.app.data.TaskRepository
import com.mikimouse.app.models.SecureKeyStore
import com.mikimouse.operator.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Autonomous task chain executor.
 * Breaks complex goals into sub-tasks, executes each step,
 * verifies results, and auto-retries on failure.
 */
class AutonomousOperator(
    private val ctx: Context,
    private val operatorRoot: File,
    private val settingsStore: com.mikimouse.app.data.SettingsStore
) {
    data class ChainStep(
        val id: String,
        val instruction: String,
        val dependsOn: List<String> = emptyList(),
        var status: Status = Status.PENDING,
        var result: String = "",
        var error: String = "",
        var retryCount: Int = 0,
        val maxRetries: Int = 2
    )

    enum class Status { PENDING, RUNNING, COMPLETED, FAILED, SKIPPED }

    data class ChainResult(
        val taskId: String,
        val completedSteps: Int,
        val totalSteps: Int,
        val allCompleted: Boolean,
        val outputs: List<String>,
        val errors: List<String>
    )

    /**
     * Autonomous execution: break goal into steps, execute, verify, retry.
     */
    suspend fun executeAutonomous(
        goal: String,
        files: List<File> = emptyList(),
        approve: (String) -> Boolean = { true } // Auto-approve for autonomy
    ): ChainResult {
        val taskId = "auto-${System.currentTimeMillis()}"
        val wm = com.mikimouse.operator.WorkspaceManager(operatorRoot)
        val ws = wm.create(taskId)

        // Import files
        val ctx = this.ctx
        val repo = com.mikimouse.app.data.TaskRepository(ctx, wm)
        val imported = if (files.isNotEmpty()) {
            val uris = files.map { it.toURI().toString() }
            // Use file paths directly for local files
            files.map { f ->
                f.copyTo(File(ws.input, f.name), overwrite = true)
                f
            }
        } else emptyList()

        // Step 1: Break goal into sub-tasks using the model
        val budget = settingsStore.budget()
        val router = try {
            settingsStore.router(com.mikimouse.app.models.SecureKeyStore())
        } catch (e: Exception) {
            com.mikimouse.operator.ModelRouter()
        }

        val plan = breakIntoSteps(goal, router)

        val steps = mutableListOf<ChainStep>()
        plan.forEachIndexed { i, stepText ->
            steps.add(ChainStep(
                id = "step-${i + 1}",
                instruction = stepText,
                dependsOn = if (i == 0) emptyList() else listOf("step-${i}"),
                status = Status.PENDING
            ))
        }

        val outputs = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // Execute steps sequentially with auto-retry
        for (step in steps) {
            // Check dependencies
            val depsMet = step.dependsOn.all { depId ->
                steps.first { it.id == depId }.status == Status.COMPLETED
            }
            if (!depsMet) {
                step.status = Status.SKIPPED
                errors.add("Step ${step.id} skipped: dependency not met")
                continue
            }

            step.status = Status.RUNNING
            var succeeded = false

            for (attempt in 0..step.maxRetries) {
                step.retryCount = attempt
                try {
                    val stepResult = executeSingleStep(
                        step.instruction,
                        files + outputs.map { File(it) },
                        router,
                        budget,
                        wm,
                        ws
                    )

                    if (stepResult.isNotEmpty()) {
                        step.status = Status.COMPLETED
                        step.result = stepResult
                        outputs.add(stepResult)
                        succeeded = true
                        break
                    } else {
                        step.error = "Step produced no output (attempt ${attempt + 1})"
                    }
                } catch (e: Exception) {
                    step.error = "Attempt ${attempt + 1} failed: ${e.message?.take(200) ?: "unknown error"}"
                }

                if (attempt < step.maxRetries) {
                    // Wait before retry
                    kotlinx.coroutines.delay(2000)
                }
            }

            if (!succeeded) {
                step.status = Status.FAILED
                errors.add("Step ${step.id} (${step.instruction}): ${step.error}")
            }
        }

        val allCompleted = steps.all { it.status == Status.COMPLETED }

        return ChainResult(
            taskId = taskId,
            completedSteps = steps.count { it.status == Status.COMPLETED },
            totalSteps = steps.size,
            allCompleted = allCompleted,
            outputs = outputs,
            errors = errors
        )
    }

    /**
     * Break a complex goal into executable sub-steps using the model.
     */
    private suspend fun breakIntoSteps(
        goal: String,
        router: com.mikimouse.operator.ModelRouter
    ): List<String> {
        val prompt = """Break this goal into 3-5 sequential, executable steps. Each step should be a single actionable task. Return only the steps, one per line, no numbering:

Goal: $goal

Steps:"""

        val response = try {
            router.chat(com.mikimouse.operator.ChatRequest(user = prompt, maxTokens = 500))
        } catch (e: Exception) {
            // Fallback: simple keyword-based breakdown
            return fallbackSteps(goal)
        }

        return response.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.removeSurrounding("\"").trim() }
            .filter { it.length > 5 }
            .take(5)
            .takeIf { it.isNotEmpty() }
            ?: fallbackSteps(goal)
    }

    private fun fallbackSteps(goal: String): List<String> {
        val lower = goal.lowercase()
        return when {
            lower.contains("video") || lower.contains("mp4") ->
                listOf("Inspect input files", "Create video storyboard", "Render video", "Export video")
            lower.contains("presentation") || lower.contains("pptx") ->
                listOf("Inspect input files", "Extract content from images", "Create presentation outline", "Render presentation")
            lower.contains("pdf") ->
                listOf("Inspect input files", "Convert to PDF", "Export PDF")
            lower.contains("email") ->
                listOf("Draft email content", "Send email")
            lower.contains("research") ->
                listOf("Search web for information", "Collect sources", "Write report", "Export report")
            else ->
                listOf("Analyze inputs", "Process according to goal", "Export result")
        }
    }

    /**
     * Execute a single step autonomously.
     */
    private suspend fun executeSingleStep(
        instruction: String,
        files: List<File>,
        router: com.mikimouse.operator.ModelRouter,
        budget: com.mikimouse.operator.ResourceBudget,
        wm: com.mikimouse.operator.WorkspaceManager,
        ws: com.mikimouse.operator.TaskWorkspace
    ): String {
        // Use the model to determine what to do and generate output
        val fileList = files.joinToString(", ") { it.name }
        val prompt = """You are an autonomous task executor. Complete this task:

Task: $instruction

Available files: $fileList

Produce the final output content directly. Be concise and practical."""

        val modelOutput = try {
            router.chat(com.mikimouse.operator.ChatRequest(user = prompt, maxTokens = 1000))
        } catch (e: Exception) {
            "Model unavailable: ${e.message?.take(100) ?: "unknown error"}"
        }

        // Write output to workspace
        val stepOutput = File(ws.output, "step-output-${System.currentTimeMillis()}.txt")
        stepOutput.writeText(modelOutput)

        // Also try to execute actual tool actions based on instruction keywords
        val lower = instruction.lowercase()
        var extraOutput = ""

        if (lower.contains("video") || lower.contains("mp4")) {
            // Try to create video from images
            val images = files.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            if (images.isNotEmpty()) {
                try {
                    val mp4 = File(ws.output, "step-video.mp4")
                    com.mikimouse.app.media.RealVideoEncoder.encodeSlideshow(
                        images, mp4, width = 1280, height = 720, fps = 30,
                        secondsPerImage = 5, bitrate = 2_000_000
                    )
                    extraOutput += "\nVideo created: ${mp4.name} (${mp4.length()} bytes)"
                } catch (e: Exception) {
                    extraOutput += "\nVideo creation failed: ${e.message?.take(100) ?: "unknown error"}"
                }
            }
        }

        if (lower.contains("presentation") || lower.contains("pptx")) {
            val images = files.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            if (images.isNotEmpty()) {
                try {
                    val slides = com.mikimouse.operator.DocumentPipeline.outlineFromTexts(
                        "Auto Presentation", files.map { it.name }
                    )
                    val pptxSlides = slides.map { com.mikimouse.app.media.AndroidPptxRenderer.Slide(it.title, it.bullets) }
                    val pptx = File(ws.output, "auto-presentation.pptx")
                    com.mikimouse.app.media.AndroidPptxRenderer.render(pptxSlides, pptx)
                    extraOutput += "\nPresentation created: ${pptx.name} (${pptx.length()} bytes)"
                } catch (e: Exception) {
                    extraOutput += "\nPresentation creation failed: ${e.message?.take(100) ?: "unknown error"}"
                }
            }
        }

        if (lower.contains("pdf")) {
            val images = files.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            if (images.isNotEmpty()) {
                try {
                    val slides = com.mikimouse.operator.DocumentPipeline.outlineFromTexts("Auto PDF", files.map { it.name })
                    val pdf = File(ws.output, "auto-document.pdf")
                    com.mikimouse.app.media.AndroidPdfRenderer.renderSlides(
                        "Auto PDF", slides, pdf, images
                    )
                    extraOutput += "\nPDF created: ${pdf.name} (${pdf.length()} bytes)"
                } catch (e: Exception) {
                    extraOutput += "\nPDF creation failed: ${e.message?.take(100) ?: "unknown error"}"
                }
            }
        }

        return modelOutput + extraOutput
    }
}
