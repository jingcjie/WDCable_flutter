import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../l10n/app_localizations.dart';
import '../controllers/wifi_direct_controller.dart';
import '../models/wifi_direct_models.dart';
import '../theme/theme_provider.dart';
import '../providers/language_provider.dart';

class SettingsTab extends StatefulWidget {
  final WiFiDirectController controller;
  final WiFiDirectState state;

  const SettingsTab({
    super.key,
    required this.controller,
    required this.state,
  });

  @override
  State<SettingsTab> createState() => _SettingsTabState();
}

class _SettingsTabState extends State<SettingsTab> {
  double _transferTimeout = 30.0;
  String _selectedLanguage = 'Follow System';

  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  Theme.of(context).colorScheme.primary,
                  Theme.of(context).colorScheme.primary.withOpacity(0.8),
                ],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(16),
              boxShadow: [
                BoxShadow(
                  color: Theme.of(context).colorScheme.primary.withOpacity(0.3),
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
                  child: const Icon(
                    Icons.settings,
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
                        AppLocalizations.of(context)!.settingsTitle,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 24,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        AppLocalizations.of(context)!.settingsSubtitle,
                        style: const TextStyle(
                          color: Colors.white70,
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          // Transfer Settings Section
          _buildSection(
            AppLocalizations.of(context)!.transferSettings,
            Icons.swap_horiz,
            [
              _buildSliderSetting(
                AppLocalizations.of(context)!.transferTimeout,
                AppLocalizations.of(context)!.timeoutForFileTransfers,
                Icons.timer,
                _transferTimeout,
                5.0,
                120.0,
                (value) => setState(() => _transferTimeout = value),
              ),
            ],
          ),
          const SizedBox(height: 20),

          // App Settings Section
          _buildSection(
            AppLocalizations.of(context)!.appSettings,
            Icons.app_settings_alt,
            [
              _buildLanguageSetting(),
              Consumer<ThemeProvider>(
                builder: (context, themeProvider, child) {
                  return _buildSwitchSetting(
                    AppLocalizations.of(context)!.darkMode,
                    AppLocalizations.of(context)!.useDarkTheme,
                    Icons.dark_mode,
                    themeProvider.isDarkMode,
                    (value) {
                      if (value) {
                        themeProvider.setThemeMode(ThemeMode.dark);
                      } else {
                        themeProvider.setThemeMode(ThemeMode.light);
                      }
                    },
                  );
                },
              ),
            ],
          ),
          const SizedBox(height: 20),

          // GitHub Repositories Section
          _buildSection(
            AppLocalizations.of(context)!.githubRepositories,
            Icons.code,
            [
              _buildActionTile(
                AppLocalizations.of(context)!.flutterAppRepository,
                AppLocalizations.of(context)!.flutterAppDescription,
                Icons.phone_android,
                () => _copyToClipboard('https://github.com/jingcjie/WDCable_flutter'),
              ),
              _buildActionTile(
                AppLocalizations.of(context)!.windowsAppRepository,
                AppLocalizations.of(context)!.windowsAppDescription,
                Icons.desktop_windows,
                () => _copyToClipboard('https://github.com/jingcjie/WDCableWUI'),
              ),
            ],
          ),
          const SizedBox(height: 20),

          // About Section
          _buildSection(
            AppLocalizations.of(context)!.about,
            Icons.info,
            [
              _buildInfoTile(
                AppLocalizations.of(context)!.version,
                '1.0.1',
                Icons.info_outline,
              ),
              _buildActionTile(
                AppLocalizations.of(context)!.privacyPolicy,
                AppLocalizations.of(context)!.viewOurPrivacyPolicy,
                Icons.privacy_tip,
                () => _showPrivacyPolicy(),
              ),
            ],
          ),
          const SizedBox(height: 40),
        ],
      ),
    );
  }

  Widget _buildSection(String title, IconData icon, List<Widget> children) {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).cardTheme.color,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Theme.of(context).shadowColor.withOpacity(0.05),
            blurRadius: 10,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Icon(
                  icon,
                  color: Theme.of(context).colorScheme.primary,
                  size: 24,
                ),
                const SizedBox(width: 12),
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
          ...children,
        ],
      ),
    );
  }

  Widget _buildLanguageSetting() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                Icons.language,
                color: Colors.grey[600],
                size: 20,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      AppLocalizations.of(context)!.language,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      AppLocalizations.of(context)!.chooseYourPreferredLanguage,
                      style: const TextStyle(
                        fontSize: 12,
                        color: Colors.grey,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            decoration: BoxDecoration(
              border: Border.all(color: Colors.grey[300]!),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Consumer<LanguageProvider>(
              builder: (context, languageProvider, child) {
                String currentValue = AppLocalizations.of(context)!.followSystem;
                if (languageProvider.locale?.languageCode == 'en') {
                  currentValue = AppLocalizations.of(context)!.english;
                } else if (languageProvider.locale?.languageCode == 'zh') {
                  currentValue = AppLocalizations.of(context)!.chinese;
                }
                
                return DropdownButtonHideUnderline(
                  child: DropdownButton<String>(
                    value: currentValue,
                    isExpanded: true,
                    items: [
                      DropdownMenuItem(
                        value: AppLocalizations.of(context)!.followSystem,
                        child: Text(AppLocalizations.of(context)!.followSystem),
                      ),
                      DropdownMenuItem(
                        value: AppLocalizations.of(context)!.english,
                        child: Text(AppLocalizations.of(context)!.english),
                      ),
                      DropdownMenuItem(
                        value: AppLocalizations.of(context)!.chinese,
                        child: Text(AppLocalizations.of(context)!.chinese),
                      ),
                    ],
                    onChanged: (value) {
                      if (value != null) {
                        if (value == AppLocalizations.of(context)!.followSystem) {
                          languageProvider.clearLanguage();
                        } else if (value == AppLocalizations.of(context)!.english) {
                          languageProvider.setLanguage(const Locale('en'));
                        } else if (value == AppLocalizations.of(context)!.chinese) {
                          languageProvider.setLanguage(const Locale('zh'));
                        }
                      }
                    },
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSwitchSetting(
    String title,
    String subtitle,
    IconData icon,
    bool value,
    ValueChanged<bool> onChanged, {
    bool enabled = true,
  }) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Row(
        children: [
          Icon(
            icon,
            color: enabled ? Colors.grey[600] : Colors.grey[400],
            size: 20,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w500,
                    color: enabled ? Colors.black : Colors.grey[500],
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
          ),
          Switch(
            value: value,
            onChanged: enabled ? onChanged : null,
            activeColor: Theme.of(context).colorScheme.primary,
          ),
        ],
      ),
    );
  }

  Widget _buildSliderSetting(
    String title,
    String subtitle,
    IconData icon,
    double value,
    double min,
    double max,
    ValueChanged<double> onChanged,
  ) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                icon,
                color: Colors.grey[600],
                size: 20,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[600],
                      ),
                    ),
                  ],
                ),
              ),
              Text(
                '${value.round()}${AppLocalizations.of(context)!.timeoutUnit}',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: Theme.of(context).colorScheme.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: Theme.of(context).colorScheme.primary,
              inactiveTrackColor: Colors.grey[300],
              thumbColor: Theme.of(context).colorScheme.primary,
              overlayColor: Theme.of(context).colorScheme.primary.withOpacity(0.2),
            ),
            child: Slider(
              value: value,
              min: min,
              max: max,
              divisions: ((max - min) / 5).round(),
              onChanged: onChanged,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoTile(String title, String value, IconData icon) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Row(
        children: [
          Icon(
            icon,
            color: Colors.grey[600],
            size: 20,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              title,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Text(
            value,
            style: TextStyle(
              fontSize: 14,
              color: Colors.grey[600],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionTile(
    String title,
    String subtitle,
    IconData icon,
    VoidCallback onTap, {
    bool isDestructive = false,
  }) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Row(
            children: [
              Icon(
                icon,
                color: isDestructive ? Colors.red[600] : Colors.grey[600],
                size: 20,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w500,
                        color: isDestructive ? Colors.red[600] : Colors.black,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[600],
                      ),
                    ),
                  ],
                ),
              ),
              Icon(
                Icons.chevron_right,
                color: Colors.grey[400],
                size: 20,
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showPrivacyPolicy() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(AppLocalizations.of(context)!.privacyPolicy),
        content: Text(
          AppLocalizations.of(context)!.privacyPolicyContent,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(AppLocalizations.of(context)!.ok),
          ),
        ],
      ),
    );
  }

  void _copyToClipboard(String url) {
    Clipboard.setData(ClipboardData(text: url));
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(AppLocalizations.of(context)!.urlCopiedToClipboard(url)),
        duration: const Duration(seconds: 2),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }


}