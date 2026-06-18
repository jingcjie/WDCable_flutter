package com.jingcjie.wifi_direct_cable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build

class WiFiDirectBroadcastReceiver(
    private val wifiDirectManager: WiFiDirectManager
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = wifiDirectManager.getManager()
        val channel = wifiDirectManager.channel

        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                wifiDirectManager.handleWifiP2pStateChanged(enabled)
            }

            WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                val state = intent.getIntExtra(
                    WifiP2pManager.EXTRA_DISCOVERY_STATE,
                    WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED
                )
                wifiDirectManager.handleDiscoveryStateChanged(state, "broadcast", true)
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                val missingCapabilities = wifiDirectManager.missingWifiDirectCapabilities()
                if (missingCapabilities.isEmpty() && channel != null) {
                    manager.requestPeers(channel) { peers ->
                        wifiDirectManager.handlePeersAvailable(peers)
                    }
                } else if (missingCapabilities.isNotEmpty()) {
                    wifiDirectManager.notifyPermissionDenied(missingCapabilities)
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val missingCapabilities = wifiDirectManager.missingWifiDirectCapabilities()
                if (missingCapabilities.isEmpty() && channel != null) {
                    manager.requestConnectionInfo(channel) { info ->
                        wifiDirectManager.handleConnectionInfoAvailable(info)
                    }
                    manager.requestGroupInfo(channel) { group ->
                        wifiDirectManager.handleGroupInfoAvailable(group, "broadcast")
                    }
                } else if (missingCapabilities.isNotEmpty()) {
                    wifiDirectManager.notifyPermissionDenied(missingCapabilities)
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                wifiDirectManager.handleThisDeviceChanged(
                    intent.wifiP2pDeviceExtra(),
                    "broadcast"
                )
            }

            WifiP2pManager.ACTION_WIFI_P2P_REQUEST_RESPONSE_CHANGED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val response = intent.getIntExtra(WifiP2pManager.EXTRA_REQUEST_RESPONSE, -1)
                    val config = intent.wifiP2pConfigExtra()
                    wifiDirectManager.handleConnectionRequestResponse(response, config)
                }
            }

            WifiP2pManager.ACTION_WIFI_P2P_LISTEN_STATE_CHANGED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_LISTEN_STATE,
                        WifiP2pManager.WIFI_P2P_LISTEN_STOPPED
                    )
                    wifiDirectManager.handleListenStateChanged(state, "broadcast", true)
                }
            }
        }
    }

    private fun Intent.wifiP2pDeviceExtra(): WifiP2pDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
        }
    }

    private fun Intent.wifiP2pConfigExtra(): WifiP2pConfig? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(WifiP2pManager.EXTRA_REQUEST_CONFIG, WifiP2pConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(WifiP2pManager.EXTRA_REQUEST_CONFIG)
        }
    }
}
