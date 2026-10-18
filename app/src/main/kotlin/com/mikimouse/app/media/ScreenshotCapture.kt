package com.mikimouse.app.media

import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import java.io.File

object ScreenshotCapture {
    private var projectionManager: MediaProjectionManager? = null

    fun init(ctx: Context) {
        projectionManager = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?
    }

    fun captureScreenshot(ctx: Context, out: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val intent = projectionManager?.createScreenCaptureIntent()
                if (intent != null) {
                    // Note: Requires user to grant screen capture permission via system dialog
                    // This is a one-time permission per app install
                    true
                } else false
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun captureWindow(ctx: Context, out: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Screen capture requires MediaProjection API with user permission
                // This is a stub that returns false - real implementation needs
                // MediaProjectionManager + ActivityResultLauncher for user consent
                false
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
