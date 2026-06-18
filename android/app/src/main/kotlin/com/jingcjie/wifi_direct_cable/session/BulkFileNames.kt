package com.jingcjie.wifi_direct_cable.session

import java.io.File

internal object BulkFileNames {
    fun safeFileName(fileName: String): String {
        val sanitized = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\r\\n\\u0000]"), "_")
            .replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .trim()
        return sanitized.ifBlank { "unknown_file" }
    }

    fun duplicateSafeFile(directory: File, safeFileName: String): File {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val baseName = safeFileName.substringBeforeLast('.', safeFileName)
        val extension = safeFileName.substringAfterLast('.', "")
        var candidate = File(directory, safeFileName)
        var index = 1
        while (candidate.exists()) {
            val nextName = if (extension.isBlank()) {
                "$baseName ($index)"
            } else {
                "$baseName ($index).$extension"
            }
            candidate = File(directory, nextName)
            index++
        }
        return candidate
    }
}
