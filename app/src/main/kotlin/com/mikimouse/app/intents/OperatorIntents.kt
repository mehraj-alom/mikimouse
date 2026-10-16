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

    fun canHandle(ctx: Context, deepLink: String): Boolean {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
        return ctx.packageManager.resolveActivity(i, 0) != null
    }
}
