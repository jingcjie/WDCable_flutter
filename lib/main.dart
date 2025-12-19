import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:wifi_direct_cable/windows_msix_manager.dart';
import 'package:wifi_direct_cable/widgets/setup_required_page.dart';
import 'controllers/wifi_direct_controller.dart';
import 'models/wifi_direct_models.dart';
import 'widgets/connection_tab.dart';
import 'widgets/chat_tab.dart';
import 'widgets/speed_test_tab.dart';
import 'widgets/file_transfer_tab.dart';
import 'widgets/settings_tab.dart';
import 'wifi_direct_service.dart';
import 'theme/theme_provider.dart';
import 'providers/language_provider.dart';
import 'package:wifi_direct_cable/l10n/app_localizations.dart';

import 'package:geolocator/geolocator.dart';

import 'dart:io';

void main() async {
  // 1. Essential: Initialize bindings for native calls
  WidgetsFlutterBinding.ensureInitialized();

  // Default: Assume identity is fine (Android/iOS)
  bool hasIdentity = true;

  // 2. Windows-Specific Logic
  if (Platform.isWindows) {
    // A. Force Windows to unblock WiFi Direct privacy via Location
    try {
      await _prepareLocation();
    } catch (e) {
      debugPrint("Windows Location Prep Warning: $e");
    }

    // B. Check if we are running with Package Identity (MSIX/Sparse)
    hasIdentity = WindowsMsixManager.hasPackageIdentity();
  }

  // 3. Launch App
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (context) => ThemeProvider()),
        ChangeNotifierProvider(create: (context) => LanguageProvider()),
      ],
      child: MyApp(initialRoute: hasIdentity ? '/main' : '/setup'),
    ),
  );
}

Future<void> _prepareLocation() async {
  bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
  if (!serviceEnabled) return;

  LocationPermission permission = await Geolocator.checkPermission();
  if (permission == LocationPermission.denied) {
    permission = await Geolocator.requestPermission();
  }

  if (permission == LocationPermission.always || permission == LocationPermission.whileInUse) {
    try {
      await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(accuracy: LocationAccuracy.low),
      ).timeout(const Duration(seconds: 2));
    } catch (_) {
      // We don't actually need the coordinates, just the permission handshake
    }
  }
}


class MyApp extends StatelessWidget {
  final String initialRoute;
  
  const MyApp({super.key, required this.initialRoute});

  @override
  Widget build(BuildContext context) {
    return Consumer2<ThemeProvider, LanguageProvider>(
      builder: (context, themeProvider, languageProvider, child) {
        return MaterialApp(
          title: 'WiFi Direct Cable',
          theme: AppThemes.lightTheme,
          darkTheme: AppThemes.darkTheme,
          themeMode: themeProvider.themeMode,
          locale: languageProvider.locale,
          localizationsDelegates: const [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [
            Locale('en'),
            Locale('zh'),
          ],
          // Use routes to handle the two different startup screens
          initialRoute: initialRoute,
          routes: {
            '/main': (context) => const WiFiDirectHomePage(),
            '/setup': (context) => const SetupRequiredPage(),
          },
        );
      },
    );
  }
}

class WiFiDirectHomePage extends StatefulWidget {
  const WiFiDirectHomePage({super.key});

  @override
  State<WiFiDirectHomePage> createState() => _WiFiDirectHomePageState();
}

class _WiFiDirectHomePageState extends State<WiFiDirectHomePage>
    with TickerProviderStateMixin {
  late WiFiDirectController _controller;
  late TabController _tabController;
  WiFiDirectState _state = WiFiDirectState();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 5, vsync: this);
    _controller = WiFiDirectController(WiFiDirectService());
    _initializeController();
  }

  void _initializeController() {
    // Listen to state changes from the controller
    _controller.stateStream.listen((newState) {
      if (mounted) {
        setState(() {
          _state = newState;
        });
      }
    });

    // Controller initializes automatically in constructor
  }

  @override
  void dispose() {
    _tabController.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: Brightness.dark,
        systemNavigationBarColor: Theme.of(context).scaffoldBackgroundColor,
      ),
      child: Scaffold(
        backgroundColor: Theme.of(context).scaffoldBackgroundColor,
        body: SafeArea(
          child: Column(
            children: [
              // Modern tab bar with elevated design
              Container(
                margin: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Theme.of(context).cardTheme.color,
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: [
                    BoxShadow(
                      color: Theme.of(context).shadowColor.withOpacity(0.1),
                      blurRadius: 10,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(16),
                  child: TabBar(
                     controller: _tabController,
                     indicator: BoxDecoration(
                       color: Theme.of(context).colorScheme.primary,
                       borderRadius: BorderRadius.circular(12),
                     ),
                     indicatorSize: TabBarIndicatorSize.tab,
                     indicatorPadding: const EdgeInsets.all(2),
                    labelColor: Colors.white,
                    unselectedLabelColor: Theme.of(context).textTheme.bodyMedium?.color?.withOpacity(0.6),
                    labelStyle: const TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                    ),
                    unselectedLabelStyle: const TextStyle(
                      fontWeight: FontWeight.w500,
                      fontSize: 14,
                    ),
                    tabs: const [
                      Tab(
                        icon: Icon(Icons.wifi, size: 20),
                        height: 60,
                      ),
                      Tab(
                        icon: Icon(Icons.chat_bubble_outline, size: 20),
                        height: 60,
                      ),
                      Tab(
                        icon: Icon(Icons.speed, size: 20),
                        height: 60,
                      ),
                      Tab(
                        icon: Icon(Icons.folder, size: 20),
                        height: 60,
                      ),
                      Tab(
                        icon: Icon(Icons.settings, size: 20),
                        height: 60,
                      ),
                    ],
                  ),
                ),
              ),
              // Tab content with padding
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: TabBarView(
                    controller: _tabController,
                    children: [
                      ConnectionTab(
                        controller: _controller,
                        state: _state,
                      ),
                      ChatTab(
                        controller: _controller,
                        state: _state,
                      ),
                      SpeedTestTab(
                        controller: _controller,
                        state: _state,
                      ),
                      FileTransferTab(
                        controller: _controller,
                        state: _state,
                      ),
                      SettingsTab(
                        controller: _controller,
                        state: _state,
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }


}
