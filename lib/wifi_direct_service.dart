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
  SpeedTestDataReceivedEvent(this.bytesReceived, this.durationMs, this.speedMbps);
}

class SpeedTestReceiveProgressEvent extends WiFiDirectEvent {
  final int bytesReceived;
  final int totalBytes;
  final double speedMbps;
  final double progress;
  SpeedTestReceiveProgressEvent(this.bytesReceived, this.totalBytes, this.speedMbps, this.progress);
}

class SpeedTestSendProgressEvent extends WiFiDirectEvent {
  final int bytesSent;
  final int totalBytes;
  final double speedMbps;
  final double progress;
  SpeedTestSendProgressEvent(this.bytesSent, this.totalBytes, this.speedMbps, this.progress);
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

class WiFiDirectResetEvent extends WiFiDirectEvent {}

class PermissionDeniedEvent extends WiFiDirectEvent {}

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
  final StreamController<WiFiDirectEvent> _eventController = StreamController<WiFiDirectEvent>.broadcast();
  
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
          final peers = peersData.map((data) => WiFiDirectDevice.fromMap(Map<String, dynamic>.from(data as Map))).toList();
          _eventController.add(PeersChangedEvent(peers));
          break;
          
        case 'onWifiP2pStateChanged':
          final bool enabled = call.arguments;
          _eventController.add(WiFiP2pStateChangedEvent(enabled));
          break;
          
        case 'onConnectionChanged':
          final Map<String, dynamic> connectionData = Map<String, dynamic>.from(call.arguments as Map);
          final connectionInfo = WiFiDirectConnectionInfo.fromMap(connectionData);
          _eventController.add(ConnectionChangedEvent(connectionInfo));
          break;
          
        case 'onDataReceived':
          if (call.arguments is Map) {
            // New JSON format with message and timestamp
            final Map<String, dynamic> messageData = Map<String, dynamic>.from(call.arguments as Map);
            _eventController.add(DataReceivedEvent(
              messageData['message'] as String,
              timestamp: messageData['timestamp'] as int?,
            ));
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
          final Map<String, dynamic> speedData = Map<String, dynamic>.from(call.arguments as Map);
          _eventController.add(SpeedTestDataReceivedEvent(
            speedData['bytesReceived'],
            speedData['durationMs'],
            speedData['speedMbps'].toDouble(),
          ));
          break;
          
        case 'onSpeedTestReceiveProgress':
          final Map<String, dynamic> progressData = Map<String, dynamic>.from(call.arguments as Map);
          _eventController.add(SpeedTestReceiveProgressEvent(
            progressData['bytesReceived'],
            progressData['totalBytes'],
            progressData['speedMbps'].toDouble(),
            progressData['progress'].toDouble(),
          ));
          break;
          
        case 'onSpeedTestSendProgress':
          final Map<String, dynamic> progressData = Map<String, dynamic>.from(call.arguments as Map);
          _eventController.add(SpeedTestSendProgressEvent(
            progressData['bytesSent'],
            progressData['totalBytes'],
            progressData['speedMbps'].toDouble(),
            progressData['progress'].toDouble(),
          ));
          break;
          
        case 'onFileReceiveStarted':
          final Map<String, dynamic> fileData = Map<String, dynamic>.from(call.arguments as Map);
          _eventController.add(FileReceiveStartedEvent(
            fileData['fileName'],
            fileData['fileSize'],
          ));
          break;
          
        case 'onFileSendStarted':
          final Map<String, dynamic> fileData = Map<String, dynamic>.from(call.arguments as Map);
          _eventController.add(FileSendStartedEvent(
            fileData['fileName'],
            fileData['fileSize'],
          ));
          break;
          
        case 'onFileReceiveProgress':
          final Map<String, dynamic> progressData = Map<String, dynamic>.from(call.arguments as Map);
          _eventController.add(FileReceiveProgressEvent(
            progressData['fileName'],
            progressData['progress'].toDouble(),
          ));
          break;
          
        case 'onFileSendProgress':
          final Map<String, dynamic> progressData = Map<String, dynamic>.from(call.arguments as Map);
          _eventController.add(FileSendProgressEvent(
            progressData['fileName'],
            progressData['progress'].toDouble(),
          ));
          break;
          
        case 'onFileReceived':
          final Map<String, dynamic> fileData = Map<String, dynamic>.from(call.arguments as Map);
          _eventController.add(FileReceivedEvent(
            fileData['fileName'],
            filePath: fileData['filePath'],
          ));
          break;
          
        case 'onError':
          final String error = call.arguments;
          _eventController.add(ErrorEvent(error));
          break;
          
        case 'onWifiDirectReset':
          _eventController.add(WiFiDirectResetEvent());
          break;
          
        case 'onPermissionDenied':
          _eventController.add(PermissionDeniedEvent());
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
      final String result = await _channel.invokeMethod('connectToPeer', {'deviceAddress': deviceAddress});
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
      final String result = await _channel.invokeMethod('sendData', {'data': data});
      return result;
    } catch (e) {
      throw Exception('Failed to send data: $e');
    }
  }
  

  
  Future<String> sendFileStream(String filePath) async {
    try {
      final String result = await _channel.invokeMethod('sendFileStream', {'filePath': filePath});
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
      final String result = await _channel.invokeMethod('resetWifiDirectSettings');
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
      final String result = await _channel.invokeMethod('requestSpeedTestData', {
        'sizeBytes': sizeBytes,
      });
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
  
  void dispose() {
    _eventController.close();
  }
}