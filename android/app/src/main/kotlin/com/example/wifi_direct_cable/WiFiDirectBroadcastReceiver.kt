package com.example.wifi_direct_cable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel?,
    private val wifiDirectManager: WiFiDirectManager
) : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                wifiDirectManager.handleWifiP2pStateChanged(enabled)
            }
            
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                val missingCapabilities = wifiDirectManager.missingWifiDirectCapabilities()
                if (missingCapabilities.isEmpty()) {
                    manager.requestPeers(channel) { peers ->
                        wifiDirectManager.handlePeersAvailable(peers)
                    }
                } else {
                    wifiDirectManager.notifyPermissionDenied(missingCapabilities)
                }
            }
            
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val missingCapabilities = wifiDirectManager.missingWifiDirectCapabilities()
                if (missingCapabilities.isEmpty()) {
                    manager.requestConnectionInfo(channel) { info ->
                        wifiDirectManager.handleConnectionInfoAvailable(info)
                    }
                } else {
                    wifiDirectManager.notifyPermissionDenied(missingCapabilities)
                }
            }
            
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // Device info changed - could be used for device name updates
            }
        }
    }
}
