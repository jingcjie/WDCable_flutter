import 'dart:developer' as developer;

class AppLogger {
  const AppLogger._();

  static void info(String message, {String name = 'WDCable'}) {
    developer.log(message, name: name);
  }

  static void error(String message, {String name = 'WDCable'}) {
    developer.log(message, name: name, level: 1000);
  }
}
