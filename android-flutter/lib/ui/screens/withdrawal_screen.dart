/// The withdrawal screen: the point-amount form, its two-step confirmation and
/// the withdrawal history.
///
/// Port of `android-app/.../ui/screens/account/WithdrawalScreen.kt`, rebuilt
/// with the ShortLink design system. The screen class itself is stateless:
/// state is read through `context.watch<AppStateController>().state` and every
/// action dispatches on that controller; the form keeps only the local draft
/// state (the typed amount and the confirm step) that a form must own.
///
/// Phase 3: the page chrome (title, back affordance, page padding, safe area,
/// scrolling, bottom inset) comes from the shell's `AppSubPage`, so this screen
/// only renders its sections.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_language.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_typography.dart';
import '../../ui/components/app_button.dart';
import '../../ui/components/app_empty_state.dart';
import '../../ui/components/app_text_field.dart';

/// Vertical gap between the page sections.
const double _sectionGap = 14;

/// Gap between rows inside a panel.
const double _panelGap = 10;

/// Padding inside a surface panel.
const EdgeInsets _panelPadding = EdgeInsets.all(16);

/// Gap between the two confirm-step buttons.
const double _confirmGap = 10;

/// Sentinel for an absent summary, so the submit gate stays closed.
const int _noSummaryMinimum = 2147483647;

/// The withdrawal screen.
///
/// Shows the conversion lines, the digits-only point-amount field (whose idle
/// placeholder swaps to the "arrives in about a day" copy on focus loss), the
/// wallet address line, the submit action behind a two-step confirmation, and
/// the withdrawal history newest first. Status colour follows the label
/// semantics of [withdrawalStatusLabel], not the raw status string - the
/// deliberate fix recorded in `docs/parity-matrix.md` G16/D11.
class WithdrawalScreen extends StatelessWidget {
  /// Creates the withdrawal screen. The shell instantiates it without
  /// arguments.
  const WithdrawalScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    final AppStrings copy = strings(state.language);
    final bool loggedIn = state.session != null;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        if (!loggedIn)
          _GuestPanel(copy: copy, onSignIn: controller.showAuthPrompt)
        else
          _WithdrawalFormSection(copy: copy),
        const SizedBox(height: _sectionGap),
        _WithdrawalHistorySection(copy: copy),
      ],
    );
  }
}

/// The guest panel: sign-in prompt copy and the sign-in action.
class _GuestPanel extends StatelessWidget {
  const _GuestPanel({required this.copy, required this.onSignIn});

  final AppStrings copy;
  final VoidCallback onSignIn;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(
          copy.fundsLoginRequired,
          style: textTheme.titleMedium?.copyWith(color: colors.accentPrimary),
        ),
        const SizedBox(height: _panelGap),
        AppButton(
          label: copy.accountGuestSignIn,
          onPressed: onSignIn,
          isExpanded: true,
        ),
      ],
    );
  }
}

/// A raised surface card with a hairline border.
class _Panel extends StatelessWidget {
  const _Panel({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: shapes.containerRadius,
        border: Border.all(
          color: colors.hairline,
          width: shapes.strokeHairline,
        ),
      ),
      child: Padding(padding: _panelPadding, child: child),
    );
  }
}

/// The form panel hosting the two-step withdrawal form.
class _WithdrawalFormSection extends StatelessWidget {
  const _WithdrawalFormSection({required this.copy});

  final AppStrings copy;

  @override
  Widget build(BuildContext context) {
    return _Panel(child: _WithdrawalForm(copy: copy));
  }
}

/// The two-step form state machine.
///
/// Step one is the amount form; step two restates the amount and the
/// conversion lines before dispatching
/// [AppStateController.submitWithdrawal]. The conversion lines, the fee ceil
/// and the truncated USDT estimate all come from the shared
/// [withdrawalConversionLines] formatter, exactly as in the Kotlin screen.
class _WithdrawalForm extends StatefulWidget {
  const _WithdrawalForm({required this.copy});

  final AppStrings copy;

  @override
  State<_WithdrawalForm> createState() => _WithdrawalFormState();
}

class _WithdrawalFormState extends State<_WithdrawalForm> {
  final TextEditingController _pointsController = TextEditingController();
  final FocusNode _pointsFocus = FocusNode();
  bool _pointsFocused = false;
  bool _confirming = false;

  @override
  void initState() {
    super.initState();
    _pointsFocus.addListener(_handleFocusChange);
  }

  @override
  void dispose() {
    _pointsFocus.removeListener(_handleFocusChange);
    _pointsFocus.dispose();
    _pointsController.dispose();
    super.dispose();
  }

  /// Tracks focus so the idle placeholder swaps to the arrival hint on focus
  /// loss while the field is empty.
  void _handleFocusChange() {
    final bool focused = _pointsFocus.hasFocus;
    if (focused != _pointsFocused) {
      setState(() => _pointsFocused = focused);
    }
  }

  /// The typed point amount; non-digits never reach this parse because the
  /// field filters to digits.
  int get _points => int.tryParse(_pointsController.text) ?? 0;

  /// The submit gate, mirroring the Kotlin condition exactly: a summary with a
  /// bound wallet, an amount at or above the dynamic minimum and at or below
  /// the available balance.
  bool _canSubmit(bool submitting) {
    if (submitting) {
      return false;
    }
    final WithdrawalSummary? summary = context
        .read<AppStateController>()
        .state
        .withdrawalSummary;
    final int minimum = summary?.minimumPoints ?? _noSummaryMinimum;
    final int available = summary?.availablePoints ?? 0;
    return summary?.walletAddress != null &&
        _points >= minimum &&
        _points <= available;
  }

  void _beginConfirm() {
    setState(() => _confirming = true);
  }

  void _cancelConfirm() {
    setState(() => _confirming = false);
  }

  void _dispatchSubmit() {
    final int points = _points;
    final AppStateController controller = context.read<AppStateController>();
    setState(() => _confirming = false);
    unawaited(controller.submitWithdrawal(points));
  }

  /// Keeps the field digits-only, as a second guard beside the input
  /// formatter (Compose filtered every change through `Char::isDigit`).
  void _handlePointsChanged(String value) {
    final String digits = value.replaceAll(RegExp('[^0-9]'), '');
    if (digits != value) {
      _pointsController.value = TextEditingValue(
        text: digits,
        selection: TextSelection.collapsed(offset: digits.length),
      );
      return;
    }
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    final bool submitting =
        state.accountOperation == AccountOperation.withdrawal;

    if (_confirming) {
      return _ConfirmStep(
        copy: widget.copy,
        points: _points,
        busy: submitting,
        onConfirm: _canSubmit(submitting) ? _dispatchSubmit : null,
        onCancel: _cancelConfirm,
      );
    }
    return _AmountStep(
      copy: widget.copy,
      state: state,
      controller: _pointsController,
      focusNode: _pointsFocus,
      pointsFocused: _pointsFocused,
      busy: submitting,
      canSubmit: _canSubmit(submitting),
      onChanged: _handlePointsChanged,
      onSubmit: _canSubmit(submitting) ? _beginConfirm : null,
    );
  }
}

/// Step one: the amount form.
class _AmountStep extends StatelessWidget {
  const _AmountStep({
    required this.copy,
    required this.state,
    required this.controller,
    required this.focusNode,
    required this.pointsFocused,
    required this.busy,
    required this.canSubmit,
    required this.onChanged,
    required this.onSubmit,
  });

  final AppStrings copy;
  final AppUiState state;
  final TextEditingController controller;
  final FocusNode focusNode;
  final bool pointsFocused;
  final bool busy;
  final bool canSubmit;
  final ValueChanged<String> onChanged;
  final VoidCallback? onSubmit;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final WithdrawalSummary? summary = state.withdrawalSummary;
    final int points = int.tryParse(controller.text) ?? 0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        for (final String line in withdrawalConversionLines(
          summary,
          points,
          state.language,
        ))
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: Text(
              line,
              style: textTheme.bodyMedium?.copyWith(
                color: colors.textSecondary,
              ),
            ),
          ),
        Text(
          _walletLine(summary),
          style: textTheme.bodySmall?.copyWith(
            color: summary?.walletAddress == null
                ? colors.accentDanger
                : colors.textSecondary,
          ),
        ),
        const SizedBox(height: _panelGap),
        AppTextField(
          label: copy.accountWithdrawPointAmountLabel,
          controller: controller,
          focusNode: focusNode,
          hintText: withdrawalPointAmountPlaceholder(
            controller.text,
            pointsFocused,
            state.language,
          ),
          keyboardType: TextInputType.number,
          textInputAction: TextInputAction.done,
          inputFormatters: <TextInputFormatter>[
            FilteringTextInputFormatter.digitsOnly,
          ],
          onChanged: onChanged,
        ),
        const SizedBox(height: _panelGap),
        AppButton(
          label: copy.accountWithdrawSubmitAction,
          onPressed: canSubmit ? onSubmit : null,
          isLoading: busy,
          isExpanded: true,
        ),
      ],
    );
  }

  /// The wallet line: `network · address`, or the bind-first copy in the
  /// danger accent when no wallet is bound.
  String _walletLine(WithdrawalSummary? summary) {
    final String? walletAddress = summary?.walletAddress;
    if (walletAddress == null) {
      return copy.accountWithdrawWalletRequired;
    }
    final String network = state.wallet?.network ?? 'ERC20';
    return '$network · $walletAddress';
  }
}

/// Step two: the confirmation before dispatching the withdrawal.
///
/// Restates the amount and the conversion lines (the authoritative fee/USDT
/// arithmetic of [withdrawalConversionLines]) plus the shared confirmation
/// copy before the submit dispatches.
class _ConfirmStep extends StatelessWidget {
  const _ConfirmStep({
    required this.copy,
    required this.points,
    required this.busy,
    required this.onConfirm,
    required this.onCancel,
  });

  final AppStrings copy;
  final int points;
  final bool busy;
  final VoidCallback? onConfirm;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final AppLanguage language = state.language;
    final WithdrawalSummary? summary = state.withdrawalSummary;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(accountConfirmationTitle(language), style: textTheme.titleMedium),
        const SizedBox(height: _panelGap),
        Text(
          '${copy.accountWithdrawSubmitAction}: '
          '$points${copy.accountPointsSuffix}',
          style: textTheme.bodyLarge,
        ),
        for (final String line in withdrawalConversionLines(
          summary,
          points,
          language,
        ))
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
              line,
              style: textTheme.bodyMedium?.copyWith(
                color: colors.textSecondary,
              ),
            ),
          ),
        const SizedBox(height: 4),
        Text(
          accountConfirmationBody(
            '$points${copy.accountPointsSuffix}',
            language,
          ),
          style: textTheme.bodySmall?.copyWith(color: colors.textSecondary),
        ),
        const SizedBox(height: _panelGap),
        Row(
          children: <Widget>[
            Expanded(
              child: AppButton(
                label: accountConfirmationConfirm(language),
                onPressed: onConfirm,
                isLoading: busy,
                isExpanded: true,
              ),
            ),
            const SizedBox(width: _confirmGap),
            Expanded(
              child: AppButton(
                label: accountConfirmationCancel(language),
                onPressed: busy ? null : onCancel,
                variant: AppButtonVariant.secondary,
                isExpanded: true,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

/// The withdrawal history section: heading, rows newest first, empty state.
class _WithdrawalHistorySection extends StatelessWidget {
  const _WithdrawalHistorySection({required this.copy});

  final AppStrings copy;

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final TextTheme textTheme = Theme.of(context).textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(copy.accountWithdrawalRecordsTitle, style: textTheme.titleMedium),
        const SizedBox(height: _panelGap),
        if (state.withdrawals.isEmpty)
          AppEmptyState(
            icon: Icons.receipt_long,
            title: copy.accountWithdrawalRecordsTitle,
            pillLabel: copy.accountWithdrawalRecordsEmpty,
          )
        else
          for (final WithdrawalRecord record in state.withdrawals)
            _WithdrawalHistoryRow(record: record, copy: copy),
      ],
    );
  }
}

/// One withdrawal history row.
///
/// The status colour is derived from the label's semantics ("Pending",
/// "Completed", "Rejected"), never from the raw status string, so an
/// `APPROVED` record takes the success accent - the deliberate G16/D11 fix.
class _WithdrawalHistoryRow extends StatelessWidget {
  const _WithdrawalHistoryRow({required this.record, required this.copy});

  final WithdrawalRecord record;
  final AppStrings copy;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography typography = Theme.of(context)
        .extension<AppTypography>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final AppLanguage language = context
        .watch<AppStateController>()
        .state
        .language;
    final String statusLabel = withdrawalStatusLabel(record.status, language);
    // Rejected reasons and settlement tx hashes ride along, exactly like the
    // Kotlin `withdrawalRecordDetail` line.
    final String? detail = withdrawalRecordDetail(
      record.status,
      record.adminNote,
      record.txHash,
      language,
    );
    final Color statusColor = switch (statusLabel) {
      'Completed' => colors.accentSuccess,
      'Rejected' => colors.accentDanger,
      'Pending' => colors.accentWarning,
      _ => colors.textSecondary,
    };

    return Padding(
      padding: const EdgeInsets.only(bottom: _panelGap),
      child: _Panel(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                Expanded(
                  child: Text(
                    '${record.pointAmount} pts · ${record.usdtAmount} USDT',
                    style: typography.numeric,
                  ),
                ),
                Text(
                  statusLabel,
                  style: textTheme.bodySmall?.copyWith(color: statusColor),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              '${record.network} · ${record.walletAddress}',
              style: textTheme.bodySmall?.copyWith(color: colors.textSecondary),
            ),
            if (detail != null)
              Text(
                detail,
                style: textTheme.bodySmall?.copyWith(
                  color: colors.textSecondary,
                ),
              ),
          ],
        ),
      ),
    );
  }
}
