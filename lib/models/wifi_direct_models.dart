/// Data models for WiFi Direct functionality

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
    return other is WiFiDirectDevice &&
        other.deviceAddress == deviceAddress;
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

enum MessageType {
  text,
  file,
  image,
  system,
}

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

class WiFiDirectState {
  final bool isWifiP2pEnabled;
  final bool isDiscovering;
  final bool isServerStarted;
  final List<WiFiDirectDevice> peers;
  final WiFiDirectConnectionInfo? connectionInfo;
  final List<String> logs;
  final List<ChatMessage> chatMessages;
  final SpeedTestResult? lastSpeedTest;
  final bool isSpeedTesting;
  final List<SpeedTestResult> speedTestResults;
  final FileTransferInfo? currentFileTransfer;
  final List<FileTransferInfo> recentFileTransfers;

  WiFiDirectState({
    this.isWifiP2pEnabled = false,
    this.isDiscovering = false,
    this.isServerStarted = false,
    this.peers = const [],
    this.connectionInfo,
    this.logs = const [],
    this.chatMessages = const [],
    this.lastSpeedTest,
    this.isSpeedTesting = false,
    this.speedTestResults = const [],
    this.currentFileTransfer,
    this.recentFileTransfers = const [],
  });

  WiFiDirectState copyWith({
    bool? isWifiP2pEnabled,
    bool? isDiscovering,
    bool? isServerStarted,
    List<WiFiDirectDevice>? peers,
    WiFiDirectConnectionInfo? connectionInfo,
    List<String>? logs,
    List<ChatMessage>? chatMessages,
    SpeedTestResult? lastSpeedTest,
    bool? isSpeedTesting,
    List<SpeedTestResult>? speedTestResults,
    FileTransferInfo? currentFileTransfer,
    List<FileTransferInfo>? recentFileTransfers,
  }) {
    return WiFiDirectState(
      isWifiP2pEnabled: isWifiP2pEnabled ?? this.isWifiP2pEnabled,
      isDiscovering: isDiscovering ?? this.isDiscovering,
      isServerStarted: isServerStarted ?? this.isServerStarted,
      peers: peers ?? this.peers,
      connectionInfo: connectionInfo ?? this.connectionInfo,
      logs: logs ?? this.logs,
      chatMessages: chatMessages ?? this.chatMessages,
      lastSpeedTest: lastSpeedTest ?? this.lastSpeedTest,
      isSpeedTesting: isSpeedTesting ?? this.isSpeedTesting,
      speedTestResults: speedTestResults ?? this.speedTestResults,
      currentFileTransfer: currentFileTransfer ?? this.currentFileTransfer,
      recentFileTransfers: recentFileTransfers ?? this.recentFileTransfers,
    );
  }
}