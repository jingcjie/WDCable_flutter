import 'dart:async';

import 'package:flutter/services.dart';
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
        audioStreamId: 12,
        audioLastError: 'old error',
      );

      final cleared = state.copyWith(
        isConnecting: false,
        pendingPeerAddress: null,
        connectionInfo: null,
        lastSpeedTest: null,
        currentFileTransfer: null,
        audioStreamId: null,
        audioLastError: null,
      );

      expect(cleared.isConnecting, isFalse);
      expect(cleared.pendingPeerAddress, isNull);
      expect(cleared.connectionInfo, isNull);
      expect(cleared.lastSpeedTest, isNull);
      expect(cleared.currentFileTransfer, isNull);
      expect(cleared.audioStreamId, isNull);
      expect(cleared.audioLastError, isNull);
    });

    test('peerSupportsAudio requires all v2 capabilities', () {
      final v1Only = WiFiDirectState(
        peerCapabilities: const ['audio.link', 'audio.codec.opus'],
      );
      final v2 = WiFiDirectState(
        peerCapabilities: const [
          'audio.link',
          'audio.codec.opus',
          'audio.transport.rtp',
          'audio.rtcp',
          'audio.codec.libopus',
        ],
      );

      expect(v1Only.peerSupportsAudio, isFalse);
      expect(v2.peerSupportsAudio, isTrue);
    });

    test('AudioLinkStats parses v2 fields with defaults', () {
      final stats = AudioLinkStats.fromMap({
        'latencyMode': 'stable',
        'bitrateBps': '32000',
        'packetLossCount': 2.0,
        'latePacketDrops': '3',
        'plcCount': 4,
        'rtcpFractionLost': '5',
        'rtcpJitter': 6,
        'roundTripMs': '7',
      });

      expect(stats.latencyMode, 'stable');
      expect(stats.bitrateBps, 32000);
      expect(stats.packetLossCount, 2);
      expect(stats.latePacketDrops, 3);
      expect(stats.plcCount, 4);
      expect(stats.rtcpFractionLost, 5);
      expect(stats.rtcpJitter, 6);
      expect(stats.roundTripMs, 7);
      expect(stats.rtpPacketsSent, 0);
      expect(stats.latencyMs, -1);
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

    test('keeps peer list visible while connection is pending', () async {
      final peer = WiFiDirectDevice(
        deviceName: 'Peer',
        deviceAddress: 'aa:bb:cc:dd:ee:ff',
        status: 3,
      );

      service.emit(PeersChangedEvent([peer]));
      await pumpEventQueue();

      await controller.connectToPeer(peer);
      await pumpEventQueue();

      service.emit(PeersChangedEvent(const []));
      service.emit(
        ConnectionChangedEvent(
          WiFiDirectConnectionInfo(
            isConnected: false,
            isGroupOwner: false,
            groupOwnerAddress: null,
          ),
        ),
      );
      await pumpEventQueue();

      expect(controller.currentState.isConnecting, isTrue);
      expect(controller.currentState.pendingPeerAddress, peer.deviceAddress);
      expect(controller.currentState.peers, [peer]);
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
      expect(controller.currentState.sessionState, 'WifiDirectConnected');
      expect(controller.currentState.isServerStarted, isFalse);
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

    test('disconnect clears active transfer and audio state', () async {
      service.emit(
        ConnectionChangedEvent(
          WiFiDirectConnectionInfo(
            isConnected: true,
            isGroupOwner: false,
            groupOwnerAddress: '192.168.49.1',
          ),
        ),
      );
      service.emit(FileReceiveStartedEvent('sample.txt', 128));
      service.emit(
        AudioStateChangedEvent(
          mode: 'receive',
          state: 'streaming',
          streamId: 7,
          source: 'microphone',
          encoding: 'opus',
          peerReady: true,
          isStreaming: true,
          message: 'Audio Link streaming',
        ),
      );
      await pumpEventQueue();

      expect(controller.currentState.currentFileTransfer, isNotNull);
      expect(controller.currentState.audioState, 'streaming');

      service.emit(
        ConnectionChangedEvent(
          WiFiDirectConnectionInfo(
            isConnected: false,
            isGroupOwner: false,
            groupOwnerAddress: null,
          ),
        ),
      );
      await pumpEventQueue();

      expect(controller.currentState.connectionInfo, isNull);
      expect(controller.currentState.sessionState, 'Disconnected');
      expect(controller.currentState.currentFileTransfer, isNull);
      expect(controller.currentState.audioState, 'idle');
      expect(controller.currentState.audioStreamId, isNull);
    });

    test(
      'chat send waits for session ready after Wi-Fi Direct connects',
      () async {
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

        await controller.sendMessage('hello before ready');
        await pumpEventQueue();

        expect(service.sentMessages, isEmpty);
        expect(
          controller.currentState.logs.last,
          contains('session is not ready'),
        );

        service.emit(
          SessionReadyEvent(
            sessionId: 'session-1',
            role: 'client',
            protocolVersion: 2,
            capabilities: const ['control.chat'],
          ),
        );
        await pumpEventQueue();

        await controller.sendMessage('hello after ready');
        await pumpEventQueue();

        expect(service.sentMessages, ['hello after ready']);
        expect(controller.currentState.isSessionReady, isTrue);
        expect(
          controller.currentState.chatMessages.last.content,
          'hello after ready',
        );
      },
    );

    test('peer protocol missing is surfaced as failed session', () async {
      service.emit(
        PeerProtocolMissingEvent(
          reason: 'peer_protocol_missing',
          message: 'Timed out waiting for WDCable handshake',
          sessionId: 'session-2',
        ),
      );
      await pumpEventQueue();

      expect(controller.currentState.sessionState, 'Failed');
      expect(controller.currentState.disconnectReason, 'peer_protocol_missing');
      expect(controller.currentState.isSessionReady, isFalse);
      expect(
        controller.currentState.logs.last,
        contains('not running the upgraded WDCable protocol'),
      );
    });

    test(
      'session failure clears feature state and blocks false ready UI',
      () async {
        service.emit(
          SessionReadyEvent(
            sessionId: 'session-failure',
            role: 'client',
            protocolVersion: 2,
            capabilities: const [
              'control.chat',
              'audio.link',
              'audio.codec.opus',
              'audio.transport.rtp',
              'audio.rtcp',
              'audio.codec.libopus',
            ],
            peerCapabilities: const [
              'audio.link',
              'audio.codec.opus',
              'audio.transport.rtp',
              'audio.rtcp',
              'audio.codec.libopus',
            ],
          ),
        );
        service.emit(FileReceiveStartedEvent('active.bin', 256));
        service.emit(
          AudioStateChangedEvent(
            mode: 'send',
            state: 'streaming',
            streamId: 9,
            source: 'microphone',
            encoding: 'opus',
            peerReady: true,
            isStreaming: true,
            message: 'Audio Link streaming',
          ),
        );
        await pumpEventQueue();

        expect(controller.currentState.isSessionReady, isTrue);
        expect(controller.currentState.currentFileTransfer, isNotNull);
        expect(controller.currentState.audioState, 'streaming');

        service.emit(
          SessionFailedEvent(
            reason: 'heartbeat_timeout',
            message: 'Heartbeat timed out',
            sessionId: 'session-failure',
          ),
        );
        await pumpEventQueue();

        expect(controller.currentState.sessionState, 'Failed');
        expect(controller.currentState.isSessionReady, isFalse);
        expect(controller.currentState.currentFileTransfer, isNull);
        expect(controller.currentState.isSpeedTesting, isFalse);
        expect(controller.currentState.audioState, 'idle');
        expect(controller.currentState.audioStreamId, isNull);
        expect(controller.currentState.audioPeerReady, isFalse);
      },
    );

    test('permission denial clears pending connection state', () async {
      final peer = WiFiDirectDevice(
        deviceName: 'Peer',
        deviceAddress: 'aa:bb:cc:dd:ee:ff',
        status: 3,
      );

      await controller.connectToPeer(peer);
      await pumpEventQueue();

      expect(controller.currentState.isConnecting, isTrue);
      expect(controller.currentState.pendingPeerAddress, peer.deviceAddress);

      service.emit(PermissionDeniedEvent(const ['Nearby Wi-Fi devices']));
      await pumpEventQueue();

      expect(controller.currentState.isWifiP2pEnabled, isFalse);
      expect(controller.currentState.isDiscovering, isFalse);
      expect(controller.currentState.isConnecting, isFalse);
      expect(controller.currentState.pendingPeerAddress, isNull);
    });

    test('discovery state follows native discovery events', () async {
      await controller.discoverPeers();
      await pumpEventQueue();

      expect(service.discoverCalls, 1);
      expect(controller.currentState.isDiscovering, isFalse);

      service.emit(
        DiscoveryStateChangedEvent({
          'state': 'Discovering',
          'opId': 1,
          'p2pStateKnown': true,
          'p2pEnabled': true,
          'isDiscovering': true,
          'discoveryState': 'started',
          'isListening': false,
          'listenState': 'unknown',
          'serviceRegistered': false,
          'callback': 'test',
        }),
      );
      await pumpEventQueue();

      expect(controller.currentState.isDiscovering, isTrue);
      expect(controller.currentState.discoveryState, 'started');

      service.emit(
        DiscoveryStateChangedEvent({
          'state': 'ServiceRegistered',
          'opId': 1,
          'p2pStateKnown': true,
          'p2pEnabled': true,
          'isDiscovering': false,
          'discoveryState': 'stopped',
          'isListening': true,
          'listenState': 'started',
          'serviceRegistered': true,
          'callback': 'test',
        }),
      );
      await pumpEventQueue();

      expect(controller.currentState.isDiscovering, isFalse);
      expect(controller.currentState.discoveryState, 'stopped');
      expect(controller.currentState.isAvailableNearby, isTrue);
    });

    test(
      'degraded preserves link but blocks sends until ready again',
      () async {
        service.emit(
          ConnectionChangedEvent(
            WiFiDirectConnectionInfo(
              isConnected: true,
              isGroupOwner: false,
              groupOwnerAddress: '192.168.49.1',
            ),
          ),
        );
        service.emit(
          SessionReadyEvent(
            sessionId: 'session-recover',
            role: 'client',
            protocolVersion: 2,
            capabilities: const ['control.chat'],
          ),
        );
        await pumpEventQueue();

        expect(controller.currentState.connectionInfo?.isConnected, isTrue);
        expect(controller.currentState.isSessionReady, isTrue);

        service.emit(
          SessionStateChangedEvent(
            state: 'Degraded',
            sessionId: 'session-recover',
            role: 'client',
            groupOwnerAddress: '192.168.49.1',
            disconnectReason: 'heartbeat_timeout',
          ),
        );
        await pumpEventQueue();

        expect(controller.currentState.connectionInfo?.isConnected, isTrue);
        expect(controller.currentState.sessionState, 'Degraded');
        expect(controller.currentState.isSessionReady, isFalse);

        await controller.sendMessage('blocked while degraded');
        await pumpEventQueue();

        expect(service.sentMessages, isEmpty);
        expect(
          controller.currentState.logs.last,
          contains('session is not ready'),
        );

        service.emit(
          SessionReadyEvent(
            sessionId: 'session-recover',
            role: 'client',
            protocolVersion: 2,
            capabilities: const ['control.chat'],
          ),
        );
        await pumpEventQueue();

        await controller.sendMessage('sent after recovery');
        await pumpEventQueue();

        expect(controller.currentState.sessionState, 'Ready');
        expect(service.sentMessages, ['sent after recovery']);
      },
    );

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

    test('handles audio state stats and errors', () async {
      service.emit(
        SessionReadyEvent(
          sessionId: 'session-audio',
          role: 'client',
          protocolVersion: 2,
          capabilities: const [
            'audio.link',
            'audio.codec.opus',
            'audio.transport.rtp',
            'audio.rtcp',
            'audio.codec.libopus',
          ],
          peerCapabilities: const [
            'audio.link',
            'audio.codec.opus',
            'audio.transport.rtp',
            'audio.rtcp',
            'audio.codec.libopus',
          ],
        ),
      );
      await pumpEventQueue();

      service.emit(
        AudioStateChangedEvent(
          mode: 'receive',
          state: 'streaming',
          streamId: 44,
          source: 'microphone',
          encoding: 'opus',
          peerReady: true,
          isStreaming: true,
          message: 'Audio Link streaming',
        ),
      );
      service.emit(
        AudioStatsEvent(
          mode: 'receive',
          state: 'streaming',
          streamId: 44,
          bitrateBps: 24000,
          bufferLevelMs: 60,
          framesSent: 0,
          framesReceived: 5,
          droppedFrames: 1,
          packetLossCount: 2,
          latePacketDrops: 3,
          underflowCount: 2,
          plcCount: 4,
          rtcpFractionLost: 5,
          rtcpJitter: 6,
          roundTripMs: 7,
          latencyMs: -1,
        ),
      );
      await pumpEventQueue();

      expect(controller.currentState.audioState, 'streaming');
      expect(controller.currentState.audioStreamId, 44);
      expect(controller.currentState.audioStats.bitrateBps, 24000);
      expect(controller.currentState.audioStats.bufferLevelMs, 60);
      expect(controller.currentState.audioStats.packetLossCount, 2);
      expect(controller.currentState.audioStats.latePacketDrops, 3);
      expect(controller.currentState.audioStats.plcCount, 4);
      expect(controller.currentState.audioStats.rtcpFractionLost, 5);
      expect(controller.currentState.audioStats.rtcpJitter, 6);
      expect(controller.currentState.audioStats.roundTripMs, 7);

      service.emit(
        AudioErrorEvent(
          code: 'audio_receiver_not_ready',
          message: 'Receiver has not started',
          streamId: 44,
        ),
      );
      await pumpEventQueue();

      expect(controller.currentState.audioState, 'idle');
      expect(
        controller.currentState.audioLastError,
        contains('audio_receiver_not_ready'),
      );
    });

    test(
      'audio start is blocked until session and peer support are ready',
      () async {
        await controller.startAudio(mode: 'send');
        await pumpEventQueue();

        expect(service.audioStartCalls, 0);
        expect(
          controller.currentState.logs.last,
          contains('session is not ready'),
        );

        service.emit(
          SessionReadyEvent(
            sessionId: 'session-no-audio',
            role: 'client',
            protocolVersion: 2,
            capabilities: const [
              'audio.link',
              'audio.codec.opus',
              'audio.transport.rtp',
              'audio.rtcp',
              'audio.codec.libopus',
            ],
            peerCapabilities: const ['control.chat'],
          ),
        );
        await pumpEventQueue();

        await controller.startAudio(mode: 'send');
        await pumpEventQueue();

        expect(service.audioStartCalls, 0);
        expect(
          controller.currentState.audioLastError,
          contains('does not support'),
        );
      },
    );

    test('audio start calls native service when supported', () async {
      service.emit(
        SessionReadyEvent(
          sessionId: 'session-audio-ready',
          role: 'client',
          protocolVersion: 2,
          capabilities: const [
            'audio.link',
            'audio.codec.opus',
            'audio.transport.rtp',
            'audio.rtcp',
            'audio.codec.libopus',
          ],
          peerCapabilities: const [
            'audio.link',
            'audio.codec.opus',
            'audio.transport.rtp',
            'audio.rtcp',
            'audio.codec.libopus',
          ],
        ),
      );
      await pumpEventQueue();

      await controller.setAudioLatencyMode('stable');
      await controller.startAudio(mode: 'receive');
      await pumpEventQueue();

      expect(service.audioStartCalls, 1);
      expect(service.lastAudioMode, 'receive');
      expect(service.lastAudioLatencyMode, 'stable');
    });

    test('ignores duplicate file received completion events', () async {
      service.emit(FileReceiveStartedEvent('sample.txt', 128));
      service.emit(
        FileReceivedEvent('sample.txt', filePath: '/tmp/sample.txt'),
      );
      await pumpEventQueue();

      expect(controller.currentState.recentFileTransfers, hasLength(1));

      service.emit(
        FileReceivedEvent('sample.txt', filePath: '/tmp/sample.txt'),
      );
      await pumpEventQueue();

      expect(controller.currentState.recentFileTransfers, hasLength(1));
      expect(
        controller.currentState.recentFileTransfers.first.filePath,
        '/tmp/sample.txt',
      );
    });
  });

  group('WiFiDirectService bridge parsing', () {
    test(
      'emits typed events from platform method calls with coercion',
      () async {
        final service = WiFiDirectService();
        final events = <WiFiDirectEvent>[];
        final subscription = service.eventStream.listen(events.add);

        await _invokePlatformEvent('onAudioStats', <String, Object?>{
          'mode': 'receive',
          'state': 'streaming',
          'streamId': '42',
          'bitrateBps': 24000.0,
          'bufferLevelMs': '80',
          'framesSent': 1,
          'framesReceived': '2',
          'droppedFrames': 0,
          'packetLossCount': '4',
          'latePacketDrops': 5.0,
          'plcCount': '6',
          'rtcpFractionLost': 7,
          'rtcpJitter': '8',
          'roundTripMs': 9,
          'underflowCount': '3',
          'latencyMs': -1,
        });
        await pumpEventQueue();

        expect(events, hasLength(1));
        final stats = events.single as AudioStatsEvent;
        expect(stats.streamId, 42);
        expect(stats.bufferLevelMs, 80);
        expect(stats.framesReceived, 2);
        expect(stats.underflowCount, 3);
        expect(stats.packetLossCount, 4);
        expect(stats.latePacketDrops, 5);
        expect(stats.plcCount, 6);
        expect(stats.rtcpFractionLost, 7);
        expect(stats.rtcpJitter, 8);
        expect(stats.roundTripMs, 9);

        await subscription.cancel();
        service.dispose();
      },
    );

    test('malformed platform payload is surfaced as ErrorEvent', () async {
      final service = WiFiDirectService();
      final events = <WiFiDirectEvent>[];
      final subscription = service.eventStream.listen(events.add);

      await _invokePlatformEvent('onSessionReady', 'not a map');
      await pumpEventQueue();

      expect(events, hasLength(1));
      expect(events.single, isA<ErrorEvent>());
      expect((events.single as ErrorEvent).error, contains('Error handling'));

      await subscription.cancel();
      service.dispose();
    });
  });
}

Future<void> _invokePlatformEvent(String method, Object? arguments) async {
  final binding = TestDefaultBinaryMessengerBinding.instance;
  const codec = StandardMethodCodec();
  final completer = Completer<void>();
  await binding.defaultBinaryMessenger.handlePlatformMessage(
    'wifi_direct_cable',
    codec.encodeMethodCall(MethodCall(method, arguments)),
    (ByteData? data) {
      completer.complete();
    },
  );
  await completer.future;
}

class _FakeWiFiDirectService extends WiFiDirectService {
  final StreamController<WiFiDirectEvent> _events =
      StreamController<WiFiDirectEvent>.broadcast();

  int connectCalls = 0;
  int discoverCalls = 0;
  int stopDiscoveryCalls = 0;
  int audioStartCalls = 0;
  String? lastAudioMode;
  String? lastAudioLatencyMode;
  final List<String> sentMessages = [];

  @override
  Stream<WiFiDirectEvent> get eventStream => _events.stream;

  void emit(WiFiDirectEvent event) {
    _events.add(event);
  }

  @override
  Future<bool> isWifiP2pEnabled() async => true;

  @override
  Future<Map<String, dynamic>> getDiscoveryStatus() async {
    return {
      'state': 'Ready',
      'opId': 0,
      'p2pStateKnown': true,
      'p2pEnabled': true,
      'isDiscovering': false,
      'discoveryState': 'stopped',
      'isListening': false,
      'listenState': 'unknown',
      'serviceRegistered': false,
      'reasonCode': -1,
      'reasonName': '',
      'peersCount': 0,
    };
  }

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
  Future<String> discoverPeers() async {
    discoverCalls++;
    return 'Discovery requested';
  }

  @override
  Future<void> stopDiscovery() async {
    stopDiscoveryCalls++;
  }

  @override
  Future<String> sendData(String data) async {
    sentMessages.add(data);
    return 'Message sent';
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
  Future<Map<String, dynamic>> getAudioSupport() async {
    return {
      'audioLinkSupported': true,
      'canSend': true,
      'canReceive': true,
      'codec': 'opus',
      'codecImpl': 'libopus',
      'transport': 'rtp-udp',
      'source': 'microphone',
      'bitrateBps': 32000,
      'rtpPort': 8990,
      'rtcpPort': 8991,
      'rtpPayloadType': 111,
      'latencyModes': ['lowLatency', 'stable'],
      'message': 'Audio Link is supported',
    };
  }

  @override
  Future<String> startAudio({
    required String mode,
    String source = 'microphone',
    String encoding = 'opus',
    String latencyMode = 'lowLatency',
  }) async {
    audioStartCalls++;
    lastAudioMode = mode;
    lastAudioLatencyMode = latencyMode;
    return 'Audio started';
  }

  @override
  Future<String> stopAudio() async => 'Audio stopped';

  @override
  void dispose() {
    _events.close();
  }
}
