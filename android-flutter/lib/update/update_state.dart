/// The self-update state hierarchy, failure reasons and platform install boundary.
///
/// 1:1 port of the `UpdateFailureReason`, `UpdateState` and `InstallRequest`
/// declarations of
/// `android-app/app-core/src/main/kotlin/com/reelshort/app/update/UpdateCoordinator.kt`.
/// The states are `final` subclasses of a `sealed class` so every consumer can
/// `switch` exhaustively, and they implement value equality exactly like the
/// Kotlin `data class`/`data object` originals.
library;

import 'dart:io';

import 'release_models.dart';

/// Why an update attempt failed (`UpdateFailureReason`).
enum UpdateFailureReason {
  network,
  invalidRelease,
  storage,
  download,
  verification,
  install,
}

/// One observable state of the update flow (`UpdateState`).
sealed class UpdateState {
  const UpdateState();
}

/// Nothing is happening; the app has no pending update.
final class Idle extends UpdateState {
  const Idle();

  @override
  bool operator ==(Object other) => other is Idle;

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() => 'Idle()';
}

/// A manifest request is in flight; [manual] distinguishes the two entry points.
final class Checking extends UpdateState {
  const Checking({required this.manual});

  final bool manual;

  @override
  bool operator ==(Object other) => other is Checking && other.manual == manual;

  @override
  int get hashCode => Object.hash(Checking, manual);

  @override
  String toString() => 'Checking($manual)';
}

/// A newer release is published and can be downloaded.
final class Available extends UpdateState {
  const Available(this.release);

  final ReleaseInfo release;

  @override
  bool operator ==(Object other) =>
      other is Available && other.release == release;

  @override
  int get hashCode => Object.hash(Available, release);

  @override
  String toString() => 'Available(${release.tagName})';
}

/// The APK is streaming to the private cache directory.
final class Downloading extends UpdateState {
  const Downloading(this.release, this.progress);

  final ReleaseInfo release;
  final DownloadProgress progress;

  @override
  bool operator ==(Object other) =>
      other is Downloading &&
      other.release == release &&
      other.progress == progress;

  @override
  int get hashCode => Object.hash(Downloading, release, progress);

  @override
  String toString() => 'Downloading(${release.tagName}, $progress)';
}

/// The download finished; size and SHA-256 are being checked.
final class Verifying extends UpdateState {
  const Verifying(this.release);

  final ReleaseInfo release;

  @override
  bool operator ==(Object other) =>
      other is Verifying && other.release == release;

  @override
  int get hashCode => Object.hash(Verifying, release);

  @override
  String toString() => 'Verifying(${release.tagName})';
}

/// The verified APK needs the "install unknown apps" permission.
final class PermissionRequired extends UpdateState {
  const PermissionRequired(this.release, this.apkFile);

  final ReleaseInfo release;
  final File apkFile;

  @override
  bool operator ==(Object other) =>
      other is PermissionRequired &&
      other.release == release &&
      other.apkFile.path == apkFile.path;

  @override
  int get hashCode => Object.hash(PermissionRequired, release, apkFile.path);

  @override
  String toString() => 'PermissionRequired(${release.tagName})';
}

/// The verified APK can be handed to the platform installer.
final class ReadyToInstall extends UpdateState {
  const ReadyToInstall(this.release, this.apkFile);

  final ReleaseInfo release;
  final File apkFile;

  @override
  bool operator ==(Object other) =>
      other is ReadyToInstall &&
      other.release == release &&
      other.apkFile.path == apkFile.path;

  @override
  int get hashCode => Object.hash(ReadyToInstall, release, apkFile.path);

  @override
  String toString() => 'ReadyToInstall(${release.tagName})';
}

/// The installed build is the newest one; only manual checks surface this.
final class UpToDate extends UpdateState {
  const UpToDate(this.currentVersion);

  final SemanticVersion currentVersion;

  @override
  bool operator ==(Object other) =>
      other is UpToDate && other.currentVersion == currentVersion;

  @override
  int get hashCode => Object.hash(UpToDate, currentVersion);

  @override
  String toString() => 'UpToDate($currentVersion)';
}

/// The last attempt failed; [release] is kept when a retry is still possible.
final class Failed extends UpdateState {
  const Failed(this.reason, {required this.manual, this.release});

  final UpdateFailureReason reason;
  final bool manual;
  final ReleaseInfo? release;

  @override
  bool operator ==(Object other) =>
      other is Failed &&
      other.reason == reason &&
      other.manual == manual &&
      other.release == release;

  @override
  int get hashCode => Object.hash(Failed, reason, manual, release);

  @override
  String toString() => 'Failed($reason, manual: $manual)';
}

/// What the platform layer must do next (`InstallRequest`).
sealed class InstallRequest {
  const InstallRequest();
}

/// Ask the user for the "install unknown apps" permission.
final class RequestPermission extends InstallRequest {
  const RequestPermission();

  @override
  bool operator ==(Object other) => other is RequestPermission;

  @override
  int get hashCode => runtimeType.hashCode;

  @override
  String toString() => 'RequestPermission()';
}

/// Hand [apkFile] to the system installer.
final class Install extends InstallRequest {
  const Install(this.apkFile);

  final File apkFile;

  @override
  bool operator ==(Object other) =>
      other is Install && other.apkFile.path == apkFile.path;

  @override
  int get hashCode => Object.hash(Install, apkFile.path);

  @override
  String toString() => 'Install(${apkFile.path})';
}

/// Boundary for the Android-only install step.
///
/// The Kotlin original shells out to `AndroidUpdateInstaller`, which checks
/// `PackageManager.canRequestPackageInstalls()`, opens
/// `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` and finally starts the
/// `ACTION_VIEW` package-installer intent through a `FileProvider` URI. None of
/// that is portable Dart, so this Flutter workstream keeps the *decision* logic
/// in `UpdateCoordinator` (the `prepareInstall` matrix over these three calls)
/// and leaves the platform effects to the later Android implementation:
///
///  * [canRequestPackageInstalls] mirrors `canRequestPackageInstalls()`;
///  * [requestInstallPermission] mirrors the unknown-sources settings intent;
///  * [install] mirrors `install(File)` and must throw when Android cannot open
///    the installer, because the coordinator then reports
///    [UpdateFailureReason.install].
abstract interface class UpdateInstaller {
  /// Whether this app may currently start an APK installation.
  Future<bool> canRequestPackageInstalls();

  /// Opens the system "install unknown apps" screen for this package.
  Future<void> requestInstallPermission();

  /// Starts the system package installer for [apk].
  ///
  /// Implementations must delete the staged APK once the installer has read it,
  /// as `AndroidUpdateInstaller.scheduleCacheCleanup` does.
  Future<void> install(File apk);
}
