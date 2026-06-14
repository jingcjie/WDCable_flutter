package com.example.wifi_direct_cable.protocol

enum class ProtocolChannel(val id: Int, val protocolName: String) {
    CONTROL(1, "control"),
    BULK(2, "bulk"),
    AUDIO(3, "audio");

    companion object {
        fun fromId(id: Int): ProtocolChannel {
            return entries.firstOrNull { it.id == id }
                ?: throw ProtocolException(
                    ProtocolError.INVALID_CHANNEL,
                    "Unknown protocol channel id: $id"
                )
        }
    }
}
