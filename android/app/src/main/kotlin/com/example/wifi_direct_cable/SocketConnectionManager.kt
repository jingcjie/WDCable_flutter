package com.example.wifi_direct_cable

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodChannel
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.math.pow

class SocketConnectionManager(
    private val methodChannel: MethodChannel,
    private val activity: android.app.Activity
) {
    // Service references for delegation
    private val chatService: ChatService
    private val speedTestService: SpeedTestService
    private val fileTransferService: FileTransferService
    // Multiple ports for different functions
    private val CHAT_PORT = 8888
    private val SPEED_TEST_PORT = 8889
    private val FILE_TRANSFER_PORT = 8890
    
    // Connection state
    private var isConnected = false
    private var isGroupOwner = false
    private var groupOwnerAddress: String? = null
    
    // Multiple socket pairs for different functions
    // Chat sockets
    private var chatServerSocket: ServerSocket? = null
    private var chatClientSocket: Socket? = null
    private val isChatServerRunning = AtomicBoolean(false)
    
    // Speed test sockets
    private var speedTestServerSocket: ServerSocket? = null
    private var speedTestClientSocket: Socket? = null
    private val isSpeedTestServerRunning = AtomicBoolean(false)
    
    // File transfer sockets
    private var fileTransferServerSocket: ServerSocket? = null
    private var fileTransferClientSocket: Socket? = null
    private val isFileTransferServerRunning = AtomicBoolean(false)
    
    // TCP settings
    private var bufferSize = 8192
    private var timeout = 0
    private var keepAlive = true
    
    // Retry settings
    private val MAX_RETRY_ATTEMPTS = 10
    private val INITIAL_RETRY_DELAY_MS = 1000L
    private val MAX_RETRY_DELAY_MS = 30000L
    
    // Retry state for each service
    private val chatRetryAttempts = AtomicBoolean(false)
    private val speedTestRetryAttempts = AtomicBoolean(false)
    private val fileTransferRetryAttempts = AtomicBoolean(false)
    
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Initialize services internally
    init {
        chatService = ChatService(this, methodChannel)
        speedTestService = SpeedTestService(this, methodChannel)
        fileTransferService = FileTransferService(activity, this, methodChannel)
    }
    
    fun updateConnectionState(connected: Boolean, groupOwner: Boolean, ownerAddress: String?) {
        isConnected = connected
        isGroupOwner = groupOwner
        groupOwnerAddress = ownerAddress
    }
    
    fun updateConnectionInfo(connected: Boolean, groupOwner: Boolean, ownerAddress: String?) {
        // Clean up existing connections when connection info changes
        if (isConnected != connected || isGroupOwner != groupOwner || groupOwnerAddress != ownerAddress) {
            cleanup()
        }
        
        updateConnectionState(connected, groupOwner, ownerAddress)
    }
    
    fun startServers() {
        if (isGroupOwner) {
            startChatServer()
            startSpeedTestServer()
            startFileTransferServer()
        } else {
            // Non-group owners only connect as clients, don't start servers
            groupOwnerAddress?.let { connectToServer(it) }
        }
    }
    
    fun getConnectionState() = Triple(isConnected, isGroupOwner, groupOwnerAddress)
    
    fun getChatSocket(): Socket? = chatClientSocket
    fun getSpeedTestSocket(): Socket? = speedTestClientSocket
    fun getFileTransferSocket(): Socket? = fileTransferClientSocket
    
    // Service getters for external access
    fun getChatService(): ChatService = chatService
    fun getSpeedTestService(): SpeedTestService = speedTestService
    fun getFileTransferService(): FileTransferService = fileTransferService
    
    fun configureTcpSettings(bufferSize: Int, timeout: Int, keepAlive: Boolean) {
        this.bufferSize = bufferSize
        this.timeout = timeout
        this.keepAlive = keepAlive
    }
    
    fun getTcpSettings() = Triple(bufferSize, timeout, keepAlive)
    
    fun getConnectionStats(): Map<String, Any> {
        return mapOf(
            "isConnected" to isConnected,
            "isGroupOwner" to isGroupOwner,
            "groupOwnerAddress" to (groupOwnerAddress ?: ""),
            "isChatServerRunning" to isChatServerRunning.get(),
            "isSpeedTestServerRunning" to isSpeedTestServerRunning.get(),
            "isFileTransferServerRunning" to isFileTransferServerRunning.get(),
            "bufferSize" to bufferSize,
            "timeout" to timeout,
            "keepAlive" to keepAlive
        )
    }
    
    fun startChatServer() {
        if (isChatServerRunning.get()) return
        
        executor.execute {
            try {
                chatServerSocket = ServerSocket(CHAT_PORT)
                chatServerSocket?.soTimeout = 0 // No timeout for server socket
                isChatServerRunning.set(true)
                
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "Chat server started on port $CHAT_PORT")
                }
                
                while (isChatServerRunning.get()) {
                    try {
                        val clientSocket = chatServerSocket?.accept()
                        if (clientSocket != null) {
                            chatClientSocket = clientSocket
                            chatClientSocket?.keepAlive = keepAlive
                            chatClientSocket?.soTimeout = 0 // No timeout for persistent connection
                            
                            mainHandler.post {
                                methodChannel.invokeMethod("onDebug", "Chat client connected")
                            }
                            
                            // Start listening for chat messages
                            chatService.startChatListener(chatClientSocket)
                        }
                    } catch (e: Exception) {
                        if (isChatServerRunning.get()) {
                            mainHandler.post {
                                methodChannel.invokeMethod("onError", "Chat server error: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    methodChannel.invokeMethod("onError", "Failed to start chat server: ${e.message}")
                }
            }
        }
    }
    
    fun startSpeedTestServer() {
        if (isSpeedTestServerRunning.get()) return
        
        executor.execute {
            try {
                speedTestServerSocket = ServerSocket(SPEED_TEST_PORT)
                speedTestServerSocket?.soTimeout = 0 // No timeout for server socket
                isSpeedTestServerRunning.set(true)
                
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "Speed test server started on port $SPEED_TEST_PORT")
                }
                
                while (isSpeedTestServerRunning.get()) {
                    try {
                        val clientSocket = speedTestServerSocket?.accept()
                        if (clientSocket != null) {
                            speedTestClientSocket = clientSocket
                            speedTestClientSocket?.keepAlive = keepAlive
                            speedTestClientSocket?.soTimeout = 0 // No timeout for persistent connection
                            
                            mainHandler.post {
                                methodChannel.invokeMethod("onDebug", "Speed test client connected")
                            }
                            
                            // Start listening for speed test protocol messages
                            speedTestService.startSpeedTestListener(speedTestClientSocket)
                        }
                    } catch (e: Exception) {
                        if (isSpeedTestServerRunning.get()) {
                            mainHandler.post {
                                methodChannel.invokeMethod("onError", "Speed test server error: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    methodChannel.invokeMethod("onError", "Failed to start speed test server: ${e.message}")
                }
            }
        }
    }
    
    fun startFileTransferServer() {
        if (isFileTransferServerRunning.get()) return
        
        executor.execute {
            try {
                fileTransferServerSocket = ServerSocket(FILE_TRANSFER_PORT)
                fileTransferServerSocket?.soTimeout = 0 // No timeout for server socket
                isFileTransferServerRunning.set(true)
                
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "File transfer server started on port $FILE_TRANSFER_PORT")
                }
                
                while (isFileTransferServerRunning.get()) {
                    try {
                        val clientSocket = fileTransferServerSocket?.accept()
                        if (clientSocket != null) {
                            fileTransferClientSocket = clientSocket
                            fileTransferClientSocket?.keepAlive = keepAlive
                            fileTransferClientSocket?.soTimeout = 0 // No timeout for persistent connection
                            
                            mainHandler.post {
                                methodChannel.invokeMethod("onDebug", "File transfer client connected")
                            }
                            
                            // Start listening for file transfers
                            fileTransferService.startFileTransferListener(fileTransferClientSocket)
                        }
                    } catch (e: Exception) {
                        if (isFileTransferServerRunning.get()) {
                            mainHandler.post {
                                methodChannel.invokeMethod("onError", "File transfer server error: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    methodChannel.invokeMethod("onError", "Failed to start file transfer server: ${e.message}")
                }
            }
        }
    }
    
    fun connectToServer(serverAddress: String) {
        // Start retry connections for each service
        connectChatWithRetry(serverAddress)
        connectSpeedTestWithRetry(serverAddress)
        connectFileTransferWithRetry(serverAddress)
    }
    
    private fun connectChatWithRetry(serverAddress: String, attempt: Int = 1) {
        if (!isConnected || attempt > MAX_RETRY_ATTEMPTS) {
            if (attempt > MAX_RETRY_ATTEMPTS) {
                mainHandler.post {
                    methodChannel.invokeMethod("onError", "Chat connection failed after $MAX_RETRY_ATTEMPTS attempts")
                }
            }
            return
        }
        
        executor.execute {
            try {
                chatClientSocket?.close()
                chatClientSocket = Socket(serverAddress, CHAT_PORT)
                chatClientSocket?.keepAlive = keepAlive
                chatClientSocket?.soTimeout = 0
                chatService.startChatListener(chatClientSocket)
                
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "Chat connected to $serverAddress (attempt $attempt)")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "Chat connection attempt $attempt failed: ${e.message}")
                }
                
                // Schedule retry with exponential backoff
                val delay = calculateRetryDelay(attempt)
                mainHandler.postDelayed({
                    connectChatWithRetry(serverAddress, attempt + 1)
                }, delay)
            }
        }
    }
    
    private fun connectSpeedTestWithRetry(serverAddress: String, attempt: Int = 1) {
        if (!isConnected || attempt > MAX_RETRY_ATTEMPTS) {
            if (attempt > MAX_RETRY_ATTEMPTS) {
                mainHandler.post {
                    methodChannel.invokeMethod("onError", "Speed test connection failed after $MAX_RETRY_ATTEMPTS attempts")
                }
            }
            return
        }
        
        executor.execute {
            try {
                speedTestClientSocket?.close()
                speedTestClientSocket = Socket(serverAddress, SPEED_TEST_PORT)
                speedTestClientSocket?.keepAlive = keepAlive
                speedTestClientSocket?.soTimeout = 0
                speedTestService.startSpeedTestListener(speedTestClientSocket)
                
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "Speed test connected to $serverAddress (attempt $attempt)")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "Speed test connection attempt $attempt failed: ${e.message}")
                }
                
                // Schedule retry with exponential backoff
                val delay = calculateRetryDelay(attempt)
                mainHandler.postDelayed({
                    connectSpeedTestWithRetry(serverAddress, attempt + 1)
                }, delay)
            }
        }
    }
    
    private fun connectFileTransferWithRetry(serverAddress: String, attempt: Int = 1) {
        if (!isConnected || attempt > MAX_RETRY_ATTEMPTS) {
            if (attempt > MAX_RETRY_ATTEMPTS) {
                mainHandler.post {
                    methodChannel.invokeMethod("onError", "File transfer connection failed after $MAX_RETRY_ATTEMPTS attempts")
                }
            }
            return
        }
        
        executor.execute {
            try {
                fileTransferClientSocket?.close()
                fileTransferClientSocket = Socket(serverAddress, FILE_TRANSFER_PORT)
                fileTransferClientSocket?.keepAlive = keepAlive
                fileTransferClientSocket?.soTimeout = 0
                fileTransferService.startFileTransferListener(fileTransferClientSocket)
                
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "File transfer connected to $serverAddress (attempt $attempt)")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    methodChannel.invokeMethod("onDebug", "File transfer connection attempt $attempt failed: ${e.message}")
                }
                
                // Schedule retry with exponential backoff
                val delay = calculateRetryDelay(attempt)
                mainHandler.postDelayed({
                    connectFileTransferWithRetry(serverAddress, attempt + 1)
                }, delay)
            }
        }
    }
    
    private fun calculateRetryDelay(attempt: Int): Long {
        val exponentialDelay = INITIAL_RETRY_DELAY_MS * (2.0.pow(attempt - 1)).toLong()
        return min(exponentialDelay, MAX_RETRY_DELAY_MS)
    }
    
    fun retryConnectionIfNeeded(serviceName: String) {
        if (!isConnected) return
        
        groupOwnerAddress?.let { address ->
            when (serviceName) {
                "chat" -> {
                    if (chatClientSocket?.isClosed == true || chatClientSocket?.isConnected == false) {
                        connectChatWithRetry(address)
                    }
                }
                "speedTest" -> {
                    if (speedTestClientSocket?.isClosed == true || speedTestClientSocket?.isConnected == false) {
                        connectSpeedTestWithRetry(address)
                    }
                }
                "fileTransfer" -> {
                    if (fileTransferClientSocket?.isClosed == true || fileTransferClientSocket?.isConnected == false) {
                        connectFileTransferWithRetry(address)
                    }
                }
            }
        }
    }
    

    
    fun cleanup() {
        isChatServerRunning.set(false)
        isSpeedTestServerRunning.set(false)
        isFileTransferServerRunning.set(false)
        
        try {
            chatClientSocket?.close()
            chatClientSocket = null
        } catch (e: Exception) {
            // Ignore
        }
        
        try {
            speedTestClientSocket?.close()
            speedTestClientSocket = null
        } catch (e: Exception) {
            // Ignore
        }
        
        try {
            fileTransferClientSocket?.close()
            fileTransferClientSocket = null
        } catch (e: Exception) {
            // Ignore
        }
        
        try {
            chatServerSocket?.close()
            chatServerSocket = null
        } catch (e: Exception) {
            // Ignore
        }
        
        try {
            speedTestServerSocket?.close()
            speedTestServerSocket = null
        } catch (e: Exception) {
            // Ignore
        }
        
        try {
            fileTransferServerSocket?.close()
            fileTransferServerSocket = null
        } catch (e: Exception) {
            // Ignore
        }
        
        isConnected = false
        isGroupOwner = false
        groupOwnerAddress = null
    }
}