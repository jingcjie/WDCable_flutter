package com.jingcjie.wifi_direct_cable

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.jingcjie.wifi_direct_cable.diagnostics.DiagnosticsLogger
import com.jingcjie.wifi_direct_cable.session.BulkFileNames
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

class ReceiveDestinationManager(
    private val context: Context,
    private val methodChannel: MethodChannel
) {
    data class Destination(
        val mode: String,
        val displayName: String,
        val uri: String? = null
    ) {
        fun toMap(message: String? = null): Map<String, Any?> = mapOf(
            "mode" to mode,
            "displayName" to displayName,
            "uri" to uri,
            "message" to message
        )
    }

    data class PublishedFile(
        val displayName: String,
        val location: String,
        val savedLocation: String
    )

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val partialDirectory = File(context.cacheDir, "incoming-file-parts")
    private val partialFileStore = IncomingPartialFileStore(partialDirectory)
    private val mainThreadDispatcher = MainThreadDispatcher()

    init {
        cleanupStalePartialFiles()
    }

    fun current(): Destination {
        val mode = preferences.getString(KEY_MODE, MODE_APP) ?: MODE_APP
        return when (mode) {
            MODE_DOWNLOADS -> Destination(MODE_DOWNLOADS, "Downloads")
            MODE_CUSTOM -> {
                val uri = preferences.getString(KEY_CUSTOM_URI, null)
                val name = preferences.getString(KEY_CUSTOM_NAME, null)
                if (
                    uri.isNullOrBlank() ||
                    name.isNullOrBlank() ||
                    !hasPersistedWritePermission(Uri.parse(uri))
                ) {
                    fallbackToAppStorage("Saved custom folder is unavailable")
                } else {
                    Destination(MODE_CUSTOM, name, uri)
                }
            }
            else -> Destination(MODE_APP, "App storage")
        }
    }

    fun setMode(mode: String): Destination {
        val destination = when (mode) {
            MODE_DOWNLOADS -> Destination(MODE_DOWNLOADS, "Downloads")
            MODE_CUSTOM -> {
                val current = current()
                if (current.mode != MODE_CUSTOM) {
                    throw IOException("Choose a custom folder first")
                }
                current
            }
            else -> Destination(MODE_APP, "App storage")
        }
        preferences.edit().putString(KEY_MODE, destination.mode).apply()
        return destination
    }

    fun saveCustomFolder(uri: Uri, displayName: String): Destination {
        verifyCustomFolder(uri)
        val destination = Destination(MODE_CUSTOM, displayName, uri.toString())
        preferences.edit()
            .putString(KEY_MODE, MODE_CUSTOM)
            .putString(KEY_CUSTOM_URI, uri.toString())
            .putString(KEY_CUSTOM_NAME, displayName)
            .apply()
        return destination
    }

    fun createPartialFile(transferId: String): File {
        return partialFileStore.create(transferId)
    }

    fun publish(partialFile: File, requestedName: String, mimeType: String = "application/octet-stream"): PublishedFile {
        val destination = current()
        return when (destination.mode) {
            MODE_DOWNLOADS -> publishToDownloads(partialFile, requestedName, mimeType)
            MODE_CUSTOM -> {
                try {
                    publishToCustomFolder(
                        partialFile,
                        requestedName,
                        mimeType,
                        Uri.parse(destination.uri)
                    )
                } catch (exception: Exception) {
                    val fallback = fallbackToAppStorage(
                        "Custom folder access was lost; received files now use app storage"
                    )
                    mainThreadDispatcher.dispatch {
                        methodChannel.invokeMethod(
                            "onReceiveDestinationChanged",
                            fallback.toMap(
                                "Custom folder access was lost; switched to app storage"
                            )
                        )
                    }
                    publishToAppStorage(partialFile, requestedName)
                }
            }
            else -> publishToAppStorage(partialFile, requestedName)
        }
    }

    fun cleanupStalePartialFiles() {
        partialFileStore.cleanupStaleFiles()
    }

    private fun publishToAppStorage(partialFile: File, requestedName: String): PublishedFile {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        val target = BulkFileNames.duplicateSafeFile(directory, requestedName)
        moveOrCopy(partialFile, target)
        return PublishedFile(
            displayName = target.name,
            location = target.absolutePath,
            savedLocation = target.parentFile?.absolutePath ?: "App storage"
        )
    }

    private fun publishToDownloads(
        partialFile: File,
        requestedName: String,
        mimeType: String
    ): PublishedFile {
        val resolver = context.contentResolver
        val safeName = duplicateSafeMediaStoreName(requestedName)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create file in Downloads")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(partialFile).use { input -> input.copyTo(output) }
            } ?: throw IOException("Unable to write file in Downloads")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            partialFile.delete()
            return PublishedFile(safeName, uri.toString(), "Downloads")
        } catch (exception: Exception) {
            resolver.delete(uri, null, null)
            throw exception
        }
    }

    private fun publishToCustomFolder(
        partialFile: File,
        requestedName: String,
        mimeType: String,
        treeUri: Uri
    ): PublishedFile {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val safeName = duplicateSafeDocumentName(treeUri, requestedName)
        val uri = DocumentsContract.createDocument(resolver, parent, mimeType, safeName)
            ?: throw IOException("Unable to create file in selected folder")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(partialFile).use { input -> input.copyTo(output) }
            } ?: throw IOException("Unable to write file in selected folder")
            partialFile.delete()
            return PublishedFile(
                safeName,
                uri.toString(),
                current().displayName
            )
        } catch (exception: Exception) {
            try {
                DocumentsContract.deleteDocument(resolver, uri)
            } catch (_: Exception) {
            }
            throw exception
        }
    }

    private fun verifyCustomFolder(treeUri: Uri) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val probe = DocumentsContract.createDocument(
            resolver,
            parent,
            "application/octet-stream",
            "wdcable-write-test-${UUID.randomUUID()}.tmp"
        ) ?: throw IOException("Selected folder is not writable")
        try {
            resolver.openOutputStream(probe, "w")?.use { output ->
                output.write(0)
                output.flush()
            } ?: throw IOException("Selected folder is not writable")
        } finally {
            try {
                DocumentsContract.deleteDocument(resolver, probe)
            } catch (exception: Exception) {
                DiagnosticsLogger.log(
                    "file",
                    "Could not delete custom folder write probe",
                    mapOf(
                        "uri" to probe.toString(),
                        "error" to exception.message
                    )
                )
            }
        }
    }

    private fun hasPersistedWritePermission(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any { permission ->
            permission.isWritePermission && sameTree(permission.uri, uri)
        }
    }

    private fun sameTree(first: Uri, second: Uri): Boolean {
        if (first == second) return true
        if (first.authority != second.authority) return false
        return try {
            DocumentsContract.getTreeDocumentId(first) ==
                DocumentsContract.getTreeDocumentId(second)
        } catch (_: Exception) {
            first.normalizeScheme().toString().trimEnd('/') ==
                second.normalizeScheme().toString().trimEnd('/')
        }
    }

    private fun duplicateSafeMediaStoreName(requestedName: String): String {
        val safeName = BulkFileNames.safeFileName(requestedName)
        return duplicateSafeName(safeName) { candidate ->
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
                arrayOf(candidate, "${Environment.DIRECTORY_DOWNLOADS}/"),
                null
            )?.use { it.moveToFirst() } == true
        }
    }

    private fun duplicateSafeDocumentName(treeUri: Uri, requestedName: String): String {
        val safeName = BulkFileNames.safeFileName(requestedName)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val existing = mutableSetOf<String>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val column = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext() && column >= 0) {
                existing += cursor.getString(column)
            }
        }
        return duplicateSafeName(safeName, existing::contains)
    }

    private fun duplicateSafeName(safeName: String, exists: (String) -> Boolean): String {
        if (!exists(safeName)) return safeName
        val baseName = safeName.substringBeforeLast('.', safeName)
        val extension = safeName.substringAfterLast('.', "")
        var index = 1
        while (true) {
            val candidate = if (extension.isBlank()) {
                "$baseName ($index)"
            } else {
                "$baseName ($index).$extension"
            }
            if (!exists(candidate)) return candidate
            index++
        }
    }

    private fun fallbackToAppStorage(message: String): Destination {
        preferences.edit().putString(KEY_MODE, MODE_APP).apply()
        DiagnosticsLogger.log("file", message)
        return Destination(MODE_APP, "App storage")
    }

    private fun moveOrCopy(source: File, target: File) {
        if (source.renameTo(target)) return
        FileInputStream(source).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (!source.delete()) {
            DiagnosticsLogger.log(
                "file",
                "Unable to delete published partial file",
                mapOf("path" to source.absolutePath)
            )
        }
    }

    companion object {
        const val MODE_APP = "app"
        const val MODE_DOWNLOADS = "downloads"
        const val MODE_CUSTOM = "custom"

        private const val PREFERENCES_NAME = "wifi_direct_cable_prefs"
        private const val KEY_MODE = "receive.destination.mode"
        private const val KEY_CUSTOM_URI = "receive.destination.customUri"
        private const val KEY_CUSTOM_NAME = "receive.destination.customName"
    }
}
