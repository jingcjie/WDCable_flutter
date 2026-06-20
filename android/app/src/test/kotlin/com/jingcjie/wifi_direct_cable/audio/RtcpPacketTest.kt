package com.jingcjie.wifi_direct_cable.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class RtcpPacketTest {
    @Test
    fun senderReportRoundTrips() {
        val encoded = RtcpCodec.encodeSenderReport(
            senderSsrc = 0x10101010L,
            rtpTimestamp = 960L,
            packetCount = 7L,
            octetCount = 1024L,
            nowMs = 1_700_000_000_123L
        )

        val decoded = RtcpCodec.decode(encoded) as RtcpPacket.SenderReport

        assertEquals(0x10101010L, decoded.report.senderSsrc)
        assertEquals(960L, decoded.report.rtpTimestamp)
        assertEquals(7L, decoded.report.packetCount)
        assertEquals(1024L, decoded.report.octetCount)
    }

    @Test
    fun receiverReportRoundTrips() {
        val report = RtcpReceiverReport(
            receiverSsrc = 1L,
            senderSsrc = 2L,
            fractionLost = 12,
            cumulativePacketsLost = 34,
            highestSequenceReceived = 56,
            interarrivalJitter = 78,
            lastSenderReport = 90,
            delaySinceLastSenderReport = 123
        )

        val decoded = RtcpCodec.decode(RtcpCodec.encodeReceiverReport(report)) as RtcpPacket.ReceiverReport

        assertEquals(report, decoded.report)
    }
}
