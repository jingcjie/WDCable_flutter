import 'dart:async';
import 'dart:convert';
import 'dart:io';
import '../wifi_direct_service.dart';
import '../models/wifi_direct_models.dart';

/// Controller that manages WiFi Direct state and business logic
class WiFiDirectController {
  final WiFiDirectService _service;
  late final StreamController<WiFiDirectState> _stateController;
  late final StreamSubscription _eventSubscription;
  
  WiFiDirectState _currentState = WiFiDirectState();
  double _lastReceivedDownloadSpeed = 0.0;
  double _lastUploadSpeed = 0.0;
  
  /// Stream of state changes
  Stream<WiFiDirectState> get stateStream => _stateController.stream;
  
  /// Stream of WiFi Direct events
  Stream<WiFiDirectEvent> get eventStream => _service.eventStream;
  
  /// Current state
  WiFiDirectState get currentState => _currentState;
  
  WiFiDirectController(this._service) {
    _stateController = StreamController<WiFiDirectState>.broadcast();
    _setupEventListeners();
    _initializeState();
  }
  
  void _setupEventListeners() {
    _eventSubscription = _service.eventStream.listen((event) {
      _handleEvent(event);
    });
  }
  
  void _handleEvent(WiFiDirectEvent event) {
    switch (event.runtimeType) {
      case PeersChangedEvent:
        final peersEvent = event as PeersChangedEvent;
        _updateState(_currentState.copyWith(peers: peersEvent.peers));
        _addLog('Peers updated: ${peersEvent.peers.length} devices found');
        break;
        
      case WiFiP2pStateChangedEvent:
        final stateEvent = event as WiFiP2pStateChangedEvent;
        _updateState(_currentState.copyWith(isWifiP2pEnabled: stateEvent.enabled));
        final wifiP2pStatus = 'WiFi P2P ${stateEvent.enabled ? "enabled" : "disabled"}';
        _addLog(wifiP2pStatus);
        print('[ANDROID WIFI] $wifiP2pStatus');
        break;
        
      case ConnectionChangedEvent:
        final connectionEvent = event as ConnectionChangedEvent;
        _updateState(_currentState.copyWith(connectionInfo: connectionEvent.connectionInfo));
        if (connectionEvent.connectionInfo.isConnected) {
          final connectionStatus = 'Connected as ${connectionEvent.connectionInfo.isGroupOwner ? "Group Owner" : "Client"}';
          _addLog(connectionStatus);
          print('[ANDROID CONNECTION] $connectionStatus');
          stopDiscovery();
          // Multiple servers are now started automatically in Android code
          _updateState(_currentState.copyWith(isServerStarted: true));
          _addLog('Chat server (port 8888), Speed test server (port 8889), and File transfer server (port 8890) are now active');
        } else {
          _addLog('Disconnected');
          print('[ANDROID CONNECTION] Disconnected');
          _updateState(_currentState.copyWith(isServerStarted: false));
        }
        break;
        
      case DataReceivedEvent:
        final dataEvent = event as DataReceivedEvent;
        _handleDataReceived(dataEvent.message, dataEvent.timestamp);
        break;
        
      case BinaryDataReceivedEvent:
        final binaryEvent = event as BinaryDataReceivedEvent;
        _handleBinaryDataReceived(binaryEvent.data);
        break;
        
      case FileReceiveStartedEvent:
        final startEvent = event as FileReceiveStartedEvent;
        _handleFileReceiveStarted(startEvent.fileName, startEvent.fileSize);
        break;
        
      case FileSendStartedEvent:
        final startEvent = event as FileSendStartedEvent;
        _handleFileSendStarted(startEvent.fileName, startEvent.fileSize);
        break;
        
      case FileReceiveProgressEvent:
        final progressEvent = event as FileReceiveProgressEvent;
        _handleFileTransferProgress(progressEvent.fileName, progressEvent.progress);
        _addLog('File receive progress: ${progressEvent.fileName} - ${(progressEvent.progress * 100).toStringAsFixed(1)}%');
        break;
        
      case FileSendProgressEvent:
        final progressEvent = event as FileSendProgressEvent;
        _handleFileTransferProgress(progressEvent.fileName, progressEvent.progress);
        _addLog('File send progress: ${progressEvent.fileName} - ${(progressEvent.progress * 100).toStringAsFixed(1)}%');
        break;
        
      case FileReceivedEvent:
        final fileEvent = event as FileReceivedEvent;
        _handleFileReceived(fileEvent.fileName, fileEvent.filePath);
        break;
        
      case ErrorEvent:
        final errorEvent = event as ErrorEvent;
        final errorMessage = 'Error: ${errorEvent.error}';
        _addLog(errorMessage);
        // Also print to command line for debugging
        print('[ANDROID ERROR] ${errorEvent.error}');
        break;
        
      case DebugEvent:
        final debugEvent = event as DebugEvent;
        final debugMessage = 'Debug: ${debugEvent.message}';
        _addLog(debugMessage);
        // Also print to command line for debugging
        print('[ANDROID DEBUG] ${debugEvent.message}');
        break;
        
      case WiFiDirectResetEvent:
        _updateState(_currentState.copyWith(
          peers: [],
          connectionInfo: null,
          isServerStarted: false,
        ));
        _addLog('WiFi Direct settings have been reset');
        break;
        
      case PermissionDeniedEvent:
        _addLog('Permission denied. Please grant location permission.');
        break;
        
      case ClientConnectedEvent:
        final clientEvent = event as ClientConnectedEvent;
        final clientMessage = 'Client connected: ${clientEvent.message}';
        _addLog(clientMessage);
        print('[ANDROID CLIENT] ${clientEvent.message}');
        break;
        
      case SpeedTestDataReceivedEvent:
        final speedEvent = event as SpeedTestDataReceivedEvent;
        _handleSpeedTestDataReceived(speedEvent.bytesReceived, speedEvent.durationMs, speedEvent.speedMbps);
        break;
        
      case SpeedTestReceiveProgressEvent:
        final progressEvent = event as SpeedTestReceiveProgressEvent;
        _handleSpeedTestReceiveProgress(progressEvent.bytesReceived, progressEvent.totalBytes, progressEvent.speedMbps, progressEvent.progress);
        break;
        
      case SpeedTestSendProgressEvent:
        final progressEvent = event as SpeedTestSendProgressEvent;
        _handleSpeedTestSendProgress(progressEvent.bytesSent, progressEvent.totalBytes, progressEvent.speedMbps, progressEvent.progress);
        _lastUploadSpeed = progressEvent.speedMbps;
        break;
    }
  }
  
  void _handleDataReceived(String data, int? timestamp) {

    // Extract message content from JSON if applicable
    String messageContent = data;
    if (data.startsWith('{') && data.endsWith('}')) {
      try {
        final jsonData = json.decode(data);
        if (jsonData is Map<String, dynamic> && jsonData.containsKey('message')) {
          messageContent = jsonData['message'] as String;
        }
      } catch (e) {
        // If JSON parsing fails, use original data
      }
    }
    
    // Add the received message to chat
    _addLog('Message received: $messageContent');
    _addChatMessage(messageContent, false, timestamp);
  }
  
  void _handleBinaryDataReceived(List<int> data) {
    if (_currentState.isSpeedTesting) {
      // Drop data during speed tests to prevent out of memory errors
      _addLog('Speed test data received and dropped: ${data.length} bytes');
      return;
    }
    _addLog('Received binary data: ${data.length} bytes');
  }
  
  void _handleSpeedTestDataReceived(int bytesReceived, int durationMs, double speedMbps) {
    _addLog('Speed test data received: ${bytesReceived} bytes in ${durationMs}ms = ${speedMbps.toStringAsFixed(2)} Mbps');
    
    // Store the download speed result
    if (_currentState.isSpeedTesting) {
      final downloadSpeedMBps = speedMbps / 8.0; // Convert Mbps to MB/s
      
      // Create or update the speed test result
      final currentResult = _currentState.lastSpeedTest ?? SpeedTestResult(
        downloadSpeed: 0.0,
        uploadSpeed: 0.0,
        timestamp: DateTime.now(),
        status: 'In Progress',
      );
      
      _updateState(_currentState.copyWith(
        lastSpeedTest: currentResult.copyWith(
          downloadSpeed: downloadSpeedMBps,
        ),
      ));
      
      // Store the received speed for the download test method (keep in Mbps for internal use)
      _lastReceivedDownloadSpeed = speedMbps;
    }
  }
  
  void _handleSpeedTestReceiveProgress(int bytesReceived, int totalBytes, double speedMbps, double progress) {
    // Update real-time download progress
    _lastReceivedDownloadSpeed = speedMbps;
    final speedMBps = speedMbps / 8.0; // Convert Mbps to MB/s for display
    _addLog('Download progress: ${(progress * 100).toStringAsFixed(1)}% - Speed: ${speedMBps.toStringAsFixed(2)} MB/s (${speedMbps.toStringAsFixed(2)} Mbps)');
    
    // Update the current speed test result with real-time data
    if (_currentState.isSpeedTesting && _currentState.lastSpeedTest != null) {
      final updatedResult = _currentState.lastSpeedTest!.copyWith(
        downloadSpeed: speedMBps,
      );
      _updateState(_currentState.copyWith(lastSpeedTest: updatedResult));
    }
  }
  
  void _handleSpeedTestSendProgress(int bytesSent, int totalBytes, double speedMbps, double progress) {
    // Update real-time upload progress
    final speedMBps = speedMbps / 8.0; // Convert Mbps to MB/s for display
    _addLog('Upload progress: ${(progress * 100).toStringAsFixed(1)}% - Speed: ${speedMBps.toStringAsFixed(2)} MB/s (${speedMbps.toStringAsFixed(2)} Mbps)');
    
    // Update the current speed test result with real-time data
    if (_currentState.isSpeedTesting && _currentState.lastSpeedTest != null) {
      final updatedResult = _currentState.lastSpeedTest!.copyWith(
        uploadSpeed: speedMBps,
      );
      _updateState(_currentState.copyWith(lastSpeedTest: updatedResult));
    }
  }
  
  void _updateState(WiFiDirectState newState) {
    _currentState = newState;
    _stateController.add(_currentState);
  }
  
  void _addLog(String message) {
    final timestamp = DateTime.now().toString().substring(11, 19);
    final logEntry = '$timestamp: $message';
    final updatedLogs = List<String>.from(_currentState.logs)..add(logEntry);
    _updateState(_currentState.copyWith(logs: updatedLogs));
  }
  
  void _addChatMessage(String content, bool isSent, [int? receivedTimestamp]) {
    final message = ChatMessage(
      content: content,
      timestamp: receivedTimestamp != null 
          ? DateTime.fromMillisecondsSinceEpoch(receivedTimestamp)
          : DateTime.now(),
      isSent: isSent,
      senderName: isSent ? 'You' : 'Peer',
    );
    final updatedMessages = List<ChatMessage>.from(_currentState.chatMessages)..add(message);
    _updateState(_currentState.copyWith(chatMessages: updatedMessages));
  }
  
  Future<void> _initializeState() async {
    try {
      final isEnabled = await _service.isWifiP2pEnabled();
      _updateState(_currentState.copyWith(isWifiP2pEnabled: isEnabled));
      await logDeviceSettings();
    } catch (e) {
      _addLog('Failed to initialize state: $e');
    }
  }
  
  // Public methods for UI to call
  
  Future<void> discoverPeers() async {
    _updateState(_currentState.copyWith(isDiscovering: true));
    
    try {
      final result = await _service.discoverPeers();
      _addLog(result);
    } catch (e) {
      _addLog('Discovery failed: $e');
    } finally {
      _updateState(_currentState.copyWith(isDiscovering: false));
    }
  }
  
  Future<void> connectToPeer(WiFiDirectDevice device) async {
    try {
      final result = await _service.connectToPeer(device.deviceAddress);
      _addLog('Connecting to ${device.deviceName}: $result');
    } catch (e) {
      _addLog('Connection failed: $e');
    }
  }
  
  Future<void> disconnect() async {
    try {
      final result = await _service.disconnect();
      _addLog('Disconnected: $result');
      _updateState(_currentState.copyWith(isServerStarted: false));
    } catch (e) {
      _addLog('Disconnect failed: $e');
    }
  }
  
  Future<void> sendMessage(String message) async {
    if (message.trim().isEmpty) {
      _addLog('Send cancelled: empty message');
      return;
    }

    if (_currentState.connectionInfo?.isConnected != true) {
      _addLog('Send cancelled: not connected');
      return;
    }

    try {
      _addLog('Attempting to send message...');
      final result = await _service.sendData(message);
      _addLog(result);
      _addChatMessage(message, true);
    } catch (e) {
      _addLog('Send failed: $e');
    }
  }
  

  
  Future<void> logDeviceSettings() async {
    try {
      final settings = await _service.getDeviceSettings();
      _addLog('=== WiFi Direct Device Settings ===');
      _addLog('Device Name: ${settings['deviceName'] ?? 'Unknown'}');
      _addLog('WiFi Direct Supported: ${settings['wifiDirectSupported'] ?? false}');
      _addLog('WiFi P2P Enabled: ${settings['wifiP2pEnabled'] ?? false}');
      _addLog('Is Group Owner: ${settings['isGroupOwner'] ?? false}');
      _addLog('Chat Server Running: ${settings['chatServerRunning'] ?? false}');
      _addLog('Speed Test Server Running: ${settings['speedTestServerRunning'] ?? false}');
      _addLog('File Transfer Server Running: ${settings['fileTransferServerRunning'] ?? false}');
      _addLog('Discovered Devices: ${settings['discoveredDevicesCount'] ?? 0}');
      _addLog('Connected Clients: ${settings['connectedClientsCount'] ?? 0}');
      _addLog('Timestamp: ${DateTime.fromMillisecondsSinceEpoch(settings['timestamp'] ?? 0)}');
      _addLog('=====================================');
    } catch (e) {
      _addLog('Failed to get device settings: $e');
    }
  }
  
  Future<void> getDiscoveryStatus() async {
    try {
      final status = await _service.getDiscoveryStatus();
      _addLog('=== Discovery Status ===');
      
      // Check if this is Android or Windows based on available fields
      if (status.containsKey('discoveryAttempts')) {
        // Android-specific debugging
        _addLog('--- Android WiFi P2P Status ---');
        _addLog('Is Discovering: ${status['isDiscovering'] ?? false}');
        _addLog('Discovery Attempts: ${status['discoveryAttempts'] ?? 0}');
        _addLog('Last Discovery Time: ${status['lastDiscoveryTime'] != null ? DateTime.fromMillisecondsSinceEpoch(status['lastDiscoveryTime']) : 'Never'}');
        _addLog('Current Peer Count: ${status['currentPeerCount'] ?? 0}');
        _addLog('Last Peer Count: ${status['lastPeerCount'] ?? 0}');
        _addLog('WiFi Enabled: ${status['wifiEnabled'] ?? false}');
        _addLog('WiFi P2P Enabled: ${status['wifiP2pEnabled'] ?? false}');
        _addLog('Location Permission: ${status['locationPermission'] ?? false}');
        _addLog('Nearby WiFi Permission: ${status['nearbyWifiPermission'] ?? false}');
        
        // Log peer details if available
        final peers = status['peers'] as List<dynamic>? ?? [];
        if (peers.isNotEmpty) {
          _addLog('--- Current Peers ---');
          for (int i = 0; i < peers.length; i++) {
            final peer = Map<String, dynamic>.from(peers[i] as Map);
            _addLog('Peer $i: ${peer['deviceName']} (${peer['deviceAddress']}) - ${peer['status']}');
          }
        }
      } 
      _addLog('Timestamp: ${DateTime.fromMillisecondsSinceEpoch(status['timestamp'] ?? 0)}');
      _addLog('========================');
    } catch (e) {
      _addLog('Failed to get discovery status: $e');
    }
  }
  
  Future<void> stopDiscovery() async {
    try {
      _addLog('Stopping current discovery...');
      await _service.stopDiscovery();
      _addLog('Discovery stopped. Click Discover button to start new discovery.');
    } catch (e) {
      _addLog('Failed to stop discovery: $e');
    }
  }
  
  Future<void> resetWifiDirectSettings() async {
    try {
      final result = await _service.resetWifiDirectSettings();
      _addLog(result);
    } catch (e) {
      _addLog('Failed to reset WiFi Direct settings: $e');
    }
  }
  
  
  // Speed test methods
  Future<void> startSpeedTest(int testDataSizeMB) async {
    if (_currentState.connectionInfo?.isConnected != true) {
      _addLog('Speed test cancelled: not connected to peer');
      return;
    }
    
    _updateState(_currentState.copyWith(isSpeedTesting: true));
    _addLog('Starting comprehensive speed test with ${testDataSizeMB}MB data size...');
    
    // Initialize speed test result
    final initialResult = SpeedTestResult(
      downloadSpeed: 0.0,
      uploadSpeed: 0.0,
      timestamp: DateTime.now(),
      status: 'In Progress',
    );
    _updateState(_currentState.copyWith(lastSpeedTest: initialResult));
    
    try {
      await _service.setSpeedTesting(true);
      // Test download speed
      _addLog('Testing download speed...');
      final downloadSpeed = await _testDownloadSpeed(testDataSizeMB).timeout(
        const Duration(seconds: 30),
        onTimeout: () {
          _addLog('Download speed test timed out');
          return 0.0;
        },
      );
      
      // Test upload speed
      _addLog('Testing upload speed...');
      final uploadSpeed = await _testUploadSpeed(testDataSizeMB).timeout(
        const Duration(seconds: 30),
        onTimeout: () {
          _addLog('Upload speed test timed out');
          return 0.0;
        },
      );
      
      final result = SpeedTestResult(
        downloadSpeed: downloadSpeed,
        uploadSpeed: uploadSpeed,
        timestamp: DateTime.now(),
        status: 'Completed',
      );
      
      // Add to results list and update state
      final updatedResults = List<SpeedTestResult>.from(_currentState.speedTestResults)..add(result);
      _updateState(_currentState.copyWith(
        lastSpeedTest: result,
        speedTestResults: updatedResults,
      ));
      
      _addLog('=== Speed Test Completed ===');
      _addLog('Download Speed: ${downloadSpeed.toStringAsFixed(2)} MB/s');
      _addLog('Upload Speed: ${uploadSpeed.toStringAsFixed(2)} MB/s');
      _addLog('Test Duration: ${DateTime.now().difference(result.timestamp).inSeconds} seconds');
      _addLog('============================');
      
    } catch (e) {
      _addLog('Speed test failed with error: $e');
      
      // Determine the type of error for better user feedback
      String errorStatus;
      if (e.toString().contains('timeout') || e.toString().contains('Timeout')) {
        errorStatus = 'Failed: Connection timeout';
      } else if (e.toString().contains('connection') || e.toString().contains('Connection')) {
        errorStatus = 'Failed: Connection error';
      } else {
        errorStatus = 'Failed: $e';
      }
      
      final failedResult = SpeedTestResult(
        downloadSpeed: 0.0,
        uploadSpeed: 0.0,
        timestamp: DateTime.now(),
        status: errorStatus,
      );
      final updatedResults = List<SpeedTestResult>.from(_currentState.speedTestResults)..add(failedResult);
      _updateState(_currentState.copyWith(
        lastSpeedTest: failedResult,
        speedTestResults: updatedResults,
      ));
    } finally {
      try {
        await _service.setSpeedTesting(false);
      } catch (e) {
        _addLog('Warning: Failed to reset speed testing mode: $e');
      }
      _updateState(_currentState.copyWith(isSpeedTesting: false));
      _addLog('Speed test session ended.');
    }
  }
  
  
  Future<double> _testDownloadSpeed(int testDataSizeMB) async {
    _addLog('Testing download speed...');
    try {
      final sizeBytes = testDataSizeMB * 1024 * 1024;
      
      // Reset the last received download speed
      _lastReceivedDownloadSpeed = 0.0;
      
      // Create a completer to wait for the actual completion
      final completer = Completer<double>();
      
      // Set up a listener for the completion event
      late StreamSubscription subscription;
      subscription = _service.eventStream.listen((event) {
        if (event is SpeedTestDataReceivedEvent) {
          subscription.cancel();
          final speedMbps = event.speedMbps;
          final speedMBps = speedMbps / 8.0; // Convert Mbps to MB/s
          completer.complete(speedMBps);
        }
      });
      
      // Request speed test data from the peer
      await _service.requestSpeedTestData(sizeBytes);
      
      // Wait for completion with proper timeout (35 seconds to match Android timeout + buffer)
      final downloadSpeedMBps = await completer.future.timeout(
        const Duration(seconds: 35),
        onTimeout: () {
          subscription.cancel();
          throw TimeoutException('Download speed test timed out', const Duration(seconds: 35));
        },
      );
      
      _addLog('Download speed test completed: ${downloadSpeedMBps.toStringAsFixed(2)} MB/s');
      return downloadSpeedMBps;
    } catch (e) {
      _addLog('Download speed test failed: $e');
      return 0.0;
    }
  }
  
  Future<double> _testUploadSpeed(int testDataSizeMB) async {
    _addLog('Testing upload speed...');
    try {
      final sizeBytes = testDataSizeMB * 1024 * 1024;
      
      // Reset the last upload speed
      _lastUploadSpeed = 0.0;
      
      // Send speed test data and wait for completion
      final result = await _service.sendSpeedTestData(sizeBytes);
      _addLog('Upload result: $result');
      
      // Use the speed from progress events (which is more accurate)
      final speedMBps = _lastUploadSpeed / 8.0; // Convert Mbps to MB/s
      
      _addLog('Upload speed test completed: ${speedMBps.toStringAsFixed(2)} MB/s');
      return speedMBps;
    } catch (e) {
      _addLog('Upload speed test failed: $e');
      return 0.0;
    }
  }
  
  // File transfer methods
  Future<void> sendFile(String filePath, {String? fileName}) async {
    if (_currentState.connectionInfo?.isConnected != true) {
      _addLog('File send cancelled: not connected');
      return;
    }

    try {
      // Use provided file name or extract from path as fallback
      final actualFileName = fileName ?? filePath.split('/').last.split('\\').last;
      
      // Create file transfer info (file size will be determined on Android side)
      final fileTransfer = FileTransferInfo(
        fileName: actualFileName,
        fileSize: 0, // Will be updated when Android determines the actual size
        progress: 0.0,
        isUploading: true,
        isCompleted: false,
        timestamp: DateTime.now(),
        filePath: filePath,
      );
      
      // Update state with current transfer
      _updateState(_currentState.copyWith(currentFileTransfer: fileTransfer));
      _addLog('Starting file transfer: $actualFileName');
      
      // Send file using service (this will trigger progress events)
      final result = await _service.sendFileStream(filePath);
      _addLog('File transfer initiated: $result');
      
      // Note: The transfer completion will be handled by progress events
      // reaching 100% or by error handling
      
    } catch (e) {
      _addLog('File transfer failed: $e');
      
      // Mark transfer as failed
      if (_currentState.currentFileTransfer != null) {
        final failedTransfer = _currentState.currentFileTransfer!.copyWith(
          isCompleted: true,
          error: e.toString(),
        );
        
        final updatedRecent = List<FileTransferInfo>.from(_currentState.recentFileTransfers)
          ..insert(0, failedTransfer);
        
        _updateState(_currentState.copyWith(
          currentFileTransfer: null,
          recentFileTransfers: updatedRecent.take(10).toList(),
        ));
      }
    }
  }
  
  void _handleFileReceiveStarted(String fileName, int fileSize) {
    // Create a new file transfer for incoming file
    final fileTransfer = FileTransferInfo(
      fileName: fileName,
      fileSize: fileSize,
      progress: 0.0,
      isUploading: false,
      isCompleted: false,
      timestamp: DateTime.now(),
    );
    
    _updateState(_currentState.copyWith(currentFileTransfer: fileTransfer));
    _addLog('Starting file receive: $fileName (${(fileSize / 1024 / 1024).toStringAsFixed(2)} MB)');
  }
  
  void _handleFileSendStarted(String fileName, int fileSize) {
    // Update the current file transfer with the actual file size from Android
    final currentTransfer = _currentState.currentFileTransfer;
    if (currentTransfer != null && currentTransfer.fileName == fileName && currentTransfer.isUploading) {
      final updatedTransfer = currentTransfer.copyWith(fileSize: fileSize);
      _updateState(_currentState.copyWith(currentFileTransfer: updatedTransfer));
      _addLog('File send started: $fileName (${(fileSize / 1024 / 1024).toStringAsFixed(2)} MB)');
    }
  }
  
  void _handleFileTransferProgress(String fileName, double progress) {
    if (_currentState.currentFileTransfer?.fileName == fileName) {
      final updatedTransfer = _currentState.currentFileTransfer!.copyWith(
        progress: progress,
      );
      _updateState(_currentState.copyWith(currentFileTransfer: updatedTransfer));
      
      // Mark transfer as completed when progress reaches 100% for sent files
      if (progress >= 1.0 && updatedTransfer.isUploading) {
        final completedTransfer = updatedTransfer.copyWith(
          isCompleted: true,
        );
        
        _updateState(_currentState.copyWith(
          currentFileTransfer: completedTransfer,
        ));
        
        _addLog('File send completed: $fileName');
      }
    }
  }
  
  void _handleFileReceived(String fileName, String? filePath) {
    final currentTransfer = _currentState.currentFileTransfer;
    
    if (currentTransfer != null && currentTransfer.fileName == fileName) {
      // Complete the current transfer
      final completedTransfer = currentTransfer.copyWith(
        progress: 1.0,
        isCompleted: true,
        filePath: filePath,
      );
      
      // Add to recent transfers and clear current transfer
      final updatedRecent = List<FileTransferInfo>.from(_currentState.recentFileTransfers)
        ..insert(0, completedTransfer);
      
      _updateState(_currentState.copyWith(
        currentFileTransfer: null,
        recentFileTransfers: updatedRecent.take(10).toList(),
      ));
    } else {
      // Handle case where we don't have a current transfer (shouldn't happen with new code)
      final fileTransfer = FileTransferInfo(
        fileName: fileName,
        fileSize: 0,
        progress: 1.0,
        isUploading: false,
        isCompleted: true,
        timestamp: DateTime.now(),
      );
      
      final updatedRecent = List<FileTransferInfo>.from(_currentState.recentFileTransfers)
        ..insert(0, fileTransfer);
      
      _updateState(_currentState.copyWith(
        recentFileTransfers: updatedRecent.take(10).toList(),
      ));
    }
    
    _addLog('File received: $fileName');
  }
  
  /// Clear the current file transfer from the UI
  void clearCurrentTransfer() {
    if (_currentState.currentFileTransfer != null) {
      final currentTransfer = _currentState.currentFileTransfer!;
      
      // Only clear if the transfer is completed
      if (currentTransfer.isCompleted) {
        // Add to recent transfers before clearing
        final updatedRecent = List<FileTransferInfo>.from(_currentState.recentFileTransfers)
          ..insert(0, currentTransfer);
        
        _updateState(_currentState.copyWith(
          currentFileTransfer: null,
          recentFileTransfers: updatedRecent.take(10).toList(),
        ));
        _addLog('Cleared completed transfer: ${currentTransfer.fileName}');
      }
    }
  }
  
  void dispose() {
    _eventSubscription.cancel();
    _stateController.close();
    _service.dispose();
  }
}