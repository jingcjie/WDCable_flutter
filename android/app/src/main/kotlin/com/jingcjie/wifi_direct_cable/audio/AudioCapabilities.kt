package com.jingcjie.wifi_direct_cable.audio

import com.jingcjie.wifi_direct_cable.protocol.ProtocolConstants
import org.json.JSONArray

object AudioCapabilities {
    val requiredAudioCapabilities: List<String> = listOf(
        ProtocolConstants.CAPABILITY_AUDIO_LINK,
        ProtocolConstants.CAPABILITY_AUDIO_CODEC_OPUS,
        ProtocolConstants.CAPABILITY_AUDIO_TRANSPORT_RTP,
        ProtocolConstants.CAPABILITY_AUDIO_RTCP,
        ProtocolConstants.CAPABILITY_AUDIO_CODEC_LIBOPUS
    )

    val optionalAudioCapabilities: List<String> = listOf(
        ProtocolConstants.CAPABILITY_AUDIO_QUALITY_SELECT
    )

    val baseCapabilities: List<String> = listOf(
        ProtocolConstants.CAPABILITY_CHAT,
        ProtocolConstants.CAPABILITY_BULK_FILE,
        ProtocolConstants.CAPABILITY_BULK_SPEED,
        ProtocolConstants.CAPABILITY_DIAGNOSTICS_EXPORT
    )

    fun advertisedCapabilitiesForRuntime(): List<String> {
        return if (NativeOpus.available) {
            baseCapabilities + requiredAudioCapabilities + optionalAudioCapabilities
        } else {
            baseCapabilities
        }
    }

    fun advertisedCapabilitiesJson(): JSONArray {
        val capabilities = JSONArray()
        for (capability in advertisedCapabilitiesForRuntime()) {
            capabilities.put(capability)
        }
        return capabilities
    }

    fun peerSupportsAudio(peerCapabilities: Set<String>): Boolean {
        return requiredAudioCapabilities.all(peerCapabilities::contains)
    }

    fun peerSupportsAudioQualitySelection(peerCapabilities: Set<String>): Boolean {
        return peerCapabilities.contains(ProtocolConstants.CAPABILITY_AUDIO_QUALITY_SELECT)
    }
}
