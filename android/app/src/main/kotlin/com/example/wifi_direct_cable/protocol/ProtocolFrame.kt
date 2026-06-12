package com.example.wifi_direct_cable.protocol

import java.util.UUID

data class ProtocolFrame(
    val type: ProtocolFrameType,
    val channel: ProtocolChannel,
    val flags: Int = 0,
    val streamId: Long = 0,
    val sequenceNumber: Long = 0,
    val correlationId: UUID = UUID(0L, 0L),
    val metadataJson: String = "",
    val payload: ByteArray = ByteArray(0)
)
