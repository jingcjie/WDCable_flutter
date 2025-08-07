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
  /// **'Files will be automatically received when sent by peer'**
  String get filesWillBeAutomaticallyReceived;

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

  /// Transfer settings section title
  ///
  /// In en, this message translates to:
  /// **'Transfer Settings'**
  String get transferSettings;

  /// Transfer timeout setting title
  ///
  /// In en, this message translates to:
  /// **'Transfer Timeout'**
  String get transferTimeout;

  /// Transfer timeout setting description
  ///
  /// In en, this message translates to:
  /// **'Timeout for file transfers (seconds)'**
  String get timeoutForFileTransfers;

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

  /// Unit for timeout (seconds)
  ///
  /// In en, this message translates to:
  /// **'s'**
  String get timeoutUnit;

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
