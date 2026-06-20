package com.jingcjie.wifi_direct_cable.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterBufferTest {
    @Test
    fun waitsForInitialTargetBufferThenReturnsInSequenceOrder() {
        val buffer = JitterBuffer(AudioLatencyMode.LOW)

        buffer.add(frame(sequence = 3))
        buffer.add(frame(sequence = 1))

        assertEquals(JitterBufferPoll.Wait, buffer.pollForPlayback())

        buffer.add(frame(sequence = 2))

        assertEquals(1, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
        assertEquals(2, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
        assertEquals(3, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
    }

    @Test
    fun missingPacketTriggersPlcPathWithoutWaiting() {
        val buffer = JitterBuffer(AudioLatencyMode.LOW)

        buffer.add(frame(sequence = 1))
        buffer.add(frame(sequence = 3))
        buffer.add(frame(sequence = 4))

        assertEquals(1, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
        assertEquals(JitterBufferPoll.Missing, buffer.pollForPlayback())
        assertEquals(3, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
        assertEquals(1L, buffer.snapshot().plcCount)
    }

    @Test
    fun dropsOverflowFrames() {
        val buffer = JitterBuffer(AudioLatencyMode.LOW)

        for (sequence in 1..8) {
            buffer.add(frame(sequence = sequence))
        }

        val snapshot = buffer.snapshot()
        assertTrue(snapshot.bufferLevelMs <= AudioLatencyMode.LOW.maxDelayMs)
        assertTrue(snapshot.droppedFrames > 0)
    }

    @Test
    fun lateAndDuplicatePacketsAreCounted() {
        val buffer = JitterBuffer(AudioLatencyMode.LOW)

        buffer.add(frame(sequence = 1))
        buffer.add(frame(sequence = 2))
        buffer.add(frame(sequence = 3))
        buffer.pollForPlayback()
        buffer.add(frame(sequence = 1))
        buffer.add(frame(sequence = 3))
        buffer.add(frame(sequence = 3))

        val snapshot = buffer.snapshot()
        assertTrue(snapshot.latePacketDrops > 0)
        assertTrue(snapshot.duplicatePackets > 0)
    }

    @Test
    fun stableModeUsesLargerDelayBounds() {
        assertEquals(100, AudioLatencyMode.STABLE.initialDelayMs)
        assertEquals(80, AudioLatencyMode.STABLE.minDelayMs)
        assertEquals(220, AudioLatencyMode.STABLE.maxDelayMs)
    }

    private fun frame(sequence: Int): RtpAudioFrame {
        return RtpAudioFrame(
            sequenceNumber = sequence,
            timestamp = sequence * AudioProtocol.RTP_TIMESTAMP_INCREMENT.toLong(),
            receivedAtMs = 1000L + sequence,
            payload = byteArrayOf(sequence.toByte())
        )
    }
}
