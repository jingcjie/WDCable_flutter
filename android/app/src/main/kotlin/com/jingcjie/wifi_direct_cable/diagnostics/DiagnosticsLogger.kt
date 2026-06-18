package com.jingcjie.wifi_direct_cable.diagnostics

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
        val requiredKeys = listOf(
            "platform",
            "opId",
            "state",
            "api",
            "callback",
            "broadcast",
            "result",
            "reasonCode",
            "reasonName",
            "peerAddress",
            "peerName",
            "discoveryState",
            "listenState",
            "groupFormed",
            "isGroupOwner",
            "groupOwnerAddress"
        )
        return buildString {
            appendLine("WDCable Android Diagnostics")
            appendLine("entries=${snapshot.size}")
            for (entry in snapshot) {
                val fieldsWithDefaults = linkedMapOf<String, Any?>(
                    "platform" to "android",
                    "opId" to "",
                    "state" to "",
                    "api" to "",
                    "callback" to "",
                    "broadcast" to "",
                    "result" to "",
                    "reasonCode" to "",
                    "reasonName" to "",
                    "peerAddress" to "",
                    "peerName" to "",
                    "discoveryState" to "",
                    "listenState" to "",
                    "groupFormed" to "",
                    "isGroupOwner" to "",
                    "groupOwnerAddress" to ""
                )
                fieldsWithDefaults.putAll(entry.fields)
                append(timestampFormat.format(Date(entry.timestampMs)))
                append(" | ")
                append(requiredKeys.joinToString(" | ") { key -> "$key=${fieldsWithDefaults[key] ?: ""}" })
                append(" | category=")
                append(entry.category)
                append(" | message=")
                append(entry.message)
                val extra = entry.fields
                    .filterKeys { key -> key !in requiredKeys && key != "message" }
                    .entries
                    .joinToString(" | ") { (key, value) -> "$key=${value ?: ""}" }
                if (extra.isNotEmpty()) {
                    append(" | ")
                    append(extra)
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
