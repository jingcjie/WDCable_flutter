package com.jingcjie.wifi_direct_cable.session

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import com.jingcjie.wifi_direct_cable.WdCableRuntime
import com.jingcjie.wifi_direct_cable.diagnostics.DiagnosticsLogger
import com.jingcjie.wifi_direct_cable.protocol.ProtocolChannel
import com.jingcjie.wifi_direct_cable.protocol.ProtocolConstants
import com.jingcjie.wifi_direct_cable.protocol.ProtocolError
import com.jingcjie.wifi_direct_cable.protocol.ProtocolException
import com.jingcjie.wifi_direct_cable.protocol.ProtocolFrame
import com.jingcjie.wifi_direct_cable.protocol.ProtocolFrameType
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.math.pow

class SessionManager(
    private val context: Context,
    private val methodChannel: MethodChannel,
    private val transportAdapter: SessionTransportAdapter = SocketSessionTransportAdapter()
) {
    private data class Runtime(
        val generation: Int,
        val sessionId: String,
        val role: SessionRole,
        val peerAddress: String?,
        val transports: Map<ProtocolChannel, SessionTransport>,
        val peerCapabilities: MutableSet<String> = mutableSetOf(),
        val sequenceNumber: AtomicLong = AtomicLong(0),
        val lastFrameReceivedAt: AtomicLong = AtomicLong(System.currentTimeMillis())
    ) {
        val controlTransport: SessionTransport
            get() = transports.getValue(ProtocolChannel.CONTROL)

        val bulkTransport: SessionTransport
            get() = transports.getValue(ProtocolChannel.BULK)

        val transportRole: SessionTransportRole
            get() = role.transportRole()
    }

    private data class IncomingFileStream(
        val transferId: String,
        val fileName: String,
        val safeFileName: String,
        val expectedSize: Long,
        val file: File,
        val outputStream: FileOutputStream,
        val digest: MessageDigest,
        val startedAtMs: Long,
        var bytesReceived: Long = 0L
    )

    private data class IncomingSpeedStream(
        val testId: String,
        val expectedSize: Long,
        val startedAtMs: Long,
        var bytesReceived: Long = 0L
    )

    private val setupExecutor = Executors.newSingleThreadExecutor()
    private val ioExecutor = Executors.newCachedThreadPool()
    private val heartbeatExecutor = Executors.newSingleThreadScheduledExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val transportSetup = ProtocolV2TransportSetup(
        transportAdapter = transportAdapter,
        ioExecutor = ioExecutor,
        isCurrent = { expectedGeneration -> isCurrent(expectedGeneration) },
        emitDebug = { message -> methodChannel.invokeOnMain("onDebug", message) }
    )
    private val stateMachine = SessionStateMachine()
    private val generation = AtomicInteger(0)
    private val activeFileReceives = ConcurrentHashMap<Long, IncomingFileStream>()
    private val activeSpeedReceives = ConcurrentHashMap<Long, IncomingSpeedStream>()
    private val speedTestActive = AtomicBoolean(false)
    private val bulkSendLock = Any()
    private val audioTransportLock = Any()

    @Volatile
    private var audioHandler: AudioSessionHandler? = null

    @Volatile
    private var audioTransport: SessionTransport? = null

    @Volatile
    private var audioListener: SessionTransportListener? = null

    @Volatile
    private var runtime: Runtime? = null

    @Volatile
    private var currentSessionId: String? = null

    @Volatile
    private var currentRole: SessionRole? = null

    @Volatile
    private var groupOwnerAddress: String? = null

    @Volatile
    private var lastDisconnectReason: String? = null

    @Volatile
    var lastRecoveryReason: String? = null
        private set

    @Volatile
    private var heartbeatFuture: ScheduledFuture<*>? = null

    fun updateConnectionInfo(groupFormed: Boolean, isGroupOwner: Boolean, ownerAddress: String?) {
        if (!groupFormed) {
            disconnect("wifi_direct_disconnected")
            return
        }

        val role = if (isGroupOwner) SessionRole.GROUP_OWNER else SessionRole.CLIENT
        val normalizedOwnerAddress = ownerAddress?.takeIf { it.isNotBlank() }
        if (role == SessionRole.CLIENT && normalizedOwnerAddress == null) {
            failWithoutRuntime("missing_group_owner_address", "Wi-Fi Direct did not provide a group owner address")
            return
        }

        val currentPhase = stateMachine.phase
        if (isSameWifiDirectGroup(role, normalizedOwnerAddress) && currentPhase.keepsActiveSession()) {
            DiagnosticsLogger.log(
                "session",
                "Ignoring duplicate Wi-Fi Direct connection info",
                mapOf(
                    "sessionId" to currentSessionId,
                    "role" to role.eventName,
                    "transportRole" to role.transportRole().eventName,
                    "peer" to normalizedOwnerAddress,
                    "phase" to currentPhase.eventName
                )
            )
            methodChannel.invokeOnMain("onDebug", "Wi-Fi Direct connection info unchanged; keeping WDCable session")
            return
        }

        val sessionId = UUID.randomUUID().toString()
        val newGeneration = generation.incrementAndGet()
        audioHandler?.onSessionEnded("wifi_direct_reconnected")
        closeAudioTransport()
        closeRuntimeOnly()
        transportAdapter.cancel()
        stopHeartbeat()
        cleanupIncomingBulkStreams(deletePartialFiles = true)
        speedTestActive.set(false)
        stateMachine.reset(SessionPhase.DISCONNECTED)

        currentSessionId = sessionId
        currentRole = role
        groupOwnerAddress = normalizedOwnerAddress
        lastDisconnectReason = null
        lastRecoveryReason = null

        emitState(SessionPhase.WIFI_DIRECT_CONNECTED, sessionId, role)
        DiagnosticsLogger.log(
            "session",
            "Wi-Fi Direct connected",
            mapOf(
                "sessionId" to sessionId,
                "role" to role.eventName,
                "transportRole" to role.transportRole().eventName,
                "peer" to normalizedOwnerAddress
            )
        )

        setupExecutor.execute {
            setupSession(newGeneration, sessionId, role, normalizedOwnerAddress)
        }
    }

    fun disconnect(reason: String = "local_disconnect") {
        generation.incrementAndGet()
        lastDisconnectReason = reason
        val sessionId = currentSessionId
        val role = currentRole

        transitionForCleanup(SessionPhase.DISCONNECTING, sessionId, role, reason)
        audioHandler?.onSessionEnded(reason)
        stopHeartbeat()
        closeAudioTransport()
        closeRuntimeOnly()
        transportAdapter.cancel()
        runtime = null
        transitionForCleanup(SessionPhase.DISCONNECTED, sessionId, role, reason)
        emitDisconnectReason(reason, sessionId)
        cleanupIncomingBulkStreams(deletePartialFiles = true)
        speedTestActive.set(false)
        WdCableRuntime.stopConnectionService(context)
        DiagnosticsLogger.log(
            "session",
            "Disconnected",
            mapOf("sessionId" to sessionId, "reason" to reason)
        )
    }

    fun cleanup() {
        disconnect("app_destroyed")
        setupExecutor.shutdownNow()
        ioExecutor.shutdownNow()
        heartbeatExecutor.shutdownNow()
    }

    fun configureTransportSettings(bufferSize: Int, timeout: Int, keepAlive: Boolean) {
        methodChannel.invokeOnMain("onDebug", "Transport settings received: bufferSize=$bufferSize timeout=$timeout keepAlive=$keepAlive")
    }

    fun registerAudioHandler(handler: AudioSessionHandler) {
        audioHandler = handler
    }

    fun clearSpeedTestState() {
        speedTestActive.set(false)
    }

    fun getConnectionStats(): Map<String, Any> {
        val phase = stateMachine.phase
        val activeRuntime = runtime
        return mapOf(
            "isConnected" to (phase != SessionPhase.DISCONNECTED && phase != SessionPhase.FAILED),
            "isGroupOwner" to (currentRole == SessionRole.GROUP_OWNER),
            "transportRole" to (currentRole?.transportRole()?.eventName ?: ""),
            "groupOwnerAddress" to (groupOwnerAddress ?: ""),
            "sessionState" to phase.eventName,
            "sessionId" to (currentSessionId ?: ""),
            "isReady" to (phase == SessionPhase.READY),
            "disconnectReason" to (lastDisconnectReason ?: ""),
            "lastRecoveryReason" to (lastRecoveryReason ?: ""),
            "controlChannelOpen" to (activeRuntime?.transports?.containsKey(ProtocolChannel.CONTROL) == true),
            "bulkChannelOpen" to (activeRuntime?.transports?.containsKey(ProtocolChannel.BULK) == true),
            "audioChannelOpen" to (audioTransport != null)
        )
    }

    fun replayCurrentState() {
        val phase = stateMachine.phase
        emitState(phase, currentSessionId, currentRole, lastDisconnectReason)
        val activeRuntime = runtime
        if (phase == SessionPhase.READY && activeRuntime != null) {
            emitSessionReady(activeRuntime)
        }
    }

    fun getAudioSessionInfo(): AudioSessionInfo? {
        val activeRuntime = runtime ?: return null
        if (stateMachine.phase != SessionPhase.READY) return null
        return AudioSessionInfo(
            sessionId = activeRuntime.sessionId,
            role = activeRuntime.role,
            peerAddress = activeRuntime.peerAddress,
            peerCapabilities = activeRuntime.peerCapabilities.toSet()
        )
    }

    fun sendAudioControl(metadata: JSONObject) {
        val activeRuntime = runtime ?: throw IOException("The WDCable session is not ready")
        activeRuntime.controlTransport.writeFrame(
            ProtocolFrame(
                type = ProtocolFrameType.CONTROL_MESSAGE,
                channel = ProtocolChannel.CONTROL,
                streamId = metadata.optLong("streamId", 0L),
                sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                correlationId = UUID.randomUUID(),
                metadataJson = metadata
                    .put("sessionId", activeRuntime.sessionId)
                    .put("timestamp", System.currentTimeMillis())
                    .toString()
            )
        )
    }

    fun sendAudioError(streamId: Long, code: String, message: String) {
        val activeRuntime = runtime ?: return
        sendFeatureError(activeRuntime, streamId, code, message, "audio")
    }

    fun startAudioListener(
        streamId: Long,
        onPort: (Int) -> Unit,
        onConnected: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val activeRuntime = runtime
        if (activeRuntime == null || stateMachine.phase != SessionPhase.READY) {
            onError(IOException("The WDCable session is not ready"))
            return
        }
        if (activeRuntime.role != SessionRole.GROUP_OWNER) {
            onError(IOException("Only the group owner can listen for the audio channel"))
            return
        }

        try {
            closeAudioTransport()
            val listener = transportAdapter.listen(ProtocolChannel.AUDIO, preferredPort = 0)
            synchronized(audioTransportLock) {
                audioListener = listener
            }
            onPort(listener.port)
            ioExecutor.execute {
                try {
                    val transport = listener.accept { !isCurrent(activeRuntime.generation) }
                    synchronized(audioTransportLock) {
                        audioTransport = transport
                        audioListener = null
                    }
                    startAudioReadLoop(activeRuntime, transport)
                    DiagnosticsLogger.log(
                        "audio",
                        "Audio channel accepted",
                        mapOf("sessionId" to activeRuntime.sessionId, "streamId" to streamId)
                    )
                    onConnected()
                } catch (exception: Exception) {
                    synchronized(audioTransportLock) {
                        if (audioListener === listener) {
                            audioListener = null
                        }
                    }
                    if (isCurrent(activeRuntime.generation)) {
                        onError(exception)
                    }
                }
            }
        } catch (exception: Exception) {
            onError(exception)
        }
    }

    fun connectAudioTransport(
        streamId: Long,
        port: Int,
        onConnected: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val activeRuntime = runtime
        if (activeRuntime == null || stateMachine.phase != SessionPhase.READY) {
            onError(IOException("The WDCable session is not ready"))
            return
        }
        if (activeRuntime.role != SessionRole.CLIENT) {
            onError(IOException("Only the client connects to the audio channel"))
            return
        }
        val host = activeRuntime.peerAddress
        if (host.isNullOrBlank()) {
            onError(IOException("Missing group owner address for audio channel"))
            return
        }

        closeAudioTransport()
        ioExecutor.execute {
            try {
                val transport = connectWithRetry(ProtocolChannel.AUDIO, host, port, activeRuntime.generation)
                synchronized(audioTransportLock) {
                    audioTransport = transport
                }
                startAudioReadLoop(activeRuntime, transport)
                DiagnosticsLogger.log(
                    "audio",
                    "Audio channel connected",
                    mapOf("sessionId" to activeRuntime.sessionId, "streamId" to streamId, "port" to port)
                )
                onConnected()
            } catch (exception: Exception) {
                if (isCurrent(activeRuntime.generation)) {
                    onError(exception)
                }
            }
        }
    }

    fun writeAudioFrame(streamId: Long, sequenceNumber: Long, metadata: JSONObject, payload: ByteArray) {
        val transport = synchronized(audioTransportLock) { audioTransport }
            ?: throw IOException("Audio channel is not connected")
        transport.writeFrame(
            ProtocolFrame(
                type = ProtocolFrameType.AUDIO_FRAME,
                channel = ProtocolChannel.AUDIO,
                streamId = streamId,
                sequenceNumber = sequenceNumber,
                correlationId = UUID.randomUUID(),
                metadataJson = metadata.toString(),
                payload = payload
            )
        )
    }

    fun closeAudioTransport() {
        val listener: SessionTransportListener?
        val transport: SessionTransport?
        synchronized(audioTransportLock) {
            listener = audioListener
            transport = audioTransport
            audioListener = null
            audioTransport = null
        }
        try {
            listener?.close()
        } catch (_: Exception) {
        }
        try {
            transport?.cancel()
        } catch (_: Exception) {
        }
    }

    fun sendFileStream(
        fileName: String,
        sizeBytes: Long,
        inputStream: InputStream,
        result: MethodChannel.Result
    ) {
        val activeRuntime = readyRuntimeOrError(result) ?: run {
            inputStream.close()
            return
        }
        ioExecutor.execute {
            inputStream.use { stream ->
                try {
                    sendFileStreamInternal(activeRuntime, fileName, sizeBytes, stream)
                    mainHandler.post {
                        result.success("File sent")
                    }
                } catch (exception: Exception) {
                    DiagnosticsLogger.log(
                        "file",
                        "File send failed",
                        mapOf(
                            "sessionId" to activeRuntime.sessionId,
                            "fileName" to fileName,
                            "error" to exception.message
                        )
                    )
                    methodChannel.invokeOnMain("onError", "File send failed: ${exception.message}")
                    mainHandler.post {
                        result.error("SEND_FAILED", "Failed to send file: ${exception.message}", null)
                    }
                }
            }
        }
    }

    fun requestSpeedTestData(sizeBytes: Long, result: MethodChannel.Result) {
        val activeRuntime = readyRuntimeOrError(result) ?: return
        if (!speedTestActive.compareAndSet(false, true)) {
            result.error("SPEED_TEST_BUSY", "A speed test is already running", null)
            return
        }

        ioExecutor.execute {
            try {
                val testId = UUID.randomUUID().toString()
                val streamId = nextStreamId()
                val metadata = JSONObject()
                    .put("kind", "speed-request")
                    .put("testId", testId)
                    .put("sessionId", activeRuntime.sessionId)
                    .put("sizeBytes", sizeBytes)
                    .put("timestamp", System.currentTimeMillis())

                activeRuntime.bulkTransport.writeFrame(
                    ProtocolFrame(
                        type = ProtocolFrameType.BULK_START,
                        channel = ProtocolChannel.BULK,
                        streamId = streamId,
                        sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                        correlationId = UUID.fromString(testId),
                        metadataJson = metadata.toString()
                    )
                )
                DiagnosticsLogger.log(
                    "speed",
                    "Speed download requested",
                    mapOf("sessionId" to activeRuntime.sessionId, "testId" to testId, "sizeBytes" to sizeBytes)
                )
                mainHandler.post {
                    result.success("Speed test data request sent: $sizeBytes bytes")
                }
            } catch (exception: Exception) {
                speedTestActive.set(false)
                mainHandler.post {
                    result.error("REQUEST_FAILED", "Failed to request speed test data: ${exception.message}", null)
                }
            }
        }
    }

    fun sendSpeedTestData(sizeBytes: Long, result: MethodChannel.Result) {
        val activeRuntime = readyRuntimeOrError(result) ?: return
        if (!speedTestActive.compareAndSet(false, true)) {
            result.error("SPEED_TEST_BUSY", "A speed test is already running", null)
            return
        }

        ioExecutor.execute {
            try {
                sendSpeedPayload(activeRuntime, UUID.randomUUID().toString(), sizeBytes, emitLocalProgress = true)
                mainHandler.post {
                    result.success("Speed test data sent: $sizeBytes bytes")
                }
            } catch (exception: Exception) {
                speedTestActive.set(false)
                mainHandler.post {
                    result.error("SEND_FAILED", "Failed to send speed test data: ${exception.message}", null)
                }
            }
        }
    }

    fun sendChatMessage(message: String, result: MethodChannel.Result) {
        val activeRuntime = readyRuntimeOrError(result) ?: return

        ioExecutor.execute {
            try {
                val timestamp = System.currentTimeMillis()
                val messageId = UUID.randomUUID().toString()
                val metadata = JSONObject()
                    .put("kind", "chat")
                    .put("messageId", messageId)
                    .put("timestamp", timestamp)
                    .put("senderPlatform", "android")
                    .put("sessionId", activeRuntime.sessionId)

                activeRuntime.controlTransport.writeFrame(
                    ProtocolFrame(
                        type = ProtocolFrameType.CONTROL_MESSAGE,
                        channel = ProtocolChannel.CONTROL,
                        streamId = 0L,
                        sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                        correlationId = UUID.fromString(messageId),
                        metadataJson = metadata.toString(),
                        payload = message.toByteArray(Charsets.UTF_8)
                    )
                )
                DiagnosticsLogger.log(
                    "chat",
                    "Chat message sent",
                    mapOf("sessionId" to activeRuntime.sessionId, "messageId" to messageId)
                )
                mainHandler.post {
                    result.success("Message sent")
                }
            } catch (exception: Exception) {
                methodChannel.invokeOnMain("onError", "Chat send failed: ${exception.message}")
                mainHandler.post {
                    result.error("SEND_FAILED", "Failed to send chat message: ${exception.message}", null)
                }
            }
        }
    }

    private fun setupSession(
        expectedGeneration: Int,
        sessionId: String,
        role: SessionRole,
        ownerAddress: String?
    ) {
        val openedTransports = linkedMapOf<ProtocolChannel, SessionTransport>()
        try {
            if (!isCurrent(expectedGeneration)) return
            val setupDeadlineMs = transportSetup.deadlineFromNow()

            emitState(SessionPhase.CONNECTING_TRANSPORT, sessionId, role)
            val opened = transportSetup.openTransports(role, ownerAddress, expectedGeneration, sessionId, setupDeadlineMs)
            openedTransports.putAll(opened.transports)
            if (!isCurrent(expectedGeneration)) {
                closeTransports(openedTransports.values)
                return
            }

            val newRuntime = Runtime(
                generation = expectedGeneration,
                sessionId = sessionId,
                role = role,
                peerAddress = opened.peerAddress,
                transports = openedTransports.toMap()
            )

            emitState(SessionPhase.HANDSHAKING, sessionId, role)
            performHandshake(newRuntime)
            newRuntime.transports.values.forEach { it.setReadTimeout(0) }

            if (!isCurrent(expectedGeneration)) {
                closeTransports(newRuntime.transports.values)
                return
            }

            runtime = newRuntime
            emitState(SessionPhase.READY, sessionId, role)
            emitSessionReady(newRuntime)
            startControlReadLoop(newRuntime)
            startBulkReadLoop(newRuntime)
            startHeartbeat(newRuntime)
            DiagnosticsLogger.log(
                "session",
                "Session ready",
                mapOf(
                    "sessionId" to sessionId,
                    "role" to role.eventName,
                    "transportRole" to role.transportRole().eventName,
                    "remoteEndpoint" to (opened.peerAddress ?: "")
                )
            )
            WdCableRuntime.startConnectionService(context)
        } catch (exception: TransportSetupException) {
            closeTransports(openedTransports.values)
            failSession(expectedGeneration, sessionId, role, exception.failureReason, exception, false)
        } catch (exception: PeerProtocolMissingException) {
            closeTransports(openedTransports.values)
            failSession(
                expectedGeneration,
                sessionId,
                role,
                exception.failureReason,
                exception,
                exception.failureReason == "protocol_mismatch"
            )
        } catch (exception: ProtocolException) {
            closeTransports(openedTransports.values)
            val reason = when (exception.error) {
                ProtocolError.UNSUPPORTED_VERSION,
                ProtocolError.MALFORMED_MAGIC -> "protocol_mismatch"
                else -> "protocol_mismatch"
            }
            failSession(expectedGeneration, sessionId, role, reason, exception, true)
        } catch (exception: Exception) {
            closeTransports(openedTransports.values)
            val reason = if (isCurrent(expectedGeneration)) "tcp_connect_timeout" else "cancelled"
            failSession(expectedGeneration, sessionId, role, reason, exception, false)
        }
    }

    private fun connectWithRetry(
        channel: ProtocolChannel,
        host: String,
        port: Int,
        expectedGeneration: Int
    ): SessionTransport {
        var attempt = 1
        var lastError: Exception? = null
        while (attempt <= MAX_CONNECT_ATTEMPTS && isCurrent(expectedGeneration)) {
            try {
                methodChannel.invokeOnMain(
                    "onDebug",
                    "Connecting ${channel.protocolName} channel to $host:$port (attempt $attempt)"
                )
                return transportAdapter.connect(channel, host, port)
            } catch (exception: Exception) {
                lastError = exception
                Thread.sleep(connectRetryDelayMs(attempt))
                attempt++
            }
        }
        throw lastError ?: IOException("Connection cancelled")
    }

    private fun performHandshake(activeRuntime: Runtime) {
        val control = activeRuntime.controlTransport
        control.setReadTimeout(HANDSHAKE_TIMEOUT_MS)

        try {
            if (activeRuntime.role == SessionRole.CLIENT) {
                DiagnosticsLogger.log(
                    "protocol",
                    "Sending handshake hello",
                    mapOf(
                        "sessionId" to activeRuntime.sessionId,
                        "role" to activeRuntime.role.eventName,
                        "transportRole" to activeRuntime.transportRole.eventName
                    )
                )
                control.writeFrame(buildHandshakeHello(activeRuntime))
                val ack = control.readFrame() ?: throw PeerProtocolMissingException("Peer closed before handshake ack")
                if (ack.type != ProtocolFrameType.HANDSHAKE_ACK) {
                    throw PeerProtocolMissingException("Expected handshake ack, received ${ack.type.protocolName}")
                }
                activeRuntime.peerCapabilities.addAll(validateHandshakeAck(ack.metadataJson))
                DiagnosticsLogger.log(
                    "protocol",
                    "Handshake ack accepted",
                    mapOf(
                        "sessionId" to activeRuntime.sessionId,
                        "role" to activeRuntime.role.eventName,
                        "transportRole" to activeRuntime.transportRole.eventName
                    )
                )
            } else {
                val hello = control.readFrame() ?: throw PeerProtocolMissingException("Peer closed before handshake hello")
                if (hello.type != ProtocolFrameType.HANDSHAKE_HELLO) {
                    throw PeerProtocolMissingException("Expected handshake hello, received ${hello.type.protocolName}")
                }
                activeRuntime.peerCapabilities.addAll(validateHandshakeHello(hello.metadataJson))
                DiagnosticsLogger.log(
                    "protocol",
                    "Handshake hello accepted",
                    mapOf(
                        "sessionId" to activeRuntime.sessionId,
                        "role" to activeRuntime.role.eventName,
                        "transportRole" to activeRuntime.transportRole.eventName
                    )
                )
                control.writeFrame(buildHandshakeAck(activeRuntime))
            }
        } catch (exception: SocketTimeoutException) {
            throw PeerProtocolMissingException(
                "Timed out waiting for WDCable handshake",
                exception,
                "handshake_timeout"
            )
        } catch (exception: org.json.JSONException) {
            throw PeerProtocolMissingException("Invalid handshake metadata", exception)
        }
    }

    private fun validateHandshakeHello(metadataJson: String): Set<String> {
        val metadata = JSONObject(metadataJson)
        if (metadata.optString("appId") != ProtocolConstants.APP_ID) {
            throw PeerProtocolMissingException("Peer app id is not WDCable")
        }
        val protocolMin = metadata.optInt("protocolMin", -1)
        val protocolMax = metadata.optInt("protocolMax", -1)
        if (ProtocolConstants.VERSION !in protocolMin..protocolMax) {
            throw ProtocolException(
                ProtocolError.UNSUPPORTED_VERSION,
                "Peer supports protocol $protocolMin..$protocolMax"
            )
        }
        validateHandshakeRoles(
            metadata,
            expectedRole = SessionRole.CLIENT,
            expectedTransportRole = SessionTransportRole.LISTENER
        )
        return capabilitiesFrom(metadata)
    }

    private fun validateHandshakeAck(metadataJson: String): Set<String> {
        val metadata = JSONObject(metadataJson)
        if (metadata.optString("appId") != ProtocolConstants.APP_ID) {
            throw PeerProtocolMissingException("Peer app id is not WDCable")
        }
        if (metadata.optInt("protocolVersion", -1) != ProtocolConstants.VERSION) {
            throw ProtocolException(
                ProtocolError.UNSUPPORTED_VERSION,
                "Peer selected unsupported protocol ${metadata.optInt("protocolVersion", -1)}"
            )
        }
        validateHandshakeRoles(
            metadata,
            expectedRole = SessionRole.GROUP_OWNER,
            expectedTransportRole = SessionTransportRole.CONNECTOR
        )
        return capabilitiesFrom(metadata)
    }

    private fun validateHandshakeRoles(
        metadata: JSONObject,
        expectedRole: SessionRole,
        expectedTransportRole: SessionTransportRole
    ) {
        val peerRole = metadata.optString("role")
        val peerTransportRole = metadata.optString("transportRole")
        if (peerRole != expectedRole.eventName || peerTransportRole != expectedTransportRole.eventName) {
            throw PeerProtocolMissingException(
                "Peer role mismatch: role=$peerRole transportRole=$peerTransportRole"
            )
        }
    }

    private fun capabilitiesFrom(metadata: JSONObject): Set<String> {
        val capabilities = metadata.optJSONArray("capabilities") ?: return emptySet()
        return buildSet {
            for (index in 0 until capabilities.length()) {
                val capability = capabilities.optString(index)
                if (capability.isNotBlank()) {
                    add(capability)
                }
            }
        }
    }

    private fun buildHandshakeHello(activeRuntime: Runtime): ProtocolFrame {
        return ProtocolFrame(
            type = ProtocolFrameType.HANDSHAKE_HELLO,
            channel = ProtocolChannel.CONTROL,
            sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
            correlationId = UUID.randomUUID(),
            metadataJson = baseHandshakeMetadata(activeRuntime)
                .put("protocolMin", ProtocolConstants.VERSION)
                .put("protocolMax", ProtocolConstants.VERSION)
                .toString()
        )
    }

    private fun buildHandshakeAck(activeRuntime: Runtime): ProtocolFrame {
        return ProtocolFrame(
            type = ProtocolFrameType.HANDSHAKE_ACK,
            channel = ProtocolChannel.CONTROL,
            sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
            correlationId = UUID.randomUUID(),
            metadataJson = baseHandshakeMetadata(activeRuntime)
                .put("protocolVersion", ProtocolConstants.VERSION)
                .toString()
        )
    }

    private fun baseHandshakeMetadata(activeRuntime: Runtime): JSONObject {
        return JSONObject()
            .put("appId", ProtocolConstants.APP_ID)
            .put("platform", "android")
            .put("appVersion", appVersion())
            .put("deviceName", Build.MODEL ?: "Android")
            .put("role", activeRuntime.role.eventName)
            .put("transportRole", activeRuntime.transportRole.eventName)
            .put("sessionId", activeRuntime.sessionId)
            .put("capabilities", capabilitiesJson())
            .put("channels", channelsJson())
    }

    private fun capabilitiesJson(): JSONArray {
        return JSONArray()
            .put(ProtocolConstants.CAPABILITY_CHAT)
            .put(ProtocolConstants.CAPABILITY_BULK_FILE)
            .put(ProtocolConstants.CAPABILITY_BULK_SPEED)
            .put(ProtocolConstants.CAPABILITY_DIAGNOSTICS_EXPORT)
            .put(ProtocolConstants.CAPABILITY_AUDIO_LINK)
            .put(ProtocolConstants.CAPABILITY_AUDIO_CODEC_OPUS)
    }

    private fun channelsJson(): JSONObject {
        val channels = JSONObject()
        for ((channel, port) in channelPorts()) {
            channels.put(
                channel.protocolName,
                JSONObject()
                    .put("transport", "tcp")
                    .put("port", port)
            )
        }
        return channels
    }

    private fun startControlReadLoop(activeRuntime: Runtime) {
        ioExecutor.execute {
            while (isCurrent(activeRuntime.generation) && stateMachine.phase == SessionPhase.READY) {
                try {
                    val frame = activeRuntime.controlTransport.readFrame()
                        ?: throw IOException("Control channel closed by peer")
                    activeRuntime.lastFrameReceivedAt.set(System.currentTimeMillis())
                    handleControlFrame(activeRuntime, frame)
                } catch (exception: Exception) {
                    if (isCurrent(activeRuntime.generation) && stateMachine.phase == SessionPhase.READY) {
                        degradeReadySession(activeRuntime, "control_channel_failed", exception)
                    }
                    return@execute
                }
            }
        }
    }

    private fun startBulkReadLoop(activeRuntime: Runtime) {
        ioExecutor.execute {
            while (isCurrent(activeRuntime.generation) && stateMachine.phase == SessionPhase.READY) {
                try {
                    val frame = activeRuntime.bulkTransport.readFrame()
                        ?: throw IOException("Bulk channel closed by peer")
                    handleBulkFrame(activeRuntime, frame)
                } catch (exception: Exception) {
                    if (isCurrent(activeRuntime.generation) && stateMachine.phase == SessionPhase.READY) {
                        degradeReadySession(activeRuntime, "bulk_channel_failed", exception)
                    }
                    return@execute
                }
            }
        }
    }

    private fun startAudioReadLoop(activeRuntime: Runtime, transport: SessionTransport) {
        ioExecutor.execute {
            while (
                isCurrent(activeRuntime.generation) &&
                stateMachine.phase == SessionPhase.READY &&
                synchronized(audioTransportLock) { audioTransport === transport }
            ) {
                try {
                    val frame = transport.readFrame()
                        ?: throw IOException("Audio channel closed by peer")
                    if (frame.type == ProtocolFrameType.AUDIO_FRAME && frame.channel == ProtocolChannel.AUDIO) {
                        audioHandler?.onAudioFrame(frame)
                    } else {
                        DiagnosticsLogger.log(
                            "audio",
                            "Ignoring unexpected audio frame",
                            mapOf("type" to frame.type.protocolName, "channel" to frame.channel.protocolName)
                        )
                    }
                } catch (exception: Exception) {
                    val wasActive = synchronized(audioTransportLock) {
                        if (audioTransport === transport) {
                            audioTransport = null
                            true
                        } else {
                            false
                        }
                    }
                    try {
                        transport.cancel()
                    } catch (_: Exception) {
                    }
                    if (wasActive && isCurrent(activeRuntime.generation) && stateMachine.phase == SessionPhase.READY) {
                        DiagnosticsLogger.log(
                            "audio",
                            "Audio channel closed",
                            mapOf(
                                "sessionId" to activeRuntime.sessionId,
                                "errorType" to exception.javaClass.simpleName,
                                "error" to exception.message
                            )
                        )
                        audioHandler?.onAudioTransportClosed("audio_transport_closed")
                    }
                    return@execute
                }
            }
        }
    }

    private fun handleBulkFrame(activeRuntime: Runtime, frame: ProtocolFrame) {
        val metadata = if (frame.metadataJson.isBlank()) JSONObject() else JSONObject(frame.metadataJson)
        when (frame.type) {
            ProtocolFrameType.BULK_START -> handleBulkStart(activeRuntime, frame, metadata)
            ProtocolFrameType.BULK_CHUNK -> handleBulkChunk(frame, metadata)
            ProtocolFrameType.BULK_COMPLETE -> handleBulkComplete(activeRuntime, frame, metadata)
            ProtocolFrameType.BULK_CANCEL -> handleBulkCancel(frame, metadata)
            else -> DiagnosticsLogger.log(
                "bulk",
                "Ignoring unexpected bulk frame",
                mapOf("type" to frame.type.protocolName, "streamId" to frame.streamId)
            )
        }
    }

    private fun handleBulkStart(activeRuntime: Runtime, frame: ProtocolFrame, metadata: JSONObject) {
        when (metadata.optString("kind")) {
            "file" -> startIncomingFile(frame, metadata)
            "speed-data" -> {
                activeSpeedReceives[frame.streamId] = IncomingSpeedStream(
                    testId = metadata.optString("testId"),
                    expectedSize = metadata.optLong("sizeBytes", -1L),
                    startedAtMs = System.currentTimeMillis()
                )
            }
            "speed-request" -> {
                val requestedBytes = metadata.optLong("sizeBytes", 0L)
                val testId = metadata.optString("testId", UUID.randomUUID().toString())
                ioExecutor.execute {
                    try {
                        sendSpeedPayload(activeRuntime, testId, requestedBytes, emitLocalProgress = false)
                    } catch (exception: Exception) {
                        sendBulkError(activeRuntime, frame.streamId, "speed_send_failed", exception.message ?: "Speed send failed")
                    }
                }
            }
            else -> DiagnosticsLogger.log(
                "bulk",
                "Unknown bulk start kind",
                mapOf("kind" to metadata.optString("kind"), "streamId" to frame.streamId)
            )
        }
    }

    private fun startIncomingFile(frame: ProtocolFrame, metadata: JSONObject) {
        val transferId = metadata.optString("transferId", UUID.randomUUID().toString())
        val fileName = metadata.optString("fileName", "unknown_file")
        val expectedSize = metadata.optLong("sizeBytes", -1L)
        val targetFile = duplicateSafeFile(safeFileName(fileName))
        val incoming = IncomingFileStream(
            transferId = transferId,
            fileName = fileName,
            safeFileName = targetFile.name,
            expectedSize = expectedSize,
            file = targetFile,
            outputStream = FileOutputStream(targetFile),
            digest = MessageDigest.getInstance("SHA-256"),
            startedAtMs = System.currentTimeMillis()
        )
        activeFileReceives[frame.streamId] = incoming
        methodChannel.invokeOnMain(
            "onFileReceiveStarted",
            mapOf("fileName" to incoming.safeFileName, "fileSize" to expectedSize)
        )
        DiagnosticsLogger.log(
            "file",
            "Incoming file started",
            mapOf("transferId" to transferId, "fileName" to incoming.safeFileName, "sizeBytes" to expectedSize)
        )
    }

    private fun handleBulkChunk(frame: ProtocolFrame, metadata: JSONObject) {
        activeFileReceives[frame.streamId]?.let { incoming ->
            incoming.outputStream.write(frame.payload)
            incoming.digest.update(frame.payload)
            incoming.bytesReceived += frame.payload.size
            val progress = if (incoming.expectedSize > 0) {
                (incoming.bytesReceived.toDouble() / incoming.expectedSize.toDouble()).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            methodChannel.invokeOnMain(
                "onFileReceiveProgress",
                mapOf("fileName" to incoming.safeFileName, "progress" to progress)
            )
            return
        }

        activeSpeedReceives[frame.streamId]?.let { incoming ->
            incoming.bytesReceived += frame.payload.size
            val elapsedMs = (System.currentTimeMillis() - incoming.startedAtMs).coerceAtLeast(1L)
            val speedMbps = calculateSpeedMbps(incoming.bytesReceived, elapsedMs)
            val progress = if (incoming.expectedSize > 0) {
                (incoming.bytesReceived.toDouble() / incoming.expectedSize.toDouble()).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            methodChannel.invokeOnMain(
                "onSpeedTestReceiveProgress",
                mapOf(
                    "bytesReceived" to incoming.bytesReceived,
                    "totalBytes" to incoming.expectedSize,
                    "speedMbps" to speedMbps,
                    "progress" to progress
                )
            )
            return
        }

        DiagnosticsLogger.log(
            "bulk",
            "Chunk for unknown stream",
            mapOf("streamId" to frame.streamId, "kind" to metadata.optString("kind"))
        )
    }

    private fun handleBulkComplete(activeRuntime: Runtime, frame: ProtocolFrame, metadata: JSONObject) {
        activeFileReceives.remove(frame.streamId)?.let { incoming ->
            completeIncomingFile(activeRuntime, frame, metadata, incoming)
            return
        }

        activeSpeedReceives.remove(frame.streamId)?.let { incoming ->
            val durationMs = (System.currentTimeMillis() - incoming.startedAtMs).coerceAtLeast(1L)
            val speedMbps = calculateSpeedMbps(incoming.bytesReceived, durationMs)
            methodChannel.invokeOnMain(
                "onSpeedTestDataReceived",
                mapOf(
                    "bytesReceived" to incoming.bytesReceived,
                    "durationMs" to durationMs,
                    "speedMbps" to speedMbps
                )
            )
            speedTestActive.set(false)
            DiagnosticsLogger.log(
                "speed",
                "Speed data received",
                mapOf(
                    "sessionId" to activeRuntime.sessionId,
                    "testId" to incoming.testId,
                    "bytesReceived" to incoming.bytesReceived,
                    "durationMs" to durationMs,
                    "speedMbps" to speedMbps
                )
            )
            return
        }
    }

    private fun completeIncomingFile(
        activeRuntime: Runtime,
        frame: ProtocolFrame,
        metadata: JSONObject,
        incoming: IncomingFileStream
    ) {
        try {
            incoming.outputStream.flush()
            incoming.outputStream.close()
            val actualHash = incoming.digest.digest().toHex()
            val expectedHash = metadata.optString("sha256", "")
            if (expectedHash.isNotBlank() && !expectedHash.equals(actualHash, ignoreCase = true)) {
                incoming.file.delete()
                throw IOException("Checksum mismatch for ${incoming.safeFileName}")
            }
            methodChannel.invokeOnMain(
                "onFileReceiveProgress",
                mapOf("fileName" to incoming.safeFileName, "progress" to 1.0)
            )
            methodChannel.invokeOnMain(
                "onFileReceived",
                mapOf("fileName" to incoming.safeFileName, "filePath" to incoming.file.absolutePath)
            )
            DiagnosticsLogger.log(
                "file",
                "Incoming file completed",
                mapOf(
                    "sessionId" to activeRuntime.sessionId,
                    "transferId" to incoming.transferId,
                    "fileName" to incoming.safeFileName,
                    "bytesReceived" to incoming.bytesReceived,
                    "sha256" to actualHash
                )
            )
            sendAck(activeRuntime, frame, "bulk.complete")
        } catch (exception: Exception) {
            sendBulkError(activeRuntime, frame.streamId, "file_receive_failed", exception.message ?: "File receive failed")
            methodChannel.invokeOnMain("onError", "File receive failed: ${exception.message}")
        }
    }

    private fun handleBulkCancel(frame: ProtocolFrame, metadata: JSONObject) {
        activeFileReceives.remove(frame.streamId)?.let { incoming ->
            closeQuietly(incoming.outputStream)
            incoming.file.delete()
        }
        activeSpeedReceives.remove(frame.streamId)
        speedTestActive.set(false)
        DiagnosticsLogger.log(
            "bulk",
            "Bulk stream cancelled",
            mapOf("streamId" to frame.streamId, "reason" to metadata.optString("reason"))
        )
    }

    private fun handleControlFrame(activeRuntime: Runtime, frame: ProtocolFrame) {
        when (frame.type) {
            ProtocolFrameType.HEARTBEAT_PING -> {
                activeRuntime.controlTransport.writeFrame(
                    ProtocolFrame(
                        type = ProtocolFrameType.HEARTBEAT_PONG,
                        channel = ProtocolChannel.CONTROL,
                        sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                        correlationId = frame.correlationId,
                        metadataJson = heartbeatMetadata(activeRuntime).toString()
                    )
                )
            }
            ProtocolFrameType.HEARTBEAT_PONG -> {
                activeRuntime.lastFrameReceivedAt.set(System.currentTimeMillis())
            }
            ProtocolFrameType.CLOSE -> {
                disconnect("peer_closed")
            }
            ProtocolFrameType.ERROR -> {
                val metadata = if (frame.metadataJson.isBlank()) JSONObject() else JSONObject(frame.metadataJson)
                val code = metadata.optString("code", "peer_error")
                val message = metadata.optString("message", "Peer reported protocol error")
                if (code.startsWith("audio_")) {
                    audioHandler?.onAudioFeatureError(code, message, metadata.optLong("streamId", frame.streamId))
                    DiagnosticsLogger.log(
                        "audio",
                        "Peer reported audio error",
                        mapOf("sessionId" to activeRuntime.sessionId, "code" to code, "message" to message)
                    )
                } else if (code.startsWith("file_") || code.startsWith("speed_") || code.startsWith("bulk_")) {
                    methodChannel.invokeOnMain("onError", message)
                    DiagnosticsLogger.log(
                        "bulk",
                        "Peer reported stream error",
                        mapOf("sessionId" to activeRuntime.sessionId, "code" to code, "message" to message)
                    )
                } else {
                    failSession(
                        activeRuntime.generation,
                        activeRuntime.sessionId,
                        activeRuntime.role,
                        "peer_error",
                        IOException(message),
                        false
                    )
                }
            }
            ProtocolFrameType.CONTROL_MESSAGE -> handleControlMessage(activeRuntime, frame)
            ProtocolFrameType.ACK -> {
                methodChannel.invokeOnMain("onDebug", "Received ack ${frame.correlationId}")
                DiagnosticsLogger.log("control", "Ack received", mapOf("correlationId" to frame.correlationId))
            }
            else -> {
                methodChannel.invokeOnMain(
                    "onDebug",
                    "Ignoring ${frame.type.protocolName} on control channel"
                )
            }
        }
    }

    private fun handleControlMessage(activeRuntime: Runtime, frame: ProtocolFrame) {
        val metadata = if (frame.metadataJson.isBlank()) JSONObject() else JSONObject(frame.metadataJson)
        val kind = metadata.optString("kind")
        when {
            kind.startsWith("audio.") -> {
                audioHandler?.onAudioControlMessage(metadata)
            }
            kind == "chat" -> {
                val message = frame.payload.toString(Charsets.UTF_8)
                val data = mapOf(
                    "message" to message,
                    "timestamp" to metadata.optLong("timestamp", System.currentTimeMillis()),
                    "messageId" to metadata.optString("messageId"),
                    "senderPlatform" to metadata.optString("senderPlatform", "unknown"),
                    "sessionId" to metadata.optString("sessionId", activeRuntime.sessionId)
                )
                methodChannel.invokeOnMain("onDataReceived", data)
                DiagnosticsLogger.log(
                    "chat",
                    "Chat message received",
                    mapOf(
                        "sessionId" to activeRuntime.sessionId,
                        "messageId" to metadata.optString("messageId"),
                        "senderPlatform" to metadata.optString("senderPlatform", "unknown")
                    )
                )
            }
            else -> {
                methodChannel.invokeOnMain("onDebug", "Unknown control message kind: $kind")
            }
        }
    }

    private fun startHeartbeat(activeRuntime: Runtime) {
        stopHeartbeat()
        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate({
            if (!isCurrent(activeRuntime.generation) || stateMachine.phase != SessionPhase.READY) return@scheduleAtFixedRate

            val ageMs = System.currentTimeMillis() - activeRuntime.lastFrameReceivedAt.get()
            if (ageMs > HEARTBEAT_TIMEOUT_MS) {
                degradeReadySession(
                    activeRuntime,
                    "heartbeat_timeout",
                    IOException("No control frames received for ${ageMs}ms")
                )
                return@scheduleAtFixedRate
            }

            try {
                activeRuntime.controlTransport.writeFrame(
                    ProtocolFrame(
                        type = ProtocolFrameType.HEARTBEAT_PING,
                        channel = ProtocolChannel.CONTROL,
                        sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                        correlationId = UUID.randomUUID(),
                        metadataJson = heartbeatMetadata(activeRuntime).toString()
                    )
                )
            } catch (exception: Exception) {
                degradeReadySession(activeRuntime, "heartbeat_send_failed", exception)
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun heartbeatMetadata(activeRuntime: Runtime): JSONObject {
        return JSONObject()
            .put("sessionId", activeRuntime.sessionId)
            .put("timestamp", System.currentTimeMillis())
    }

    private fun sendFileStreamInternal(
        activeRuntime: Runtime,
        fileName: String,
        sizeBytes: Long,
        inputStream: InputStream
    ) {
        val transferId = UUID.randomUUID().toString()
        val streamId = nextStreamId()
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesSent = 0L
        val startedAtMs = System.currentTimeMillis()
        val safeName = safeFileName(fileName)

        synchronized(bulkSendLock) {
            activeRuntime.bulkTransport.writeFrame(
                ProtocolFrame(
                    type = ProtocolFrameType.BULK_START,
                    channel = ProtocolChannel.BULK,
                    streamId = streamId,
                    sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                    correlationId = UUID.fromString(transferId),
                    metadataJson = JSONObject()
                        .put("kind", "file")
                        .put("transferId", transferId)
                        .put("sessionId", activeRuntime.sessionId)
                        .put("fileName", safeName)
                        .put("sizeBytes", sizeBytes)
                        .put("timestamp", startedAtMs)
                        .toString()
                )
            )

            val buffer = ByteArray(BULK_CHUNK_SIZE)
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                val payload = buffer.copyOf(read)
                digest.update(payload)
                bytesSent += read
                activeRuntime.bulkTransport.writeFrame(
                    ProtocolFrame(
                        type = ProtocolFrameType.BULK_CHUNK,
                        channel = ProtocolChannel.BULK,
                        streamId = streamId,
                        sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                        correlationId = UUID.fromString(transferId),
                        metadataJson = JSONObject()
                            .put("kind", "file")
                            .put("transferId", transferId)
                            .put("offset", bytesSent - read)
                            .toString(),
                        payload = payload
                    )
                )
                val progress = if (sizeBytes > 0) {
                    (bytesSent.toDouble() / sizeBytes.toDouble()).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                methodChannel.invokeOnMain(
                    "onFileSendProgress",
                    mapOf("fileName" to safeName, "progress" to progress)
                )
            }

            val sha256 = digest.digest().toHex()
            activeRuntime.bulkTransport.writeFrame(
                ProtocolFrame(
                    type = ProtocolFrameType.BULK_COMPLETE,
                    channel = ProtocolChannel.BULK,
                    streamId = streamId,
                    sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                    correlationId = UUID.fromString(transferId),
                    metadataJson = JSONObject()
                        .put("kind", "file")
                        .put("transferId", transferId)
                        .put("sizeBytes", bytesSent)
                        .put("sha256", sha256)
                        .put("timestamp", System.currentTimeMillis())
                        .toString()
                )
            )
            methodChannel.invokeOnMain(
                "onFileSendProgress",
                mapOf("fileName" to safeName, "progress" to 1.0)
            )
            DiagnosticsLogger.log(
                "file",
                "File sent",
                mapOf(
                    "sessionId" to activeRuntime.sessionId,
                    "transferId" to transferId,
                    "fileName" to safeName,
                    "bytesSent" to bytesSent,
                    "sha256" to sha256,
                    "durationMs" to (System.currentTimeMillis() - startedAtMs)
                )
            )
        }
    }

    private fun sendSpeedPayload(
        activeRuntime: Runtime,
        testId: String,
        sizeBytes: Long,
        emitLocalProgress: Boolean
    ) {
        val safeSize = sizeBytes.coerceAtLeast(0L)
        val streamId = nextStreamId()
        var bytesSent = 0L
        val startedAtMs = System.currentTimeMillis()
        val correlationId = UUID.fromString(testId)

        synchronized(bulkSendLock) {
            activeRuntime.bulkTransport.writeFrame(
                ProtocolFrame(
                    type = ProtocolFrameType.BULK_START,
                    channel = ProtocolChannel.BULK,
                    streamId = streamId,
                    sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                    correlationId = correlationId,
                    metadataJson = JSONObject()
                        .put("kind", "speed-data")
                        .put("testId", testId)
                        .put("sessionId", activeRuntime.sessionId)
                        .put("sizeBytes", safeSize)
                        .put("timestamp", startedAtMs)
                        .toString()
                )
            )

            val buffer = ByteArray(BULK_CHUNK_SIZE)
            while (bytesSent < safeSize) {
                val chunkSize = minOf(buffer.size.toLong(), safeSize - bytesSent).toInt()
                val payload = if (chunkSize == buffer.size) buffer else buffer.copyOf(chunkSize)
                activeRuntime.bulkTransport.writeFrame(
                    ProtocolFrame(
                        type = ProtocolFrameType.BULK_CHUNK,
                        channel = ProtocolChannel.BULK,
                        streamId = streamId,
                        sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                        correlationId = correlationId,
                        metadataJson = JSONObject()
                            .put("kind", "speed-data")
                            .put("testId", testId)
                            .put("offset", bytesSent)
                            .toString(),
                        payload = payload
                    )
                )
                bytesSent += chunkSize
                if (emitLocalProgress) {
                    val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                    methodChannel.invokeOnMain(
                        "onSpeedTestSendProgress",
                        mapOf(
                            "bytesSent" to bytesSent,
                            "totalBytes" to safeSize,
                            "speedMbps" to calculateSpeedMbps(bytesSent, elapsedMs),
                            "progress" to if (safeSize > 0) bytesSent.toDouble() / safeSize.toDouble() else 1.0
                        )
                    )
                }
            }

            activeRuntime.bulkTransport.writeFrame(
                ProtocolFrame(
                    type = ProtocolFrameType.BULK_COMPLETE,
                    channel = ProtocolChannel.BULK,
                    streamId = streamId,
                    sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                    correlationId = correlationId,
                    metadataJson = JSONObject()
                        .put("kind", "speed-data")
                        .put("testId", testId)
                        .put("sizeBytes", bytesSent)
                        .put("timestamp", System.currentTimeMillis())
                        .toString()
                )
            )
        }

        if (emitLocalProgress) {
            val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
            methodChannel.invokeOnMain(
                "onSpeedTestSendProgress",
                mapOf(
                    "bytesSent" to bytesSent,
                    "totalBytes" to safeSize,
                    "speedMbps" to calculateSpeedMbps(bytesSent, elapsedMs),
                    "progress" to 1.0
                )
            )
            speedTestActive.set(false)
        }

        DiagnosticsLogger.log(
            "speed",
            "Speed payload sent",
            mapOf(
                "sessionId" to activeRuntime.sessionId,
                "testId" to testId,
                "bytesSent" to bytesSent,
                "durationMs" to (System.currentTimeMillis() - startedAtMs)
            )
        )
    }

    private fun sendAck(activeRuntime: Runtime, frame: ProtocolFrame, ackFor: String) {
        try {
            activeRuntime.controlTransport.writeFrame(
                ProtocolFrame(
                    type = ProtocolFrameType.ACK,
                    channel = ProtocolChannel.CONTROL,
                    streamId = frame.streamId,
                    sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                    correlationId = frame.correlationId,
                    metadataJson = JSONObject()
                        .put("ackFor", ackFor)
                        .put("streamId", frame.streamId)
                        .put("timestamp", System.currentTimeMillis())
                        .toString()
                )
            )
            DiagnosticsLogger.log(
                "control",
                "Ack sent",
                mapOf("sessionId" to activeRuntime.sessionId, "ackFor" to ackFor, "streamId" to frame.streamId)
            )
        } catch (exception: Exception) {
            DiagnosticsLogger.log(
                "control",
                "Ack send failed",
                mapOf(
                    "sessionId" to activeRuntime.sessionId,
                    "ackFor" to ackFor,
                    "streamId" to frame.streamId,
                    "errorType" to exception.javaClass.simpleName,
                    "error" to exception.message
                )
            )
        }
    }

    private fun sendBulkError(activeRuntime: Runtime, streamId: Long, code: String, message: String) {
        sendFeatureError(activeRuntime, streamId, code, message, "bulk")
    }

    private fun sendFeatureError(
        activeRuntime: Runtime,
        streamId: Long,
        code: String,
        message: String,
        category: String
    ) {
        try {
            activeRuntime.controlTransport.writeFrame(
                ProtocolFrame(
                    type = ProtocolFrameType.ERROR,
                    channel = ProtocolChannel.CONTROL,
                    streamId = streamId,
                    sequenceNumber = activeRuntime.sequenceNumber.incrementAndGet(),
                    correlationId = UUID.randomUUID(),
                    metadataJson = JSONObject()
                        .put("code", code)
                        .put("message", message)
                        .put("streamId", streamId)
                        .toString()
                )
            )
            DiagnosticsLogger.log(
                category,
                "Feature error sent",
                mapOf("sessionId" to activeRuntime.sessionId, "streamId" to streamId, "code" to code, "message" to message)
            )
        } catch (exception: Exception) {
            DiagnosticsLogger.log(
                category,
                "Feature error send failed",
                mapOf(
                    "sessionId" to activeRuntime.sessionId,
                    "streamId" to streamId,
                    "code" to code,
                    "errorType" to exception.javaClass.simpleName,
                    "error" to exception.message
                )
            )
        }
    }

    private fun readyRuntimeOrError(result: MethodChannel.Result): Runtime? {
        val activeRuntime = runtime
        if (activeRuntime == null || stateMachine.phase != SessionPhase.READY) {
            result.error("SESSION_NOT_READY", "The WDCable session is not ready", null)
            return null
        }
        return activeRuntime
    }

    private fun cleanupIncomingBulkStreams(deletePartialFiles: Boolean) {
        activeFileReceives.values.forEach { incoming ->
            closeQuietly(incoming.outputStream)
            if (deletePartialFiles) {
                incoming.file.delete()
            }
        }
        activeFileReceives.clear()
        activeSpeedReceives.clear()
    }

    private fun duplicateSafeFile(safeFileName: String): File {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        return BulkFileNames.duplicateSafeFile(directory, safeFileName)
    }

    private fun safeFileName(fileName: String): String = BulkFileNames.safeFileName(fileName)

    private fun nextStreamId(): Long = kotlin.math.abs(UUID.randomUUID().mostSignificantBits)

    private fun calculateSpeedMbps(bytes: Long, elapsedTimeMs: Long): Double {
        if (elapsedTimeMs <= 0) return 0.0
        return (bytes * 8.0) / (elapsedTimeMs / 1000.0) / (1024 * 1024)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun closeQuietly(outputStream: FileOutputStream) {
        try {
            outputStream.close()
        } catch (_: Exception) {
        }
    }

    private fun isSameWifiDirectGroup(role: SessionRole, ownerAddress: String?): Boolean {
        val knownRole = currentRole ?: return false
        if (knownRole != role) return false
        return role == SessionRole.GROUP_OWNER || groupOwnerAddress == ownerAddress
    }

    private fun isSameWifiDirectGroupInfo(
        info: android.net.wifi.p2p.WifiP2pInfo?,
        role: SessionRole,
        ownerAddress: String?
    ): Boolean {
        if (info?.groupFormed != true) return false
        val currentInfoRole = if (info.isGroupOwner) SessionRole.GROUP_OWNER else SessionRole.CLIENT
        if (currentInfoRole != role) return false
        if (role == SessionRole.GROUP_OWNER) return true
        return info.groupOwnerAddress?.hostAddress == ownerAddress
    }

    private fun SessionPhase.keepsActiveSession(): Boolean {
        return when (this) {
            SessionPhase.WIFI_DIRECT_CONNECTED,
            SessionPhase.CONNECTING_TRANSPORT,
            SessionPhase.HANDSHAKING,
            SessionPhase.READY,
            SessionPhase.DEGRADED -> true
            SessionPhase.DISCONNECTING,
            SessionPhase.DISCONNECTED,
            SessionPhase.FAILED -> false
        }
    }

    private fun failWithoutRuntime(reason: String, message: String) {
        generation.incrementAndGet()
        lastDisconnectReason = reason
        currentSessionId = UUID.randomUUID().toString()
        stateMachine.reset(SessionPhase.DISCONNECTED)
        emitState(SessionPhase.FAILED, currentSessionId, currentRole, reason)
        methodChannel.invokeOnMain(
            "onSessionFailed",
            mapOf("reason" to reason, "message" to message, "sessionId" to (currentSessionId ?: ""))
        )
    }

    private fun degradeReadySession(activeRuntime: Runtime, reason: String, exception: Exception) {
        if (!generation.compareAndSet(activeRuntime.generation, activeRuntime.generation + 1)) return

        val recoveryGeneration = activeRuntime.generation + 1
        lastDisconnectReason = reason
        lastRecoveryReason = reason
        DiagnosticsLogger.log(
            "session",
            "Session degraded",
            mapOf(
                "sessionId" to activeRuntime.sessionId,
                "role" to activeRuntime.role.eventName,
                "reason" to reason,
                "errorType" to exception.javaClass.simpleName,
                "error" to exception.message
            )
        )

        stopHeartbeat()
        audioHandler?.onSessionEnded(reason)
        closeAudioTransport()
        cleanupIncomingBulkStreams(deletePartialFiles = true)
        speedTestActive.set(false)
        closeRuntimeOnly()
        transportAdapter.cancel()
        runtime = null
        emitState(SessionPhase.DEGRADED, activeRuntime.sessionId, activeRuntime.role, reason)
        confirmGroupThenRecoverTransport(
            recoveryGeneration,
            activeRuntime.sessionId,
            activeRuntime.role,
            activeRuntime.peerAddress,
            reason
        )
    }

    private fun confirmGroupThenRecoverTransport(
        recoveryGeneration: Int,
        sessionId: String,
        role: SessionRole,
        ownerAddress: String?,
        degradedReason: String
    ) {
        setupExecutor.execute {
            try {
                Thread.sleep(DEGRADED_GROUP_CONFIRM_DELAY_MS)
            } catch (_: InterruptedException) {
                return@execute
            }

            if (!isCurrent(recoveryGeneration) || stateMachine.phase != SessionPhase.DEGRADED) return@execute

            WdCableRuntime.requestConnectionInfoOnce(
                reason = "degraded_group_confirm:$degradedReason",
                dispatchToListener = false
            ) { info ->
                setupExecutor.execute {
                    if (!isCurrent(recoveryGeneration) || stateMachine.phase != SessionPhase.DEGRADED) return@execute

                    if (!isSameWifiDirectGroupInfo(info, role, ownerAddress)) {
                        lastRecoveryReason = "wifi_direct_group_lost_after_$degradedReason"
                        DiagnosticsLogger.log(
                            "session",
                            "Wi-Fi Direct group lost during degraded recovery",
                            mapOf(
                                "sessionId" to sessionId,
                                "reason" to degradedReason,
                                "groupFormed" to (info?.groupFormed ?: false),
                                "isGroupOwner" to (info?.isGroupOwner ?: false),
                                "groupOwnerAddress" to (info?.groupOwnerAddress?.hostAddress ?: "")
                            )
                        )
                        disconnect("wifi_direct_group_lost")
                        return@execute
                    }

                    rebuildTransportAfterDegraded(recoveryGeneration, sessionId, role, ownerAddress, degradedReason)
                }
            }
        }
    }

    private fun rebuildTransportAfterDegraded(
        recoveryGeneration: Int,
        sessionId: String,
        role: SessionRole,
        ownerAddress: String?,
        degradedReason: String
    ) {
        val openedTransports = linkedMapOf<ProtocolChannel, SessionTransport>()
        try {
            if (!isCurrent(recoveryGeneration) || stateMachine.phase != SessionPhase.DEGRADED) return
            val setupDeadlineMs = transportSetup.deadlineFromNow()

            DiagnosticsLogger.log(
                "session",
                "Rebuilding WDCable transport after degraded state",
                mapOf(
                    "sessionId" to sessionId,
                    "role" to role.eventName,
                    "transportRole" to role.transportRole().eventName,
                    "reason" to degradedReason
                )
            )
            val opened = transportSetup.openTransports(role, ownerAddress, recoveryGeneration, sessionId, setupDeadlineMs)
            openedTransports.putAll(opened.transports)
            if (!isCurrent(recoveryGeneration) || stateMachine.phase != SessionPhase.DEGRADED) {
                closeTransports(openedTransports.values)
                return
            }

            val newRuntime = Runtime(
                generation = recoveryGeneration,
                sessionId = sessionId,
                role = role,
                peerAddress = opened.peerAddress,
                transports = openedTransports.toMap()
            )

            performHandshake(newRuntime)
            newRuntime.transports.values.forEach { it.setReadTimeout(0) }

            if (!isCurrent(recoveryGeneration) || stateMachine.phase != SessionPhase.DEGRADED) {
                closeTransports(newRuntime.transports.values)
                return
            }

            runtime = newRuntime
            lastDisconnectReason = null
            lastRecoveryReason = "recovered_after_$degradedReason"
            emitState(SessionPhase.READY, sessionId, role)
            emitSessionReady(newRuntime)
            startControlReadLoop(newRuntime)
            startBulkReadLoop(newRuntime)
            startHeartbeat(newRuntime)
            WdCableRuntime.startConnectionService(context)
            DiagnosticsLogger.log(
                "session",
                "WDCable transport recovered",
                mapOf(
                    "sessionId" to sessionId,
                    "role" to role.eventName,
                    "transportRole" to role.transportRole().eventName,
                    "reason" to degradedReason,
                    "remoteEndpoint" to (opened.peerAddress ?: "")
                )
            )
        } catch (exception: TransportSetupException) {
            closeTransports(openedTransports.values)
            lastRecoveryReason = "transport_recovery_failed_after_$degradedReason"
            failSession(recoveryGeneration, sessionId, role, exception.failureReason, exception, false)
        } catch (exception: Exception) {
            closeTransports(openedTransports.values)
            lastRecoveryReason = "transport_recovery_failed_after_$degradedReason"
            val reason = if (isCurrent(recoveryGeneration)) "tcp_connect_timeout" else "cancelled"
            failSession(recoveryGeneration, sessionId, role, reason, exception, false)
        }
    }

    private fun failSession(
        expectedGeneration: Int,
        sessionId: String,
        role: SessionRole,
        reason: String,
        exception: Exception,
        peerProtocolMissing: Boolean
    ) {
        if (!generation.compareAndSet(expectedGeneration, expectedGeneration + 1)) return

        lastDisconnectReason = reason
        DiagnosticsLogger.log(
            "session",
            "Session failed",
            mapOf(
                "sessionId" to sessionId,
                "role" to role.eventName,
                "transportRole" to role.transportRole().eventName,
                "reason" to reason,
                "errorType" to exception.javaClass.simpleName,
                "error" to exception.message
            )
        )
        stopHeartbeat()
        audioHandler?.onSessionEnded(reason)
        closeAudioTransport()
        cleanupIncomingBulkStreams(deletePartialFiles = true)
        speedTestActive.set(false)
        closeRuntimeOnly()
        transportAdapter.cancel()
        runtime = null
        emitState(SessionPhase.FAILED, sessionId, role, reason)
        WdCableRuntime.stopConnectionService(context)

        if (peerProtocolMissing) {
            methodChannel.invokeOnMain(
                "onPeerProtocolMissing",
                mapOf(
                    "reason" to reason,
                    "message" to (exception.message ?: "Peer is not running the upgraded WDCable protocol"),
                    "sessionId" to sessionId,
                    "transportRole" to role.transportRole().eventName
                )
            )
        }

        methodChannel.invokeOnMain(
            "onSessionFailed",
            mapOf(
                "reason" to reason,
                "message" to (exception.message ?: reason),
                "sessionId" to sessionId,
                "transportRole" to role.transportRole().eventName
            )
        )
        emitDisconnectReason(reason, sessionId)
    }

    private fun emitSessionReady(activeRuntime: Runtime) {
        methodChannel.invokeOnMain(
            "onSessionReady",
            mapOf(
                "sessionId" to activeRuntime.sessionId,
                "role" to activeRuntime.role.eventName,
                "transportRole" to activeRuntime.transportRole.eventName,
                "protocolVersion" to ProtocolConstants.VERSION,
                "capabilities" to listOf(
                    ProtocolConstants.CAPABILITY_CHAT,
                    ProtocolConstants.CAPABILITY_BULK_FILE,
                    ProtocolConstants.CAPABILITY_BULK_SPEED,
                    ProtocolConstants.CAPABILITY_DIAGNOSTICS_EXPORT,
                    ProtocolConstants.CAPABILITY_AUDIO_LINK,
                    ProtocolConstants.CAPABILITY_AUDIO_CODEC_OPUS
                ),
                "peerCapabilities" to activeRuntime.peerCapabilities.toList()
            )
        )
    }

    private fun emitState(
        phase: SessionPhase,
        sessionId: String?,
        role: SessionRole?,
        reason: String? = null
    ) {
        try {
            if (stateMachine.phase != phase) {
                stateMachine.transitionTo(phase)
            }
        } catch (_: IllegalStateException) {
            stateMachine.reset(phase)
        }

        methodChannel.invokeOnMain(
            "onSessionStateChanged",
            mapOf(
                "state" to phase.eventName,
                "sessionId" to (sessionId ?: ""),
                "role" to (role?.eventName ?: ""),
                "transportRole" to (role?.transportRole()?.eventName ?: ""),
                "groupOwnerAddress" to (groupOwnerAddress ?: ""),
                "disconnectReason" to (reason ?: lastDisconnectReason ?: "")
            )
        )
    }

    private fun transitionForCleanup(
        phase: SessionPhase,
        sessionId: String?,
        role: SessionRole?,
        reason: String
    ) {
        try {
            emitState(phase, sessionId, role, reason)
        } catch (_: Exception) {
            stateMachine.reset(phase)
        }
    }

    private fun emitDisconnectReason(reason: String, sessionId: String?) {
        methodChannel.invokeOnMain(
            "onDisconnectReason",
            mapOf(
                "reason" to reason,
                "sessionId" to (sessionId ?: "")
            )
        )
    }

    private fun stopHeartbeat() {
        heartbeatFuture?.cancel(true)
        heartbeatFuture = null
    }

    private fun closeRuntimeOnly() {
        closeAudioTransport()
        runtime?.transports?.values?.let(::closeTransports)
        runtime = null
    }

    private fun closeTransports(transports: Collection<SessionTransport>) {
        transports.forEach { transport ->
            try {
                transport.cancel()
            } catch (_: Exception) {
            }
        }
    }

    private fun isCurrent(expectedGeneration: Int): Boolean {
        return generation.get() == expectedGeneration
    }

    private fun channelPorts(): List<Pair<ProtocolChannel, Int>> {
        return listOf(
            ProtocolChannel.CONTROL to ProtocolConstants.DEFAULT_CONTROL_PORT,
            ProtocolChannel.BULK to ProtocolConstants.DEFAULT_BULK_PORT
        )
    }

    private fun connectRetryDelayMs(attempt: Int): Long {
        val exponentialDelay = INITIAL_RETRY_DELAY_MS * (2.0.pow(attempt - 1)).toLong()
        return min(exponentialDelay, MAX_RETRY_DELAY_MS)
    }

    private fun appVersion(): String {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun MethodChannel.invokeOnMain(method: String, arguments: Any?) {
        mainHandler.post {
            invokeMethod(method, arguments)
        }
    }

    companion object {
        private const val MAX_CONNECT_ATTEMPTS = 10
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val HANDSHAKE_TIMEOUT_MS = 10000
        private const val HEARTBEAT_INTERVAL_MS = 5000L
        private const val HEARTBEAT_TIMEOUT_MS = 15000L
        private const val DEGRADED_GROUP_CONFIRM_DELAY_MS = 1000L
        private const val BULK_CHUNK_SIZE = 64 * 1024
    }
}
