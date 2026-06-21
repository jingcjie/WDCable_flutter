import 'dart:async';
import 'dart:convert';
import '../wifi_direct_service.dart';
import '../models/wifi_direct_models.dart';
import '../services/data_manager.dart';
import '../utils/app_logger.dart';

/// Controller that manages WiFi Direct state and business logic
class WiFiDirectController {
  static const int _maxLogCount = 200;
  static const String _audioLatencyModeKey = 'audio.latencyMode';
  static const String _audioQualityModeKey = 'audio.qualityMode';

  final WiFiDirectService _service;
  late final StreamController<WiFiDirectState> _stateController;
  late final StreamSubscription _eventSubscription;

  WiFiDirectState _currentState = WiFiDirectState();
  int _sessionRevision = 0;

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

  WiFiDirectState _stateWithClearedSession() {
    return _currentState.copyWith(
      isDiscovering: false,
      isConnecting: false,
      pendingPeerAddress: null,
      isServerStarted: false,
      peers: const [],
      connectionInfo: null,
      sessionState: 'Disconnected',
      sessionId: null,
      sessionRole: null,
      sessionTransportRole: null,
      disconnectReason: null,
      lastSpeedTest: null,
      isSpeedTesting: false,
      currentFileTransfer: null,
      sessionCapabilities: const [],
      peerCapabilities: const [],
      audioMode: 'receive',
      audioState: 'idle',
      audioPeerReady: false,
      audioStreamId: null,
      audioStats: const AudioLinkStats(),
      audioLastError: null,
    );
  }

  void _clearSessionState() {
    _sessionRevision++;
    _updateState(_stateWithClearedSession());
  }

  WiFiDirectState _stateWithClearedFeatureActivity(WiFiDirectState base) {
    return base.copyWith(
      lastSpeedTest: null,
      isSpeedTesting: false,
      currentFileTransfer: null,
      audioState: 'idle',
      audioPeerReady: false,
      audioStreamId: null,
      audioStats: const AudioLinkStats(),
      audioLastError: null,
    );
  }

  void _handleEvent(WiFiDirectEvent event) {
    switch (event) {
      case PeersChangedEvent peersEvent:
        if (_currentState.isConnecting &&
            peersEvent.peers.isEmpty &&
            _currentState.peers.isNotEmpty) {
          _addLog('Peer refresh returned empty while connection is pending');
          break;
        }
        _updateState(_currentState.copyWith(peers: peersEvent.peers));
        _addLog('Peers updated: ${peersEvent.peers.length} devices found');
        break;

      case WiFiP2pStateChangedEvent stateEvent:
        _updateState(
          _currentState.copyWith(isWifiP2pEnabled: stateEvent.enabled),
        );
        final wifiP2pStatus =
            'WiFi P2P ${stateEvent.enabled ? "enabled" : "disabled"}';
        _addLog(wifiP2pStatus);
        AppLogger.info('[ANDROID WIFI] $wifiP2pStatus');
        break;

      case ConnectionChangedEvent connectionEvent:
        if (connectionEvent.connectionInfo.isConnected) {
          _updateState(
            _currentState.copyWith(
              connectionInfo: connectionEvent.connectionInfo,
              isConnecting: false,
              pendingPeerAddress: null,
              sessionState: _currentState.isSessionReady
                  ? _currentState.sessionState
                  : 'WifiDirectConnected',
            ),
          );
          final connectionStatus =
              'Wi-Fi Direct connected as ${connectionEvent.connectionInfo.isGroupOwner ? "Group Owner" : "Client"}';
          _addLog(connectionStatus);
          AppLogger.info('[ANDROID CONNECTION] $connectionStatus');
          _addLog('Waiting for WDCable session handshake');
        } else {
          if (_currentState.isConnecting) {
            _addLog('Wi-Fi Direct connection is still pending');
            break;
          }
          _addLog('Disconnected');
          AppLogger.info('[ANDROID CONNECTION] Disconnected');
          _clearSessionState();
        }
        break;

      case DiscoveryStateChangedEvent nativeEvent:
        final wasDiscovering = _currentState.isDiscovering;
        _handleNativeStateChanged(nativeEvent, logStateChange: false);
        if (nativeEvent.callback != 'replay' &&
            wasDiscovering != nativeEvent.isDiscovering) {
          _addLog(nativeEvent.isDiscovering ? 'Scan running' : 'Scan stopped');
        }
        break;

      case ListenStateChangedEvent nativeEvent:
        final wasAvailable = _currentState.isAvailableNearby;
        _handleNativeStateChanged(nativeEvent, logStateChange: false);
        if (nativeEvent.callback != 'replay' &&
            !wasAvailable &&
            _currentState.isAvailableNearby) {
          _addLog('Available nearby');
        }
        break;

      case ServiceStateChangedEvent nativeEvent:
        final wasRegistered = _currentState.isServiceRegistered;
        _handleNativeStateChanged(nativeEvent, logStateChange: false);
        if (nativeEvent.callback != 'replay' &&
            wasRegistered != nativeEvent.serviceRegistered) {
          _addLog(
            nativeEvent.serviceRegistered
                ? 'WDCable service registered'
                : 'WDCable service not registered',
          );
        }
        break;

      case NativeStateChangedEvent nativeEvent:
        _handleNativeStateChanged(nativeEvent);
        break;

      case SessionStateChangedEvent sessionEvent:
        _updateState(
          _currentState.copyWith(
            sessionState: sessionEvent.state,
            sessionId: sessionEvent.sessionId.isEmpty
                ? null
                : sessionEvent.sessionId,
            sessionRole: sessionEvent.role.isEmpty ? null : sessionEvent.role,
            sessionTransportRole: sessionEvent.transportRole.isEmpty
                ? null
                : sessionEvent.transportRole,
            disconnectReason: sessionEvent.disconnectReason.isEmpty
                ? _currentState.disconnectReason
                : sessionEvent.disconnectReason,
            isServerStarted: sessionEvent.state == 'Ready',
          ),
        );
        _addLog('Session state: ${sessionEvent.state}');
        break;

      case SessionReadyEvent sessionEvent:
        final roleLabel = sessionEvent.transportRole.isEmpty
            ? sessionEvent.role
            : '${sessionEvent.role}/${sessionEvent.transportRole}';
        _updateState(
          _currentState.copyWith(
            sessionState: 'Ready',
            sessionId: sessionEvent.sessionId,
            sessionRole: sessionEvent.role,
            sessionTransportRole: sessionEvent.transportRole.isEmpty
                ? null
                : sessionEvent.transportRole,
            disconnectReason: null,
            isConnecting: false,
            pendingPeerAddress: null,
            isServerStarted: true,
            sessionCapabilities: sessionEvent.capabilities,
            peerCapabilities: sessionEvent.peerCapabilities,
            audioLastError: null,
          ),
        );
        _addLog(
          'WDCable session ready ($roleLabel, protocol v${sessionEvent.protocolVersion})',
        );
        loadAudioSupport();
        break;

      case SessionFailedEvent failureEvent:
        _updateState(
          _stateWithClearedFeatureActivity(
            _currentState.copyWith(
              sessionState: 'Failed',
              sessionId: failureEvent.sessionId.isEmpty
                  ? _currentState.sessionId
                  : failureEvent.sessionId,
              disconnectReason: failureEvent.reason,
              isConnecting: false,
              pendingPeerAddress: null,
              isServerStarted: false,
            ),
          ),
        );
        _addLog('Session failed: ${failureEvent.message}');
        break;

      case PeerProtocolMissingEvent missingEvent:
        _updateState(
          _stateWithClearedFeatureActivity(
            _currentState.copyWith(
              sessionState: 'Failed',
              sessionId: missingEvent.sessionId.isEmpty
                  ? _currentState.sessionId
                  : missingEvent.sessionId,
              disconnectReason: missingEvent.reason,
              isConnecting: false,
              pendingPeerAddress: null,
              isServerStarted: false,
            ),
          ),
        );
        _addLog(
          'Peer is connected by Wi-Fi Direct but is not running the upgraded WDCable protocol',
        );
        break;

      case DisconnectReasonEvent disconnectEvent:
        if (disconnectEvent.reason.isNotEmpty) {
          _updateState(
            _currentState.copyWith(disconnectReason: disconnectEvent.reason),
          );
          _addLog('Disconnect reason: ${disconnectEvent.reason}');
        }
        break;

      case DataReceivedEvent dataEvent:
        _handleDataReceived(dataEvent.message, dataEvent.timestamp);
        break;

      case BinaryDataReceivedEvent binaryEvent:
        _handleBinaryDataReceived(binaryEvent.data);
        break;

      case FileReceiveStartedEvent startEvent:
        _handleFileReceiveStarted(startEvent.fileName, startEvent.fileSize);
        break;

      case FileSendStartedEvent startEvent:
        _handleFileSendStarted(startEvent.fileName, startEvent.fileSize);
        break;

      case FileReceiveProgressEvent progressEvent:
        _handleFileTransferProgress(
          progressEvent.fileName,
          progressEvent.progress,
        );
        // _addLog('File receive progress: ${progressEvent.fileName} - ${(progressEvent.progress * 100).toStringAsFixed(1)}%');
        break;

      case FileSendProgressEvent progressEvent:
        _handleFileTransferProgress(
          progressEvent.fileName,
          progressEvent.progress,
        );
        // _addLog('File send progress: ${progressEvent.fileName} - ${(progressEvent.progress * 100).toStringAsFixed(1)}%');
        break;

      case FileReceivedEvent fileEvent:
        _handleFileReceived(fileEvent.fileName, fileEvent.filePath);
        break;

      case ErrorEvent errorEvent:
        final errorMessage = 'Error: ${errorEvent.error}';
        _addLog(errorMessage);
        AppLogger.error('[ANDROID ERROR] ${errorEvent.error}');
        _updateState(_currentState.copyWith(lastNativeError: errorEvent.error));
        if (errorEvent.error.contains('Connection failed')) {
          _updateState(
            _currentState.copyWith(
              isConnecting: false,
              pendingPeerAddress: null,
            ),
          );
        }
        break;

      case DebugEvent debugEvent:
        final debugMessage = 'Debug: ${debugEvent.message}';
        _addLog(debugMessage);
        AppLogger.info('[ANDROID DEBUG] ${debugEvent.message}');
        break;

      case WiFiDirectResetEvent _:
        _clearSessionState();
        _addLog('WiFi Direct settings have been reset');
        break;

      case PermissionDeniedEvent permissionEvent:
        final missingCapabilities = permissionEvent.missingCapabilities.isEmpty
            ? 'required Wi-Fi Direct permissions'
            : permissionEvent.missingCapabilities.join(', ');
        _updateState(
          _currentState.copyWith(
            nativeWifiDirectState: 'BlockedByPermission',
            isWifiP2pEnabled: false,
            isDiscovering: false,
            isConnecting: false,
            pendingPeerAddress: null,
            lastNativeError: 'Permission denied: $missingCapabilities',
          ),
        );
        _addLog(
          'Permission denied: $missingCapabilities. Grant permissions in Android Settings and retry.',
        );
        break;

      case ClientConnectedEvent clientEvent:
        final clientMessage = 'Client connected: ${clientEvent.message}';
        _addLog(clientMessage);
        AppLogger.info('[ANDROID CLIENT] ${clientEvent.message}');
        break;

      case SpeedTestDataReceivedEvent speedEvent:
        _handleSpeedTestDataReceived(
          speedEvent.bytesReceived,
          speedEvent.durationMs,
          speedEvent.speedMbps,
        );
        break;

      case SpeedTestReceiveProgressEvent progressEvent:
        _handleSpeedTestReceiveProgress(
          progressEvent.bytesReceived,
          progressEvent.totalBytes,
          progressEvent.speedMbps,
          progressEvent.progress,
        );
        break;

      case SpeedTestSendProgressEvent progressEvent:
        _handleSpeedTestSendProgress(
          progressEvent.bytesSent,
          progressEvent.totalBytes,
          progressEvent.speedMbps,
          progressEvent.progress,
        );
        break;

      case AudioStateChangedEvent audioEvent:
        _handleAudioStateChanged(audioEvent);
        break;

      case AudioStatsEvent audioStats:
        _handleAudioStats(audioStats);
        break;

      case AudioErrorEvent audioError:
        _handleAudioError(audioError);
        break;
    }
  }

  void _handleNativeStateChanged(
    NativeStateChangedEvent event, {
    bool logStateChange = true,
  }) {
    final previousState = _currentState.nativeWifiDirectState;
    final nextState = _stateFromNativeEvent(event);
    _updateState(nextState);

    if (event.callback != 'replay' &&
        logStateChange &&
        previousState != event.state) {
      _addLog('Android Wi-Fi Direct: ${event.state}');
    }

    if (event.state == 'Error' && event.decodedError.isNotEmpty) {
      _addLog('Native Wi-Fi Direct error: ${event.decodedError}');
    }
  }

  WiFiDirectState _stateFromNativeEvent(NativeStateChangedEvent event) {
    var nextState = _currentState.copyWith(
      nativeWifiDirectState: event.state,
      operationId: event.operationId,
      isDiscovering: event.isDiscovering,
      discoveryState: event.discoveryState,
      isListening: event.isListening,
      listenState: event.listenState,
      isServiceRegistered: event.serviceRegistered,
      isWifiP2pEnabled: event.p2pStateKnown
          ? event.p2pEnabled
          : _currentState.isWifiP2pEnabled,
    );

    if (event.peerAddress.isNotEmpty) {
      nextState = nextState.copyWith(pendingPeerAddress: event.peerAddress);
    }

    switch (event.state) {
      case 'Connecting':
        nextState = nextState.copyWith(isConnecting: true);
        break;
      case 'Connected':
        nextState = nextState.copyWith(
          isConnecting: false,
          pendingPeerAddress: null,
          lastNativeError: null,
        );
        break;
      case 'Error':
        nextState = nextState.copyWith(
          isConnecting: false,
          pendingPeerAddress: null,
          lastNativeError: event.decodedError.isEmpty
              ? 'Native Wi-Fi Direct error'
              : event.decodedError,
        );
        break;
      case 'BlockedByPermission':
        nextState = nextState.copyWith(
          isConnecting: false,
          pendingPeerAddress: null,
          lastNativeError: 'Permission denied',
        );
        break;
      case 'Disconnecting':
      case 'Unavailable':
      case 'Ready':
      case 'Listening':
      case 'ServiceRegistered':
      case 'Discovering':
      case 'UserStoppedScan':
      case 'Background':
        nextState = nextState.copyWith(
          isConnecting: false,
          pendingPeerAddress: null,
        );
        break;
    }

    if (event.state == 'Connected') {
      nextState = nextState.copyWith(lastNativeError: null);
    }

    return nextState;
  }

  void _handleDataReceived(String data, int? timestamp) {
    // Extract message content from JSON if applicable
    String messageContent = data;
    if (data.startsWith('{') && data.endsWith('}')) {
      try {
        final jsonData = json.decode(data);
        if (jsonData is Map<String, dynamic> &&
            jsonData.containsKey('message')) {
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

  void _handleSpeedTestDataReceived(
    int bytesReceived,
    int durationMs,
    double speedMbps,
  ) {
    _addLog(
      'Speed test data received: $bytesReceived bytes in ${durationMs}ms = ${speedMbps.toStringAsFixed(2)} Mbps',
    );

    // Store the download speed result
    if (_currentState.isSpeedTesting) {
      final downloadSpeedMBps = speedMbps / 8.0; // Convert Mbps to MB/s

      // Create or update the speed test result
      final currentResult =
          _currentState.lastSpeedTest ??
          SpeedTestResult(
            downloadSpeed: 0.0,
            uploadSpeed: 0.0,
            timestamp: DateTime.now(),
            status: 'In Progress',
          );

      _updateState(
        _currentState.copyWith(
          lastSpeedTest: currentResult.copyWith(
            downloadSpeed: downloadSpeedMBps,
          ),
        ),
      );
    }
  }

  void _handleSpeedTestReceiveProgress(
    int bytesReceived,
    int totalBytes,
    double speedMbps,
    double progress,
  ) {
    // Update real-time download progress
    final speedMBps = speedMbps / 8.0; // Convert Mbps to MB/s for display
    _addLog(
      'Download progress: ${(progress * 100).toStringAsFixed(1)}% - Speed: ${speedMBps.toStringAsFixed(2)} MB/s (${speedMbps.toStringAsFixed(2)} Mbps)',
    );

    // Update the current speed test result with real-time data
    if (_currentState.isSpeedTesting && _currentState.lastSpeedTest != null) {
      final updatedResult = _currentState.lastSpeedTest!.copyWith(
        downloadSpeed: speedMBps,
      );
      _updateState(_currentState.copyWith(lastSpeedTest: updatedResult));
    }
  }

  void _handleSpeedTestSendProgress(
    int bytesSent,
    int totalBytes,
    double speedMbps,
    double progress,
  ) {
    // Update real-time upload progress
    final speedMBps = speedMbps / 8.0; // Convert Mbps to MB/s for display
    _addLog(
      'Upload progress: ${(progress * 100).toStringAsFixed(1)}% - Speed: ${speedMBps.toStringAsFixed(2)} MB/s (${speedMbps.toStringAsFixed(2)} Mbps)',
    );

    // Update the current speed test result with real-time data
    if (_currentState.isSpeedTesting && _currentState.lastSpeedTest != null) {
      final updatedResult = _currentState.lastSpeedTest!.copyWith(
        uploadSpeed: speedMBps,
      );
      _updateState(_currentState.copyWith(lastSpeedTest: updatedResult));
    }
  }

  void _handleAudioStateChanged(AudioStateChangedEvent event) {
    _updateState(
      _currentState.copyWith(
        audioMode: event.mode == 'idle' ? _currentState.audioMode : event.mode,
        audioLatencyMode: event.mode == 'send'
            ? event.latencyMode
            : _currentState.audioLatencyMode,
        audioQualityMode: event.mode == 'send'
            ? event.qualityMode
            : _currentState.audioQualityMode,
        audioState: event.state,
        audioSource: event.source,
        audioEncoding: event.encoding,
        audioPeerReady: event.peerReady,
        audioStreamId: event.streamId == 0 ? null : event.streamId,
        audioLastError: null,
      ),
    );
    if (event.message.isNotEmpty) {
      _addLog('Audio: ${event.message}');
    }
  }

  void _handleAudioStats(AudioStatsEvent event) {
    _updateState(
      _currentState.copyWith(
        audioState: event.state,
        audioStats: AudioLinkStats(
          latencyMode: event.latencyMode,
          qualityMode: event.qualityMode,
          bitrateBps: event.bitrateBps,
          configuredBitrateBps: event.configuredBitrateBps,
          bufferLevelMs: event.bufferLevelMs,
          framesSent: event.framesSent,
          framesReceived: event.framesReceived,
          droppedFrames: event.droppedFrames,
          packetLossCount: event.packetLossCount,
          latePacketDrops: event.latePacketDrops,
          overflowDrops: event.overflowDrops,
          duplicatePackets: event.duplicatePackets,
          reorderedPackets: event.reorderedPackets,
          underflowCount: event.underflowCount,
          plcCount: event.plcCount,
          rtpPacketsSent: event.rtpPacketsSent,
          rtpPacketsReceived: event.rtpPacketsReceived,
          rtpBytesSent: event.rtpBytesSent,
          rtpBytesReceived: event.rtpBytesReceived,
          rtcpPacketsSent: event.rtcpPacketsSent,
          rtcpPacketsReceived: event.rtcpPacketsReceived,
          rtcpFractionLost: event.rtcpFractionLost,
          rtcpJitter: event.rtcpJitter,
          rtcpPacketCount: event.rtcpPacketCount,
          rtcpOctetCount: event.rtcpOctetCount,
          roundTripMs: event.roundTripMs,
          encodeErrorCount: event.encodeErrorCount,
          decodeErrorCount: event.decodeErrorCount,
          udpSendErrorCount: event.udpSendErrorCount,
          udpReceiveErrorCount: event.udpReceiveErrorCount,
          latencyMs: event.latencyMs,
        ),
      ),
    );
  }

  void _handleAudioError(AudioErrorEvent event) {
    final message = '${event.code}: ${event.message}';
    _updateState(
      _currentState.copyWith(
        audioState: 'idle',
        audioStreamId: null,
        audioLastError: message,
      ),
    );
    _addLog('Audio error: $message');
  }

  void _updateState(WiFiDirectState newState) {
    _currentState = newState;
    _stateController.add(_currentState);
  }

  void _addLog(String message) {
    final timestamp = DateTime.now().toString().substring(11, 19);
    final logEntry = '$timestamp: $message';
    final updatedLogs = List<String>.from(_currentState.logs)..add(logEntry);
    final boundedLogs = updatedLogs.length > _maxLogCount
        ? updatedLogs.sublist(updatedLogs.length - _maxLogCount)
        : updatedLogs;
    _updateState(_currentState.copyWith(logs: boundedLogs));
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
    final updatedMessages = List<ChatMessage>.from(_currentState.chatMessages)
      ..add(message);
    _updateState(_currentState.copyWith(chatMessages: updatedMessages));
  }

  Future<void> _initializeState() async {
    try {
      await loadAudioLatencyMode();
      await loadAudioQualityMode();
      final isEnabled = await _service.isWifiP2pEnabled();
      _updateState(_currentState.copyWith(isWifiP2pEnabled: isEnabled));
      final discoveryStatus = await _service.getDiscoveryStatus();
      _updateState(
        _stateFromNativeEvent(NativeStateChangedEvent(discoveryStatus)),
      );
      await logDeviceSettings();
    } catch (e) {
      _addLog('Failed to initialize state: $e');
    }
  }

  // Public methods for UI to call

  Future<void> discoverPeers() async {
    try {
      _updateState(_currentState.copyWith(lastNativeError: null));
      final result = await _service.discoverPeers();
      _addLog(result);
    } catch (e) {
      _updateState(_currentState.copyWith(lastNativeError: e.toString()));
      _addLog('Discovery failed: $e');
    }
  }

  Future<void> connectToPeer(WiFiDirectDevice device) async {
    if (_currentState.connectionInfo?.isConnected == true ||
        device.status == 0) {
      _addLog('Already connected to ${device.deviceName}');
      return;
    }

    final isSamePendingPeer =
        _currentState.pendingPeerAddress == device.deviceAddress;
    if (_currentState.isConnecting || device.status == 1) {
      if (isSamePendingPeer || device.status == 1) {
        _addLog('Connection already pending for ${device.deviceName}');
      } else {
        _addLog(
          'Connection already pending for another peer. Ignoring ${device.deviceName}.',
        );
      }
      return;
    }

    _updateState(
      _currentState.copyWith(
        isConnecting: true,
        pendingPeerAddress: device.deviceAddress,
        lastNativeError: null,
      ),
    );

    try {
      final result = await _service.connectToPeer(device.deviceAddress);
      _addLog('Connecting to ${device.deviceName}: $result');
    } catch (e) {
      _updateState(
        _currentState.copyWith(
          isConnecting: false,
          pendingPeerAddress: null,
          lastNativeError: e.toString(),
        ),
      );
      _addLog('Connection failed: $e');
    }
  }

  Future<void> disconnect() async {
    try {
      _updateState(_currentState.copyWith(lastNativeError: null));
      final result = await _service.disconnect();
      _addLog('Disconnected: $result');
      _clearSessionState();
    } catch (e) {
      _addLog('Disconnect failed: $e');
    }
  }

  Future<void> sendMessage(String message) async {
    if (message.trim().isEmpty) {
      _addLog('Send cancelled: empty message');
      return;
    }

    if (!_currentState.isSessionReady) {
      _addLog('Send cancelled: WDCable session is not ready');
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
      final appVersion = settings['appVersion']?.toString() ?? '';
      if (appVersion.isNotEmpty && appVersion != _currentState.appVersion) {
        _updateState(_currentState.copyWith(appVersion: appVersion));
      }
      _addLog('=== WiFi Direct Device Settings ===');
      _addLog('Device Name: ${settings['deviceName'] ?? 'Unknown'}');
      _addLog('App Version: ${settings['appVersion'] ?? 'Unknown'}');
      _addLog(
        'WiFi Direct Supported: ${settings['wifiDirectSupported'] ?? false}',
      );
      _addLog('WiFi P2P Enabled: ${settings['wifiP2pEnabled'] ?? false}');
      _addLog(
        'Native State: ${settings['nativeWifiDirectState'] ?? 'Unknown'}',
      );
      _addLog('Discovery State: ${settings['discoveryState'] ?? 'unknown'}');
      _addLog('Listen State: ${settings['listenState'] ?? 'unknown'}');
      _addLog('Service Registered: ${settings['serviceRegistered'] ?? false}');
      _addLog('Operation ID: ${settings['operationId'] ?? 0}');
      _addLog('Is Group Owner: ${settings['isGroupOwner'] ?? false}');
      _addLog('Transport Role: ${settings['transportRole'] ?? ''}');
      _addLog('Chat Server Running: ${settings['chatServerRunning'] ?? false}');
      _addLog(
        'Speed Test Server Running: ${settings['speedTestServerRunning'] ?? false}',
      );
      _addLog(
        'File Transfer Server Running: ${settings['fileTransferServerRunning'] ?? false}',
      );
      _addLog('Discovered Devices: ${settings['discoveredDevicesCount'] ?? 0}');
      _addLog('Connected Clients: ${settings['connectedClientsCount'] ?? 0}');
      _addLog(
        'Timestamp: ${DateTime.fromMillisecondsSinceEpoch(settings['timestamp'] ?? 0)}',
      );
      _addLog('=====================================');
    } catch (e) {
      _addLog('Failed to get device settings: $e');
    }
  }

  Future<String> getDiagnosticLogs() async {
    try {
      final logs = await _service.getDiagnosticLogs();
      final dartState = const JsonEncoder.withIndent('  ').convert({
        'nativeWifiDirectState': _currentState.nativeWifiDirectState,
        'isWifiP2pEnabled': _currentState.isWifiP2pEnabled,
        'isDiscovering': _currentState.isDiscovering,
        'discoveryState': _currentState.discoveryState,
        'isListening': _currentState.isListening,
        'listenState': _currentState.listenState,
        'isServiceRegistered': _currentState.isServiceRegistered,
        'operationId': _currentState.operationId,
        'isConnecting': _currentState.isConnecting,
        'pendingPeerAddress': _currentState.pendingPeerAddress,
        'hasWifiDirectLink': _currentState.hasWifiDirectLink,
        'sessionState': _currentState.sessionState,
        'sessionId': _currentState.sessionId,
        'sessionRole': _currentState.sessionRole,
        'sessionTransportRole': _currentState.sessionTransportRole,
        'disconnectReason': _currentState.disconnectReason,
        'lastNativeError': _currentState.lastNativeError,
        'peersCount': _currentState.peers.length,
      });
      _addLog('Diagnostic logs exported');
      return '$logs\nDart Wi-Fi Direct State\n$dartState\n';
    } catch (e) {
      _addLog('Failed to export diagnostic logs: $e');
      rethrow;
    }
  }

  Future<void> clearDiagnosticLogs() async {
    try {
      final result = await _service.clearDiagnosticLogs();
      _addLog(result);
    } catch (e) {
      _addLog('Failed to clear diagnostic logs: $e');
    }
  }

  Future<void> stopDiscovery() async {
    try {
      _addLog('Stopping current discovery...');
      await _service.stopDiscovery();
      _addLog('Discovery stop requested');
    } catch (e) {
      _updateState(_currentState.copyWith(lastNativeError: e.toString()));
      _addLog('Failed to stop discovery: $e');
    }
  }

  Future<void> resetWifiDirectSettings() async {
    try {
      _updateState(_currentState.copyWith(lastNativeError: null));
      final result = await _service.resetWifiDirectSettings();
      _addLog(result);
      _clearSessionState();
    } catch (e) {
      _addLog('Failed to reset WiFi Direct settings: $e');
    }
  }

  // Speed test methods
  Future<void> startSpeedTest(int testDataSizeMB) async {
    if (_currentState.isSpeedTesting) {
      _addLog('Speed test already in progress');
      return;
    }

    if (!_currentState.isSessionReady) {
      _addLog('Speed test cancelled: WDCable session is not ready');
      return;
    }

    final speedTestSessionRevision = _sessionRevision;
    _updateState(_currentState.copyWith(isSpeedTesting: true));
    _addLog(
      'Starting comprehensive speed test with ${testDataSizeMB}MB data size...',
    );

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

      if (speedTestSessionRevision != _sessionRevision) {
        _addLog('Speed test result ignored after disconnect/reset');
        return;
      }

      // Add to results list and update state
      final updatedResults = List<SpeedTestResult>.from(
        _currentState.speedTestResults,
      )..add(result);
      _updateState(
        _currentState.copyWith(
          lastSpeedTest: result,
          speedTestResults: updatedResults,
        ),
      );

      _addLog('=== Speed Test Completed ===');
      _addLog('Download Speed: ${downloadSpeed.toStringAsFixed(2)} MB/s');
      _addLog('Upload Speed: ${uploadSpeed.toStringAsFixed(2)} MB/s');
      _addLog(
        'Test Duration: ${DateTime.now().difference(result.timestamp).inSeconds} seconds',
      );
      _addLog('============================');
    } catch (e) {
      _addLog('Speed test failed with error: $e');

      // Determine the type of error for better user feedback
      String errorStatus;
      if (e.toString().contains('timeout') ||
          e.toString().contains('Timeout')) {
        errorStatus = 'Failed: Connection timeout';
      } else if (e.toString().contains('connection') ||
          e.toString().contains('Connection')) {
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
      if (speedTestSessionRevision != _sessionRevision) {
        _addLog('Speed test failure ignored after disconnect/reset');
        return;
      }

      final updatedResults = List<SpeedTestResult>.from(
        _currentState.speedTestResults,
      )..add(failedResult);
      _updateState(
        _currentState.copyWith(
          lastSpeedTest: failedResult,
          speedTestResults: updatedResults,
        ),
      );
    } finally {
      try {
        await _service.setSpeedTesting(false);
      } catch (e) {
        _addLog('Warning: Failed to reset speed testing mode: $e');
      }
      if (speedTestSessionRevision == _sessionRevision) {
        _updateState(_currentState.copyWith(isSpeedTesting: false));
      }
      _addLog('Speed test session ended.');
    }
  }

  Future<double> _testDownloadSpeed(int testDataSizeMB) async {
    _addLog('Testing download speed...');
    try {
      final sizeBytes = testDataSizeMB * 1024 * 1024;

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
          throw TimeoutException(
            'Download speed test timed out',
            const Duration(seconds: 35),
          );
        },
      );

      _addLog(
        'Download speed test completed: ${downloadSpeedMBps.toStringAsFixed(2)} MB/s',
      );
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

      // Create a completer to wait for the final upload progress event
      final completer = Completer<double>();

      // Set up a listener for the final upload progress event (progress = 1.0)
      late StreamSubscription subscription;
      subscription = _service.eventStream.listen((event) {
        if (event is SpeedTestSendProgressEvent && event.progress >= 0.99) {
          subscription.cancel();
          final speedMbps = event.speedMbps;
          final speedMBps = speedMbps / 8.0; // Convert Mbps to MB/s
          completer.complete(speedMBps);
        }
      });

      // Send speed test data
      final result = await _service.sendSpeedTestData(sizeBytes);
      _addLog('Upload result: $result');

      // Wait for the final progress event with proper timeout
      final uploadSpeedMBps = await completer.future.timeout(
        const Duration(seconds: 35),
        onTimeout: () {
          subscription.cancel();
          throw TimeoutException(
            'Upload speed test timed out',
            const Duration(seconds: 35),
          );
        },
      );

      _addLog(
        'Upload speed test completed: ${uploadSpeedMBps.toStringAsFixed(2)} MB/s',
      );
      return uploadSpeedMBps;
    } catch (e) {
      _addLog('Upload speed test failed: $e');
      return 0.0;
    }
  }

  Future<void> loadAudioSupport() async {
    try {
      final support = await _service.getAudioSupport();
      _updateState(
        _currentState.copyWith(audioSupport: AudioSupportInfo.fromMap(support)),
      );
    } catch (e) {
      _addLog('Failed to load audio support: $e');
    }
  }

  Future<void> loadAudioLatencyMode() async {
    final saved = await DataManager.instance.getString(
      _audioLatencyModeKey,
      defaultValue: 'lowLatency',
    );
    final mode = saved == 'stable' ? 'stable' : 'lowLatency';
    _updateState(_currentState.copyWith(audioLatencyMode: mode));
  }

  Future<void> setAudioLatencyMode(String mode) async {
    final normalized = mode == 'stable' ? 'stable' : 'lowLatency';
    _updateState(_currentState.copyWith(audioLatencyMode: normalized));
    await DataManager.instance.setString(_audioLatencyModeKey, normalized);
  }

  Future<void> loadAudioQualityMode() async {
    final saved = await DataManager.instance.getString(
      _audioQualityModeKey,
      defaultValue: 'standard',
    );
    _updateState(
      _currentState.copyWith(
        audioQualityMode: _normalizeAudioQualityMode(saved),
      ),
    );
  }

  Future<void> setAudioQualityMode(String mode) async {
    final normalized = _normalizeAudioQualityMode(mode);
    _updateState(_currentState.copyWith(audioQualityMode: normalized));
    await DataManager.instance.setString(_audioQualityModeKey, normalized);
  }

  Future<void> startAudio({
    required String mode,
    String source = 'microphone',
    String encoding = 'opus',
    String? latencyMode,
    String? qualityMode,
  }) async {
    if (!_currentState.isSessionReady) {
      _addLog('Audio cancelled: WDCable session is not ready');
      return;
    }
    if (!_currentState.peerSupportsAudio) {
      _updateState(
        _currentState.copyWith(
          audioLastError: 'The connected peer does not support Audio Link',
        ),
      );
      _addLog('Audio cancelled: peer does not support Audio Link');
      return;
    }

    var support = _currentState.audioSupport;
    if (!support.audioLinkSupported) {
      await loadAudioSupport();
      support = _currentState.audioSupport;
    }

    if (mode == 'send' && !support.canSend) {
      _updateState(_currentState.copyWith(audioLastError: support.message));
      _addLog('Audio send cancelled: ${support.message}');
      return;
    }
    if (mode == 'receive' && !support.canReceive) {
      _updateState(_currentState.copyWith(audioLastError: support.message));
      _addLog('Audio receive cancelled: ${support.message}');
      return;
    }

    final selectedLatencyMode = mode == 'send'
        ? (latencyMode ?? _currentState.audioLatencyMode)
        : null;
    final selectedQualityMode = mode == 'send'
        ? _normalizeAudioQualityMode(
            qualityMode ?? _currentState.audioQualityMode,
          )
        : null;

    if (selectedQualityMode != null &&
        selectedQualityMode != 'standard' &&
        !_currentState.peerSupportsAudioQualitySelection) {
      const message =
          'The connected peer does not support selectable audio quality';
      _updateState(_currentState.copyWith(audioLastError: message));
      _addLog('Audio send cancelled: $message');
      return;
    }

    try {
      _updateState(
        _currentState.copyWith(
          audioMode: mode,
          audioLatencyMode:
              selectedLatencyMode ?? _currentState.audioLatencyMode,
          audioQualityMode:
              selectedQualityMode ?? _currentState.audioQualityMode,
          audioSource: source,
          audioEncoding: encoding,
          audioLastError: null,
        ),
      );
      final result = await _service.startAudio(
        mode: mode,
        source: source,
        encoding: encoding,
        latencyMode: selectedLatencyMode,
        qualityMode: selectedQualityMode,
      );
      _addLog(result);
    } catch (e) {
      _updateState(
        _currentState.copyWith(
          audioState: 'idle',
          audioLastError: e.toString(),
        ),
      );
      _addLog('Audio start failed: $e');
    }
  }

  String _normalizeAudioQualityMode(String? mode) {
    return defaultAudioQualityModes.any((item) => item.qualityMode == mode)
        ? mode!
        : 'standard';
  }

  Future<void> stopAudio() async {
    try {
      final result = await _service.stopAudio();
      _addLog(result);
    } catch (e) {
      _addLog('Audio stop failed: $e');
    }
  }

  // File transfer methods
  Future<bool> sendFile(String filePath, {String? fileName}) async {
    if (!_currentState.isSessionReady) {
      _addLog('File send cancelled: WDCable session is not ready');
      return false;
    }

    try {
      // Use provided file name or extract from path as fallback
      final actualFileName =
          fileName ?? filePath.split('/').last.split('\\').last;

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
      return true;

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

        final updatedRecent = List<FileTransferInfo>.from(
          _currentState.recentFileTransfers,
        )..insert(0, failedTransfer);

        _updateState(
          _currentState.copyWith(
            currentFileTransfer: null,
            recentFileTransfers: updatedRecent.take(10).toList(),
          ),
        );
      }
      return false;
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
    _addLog('Starting file receive: $fileName (${_formatSize(fileSize)})');
  }

  void _handleFileSendStarted(String fileName, int fileSize) {
    // Update the current file transfer with the actual file size from Android
    final currentTransfer = _currentState.currentFileTransfer;
    if (currentTransfer != null &&
        currentTransfer.fileName == fileName &&
        currentTransfer.isUploading) {
      final updatedTransfer = currentTransfer.copyWith(fileSize: fileSize);
      _updateState(
        _currentState.copyWith(currentFileTransfer: updatedTransfer),
      );
      _addLog('File send started: $fileName (${_formatSize(fileSize)})');
    }
  }

  void _handleFileTransferProgress(String fileName, double progress) {
    if (_currentState.currentFileTransfer?.fileName == fileName) {
      final updatedTransfer = _currentState.currentFileTransfer!.copyWith(
        progress: progress,
      );
      _updateState(
        _currentState.copyWith(currentFileTransfer: updatedTransfer),
      );

      // Mark transfer as completed when progress reaches 100% for both sent and received files
      if (progress >= 1.0) {
        final completedTransfer = updatedTransfer.copyWith(isCompleted: true);

        _updateState(
          _currentState.copyWith(currentFileTransfer: completedTransfer),
        );

        _addLog(
          'File ${updatedTransfer.isUploading ? "send" : "receive"} completed: $fileName',
        );
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
      final updatedRecent = List<FileTransferInfo>.from(
        _currentState.recentFileTransfers,
      )..insert(0, completedTransfer);

      _updateState(
        _currentState.copyWith(
          currentFileTransfer: null,
          recentFileTransfers: updatedRecent.take(10).toList(),
        ),
      );
    } else {
      final isDuplicateCompletion =
          filePath != null &&
          _currentState.recentFileTransfers.any(
            (transfer) =>
                !transfer.isUploading &&
                transfer.isCompleted &&
                transfer.fileName == fileName &&
                transfer.filePath == filePath,
          );
      if (isDuplicateCompletion) {
        _addLog('Ignoring duplicate file completion event: $fileName');
        return;
      }

      // Handle case where we don't have a current transfer (shouldn't happen with new code)
      final fileTransfer = FileTransferInfo(
        fileName: fileName,
        fileSize: 0,
        progress: 1.0,
        isUploading: false,
        isCompleted: true,
        timestamp: DateTime.now(),
      );

      final updatedRecent = List<FileTransferInfo>.from(
        _currentState.recentFileTransfers,
      )..insert(0, fileTransfer);

      _updateState(
        _currentState.copyWith(
          recentFileTransfers: updatedRecent.take(10).toList(),
        ),
      );
    }

    _addLog('File received: $fileName');
  }

  /// Clear the current file transfer from the UI
  void clearCurrentTransfer() {
    if (_currentState.currentFileTransfer != null) {
      final currentTransfer = _currentState.currentFileTransfer!;

      // Only clear if the transfer is completed
      if (currentTransfer.isCompleted) {
        // Simply clear the current transfer (history already added when transfer completed)
        _updateState(_currentState.copyWith(currentFileTransfer: null));
        _addLog('Cleared completed transfer: ${currentTransfer.fileName}');
      }
    }
  }

  String _formatSize(int bytes) {
    if (bytes < 0) return 'unknown size';
    return '${(bytes / 1024 / 1024).toStringAsFixed(2)} MB';
  }

  void dispose() {
    _eventSubscription.cancel();
    _stateController.close();
    _service.dispose();
  }
}
