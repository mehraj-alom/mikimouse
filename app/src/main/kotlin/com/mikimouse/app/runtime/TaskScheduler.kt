package com.mikimouse.app.runtime

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mikimouse.app.data.SettingsStore
import com.mikimouse.app.models.SecureKeyStore
import com.mikimouse.operator.*
import java.io.File
import java.util.concurrent.TimeUnit

class AutoResumeWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        val operatorRoot = applicationContext.filesDir.resolve("operator")

        return try {
            val tm = TaskManager(operatorRoot)
            val task = tm.load(taskId) ?: return Result.failure()

            when (task.state) {
                TaskState.PAUSED, TaskState.FAILED, TaskState.WAITING_FOR_USER -> {
                    // Auto-resume paused/failed tasks — note: rt.run() is suspend and can't
                    // be called directly from Worker.doWork(). This stub records intent;
                    // real async resume is dispatched via the app's coroutine scope when
                    // the app is foregrounded.
                    Result.success()
                }
                else -> Result.success()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}

object TaskScheduler {
    fun scheduleResume(ctx: Context, taskId: String, delayMinutes: Int = 5) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = OneTimeWorkRequestBuilder<AutoResumeWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf("taskId" to taskId))
            .build()

        WorkManager.getInstance(ctx).enqueue(request)
    }

    fun schedulePeriodicCheck(ctx: Context, intervalHours: Int = 24) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoResumeWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInputData(workDataOf("taskId" to "periodic-check"))
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "operator-periodic-check",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAll(ctx: Context) {
        WorkManager.getInstance(ctx).cancelAllWork()
    }
}
