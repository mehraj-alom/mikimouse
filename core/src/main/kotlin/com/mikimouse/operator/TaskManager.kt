package com.mikimouse.operator

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PersistedTask(
    val spec: TaskSpec,
    var state: TaskState,
    var plan: TaskPlan? = null,
    var log: List<String> = emptyList(),
)

class TaskManager(
    private val operatorRoot: File,
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true },
) {
    private fun file(taskId: String): File = File(operatorRoot, "tasks/$taskId/metadata/task.json")

    fun save(t: PersistedTask) {
        val f = file(t.spec.taskId)
        f.parentFile?.mkdirs()
        f.writeText(json.encodeToString(PersistedTask.serializer(), t))
    }

    fun load(taskId: String): PersistedTask? {
        val f = file(taskId)
        if (!f.exists()) return null
        return json.decodeFromString(PersistedTask.serializer(), f.readText())
    }

    fun listTasks(): List<PersistedTask> {
        val tasksDir = File(operatorRoot, "tasks")
        return tasksDir.listFiles()
            ?.mapNotNull { runCatching { load(it.name) }.getOrNull() }
            ?: emptyList()
    }

    fun create(spec: TaskSpec): PersistedTask {
        val t = PersistedTask(spec, TaskState.CREATED)
        save(t)
        return t
    }

    fun transition(taskId: String, to: TaskState): PersistedTask {
        val t = load(taskId) ?: throw IllegalArgumentException("no task $taskId")
        t.state = to
        save(t)
        return t
    }

    fun recover(taskId: String): PersistedTask {
        val t = load(taskId) ?: throw IllegalArgumentException("no task $taskId")
        // Crash recovery: RUNNING work that died restarts as PAUSED (resume) —
        // caller decides resume vs retry. Never silently re-run HIGH steps.
        if (t.state == TaskState.RUNNING || t.state == TaskState.CLEANING) {
            t.state = TaskState.PAUSED
            save(t)
        }
        return t
    }
}
