import 'package:flutter/material.dart';

import '../controllers/wifi_direct_controller.dart';
import '../l10n/app_localizations.dart';
import '../models/wifi_direct_models.dart';

class AudioTab extends StatefulWidget {
  final WiFiDirectController controller;
  final WiFiDirectState state;

  const AudioTab({super.key, required this.controller, required this.state});

  @override
  State<AudioTab> createState() => _AudioTabState();
}

class _AudioTabState extends State<AudioTab> {
  String _mode = 'receive';

  @override
  void initState() {
    super.initState();
    _mode = widget.state.audioMode == 'send' ? 'send' : 'receive';
    WidgetsBinding.instance.addPostFrameCallback((_) {
      widget.controller.loadAudioSupport();
    });
  }

  @override
  void didUpdateWidget(covariant AudioTab oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.state.audioMode != widget.state.audioMode &&
        widget.state.audioMode != 'idle') {
      _mode = widget.state.audioMode;
    }
    if (!oldWidget.state.isSessionReady && widget.state.isSessionReady) {
      widget.controller.loadAudioSupport();
    }
  }

  @override
  Widget build(BuildContext context) {
    final isReady = widget.state.isSessionReady;
    final peerSupported = widget.state.peerSupportsAudio;
    final support = widget.state.audioSupport;
    final isActive = widget.state.isAudioActive;
    final canStart =
        isReady &&
        peerSupported &&
        !isActive &&
        (_mode == 'send' ? support.canSend : support.canReceive);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildStatus(context, isReady, peerSupported, support),
          const SizedBox(height: 16),
          _buildControls(context, canStart, isActive),
          const SizedBox(height: 16),
          _buildStats(context),
        ],
      ),
    );
  }

  Widget _buildStatus(
    BuildContext context,
    bool isReady,
    bool peerSupported,
    AudioSupportInfo support,
  ) {
    final color = widget.state.isAudioStreaming
        ? Colors.green
        : isReady && peerSupported && support.audioLinkSupported
        ? Theme.of(context).colorScheme.primary
        : Colors.orange;
    final message =
        widget.state.audioLastError ??
        (!isReady
            ? AppLocalizations.of(context)!.audioConnectToPeerFirst
            : !peerSupported
            ? AppLocalizations.of(context)!.audioPeerUnsupported
            : support.message.isEmpty
            ? AppLocalizations.of(context)!.audioReady
            : support.message);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withValues(alpha: 0.5)),
      ),
      child: Row(
        children: [
          Icon(
            widget.state.isAudioStreaming ? Icons.graphic_eq : Icons.mic,
            color: color,
            size: 26,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  AppLocalizations.of(context)!.audioLink,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  message,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: color,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildControls(BuildContext context, bool canStart, bool isActive) {
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              AppLocalizations.of(context)!.audioMode,
              style: Theme.of(
                context,
              ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 8),
            SegmentedButton<String>(
              segments: [
                ButtonSegment(
                  value: 'receive',
                  icon: const Icon(Icons.hearing),
                  label: Text(AppLocalizations.of(context)!.audioReceive),
                ),
                ButtonSegment(
                  value: 'send',
                  icon: const Icon(Icons.mic),
                  label: Text(AppLocalizations.of(context)!.audioSend),
                ),
              ],
              selected: {_mode},
              onSelectionChanged: isActive
                  ? null
                  : (selection) {
                      setState(() {
                        _mode = selection.first;
                      });
                    },
            ),
            const SizedBox(height: 16),
            _buildOptionRow(
              context,
              icon: Icons.input,
              title: AppLocalizations.of(context)!.audioSource,
              value: AppLocalizations.of(context)!.audioMicrophone,
              trailing: Text(
                AppLocalizations.of(context)!.audioDeviceAudioUnavailable,
              ),
            ),
            const SizedBox(height: 12),
            _buildOptionRow(
              context,
              icon: Icons.settings_voice,
              title: AppLocalizations.of(context)!.audioEncoding,
              value: AppLocalizations.of(context)!.audioOpus24Kbps,
              trailing: Text(AppLocalizations.of(context)!.audioOnlyOption),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: isActive
                    ? widget.controller.stopAudio
                    : canStart
                    ? () => widget.controller.startAudio(mode: _mode)
                    : null,
                icon: Icon(isActive ? Icons.stop : Icons.play_arrow),
                label: Text(
                  isActive
                      ? AppLocalizations.of(context)!.audioStop
                      : AppLocalizations.of(context)!.audioStart,
                ),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  backgroundColor: isActive ? Colors.red : null,
                  foregroundColor: isActive ? Colors.white : null,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildOptionRow(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String value,
    required Widget trailing,
  }) {
    return Row(
      children: [
        Icon(icon, size: 20, color: Theme.of(context).colorScheme.primary),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: Theme.of(
                  context,
                ).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 2),
              Text(value, style: Theme.of(context).textTheme.bodySmall),
            ],
          ),
        ),
        DefaultTextStyle.merge(
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: Theme.of(
              context,
            ).colorScheme.onSurface.withValues(alpha: 0.55),
          ),
          child: trailing,
        ),
      ],
    );
  }

  Widget _buildStats(BuildContext context) {
    final stats = widget.state.audioStats;
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
                  AppLocalizations.of(context)!.audioLiveStats,
                  style: Theme.of(
                    context,
                  ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                ),
              ],
            ),
            const SizedBox(height: 12),
            GridView.count(
              crossAxisCount: 2,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              childAspectRatio: 2.7,
              mainAxisSpacing: 8,
              crossAxisSpacing: 8,
              children: [
                _buildStatTile(
                  AppLocalizations.of(context)!.audioState,
                  _formatState(widget.state.audioState),
                ),
                _buildStatTile(
                  AppLocalizations.of(context)!.audioBitrate,
                  _formatBitrate(stats.bitrateBps),
                ),
                _buildStatTile(
                  AppLocalizations.of(context)!.audioBuffer,
                  '${stats.bufferLevelMs} ms',
                ),
                _buildStatTile(
                  AppLocalizations.of(context)!.audioDropped,
                  stats.droppedFrames.toString(),
                ),
                _buildStatTile(
                  AppLocalizations.of(context)!.audioFrames,
                  '${stats.framesSent}/${stats.framesReceived}',
                ),
                _buildStatTile(
                  AppLocalizations.of(context)!.audioLatency,
                  stats.latencyMs >= 0
                      ? '${stats.latencyMs} ms'
                      : AppLocalizations.of(context)!.notAvailableShort,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatTile(String label, String value) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            label,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 2),
          Text(
            value,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(
              context,
            ).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
        ],
      ),
    );
  }

  String _formatState(String value) {
    switch (value) {
      case 'receiveReady':
        return AppLocalizations.of(context)!.audioStateReceiveReady;
      case 'offerSent':
        return AppLocalizations.of(context)!.audioStateOfferSent;
      case 'connecting':
        return AppLocalizations.of(context)!.audioStateConnecting;
      case 'streaming':
        return AppLocalizations.of(context)!.audioStateStreaming;
      default:
        return AppLocalizations.of(context)!.audioStateIdle;
    }
  }

  String _formatBitrate(int bitrateBps) {
    final unit = AppLocalizations.of(context)!.kbpsUnit;
    if (bitrateBps <= 0) return '0 $unit';
    return '${(bitrateBps / 1000).toStringAsFixed(1)} $unit';
  }
}
