package com.example.wifi_direct_cable.audio

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

object AudioSupport {
    fun supportMap(): Map<String, Any> {
        val hasDecoder = hasCodec(encoder = false, mimeType = MediaFormat.MIMETYPE_AUDIO_OPUS)
        val hasEncoder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            hasCodec(encoder = true, mimeType = MediaFormat.MIMETYPE_AUDIO_OPUS)

        return mapOf(
            "audioLinkSupported" to hasDecoder,
            "canSend" to hasEncoder,
            "canReceive" to hasDecoder,
            "codec" to AudioProtocol.CODEC_OPUS,
            "source" to AudioProtocol.SOURCE_MICROPHONE,
            "sampleRate" to AudioProtocol.SAMPLE_RATE,
            "channels" to AudioProtocol.CHANNELS,
            "frameDurationMs" to AudioProtocol.FRAME_DURATION_MS,
            "bitrateBps" to AudioProtocol.BITRATE_BPS,
            "requiresApiForSend" to 29,
            "androidApi" to Build.VERSION.SDK_INT,
            "message" to supportMessage(hasEncoder, hasDecoder)
        )
    }

    fun canSendOpus(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            hasCodec(encoder = true, mimeType = MediaFormat.MIMETYPE_AUDIO_OPUS)
    }

    fun canReceiveOpus(): Boolean {
        return hasCodec(encoder = false, mimeType = MediaFormat.MIMETYPE_AUDIO_OPUS)
    }

    private fun supportMessage(hasEncoder: Boolean, hasDecoder: Boolean): String {
        return when {
            hasEncoder && hasDecoder -> "Opus microphone streaming is supported"
            !hasDecoder -> "Opus playback is not available on this device"
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "Sending Opus audio requires Android 10 or newer"
            else -> "Opus encoder is not available on this device"
        }
    }

    private fun hasCodec(encoder: Boolean, mimeType: String): Boolean {
        return try {
            val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            codecs.any { codecInfo ->
                codecInfo.isEncoder == encoder && supportsMimeType(codecInfo, mimeType)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun supportsMimeType(codecInfo: MediaCodecInfo, mimeType: String): Boolean {
        return codecInfo.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
    }
}
