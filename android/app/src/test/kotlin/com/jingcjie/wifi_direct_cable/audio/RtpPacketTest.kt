package com.jingcjie.wifi_direct_cable.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RtpPacketTest {
    @Test
    fun rtpHeaderRoundTrips() {
        val packet = RtpPacket(
            payloadType = AudioProtocol.RTP_PAYLOAD_TYPE,
            sequenceNumber = 65535,
            timestamp = 0xfffffff0L,
            ssrc = 0xabcdef01L,
            payload = byteArrayOf(1, 2, 3)
        )

        val decoded = RtpCodec.decode(RtpCodec.encode(packet))

        assertEquals(packet.payloadType, decoded.payloadType)
        assertEquals(packet.sequenceNumber, decoded.sequenceNumber)
        assertEquals(packet.timestamp, decoded.timestamp)
        assertEquals(packet.ssrc, decoded.ssrc)
        assertArrayEquals(packet.payload, decoded.payload)
    }

    @Test
    fun sequenceAndTimestampWrap() {
        assertEquals(0, RtpCodec.nextSequence(65535))
        assertEquals(0x3b0L, RtpCodec.nextTimestamp(0xfffffff0L))
        assertEquals(2, RtpCodec.sequenceDistance(65535, 1))
    }
}
