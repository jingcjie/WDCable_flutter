import 'dart:ffi';
import 'dart:io' show Platform;
import 'package:ffi/ffi.dart';
import 'package:win32/win32.dart';

class WindowsMsixManager {
  // Checks if the running process has a package identity.
  // This is the core of our check.
  static bool hasPackageIdentity() {
    // This function is only available on Windows.
    if (!Platform.isWindows) {
      return false;
    }

    return using((Arena arena) {
      final length = arena<UINT32>()..value = 0;
      final buffer = nullptr;

      // First, call with a null buffer to get the required buffer size.
      final result = GetCurrentPackageFullName(length, buffer);

      // APPMODEL_ERROR_NO_PACKAGE is the specific error code we get
      // when running as a normal, unpackaged Win32 application.
      // See: https://learn.microsoft.com/en-us/windows/win32/api/appmodel/nf-appmodel-getcurrentpackagefullname
      const int APPMODEL_ERROR_NO_PACKAGE = 15700;
      
      print(result);

      if (result == APPMODEL_ERROR_NO_PACKAGE) {
        print("LifecycleManager: No package identity found.");
        return false;
      }

      // If we get here, it means the function succeeded or failed for another reason,
      // but in either case, it's not the "no package" error, so we assume identity exists.
      print("LifecycleManager: Package identity found (Result code: $result).");
      return true;
    });
  }
}