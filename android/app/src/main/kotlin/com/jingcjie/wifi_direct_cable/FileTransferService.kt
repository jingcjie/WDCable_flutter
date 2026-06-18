package com.jingcjie.wifi_direct_cable

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import com.jingcjie.wifi_direct_cable.diagnostics.DiagnosticsLogger
import com.jingcjie.wifi_direct_cable.session.SessionManager
import io.flutter.plugin.common.MethodChannel
import java.io.*

class FileTransferService(
    private val activity: Activity,
    private val sessionManager: SessionManager,
    private val methodChannel: MethodChannel
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val REQUEST_FILE_PICKER = 1002
    
    // File picker result callback
    private var filePickerResult: MethodChannel.Result? = null
    
    fun pickFile(result: MethodChannel.Result) {
        methodChannel.invokeMethod("onDebug", "FileTransfer: Starting file picker")
        
        filePickerResult = result
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        activity.startActivityForResult(Intent.createChooser(intent, "Select File"), REQUEST_FILE_PICKER)
        methodChannel.invokeMethod("onDebug", "FileTransfer: File picker intent launched")
    }
    
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_FILE_PICKER) {
            methodChannel.invokeMethod("onDebug", "FileTransfer: File picker result - requestCode: $requestCode, resultCode: $resultCode")
            if (resultCode == Activity.RESULT_OK && data != null) {
                val uri = data.data
                if (uri != null) {
                    val fileName = getFileName(uri)
                    methodChannel.invokeMethod("onDebug", "FileTransfer: File selected - URI: $uri, fileName: $fileName")
                    filePickerResult?.success(mapOf(
                        "path" to uri.toString(),
                        "name" to fileName
                    ))
                } else {
                    methodChannel.invokeMethod("onDebug", "FileTransfer: File picker error - URI is null")
                    filePickerResult?.error("FILE_PICKER_ERROR", "No file selected", null)
                }
            } else {
                methodChannel.invokeMethod("onDebug", "FileTransfer: File picker cancelled or failed")
                filePickerResult?.error("FILE_PICKER_CANCELLED", "File picker cancelled", null)
            }
            filePickerResult = null
        }
    }
    
    fun sendFileStream(filePath: String?, result: MethodChannel.Result) {
        methodChannel.invokeMethod("onDebug", "FileTransfer: Starting sendFileStream with path: $filePath")
        
        if (filePath == null) {
            methodChannel.invokeMethod("onDebug", "FileTransfer: Error - File path is null")
            result.error("INVALID_ARGUMENT", "File path is required", null)
            return
        }

        Thread {
            try {
                val fileName: String
                val fileSize: Long
                val inputStream: InputStream
                
                if (filePath.startsWith("content://")) {
                    // Handle URI from file picker
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Processing content URI: $filePath")
                    }
                    val uri = Uri.parse(filePath)
                    fileName = getFileName(uri)
                    fileSize = getFileSize(uri)
                    inputStream = activity.contentResolver.openInputStream(uri)
                        ?: throw IOException("Unable to open selected file")
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: URI file - name: $fileName, size: $fileSize bytes")
                        // Send file info back to Flutter
                        methodChannel.invokeMethod("onFileSendStarted", mapOf(
                            "fileName" to fileName,
                            "fileSize" to fileSize
                        ))
                    }
                } else {
                    // Handle regular file path
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Processing regular file path: $filePath")
                    }
                    val file = File(filePath)
                    if (!file.exists()) {
                        throw FileNotFoundException("File not found: $filePath")
                    }
                    fileName = file.name
                    fileSize = file.length()
                    inputStream = FileInputStream(file)
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Regular file - name: $fileName, size: $fileSize bytes")
                        // Send file info back to Flutter
                        methodChannel.invokeMethod("onFileSendStarted", mapOf(
                            "fileName" to fileName,
                            "fileSize" to fileSize
                        ))
                    }
                }

                DiagnosticsLogger.log(
                    "file",
                    "File send requested",
                    mapOf("fileName" to fileName, "fileSize" to fileSize)
                )
                sessionManager.sendFileStream(fileName, fileSize, inputStream, result)
            } catch (e: Exception) {
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "FileTransfer: Exception during send - ${e.javaClass.simpleName}: ${e.message}")
                    result.error("SEND_ERROR", "Failed to send file: ${e.message}", null)
                    methodChannel.invokeMethod("onError", "Send file error: ${e.message}")
                }
                e.printStackTrace()
            }
        }.start()
    }
    
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            mainHandler.post {
                methodChannel.invokeMethod("onDebug", "FileTransfer: URI is content scheme, querying content resolver")
            }
            val cursor = activity.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex >= 0) {
                        result = it.getString(columnIndex)
                        mainHandler.post {
                            methodChannel.invokeMethod("onDebug", "FileTransfer: Got file name from content resolver: $result")
                        }
                    }
                }
            }
        }
        if (result == null) {
            mainHandler.post {
                methodChannel.invokeMethod("onDebug", "FileTransfer: Extracting file name from URI path")
            }
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
            mainHandler.post {
                methodChannel.invokeMethod("onDebug", "FileTransfer: Extracted file name from path: $result")
            }
        }
        val finalResult = result ?: "unknown_file"
        mainHandler.post {
            methodChannel.invokeMethod("onDebug", "FileTransfer: Final file name: $finalResult")
        }
        return finalResult
    }
    
    private fun getFileSize(uri: Uri): Long {
        var result: Long = -1
        if (uri.scheme == "content") {
            val cursor = activity.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (columnIndex >= 0) {
                        result = if (it.isNull(columnIndex)) -1 else it.getLong(columnIndex)
                    } else {
                        mainHandler.post {
                            methodChannel.invokeMethod("onDebug", "FileTransfer: SIZE column not found in content resolver")
                        }
                    }
                } else {
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Content resolver cursor is empty")
                    }
                }
            }
        } else {
            mainHandler.post {
                methodChannel.invokeMethod("onDebug", "FileTransfer: URI is not content scheme, cannot determine size")
            }
        }
        mainHandler.post {
            methodChannel.invokeMethod("onDebug", "FileTransfer: Final file size: $result bytes")
        }
        return result
    }
    
}
