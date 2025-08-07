package com.example.wifi_direct_cable

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import io.flutter.plugin.common.MethodChannel
import java.io.*
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class FileTransferService(
    private val activity: Activity,
    private val socketManager: SocketConnectionManager,
    private val methodChannel: MethodChannel
) {
    private var permissionManager: PermissionManager? = null
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val REQUEST_FILE_PICKER = 1002
    
    // File picker result callback
    private var filePickerResult: MethodChannel.Result? = null
    
    fun setPermissionManager(permissionManager: PermissionManager) {
        this.permissionManager = permissionManager
    }
    
    fun pickFile(result: MethodChannel.Result) {
        methodChannel.invokeMethod("onDebug", "FileTransfer: Starting file picker")
        
        // Check storage permissions before launching file picker
        if (permissionManager?.hasStoragePermission() != true) {
            methodChannel.invokeMethod("onDebug", "FileTransfer: Storage permission not granted, requesting permissions")
            permissionManager?.requestStoragePermissions()
            result.error("PERMISSION_DENIED", "Storage permission required to access files", null)
            return
        }
        
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
        
        val (isConnected, _, _) = socketManager.getConnectionState()
        methodChannel.invokeMethod("onDebug", "FileTransfer: Connection state - isConnected: $isConnected")
        if (!isConnected) {
            methodChannel.invokeMethod("onDebug", "FileTransfer: Error - Not connected to any peer")
            result.error("NOT_CONNECTED", "Not connected to any peer", null)
            return
        }
        
        executor.execute {
            try {
                val inputStream: InputStream
                val fileName: String
                val fileSize: Long
                
                if (filePath.startsWith("content://")) {
                    // Handle URI from file picker
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Processing content URI: $filePath")
                    }
                    val uri = Uri.parse(filePath)
                    inputStream = activity.contentResolver.openInputStream(uri)!!
                    fileName = getFileName(uri)
                    fileSize = getFileSize(uri)
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
                        mainHandler.post {
                            methodChannel.invokeMethod("onDebug", "FileTransfer: Error - File not found: $filePath")
                            result.error("FILE_NOT_FOUND", "File not found: $filePath", null)
                        }
                        return@execute
                    }
                    inputStream = FileInputStream(file)
                    fileName = file.name
                    fileSize = file.length()
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Regular file - name: $fileName, size: $fileSize bytes")
                        // Send file info back to Flutter
                        methodChannel.invokeMethod("onFileSendStarted", mapOf(
                            "fileName" to fileName,
                            "fileSize" to fileSize
                        ))
                    }
                }
                
                val socket = socketManager.getFileTransferSocket()
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "FileTransfer: Got socket - isNull: ${socket == null}, isClosed: ${socket?.isClosed}")
                }
                if (socket != null && !socket.isClosed) {
                    val output = socket.getOutputStream()
                    val (bufferSize, _, _) = socketManager.getTcpSettings()
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Using buffer size: $bufferSize bytes")
                    }
                    val buffer = ByteArray(bufferSize)
                    var totalBytes = 0L
                    
                    // Send file info first
                    val fileInfo = "FILE:${fileName}:$fileSize\n"
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Sending file header: $fileInfo")
                    }
                    output.write(fileInfo.toByteArray())
                    output.flush()
                    
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Starting file data transmission")
                    }
                    var bytesRead = inputStream.read(buffer)
                    var chunkCount = 0
                    while (bytesRead != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        chunkCount++
                        
                        val progress = (totalBytes.toDouble() / fileSize.toDouble())
                        
                        
                        mainHandler.post {
                            methodChannel.invokeMethod("onFileSendProgress", mapOf(
                                "fileName" to fileName,
                                "progress" to progress
                            ))
                        }
                        bytesRead = inputStream.read(buffer)
                    }
                    
                    inputStream.close()
                    output.flush()
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: File transmission completed - total chunks: $chunkCount, total bytes: $totalBytes")
                        result.success("File sent")
                    }
                } else {
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Error - No active file transfer connection")
                        result.error("CONNECTION_ERROR", "No active file transfer connection", null)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "FileTransfer: Exception during send - ${e.javaClass.simpleName}: ${e.message}")
                    result.error("SEND_ERROR", "Failed to send file: ${e.message}", null)
                    methodChannel.invokeMethod("onError", "Send file error: ${e.message}")
                }
                e.printStackTrace()
            }
        }
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
        var result: Long = 0
        if (uri.scheme == "content") {
            val cursor = activity.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (columnIndex >= 0) {
                        result = it.getLong(columnIndex)
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
    
    fun startFileTransferListener(socket: Socket?) {
        mainHandler.post {
            methodChannel.invokeMethod("onDebug", "FileTransfer: Starting file transfer listener")
        }
        thread {
            try {
                if (socket != null && !socket.isClosed) {
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - socket is valid, setting up input stream")
                    }
                    socket.soTimeout = 0 // Remove timeout for persistent connection
                    val input = socket.getInputStream()
                    val (bufferSize, _, _) = socketManager.getTcpSettings()
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - using buffer size: $bufferSize bytes")
                    }
                    val buffer = ByteArray(bufferSize)
                    
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - entering main receive loop")
                    }
                    var totalBytesReceived = 0L
                    var isReceivingFile = false
                    var currentFileName = ""
                    var expectedFileSize = 0L
                    var receivedFileData = 0L
                    var currentFileOutputStream: FileOutputStream? = null
                    var currentFilePath: String? = null
                    
                    var headerBuffer = ByteArray(0)
                    
                    while (!socket.isClosed && socket.isConnected) {
                        try {
                            val bytesRead = input.read(buffer)
                            if (bytesRead > 0) {
                                totalBytesReceived += bytesRead
                                
                                if (!isReceivingFile) {
                                    // Accumulate bytes until we find the complete header
                                    headerBuffer += buffer.sliceArray(0 until bytesRead)
                                    
                                    // Look for newline character to find end of header
                                    val newlineIndex = headerBuffer.indexOf(10) // 10 is ASCII for '\n'
                                    if (newlineIndex != -1) {
                                        // Extract header as string (only the header part)
                                        val headerBytes = headerBuffer.sliceArray(0 until newlineIndex)
                                        val header = String(headerBytes, Charsets.UTF_8)
                                        
                                        mainHandler.post {
                                            methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - received header: $header")
                                        }
                                        
                                        if (header.startsWith("FILE:")) {
                                            // Handle file transfer protocol
                                            val parts = header.split(":")
                                            if (parts.size >= 3) {
                                                currentFileName = parts[1]
                                                expectedFileSize = parts[2].toLongOrNull() ?: 0L
                                                isReceivingFile = true
                                                receivedFileData = 0L
                                                
                                                // Create file in Downloads directory
                                                try {
                                                    // Use app's private external storage directory for compatibility
                                                    // This doesn't require special permissions on Android 10+
                                                    val appExternalDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                                    if (appExternalDir != null) {
                                                        if (!appExternalDir.exists()) {
                                                            appExternalDir.mkdirs()
                                                        }
                                                        val file = File(appExternalDir, currentFileName)
                                                        currentFilePath = file.absolutePath
                                                        currentFileOutputStream = FileOutputStream(file)
                                                    } else {
                                                        // Fallback to internal storage if external is not available
                                                        val internalDir = File(activity.filesDir, "downloads")
                                                        if (!internalDir.exists()) {
                                                            internalDir.mkdirs()
                                                        }
                                                        val file = File(internalDir, currentFileName)
                                                        currentFilePath = file.absolutePath
                                                        currentFileOutputStream = FileOutputStream(file)
                                                    }
                                                    
                                                    mainHandler.post {
                                                        methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - created file: $currentFilePath")
                                                    }
                                                } catch (e: Exception) {
                                                    mainHandler.post {
                                                        methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - failed to create file: ${e.message}")
                                                        methodChannel.invokeMethod("onError", "Failed to create file: ${e.message}")
                                                    }
                                                    isReceivingFile = false
                                                    continue
                                                }
                                                
                                                mainHandler.post {
                                                    methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - starting file receive: $currentFileName, size: $expectedFileSize bytes")
                                                    methodChannel.invokeMethod("onFileReceiveStarted", mapOf(
                                                        "fileName" to currentFileName,
                                                        "fileSize" to expectedFileSize
                                                    ))
                                                }
                                                
                                                // Handle any file data that came after the header
                                                val remainingDataStart = newlineIndex + 1
                                                val remainingDataLength = headerBuffer.size - remainingDataStart
                                                if (remainingDataLength > 0) {
                                                    try {
                                                        val remainingData = headerBuffer.sliceArray(remainingDataStart until headerBuffer.size)
                                                        currentFileOutputStream?.write(remainingData)
                                                        receivedFileData += remainingDataLength
                                                        mainHandler.post {
                                                            methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - received $remainingDataLength bytes of file data with header")
                                                        }
                                                    } catch (e: Exception) {
                                                        mainHandler.post {
                                                            methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - failed to write file data: ${e.message}")
                                                        }
                                                    }
                                                }
                                                
                                                // Clear header buffer since we've processed it
                                                 headerBuffer = ByteArray(0)
                                             } else {
                                                 mainHandler.post {
                                                     methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - invalid header format: $header")
                                                 }
                                                 // Reset header buffer for invalid header
                                                 headerBuffer = ByteArray(0)
                                             }
                                         } else {
                                             mainHandler.post {
                                                 methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - header does not start with FILE: $header")
                                             }
                                             // Reset header buffer for non-file data
                                             headerBuffer = ByteArray(0)
                                         }
                                     } else {
                                         // Header not complete yet, check if buffer is getting too large
                                         if (headerBuffer.size > 1024) {
                                             mainHandler.post {
                                                 methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - header buffer too large, resetting")
                                             }
                                             headerBuffer = ByteArray(0)
                                         }
                                     }
                                } else {
                                    // Receiving file data
                                    try {
                                        currentFileOutputStream?.write(buffer, 0, bytesRead)
                                        receivedFileData += bytesRead
                                        val progress = if (expectedFileSize > 0) receivedFileData.toDouble() / expectedFileSize.toDouble() else 0.0
                                        
                                        mainHandler.post {
                                            methodChannel.invokeMethod("onFileReceiveProgress", mapOf(
                                                "fileName" to currentFileName,
                                                "progress" to progress
                                            ))
                                        }
                                        
                                        // Check if file is complete
                                        if (receivedFileData >= expectedFileSize) {
                                            currentFileOutputStream?.close()
                                            currentFileOutputStream = null
                                            
                                            mainHandler.post {
                                                methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - file receive completed: $currentFileName at $currentFilePath")
                                                methodChannel.invokeMethod("onFileReceived", mapOf(
                                                    "fileName" to currentFileName,
                                                    "filePath" to currentFilePath
                                                ))
                                            }
                                            isReceivingFile = false
                                        }
                                    } catch (e: Exception) {
                                        mainHandler.post {
                                            methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - failed to write file data: ${e.message}")
                                        }
                                        // Close file on error
                                        currentFileOutputStream?.close()
                                        currentFileOutputStream = null
                                        isReceivingFile = false
                                    }
                                }
                            } else if (bytesRead == -1) {
                                // Connection closed by server
                                mainHandler.post {
                                    methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - connection closed by peer")
                                }
                                break
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            // Continue listening, this is expected for persistent connections
                            continue
                        } catch (e: java.net.SocketException) {
                            // Socket connection error, attempt to reconnect
                            mainHandler.post {
                                methodChannel.invokeMethod("onDebug", "FileTransfer: Socket error: ${e.message}, attempting to reconnect...")
                            }
                            socketManager.retryConnectionIfNeeded("fileTransfer")
                            break
                        } catch (e: java.io.IOException) {
                            // IO error, attempt to reconnect
                            mainHandler.post {
                                methodChannel.invokeMethod("onDebug", "FileTransfer: IO error: ${e.message}, attempting to reconnect...")
                            }
                            socketManager.retryConnectionIfNeeded("fileTransfer")
                            break
                        }
                    }
                    
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - exited main loop, total bytes received: $totalBytesReceived")
                    }
                } else {
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - invalid socket: isNull=${socket == null}, isClosed=${socket?.isClosed}")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "FileTransfer: Listener - exception: ${e.javaClass.simpleName}: ${e.message}")
                    methodChannel.invokeMethod("onError", "File transfer listener error: ${e.message}")
                }
                // Attempt to reconnect on unexpected errors
                socketManager.retryConnectionIfNeeded("fileTransfer")
                e.printStackTrace()
            }
        }
    }
}