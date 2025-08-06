package com.example.wifi_direct_cable

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("wifi_direct_cable_prefs", Context.MODE_PRIVATE)
    
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
            // SharedPreferences methods
            "setStringPreference" -> {
                val key = call.argument<String>("key")
                val value = call.argument<String>("value")
                setStringPreference(key, value, result)
            }
            "getStringPreference" -> {
                val key = call.argument<String>("key")
                val defaultValue = call.argument<String>("defaultValue")
                getStringPreference(key, defaultValue, result)
            }
            "setIntPreference" -> {
                val key = call.argument<String>("key")
                val value = call.argument<Int>("value")
                setIntPreference(key, value, result)
            }
            "getIntPreference" -> {
                val key = call.argument<String>("key")
                val defaultValue = call.argument<Int>("defaultValue")
                getIntPreference(key, defaultValue, result)
            }
            "setBoolPreference" -> {
                val key = call.argument<String>("key")
                val value = call.argument<Boolean>("value")
                setBoolPreference(key, value, result)
            }
            "getBoolPreference" -> {
                val key = call.argument<String>("key")
                val defaultValue = call.argument<Boolean>("defaultValue")
                getBoolPreference(key, defaultValue, result)
            }
            "setDoublePreference" -> {
                val key = call.argument<String>("key")
                val value = call.argument<Double>("value")
                setDoublePreference(key, value, result)
            }
            "getDoublePreference" -> {
                val key = call.argument<String>("key")
                val defaultValue = call.argument<Double>("defaultValue")
                getDoublePreference(key, defaultValue, result)
            }
            "removePreference" -> {
                val key = call.argument<String>("key")
                removePreference(key, result)
            }
            "clearPreferences" -> {
                clearPreferences(result)
            }
            "containsKey" -> {
                val key = call.argument<String>("key")
                containsKey(key, result)
            }
            "getKeys" -> {
                getKeys(result)
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
    
    // SharedPreferences implementation methods
    private fun setStringPreference(key: String?, value: String?, result: MethodChannel.Result) {
        if (key == null || value == null) {
            result.error("INVALID_ARGUMENTS", "Key and value cannot be null", null)
            return
        }
        try {
            val editor = sharedPreferences.edit()
            editor.putString(key, value)
            val success = editor.commit()
            result.success(success)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to set string preference: ${e.message}", null)
        }
    }
    
    private fun getStringPreference(key: String?, defaultValue: String?, result: MethodChannel.Result) {
        if (key == null) {
            result.error("INVALID_ARGUMENTS", "Key cannot be null", null)
            return
        }
        try {
            val value = sharedPreferences.getString(key, defaultValue)
            result.success(value)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to get string preference: ${e.message}", null)
        }
    }
    
    private fun setIntPreference(key: String?, value: Int?, result: MethodChannel.Result) {
        if (key == null || value == null) {
            result.error("INVALID_ARGUMENTS", "Key and value cannot be null", null)
            return
        }
        try {
            val editor = sharedPreferences.edit()
            editor.putInt(key, value)
            val success = editor.commit()
            result.success(success)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to set int preference: ${e.message}", null)
        }
    }
    
    private fun getIntPreference(key: String?, defaultValue: Int?, result: MethodChannel.Result) {
        if (key == null) {
            result.error("INVALID_ARGUMENTS", "Key cannot be null", null)
            return
        }
        try {
            val value = sharedPreferences.getInt(key, defaultValue ?: 0)
            result.success(value)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to get int preference: ${e.message}", null)
        }
    }
    
    private fun setBoolPreference(key: String?, value: Boolean?, result: MethodChannel.Result) {
        if (key == null || value == null) {
            result.error("INVALID_ARGUMENTS", "Key and value cannot be null", null)
            return
        }
        try {
            val editor = sharedPreferences.edit()
            editor.putBoolean(key, value)
            val success = editor.commit()
            result.success(success)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to set bool preference: ${e.message}", null)
        }
    }
    
    private fun getBoolPreference(key: String?, defaultValue: Boolean?, result: MethodChannel.Result) {
        if (key == null) {
            result.error("INVALID_ARGUMENTS", "Key cannot be null", null)
            return
        }
        try {
            val value = sharedPreferences.getBoolean(key, defaultValue ?: false)
            result.success(value)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to get bool preference: ${e.message}", null)
        }
    }
    
    private fun setDoublePreference(key: String?, value: Double?, result: MethodChannel.Result) {
        if (key == null || value == null) {
            result.error("INVALID_ARGUMENTS", "Key and value cannot be null", null)
            return
        }
        try {
            val editor = sharedPreferences.edit()
            editor.putFloat(key, value.toFloat())
            val success = editor.commit()
            result.success(success)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to set double preference: ${e.message}", null)
        }
    }
    
    private fun getDoublePreference(key: String?, defaultValue: Double?, result: MethodChannel.Result) {
        if (key == null) {
            result.error("INVALID_ARGUMENTS", "Key cannot be null", null)
            return
        }
        try {
            val value = sharedPreferences.getFloat(key, defaultValue?.toFloat() ?: 0.0f).toDouble()
            result.success(value)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to get double preference: ${e.message}", null)
        }
    }
    
    private fun removePreference(key: String?, result: MethodChannel.Result) {
        if (key == null) {
            result.error("INVALID_ARGUMENTS", "Key cannot be null", null)
            return
        }
        try {
            val editor = sharedPreferences.edit()
            editor.remove(key)
            val success = editor.commit()
            result.success(success)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to remove preference: ${e.message}", null)
        }
    }
    
    private fun clearPreferences(result: MethodChannel.Result) {
        try {
            val editor = sharedPreferences.edit()
            editor.clear()
            val success = editor.commit()
            result.success(success)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to clear preferences: ${e.message}", null)
        }
    }
    
    private fun containsKey(key: String?, result: MethodChannel.Result) {
        if (key == null) {
            result.error("INVALID_ARGUMENTS", "Key cannot be null", null)
            return
        }
        try {
            val contains = sharedPreferences.contains(key)
            result.success(contains)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to check key existence: ${e.message}", null)
        }
    }
    
    private fun getKeys(result: MethodChannel.Result) {
        try {
            val keys = sharedPreferences.all.keys.toList()
            result.success(keys)
        } catch (e: Exception) {
            result.error("PREFERENCE_ERROR", "Failed to get keys: ${e.message}", null)
        }
    }
}