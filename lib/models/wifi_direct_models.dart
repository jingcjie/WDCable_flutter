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

  WiFiDirectDevice({
    required this.deviceName,
    required this.deviceAddress,
    required this.status,
  });

  factory WiFiDirectDevice.fromMap(Map<String, dynamic> map) {
    return WiFiDirectDevice(
      deviceName: map['deviceName'] ?? 'Unknown Device',
      deviceAddress: map['deviceAddress'] ?? '',
      status: map['status'] ?? 0,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'deviceName': deviceName,
      'deviceAddress': deviceAddress,
      'status': status,
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
  final String? groupOwnerAddress;

  WiFiDirectConnectionInfo({
    required this.isConnected,
    required this.isGroupOwner,
    this.groupOwnerAddress,
  });

  factory WiFiDirectConnectionInfo.fromMap(Map<String, dynamic> map) {
    return WiFiDirectConnectionInfo(
      isConnected: map['isConnected'] ?? false,
      isGroupOwner: map['isGroupOwner'] ?? false,
      groupOwnerAddress: map['groupOwnerAddress'],
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'isConnected': isConnected,
      'isGroupOwner': isGroupOwner,
      'groupOwnerAddress': groupOwnerAddress,
    };
  }

  WiFiDirectConnectionInfo copyWith({
    bool? isConnected,
    bool? isGroupOwner,
    String? groupOwnerAddress,
  }) {
    return WiFiDirectConnectionInfo(
      isConnected: isConnected ?? this.isConnected,
      isGroupOwner: isGroupOwner ?? this.isGroupOwner,
      groupOwnerAddress: groupOwnerAddress ?? this.groupOwnerAddress,
    );
  }
}

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
  final int bitrateBps;
  final int bufferLevelMs;
  final int framesSent;
  final int framesReceived;
  final int droppedFrames;
  final int underflowCount;
  final int latencyMs;

  const AudioLinkStats({
    this.bitrateBps = 0,
    this.bufferLevelMs = 0,
    this.framesSent = 0,
    this.framesReceived = 0,
    this.droppedFrames = 0,
    this.underflowCount = 0,
    this.latencyMs = -1,
  });

  AudioLinkStats copyWith({
    int? bitrateBps,
    int? bufferLevelMs,
    int? framesSent,
    int? framesReceived,
    int? droppedFrames,
    int? underflowCount,
    int? latencyMs,
  }) {
    return AudioLinkStats(
      bitrateBps: bitrateBps ?? this.bitrateBps,
      bufferLevelMs: bufferLevelMs ?? this.bufferLevelMs,
      framesSent: framesSent ?? this.framesSent,
      framesReceived: framesReceived ?? this.framesReceived,
      droppedFrames: droppedFrames ?? this.droppedFrames,
      underflowCount: underflowCount ?? this.underflowCount,
      latencyMs: latencyMs ?? this.latencyMs,
    );
  }
}

class AudioSupportInfo {
  final bool audioLinkSupported;
  final bool canSend;
  final bool canReceive;
  final String codec;
  final String source;
  final int sampleRate;
  final int channels;
  final int frameDurationMs;
  final int bitrateBps;
  final int requiresApiForSend;
  final int androidApi;
  final String message;

  const AudioSupportInfo({
    this.audioLinkSupported = false,
    this.canSend = false,
    this.canReceive = false,
    this.codec = 'opus',
    this.source = 'microphone',
    this.sampleRate = 48000,
    this.channels = 1,
    this.frameDurationMs = 20,
    this.bitrateBps = 24000,
    this.requiresApiForSend = 29,
    this.androidApi = 0,
    this.message = '',
  });

  factory AudioSupportInfo.fromMap(Map<String, dynamic> map) {
    return AudioSupportInfo(
      audioLinkSupported: map['audioLinkSupported'] == true,
      canSend: map['canSend'] == true,
      canReceive: map['canReceive'] == true,
      codec: map['codec']?.toString() ?? 'opus',
      source: map['source']?.toString() ?? 'microphone',
      sampleRate: _intFromMap(map['sampleRate'], 48000),
      channels: _intFromMap(map['channels'], 1),
      frameDurationMs: _intFromMap(map['frameDurationMs'], 20),
      bitrateBps: _intFromMap(map['bitrateBps'], 24000),
      requiresApiForSend: _intFromMap(map['requiresApiForSend'], 29),
      androidApi: _intFromMap(map['androidApi'], 0),
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
  final bool isDiscovering;
  final bool isConnecting;
  final String? pendingPeerAddress;
  final bool isServerStarted;
  final List<WiFiDirectDevice> peers;
  final WiFiDirectConnectionInfo? connectionInfo;
  final String sessionState;
  final String? sessionId;
  final String? sessionRole;
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
  final String audioSource;
  final String audioEncoding;
  final String audioState;
  final bool audioPeerReady;
  final int? audioStreamId;
  final AudioLinkStats audioStats;
  final String? audioLastError;

  WiFiDirectState({
    this.isWifiP2pEnabled = false,
    this.isDiscovering = false,
    this.isConnecting = false,
    this.pendingPeerAddress,
    this.isServerStarted = false,
    this.peers = const [],
    this.connectionInfo,
    this.sessionState = 'Disconnected',
    this.sessionId,
    this.sessionRole,
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
    bool? isDiscovering,
    bool? isConnecting,
    Object? pendingPeerAddress = _unset,
    bool? isServerStarted,
    List<WiFiDirectDevice>? peers,
    Object? connectionInfo = _unset,
    String? sessionState,
    Object? sessionId = _unset,
    Object? sessionRole = _unset,
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
      isDiscovering: isDiscovering ?? this.isDiscovering,
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

  bool get isSessionReady => sessionState == 'Ready';

  bool get peerSupportsAudio =>
      peerCapabilities.contains('audio.link') &&
      peerCapabilities.contains('audio.codec.opus');

  bool get isAudioActive => audioState != 'idle';

  bool get isAudioStreaming => audioState == 'streaming';
}
