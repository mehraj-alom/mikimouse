package com.mikimouse.operator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class WorkspaceIsolationTest {
    private fun tmp(): File = createTempDir("ws-iso")

    @Test fun inputsAreImmutableAndEscapesDenied() {
        val wm = WorkspaceManager(tmp())
        val ws = wm.create("task-001")
        val orig = wm.importBytes(ws, "photo.jpg", ByteArray(128) { 7 })
        val before = orig.readBytes().copyOf()
        wm.writeWork(ws, "scratch.txt", "work".toByteArray())
        assertTrue(orig.readBytes().contentEquals(before), "original must be untouched")
        assertFailsWith<IllegalArgumentException> { wm.resolve(ws, "../outside.txt") }
        assertFailsWith<IllegalArgumentException> { wm.resolve(ws, "workspace/../../evil.txt") }
    }

    @Test fun cleanupKeepsInputsAndOutputs() {
        val wm = WorkspaceManager(tmp())
        val ws = wm.create("task-002")
        wm.importBytes(ws, "in.jpg", ByteArray(64))
        wm.writeWork(ws, "tmp.bin", ByteArray(64))
        wm.writeOutput(ws, "final_video.mp4", ByteArray(128))
        wm.cleanupTemp(ws)
        assertTrue(File(ws.input, "in.jpg").exists())
        assertTrue(File(ws.output, "final_video.mp4").exists())
        assertTrue((ws.work.listFiles()?.size ?: 0) == 0)
    }
}
