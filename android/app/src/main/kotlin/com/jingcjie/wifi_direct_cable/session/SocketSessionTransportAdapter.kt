package com.jingcjie.wifi_direct_cable.session

import com.jingcjie.wifi_direct_cable.protocol.ProtocolChannel
import com.jingcjie.wifi_direct_cable.protocol.ProtocolCodec
import com.jingcjie.wifi_direct_cable.protocol.ProtocolFrame
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections

class SocketSessionTransportAdapter(
    private val connectTimeoutMs: Int = 3000,
    private val acceptPollTimeoutMs: Int = 1000,
    private val keepAlive: Boolean = true
) : SessionTransportAdapter {
    private val serverSockets = Collections.synchronizedSet(mutableSetOf<ServerSocket>())
    private val sockets = Collections.synchronizedSet(mutableSetOf<Socket>())

    override fun accept(
        channel: ProtocolChannel,
        port: Int,
        shouldCancel: () -> Boolean
    ): SessionTransport {
        val serverSocket = ServerSocket()
        serverSockets.add(serverSocket)
        try {
            serverSocket.reuseAddress = true
            serverSocket.soTimeout = acceptPollTimeoutMs
            serverSocket.bind(InetSocketAddress(port))

            while (!shouldCancel()) {
                try {
                    val socket = serverSocket.accept()
                    configureSocket(socket, channel)
                    sockets.add(socket)
                    return SocketSessionTransport(channel, socket)
                } catch (exception: SocketTimeoutException) {
                    // Poll again so cancellation and session replacement can stop accept promptly.
                }
            }

            throw InterruptedIOException("Accept cancelled for ${channel.protocolName}")
        } finally {
            serverSockets.remove(serverSocket)
            closeQuietly(serverSocket)
        }
    }

    override fun connect(
        channel: ProtocolChannel,
        host: String,
        port: Int
    ): SessionTransport {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            configureSocket(socket, channel)
            sockets.add(socket)
            return SocketSessionTransport(channel, socket)
        } catch (exception: Exception) {
            closeQuietly(socket)
            throw exception
        }
    }

    override fun listen(
        channel: ProtocolChannel,
        preferredPort: Int
    ): SessionTransportListener {
        val serverSocket = ServerSocket()
        serverSockets.add(serverSocket)
        try {
            serverSocket.reuseAddress = true
            serverSocket.soTimeout = acceptPollTimeoutMs
            serverSocket.bind(InetSocketAddress(preferredPort))
            return SocketSessionTransportListener(channel, serverSocket)
        } catch (exception: Exception) {
            serverSockets.remove(serverSocket)
            closeQuietly(serverSocket)
            throw exception
        }
    }

    override fun close() {
        serverSockets.toList().forEach(::closeQuietly)
        sockets.toList().forEach(::closeQuietly)
        serverSockets.clear()
        sockets.clear()
    }

    override fun cancel() {
        close()
    }

    private fun configureSocket(socket: Socket, channel: ProtocolChannel) {
        socket.keepAlive = keepAlive
        socket.tcpNoDelay = channel != ProtocolChannel.BULK
    }

    private inner class SocketSessionTransportListener(
        override val channel: ProtocolChannel,
        private val serverSocket: ServerSocket
    ) : SessionTransportListener {
        override val port: Int
            get() = serverSocket.localPort

        override fun accept(shouldCancel: () -> Boolean): SessionTransport {
            try {
                while (!shouldCancel()) {
                    try {
                        val socket = serverSocket.accept()
                        configureSocket(socket, channel)
                        sockets.add(socket)
                        return SocketSessionTransport(channel, socket)
                    } catch (exception: SocketTimeoutException) {
                        // Poll again so cancellation and session replacement can stop accept promptly.
                    }
                }

                throw InterruptedIOException("Accept cancelled for ${channel.protocolName}")
            } finally {
                close()
            }
        }

        override fun close() {
            serverSockets.remove(serverSocket)
            closeQuietly(serverSocket)
        }
    }

    private fun closeQuietly(serverSocket: ServerSocket) {
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}

private class SocketSessionTransport(
    override val channel: ProtocolChannel,
    private val socket: Socket
) : SessionTransport {
    private val writeLock = Any()

    override fun readFrame(): ProtocolFrame? {
        return ProtocolCodec.readFrame(socket.getInputStream())
    }

    override fun writeFrame(frame: ProtocolFrame) {
        synchronized(writeLock) {
            ProtocolCodec.writeFrame(frame, socket.getOutputStream())
        }
    }

    override fun setReadTimeout(timeoutMs: Int) {
        socket.soTimeout = timeoutMs
    }

    override fun close() {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    override fun cancel() {
        close()
    }
}
