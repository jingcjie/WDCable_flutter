package com.example.wifi_direct_cable

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wifi_direct_cable.diagnostics.DiagnosticsLogger
import io.flutter.plugin.common.MethodChannel

class PermissionManager(
    private val activity: Activity,
    private val methodChannel: MethodChannel
) {
    private val REQUEST_PERMISSIONS = 1001
    
    fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
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
    
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasNearbyWifiDevicesPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on older versions
        }
    }

    private fun permissionToCapability(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
            Manifest.permission.NEARBY_WIFI_DEVICES -> "Nearby Wi-Fi devices"
            else -> permission
        }
    }
}
