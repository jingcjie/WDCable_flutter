import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../l10n/app_localizations.dart';
import '../controllers/wifi_direct_controller.dart';
import '../models/wifi_direct_models.dart';
import '../wifi_direct_service.dart';

class FileTransferTab extends StatefulWidget {
  final WiFiDirectController controller;
  final WiFiDirectState state;

  const FileTransferTab({
    super.key,
    required this.controller,
    required this.state,
  });

  @override
  State<FileTransferTab> createState() => _FileTransferTabState();
}

class _FileTransferTabState extends State<FileTransferTab>
    with TickerProviderStateMixin {
  late AnimationController _uploadAnimationController;
  late Animation<double> _uploadAnimation;
  StreamSubscription<WiFiDirectEvent>? _transferEventSubscription;
  bool _isPickingFile = false;

  @override
  void initState() {
    super.initState();
    _uploadAnimationController = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    );
    _uploadAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(
        parent: _uploadAnimationController,
        curve: Curves.easeInOut,
      ),
    );
    _transferEventSubscription = widget.controller.eventStream.listen((event) {
      if (!mounted) return;
      switch (event) {
        case FileTransferCompletedEvent completed:
          _showSnackBar(
            completed.isUploading
                ? AppLocalizations.of(context)!.fileSent(completed.fileName)
                : AppLocalizations.of(
                    context,
                  )!.fileReceived(completed.fileName),
            Colors.green,
          );
          break;
        case FileTransferCancelledEvent cancelled:
          _showSnackBar(
            AppLocalizations.of(
              context,
            )!.fileTransferCancelled(cancelled.fileName),
            Colors.grey.shade700,
          );
          break;
        case FileTransferFailedEvent failed:
          _showSnackBar(
            AppLocalizations.of(context)!.fileTransferFailed(
              failed.fileName,
              failed.error ?? AppLocalizations.of(context)!.failed,
            ),
            Colors.red,
          );
          break;
        default:
          break;
      }
    });
  }

  @override
  void dispose() {
    _transferEventSubscription?.cancel();
    _uploadAnimationController.dispose();
    super.dispose();
  }

  Future<void> _pickAndSendFile() async {
    if (_isPickingFile) return;
    if (!widget.state.isSessionReady) {
      _showSnackBar(
        AppLocalizations.of(context)!.pleaseConnectToPeerFirst,
        Colors.orange,
      );
      return;
    }

    try {
      setState(() => _isPickingFile = true);
      const platform = MethodChannel('wifi_direct_cable');
      final result = await platform.invokeMethod('pickFile');

      if (!mounted) return;

      if (result != null) {
        final Map<String, dynamic> fileInfo = Map<String, dynamic>.from(result);
        final String filePath = fileInfo['path'];
        final String fileName = fileInfo['name'];

        _uploadAnimationController.forward();
        await widget.controller.sendFile(filePath, fileName: fileName);

        if (!mounted) return;
        _uploadAnimationController.reverse();
      }
      // If result is null (no file selected), just return without showing error
    } catch (e) {
      if (!mounted) return;

      _uploadAnimationController.reverse();
      _showSnackBar(
        AppLocalizations.of(context)!.failedToSendFile(e.toString()),
        Colors.red,
      );
    } finally {
      if (mounted) {
        setState(() => _isPickingFile = false);
      }
    }
  }

  void _showSnackBar(String message, Color color) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: color,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isConnected = widget.state.isSessionReady;
    final theme = Theme.of(context);

    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Connection Status Card
            _buildConnectionStatusCard(isConnected, theme),
            const SizedBox(height: 20),

            // File Transfer Actions
            _buildFileTransferActions(isConnected, theme),
            const SizedBox(height: 20),

            // Transfer Progress Section
            _buildTransferProgressSection(theme),
            const SizedBox(height: 20),

            // Recent Files Section
            _buildRecentFilesSection(theme),
          ],
        ),
      ),
    );
  }

  Widget _buildConnectionStatusCard(bool isConnected, ThemeData theme) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: isConnected
              ? [Colors.green.shade400, Colors.green.shade600]
              : [Colors.grey.shade400, Colors.grey.shade600],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: (isConnected ? Colors.green : Colors.grey).withValues(
              alpha: 0.3,
            ),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(
              isConnected ? Icons.cloud_done : Icons.cloud_off,
              color: Colors.white,
              size: 28,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  isConnected
                      ? AppLocalizations.of(context)!.connected
                      : AppLocalizations.of(context)!.notConnected,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  isConnected
                      ? AppLocalizations.of(context)!.readyForFileTransfer
                      : AppLocalizations.of(
                          context,
                        )!.connectToStartTransferringFiles,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.9),
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFileTransferActions(bool isConnected, ThemeData theme) {
    return Column(
      children: [
        // Send File Button
        AnimatedBuilder(
          animation: _uploadAnimation,
          builder: (context, child) {
            return Transform.scale(
              scale: 1.0 + (_uploadAnimation.value * 0.05),
              child: Container(
                width: double.infinity,
                height: 80,
                margin: const EdgeInsets.only(bottom: 16),
                child: ElevatedButton(
                  onPressed: isConnected && !_isPickingFile
                      ? _pickAndSendFile
                      : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: theme.colorScheme.primary,
                    foregroundColor: Colors.white,
                    elevation: _uploadAnimation.value * 8,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.upload_file,
                        size: 28,
                        color: Colors.white.withValues(
                          alpha: isConnected ? 1.0 : 0.5,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Text(
                        AppLocalizations.of(context)!.sendFile,
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: Colors.white.withValues(
                            alpha: isConnected ? 1.0 : 0.5,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        ),

        // Receive Files Info
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: theme.colorScheme.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: theme.colorScheme.outline.withValues(alpha: 0.2),
            ),
          ),
          child: Column(
            children: [
              Icon(Icons.download, size: 32, color: theme.colorScheme.primary),
              const SizedBox(height: 8),
              Text(
                AppLocalizations.of(context)!.receiveFiles,
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: theme.colorScheme.onSurface,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                AppLocalizations.of(
                  context,
                )!.filesWillBeAutomaticallyReceived(_receiveDestinationLabel()),
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 12,
                  color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
                ),
              ),
              const SizedBox(height: 12),
              InputDecorator(
                decoration: InputDecoration(
                  labelText: AppLocalizations.of(context)!.saveReceivedFilesTo,
                  border: const OutlineInputBorder(),
                  isDense: true,
                ),
                child: DropdownButtonHideUnderline(
                  child: DropdownButton<String>(
                    value: widget.state.receiveDestination.mode,
                    isExpanded: true,
                    isDense: true,
                    items: [
                      DropdownMenuItem(
                        value: 'app',
                        child: Text(AppLocalizations.of(context)!.appStorage),
                      ),
                      DropdownMenuItem(
                        value: 'downloads',
                        child: Text(
                          AppLocalizations.of(context)!.downloadsFolder,
                        ),
                      ),
                      DropdownMenuItem(
                        value: 'custom',
                        child: Text(
                          widget.state.receiveDestination.mode == 'custom'
                              ? widget.state.receiveDestination.displayName
                              : AppLocalizations.of(
                                  context,
                                )!.chooseCustomFolder,
                        ),
                      ),
                    ],
                    onChanged: (mode) async {
                      if (mode == null) return;
                      final success = mode == 'custom'
                          ? await widget.controller
                                .pickCustomReceiveDestination()
                          : await widget.controller.setReceiveDestination(mode);
                      if (!success && mounted) {
                        _showSnackBar(
                          AppLocalizations.of(
                            context,
                          )!.receiveDestinationFailed,
                          Colors.red,
                        );
                      }
                    },
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildTransferProgressSection(ThemeData theme) {
    final activeTransfers = widget.state.activeFileTransfers.values.toList()
      ..sort((a, b) => a.timestamp.compareTo(b.timestamp));

    if (activeTransfers.isEmpty) {
      return Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: theme.colorScheme.outline.withValues(alpha: 0.2),
          ),
        ),
        child: Column(
          children: [
            Icon(
              Icons.hourglass_empty,
              size: 32,
              color: theme.colorScheme.onSurface.withValues(alpha: 0.5),
            ),
            const SizedBox(height: 8),
            Text(
              AppLocalizations.of(context)!.noActiveTransfers,
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w500,
                color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
              ),
            ),
          ],
        ),
      );
    }

    return Column(
      children: activeTransfers
          .map((transfer) => _buildActiveTransferCard(transfer, theme))
          .toList(),
    );
  }

  Widget _buildActiveTransferCard(FileTransferInfo transfer, ThemeData theme) {
    final hasKnownSize = transfer.fileSize >= 0;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: theme.colorScheme.primary.withValues(alpha: 0.3),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                transfer.isUploading ? Icons.upload : Icons.download,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  transfer.fileName,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if (transfer.canCancel)
                TextButton.icon(
                  onPressed: () =>
                      widget.controller.cancelFileTransfer(transfer.transferId),
                  icon: const Icon(Icons.close, size: 18),
                  label: Text(AppLocalizations.of(context)!.cancel),
                )
              else if (transfer.status == FileTransferStatus.cancelling)
                const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
            ],
          ),
          const SizedBox(height: 12),
          LinearProgressIndicator(
            value: hasKnownSize ? transfer.progress : null,
            backgroundColor: theme.colorScheme.outline.withValues(alpha: 0.2),
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                hasKnownSize
                    ? '${(transfer.progress * 100).toStringAsFixed(1)}%'
                    : _formatFileSize(transfer.bytesTransferred),
                style: TextStyle(
                  fontSize: 12,
                  color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
                ),
              ),
              Text(
                _transferStatusLabel(transfer),
                style: TextStyle(
                  fontSize: 12,
                  color: theme.colorScheme.primary,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildRecentFilesSection(ThemeData theme) {
    final recentFiles = widget.state.recentFileTransfers;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          AppLocalizations.of(context)!.recentTransfers,
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: theme.colorScheme.onSurface,
          ),
        ),
        const SizedBox(height: 12),

        if (recentFiles.isEmpty)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: theme.colorScheme.surface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: theme.colorScheme.outline.withValues(alpha: 0.2),
              ),
            ),
            child: Column(
              children: [
                Icon(
                  Icons.history,
                  size: 32,
                  color: theme.colorScheme.onSurface.withValues(alpha: 0.5),
                ),
                const SizedBox(height: 8),
                Text(
                  AppLocalizations.of(context)!.noRecentTransfers,
                  style: TextStyle(
                    fontSize: 14,
                    color: theme.colorScheme.onSurface.withValues(alpha: 0.7),
                  ),
                ),
              ],
            ),
          )
        else
          ...recentFiles
              .take(5)
              .map((transfer) => _buildFileTransferItem(transfer, theme)),
      ],
    );
  }

  Widget _buildFileTransferItem(FileTransferInfo transfer, ThemeData theme) {
    final statusColor = switch (transfer.status) {
      FileTransferStatus.completed => Colors.green,
      FileTransferStatus.cancelled => Colors.grey,
      FileTransferStatus.failed => Colors.red,
      _ => Colors.orange,
    };
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: theme.colorScheme.outline.withValues(alpha: 0.2),
        ),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: statusColor.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(
              transfer.isUploading ? Icons.upload : Icons.download,
              size: 20,
              color: statusColor,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  transfer.fileName,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w500,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  '${_transferStatusLabel(transfer)} • ${_formatFileSize(transfer.fileSize)}',
                  style: TextStyle(
                    fontSize: 12,
                    color: theme.colorScheme.onSurface.withValues(alpha: 0.6),
                  ),
                ),
              ],
            ),
          ),
          // Show open button for successfully completed files (both sent and received)
          if (transfer.isCompleted && transfer.filePath != null)
            Padding(
              padding: const EdgeInsets.only(left: 8),
              child: IconButton(
                onPressed: () => _openFile(transfer),
                icon: const Icon(Icons.open_in_new),
                iconSize: 20,
                color: theme.colorScheme.primary,
                tooltip: AppLocalizations.of(context)!.openFile,
                style: IconButton.styleFrom(
                  backgroundColor: theme.colorScheme.primary.withValues(
                    alpha: 0.1,
                  ),
                  padding: const EdgeInsets.all(8),
                ),
              ),
            ),
          const SizedBox(width: 8),
          Icon(
            switch (transfer.status) {
              FileTransferStatus.completed => Icons.check_circle,
              FileTransferStatus.cancelled => Icons.cancel_outlined,
              FileTransferStatus.failed => Icons.error,
              _ => Icons.schedule,
            },
            size: 20,
            color: statusColor,
          ),
        ],
      ),
    );
  }

  Future<void> _openFile(FileTransferInfo transfer) async {
    final filePath = transfer.filePath!;
    try {
      const platform = MethodChannel('wifi_direct_cable');
      await platform.invokeMethod('openFile', {'filePath': filePath});
    } on PlatformException catch (e) {
      if (!mounted) return;

      if (e.code == 'NO_APP_FOUND') {
        _showSnackBar(
          AppLocalizations.of(context)!.noSupportedAppToOpenFile(
            transfer.fileName,
            transfer.savedLocation ?? _fallbackSavedLocation(filePath),
          ),
          Theme.of(context).colorScheme.secondary,
        );
        return;
      }

      _showSnackBar(
        AppLocalizations.of(context)!.failedToOpenFile(e.toString()),
        Colors.red,
      );
    } catch (e) {
      if (!mounted) return;
      _showSnackBar(
        AppLocalizations.of(context)!.failedToOpenFile(e.toString()),
        Colors.red,
      );
    }
  }

  String _fallbackSavedLocation(String filePath) {
    if (filePath.startsWith('content://')) {
      return _receiveDestinationLabel();
    }
    final normalized = filePath.replaceAll('\\', '/');
    final separator = normalized.lastIndexOf('/');
    return separator > 0 ? normalized.substring(0, separator) : filePath;
  }

  String _formatFileSize(int bytes) {
    if (bytes < 0) return 'Unknown size';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }

  String _transferStatusLabel(FileTransferInfo transfer) {
    final localizations = AppLocalizations.of(context)!;
    return switch (transfer.status) {
      FileTransferStatus.preparing => localizations.preparing,
      FileTransferStatus.queued => localizations.queued,
      FileTransferStatus.transferring =>
        transfer.isUploading
            ? localizations.uploading
            : localizations.downloading,
      FileTransferStatus.cancelling => localizations.cancelling,
      FileTransferStatus.completed =>
        transfer.isUploading ? localizations.sent : localizations.received,
      FileTransferStatus.cancelled => localizations.cancelled,
      FileTransferStatus.failed => localizations.failed,
    };
  }

  String _receiveDestinationLabel() {
    final localizations = AppLocalizations.of(context)!;
    return switch (widget.state.receiveDestination.mode) {
      'downloads' => localizations.downloadsFolder,
      'custom' => widget.state.receiveDestination.displayName,
      _ => localizations.appStorage,
    };
  }
}
