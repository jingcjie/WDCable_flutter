package com.example.wifi_direct_cable.audio

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

    const val SOURCE_MICROPHONE = "microphone"
    const val CODEC_OPUS = "opus"
    const val TRANSPORT_TCP = "tcp"

    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 1
    const val FRAME_DURATION_MS = 20
    const val BITRATE_BPS = 24_000

    fun receiveReady(streamId: Long): JSONObject = base(KIND_RECEIVE_READY, streamId)

    fun receiveStopped(streamId: Long): JSONObject = base(KIND_RECEIVE_STOPPED, streamId)

    fun offer(streamId: Long, offerId: String): JSONObject = base(KIND_OFFER, streamId)
        .put("offerId", offerId)
        .put("source", SOURCE_MICROPHONE)
        .put("codec", CODEC_OPUS)
        .put("sampleRate", SAMPLE_RATE)
        .put("channels", CHANNELS)
        .put("frameDurationMs", FRAME_DURATION_MS)
        .put("bitrateBps", BITRATE_BPS)

    fun accept(streamId: Long, offerId: String): JSONObject = base(KIND_ACCEPT, streamId)
        .put("offerId", offerId)
        .put("codec", CODEC_OPUS)
        .put("sampleRate", SAMPLE_RATE)
        .put("channels", CHANNELS)
        .put("frameDurationMs", FRAME_DURATION_MS)

    fun transport(streamId: Long, port: Int): JSONObject = base(KIND_TRANSPORT, streamId)
        .put("transport", TRANSPORT_TCP)
        .put("port", port)

    fun stop(streamId: Long, reason: String): JSONObject = base(KIND_STOP, streamId)
        .put("reason", reason)

    fun frameMetadata(sentAtMs: Long): JSONObject = JSONObject()
        .put("codec", CODEC_OPUS)
        .put("sentAtMs", sentAtMs)
        .put("durationMs", FRAME_DURATION_MS)

    fun parseOffer(metadata: JSONObject): AudioOffer {
        requireKind(metadata, KIND_OFFER)
        val streamId = requiredLong(metadata, "streamId")
        val offerId = requiredString(metadata, "offerId")
        val source = requiredString(metadata, "source")
        val codec = requiredString(metadata, "codec")
        val sampleRate = metadata.optInt("sampleRate", 0)
        val channels = metadata.optInt("channels", 0)
        val frameDurationMs = metadata.optInt("frameDurationMs", 0)
        val bitrateBps = metadata.optInt("bitrateBps", 0)
        return AudioOffer(streamId, offerId, source, codec, sampleRate, channels, frameDurationMs, bitrateBps)
    }

    fun parseAccept(metadata: JSONObject): AudioAccept {
        requireKind(metadata, KIND_ACCEPT)
        return AudioAccept(
            streamId = requiredLong(metadata, "streamId"),
            offerId = requiredString(metadata, "offerId"),
            codec = requiredString(metadata, "codec")
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
}

data class AudioOffer(
    val streamId: Long,
    val offerId: String,
    val source: String,
    val codec: String,
    val sampleRate: Int,
    val channels: Int,
    val frameDurationMs: Int,
    val bitrateBps: Int
)

data class AudioAccept(
    val streamId: Long,
    val offerId: String,
    val codec: String
)

data class AudioTransportOffer(
    val streamId: Long,
    val transport: String,
    val port: Int
)
