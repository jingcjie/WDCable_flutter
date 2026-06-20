package com.jingcjie.wifi_direct_cable.audio

object NativeOpus {
    val available: Boolean by lazy {
        try {
            System.loadLibrary("opus")
            System.loadLibrary("wdcable_opus_jni")
            nativeVersion().isNotBlank()
        } catch (_: Throwable) {
            false
        }
    }

    fun versionString(): String = if (available) nativeVersion() else ""

    fun createEncoder(sampleRate: Int, channels: Int, bitrateBps: Int): Long {
        ensureAvailable()
        return nativeCreateEncoder(sampleRate, channels, bitrateBps)
    }

    fun destroyEncoder(handle: Long) {
        if (available && handle != 0L) {
            nativeDestroyEncoder(handle)
        }
    }

    fun encode(handle: Long, pcm: ByteArray, frameSize: Int, output: ByteArray): Int {
        ensureAvailable()
        return nativeEncode(handle, pcm, frameSize, output, output.size)
    }

    fun createDecoder(sampleRate: Int, channels: Int): Long {
        ensureAvailable()
        return nativeCreateDecoder(sampleRate, channels)
    }

    fun destroyDecoder(handle: Long) {
        if (available && handle != 0L) {
            nativeDestroyDecoder(handle)
        }
    }

    fun decode(handle: Long, packet: ByteArray?, frameSize: Int, pcm: ByteArray, decodeFec: Boolean = false): Int {
        ensureAvailable()
        return nativeDecode(handle, packet, packet?.size ?: 0, frameSize, pcm, decodeFec)
    }

    private fun ensureAvailable() {
        check(available) { "libopus JNI runtime is not available" }
    }

    private external fun nativeVersion(): String
    private external fun nativeCreateEncoder(sampleRate: Int, channels: Int, bitrateBps: Int): Long
    private external fun nativeDestroyEncoder(handle: Long)
    private external fun nativeEncode(handle: Long, pcm: ByteArray, frameSize: Int, output: ByteArray, maxOutputBytes: Int): Int
    private external fun nativeCreateDecoder(sampleRate: Int, channels: Int): Long
    private external fun nativeDestroyDecoder(handle: Long)
    private external fun nativeDecode(
        handle: Long,
        packet: ByteArray?,
        packetLength: Int,
        frameSize: Int,
        pcm: ByteArray,
        decodeFec: Boolean
    ): Int
}
