package com.mikimouse.operator

class Planner {
    fun plan(spec: TaskSpec): TaskPlan {
        val t = spec.instruction.lowercase()
        return when {
            ("trim" in t || "cut" in t) && "audio" in t -> audioTrimPlan(spec)
            ("trim" in t || "cut" in t) && "video" in t -> videoTrimPlan(spec)
            "video" in t || "mp4" in t || "reel" in t || "social" in t -> videoPlan(spec)
            "presentation" in t || "pptx" in t || "slides" in t || "slide" in t -> presentationPlan(spec)
            ("merge" in t || "combine" in t) && "pdf" in t -> mergePdfPlan(spec)
            ("split" in t || "divide" in t) && "pdf" in t -> splitPdfPlan(spec)
            ".pdf" in t || " pdf" in t || "convert" in t && "pdf" in t -> pdfPlan(spec)
            "extract text" in t || "ocr" in t -> ocrPlan(spec)
            "compress" in t -> compressPlan(spec)
            "download" in t || "organize" in t -> downloadPlan(spec)
            "research" in t -> researchPlan(spec)
            "docx" in t || "document" in t || "report" in t -> docPlan(spec)
            "resize" in t && ("image" in t || "photo" in t || "picture" in t) -> imageResizePlan(spec)
            "convert" in t && ("image" in t || "photo" in t || "picture" in t) -> imageConvertPlan(spec)
            ("mix" in t || "blend" in t) && ("audio" in t || "track" in t) -> audioMixPlan(spec)
            "text to speech" in t || "tts" in t -> ttsPlan(spec)
            "speech to text" in t || "stt" in t || "transcribe" in t -> sttPlan(spec)
            "email" in t || "send email" in t -> emailPlan(spec)
            "sms" in t || "text message" in t -> smsPlan(spec)
            "zip" in t || "archive" in t || "compress files" in t -> archivePlan(spec)
            "browser" in t || "open url" in t || "open website" in t -> browserPlan(spec)
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

    private fun videoTrimPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect video inputs", "media_inspect"),
            PlanStep("trim", "Trim video segment", "video_trim"),
            PlanStep("validate", "Validate trimmed output", "read_input"),
            PlanStep("export", "Export trimmed video", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "video_trim", "share_artifact"),
        estimatedStorageMB = 200, overallRisk = Risk.LOW,
    )

    private fun imageResizePlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect images", "media_inspect"),
            PlanStep("resize", "Resize images", "image_resize"),
            PlanStep("validate", "Validate resized images", "read_input"),
            PlanStep("export", "Export resized images", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "image_resize", "share_artifact"),
        estimatedStorageMB = 100, overallRisk = Risk.LOW,
    )

    private fun imageConvertPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect images", "media_inspect"),
            PlanStep("convert", "Convert image format", "image_convert"),
            PlanStep("validate", "Validate converted images", "read_input"),
            PlanStep("export", "Export converted images", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "image_convert", "share_artifact"),
        estimatedStorageMB = 100, overallRisk = Risk.LOW,
    )

    private fun audioTrimPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect audio inputs", "media_inspect"),
            PlanStep("trim", "Trim audio segment", "audio_trim"),
            PlanStep("validate", "Validate trimmed audio", "read_input"),
            PlanStep("export", "Export trimmed audio", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "audio_trim", "share_artifact"),
        estimatedStorageMB = 100, overallRisk = Risk.LOW,
    )

    private fun audioMixPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect audio inputs", "media_inspect"),
            PlanStep("mix", "Mix audio tracks", "audio_mix"),
            PlanStep("validate", "Validate mixed audio", "read_input"),
            PlanStep("export", "Export mixed audio", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "audio_mix", "share_artifact"),
        estimatedStorageMB = 150, overallRisk = Risk.LOW,
    )

    private fun mergePdfPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect PDFs", "media_inspect"),
            PlanStep("merge", "Merge PDFs", "merge_pdf"),
            PlanStep("validate", "Validate merged PDF", "read_input"),
            PlanStep("export", "Export merged PDF", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "merge_pdf", "share_artifact"),
        estimatedStorageMB = 100, overallRisk = Risk.LOW,
    )

    private fun splitPdfPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect PDF", "media_inspect"),
            PlanStep("split", "Split PDF by pages", "split_pdf"),
            PlanStep("validate", "Validate split PDFs", "read_input"),
            PlanStep("export", "Export split PDFs", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "split_pdf", "share_artifact"),
        estimatedStorageMB = 100, overallRisk = Risk.LOW,
    )

    private fun ttsPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect text inputs", "media_inspect"),
            PlanStep("tts", "Convert text to speech", "text_to_speech"),
            PlanStep("validate", "Validate audio output", "read_input"),
            PlanStep("export", "Export audio", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "text_to_speech", "share_artifact"),
        estimatedStorageMB = 50, overallRisk = Risk.LOW,
    )

    private fun sttPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect audio inputs", "media_inspect"),
            PlanStep("stt", "Transcribe audio to text", "speech_to_text"),
            PlanStep("validate", "Validate transcription", "read_input"),
            PlanStep("export", "Export text", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "speech_to_text", "share_artifact"),
        estimatedStorageMB = 50, overallRisk = Risk.LOW,
    )

    private fun emailPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect inputs to email", "media_inspect"),
            PlanStep("render", "Prepare email content", "write_workspace"),
            PlanStep("send", "Send email with attachment", "email_send", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "write_workspace", "email_send"),
        estimatedStorageMB = 50, overallRisk = Risk.HIGH,
    )

    private fun smsPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect message content", "media_inspect"),
            PlanStep("render", "Prepare SMS content", "write_workspace"),
            PlanStep("send", "Send SMS", "sms_send", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "write_workspace", "sms_send"),
        estimatedStorageMB = 20, overallRisk = Risk.MEDIUM,
    )

    private fun archivePlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("inspect", "Inspect inputs to archive", "media_inspect"),
            PlanStep("archive", "Create ZIP archive", "archive"),
            PlanStep("validate", "Validate archive", "read_input"),
            PlanStep("export", "Export archive", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("media_inspect", "archive", "share_artifact"),
        estimatedStorageMB = 200, overallRisk = Risk.LOW,
    )

    private fun browserPlan(spec: TaskSpec) = TaskPlan(
        goal = spec.instruction,
        steps = listOf(
            PlanStep("open", "Open URL in browser", "browser_open", Risk.MEDIUM),
            PlanStep("export", "Export result", "share_artifact", Risk.MEDIUM),
        ),
        requiredTools = listOf("browser_open", "share_artifact"),
        estimatedStorageMB = 20, overallRisk = Risk.MEDIUM,
    )
}
