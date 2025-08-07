// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => 'WiFi直连线缆';

  @override
  String get wifiP2pDriver => 'WiFi P2P驱动';

  @override
  String get readyForConnections => '准备连接';

  @override
  String get disabledEnableWifi => '已禁用 - 启用WiFi以继续';

  @override
  String get connectionStatus => '连接状态';

  @override
  String get connected => '已连接';

  @override
  String get disconnected => '未连接';

  @override
  String get scanForDevices => '扫描设备';

  @override
  String get scanning => '扫描中...';

  @override
  String get stopScan => '停止扫描';

  @override
  String get deviceInfo => '设备信息';

  @override
  String get resetWifiDirect => '重置WiFi直连';

  @override
  String get noDevicesFound => '未找到设备';

  @override
  String get tapScanForDevices => '点击\"扫描设备\"查找附近设备';

  @override
  String availableDevices(int count) {
    return '可用设备 ($count)';
  }

  @override
  String get connect => '连接';

  @override
  String get disconnect => '断开连接';

  @override
  String get logs => '日志';

  @override
  String get clearLogs => '清除日志';

  @override
  String get chat => '聊天';

  @override
  String get speedTest => '速度测试';

  @override
  String get fileTransfer => '文件传输';

  @override
  String get settings => '设置';

  @override
  String get connectedReadyToChat => '已连接 - 准备聊天';

  @override
  String get notConnectedConnectToPeer => '未连接 - 连接到对等设备开始聊天';

  @override
  String get host => '主机';

  @override
  String get client => '客户端';

  @override
  String get noMessagesYet => '暂无消息';

  @override
  String get connectToPeerAndStartChatting => '连接到对等设备开始聊天！';

  @override
  String get typeAMessage => '输入消息...';

  @override
  String get connectToStartChatting => '连接后开始聊天';

  @override
  String get justNow => '刚刚';

  @override
  String daysAgo(int count) {
    return '$count天前';
  }

  @override
  String hoursAgo(int count) {
    return '$count小时前';
  }

  @override
  String minutesAgo(int count) {
    return '$count分钟前';
  }

  @override
  String get pleaseConnectToPeerFirst => '请先连接到对等设备';

  @override
  String fileSent(String fileName) {
    return '文件已发送：$fileName';
  }

  @override
  String failedToSendFile(String error) {
    return '发送文件失败：$error';
  }

  @override
  String get notConnected => '未连接';

  @override
  String get readyForFileTransfer => '准备文件传输';

  @override
  String get connectToStartTransferringFiles => '连接后开始传输文件';

  @override
  String get sendFile => '发送文件';

  @override
  String get receiveFiles => '接收文件';

  @override
  String get filesWillBeAutomaticallyReceived => '对等设备发送的文件将自动接收';

  @override
  String get noActiveTransfers => '无活动传输';

  @override
  String get uploadComplete => '上传完成';

  @override
  String get downloadComplete => '下载完成';

  @override
  String get uploading => '上传中...';

  @override
  String get downloading => '下载中...';

  @override
  String get recentTransfers => '最近传输';

  @override
  String get noRecentTransfers => '无最近传输';

  @override
  String get sent => '已发送';

  @override
  String get received => '已接收';

  @override
  String get openFile => '打开文件';

  @override
  String get clear => '清除';

  @override
  String failedToOpenFile(String error) {
    return '打开文件失败：$error';
  }

  @override
  String get systemLogs => '系统日志';

  @override
  String get noLogsYet => '暂无日志';

  @override
  String get customizeYourWifiDirectExperience => '自定义您的WiFi直连体验';

  @override
  String get transferSettings => '传输设置';

  @override
  String get transferTimeout => '传输超时';

  @override
  String get timeoutForFileTransfers => '文件传输超时时间（秒）';

  @override
  String get appSettings => '应用设置';

  @override
  String get language => '语言';

  @override
  String get chooseYourPreferredLanguage => '选择您的首选语言';

  @override
  String get followSystem => '跟随系统';

  @override
  String get english => 'English';

  @override
  String get darkMode => '深色模式';

  @override
  String get useDarkTheme => '使用深色主题';

  @override
  String get about => '关于';

  @override
  String get version => '版本';

  @override
  String get privacyPolicy => '隐私政策';

  @override
  String get viewOurPrivacyPolicy => '查看我们的隐私政策';

  @override
  String get privacyPolicyContent =>
      '此应用使用WiFi直连建立点对点连接。不会向外部服务器发送数据。所有通信都直接在设备之间进行。';

  @override
  String get ok => '确定';

  @override
  String get speedTestStatus => '速度测试状态';

  @override
  String get readyToTestConnectionSpeed => '准备测试连接速度';

  @override
  String get connectToPeerToTestSpeed => '连接到对等设备以测试速度';

  @override
  String get testing => '测试中...';

  @override
  String get start => '开始';

  @override
  String get tapToStartSpeedTest => '点击开始速度测试';

  @override
  String get connectToPeerFirst => '请先连接到对等设备';

  @override
  String get downloadTest => '下载测试';

  @override
  String get uploadTest => '上传测试';

  @override
  String get download => '下载';

  @override
  String get upload => '上传';

  @override
  String speed(String speed) {
    return '速度：$speed MB/s';
  }

  @override
  String get complete => '完成';

  @override
  String get inProgress => '进行中';

  @override
  String get latestResults => '最新结果';

  @override
  String get testCompletedAt => '测试完成时间';

  @override
  String get noTestResultsYet => '暂无测试结果';

  @override
  String get runSpeedTestToSeeResults => '运行速度测试以查看结果';

  @override
  String get testHistory => '测试历史';

  @override
  String tests(int count) {
    return '$count次测试';
  }

  @override
  String get noTestHistory => '无测试历史';

  @override
  String dayAgo(int count) {
    return '$count天前';
  }

  @override
  String daysAgoLong(int count) {
    return '$count天前';
  }

  @override
  String hourAgo(int count) {
    return '$count小时前';
  }

  @override
  String hoursAgoLong(int count) {
    return '$count小时前';
  }

  @override
  String minuteAgo(int count) {
    return '$count分钟前';
  }

  @override
  String minutesAgoLong(int count) {
    return '$count分钟前';
  }

  @override
  String get timeoutUnit => '秒';

  @override
  String get chinese => '中文';

  @override
  String get githubRepositories => 'GitHub 仓库';

  @override
  String get flutterAppRepository => 'Flutter 应用仓库';

  @override
  String get flutterAppDescription => 'WDCable Flutter - 移动端 WiFi 直连文件传输应用';

  @override
  String get windowsAppRepository => 'Windows 应用仓库';

  @override
  String get windowsAppDescription => 'WDCableWUI - Windows 配套应用程序';

  @override
  String urlCopiedToClipboard(String url) {
    return 'URL 已复制到剪贴板：$url';
  }

  @override
  String get settingsTitle => '设置';

  @override
  String get settingsSubtitle => '自定义您的WiFi直连体验';
}
