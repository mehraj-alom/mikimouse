package com.mikimouse.app.media

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object OcrReader {
    suspend fun read(file: File): String {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return ""
        val image = InputImage.fromBitmap(bmp, 0)
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine { cont ->
                client.process(image)
                    .addOnSuccessListener { cont.resume(it.text ?: "") }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            return text
        } finally {
            bmp.recycle()
            client.close()
        }
    }

    suspend fun readAll(files: List<File>): Map<String, String> =
        files.associate { it.name to runCatching { read(it) }.getOrDefault("") }
}
