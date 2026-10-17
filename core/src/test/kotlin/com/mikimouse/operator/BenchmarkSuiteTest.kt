package com.mikimouse.operator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class BenchmarkSuiteTest {
    private val planner = Planner()

    private fun planFor(s: String) = planner.plan(TaskSpec("b", s))

    @Test fun benchmarkPlanShapes() {
        // 1: 2 images + 1 video → 30s video
        assertTrue(planFor("I have two images and one video. Create a 30-second professional video.")
            .requiredTools.contains("run_media_pipeline"))
        // 2: screenshots → presentation
        assertTrue(planFor("Create a presentation from these screenshots.").steps.any { it.tool == "render_document" })
        // 3: images → PDF
        assertTrue(planFor("Convert these images into a PDF.").steps.any { it.tool == "render_document" })
        // 4: research → 5-page report
        assertTrue(planFor("Research this topic and produce a 5-page report.").requiredTools.contains("web_search"))
        // 5: compress videos
        assertTrue(planFor("Compress these videos without losing too much quality.").requiredTools.contains("run_media_pipeline"))
        // 6: screenshots → DOCX
        assertTrue(planFor("Extract text from these screenshots and create a DOCX.").requiredTools.contains("ocr_image"))
        // 7: download + organize
        assertTrue(planFor("Download these files and organize them.").requiredTools.contains("download_to_workspace"))
        // 8: social video
        assertTrue(planFor("Create a social-media-ready video from these assets.").requiredTools.contains("run_media_pipeline"))
        // 9: configured API content (generic path still exportable)
        assertTrue(planFor("Use my configured API to generate the content.").steps.isNotEmpty())
        // 10: offline
        val off = planFor("Perform the task completely offline using the local model.")
        assertTrue(off.steps.isNotEmpty())
        // 11: trim video
        assertTrue(planFor("Trim the first 10 seconds of this video.").requiredTools.contains("video_trim"))
        // 12: resize images
        assertTrue(planFor("Resize these images to 1280x720.").requiredTools.contains("image_resize"))
        // 13: convert image format
        assertTrue(planFor("Convert these images to PNG.").requiredTools.contains("image_convert"))
        // 14: trim audio
        assertTrue(planFor("Trim the beginning of this audio track.").requiredTools.contains("audio_trim"))
        // 15: mix audio
        assertTrue(planFor("Mix these two audio tracks together.").requiredTools.contains("audio_mix"))
        // 16: merge PDFs
        assertTrue(planFor("Merge these three PDFs into one.").requiredTools.contains("merge_pdf"))
        // 17: split PDF
        assertTrue(planFor("Split this PDF by pages.").requiredTools.contains("split_pdf"))
        // 18: text to speech
        assertTrue(planFor("Convert this text to speech.").requiredTools.contains("text_to_speech"))
        // 19: speech to text
        assertTrue(planFor("Transcribe this audio to text.").requiredTools.contains("speech_to_text"))
        // 20: email
        assertTrue(planFor("Email this file to john@example.com.").requiredTools.contains("email_send"))
        // 21: SMS
        assertTrue(planFor("Send an SMS to +15551234567.").requiredTools.contains("sms_send"))
        // 22: zip/archive
        assertTrue(planFor("Zip these files into an archive.").requiredTools.contains("archive"))
        // 23: browser
        assertTrue(planFor("Open this website in the browser.").requiredTools.contains("browser_open"))
    }

    @Test fun benchmark1EndToEnd() = runTest {
        val root = createTempDir("bench1")
        val rt = OperatorRuntime(root, resources = ResourceManager(ResourceBudget(storageQuotaMB = 1024)))
        val ws = WorkspaceManager(root).create("bench-1")
        WorkspaceManager(root).importBytes(ws, "photo1.jpg", ByteArray(4096) { 1 })
        WorkspaceManager(root).importBytes(ws, "photo2.jpg", ByteArray(4096) { 2 })
        WorkspaceManager(root).importBytes(ws, "clip.mp4", ByteArray(8192) { 3 })
        val res = rt.run(
            TaskSpec("bench-1", "Create a professional 30-second video from these files."),
            approve = { true },
        )
        assertTrue(res.state == TaskState.COMPLETED, res.summary)
        val mp4 = File(root, "tasks/bench-1/output/final_video.mp4")
        assertTrue(mp4.exists() && mp4.length() > 1024, "missing final_video.mp4")
        assertTrue(ArtifactManager(WorkspaceManager(root)).validateVideoMp4(mp4))
    }

    @Test fun benchmark2EndToEnd() = runTest {
        val root = createTempDir("bench2")
        val rt = OperatorRuntime(root)
        val ws = WorkspaceManager(root).create("bench-2")
        WorkspaceManager(root).importBytes(ws, "shot1.png", ByteArray(2048) { 4 })
        WorkspaceManager(root).importBytes(ws, "shot2.png", ByteArray(2048) { 5 })
        val res = rt.run(
            TaskSpec("bench-2", "Make a presentation from these screenshots."),
            approve = { true },
        )
        assertTrue(res.state == TaskState.COMPLETED, res.summary)
        val pptx = File(root, "tasks/bench-2/output/presentation.pptx")
        assertTrue(pptx.exists() && ArtifactManager(WorkspaceManager(root)).validatePptx(pptx))
    }
}
