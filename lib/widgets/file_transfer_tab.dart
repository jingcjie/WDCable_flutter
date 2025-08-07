import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../l10n/app_localizations.dart';
import '../controllers/wifi_direct_controller.dart';
import '../models/wifi_direct_models.dart';

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
  late AnimationController _downloadAnimationController;
  late Animation<double> _uploadAnimation;
  late Animation<double> _downloadAnimation;

  @override
  void initState() {
    super.initState();
    _uploadAnimationController = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    );
    _downloadAnimationController = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    );
    _uploadAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _uploadAnimationController, curve: Curves.easeInOut),
    );
    _downloadAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _downloadAnimationController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _uploadAnimationController.dispose();
    _downloadAnimationController.dispose();
    super.dispose();
  }

  Future<void> _pickAndSendFile() async {
    if (widget.state.connectionInfo?.isConnected != true) {
      _showSnackBar(AppLocalizations.of(context)!.pleaseConnectToPeerFirst, Colors.orange);
      return;
    }

    try {
      const platform = MethodChannel('wifi_direct_cable');
      final result = await platform.invokeMethod('pickFile');
      
      if (result != null) {
        final Map<String, dynamic> fileInfo = Map<String, dynamic>.from(result);
        final String filePath = fileInfo['path'];
        final String fileName = fileInfo['name'];
        
        _uploadAnimationController.forward();
        await widget.controller.sendFile(filePath, fileName: fileName);
        _showSnackBar(AppLocalizations.of(context)!.fileSent(fileName), Colors.green);
        _uploadAnimationController.reverse();
      }
      // If result is null (no file selected), just return without showing error
    } catch (e) {
      _uploadAnimationController.reverse();
      _showSnackBar(AppLocalizations.of(context)!.failedToSendFile(e.toString()), Colors.red);
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
    final isConnected = widget.state.connectionInfo?.isConnected ?? false;
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
            color: (isConnected ? Colors.green : Colors.grey).withOpacity(0.3),
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
              color: Colors.white.withOpacity(0.2),
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
                  isConnected ? AppLocalizations.of(context)!.connected : AppLocalizations.of(context)!.notConnected,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  isConnected 
                      ? AppLocalizations.of(context)!.readyForFileTransfer
                      : AppLocalizations.of(context)!.connectToStartTransferringFiles,
                  style: TextStyle(
                    color: Colors.white.withOpacity(0.9),
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
                  onPressed: isConnected ? _pickAndSendFile : null,
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
                        color: Colors.white.withOpacity(
                          isConnected ? 1.0 : 0.5,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Text(
                        AppLocalizations.of(context)!.sendFile,
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: Colors.white.withOpacity(
                            isConnected ? 1.0 : 0.5,
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
              color: theme.colorScheme.outline.withOpacity(0.2),
            ),
          ),
          child: Column(
            children: [
              Icon(
                Icons.download,
                size: 32,
                color: theme.colorScheme.primary,
              ),
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
                AppLocalizations.of(context)!.filesWillBeAutomaticallyReceived,
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: 12,
                  color: theme.colorScheme.onSurface.withOpacity(0.7),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildTransferProgressSection(ThemeData theme) {
    final currentTransfer = widget.state.currentFileTransfer;
    
    if (currentTransfer == null) {
      return Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: theme.colorScheme.outline.withOpacity(0.2),
          ),
        ),
        child: Column(
          children: [
            Icon(
              Icons.hourglass_empty,
              size: 32,
              color: theme.colorScheme.onSurface.withOpacity(0.5),
            ),
            const SizedBox(height: 8),
            Text(
              AppLocalizations.of(context)!.noActiveTransfers,
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w500,
                color: theme.colorScheme.onSurface.withOpacity(0.7),
              ),
            ),
          ],
        ),
      );
    }

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: currentTransfer.isCompleted 
              ? Colors.green.withOpacity(0.3)
              : theme.colorScheme.primary.withOpacity(0.3),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                currentTransfer.isCompleted
                    ? Icons.check_circle
                    : (currentTransfer.isUploading ? Icons.upload : Icons.download),
                color: currentTransfer.isCompleted 
                    ? Colors.green 
                    : theme.colorScheme.primary,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  currentTransfer.fileName,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              // Action buttons for completed transfers
              if (currentTransfer.isCompleted) ...<Widget>[
                // Open button for completed transfers with file path
                if (currentTransfer.filePath != null)
                  IconButton(
                    onPressed: () => _openFile(currentTransfer.filePath!),
                    icon: const Icon(Icons.open_in_new),
                    iconSize: 20,
                    color: theme.colorScheme.primary,
                    tooltip: AppLocalizations.of(context)!.openFile,
                    style: IconButton.styleFrom(
                      backgroundColor: theme.colorScheme.primary.withOpacity(0.1),
                      padding: const EdgeInsets.all(8),
                    ),
                  ),
                // Clear button
                IconButton(
                  onPressed: () => widget.controller.clearCurrentTransfer(),
                  icon: const Icon(Icons.clear),
                  iconSize: 20,
                  color: theme.colorScheme.onSurface.withOpacity(0.7),
                  tooltip: AppLocalizations.of(context)!.clear,
                  style: IconButton.styleFrom(
                    backgroundColor: theme.colorScheme.surface,
                    padding: const EdgeInsets.all(8),
                  ),
                ),
              ]
            ],
          ),
          const SizedBox(height: 12),
          LinearProgressIndicator(
            value: currentTransfer.progress,
            backgroundColor: theme.colorScheme.outline.withOpacity(0.2),
            valueColor: AlwaysStoppedAnimation<Color>(
              currentTransfer.isCompleted ? Colors.green : theme.colorScheme.primary,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '${(currentTransfer.progress * 100).toStringAsFixed(1)}%',
                style: TextStyle(
                  fontSize: 12,
                  color: theme.colorScheme.onSurface.withOpacity(0.7),
                ),
              ),
              Text(
                currentTransfer.isCompleted
                    ? (currentTransfer.isUploading ? AppLocalizations.of(context)!.uploadComplete : AppLocalizations.of(context)!.downloadComplete)
                    : (currentTransfer.isUploading ? AppLocalizations.of(context)!.uploading : AppLocalizations.of(context)!.downloading),
                style: TextStyle(
                  fontSize: 12,
                  color: currentTransfer.isCompleted 
                      ? Colors.green 
                      : theme.colorScheme.primary,
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
                color: theme.colorScheme.outline.withOpacity(0.2),
              ),
            ),
            child: Column(
              children: [
                Icon(
                  Icons.history,
                  size: 32,
                  color: theme.colorScheme.onSurface.withOpacity(0.5),
                ),
                const SizedBox(height: 8),
                Text(
                  AppLocalizations.of(context)!.noRecentTransfers,
                  style: TextStyle(
                    fontSize: 14,
                    color: theme.colorScheme.onSurface.withOpacity(0.7),
                  ),
                ),
              ],
            ),
          )
        else
          ...recentFiles.take(5).map((transfer) => _buildFileTransferItem(transfer, theme)),
      ],
    );
  }

  Widget _buildFileTransferItem(FileTransferInfo transfer, ThemeData theme) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: theme.colorScheme.outline.withOpacity(0.2),
        ),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: transfer.isCompleted
                  ? Colors.green.withOpacity(0.1)
                  : Colors.orange.withOpacity(0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(
              transfer.isUploading ? Icons.upload : Icons.download,
              size: 20,
              color: transfer.isCompleted ? Colors.green : Colors.orange,
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
                  '${transfer.isUploading ? AppLocalizations.of(context)!.sent : AppLocalizations.of(context)!.received} • ${_formatFileSize(transfer.fileSize)}',
                  style: TextStyle(
                    fontSize: 12,
                    color: theme.colorScheme.onSurface.withOpacity(0.6),
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
                onPressed: () => _openFile(transfer.filePath!),
                icon: const Icon(Icons.open_in_new),
                iconSize: 20,
                color: theme.colorScheme.primary,
                tooltip: AppLocalizations.of(context)!.openFile,
                style: IconButton.styleFrom(
                  backgroundColor: theme.colorScheme.primary.withOpacity(0.1),
                  padding: const EdgeInsets.all(8),
                ),
              ),
            ),
          const SizedBox(width: 8),
          Icon(
            transfer.isCompleted ? Icons.check_circle : Icons.error,
            size: 20,
            color: transfer.isCompleted ? Colors.green : Colors.red,
          ),
        ],
      ),
    );
  }

  Future<void> _openFile(String filePath) async {
    try {
      const platform = MethodChannel('wifi_direct_cable');
      await platform.invokeMethod('openFile', {'filePath': filePath});
    } catch (e) {
      _showSnackBar(AppLocalizations.of(context)!.failedToOpenFile(e.toString()), Colors.red);
    }
  }

  String _formatFileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }
}