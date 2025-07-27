import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'controllers/wifi_direct_controller.dart';
import 'models/wifi_direct_models.dart';
import 'widgets/connection_tab.dart';
import 'widgets/chat_tab.dart';
import 'widgets/speed_test_tab.dart';
import 'widgets/file_transfer_tab.dart';
import 'wifi_direct_service.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'WiFi Direct Cable',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const WiFiDirectHomePage(),
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
    _tabController = TabController(length: 4, vsync: this);
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
        backgroundColor: Colors.grey[50],
        body: SafeArea(
          child: Column(
            children: [
              // Modern tab bar with elevated design
              Container(
                margin: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.1),
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
                    unselectedLabelColor: Colors.grey[600],
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
                        text: 'Connection',
                        height: 60,
                      ),
                      Tab(
                        icon: Icon(Icons.chat_bubble_outline, size: 20),
                        text: 'Chat',
                        height: 60,
                      ),
                      Tab(
                        icon: Icon(Icons.speed, size: 20),
                        text: 'Speed Test',
                        height: 60,
                      ),
                      Tab(
                        icon: Icon(Icons.folder, size: 20),
                        text: 'Files',
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
