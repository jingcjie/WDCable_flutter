package com.jingcjie.wifi_direct_cable.session

import com.jingcjie.wifi_direct_cable.diagnostics.DiagnosticsLogger
import com.jingcjie.wifi_direct_cable.protocol.ProtocolChannel
import com.jingcjie.wifi_direct_cable.protocol.ProtocolConstants
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

internal data class OpenedTransports(
    val transports: Map<ProtocolChannel, SessionTransport>,
    val peerAddress: String?
)

internal class TransportSetupException(
    val failureReason: String,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

internal class ProtocolV2TransportSetup(
    private val transportAdapter: SessionTransportAdapter,
    private val ioExecutor: ExecutorService,
    private val isCurrent: (Int) -> Boolean,
    private val emitDebug: (String) -> Unit
) {
    private data class RendezvousEndpoint(
        val hostAddress: String,
        val rendezvousId: String,
        val controlPort: Int,
        val bulkPort: Int
    )

    fun deadlineFromNow(): Long = System.currentTimeMillis() + SETUP_TIMEOUT_MS

    fun openTransports(
        role: SessionRole,
        ownerAddress: String?,
        expectedGeneration: Int,
        sessionId: String,
        deadlineMs: Long
    ): OpenedTransports {
        DiagnosticsLogger.log(
            "transport",
            "Opening WDCable transport",
            mapOf(
                "sessionId" to sessionId,
                "role" to role.eventName,
                "transportRole" to role.transportRole().eventName,
                "groupOwnerAddress" to (ownerAddress ?: "")
            )
        )
        return when (role) {
            SessionRole.CLIENT -> openListenerTransports(role, ownerAddress, expectedGeneration, sessionId, deadlineMs)
            SessionRole.GROUP_OWNER -> openConnectorTransports(role, expectedGeneration, sessionId, deadlineMs)
        }
    }

    private fun openListenerTransports(
        role: SessionRole,
        ownerAddress: String?,
        expectedGeneration: Int,
        sessionId: String,
        deadlineMs: Long
    ): OpenedTransports {
        val groupOwnerHost = ownerAddress?.takeIf { it.isNotBlank() }
            ?: throw TransportSetupException(
                "endpoint_unavailable",
                "Wi-Fi Direct did not provide a group owner address for rendezvous"
            )
        val rendezvousTarget = resolveRendezvousTarget(groupOwnerHost)
        val listeners = linkedMapOf<ProtocolChannel, SessionTransportListener>()
        val transports = linkedMapOf<ProtocolChannel, SessionTransport>()
        val stopRendezvous = AtomicBoolean(false)
        var rendezvousFuture: Future<*>? = null

        try {
            for ((channel, port) in channelPorts()) {
                DiagnosticsLogger.log(
                    "transport",
                    "TCP listener bind start",
                    mapOf(
                        "sessionId" to sessionId,
                        "channel" to channel.protocolName,
                        "role" to role.eventName,
                        "transportRole" to role.transportRole().eventName,
                        "localEndpoint" to "0.0.0.0:$port",
                        "remoteEndpoint" to "${rendezvousTarget.address.hostAddress}:${rendezvousTarget.port}"
                    )
                )
                try {
                    listeners[channel] = transportAdapter.listen(channel, port)
                    DiagnosticsLogger.log(
                        "transport",
                        "TCP listener bind success",
                        mapOf(
                            "sessionId" to sessionId,
                            "channel" to channel.protocolName,
                            "port" to port,
                            "role" to role.eventName,
                            "transportRole" to role.transportRole().eventName,
                            "localEndpoint" to "0.0.0.0:$port"
                        )
                    )
                } catch (exception: Exception) {
                    DiagnosticsLogger.log(
                        "transport",
                        "TCP listener bind failure",
                        mapOf(
                            "sessionId" to sessionId,
                            "channel" to channel.protocolName,
                            "port" to port,
                            "role" to role.eventName,
                            "transportRole" to role.transportRole().eventName,
                            "failureReason" to "bind_failed",
                            "errorType" to exception.javaClass.simpleName,
                            "error" to exception.message
                        )
                    )
                    throw TransportSetupException(
                        "bind_failed",
                        "Failed to bind ${channel.protocolName} listener on port $port",
                        exception
                    )
                }
            }

            rendezvousFuture = startRendezvousSender(
                sessionId = sessionId,
                role = role,
                target = rendezvousTarget,
                stopRendezvous = stopRendezvous,
                expectedGeneration = expectedGeneration,
                deadlineMs = deadlineMs
            )

            val controlListener = listeners.getValue(ProtocolChannel.CONTROL)
            transports[ProtocolChannel.CONTROL] = acceptSetupTransport(
                controlListener,
                expectedGeneration,
                deadlineMs,
                sessionId,
                role
            )
            stopRendezvous.set(true)
            rendezvousFuture.cancel(true)

            val bulkListener = listeners.getValue(ProtocolChannel.BULK)
            transports[ProtocolChannel.BULK] = acceptSetupTransport(
                bulkListener,
                expectedGeneration,
                deadlineMs,
                sessionId,
                role
            )

            return OpenedTransports(transports.toMap(), groupOwnerHost)
        } catch (exception: TransportSetupException) {
            closeTransports(transports.values)
            closeListeners(listeners.values)
            throw exception
        } catch (exception: Exception) {
            closeTransports(transports.values)
            closeListeners(listeners.values)
            val failureReason = setupCancellationReason(expectedGeneration, deadlineMs, "tcp_connect_timeout")
            throw TransportSetupException(
                failureReason,
                "Failed while accepting WDCable listener transports",
                exception
            )
        } finally {
            stopRendezvous.set(true)
            rendezvousFuture?.cancel(true)
        }
    }

    private fun openConnectorTransports(
        role: SessionRole,
        expectedGeneration: Int,
        sessionId: String,
        deadlineMs: Long
    ): OpenedTransports {
        val transports = linkedMapOf<ProtocolChannel, SessionTransport>()
        val endpoint = receiveRendezvousEndpoint(role, expectedGeneration, sessionId, deadlineMs)

        try {
            for ((channel, defaultPort) in channelPorts()) {
                val port = when (channel) {
                    ProtocolChannel.CONTROL -> endpoint.controlPort
                    ProtocolChannel.BULK -> endpoint.bulkPort
                    else -> defaultPort
                }
                transports[channel] = connectSetupWithRetry(
                    channel = channel,
                    host = endpoint.hostAddress,
                    port = port,
                    expectedGeneration = expectedGeneration,
                    deadlineMs = deadlineMs,
                    sessionId = sessionId,
                    role = role,
                    rendezvousId = endpoint.rendezvousId
                )
            }
            return OpenedTransports(transports.toMap(), endpoint.hostAddress)
        } catch (exception: Exception) {
            closeTransports(transports.values)
            throw exception
        }
    }

    private fun acceptSetupTransport(
        listener: SessionTransportListener,
        expectedGeneration: Int,
        deadlineMs: Long,
        sessionId: String,
        role: SessionRole
    ): SessionTransport {
        val channel = listener.channel
        emitDebug("Accepting ${channel.protocolName} channel on port ${listener.port}")
        try {
            val transport = listener.accept { setupCancelled(expectedGeneration, deadlineMs) }
            DiagnosticsLogger.log(
                "transport",
                "TCP listener accept success",
                mapOf(
                    "sessionId" to sessionId,
                    "channel" to channel.protocolName,
                    "port" to listener.port,
                    "role" to role.eventName,
                    "transportRole" to role.transportRole().eventName
                )
            )
            return transport
        } catch (exception: InterruptedIOException) {
            val failureReason = setupCancellationReason(expectedGeneration, deadlineMs, "tcp_connect_timeout")
            throw TransportSetupException(
                failureReason,
                "Timed out accepting ${channel.protocolName} channel",
                exception
            )
        } catch (exception: Exception) {
            val failureReason = setupCancellationReason(expectedGeneration, deadlineMs, "tcp_connect_timeout")
            throw TransportSetupException(
                failureReason,
                "Failed accepting ${channel.protocolName} channel",
                exception
            )
        }
    }

    private fun connectSetupWithRetry(
        channel: ProtocolChannel,
        host: String,
        port: Int,
        expectedGeneration: Int,
        deadlineMs: Long,
        sessionId: String,
        role: SessionRole,
        rendezvousId: String
    ): SessionTransport {
        var attempt = 1
        var lastError: Exception? = null
        while (!setupCancelled(expectedGeneration, deadlineMs)) {
            try {
                emitDebug("Connecting ${channel.protocolName} channel to $host:$port (attempt $attempt)")
                DiagnosticsLogger.log(
                    "transport",
                    "TCP connect attempt",
                    mapOf(
                        "sessionId" to sessionId,
                        "channel" to channel.protocolName,
                        "attempt" to attempt,
                        "role" to role.eventName,
                        "transportRole" to role.transportRole().eventName,
                        "remoteEndpoint" to "$host:$port",
                        "rendezvousId" to rendezvousId
                    )
                )
                val transport = transportAdapter.connect(channel, host, port)
                DiagnosticsLogger.log(
                    "transport",
                    "TCP connect success",
                    mapOf(
                        "sessionId" to sessionId,
                        "channel" to channel.protocolName,
                        "attempt" to attempt,
                        "role" to role.eventName,
                        "transportRole" to role.transportRole().eventName,
                        "remoteEndpoint" to "$host:$port",
                        "rendezvousId" to rendezvousId
                    )
                )
                return transport
            } catch (exception: Exception) {
                lastError = exception
                DiagnosticsLogger.log(
                    "transport",
                    "TCP connect failure",
                    mapOf(
                        "sessionId" to sessionId,
                        "channel" to channel.protocolName,
                        "attempt" to attempt,
                        "role" to role.eventName,
                        "transportRole" to role.transportRole().eventName,
                        "remoteEndpoint" to "$host:$port",
                        "failureReason" to "tcp_connect_timeout",
                        "errorType" to exception.javaClass.simpleName,
                        "error" to exception.message
                    )
                )
                sleepUntilNextSetupAttempt(deadlineMs)
                attempt++
            }
        }

        val failureReason = setupCancellationReason(expectedGeneration, deadlineMs, "tcp_connect_timeout")
        throw TransportSetupException(
            failureReason,
            "Timed out connecting ${channel.protocolName} channel to $host:$port",
            lastError
        )
    }

    private fun startRendezvousSender(
        sessionId: String,
        role: SessionRole,
        target: InetSocketAddress,
        stopRendezvous: AtomicBoolean,
        expectedGeneration: Int,
        deadlineMs: Long
    ): Future<*> {
        return ioExecutor.submit {
            var socket: DatagramSocket? = null
            var sendCount = 0
            try {
                socket = DatagramSocket()
                DiagnosticsLogger.log(
                    "transport",
                    "UDP rendezvous send start",
                    mapOf(
                        "sessionId" to sessionId,
                        "rendezvousId" to sessionId,
                        "role" to role.eventName,
                        "transportRole" to role.transportRole().eventName,
                        "remoteEndpoint" to "${target.address.hostAddress}:${target.port}"
                    )
                )
                while (!stopRendezvous.get() && !setupCancelled(expectedGeneration, deadlineMs)) {
                    val payload = buildRendezvousPayload(sessionId, role).toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(payload, payload.size, target.address, target.port)
                    try {
                        socket.send(packet)
                        sendCount++
                        if (sendCount == 1 || sendCount % RENDEZVOUS_SEND_LOG_EVERY == 0) {
                            DiagnosticsLogger.log(
                                "transport",
                                "UDP rendezvous send success",
                                mapOf(
                                    "sessionId" to sessionId,
                                    "rendezvousId" to sessionId,
                                    "sendCount" to sendCount,
                                    "role" to role.eventName,
                                    "transportRole" to role.transportRole().eventName,
                                    "remoteEndpoint" to "${target.address.hostAddress}:${target.port}"
                                )
                            )
                        }
                    } catch (exception: Exception) {
                        DiagnosticsLogger.log(
                            "transport",
                            "UDP rendezvous send failure",
                            mapOf(
                                "sessionId" to sessionId,
                                "rendezvousId" to sessionId,
                                "sendCount" to sendCount,
                                "role" to role.eventName,
                                "transportRole" to role.transportRole().eventName,
                                "remoteEndpoint" to "${target.address.hostAddress}:${target.port}",
                                "errorType" to exception.javaClass.simpleName,
                                "error" to exception.message
                            )
                        )
                    }
                    sleepRendezvousInterval(deadlineMs)
                }
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (exception: Exception) {
                if (!stopRendezvous.get() && isCurrent(expectedGeneration)) {
                    DiagnosticsLogger.log(
                        "transport",
                        "UDP rendezvous sender failed",
                        mapOf(
                            "sessionId" to sessionId,
                            "rendezvousId" to sessionId,
                            "role" to role.eventName,
                            "transportRole" to role.transportRole().eventName,
                            "remoteEndpoint" to "${target.address.hostAddress}:${target.port}",
                            "errorType" to exception.javaClass.simpleName,
                            "error" to exception.message
                        )
                    )
                }
            } finally {
                socket?.close()
                DiagnosticsLogger.log(
                    "transport",
                    "UDP rendezvous send stopped",
                    mapOf(
                        "sessionId" to sessionId,
                        "rendezvousId" to sessionId,
                        "sendCount" to sendCount,
                        "role" to role.eventName,
                        "transportRole" to role.transportRole().eventName
                    )
                )
            }
        }
    }

    private fun receiveRendezvousEndpoint(
        role: SessionRole,
        expectedGeneration: Int,
        sessionId: String,
        deadlineMs: Long
    ): RendezvousEndpoint {
        val socket = DatagramSocket(null)
        try {
            socket.reuseAddress = true
            socket.soTimeout = RENDEZVOUS_RECEIVE_POLL_TIMEOUT_MS
            DiagnosticsLogger.log(
                "transport",
                "UDP rendezvous receive bind start",
                mapOf(
                    "sessionId" to sessionId,
                    "role" to role.eventName,
                    "transportRole" to role.transportRole().eventName,
                    "localEndpoint" to "0.0.0.0:${ProtocolConstants.DEFAULT_RENDEZVOUS_PORT}"
                )
            )
            socket.bind(InetSocketAddress(ProtocolConstants.DEFAULT_RENDEZVOUS_PORT))
            DiagnosticsLogger.log(
                "transport",
                "UDP rendezvous receive bind success",
                mapOf(
                    "sessionId" to sessionId,
                    "role" to role.eventName,
                    "transportRole" to role.transportRole().eventName,
                    "localEndpoint" to "0.0.0.0:${ProtocolConstants.DEFAULT_RENDEZVOUS_PORT}"
                )
            )
        } catch (exception: Exception) {
            socket.close()
            DiagnosticsLogger.log(
                "transport",
                "UDP rendezvous receive bind failure",
                mapOf(
                    "sessionId" to sessionId,
                    "role" to role.eventName,
                    "transportRole" to role.transportRole().eventName,
                    "failureReason" to "bind_failed",
                    "errorType" to exception.javaClass.simpleName,
                    "error" to exception.message
                )
            )
            throw TransportSetupException(
                "bind_failed",
                "Failed to bind UDP rendezvous receive port ${ProtocolConstants.DEFAULT_RENDEZVOUS_PORT}",
                exception
            )
        }

        try {
            val buffer = ByteArray(RENDEZVOUS_MAX_PACKET_BYTES)
            while (!setupCancelled(expectedGeneration, deadlineMs)) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val sourceAddress = packet.address
                    val sourceEndpoint = "${sourceAddress.hostAddress}:${packet.port}"
                    DiagnosticsLogger.log(
                        "transport",
                        "UDP rendezvous packet received",
                        mapOf(
                            "sessionId" to sessionId,
                            "role" to role.eventName,
                            "transportRole" to role.transportRole().eventName,
                            "remoteEndpoint" to sourceEndpoint
                        )
                    )
                    val endpoint = parseRendezvousPacket(packet, role, sessionId)
                    if (endpoint != null) {
                        DiagnosticsLogger.log(
                            "transport",
                            "UDP rendezvous source accepted",
                            mapOf(
                                "sessionId" to sessionId,
                                "rendezvousId" to endpoint.rendezvousId,
                                "role" to role.eventName,
                                "transportRole" to role.transportRole().eventName,
                                "remoteEndpoint" to "${endpoint.hostAddress}:${ProtocolConstants.DEFAULT_CONTROL_PORT}"
                            )
                        )
                        return endpoint
                    }
                } catch (exception: SocketTimeoutException) {
                    // Poll again so cancellation and setup timeout are observed.
                }
            }
        } finally {
            socket.close()
        }

        val failureReason = setupCancellationReason(expectedGeneration, deadlineMs, "udp_rendezvous_timeout")
        throw TransportSetupException(
            failureReason,
            "Timed out waiting for UDP rendezvous on port ${ProtocolConstants.DEFAULT_RENDEZVOUS_PORT}"
        )
    }

    private fun parseRendezvousPacket(
        packet: DatagramPacket,
        role: SessionRole,
        sessionId: String
    ): RendezvousEndpoint? {
        val sourceAddress = packet.address
        val sourceHost = sourceAddress.hostAddress
        if (sourceHost.isNullOrBlank()) {
            logIgnoredRendezvousPacket(sessionId, role, ":${packet.port}", "missing_source_address")
            return null
        }
        val sourceEndpoint = "$sourceHost:${packet.port}"
        if (sourceAddress !is Inet4Address) {
            logIgnoredRendezvousPacket(sessionId, role, sourceEndpoint, "non_ipv4_source")
            return null
        }

        val metadata = try {
            JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
        } catch (exception: Exception) {
            logIgnoredRendezvousPacket(sessionId, role, sourceEndpoint, "invalid_json", exception)
            return null
        }

        val appId = metadata.optString("appId")
        if (appId != ProtocolConstants.APP_ID) {
            logIgnoredRendezvousPacket(sessionId, role, sourceEndpoint, "invalid_app_id")
            return null
        }
        if (metadata.optInt("rendezvousVersion", -1) != RENDEZVOUS_VERSION) {
            logIgnoredRendezvousPacket(sessionId, role, sourceEndpoint, "unsupported_rendezvous_version")
            return null
        }

        val protocolMin = metadata.optInt("protocolMin", -1)
        val protocolMax = metadata.optInt("protocolMax", -1)
        if (ProtocolConstants.VERSION !in protocolMin..protocolMax) {
            logIgnoredRendezvousPacket(sessionId, role, sourceEndpoint, "unsupported_protocol_version")
            return null
        }

        if (metadata.optString("wifiRole") != SessionRole.CLIENT.eventName ||
            metadata.optString("transportRole") != SessionTransportRole.LISTENER.eventName) {
            logIgnoredRendezvousPacket(sessionId, role, sourceEndpoint, "impossible_role")
            return null
        }

        val controlPort = metadata.optInt("controlPort", -1)
        val bulkPort = metadata.optInt("bulkPort", -1)
        if (controlPort != ProtocolConstants.DEFAULT_CONTROL_PORT ||
            bulkPort != ProtocolConstants.DEFAULT_BULK_PORT) {
            logIgnoredRendezvousPacket(sessionId, role, sourceEndpoint, "invalid_ports")
            return null
        }

        val rendezvousId = metadata.optString("rendezvousId")
        if (rendezvousId.isBlank()) {
            logIgnoredRendezvousPacket(sessionId, role, sourceEndpoint, "missing_rendezvous_id")
            return null
        }

        val timestamp = metadata.optLong("timestamp", -1L)
        if (timestamp <= 0L ||
            kotlin.math.abs(System.currentTimeMillis() - timestamp) > RENDEZVOUS_STALE_MS) {
            logIgnoredRendezvousPacket(sessionId, role, sourceEndpoint, "stale_rendezvous")
            return null
        }

        return RendezvousEndpoint(
            hostAddress = sourceHost,
            rendezvousId = rendezvousId,
            controlPort = controlPort,
            bulkPort = bulkPort
        )
    }

    private fun buildRendezvousPayload(sessionId: String, role: SessionRole): String {
        return JSONObject()
            .put("appId", ProtocolConstants.APP_ID)
            .put("rendezvousVersion", RENDEZVOUS_VERSION)
            .put("rendezvousId", sessionId)
            .put("protocolMin", ProtocolConstants.VERSION)
            .put("protocolMax", ProtocolConstants.VERSION)
            .put("wifiRole", role.eventName)
            .put("transportRole", role.transportRole().eventName)
            .put("controlPort", ProtocolConstants.DEFAULT_CONTROL_PORT)
            .put("bulkPort", ProtocolConstants.DEFAULT_BULK_PORT)
            .put("timestamp", System.currentTimeMillis())
            .toString()
    }

    private fun resolveRendezvousTarget(groupOwnerHost: String): InetSocketAddress {
        return try {
            InetSocketAddress(InetAddress.getByName(groupOwnerHost), ProtocolConstants.DEFAULT_RENDEZVOUS_PORT)
        } catch (exception: Exception) {
            throw TransportSetupException(
                "endpoint_unavailable",
                "Invalid group owner rendezvous address: $groupOwnerHost",
                exception
            )
        }
    }

    private fun logIgnoredRendezvousPacket(
        sessionId: String,
        role: SessionRole,
        sourceEndpoint: String,
        reason: String,
        exception: Exception? = null
    ) {
        DiagnosticsLogger.log(
            "transport",
            "UDP rendezvous packet ignored",
            mapOf(
                "sessionId" to sessionId,
                "role" to role.eventName,
                "transportRole" to role.transportRole().eventName,
                "remoteEndpoint" to sourceEndpoint,
                "failureReason" to reason,
                "errorType" to (exception?.javaClass?.simpleName ?: ""),
                "error" to (exception?.message ?: "")
            )
        )
    }

    private fun sleepRendezvousInterval(deadlineMs: Long) {
        val remainingMs = deadlineMs - System.currentTimeMillis()
        if (remainingMs <= 0L) return
        Thread.sleep(min(RENDEZVOUS_SEND_INTERVAL_MS, remainingMs))
    }

    private fun sleepUntilNextSetupAttempt(deadlineMs: Long) {
        val remainingMs = deadlineMs - System.currentTimeMillis()
        if (remainingMs <= 0L) return
        try {
            Thread.sleep(min(TCP_CONNECT_RETRY_DELAY_MS, remainingMs))
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun setupCancelled(expectedGeneration: Int, deadlineMs: Long): Boolean {
        return !isCurrent(expectedGeneration) || System.currentTimeMillis() >= deadlineMs
    }

    private fun setupCancellationReason(
        expectedGeneration: Int,
        deadlineMs: Long,
        timeoutReason: String
    ): String {
        return if (!isCurrent(expectedGeneration)) {
            "cancelled"
        } else if (System.currentTimeMillis() >= deadlineMs) {
            timeoutReason
        } else {
            timeoutReason
        }
    }

    private fun closeTransports(transports: Collection<SessionTransport>) {
        transports.forEach { transport ->
            try {
                transport.cancel()
            } catch (_: Exception) {
            }
        }
    }

    private fun closeListeners(listeners: Collection<SessionTransportListener>) {
        listeners.forEach { listener ->
            try {
                listener.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun channelPorts(): List<Pair<ProtocolChannel, Int>> {
        return listOf(
            ProtocolChannel.CONTROL to ProtocolConstants.DEFAULT_CONTROL_PORT,
            ProtocolChannel.BULK to ProtocolConstants.DEFAULT_BULK_PORT
        )
    }

    companion object {
        private const val SETUP_TIMEOUT_MS = 30000L
        private const val TCP_CONNECT_RETRY_DELAY_MS = 1000L
        private const val RENDEZVOUS_VERSION = 2
        private const val RENDEZVOUS_SEND_INTERVAL_MS = 500L
        private const val RENDEZVOUS_RECEIVE_POLL_TIMEOUT_MS = 1000
        private const val RENDEZVOUS_STALE_MS = 30000L
        private const val RENDEZVOUS_MAX_PACKET_BYTES = 2048
        private const val RENDEZVOUS_SEND_LOG_EVERY = 10
    }
}
