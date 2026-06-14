package com.example.wifi_direct_cable.audio

import java.util.TreeMap

data class EncodedAudioFrame(
    val sequenceNumber: Long,
    val sentAtMs: Long,
    val durationMs: Int,
    val payload: ByteArray,
    val codecConfig: Boolean = false
)

data class JitterBufferSnapshot(
    val bufferLevelMs: Int,
    val droppedFrames: Long,
    val underflowCount: Long,
    val queuedFrames: Int
)

class JitterBuffer(
    private val targetBufferMs: Int = 60,
    private val maxBufferMs: Int = 200
) {
    private val lock = Any()
    private val frames = TreeMap<Long, EncodedAudioFrame>()
    private var lastPoppedSequence: Long = Long.MIN_VALUE
    private var droppedFrames: Long = 0
    private var underflowCount: Long = 0

    fun add(frame: EncodedAudioFrame) {
        synchronized(lock) {
            if (frame.sequenceNumber <= lastPoppedSequence) {
                droppedFrames++
                return
            }
            frames[frame.sequenceNumber] = frame
            trimOverflowLocked()
        }
    }

    fun pollReady(): EncodedAudioFrame? {
        synchronized(lock) {
            if (frames.isEmpty()) {
                underflowCount++
                return null
            }
            if (bufferLevelLocked() < targetBufferMs && lastPoppedSequence == Long.MIN_VALUE) {
                return null
            }
            val first = frames.pollFirstEntry()?.value ?: return null
            lastPoppedSequence = first.sequenceNumber
            return first
        }
    }

    fun clear() {
        synchronized(lock) {
            frames.clear()
            lastPoppedSequence = Long.MIN_VALUE
            droppedFrames = 0
            underflowCount = 0
        }
    }

    fun snapshot(): JitterBufferSnapshot {
        synchronized(lock) {
            return JitterBufferSnapshot(
                bufferLevelMs = bufferLevelLocked(),
                droppedFrames = droppedFrames,
                underflowCount = underflowCount,
                queuedFrames = frames.size
            )
        }
    }

    private fun trimOverflowLocked() {
        while (bufferLevelLocked() > maxBufferMs && frames.isNotEmpty()) {
            frames.pollFirstEntry()
            droppedFrames++
        }
    }

    private fun bufferLevelLocked(): Int = frames.values.sumOf { it.durationMs }
}
