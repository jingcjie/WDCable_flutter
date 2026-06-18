import 'dart:async';
import 'package:flutter/services.dart';
import 'models/wifi_direct_models.dart';

// Event classes for the event-driven architecture
abstract class WiFiDirectEvent {}

class PeersChangedEvent extends WiFiDirectEvent {
  final List<WiFiDirectDevice> peers;
  PeersChangedEvent(this.peers);
}

class WiFiP2pStateChangedEvent extends WiFiDirectEvent {
  final bool enabled;
  WiFiP2pStateChangedEvent(this.enabled);
}

class ConnectionChangedEvent extends WiFiDirectEvent {
  final WiFiDirectConnectionInfo connectionInfo;
  ConnectionChangedEvent(this.connectionInfo);
}

class NativeStateChangedEvent extends WiFiDirectEvent {
  final Map<String, dynamic> data;
  NativeStateChangedEvent(this.data);

  String get state => data['state']?.toString() ?? 'Unavailable';
  int get operationId => _readEventInt(data['opId']);
  bool get p2pStateKnown => data['p2pStateKnown'] == true;
  bool get p2pEnabled => data['p2pEnabled'] == true;
  bool get isDiscovering => data['isDiscovering'] == true;
  String get discoveryState => data['discoveryState']?.toString() ?? 'stopped';
  bool get isListening => data['isListening'] == true;
  String get listenState => data['listenState']?.toString() ?? 'unknown';
  bool get serviceRegistered => data['serviceRegistered'] == true;
  String get peerAddress => data['peerAddress']?.toString() ?? '';
  String get peerName => data['peerName']?.toString() ?? '';
  int get reasonCode => _readEventInt(data['reasonCode'], fallback: -1);
  String get reasonName => data['reasonName']?.toString() ?? '';
  String get callback => data['callback']?.toString() ?? '';

  String get decodedError {
    if (reasonCode < 0 || reasonName.isEmpty) return '';
    return '$reasonName ($reasonCode)';
  }
}

class DiscoveryStateChangedEvent extends NativeStateChangedEvent {
  DiscoveryStateChangedEvent(super.data);
}

class ListenStateChangedEvent extends NativeStateChangedEvent {
  ListenStateChangedEvent(super.data);
}

class ServiceStateChangedEvent extends NativeStateChangedEvent {
  ServiceStateChangedEvent(super.data);
}

class SessionStateChangedEvent extends WiFiDirectEvent {
  final String state;
  final String sessionId;
  final String role;
  final String groupOwnerAddress;
  final String disconnectReason;

  SessionStateChangedEvent({
    required this.state,
    required this.sessionId,
    required this.role,
    required this.groupOwnerAddress,
    required this.disconnectReason,
  });
}

class SessionReadyEvent extends WiFiDirectEvent {
  final String sessionId;
  final String role;
  final int protocolVersion;
  final List<String> capabilities;
  final List<String> peerCapabilities;

  SessionReadyEvent({
    required this.sessionId,
    required this.role,
    required this.protocolVersion,
    required this.capabilities,
    this.peerCapabilities = const [],
  });
}

class SessionFailedEvent extends WiFiDirectEvent {
  final String reason;
  final String message;
  final String sessionId;

  SessionFailedEvent({
    required this.reason,
    required this.message,
    required this.sessionId,
  });
}

class PeerProtocolMissingEvent extends WiFiDirectEvent {
  final String reason;
  final String message;
  final String sessionId;

  PeerProtocolMissingEvent({
    required this.reason,
    required this.message,
    required this.sessionId,
  });
}

class DisconnectReasonEvent extends WiFiDirectEvent {
  final String reason;
  final String sessionId;

  DisconnectReasonEvent({required this.reason, required this.sessionId});
}

class DataReceivedEvent extends WiFiDirectEvent {
  final String message;
  final int? timestamp;
  DataReceivedEvent(this.message, {this.timestamp});
}

class BinaryDataReceivedEvent extends WiFiDirectEvent {
  final Uint8List data;
  BinaryDataReceivedEvent(this.data);
}

class SpeedTestDataReceivedEvent extends WiFiDirectEvent {
  final int bytesReceived;
  final int durationMs;
  final double speedMbps;
  SpeedTestDataReceivedEvent(
    this.bytesReceived,
    this.durationMs,
    this.speedMbps,
  );
}

class SpeedTestReceiveProgressEvent extends WiFiDirectEvent {
  final int bytesReceived;
  final int totalBytes;
  final double speedMbps;
  final double progress;
  SpeedTestReceiveProgressEvent(
    this.bytesReceived,
    this.totalBytes,
    this.speedMbps,
    this.progress,
  );
}

class SpeedTestSendProgressEvent extends WiFiDirectEvent {
  final int bytesSent;
  final int totalBytes;
  final double speedMbps;
  final double progress;
  SpeedTestSendProgressEvent(
    this.bytesSent,
    this.totalBytes,
    this.speedMbps,
    this.progress,
  );
}

class FileReceiveProgressEvent extends WiFiDirectEvent {
  final String fileName;
  final double progress;
  FileReceiveProgressEvent(this.fileName, this.progress);
}

class FileSendProgressEvent extends WiFiDirectEvent {
  final String fileName;
  final double progress;
  FileSendProgressEvent(this.fileName, this.progress);
}

class FileReceiveStartedEvent extends WiFiDirectEvent {
  final String fileName;
  final int fileSize;
  FileReceiveStartedEvent(this.fileName, this.fileSize);
}

class FileSendStartedEvent extends WiFiDirectEvent {
  final String fileName;
  final int fileSize;
  FileSendStartedEvent(this.fileName, this.fileSize);
}

class FileReceivedEvent extends WiFiDirectEvent {
  final String fileName;
  final String? filePath;
  FileReceivedEvent(this.fileName, {this.filePath});
}

class ErrorEvent extends WiFiDirectEvent {
  final String error;
  ErrorEvent(this.error);
}

class AudioStateChangedEvent extends WiFiDirectEvent {
  final String mode;
  final String state;
  final int streamId;
  final String source;
  final String encoding;
  final bool peerReady;
  final bool isStreaming;
  final String message;

  AudioStateChangedEvent({
    required this.mode,
    required this.state,
    required this.streamId,
    required this.source,
    required this.encoding,
    required this.peerReady,
    required this.isStreaming,
    required this.message,
  });
}

class AudioStatsEvent extends WiFiDirectEvent {
  final String mode;
  final String state;
  final int streamId;
  final int bitrateBps;
  final int bufferLevelMs;
  final int framesSent;
  final int framesReceived;
  final int droppedFrames;
  final int underflowCount;
  final int latencyMs;

  AudioStatsEvent({
    required this.mode,
    required this.state,
    required this.streamId,
    required this.bitrateBps,
    required this.bufferLevelMs,
    required this.framesSent,
    required this.framesReceived,
    required this.droppedFrames,
    required this.underflowCount,
    required this.latencyMs,
  });
}

class AudioErrorEvent extends WiFiDirectEvent {
  final String code;
  final String message;
  final int streamId;

  AudioErrorEvent({
    required this.code,
    required this.message,
    required this.streamId,
  });
}

class WiFiDirectResetEvent extends WiFiDirectEvent {}

class PermissionDeniedEvent extends WiFiDirectEvent {
  final List<String> missingCapabilities;
  PermissionDeniedEvent([this.missingCapabilities = const []]);
}

class ClientConnectedEvent extends WiFiDirectEvent {
  final String message;
  ClientConnectedEvent(this.message);
}

class DebugEvent extends WiFiDirectEvent {
  final String message;
  DebugEvent(this.message);
}

class WiFiDirectService {
  static const MethodChannel _channel = MethodChannel('wifi_direct_cable');

  // Event stream controller
  final StreamController<WiFiDirectEvent> _eventController =
      StreamController<WiFiDirectEvent>.broadcast();

  // Public stream for events
  Stream<WiFiDirectEvent> get eventStream => _eventController.stream;

  WiFiDirectService() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {
    try {
      switch (call.method) {
        case 'onPeersChanged':
          final List<dynamic> peersData = call.arguments;
          final peers = peersData
              .map(
                (data) => WiFiDirectDevice.fromMap(
                  Map<String, dynamic>.from(data as Map),
                ),
              )
              .toList();
          _eventController.add(PeersChangedEvent(peers));
          break;

        case 'onWifiP2pStateChanged':
          final bool enabled = call.arguments;
          _eventController.add(WiFiP2pStateChangedEvent(enabled));
          break;

        case 'onConnectionChanged':
          final Map<String, dynamic> connectionData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          final connectionInfo = WiFiDirectConnectionInfo.fromMap(
            connectionData,
          );
          _eventController.add(ConnectionChangedEvent(connectionInfo));
          break;

        case 'onNativeStateChanged':
          _eventController.add(
            NativeStateChangedEvent(
              Map<String, dynamic>.from(call.arguments as Map),
            ),
          );
          break;

        case 'onDiscoveryStateChanged':
          _eventController.add(
            DiscoveryStateChangedEvent(
              Map<String, dynamic>.from(call.arguments as Map),
            ),
          );
          break;

        case 'onListenStateChanged':
          _eventController.add(
            ListenStateChangedEvent(
              Map<String, dynamic>.from(call.arguments as Map),
            ),
          );
          break;

        case 'onServiceStateChanged':
          _eventController.add(
            ServiceStateChangedEvent(
              Map<String, dynamic>.from(call.arguments as Map),
            ),
          );
          break;

        case 'onSessionStateChanged':
          final Map<String, dynamic> sessionData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            SessionStateChangedEvent(
              state: sessionData['state']?.toString() ?? 'Disconnected',
              sessionId: sessionData['sessionId']?.toString() ?? '',
              role: sessionData['role']?.toString() ?? '',
              groupOwnerAddress:
                  sessionData['groupOwnerAddress']?.toString() ?? '',
              disconnectReason:
                  sessionData['disconnectReason']?.toString() ?? '',
            ),
          );
          break;

        case 'onSessionReady':
          final Map<String, dynamic> sessionData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          final capabilities = sessionData['capabilities'];
          final peerCapabilities = sessionData['peerCapabilities'];
          _eventController.add(
            SessionReadyEvent(
              sessionId: sessionData['sessionId']?.toString() ?? '',
              role: sessionData['role']?.toString() ?? '',
              protocolVersion: sessionData['protocolVersion'] is int
                  ? sessionData['protocolVersion'] as int
                  : int.tryParse(
                          sessionData['protocolVersion']?.toString() ?? '',
                        ) ??
                        0,
              capabilities: capabilities is List
                  ? capabilities.map((item) => item.toString()).toList()
                  : const [],
              peerCapabilities: peerCapabilities is List
                  ? peerCapabilities.map((item) => item.toString()).toList()
                  : const [],
            ),
          );
          break;

        case 'onSessionFailed':
          final Map<String, dynamic> failureData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            SessionFailedEvent(
              reason: failureData['reason']?.toString() ?? 'session_failed',
              message: failureData['message']?.toString() ?? 'Session failed',
              sessionId: failureData['sessionId']?.toString() ?? '',
            ),
          );
          break;

        case 'onPeerProtocolMissing':
          final Map<String, dynamic> failureData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            PeerProtocolMissingEvent(
              reason:
                  failureData['reason']?.toString() ?? 'peer_protocol_missing',
              message:
                  failureData['message']?.toString() ??
                  'Peer is not running the upgraded WDCable protocol',
              sessionId: failureData['sessionId']?.toString() ?? '',
            ),
          );
          break;

        case 'onDisconnectReason':
          final Map<String, dynamic> disconnectData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            DisconnectReasonEvent(
              reason: disconnectData['reason']?.toString() ?? '',
              sessionId: disconnectData['sessionId']?.toString() ?? '',
            ),
          );
          break;

        case 'onDataReceived':
          if (call.arguments is Map) {
            // New JSON format with message and timestamp
            final Map<String, dynamic> messageData = Map<String, dynamic>.from(
              call.arguments as Map,
            );
            _eventController.add(
              DataReceivedEvent(
                messageData['message'] as String,
                timestamp: messageData['timestamp'] as int?,
              ),
            );
          } else {
            // Backward compatibility: plain string
            final String data = call.arguments as String;
            _eventController.add(DataReceivedEvent(data));
          }
          break;

        case 'onBinaryDataReceived':
          final Uint8List data = call.arguments;
          _eventController.add(BinaryDataReceivedEvent(data));
          break;

        case 'onSpeedTestDataReceived':
          final Map<String, dynamic> speedData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            SpeedTestDataReceivedEvent(
              speedData['bytesReceived'],
              speedData['durationMs'],
              speedData['speedMbps'].toDouble(),
            ),
          );
          break;

        case 'onSpeedTestReceiveProgress':
          final Map<String, dynamic> progressData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            SpeedTestReceiveProgressEvent(
              progressData['bytesReceived'],
              progressData['totalBytes'],
              progressData['speedMbps'].toDouble(),
              progressData['progress'].toDouble(),
            ),
          );
          break;

        case 'onSpeedTestSendProgress':
          final Map<String, dynamic> progressData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            SpeedTestSendProgressEvent(
              progressData['bytesSent'],
              progressData['totalBytes'],
              progressData['speedMbps'].toDouble(),
              progressData['progress'].toDouble(),
            ),
          );
          break;

        case 'onFileReceiveStarted':
          final Map<String, dynamic> fileData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            FileReceiveStartedEvent(fileData['fileName'], fileData['fileSize']),
          );
          break;

        case 'onFileSendStarted':
          final Map<String, dynamic> fileData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            FileSendStartedEvent(fileData['fileName'], fileData['fileSize']),
          );
          break;

        case 'onFileReceiveProgress':
          final Map<String, dynamic> progressData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            FileReceiveProgressEvent(
              progressData['fileName'],
              progressData['progress'].toDouble(),
            ),
          );
          break;

        case 'onFileSendProgress':
          final Map<String, dynamic> progressData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            FileSendProgressEvent(
              progressData['fileName'],
              progressData['progress'].toDouble(),
            ),
          );
          break;

        case 'onFileReceived':
          final Map<String, dynamic> fileData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            FileReceivedEvent(
              fileData['fileName'],
              filePath: fileData['filePath'],
            ),
          );
          break;

        case 'onError':
          final String error = call.arguments;
          _eventController.add(ErrorEvent(error));
          break;

        case 'onAudioStateChanged':
          final Map<String, dynamic> audioData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            AudioStateChangedEvent(
              mode: audioData['mode']?.toString() ?? 'idle',
              state: audioData['state']?.toString() ?? 'idle',
              streamId: _readInt(audioData['streamId']),
              source: audioData['source']?.toString() ?? 'microphone',
              encoding: audioData['encoding']?.toString() ?? 'opus',
              peerReady: audioData['peerReady'] == true,
              isStreaming: audioData['isStreaming'] == true,
              message: audioData['message']?.toString() ?? '',
            ),
          );
          break;

        case 'onAudioStats':
          final Map<String, dynamic> statsData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            AudioStatsEvent(
              mode: statsData['mode']?.toString() ?? 'idle',
              state: statsData['state']?.toString() ?? 'idle',
              streamId: _readInt(statsData['streamId']),
              bitrateBps: _readInt(statsData['bitrateBps']),
              bufferLevelMs: _readInt(statsData['bufferLevelMs']),
              framesSent: _readInt(statsData['framesSent']),
              framesReceived: _readInt(statsData['framesReceived']),
              droppedFrames: _readInt(statsData['droppedFrames']),
              underflowCount: _readInt(statsData['underflowCount']),
              latencyMs: _readInt(statsData['latencyMs']),
            ),
          );
          break;

        case 'onAudioError':
          final Map<String, dynamic> errorData = Map<String, dynamic>.from(
            call.arguments as Map,
          );
          _eventController.add(
            AudioErrorEvent(
              code: errorData['code']?.toString() ?? 'audio_error',
              message: errorData['message']?.toString() ?? 'Audio Link error',
              streamId: _readInt(errorData['streamId']),
            ),
          );
          break;

        case 'onWifiDirectReset':
          _eventController.add(WiFiDirectResetEvent());
          break;

        case 'onPermissionDenied':
          if (call.arguments is Map) {
            final Map<String, dynamic> permissionData =
                Map<String, dynamic>.from(call.arguments as Map);
            final capabilities = permissionData['capabilities'];
            _eventController.add(
              PermissionDeniedEvent(
                capabilities is List
                    ? capabilities.map((item) => item.toString()).toList()
                    : const [],
              ),
            );
          } else {
            _eventController.add(PermissionDeniedEvent());
          }
          break;

        case 'onClientConnected':
          final String message = call.arguments;
          _eventController.add(ClientConnectedEvent(message));
          break;

        case 'onDebug':
          final String message = call.arguments;
          _eventController.add(DebugEvent(message));
          break;
      }
    } catch (e) {
      _eventController.add(ErrorEvent('Error handling method call: $e'));
    }
  }

  // WiFi Direct operations
  Future<String> discoverPeers() async {
    try {
      final String result = await _channel.invokeMethod('discoverPeers');
      return result;
    } catch (e) {
      throw Exception('Failed to discover peers: $e');
    }
  }

  Future<String> connectToPeer(String deviceAddress) async {
    try {
      final String result = await _channel.invokeMethod('connectToPeer', {
        'deviceAddress': deviceAddress,
      });
      return result;
    } catch (e) {
      throw Exception('Failed to connect to peer: $e');
    }
  }

  Future<String> disconnect() async {
    try {
      final String result = await _channel.invokeMethod('disconnect');
      return result;
    } catch (e) {
      throw Exception('Failed to disconnect: $e');
    }
  }

  Future<String> sendData(String data) async {
    try {
      final String result = await _channel.invokeMethod('sendData', {
        'data': data,
      });
      return result;
    } catch (e) {
      throw Exception('Failed to send data: $e');
    }
  }

  Future<String> sendFileStream(String filePath) async {
    try {
      final String result = await _channel.invokeMethod('sendFileStream', {
        'filePath': filePath,
      });
      return result;
    } catch (e) {
      throw Exception('Failed to send file stream: $e');
    }
  }

  Future<void> configureTcpSettings({
    required int bufferSize,
    required int timeout,
    required bool keepAlive,
  }) async {
    try {
      await _channel.invokeMethod('configureTcpSettings', {
        'bufferSize': bufferSize,
        'timeout': timeout,
        'keepAlive': keepAlive,
      });
    } catch (e) {
      throw Exception('Failed to configure TCP settings: $e');
    }
  }

  Future<Map<String, dynamic>> getConnectionStats() async {
    try {
      final result = await _channel.invokeMethod('getConnectionStats');
      return Map<String, dynamic>.from(result as Map);
    } catch (e) {
      throw Exception('Failed to get connection stats: $e');
    }
  }

  Future<String> getDiagnosticLogs() async {
    try {
      final String result = await _channel.invokeMethod('getDiagnosticLogs');
      return result;
    } catch (e) {
      throw Exception('Failed to get diagnostic logs: $e');
    }
  }

  Future<String> clearDiagnosticLogs() async {
    try {
      final String result = await _channel.invokeMethod('clearDiagnosticLogs');
      return result;
    } catch (e) {
      throw Exception('Failed to clear diagnostic logs: $e');
    }
  }

  Future<Map<String, dynamic>> getDeviceSettings() async {
    try {
      final result = await _channel.invokeMethod('getDeviceSettings');
      return Map<String, dynamic>.from(result as Map);
    } catch (e) {
      throw Exception('Failed to get device settings: $e');
    }
  }

  Future<bool> isWifiP2pEnabled() async {
    try {
      final bool result = await _channel.invokeMethod('isWifiP2pEnabled');
      return result;
    } catch (e) {
      throw Exception('Failed to check WiFi P2P status: $e');
    }
  }

  Future<Map<String, dynamic>> getDiscoveryStatus() async {
    try {
      final result = await _channel.invokeMethod('getDiscoveryStatus');
      return Map<String, dynamic>.from(result as Map);
    } catch (e) {
      throw Exception('Failed to get discovery status: $e');
    }
  }

  Future<void> stopDiscovery() async {
    try {
      await _channel.invokeMethod('stopDiscovery');
    } catch (e) {
      throw Exception('Failed to stop discovery: $e');
    }
  }

  Future<String> resetWifiDirectSettings() async {
    try {
      final String result = await _channel.invokeMethod(
        'resetWifiDirectSettings',
      );
      return result;
    } catch (e) {
      throw Exception('Failed to reset WiFi Direct settings: $e');
    }
  }

  Future<void> setSpeedTesting(bool enabled) async {
    try {
      await _channel.invokeMethod('setSpeedTesting', {'enabled': enabled});
    } catch (e) {
      throw Exception('Failed to set speed testing mode: $e');
    }
  }

  Future<String> requestSpeedTestData(int sizeBytes) async {
    try {
      final String result = await _channel.invokeMethod(
        'requestSpeedTestData',
        {'sizeBytes': sizeBytes},
      );
      return result;
    } catch (e) {
      throw Exception('Failed to request speed test data: $e');
    }
  }

  Future<String> sendSpeedTestData(int sizeBytes) async {
    try {
      final String result = await _channel.invokeMethod('sendSpeedTestData', {
        'sizeBytes': sizeBytes,
      });
      return result;
    } catch (e) {
      throw Exception('Failed to send speed test data: $e');
    }
  }

  Future<Map<String, dynamic>> getAudioSupport() async {
    try {
      final result = await _channel.invokeMethod('getAudioSupport');
      return Map<String, dynamic>.from(result as Map);
    } catch (e) {
      throw Exception('Failed to get audio support: $e');
    }
  }

  Future<String> startAudio({
    required String mode,
    String source = 'microphone',
    String encoding = 'opus',
  }) async {
    try {
      final String result = await _channel.invokeMethod('startAudio', {
        'mode': mode,
        'source': source,
        'encoding': encoding,
      });
      return result;
    } catch (e) {
      throw Exception('Failed to start audio: $e');
    }
  }

  Future<String> stopAudio() async {
    try {
      final String result = await _channel.invokeMethod('stopAudio');
      return result;
    } catch (e) {
      throw Exception('Failed to stop audio: $e');
    }
  }

  void dispose() {
    _eventController.close();
  }

  int _readInt(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }
}

int _readEventInt(Object? value, {int fallback = 0}) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse(value?.toString() ?? '') ?? fallback;
}
