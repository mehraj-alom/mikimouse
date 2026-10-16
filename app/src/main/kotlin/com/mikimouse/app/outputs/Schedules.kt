package com.mikimouse.app.outputs

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.WorkerParameters
import com.mikimouse.operator.OperatorRuntime
import com.mikimouse.operator.ResourceManager
import com.mikimouse.operator.TaskManager
import com.mikimouse.operator.TaskSpec
import com.mikimouse.operator.TaskState
import java.io.File
import java.util.concurrent.TimeUnit

class ScheduledRunWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val instruction = inputData.getString("instruction").orEmpty()
        if (instruction.isBlank()) return Result.failure()
        val root = File(applicationContext.filesDir, "operator")
        return try {
            val taskId = "task-sched-${System.currentTimeMillis()}"
            val wm = com.mikimouse.operator.WorkspaceManager(root)
            wm.create(taskId)
            val tm = TaskManager(root)
            tm.create(TaskSpec(taskId, instruction))
            tm.transition(taskId, TaskState.RUNNING)
            val rt = OperatorRuntime(root, resources = ResourceManager())
            val res = rt.run(TaskSpec(taskId, instruction), approve = { false })
            tm.transition(taskId, if (res.state == TaskState.COMPLETED) TaskState.COMPLETED else TaskState.FAILED)
            Notifications.notify(
                applicationContext, taskId.hashCode(),
                "Scheduled task done", "${res.summary.take(120)}",
            )
            Result.success()
        } catch (t: Throwable) {
            Notifications.notify(applicationContext, 0, "Scheduled task failed", "${t.message?.take(120)}")
            Result.retry()
        }
    }
}

object Schedules {
    private const val NAME = "operator-schedule"

    fun set(ctx: Context, instruction: String, hours: Long) {
        if (instruction.isBlank()) {
            cancel(ctx)
            return
        }
        val req = PeriodicWorkRequestBuilder<ScheduledRunWorker>(hours, TimeUnit.HOURS)
            .setInputData(workDataOf("instruction" to instruction))
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
}
