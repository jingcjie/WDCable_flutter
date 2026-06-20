package com.jingcjie.wifi_direct_cable.audio

import android.os.Build

object AudioSupport {
    fun supportMap(): Map<String, Any> {
        val hasLibopus = NativeOpus.available
        return mapOf(
            "audioLinkSupported" to hasLibopus,
            "canSend" to hasLibopus,
            "canReceive" to hasLibopus,
            "codec" to AudioProtocol.CODEC_OPUS,
            "codecImpl" to AudioProtocol.CODEC_IMPL_LIBOPUS,
            "transport" to AudioProtocol.TRANSPORT_RTP_UDP,
            "source" to AudioProtocol.SOURCE_MICROPHONE,
            "sampleRate" to AudioProtocol.SAMPLE_RATE,
            "channels" to AudioProtocol.CHANNELS,
            "frameDurationMs" to AudioProtocol.FRAME_DURATION_MS,
            "bitrateBps" to AudioProtocol.BITRATE_BPS,
            "defaultBitrateBps" to AudioProtocol.BITRATE_BPS,
            "rtpPort" to AudioProtocol.RTP_PORT,
            "rtcpPort" to AudioProtocol.RTCP_PORT,
            "rtpPayloadType" to AudioProtocol.RTP_PAYLOAD_TYPE,
            "latencyModes" to listOf(AudioProtocol.LATENCY_LOW, AudioProtocol.LATENCY_STABLE),
            "qualityModes" to AudioProtocol.QUALITY_BITRATES.map { (qualityMode, bitrateBps) ->
                mapOf(
                    "qualityMode" to qualityMode,
                    "bitrateBps" to bitrateBps
                )
            },
            "defaultQualityMode" to AudioProtocol.QUALITY_STANDARD,
            "requiresApiForSend" to 23,
            "androidApi" to Build.VERSION.SDK_INT,
            "libopusVersion" to NativeOpus.versionString(),
            "message" to supportMessage(hasLibopus)
        )
    }

    fun canSendOpus(): Boolean = NativeOpus.available

    fun canReceiveOpus(): Boolean = NativeOpus.available

    private fun supportMessage(hasLibopus: Boolean): String {
        return if (hasLibopus) {
            "RTP/libopus microphone streaming is supported"
        } else {
            "libopus runtime is not available on this device"
        }
    }
}
