package com.jingcjie.wifi_direct_cable.audio

import android.os.SystemClock
import java.util.TreeMap

enum class AudioLatencyMode(
    val wireValue: String,
    val initialDelayMs: Int,
    val minDelayMs: Int,
    val maxDelayMs: Int
) {
    LOW(AudioProtocol.LATENCY_LOW, initialDelayMs = 50, minDelayMs = 40, maxDelayMs = 120),
    STABLE(AudioProtocol.LATENCY_STABLE, initialDelayMs = 100, minDelayMs = 80, maxDelayMs = 220);

    companion object {
        fun fromWire(value: String?): AudioLatencyMode {
            return entries.firstOrNull { it.wireValue == value } ?: LOW
        }
    }
}

data class RtpAudioFrame(
    val sequenceNumber: Int,
    val timestamp: Long,
    val receivedAtMs: Long,
    val payload: ByteArray
)

data class JitterBufferSnapshot(
    val bufferLevelMs: Int,
    val droppedFrames: Long,
    val latePacketDrops: Long,
    val overflowDrops: Long,
    val duplicatePackets: Long,
    val reorderedPackets: Long,
    val underflowCount: Long,
    val plcCount: Long,
    val queuedFrames: Int
)

sealed class JitterBufferPoll {
    data class Packet(val frame: RtpAudioFrame) : JitterBufferPoll()
    data object Missing : JitterBufferPoll()
    data class Wait(val waitMs: Long) : JitterBufferPoll()
}

class JitterBuffer(
    private val latencyMode: AudioLatencyMode = AudioLatencyMode.LOW,
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val lock = Any()
    private val frames = TreeMap<Int, RtpAudioFrame>()
    private var started = false
    private var expectedSequence: Int? = null
    private var highestSequence: Int? = null
    private var initialPlayoutAtMs: Long? = null
    private var nextPlayoutAtMs: Long? = null
    private var droppedFrames: Long = 0
    private var latePacketDrops: Long = 0
    private var overflowDrops: Long = 0
    private var duplicatePackets: Long = 0
    private var reorderedPackets: Long = 0
    private var underflowCount: Long = 0
    private var plcCount: Long = 0

    fun add(frame: RtpAudioFrame) {
        synchronized(lock) {
            val expected = expectedSequence
            if (expected != null && RtpCodec.isSequenceBefore(frame.sequenceNumber, expected)) {
                latePacketDrops++
                droppedFrames++
                return
            }
            if (frames.containsKey(frame.sequenceNumber)) {
                duplicatePackets++
                droppedFrames++
                return
            }
            if (initialPlayoutAtMs == null && !started) {
                initialPlayoutAtMs = clockMs() + latencyMode.initialDelayMs
            }
            val highest = highestSequence
            if (highest != null) {
                if (RtpCodec.isSequenceBefore(frame.sequenceNumber, highest)) {
                    reorderedPackets++
                } else {
                    highestSequence = frame.sequenceNumber
                }
            } else {
                highestSequence = frame.sequenceNumber
            }
            frames[frame.sequenceNumber] = frame
            trimOverflowLocked()
        }
    }

    fun pollForPlayback(): JitterBufferPoll {
        synchronized(lock) {
            val nowMs = clockMs()
            if (!started) {
                if (frames.isEmpty()) {
                    return JitterBufferPoll.Wait(DEFAULT_WAIT_MS)
                }
                val initialDeadline = initialPlayoutAtMs ?: (nowMs + latencyMode.initialDelayMs).also {
                    initialPlayoutAtMs = it
                }
                if (nowMs < initialDeadline) {
                    return JitterBufferPoll.Wait(initialDeadline - nowMs)
                }
                expectedSequence = frames.firstKey()
                nextPlayoutAtMs = initialDeadline
                started = true
            }

            val playoutDeadline = nextPlayoutAtMs ?: nowMs
            if (nowMs < playoutDeadline) {
                return JitterBufferPoll.Wait(playoutDeadline - nowMs)
            }

            val expected = expectedSequence ?: return JitterBufferPoll.Wait(DEFAULT_WAIT_MS)
            val frame = frames.remove(expected)
            expectedSequence = RtpCodec.nextSequence(expected)
            nextPlayoutAtMs = playoutDeadline + AudioProtocol.FRAME_DURATION_MS
            return if (frame != null) {
                JitterBufferPoll.Packet(frame)
            } else {
                underflowCount++
                plcCount++
                JitterBufferPoll.Missing
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            frames.clear()
            started = false
            expectedSequence = null
            highestSequence = null
            initialPlayoutAtMs = null
            nextPlayoutAtMs = null
            droppedFrames = 0
            latePacketDrops = 0
            overflowDrops = 0
            duplicatePackets = 0
            reorderedPackets = 0
            underflowCount = 0
            plcCount = 0
        }
    }

    fun snapshot(): JitterBufferSnapshot {
        synchronized(lock) {
            return JitterBufferSnapshot(
                bufferLevelMs = bufferLevelLocked(),
                droppedFrames = droppedFrames,
                latePacketDrops = latePacketDrops,
                overflowDrops = overflowDrops,
                duplicatePackets = duplicatePackets,
                reorderedPackets = reorderedPackets,
                underflowCount = underflowCount,
                plcCount = plcCount,
                queuedFrames = frames.size
            )
        }
    }

    private fun trimOverflowLocked() {
        while (bufferLevelLocked() > latencyMode.maxDelayMs && frames.isNotEmpty()) {
            frames.pollFirstEntry()
            droppedFrames++
            overflowDrops++
        }
    }

    private fun bufferLevelLocked(): Int = frames.size * AudioProtocol.FRAME_DURATION_MS

    private companion object {
        const val DEFAULT_WAIT_MS = 5L
    }
}
