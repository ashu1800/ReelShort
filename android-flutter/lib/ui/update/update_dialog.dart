/// The self-update modal dialog.
///
/// Flutter port of
/// `android-app/app/src/main/java/com/reelshort/app/ui/components/UpdateDialog.kt`.
/// The shell opens this widget through [showUpdateDialog] while
/// [shouldShowUpdateDialog] reports the coordinator state as visible; the dialog
/// subscribes to the coordinator itself with a `ValueListenableBuilder<UpdateState>`
/// so the content tracks every state change without the shell re-showing anything.
///
/// Copy and layout rules follow `UpdateStrings` / `UpdateUiFormat` exactly:
/// failed states always use `checkFailed` as the title with `failureMessage`
/// as the body, and no string is reworded here.
library;

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../../core/formatting/update_ui_format.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../update/release_models.dart';
import '../../update/update_coordinator.dart';
import '../../update/update_state.dart';

/// Maximum content height inside the dialog (`heightIn(max = 360.dp)`).
const double _maxContentHeight = 360;

/// Height of the determinate/indeterminate progress bar (`height(5.dp)`).
const double _progressBarHeight = 5;

/// Diameter of the verifying spinner.
const double _spinnerSize = 18;

/// Minimum size of one action button (`heightIn(min = 48.dp)` touch target).
const Size _minActionSize = Size(64, 48);

/// Vertical gap between content blocks (`spacedBy(12.dp)`).
const double _contentGap = 12;

/// Barrier opacity over the theme scrim.
const double _scrimAlpha = 0.6;

/// Opens the update dialog as a modal route.
///
/// Call this from the shell whenever `shouldShowUpdateDialog(coordinator.state.value)`
/// becomes true. The barrier is tappable in the non-busy states; while a download
/// or verification is running the dialog itself blocks every dismissal path with
/// a [PopScope], so neither the barrier nor the system back gesture can leave
/// those states early.
Future<void> showUpdateDialog(BuildContext context) {
  final AppColors colors = Theme.of(context).extension<AppColors>()!;
  return showDialog<void>(
    context: context,
    barrierDismissible: true,
    barrierColor: colors.scrim.withValues(alpha: _scrimAlpha),
    builder: (BuildContext context) => const UpdateDialog(),
  );
}

/// Supplies the [UpdateCoordinator] to [UpdateDialog] through the widget tree.
///
/// Place this **above the `Navigator`** — for example in `MaterialApp.builder` —
/// so the dialog route can see it:
///
/// ```dart
/// MaterialApp(
///   builder: (BuildContext context, Widget? child) => UpdateDialogScope(
///     coordinator: coordinator,
///     child: child!,
///   ),
/// )
/// ```
///
/// Shells that cannot wrap the tree may instead set
/// [UpdateDialog.coordinatorOverride] once at composition-root time.
final class UpdateDialogScope extends InheritedWidget {
  /// Creates a scope that exposes [coordinator] to the update dialog.
  const UpdateDialogScope({
    super.key,
    required this.coordinator,
    required super.child,
  });

  /// The update state machine the dialog observes.
  final UpdateCoordinator coordinator;

  /// Returns the enclosing scope, or `null` when the dialog has none.
  static UpdateDialogScope? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<UpdateDialogScope>();

  /// Returns the enclosing scope, failing with guidance when it is missing.
  static UpdateDialogScope of(BuildContext context) {
    final UpdateDialogScope? scope = maybeOf(context);
    if (scope == null) {
      throw FlutterError(
        'No UpdateDialogScope found above the Navigator. Wrap the tree with '
        'UpdateDialogScope(coordinator: ...) or set '
        'UpdateDialog.coordinatorOverride before showing the dialog.',
      );
    }
    return scope;
  }

  @override
  bool updateShouldNotify(UpdateDialogScope oldWidget) =>
      oldWidget.coordinator != coordinator;
}

/// The update dialog.
///
/// Contract:
///  * the state is read with a `ValueListenableBuilder<UpdateState>` on
///    `coordinator.state`, never passed in — the modal re-renders on every
///    transition while it is open;
///  * `Failed` states always title with `UpdateStrings.checkFailed` and body
///    with `failureMessage(reason)`, exactly as published;
///  * dismissal is impossible while `Downloading` or `Verifying` (`PopScope`),
///    and `coordinator.dismiss()` runs whenever the route does close;
///  * the dialog is labelled by its title: the Material `AlertDialog` marks the
///    title as the route name (`namesRoute`), and the title additionally carries
///    a header semantic so screen readers reach it first;
///  * action buttons are focusable in DOM order — the dismiss/cancel button is
///    emitted before the confirm button.
class UpdateDialog extends StatelessWidget {
  /// Creates the update dialog. The coordinator comes from the ambient
  /// [UpdateDialogScope] or [coordinatorOverride].
  const UpdateDialog({super.key});

  /// Composition-root fallback for the coordinator.
  ///
  /// Prefer [UpdateDialogScope]; this override exists for composition roots
  /// that render the dialog without a scope in the tree (tests, plugins that
  /// push the dialog from outside the shell).
  static UpdateCoordinator? coordinatorOverride;

  @override
  Widget build(BuildContext context) {
    final UpdateCoordinator? coordinator =
        UpdateDialogScope.maybeOf(context)?.coordinator ?? coordinatorOverride;
    if (coordinator == null) {
      // Without a coordinator the state cannot be shown; keep the modal inert
      // instead of crashing, so a mis-scoped shell degrades gracefully.
      return const SizedBox.shrink();
    }
    return ValueListenableBuilder<UpdateState>(
      valueListenable: coordinator.state,
      builder: (BuildContext context, UpdateState state, Widget? child) {
        if (!shouldShowUpdateDialog(state)) {
          _closeIfModal(context);
          return const SizedBox.shrink();
        }
        return _UpdateDialogBody(coordinator: coordinator, state: state);
      },
    );
  }

  /// Closes the dialog route when the state stops being shown.
  ///
  /// Guarded with `context.mounted` because `coordinator.dismiss()` may already
  /// have popped the route before this frame callback runs.
  static void _closeIfModal(BuildContext context) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!context.mounted) {
        return;
      }
      final ModalRoute<Object?>? route = ModalRoute.of<Object?>(context);
      if (route is PopupRoute<Object?> && route.isCurrent) {
        Navigator.of(context).pop();
      }
    });
  }
}

/// The progress figure under the download bar: a percentage line and a
/// transferred-bytes line.
///
/// Public so the layout can be exercised without mounting the whole update
/// dialog: the dialog's coordinator wiring needs real async plumbing, while this
/// widget's only job is to show the numbers in full inside the dialog's width.
///
/// The earlier single-line `Downloading update   82% · 44.4 MB / 54.0 MB`
/// layout either overflowed (its own Row, 24 px) or, once bounded, ellipsised
/// the total away (`29% · 16.2 MB / 54.…`) — the user could not read what was
/// left to download. Splitting into two lines gives each number the full width.
class DownloadProgressRow extends StatelessWidget {
  /// Creates the progress figure.
  const DownloadProgressRow({
    required this.progress,
    required this.textTheme,
    required this.amountColor,
    this.secondaryColor,
    super.key,
  });

  /// Bytes downloaded so far, with the declared total when known.
  final DownloadProgress progress;

  /// Theme whose `bodySmall` / `bodyMedium` carry the two lines.
  final TextTheme textTheme;

  /// Colour of the percentage line.
  final Color amountColor;

  /// Colour of the byte line; defaults to [amountColor] at 70% opacity.
  final Color? secondaryColor;

  @override
  Widget build(BuildContext context) {
    final int? percent = updateProgressPercent(progress);
    final Color secondary =
        secondaryColor ?? amountColor.withValues(alpha: 0.7);

    // "16.2 MB of 54.0 MB" reads better than "16.2 MB / 54.0 MB" and survives
    // wrapping; with an unknown total there is only the first number.
    final String bytes = progress.totalBytes == null
        ? formatUpdateBytes(progress.downloadedBytes)
        : '${formatUpdateBytes(progress.downloadedBytes)} of '
              '${formatUpdateBytes(progress.totalBytes!)}';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        // The percentage is the headline: it is the number people look for.
        // It wraps rather than ellipsises so a huge value is never truncated.
        Text(
          percent == null ? updateStrings().downloading : '$percent%',
          style: textTheme.titleMedium?.copyWith(
            color: amountColor,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          bytes,
          // Two lines is enough for the longest realistic amount; anything past
          // that is a formatting bug, not a layout one.
          maxLines: 2,
          style: textTheme.bodySmall?.copyWith(color: secondary),
        ),
      ],
    );
  }
}

/// The per-state dialog content and actions.
class _UpdateDialogBody extends StatelessWidget {
  const _UpdateDialogBody({required this.coordinator, required this.state});

  final UpdateCoordinator coordinator;
  final UpdateState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final UpdateStrings copy = updateStrings();
    // `shouldShowUpdateDialog` guarantees a release for every visible state;
    // the null check mirrors the Kotlin `?: return` as a defensive guard.
    final ReleaseInfo? release = state.releaseOrNull();
    if (release == null) {
      return const SizedBox.shrink();
    }
    final bool busy = state is Downloading || state is Verifying;

    return PopScope(
      // Never dismissible while downloading or verifying: the barrier tap and
      // the system back gesture both go through `maybePop`, which this blocks.
      canPop: !busy,
      onPopInvokedWithResult: (bool didPop, Object? result) {
        if (didPop) {
          coordinator.dismiss();
        }
      },
      child: AlertDialog(
        backgroundColor: colors.surface,
        shape: RoundedRectangleBorder(borderRadius: shapes.containerRadius),
        title: Semantics(
          header: true,
          child: Text(
            _title(copy),
            style: textTheme.titleLarge?.copyWith(
              color: colors.textPrimary,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
        content: SizedBox(
          width: double.maxFinite,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxHeight: _maxContentHeight),
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                spacing: _contentGap,
                children: <Widget>[
                  Text(
                    '${release.title} · ${release.tagName}',
                    style: textTheme.titleMedium?.copyWith(
                      color: colors.accentPrimary,
                    ),
                  ),
                  ..._contentFor(copy, colors, textTheme, release),
                ],
              ),
            ),
          ),
        ),
        actions: _actions(context, copy, colors, textTheme),
      ),
    );
  }

  /// The dialog title for the current state.
  ///
  /// Failed states are pinned to [UpdateStrings.checkFailed]; the body carries
  /// the reason through `failureMessage`, exactly as published.
  String _title(UpdateStrings copy) => switch (state) {
    Available() => copy.availableTitle,
    Downloading() => copy.downloading,
    Verifying() => copy.verifying,
    PermissionRequired() => copy.permissionTitle,
    ReadyToInstall() => copy.readyTitle,
    Failed() => copy.checkFailed,
    // Idle/Checking/UpToDate never render (shouldShowUpdateDialog is false);
    // the fallback mirrors the Kotlin `else -> availableTitle`.
    _ => copy.availableTitle,
  };

  /// The state-specific content blocks under the release headline.
  List<Widget> _contentFor(
    UpdateStrings copy,
    AppColors colors,
    TextTheme textTheme,
    ReleaseInfo release,
  ) => switch (state) {
    Available() => <Widget>[
      _metaLine(copy.publishedLabel, release.publishedAt, colors, textTheme),
      _metaLine(
        copy.sizeLabel,
        formatUpdateBytes(release.apkAsset.sizeBytes),
        colors,
        textTheme,
      ),
    ],
    Downloading(:final DownloadProgress progress) => _downloadContent(
      colors,
      textTheme,
      progress,
    ),
    Verifying() => <Widget>[
      Row(
        children: <Widget>[
          SizedBox(
            width: _spinnerSize,
            height: _spinnerSize,
            child: CircularProgressIndicator(
              strokeWidth: 2.4,
              color: colors.accentPrimary,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              copy.verifying,
              style: _bodyStyle(textTheme, colors),
            ),
          ),
        ],
      ),
    ],
    PermissionRequired() => <Widget>[
      Text(copy.permissionBody, style: _bodyStyle(textTheme, colors)),
    ],
    ReadyToInstall() => <Widget>[
      Text(copy.readyBody, style: _bodyStyle(textTheme, colors)),
    ],
    Failed(:final UpdateFailureReason reason) => <Widget>[
      Text(copy.failureMessage(reason), style: _bodyStyle(textTheme, colors)),
    ],
    _ => const <Widget>[],
  };

  /// Progress bar plus the `label / percent · amount` row of the Kotlin dialog.
  ///
  /// The percent comes from `updateProgressPercent`; an unknown total keeps the
  /// indeterminate bar and the plain amount.
  List<Widget> _downloadContent(
    AppColors colors,
    TextTheme textTheme,
    DownloadProgress progress,
  ) => <Widget>[
    ClipRRect(
      borderRadius: BorderRadius.circular(999),
      child: SizedBox(
        height: _progressBarHeight,
        width: double.infinity,
        child: LinearProgressIndicator(
          value: updateProgressPercent(progress) == null
              ? null
              : updateProgressPercent(progress)! / 100,
          minHeight: _progressBarHeight,
          color: colors.accentPrimary,
          backgroundColor: colors.surfaceAlt,
        ),
      ),
    ),
    DownloadProgressRow(
      progress: progress,
      textTheme: textTheme,
      amountColor: colors.textPrimary,
      secondaryColor: colors.textSecondary,
    ),
  ];

  Widget _metaLine(
    String label,
    String value,
    AppColors colors,
    TextTheme textTheme,
  ) => Text(
    '$label: $value',
    style: textTheme.bodyMedium?.copyWith(color: colors.textSecondary),
  );

  TextStyle? _bodyStyle(TextTheme textTheme, AppColors colors) =>
      textTheme.bodyMedium?.copyWith(color: colors.textSecondary);

  /// Dialog actions, dismiss/cancel first so focus order follows DOM order.
  List<Widget> _actions(
    BuildContext context,
    UpdateStrings copy,
    AppColors colors,
    TextTheme textTheme,
  ) {
    final List<Widget> actions = <Widget>[];
    switch (state) {
      case Verifying():
        break;
      case Downloading():
        actions.add(
          _actionButton(
            context,
            copy.cancel,
            colors,
            textTheme,
            coordinator.cancelDownload,
          ),
        );
      default:
        actions.add(
          _actionButton(context, copy.later, colors, textTheme, () {
            _dismiss(context);
          }),
        );
    }
    switch (state) {
      case Available():
        actions.add(
          _actionButton(context, copy.updateNow, colors, textTheme, () {
            unawaited(_startDownload());
          }),
        );
      case Failed():
        actions.add(
          _actionButton(context, copy.retry, colors, textTheme, () {
            unawaited(_startDownload());
          }),
        );
      case PermissionRequired():
        actions.add(
          _actionButton(context, copy.openSettings, colors, textTheme, () {
            unawaited(_openSettings());
          }),
        );
      case ReadyToInstall():
        actions.add(
          _actionButton(context, copy.install, colors, textTheme, () {
            unawaited(_install());
          }),
        );
      default:
        break;
    }
    return actions;
  }

  Widget _actionButton(
    BuildContext context,
    String label,
    AppColors colors,
    TextTheme textTheme,
    VoidCallback onPressed,
  ) => TextButton(
    onPressed: onPressed,
    style: TextButton.styleFrom(minimumSize: _minActionSize),
    child: Text(
      label,
      style: textTheme.labelLarge?.copyWith(color: colors.accentPrimary),
    ),
  );

  /// `updateNow` / `retry`: streams the APK into the private updates cache.
  ///
  /// The coordinator reports its own failures as a `Failed` state; a cache
  /// directory failure is the one error outside its gates, so it only leaves
  /// the current state untouched and keeps the retry affordance available.
  Future<void> _startDownload() async {
    try {
      final Directory cacheDir = await getTemporaryDirectory();
      await coordinator.downloadAvailable(cacheDir);
    } catch (_) {
      // path_provider failure: state stays as-is, the user can press again.
    }
  }

  /// `install`: runs the coordinator install matrix, then the resulting request.
  ///
  /// `prepareInstallWithInstaller` can surface a platform failure (the
  /// `UpdateInstaller` boundary throws on `PlatformException`); reporting it
  /// through `installationFailed` is the coordinator's only route to
  /// `UpdateFailureReason.install`. `handleInstallRequest` performs the same
  /// report for a failed installer start on its own.
  Future<void> _install() async {
    try {
      final InstallRequest? request =
          await coordinator.prepareInstallWithInstaller();
      await coordinator.handleInstallRequest(request);
    } catch (_) {
      coordinator.installationFailed();
    }
  }

  /// `openSettings`: asks the platform for the unknown-sources screen.
  Future<void> _openSettings() async {
    try {
      await coordinator.requestInstallPermission();
    } catch (_) {
      coordinator.installationFailed();
    }
  }

  /// `later` / `cancel`-adjacent dismissal: drops the state and closes the modal.
  void _dismiss(BuildContext context) {
    coordinator.dismiss();
    final ModalRoute<Object?>? route = ModalRoute.of<Object?>(context);
    if (route is PopupRoute<Object?> && route.isCurrent) {
      Navigator.of(context).pop();
    }
  }
}
