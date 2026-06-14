package com.example.wifi_direct_cable

import android.content.Intent
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pDeviceList
import android.os.Bundle
import com.example.wifi_direct_cable.audio.AudioService
import com.example.wifi_direct_cable.session.SessionManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity(), WiFiDirectManager.ConnectionListener {
    private val CHANNEL = "wifi_direct_cable"
    
    private lateinit var methodChannel: MethodChannel
    private lateinit var wifiDirectManager: WiFiDirectManager
    private lateinit var sessionManager: SessionManager
    private lateinit var chatService: ChatService
    private lateinit var speedTestService: SpeedTestService
    private lateinit var fileTransferService: FileTransferService
    private lateinit var audioService: AudioService
    private lateinit var permissionManager: PermissionManager
    private lateinit var methodChannelHandler: FlutterMethodChannelHandler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        
        // Initialize services with methodChannel
        permissionManager = PermissionManager(this, methodChannel)
        sessionManager = SessionManager(this, methodChannel)
        wifiDirectManager = WiFiDirectManager(this, methodChannel)
        wifiDirectManager.initialize(this)
        WdCableRuntime.configure(this, methodChannel, wifiDirectManager, sessionManager)
        
        chatService = ChatService(sessionManager, methodChannel)
        speedTestService = SpeedTestService(sessionManager, methodChannel)
        fileTransferService = FileTransferService(this, sessionManager, methodChannel)
        audioService = AudioService(this, sessionManager, methodChannel, permissionManager)
        
        methodChannelHandler = FlutterMethodChannelHandler(
            this,
            methodChannel,
            wifiDirectManager,
            sessionManager,
            chatService,
            speedTestService,
            fileTransferService,
            audioService,
            permissionManager
        )
        
        methodChannel.setMethodCallHandler(methodChannelHandler)
        
        // Check permissions after initialization
        permissionManager.checkPermissions()
    }
    
    // Lifecycle methods
    override fun onResume() {
        super.onResume()
        WdCableRuntime.registerReceiver(WdCableRuntime.ReceiverOwner.ACTIVITY)
        WdCableRuntime.replayCurrentStateToFlutter()
    }

    override fun onPause() {
        WdCableRuntime.unregisterReceiver(WdCableRuntime.ReceiverOwner.ACTIVITY)
        super.onPause()
    }

    override fun onDestroy() {
        WdCableRuntime.unregisterReceiver(WdCableRuntime.ReceiverOwner.ACTIVITY)
        if (::sessionManager.isInitialized) {
            sessionManager.cleanup()
        }
        if (::audioService.isInitialized) {
            audioService.cleanup()
        }
        if (::wifiDirectManager.isInitialized) {
            wifiDirectManager.channel?.close()
        }
        WdCableRuntime.clear()
        super.onDestroy()
    }
    
    // Handle activity results
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fileTransferService.handleActivityResult(requestCode, resultCode, data)
        WdCableRuntime.replayCurrentStateToFlutter()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.handlePermissionResult(requestCode, permissions, grantResults)
    }
    
    // WiFiDirectManager.ConnectionListener implementation
    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        sessionManager.updateConnectionInfo(info.groupFormed, info.isGroupOwner, info.groupOwnerAddress?.hostAddress)
        
        val connectionInfo = mapOf(
            "isConnected" to info.groupFormed,
            "isGroupOwner" to info.isGroupOwner,
            "groupOwnerAddress" to (info.groupOwnerAddress?.hostAddress ?: "")
        )
        methodChannel.invokeMethod("onConnectionChanged", connectionInfo)
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
