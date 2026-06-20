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
    const val BITRATE_BPS = 32_000
    const val RTP_PORT = 8990
    const val RTCP_PORT = 8991
    const val RTP_PAYLOAD_TYPE = 111
    const val RTP_CLOCK_RATE = 48_000
    const val RTP_TIMESTAMP_INCREMENT = 960

    const val LATENCY_LOW = "lowLatency"
    const val LATENCY_STABLE = "stable"

    fun receiveReady(streamId: Long): JSONObject = base(KIND_RECEIVE_READY, streamId)

    fun receiveStopped(streamId: Long): JSONObject = base(KIND_RECEIVE_STOPPED, streamId)

    fun offer(
        streamId: Long,
        offerId: String,
        rtpSsrc: Long,
        transportRole: SessionTransportRole
    ): JSONObject = base(KIND_OFFER, streamId)
        .put("offerId", offerId)
        .put("transport", TRANSPORT_RTP_UDP)
        .put("source", SOURCE_MICROPHONE)
        .put("codec", CODEC_OPUS)
        .put("codecImpl", CODEC_IMPL_LIBOPUS)
        .put("sampleRate", SAMPLE_RATE)
        .put("channels", CHANNELS)
        .put("frameDurationMs", FRAME_DURATION_MS)
        .put("bitrateBps", BITRATE_BPS)
        .put("rtpPayloadType", RTP_PAYLOAD_TYPE)
        .put("rtpClockRate", RTP_CLOCK_RATE)
        .put("rtpSsrc", rtpSsrc)
        .put("transportRole", transportRole.eventName)

    fun accept(
        streamId: Long,
        offerId: String,
        rtpSsrc: Long,
        transportRole: SessionTransportRole,
        receiverProbeRequired: Boolean
    ): JSONObject = base(KIND_ACCEPT, streamId)
        .put("offerId", offerId)
        .put("transport", TRANSPORT_RTP_UDP)
        .put("codec", CODEC_OPUS)
        .put("codecImpl", CODEC_IMPL_LIBOPUS)
        .put("sampleRate", SAMPLE_RATE)
        .put("channels", CHANNELS)
        .put("frameDurationMs", FRAME_DURATION_MS)
        .put("bitrateBps", BITRATE_BPS)
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
            bitrateBps = requiredInt(metadata, "bitrateBps"),
            rtpPayloadType = requiredInt(metadata, "rtpPayloadType"),
            rtpClockRate = requiredInt(metadata, "rtpClockRate"),
            rtpSsrc = requiredUnsignedInt(metadata, "rtpSsrc"),
            transportRole = parseTransportRole(requiredString(metadata, "transportRole"))
        )
    }

    fun parseAccept(metadata: JSONObject): AudioAccept {
        requireKind(metadata, KIND_ACCEPT)
        return AudioAccept(
            streamId = requiredLong(metadata, "streamId"),
            offerId = requiredString(metadata, "offerId"),
            transport = requiredString(metadata, "transport"),
            codec = requiredString(metadata, "codec"),
            codecImpl = requiredString(metadata, "codecImpl"),
            sampleRate = requiredInt(metadata, "sampleRate"),
            channels = requiredInt(metadata, "channels"),
            frameDurationMs = requiredInt(metadata, "frameDurationMs"),
            bitrateBps = requiredInt(metadata, "bitrateBps"),
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
        return offer.transport == TRANSPORT_RTP_UDP &&
            offer.source == SOURCE_MICROPHONE &&
            offer.codec == CODEC_OPUS &&
            offer.codecImpl == CODEC_IMPL_LIBOPUS &&
            offer.sampleRate == SAMPLE_RATE &&
            offer.channels == CHANNELS &&
            offer.frameDurationMs == FRAME_DURATION_MS &&
            offer.rtpPayloadType == RTP_PAYLOAD_TYPE &&
            offer.rtpClockRate == RTP_CLOCK_RATE
    }

    fun validateAccept(accept: AudioAccept): Boolean {
        return accept.transport == TRANSPORT_RTP_UDP &&
            accept.codec == CODEC_OPUS &&
            accept.codecImpl == CODEC_IMPL_LIBOPUS &&
            accept.sampleRate == SAMPLE_RATE &&
            accept.channels == CHANNELS &&
            accept.frameDurationMs == FRAME_DURATION_MS &&
            accept.rtpPayloadType == RTP_PAYLOAD_TYPE &&
            accept.rtpClockRate == RTP_CLOCK_RATE
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
