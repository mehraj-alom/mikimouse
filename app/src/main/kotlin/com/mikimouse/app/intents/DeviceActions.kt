package com.mikimouse.app.intents

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

object DeviceActions {
    fun setTorch(ctx: Context, on: Boolean): String {
        if (Build.VERSION.SDK_INT < 23) return "Torch needs Android 6+"
        if (ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return "needs Camera permission"
        }
        return try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull() ?: return "no camera found"
            cm.setTorchMode(id, on)
            if (on) "Torch on" else "Torch off"
        } catch (t: Throwable) {
            "Torch failed: ${t.message?.take(100)}"
        }
    }

    fun openAppSettings(ctx: Context) {
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Exception) {}
    }
}
