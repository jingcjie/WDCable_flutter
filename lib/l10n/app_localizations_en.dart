// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'WiFi Direct Cable';

  @override
  String get wifiP2pDriver => 'WiFi P2P Driver';

  @override
  String get readyForConnections => 'Ready for connections';

  @override
  String get disabledEnableWifi => 'Disabled - Enable WiFi to continue';

  @override
  String get connectionStatus => 'Connection Status';

  @override
  String get connected => 'Connected';

  @override
  String get disconnected => 'Disconnected';

  @override
  String get scanForDevices => 'Scan for Devices';

  @override
  String get scanning => 'Scanning...';

  @override
  String get stopScan => 'Stop Scan';

  @override
  String get deviceInfo => 'Device Info';

  @override
  String get resetWifiDirect => 'Reset WiFi Direct';

  @override
  String get noDevicesFound => 'No devices found';

  @override
  String get tapScanForDevices =>
      'Tap \"Scan for Devices\" to find nearby devices';

  @override
  String availableDevices(int count) {
    return 'Available Devices ($count)';
  }

  @override
  String get connect => 'Connect';

  @override
  String get disconnect => 'Disconnect';

  @override
  String get logs => 'Logs';

  @override
  String get clearLogs => 'Clear Logs';

  @override
  String get chat => 'Chat';

  @override
  String get speedTest => 'Speed Test';

  @override
  String get fileTransfer => 'File Transfer';

  @override
  String get settings => 'Settings';

  @override
  String get connectedReadyToChat => 'Connected - Ready to chat';

  @override
  String get notConnectedConnectToPeer =>
      'Not connected - Connect to a peer to start chatting';

  @override
  String get host => 'Host';

  @override
  String get client => 'Client';

  @override
  String get noMessagesYet => 'No messages yet';

  @override
  String get connectToPeerAndStartChatting =>
      'Connect to a peer and start chatting!';

  @override
  String get typeAMessage => 'Type a message...';

  @override
  String get connectToStartChatting => 'Connect to start chatting';

  @override
  String get justNow => 'Just now';

  @override
  String daysAgo(int count) {
    return '${count}d ago';
  }

  @override
  String hoursAgo(int count) {
    return '${count}h ago';
  }

  @override
  String minutesAgo(int count) {
    return '${count}m ago';
  }

  @override
  String get pleaseConnectToPeerFirst => 'Please connect to a peer first';

  @override
  String fileSent(String fileName) {
    return 'File sent: $fileName';
  }

  @override
  String failedToSendFile(String error) {
    return 'Failed to send file: $error';
  }

  @override
  String get notConnected => 'Not Connected';

  @override
  String get readyForFileTransfer => 'Ready for file transfer';

  @override
  String get connectToStartTransferringFiles =>
      'Connect to start transferring files';

  @override
  String get sendFile => 'Send File';

  @override
  String get receiveFiles => 'Receive Files';

  @override
  String get filesWillBeAutomaticallyReceived =>
      'Files will be automatically received when sent by peer';

  @override
  String get noActiveTransfers => 'No Active Transfers';

  @override
  String get uploadComplete => 'Upload Complete';

  @override
  String get downloadComplete => 'Download Complete';

  @override
  String get uploading => 'Uploading...';

  @override
  String get downloading => 'Downloading...';

  @override
  String get recentTransfers => 'Recent Transfers';

  @override
  String get noRecentTransfers => 'No recent transfers';

  @override
  String get sent => 'Sent';

  @override
  String get received => 'Received';

  @override
  String get openFile => 'Open file';

  @override
  String get clear => 'Clear';

  @override
  String failedToOpenFile(String error) {
    return 'Failed to open file: $error';
  }

  @override
  String get systemLogs => 'System Logs';

  @override
  String get noLogsYet => 'No logs yet';

  @override
  String get customizeYourWifiDirectExperience =>
      'Customize your WiFi Direct experience';

  @override
  String get transferSettings => 'Transfer Settings';

  @override
  String get transferTimeout => 'Transfer Timeout';

  @override
  String get timeoutForFileTransfers => 'Timeout for file transfers (seconds)';

  @override
  String get appSettings => 'App Settings';

  @override
  String get language => 'Language';

  @override
  String get chooseYourPreferredLanguage => 'Choose your preferred language';

  @override
  String get followSystem => 'Follow System';

  @override
  String get english => 'English';

  @override
  String get darkMode => 'Dark Mode';

  @override
  String get useDarkTheme => 'Use dark theme';

  @override
  String get about => 'About';

  @override
  String get version => 'Version';

  @override
  String get privacyPolicy => 'Privacy Policy';

  @override
  String get viewOurPrivacyPolicy => 'View our privacy policy';

  @override
  String get privacyPolicyContent =>
      'This app uses WiFi Direct to establish peer-to-peer connections. No data is sent to external servers. All communications happen directly between devices.';

  @override
  String get ok => 'OK';

  @override
  String get speedTestStatus => 'Speed Test Status';

  @override
  String get readyToTestConnectionSpeed => 'Ready to test connection speed';

  @override
  String get connectToPeerToTestSpeed => 'Connect to a peer to test speed';

  @override
  String get testing => 'Testing...';

  @override
  String get start => 'START';

  @override
  String get tapToStartSpeedTest => 'Tap to start speed test';

  @override
  String get connectToPeerFirst => 'Connect to a peer first';

  @override
  String get downloadTest => 'Download Test';

  @override
  String get uploadTest => 'Upload Test';

  @override
  String get download => 'Download';

  @override
  String get upload => 'Upload';

  @override
  String speed(String speed) {
    return 'Speed: $speed MB/s';
  }

  @override
  String get complete => 'Complete';

  @override
  String get inProgress => 'In Progress';

  @override
  String get latestResults => 'Latest Results';

  @override
  String get testCompletedAt => 'Test completed at';

  @override
  String get noTestResultsYet => 'No test results yet';

  @override
  String get runSpeedTestToSeeResults => 'Run a speed test to see results here';

  @override
  String get testHistory => 'Test History';

  @override
  String tests(int count) {
    return '$count tests';
  }

  @override
  String get noTestHistory => 'No test history';

  @override
  String dayAgo(int count) {
    return '$count day ago';
  }

  @override
  String daysAgoLong(int count) {
    return '$count days ago';
  }

  @override
  String hourAgo(int count) {
    return '$count hour ago';
  }

  @override
  String hoursAgoLong(int count) {
    return '$count hours ago';
  }

  @override
  String minuteAgo(int count) {
    return '$count minute ago';
  }

  @override
  String minutesAgoLong(int count) {
    return '$count minutes ago';
  }

  @override
  String get timeoutUnit => 's';

  @override
  String get chinese => '中文';

  @override
  String get githubRepositories => 'GitHub Repositories';

  @override
  String get flutterAppRepository => 'Flutter App Repository';

  @override
  String get flutterAppDescription =>
      'WDCable Flutter - Mobile WiFi Direct file transfer app';

  @override
  String get windowsAppRepository => 'Windows App Repository';

  @override
  String get windowsAppDescription =>
      'WDCableWUI - Windows companion application';

  @override
  String urlCopiedToClipboard(String url) {
    return 'URL copied to clipboard: $url';
  }

  @override
  String get settingsTitle => 'Settings';

  @override
  String get settingsSubtitle => 'Customize your WiFi Direct experience';
}
