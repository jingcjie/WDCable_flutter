package com.example.wifi_direct_cable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File

class FlutterMethodChannelHandler(
    private val context: Context,
    private val methodChannel: MethodChannel,
    private val wifiDirectManager: WiFiDirectManager,
    private val socketManager: SocketConnectionManager,
    private val chatService: ChatService,
    private val speedTestService: SpeedTestService,
    private val fileTransferService: FileTransferService,
    private val permissionManager: PermissionManager
) : MethodChannel.MethodCallHandler {
    
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "discoverPeers" -> {
                wifiDirectManager.discoverPeers(result)
            }
            "connectToPeer" -> {
                val deviceAddress = call.argument<String>("deviceAddress")
                wifiDirectManager.connectToPeer(deviceAddress, result)
            }
            "disconnect" -> {
                socketManager.cleanup()
                wifiDirectManager.disconnect(result)
            }
            "sendData" -> {
                val data = call.argument<String>("data")
                chatService.sendData(data, result)
            }

            "sendFileStream" -> {
                val filePath = call.argument<String>("filePath")
                fileTransferService.sendFileStream(filePath, result)
            }
            "configureTcpSettings" -> {
                val bufferSize = call.argument<Int>("bufferSize") ?: 8192
                val timeout = call.argument<Int>("timeout") ?: 30000
                val keepAlive = call.argument<Boolean>("keepAlive") ?: true
                socketManager.configureTcpSettings(bufferSize, timeout, keepAlive)
                result.success("TCP settings configured")
            }
            "getConnectionStats" -> {
                val stats = socketManager.getConnectionStats()
                result.success(stats)
            }
            "getDeviceSettings" -> {
                getDeviceSettings(result)
            }
            "isWifiP2pEnabled" -> {
                isWifiP2pEnabled(result)
            }
            "getDiscoveryStatus" -> {
                getDiscoveryStatus(result)
            }
            "stopDiscovery" -> {
                wifiDirectManager.stopDiscovery(result)
            }
            "resetWifiDirectSettings" -> {
                socketManager.cleanup()
                wifiDirectManager.resetWifiDirectSettings(result)
            }
            "setSpeedTesting" -> {
                val enabled = call.argument<Boolean>("enabled") ?: false
                speedTestService.setSpeedTesting(enabled)
                result.success("Speed testing set to $enabled")
            }

            "requestSpeedTestData" -> {
                val sizeBytes = (call.argument<Any>("sizeBytes") as? Number)?.toLong() ?: 1048576L
                speedTestService.requestSpeedTestData(sizeBytes, result)
            }
            "sendSpeedTestData" -> {
                val sizeBytes = (call.argument<Any>("sizeBytes") as? Number)?.toLong() ?: 1048576L
                speedTestService.sendSpeedTestData(sizeBytes, result)
            }
            "pickFile" -> {
                fileTransferService.pickFile(result)
            }
            "openFile" -> {
                val filePath = call.argument<String>("filePath")
                openFile(filePath, result)
            }
            else -> {
                result.notImplemented()
            }
        }
    }
    
    private fun getDeviceSettings(result: MethodChannel.Result) {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val settings = mapOf(
            "wifiEnabled" to wifiManager.isWifiEnabled,
            "deviceName" to Build.MODEL,
            "deviceAddress" to "Unknown" // P2P device address is not easily accessible
        )
        result.success(settings)
    }
    
    private fun isWifiP2pEnabled(result: MethodChannel.Result) {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        result.success(wifiManager.isWifiEnabled)
    }
    
    private fun getDiscoveryStatus(result: MethodChannel.Result) {
        val status = mapOf(
            "isDiscovering" to true, // We don't have direct access to discovery state
            "peersCount" to 0 // This would be updated via broadcast receiver
        )
        result.success(status)
    }
    
    private fun openFile(filePath: String?, result: MethodChannel.Result) {
        if (filePath == null) {
            result.error("INVALID_PATH", "File path cannot be null", null)
            return
        }
        
        // Check storage permissions before opening file
        if (!permissionManager.hasStoragePermission()) {
            permissionManager.requestStoragePermissions()
            result.error("PERMISSION_DENIED", "Storage permission required to open files", null)
            return
        }
        
        try {
            val uri: Uri
            val mimeType: String
            
            if (filePath.startsWith("content://")) {
                // Handle content URI
                uri = Uri.parse(filePath)
                mimeType = context.contentResolver.getType(uri) ?: "*/*"
            } else {
                // Handle regular file path
                val file = File(filePath)
                if (!file.exists()) {
                    result.error("FILE_NOT_FOUND", "File does not exist: $filePath", null)
                    return
                }
                
                uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                
                mimeType = getMimeType(file.absolutePath) ?: "*/*"
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Check if there's an app that can handle this intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                result.success("File opened successfully")
            } else {
                result.error("NO_APP_FOUND", "No app found to open this file type", null)
            }
            
        } catch (e: Exception) {
            result.error("OPEN_FILE_ERROR", "Failed to open file: ${e.message}", null)
        }
    }
    
    private fun getMimeType(filePath: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(filePath)
        return if (extension != null) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        } else {
            null
        }
    }
}