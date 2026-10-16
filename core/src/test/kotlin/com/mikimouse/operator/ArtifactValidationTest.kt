package com.mikimouse.operator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ArtifactValidationTest {
    @Test fun mp4AndPptxValidators() {
        val root = createTempDir("art")
        val wm = WorkspaceManager(root)
        val am = ArtifactManager(wm)
        val ws = wm.create("t-art")
        val mp4 = File(ws.output, "final_video.mp4").apply { parentFile.mkdirs() }
        MediaPipeline.renderJvmStoryboard(listOf(File(ws.input, "a.txt").apply {
            parentFile.mkdirs(); writeText("x".repeat(2000))
        }), mp4, Storyboard())
        assertTrue(am.validateVideoMp4(mp4))
        assertFalse(am.validateVideoMp4(File(ws.output, "empty.mp4").apply { parentFile.mkdirs(); writeBytes(ByteArray(10)) }))

        val pptx = DocumentPipeline.renderPptx(
            DocumentPipeline.outlineFromTexts("T", listOf("alpha", "beta")),
            File(ws.output, "presentation.pptx"),
        )
        assertTrue(am.validatePptx(pptx))
        val reg = am.register("t-art", pptx, "pptx")
        assertTrue(reg.checksum.length == 64 && reg.size > 0)

        val dest = am.verifiedCopy("t-art", pptx, File(root, "export"))
        assertTrue(dest.exists() && dest.length() == pptx.length())
    }
}
