package com.example.wifi_direct_cable

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.*
import android.net.wifi.WpsInfo
import android.os.Build
import androidx.core.app.ActivityCompat
import com.example.wifi_direct_cable.diagnostics.DiagnosticsLogger
import io.flutter.plugin.common.MethodChannel

class WiFiDirectManager(
    private val context: Context,
    private val methodChannel: MethodChannel
) {
    lateinit var wifiP2pManager: WifiP2pManager
    var channel: WifiP2pManager.Channel? = null
    private var latestPeersCount = 0
    private var latestConnectionInfo: Map<String, Any>? = null
    val discoveredDevicesCount: Int
        get() = latestPeersCount
    
    interface ConnectionListener {
        fun onConnectionInfoAvailable(info: WifiP2pInfo)
        fun onPeersAvailable(peers: WifiP2pDeviceList)
        fun onWifiP2pStateChanged(enabled: Boolean)
    }
    
    private var connectionListener: ConnectionListener? = null
    
    fun initialize(connectionListener: ConnectionListener) {
        this.connectionListener = connectionListener
        wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(context, context.mainLooper, null)
        DiagnosticsLogger.log("wifi", "Wi-Fi Direct manager initialized")
        methodChannel.invokeMethod("onDebug", "WiFi Direct manager initialized")
    }
    
    fun getManager(): WifiP2pManager = wifiP2pManager

    fun missingWifiDirectCapabilities(): List<String> {
        val missingCapabilities = mutableListOf<String>()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingCapabilities.add("Location")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            missingCapabilities.add("Nearby Wi-Fi devices")
        }

        return missingCapabilities
    }

    fun notifyPermissionDenied(capabilities: List<String>) {
        methodChannel.invokeMethod("onPermissionDenied", mapOf(
            "capabilities" to capabilities
        ))
    }
    
    fun discoverPeers(result: MethodChannel.Result) {
        val missingCapabilities = missingWifiDirectCapabilities()
        if (missingCapabilities.isNotEmpty()) {
            notifyPermissionDenied(missingCapabilities)
            result.error("PERMISSION_DENIED", "${missingCapabilities.joinToString(", ")} permission required", null)
            return
        }
        
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success("Discovery started")
                DiagnosticsLogger.log("wifi", "Peer discovery started")
                methodChannel.invokeMethod("onDebug", "Peer discovery started")
            }
            
            override fun onFailure(reasonCode: Int) {
                val error = "Discovery failed: $reasonCode"
                DiagnosticsLogger.log("wifi", "Peer discovery failed", mapOf("reasonCode" to reasonCode))
                result.error("DISCOVERY_FAILED", error, null)
                methodChannel.invokeMethod("onError", error)
            }
        })
    }
    
    fun connectToPeer(deviceAddress: String?, result: MethodChannel.Result) {
        if (deviceAddress == null) {
            result.error("INVALID_ARGUMENT", "Device address is required", null)
            return
        }
        
        val missingCapabilities = missingWifiDirectCapabilities()
        if (missingCapabilities.isNotEmpty()) {
            notifyPermissionDenied(missingCapabilities)
            result.error("PERMISSION_DENIED", "${missingCapabilities.joinToString(", ")} permission required", null)
            return
        }
        
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            wps.setup = WpsInfo.PBC
        }
        
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success("Connection initiated")
                DiagnosticsLogger.log("wifi", "Connection initiated", mapOf("deviceAddress" to deviceAddress))
                methodChannel.invokeMethod("onDebug", "Connection initiated to $deviceAddress")
            }
            
            override fun onFailure(reasonCode: Int) {
                val error = "Connection failed: $reasonCode"
                DiagnosticsLogger.log("wifi", "Connection failed", mapOf("deviceAddress" to deviceAddress, "reasonCode" to reasonCode))
                result.error("CONNECTION_FAILED", error, null)
                methodChannel.invokeMethod("onError", error)
            }
        })
    }
    
    fun disconnect(result: MethodChannel.Result) {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success("Disconnected")
                DiagnosticsLogger.log("wifi", "Disconnected from group")
                methodChannel.invokeMethod("onDebug", "Disconnected from group")
            }
            
            override fun onFailure(reasonCode: Int) {
                result.success("Disconnected") // Still consider it success
                DiagnosticsLogger.log("wifi", "Group removal failed", mapOf("reasonCode" to reasonCode))
                methodChannel.invokeMethod("onDebug", "Group removal failed: $reasonCode")
            }
        })
    }
    
    fun stopDiscovery(result: MethodChannel.Result) {
        wifiP2pManager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success("Discovery stopped")
                DiagnosticsLogger.log("wifi", "Peer discovery stopped")
            }
            
            override fun onFailure(reasonCode: Int) {
                result.error("STOP_DISCOVERY_FAILED", "Failed to stop discovery: $reasonCode", null)
                DiagnosticsLogger.log("wifi", "Stop discovery failed", mapOf("reasonCode" to reasonCode))
            }
        })
    }
    
    fun resetWifiDirectSettings(result: MethodChannel.Result) {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success("WiFi Direct settings reset")
                DiagnosticsLogger.log("wifi", "Wi-Fi Direct settings reset")
                methodChannel.invokeMethod("onWifiDirectReset", null)
            }
            
            override fun onFailure(reasonCode: Int) {
                result.success("WiFi Direct settings reset") // Still consider success
                DiagnosticsLogger.log("wifi", "Wi-Fi Direct reset failed", mapOf("reasonCode" to reasonCode))
                methodChannel.invokeMethod("onDebug", "WiFi Direct settings reset failed: $reasonCode")
                methodChannel.invokeMethod("onWifiDirectReset", null)
            }
        })
    }

    fun requestConnectionInfoOnce(
        reason: String,
        dispatchToListener: Boolean,
        callback: ((WifiP2pInfo?) -> Unit)? = null
    ) {
        val missingCapabilities = missingWifiDirectCapabilities()
        if (missingCapabilities.isNotEmpty()) {
            notifyPermissionDenied(missingCapabilities)
            DiagnosticsLogger.log(
                "wifi",
                "One-shot connection info skipped because permissions are missing",
                mapOf("reason" to reason, "capabilities" to missingCapabilities.joinToString(","))
            )
            callback?.invoke(null)
            return
        }

        try {
            wifiP2pManager.requestConnectionInfo(channel) { info ->
                DiagnosticsLogger.log(
                    "wifi",
                    "One-shot connection info received",
                    mapOf(
                        "reason" to reason,
                        "groupFormed" to info.groupFormed,
                        "isGroupOwner" to info.isGroupOwner,
                        "groupOwnerAddress" to (info.groupOwnerAddress?.hostAddress ?: "")
                    )
                )
                if (dispatchToListener) {
                    handleConnectionInfoAvailable(info)
                } else {
                    latestConnectionInfo = connectionInfoMap(info)
                }
                callback?.invoke(info)
            }
        } catch (exception: Exception) {
            DiagnosticsLogger.log(
                "wifi",
                "One-shot connection info failed",
                mapOf("reason" to reason, "error" to exception.message)
            )
            callback?.invoke(null)
        }
    }

    fun replayLatestConnectionInfo() {
        latestConnectionInfo?.let { connectionInfo ->
            methodChannel.invokeMethod("onConnectionChanged", connectionInfo)
        }
    }
    
    // Called from broadcast receiver
    fun handleConnectionInfoAvailable(info: WifiP2pInfo) {
        latestConnectionInfo = connectionInfoMap(info)
        connectionListener?.onConnectionInfoAvailable(info)
    }
    
    // Called from broadcast receiver
    fun handlePeersAvailable(peers: WifiP2pDeviceList) {
        latestPeersCount = peers.deviceList.size
        DiagnosticsLogger.log("wifi", "Peers available", mapOf("count" to latestPeersCount))
        connectionListener?.onPeersAvailable(peers)
    }
    
    // Called from broadcast receiver
    fun handleWifiP2pStateChanged(enabled: Boolean) {
        DiagnosticsLogger.log("wifi", "Wi-Fi P2P state changed", mapOf("enabled" to enabled))
        if (!enabled) {
            WdCableRuntime.handleWifiP2pStateChanged(enabled)
        }
        connectionListener?.onWifiP2pStateChanged(enabled)
    }

    private fun connectionInfoMap(info: WifiP2pInfo): Map<String, Any> {
        return mapOf(
            "isConnected" to info.groupFormed,
            "isGroupOwner" to info.isGroupOwner,
            "groupOwnerAddress" to (info.groupOwnerAddress?.hostAddress ?: "")
        )
    }
}
