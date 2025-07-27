package com.example.wifi_direct_cable

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.*
import android.net.wifi.WpsInfo
import androidx.core.app.ActivityCompat
import io.flutter.plugin.common.MethodChannel

class WiFiDirectManager(
    private val context: Context,
    private val methodChannel: MethodChannel
) {
    lateinit var wifiP2pManager: WifiP2pManager
    var channel: WifiP2pManager.Channel? = null
    
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
        // Reset any existing WiFi Direct connections and settings
        resetWifiDirectSettings(object : MethodChannel.Result {
            override fun success(result: Any?) {
                methodChannel.invokeMethod("onDebug", "WiFi Direct settings reset during initialization")
            }
            override fun error(code: String, message: String?, details: Any?) {
                methodChannel.invokeMethod("onError", "Failed to reset WiFi Direct settings: $message")
            }
            override fun notImplemented() {}
        })
        
    }
    
    fun getManager(): WifiP2pManager = wifiP2pManager
    
    fun discoverPeers(result: MethodChannel.Result) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            result.error("PERMISSION_DENIED", "Location permission required", null)
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
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            result.error("PERMISSION_DENIED", "Location permission required", null)
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
        connectionListener?.onPeersAvailable(peers)
    }
    
    // Called from broadcast receiver
    fun handleWifiP2pStateChanged(enabled: Boolean) {
        connectionListener?.onWifiP2pStateChanged(enabled)
    }
}