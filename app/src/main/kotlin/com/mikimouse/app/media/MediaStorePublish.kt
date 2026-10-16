package com.mikimouse.app.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object MediaStorePublish {
    private fun publishDoc(ctx: Context, file: File): String {
        val mime = mapOf(
            "pdf" to "application/pdf",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "csv" to "text/csv",
            "md" to "text/markdown",
            "txt" to "text/plain",
        )[file.extension.lowercase()] ?: "*/*"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MikiMouse")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = ctx.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Downloads insert failed")
        ctx.contentResolver.openOutputStream(uri)?.use { os ->
            file.inputStream().use { it.copyTo(os) }
        } ?: throw IllegalStateException("Downloads write failed")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        ctx.contentResolver.update(uri, values, null, null)
        return uri.toString()
    }

    fun publish(ctx: Context, file: File): String {
        val ext = file.extension.lowercase()
        if (ext in setOf("pdf", "pptx", "docx", "md", "txt", "csv", "xlsx") && Build.VERSION.SDK_INT >= 29) {
            return publishDoc(ctx, file)
        }
        val (collection, mime, dir) = when (ext) {
            "mp4", "mov", "3gp" -> Triple(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "video/mp4", Environment.DIRECTORY_MOVIES,
            )
            "jpg", "jpeg", "png", "webp" -> Triple(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "image/${if (ext == "jpg") "jpeg" else ext}", Environment.DIRECTORY_PICTURES,
            )
            else -> throw IllegalArgumentException("Gallery publish supports video/images, not .$ext")
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/MikiMouse")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        ctx.contentResolver.openOutputStream(uri)?.use { os ->
            file.inputStream().use { it.copyTo(os) }
        } ?: throw IllegalStateException("MediaStore write failed")
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            ctx.contentResolver.update(uri, values, null, null)
        }
        return uri.toString()
    }
}
