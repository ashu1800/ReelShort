/// The self-update state machine.
///
/// Port of
/// `android-app/app-core/src/main/kotlin/com/reelshort/app/update/UpdateCoordinator.kt`
/// together with the serialisation and install plumbing the Kotlin app keeps in
/// `ReelShortViewModel`: a download is single-flight (the ViewModel drops a
/// second `downloadUpdate()` while its `downloadJob` is active) and the install
/// decision matrix is driven through the [UpdateInstaller] boundary.
library;

import 'dart:io';

import 'package:flutter/foundation.dart';

import 'release_downloader.dart';
import 'release_models.dart';
import 'update_state.dart';

/// Drives check → download → verify → install for one app instance.
class UpdateCoordinator {
  UpdateCoordinator({
    required this.releaseClient,
    required this.downloader,
    required this.currentVersion,
    this.currentVersionCode = 0,
    required this.availableBytes,
    this.maxApkBytes = maxApkBytesLimit,
    this.installer,
  });

  final ReleaseUpdateClient releaseClient;
  final ReleaseDownloader downloader;
  final SemanticVersion currentVersion;

  /// Installed `versionCode`, read from `PackageManager` on Android.
  final int currentVersionCode;

  /// Free-space probe for the updates directory.
  ///
  /// Kotlin defaults to `File.usableSpace`; `dart:io` exposes no usable-space
  /// API, so the probe is required instead of defaulting to a value that would
  /// silently disable the storage gate.
  final int Function(Directory directory) availableBytes;

  /// Ceiling for one APK download, in bytes.
  final int maxApkBytes;

  /// Platform install boundary; without it the install bridges are no-ops.
  final UpdateInstaller? installer;

  final ValueNotifier<UpdateState> _notifier = ValueNotifier<UpdateState>(
    const Idle(),
  );

  bool _downloadInFlight = false;
  bool _cancelRequested = false;

  /// Observable state, conflated on value equality like the Kotlin `StateFlow`.
  ValueListenable<UpdateState> get state => _notifier;

  /// Whether a download started by [downloadAvailable] is still running.
  bool get downloadInFlight => _downloadInFlight;

  /// Whether the running download was asked to stop ([cancelDownload]).
  bool get cancelRequested => _cancelRequested;

  /// Checks the release endpoint; a no-op while checking or downloading.
  ///
  /// An automatic check never surfaces failures ([Idle]) and never reports
  /// [UpToDate]; a manual one does both.
  Future<void> checkForUpdate({required bool manual}) async {
    final UpdateState current = _notifier.value;
    if (current is Checking || current is Downloading) {
      return;
    }
    _notifier.value = Checking(manual: manual);
    try {
      final ReleaseInfo? release = await releaseClient.fetchLatestStable();
      // Anti-downgrade: a manifest below the installed build is rejected so the
      // app cannot be talked into installing an older package.
      if (release != null && release.versionCode < currentVersionCode) {
        throw const InvalidReleaseException(
          'versionCode is lower than the installed build',
        );
      }
      if (release == null) {
        _notifier.value = manual ? UpToDate(currentVersion) : const Idle();
      } else if (release.version > currentVersion) {
        _notifier.value = Available(release);
      } else {
        _notifier.value = manual ? UpToDate(currentVersion) : const Idle();
      }
    } catch (error) {
      // Kotlin rethrows CancellationException here without touching the state.
      if (error is DownloadCancelledException) {
        rethrow;
      }
      _notifier.value = manual
          ? Failed(_failureReason(error), manual: true)
          : const Idle();
    }
  }

  /// Downloads the available release; a no-op unless one is pending.
  ///
  /// Only [Available] and [Failed] with a retained release can download, and the
  /// second concurrent call is dropped, exactly like the Kotlin
  /// `downloadJob?.isActive == true` guard.
  Future<void> downloadAvailable(Directory cacheDir) async {
    if (_downloadInFlight) {
      return;
    }
    final ReleaseInfo? release = switch (_notifier.value) {
      Available(:final ReleaseInfo release) => release,
      Failed(:final ReleaseInfo? release) => release,
      _ => null,
    };
    if (release == null) {
      return;
    }
    _downloadInFlight = true;
    _cancelRequested = false;
    try {
      await _download(release, cacheDir);
    } finally {
      _downloadInFlight = false;
    }
  }

  /// Port of `downloadAvailableBlocking`: gates, stream, verify, move.
  Future<void> _download(ReleaseInfo release, Directory cacheDir) async {
    final String separator = Platform.pathSeparator;
    final Directory updatesDir = Directory(
      '${cacheDir.path}${separator}updates',
    );
    await updatesDir.create(recursive: true);
    final File partialFile = File(
      '${updatesDir.path}$separator${release.apkAsset.name}.part',
    );
    final File finalFile = File(
      '${updatesDir.path}$separator${release.apkAsset.name}',
    );
    await deleteIfExists(partialFile);
    await deleteIfExists(finalFile);
    try {
      if (release.apkAsset.sizeBytes > maxApkBytes) {
        throw DownloadTooLargeException(maxApkBytes);
      }
      final int requiredBytes =
          release.apkAsset.sizeBytes + freeSpaceMarginBytes;
      if (availableBytes(updatesDir) < requiredBytes) {
        throw const _InsufficientStorageException();
      }
      _notifier.value = Downloading(
        release,
        DownloadProgress(0, release.apkAsset.sizeBytes),
      );
      final String expectedChecksum = await downloader.fetchSha256(
        release.sha256Asset.downloadUrl,
      );
      _throwIfCancelled();
      await downloader.download(
        release.apkAsset.downloadUrl,
        partialFile,
        maxApkBytes,
        (int downloaded, int? total) {
          _notifier.value = Downloading(
            release,
            DownloadProgress(downloaded, total),
          );
        },
      );
      _throwIfCancelled();
      _notifier.value = Verifying(release);
      final int downloadedSize = await partialFile.length();
      if (downloadedSize != release.apkAsset.sizeBytes) {
        throw const SecurityException(
          'Downloaded APK size does not match release metadata',
        );
      }
      final String actualChecksum = await sha256HexOfFile(partialFile);
      if (actualChecksum.toLowerCase() != expectedChecksum.toLowerCase()) {
        throw const InvalidChecksumException();
      }
      await moveAtomically(partialFile, finalFile);
      _notifier.value = ReadyToInstall(release, finalFile);
    } catch (error) {
      await deleteIfExists(partialFile);
      await deleteIfExists(finalFile);
      // A cancelled download leaves the state to `downloadCancelled()`, exactly
      // like the Kotlin CancellationException path.
      if (error is DownloadCancelledException) {
        rethrow;
      }
      _notifier.value = Failed(
        _failureReason(error),
        manual: true,
        release: release,
      );
    }
  }

  /// Stops a running download and returns to [Available].
  ///
  /// Port of `ReelShortViewModel.cancelUpdateDownload()`: the job is signalled
  /// through [cancelRequested] and the state is reset right away, so the
  /// partial files are deleted by the rethrow without a [Failed] state.
  void cancelDownload() {
    _cancelRequested = true;
    downloadCancelled();
  }

  /// Returns [Downloading]/[Verifying] to [Available]; otherwise a no-op.
  void downloadCancelled() {
    final UpdateState current = _notifier.value;
    final ReleaseInfo? release = switch (current) {
      Downloading(:final ReleaseInfo release) => release,
      Verifying(:final ReleaseInfo release) => release,
      _ => null,
    };
    if (release != null) {
      _notifier.value = Available(release);
    }
  }

  /// Decides the next install step; `null` when there is nothing to install.
  InstallRequest? prepareInstall(bool canRequestPackageInstalls) {
    final UpdateState current = _notifier.value;
    if (current is ReadyToInstall) {
      if (canRequestPackageInstalls) {
        return Install(current.apkFile);
      }
      _notifier.value = PermissionRequired(current.release, current.apkFile);
      return const RequestPermission();
    }
    if (current is PermissionRequired) {
      if (canRequestPackageInstalls) {
        _notifier.value = ReadyToInstall(current.release, current.apkFile);
        return Install(current.apkFile);
      }
      return const RequestPermission();
    }
    return null;
  }

  /// Re-evaluates the permission request; only [PermissionRequired] reacts.
  InstallRequest? onInstallPermissionResult(bool granted) {
    final UpdateState current = _notifier.value;
    if (current is! PermissionRequired || !granted) {
      return null;
    }
    _notifier.value = ReadyToInstall(current.release, current.apkFile);
    return Install(current.apkFile);
  }

  /// Reports a failed installer start as [UpdateFailureReason.install].
  void installationFailed() {
    final UpdateState current = _notifier.value;
    final ReleaseInfo? release = switch (current) {
      ReadyToInstall(:final ReleaseInfo release) => release,
      PermissionRequired(:final ReleaseInfo release) => release,
      _ => null,
    };
    if (release == null) {
      return;
    }
    _notifier.value = Failed(
      UpdateFailureReason.install,
      manual: true,
      release: release,
    );
  }

  /// Hides the update UI; ignored while a download is running.
  void dismiss() {
    final UpdateState current = _notifier.value;
    if (current is! Downloading && current is! Verifying) {
      _notifier.value = const Idle();
    }
  }

  /// Asks the platform whether APK installs are allowed right now.
  Future<bool> canRequestPackageInstalls() async =>
      await installer?.canRequestPackageInstalls() ?? false;

  /// Platform bridge for `prepareInstall(installer.canRequestPackageInstalls())`.
  Future<InstallRequest?> prepareInstallWithInstaller() async =>
      prepareInstall(await canRequestPackageInstalls());

  /// Opens the system "install unknown apps" screen; the state is unchanged
  /// until [onInstallPermissionResult] runs.
  Future<void> requestInstallPermission() async {
    await installer?.requestInstallPermission();
  }

  /// Port of `ReelShortViewModel.handleInstallRequest`: runs an [Install] and
  /// reports a failed start through [installationFailed].
  Future<void> handleInstallRequest(InstallRequest? request) async {
    if (request is! Install) {
      return;
    }
    try {
      await installer?.install(request.apkFile);
    } catch (_) {
      installationFailed();
    }
  }

  void _throwIfCancelled() {
    if (_cancelRequested) {
      throw const DownloadCancelledException();
    }
  }

  /// Failure classification of the Kotlin `Throwable.toFailureReason()`.
  UpdateFailureReason _failureReason(Object error) {
    if (error is _InsufficientStorageException ||
        error is DownloadTooLargeException) {
      return UpdateFailureReason.storage;
    }
    if (error is InvalidChecksumException || error is SecurityException) {
      return UpdateFailureReason.verification;
    }
    if (error is InvalidReleaseException) {
      return UpdateFailureReason.invalidRelease;
    }
    if (error is ReleaseHttpException) {
      return UpdateFailureReason.network;
    }
    return UpdateFailureReason.download;
  }
}

/// Raised by the free-space gate; private, like the Kotlin nested class.
class _InsufficientStorageException implements Exception {
  const _InsufficientStorageException();

  @override
  String toString() => '_InsufficientStorageException';
}
