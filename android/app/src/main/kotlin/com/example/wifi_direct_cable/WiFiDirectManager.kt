package com.example.wifi_direct_cable

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.*
import android.net.wifi.WpsInfo
import android.os.Build
import androidx.core.app.ActivityCompat
import io.flutter.plugin.common.MethodChannel

class WiFiDirectManager(
    private val context: Context,
    private val methodChannel: MethodChannel
) {
    lateinit var wifiP2pManager: WifiP2pManager
    var channel: WifiP2pManager.Channel? = null
    private var latestPeersCount = 0
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
                methodChannel.invokeMethod("onDebug", "Peer discovery started")
            }
            
            override fun onFailure(reasonCode: Int) {
                val error = "Discovery failed: $reasonCode"
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
                methodChannel.invokeMethod("onDebug", "Connection initiated to $deviceAddress")
            }
            
            override fun onFailure(reasonCode: Int) {
                val error = "Connection failed: $reasonCode"
                result.error("CONNECTION_FAILED", error, null)
                methodChannel.invokeMethod("onError", error)
            }
        })
    }
    
    fun disconnect(result: MethodChannel.Result) {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success("Disconnected")
                methodChannel.invokeMethod("onDebug", "Disconnected from group")
            }
            
            override fun onFailure(reasonCode: Int) {
                result.success("Disconnected") // Still consider it success
                methodChannel.invokeMethod("onDebug", "Group removal failed: $reasonCode")
            }
        })
    }
    
    fun stopDiscovery(result: MethodChannel.Result) {
        wifiP2pManager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success("Discovery stopped")
            }
            
            override fun onFailure(reasonCode: Int) {
                result.error("STOP_DISCOVERY_FAILED", "Failed to stop discovery: $reasonCode", null)
            }
        })
    }
    
    fun resetWifiDirectSettings(result: MethodChannel.Result) {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                result.success("WiFi Direct settings reset")
                methodChannel.invokeMethod("onWifiDirectReset", null)
            }
            
            override fun onFailure(reasonCode: Int) {
                result.success("WiFi Direct settings reset") // Still consider success
                methodChannel.invokeMethod("onDebug", "WiFi Direct settings reset failed: $reasonCode")
                methodChannel.invokeMethod("onWifiDirectReset", null)
            }
        })
    }
    
    // Called from broadcast receiver
    fun handleConnectionInfoAvailable(info: WifiP2pInfo) {
        connectionListener?.onConnectionInfoAvailable(info)
    }
    
    // Called from broadcast receiver
    fun handlePeersAvailable(peers: WifiP2pDeviceList) {
        latestPeersCount = peers.deviceList.size
        connectionListener?.onPeersAvailable(peers)
    }
    
    // Called from broadcast receiver
    fun handleWifiP2pStateChanged(enabled: Boolean) {
        connectionListener?.onWifiP2pStateChanged(enabled)
    }
}
