package com.jingcjie.wifi_direct_cable.audio

data class RtcpSenderReport(
    val senderSsrc: Long,
    val ntpSeconds: Long,
    val ntpFraction: Long,
    val rtpTimestamp: Long,
    val packetCount: Long,
    val octetCount: Long
) {
    val compactNtp: Long
        get() = ((ntpSeconds and 0xffffL) shl 16) or ((ntpFraction ushr 16) and 0xffffL)
}

data class RtcpReceiverReport(
    val receiverSsrc: Long,
    val senderSsrc: Long,
    val fractionLost: Int,
    val cumulativePacketsLost: Int,
    val highestSequenceReceived: Long,
    val interarrivalJitter: Long,
    val lastSenderReport: Long = 0,
    val delaySinceLastSenderReport: Long = 0
)

sealed class RtcpPacket {
    data class SenderReport(val report: RtcpSenderReport) : RtcpPacket()
    data class ReceiverReport(val report: RtcpReceiverReport) : RtcpPacket()
}

object RtcpCodec {
    private const val RTP_VERSION = 2
    private const val PT_SENDER_REPORT = 200
    private const val PT_RECEIVER_REPORT = 201
    private const val NTP_UNIX_EPOCH_OFFSET_SECONDS = 2_208_988_800L

    fun encodeSenderReport(
        senderSsrc: Long,
        rtpTimestamp: Long,
        packetCount: Long,
        octetCount: Long,
        nowMs: Long = System.currentTimeMillis()
    ): ByteArray {
        val output = ByteArray(28)
        output[0] = (RTP_VERSION shl 6).toByte()
        output[1] = PT_SENDER_REPORT.toByte()
        RtpCodec.writeUInt16(output, 2, 6)
        RtpCodec.writeUInt32(output, 4, senderSsrc)
        val ntpSeconds = nowMs / 1000L + NTP_UNIX_EPOCH_OFFSET_SECONDS
        val ntpFraction = ((nowMs % 1000L) * 0x100000000L / 1000L) and 0xffffffffL
        RtpCodec.writeUInt32(output, 8, ntpSeconds)
        RtpCodec.writeUInt32(output, 12, ntpFraction)
        RtpCodec.writeUInt32(output, 16, rtpTimestamp)
        RtpCodec.writeUInt32(output, 20, packetCount)
        RtpCodec.writeUInt32(output, 24, octetCount)
        return output
    }

    fun encodeReceiverReport(report: RtcpReceiverReport): ByteArray {
        val output = ByteArray(32)
        output[0] = ((RTP_VERSION shl 6) or 1).toByte()
        output[1] = PT_RECEIVER_REPORT.toByte()
        RtpCodec.writeUInt16(output, 2, 7)
        RtpCodec.writeUInt32(output, 4, report.receiverSsrc)
        RtpCodec.writeUInt32(output, 8, report.senderSsrc)
        output[12] = (report.fractionLost.coerceIn(0, 255) and 0xff).toByte()
        RtpCodec.writeUInt24(output, 13, report.cumulativePacketsLost)
        RtpCodec.writeUInt32(output, 16, report.highestSequenceReceived)
        RtpCodec.writeUInt32(output, 20, report.interarrivalJitter)
        RtpCodec.writeUInt32(output, 24, report.lastSenderReport)
        RtpCodec.writeUInt32(output, 28, report.delaySinceLastSenderReport)
        return output
    }

    fun decode(datagram: ByteArray, length: Int = datagram.size): RtcpPacket {
        require(length >= 8) { "RTCP packet too short" }
        val version = (datagram[0].toInt() ushr 6) and 0x03
        require(version == RTP_VERSION) { "Unsupported RTCP version: $version" }
        return when (datagram[1].toInt() and 0xff) {
            PT_SENDER_REPORT -> decodeSenderReport(datagram, length)
            PT_RECEIVER_REPORT -> decodeReceiverReport(datagram, length)
            else -> throw IllegalArgumentException("Unsupported RTCP packet type")
        }
    }

    fun delaySinceLastSenderReport(receivedAtMs: Long, nowMs: Long = System.currentTimeMillis()): Long {
        val elapsedMs = (nowMs - receivedAtMs).coerceAtLeast(0L)
        return (elapsedMs * 65536L / 1000L) and 0xffffffffL
    }

    fun dlsrToMillis(dlsr: Long): Long = dlsr * 1000L / 65536L

    private fun decodeSenderReport(datagram: ByteArray, length: Int): RtcpPacket.SenderReport {
        require(length >= 28) { "RTCP sender report too short" }
        val report = RtcpSenderReport(
            senderSsrc = RtpCodec.readUInt32(datagram, 4),
            ntpSeconds = RtpCodec.readUInt32(datagram, 8),
            ntpFraction = RtpCodec.readUInt32(datagram, 12),
            rtpTimestamp = RtpCodec.readUInt32(datagram, 16),
            packetCount = RtpCodec.readUInt32(datagram, 20),
            octetCount = RtpCodec.readUInt32(datagram, 24)
        )
        return RtcpPacket.SenderReport(report)
    }

    private fun decodeReceiverReport(datagram: ByteArray, length: Int): RtcpPacket.ReceiverReport {
        require(length >= 32) { "RTCP receiver report too short" }
        val report = RtcpReceiverReport(
            receiverSsrc = RtpCodec.readUInt32(datagram, 4),
            senderSsrc = RtpCodec.readUInt32(datagram, 8),
            fractionLost = datagram[12].toInt() and 0xff,
            cumulativePacketsLost = RtpCodec.readInt24(datagram, 13),
            highestSequenceReceived = RtpCodec.readUInt32(datagram, 16),
            interarrivalJitter = RtpCodec.readUInt32(datagram, 20),
            lastSenderReport = RtpCodec.readUInt32(datagram, 24),
            delaySinceLastSenderReport = RtpCodec.readUInt32(datagram, 28)
        )
        return RtcpPacket.ReceiverReport(report)
    }
}
