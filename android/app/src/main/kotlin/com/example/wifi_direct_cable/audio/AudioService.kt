package com.example.wifi_direct_cable.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import com.example.wifi_direct_cable.diagnostics.DiagnosticsLogger
import com.example.wifi_direct_cable.PermissionManager
import com.example.wifi_direct_cable.protocol.ProtocolConstants
import com.example.wifi_direct_cable.protocol.ProtocolFrame
import com.example.wifi_direct_cable.session.AudioSessionHandler
import com.example.wifi_direct_cable.session.SessionManager
import com.example.wifi_direct_cable.session.SessionRole
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max

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
    private val running = AtomicBoolean(false)
    private val audioSequence = AtomicLong(0)
    private val bytesSent = AtomicLong(0)
    private val bytesReceived = AtomicLong(0)
    private val framesSent = AtomicLong(0)
    private val framesReceived = AtomicLong(0)
    private val extraDroppedFrames = AtomicLong(0)
    private val jitterBuffer = JitterBuffer()

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
    private var transportReady: Boolean = false

    @Volatile
    private var listenerStarted: Boolean = false

    @Volatile
    private var captureThread: Thread? = null

    @Volatile
    private var playbackThread: Thread? = null

    @Volatile
    private var statsFuture: ScheduledFuture<*>? = null

    init {
        sessionManager.registerAudioHandler(this)
    }

    fun getAudioSupport(): Map<String, Any> = AudioSupport.supportMap()

    fun startAudio(
        requestedMode: String?,
        source: String?,
        encoding: String?,
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
            result.error(AudioProtocol.ERROR_UNSUPPORTED, "The connected peer does not advertise Audio Link", null)
            emitAudioError(AudioProtocol.ERROR_UNSUPPORTED, "The connected peer does not support Audio Link", 0L)
            return
        }

        synchronized(stateLock) {
            if (state != STATE_IDLE) {
                result.error(AudioProtocol.ERROR_BUSY, "Audio Link is already active", null)
                return
            }
        }

        if (normalizedMode == MODE_RECEIVE) {
            startReceive(result)
            return
        }

        if (!AudioSupport.canSendOpus()) {
            result.error(AudioProtocol.ERROR_CODEC_UNAVAILABLE, "Sending Opus audio requires Android 10+ with an Opus encoder", null)
            emitAudioError(AudioProtocol.ERROR_CODEC_UNAVAILABLE, "Opus encoder is not available on this device", 0L)
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
                    mapOf(
                        "errorType" to exception.javaClass.simpleName,
                        "error" to exception.message
                    )
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
            AudioProtocol.KIND_TRANSPORT -> handleTransport(metadata)
            AudioProtocol.KIND_STOP -> {
                cleanupLocal(metadata.optString("reason", "peer_stop"), emitStopped = true)
            }
        }
    }

    override fun onAudioFrame(frame: ProtocolFrame) {
        if (isSender || frame.streamId != streamId || state == STATE_IDLE) {
            extraDroppedFrames.incrementAndGet()
            return
        }

        val metadata = if (frame.metadataJson.isBlank()) JSONObject() else JSONObject(frame.metadataJson)
        if (metadata.optString("codec") != AudioProtocol.CODEC_OPUS) {
            extraDroppedFrames.incrementAndGet()
            return
        }

        jitterBuffer.add(
            EncodedAudioFrame(
                sequenceNumber = frame.sequenceNumber,
                sentAtMs = metadata.optLong("sentAtMs", System.currentTimeMillis()),
                durationMs = metadata.optInt("durationMs", AudioProtocol.FRAME_DURATION_MS),
                payload = frame.payload,
                codecConfig = metadata.optBoolean("codecConfig", false)
            )
        )
        if (!metadata.optBoolean("codecConfig", false)) {
            bytesReceived.addAndGet(frame.payload.size.toLong())
            framesReceived.incrementAndGet()
        }
    }

    override fun onAudioFeatureError(code: String, message: String, streamId: Long) {
        emitAudioError(code, message, streamId)
        cleanupLocal("peer_error", emitStopped = true)
    }

    override fun onAudioTransportClosed(reason: String) {
        if (state != STATE_IDLE) {
            emitAudioError(AudioProtocol.ERROR_TRANSPORT_FAILED, "Audio transport closed", streamId)
            cleanupLocal(reason, emitStopped = true)
        }
    }

    override fun onSessionEnded(reason: String) {
        cleanupLocal(reason, emitStopped = true)
    }

    private fun startReceive(result: MethodChannel.Result) {
        if (!AudioSupport.canReceiveOpus()) {
            result.error(AudioProtocol.ERROR_CODEC_UNAVAILABLE, "Opus playback is not available on this device", null)
            emitAudioError(AudioProtocol.ERROR_CODEC_UNAVAILABLE, "Opus playback is not available on this device", 0L)
            return
        }

        synchronized(stateLock) {
            mode = MODE_RECEIVE
            state = STATE_RECEIVE_READY
            streamId = 0L
            offerId = ""
            isSender = false
            accepted = false
            transportReady = false
            listenerStarted = false
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
        val newStreamId = nextStreamId()
        val newOfferId = UUID.randomUUID().toString()
        synchronized(stateLock) {
            mode = MODE_SEND
            state = STATE_OFFER_SENT
            streamId = newStreamId
            offerId = newOfferId
            isSender = true
            accepted = false
            transportReady = false
            listenerStarted = false
        }
        resetStats()
        controlExecutor.execute {
            try {
                sessionManager.sendAudioControl(AudioProtocol.offer(newStreamId, newOfferId))
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
            sessionManager.sendAudioError(0L, AudioProtocol.ERROR_UNSUPPORTED, exception.message ?: "Invalid audio offer")
            return
        }

        if (state != STATE_RECEIVE_READY || mode != MODE_RECEIVE) {
            sessionManager.sendAudioError(offer.streamId, AudioProtocol.ERROR_RECEIVER_NOT_READY, "Receiver has not started Audio Link receive mode")
            return
        }
        if (offer.source != AudioProtocol.SOURCE_MICROPHONE ||
            offer.codec != AudioProtocol.CODEC_OPUS ||
            offer.sampleRate != AudioProtocol.SAMPLE_RATE ||
            offer.channels != AudioProtocol.CHANNELS
        ) {
            sessionManager.sendAudioError(offer.streamId, AudioProtocol.ERROR_UNSUPPORTED, "Unsupported audio offer")
            return
        }

        synchronized(stateLock) {
            streamId = offer.streamId
            offerId = offer.offerId
            isSender = false
            accepted = true
            state = STATE_CONNECTING
        }
        try {
            sessionManager.sendAudioControl(AudioProtocol.accept(offer.streamId, offer.offerId))
            emitAudioState(STATE_CONNECTING, "Audio offer accepted")
            openListenerIfGroupOwner()
        } catch (exception: Exception) {
            failAudio(AudioProtocol.ERROR_TRANSPORT_FAILED, "Failed to accept audio offer: ${exception.message}", offer.streamId)
        }
    }

    private fun handleAccept(metadata: JSONObject) {
        val accept = try {
            AudioProtocol.parseAccept(metadata)
        } catch (exception: IllegalArgumentException) {
            failAudio(AudioProtocol.ERROR_UNSUPPORTED, exception.message ?: "Invalid audio accept", streamId)
            return
        }

        if (!isSender || accept.streamId != streamId || accept.offerId != offerId) {
            return
        }
        if (accept.codec != AudioProtocol.CODEC_OPUS) {
            failAudio(AudioProtocol.ERROR_UNSUPPORTED, "Peer accepted an unsupported codec", accept.streamId)
            return
        }

        synchronized(stateLock) {
            accepted = true
            state = STATE_CONNECTING
        }
        emitAudioState(STATE_CONNECTING, "Audio offer accepted by peer")
        openListenerIfGroupOwner()
    }

    private fun handleTransport(metadata: JSONObject) {
        val transport = try {
            AudioProtocol.parseTransport(metadata)
        } catch (exception: IllegalArgumentException) {
            failAudio(AudioProtocol.ERROR_TRANSPORT_FAILED, exception.message ?: "Invalid audio transport", streamId)
            return
        }

        if (transport.streamId != streamId || transport.transport != AudioProtocol.TRANSPORT_TCP || transport.port <= 0) {
            failAudio(AudioProtocol.ERROR_TRANSPORT_FAILED, "Invalid audio transport details", streamId)
            return
        }

        val info = sessionManager.getAudioSessionInfo()
        if (info?.role != SessionRole.CLIENT) {
            return
        }

        emitAudioState(STATE_CONNECTING, "Connecting audio channel")
        sessionManager.connectAudioTransport(
            streamId = streamId,
            port = transport.port,
            onConnected = { onAudioTransportReady() },
            onError = { exception ->
                failAudio(AudioProtocol.ERROR_TRANSPORT_FAILED, "Failed to connect audio channel: ${exception.message}", streamId)
            }
        )
    }

    private fun openListenerIfGroupOwner() {
        val info = sessionManager.getAudioSessionInfo() ?: return
        if (info.role != SessionRole.GROUP_OWNER || listenerStarted || !accepted) {
            return
        }

        listenerStarted = true
        sessionManager.startAudioListener(
            streamId = streamId,
            onPort = { port ->
                try {
                    sessionManager.sendAudioControl(AudioProtocol.transport(streamId, port))
                    emitAudioState(STATE_CONNECTING, "Audio channel listening on port $port")
                } catch (exception: Exception) {
                    failAudio(AudioProtocol.ERROR_TRANSPORT_FAILED, "Failed to send audio transport details: ${exception.message}", streamId)
                }
            },
            onConnected = { onAudioTransportReady() },
            onError = { exception ->
                failAudio(AudioProtocol.ERROR_TRANSPORT_FAILED, "Audio listener failed: ${exception.message}", streamId)
            }
        )
    }

    private fun onAudioTransportReady() {
        synchronized(stateLock) {
            transportReady = true
            state = STATE_STREAMING
        }
        running.set(true)
        startStats()
        if (isSender) {
            startCapture()
        } else {
            startPlayback()
        }
        emitAudioState(STATE_STREAMING, "Audio Link streaming")
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
        var encoder: MediaCodec? = null
        try {
            val pcmFrameBytes = AudioProtocol.SAMPLE_RATE *
                AudioProtocol.CHANNELS *
                BYTES_PER_SAMPLE *
                AudioProtocol.FRAME_DURATION_MS / 1000
            val minBufferSize = AudioRecord.getMinBufferSize(
                AudioProtocol.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val recordBufferSize = max(minBufferSize, pcmFrameBytes * 4)
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

            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                AudioProtocol.SAMPLE_RATE,
                AudioProtocol.CHANNELS
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, AudioProtocol.BITRATE_BPS)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, pcmFrameBytes)
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            recorder.startRecording()

            val pcm = ByteArray(pcmFrameBytes)
            var presentationTimeUs = 0L
            while (running.get()) {
                val read = recorder.read(pcm, 0, pcm.size)
                if (read <= 0) continue
                val inputIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(pcm, 0, read)
                    encoder.queueInputBuffer(inputIndex, 0, read, presentationTimeUs, 0)
                    presentationTimeUs += AudioProtocol.FRAME_DURATION_MS * 1000L
                }
                drainEncoder(encoder)
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
            try {
                encoder?.stop()
            } catch (_: Exception) {
            }
            try {
                encoder?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun drainEncoder(encoder: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                sendCodecSpecificData(encoder.outputFormat)
                continue
            }
            if (outputIndex < 0) return
            try {
                if (bufferInfo.size > 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex) ?: return
                    val packet = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    outputBuffer.get(packet)
                    val codecConfig =
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    val sentAtMs = System.currentTimeMillis()
                    sessionManager.writeAudioFrame(
                        streamId = streamId,
                        sequenceNumber = audioSequence.incrementAndGet(),
                        metadata = AudioProtocol.frameMetadata(sentAtMs, codecConfig),
                        payload = packet
                    )
                    if (!codecConfig) {
                        bytesSent.addAndGet(packet.size.toLong())
                        framesSent.incrementAndGet()
                    }
                }
            } finally {
                encoder.releaseOutputBuffer(outputIndex, false)
            }
        }
    }

    private fun sendCodecSpecificData(format: MediaFormat) {
        for (index in 0..2) {
            val key = "csd-$index"
            if (!format.containsKey(key)) continue
            val buffer = format.getByteBuffer(key) ?: continue
            val duplicate = buffer.duplicate()
            duplicate.position(0)
            val packet = ByteArray(duplicate.remaining())
            duplicate.get(packet)
            sessionManager.writeAudioFrame(
                streamId = streamId,
                sequenceNumber = audioSequence.incrementAndGet(),
                metadata = AudioProtocol.frameMetadata(System.currentTimeMillis(), codecConfig = true),
                payload = packet
            )
        }
    }

    private fun playbackLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var decoder: MediaCodec? = null
        var audioTrack: AudioTrack? = null
        try {
            val pcmFrameBytes = AudioProtocol.SAMPLE_RATE *
                AudioProtocol.CHANNELS *
                BYTES_PER_SAMPLE *
                AudioProtocol.FRAME_DURATION_MS / 1000
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
                .setBufferSizeInBytes(max(minTrackBuffer, pcmFrameBytes * 10))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.play()

            var presentationTimeUs = 0L
            val codecSpecificData = mutableListOf<ByteArray>()
            while (running.get()) {
                val encodedFrame = jitterBuffer.pollReady()
                if (encodedFrame == null) {
                    Thread.sleep(5)
                    decoder?.let { drainDecoder(it, audioTrack) }
                    continue
                }
                if (encodedFrame.codecConfig && decoder == null) {
                    codecSpecificData.add(encodedFrame.payload)
                    continue
                }
                if (decoder == null) {
                    decoder = createOpusDecoder(codecSpecificData)
                }
                val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(encodedFrame.payload)
                    val flags = if (encodedFrame.codecConfig) {
                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    } else {
                        0
                    }
                    decoder.queueInputBuffer(
                        inputIndex,
                        0,
                        encodedFrame.payload.size,
                        presentationTimeUs,
                        flags
                    )
                    if (!encodedFrame.codecConfig) {
                        presentationTimeUs += encodedFrame.durationMs * 1000L
                    }
                } else {
                    extraDroppedFrames.incrementAndGet()
                }
                drainDecoder(decoder, audioTrack)
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
            try {
                decoder?.stop()
            } catch (_: Exception) {
            }
            try {
                decoder?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun createOpusDecoder(codecSpecificData: List<ByteArray>): MediaCodec {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS,
            AudioProtocol.SAMPLE_RATE,
            AudioProtocol.CHANNELS
        )
        codecSpecificData.forEachIndexed { index, data ->
            if (data.isNotEmpty()) {
                format.setByteBuffer("csd-$index", ByteBuffer.wrap(data))
            }
        }
        return MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).also { decoder ->
            decoder.configure(format, null, null, 0)
            decoder.start()
        }
    }

    private fun drainDecoder(decoder: MediaCodec, audioTrack: AudioTrack) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
            if (outputIndex < 0) return
            try {
                if (bufferInfo.size > 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputIndex) ?: return
                    val pcm = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    outputBuffer.get(pcm)
                    audioTrack.write(pcm, 0, pcm.size)
                }
            } finally {
                decoder.releaseOutputBuffer(outputIndex, false)
            }
        }
    }

    private fun failAudio(code: String, message: String, targetStreamId: Long) {
        DiagnosticsLogger.log("audio", "Audio Link failed", mapOf("code" to code, "message" to message, "streamId" to targetStreamId))
        try {
            if (targetStreamId != 0L) {
                sessionManager.sendAudioError(targetStreamId, code, message)
            }
        } catch (_: Exception) {
        }
        emitAudioError(code, message, targetStreamId)
        cleanupLocal("failed", emitStopped = true)
    }

    private fun cleanupLocal(reason: String, emitStopped: Boolean) {
        val previousState = state
        running.set(false)
        stopStats()
        jitterBuffer.clear()
        sessionManager.closeAudioTransport()
        synchronized(stateLock) {
            mode = MODE_IDLE
            state = STATE_IDLE
            streamId = 0L
            offerId = ""
            isSender = false
            accepted = false
            transportReady = false
            listenerStarted = false
            peerReady = false
        }
        if (emitStopped && previousState != STATE_IDLE) {
            emitAudioState(STATE_IDLE, reason)
        }
    }

    private fun startStats() {
        stopStats()
        var lastBytes = 0L
        statsFuture = statsExecutor.scheduleAtFixedRate({
            val totalBytes = if (isSender) bytesSent.get() else bytesReceived.get()
            val bitrateBps = (totalBytes - lastBytes).coerceAtLeast(0L) * 8L
            lastBytes = totalBytes
            val snapshot = jitterBuffer.snapshot()
            val dropped = snapshot.droppedFrames + extraDroppedFrames.get()
            methodChannel.invokeOnMain(
                "onAudioStats",
                mapOf(
                    "mode" to mode,
                    "state" to state,
                    "streamId" to streamId,
                    "bitrateBps" to bitrateBps,
                    "bufferLevelMs" to snapshot.bufferLevelMs,
                    "framesSent" to framesSent.get(),
                    "framesReceived" to framesReceived.get(),
                    "droppedFrames" to dropped,
                    "underflowCount" to snapshot.underflowCount,
                    "latencyMs" to -1
                )
            )
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun stopStats() {
        statsFuture?.cancel(true)
        statsFuture = null
    }

    private fun resetStats() {
        audioSequence.set(0L)
        bytesSent.set(0L)
        bytesReceived.set(0L)
        framesSent.set(0L)
        framesReceived.set(0L)
        extraDroppedFrames.set(0L)
        jitterBuffer.clear()
    }

    private fun emitAudioState(nextState: String, message: String) {
        methodChannel.invokeOnMain(
            "onAudioStateChanged",
            mapOf(
                "mode" to mode,
                "state" to nextState,
                "streamId" to streamId,
                "source" to AudioProtocol.SOURCE_MICROPHONE,
                "encoding" to AudioProtocol.CODEC_OPUS,
                "peerReady" to peerReady,
                "isStreaming" to (nextState == STATE_STREAMING),
                "message" to message
            )
        )
    }

    private fun emitAudioError(code: String, message: String, targetStreamId: Long) {
        methodChannel.invokeOnMain(
            "onAudioError",
            mapOf(
                "code" to code,
                "message" to message,
                "streamId" to targetStreamId
            )
        )
    }

    private fun peerSupportsAudio(peerCapabilities: Set<String>): Boolean {
        return peerCapabilities.contains(ProtocolConstants.CAPABILITY_AUDIO_LINK) &&
            peerCapabilities.contains(ProtocolConstants.CAPABILITY_AUDIO_CODEC_OPUS)
    }

    private fun nextStreamId(): Long {
        val value = abs(UUID.randomUUID().mostSignificantBits)
        return if (value == 0L) 1L else value
    }

    private fun MethodChannel.invokeOnMain(method: String, arguments: Any?) {
        mainHandler.post {
            invokeMethod(method, arguments)
        }
    }

    private fun MethodChannel.Result.successOnMain(value: Any?) {
        mainHandler.post {
            success(value)
        }
    }

    private fun MethodChannel.Result.errorOnMain(code: String, message: String, details: Any?) {
        mainHandler.post {
            error(code, message, details)
        }
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

        private const val BYTES_PER_SAMPLE = 2
        private const val CODEC_TIMEOUT_US = 10_000L
    }
}
