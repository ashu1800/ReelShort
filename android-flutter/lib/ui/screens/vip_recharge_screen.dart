/// The VIP recharge screen: order creation, the TRC20 payment details with a
/// locally generated QR code and the pending-order countdown.
///
/// Port of `android-app/.../ui/screens/account/VipRechargeScreen.kt`, rebuilt
/// with the ShortLink design system (`docs/ui-redesign-notes.md` §7.10). The
/// screen class itself is stateless: state is read through
/// `context.watch<AppStateController>().state` and every action dispatches on
/// that controller; the countdown keeps only the local ticking state a timer
/// must own. No payment polling runs here - the user refreshes manually.
///
/// Phase 3 provides the page chrome (title, back affordance, page padding, safe
/// area, scrolling, bottom inset) through the shell's `AppSubPage`; this screen
/// renders the membership header and one section card per order state. The
/// branch decision tree, the buttons' actions and every piece of copy are the
/// port's own, untouched by the redesign.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/app_ui_copy.dart';
import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';
import '../../ui/components/app_amount_block.dart';
import '../../ui/components/app_button.dart';
import '../../ui/components/app_list_row.dart';
import '../../ui/components/app_status_pill.dart';
import '../../ui/components/app_loading.dart';
import '../../ui/components/app_membership_chip.dart';
import '../../ui/components/app_section.dart';
import '../../ui/components/ledger_row.dart';

/// Side length of the generated TRC20 payment QR code.
const double _qrSide = 184;

/// Padding of the white QR plate around the code (the scanning quiet zone).
const double _qrQuietZone = 12;

/// The one-second tick of the pending-order countdown.
const Duration _countdownTick = Duration(seconds: 1);

/// The VIP recharge screen.
///
/// Shows the active VIP state, the latest confirmed order, the pending TRC20
/// payment details (raw Base58 address, locally generated QR, copy action and
/// an `M:SS` countdown) or the price and unlock action. Start the order with
/// [AppStateController.createVipOrder]; refresh payment state with
/// [AppStateController.refreshLatestVipOrder].
class VipRechargeScreen extends StatelessWidget {
  /// Creates the VIP recharge screen. The shell instantiates it without
  /// arguments.
  const VipRechargeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final AppSpacing spacing = appSpacingOf(context);
    final AppStrings copy = strings(
      context.watch<AppStateController>().state.language,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        _VipOrderPanel(copy: copy),
        SizedBox(height: spacing.sectionGap),
      ],
    );
  }
}

/// The order panel; every branch of the Kotlin decision tree lives here.
///
/// The Kotlin screen computes `activePending = pending.takeIf { expiresAt ==
/// null || remaining > 0 }` on every recomposition, so a countdown that runs
/// out drops the panel straight into the create branch; the one-second ticker
/// therefore raises an [onExpired] rebuild callback instead of staying private
/// to the details widget.
class _VipOrderPanel extends StatefulWidget {
  const _VipOrderPanel({required this.copy});

  final AppStrings copy;

  @override
  State<_VipOrderPanel> createState() => _VipOrderPanelState();
}

class _VipOrderPanelState extends State<_VipOrderPanel> {
  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    final AppSpacing spacing = appSpacingOf(context);
    final bool loggedIn = state.session != null;
    final AccountOperation? operation = state.accountOperation;
    final VipOrder? latest = state.latestVipOrder;
    final VipOrder? pending = latest != null && latest.status == 'PENDING'
        ? latest
        : null;
    // `activePending`: an order with no expiry, or one whose countdown still
    // runs. An expired PENDING order falls through to the create branch.
    final bool pendingLive =
        pending != null &&
        (pending.expiresAt == null || remainingSeconds(pending.expiresAt) > 0);
    final bool confirmed = latest != null && latest.status == 'CONFIRMED';

    if (!loggedIn) {
      return _GuestPanel(copy: widget.copy, onSignIn: controller.showAuthPrompt);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        _MembershipHeader(state: state, copy: widget.copy),
        SizedBox(height: spacing.sectionGap),
        AppSectionHeader(title: widget.copy.vipPayTitle),
        SizedBox(height: spacing.space2),
        if (state.vipActive)
          _StatusCard(
            icon: Icons.verified_outlined,
            tone: AppStatusTone.success,
            label: widget.copy.accountVipStatusActive,
          )
        else if (confirmed)
          _ConfirmedOrder(order: latest, copy: widget.copy, operation: operation)
        else if (pendingLive)
          _PendingOrder(
            order: pending,
            copy: widget.copy,
            onExpired: () => setState(() {}),
          )
        else
          _CreateOrder(copy: widget.copy, latest: latest),
      ],
    );
  }
}

/// The membership header: status pill plus the expiry caption.
///
/// While the account snapshot is unknown the header shows a neutral plate
/// instead of claiming "VIP not active" - the same contract as Me and Funds.
class _MembershipHeader extends StatelessWidget {
  const _MembershipHeader({required this.state, required this.copy});

  final AppUiState state;
  final AppStrings copy;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final bool active = state.vipActive;
    final String? until = state.vipUntil;
    final bool known = state.pointAccount != null;
    final double textScale = MediaQuery.textScalerOf(context).scale(1);

    if (!known) {
      return AppSectionCard(
        children: <Widget>[
          Row(
            children: <Widget>[
              AppSkeleton(
                width: membershipChipPlaceholderSize.width * textScale,
                height: membershipChipPlaceholderSize.height * textScale,
                radius: shapes.radiusInline,
              ),
              SizedBox(width: spacing.space3),
              const Expanded(child: AppSkeletonText(lines: 1)),
            ],
          ),
        ],
      );
    }

    return AppSectionCard(
      children: <Widget>[
        Row(
          children: <Widget>[
            // Flexed: the chip's own label ellipsises instead of pushing this
            // row past the card at 3.2x.
            Flexible(
              child: AppMembershipChip(
                active: active,
                label: active
                    ? copy.accountVipStatusActive
                    : copy.accountVipStatusInactive,
              ),
            ),
            if (active && until != null) ...<Widget>[
              SizedBox(width: spacing.space3),
              Flexible(
                child: Text(
                  '${copy.accountVipExpiryPrefix}${dateOnlyLabel(until)}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: roles.caption.copyWith(color: colors.textSecondary),
                ),
              ),
            ],
          ],
        ),
      ],
    );
  }
}

/// A status-only card: the pill alone carries the state word.
class _StatusCard extends StatelessWidget {
  const _StatusCard({
    required this.icon,
    required this.tone,
    required this.label,
  });

  final IconData icon;
  final AppStatusTone tone;
  final String label;

  @override
  Widget build(BuildContext context) {
    // The pill is the only label: a second copy of the same word beside it read
    // as duplicated content on device.
    return AppSectionCard(
      children: <Widget>[AppStatusPill(tone: tone, icon: icon, label: label)],
    );
  }
}

/// The pending order: amount, order number, validity, payment address, QR code,
/// countdown and refresh. Keeps the one-second ticking countdown.
class _PendingOrder extends StatefulWidget {
  const _PendingOrder({
    required this.order,
    required this.copy,
    required this.onExpired,
  });

  final VipOrder order;
  final AppStrings copy;

  /// Called once when the countdown reaches zero, so the panel re-routes to the
  /// create branch without waiting for a manual refresh.
  final VoidCallback onExpired;

  @override
  State<_PendingOrder> createState() => _PendingOrderState();
}

class _PendingOrderState extends State<_PendingOrder> {
  Timer? _ticker;
  int _remaining = 0;

  @override
  void initState() {
    super.initState();
    _remaining = remainingSeconds(widget.order.expiresAt);
    _startTicker();
  }

  @override
  void didUpdateWidget(covariant _PendingOrder oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.order.expiresAt != widget.order.expiresAt) {
      setState(() {
        _remaining = remainingSeconds(widget.order.expiresAt);
      });
      _startTicker();
    }
  }

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }

  /// Ticks the countdown down once a second until it reaches zero, exactly like
  /// the Kotlin `LaunchedEffect` loop.
  void _startTicker() {
    _ticker?.cancel();
    if (_remaining <= 0) {
      return;
    }
    _ticker = Timer.periodic(_countdownTick, (Timer timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      if (_remaining > 0) {
        setState(() => _remaining -= 1);
      } else {
        timer.cancel();
        widget.onExpired();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStateController controller = context.watch<AppStateController>();
    final AccountOperation? operation = controller.state.accountOperation;
    final String address =
        widget.order.receivingWalletAddress ??
        controller.state.vipCollectionAddress;
    final String countdown =
        '${_remaining ~/ 60}:${(_remaining % 60).toString().padLeft(2, '0')}';

    return AppSectionCard(
      children: <Widget>[
        AppAmountBlock(
          label: widget.copy.vipPayAmount,
          // The unit rides in the value: this screen's tests pin the rendered
          // single string, and the test file is outside this task's write scope.
          // AppAmountBlock now flexes its unit slot, so restoring the split is
          // a follow-up one-liner plus an assertion adaptation.
          value: '${widget.order.effectiveAmount} USDT',
          size: AppAmountSize.large,
          valueColor: colors.accentPrimary,
        ),
        AppLedgerRow(
          label: widget.copy.vipPayOrderNo,
          value: widget.order.orderNo,
          valueColor: colors.textPrimary,
        ),
        AppLedgerRow(
          label: AppUiCopy.vipValidity,
          value: widget.order.expiresAt == null
              ? '--'
              : dateOnlyLabel(widget.order.expiresAt),
        ),
        AppListRow(
          title: widget.copy.vipPayAddress,
          subtitle: address.isEmpty ? 'N/A' : address,
          subtitleMaxLines: 1,
          divider: false,
          trailingTakesIntrinsicWidth: true,
          trailing: AppButton(
            label: widget.copy.vipPayCopy,
            variant: AppButtonVariant.tertiary,
            onPressed: operation == null
                ? () {
                    Clipboard.setData(ClipboardData(text: address));
                  }
                : null,
          ),
        ),
        if (address.trim().isNotEmpty) _PaymentQr(address: address),
        Text(
          '${widget.copy.vipPayExpiresIn} $countdown',
          style: roles.numeric.copyWith(color: colors.accentPrimary),
        ),
        Text(
          widget.copy.vipPayHint,
          style: roles.caption.copyWith(color: colors.textSecondary),
        ),
        SizedBox(height: spacing.space2),
        _RefreshButton(
          copy: widget.copy,
          busy: operation != null,
          onPressed: operation == null
              ? () {
                  unawaited(controller.refreshLatestVipOrder());
                }
              : null,
        ),
      ],
    );
  }
}

/// The confirmed order: amount, order number, the success status and refresh.
class _ConfirmedOrder extends StatelessWidget {
  const _ConfirmedOrder({
    required this.order,
    required this.copy,
    required this.operation,
  });

  final VipOrder order;
  final AppStrings copy;
  final AccountOperation? operation;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppStateController controller = context.read<AppStateController>();

    return AppSectionCard(
      children: <Widget>[
        AppAmountBlock(
          label: copy.vipPayAmount,
          value: '${order.effectiveAmount} USDT',
          size: AppAmountSize.large,
          valueColor: colors.accentPrimary,
        ),
        AppLedgerRow(
          label: copy.vipPayOrderNo,
          value: order.orderNo,
          divider: false,
        ),
        AppStatusPill(
          tone: AppStatusTone.success,
          icon: Icons.check_circle_outline,
          label: copy.vipPayStatusConfirmed,
        ),
        SizedBox(height: spacing.space2),
        _RefreshButton(
          copy: copy,
          busy: operation != null,
          onPressed: operation == null
              ? () {
                  unawaited(controller.refreshLatestVipOrder());
                }
              : null,
        ),
      ],
    );
  }
}

/// The not-active, not-pending section: prior-order status, price, unlock.
class _CreateOrder extends StatelessWidget {
  const _CreateOrder({required this.copy, required this.latest});

  final AppStrings copy;
  final VipOrder? latest;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    final VipOrder? order = latest;
    final AccountOperation? operation = state.accountOperation;
    final bool canCreate =
        state.session != null &&
        !state.vipActive &&
        operation == null &&
        (order == null ||
            order.status == 'EXPIRED' ||
            order.status == 'REJECTED' ||
            order.status == 'PENDING');
    final String price = state.vipPriceUsdt.isEmpty
        ? '--'
        : state.vipPriceUsdt;

    return AppSectionCard(
      children: <Widget>[
        if (order?.status == 'REJECTED')
          AppStatusPill(
            tone: AppStatusTone.danger,
            icon: Icons.error_outline,
            label: copy.vipPayStatusRejected,
          )
        else if (order?.status == 'EXPIRED') ...<Widget>[
          // The capsule carries the short status word; the long-form sentence
          // stays visible as a caption underneath.
          AppStatusPill(
            tone: AppStatusTone.danger,
            icon: Icons.schedule,
            label: AppUiCopy.statusExpired,
          ),
          Text(
            copy.vipPayExpired,
            style: roles.caption.copyWith(color: colors.textSecondary),
          ),
        ],
        AppAmountBlock(
          label: AppUiCopy.vipPrice,
          // The existing price line, verbatim: the figure plus its
          // `/ 30 days` suffix. One value lets it wrap instead of pushing a
          // long unit past the card at 3.2x.
          value: '$price${copy.accountVipPriceSuffix}',
          size: AppAmountSize.large,
          valueColor: colors.accentPrimary,
        ),
        SizedBox(height: spacing.space2),
        AppButton(
          label: operation != null
              ? copy.vipPayCreating
              : copy.accountVipUnlockAction,
          onPressed: canCreate
              ? () {
                  unawaited(controller.createVipOrder());
                }
              : null,
          isLoading: operation != null,
          isExpanded: true,
        ),
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
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return AppSectionCard(
      children: <Widget>[
        Text(
          copy.fundsLoginRequired,
          style: roles.body.copyWith(color: colors.accentPrimary),
        ),
        SizedBox(height: spacing.space2),
        AppButton(
          label: copy.accountGuestSignIn,
          onPressed: onSignIn,
          isExpanded: true,
        ),
      ],
    );
  }
}

/// The payment QR code on a white quiet-zone plate.
///
/// The plate and the modules are deliberately theme-independent: a dark-theme
/// QR would be light modules on a dark plate and no longer scannable.
class _PaymentQr extends StatelessWidget {
  const _PaymentQr({required this.address});

  final String address;

  @override
  Widget build(BuildContext context) {
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppStrings copy = strings(
      context.watch<AppStateController>().state.language,
    );
    final Color ink = AppColors.light.textPrimary;

    return Center(
      child: Semantics(
        label: copy.vipPayQrHint,
        excludeSemantics: true,
        child: Container(
          padding: EdgeInsets.all(_qrQuietZone),
          decoration: BoxDecoration(
            color: AppColors.light.surface,
            borderRadius: BorderRadius.circular(shapes.radiusControl),
          ),
          child: QrImageView(
            data: address,
            size: _qrSide,
            backgroundColor: AppColors.light.surface,
            eyeStyle: QrEyeStyle(eyeShape: QrEyeShape.square, color: ink),
            dataModuleStyle: QrDataModuleStyle(
              dataModuleShape: QrDataModuleShape.square,
              color: ink,
            ),
            errorStateBuilder: (BuildContext context, Object? error) =>
                const SizedBox.shrink(),
          ),
        ),
      ),
    );
  }
}

/// The refresh action shared by the confirmed and pending branches.
class _RefreshButton extends StatelessWidget {
  const _RefreshButton({
    required this.copy,
    required this.busy,
    required this.onPressed,
  });

  final AppStrings copy;
  final bool busy;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return AppButton(
      label: busy ? copy.vipPayRefreshing : copy.vipPayRefresh,
      // Secondary: the create step owns the screen's one primary action.
      variant: AppButtonVariant.secondary,
      onPressed: onPressed,
      isLoading: busy,
      isExpanded: true,
    );
  }
}

/// The remaining seconds of the pending order.
///
/// Mirrors the Kotlin `remainingSeconds`: parse the ISO-8601 expiry, subtract
/// from now, clamp to zero; a parse failure yields zero.
int remainingSeconds(String? expiresAt) {
  if (expiresAt == null) {
    return 0;
  }
  final DateTime? expiry = DateTime.tryParse(expiresAt);
  if (expiry == null) {
    return 0;
  }
  final Duration difference = expiry.difference(DateTime.now());
  return difference.isNegative ? 0 : difference.inSeconds;
}