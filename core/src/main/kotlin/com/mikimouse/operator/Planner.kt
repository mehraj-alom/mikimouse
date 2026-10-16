package com.mikimouse.operator

class Planner {
    fun plan(spec: TaskSpec): TaskPlan {
        val t = spec.instruction.lowercase()
        return when {
            "video" in t || "mp4" in t || "reel" in t || "social" in t -> videoPlan(spec)
            "presentation" in t || "pptx" in t || "slides" in t || "slide" in t -> presentationPlan(spec)
            ".pdf" in t || " pdf" in t || "convert" in t && "pdf" in t -> pdfPlan(spec)
            "extract text" in t || "ocr" in t -> ocrPlan(spec)
            "compress" in t -> compressPlan(spec)
            "download" in t || "organize" in t -> downloadPlan(spec)
            "research" in t -> researchPlan(spec)
            "docx" in t || "document" in t || "report" in t -> docPlan(spec)
            else -> genericPlan(spec)
        }
    }

    private fun videoPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect inputs (dimensions/codec/duration)", "media_inspect"),
            PlanStep("storyboard", "Create 30s storyboard from inputs", "write_workspace"),
            PlanStep("normalize", "Resize/crop inputs to 1280x720", "image_process"),
            PlanStep("timeline", "Arrange timeline with transitions + title", "write_workspace"),
            PlanStep("render", "Render final_video.mp4 (streaming)", "run_media_pipeline"),
            PlanStep("validate", "Validate mp4 output", "read_input"),
            PlanStep("export", "Export final_video.mp4 to user location", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "image_process", "run_media_pipeline", "share_artifact"),
        estimatedStorageMB = 300, overallRisk = Risk.MEDIUM,
    )

    private fun presentationPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect screenshots/files", "media_inspect"),
            PlanStep("ocr", "Extract text from images", "ocr_image"),
            PlanStep("outline", "Generate outline + design system", "write_workspace"),
            PlanStep("render-pptx", "Render presentation.pptx", "render_document"),
            PlanStep("render-pdf", "Render preview PDF", "render_document"),
            PlanStep("validate", "Validate PPTX/PDF", "read_input"),
            PlanStep("export", "Export PPTX + PDF", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("ocr_image", "render_document", "share_artifact"),
        estimatedStorageMB = 100, overallRisk = Risk.MEDIUM,
    )

    private fun pdfPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect inputs", "media_inspect"),
            PlanStep("render", "Render document.pdf", "render_document"),
            PlanStep("validate", "Validate PDF", "read_input"),
            PlanStep("export", "Export PDF", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("render_document", "share_artifact"),
        estimatedStorageMB = 60, overallRisk = Risk.LOW,
    )

    private fun docPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("outline", "Generate document outline", "write_workspace"),
            PlanStep("render", "Render DOCX + Markdown", "render_document"),
            PlanStep("validate", "Validate document", "read_input"),
            PlanStep("export", "Export document", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("render_document", "share_artifact"),
        estimatedStorageMB = 40, overallRisk = Risk.LOW,
    )

    private fun compressPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect media bitrate/size", "media_inspect"),
            PlanStep("compress", "Re-encode at lower bitrate (streaming)", "run_media_pipeline"),
            PlanStep("validate", "Validate output quality/size", "read_input"),
            PlanStep("export", "Export compressed file", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "run_media_pipeline", "share_artifact"),
        estimatedStorageMB = 250, overallRisk = Risk.LOW,
    )

    private fun ocrPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("ocr", "Extract text from screenshots", "ocr_image"),
            PlanStep("render", "Create DOCX from extracted text", "render_document"),
            PlanStep("export", "Export DOCX", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("ocr_image", "render_document", "share_artifact"),
        estimatedStorageMB = 30, overallRisk = Risk.LOW,
    )

    private fun downloadPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("download", "Download files to workspace (capped)", "download_to_workspace", Risk.MEDIUM),
            PlanStep("organize", "Organize into output/", "write_workspace"),
            PlanStep("export", "Export organized files", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("download_to_workspace", "share_artifact"),
        estimatedStorageMB = 200, overallRisk = Risk.MEDIUM, requiredSkills = listOf("composio"),
    )

    private fun researchPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("search", "Collect sources (network-gated)", "web_search", Risk.MEDIUM),
            PlanStep("draft", "Draft 5-page report", "write_workspace"),
            PlanStep("render", "Render report PDF/DOCX", "render_document"),
            PlanStep("export", "Export report", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("web_search", "render_document", "share_artifact"),
        estimatedStorageMB = 50, overallRisk = Risk.MEDIUM, requiredSkills = listOf("composio"),
    )

    private fun genericPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect inputs", "media_inspect"),
            PlanStep("process", "Process per instruction", "write_workspace"),
            PlanStep("export", "Export result", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("write_workspace", "share_artifact"),
        estimatedStorageMB = 50, overallRisk = Risk.MEDIUM,
    )
}
