package com.jingcjie.wifi_direct_cable

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.jingcjie.wifi_direct_cable.diagnostics.DiagnosticsLogger
import com.jingcjie.wifi_direct_cable.session.FileTransferCancelledException
import com.jingcjie.wifi_direct_cable.session.SessionManager
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FileTransferService(
    private val activity: Activity,
    private val sessionManager: SessionManager,
    private val methodChannel: MethodChannel
) {
    private class OutgoingTransferJob(
        val transferId: String,
        val filePath: String,
        val requestedName: String
    ) {
        val cancelled = AtomicBoolean(false)
        val started = AtomicBoolean(false)
        val terminal = AtomicBoolean(false)

        @Volatile
        var inputStream: InputStream? = null

        @Volatile
        var fileName: String = requestedName

        @Volatile
        var fileSize: Long = -1L

        @Volatile
        var bytesTransferred: Long = 0L
    }

    private val mainThreadDispatcher = MainThreadDispatcher()
    private val outgoingExecutor = Executors.newSingleThreadExecutor()
    private val outgoingJobs = ConcurrentHashMap<String, OutgoingTransferJob>()
    private var filePickerResult: MethodChannel.Result? = null
    private var customFolderResult: MethodChannel.Result? = null

    fun pickFile(result: MethodChannel.Result) {
        if (filePickerResult != null) {
            result.error("PICKER_BUSY", "A file picker is already open", null)
            return
        }
        filePickerResult = result
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivityForResult(
            Intent.createChooser(intent, "Select File"),
            REQUEST_FILE_PICKER
        )
    }

    fun pickCustomReceiveDestination(result: MethodChannel.Result) {
        if (customFolderResult != null) {
            result.error("PICKER_BUSY", "A folder picker is already open", null)
            return
        }
        customFolderResult = result
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        activity.startActivityForResult(intent, REQUEST_CUSTOM_FOLDER)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_FILE_PICKER -> handleFilePickerResult(resultCode, data)
            REQUEST_CUSTOM_FOLDER -> handleCustomFolderResult(resultCode, data)
        }
    }

    fun startFileTransfer(
        transferId: String?,
        filePath: String?,
        requestedName: String?,
        result: MethodChannel.Result
    ) {
        if (transferId.isNullOrBlank() || filePath.isNullOrBlank()) {
            result.error("INVALID_ARGUMENT", "Transfer ID and file path are required", null)
            return
        }
        if (!sessionManager.isReady()) {
            result.error("SESSION_NOT_READY", "The WDCable session is not ready", null)
            return
        }

        val job = OutgoingTransferJob(
            transferId = transferId,
            filePath = filePath,
            requestedName = requestedName?.takeIf { it.isNotBlank() } ?: "unknown_file"
        )
        if (outgoingJobs.putIfAbsent(transferId, job) != null) {
            result.error("DUPLICATE_TRANSFER", "Transfer ID is already active", null)
            return
        }

        emitStarted(job, status = "queued")
        outgoingExecutor.execute { runOutgoingTransfer(job) }
        result.success(mapOf("transferId" to transferId, "status" to "queued"))
    }

    fun cancelFileTransfer(transferId: String?, result: MethodChannel.Result) {
        if (transferId.isNullOrBlank()) {
            result.error("INVALID_ARGUMENT", "Transfer ID is required", null)
            return
        }
        val job = outgoingJobs[transferId]
        if (job == null) {
            result.success(false)
            return
        }

        job.cancelled.set(true)
        try {
            job.inputStream?.close()
        } catch (_: Exception) {
        }

        if (!job.started.get()) {
            finishCancelled(job, "user_cancelled")
        }
        result.success(true)
    }

    fun cancelAll(reason: String) {
        outgoingJobs.values.toList().forEach { job ->
            job.cancelled.set(true)
            try {
                job.inputStream?.close()
            } catch (_: Exception) {
            }
            if (!job.started.get()) {
                finishCancelled(job, reason)
            }
        }
    }

    fun cleanup() {
        cancelAll("app_destroyed")
        outgoingExecutor.shutdownNow()
    }

    private fun runOutgoingTransfer(job: OutgoingTransferJob) {
        if (job.terminal.get()) return
        job.started.set(true)
        if (job.cancelled.get()) {
            finishCancelled(job, "user_cancelled")
            return
        }

        try {
            val source = openSource(job.filePath, job.requestedName)
            job.fileName = source.fileName
            job.fileSize = source.fileSize
            job.inputStream = source.inputStream
            emitStarted(job, status = "transferring")

            val sendResult = source.inputStream.use { input ->
                sessionManager.sendFileStream(
                    transferId = job.transferId,
                    fileName = job.fileName,
                    sizeBytes = job.fileSize,
                    inputStream = input,
                    shouldCancel = job.cancelled::get
                ) { bytesTransferred ->
                    job.bytesTransferred = bytesTransferred
                    emitProgress(job)
                }
            }
            job.fileName = sendResult.fileName
            job.bytesTransferred = sendResult.bytesSent
            if (job.fileSize < 0) {
                job.fileSize = sendResult.bytesSent
            }
            finishCompleted(job)
        } catch (exception: FileTransferCancelledException) {
            job.bytesTransferred = exception.bytesTransferred
            finishCancelled(job, exception.message ?: "cancelled")
        } catch (exception: Exception) {
            if (job.cancelled.get()) {
                finishCancelled(job, "user_cancelled")
            } else {
                finishFailed(job, exception.message ?: "File transfer failed")
            }
        } finally {
            job.inputStream = null
        }
    }

    private fun openSource(filePath: String, requestedName: String): SourceFile {
        if (filePath.startsWith("content://")) {
            val uri = Uri.parse(filePath)
            val fileName = getFileName(uri).takeIf { it.isNotBlank() } ?: requestedName
            val fileSize = getFileSize(uri)
            val input = activity.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open selected file")
            return SourceFile(fileName, fileSize, input)
        }

        val file = File(filePath)
        if (!file.exists()) throw FileNotFoundException("File not found: $filePath")
        return SourceFile(file.name, file.length(), FileInputStream(file))
    }

    private fun handleFilePickerResult(resultCode: Int, data: Intent?) {
        val result = filePickerResult ?: return
        filePickerResult = null
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            result.success(null)
            return
        }
        val uri = data.data!!
        result.success(mapOf("path" to uri.toString(), "name" to getFileName(uri)))
    }

    private fun handleCustomFolderResult(resultCode: Int, data: Intent?) {
        val result = customFolderResult ?: return
        customFolderResult = null
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            result.success(null)
            return
        }

        val uri = data.data!!
        try {
            val flags = data.flags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            if (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0) {
                throw IOException("Selected folder did not grant write access")
            }
            activity.contentResolver.takePersistableUriPermission(uri, flags)
            val displayName = getFolderName(uri)
            val destination = sessionManager.saveCustomReceiveDestination(uri, displayName)
            DiagnosticsLogger.log(
                "file",
                "Custom receive folder selected",
                mapOf(
                    "uri" to uri.toString(),
                    "displayName" to displayName,
                    "flags" to flags
                )
            )
            result.success(destination.toMap())
        } catch (exception: Exception) {
            DiagnosticsLogger.log(
                "file",
                "Custom receive folder rejected",
                mapOf(
                    "uri" to uri.toString(),
                    "errorType" to exception.javaClass.simpleName,
                    "error" to exception.message
                )
            )
            result.error(
                "FOLDER_NOT_WRITABLE",
                exception.message ?: "Selected folder is not writable",
                null
            )
        }
    }

    private fun finishCompleted(job: OutgoingTransferJob) {
        if (!job.terminal.compareAndSet(false, true)) return
        outgoingJobs.remove(job.transferId, job)
        emitTerminal("onFileTransferCompleted", job, status = "completed")
        DiagnosticsLogger.log(
            "file",
            "File transfer completed",
            mapOf("transferId" to job.transferId, "fileName" to job.fileName)
        )
    }

    private fun finishCancelled(job: OutgoingTransferJob, reason: String) {
        if (!job.terminal.compareAndSet(false, true)) return
        outgoingJobs.remove(job.transferId, job)
        emitTerminal(
            "onFileTransferCancelled",
            job,
            status = "cancelled",
            error = reason
        )
        DiagnosticsLogger.log(
            "file",
            "File transfer cancelled",
            mapOf("transferId" to job.transferId, "reason" to reason)
        )
    }

    private fun finishFailed(job: OutgoingTransferJob, error: String) {
        if (!job.terminal.compareAndSet(false, true)) return
        outgoingJobs.remove(job.transferId, job)
        emitTerminal("onFileTransferFailed", job, status = "failed", error = error)
        DiagnosticsLogger.log(
            "file",
            "File transfer failed",
            mapOf("transferId" to job.transferId, "error" to error)
        )
    }

    private fun emitStarted(job: OutgoingTransferJob, status: String) {
        emit(
            "onFileTransferStarted",
            eventData(job, status = status)
        )
    }

    private fun emitProgress(job: OutgoingTransferJob) {
        emit(
            "onFileTransferProgress",
            eventData(job, status = "transferring")
        )
    }

    private fun emitTerminal(
        method: String,
        job: OutgoingTransferJob,
        status: String,
        error: String? = null
    ) {
        emit(method, eventData(job, status = status, error = error))
    }

    private fun eventData(
        job: OutgoingTransferJob,
        status: String,
        error: String? = null
    ): Map<String, Any?> = mapOf(
        "transferId" to job.transferId,
        "direction" to "send",
        "fileName" to job.fileName,
        "fileSize" to job.fileSize,
        "bytesTransferred" to job.bytesTransferred,
        "status" to status,
        "filePath" to job.filePath,
        "savedLocation" to sourceLocation(job.filePath),
        "error" to error
    )

    private fun sourceLocation(filePath: String): String {
        if (filePath.startsWith("content://")) {
            return "Selected file location"
        }
        return File(filePath).parentFile?.absolutePath ?: filePath
    }

    private fun emit(method: String, data: Map<String, Any?>) {
        mainThreadDispatcher.dispatch { methodChannel.invokeMethod(method, data) }
    }

    private fun getFileName(uri: Uri): String {
        activity.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "unknown_file"
    }

    private fun getFileSize(uri: Uri): Long {
        activity.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
            }
        }
        return -1L
    }

    private fun getFolderName(uri: Uri): String {
        try {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri)
            )
            activity.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )
                    if (index >= 0 && !cursor.isNull(index)) {
                        return cursor.getString(index)
                    }
                }
            }
        } catch (exception: Exception) {
            DiagnosticsLogger.log(
                "file",
                "Could not resolve custom folder display name",
                mapOf("uri" to uri.toString(), "error" to exception.message)
            )
        }
        return try {
            DocumentsContract.getTreeDocumentId(uri)
                .substringAfterLast(':')
                .substringAfterLast('/')
                .ifBlank { "Custom folder" }
        } catch (_: Exception) {
            uri.lastPathSegment ?: "Custom folder"
        }
    }

    private data class SourceFile(
        val fileName: String,
        val fileSize: Long,
        val inputStream: InputStream
    )

    companion object {
        private const val REQUEST_FILE_PICKER = 1002
        private const val REQUEST_CUSTOM_FOLDER = 1004
    }
}
