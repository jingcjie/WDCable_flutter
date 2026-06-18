package com.jingcjie.wifi_direct_cable.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterBufferTest {
    @Test
    fun waitsForInitialTargetBufferThenReturnsInSequenceOrder() {
        val buffer = JitterBuffer(targetBufferMs = 60, maxBufferMs = 200)

        buffer.add(frame(sequence = 3))
        buffer.add(frame(sequence = 1))

        assertNull(buffer.pollReady())

        buffer.add(frame(sequence = 2))

        assertEquals(1L, buffer.pollReady()?.sequenceNumber)
        assertEquals(2L, buffer.pollReady()?.sequenceNumber)
        assertEquals(3L, buffer.pollReady()?.sequenceNumber)
    }

    @Test
    fun dropsOverflowFrames() {
        val buffer = JitterBuffer(targetBufferMs = 20, maxBufferMs = 60)

        buffer.add(frame(sequence = 1))
        buffer.add(frame(sequence = 2))
        buffer.add(frame(sequence = 3))
        buffer.add(frame(sequence = 4))

        val snapshot = buffer.snapshot()
        assertEquals(60, snapshot.bufferLevelMs)
        assertTrue(snapshot.droppedFrames > 0)
    }

    private fun frame(sequence: Long): EncodedAudioFrame {
        return EncodedAudioFrame(
            sequenceNumber = sequence,
            sentAtMs = 1000L + sequence,
            durationMs = 20,
            payload = byteArrayOf(sequence.toByte())
        )
    }
}
