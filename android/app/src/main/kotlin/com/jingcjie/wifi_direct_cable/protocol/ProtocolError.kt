package com.jingcjie.wifi_direct_cable.protocol

enum class ProtocolError {
    PARTIAL_READ,
    MALFORMED_MAGIC,
    UNSUPPORTED_VERSION,
    INVALID_HEADER_SIZE,
    INVALID_FRAME_TYPE,
    INVALID_CHANNEL,
    INVALID_LENGTH,
    METADATA_TOO_LARGE,
    PAYLOAD_TOO_LARGE
}
