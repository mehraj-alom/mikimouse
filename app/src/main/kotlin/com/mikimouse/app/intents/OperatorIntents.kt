package com.mikimouse.app.intents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import com.mikimouse.app.outputs.OutputStore
import java.io.File

object OperatorIntents {
    fun shareFile(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "com.mikimouse.app.fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = OutputStore.mimeFor(file.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Share ${file.name}"))
    }

    fun openInApp(ctx: Context, deepLink: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Exception) {
        }
    }

    fun calendarEvent(ctx: Context, title: String, description: String = "") {
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
        }
        ctx.startActivity(i)
    }

    fun smsDraft(ctx: Context, number: String, body: String) {
        try {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                putExtra("sms_body", body)
            }
            ctx.startActivity(i)
        } catch (_: Exception) {}
    }

    fun mapSearch(ctx: Context, query: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(Intent.createChooser(i, "Open map"))
        } catch (_: Exception) {}
    }

    fun setAlarm(ctx: Context, message: String) {
        try {
            val i = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
            }
            ctx.startActivity(i)
        } catch (_: Exception) {}
    }

    fun adjustVolume(ctx: Context, direction: String): String {
        return try {
            val am = ctx.getSystemService(android.media.AudioManager::class.java) ?: return "no audio service"
            when {
                "max" in direction || "up" in direction ->
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                "down" in direction || "min" in direction || "mute" in direction ->
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                        if ("mute" in direction) android.media.AudioManager.ADJUST_MUTE
                        else android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                else -> return "say volume up, down, max or mute"
            }
            "Volume adjusted"
        } catch (t: Throwable) {
            "Volume failed: ${t.message?.take(100)}"
        }
    }

    fun openDisplaySettings(ctx: Context) {
        try {
            val i = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Exception) {}
    }

    fun printPdf(ctx: Context, file: File) {
        try {
            val pm = ctx.getSystemService(android.print.PrintManager::class.java) ?: return
            val adapter = object : android.print.PrintDocumentAdapter() {
                override fun onLayout(
                    old: android.print.PrintAttributes?, new: android.print.PrintAttributes?,
                    cancel: android.os.CancellationSignal?,
                    result: LayoutResultCallback?, extras: android.os.Bundle?,
                ) {
                    if (cancel?.isCanceled == true) {
                        result?.onLayoutCancelled()
                        return
                    }
                    val info = android.print.PrintDocumentInfo.Builder(file.name)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()
                    result?.onLayoutFinished(info, new == null || old == new)
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    fd: android.os.ParcelFileDescriptor?,
                    cancel: android.os.CancellationSignal?,
                    result: WriteResultCallback?,
                ) {
                    try {
                        file.inputStream().use { ins ->
                            android.os.ParcelFileDescriptor.AutoCloseOutputStream(fd).use { ins.copyTo(it) }
                        }
                        result?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (t: Throwable) {
                        result?.onWriteFailed(t.message)
                    }
                }
            }
            pm.print(file.name, adapter, null)
        } catch (_: Exception) {}
    }

    fun canHandle(ctx: Context, deepLink: String): Boolean {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
        return ctx.packageManager.resolveActivity(i, 0) != null
    }
}
