package com.mikimouse.app.outputs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mikimouse.app.MainActivity
import java.io.File
import java.util.concurrent.TimeUnit

data class OutputFile(
    val taskId: String,
    val file: File,
    val sizeBytes: Long = file.length(),
    val modifiedAt: Long = file.lastModified(),
)

object OutputStore {
    fun listAll(operatorRoot: File): List<OutputFile> {
        val tasks = File(operatorRoot, "tasks").listFiles()?.toList() ?: return emptyList()
        return tasks.flatMap { task ->
            File(task, "output").listFiles()?.map { OutputFile(task.name, it) } ?: emptyList()
        }.sortedByDescending { it.modifiedAt }
    }

    fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4" -> "video/mp4"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "pdf" -> "application/pdf"
        "md" -> "text/markdown"
        "txt" -> "text/plain"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "*/*"
    }
}

object Notifications {
    const val CHANNEL = "outputs"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Outputs & reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun notify(ctx: Context, id: Int, title: String, text: String) {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    fun remindInOneHour(ctx: Context, fileName: String) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .setInputData(workDataOf("file" to fileName))
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val file = inputData.getString("file") ?: "your output"
        Notifications.notify(
            applicationContext, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            "Reminder: $file", "Your MikiMouse output is still in the app. Open, export or share it.",
        )
        return Result.success()
    }
}
