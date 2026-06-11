import 'package:flutter/material.dart';
import '../models/wifi_direct_models.dart';
import '../controllers/wifi_direct_controller.dart';
import 'package:wifi_direct_cable/l10n/app_localizations.dart';

class ConnectionTab extends StatelessWidget {
  final WiFiDirectController controller;
  final WiFiDirectState state;

  const ConnectionTab({
    super.key,
    required this.controller,
    required this.state,
  });

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        children: [
          // WiFi P2P Status
          _buildWifiP2pStatus(context),
          // Connection Status
          _buildConnectionStatus(context),
          // Control Buttons
          _buildControlButtons(context),
          // Peers List
          SizedBox(height: 300, child: _buildPeersList(context)),
          // Logs Section
          _buildLogsSection(context),
        ],
      ),
    );
  }

  Widget _buildWifiP2pStatus(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: state.isWifiP2pEnabled
            ? Colors.green.withValues(alpha: 0.1)
            : Colors.red.withValues(alpha: 0.1),
        border: Border(
          bottom: BorderSide(
            color: Theme.of(context).colorScheme.outline.withValues(alpha: 0.2),
            width: 1,
          ),
        ),
      ),
      child: Row(
        children: [
          Icon(
            state.isWifiP2pEnabled ? Icons.wifi : Icons.wifi_off,
            color: state.isWifiP2pEnabled ? Colors.green : Colors.red,
            size: 24,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  AppLocalizations.of(context)!.wifiP2pDriver,
                  style: Theme.of(
                    context,
                  ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                ),
                Text(
                  state.isWifiP2pEnabled
                      ? AppLocalizations.of(context)!.readyForConnections
                      : AppLocalizations.of(context)!.disabledEnableWifi,
                  style: TextStyle(
                    color: state.isWifiP2pEnabled ? Colors.green : Colors.red,
                    fontSize: 12,
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

  Widget _buildConnectionStatus(BuildContext context) {
    final isConnected = state.connectionInfo?.isConnected == true;
    final statusText = isConnected
        ? AppLocalizations.of(context)!.connected
        : state.isConnecting
        ? 'Connecting...'
        : AppLocalizations.of(context)!.disconnected;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            Theme.of(context).colorScheme.primaryContainer,
            Theme.of(context).colorScheme.secondaryContainer,
          ],
        ),
      ),
      child: Row(
        children: [
          Icon(
            isConnected ? Icons.link : Icons.link_off,
            color: isConnected ? Colors.green : Colors.red,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              '${AppLocalizations.of(context)!.connectionStatus}: $statusText',
              style: Theme.of(
                context,
              ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildControlButtons(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: state.isDiscovering
                      ? null
                      : controller.discoverPeers,
                  icon: state.isDiscovering
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.search),
                  label: Text(
                    state.isDiscovering
                        ? AppLocalizations.of(context)!.scanning
                        : AppLocalizations.of(context)!.scanForDevices,
                  ),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: controller.stopDiscovery,
                  icon: const Icon(Icons.stop),
                  label: Text(AppLocalizations.of(context)!.stopScan),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: controller.logDeviceSettings,
                  icon: const Icon(Icons.info),
                  label: Text(AppLocalizations.of(context)!.deviceInfo),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: controller.resetWifiDirectSettings,
                  icon: const Icon(Icons.refresh),
                  label: Text(AppLocalizations.of(context)!.resetWifiDirect),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    backgroundColor: Colors.orange,
                    foregroundColor: Colors.white,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildPeersList(BuildContext context) {
    if (state.peers.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.devices_other, size: 64, color: Colors.grey),
            const SizedBox(height: 16),
            Text(
              AppLocalizations.of(context)!.noDevicesFound,
              style: const TextStyle(fontSize: 16, color: Colors.grey),
            ),
            const SizedBox(height: 8),
            Text(
              AppLocalizations.of(context)!.tapScanForDevices,
              style: const TextStyle(fontSize: 12, color: Colors.grey),
            ),
          ],
        ),
      );
    }

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      decoration: BoxDecoration(
        border: Border.all(
          color: Theme.of(context).colorScheme.outline.withValues(alpha: 0.2),
        ),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              borderRadius: const BorderRadius.only(
                topLeft: Radius.circular(8),
                topRight: Radius.circular(8),
              ),
            ),
            child: Text(
              AppLocalizations.of(
                context,
              )!.availableDevices(state.peers.length),
              style: Theme.of(
                context,
              ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
            ),
          ),
          Expanded(
            child: ListView.separated(
              itemCount: state.peers.length,
              separatorBuilder: (context, index) => Divider(
                height: 1,
                color: Theme.of(
                  context,
                ).colorScheme.outline.withValues(alpha: 0.2),
              ),
              itemBuilder: (context, index) {
                final peer = state.peers[index];
                final action = _getPeerAction(context, peer);
                return ListTile(
                  leading: CircleAvatar(
                    backgroundColor: _getStatusColor(peer.status),
                    child: Icon(
                      Icons.device_hub,
                      color: Colors.white,
                      size: 20,
                    ),
                  ),
                  title: Text(
                    peer.deviceName,
                    style: const TextStyle(fontWeight: FontWeight.w500),
                  ),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        peer.deviceAddress,
                        style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                      ),
                      const SizedBox(height: 2),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 2,
                        ),
                        decoration: BoxDecoration(
                          color: _getStatusColor(
                            peer.status,
                          ).withValues(alpha: 0.2),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          peer.statusText,
                          style: TextStyle(
                            fontSize: 10,
                            color: _getStatusColor(peer.status),
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ],
                  ),
                  trailing: ElevatedButton(
                    onPressed: action.enabled
                        ? () => controller.connectToPeer(peer)
                        : null,
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 8,
                      ),
                      minimumSize: Size.zero,
                    ),
                    child: Text(
                      action.label,
                      style: const TextStyle(fontSize: 12),
                    ),
                  ),
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 4,
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  _PeerAction _getPeerAction(BuildContext context, WiFiDirectDevice peer) {
    final isPendingPeer =
        state.isConnecting && state.pendingPeerAddress == peer.deviceAddress;
    final isConnectedPeer = peer.status == 0;
    final isInvitedPeer = peer.status == 1;

    if (isConnectedPeer) {
      return _PeerAction(AppLocalizations.of(context)!.connected, false);
    }

    if (isPendingPeer) {
      return const _PeerAction('Connecting...', false);
    }

    if (isInvitedPeer) {
      return const _PeerAction('Invited', false);
    }

    if (state.isConnecting || state.connectionInfo?.isConnected == true) {
      return _PeerAction(AppLocalizations.of(context)!.connect, false);
    }

    return _PeerAction(AppLocalizations.of(context)!.connect, true);
  }

  Widget _buildLogsSection(BuildContext context) {
    return Container(
      height: 200,
      margin: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        border: Border.all(
          color: Theme.of(context).colorScheme.outline.withValues(alpha: 0.2),
        ),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              borderRadius: const BorderRadius.only(
                topLeft: Radius.circular(8),
                topRight: Radius.circular(8),
              ),
            ),
            child: Row(
              children: [
                const Icon(Icons.terminal, size: 16),
                const SizedBox(width: 8),
                Text(
                  AppLocalizations.of(context)!.systemLogs,
                  style: Theme.of(
                    context,
                  ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                ),
              ],
            ),
          ),
          Expanded(
            child: state.logs.isEmpty
                ? Center(
                    child: Text(
                      AppLocalizations.of(context)!.noLogsYet,
                      style: TextStyle(color: Colors.grey),
                    ),
                  )
                : SingleChildScrollView(
                    padding: const EdgeInsets.all(8),
                    child: SelectableText(
                      state.logs.join('\n'),
                      style: const TextStyle(
                        fontSize: 11,
                        fontFamily: 'monospace',
                      ),
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Color _getStatusColor(int status) {
    switch (status) {
      case 0: // Connected
        return Colors.green;
      case 1: // Invited
        return Colors.orange;
      case 2: // Failed
        return Colors.red;
      case 3: // Available
        return Colors.blue;
      case 4: // Unavailable
        return Colors.grey;
      default:
        return Colors.grey;
    }
  }
}

class _PeerAction {
  final String label;
  final bool enabled;

  const _PeerAction(this.label, this.enabled);
}
