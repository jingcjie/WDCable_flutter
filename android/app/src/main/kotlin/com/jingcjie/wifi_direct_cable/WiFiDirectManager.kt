package com.jingcjie.wifi_direct_cable

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.jingcjie.wifi_direct_cable.diagnostics.DiagnosticsLogger
import io.flutter.plugin.common.MethodChannel

class WiFiDirectManager(
    private val context: Context,
    private val methodChannel: MethodChannel
) {
    companion object {
        private const val STATE_BLOCKED_BY_PERMISSION = "BlockedByPermission"
        private const val STATE_UNAVAILABLE = "Unavailable"
        private const val STATE_READY = "Ready"
        private const val STATE_LISTENING = "Listening"
        private const val STATE_SERVICE_REGISTERED = "ServiceRegistered"
        private const val STATE_DISCOVERING = "Discovering"
        private const val STATE_CONNECTING = "Connecting"
        private const val STATE_CONNECTED = "Connected"
        private const val STATE_DISCONNECTING = "Disconnecting"
        private const val STATE_USER_STOPPED_SCAN = "UserStoppedScan"
        private const val STATE_BACKGROUND = "Background"
        private const val STATE_ERROR = "Error"

        private const val CONNECT_TIMEOUT_MS = 60_000L
        private const val SERVICE_INSTANCE = "WDCable"
        private const val SERVICE_TYPE = "_wdcable._tcp"
    }

    lateinit var wifiP2pManager: WifiP2pManager
    var channel: WifiP2pManager.Channel? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainThreadDispatcher = MainThreadDispatcher()

    private var latestPeersCount = 0
    private var latestConnectionInfo: Map<String, Any> = connectionInfoMap(false, false, "")
    private var p2pStateKnown = false
    private var p2pEnabled = false
    private var foreground = false
    private var shuttingDown = false

    private var availabilityDesired = false
    private var listenDesired = false
    private var listenInFlight = false
    private var localServiceRegistered = false
    private var localServiceAddInFlight = false
    private var localServiceInfo: WifiP2pServiceInfo? = null
    private var serviceRequest: WifiP2pServiceRequest? = null
    private var serviceRequestRegistered = false
    private var serviceRequestInFlight = false
    private var serviceDiscoveryInFlight = false

    private var discoveryState = WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED
    private var listenState: Int? = null
    private var nativeState = STATE_UNAVAILABLE
    private var operationId = 0
    private var activeConnectOpId = 0
    private var pendingPeerAddress: String? = null
    private var pendingPeerName: String? = null
    private var connectedGroupFormed = false
    private var hasKnownGroup = false
    private var isGroupOwner = false
    private var groupOwnerAddress = ""
    private var lastReasonCode: Int? = null
    private var lastReasonName = ""
    private val wdCablePeerAddresses = linkedSetOf<String>()

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
        initializeChannel("initialize")
        logWifi("Wi-Fi Direct manager initialized", mapOf("callback" to "initialize"))
        ensureAvailable("initialize")
    }

    fun close() {
        shuttingDown = true
        try {
            channel?.close()
        } catch (exception: Exception) {
            logWifi(
                "Wi-Fi Direct channel close failed",
                mapOf("callback" to "close", "result" to "failed", "error" to exception.message)
            )
        } finally {
            channel = null
        }
    }

    fun getManager(): WifiP2pManager = wifiP2pManager

    fun setForeground(isForeground: Boolean, reason: String) {
        foreground = isForeground
        listenDesired = isForeground && nativeState != STATE_CONNECTING && nativeState != STATE_CONNECTED
        logWifi(
            "Foreground state changed",
            mapOf("callback" to "setForeground", "reason" to reason, "foreground" to isForeground)
        )
        if (isForeground) {
            ensureAvailable(reason)
        } else if (nativeState != STATE_CONNECTED && nativeState != STATE_CONNECTING) {
            setNativeState(STATE_BACKGROUND, reason)
        }
    }

    fun hydrateState(reason: String) {
        val missingCapabilities = missingWifiDirectCapabilities()
        logPermissionCheck(reason, missingCapabilities)
        if (missingCapabilities.isNotEmpty()) {
            notifyPermissionDenied(missingCapabilities)
            setNativeState(STATE_BLOCKED_BY_PERMISSION, reason)
            return
        }

        val activeChannel = channel
        if (activeChannel == null) {
            setNativeState(STATE_UNAVAILABLE, reason)
            return
        }

        try {
            wifiP2pManager.requestP2pState(activeChannel) { state ->
                val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                handleWifiP2pStateChanged(enabled, "requestP2pState:$reason")
            }
            logWifi("requestP2pState requested", mapOf("api" to "requestP2pState", "result" to "requested", "reason" to reason))
        } catch (exception: Exception) {
            logWifi(
                "requestP2pState failed",
                mapOf("api" to "requestP2pState", "result" to "failed", "reason" to reason, "error" to exception.message)
            )
        }

        try {
            wifiP2pManager.requestDiscoveryState(activeChannel) { state ->
                handleDiscoveryStateChanged(state, "requestDiscoveryState:$reason", false)
            }
            logWifi(
                "requestDiscoveryState requested",
                mapOf("api" to "requestDiscoveryState", "result" to "requested", "reason" to reason)
            )
        } catch (exception: Exception) {
            logWifi(
                "requestDiscoveryState failed",
                mapOf("api" to "requestDiscoveryState", "result" to "failed", "reason" to reason, "error" to exception.message)
            )
        }

        try {
            wifiP2pManager.requestDeviceInfo(activeChannel) { device ->
                handleThisDeviceChanged(device, "requestDeviceInfo:$reason")
            }
            logWifi("requestDeviceInfo requested", mapOf("api" to "requestDeviceInfo", "result" to "requested", "reason" to reason))
        } catch (exception: Exception) {
            logWifi(
                "requestDeviceInfo failed",
                mapOf("api" to "requestDeviceInfo", "result" to "failed", "reason" to reason, "error" to exception.message)
            )
        }

        requestConnectionInfoOnce(reason, dispatchToListener = true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                wifiP2pManager.getListenState(activeChannel, context.mainExecutor) { state ->
                    handleListenStateChanged(state, "getListenState:$reason", false)
                }
                logWifi("getListenState requested", mapOf("api" to "getListenState", "result" to "requested", "reason" to reason))
            } catch (exception: Exception) {
                logWifi(
                    "getListenState failed",
                    mapOf("api" to "getListenState", "result" to "failed", "reason" to reason, "error" to exception.message)
                )
            }
        }
    }

    fun ensureAvailable(reason: String) {
        availabilityDesired = true

        val missingCapabilities = missingWifiDirectCapabilities()
        logPermissionCheck(reason, missingCapabilities)
        if (missingCapabilities.isNotEmpty()) {
            notifyPermissionDenied(missingCapabilities)
            setNativeState(STATE_BLOCKED_BY_PERMISSION, reason)
            return
        }

        val activeChannel = channel
        if (activeChannel == null) {
            setNativeState(STATE_UNAVAILABLE, reason)
            return
        }

        if (!foreground) {
            setNativeState(STATE_BACKGROUND, reason)
            return
        }

        if (!p2pStateKnown) {
            hydrateState("ensureAvailable:$reason")
        } else if (!p2pEnabled) {
            setNativeState(STATE_UNAVAILABLE, reason)
            return
        }

        if (nativeState == STATE_CONNECTING || nativeState == STATE_CONNECTED || connectedGroupFormed) {
            updateDerivedState(reason)
            return
        }

        listenDesired = true
        startListeningIfNeeded(activeChannel, reason)
        registerLocalServiceIfNeeded(activeChannel, reason)
        updateDerivedState(reason)
    }

    fun missingWifiDirectCapabilities(): List<String> {
        val missingCapabilities = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            missingCapabilities.add("Nearby Wi-Fi devices")
        }

        return missingCapabilities
    }

    fun notifyPermissionDenied(capabilities: List<String>) {
        invokeFlutter(
            "onPermissionDenied",
            mapOf(
                "permissions" to wifiDirectRuntimePermissions(),
                "capabilities" to capabilities
            )
        )
    }

    fun discoverPeers(result: MethodChannel.Result) {
        val missingCapabilities = missingWifiDirectCapabilities()
        logPermissionCheck("discoverPeers", missingCapabilities)
        if (missingCapabilities.isNotEmpty()) {
            notifyPermissionDenied(missingCapabilities)
            result.error("PERMISSION_DENIED", "${missingCapabilities.joinToString(", ")} permission required", null)
            return
        }

        if (nativeState == STATE_CONNECTING || nativeState == STATE_CONNECTED || connectedGroupFormed) {
            val error = "Cannot scan while $nativeState"
            logWifi("User scan rejected", mapOf("api" to "discoverPeers", "result" to "rejected", "reason" to error))
            result.error("SCAN_REJECTED", error, nativeSnapshot())
            return
        }

        if (discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
            logWifi("User scan deduped; discovery already running", mapOf("api" to "discoverPeers", "result" to "deduped"))
            result.success("Discovery already running")
            return
        }

        val activeChannel = channel
        if (activeChannel == null) {
            result.error("WIFI_P2P_UNAVAILABLE", "Wi-Fi Direct channel is not available", nativeSnapshot())
            return
        }

        val opId = nextOperationId()
        logWifi(
            "User scan requested",
            mapOf("api" to "discoverPeers", "opId" to opId, "result" to "requested", "reason" to "user_scan")
        )

        wifiP2pManager.discoverPeers(activeChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logWifi(
                    "discoverPeers succeeded",
                    mapOf("api" to "discoverPeers", "callback" to "onSuccess", "opId" to opId, "result" to "succeeded")
                )
                requestDiscoveryState("discoverPeers.onSuccess")
                startServiceDiscoveryDiagnostics(activeChannel, opId, "discoverPeers.onSuccess")
                result.success("Discovery requested")
            }

            override fun onFailure(reasonCode: Int) {
                setLastReason(reasonCode)
                logWifi(
                    "discoverPeers failed",
                    mapOf(
                        "api" to "discoverPeers",
                        "callback" to "onFailure",
                        "opId" to opId,
                        "result" to "failed",
                        "reasonCode" to reasonCode,
                        "reasonName" to reasonName(reasonCode)
                    )
                )
                setNativeState(STATE_ERROR, "discoverPeers.onFailure")
                val error = "Discovery failed: ${reasonName(reasonCode)} ($reasonCode)"
                result.error("DISCOVERY_FAILED", error, nativeSnapshot())
                invokeFlutter("onError", error)
            }
        })
    }

    fun connectToPeer(deviceAddress: String?, result: MethodChannel.Result) {
        if (deviceAddress == null) {
            result.error("INVALID_ARGUMENT", "Device address is required", null)
            return
        }

        val missingCapabilities = missingWifiDirectCapabilities()
        logPermissionCheck("connectToPeer", missingCapabilities)
        if (missingCapabilities.isNotEmpty()) {
            notifyPermissionDenied(missingCapabilities)
            result.error("PERMISSION_DENIED", "${missingCapabilities.joinToString(", ")} permission required", null)
            return
        }

        if (nativeState == STATE_CONNECTING) {
            val error = "Connection already pending for ${pendingPeerAddress ?: "another peer"}"
            logWifi(
                "connect rejected because another connection is pending",
                mapOf("api" to "connect", "result" to "rejected", "peerAddress" to deviceAddress, "reason" to error)
            )
            result.error("CONNECT_REJECTED", error, nativeSnapshot())
            return
        }

        if (nativeState == STATE_CONNECTED || connectedGroupFormed) {
            val error = "Already connected"
            logWifi(
                "connect rejected because a group is connected",
                mapOf("api" to "connect", "result" to "rejected", "peerAddress" to deviceAddress, "reason" to error)
            )
            result.error("CONNECT_REJECTED", error, nativeSnapshot())
            return
        }

        val activeChannel = channel
        if (activeChannel == null) {
            result.error("WIFI_P2P_UNAVAILABLE", "Wi-Fi Direct channel is not available", nativeSnapshot())
            return
        }

        val opId = nextOperationId()
        activeConnectOpId = opId
        pendingPeerAddress = deviceAddress
        pendingPeerName = null
        connectedGroupFormed = false
        setNativeState(STATE_CONNECTING, "connectToPeer")

        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            wps.setup = WpsInfo.PBC
        }

        logWifi(
            "connect requested",
            mapOf("api" to "connect", "opId" to opId, "result" to "requested", "peerAddress" to deviceAddress)
        )

        wifiP2pManager.connect(activeChannel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logWifi(
                    "connect request initiated",
                    mapOf("api" to "connect", "callback" to "onSuccess", "opId" to opId, "result" to "initiated", "peerAddress" to deviceAddress)
                )
                scheduleConnectTimeout(opId, deviceAddress)
                result.success("Connection request initiated")
            }

            override fun onFailure(reasonCode: Int) {
                setLastReason(reasonCode)
                val reasonName = reasonName(reasonCode)
                logWifi(
                    "connect failed",
                    mapOf(
                        "api" to "connect",
                        "callback" to "onFailure",
                        "opId" to opId,
                        "result" to "failed",
                        "reasonCode" to reasonCode,
                        "reasonName" to reasonName,
                        "peerAddress" to deviceAddress
                    )
                )
                val error = "Connection failed: $reasonName ($reasonCode)"
                result.error("CONNECTION_FAILED", error, nativeSnapshot())
                invokeFlutter("onError", error)
                cancelPendingConnect("connect.onFailure", opId) {
                    pendingPeerAddress = null
                    pendingPeerName = null
                    updateDerivedState("connect.onFailure")
                }
            }
        })
    }

    fun disconnect(result: MethodChannel.Result) {
        cleanupWifiDirect(
            reason = "user_disconnect",
            successMessage = "Disconnected",
            emitReset = false,
            result = result
        )
    }

    fun stopDiscovery(result: MethodChannel.Result) {
        val activeChannel = channel
        if (activeChannel == null) {
            result.error("WIFI_P2P_UNAVAILABLE", "Wi-Fi Direct channel is not available", nativeSnapshot())
            return
        }

        if (discoveryState != WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
            logWifi("stopPeerDiscovery skipped; discovery is not running", mapOf("api" to "stopPeerDiscovery", "result" to "skipped"))
            result.success("Discovery was not running")
            return
        }

        val opId = nextOperationId()
        logWifi("stopPeerDiscovery requested", mapOf("api" to "stopPeerDiscovery", "opId" to opId, "result" to "requested"))
        wifiP2pManager.stopPeerDiscovery(activeChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logWifi(
                    "stopPeerDiscovery succeeded",
                    mapOf("api" to "stopPeerDiscovery", "callback" to "onSuccess", "opId" to opId, "result" to "succeeded")
                )
                setNativeState(STATE_USER_STOPPED_SCAN, "stopPeerDiscovery.onSuccess")
                handleDiscoveryStateChanged(WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED, "stopPeerDiscovery.onSuccess", false)
                ensureAvailable("stopPeerDiscovery.onSuccess")
                result.success("Discovery stopped")
            }

            override fun onFailure(reasonCode: Int) {
                setLastReason(reasonCode)
                logWifi(
                    "stopPeerDiscovery failed",
                    mapOf(
                        "api" to "stopPeerDiscovery",
                        "callback" to "onFailure",
                        "opId" to opId,
                        "result" to "failed",
                        "reasonCode" to reasonCode,
                        "reasonName" to reasonName(reasonCode)
                    )
                )
                if (nativeState == STATE_CONNECTING && reasonCode == WifiP2pManager.BUSY) {
                    result.success("Discovery stop deferred while connection setup is pending")
                    return
                }
                result.error("STOP_DISCOVERY_FAILED", "Failed to stop discovery: ${reasonName(reasonCode)} ($reasonCode)", nativeSnapshot())
            }
        })
    }

    fun resetWifiDirectSettings(result: MethodChannel.Result) {
        cleanupWifiDirect(
            reason = "reset_requested",
            successMessage = "WiFi Direct settings reset",
            emitReset = true,
            result = result
        )
    }

    fun requestConnectionInfoOnce(
        reason: String,
        dispatchToListener: Boolean,
        callback: ((WifiP2pInfo?) -> Unit)? = null
    ) {
        mainThreadDispatcher.dispatch {
            requestConnectionInfoOnceOnMain(reason, dispatchToListener, callback)
        }
    }

    private fun requestConnectionInfoOnceOnMain(
        reason: String,
        dispatchToListener: Boolean,
        callback: ((WifiP2pInfo?) -> Unit)?
    ) {
        var callbackDelivered = false
        fun complete(info: WifiP2pInfo?) {
            if (callbackDelivered) return
            callbackDelivered = true
            callback?.invoke(info)
        }

        if (shuttingDown) {
            complete(null)
            return
        }

        val missingCapabilities = missingWifiDirectCapabilities()
        if (missingCapabilities.isNotEmpty()) {
            notifyPermissionDenied(missingCapabilities)
            logWifi(
                "One-shot connection info skipped because permissions are missing",
                mapOf("api" to "requestConnectionInfo", "result" to "skipped", "reason" to reason, "capabilities" to missingCapabilities.joinToString(","))
            )
            complete(null)
            return
        }

        val activeChannel = channel
        if (activeChannel == null) {
            complete(null)
            return
        }

        try {
            wifiP2pManager.requestConnectionInfo(activeChannel) { info ->
                mainThreadDispatcher.dispatch {
                    if (callbackDelivered) return@dispatch
                    if (shuttingDown) {
                        complete(null)
                        return@dispatch
                    }
                    logWifi(
                        "requestConnectionInfo result",
                        mapOf(
                            "api" to "requestConnectionInfo",
                            "callback" to "onConnectionInfoAvailable",
                            "result" to "succeeded",
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
                    complete(info)
                }
            }
        } catch (exception: Exception) {
            logWifi(
                "requestConnectionInfo failed",
                mapOf("api" to "requestConnectionInfo", "result" to "failed", "reason" to reason, "error" to exception.message)
            )
            complete(null)
            return
        }

        logWifi("requestConnectionInfo requested", mapOf("api" to "requestConnectionInfo", "result" to "requested", "reason" to reason))
    }

    fun replayLatestConnectionInfo() {
        invokeFlutter("onConnectionChanged", latestConnectionInfo)
    }

    fun replayLatestNativeState() {
        emitNativeState(mapOf("callback" to "replay"))
        emitDiscoveryState("replay")
        emitListenState("replay")
        emitServiceState("replay")
    }

    fun getDiscoveryStatus(): Map<String, Any> {
        return nativeSnapshot() + mapOf(
            "isDiscovering" to (discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED),
            "peersCount" to latestPeersCount
        )
    }

    fun isWdCablePeer(deviceAddress: String): Boolean {
        return wdCablePeerAddresses.contains(deviceAddress)
    }

    fun handleConnectionInfoAvailable(info: WifiP2pInfo) {
        latestConnectionInfo = connectionInfoMap(info)
        connectedGroupFormed = info.groupFormed
        isGroupOwner = info.isGroupOwner
        groupOwnerAddress = info.groupOwnerAddress?.hostAddress ?: ""

        if (info.groupFormed) {
            hasKnownGroup = true
            pendingPeerAddress = null
            pendingPeerName = null
            discoveryState = WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED
            setNativeState(STATE_CONNECTED, "connectionInfo.groupFormed")
        } else if (nativeState == STATE_CONNECTED) {
            hasKnownGroup = false
            pendingPeerAddress = null
            pendingPeerName = null
            updateDerivedState("connectionInfo.disconnected")
            ensureAvailable("connectionInfo.disconnected")
        } else if (nativeState == STATE_CONNECTING) {
            logWifi(
                "Connection info has no group while connection is pending",
                mapOf("api" to "requestConnectionInfo", "callback" to "onConnectionInfoAvailable", "result" to "pending")
            )
        } else {
            hasKnownGroup = false
            updateDerivedState("connectionInfo.noGroup")
        }

        connectionListener?.onConnectionInfoAvailable(info)
        emitNativeState(mapOf("callback" to "onConnectionInfoAvailable"))
    }

    fun handlePeersAvailable(peers: WifiP2pDeviceList) {
        latestPeersCount = peers.deviceList.size
        val wdCableCount = peers.deviceList.count { isWdCablePeer(it.deviceAddress) }
        logWifi(
            "Peers available",
            mapOf("api" to "requestPeers", "callback" to "onPeersAvailable", "result" to "succeeded", "peerCount" to latestPeersCount, "wdCablePeerCount" to wdCableCount)
        )
        connectionListener?.onPeersAvailable(peers)
    }

    fun handleWifiP2pStateChanged(enabled: Boolean, source: String = "broadcast") {
        p2pStateKnown = true
        p2pEnabled = enabled
        logWifi(
            "Wi-Fi P2P state changed",
            mapOf("callback" to source, "broadcast" to WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION, "result" to if (enabled) "enabled" else "disabled")
        )
        if (!enabled) {
            connectedGroupFormed = false
            hasKnownGroup = false
            pendingPeerAddress = null
            pendingPeerName = null
            setNativeState(STATE_UNAVAILABLE, source)
            WdCableRuntime.handleWifiP2pStateChanged(enabled)
        } else {
            updateDerivedState(source)
            if (availabilityDesired) {
                ensureAvailable(source)
            }
        }
        connectionListener?.onWifiP2pStateChanged(enabled)
    }

    fun handleDiscoveryStateChanged(state: Int, source: String, fromBroadcast: Boolean) {
        discoveryState = state
        logWifi(
            "Discovery state changed",
            mapOf(
                "callback" to source,
                "broadcast" to if (fromBroadcast) WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION else "",
                "result" to discoveryStateName(state),
                "discoveryState" to discoveryStateName(state)
            )
        )
        emitDiscoveryState(source)
        updateDerivedState(source)
    }

    fun handleListenStateChanged(state: Int, source: String, fromBroadcast: Boolean) {
        listenState = state
        logWifi(
            "Listen state changed",
            mapOf(
                "callback" to source,
                "broadcast" to if (fromBroadcast) WifiP2pManager.ACTION_WIFI_P2P_LISTEN_STATE_CHANGED else "",
                "result" to listenStateName(state),
                "listenState" to listenStateName(state)
            )
        )
        emitListenState(source)
        updateDerivedState(source)
    }

    fun handleConnectionRequestResponse(response: Int, config: WifiP2pConfig?) {
        val responseName = requestResponseName(response)
        val responsePeer = config?.deviceAddress ?: ""
        logWifi(
            "Connection request response changed",
            mapOf(
                "api" to "connect",
                "broadcast" to WifiP2pManager.ACTION_WIFI_P2P_REQUEST_RESPONSE_CHANGED,
                "result" to responseName,
                "peerAddress" to responsePeer
            )
        )

        val matchesCurrentRequest = pendingPeerAddress == null ||
            responsePeer.isEmpty() ||
            responsePeer == pendingPeerAddress

        if (nativeState == STATE_CONNECTING && matchesCurrentRequest && response == WifiP2pManager.CONNECTION_REQUEST_REJECT) {
            invokeFlutter("onError", "Connection request rejected by peer")
            cancelPendingConnect("request_response_rejected", activeConnectOpId) {
                pendingPeerAddress = null
                pendingPeerName = null
                setNativeState(STATE_ERROR, "request_response_rejected")
                ensureAvailable("request_response_rejected")
            }
        }
    }

    fun handleThisDeviceChanged(device: WifiP2pDevice?, source: String = "broadcast") {
        logWifi(
            "This device changed",
            mapOf(
                "callback" to source,
                "broadcast" to WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION,
                "peerAddress" to (device?.deviceAddress ?: ""),
                "peerName" to (device?.deviceName ?: ""),
                "deviceStatus" to (device?.status ?: "")
            )
        )
    }

    fun handleGroupInfoAvailable(group: WifiP2pGroup?, source: String) {
        hasKnownGroup = group != null
        logWifi(
            "Group info available",
            mapOf(
                "api" to "requestGroupInfo",
                "callback" to source,
                "result" to if (group != null) "group" else "none",
                "groupFormed" to (group != null),
                "isGroupOwner" to (group?.isGroupOwner == true),
                "peerName" to (group?.networkName ?: ""),
                "peerCount" to (group?.clientList?.size ?: 0)
            )
        )
    }

    private fun initializeChannel(reason: String) {
        channel = wifiP2pManager.initialize(
            context,
            context.mainLooper,
            object : WifiP2pManager.ChannelListener {
                override fun onChannelDisconnected() {
                    handleChannelDisconnected()
                }
            }
        )
        localServiceRegistered = false
        localServiceAddInFlight = false
        serviceRequestRegistered = false
        serviceRequestInFlight = false
        serviceDiscoveryInFlight = false
        listenInFlight = false
        configureDnsSdListeners(reason)
        logWifi("Wi-Fi Direct channel created", mapOf("callback" to "initializeChannel", "result" to "created", "reason" to reason))
    }

    private fun handleChannelDisconnected() {
        if (shuttingDown) return
        logWifi("Wi-Fi Direct channel lost", mapOf("callback" to "onChannelDisconnected", "result" to "lost"))
        channel = null
        localServiceRegistered = false
        localServiceAddInFlight = false
        serviceRequestRegistered = false
        serviceRequestInFlight = false
        serviceDiscoveryInFlight = false
        listenInFlight = false

        if (nativeState == STATE_CONNECTING || nativeState == STATE_CONNECTED || connectedGroupFormed) {
            setNativeState(STATE_ERROR, "channel_disconnected")
            return
        }

        initializeChannel("channel_disconnected")
        hydrateState("channel_reinitialized")
        if (availabilityDesired) {
            ensureAvailable("channel_reinitialized")
        }
    }

    private fun configureDnsSdListeners(reason: String) {
        val activeChannel = channel ?: return
        try {
            wifiP2pManager.setDnsSdResponseListeners(
                activeChannel,
                { instanceName, registrationType, srcDevice ->
                    if (registrationType == SERVICE_TYPE || registrationType.contains("wdcable", ignoreCase = true)) {
                        wdCablePeerAddresses.add(srcDevice.deviceAddress)
                    }
                    logWifi(
                        "DNS-SD service response",
                        mapOf(
                            "api" to "setDnsSdResponseListeners",
                            "callback" to "onDnsSdServiceAvailable",
                            "result" to registrationType,
                            "reason" to reason,
                            "peerAddress" to srcDevice.deviceAddress,
                            "peerName" to srcDevice.deviceName,
                            "instanceName" to instanceName
                        )
                    )
                },
                { fullDomainName, txtRecordMap, srcDevice ->
                    if (fullDomainName.contains("wdcable", ignoreCase = true) ||
                        txtRecordMap["app"]?.equals("WDCable", ignoreCase = true) == true) {
                        wdCablePeerAddresses.add(srcDevice.deviceAddress)
                    }
                    logWifi(
                        "DNS-SD TXT response",
                        mapOf(
                            "api" to "setDnsSdResponseListeners",
                            "callback" to "onDnsSdTxtRecordAvailable",
                            "result" to "txt",
                            "reason" to reason,
                            "peerAddress" to srcDevice.deviceAddress,
                            "peerName" to srcDevice.deviceName,
                            "serviceName" to fullDomainName,
                            "txt" to txtRecordMap.entries.joinToString(",") { "${it.key}=${it.value}" }
                        )
                    )
                }
            )
            logWifi("DNS-SD listeners registered", mapOf("api" to "setDnsSdResponseListeners", "result" to "registered", "reason" to reason))
        } catch (exception: Exception) {
            logWifi(
                "DNS-SD listener registration failed",
                mapOf("api" to "setDnsSdResponseListeners", "result" to "failed", "reason" to reason, "error" to exception.message)
            )
        }
    }

    private fun startListeningIfNeeded(activeChannel: WifiP2pManager.Channel, reason: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!listenDesired || listenInFlight || listenState == WifiP2pManager.WIFI_P2P_LISTEN_STARTED) return

        listenInFlight = true
        logWifi("startListening requested", mapOf("api" to "startListening", "result" to "requested", "reason" to reason))
        wifiP2pManager.startListening(activeChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                listenInFlight = false
                logWifi(
                    "startListening succeeded",
                    mapOf("api" to "startListening", "callback" to "onSuccess", "result" to "succeeded", "reason" to reason)
                )
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    handleListenStateChanged(WifiP2pManager.WIFI_P2P_LISTEN_STARTED, "startListening.onSuccess", false)
                }
                updateDerivedState("startListening.onSuccess")
            }

            override fun onFailure(reasonCode: Int) {
                listenInFlight = false
                setLastReason(reasonCode)
                logWifi(
                    "startListening failed",
                    mapOf(
                        "api" to "startListening",
                        "callback" to "onFailure",
                        "result" to "failed",
                        "reason" to reason,
                        "reasonCode" to reasonCode,
                        "reasonName" to reasonName(reasonCode)
                    )
                )
                if (reasonCode != WifiP2pManager.BUSY) {
                    setNativeState(STATE_ERROR, "startListening.onFailure")
                }
            }
        })
    }

    private fun registerLocalServiceIfNeeded(activeChannel: WifiP2pManager.Channel, reason: String) {
        if (localServiceRegistered || localServiceAddInFlight) return

        val serviceInfo = localServiceInfo ?: buildLocalServiceInfo().also { localServiceInfo = it }
        localServiceAddInFlight = true
        logWifi("addLocalService requested", mapOf("api" to "addLocalService", "result" to "requested", "reason" to reason))
        wifiP2pManager.addLocalService(activeChannel, serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                localServiceAddInFlight = false
                localServiceRegistered = true
                logWifi(
                    "addLocalService succeeded",
                    mapOf("api" to "addLocalService", "callback" to "onSuccess", "result" to "succeeded", "reason" to reason)
                )
                emitServiceState("addLocalService.onSuccess")
                updateDerivedState("addLocalService.onSuccess")
            }

            override fun onFailure(reasonCode: Int) {
                localServiceAddInFlight = false
                localServiceRegistered = false
                setLastReason(reasonCode)
                logWifi(
                    "addLocalService failed",
                    mapOf(
                        "api" to "addLocalService",
                        "callback" to "onFailure",
                        "result" to "failed",
                        "reason" to reason,
                        "reasonCode" to reasonCode,
                        "reasonName" to reasonName(reasonCode)
                    )
                )
                emitServiceState("addLocalService.onFailure")
                if (reasonCode != WifiP2pManager.BUSY) {
                    setNativeState(STATE_ERROR, "addLocalService.onFailure")
                }
            }
        })
    }

    private fun buildLocalServiceInfo(): WifiP2pServiceInfo {
        val txtRecord = mapOf(
            "proto" to "1",
            "app" to "WDCable",
            "platform" to "android",
            "version" to appVersionName()
        )
        return WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, SERVICE_TYPE, txtRecord)
    }

    private fun startServiceDiscoveryDiagnostics(
        activeChannel: WifiP2pManager.Channel,
        opId: Int,
        reason: String
    ) {
        if (nativeState == STATE_CONNECTING || nativeState == STATE_CONNECTED || connectedGroupFormed) {
            logWifi(
                "Service discovery skipped during connection state",
                mapOf("api" to "discoverServices", "opId" to opId, "result" to "skipped", "reason" to reason)
            )
            return
        }

        if (serviceRequestRegistered) {
            discoverWdcableServices(activeChannel, opId, reason)
            return
        }

        if (serviceRequestInFlight) return

        val request = serviceRequest ?: WifiP2pDnsSdServiceRequest
            .newInstance(SERVICE_INSTANCE, SERVICE_TYPE)
            .also { serviceRequest = it }

        serviceRequestInFlight = true
        logWifi("addServiceRequest requested", mapOf("api" to "addServiceRequest", "opId" to opId, "result" to "requested", "reason" to reason))
        wifiP2pManager.addServiceRequest(activeChannel, request, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                serviceRequestInFlight = false
                serviceRequestRegistered = true
                logWifi(
                    "addServiceRequest succeeded",
                    mapOf("api" to "addServiceRequest", "callback" to "onSuccess", "opId" to opId, "result" to "succeeded", "reason" to reason)
                )
                discoverWdcableServices(activeChannel, opId, "addServiceRequest.onSuccess")
            }

            override fun onFailure(reasonCode: Int) {
                serviceRequestInFlight = false
                serviceRequestRegistered = false
                setLastReason(reasonCode)
                logWifi(
                    "addServiceRequest failed",
                    mapOf(
                        "api" to "addServiceRequest",
                        "callback" to "onFailure",
                        "opId" to opId,
                        "result" to "failed",
                        "reason" to reason,
                        "reasonCode" to reasonCode,
                        "reasonName" to reasonName(reasonCode)
                    )
                )
            }
        })
    }

    private fun discoverWdcableServices(
        activeChannel: WifiP2pManager.Channel,
        opId: Int,
        reason: String
    ) {
        if (serviceDiscoveryInFlight) return
        serviceDiscoveryInFlight = true
        logWifi("discoverServices requested", mapOf("api" to "discoverServices", "opId" to opId, "result" to "requested", "reason" to reason))
        wifiP2pManager.discoverServices(activeChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                serviceDiscoveryInFlight = false
                logWifi(
                    "discoverServices succeeded",
                    mapOf("api" to "discoverServices", "callback" to "onSuccess", "opId" to opId, "result" to "succeeded", "reason" to reason)
                )
            }

            override fun onFailure(reasonCode: Int) {
                serviceDiscoveryInFlight = false
                setLastReason(reasonCode)
                logWifi(
                    "discoverServices failed",
                    mapOf(
                        "api" to "discoverServices",
                        "callback" to "onFailure",
                        "opId" to opId,
                        "result" to "failed",
                        "reason" to reason,
                        "reasonCode" to reasonCode,
                        "reasonName" to reasonName(reasonCode)
                    )
                )
            }
        })
    }

    private fun cleanupWifiDirect(
        reason: String,
        successMessage: String,
        emitReset: Boolean,
        result: MethodChannel.Result
    ) {
        val opId = nextOperationId()
        logWifi("Disconnect cleanup started", mapOf("api" to "cleanup", "opId" to opId, "result" to "started", "reason" to reason))
        setNativeState(STATE_DISCONNECTING, reason)

        val finish: () -> Unit = {
            pendingPeerAddress = null
            pendingPeerName = null
            activeConnectOpId = 0
            connectedGroupFormed = false
            hasKnownGroup = false
            isGroupOwner = false
            groupOwnerAddress = ""
            latestConnectionInfo = connectionInfoMap(false, false, "")
            logWifi("Disconnect cleanup complete", mapOf("api" to "cleanup", "opId" to opId, "result" to "complete", "reason" to reason))
            result.success(successMessage)
            if (emitReset) {
                invokeFlutter("onWifiDirectReset", null)
            }
            ensureAvailable("$reason.complete")
        }

        val removeIfNeeded: () -> Unit = {
            requestGroupInfo("cleanup:$reason") { group ->
                if (group != null || connectedGroupFormed || hasKnownGroup) {
                    removeGroupInternal(reason, opId, finish)
                } else {
                    finish()
                }
            }
        }

        if (nativeState == STATE_CONNECTING || pendingPeerAddress != null) {
            cancelPendingConnect(reason, opId, removeIfNeeded)
        } else {
            removeIfNeeded()
        }
    }

    private fun cancelPendingConnect(reason: String, opId: Int, onComplete: () -> Unit) {
        val activeChannel = channel
        if (activeChannel == null) {
            onComplete()
            return
        }

        logWifi("cancelConnect requested", mapOf("api" to "cancelConnect", "opId" to opId, "result" to "requested", "reason" to reason))
        wifiP2pManager.cancelConnect(activeChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logWifi(
                    "cancelConnect succeeded",
                    mapOf("api" to "cancelConnect", "callback" to "onSuccess", "opId" to opId, "result" to "succeeded", "reason" to reason)
                )
                onComplete()
            }

            override fun onFailure(reasonCode: Int) {
                setLastReason(reasonCode)
                logWifi(
                    "cancelConnect failed",
                    mapOf(
                        "api" to "cancelConnect",
                        "callback" to "onFailure",
                        "opId" to opId,
                        "result" to "failed",
                        "reason" to reason,
                        "reasonCode" to reasonCode,
                        "reasonName" to reasonName(reasonCode)
                    )
                )
                onComplete()
            }
        })
    }

    private fun removeGroupInternal(reason: String, opId: Int, onComplete: () -> Unit) {
        val activeChannel = channel
        if (activeChannel == null) {
            onComplete()
            return
        }

        logWifi("removeGroup requested", mapOf("api" to "removeGroup", "opId" to opId, "result" to "requested", "reason" to reason))
        wifiP2pManager.removeGroup(activeChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                logWifi(
                    "removeGroup succeeded",
                    mapOf("api" to "removeGroup", "callback" to "onSuccess", "opId" to opId, "result" to "succeeded", "reason" to reason)
                )
                onComplete()
            }

            override fun onFailure(reasonCode: Int) {
                setLastReason(reasonCode)
                logWifi(
                    "removeGroup failed",
                    mapOf(
                        "api" to "removeGroup",
                        "callback" to "onFailure",
                        "opId" to opId,
                        "result" to "failed",
                        "reason" to reason,
                        "reasonCode" to reasonCode,
                        "reasonName" to reasonName(reasonCode)
                    )
                )
                onComplete()
            }
        })
    }

    private fun requestGroupInfo(reason: String, callback: (WifiP2pGroup?) -> Unit) {
        val activeChannel = channel
        if (activeChannel == null) {
            callback(null)
            return
        }

        try {
            wifiP2pManager.requestGroupInfo(activeChannel) { group ->
                handleGroupInfoAvailable(group, "requestGroupInfo:$reason")
                callback(group)
            }
            logWifi("requestGroupInfo requested", mapOf("api" to "requestGroupInfo", "result" to "requested", "reason" to reason))
        } catch (exception: Exception) {
            logWifi(
                "requestGroupInfo failed",
                mapOf("api" to "requestGroupInfo", "result" to "failed", "reason" to reason, "error" to exception.message)
            )
            callback(null)
        }
    }

    private fun requestDiscoveryState(reason: String) {
        val activeChannel = channel ?: return
        try {
            wifiP2pManager.requestDiscoveryState(activeChannel) { state ->
                handleDiscoveryStateChanged(state, "requestDiscoveryState:$reason", false)
            }
            logWifi("requestDiscoveryState requested", mapOf("api" to "requestDiscoveryState", "result" to "requested", "reason" to reason))
        } catch (exception: Exception) {
            logWifi(
                "requestDiscoveryState failed",
                mapOf("api" to "requestDiscoveryState", "result" to "failed", "reason" to reason, "error" to exception.message)
            )
        }
    }

    private fun scheduleConnectTimeout(opId: Int, deviceAddress: String) {
        mainHandler.postDelayed({
            if (nativeState == STATE_CONNECTING && activeConnectOpId == opId && pendingPeerAddress == deviceAddress) {
                logWifi(
                    "connect timed out",
                    mapOf("api" to "connect", "callback" to "timeout", "opId" to opId, "result" to "timeout", "peerAddress" to deviceAddress)
                )
                invokeFlutter("onError", "Connection timed out")
                cancelPendingConnect("connect.timeout", opId) {
                    pendingPeerAddress = null
                    pendingPeerName = null
                    setNativeState(STATE_ERROR, "connect.timeout")
                    ensureAvailable("connect.timeout")
                }
            }
        }, CONNECT_TIMEOUT_MS)
    }

    private fun updateDerivedState(reason: String) {
        setNativeState(deriveNativeState(), reason)
    }

    private fun deriveNativeState(): String {
        return when {
            missingWifiDirectCapabilities().isNotEmpty() -> STATE_BLOCKED_BY_PERMISSION
            !foreground -> STATE_BACKGROUND
            !p2pEnabled && p2pStateKnown -> STATE_UNAVAILABLE
            connectedGroupFormed -> STATE_CONNECTED
            pendingPeerAddress != null -> STATE_CONNECTING
            discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED -> STATE_DISCOVERING
            localServiceRegistered -> STATE_SERVICE_REGISTERED
            listenState == WifiP2pManager.WIFI_P2P_LISTEN_STARTED -> STATE_LISTENING
            channel != null -> STATE_READY
            else -> STATE_UNAVAILABLE
        }
    }

    private fun setNativeState(nextState: String, reason: String) {
        val changed = nativeState != nextState
        nativeState = nextState
        if (changed) {
            logWifi(
                "Native Wi-Fi Direct state changed",
                mapOf("callback" to reason, "result" to nextState, "reason" to reason)
            )
        }
        emitNativeState(mapOf("callback" to reason, "reason" to reason))
    }

    private fun emitNativeState(extra: Map<String, Any?> = emptyMap()) {
        invokeFlutter("onNativeStateChanged", nativeSnapshot(extra))
    }

    private fun emitDiscoveryState(source: String) {
        invokeFlutter(
            "onDiscoveryStateChanged",
            nativeSnapshot(
                mapOf(
                    "callback" to source,
                    "isDiscovering" to (discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED),
                    "discoveryState" to discoveryStateName(discoveryState),
                    "discoveryStateCode" to discoveryState
                )
            )
        )
    }

    private fun emitListenState(source: String) {
        invokeFlutter(
            "onListenStateChanged",
            nativeSnapshot(
                mapOf(
                    "callback" to source,
                    "isListening" to (listenState == WifiP2pManager.WIFI_P2P_LISTEN_STARTED),
                    "listenState" to listenStateName(listenState),
                    "listenStateCode" to (listenState ?: -1)
                )
            )
        )
    }

    private fun emitServiceState(source: String) {
        invokeFlutter(
            "onServiceStateChanged",
            nativeSnapshot(
                mapOf(
                    "callback" to source,
                    "serviceRegistered" to localServiceRegistered,
                    "serviceType" to SERVICE_TYPE
                )
            )
        )
    }

    private fun nativeSnapshot(extra: Map<String, Any?> = emptyMap()): Map<String, Any> {
        val snapshot = linkedMapOf<String, Any>(
            "timestamp" to System.currentTimeMillis(),
            "platform" to "android",
            "opId" to operationId,
            "state" to nativeState,
            "api" to Build.VERSION.SDK_INT,
            "p2pEnabled" to p2pEnabled,
            "p2pStateKnown" to p2pStateKnown,
            "isDiscovering" to (discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED),
            "discoveryState" to discoveryStateName(discoveryState),
            "discoveryStateCode" to discoveryState,
            "isListening" to (listenState == WifiP2pManager.WIFI_P2P_LISTEN_STARTED),
            "listenState" to listenStateName(listenState),
            "listenStateCode" to (listenState ?: -1),
            "listenDesired" to listenDesired,
            "serviceRegistered" to localServiceRegistered,
            "serviceType" to SERVICE_TYPE,
            "peerAddress" to (pendingPeerAddress ?: ""),
            "peerName" to (pendingPeerName ?: ""),
            "groupFormed" to connectedGroupFormed,
            "isGroupOwner" to isGroupOwner,
            "wifiRole" to wifiRoleName(connectedGroupFormed, isGroupOwner),
            "transportRole" to transportRoleName(connectedGroupFormed, isGroupOwner),
            "groupOwnerAddress" to groupOwnerAddress,
            "reasonCode" to (lastReasonCode ?: -1),
            "reasonName" to lastReasonName,
            "peersCount" to latestPeersCount
        )
        for ((key, value) in extra) {
            if (value != null) {
                snapshot[key] = value
            }
        }
        return snapshot
    }

    private fun logWifi(message: String, fields: Map<String, Any?> = emptyMap()) {
        val merged = nativeSnapshot(fields).toMutableMap()
        merged["message"] = message
        DiagnosticsLogger.log("wifi", message, merged)
        invokeFlutter("onDebug", diagnosticLine(message, merged))
    }

    private fun invokeFlutter(method: String, arguments: Any?) {
        mainThreadDispatcher.dispatch {
            if (!shuttingDown) {
                methodChannel.invokeMethod(method, arguments)
            }
        }
    }

    private fun diagnosticLine(message: String, fields: Map<String, Any?>): String {
        val keys = listOf(
            "platform",
            "opId",
            "state",
            "api",
            "callback",
            "broadcast",
            "result",
            "reasonCode",
            "reasonName",
            "peerAddress",
            "peerName",
            "discoveryState",
            "listenState",
            "groupFormed",
            "isGroupOwner",
            "wifiRole",
            "transportRole",
            "groupOwnerAddress"
        )
        val values = keys.joinToString(" | ") { key -> "$key=${fields[key] ?: ""}" }
        return "$message | $values"
    }

    private fun logPermissionCheck(reason: String, missingCapabilities: List<String>) {
        logWifi(
            "Permission check result",
            mapOf(
                "api" to "permissions",
                "callback" to reason,
                "result" to if (missingCapabilities.isEmpty()) "granted" else "blocked",
                "capabilities" to missingCapabilities.joinToString(",")
            )
        )
    }

    private fun setLastReason(reasonCode: Int) {
        lastReasonCode = reasonCode
        lastReasonName = reasonName(reasonCode)
    }

    private fun nextOperationId(): Int {
        operationId += 1
        return operationId
    }

    private fun wifiDirectRuntimePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            emptyList()
        }
    }

    private fun appVersionName(): String {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (exception: Exception) {
            ""
        }
    }

    private fun reasonName(reasonCode: Int): String {
        if (reasonCode == WifiP2pManager.ERROR) return "ERROR"
        if (reasonCode == WifiP2pManager.P2P_UNSUPPORTED) return "P2P_UNSUPPORTED"
        if (reasonCode == WifiP2pManager.BUSY) return "BUSY"
        if (reasonCode == WifiP2pManager.NO_SERVICE_REQUESTS) return "NO_SERVICE_REQUESTS"
        if (reasonCode == 4) return "NO_PERMISSION"
        return "UNKNOWN_$reasonCode"
    }

    private fun discoveryStateName(state: Int): String {
        return when (state) {
            WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED -> "started"
            WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED -> "stopped"
            else -> "unknown_$state"
        }
    }

    private fun listenStateName(state: Int?): String {
        return when (state) {
            WifiP2pManager.WIFI_P2P_LISTEN_STARTED -> "started"
            WifiP2pManager.WIFI_P2P_LISTEN_STOPPED -> "stopped"
            null -> "unknown"
            else -> "unknown_$state"
        }
    }

    private fun requestResponseName(response: Int): String {
        return when (response) {
            WifiP2pManager.CONNECTION_REQUEST_ACCEPT -> "accepted"
            WifiP2pManager.CONNECTION_REQUEST_REJECT -> "rejected"
            WifiP2pManager.CONNECTION_REQUEST_DEFER_TO_SERVICE -> "deferred_to_service"
            WifiP2pManager.CONNECTION_REQUEST_DEFER_SHOW_PIN_TO_SERVICE -> "deferred_show_pin_to_service"
            else -> "unknown_$response"
        }
    }

    private fun connectionInfoMap(info: WifiP2pInfo): Map<String, Any> {
        return connectionInfoMap(
            isConnected = info.groupFormed,
            isGroupOwner = info.isGroupOwner,
            groupOwnerAddress = info.groupOwnerAddress?.hostAddress ?: ""
        )
    }

    private fun connectionInfoMap(
        isConnected: Boolean,
        isGroupOwner: Boolean,
        groupOwnerAddress: String
    ): Map<String, Any> {
        return mapOf(
            "isConnected" to isConnected,
            "isGroupOwner" to isGroupOwner,
            "wifiRole" to wifiRoleName(isConnected, isGroupOwner),
            "transportRole" to transportRoleName(isConnected, isGroupOwner),
            "groupOwnerAddress" to groupOwnerAddress
        )
    }

    private fun wifiRoleName(isConnected: Boolean, isGroupOwner: Boolean): String {
        if (!isConnected) return ""
        return if (isGroupOwner) "groupOwner" else "client"
    }

    private fun transportRoleName(isConnected: Boolean, isGroupOwner: Boolean): String {
        if (!isConnected) return ""
        return if (isGroupOwner) "connector" else "listener"
    }
}
