package com.example.wifi_direct_cable

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class ChatService(
    private val socketManager: SocketConnectionManager,
    private val methodChannel: MethodChannel
) {
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    fun sendData(data: String?, result: MethodChannel.Result) {
        if (data == null) {
            result.error("INVALID_ARGUMENT", "Data is required", null)
            return
        }
        
        val (isConnected, _, _) = socketManager.getConnectionState()
        if (!isConnected) {
            result.error("NOT_CONNECTED", "Not connected to any peer", null)
            return
        }
        
        executor.execute {
            try {
                val socket = socketManager.getChatSocket()
                if (socket != null && !socket.isClosed) {
                    val output = socket.getOutputStream()
                    val writer = PrintWriter(output, true)
                    
                    // Encode message as JSON
                    val jsonMessage = JSONObject()
                    jsonMessage.put("message", data)
                    jsonMessage.put("timestamp", System.currentTimeMillis())
                    
                    writer.println(jsonMessage.toString())
                    
                    mainHandler.post {
                        result.success("Data sent")
                    }
                } else {
                    mainHandler.post {
                        result.error("CONNECTION_ERROR", "No active chat connection", null)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    result.error("SEND_ERROR", "Failed to send data: ${e.message}", null)
                    methodChannel.invokeMethod("onError", "Send error: ${e.message}")
                }
            }
        }
    }
    
    fun startChatListener(socket: Socket?) {
        thread {
            try {
                if (socket != null && !socket.isClosed) {
                    socket.soTimeout = 0 // Remove timeout for persistent connection
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        try {
                            val line = reader.readLine()
                            if (line != null) {
                                mainHandler.post {
                                    try {
                                        // Try to parse as JSON first
                                        val jsonObject = org.json.JSONObject(line)
                                        val message = jsonObject.getString("message")
                                        val timestamp = jsonObject.optLong("timestamp", System.currentTimeMillis())
                                        
                                        // Send parsed message data to Flutter
                                        val messageData = mapOf(
                                            "message" to message,
                                            "timestamp" to timestamp
                                        )
                                        methodChannel.invokeMethod("onDataReceived", messageData)
                                    } catch (jsonException: org.json.JSONException) {
                                        // Fallback: treat as plain text for backward compatibility
                                        methodChannel.invokeMethod("onDataReceived", line)
                                    }
                                }
                            } else {
                                // Connection closed by server
                                mainHandler.post {
                                    methodChannel.invokeMethod("onDebug", "Chat connection closed, attempting to reconnect...")
                                }
                                socketManager.retryConnectionIfNeeded("chat")
                                break
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            // Continue listening, this is expected for persistent connections
                            continue
                        } catch (e: java.net.SocketException) {
                            // Socket connection error, attempt to reconnect
                            mainHandler.post {
                                methodChannel.invokeMethod("onDebug", "Chat socket error: ${e.message}, attempting to reconnect...")
                            }
                            socketManager.retryConnectionIfNeeded("chat")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    methodChannel.invokeMethod("onError", "Chat listener error: ${e.message}")
                }
                // Attempt to reconnect on unexpected errors
                socketManager.retryConnectionIfNeeded("chat")
            }
        }
    }
}