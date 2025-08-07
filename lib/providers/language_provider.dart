import 'package:flutter/material.dart';
import '../services/data_manager.dart';

class LanguageProvider extends ChangeNotifier {
  Locale? _locale;
  
  Locale? get locale => _locale;
  
  LanguageProvider() {
    _loadLanguage();
  }
  
  void _loadLanguage() async {
    String? languageCode = await DataManager.instance.getString('language_code');
    if (languageCode != null) {
      _locale = Locale(languageCode);
      notifyListeners();
    }
  }
  
  void setLanguage(Locale locale) async {
    _locale = locale;
    await DataManager.instance.setString('language_code', locale.languageCode);
    notifyListeners();
  }
  
  void clearLanguage() async {
    _locale = null;
    await DataManager.instance.remove('language_code');
    notifyListeners();
  }
}