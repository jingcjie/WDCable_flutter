import 'package:flutter/material.dart';
import '../l10n/app_localizations.dart';
import '../models/wifi_direct_models.dart';
import '../controllers/wifi_direct_controller.dart';
import '../wifi_direct_service.dart';

class SpeedTestTab extends StatefulWidget {
  final WiFiDirectController controller;
  final WiFiDirectState state;

  const SpeedTestTab({
    super.key,
    required this.controller,
    required this.state,
  });

  @override
  State<SpeedTestTab> createState() => _SpeedTestTabState();
}

class _SpeedTestTabState extends State<SpeedTestTab>
    with TickerProviderStateMixin {
  late AnimationController _animationController;
  late Animation<double> _animation;
  bool _isTestRunning = false;
  
  // Progress tracking
  double _downloadProgress = 0.0;
  double _uploadProgress = 0.0;
  double _currentDownloadSpeed = 0.0;
  double _currentUploadSpeed = 0.0;
  String _currentTestPhase = '';

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(
      duration: const Duration(seconds: 2),
      vsync: this,
    );
    _animation = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _animationController,
      curve: Curves.easeInOut,
    ));
    
    // Listen to speed test progress events
    _setupProgressListener();
  }
  
  void _setupProgressListener() {
    widget.controller.eventStream.listen((event) {
      if (mounted) {
        if (event is SpeedTestReceiveProgressEvent) {
          setState(() {
            _downloadProgress = event.progress;
            // Convert Mbps to MB/s for display consistency
            _currentDownloadSpeed = event.speedMbps / 8.0;
            _currentTestPhase = 'Download Test';
          });
        } else if (event is SpeedTestSendProgressEvent) {
          setState(() {
            _uploadProgress = event.progress;
            // Convert Mbps to MB/s for display consistency
            _currentUploadSpeed = event.speedMbps / 8.0;
            _currentTestPhase = 'Upload Test';
          });
        }
      }
    });
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }
  
  Widget _buildProgressIndicators(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: Theme.of(context).colorScheme.outline.withOpacity(0.2),
        ),
      ),
      child: Column(
        children: [
          // Download Progress
          _buildProgressItem(
            context,
            'Download',
            _downloadProgress,
            _currentDownloadSpeed,
            Icons.download,
            Colors.blue,
          ),
          const SizedBox(height: 16),
          // Upload Progress
          _buildProgressItem(
            context,
            'Upload',
            _uploadProgress,
            _currentUploadSpeed,
            Icons.upload,
            Colors.green,
          ),
        ],
      ),
    );
  }
  
  Widget _buildProgressItem(
    BuildContext context,
    String label,
    double progress,
    double speed,
    IconData icon,
    Color color,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(icon, size: 16, color: color),
            const SizedBox(width: 8),
            Text(
              label,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w500,
              ),
            ),
            const Spacer(),
            Text(
              '${(progress * 100).toStringAsFixed(1)}%',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: color,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        LinearProgressIndicator(
          value: progress,
          backgroundColor: color.withOpacity(0.2),
          valueColor: AlwaysStoppedAnimation<Color>(color),
          minHeight: 6,
        ),
        const SizedBox(height: 4),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Speed: ${speed.toStringAsFixed(2)} MB/s',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Theme.of(context).colorScheme.onSurface.withOpacity(0.7),
              ),
            ),
            if (progress > 0)
              Text(
                progress >= 1.0 ? AppLocalizations.of(context)!.complete : AppLocalizations.of(context)!.inProgress,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: progress >= 1.0 ? Colors.green : color,
                  fontWeight: FontWeight.w500,
                ),
              ),
          ],
        ),
      ],
    );
  }

  void _startSpeedTest() async {
    if (!_isTestRunning && widget.state.connectionInfo?.isConnected == true) {
      setState(() {
        _isTestRunning = true;
        _downloadProgress = 0.0;
        _uploadProgress = 0.0;
        _currentDownloadSpeed = 0.0;
        _currentUploadSpeed = 0.0;
        _currentTestPhase = 'Initializing...';
      });
      _animationController.repeat();
      
      await widget.controller.startSpeedTest(20);
      
      setState(() {
        _isTestRunning = false;
        _currentTestPhase = 'Completed';
      });
      _animationController.stop();
      _animationController.reset();
    }
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Connection Status
          _buildConnectionStatus(context),
          const SizedBox(height: 24),
          // Speed Test Controls
          Center(child: _buildSpeedTestControls(context)),
          const SizedBox(height: 24),
          // Current Test Results
          _buildCurrentResults(context),
          const SizedBox(height: 24),
          // Historical Results
          _buildHistoricalResults(context),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildConnectionStatus(BuildContext context) {
    final isConnected = widget.state.connectionInfo?.isConnected == true;
    
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isConnected
            ? Colors.green.withOpacity(0.1)
            : Colors.red.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isConnected ? Colors.green : Colors.red,
          width: 1,
        ),
      ),
      child: Row(
        children: [
          Icon(
            isConnected ? Icons.speed : Icons.speed_outlined,
            color: isConnected ? Colors.green : Colors.red,
            size: 24,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  AppLocalizations.of(context)!.speedTestStatus,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  isConnected
                      ? AppLocalizations.of(context)!.readyToTestConnectionSpeed
                      : AppLocalizations.of(context)!.connectToPeerToTestSpeed,
                  style: TextStyle(
                    color: isConnected ? Colors.green : Colors.red,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSpeedTestControls(BuildContext context) {
    final isConnected = widget.state.connectionInfo?.isConnected == true;
    
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            // Speed Test Button
            AnimatedBuilder(
              animation: _animation,
              builder: (context, child) {
                return Container(
                  width: 120,
                  height: 120,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: RadialGradient(
                      colors: [
                        Theme.of(context).colorScheme.primary.withOpacity(0.8),
                        Theme.of(context).colorScheme.primary,
                      ],
                    ),
                    boxShadow: _isTestRunning
                        ? [
                            BoxShadow(
                              color: Theme.of(context)
                                  .colorScheme
                                  .primary
                                  .withOpacity(0.3 * _animation.value),
                              blurRadius: 20 * _animation.value,
                              spreadRadius: 10 * _animation.value,
                            ),
                          ]
                        : [],
                  ),
                  child: Material(
                    color: Colors.transparent,
                    child: InkWell(
                      borderRadius: BorderRadius.circular(60),
                      onTap: isConnected && !_isTestRunning ? _startSpeedTest : null,
                      child: Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              _isTestRunning ? Icons.hourglass_empty : Icons.speed,
                              color: Colors.white,
                              size: 32,
                            ),
                            const SizedBox(height: 4),
                            Text(
                              _isTestRunning ? AppLocalizations.of(context)!.testing : AppLocalizations.of(context)!.start,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 12,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
            const SizedBox(height: 16),
            Text(
              _isTestRunning
                  ? _currentTestPhase
                  : isConnected
                      ? AppLocalizations.of(context)!.tapToStartSpeedTest
                      : AppLocalizations.of(context)!.connectToPeerFirst,
              style: TextStyle(
                color: isConnected ? null : Colors.grey,
                fontSize: 14,
              ),
              textAlign: TextAlign.center,
            ),
            
            // Progress indicators when test is running
            if (_isTestRunning) ...[
              const SizedBox(height: 20),
              _buildProgressIndicators(context),
            ]
          ],
        ),
      ),
    );
  }

  Widget _buildCurrentResults(BuildContext context) {
    final lastResult = widget.state.speedTestResults.isNotEmpty
        ? widget.state.speedTestResults.last
        : null;
    
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.analytics, size: 20),
                const SizedBox(width: 8),
                Text(
                  AppLocalizations.of(context)!.latestResults,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (lastResult != null) ...[
              Row(
                children: [
                  Expanded(
                    child: _buildSpeedCard(
                      context,
                      AppLocalizations.of(context)!.download,
                      lastResult.downloadSpeed,
                      Icons.download,
                      Colors.blue,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildSpeedCard(
                      context,
                      AppLocalizations.of(context)!.upload,
                      lastResult.uploadSpeed,
                      Icons.upload,
                      Colors.green,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              // Latency card
              const SizedBox(height: 12),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surfaceVariant,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Column(
                  children: [
                    Text(
                      AppLocalizations.of(context)!.testCompletedAt,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[600],
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _formatDateTime(lastResult.timestamp),
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ),
              ),
            ] else ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(32),
                child: Column(
                  children: [
                    Icon(
                      Icons.speed_outlined,
                      size: 48,
                      color: Colors.grey[400],
                    ),
                    const SizedBox(height: 16),
                    Text(
                      AppLocalizations.of(context)!.noTestResultsYet,
                      style: TextStyle(
                        fontSize: 16,
                        color: Colors.grey[600],
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      AppLocalizations.of(context)!.runSpeedTestToSeeResults,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[500],
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildSpeedCard(
    BuildContext context,
    String label,
    double speed,
    IconData icon,
    Color color,
  ) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: color.withOpacity(0.3),
          width: 1,
        ),
      ),
      child: Column(
        children: [
          Icon(
            icon,
            color: color,
            size: 24,
          ),
          const SizedBox(height: 8),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: color,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            '${speed.toStringAsFixed(2)} MB/s',
            style: TextStyle(
              fontSize: 16,
              color: color,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHistoricalResults(BuildContext context) {
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.history, size: 20),
                const SizedBox(width: 8),
                Text(
                  AppLocalizations.of(context)!.testHistory,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const Spacer(),
                Text(
                  AppLocalizations.of(context)!.tests(widget.state.speedTestResults.length),
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (widget.state.speedTestResults.isEmpty) ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(24),
                child: Column(
                  children: [
                    Icon(
                      Icons.history_outlined,
                      size: 32,
                      color: Colors.grey[400],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      AppLocalizations.of(context)!.noTestHistory,
                      style: TextStyle(
                        color: Colors.grey[600],
                      ),
                    ),
                  ],
                ),
              ),
            ] else ...[
              SizedBox(
                height: 200,
                child: ListView.builder(
                  itemCount: widget.state.speedTestResults.length,
                  itemBuilder: (context, index) {
                    final result = widget.state.speedTestResults[
                        widget.state.speedTestResults.length - 1 - index];
                    return ListTile(
                      leading: CircleAvatar(
                        backgroundColor:
                            Theme.of(context).colorScheme.primary.withOpacity(0.1),
                        child: Icon(
                          Icons.speed,
                          color: Theme.of(context).colorScheme.primary,
                          size: 16,
                        ),
                      ),
                      title: Text(
                        '↓ ${result.downloadSpeed.toStringAsFixed(1)} MB/s  ↑ ${result.uploadSpeed.toStringAsFixed(1)} MB/s',
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      subtitle: Text(
                        _formatDateTime(result.timestamp),
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.grey[600],
                        ),
                      ),
                      dense: true,
                    );
                  },
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _formatDateTime(DateTime dateTime) {
    final now = DateTime.now();
    final difference = now.difference(dateTime);
    
    if (difference.inDays > 0) {
      return difference.inDays == 1 
          ? AppLocalizations.of(context)!.dayAgo(difference.inDays)
                : AppLocalizations.of(context)!.daysAgoLong(difference.inDays);
    } else if (difference.inHours > 0) {
      return difference.inHours == 1
          ? AppLocalizations.of(context)!.hourAgo(difference.inHours)
                : AppLocalizations.of(context)!.hoursAgoLong(difference.inHours);
    } else if (difference.inMinutes > 0) {
      return difference.inMinutes == 1
          ? AppLocalizations.of(context)!.minuteAgo(difference.inMinutes)
                : AppLocalizations.of(context)!.minutesAgoLong(difference.inMinutes);
    } else {
      return AppLocalizations.of(context)!.justNow;
    }
  }
}