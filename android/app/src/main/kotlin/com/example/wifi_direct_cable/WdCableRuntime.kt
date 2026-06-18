package com.example.wifi_direct_cable

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import com.example.wifi_direct_cable.diagnostics.DiagnosticsLogger
import com.example.wifi_direct_cable.session.SessionManager
import io.flutter.plugin.common.MethodChannel

object WdCableRuntime {
    enum class ReceiverOwner(val label: String) {
        ACTIVITY("activity"),
        SERVICE("service")
    }

    private val lock = Any()
    private val receiverOwners = linkedSetOf<ReceiverOwner>()

    private var appContext: Context? = null
    private var methodChannel: MethodChannel? = null
    private var wifiDirectManager: WiFiDirectManager? = null
    private var sessionManager: SessionManager? = null
    private var receiver: WiFiDirectBroadcastReceiver? = null
    private var receiverRegistered = false

    fun configure(
        context: Context,
        methodChannel: MethodChannel,
        wifiDirectManager: WiFiDirectManager,
        sessionManager: SessionManager
    ) {
        synchronized(lock) {
            appContext = context.applicationContext
            this.methodChannel = methodChannel
            this.wifiDirectManager = wifiDirectManager
            this.sessionManager = sessionManager
        }
    }

    fun clear() {
        synchronized(lock) {
            unregisterReceiverLocked()
            receiverOwners.clear()
            receiver = null
            appContext = null
            methodChannel = null
            wifiDirectManager = null
            sessionManager = null
        }
    }

    fun registerReceiver(owner: ReceiverOwner) {
        synchronized(lock) {
            receiverOwners.add(owner)
            if (receiverRegistered) return

            val context = appContext ?: return
            val manager = wifiDirectManager ?: return
            if (manager.channel == null) return

            val activeReceiver = receiver ?: WiFiDirectBroadcastReceiver(manager)
                .also { receiver = it }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(activeReceiver, wifiDirectIntentFilter(), Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(activeReceiver, wifiDirectIntentFilter())
                }
                receiverRegistered = true
                DiagnosticsLogger.log("runtime", "Wi-Fi Direct receiver registered", mapOf("owner" to receiverOwnerLabelLocked()))
                methodChannel?.invokeMethod("onDebug", "Wi-Fi Direct receiver registered by ${owner.label}")
            } catch (exception: Exception) {
                receiverOwners.remove(owner)
                DiagnosticsLogger.log(
                    "runtime",
                    "Wi-Fi Direct receiver registration failed",
                    mapOf("owner" to owner.label, "error" to exception.message)
                )
                methodChannel?.invokeMethod("onDebug", "Wi-Fi Direct receiver registration failed: ${exception.message}")
            }
        }
    }

    fun unregisterReceiver(owner: ReceiverOwner) {
        synchronized(lock) {
            receiverOwners.remove(owner)
            if (receiverOwners.isNotEmpty()) return
            unregisterReceiverLocked()
        }
    }

    fun replayCurrentStateToFlutter() {
        val manager = synchronized(lock) { wifiDirectManager }
        val session = synchronized(lock) { sessionManager }
        manager?.replayLatestConnectionInfo()
        manager?.replayLatestNativeState()
        session?.replayCurrentState()
    }

    fun requestConnectionInfoOnce(
        reason: String,
        dispatchToListener: Boolean,
        callback: ((WifiP2pInfo?) -> Unit)? = null
    ) {
        val manager = synchronized(lock) { wifiDirectManager }
        if (manager == null) {
            callback?.invoke(null)
            return
        }
        manager.requestConnectionInfoOnce(reason, dispatchToListener, callback)
    }

    fun startConnectionService(context: Context? = null) {
        val targetContext = context?.applicationContext ?: synchronized(lock) { appContext } ?: return
        if (WdCableConnectionService.isRunning) return
        try {
            WdCableConnectionService.start(targetContext)
        } catch (exception: Exception) {
            DiagnosticsLogger.log(
                "service",
                "Foreground service start failed",
                mapOf("error" to exception.message)
            )
            methodChannel?.invokeMethod("onDebug", "Foreground service start failed: ${exception.message}")
        }
    }

    fun stopConnectionService(context: Context? = null) {
        val targetContext = context?.applicationContext ?: synchronized(lock) { appContext } ?: return
        WdCableConnectionService.stop(targetContext)
    }

    fun handleWifiP2pStateChanged(enabled: Boolean) {
        if (enabled) return

        val session = synchronized(lock) { sessionManager }
        session?.disconnect("wifi_disabled")
        stopConnectionService()
    }

    fun handleNotificationStop() {
        val session = synchronized(lock) { sessionManager }
        session?.disconnect("notification_stop")
        stopConnectionService()
    }

    fun diagnostics(): Map<String, Any> {
        val receiverOwner = synchronized(lock) { receiverOwnerLabelLocked() }
        val recoveryReason = synchronized(lock) { sessionManager?.lastRecoveryReason ?: "" }
        return mapOf(
            "foregroundServiceRunning" to WdCableConnectionService.isRunning,
            "wifiLockHeld" to WdCableConnectionService.wifiLockHeld,
            "wakeLockHeld" to WdCableConnectionService.wakeLockHeld,
            "receiverOwner" to receiverOwner,
            "lastRecoveryReason" to recoveryReason
        )
    }

    private fun unregisterReceiverLocked() {
        if (!receiverRegistered) return
        val context = appContext ?: return
        try {
            receiver?.let { context.unregisterReceiver(it) }
            DiagnosticsLogger.log("runtime", "Wi-Fi Direct receiver unregistered")
            methodChannel?.invokeMethod("onDebug", "Wi-Fi Direct receiver unregistered")
        } catch (exception: IllegalArgumentException) {
            methodChannel?.invokeMethod("onDebug", "Wi-Fi Direct receiver was already unregistered")
        } finally {
            receiverRegistered = false
        }
    }

    private fun receiverOwnerLabelLocked(): String {
        return if (receiverRegistered && receiverOwners.isNotEmpty()) {
            receiverOwners.joinToString("+") { it.label }
        } else {
            "none"
        }
    }

    private fun wifiDirectIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
            addAction(WifiP2pManager.ACTION_WIFI_P2P_REQUEST_RESPONSE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                addAction(WifiP2pManager.ACTION_WIFI_P2P_LISTEN_STATE_CHANGED)
            }
        }
    }
}
