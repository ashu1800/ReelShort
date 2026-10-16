/// The design system's confirmation dialog.
///
/// Three confirmations existed in three shapes: the wallet-unbind dialog, the
/// update dialog's hand-rolled `AlertDialog`, and the withdrawal flow's inline
/// "confirm" step that was not a dialog at all. [AppConfirmDialog.show] is the
/// single destructive/irreversible confirmation: it blocks dismissal while the
/// confirmed action is running, styles the confirm button by consequence and
/// keeps the buttons reachable at large text scales.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';
import 'app_button.dart';

/// A modal confirmation with a primary confirm and a secondary cancel.
class AppConfirmDialog extends StatelessWidget {
  /// Creates a confirmation dialog.
  const AppConfirmDialog({
    required this.title,
    required this.body,
    required this.confirmLabel,
    required this.cancelLabel,
    this.onConfirm,
    this.onCancel,
    this.destructive = false,
    this.busy = false,
    this.confirmIcon,
    super.key,
  });

  /// Dialog title.
  final String title;

  /// Dialog body.
  final String body;

  /// Confirm button label.
  final String confirmLabel;

  /// Cancel button label.
  final String cancelLabel;

  /// Called when the user confirms.
  final VoidCallback? onConfirm;

  /// Called when the user cancels.
  final VoidCallback? onCancel;

  /// Styles the confirm action as destructive and announces it as such.
  final bool destructive;

  /// Disables both actions while the confirmed action runs.
  final bool busy;

  /// Optional icon on the confirm button.
  final IconData? confirmIcon;

  /// Shows the dialog and resolves to `true` when the user confirmed.
  static Future<bool> show(
    BuildContext context, {
    required String title,
    required String body,
    required String confirmLabel,
    required String cancelLabel,
    bool destructive = false,
    IconData? confirmIcon,
  }) async {
    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (BuildContext dialogContext) => AppConfirmDialog(
        title: title,
        body: body,
        confirmLabel: confirmLabel,
        cancelLabel: cancelLabel,
        destructive: destructive,
        confirmIcon: confirmIcon,
        onConfirm: () => Navigator.of(dialogContext).pop(true),
        onCancel: () => Navigator.of(dialogContext).pop(false),
      ),
    );
    return confirmed ?? false;
  }

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return PopScope(
      canPop: !busy,
      child: AlertDialog(
        title: Text(title, style: roles.screenTitle),
        content: Text(
          body,
          style: roles.body.copyWith(color: colors.textSecondary),
        ),
        actions: <Widget>[
          AppButton(
            label: cancelLabel,
            variant: AppButtonVariant.secondary,
            size: AppButtonSize.compact,
            onPressed: busy ? null : onCancel,
          ),
          AppButton(
            label: confirmLabel,
            icon: confirmIcon,
            variant: destructive
                ? AppButtonVariant.destructive
                : AppButtonVariant.primary,
            size: AppButtonSize.compact,
            onPressed: busy ? null : onConfirm,
            isLoading: busy,
          ),
        ],
        actionsPadding: EdgeInsets.fromLTRB(
          spacing.space4,
          0,
          spacing.space4,
          spacing.space3,
        ),
      ),
    );
  }
}
