package com.jingcjie.wifi_direct_cable.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterBufferTest {
    private var nowMs = 1000L

    @Test
    fun waitsForInitialPlayoutDeadlineThenReturnsInSequenceOrder() {
        val buffer = testBuffer()

        buffer.add(frame(sequence = 3))
        buffer.add(frame(sequence = 1))

        assertEquals(50L, (buffer.pollForPlayback() as JitterBufferPoll.Wait).waitMs)

        buffer.add(frame(sequence = 2))
        nowMs += AudioLatencyMode.LOW.initialDelayMs

        assertEquals(1, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
        nowMs += AudioProtocol.FRAME_DURATION_MS
        assertEquals(2, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
        nowMs += AudioProtocol.FRAME_DURATION_MS
        assertEquals(3, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
    }

    @Test
    fun repeatedPollingBeforeDeadlineDoesNotAdvanceSequenceOrCreatePlc() {
        val buffer = testBuffer()

        buffer.add(frame(sequence = 1))

        repeat(5) {
            assertTrue(buffer.pollForPlayback() is JitterBufferPoll.Wait)
        }

        nowMs += AudioLatencyMode.LOW.initialDelayMs

        assertEquals(1, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
        assertEquals(0L, buffer.snapshot().plcCount)
        assertEquals(0L, buffer.snapshot().latePacketDrops)
    }

    @Test
    fun missingPacketCreatesOnePlcPerDuePlayoutTick() {
        val buffer = testBuffer()

        buffer.add(frame(sequence = 1))
        buffer.add(frame(sequence = 3))
        nowMs += AudioLatencyMode.LOW.initialDelayMs

        assertEquals(1, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
        assertTrue(buffer.pollForPlayback() is JitterBufferPoll.Wait)
        nowMs += AudioProtocol.FRAME_DURATION_MS

        assertEquals(JitterBufferPoll.Missing, buffer.pollForPlayback())
        assertTrue(buffer.pollForPlayback() is JitterBufferPoll.Wait)
        nowMs += AudioProtocol.FRAME_DURATION_MS

        assertEquals(3, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
        assertEquals(1L, buffer.snapshot().plcCount)
    }

    @Test
    fun packetArrivingBeforeDeadlineIsNotLate() {
        val buffer = testBuffer()

        buffer.add(frame(sequence = 1))
        nowMs += AudioLatencyMode.LOW.initialDelayMs
        assertEquals(1, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)

        nowMs += AudioProtocol.FRAME_DURATION_MS - 5
        buffer.add(frame(sequence = 2))

        assertEquals(0L, buffer.snapshot().latePacketDrops)
        nowMs += 5
        assertEquals(2, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)
    }

    @Test
    fun packetArrivingAfterMissedDeadlineIsLate() {
        val buffer = testBuffer()

        buffer.add(frame(sequence = 1))
        nowMs += AudioLatencyMode.LOW.initialDelayMs
        assertEquals(1, (buffer.pollForPlayback() as JitterBufferPoll.Packet).frame.sequenceNumber)

        nowMs += AudioProtocol.FRAME_DURATION_MS
        assertEquals(JitterBufferPoll.Missing, buffer.pollForPlayback())
        buffer.add(frame(sequence = 2))

        val snapshot = buffer.snapshot()
        assertEquals(1L, snapshot.latePacketDrops)
        assertEquals(1L, snapshot.droppedFrames)
    }

    @Test
    fun dropsOverflowFramesSeparatelyFromLatePackets() {
        val buffer = testBuffer()

        for (sequence in 1..8) {
            buffer.add(frame(sequence = sequence))
        }

        val snapshot = buffer.snapshot()
        assertTrue(snapshot.bufferLevelMs <= AudioLatencyMode.LOW.maxDelayMs)
        assertTrue(snapshot.droppedFrames > 0)
        assertEquals(snapshot.droppedFrames, snapshot.overflowDrops)
        assertEquals(0L, snapshot.latePacketDrops)
    }

    @Test
    fun duplicatePacketsAreCounted() {
        val buffer = testBuffer()

        buffer.add(frame(sequence = 1))
        buffer.add(frame(sequence = 1))

        val snapshot = buffer.snapshot()
        assertEquals(1L, snapshot.duplicatePackets)
        assertEquals(1L, snapshot.droppedFrames)
    }

    @Test
    fun stableModeUsesLargerDelayBounds() {
        assertEquals(100, AudioLatencyMode.STABLE.initialDelayMs)
        assertEquals(80, AudioLatencyMode.STABLE.minDelayMs)
        assertEquals(220, AudioLatencyMode.STABLE.maxDelayMs)
    }

    private fun testBuffer(): JitterBuffer {
        nowMs = 1000L
        return JitterBuffer(AudioLatencyMode.LOW) { nowMs }
    }

    private fun frame(sequence: Int): RtpAudioFrame {
        return RtpAudioFrame(
            sequenceNumber = sequence,
            timestamp = sequence * AudioProtocol.RTP_TIMESTAMP_INCREMENT.toLong(),
            receivedAtMs = nowMs,
            payload = byteArrayOf(sequence.toByte())
        )
    }
}
