package com.jingcjie.wifi_direct_cable.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import com.jingcjie.wifi_direct_cable.PermissionManager
import com.jingcjie.wifi_direct_cable.diagnostics.DiagnosticsLogger
import com.jingcjie.wifi_direct_cable.protocol.ProtocolConstants
import com.jingcjie.wifi_direct_cable.protocol.ProtocolFrame
import com.jingcjie.wifi_direct_cable.session.AudioSessionHandler
import com.jingcjie.wifi_direct_cable.session.SessionManager
import com.jingcjie.wifi_direct_cable.session.SessionTransportRole
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

class AudioService(
    private val activity: Activity,
    private val sessionManager: SessionManager,
    private val methodChannel: MethodChannel,
    private val permissionManager: PermissionManager
) : AudioSessionHandler {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val controlExecutor = Executors.newSingleThreadExecutor()
    private val statsExecutor = Executors.newSingleThreadScheduledExecutor()
    private val stateLock = Any()
    private val rtpStatsLock = Any()
    private val running = AtomicBoolean(false)

    private val bytesSent = AtomicLong(0)
    private val bytesReceived = AtomicLong(0)
    private val framesSent = AtomicLong(0)
    private val framesReceived = AtomicLong(0)
    private val rtpPacketsSent = AtomicLong(0)
    private val rtpPacketsReceived = AtomicLong(0)
    private val rtpBytesSent = AtomicLong(0)
    private val rtpBytesReceived = AtomicLong(0)
    private val rtcpPacketsSent = AtomicLong(0)
    private val rtcpPacketsReceived = AtomicLong(0)
    private val sequenceGaps = AtomicLong(0)
    private val udpSendErrors = AtomicLong(0)
    private val udpReceiveErrors = AtomicLong(0)
    private val encodeErrors = AtomicLong(0)
    private val decodeErrors = AtomicLong(0)
    private val rtcpFractionLost = AtomicLong(0)
    private val rtcpJitter = AtomicLong(0)
    private val rtcpRoundTripMs = AtomicLong(-1)
    private val rtcpRemotePacketCount = AtomicLong(0)
    private val rtcpRemoteOctetCount = AtomicLong(0)

    @Volatile
    private var mode: String = MODE_IDLE

    @Volatile
    private var state: String = STATE_IDLE

    @Volatile
    private var streamId: Long = 0L

    @Volatile
    private var offerId: String = ""

    @Volatile
    private var isSender: Boolean = false

    @Volatile
    private var peerReady: Boolean = false

    @Volatile
    private var accepted: Boolean = false

    @Volatile
    private var localSsrc: Long = 0L

    @Volatile
    private var remoteSsrc: Long = 0L

    @Volatile
    private var localTransportRole: SessionTransportRole = SessionTransportRole.CONNECTOR

    @Volatile
    private var peerAddress: String? = null

    @Volatile
    private var receiverProbeRequired: Boolean = false

    @Volatile
    private var streamSource: String = AudioProtocol.SOURCE_MICROPHONE

    @Volatile
    private var latencyMode: AudioLatencyMode = AudioLatencyMode.LOW

    @Volatile
    private var qualityMode: String = AudioProtocol.QUALITY_STANDARD

    @Volatile
    private var streamBitrateBps: Int = AudioProtocol.BITRATE_BPS

    @Volatile
    private var jitterBuffer: JitterBuffer = JitterBuffer(AudioLatencyMode.LOW)

    @Volatile
    private var rtpSocket: DatagramSocket? = null

    @Volatile
    private var rtcpSocket: DatagramSocket? = null

    @Volatile
    private var rtpDestination: SocketAddress? = null

    @Volatile
    private var rtcpDestination: SocketAddress? = null

    @Volatile
    private var captureThread: Thread? = null

    @Volatile
    private var playbackThread: Thread? = null

    @Volatile
    private var rtpReceiveThread: Thread? = null

    @Volatile
    private var rtcpReceiveThread: Thread? = null

    @Volatile
    private var probeThread: Thread? = null

    @Volatile
    private var statsFuture: ScheduledFuture<*>? = null

    @Volatile
    private var rtcpFuture: ScheduledFuture<*>? = null

    private var sendSequenceNumber = 0
    private var sendTimestamp = 0L
    private var expectedReceiveSequence: Int? = null
    private var highestSequenceReceived = 0L
    private var lastTransit: Long? = null
    private var interarrivalJitter = 0.0
    private var lastRemoteSenderReportCompact = 0L
    private var lastRemoteSenderReportReceivedAtMs = 0L
    private var lastLocalSenderReportCompact = 0L
    private var lastLocalSenderReportSentAtMs = 0L

    init {
        sessionManager.registerAudioHandler(this)
    }

    fun getAudioSupport(): Map<String, Any> = AudioSupport.supportMap()

    fun startAudio(
        requestedMode: String?,
        source: String?,
        encoding: String?,
        requestedLatencyMode: String?,
        requestedQualityMode: String?,
        result: MethodChannel.Result
    ) {
        val normalizedMode = requestedMode ?: MODE_RECEIVE
        if (normalizedMode != MODE_SEND && normalizedMode != MODE_RECEIVE) {
            result.error("INVALID_ARGUMENT", "Audio mode must be send or receive", null)
            return
        }
        if ((source ?: AudioProtocol.SOURCE_MICROPHONE) != AudioProtocol.SOURCE_MICROPHONE) {
            result.error(AudioProtocol.ERROR_UNSUPPORTED, "Only microphone input is supported", null)
            return
        }
        if ((encoding ?: AudioProtocol.CODEC_OPUS) != AudioProtocol.CODEC_OPUS) {
            result.error(AudioProtocol.ERROR_UNSUPPORTED, "Only Opus encoding is supported", null)
            return
        }

        val info = sessionManager.getAudioSessionInfo()
        if (info == null) {
            result.error("SESSION_NOT_READY", "The WDCable session is not ready", null)
            return
        }
        if (!peerSupportsAudio(info.peerCapabilities)) {
            result.error(AudioProtocol.ERROR_RTP_UNSUPPORTED, "The connected peer does not advertise RTP/libopus Audio Link", null)
            emitAudioError(AudioProtocol.ERROR_RTP_UNSUPPORTED, "The connected peer does not support RTP/libopus Audio Link", 0L)
            return
        }
        if (!NativeOpus.available) {
            result.error(AudioProtocol.ERROR_CODEC_UNAVAILABLE, "libopus runtime is not available", null)
            emitAudioError(AudioProtocol.ERROR_CODEC_UNAVAILABLE, "libopus runtime is not available", 0L)
            return
        }
        val selectedQualityMode = if (normalizedMode == MODE_SEND) {
            AudioProtocol.normalizeQualityMode(requestedQualityMode)
        } else {
            AudioProtocol.QUALITY_STANDARD
        }
        if (
            normalizedMode == MODE_SEND &&
            AudioProtocol.requiresQualitySelectionCapability(selectedQualityMode) &&
            !peerSupportsAudioQualitySelection(info.peerCapabilities)
        ) {
            result.error(AudioProtocol.ERROR_RTP_UNSUPPORTED, "The connected peer does not support sender audio quality selection", null)
            emitAudioError(AudioProtocol.ERROR_RTP_UNSUPPORTED, "The connected peer does not support sender audio quality selection", 0L)
            return
        }

        synchronized(stateLock) {
            if (state != STATE_IDLE) {
                result.error(AudioProtocol.ERROR_BUSY, "Audio Link is already active", null)
                return
            }
            latencyMode = if (normalizedMode == MODE_SEND) {
                AudioLatencyMode.fromWire(requestedLatencyMode)
            } else {
                AudioLatencyMode.LOW
            }
            qualityMode = selectedQualityMode
            streamBitrateBps = AudioProtocol.bitrateForQualityMode(selectedQualityMode)
            jitterBuffer = JitterBuffer(latencyMode)
            localTransportRole = info.transportRole
            peerAddress = info.peerAddress
        }

        if (normalizedMode == MODE_RECEIVE) {
            startReceive(result)
            return
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startSend(result)
        } else {
            permissionManager.requestRecordAudioPermission { granted ->
                if (granted) {
                    startSend(result)
                } else {
                    emitAudioError(AudioProtocol.ERROR_PERMISSION_DENIED, "Microphone permission denied", 0L)
                    result.error(AudioProtocol.ERROR_PERMISSION_DENIED, "Microphone permission denied", null)
                }
            }
        }
    }

    fun stopAudio(result: MethodChannel.Result? = null) {
        controlExecutor.execute {
            val currentStreamId = streamId
            try {
                if (currentStreamId != 0L && state != STATE_IDLE) {
                    sessionManager.sendAudioControl(AudioProtocol.stop(currentStreamId, "local_stop"))
                }
                if (mode == MODE_RECEIVE) {
                    sessionManager.sendAudioControl(AudioProtocol.receiveStopped(currentStreamId))
                }
            } catch (exception: Exception) {
                DiagnosticsLogger.log(
                    "audio",
                    "Audio stop control send failed",
                    mapOf("errorType" to exception.javaClass.simpleName, "error" to exception.message)
                )
            } finally {
                cleanupLocal("stopped", emitStopped = true)
                result?.successOnMain("Audio stopped")
            }
        }
    }

    fun cleanup() {
        cleanupLocal("service_cleanup", emitStopped = false)
        controlExecutor.shutdownNow()
        statsExecutor.shutdownNow()
    }

    override fun onAudioControlMessage(metadata: JSONObject) {
        when (metadata.optString("kind")) {
            AudioProtocol.KIND_RECEIVE_READY -> {
                peerReady = true
                emitAudioState(state, "Peer is ready to receive audio")
            }
            AudioProtocol.KIND_RECEIVE_STOPPED -> {
                peerReady = false
                emitAudioState(state, "Peer stopped receiving audio")
            }
            AudioProtocol.KIND_OFFER -> handleOffer(metadata)
            AudioProtocol.KIND_ACCEPT -> handleAccept(metadata)
            AudioProtocol.KIND_TRANSPORT -> {
                DiagnosticsLogger.log("audio", "Ignoring retired TCP audio.transport for v2", mapOf("streamId" to metadata.optLong("streamId", 0L)))
            }
            AudioProtocol.KIND_STOP -> cleanupLocal(metadata.optString("reason", "peer_stop"), emitStopped = true)
        }
    }

    override fun onAudioFrame(frame: ProtocolFrame) {
        DiagnosticsLogger.log("audio", "Ignoring retired TCP audio.frame for v2", mapOf("streamId" to frame.streamId))
    }

    override fun onAudioFeatureError(code: String, message: String, streamId: Long) {
        cleanupLocal("peer_error", emitStopped = true)
        emitAudioError(code, message, streamId)
    }

    override fun onAudioTransportClosed(reason: String) {
        DiagnosticsLogger.log("audio", "Ignoring retired TCP audio channel close for v2", mapOf("reason" to reason))
    }

    override fun onSessionEnded(reason: String) {
        cleanupLocal(reason, emitStopped = true)
    }

    private fun startReceive(result: MethodChannel.Result) {
        synchronized(stateLock) {
            mode = MODE_RECEIVE
            state = STATE_RECEIVE_READY
            streamId = 0L
            offerId = ""
            isSender = false
            accepted = false
            localSsrc = randomSsrc()
            remoteSsrc = 0L
            receiverProbeRequired = false
            streamSource = AudioProtocol.SOURCE_MICROPHONE
        }
        resetStats()
        controlExecutor.execute {
            try {
                sessionManager.sendAudioControl(AudioProtocol.receiveReady(0L))
                emitAudioState(STATE_RECEIVE_READY, "Ready to receive microphone audio")
                result.successOnMain("Audio receive mode started")
            } catch (exception: Exception) {
                cleanupLocal("receive_ready_failed", emitStopped = true)
                result.errorOnMain(
                    AudioProtocol.ERROR_TRANSPORT_FAILED,
                    "Failed to send receive-ready message: ${exception.describe()}",
                    null
                )
            }
        }
    }

    private fun startSend(result: MethodChannel.Result) {
        val info = sessionManager.getAudioSessionInfo()
        if (info == null) {
            result.error("SESSION_NOT_READY", "The WDCable session is not ready", null)
            return
        }
        val newStreamId = nextStreamId()
        val newOfferId = UUID.randomUUID().toString()
        val ssrc = randomSsrc()
        synchronized(stateLock) {
            mode = MODE_SEND
            state = STATE_OFFER_SENT
            streamId = newStreamId
            offerId = newOfferId
            isSender = true
            accepted = false
            localSsrc = ssrc
            remoteSsrc = 0L
            localTransportRole = info.transportRole
            peerAddress = info.peerAddress
            receiverProbeRequired = false
            streamSource = AudioProtocol.SOURCE_MICROPHONE
        }
        resetStats()
        controlExecutor.execute {
            try {
                sessionManager.sendAudioControl(
                    AudioProtocol.offer(
                        newStreamId,
                        newOfferId,
                        ssrc,
                        info.transportRole,
                        latencyMode.wireValue,
                        qualityMode,
                        streamBitrateBps
                    )
                )
                emitAudioState(STATE_OFFER_SENT, "Audio offer sent")
                result.successOnMain("Audio send offer sent")
            } catch (exception: Exception) {
                cleanupLocal("offer_failed", emitStopped = true)
                result.errorOnMain(
                    AudioProtocol.ERROR_TRANSPORT_FAILED,
                    "Failed to send audio offer: ${exception.describe()}",
                    null
                )
            }
        }
    }

    private fun handleOffer(metadata: JSONObject) {
        val offer = try {
            AudioProtocol.parseOffer(metadata)
        } catch (exception: IllegalArgumentException) {
            sessionManager.sendAudioError(0L, AudioProtocol.ERROR_RTP_UNSUPPORTED, exception.message ?: "Invalid audio offer")
            return
        }
        val info = sessionManager.getAudioSessionInfo()
        if (info == null) {
            sessionManager.sendAudioError(offer.streamId, AudioProtocol.ERROR_TRANSPORT_FAILED, "The WDCable session is not ready")
            return
        }
        if (
            mode == MODE_RECEIVE &&
            (state == STATE_CONNECTING || state == STATE_STREAMING) &&
            AudioProtocol.isSameNegotiation(streamId, offerId, offer.streamId, offer.offerId)
        ) {
            DiagnosticsLogger.log(
                "audio",
                "Duplicate audio offer ignored",
                mapOf("streamId" to offer.streamId, "offerId" to offer.offerId)
            )
            return
        }
        if (state != STATE_RECEIVE_READY || mode != MODE_RECEIVE) {
            sessionManager.sendAudioError(offer.streamId, AudioProtocol.ERROR_RECEIVER_NOT_READY, "Receiver has not started Audio Link receive mode")
            return
        }
        val offerRejectionReason = AudioProtocol.offerRejectionReason(offer)
        if (offerRejectionReason != null) {
            DiagnosticsLogger.log(
                "audio",
                "Audio offer rejected",
                mapOf("streamId" to offer.streamId, "reason" to offerRejectionReason)
            )
            sessionManager.sendAudioError(
                offer.streamId,
                AudioProtocol.ERROR_RTP_UNSUPPORTED,
                "Unsupported RTP/libopus audio offer: $offerRejectionReason"
            )
            return
        }

        val ssrc = randomSsrc()
        val probeRequired = AudioProtocol.receiverProbeRequired(info.transportRole)
        val offeredLatencyMode = AudioLatencyMode.fromWire(offer.latencyMode)
        synchronized(stateLock) {
            streamId = offer.streamId
            offerId = offer.offerId
            isSender = false
            accepted = true
            state = STATE_CONNECTING
            localSsrc = ssrc
            remoteSsrc = offer.rtpSsrc
            localTransportRole = info.transportRole
            peerAddress = info.peerAddress
            receiverProbeRequired = probeRequired
            streamSource = offer.source
            latencyMode = offeredLatencyMode
            qualityMode = offer.qualityMode
            streamBitrateBps = offer.bitrateBps
            jitterBuffer = JitterBuffer(offeredLatencyMode)
        }
        try {
            sessionManager.sendAudioControl(
                AudioProtocol.accept(
                    offer.streamId,
                    offer.offerId,
                    ssrc,
                    info.transportRole,
                    probeRequired,
                    offer.latencyMode,
                    offer.qualityMode,
                    offer.bitrateBps
                )
            )
            emitAudioState(STATE_CONNECTING, "Audio offer accepted")
            startUdpTransport()
        } catch (exception: Exception) {
            failAudio(AudioProtocol.ERROR_TRANSPORT_FAILED, "Failed to accept audio offer: ${exception.message}", offer.streamId)
        }
    }

    private fun handleAccept(metadata: JSONObject) {
        val accept = try {
            AudioProtocol.parseAccept(metadata)
        } catch (exception: IllegalArgumentException) {
            failAudio(AudioProtocol.ERROR_RTP_UNSUPPORTED, exception.message ?: "Invalid audio accept", streamId)
            return
        }
        val info = sessionManager.getAudioSessionInfo() ?: return

        if (!isSender || accept.streamId != streamId || accept.offerId != offerId) {
            return
        }
        if (state == STATE_CONNECTING || state == STATE_STREAMING) {
            DiagnosticsLogger.log(
                "audio",
                "Duplicate audio accept ignored",
                mapOf("streamId" to accept.streamId, "offerId" to accept.offerId)
            )
            return
        }
        val acceptRejectionReason = AudioProtocol.acceptRejectionReason(accept)
        if (acceptRejectionReason != null) {
            failAudio(
                AudioProtocol.ERROR_RTP_UNSUPPORTED,
                "Peer accepted an unsupported RTP/libopus format: $acceptRejectionReason",
                accept.streamId
            )
            return
        }
        if (
            accept.latencyMode != latencyMode.wireValue ||
            accept.qualityMode != qualityMode ||
            accept.bitrateBps != streamBitrateBps
        ) {
            failAudio(
                AudioProtocol.ERROR_RTP_UNSUPPORTED,
                "Peer accepted a different RTP/libopus stream configuration: " +
                    "offered latencyMode=${latencyMode.wireValue},qualityMode=$qualityMode,bitrateBps=$streamBitrateBps; " +
                    "accepted latencyMode=${accept.latencyMode},qualityMode=${accept.qualityMode},bitrateBps=${accept.bitrateBps}",
                accept.streamId
            )
            return
        }

        synchronized(stateLock) {
            accepted = true
            state = STATE_CONNECTING
            remoteSsrc = accept.rtpSsrc
            localTransportRole = info.transportRole
            peerAddress = info.peerAddress
            receiverProbeRequired = accept.receiverProbeRequired
        }
        emitAudioState(STATE_CONNECTING, "Audio offer accepted by peer")
        startUdpTransport()
    }

    private fun startUdpTransport() {
        if (!accepted || running.get()) return
        try {
            closeUdpSockets()
            val localRole = localTransportRole
            val newRtpSocket: DatagramSocket
            val newRtcpSocket: DatagramSocket
            if (localRole == SessionTransportRole.LISTENER) {
                newRtpSocket = bindUdpSocket(AudioProtocol.RTP_PORT)
                newRtcpSocket = bindUdpSocket(AudioProtocol.RTCP_PORT)
            } else {
                newRtpSocket = DatagramSocket()
                newRtcpSocket = DatagramSocket()
                val host = peerAddress
                if (!host.isNullOrBlank()) {
                    rtpDestination = InetSocketAddress(host, AudioProtocol.RTP_PORT)
                    rtcpDestination = InetSocketAddress(host, AudioProtocol.RTCP_PORT)
                }
            }
            newRtpSocket.soTimeout = SOCKET_POLL_TIMEOUT_MS
            newRtcpSocket.soTimeout = SOCKET_POLL_TIMEOUT_MS
            rtpSocket = newRtpSocket
            rtcpSocket = newRtcpSocket
            running.set(true)

            DiagnosticsLogger.log(
                "audio",
                "Audio RTP/RTCP sockets opened",
                mapOf(
                    "streamId" to streamId,
                    "localTransportRole" to localRole.eventName,
                    "audioPortOwnerRole" to SessionTransportRole.LISTENER.eventName,
                    "rtpBindEndpoint" to newRtpSocket.localSocketAddress.toString(),
                    "rtcpBindEndpoint" to newRtcpSocket.localSocketAddress.toString(),
                    "receiverProbeRequired" to receiverProbeRequired,
                    "latencyMode" to latencyMode.wireValue,
                    "qualityMode" to qualityMode,
                    "streamBitrateBps" to streamBitrateBps
                )
            )

            startRtpReceiveLoop()
            startRtcpReceiveLoop()
            startStats()
            startRtcpReports()

            if (isSender) {
                if (AudioProtocol.senderWaitsForReceiverProbe(localRole, receiverProbeRequired)) {
                    waitForReceiverProbeThenStartCapture()
                } else {
                    startCapture()
                    setStreaming("Audio Link streaming")
                }
            } else {
                if (localRole == SessionTransportRole.CONNECTOR) {
                    startProbeLoop()
                }
                startPlayback()
                setStreaming("Audio Link streaming")
            }
        } catch (exception: Exception) {
            failAudio(
                if (exception is java.net.BindException) AudioProtocol.ERROR_RTP_BIND_FAILED else AudioProtocol.ERROR_TRANSPORT_FAILED,
                "Failed to open RTP/RTCP sockets: ${exception.message}",
                streamId
            )
        }
    }

    private fun bindUdpSocket(port: Int): DatagramSocket {
        return DatagramSocket(null).also { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(port))
        }
    }

    private fun waitForReceiverProbeThenStartCapture() {
        Thread({
            val deadline = System.currentTimeMillis() + PROBE_TIMEOUT_MS
            while (running.get() && (rtpDestination == null || rtcpDestination == null) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            if (!running.get()) return@Thread
            if (rtpDestination == null || rtcpDestination == null) {
                val details =
                    "role=${localTransportRole.eventName} " +
                        "rtpLocal=${socketEndpoint(rtpSocket)} rtpDestination=${rtpDestination ?: "unknown"} " +
                        "rtcpLocal=${socketEndpoint(rtcpSocket)} rtcpDestination=${rtcpDestination ?: "unknown"} " +
                        "socketError=no_probe_received"
                failAudio(
                    AudioProtocol.ERROR_RTP_PROBE_TIMEOUT,
                    "Timed out waiting for receiver UDP path probe. $details",
                    streamId
                )
                return@Thread
            }
            startCapture()
            setStreaming("Audio Link streaming")
        }, "WDCableAudioProbeWait").also { it.start() }
    }

    private fun startProbeLoop() {
        if (probeThread?.isAlive == true) return
        probeThread = Thread({
            val host = peerAddress ?: return@Thread
            val rtpProbeTarget = InetSocketAddress(host, AudioProtocol.RTP_PORT)
            val rtcpProbeTarget = InetSocketAddress(host, AudioProtocol.RTCP_PORT)
            while (running.get() && rtpPacketsReceived.get() == 0L) {
                val rtpSent = sendProbeDatagram(
                    "RTP",
                    rtpSocket,
                    rtpProbeTarget,
                    AudioProtocol.RTP_PROBE_PAYLOAD
                )
                val rtcpSent = sendProbeDatagram(
                    "RTCP",
                    rtcpSocket,
                    rtcpProbeTarget,
                    AudioProtocol.RTCP_PROBE_PAYLOAD
                )
                if (rtpSent && rtcpSent) {
                    DiagnosticsLogger.log("audio", "UDP audio path probe sent", mapOf("rtpTarget" to rtpProbeTarget.toString(), "rtcpTarget" to rtcpProbeTarget.toString()))
                }
                Thread.sleep(PROBE_INTERVAL_MS)
            }
        }, "WDCableAudioProbe").also { it.start() }
    }

    private fun startRtpReceiveLoop() {
        if (rtpReceiveThread?.isAlive == true) return
        rtpReceiveThread = Thread({
            val buffer = ByteArray(MAX_DATAGRAM_BYTES)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    val socket = rtpSocket ?: break
                    socket.receive(packet)
                    if (AudioProtocol.isRtpProbePayload(packet.data, packet.length)) {
                        rtpDestination = packet.socketAddress
                        DiagnosticsLogger.log("audio", "RTP path probe received", mapOf("remoteEndpoint" to packet.socketAddress.toString()))
                        continue
                    }
                    val bytes = packet.data.copyOf(packet.length)
                    val rtp = RtpCodec.decode(bytes)
                    if (rtp.payloadType != AudioProtocol.RTP_PAYLOAD_TYPE || (remoteSsrc != 0L && rtp.ssrc != remoteSsrc)) {
                        continue
                    }
                    rtpPacketsReceived.incrementAndGet()
                    rtpBytesReceived.addAndGet(packet.length.toLong())
                    bytesReceived.addAndGet(rtp.payload.size.toLong())
                    framesReceived.incrementAndGet()
                    updateRtpReceiveStats(rtp)
                    if (!isSender) {
                        jitterBuffer.add(
                            RtpAudioFrame(
                                sequenceNumber = rtp.sequenceNumber,
                                timestamp = rtp.timestamp,
                                receivedAtMs = System.currentTimeMillis(),
                                payload = rtp.payload
                            )
                        )
                    }
                    if (rtpDestination == null) {
                        rtpDestination = packet.socketAddress
                    }
                } catch (_: SocketTimeoutException) {
                } catch (exception: Exception) {
                    if (running.get()) {
                        udpReceiveErrors.incrementAndGet()
                        DiagnosticsLogger.log("audio", "RTP receive failed", mapOf("errorType" to exception.javaClass.simpleName, "error" to exception.message))
                    }
                }
            }
        }, "WDCableAudioRtpReceive").also { it.start() }
    }

    private fun startRtcpReceiveLoop() {
        if (rtcpReceiveThread?.isAlive == true) return
        rtcpReceiveThread = Thread({
            val buffer = ByteArray(MAX_DATAGRAM_BYTES)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    val socket = rtcpSocket ?: break
                    socket.receive(packet)
                    if (AudioProtocol.isRtcpProbePayload(packet.data, packet.length)) {
                        rtcpDestination = packet.socketAddress
                        DiagnosticsLogger.log("audio", "RTCP path probe received", mapOf("remoteEndpoint" to packet.socketAddress.toString()))
                        continue
                    }
                    rtcpDestination = packet.socketAddress
                    rtcpPacketsReceived.incrementAndGet()
                    handleRtcpPacket(packet.data.copyOf(packet.length))
                } catch (_: SocketTimeoutException) {
                } catch (exception: Exception) {
                    if (running.get()) {
                        udpReceiveErrors.incrementAndGet()
                        DiagnosticsLogger.log("audio", "RTCP receive failed", mapOf("errorType" to exception.javaClass.simpleName, "error" to exception.message))
                    }
                }
            }
        }, "WDCableAudioRtcpReceive").also { it.start() }
    }

    private fun updateRtpReceiveStats(packet: RtpPacket) {
        synchronized(rtpStatsLock) {
            val expected = expectedReceiveSequence
            if (expected != null) {
                val distance = RtpCodec.sequenceDistance(expected, packet.sequenceNumber)
                if (distance in 1..0x7fff) {
                    sequenceGaps.addAndGet(distance.toLong())
                }
            }
            expectedReceiveSequence = RtpCodec.nextSequence(packet.sequenceNumber)
            highestSequenceReceived = packet.sequenceNumber.toLong()
            val arrivalTimestamp = System.currentTimeMillis() * (AudioProtocol.RTP_CLOCK_RATE / 1000L)
            val transit = arrivalTimestamp - packet.timestamp
            val previousTransit = lastTransit
            if (previousTransit != null) {
                val delta = abs(transit - previousTransit)
                interarrivalJitter += (delta - interarrivalJitter) / 16.0
                rtcpJitter.set(interarrivalJitter.roundToLong())
            }
            lastTransit = transit
        }
    }

    private fun handleRtcpPacket(datagram: ByteArray) {
        try {
            when (val packet = RtcpCodec.decode(datagram)) {
                is RtcpPacket.SenderReport -> {
                    lastRemoteSenderReportCompact = packet.report.compactNtp
                    lastRemoteSenderReportReceivedAtMs = System.currentTimeMillis()
                    rtcpRemotePacketCount.set(packet.report.packetCount)
                    rtcpRemoteOctetCount.set(packet.report.octetCount)
                }
                is RtcpPacket.ReceiverReport -> {
                    rtcpFractionLost.set(packet.report.fractionLost.toLong())
                    rtcpJitter.set(packet.report.interarrivalJitter)
                    if (packet.report.lastSenderReport != 0L &&
                        packet.report.lastSenderReport == lastLocalSenderReportCompact &&
                        lastLocalSenderReportSentAtMs != 0L
                    ) {
                        val elapsed = System.currentTimeMillis() - lastLocalSenderReportSentAtMs
                        val rtt = elapsed - RtcpCodec.dlsrToMillis(packet.report.delaySinceLastSenderReport)
                        rtcpRoundTripMs.set(rtt.coerceAtLeast(0L))
                    }
                }
            }
        } catch (exception: Exception) {
            DiagnosticsLogger.log("audio", "RTCP parse failed", mapOf("errorType" to exception.javaClass.simpleName, "error" to exception.message))
        }
    }

    private fun startCapture() {
        if (captureThread?.isAlive == true) return
        captureThread = Thread({ captureLoop() }, "WDCableAudioCapture").also { it.start() }
    }

    private fun startPlayback() {
        if (playbackThread?.isAlive == true) return
        playbackThread = Thread({ playbackLoop() }, "WDCableAudioPlayback").also { it.start() }
    }

    @SuppressLint("MissingPermission")
    private fun captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var recorder: AudioRecord? = null
        var encoderHandle = 0L
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                AudioProtocol.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val recordBufferSize = max(minBufferSize, AudioProtocol.PCM_FRAME_BYTES * 4)
            recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(AudioProtocol.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(recordBufferSize)
                .build()

            encoderHandle = NativeOpus.createEncoder(AudioProtocol.SAMPLE_RATE, AudioProtocol.CHANNELS, streamBitrateBps)
            check(encoderHandle != 0L) { "Could not create libopus encoder" }
            recorder.startRecording()

            val pcm = ByteArray(AudioProtocol.PCM_FRAME_BYTES)
            val encoded = ByteArray(MAX_OPUS_PACKET_BYTES)
            while (running.get()) {
                val read = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue
                if (read < pcm.size) {
                    pcm.fill(0, read, pcm.size)
                }
                val encodedBytes = NativeOpus.encode(encoderHandle, pcm, AudioProtocol.SAMPLES_PER_PACKET, encoded)
                if (encodedBytes <= 0) {
                    encodeErrors.incrementAndGet()
                    continue
                }
                val payload = encoded.copyOf(encodedBytes)
                val packet = RtpPacket(
                    payloadType = AudioProtocol.RTP_PAYLOAD_TYPE,
                    sequenceNumber = sendSequenceNumber,
                    timestamp = sendTimestamp,
                    ssrc = localSsrc,
                    payload = payload
                )
                val datagram = RtpCodec.encode(packet)
                sendDatagram(rtpSocket, rtpDestination, datagram)
                bytesSent.addAndGet(payload.size.toLong())
                framesSent.incrementAndGet()
                rtpPacketsSent.incrementAndGet()
                rtpBytesSent.addAndGet(datagram.size.toLong())
                sendSequenceNumber = RtpCodec.nextSequence(sendSequenceNumber)
                sendTimestamp = RtpCodec.nextTimestamp(sendTimestamp)
            }
        } catch (exception: Exception) {
            if (running.get()) {
                failAudio(AudioProtocol.ERROR_CAPTURE_FAILED, "Audio capture failed: ${exception.message}", streamId)
            }
        } finally {
            try {
                recorder?.stop()
            } catch (_: Exception) {
            }
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
            NativeOpus.destroyEncoder(encoderHandle)
        }
    }

    private fun playbackLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var decoderHandle = 0L
        var audioTrack: AudioTrack? = null
        try {
            val minTrackBuffer = AudioTrack.getMinBufferSize(
                AudioProtocol.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(AudioProtocol.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minTrackBuffer, AudioProtocol.PCM_FRAME_BYTES * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            decoderHandle = NativeOpus.createDecoder(AudioProtocol.SAMPLE_RATE, AudioProtocol.CHANNELS)
            check(decoderHandle != 0L) { "Could not create libopus decoder" }
            audioTrack.play()

            val pcm = ByteArray(AudioProtocol.PCM_FRAME_BYTES)
            val silence = ByteArray(AudioProtocol.PCM_FRAME_BYTES)
            while (running.get()) {
                when (val next = jitterBuffer.pollForPlayback()) {
                    is JitterBufferPoll.Packet -> {
                        val decodedSamples = NativeOpus.decode(decoderHandle, next.frame.payload, AudioProtocol.SAMPLES_PER_PACKET, pcm)
                        if (decodedSamples > 0) {
                            audioTrack.write(pcm, 0, decodedSamples * AudioProtocol.CHANNELS * AudioProtocol.BYTES_PER_SAMPLE)
                        } else {
                            decodeErrors.incrementAndGet()
                            audioTrack.write(silence, 0, silence.size)
                        }
                    }
                    JitterBufferPoll.Missing -> {
                        val decodedSamples = NativeOpus.decode(decoderHandle, null, AudioProtocol.SAMPLES_PER_PACKET, pcm)
                        if (decodedSamples > 0) {
                            audioTrack.write(pcm, 0, decodedSamples * AudioProtocol.CHANNELS * AudioProtocol.BYTES_PER_SAMPLE)
                        } else {
                            decodeErrors.incrementAndGet()
                            audioTrack.write(silence, 0, silence.size)
                        }
                    }
                    is JitterBufferPoll.Wait -> {
                        Thread.sleep(next.waitMs.coerceIn(1L, AudioProtocol.FRAME_DURATION_MS.toLong()))
                    }
                }
            }
        } catch (exception: Exception) {
            if (running.get()) {
                failAudio(AudioProtocol.ERROR_PLAYBACK_FAILED, "Audio playback failed: ${exception.message}", streamId)
            }
        } finally {
            try {
                audioTrack?.stop()
            } catch (_: Exception) {
            }
            try {
                audioTrack?.release()
            } catch (_: Exception) {
            }
            NativeOpus.destroyDecoder(decoderHandle)
        }
    }

    private fun startRtcpReports() {
        stopRtcpReports()
        rtcpFuture = statsExecutor.scheduleAtFixedRate({
            try {
                sendRtcpReport()
            } catch (exception: Exception) {
                DiagnosticsLogger.log("audio", "RTCP report send failed", mapOf("errorType" to exception.javaClass.simpleName, "error" to exception.message))
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun stopRtcpReports() {
        rtcpFuture?.cancel(true)
        rtcpFuture = null
    }

    private fun sendRtcpReport() {
        val destination = rtcpDestination ?: return
        val socket = rtcpSocket ?: return
        if (isSender) {
            val now = System.currentTimeMillis()
            val report = RtcpCodec.encodeSenderReport(
                senderSsrc = localSsrc,
                rtpTimestamp = sendTimestamp,
                packetCount = rtpPacketsSent.get(),
                octetCount = bytesSent.get(),
                nowMs = now
            )
            val parsed = RtcpCodec.decode(report) as RtcpPacket.SenderReport
            lastLocalSenderReportCompact = parsed.report.compactNtp
            lastLocalSenderReportSentAtMs = now
            sendDatagram(socket, destination, report)
        } else {
            val totalExpected = rtpPacketsReceived.get() + sequenceGaps.get()
            val fractionLost = if (totalExpected <= 0L) 0 else ((sequenceGaps.get() * 256L) / totalExpected).coerceIn(0L, 255L).toInt()
            val report = RtcpCodec.encodeReceiverReport(
                RtcpReceiverReport(
                    receiverSsrc = localSsrc,
                    senderSsrc = remoteSsrc,
                    fractionLost = fractionLost,
                    cumulativePacketsLost = sequenceGaps.get().coerceAtMost(0x7fffffL).toInt(),
                    highestSequenceReceived = highestSequenceReceived,
                    interarrivalJitter = rtcpJitter.get(),
                    lastSenderReport = lastRemoteSenderReportCompact,
                    delaySinceLastSenderReport = if (lastRemoteSenderReportCompact == 0L) {
                        0
                    } else {
                        RtcpCodec.delaySinceLastSenderReport(lastRemoteSenderReportReceivedAtMs)
                    }
                )
            )
            sendDatagram(socket, destination, report)
        }
        rtcpPacketsSent.incrementAndGet()
    }

    private fun sendDatagram(socket: DatagramSocket?, destination: SocketAddress?, data: ByteArray) {
        val actualSocket = socket ?: throw IOException("UDP socket is not open")
        val actualDestination = destination ?: throw IOException("UDP destination is not known")
        actualSocket.send(DatagramPacket(data, data.size, actualDestination))
    }

    private fun sendProbeDatagram(
        probeName: String,
        socket: DatagramSocket?,
        destination: SocketAddress,
        data: ByteArray
    ): Boolean {
        return try {
            sendDatagram(socket, destination, data)
            true
        } catch (exception: Exception) {
            udpSendErrors.incrementAndGet()
            DiagnosticsLogger.log(
                "audio",
                "$probeName UDP audio path probe failed",
                mapOf(
                    "streamId" to streamId,
                    "localTransportRole" to localTransportRole.eventName,
                    "localEndpoint" to socketEndpoint(socket),
                    "destination" to destination.toString(),
                    "socketError" to "${exception.javaClass.simpleName}: ${exception.message ?: ""}"
                )
            )
            false
        }
    }

    private fun setStreaming(message: String) {
        synchronized(stateLock) {
            state = STATE_STREAMING
        }
        emitAudioState(STATE_STREAMING, message)
    }

    private fun failAudio(code: String, message: String, targetStreamId: Long) {
        DiagnosticsLogger.log("audio", "Audio Link failed", mapOf("code" to code, "message" to message, "streamId" to targetStreamId))
        try {
            if (targetStreamId != 0L) {
                sessionManager.sendAudioError(targetStreamId, code, message)
            }
        } catch (_: Exception) {
        }
        cleanupLocal("failed", emitStopped = true)
        emitAudioError(code, message, targetStreamId)
    }

    private fun cleanupLocal(reason: String, emitStopped: Boolean) {
        val previousState = state
        running.set(false)
        stopStats()
        stopRtcpReports()
        closeUdpSockets()
        jitterBuffer.clear()
        sessionManager.closeAudioTransport()
        synchronized(stateLock) {
            mode = MODE_IDLE
            state = STATE_IDLE
            streamId = 0L
            offerId = ""
            isSender = false
            accepted = false
            peerReady = false
            localSsrc = 0L
            remoteSsrc = 0L
            receiverProbeRequired = false
            streamSource = AudioProtocol.SOURCE_MICROPHONE
            latencyMode = AudioLatencyMode.LOW
            qualityMode = AudioProtocol.QUALITY_STANDARD
            streamBitrateBps = AudioProtocol.BITRATE_BPS
            jitterBuffer = JitterBuffer(AudioLatencyMode.LOW)
            rtpDestination = null
            rtcpDestination = null
            expectedReceiveSequence = null
            highestSequenceReceived = 0L
            lastTransit = null
            interarrivalJitter = 0.0
            lastRemoteSenderReportCompact = 0L
            lastRemoteSenderReportReceivedAtMs = 0L
            lastLocalSenderReportCompact = 0L
            lastLocalSenderReportSentAtMs = 0L
        }
        if (emitStopped && previousState != STATE_IDLE) {
            emitAudioState(STATE_IDLE, reason)
        }
    }

    private fun closeUdpSockets() {
        try {
            rtpSocket?.close()
        } catch (_: Exception) {
        }
        try {
            rtcpSocket?.close()
        } catch (_: Exception) {
        }
        rtpSocket = null
        rtcpSocket = null
    }

    private fun startStats() {
        stopStats()
        var lastBytes = 0L
        statsFuture = statsExecutor.scheduleAtFixedRate({
            val totalBytes = if (isSender) bytesSent.get() else bytesReceived.get()
            val bitrateBps = (totalBytes - lastBytes).coerceAtLeast(0L) * 8L
            lastBytes = totalBytes
            val snapshot = jitterBuffer.snapshot()
            methodChannel.invokeOnMain(
                "onAudioStats",
                mapOf(
                    "mode" to mode,
                    "state" to state,
                    "streamId" to streamId,
                    "latencyMode" to latencyMode.wireValue,
                    "qualityMode" to qualityMode,
                    "configuredBitrateBps" to streamBitrateBps,
                    "bitrateBps" to bitrateBps,
                    "bufferLevelMs" to snapshot.bufferLevelMs,
                    "framesSent" to framesSent.get(),
                    "framesReceived" to framesReceived.get(),
                    "droppedFrames" to snapshot.droppedFrames,
                    "packetLossCount" to sequenceGaps.get(),
                    "latePacketDrops" to snapshot.latePacketDrops,
                    "overflowDrops" to snapshot.overflowDrops,
                    "duplicatePackets" to snapshot.duplicatePackets,
                    "reorderedPackets" to snapshot.reorderedPackets,
                    "underflowCount" to snapshot.underflowCount,
                    "plcCount" to snapshot.plcCount,
                    "rtpPacketsSent" to rtpPacketsSent.get(),
                    "rtpPacketsReceived" to rtpPacketsReceived.get(),
                    "rtpBytesSent" to rtpBytesSent.get(),
                    "rtpBytesReceived" to rtpBytesReceived.get(),
                    "rtcpPacketsSent" to rtcpPacketsSent.get(),
                    "rtcpPacketsReceived" to rtcpPacketsReceived.get(),
                    "rtcpFractionLost" to rtcpFractionLost.get(),
                    "rtcpJitter" to rtcpJitter.get(),
                    "rtcpPacketCount" to rtcpRemotePacketCount.get(),
                    "rtcpOctetCount" to rtcpRemoteOctetCount.get(),
                    "roundTripMs" to rtcpRoundTripMs.get(),
                    "latencyMs" to rtcpRoundTripMs.get(),
                    "encodeErrorCount" to encodeErrors.get(),
                    "decodeErrorCount" to decodeErrors.get(),
                    "udpSendErrorCount" to udpSendErrors.get(),
                    "udpReceiveErrorCount" to udpReceiveErrors.get()
                )
            )
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun stopStats() {
        statsFuture?.cancel(true)
        statsFuture = null
    }

    private fun resetStats() {
        bytesSent.set(0L)
        bytesReceived.set(0L)
        framesSent.set(0L)
        framesReceived.set(0L)
        rtpPacketsSent.set(0L)
        rtpPacketsReceived.set(0L)
        rtpBytesSent.set(0L)
        rtpBytesReceived.set(0L)
        rtcpPacketsSent.set(0L)
        rtcpPacketsReceived.set(0L)
        sequenceGaps.set(0L)
        udpSendErrors.set(0L)
        udpReceiveErrors.set(0L)
        encodeErrors.set(0L)
        decodeErrors.set(0L)
        rtcpFractionLost.set(0L)
        rtcpJitter.set(0L)
        rtcpRoundTripMs.set(-1L)
        rtcpRemotePacketCount.set(0L)
        rtcpRemoteOctetCount.set(0L)
        sendSequenceNumber = 0
        sendTimestamp = 0L
        synchronized(rtpStatsLock) {
            expectedReceiveSequence = null
            highestSequenceReceived = 0L
            lastTransit = null
            interarrivalJitter = 0.0
        }
        jitterBuffer.clear()
    }

    private fun emitAudioState(nextState: String, message: String) {
        methodChannel.invokeOnMain(
            "onAudioStateChanged",
            mapOf(
                "mode" to mode,
                "state" to nextState,
                "streamId" to streamId,
                "source" to streamSource,
                "encoding" to AudioProtocol.CODEC_OPUS,
                "transport" to AudioProtocol.TRANSPORT_RTP_UDP,
                "codecImpl" to AudioProtocol.CODEC_IMPL_LIBOPUS,
                "latencyMode" to latencyMode.wireValue,
                "qualityMode" to qualityMode,
                "peerReady" to peerReady,
                "isStreaming" to (nextState == STATE_STREAMING),
                "message" to message
            )
        )
    }

    private fun emitAudioError(code: String, message: String, targetStreamId: Long) {
        methodChannel.invokeOnMain(
            "onAudioError",
            mapOf("code" to code, "message" to message, "streamId" to targetStreamId)
        )
    }

    private fun peerSupportsAudio(peerCapabilities: Set<String>): Boolean {
        return peerCapabilities.contains(ProtocolConstants.CAPABILITY_AUDIO_LINK) &&
            peerCapabilities.contains(ProtocolConstants.CAPABILITY_AUDIO_CODEC_OPUS) &&
            peerCapabilities.contains(ProtocolConstants.CAPABILITY_AUDIO_TRANSPORT_RTP) &&
            peerCapabilities.contains(ProtocolConstants.CAPABILITY_AUDIO_RTCP) &&
            peerCapabilities.contains(ProtocolConstants.CAPABILITY_AUDIO_CODEC_LIBOPUS)
    }

    private fun peerSupportsAudioQualitySelection(peerCapabilities: Set<String>): Boolean {
        return peerCapabilities.contains(ProtocolConstants.CAPABILITY_AUDIO_QUALITY_SELECT)
    }

    private fun nextStreamId(): Long {
        val value = abs(UUID.randomUUID().mostSignificantBits)
        return if (value == 0L) 1L else value
    }

    private fun randomSsrc(): Long {
        val value = UUID.randomUUID().leastSignificantBits and 0xffffffffL
        return if (value == 0L) 1L else value
    }

    private fun socketEndpoint(socket: DatagramSocket?): String {
        return try {
            socket?.localSocketAddress?.toString() ?: "unknown"
        } catch (_: Exception) {
            "closed"
        }
    }

    private fun MethodChannel.invokeOnMain(method: String, arguments: Any?) {
        mainHandler.post { invokeMethod(method, arguments) }
    }

    private fun MethodChannel.Result.successOnMain(value: Any?) {
        mainHandler.post { success(value) }
    }

    private fun MethodChannel.Result.errorOnMain(code: String, message: String, details: Any?) {
        mainHandler.post { error(code, message, details) }
    }

    private fun Exception.describe(): String {
        return message ?: javaClass.simpleName
    }

    companion object {
        const val MODE_IDLE = "idle"
        const val MODE_SEND = "send"
        const val MODE_RECEIVE = "receive"

        const val STATE_IDLE = "idle"
        const val STATE_RECEIVE_READY = "receiveReady"
        const val STATE_OFFER_SENT = "offerSent"
        const val STATE_CONNECTING = "connecting"
        const val STATE_STREAMING = "streaming"

        private const val SOCKET_POLL_TIMEOUT_MS = 500
        private const val PROBE_TIMEOUT_MS = 3000L
        private const val PROBE_INTERVAL_MS = 200L
        private const val MAX_DATAGRAM_BYTES = 4096
        private const val MAX_OPUS_PACKET_BYTES = 1500
    }
}
