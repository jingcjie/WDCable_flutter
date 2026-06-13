package com.example.wifi_direct_cable.protocol

object ProtocolConstants {
    const val MAGIC = 0x57444342 // WDCB
    const val VERSION = 1
    const val HEADER_SIZE = 56
    const val MAX_METADATA_BYTES = 64 * 1024
    const val MAX_PAYLOAD_BYTES = 1024 * 1024

    const val APP_ID = "wdcable"

    const val DEFAULT_CONTROL_PORT = 8988
    const val DEFAULT_BULK_PORT = 8989

    const val CAPABILITY_CHAT = "control.chat"
    const val CAPABILITY_BULK_FILE = "bulk.file"
    const val CAPABILITY_BULK_SPEED = "bulk.speed"
    const val CAPABILITY_DIAGNOSTICS_EXPORT = "diagnostics.export"
}
