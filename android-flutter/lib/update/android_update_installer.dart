/// The Android `UpdateInstaller` implementation over a platform channel.
///
/// Dart side of
/// `android-app/app/src/main/java/com/reelshort/app/update/AndroidUpdateInstaller.kt`;
/// the platform effects live in
/// `android/app/src/main/kotlin/com/reelshort/app/UpdateInstallerPlugin.kt`,
/// registered from `MainActivity`. Every call maps 1:1 onto a Kotlin method:
///
///  * [canRequestPackageInstalls] → `PackageManager.canRequestPackageInstalls()`;
///  * [requestInstallPermission] → `ACTION_MANAGE_UNKNOWN_APP_SOURCES` with
///    `package:<applicationId>` data;
///  * [install] → `ACTION_VIEW` on the `FileProvider` URI of the APK, with the
///    sibling/60 s cache cleanup scheduled natively after the intent starts.
///
/// Failure contract: a `PlatformException` (or an unregistered channel, or the
/// platform reporting that no installer activity resolved) is rethrown as
/// [AndroidInstallException]. `UpdateCoordinator.handleInstallRequest` catches
/// any exception from [install] and reports it through `installationFailed()`,
/// which is the coordinator's only route to `UpdateFailureReason.install` — the
/// shared `SecurityException` stand-in maps to `verification` and must not be
/// used here.
library;

import 'dart:io';

import 'package:flutter/services.dart';

import 'update_state.dart';

/// The installer failed to act (`AndroidUpdateInstaller.InstallException`).
///
/// Raised when the platform channel errors out or reports that the package
/// installer could not be started. Both `UpdateCoordinator.handleInstallRequest`
/// and the update dialog route this to `installationFailed()`, surfacing as
/// `Failed(UpdateFailureReason.install)`.
class AndroidInstallException implements Exception {
  /// Creates a failure with the channel [code] and [message].
  const AndroidInstallException(this.code, this.message);

  /// Platform error code, or `installer_unavailable` when derived in Dart.
  final String code;

  /// Human-readable diagnostic; never shown to users directly.
  final String message;

  @override
  String toString() => 'AndroidInstallException($code): $message';
}

/// Method channel name shared with `UpdateInstallerPlugin.CHANNEL_NAME`.
const String androidUpdateInstallerChannelName =
    'com.reelshort.app/update_installer';

/// Android-only `UpdateInstaller` backed by [UpdateInstallerPlugin].
///
/// All three methods are safe to call on any platform: off-Android calls fail
/// with [AndroidInstallException] (the channel is never registered there), and
/// the coordinator turns that into an install failure state instead of crashing.
class AndroidUpdateInstaller implements UpdateInstaller {
  /// Creates an installer bound to [channel].
  ///
  /// The default channel name is the contract with
  /// `UpdateInstallerPlugin.CHANNEL_NAME`; a custom [channel] exists for tests.
  AndroidUpdateInstaller({
    MethodChannel? channel,
  }) : _channel = channel ?? MethodChannel(androidUpdateInstallerChannelName);

  final MethodChannel _channel;

  /// `PackageManager.canRequestPackageInstalls()`.
  @override
  Future<bool> canRequestPackageInstalls() async {
    try {
      final Object? result = await _channel.invokeMethod<Object?>(
        'canRequestPackageInstalls',
      );
      return result == true;
    } on PlatformException catch (error) {
      throw AndroidInstallException(error.code, error.message ?? error.code);
    } on MissingPluginException {
      throw const AndroidInstallException(
        'channel_not_registered',
        'UpdateInstallerPlugin is not registered on this platform',
      );
    }
  }

  /// Opens the system "install unknown apps" screen for this package.
  @override
  Future<void> requestInstallPermission() async {
    try {
      await _channel.invokeMethod<void>('requestInstallPermission');
    } on PlatformException catch (error) {
      throw AndroidInstallException(error.code, error.message ?? error.code);
    } on MissingPluginException {
      throw const AndroidInstallException(
        'channel_not_registered',
        'UpdateInstallerPlugin is not registered on this platform',
      );
    }
  }

  /// Hands [apk] to the system package installer.
  ///
  /// The platform side resolves the `FileProvider` URI, starts the installer
  /// (explicit `com.android.packageinstaller` first, implicit fallback) and
  /// schedules the cache cleanup. It answers `true` when an installer activity
  /// started and `false` when none resolved; [false] becomes
  /// [AndroidInstallException] so the coordinator reports an install failure.
  @override
  Future<void> install(File apk) async {
    try {
      final bool? started = await _channel.invokeMethod<bool>('install', <String, Object?>{
        'path': apk.path,
      });
      if (started != true) {
        throw const AndroidInstallException(
          'no_installer_activity',
          'No activity resolved the package-installer intent',
        );
      }
    } on PlatformException catch (error) {
      throw AndroidInstallException(error.code, error.message ?? error.code);
    } on MissingPluginException {
      throw const AndroidInstallException(
        'channel_not_registered',
        'UpdateInstallerPlugin is not registered on this platform',
      );
    }
  }
}
