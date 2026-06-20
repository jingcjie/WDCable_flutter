package com.jingcjie.wifi_direct_cable.audio

data class RtpPacket(
    val payloadType: Int,
    val sequenceNumber: Int,
    val timestamp: Long,
    val ssrc: Long,
    val payload: ByteArray
)

object RtpCodec {
    private const val HEADER_BYTES = 12
    private const val RTP_VERSION = 2

    fun encode(packet: RtpPacket): ByteArray {
        require(packet.payloadType in 0..127) { "Invalid RTP payload type" }
        require(packet.sequenceNumber in 0..0xffff) { "Invalid RTP sequence number" }
        val output = ByteArray(HEADER_BYTES + packet.payload.size)
        output[0] = (RTP_VERSION shl 6).toByte()
        output[1] = (packet.payloadType and 0x7f).toByte()
        writeUInt16(output, 2, packet.sequenceNumber)
        writeUInt32(output, 4, packet.timestamp)
        writeUInt32(output, 8, packet.ssrc)
        packet.payload.copyInto(output, HEADER_BYTES)
        return output
    }

    fun decode(datagram: ByteArray, length: Int = datagram.size): RtpPacket {
        require(length >= HEADER_BYTES) { "RTP packet too short" }
        val version = (datagram[0].toInt() ushr 6) and 0x03
        require(version == RTP_VERSION) { "Unsupported RTP version: $version" }
        val csrcCount = datagram[0].toInt() and 0x0f
        val hasExtension = (datagram[0].toInt() and 0x10) != 0
        require(!hasExtension && csrcCount == 0) { "Unsupported RTP header shape" }
        val payloadType = datagram[1].toInt() and 0x7f
        val sequence = readUInt16(datagram, 2)
        val timestamp = readUInt32(datagram, 4)
        val ssrc = readUInt32(datagram, 8)
        val payload = datagram.copyOfRange(HEADER_BYTES, length)
        return RtpPacket(payloadType, sequence, timestamp, ssrc, payload)
    }

    fun nextSequence(sequenceNumber: Int): Int = (sequenceNumber + 1) and 0xffff

    fun nextTimestamp(timestamp: Long): Long {
        return (timestamp + AudioProtocol.RTP_TIMESTAMP_INCREMENT) and 0xffffffffL
    }

    fun sequenceDistance(from: Int, to: Int): Int {
        return (to - from) and 0xffff
    }

    fun isSequenceBefore(sequence: Int, reference: Int): Boolean {
        val distance = sequenceDistance(reference, sequence)
        return distance > 0x8000
    }

    internal fun writeUInt16(output: ByteArray, offset: Int, value: Int) {
        output[offset] = ((value ushr 8) and 0xff).toByte()
        output[offset + 1] = (value and 0xff).toByte()
    }

    internal fun writeUInt24(output: ByteArray, offset: Int, value: Int) {
        val clamped = value.coerceIn(-0x800000, 0x7fffff)
        val unsigned = clamped and 0xffffff
        output[offset] = ((unsigned ushr 16) and 0xff).toByte()
        output[offset + 1] = ((unsigned ushr 8) and 0xff).toByte()
        output[offset + 2] = (unsigned and 0xff).toByte()
    }

    internal fun writeUInt32(output: ByteArray, offset: Int, value: Long) {
        output[offset] = ((value ushr 24) and 0xff).toByte()
        output[offset + 1] = ((value ushr 16) and 0xff).toByte()
        output[offset + 2] = ((value ushr 8) and 0xff).toByte()
        output[offset + 3] = (value and 0xff).toByte()
    }

    internal fun readUInt16(input: ByteArray, offset: Int): Int {
        return ((input[offset].toInt() and 0xff) shl 8) or
            (input[offset + 1].toInt() and 0xff)
    }

    internal fun readInt24(input: ByteArray, offset: Int): Int {
        val raw = ((input[offset].toInt() and 0xff) shl 16) or
            ((input[offset + 1].toInt() and 0xff) shl 8) or
            (input[offset + 2].toInt() and 0xff)
        return if ((raw and 0x800000) != 0) raw or -0x1000000 else raw
    }

    internal fun readUInt32(input: ByteArray, offset: Int): Long {
        return ((input[offset].toLong() and 0xffL) shl 24) or
            ((input[offset + 1].toLong() and 0xffL) shl 16) or
            ((input[offset + 2].toLong() and 0xffL) shl 8) or
            (input[offset + 3].toLong() and 0xffL)
    }
}
