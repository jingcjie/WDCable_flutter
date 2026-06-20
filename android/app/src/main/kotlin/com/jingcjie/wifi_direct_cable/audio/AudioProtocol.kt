package com.jingcjie.wifi_direct_cable.audio

import com.jingcjie.wifi_direct_cable.session.SessionTransportRole
import org.json.JSONObject

object AudioProtocol {
    const val KIND_RECEIVE_READY = "audio.receive.ready"
    const val KIND_RECEIVE_STOPPED = "audio.receive.stopped"
    const val KIND_OFFER = "audio.offer"
    const val KIND_ACCEPT = "audio.accept"
    const val KIND_TRANSPORT = "audio.transport"
    const val KIND_STOP = "audio.stop"

    const val ERROR_UNSUPPORTED = "audio_unsupported"
    const val ERROR_RECEIVER_NOT_READY = "audio_receiver_not_ready"
    const val ERROR_BUSY = "audio_busy"
    const val ERROR_PERMISSION_DENIED = "audio_permission_denied"
    const val ERROR_CODEC_UNAVAILABLE = "audio_codec_unavailable"
    const val ERROR_TRANSPORT_FAILED = "audio_transport_failed"
    const val ERROR_CAPTURE_FAILED = "audio_capture_failed"
    const val ERROR_PLAYBACK_FAILED = "audio_playback_failed"
    const val ERROR_RTP_BIND_FAILED = "audio_rtp_bind_failed"
    const val ERROR_RTP_PROBE_TIMEOUT = "audio_rtp_probe_timeout"
    const val ERROR_RTP_SEND_FAILED = "audio_rtp_send_failed"
    const val ERROR_RTP_RECEIVE_FAILED = "audio_rtp_receive_failed"
    const val ERROR_RTP_UNSUPPORTED = "audio_rtp_unsupported"

    const val SOURCE_MICROPHONE = "microphone"
    const val SOURCE_SYSTEM_AUDIO = "systemAudio"
    const val CODEC_OPUS = "opus"
    const val CODEC_IMPL_LIBOPUS = "libopus"
    const val TRANSPORT_RTP_UDP = "rtp-udp"
    const val TRANSPORT_TCP = "tcp"

    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 1
    const val FRAME_DURATION_MS = 20
    const val SAMPLES_PER_PACKET = 960
    const val BYTES_PER_SAMPLE = 2
    const val PCM_FRAME_BYTES = SAMPLES_PER_PACKET * CHANNELS * BYTES_PER_SAMPLE
    const val QUALITY_STANDARD = "standard"
    const val QUALITY_BALANCED = "balanced"
    const val QUALITY_HIGH = "high"
    const val QUALITY_NEAR_LOSSLESS = "nearLossless"

    const val BITRATE_STANDARD_BPS = 32_000
    const val BITRATE_BALANCED_BPS = 64_000
    const val BITRATE_HIGH_BPS = 128_000
    const val BITRATE_NEAR_LOSSLESS_BPS = 256_000
    const val BITRATE_BPS = BITRATE_STANDARD_BPS
    const val RTP_PORT = 8990
    const val RTCP_PORT = 8991
    const val RTP_PAYLOAD_TYPE = 111
    const val RTP_CLOCK_RATE = 48_000
    const val RTP_TIMESTAMP_INCREMENT = 960

    const val LATENCY_LOW = "lowLatency"
    const val LATENCY_STABLE = "stable"

    val QUALITY_BITRATES: Map<String, Int> = linkedMapOf(
        QUALITY_STANDARD to BITRATE_STANDARD_BPS,
        QUALITY_BALANCED to BITRATE_BALANCED_BPS,
        QUALITY_HIGH to BITRATE_HIGH_BPS,
        QUALITY_NEAR_LOSSLESS to BITRATE_NEAR_LOSSLESS_BPS
    )

    val RTP_PROBE_PAYLOAD: ByteArray = "WDA2RTP".toByteArray(Charsets.US_ASCII)
    val RTCP_PROBE_PAYLOAD: ByteArray = "WDA2RTCP".toByteArray(Charsets.US_ASCII)

    fun receiveReady(streamId: Long): JSONObject = base(KIND_RECEIVE_READY, streamId)

    fun receiveStopped(streamId: Long): JSONObject = base(KIND_RECEIVE_STOPPED, streamId)

    fun offer(
        streamId: Long,
        offerId: String,
        rtpSsrc: Long,
        transportRole: SessionTransportRole,
        latencyMode: String = LATENCY_LOW,
        qualityMode: String = QUALITY_STANDARD,
        bitrateBps: Int = BITRATE_BPS
    ): JSONObject = base(KIND_OFFER, streamId)
        .put("offerId", offerId)
        .put("transport", TRANSPORT_RTP_UDP)
        .put("source", SOURCE_MICROPHONE)
        .put("codec", CODEC_OPUS)
        .put("codecImpl", CODEC_IMPL_LIBOPUS)
        .put("sampleRate", SAMPLE_RATE)
        .put("channels", CHANNELS)
        .put("frameDurationMs", FRAME_DURATION_MS)
        .put("latencyMode", latencyMode)
        .put("qualityMode", qualityMode)
        .put("bitrateBps", bitrateBps)
        .put("rtpPayloadType", RTP_PAYLOAD_TYPE)
        .put("rtpClockRate", RTP_CLOCK_RATE)
        .put("rtpSsrc", rtpSsrc)
        .put("transportRole", transportRole.eventName)

    fun accept(
        streamId: Long,
        offerId: String,
        rtpSsrc: Long,
        transportRole: SessionTransportRole,
        receiverProbeRequired: Boolean,
        latencyMode: String,
        qualityMode: String,
        bitrateBps: Int
    ): JSONObject = base(KIND_ACCEPT, streamId)
        .put("offerId", offerId)
        .put("transport", TRANSPORT_RTP_UDP)
        .put("codec", CODEC_OPUS)
        .put("codecImpl", CODEC_IMPL_LIBOPUS)
        .put("sampleRate", SAMPLE_RATE)
        .put("channels", CHANNELS)
        .put("frameDurationMs", FRAME_DURATION_MS)
        .put("latencyMode", latencyMode)
        .put("qualityMode", qualityMode)
        .put("bitrateBps", bitrateBps)
        .put("rtpPayloadType", RTP_PAYLOAD_TYPE)
        .put("rtpClockRate", RTP_CLOCK_RATE)
        .put("rtpSsrc", rtpSsrc)
        .put("transportRole", transportRole.eventName)
        .put("receiverProbeRequired", receiverProbeRequired)

    fun transport(streamId: Long, port: Int): JSONObject = base(KIND_TRANSPORT, streamId)
        .put("transport", TRANSPORT_TCP)
        .put("port", port)

    fun stop(streamId: Long, reason: String): JSONObject = base(KIND_STOP, streamId)
        .put("reason", reason)

    fun parseOffer(metadata: JSONObject): AudioOffer {
        requireKind(metadata, KIND_OFFER)
        val bitrateBps = requiredInt(metadata, "bitrateBps")
        return AudioOffer(
            streamId = requiredLong(metadata, "streamId"),
            offerId = requiredString(metadata, "offerId"),
            transport = requiredString(metadata, "transport"),
            source = requiredString(metadata, "source"),
            codec = requiredString(metadata, "codec"),
            codecImpl = requiredString(metadata, "codecImpl"),
            sampleRate = requiredInt(metadata, "sampleRate"),
            channels = requiredInt(metadata, "channels"),
            frameDurationMs = requiredInt(metadata, "frameDurationMs"),
            latencyMode = requiredString(metadata, "latencyMode"),
            qualityMode = optionalQualityMode(metadata, bitrateBps),
            bitrateBps = bitrateBps,
            rtpPayloadType = requiredInt(metadata, "rtpPayloadType"),
            rtpClockRate = requiredInt(metadata, "rtpClockRate"),
            rtpSsrc = requiredUnsignedInt(metadata, "rtpSsrc"),
            transportRole = parseTransportRole(requiredString(metadata, "transportRole"))
        )
    }

    fun parseAccept(metadata: JSONObject): AudioAccept {
        requireKind(metadata, KIND_ACCEPT)
        val bitrateBps = requiredInt(metadata, "bitrateBps")
        return AudioAccept(
            streamId = requiredLong(metadata, "streamId"),
            offerId = requiredString(metadata, "offerId"),
            transport = requiredString(metadata, "transport"),
            codec = requiredString(metadata, "codec"),
            codecImpl = requiredString(metadata, "codecImpl"),
            sampleRate = requiredInt(metadata, "sampleRate"),
            channels = requiredInt(metadata, "channels"),
            frameDurationMs = requiredInt(metadata, "frameDurationMs"),
            latencyMode = requiredString(metadata, "latencyMode"),
            qualityMode = optionalQualityMode(metadata, bitrateBps),
            bitrateBps = bitrateBps,
            rtpPayloadType = requiredInt(metadata, "rtpPayloadType"),
            rtpClockRate = requiredInt(metadata, "rtpClockRate"),
            rtpSsrc = requiredUnsignedInt(metadata, "rtpSsrc"),
            transportRole = parseTransportRole(requiredString(metadata, "transportRole")),
            receiverProbeRequired = metadata.optBoolean("receiverProbeRequired", false)
        )
    }

    fun parseTransport(metadata: JSONObject): AudioTransportOffer {
        requireKind(metadata, KIND_TRANSPORT)
        return AudioTransportOffer(
            streamId = requiredLong(metadata, "streamId"),
            transport = requiredString(metadata, "transport"),
            port = metadata.optInt("port", -1)
        )
    }

    fun validateOffer(offer: AudioOffer): Boolean {
        return offerRejectionReason(offer) == null
    }

    fun validateAccept(accept: AudioAccept): Boolean {
        return acceptRejectionReason(accept) == null
    }

    fun offerRejectionReason(offer: AudioOffer): String? {
        if (offer.transport != TRANSPORT_RTP_UDP) {
            return "transport=${offer.transport}; expected=$TRANSPORT_RTP_UDP"
        }
        if (!isSupportedSource(offer.source)) {
            return "source=${offer.source}; supported=$SOURCE_MICROPHONE,$SOURCE_SYSTEM_AUDIO"
        }
        if (offer.codec != CODEC_OPUS) {
            return "codec=${offer.codec}; expected=$CODEC_OPUS"
        }
        if (offer.codecImpl != CODEC_IMPL_LIBOPUS) {
            return "codecImpl=${offer.codecImpl}; expected=$CODEC_IMPL_LIBOPUS"
        }
        if (offer.sampleRate != SAMPLE_RATE) {
            return "sampleRate=${offer.sampleRate}; expected=$SAMPLE_RATE"
        }
        if (offer.channels != CHANNELS) {
            return "channels=${offer.channels}; expected=$CHANNELS"
        }
        if (offer.frameDurationMs != FRAME_DURATION_MS) {
            return "frameDurationMs=${offer.frameDurationMs}; expected=$FRAME_DURATION_MS"
        }
        if (!isSupportedLatencyMode(offer.latencyMode)) {
            return "latencyMode=${offer.latencyMode}; supported=$LATENCY_LOW,$LATENCY_STABLE"
        }
        if (!isSupportedQualityBitratePair(offer.qualityMode, offer.bitrateBps)) {
            return "qualityMode=${offer.qualityMode},bitrateBps=${offer.bitrateBps}; expected a supported quality/bitrate pair"
        }
        if (offer.rtpPayloadType != RTP_PAYLOAD_TYPE) {
            return "rtpPayloadType=${offer.rtpPayloadType}; expected=$RTP_PAYLOAD_TYPE"
        }
        if (offer.rtpClockRate != RTP_CLOCK_RATE) {
            return "rtpClockRate=${offer.rtpClockRate}; expected=$RTP_CLOCK_RATE"
        }
        return null
    }

    fun acceptRejectionReason(accept: AudioAccept): String? {
        if (accept.transport != TRANSPORT_RTP_UDP) {
            return "transport=${accept.transport}; expected=$TRANSPORT_RTP_UDP"
        }
        if (accept.codec != CODEC_OPUS) {
            return "codec=${accept.codec}; expected=$CODEC_OPUS"
        }
        if (accept.codecImpl != CODEC_IMPL_LIBOPUS) {
            return "codecImpl=${accept.codecImpl}; expected=$CODEC_IMPL_LIBOPUS"
        }
        if (accept.sampleRate != SAMPLE_RATE) {
            return "sampleRate=${accept.sampleRate}; expected=$SAMPLE_RATE"
        }
        if (accept.channels != CHANNELS) {
            return "channels=${accept.channels}; expected=$CHANNELS"
        }
        if (accept.frameDurationMs != FRAME_DURATION_MS) {
            return "frameDurationMs=${accept.frameDurationMs}; expected=$FRAME_DURATION_MS"
        }
        if (!isSupportedLatencyMode(accept.latencyMode)) {
            return "latencyMode=${accept.latencyMode}; supported=$LATENCY_LOW,$LATENCY_STABLE"
        }
        if (!isSupportedQualityBitratePair(accept.qualityMode, accept.bitrateBps)) {
            return "qualityMode=${accept.qualityMode},bitrateBps=${accept.bitrateBps}; expected a supported quality/bitrate pair"
        }
        if (accept.rtpPayloadType != RTP_PAYLOAD_TYPE) {
            return "rtpPayloadType=${accept.rtpPayloadType}; expected=$RTP_PAYLOAD_TYPE"
        }
        if (accept.rtpClockRate != RTP_CLOCK_RATE) {
            return "rtpClockRate=${accept.rtpClockRate}; expected=$RTP_CLOCK_RATE"
        }
        return null
    }

    fun isSupportedSource(source: String): Boolean {
        return source == SOURCE_MICROPHONE || source == SOURCE_SYSTEM_AUDIO
    }

    fun isRtpProbePayload(data: ByteArray, length: Int): Boolean {
        return isProbePayload(data, length, RTP_PROBE_PAYLOAD)
    }

    fun isRtcpProbePayload(data: ByteArray, length: Int): Boolean {
        return isProbePayload(data, length, RTCP_PROBE_PAYLOAD)
    }

    fun isSameNegotiation(
        currentStreamId: Long,
        currentOfferId: String,
        incomingStreamId: Long,
        incomingOfferId: String
    ): Boolean {
        return currentStreamId == incomingStreamId && currentOfferId == incomingOfferId
    }

    fun isSupportedLatencyMode(value: String): Boolean {
        return value == LATENCY_LOW || value == LATENCY_STABLE
    }

    fun isSupportedBitrateBps(value: Int): Boolean {
        return QUALITY_BITRATES.containsValue(value)
    }

    fun isSupportedQualityMode(value: String): Boolean {
        return QUALITY_BITRATES.containsKey(value)
    }

    fun isSupportedQualityBitratePair(qualityMode: String, bitrateBps: Int): Boolean {
        return QUALITY_BITRATES[qualityMode] == bitrateBps
    }

    fun bitrateForQualityMode(qualityMode: String?): Int {
        return QUALITY_BITRATES[qualityMode] ?: BITRATE_BPS
    }

    fun normalizeQualityMode(qualityMode: String?): String {
        return if (qualityMode != null && isSupportedQualityMode(qualityMode)) {
            qualityMode
        } else {
            QUALITY_STANDARD
        }
    }

    fun requiresQualitySelectionCapability(qualityMode: String): Boolean {
        return qualityMode != QUALITY_STANDARD
    }

    fun receiverProbeRequired(receiverRole: SessionTransportRole): Boolean {
        return receiverRole == SessionTransportRole.CONNECTOR
    }

    fun senderWaitsForReceiverProbe(
        senderRole: SessionTransportRole,
        receiverProbeRequired: Boolean
    ): Boolean {
        return senderRole == SessionTransportRole.LISTENER && receiverProbeRequired
    }

    private fun base(kind: String, streamId: Long): JSONObject = JSONObject()
        .put("kind", kind)
        .put("streamId", streamId)

    private fun requireKind(metadata: JSONObject, kind: String) {
        val actual = metadata.optString("kind")
        require(actual == kind) { "Expected $kind, received $actual" }
    }

    private fun requiredString(metadata: JSONObject, key: String): String {
        val value = metadata.optString(key)
        require(value.isNotBlank()) { "Missing $key" }
        return value
    }

    private fun requiredLong(metadata: JSONObject, key: String): Long {
        require(metadata.has(key)) { "Missing $key" }
        return metadata.optLong(key)
    }

    private fun requiredInt(metadata: JSONObject, key: String): Int {
        require(metadata.has(key)) { "Missing $key" }
        return metadata.optInt(key)
    }

    private fun optionalQualityMode(metadata: JSONObject, bitrateBps: Int): String {
        val qualityMode = metadata.optString("qualityMode")
        if (qualityMode.isNotBlank()) return qualityMode
        require(bitrateBps == BITRATE_BPS) { "Missing qualityMode" }
        return QUALITY_STANDARD
    }

    private fun isProbePayload(data: ByteArray, length: Int, probe: ByteArray): Boolean {
        if (length != probe.size) return false
        for (index in probe.indices) {
            if (data[index] != probe[index]) return false
        }
        return true
    }

    private fun requiredUnsignedInt(metadata: JSONObject, key: String): Long {
        require(metadata.has(key)) { "Missing $key" }
        val value = metadata.get(key)
        return when (value) {
            is Number -> value.toLong() and 0xffffffffL
            is String -> value.toLong() and 0xffffffffL
            else -> throw IllegalArgumentException("Invalid $key")
        }
    }

    private fun parseTransportRole(value: String): SessionTransportRole {
        return SessionTransportRole.entries.firstOrNull { it.eventName == value }
            ?: throw IllegalArgumentException("Invalid transportRole: $value")
    }
}

data class AudioOffer(
    val streamId: Long,
    val offerId: String,
    val transport: String,
    val source: String,
    val codec: String,
    val codecImpl: String,
    val sampleRate: Int,
    val channels: Int,
    val frameDurationMs: Int,
    val latencyMode: String,
    val qualityMode: String,
    val bitrateBps: Int,
    val rtpPayloadType: Int,
    val rtpClockRate: Int,
    val rtpSsrc: Long,
    val transportRole: SessionTransportRole
)

data class AudioAccept(
    val streamId: Long,
    val offerId: String,
    val transport: String,
    val codec: String,
    val codecImpl: String,
    val sampleRate: Int,
    val channels: Int,
    val frameDurationMs: Int,
    val latencyMode: String,
    val qualityMode: String,
    val bitrateBps: Int,
    val rtpPayloadType: Int,
    val rtpClockRate: Int,
    val rtpSsrc: Long,
    val transportRole: SessionTransportRole,
    val receiverProbeRequired: Boolean
)

data class AudioTransportOffer(
    val streamId: Long,
    val transport: String,
    val port: Int
)
