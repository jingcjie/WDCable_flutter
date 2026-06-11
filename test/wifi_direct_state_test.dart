import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wifi_direct_cable/controllers/wifi_direct_controller.dart';
import 'package:wifi_direct_cable/models/wifi_direct_models.dart';
import 'package:wifi_direct_cable/wifi_direct_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('WiFiDirectState', () {
    test('copyWith can clear nullable fields', () {
      final state = WiFiDirectState(
        isConnecting: true,
        pendingPeerAddress: 'peer-address',
        connectionInfo: WiFiDirectConnectionInfo(
          isConnected: true,
          isGroupOwner: false,
          groupOwnerAddress: '192.168.49.1',
        ),
        lastSpeedTest: SpeedTestResult(
          downloadSpeed: 1.0,
          uploadSpeed: 2.0,
          timestamp: DateTime(2026),
          status: 'Completed',
        ),
        currentFileTransfer: FileTransferInfo(
          fileName: 'sample.txt',
          fileSize: 10,
          progress: 0.5,
          isUploading: false,
          isCompleted: false,
          timestamp: DateTime(2026),
        ),
      );

      final cleared = state.copyWith(
        isConnecting: false,
        pendingPeerAddress: null,
        connectionInfo: null,
        lastSpeedTest: null,
        currentFileTransfer: null,
      );

      expect(cleared.isConnecting, isFalse);
      expect(cleared.pendingPeerAddress, isNull);
      expect(cleared.connectionInfo, isNull);
      expect(cleared.lastSpeedTest, isNull);
      expect(cleared.currentFileTransfer, isNull);
    });
  });

  group('WiFiDirectController', () {
    late _FakeWiFiDirectService service;
    late WiFiDirectController controller;

    setUp(() async {
      service = _FakeWiFiDirectService();
      controller = WiFiDirectController(service);
      await pumpEventQueue();
    });

    tearDown(() {
      controller.dispose();
    });

    test(
      'moves received file transfer from current to recent when complete',
      () async {
        service.emit(FileReceiveStartedEvent('sample.txt', 128));
        await pumpEventQueue();

        expect(
          controller.currentState.currentFileTransfer?.fileName,
          'sample.txt',
        );
        expect(controller.currentState.currentFileTransfer?.progress, 0.0);

        service.emit(FileReceiveProgressEvent('sample.txt', 0.75));
        await pumpEventQueue();

        expect(controller.currentState.currentFileTransfer?.progress, 0.75);

        service.emit(
          FileReceivedEvent('sample.txt', filePath: '/tmp/sample.txt'),
        );
        await pumpEventQueue();

        expect(controller.currentState.currentFileTransfer, isNull);
        expect(controller.currentState.recentFileTransfers, hasLength(1));
        expect(
          controller.currentState.recentFileTransfers.first.fileName,
          'sample.txt',
        );
        expect(
          controller.currentState.recentFileTransfers.first.filePath,
          '/tmp/sample.txt',
        );
      },
    );

    test('guards duplicate connect calls while pending', () async {
      final peer = WiFiDirectDevice(
        deviceName: 'Peer',
        deviceAddress: 'aa:bb:cc:dd:ee:ff',
        status: 3,
      );

      await controller.connectToPeer(peer);
      await pumpEventQueue();

      expect(service.connectCalls, 1);
      expect(controller.currentState.isConnecting, isTrue);
      expect(controller.currentState.pendingPeerAddress, peer.deviceAddress);

      await controller.connectToPeer(peer);
      await pumpEventQueue();

      expect(service.connectCalls, 1);
      expect(controller.currentState.logs.last, contains('already pending'));

      service.emit(
        ConnectionChangedEvent(
          WiFiDirectConnectionInfo(
            isConnected: true,
            isGroupOwner: false,
            groupOwnerAddress: '192.168.49.1',
          ),
        ),
      );
      await pumpEventQueue();

      expect(controller.currentState.isConnecting, isFalse);
      expect(controller.currentState.pendingPeerAddress, isNull);
    });

    test('reset clears live connection and transfer state', () async {
      final peer = WiFiDirectDevice(
        deviceName: 'Peer',
        deviceAddress: 'aa:bb:cc:dd:ee:ff',
        status: 3,
      );

      service.emit(PeersChangedEvent([peer]));
      service.emit(
        ConnectionChangedEvent(
          WiFiDirectConnectionInfo(
            isConnected: true,
            isGroupOwner: true,
            groupOwnerAddress: '192.168.49.1',
          ),
        ),
      );
      service.emit(FileReceiveStartedEvent('sample.txt', 128));
      await pumpEventQueue();

      expect(controller.currentState.peers, isNotEmpty);
      expect(controller.currentState.connectionInfo?.isConnected, isTrue);
      expect(controller.currentState.isServerStarted, isTrue);
      expect(controller.currentState.currentFileTransfer, isNotNull);

      await controller.resetWifiDirectSettings();
      await pumpEventQueue();

      expect(controller.currentState.peers, isEmpty);
      expect(controller.currentState.connectionInfo, isNull);
      expect(controller.currentState.isServerStarted, isFalse);
      expect(controller.currentState.isConnecting, isFalse);
      expect(controller.currentState.pendingPeerAddress, isNull);
      expect(controller.currentState.isSpeedTesting, isFalse);
      expect(controller.currentState.lastSpeedTest, isNull);
      expect(controller.currentState.currentFileTransfer, isNull);
    });

    test('bounds in-memory logs', () async {
      for (var i = 0; i < 250; i++) {
        service.emit(DebugEvent('entry $i'));
      }
      await pumpEventQueue(times: 260);

      expect(controller.currentState.logs.length, lessThanOrEqualTo(200));
      expect(controller.currentState.logs.last, contains('entry 249'));
      expect(
        controller.currentState.logs.any((log) => log.contains('entry 0')),
        isFalse,
      );
    });
  });
}

class _FakeWiFiDirectService extends WiFiDirectService {
  final StreamController<WiFiDirectEvent> _events =
      StreamController<WiFiDirectEvent>.broadcast();

  int connectCalls = 0;

  @override
  Stream<WiFiDirectEvent> get eventStream => _events.stream;

  void emit(WiFiDirectEvent event) {
    _events.add(event);
  }

  @override
  Future<bool> isWifiP2pEnabled() async => true;

  @override
  Future<Map<String, dynamic>> getDeviceSettings() async {
    return {
      'deviceName': 'Test Device',
      'wifiDirectSupported': true,
      'wifiP2pEnabled': true,
      'isGroupOwner': false,
      'chatServerRunning': false,
      'speedTestServerRunning': false,
      'fileTransferServerRunning': false,
      'discoveredDevicesCount': 0,
      'connectedClientsCount': 0,
      'timestamp': DateTime(2026).millisecondsSinceEpoch,
    };
  }

  @override
  Future<String> connectToPeer(String deviceAddress) async {
    connectCalls++;
    return 'Connection initiated';
  }

  @override
  Future<String> disconnect() async => 'Disconnected';

  @override
  Future<String> resetWifiDirectSettings() async {
    emit(WiFiDirectResetEvent());
    return 'WiFi Direct settings reset';
  }

  @override
  Future<void> setSpeedTesting(bool enabled) async {}

  @override
  void dispose() {
    _events.close();
  }
}
