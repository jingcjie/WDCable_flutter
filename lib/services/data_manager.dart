import 'package:flutter/services.dart';
import '../utils/app_logger.dart';

class DataManager {
  static const MethodChannel _channel = MethodChannel('wifi_direct_cable');
  static DataManager? _instance;

  DataManager._internal();

  static DataManager get instance {
    _instance ??= DataManager._internal();
    return _instance!;
  }

  /// Save a string value to SharedPreferences
  Future<bool> setString(String key, String value) async {
    try {
      final result = await _channel.invokeMethod('setStringPreference', {
        'key': key,
        'value': value,
      });
      return result as bool;
    } catch (e) {
      AppLogger.error('Error setting string preference: $e');
      return false;
    }
  }

  /// Get a string value from SharedPreferences
  Future<String?> getString(String key, {String? defaultValue}) async {
    try {
      final result = await _channel.invokeMethod('getStringPreference', {
        'key': key,
        'defaultValue': defaultValue,
      });
      return result as String?;
    } catch (e) {
      AppLogger.error('Error getting string preference: $e');
      return defaultValue;
    }
  }

  /// Save an integer value to SharedPreferences
  Future<bool> setInt(String key, int value) async {
    try {
      final result = await _channel.invokeMethod('setIntPreference', {
        'key': key,
        'value': value,
      });
      return result as bool;
    } catch (e) {
      AppLogger.error('Error setting int preference: $e');
      return false;
    }
  }

  /// Get an integer value from SharedPreferences
  Future<int?> getInt(String key, {int? defaultValue}) async {
    try {
      final result = await _channel.invokeMethod('getIntPreference', {
        'key': key,
        'defaultValue': defaultValue,
      });
      return result as int?;
    } catch (e) {
      AppLogger.error('Error getting int preference: $e');
      return defaultValue;
    }
  }

  /// Save a boolean value to SharedPreferences
  Future<bool> setBool(String key, bool value) async {
    try {
      final result = await _channel.invokeMethod('setBoolPreference', {
        'key': key,
        'value': value,
      });
      return result as bool;
    } catch (e) {
      AppLogger.error('Error setting bool preference: $e');
      return false;
    }
  }

  /// Get a boolean value from SharedPreferences
  Future<bool?> getBool(String key, {bool? defaultValue}) async {
    try {
      final result = await _channel.invokeMethod('getBoolPreference', {
        'key': key,
        'defaultValue': defaultValue,
      });
      return result as bool?;
    } catch (e) {
      AppLogger.error('Error getting bool preference: $e');
      return defaultValue;
    }
  }

  /// Save a double value to SharedPreferences
  Future<bool> setDouble(String key, double value) async {
    try {
      final result = await _channel.invokeMethod('setDoublePreference', {
        'key': key,
        'value': value,
      });
      return result as bool;
    } catch (e) {
      AppLogger.error('Error setting double preference: $e');
      return false;
    }
  }

  /// Get a double value from SharedPreferences
  Future<double?> getDouble(String key, {double? defaultValue}) async {
    try {
      final result = await _channel.invokeMethod('getDoublePreference', {
        'key': key,
        'defaultValue': defaultValue,
      });
      return result as double?;
    } catch (e) {
      AppLogger.error('Error getting double preference: $e');
      return defaultValue;
    }
  }

  /// Remove a key from SharedPreferences
  Future<bool> remove(String key) async {
    try {
      final result = await _channel.invokeMethod('removePreference', {
        'key': key,
      });
      return result as bool;
    } catch (e) {
      AppLogger.error('Error removing preference: $e');
      return false;
    }
  }

  /// Clear all SharedPreferences
  Future<bool> clear() async {
    try {
      final result = await _channel.invokeMethod('clearPreferences');
      return result as bool;
    } catch (e) {
      AppLogger.error('Error clearing preferences: $e');
      return false;
    }
  }

  /// Check if a key exists in SharedPreferences
  Future<bool> containsKey(String key) async {
    try {
      final result = await _channel.invokeMethod('containsKey', {'key': key});
      return result as bool;
    } catch (e) {
      AppLogger.error('Error checking key existence: $e');
      return false;
    }
  }

  /// Get all keys from SharedPreferences
  Future<Set<String>> getKeys() async {
    try {
      final result = await _channel.invokeMethod('getKeys');
      final List<dynamic> keys = result as List<dynamic>;
      return keys.cast<String>().toSet();
    } catch (e) {
      AppLogger.error('Error getting keys: $e');
      return <String>{};
    }
  }
}
