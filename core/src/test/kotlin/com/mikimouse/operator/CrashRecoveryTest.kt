package com.mikimouse.operator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashRecoveryTest {
    @Test fun runningTaskRecoversToPaused() {
        val root = createTempDir("recovery")
        val tm = TaskManager(root)
        val spec = TaskSpec("task-2026-001", "Create a professional 30-second video")
        tm.create(spec)
        tm.transition(spec.taskId, TaskState.RUNNING)
        val recovered = tm.recover(spec.taskId)
        assertEquals(TaskState.PAUSED, recovered.state)
        assertTrue(File(root, "tasks/${spec.taskId}/metadata/task.json").exists())
    }

    @Test fun completedStaysCompleted() {
        val root = createTempDir("recovery2")
        val tm = TaskManager(root)
        val spec = TaskSpec("t2", "Convert these images into a PDF")
        tm.create(spec)
        tm.transition(spec.taskId, TaskState.COMPLETED)
        assertEquals(TaskState.COMPLETED, tm.recover(spec.taskId).state)
    }
}
