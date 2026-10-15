/// Presentation rules of the self-update flow.
///
/// 1:1 port of
/// `android-app/app/src/main/java/com/reelshort/app/ui/format/UpdateUiFormat.kt`:
/// the 25-field English copy table plus the byte, progress, dialog-visibility
/// and failure-message helpers.
library;

import '../../data/models/app_language.dart';
import '../../update/release_models.dart';
import '../../update/update_state.dart';

/// All user-visible copy of the update dialog (`UpdateStrings`).
class UpdateStrings {
  const UpdateStrings({
    required this.versionTitle,
    required this.checkForUpdates,
    required this.checking,
    required this.latestVersion,
    required this.checkFailed,
    required this.availableTitle,
    required this.publishedLabel,
    required this.sizeLabel,
    required this.updateNow,
    required this.later,
    required this.downloading,
    required this.verifying,
    required this.cancel,
    required this.retry,
    required this.permissionTitle,
    required this.permissionBody,
    required this.openSettings,
    required this.readyTitle,
    required this.readyBody,
    required this.install,
    required this.storageError,
    required this.verificationError,
    required this.invalidReleaseError,
    required this.downloadError,
    required this.installError,
  });

  final String versionTitle;
  final String checkForUpdates;
  final String checking;
  final String latestVersion;
  final String checkFailed;
  final String availableTitle;
  final String publishedLabel;
  final String sizeLabel;
  final String updateNow;
  final String later;
  final String downloading;
  final String verifying;
  final String cancel;
  final String retry;
  final String permissionTitle;
  final String permissionBody;
  final String openSettings;
  final String readyTitle;
  final String readyBody;
  final String install;
  final String storageError;
  final String verificationError;
  final String invalidReleaseError;
  final String downloadError;
  final String installError;
}

/// The app is English-only (see [AppLanguage]); the switch mirrors the Kotlin
/// `when (language)` so a second language can be added without touching callers.
UpdateStrings updateStrings([
  AppLanguage language = AppLanguage.defaultLanguage,
]) => switch (language) {
  AppLanguage.english => const UpdateStrings(
    versionTitle: 'App version',
    checkForUpdates: 'Check',
    checking: 'Checking…',
    latestVersion: "You're using the latest version.",
    checkFailed:
        "Couldn't check for updates. Check your connection and try again.",
    availableTitle: 'A new version is available',
    publishedLabel: 'Published',
    sizeLabel: 'Download size',
    updateNow: 'Update now',
    later: 'Later',
    downloading: 'Downloading update',
    verifying: 'Verifying update…',
    cancel: 'Cancel',
    retry: 'Retry',
    permissionTitle: 'Allow app installation',
    permissionBody:
        'Android needs permission to install unknown apps from ShortLink. '
        'Enable it in Settings, then return here.',
    openSettings: 'Open settings',
    readyTitle: 'Ready to install',
    readyBody:
        'The APK passed the download integrity check. Android will ask you to '
        'confirm installation.',
    install: 'Install',
    storageError: "There isn't enough storage space for this update.",
    verificationError:
        'The downloaded APK failed security verification and was deleted.',
    invalidReleaseError:
        'This update package is incomplete or invalid. Please try again later.',
    downloadError: "The update couldn't be downloaded. Try again.",
    installError:
        "Android couldn't open the installer. Check the installation "
        'permission and try again.',
  ),
};

/// Human readable byte count, clamped at zero (`formatUpdateBytes`).
///
/// `toStringAsFixed(1)` matches Java's `String.format(Locale.US, "%.1f …")`,
/// including its rounding, without a locale-dependent formatter.
String formatUpdateBytes(int bytes) {
  final int value = bytes < 0 ? 0 : bytes;
  if (value >= 1024 * 1024) {
    return '${(value / (1024.0 * 1024.0)).toStringAsFixed(1)} MB';
  }
  if (value >= 1024) {
    return '${(value / 1024.0).toStringAsFixed(1)} KB';
  }
  return '$value B';
}

/// Whole-percent progress, or `null` when the total is unknown (`updateProgressPercent`).
int? updateProgressPercent(DownloadProgress progress) {
  final int? total = progress.totalBytes;
  if (total == null || total <= 0) {
    return null;
  }
  return progress.downloadedBytes.clamp(0, total) * 100 ~/ total;
}

/// Whether the update dialog should be visible for [state] (`shouldShowUpdateDialog`).
bool shouldShowUpdateDialog(UpdateState state) => switch (state) {
  Available() ||
  Downloading() ||
  Verifying() ||
  PermissionRequired() ||
  ReadyToInstall() => true,
  Failed(:final ReleaseInfo? release) => release != null,
  Idle() || Checking() || UpToDate() => false,
};

/// The release a state still refers to, if any (`UpdateState.releaseOrNull()`).
extension UpdateStateRelease on UpdateState {
  ReleaseInfo? releaseOrNull() => switch (this) {
    Available(:final ReleaseInfo release) => release,
    Downloading(:final ReleaseInfo release) => release,
    Verifying(:final ReleaseInfo release) => release,
    PermissionRequired(:final ReleaseInfo release) => release,
    ReadyToInstall(:final ReleaseInfo release) => release,
    Failed(:final ReleaseInfo? release) => release,
    Idle() || Checking() || UpToDate() => null,
  };
}

/// The copy shown for [reason] (`UpdateStrings.failureMessage`).
extension UpdateStringsFailure on UpdateStrings {
  String failureMessage(UpdateFailureReason reason) => switch (reason) {
    UpdateFailureReason.network ||
    UpdateFailureReason.download => downloadError,
    UpdateFailureReason.invalidRelease => invalidReleaseError,
    UpdateFailureReason.storage => storageError,
    UpdateFailureReason.verification => verificationError,
    UpdateFailureReason.install => installError,
  };
}
