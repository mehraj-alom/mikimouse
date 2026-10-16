package com.mikimouse.operator

import java.io.File
import java.time.Instant

class AuditLogger(private val logFile: File? = null) {
    private val _entries = mutableListOf<String>()
    val entries: List<String> get() = _entries.toList()

    @Synchronized
    fun append(event: String, detail: String = "") {
        val line = "${Instant.now()} $event ${detail.take(300)}"
        _entries += line
        logFile?.let {
            it.parentFile?.mkdirs()
            it.appendText(line + "\n")
        }
    }
}
