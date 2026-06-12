package com.example.wifi_direct_cable

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.wifi_direct_cable.diagnostics.DiagnosticsLogger
import com.example.wifi_direct_cable.session.SessionManager
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.atomic.AtomicBoolean

class SpeedTestService(
    private val sessionManager: SessionManager,
    private val methodChannel: MethodChannel
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    companion object {
        private const val TAG = "SpeedTestService"
    }

    private val isSpeedTesting = AtomicBoolean(false)

    /**
     * Sets the speed testing flag to indicate whether a speed test is currently active
     */
    fun setSpeedTesting(enabled: Boolean) {
        isSpeedTesting.set(enabled)
        if (!enabled) {
            sessionManager.clearSpeedTestState()
        }
        Log.d(TAG, "Speed testing mode set to: $enabled")
    }

    /**
     * Returns the current speed testing status
     */
    fun isSpeedTesting(): Boolean {
        return isSpeedTesting.get()
    }

    /**
     * Initiates a speed test by requesting data of specified size from the connected peer
     */
    fun requestSpeedTestData(sizeBytes: Long, result: MethodChannel.Result) {
        DiagnosticsLogger.log("speed", "Speed download requested", mapOf("sizeBytes" to sizeBytes))
        sessionManager.requestSpeedTestData(sizeBytes, result)
    }

    /**
     * Sends speed test data of specified size to the connected peer
     */
    fun sendSpeedTestData(sizeBytes: Long, result: MethodChannel.Result) {
        DiagnosticsLogger.log("speed", "Speed upload requested", mapOf("sizeBytes" to sizeBytes))
        sessionManager.sendSpeedTestData(sizeBytes, result)
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        setSpeedTesting(false)
        sessionManager.getConnectionStats()
        Log.d(TAG, "SpeedTestService cleaned up")
    }
}
