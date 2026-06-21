import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('zh'),
  ];

  /// The title of the application
  ///
  /// In en, this message translates to:
  /// **'WiFi Direct Cable'**
  String get appTitle;

  /// WiFi P2P driver status label
  ///
  /// In en, this message translates to:
  /// **'WiFi P2P Driver'**
  String get wifiP2pDriver;

  /// Status when WiFi P2P is enabled
  ///
  /// In en, this message translates to:
  /// **'Ready for connections'**
  String get readyForConnections;

  /// Status when WiFi P2P is disabled
  ///
  /// In en, this message translates to:
  /// **'Disabled - Enable WiFi to continue'**
  String get disabledEnableWifi;

  /// Connection status label
  ///
  /// In en, this message translates to:
  /// **'Connection Status'**
  String get connectionStatus;

  /// Connected status
  ///
  /// In en, this message translates to:
  /// **'Connected'**
  String get connected;

  /// Disconnected status
  ///
  /// In en, this message translates to:
  /// **'Disconnected'**
  String get disconnected;

  /// Button text to scan for devices
  ///
  /// In en, this message translates to:
  /// **'Scan for Devices'**
  String get scanForDevices;

  /// Text shown when scanning for devices
  ///
  /// In en, this message translates to:
  /// **'Scanning...'**
  String get scanning;

  /// Button text to stop scanning
  ///
  /// In en, this message translates to:
  /// **'Stop Scan'**
  String get stopScan;

  /// Button text to show device information
  ///
  /// In en, this message translates to:
  /// **'Device Info'**
  String get deviceInfo;

  /// Button text to reset WiFi Direct
  ///
  /// In en, this message translates to:
  /// **'Reset WiFi Direct'**
  String get resetWifiDirect;

  /// Message when no devices are found
  ///
  /// In en, this message translates to:
  /// **'No devices found'**
  String get noDevicesFound;

  /// Instruction text for scanning devices
  ///
  /// In en, this message translates to:
  /// **'Tap \"Scan for Devices\" to find nearby devices'**
  String get tapScanForDevices;

  /// Header for available devices list
  ///
  /// In en, this message translates to:
  /// **'Available Devices ({count})'**
  String availableDevices(int count);

  /// Button text to connect to a device
  ///
  /// In en, this message translates to:
  /// **'Connect'**
  String get connect;

  /// Button text to disconnect from a device
  ///
  /// In en, this message translates to:
  /// **'Disconnect'**
  String get disconnect;

  /// Logs section header
  ///
  /// In en, this message translates to:
  /// **'Logs'**
  String get logs;

  /// Button text to clear logs
  ///
  /// In en, this message translates to:
  /// **'Clear Logs'**
  String get clearLogs;

  /// Chat tab label
  ///
  /// In en, this message translates to:
  /// **'Chat'**
  String get chat;

  /// Speed test label in chat
  ///
  /// In en, this message translates to:
  /// **'Speed Test'**
  String get speedTest;

  /// File transfer tab label
  ///
  /// In en, this message translates to:
  /// **'File Transfer'**
  String get fileTransfer;

  /// Settings tab label
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settings;

  /// Status when connected and ready to chat
  ///
  /// In en, this message translates to:
  /// **'Connected - Ready to chat'**
  String get connectedReadyToChat;

  /// Status when not connected to peer
  ///
  /// In en, this message translates to:
  /// **'Not connected - Connect to a peer to start chatting'**
  String get notConnectedConnectToPeer;

  /// Host role indicator
  ///
  /// In en, this message translates to:
  /// **'Host'**
  String get host;

  /// Client role indicator
  ///
  /// In en, this message translates to:
  /// **'Client'**
  String get client;

  /// Message when no chat messages exist
  ///
  /// In en, this message translates to:
  /// **'No messages yet'**
  String get noMessagesYet;

  /// Instruction to connect and start chatting
  ///
  /// In en, this message translates to:
  /// **'Connect to a peer and start chatting!'**
  String get connectToPeerAndStartChatting;

  /// Chat input placeholder when connected
  ///
  /// In en, this message translates to:
  /// **'Type a message...'**
  String get typeAMessage;

  /// Chat input placeholder when not connected
  ///
  /// In en, this message translates to:
  /// **'Connect to start chatting'**
  String get connectToStartChatting;

  /// Timestamp for very recent messages
  ///
  /// In en, this message translates to:
  /// **'Just now'**
  String get justNow;

  /// Days ago timestamp
  ///
  /// In en, this message translates to:
  /// **'{count}d ago'**
  String daysAgo(int count);

  /// Hours ago timestamp
  ///
  /// In en, this message translates to:
  /// **'{count}h ago'**
  String hoursAgo(int count);

  /// Minutes ago timestamp
  ///
  /// In en, this message translates to:
  /// **'{count}m ago'**
  String minutesAgo(int count);

  /// Error message when trying to send file without connection
  ///
  /// In en, this message translates to:
  /// **'Please connect to a peer first'**
  String get pleaseConnectToPeerFirst;

  /// Success message when file is sent
  ///
  /// In en, this message translates to:
  /// **'File sent: {fileName}'**
  String fileSent(String fileName);

  /// Error message when file send fails
  ///
  /// In en, this message translates to:
  /// **'Failed to send file: {error}'**
  String failedToSendFile(String error);

  /// Connection status when not connected
  ///
  /// In en, this message translates to:
  /// **'Not Connected'**
  String get notConnected;

  /// Status when ready for file transfer
  ///
  /// In en, this message translates to:
  /// **'Ready for file transfer'**
  String get readyForFileTransfer;

  /// Instruction when not connected
  ///
  /// In en, this message translates to:
  /// **'Connect to start transferring files'**
  String get connectToStartTransferringFiles;

  /// Send file button text
  ///
  /// In en, this message translates to:
  /// **'Send File'**
  String get sendFile;

  /// Receive files section title
  ///
  /// In en, this message translates to:
  /// **'Receive Files'**
  String get receiveFiles;

  /// Description of automatic file receiving
  ///
  /// In en, this message translates to:
  /// **'Files sent by the peer will be saved to {location}'**
  String filesWillBeAutomaticallyReceived(String location);

  /// No description provided for @saveReceivedFilesTo.
  ///
  /// In en, this message translates to:
  /// **'Save received files to'**
  String get saveReceivedFilesTo;

  /// No description provided for @appStorage.
  ///
  /// In en, this message translates to:
  /// **'App storage'**
  String get appStorage;

  /// No description provided for @downloadsFolder.
  ///
  /// In en, this message translates to:
  /// **'Downloads'**
  String get downloadsFolder;

  /// No description provided for @chooseCustomFolder.
  ///
  /// In en, this message translates to:
  /// **'Choose custom folder'**
  String get chooseCustomFolder;

  /// No description provided for @receiveDestinationFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not use that receive location'**
  String get receiveDestinationFailed;

  /// No description provided for @fileReceived.
  ///
  /// In en, this message translates to:
  /// **'File received: {fileName}'**
  String fileReceived(String fileName);

  /// No description provided for @fileTransferCancelled.
  ///
  /// In en, this message translates to:
  /// **'Transfer cancelled: {fileName}'**
  String fileTransferCancelled(String fileName);

  /// No description provided for @fileTransferFailed.
  ///
  /// In en, this message translates to:
  /// **'Transfer failed for {fileName}: {error}'**
  String fileTransferFailed(String fileName, String error);

  /// No description provided for @preparing.
  ///
  /// In en, this message translates to:
  /// **'Preparing'**
  String get preparing;

  /// No description provided for @queued.
  ///
  /// In en, this message translates to:
  /// **'Queued'**
  String get queued;

  /// No description provided for @cancelling.
  ///
  /// In en, this message translates to:
  /// **'Cancelling...'**
  String get cancelling;

  /// No description provided for @cancelled.
  ///
  /// In en, this message translates to:
  /// **'Cancelled'**
  String get cancelled;

  /// No description provided for @failed.
  ///
  /// In en, this message translates to:
  /// **'Failed'**
  String get failed;

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// Message when no file transfers are active
  ///
  /// In en, this message translates to:
  /// **'No Active Transfers'**
  String get noActiveTransfers;

  /// Status when upload is complete
  ///
  /// In en, this message translates to:
  /// **'Upload Complete'**
  String get uploadComplete;

  /// Status when download is complete
  ///
  /// In en, this message translates to:
  /// **'Download Complete'**
  String get downloadComplete;

  /// Status when uploading
  ///
  /// In en, this message translates to:
  /// **'Uploading...'**
  String get uploading;

  /// Status when downloading
  ///
  /// In en, this message translates to:
  /// **'Downloading...'**
  String get downloading;

  /// Recent transfers section title
  ///
  /// In en, this message translates to:
  /// **'Recent Transfers'**
  String get recentTransfers;

  /// Message when no recent transfers exist
  ///
  /// In en, this message translates to:
  /// **'No recent transfers'**
  String get noRecentTransfers;

  /// Label for sent files
  ///
  /// In en, this message translates to:
  /// **'Sent'**
  String get sent;

  /// Label for received files
  ///
  /// In en, this message translates to:
  /// **'Received'**
  String get received;

  /// Tooltip for open file button
  ///
  /// In en, this message translates to:
  /// **'Open file'**
  String get openFile;

  /// Tooltip for clear button
  ///
  /// In en, this message translates to:
  /// **'Clear'**
  String get clear;

  /// Error message when file open fails
  ///
  /// In en, this message translates to:
  /// **'Failed to open file: {error}'**
  String failedToOpenFile(String error);

  /// Informational message when no installed app supports a received file
  ///
  /// In en, this message translates to:
  /// **'There is no supported app to open {fileName}. It’s saved in {location}.'**
  String noSupportedAppToOpenFile(String fileName, String location);

  /// System logs section header
  ///
  /// In en, this message translates to:
  /// **'System Logs'**
  String get systemLogs;

  /// Message when no logs exist
  ///
  /// In en, this message translates to:
  /// **'No logs yet'**
  String get noLogsYet;

  /// Settings page subtitle
  ///
  /// In en, this message translates to:
  /// **'Customize your WiFi Direct experience'**
  String get customizeYourWifiDirectExperience;

  /// App settings section title
  ///
  /// In en, this message translates to:
  /// **'App Settings'**
  String get appSettings;

  /// Language setting title
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get language;

  /// Language setting description
  ///
  /// In en, this message translates to:
  /// **'Choose your preferred language'**
  String get chooseYourPreferredLanguage;

  /// Follow system language option
  ///
  /// In en, this message translates to:
  /// **'Follow System'**
  String get followSystem;

  /// English language option
  ///
  /// In en, this message translates to:
  /// **'English'**
  String get english;

  /// Dark mode setting title
  ///
  /// In en, this message translates to:
  /// **'Dark Mode'**
  String get darkMode;

  /// Dark mode setting description
  ///
  /// In en, this message translates to:
  /// **'Use dark theme'**
  String get useDarkTheme;

  /// About section title
  ///
  /// In en, this message translates to:
  /// **'About'**
  String get about;

  /// Version info title
  ///
  /// In en, this message translates to:
  /// **'Version'**
  String get version;

  /// Privacy policy title
  ///
  /// In en, this message translates to:
  /// **'Privacy Policy'**
  String get privacyPolicy;

  /// Privacy policy description
  ///
  /// In en, this message translates to:
  /// **'View our privacy policy'**
  String get viewOurPrivacyPolicy;

  /// Privacy policy dialog content
  ///
  /// In en, this message translates to:
  /// **'This app uses WiFi Direct to establish peer-to-peer connections. No data is sent to external servers. All communications happen directly between devices.'**
  String get privacyPolicyContent;

  /// OK button text
  ///
  /// In en, this message translates to:
  /// **'OK'**
  String get ok;

  /// Speed test status title
  ///
  /// In en, this message translates to:
  /// **'Speed Test Status'**
  String get speedTestStatus;

  /// Status when ready to test speed
  ///
  /// In en, this message translates to:
  /// **'Ready to test connection speed'**
  String get readyToTestConnectionSpeed;

  /// Status when not connected for speed test
  ///
  /// In en, this message translates to:
  /// **'Connect to a peer to test speed'**
  String get connectToPeerToTestSpeed;

  /// Speed test in progress text
  ///
  /// In en, this message translates to:
  /// **'Testing...'**
  String get testing;

  /// Start speed test button text
  ///
  /// In en, this message translates to:
  /// **'START'**
  String get start;

  /// Instruction to start speed test
  ///
  /// In en, this message translates to:
  /// **'Tap to start speed test'**
  String get tapToStartSpeedTest;

  /// Instruction when not connected
  ///
  /// In en, this message translates to:
  /// **'Connect to a peer first'**
  String get connectToPeerFirst;

  /// Download test phase label
  ///
  /// In en, this message translates to:
  /// **'Download Test'**
  String get downloadTest;

  /// Upload test phase label
  ///
  /// In en, this message translates to:
  /// **'Upload Test'**
  String get uploadTest;

  /// Download label
  ///
  /// In en, this message translates to:
  /// **'Download'**
  String get download;

  /// Upload label
  ///
  /// In en, this message translates to:
  /// **'Upload'**
  String get upload;

  /// Speed display format
  ///
  /// In en, this message translates to:
  /// **'Speed: {speed} MB/s'**
  String speed(String speed);

  /// Complete status
  ///
  /// In en, this message translates to:
  /// **'Complete'**
  String get complete;

  /// In progress status
  ///
  /// In en, this message translates to:
  /// **'In Progress'**
  String get inProgress;

  /// Latest results section title
  ///
  /// In en, this message translates to:
  /// **'Latest Results'**
  String get latestResults;

  /// Test completion time label
  ///
  /// In en, this message translates to:
  /// **'Test completed at'**
  String get testCompletedAt;

  /// Message when no test results exist
  ///
  /// In en, this message translates to:
  /// **'No test results yet'**
  String get noTestResultsYet;

  /// Instruction to run speed test
  ///
  /// In en, this message translates to:
  /// **'Run a speed test to see results here'**
  String get runSpeedTestToSeeResults;

  /// Test history section title
  ///
  /// In en, this message translates to:
  /// **'Test History'**
  String get testHistory;

  /// Number of tests format
  ///
  /// In en, this message translates to:
  /// **'{count} tests'**
  String tests(int count);

  /// Message when no test history exists
  ///
  /// In en, this message translates to:
  /// **'No test history'**
  String get noTestHistory;

  /// Single day ago format
  ///
  /// In en, this message translates to:
  /// **'{count} day ago'**
  String dayAgo(int count);

  /// Multiple days ago format
  ///
  /// In en, this message translates to:
  /// **'{count} days ago'**
  String daysAgoLong(int count);

  /// Single hour ago format
  ///
  /// In en, this message translates to:
  /// **'{count} hour ago'**
  String hourAgo(int count);

  /// Multiple hours ago format
  ///
  /// In en, this message translates to:
  /// **'{count} hours ago'**
  String hoursAgoLong(int count);

  /// Single minute ago format
  ///
  /// In en, this message translates to:
  /// **'{count} minute ago'**
  String minuteAgo(int count);

  /// Multiple minutes ago format
  ///
  /// In en, this message translates to:
  /// **'{count} minutes ago'**
  String minutesAgoLong(int count);

  /// Chinese language option
  ///
  /// In en, this message translates to:
  /// **'中文'**
  String get chinese;

  /// GitHub repositories section title
  ///
  /// In en, this message translates to:
  /// **'GitHub Repositories'**
  String get githubRepositories;

  /// Flutter app repository title
  ///
  /// In en, this message translates to:
  /// **'Flutter App Repository'**
  String get flutterAppRepository;

  /// Flutter app repository description
  ///
  /// In en, this message translates to:
  /// **'WDCable Flutter - Mobile WiFi Direct file transfer app'**
  String get flutterAppDescription;

  /// Windows app repository title
  ///
  /// In en, this message translates to:
  /// **'Windows App Repository'**
  String get windowsAppRepository;

  /// Windows app repository description
  ///
  /// In en, this message translates to:
  /// **'WDCableWUI - Windows companion application'**
  String get windowsAppDescription;

  /// Message shown when URL is copied to clipboard
  ///
  /// In en, this message translates to:
  /// **'URL copied to clipboard: {url}'**
  String urlCopiedToClipboard(String url);

  /// Settings page title
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settingsTitle;

  /// Settings page subtitle
  ///
  /// In en, this message translates to:
  /// **'Customize your WiFi Direct experience'**
  String get settingsSubtitle;

  /// No description provided for @audioLink.
  ///
  /// In en, this message translates to:
  /// **'Audio Link'**
  String get audioLink;

  /// No description provided for @audioConnectToPeerFirst.
  ///
  /// In en, this message translates to:
  /// **'Connect to a peer first'**
  String get audioConnectToPeerFirst;

  /// No description provided for @audioPeerUnsupported.
  ///
  /// In en, this message translates to:
  /// **'The connected peer does not support Audio Link'**
  String get audioPeerUnsupported;

  /// No description provided for @audioReady.
  ///
  /// In en, this message translates to:
  /// **'Audio Link is ready'**
  String get audioReady;

  /// No description provided for @audioMode.
  ///
  /// In en, this message translates to:
  /// **'Mode'**
  String get audioMode;

  /// No description provided for @audioReceive.
  ///
  /// In en, this message translates to:
  /// **'Receive'**
  String get audioReceive;

  /// No description provided for @audioSend.
  ///
  /// In en, this message translates to:
  /// **'Send'**
  String get audioSend;

  /// No description provided for @audioSource.
  ///
  /// In en, this message translates to:
  /// **'Source'**
  String get audioSource;

  /// No description provided for @audioMicrophone.
  ///
  /// In en, this message translates to:
  /// **'Microphone'**
  String get audioMicrophone;

  /// No description provided for @audioDeviceAudioUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Device audio unavailable'**
  String get audioDeviceAudioUnavailable;

  /// No description provided for @audioLatencyMode.
  ///
  /// In en, this message translates to:
  /// **'Latency Mode'**
  String get audioLatencyMode;

  /// No description provided for @audioLowLatency.
  ///
  /// In en, this message translates to:
  /// **'Low latency'**
  String get audioLowLatency;

  /// No description provided for @audioStable.
  ///
  /// In en, this message translates to:
  /// **'Stable'**
  String get audioStable;

  /// No description provided for @audioQualityMode.
  ///
  /// In en, this message translates to:
  /// **'Quality'**
  String get audioQualityMode;

  /// No description provided for @audioQualityStandard.
  ///
  /// In en, this message translates to:
  /// **'Standard'**
  String get audioQualityStandard;

  /// No description provided for @audioQualityBalanced.
  ///
  /// In en, this message translates to:
  /// **'Balanced'**
  String get audioQualityBalanced;

  /// No description provided for @audioQualityHigh.
  ///
  /// In en, this message translates to:
  /// **'High'**
  String get audioQualityHigh;

  /// No description provided for @audioQualityNearLossless.
  ///
  /// In en, this message translates to:
  /// **'Near lossless'**
  String get audioQualityNearLossless;

  /// No description provided for @audioEncoding.
  ///
  /// In en, this message translates to:
  /// **'Encoding'**
  String get audioEncoding;

  /// No description provided for @audioOpus.
  ///
  /// In en, this message translates to:
  /// **'Opus'**
  String get audioOpus;

  /// No description provided for @audioOpus32Kbps.
  ///
  /// In en, this message translates to:
  /// **'Opus 32 kbps'**
  String get audioOpus32Kbps;

  /// No description provided for @audioOnlyOption.
  ///
  /// In en, this message translates to:
  /// **'Only option'**
  String get audioOnlyOption;

  /// No description provided for @audioFollowSenderSide.
  ///
  /// In en, this message translates to:
  /// **'Follow sender side'**
  String get audioFollowSenderSide;

  /// No description provided for @audioStop.
  ///
  /// In en, this message translates to:
  /// **'Stop Audio'**
  String get audioStop;

  /// No description provided for @audioStart.
  ///
  /// In en, this message translates to:
  /// **'Start Audio'**
  String get audioStart;

  /// No description provided for @audioLiveStats.
  ///
  /// In en, this message translates to:
  /// **'Live Stats'**
  String get audioLiveStats;

  /// No description provided for @audioState.
  ///
  /// In en, this message translates to:
  /// **'State'**
  String get audioState;

  /// No description provided for @audioBitrate.
  ///
  /// In en, this message translates to:
  /// **'Bitrate'**
  String get audioBitrate;

  /// No description provided for @audioConfiguredBitrate.
  ///
  /// In en, this message translates to:
  /// **'Configured'**
  String get audioConfiguredBitrate;

  /// No description provided for @audioQuality.
  ///
  /// In en, this message translates to:
  /// **'Quality'**
  String get audioQuality;

  /// No description provided for @audioBuffer.
  ///
  /// In en, this message translates to:
  /// **'Buffer'**
  String get audioBuffer;

  /// No description provided for @audioDropped.
  ///
  /// In en, this message translates to:
  /// **'Dropped'**
  String get audioDropped;

  /// No description provided for @audioPacketLoss.
  ///
  /// In en, this message translates to:
  /// **'Packet Loss'**
  String get audioPacketLoss;

  /// No description provided for @audioLateDrops.
  ///
  /// In en, this message translates to:
  /// **'Late Drops'**
  String get audioLateDrops;

  /// No description provided for @audioOverflowDrops.
  ///
  /// In en, this message translates to:
  /// **'Overflow Drops'**
  String get audioOverflowDrops;

  /// No description provided for @audioPlc.
  ///
  /// In en, this message translates to:
  /// **'PLC'**
  String get audioPlc;

  /// No description provided for @audioRtcpLoss.
  ///
  /// In en, this message translates to:
  /// **'RTCP Loss'**
  String get audioRtcpLoss;

  /// No description provided for @audioRtcpJitter.
  ///
  /// In en, this message translates to:
  /// **'RTCP Jitter'**
  String get audioRtcpJitter;

  /// No description provided for @audioRoundTrip.
  ///
  /// In en, this message translates to:
  /// **'Round Trip'**
  String get audioRoundTrip;

  /// No description provided for @audioFrames.
  ///
  /// In en, this message translates to:
  /// **'Frames'**
  String get audioFrames;

  /// No description provided for @audioLatency.
  ///
  /// In en, this message translates to:
  /// **'Latency'**
  String get audioLatency;

  /// No description provided for @audioStateReceiveReady.
  ///
  /// In en, this message translates to:
  /// **'Receive ready'**
  String get audioStateReceiveReady;

  /// No description provided for @audioStateOfferSent.
  ///
  /// In en, this message translates to:
  /// **'Offer sent'**
  String get audioStateOfferSent;

  /// No description provided for @audioStateConnecting.
  ///
  /// In en, this message translates to:
  /// **'Connecting'**
  String get audioStateConnecting;

  /// No description provided for @audioStateStreaming.
  ///
  /// In en, this message translates to:
  /// **'Streaming'**
  String get audioStateStreaming;

  /// No description provided for @audioStateIdle.
  ///
  /// In en, this message translates to:
  /// **'Idle'**
  String get audioStateIdle;

  /// No description provided for @notAvailableShort.
  ///
  /// In en, this message translates to:
  /// **'N/A'**
  String get notAvailableShort;

  /// No description provided for @kbpsUnit.
  ///
  /// In en, this message translates to:
  /// **'kbps'**
  String get kbpsUnit;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'zh'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
