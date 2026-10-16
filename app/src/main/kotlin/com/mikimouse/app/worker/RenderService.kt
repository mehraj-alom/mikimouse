package com.mikimouse.app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class RenderService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("render", "Rendering", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "render")
            .setContentTitle("MikiMouse rendering")
            .setContentText("Processing media in workspace…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, n)
        // Work is driven by OperatorWorker; service only keeps the foreground claim
        // visible while a render is active. Stop when work completes.
        return START_NOT_STICKY
    }
}
