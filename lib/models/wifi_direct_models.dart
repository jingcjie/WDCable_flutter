/// Data models for WiFi Direct functionality
library;

class _Unset {
  const _Unset();
}

const _unset = _Unset();

class WiFiDirectDevice {
  final String deviceName;
  final String deviceAddress;
  final int status;
  final bool isWdCablePeer;

  WiFiDirectDevice({
    required this.deviceName,
    required this.deviceAddress,
    required this.status,
    this.isWdCablePeer = false,
  });

  factory WiFiDirectDevice.fromMap(Map<String, dynamic> map) {
    return WiFiDirectDevice(
      deviceName: map['deviceName'] ?? 'Unknown Device',
      deviceAddress: map['deviceAddress'] ?? '',
      status: map['status'] ?? 0,
      isWdCablePeer: map['isWdCablePeer'] == true,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'deviceName': deviceName,
      'deviceAddress': deviceAddress,
      'status': status,
      'isWdCablePeer': isWdCablePeer,
    };
  }

  String get statusText {
    switch (status) {
      case 0:
        return 'Connected';
      case 1:
        return 'Invited';
      case 2:
        return 'Failed';
      case 3:
        return 'Available';
      case 4:
        return 'Unavailable';
      default:
        return 'Unknown';
    }
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is WiFiDirectDevice && other.deviceAddress == deviceAddress;
  }

  @override
  int get hashCode => deviceAddress.hashCode;
}

class WiFiDirectConnectionInfo {
  final bool isConnected;
  final bool isGroupOwner;
  final String wifiRole;
  final String transportRole;
  final String? groupOwnerAddress;

  WiFiDirectConnectionInfo({
    required this.isConnected,
    required this.isGroupOwner,
    String? wifiRole,
    String? transportRole,
    this.groupOwnerAddress,
  }) : wifiRole = wifiRole ?? (isConnected ? _wifiRoleFor(isGroupOwner) : ''),
       transportRole =
           transportRole ??
           (isConnected ? _transportRoleFor(isGroupOwner) : '');

  factory WiFiDirectConnectionInfo.fromMap(Map<String, dynamic> map) {
    final isConnected = map['isConnected'] == true;
    final isGroupOwner = map['isGroupOwner'] == true;
    return WiFiDirectConnectionInfo(
      isConnected: isConnected,
      isGroupOwner: isGroupOwner,
      wifiRole: map['wifiRole']?.toString(),
      transportRole: map['transportRole']?.toString(),
      groupOwnerAddress: map['groupOwnerAddress'],
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'isConnected': isConnected,
      'isGroupOwner': isGroupOwner,
      'wifiRole': wifiRole,
      'transportRole': transportRole,
      'groupOwnerAddress': groupOwnerAddress,
    };
  }

  WiFiDirectConnectionInfo copyWith({
    bool? isConnected,
    bool? isGroupOwner,
    String? wifiRole,
    String? transportRole,
    String? groupOwnerAddress,
  }) {
    final nextConnected = isConnected ?? this.isConnected;
    final nextGroupOwner = isGroupOwner ?? this.isGroupOwner;
    final roleChanged = isConnected != null || isGroupOwner != null;
    return WiFiDirectConnectionInfo(
      isConnected: nextConnected,
      isGroupOwner: nextGroupOwner,
      wifiRole:
          wifiRole ??
          (nextConnected
              ? (roleChanged ? _wifiRoleFor(nextGroupOwner) : this.wifiRole)
              : ''),
      transportRole:
          transportRole ??
          (nextConnected
              ? (roleChanged
                    ? _transportRoleFor(nextGroupOwner)
                    : this.transportRole)
              : ''),
      groupOwnerAddress: groupOwnerAddress ?? this.groupOwnerAddress,
    );
  }
}

String _wifiRoleFor(bool isGroupOwner) =>
    isGroupOwner ? 'groupOwner' : 'client';

String _transportRoleFor(bool isGroupOwner) =>
    isGroupOwner ? 'connector' : 'listener';

class ChatMessage {
  final String content;
  final DateTime timestamp;
  final bool isSent;
  final String? senderName;
  final MessageType type;

  ChatMessage({
    required this.content,
    required this.timestamp,
    required this.isSent,
    this.senderName,
    this.type = MessageType.text,
  });

  Map<String, dynamic> toMap() {
    return {
      'content': content,
      'timestamp': timestamp.millisecondsSinceEpoch,
      'isSent': isSent,
      'senderName': senderName,
      'type': type.index,
    };
  }

  factory ChatMessage.fromMap(Map<String, dynamic> map) {
    return ChatMessage(
      content: map['content'] ?? '',
      timestamp: DateTime.fromMillisecondsSinceEpoch(map['timestamp'] ?? 0),
      isSent: map['isSent'] ?? false,
      senderName: map['senderName'],
      type: MessageType.values[map['type'] ?? 0],
    );
  }
}

enum MessageType { text, file, image, system }

class SpeedTestResult {
  final double downloadSpeed;
  final double uploadSpeed;
  final DateTime timestamp;
  final String status;

  SpeedTestResult({
    required this.downloadSpeed,
    required this.uploadSpeed,
    required this.timestamp,
    required this.status,
  });

  Map<String, dynamic> toMap() {
    return {
      'downloadSpeed': downloadSpeed,
      'uploadSpeed': uploadSpeed,
      'timestamp': timestamp.millisecondsSinceEpoch,
      'status': status,
    };
  }

  factory SpeedTestResult.fromMap(Map<String, dynamic> map) {
    return SpeedTestResult(
      downloadSpeed: map['downloadSpeed']?.toDouble() ?? 0.0,
      uploadSpeed: map['uploadSpeed']?.toDouble() ?? 0.0,
      timestamp: DateTime.fromMillisecondsSinceEpoch(map['timestamp'] ?? 0),
      status: map['status'] ?? 'Unknown',
    );
  }

  SpeedTestResult copyWith({
    double? downloadSpeed,
    double? uploadSpeed,
    DateTime? timestamp,
    String? status,
  }) {
    return SpeedTestResult(
      downloadSpeed: downloadSpeed ?? this.downloadSpeed,
      uploadSpeed: uploadSpeed ?? this.uploadSpeed,
      timestamp: timestamp ?? this.timestamp,
      status: status ?? this.status,
    );
  }
}

class FileTransferInfo {
  final String fileName;
  final int fileSize;
  final double progress;
  final bool isUploading;
  final bool isCompleted;
  final DateTime timestamp;
  final String? filePath;
  final String? error;

  FileTransferInfo({
    required this.fileName,
    required this.fileSize,
    required this.progress,
    required this.isUploading,
    required this.isCompleted,
    required this.timestamp,
    this.filePath,
    this.error,
  });

  Map<String, dynamic> toMap() {
    return {
      'fileName': fileName,
      'fileSize': fileSize,
      'progress': progress,
      'isUploading': isUploading,
      'isCompleted': isCompleted,
      'timestamp': timestamp.millisecondsSinceEpoch,
      'filePath': filePath,
      'error': error,
    };
  }

  factory FileTransferInfo.fromMap(Map<String, dynamic> map) {
    return FileTransferInfo(
      fileName: map['fileName'] ?? '',
      fileSize: map['fileSize'] ?? 0,
      progress: map['progress']?.toDouble() ?? 0.0,
      isUploading: map['isUploading'] ?? false,
      isCompleted: map['isCompleted'] ?? false,
      timestamp: DateTime.fromMillisecondsSinceEpoch(map['timestamp'] ?? 0),
      filePath: map['filePath'],
      error: map['error'],
    );
  }

  FileTransferInfo copyWith({
    String? fileName,
    int? fileSize,
    double? progress,
    bool? isUploading,
    bool? isCompleted,
    DateTime? timestamp,
    String? filePath,
    String? error,
  }) {
    return FileTransferInfo(
      fileName: fileName ?? this.fileName,
      fileSize: fileSize ?? this.fileSize,
      progress: progress ?? this.progress,
      isUploading: isUploading ?? this.isUploading,
      isCompleted: isCompleted ?? this.isCompleted,
      timestamp: timestamp ?? this.timestamp,
      filePath: filePath ?? this.filePath,
      error: error ?? this.error,
    );
  }
}

class AudioLinkStats {
  final String latencyMode;
  final int bitrateBps;
  final int bufferLevelMs;
  final int framesSent;
  final int framesReceived;
  final int droppedFrames;
  final int packetLossCount;
  final int latePacketDrops;
  final int overflowDrops;
  final int duplicatePackets;
  final int reorderedPackets;
  final int underflowCount;
  final int plcCount;
  final int rtpPacketsSent;
  final int rtpPacketsReceived;
  final int rtpBytesSent;
  final int rtpBytesReceived;
  final int rtcpPacketsSent;
  final int rtcpPacketsReceived;
  final int rtcpFractionLost;
  final int rtcpJitter;
  final int rtcpPacketCount;
  final int rtcpOctetCount;
  final int roundTripMs;
  final int encodeErrorCount;
  final int decodeErrorCount;
  final int udpSendErrorCount;
  final int udpReceiveErrorCount;
  final int latencyMs;

  const AudioLinkStats({
    this.latencyMode = 'lowLatency',
    this.bitrateBps = 0,
    this.bufferLevelMs = 0,
    this.framesSent = 0,
    this.framesReceived = 0,
    this.droppedFrames = 0,
    this.packetLossCount = 0,
    this.latePacketDrops = 0,
    this.overflowDrops = 0,
    this.duplicatePackets = 0,
    this.reorderedPackets = 0,
    this.underflowCount = 0,
    this.plcCount = 0,
    this.rtpPacketsSent = 0,
    this.rtpPacketsReceived = 0,
    this.rtpBytesSent = 0,
    this.rtpBytesReceived = 0,
    this.rtcpPacketsSent = 0,
    this.rtcpPacketsReceived = 0,
    this.rtcpFractionLost = 0,
    this.rtcpJitter = 0,
    this.rtcpPacketCount = 0,
    this.rtcpOctetCount = 0,
    this.roundTripMs = -1,
    this.encodeErrorCount = 0,
    this.decodeErrorCount = 0,
    this.udpSendErrorCount = 0,
    this.udpReceiveErrorCount = 0,
    this.latencyMs = -1,
  });

  factory AudioLinkStats.fromMap(Map<String, dynamic> map) {
    return AudioLinkStats(
      latencyMode: map['latencyMode']?.toString() ?? 'lowLatency',
      bitrateBps: _intFromMap(map['bitrateBps'], 0),
      bufferLevelMs: _intFromMap(map['bufferLevelMs'], 0),
      framesSent: _intFromMap(map['framesSent'], 0),
      framesReceived: _intFromMap(map['framesReceived'], 0),
      droppedFrames: _intFromMap(map['droppedFrames'], 0),
      packetLossCount: _intFromMap(map['packetLossCount'], 0),
      latePacketDrops: _intFromMap(map['latePacketDrops'], 0),
      overflowDrops: _intFromMap(map['overflowDrops'], 0),
      duplicatePackets: _intFromMap(map['duplicatePackets'], 0),
      reorderedPackets: _intFromMap(map['reorderedPackets'], 0),
      underflowCount: _intFromMap(map['underflowCount'], 0),
      plcCount: _intFromMap(map['plcCount'], 0),
      rtpPacketsSent: _intFromMap(map['rtpPacketsSent'], 0),
      rtpPacketsReceived: _intFromMap(map['rtpPacketsReceived'], 0),
      rtpBytesSent: _intFromMap(map['rtpBytesSent'], 0),
      rtpBytesReceived: _intFromMap(map['rtpBytesReceived'], 0),
      rtcpPacketsSent: _intFromMap(map['rtcpPacketsSent'], 0),
      rtcpPacketsReceived: _intFromMap(map['rtcpPacketsReceived'], 0),
      rtcpFractionLost: _intFromMap(map['rtcpFractionLost'], 0),
      rtcpJitter: _intFromMap(map['rtcpJitter'], 0),
      rtcpPacketCount: _intFromMap(map['rtcpPacketCount'], 0),
      rtcpOctetCount: _intFromMap(map['rtcpOctetCount'], 0),
      roundTripMs: _intFromMap(map['roundTripMs'], -1),
      encodeErrorCount: _intFromMap(map['encodeErrorCount'], 0),
      decodeErrorCount: _intFromMap(map['decodeErrorCount'], 0),
      udpSendErrorCount: _intFromMap(map['udpSendErrorCount'], 0),
      udpReceiveErrorCount: _intFromMap(map['udpReceiveErrorCount'], 0),
      latencyMs: _intFromMap(map['latencyMs'], -1),
    );
  }

  AudioLinkStats copyWith({
    String? latencyMode,
    int? bitrateBps,
    int? bufferLevelMs,
    int? framesSent,
    int? framesReceived,
    int? droppedFrames,
    int? packetLossCount,
    int? latePacketDrops,
    int? overflowDrops,
    int? duplicatePackets,
    int? reorderedPackets,
    int? underflowCount,
    int? plcCount,
    int? rtpPacketsSent,
    int? rtpPacketsReceived,
    int? rtpBytesSent,
    int? rtpBytesReceived,
    int? rtcpPacketsSent,
    int? rtcpPacketsReceived,
    int? rtcpFractionLost,
    int? rtcpJitter,
    int? rtcpPacketCount,
    int? rtcpOctetCount,
    int? roundTripMs,
    int? encodeErrorCount,
    int? decodeErrorCount,
    int? udpSendErrorCount,
    int? udpReceiveErrorCount,
    int? latencyMs,
  }) {
    return AudioLinkStats(
      latencyMode: latencyMode ?? this.latencyMode,
      bitrateBps: bitrateBps ?? this.bitrateBps,
      bufferLevelMs: bufferLevelMs ?? this.bufferLevelMs,
      framesSent: framesSent ?? this.framesSent,
      framesReceived: framesReceived ?? this.framesReceived,
      droppedFrames: droppedFrames ?? this.droppedFrames,
      packetLossCount: packetLossCount ?? this.packetLossCount,
      latePacketDrops: latePacketDrops ?? this.latePacketDrops,
      overflowDrops: overflowDrops ?? this.overflowDrops,
      duplicatePackets: duplicatePackets ?? this.duplicatePackets,
      reorderedPackets: reorderedPackets ?? this.reorderedPackets,
      underflowCount: underflowCount ?? this.underflowCount,
      plcCount: plcCount ?? this.plcCount,
      rtpPacketsSent: rtpPacketsSent ?? this.rtpPacketsSent,
      rtpPacketsReceived: rtpPacketsReceived ?? this.rtpPacketsReceived,
      rtpBytesSent: rtpBytesSent ?? this.rtpBytesSent,
      rtpBytesReceived: rtpBytesReceived ?? this.rtpBytesReceived,
      rtcpPacketsSent: rtcpPacketsSent ?? this.rtcpPacketsSent,
      rtcpPacketsReceived: rtcpPacketsReceived ?? this.rtcpPacketsReceived,
      rtcpFractionLost: rtcpFractionLost ?? this.rtcpFractionLost,
      rtcpJitter: rtcpJitter ?? this.rtcpJitter,
      rtcpPacketCount: rtcpPacketCount ?? this.rtcpPacketCount,
      rtcpOctetCount: rtcpOctetCount ?? this.rtcpOctetCount,
      roundTripMs: roundTripMs ?? this.roundTripMs,
      encodeErrorCount: encodeErrorCount ?? this.encodeErrorCount,
      decodeErrorCount: decodeErrorCount ?? this.decodeErrorCount,
      udpSendErrorCount: udpSendErrorCount ?? this.udpSendErrorCount,
      udpReceiveErrorCount: udpReceiveErrorCount ?? this.udpReceiveErrorCount,
      latencyMs: latencyMs ?? this.latencyMs,
    );
  }
}

class AudioSupportInfo {
  final bool audioLinkSupported;
  final bool canSend;
  final bool canReceive;
  final String codec;
  final String codecImpl;
  final String transport;
  final String source;
  final int sampleRate;
  final int channels;
  final int frameDurationMs;
  final int bitrateBps;
  final int rtpPort;
  final int rtcpPort;
  final int rtpPayloadType;
  final List<String> latencyModes;
  final int requiresApiForSend;
  final int androidApi;
  final String libopusVersion;
  final String message;

  const AudioSupportInfo({
    this.audioLinkSupported = false,
    this.canSend = false,
    this.canReceive = false,
    this.codec = 'opus',
    this.codecImpl = 'libopus',
    this.transport = 'rtp-udp',
    this.source = 'microphone',
    this.sampleRate = 48000,
    this.channels = 1,
    this.frameDurationMs = 20,
    this.bitrateBps = 32000,
    this.rtpPort = 8990,
    this.rtcpPort = 8991,
    this.rtpPayloadType = 111,
    this.latencyModes = const ['lowLatency', 'stable'],
    this.requiresApiForSend = 23,
    this.androidApi = 0,
    this.libopusVersion = '',
    this.message = '',
  });

  factory AudioSupportInfo.fromMap(Map<String, dynamic> map) {
    final latencyModes = map['latencyModes'];
    return AudioSupportInfo(
      audioLinkSupported: map['audioLinkSupported'] == true,
      canSend: map['canSend'] == true,
      canReceive: map['canReceive'] == true,
      codec: map['codec']?.toString() ?? 'opus',
      codecImpl: map['codecImpl']?.toString() ?? 'libopus',
      transport: map['transport']?.toString() ?? 'rtp-udp',
      source: map['source']?.toString() ?? 'microphone',
      sampleRate: _intFromMap(map['sampleRate'], 48000),
      channels: _intFromMap(map['channels'], 1),
      frameDurationMs: _intFromMap(map['frameDurationMs'], 20),
      bitrateBps: _intFromMap(map['bitrateBps'], 32000),
      rtpPort: _intFromMap(map['rtpPort'], 8990),
      rtcpPort: _intFromMap(map['rtcpPort'], 8991),
      rtpPayloadType: _intFromMap(map['rtpPayloadType'], 111),
      latencyModes: latencyModes is List
          ? latencyModes.map((item) => item.toString()).toList()
          : const ['lowLatency', 'stable'],
      requiresApiForSend: _intFromMap(map['requiresApiForSend'], 23),
      androidApi: _intFromMap(map['androidApi'], 0),
      libopusVersion: map['libopusVersion']?.toString() ?? '',
      message: map['message']?.toString() ?? '',
    );
  }
}

int _intFromMap(Object? value, int fallback) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse(value?.toString() ?? '') ?? fallback;
}

class WiFiDirectState {
  final bool isWifiP2pEnabled;
  final String nativeWifiDirectState;
  final bool isDiscovering;
  final String discoveryState;
  final bool isListening;
  final String listenState;
  final bool isServiceRegistered;
  final int operationId;
  final String? lastNativeError;
  final bool isConnecting;
  final String? pendingPeerAddress;
  final bool isServerStarted;
  final List<WiFiDirectDevice> peers;
  final WiFiDirectConnectionInfo? connectionInfo;
  final String sessionState;
  final String? sessionId;
  final String? sessionRole;
  final String? sessionTransportRole;
  final String? disconnectReason;
  final List<String> logs;
  final List<String> sessionCapabilities;
  final List<String> peerCapabilities;
  final List<ChatMessage> chatMessages;
  final SpeedTestResult? lastSpeedTest;
  final bool isSpeedTesting;
  final List<SpeedTestResult> speedTestResults;
  final FileTransferInfo? currentFileTransfer;
  final List<FileTransferInfo> recentFileTransfers;
  final AudioSupportInfo audioSupport;
  final String audioMode;
  final String audioLatencyMode;
  final String audioSource;
  final String audioEncoding;
  final String audioState;
  final bool audioPeerReady;
  final int? audioStreamId;
  final AudioLinkStats audioStats;
  final String? audioLastError;

  WiFiDirectState({
    this.isWifiP2pEnabled = false,
    this.nativeWifiDirectState = 'Unavailable',
    this.isDiscovering = false,
    this.discoveryState = 'stopped',
    this.isListening = false,
    this.listenState = 'unknown',
    this.isServiceRegistered = false,
    this.operationId = 0,
    this.lastNativeError,
    this.isConnecting = false,
    this.pendingPeerAddress,
    this.isServerStarted = false,
    this.peers = const [],
    this.connectionInfo,
    this.sessionState = 'Disconnected',
    this.sessionId,
    this.sessionRole,
    this.sessionTransportRole,
    this.disconnectReason,
    this.logs = const [],
    this.sessionCapabilities = const [],
    this.peerCapabilities = const [],
    this.chatMessages = const [],
    this.lastSpeedTest,
    this.isSpeedTesting = false,
    this.speedTestResults = const [],
    this.currentFileTransfer,
    this.recentFileTransfers = const [],
    this.audioSupport = const AudioSupportInfo(),
    this.audioMode = 'receive',
    this.audioLatencyMode = 'lowLatency',
    this.audioSource = 'microphone',
    this.audioEncoding = 'opus',
    this.audioState = 'idle',
    this.audioPeerReady = false,
    this.audioStreamId,
    this.audioStats = const AudioLinkStats(),
    this.audioLastError,
  });

  WiFiDirectState copyWith({
    bool? isWifiP2pEnabled,
    String? nativeWifiDirectState,
    bool? isDiscovering,
    String? discoveryState,
    bool? isListening,
    String? listenState,
    bool? isServiceRegistered,
    int? operationId,
    Object? lastNativeError = _unset,
    bool? isConnecting,
    Object? pendingPeerAddress = _unset,
    bool? isServerStarted,
    List<WiFiDirectDevice>? peers,
    Object? connectionInfo = _unset,
    String? sessionState,
    Object? sessionId = _unset,
    Object? sessionRole = _unset,
    Object? sessionTransportRole = _unset,
    Object? disconnectReason = _unset,
    List<String>? logs,
    List<String>? sessionCapabilities,
    List<String>? peerCapabilities,
    List<ChatMessage>? chatMessages,
    Object? lastSpeedTest = _unset,
    bool? isSpeedTesting,
    List<SpeedTestResult>? speedTestResults,
    Object? currentFileTransfer = _unset,
    List<FileTransferInfo>? recentFileTransfers,
    AudioSupportInfo? audioSupport,
    String? audioMode,
    String? audioLatencyMode,
    String? audioSource,
    String? audioEncoding,
    String? audioState,
    bool? audioPeerReady,
    Object? audioStreamId = _unset,
    AudioLinkStats? audioStats,
    Object? audioLastError = _unset,
  }) {
    return WiFiDirectState(
      isWifiP2pEnabled: isWifiP2pEnabled ?? this.isWifiP2pEnabled,
      nativeWifiDirectState:
          nativeWifiDirectState ?? this.nativeWifiDirectState,
      isDiscovering: isDiscovering ?? this.isDiscovering,
      discoveryState: discoveryState ?? this.discoveryState,
      isListening: isListening ?? this.isListening,
      listenState: listenState ?? this.listenState,
      isServiceRegistered: isServiceRegistered ?? this.isServiceRegistered,
      operationId: operationId ?? this.operationId,
      lastNativeError: identical(lastNativeError, _unset)
          ? this.lastNativeError
          : lastNativeError as String?,
      isConnecting: isConnecting ?? this.isConnecting,
      pendingPeerAddress: identical(pendingPeerAddress, _unset)
          ? this.pendingPeerAddress
          : pendingPeerAddress as String?,
      isServerStarted: isServerStarted ?? this.isServerStarted,
      peers: peers ?? this.peers,
      connectionInfo: identical(connectionInfo, _unset)
          ? this.connectionInfo
          : connectionInfo as WiFiDirectConnectionInfo?,
      sessionState: sessionState ?? this.sessionState,
      sessionId: identical(sessionId, _unset)
          ? this.sessionId
          : sessionId as String?,
      sessionRole: identical(sessionRole, _unset)
          ? this.sessionRole
          : sessionRole as String?,
      sessionTransportRole: identical(sessionTransportRole, _unset)
          ? this.sessionTransportRole
          : sessionTransportRole as String?,
      disconnectReason: identical(disconnectReason, _unset)
          ? this.disconnectReason
          : disconnectReason as String?,
      logs: logs ?? this.logs,
      sessionCapabilities: sessionCapabilities ?? this.sessionCapabilities,
      peerCapabilities: peerCapabilities ?? this.peerCapabilities,
      chatMessages: chatMessages ?? this.chatMessages,
      lastSpeedTest: identical(lastSpeedTest, _unset)
          ? this.lastSpeedTest
          : lastSpeedTest as SpeedTestResult?,
      isSpeedTesting: isSpeedTesting ?? this.isSpeedTesting,
      speedTestResults: speedTestResults ?? this.speedTestResults,
      currentFileTransfer: identical(currentFileTransfer, _unset)
          ? this.currentFileTransfer
          : currentFileTransfer as FileTransferInfo?,
      recentFileTransfers: recentFileTransfers ?? this.recentFileTransfers,
      audioSupport: audioSupport ?? this.audioSupport,
      audioMode: audioMode ?? this.audioMode,
      audioLatencyMode: audioLatencyMode ?? this.audioLatencyMode,
      audioSource: audioSource ?? this.audioSource,
      audioEncoding: audioEncoding ?? this.audioEncoding,
      audioState: audioState ?? this.audioState,
      audioPeerReady: audioPeerReady ?? this.audioPeerReady,
      audioStreamId: identical(audioStreamId, _unset)
          ? this.audioStreamId
          : audioStreamId as int?,
      audioStats: audioStats ?? this.audioStats,
      audioLastError: identical(audioLastError, _unset)
          ? this.audioLastError
          : audioLastError as String?,
    );
  }

  bool get hasWifiDirectLink => connectionInfo?.isConnected == true;

  bool get isAvailableNearby => isListening || isServiceRegistered;

  bool get isSessionReady => sessionState == 'Ready';

  bool get peerSupportsAudio =>
      peerCapabilities.contains('audio.link') &&
      peerCapabilities.contains('audio.codec.opus') &&
      peerCapabilities.contains('audio.transport.rtp') &&
      peerCapabilities.contains('audio.rtcp') &&
      peerCapabilities.contains('audio.codec.libopus');

  bool get isAudioActive => audioState != 'idle';

  bool get isAudioStreaming => audioState == 'streaming';
}
