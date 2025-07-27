package com.example.wifi_direct_cable

import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity(), WiFiDirectManager.ConnectionListener {
    private val CHANNEL = "wifi_direct_cable"
    
    private lateinit var methodChannel: MethodChannel
    private lateinit var wifiDirectManager: WiFiDirectManager
    private lateinit var socketManager: SocketConnectionManager
    private lateinit var chatService: ChatService
    private lateinit var speedTestService: SpeedTestService
    private lateinit var fileTransferService: FileTransferService
    private lateinit var permissionManager: PermissionManager
    private lateinit var methodChannelHandler: FlutterMethodChannelHandler
    
    private var receiver: WiFiDirectBroadcastReceiver? = null
    private val intentFilter = IntentFilter()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up intent filter
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        
        // Initialize services with methodChannel
        permissionManager = PermissionManager(this, methodChannel)
        socketManager = SocketConnectionManager(methodChannel, this)
        wifiDirectManager = WiFiDirectManager(this, methodChannel)
        wifiDirectManager.initialize(this)
        
        // Get service references from SocketConnectionManager
        chatService = socketManager.getChatService()
        speedTestService = socketManager.getSpeedTestService()
        fileTransferService = socketManager.getFileTransferService()
        
        // Set permission manager reference in file transfer service
        fileTransferService.setPermissionManager(permissionManager)
        
        methodChannelHandler = FlutterMethodChannelHandler(
            this,
            methodChannel,
            wifiDirectManager,
            socketManager,
            chatService,
            speedTestService,
            fileTransferService,
            permissionManager
        )
        
        methodChannel.setMethodCallHandler(methodChannelHandler)
        
        // Check permissions after initialization
        permissionManager.checkPermissions()
    }
    
    // Lifecycle methods
    override fun onResume() {
        super.onResume()
        receiver = WiFiDirectBroadcastReceiver(
            wifiDirectManager.wifiP2pManager,
            wifiDirectManager.channel,
            wifiDirectManager
        )
        registerReceiver(receiver, intentFilter)
    }
    
    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        socketManager.cleanup()
    }
    
    // Handle activity results
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fileTransferService.handleActivityResult(requestCode, resultCode, data)
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.handlePermissionResult(requestCode, permissions, grantResults)
    }
    
    // WiFiDirectManager.ConnectionListener implementation
    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        socketManager.updateConnectionInfo(info.groupFormed, info.isGroupOwner, info.groupOwnerAddress?.hostAddress)
        
        val connectionInfo = mapOf(
            "isConnected" to info.groupFormed,
            "isGroupOwner" to info.isGroupOwner,
            "groupOwnerAddress" to (info.groupOwnerAddress?.hostAddress ?: "")
        )
        methodChannel.invokeMethod("onConnectionChanged", connectionInfo)
        
        if (info.groupFormed) {
            socketManager.startServers()
        }
    }
    
    override fun onPeersAvailable(peers: WifiP2pDeviceList) {
        val peerList = peers.deviceList.map { device ->
            mapOf(
                "deviceName" to device.deviceName,
                "deviceAddress" to device.deviceAddress,
                "status" to device.status
            )
        }
        methodChannel.invokeMethod("onPeersChanged", peerList)
    }
    
    override fun onWifiP2pStateChanged(enabled: Boolean) {
        methodChannel.invokeMethod("onWifiP2pStateChanged", enabled)
    }
}