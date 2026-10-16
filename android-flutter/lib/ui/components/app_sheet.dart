/// The design system's bottom sheet.
///
/// The auth sheet and the VIP paywall used to be plain [Stack] children of the
/// shell, so they had no barrier, no drag-to-dismiss, no focus trap, no route
/// semantics and no answer to the system back gesture — the back button left the
/// app while a sheet was open, and a screen reader could still walk into the
/// page behind them. [AppSheet.show] routes them instead: barrier, drag handle,
/// scrim, focus trap and back handling all come from the route, while this
/// widget owns the layout contract every sheet shares (page padding, keyboard
/// inset, bottom safe area, route semantics and the close affordance).
library;

import 'package:flutter/material.dart';

import '../../core/formatting/app_ui_copy.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_icons.dart';
import '../../theme/app_motion.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';

/// A sheet body laid out with the shared sheet contract.
class AppSheet extends StatelessWidget {
  /// Creates a sheet body.
  const AppSheet({
    required this.child,
    this.title,
    this.onClose,
    this.maxHeightFactor = 0.92,
    this.showCloseButton = true,
    super.key,
  });

  /// Sheet content.
  final Widget child;

  /// Optional sheet title, rendered as the route name for screen readers.
  final String? title;

  /// Called by the close affordance; defaults to popping the route.
  final VoidCallback? onClose;

  /// Fraction of the screen height the sheet may occupy.
  final double maxHeightFactor;

  /// Whether the header shows a close button.
  final bool showCloseButton;

  /// Opens [child] as a modal sheet and resolves to the route result.
  ///
  /// The route provides the barrier, the drag handle and the back-gesture
  /// handling; `isScrollControlled` lets the content decide its own height.
  static Future<T?> show<T>(
    BuildContext context, {
    required Widget child,
    String? title,
    bool isDismissible = true,
    bool enableDrag = true,
    double maxHeightFactor = 0.92,
  }) {
    return showModalBottomSheet<T>(
      context: context,
      isScrollControlled: true,
      isDismissible: isDismissible,
      enableDrag: enableDrag,
      useSafeArea: false,
      builder: (BuildContext sheetContext) => AppSheet(
        title: title,
        maxHeightFactor: maxHeightFactor,
        child: child,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppMotion motion = appMotionOf(context);
    final AppIconSize icons = appIconSizeOf(context);
    final MediaQueryData media = MediaQuery.of(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return Semantics(
      scopesRoute: true,
      namesRoute: title != null,
      explicitChildNodes: true,
      child: AnimatedPadding(
        duration: resolveAppMotion(context, motion.sheetEnter),
        curve: motion.sheetEnterCurve,
        padding: EdgeInsets.only(bottom: media.viewInsets.bottom),
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: media.size.height * maxHeightFactor,
          ),
          child: Padding(
            padding: EdgeInsets.fromLTRB(
              spacing.sheetPaddingX,
              spacing.sheetPaddingTop,
              spacing.sheetPaddingX,
              spacing.sheetPaddingBottom + media.viewPadding.bottom,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                if (title != null)
                  Row(
                    children: <Widget>[
                      Expanded(
                        child: Text(
                          title!,
                          style: roles.screenTitle.copyWith(
                            color: colors.textPrimary,
                          ),
                        ),
                      ),
                      if (showCloseButton)
                        Semantics(
                          button: true,
                          label: AppUiCopy.close,
                          onTap: onClose ?? () => Navigator.of(context).pop(),
                          excludeSemantics: true,
                          child: IconButton(
                            onPressed:
                                onClose ?? () => Navigator.of(context).pop(),
                            icon: const Icon(Icons.close),
                            iconSize: icons.lg,
                            color: colors.textSecondary,
                            tooltip: AppUiCopy.close,
                          ),
                        ),
                    ],
                  ),
                if (title != null) SizedBox(height: spacing.space4),
                Flexible(child: child),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
