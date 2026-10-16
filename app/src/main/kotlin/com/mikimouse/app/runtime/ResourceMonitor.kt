package com.mikimouse.app.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.mikimouse.operator.DeviceCaps

object ResourceMonitor {
    fun snapshot(ctx: Context): DeviceCaps {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val stat = StatFs(ctx.filesDir.path)
        val freeMB = stat.availableBytes / (1024 * 1024)
        return DeviceCaps(
            ramMB = mi.totalMem / (1024 * 1024),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuArch = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            sdkInt = android.os.Build.VERSION.SDK_INT,
            freeStorageMB = freeMB,
            hasGpu = true, // conservative: assume HW codecs present; MediaCodec query is P1 detail
        )
    }

    fun workspaceSizeMB(root: java.io.File): Long {
        var bytes = 0L
        root.walkTopDown().forEach { if (it.isFile) bytes += it.length() }
        return bytes / (1024 * 1024)
    }
}
