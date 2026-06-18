package com.jingcjie.wifi_direct_cable

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jingcjie.wifi_direct_cable.diagnostics.DiagnosticsLogger
import io.flutter.plugin.common.MethodChannel

class PermissionManager(
    private val activity: Activity,
    private val methodChannel: MethodChannel
) {
    private val REQUEST_PERMISSIONS = 1001
    private val REQUEST_RECORD_AUDIO = 1003

    private var recordAudioCallback: ((Boolean) -> Unit)? = null
    
    fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        
        if (permissions.isNotEmpty()) {
            DiagnosticsLogger.log(
                "permissions",
                "Requesting permissions",
                mapOf("permissions" to permissions.joinToString(","))
            )
            ActivityCompat.requestPermissions(activity, permissions.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            DiagnosticsLogger.log("permissions", "Required permissions already granted")
        }
    }
    
    fun handlePermissionResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            DiagnosticsLogger.log(
                "permissions",
                if (granted) "Record audio permission granted" else "Record audio permission denied"
            )
            if (!granted) {
                methodChannel.invokeMethod("onPermissionDenied", mapOf(
                    "permissions" to listOf(Manifest.permission.RECORD_AUDIO),
                    "capabilities" to listOf("Microphone")
                ))
            }
            recordAudioCallback?.invoke(granted)
            recordAudioCallback = null
            return
        }

        if (requestCode == REQUEST_PERMISSIONS) {
            val deniedPermissions = permissions.filterIndexed { index, _ ->
                index >= grantResults.size || grantResults[index] != PackageManager.PERMISSION_GRANTED
            }
            if (deniedPermissions.isNotEmpty()) {
                DiagnosticsLogger.log(
                    "permissions",
                    "Permissions denied",
                    mapOf("permissions" to deniedPermissions.joinToString(","))
                )
                methodChannel.invokeMethod("onPermissionDenied", mapOf(
                    "permissions" to deniedPermissions,
                    "capabilities" to deniedPermissions.map { permissionToCapability(it) }
                ))
            } else {
                DiagnosticsLogger.log("permissions", "Permissions granted")
            }
        }
    }
    
    fun hasNearbyWifiDevicesPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasWifiDirectRuntimePermissions(): Boolean {
        return hasNearbyWifiDevicesPermission()
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun requestRecordAudioPermission(callback: (Boolean) -> Unit) {
        if (hasRecordAudioPermission()) {
            callback(true)
            return
        }

        recordAudioCallback = callback
        DiagnosticsLogger.log("permissions", "Requesting record audio permission")
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }

    private fun permissionToCapability(permission: String): String {
        return when (permission) {
            Manifest.permission.NEARBY_WIFI_DEVICES -> "Nearby Wi-Fi devices"
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            else -> permission
        }
    }
}
