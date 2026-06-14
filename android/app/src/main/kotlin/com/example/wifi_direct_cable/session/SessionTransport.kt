package com.example.wifi_direct_cable.session

import com.example.wifi_direct_cable.protocol.ProtocolChannel
import com.example.wifi_direct_cable.protocol.ProtocolFrame

interface SessionTransport {
    val channel: ProtocolChannel

    fun readFrame(): ProtocolFrame?

    fun writeFrame(frame: ProtocolFrame)

    fun setReadTimeout(timeoutMs: Int)

    fun close()

    fun cancel()
}

interface SessionTransportAdapter {
    fun accept(
        channel: ProtocolChannel,
        port: Int,
        shouldCancel: () -> Boolean
    ): SessionTransport

    fun connect(
        channel: ProtocolChannel,
        host: String,
        port: Int
    ): SessionTransport

    fun listen(
        channel: ProtocolChannel,
        preferredPort: Int = 0
    ): SessionTransportListener

    fun close()

    fun cancel()
}

interface SessionTransportListener {
    val channel: ProtocolChannel
    val port: Int

    fun accept(shouldCancel: () -> Boolean): SessionTransport

    fun close()
}
