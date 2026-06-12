package com.example.wifi_direct_cable.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsLogger {
    private const val MAX_ENTRIES = 1000
    private val lock = Any()
    private val entries = ArrayDeque<Entry>()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    data class Entry(
        val timestampMs: Long,
        val category: String,
        val message: String,
        val fields: Map<String, Any?>
    )

    fun log(
        category: String,
        message: String,
        fields: Map<String, Any?> = emptyMap()
    ) {
        synchronized(lock) {
            if (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.addLast(
                Entry(
                    timestampMs = System.currentTimeMillis(),
                    category = category,
                    message = message,
                    fields = fields
                )
            )
        }
    }

    fun exportText(): String {
        val snapshot = synchronized(lock) { entries.toList() }
        return buildString {
            appendLine("WDCable Android Diagnostics")
            appendLine("entries=${snapshot.size}")
            for (entry in snapshot) {
                append(timestampFormat.format(Date(entry.timestampMs)))
                append(" [")
                append(entry.category)
                append("] ")
                append(entry.message)
                if (entry.fields.isNotEmpty()) {
                    append(" ")
                    append(
                        entry.fields.entries.joinToString(" ") { (key, value) ->
                            "$key=${value ?: ""}"
                        }
                    )
                }
                appendLine()
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }
}
