package com.example.wifi_direct_cable.protocol

enum class ProtocolFrameType(val id: Int, val protocolName: String) {
    HANDSHAKE_HELLO(1, "handshake.hello"),
    HANDSHAKE_ACK(2, "handshake.ack"),
    HEARTBEAT_PING(3, "heartbeat.ping"),
    HEARTBEAT_PONG(4, "heartbeat.pong"),
    CLOSE(5, "close"),
    ERROR(6, "error"),
    CONTROL_MESSAGE(10, "control.message"),
    ACK(11, "ack"),
    BULK_START(20, "bulk.start"),
    BULK_CHUNK(21, "bulk.chunk"),
    BULK_COMPLETE(22, "bulk.complete"),
    BULK_CANCEL(23, "bulk.cancel"),
    REALTIME_START(30, "realtime.start"),
    REALTIME_DATA(31, "realtime.data"),
    REALTIME_STOP(32, "realtime.stop");

    companion object {
        fun fromId(id: Int): ProtocolFrameType {
            return entries.firstOrNull { it.id == id }
                ?: throw ProtocolException(
                    ProtocolError.INVALID_FRAME_TYPE,
                    "Unknown protocol frame type id: $id"
                )
        }
    }
}
