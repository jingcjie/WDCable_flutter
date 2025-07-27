package com.example.wifi_direct_cable

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.plugin.common.MethodChannel
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class SpeedTestService(
    private val socketManager: SocketConnectionManager,
    private val methodChannel: MethodChannel
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    companion object {
        private const val TAG = "SpeedTestService"
        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_UPDATE_INTERVAL = 100 // Update every 100ms
        private const val BASE_TIMEOUT_MS = 30000 // 30 seconds base timeout
        private const val MIN_TIMEOUT_MS = 5000 // 5 seconds minimum timeout
        private const val MAX_CONSECUTIVE_ZERO_READS = 10
    }

    private val isSpeedTesting = AtomicBoolean(false)
    private var speedTestListenerThread: Thread? = null

    /**
     * Sets the speed testing flag to indicate whether a speed test is currently active
     */
    fun setSpeedTesting(enabled: Boolean) {
        isSpeedTesting.set(enabled)
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
        thread {
            try {
                val socket = socketManager.getSpeedTestSocket()
                if (socket == null || socket.isClosed) {
                    result.error("NO_CONNECTION", "No active speed test connection", null)
                    return@thread
                }

                setSpeedTesting(true)
                Log.d(TAG, "Requesting speed test data: $sizeBytes bytes")

                // Send speed test request protocol message
                val requestMessage = "SPEED_TEST_REQUEST:$sizeBytes\n"
                socket.getOutputStream().write(requestMessage.toByteArray())
                socket.getOutputStream().flush()

                result.success("Speed test data request sent: $sizeBytes bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request speed test data", e)
                setSpeedTesting(false)
                result.error("REQUEST_FAILED", "Failed to request speed test data: ${e.message}", null)
            }
        }
    }

    /**
     * Sends speed test data of specified size to the connected peer
     */
    fun sendSpeedTestData(sizeBytes: Long, result: MethodChannel.Result) {
        thread {
            try {
                val socket = socketManager.getSpeedTestSocket()
                if (socket == null || socket.isClosed) {
                    result.error("NO_CONNECTION", "No active speed test connection", null)
                    return@thread
                }

                Log.d(TAG, "Sending speed test data: $sizeBytes bytes")
                val startTime = System.currentTimeMillis()
                var bytesSent = 0L
                var lastProgressUpdate = startTime

                // Send protocol header
                val headerMessage = "SPEED_TEST_DATA:$sizeBytes\n"
                socket.getOutputStream().write(headerMessage.toByteArray())
                socket.getOutputStream().flush()

                // Create buffer for sending data
                val buffer = ByteArray(BUFFER_SIZE)
                val outputStream = socket.getOutputStream()

                // Calculate dynamic timeout based on expected transfer time
                val estimatedTimeMs = (sizeBytes / (1024 * 1024)) * 1000 // Assume 1MB/s minimum
                val timeoutMs = maxOf(MIN_TIMEOUT_MS, minOf(BASE_TIMEOUT_MS, estimatedTimeMs.toInt() * 2))
                socket.soTimeout = timeoutMs

                while (bytesSent < sizeBytes) {
                    val remainingBytes = sizeBytes - bytesSent
                    val chunkSize = minOf(BUFFER_SIZE.toLong(), remainingBytes).toInt()

                    outputStream.write(buffer, 0, chunkSize)
                    outputStream.flush()
                    bytesSent += chunkSize

                    val currentTime = System.currentTimeMillis()
                    
                    // Send progress updates
                    if (currentTime - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL) {
                        val elapsedTime = currentTime - startTime
                        val speedMbps = calculateSpeedMbps(bytesSent, elapsedTime)
                        val progress = bytesSent.toDouble() / sizeBytes.toDouble()

                        mainHandler.post {
                            methodChannel.invokeMethod("onSpeedTestSendProgress", mapOf(
                                "bytesSent" to bytesSent,
                                "totalBytes" to sizeBytes,
                                "speedMbps" to speedMbps,
                                "progress" to progress
                            ))
                        }

                        lastProgressUpdate = currentTime
                    }
                }

                val totalTime = System.currentTimeMillis() - startTime
                val finalSpeedMbps = calculateSpeedMbps(sizeBytes, totalTime)

                Log.d(TAG, "Speed test data sent: $sizeBytes bytes in ${totalTime}ms = ${finalSpeedMbps} Mbps")
                result.success("Speed test data sent: $sizeBytes bytes in ${totalTime}ms")

                // Send final progress update
                mainHandler.post {
                    methodChannel.invokeMethod("onSpeedTestSendProgress", mapOf(
                        "bytesSent" to sizeBytes,
                        "totalBytes" to sizeBytes,
                        "speedMbps" to finalSpeedMbps,
                        "progress" to 1.0
                    ))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to send speed test data", e)
                result.error("SEND_FAILED", "Failed to send speed test data: ${e.message}", null)
            } finally {
                // Trigger garbage collection to free memory
                System.gc()
            }
        }
    }

    /**
     * Starts a background listener thread to handle incoming speed test requests
     */
    fun startSpeedTestListener(speedTestClientSocket: Socket?) {
        speedTestListenerThread?.interrupt()
        
        speedTestListenerThread = thread {
            try {
                Log.d(TAG, "Speed test listener started")
                
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        val socket = speedTestClientSocket
                        if (socket == null || socket.isClosed) {
                            Thread.sleep(1000)
                            continue
                        }

                        socket.soTimeout = 1000 // 1 second timeout for non-blocking reads
                        val inputStream = socket.getInputStream()
                        
                        try {
                            // Read protocol header line by line
                            val headerBuffer = StringBuilder()
                            var char: Int
                            while (inputStream.read().also { char = it } != -1) {
                                if (char == '\n'.code) {
                                    break
                                }
                                headerBuffer.append(char.toChar())
                            }
                            
                            if (headerBuffer.isNotEmpty()) {
                                val message = headerBuffer.toString().trim()
                                Log.d(TAG, "Speed test listener received protocol message: $message")
                                
                                when {
                                    message.startsWith("SPEED_TEST_REQUEST:") -> {
                                        val sizeStr = message.substringAfter("SPEED_TEST_REQUEST:")
                                        val sizeBytes = sizeStr.toLongOrNull()
                                        if (sizeBytes != null) {
                                            Log.d(TAG, "Received speed test request for $sizeBytes bytes")
                                            sendSpeedTestData(sizeBytes, object : MethodChannel.Result {
                                                override fun success(result: Any?) {
                                                    Log.d(TAG, "Speed test data sent successfully")
                                                }
                                                override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                                                    Log.e(TAG, "Failed to send speed test data: $errorMessage")
                                                }
                                                override fun notImplemented() {}
                                            })
                                        }
                                    }
                                    message.startsWith("SPEED_TEST_DATA:") -> {
                                        val sizeStr = message.substringAfter("SPEED_TEST_DATA:")
                                        val sizeBytes = sizeStr.toLongOrNull()
                                        if (sizeBytes != null) {
                                            Log.d(TAG, "Receiving speed test data: $sizeBytes bytes")
                                            handleSpeedTestDataReceive(socket, sizeBytes)
                                        }
                                    }
                                }
                            }
                        } catch (e: SocketTimeoutException) {
                            // Normal timeout, continue listening
                            continue
                        }
                        
                    } catch (e: Exception) {
                        if (!Thread.currentThread().isInterrupted) {
                            Log.e(TAG, "Error in speed test listener", e)
                            // Check if it's a connection error that requires retry
                            if (e is java.net.SocketException || e is java.io.IOException) {
                                mainHandler.post {
                                    methodChannel.invokeMethod("onDebug", "Speed test connection error: ${e.message}, attempting to reconnect...")
                                }
                                socketManager.retryConnectionIfNeeded("speedTest")
                                break
                            }
                            Thread.sleep(1000)
                        }
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Speed test listener interrupted")
            } catch (e: Exception) {
                Log.e(TAG, "Speed test listener error", e)
                // Attempt to reconnect on unexpected errors
                if (!Thread.currentThread().isInterrupted) {
                    mainHandler.post {
                        methodChannel.invokeMethod("onDebug", "Speed test listener crashed: ${e.message}, attempting to reconnect...")
                    }
                    socketManager.retryConnectionIfNeeded("speedTest")
                }
            } finally {
                Log.d(TAG, "Speed test listener stopped")
            }
        }
    }

    /**
     * Stops the speed test listener thread
     */
    fun stopSpeedTestListener() {
        speedTestListenerThread?.interrupt()
        speedTestListenerThread = null
        Log.d(TAG, "Speed test listener stopped")
    }

    /**
     * Calculates transfer speed in Megabits per second
     */
    private fun calculateSpeedMbps(bytes: Long, elapsedTimeMs: Long): Double {
        if (elapsedTimeMs <= 0) return 0.0
        val bits = bytes * 8.0
        val seconds = elapsedTimeMs / 1000.0
        return (bits / seconds) / (1024 * 1024) // Convert to Mbps
    }

    /**
     * Handles receiving speed test data from peer
     */
    private fun handleSpeedTestDataReceive(socket: Socket, sizeBytes: Long) {
        try {
            Log.d(TAG, "Starting to receive speed test data: $sizeBytes bytes")
            val startTime = System.currentTimeMillis()
            var bytesReceived = 0L
            var lastProgressUpdate = startTime
            var consecutiveZeroReads = 0

            val buffer = ByteArray(BUFFER_SIZE)
            val inputStream = socket.getInputStream()

            // Set timeout for receiving data
            socket.soTimeout = BASE_TIMEOUT_MS

            while (bytesReceived < sizeBytes) {
                val remainingBytes = sizeBytes - bytesReceived
                val maxRead = minOf(BUFFER_SIZE.toLong(), remainingBytes).toInt()

                val bytesRead = inputStream.read(buffer, 0, maxRead)
                
                if (bytesRead > 0) {
                    bytesReceived += bytesRead
                    consecutiveZeroReads = 0
                    
                    val currentTime = System.currentTimeMillis()
                    
                    // Send progress updates during active speed tests
                    if (isSpeedTesting() && currentTime - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL) {
                        val elapsedTime = currentTime - startTime
                        val speedMbps = calculateSpeedMbps(bytesReceived, elapsedTime)
                        val progress = bytesReceived.toDouble() / sizeBytes.toDouble()

                        mainHandler.post {
                            methodChannel.invokeMethod("onSpeedTestReceiveProgress", mapOf(
                                "bytesReceived" to bytesReceived,
                                "totalBytes" to sizeBytes,
                                "speedMbps" to speedMbps,
                                "progress" to progress
                            ))
                        }

                        lastProgressUpdate = currentTime
                    }
                } else if (bytesRead == 0) {
                    consecutiveZeroReads++
                    if (consecutiveZeroReads >= MAX_CONSECUTIVE_ZERO_READS) {
                        Log.w(TAG, "Too many consecutive zero reads, connection may be closed")
                        break
                    }
                    Thread.sleep(10) // Brief pause to avoid busy waiting
                } else {
                    Log.w(TAG, "End of stream reached before all data received")
                    break
                }
            }

            val totalTime = System.currentTimeMillis() - startTime
            val finalSpeedMbps = calculateSpeedMbps(bytesReceived, totalTime)

            Log.d(TAG, "Speed test data received: $bytesReceived bytes in ${totalTime}ms = ${finalSpeedMbps} Mbps")

            // Send completion event
            mainHandler.post {
                methodChannel.invokeMethod("onSpeedTestDataReceived", mapOf(
                    "bytesReceived" to bytesReceived,
                    "durationMs" to totalTime,
                    "speedMbps" to finalSpeedMbps
                ))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error receiving speed test data", e)
            mainHandler.post {
                methodChannel.invokeMethod("onError", "Failed to receive speed test data: ${e.message}")
            }
        } finally {
            // Trigger garbage collection to free memory
            System.gc()
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopSpeedTestListener()
        setSpeedTesting(false)
        Log.d(TAG, "SpeedTestService cleaned up")
    }
}