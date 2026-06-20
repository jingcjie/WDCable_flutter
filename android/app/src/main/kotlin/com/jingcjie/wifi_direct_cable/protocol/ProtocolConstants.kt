package com.jingcjie.wifi_direct_cable.protocol

object ProtocolConstants {
    const val MAGIC = 0x57444342 // WDCB
    const val VERSION = 2
    const val HEADER_SIZE = 56
    const val MAX_METADATA_BYTES = 64 * 1024
    const val MAX_PAYLOAD_BYTES = 1024 * 1024

    const val APP_ID = "wdcable"

    const val DEFAULT_RENDEZVOUS_PORT = 8987
    const val DEFAULT_CONTROL_PORT = 8988
    const val DEFAULT_BULK_PORT = 8989

    const val CAPABILITY_CHAT = "control.chat"
    const val CAPABILITY_BULK_FILE = "bulk.file"
    const val CAPABILITY_BULK_SPEED = "bulk.speed"
    const val CAPABILITY_DIAGNOSTICS_EXPORT = "diagnostics.export"
    const val CAPABILITY_AUDIO_LINK = "audio.link"
    const val CAPABILITY_AUDIO_CODEC_OPUS = "audio.codec.opus"
    const val CAPABILITY_AUDIO_TRANSPORT_RTP = "audio.transport.rtp"
    const val CAPABILITY_AUDIO_RTCP = "audio.rtcp"
    const val CAPABILITY_AUDIO_CODEC_LIBOPUS = "audio.codec.libopus"
    const val CAPABILITY_AUDIO_QUALITY_SELECT = "audio.quality.select"
}
