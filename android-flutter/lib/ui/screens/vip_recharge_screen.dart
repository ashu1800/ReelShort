/// The VIP recharge screen: order creation, the TRC20 payment details with a
/// locally generated QR code and the pending-order countdown.
///
/// Port of `android-app/.../ui/screens/account/VipRechargeScreen.kt`, rebuilt
/// with the ShortLink design system. The screen class itself is stateless:
/// state is read through `context.watch<AppStateController>().state` and every
/// action dispatches on that controller; the countdown keeps only the local
/// ticking state that a timer must own. No payment polling runs on this
/// screen - the user refreshes manually with the refresh action.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../core/formatting/app_strings.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_typography.dart';
import '../../ui/components/app_button.dart';

/// Horizontal padding and bottom scroll inset of the VIP page.
const EdgeInsets _pagePadding = EdgeInsets.fromLTRB(18, 4, 18, 28);

/// Gap between rows inside the panel.
const double _panelGap = 10;

/// Padding inside the surface panel.
const EdgeInsets _panelPadding = EdgeInsets.all(16);

/// Side length of the generated TRC20 payment QR code.
const double _qrSide = 184;

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
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    final AppStrings copy = strings(state.language);

    return Column(
      children: <Widget>[
        _VipHeader(title: copy.vipPayTitle, onBack: controller.backToFunds),
        Expanded(
          child: ListView(
            padding: _pagePadding,
            children: <Widget>[
              _VipOrderPanel(copy: copy),
            ],
          ),
        ),
      ],
    );
  }
}

/// The page header: back action and the screen title.
class _VipHeader extends StatelessWidget {
  const _VipHeader({required this.title, required this.onBack});

  final String title;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 4, 18, 0),
      child: Row(
        children: <Widget>[
          IconButton(
            onPressed: onBack,
            icon: const Icon(Icons.arrow_back),
            color: colors.textPrimary,
            tooltip: title,
          ),
          Expanded(
            child: Text(title, style: Theme.of(context).textTheme.titleLarge),
          ),
        ],
      ),
    );
  }
}

/// The order panel; every branch of the Kotlin decision tree lives here.
///
/// The Kotlin screen computes `activePending = pending.takeIf { expiresAt ==
/// null || remaining > 0 }` on every recomposition, so a countdown that runs
/// out drops the panel straight into the create branch; the one-second ticker
/// therefore raises an [onExpired] rebuild callback instead of staying
/// private to the details widget.
class _VipOrderPanel extends StatefulWidget {
  const _VipOrderPanel({required this.copy});

  final AppStrings copy;

  @override
  State<_VipOrderPanel> createState() => _VipOrderPanelState();
}

class _VipOrderPanelState extends State<_VipOrderPanel> {
  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    final bool loggedIn = state.session != null;
    final AccountOperation? operation = state.accountOperation;
    final VipOrder? latest = state.latestVipOrder;
    final VipOrder? pending =
        latest != null && latest.status == 'PENDING' ? latest : null;
    // `activePending`: an order with no expiry, or one whose countdown still
    // runs. An expired PENDING order falls through to the create branch.
    final bool pendingLive = pending != null &&
        (pending.expiresAt == null || remainingSeconds(pending.expiresAt) > 0);

    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: shapes.containerRadius,
        border: Border.all(
          color: colors.hairline,
          width: shapes.strokeHairline,
        ),
      ),
      child: Padding(
        padding: _panelPadding,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            if (!loggedIn) ...<Widget>[
              Text(
                widget.copy.fundsLoginRequired,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: colors.accentPrimary,
                    ),
              ),
              const SizedBox(height: _panelGap),
              AppButton(
                label: widget.copy.accountGuestSignIn,
                onPressed: controller.showAuthPrompt,
                isExpanded: true,
              ),
            ] else if (state.vipActive) ...<Widget>[
              Text(
                widget.copy.vipActiveLabel,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: colors.accentSuccess,
                    ),
              ),
              if (state.vipUntil != null)
                Text(
                  '${widget.copy.vipExpiryLabel}${state.vipUntil}',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: colors.textSecondary,
                      ),
                ),
            ] else if (latest != null && latest.status == 'CONFIRMED') ...<Widget>[
              _VipAmountLine(order: latest, copy: widget.copy),
              Text(
                '${widget.copy.vipPayOrderNo}${latest.orderNo}',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: colors.textSecondary,
                    ),
              ),
              Text(
                widget.copy.vipPayStatusConfirmed,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: colors.accentSuccess,
                    ),
              ),
              const SizedBox(height: _panelGap),
              _VipRefreshButton(
                copy: widget.copy,
                busy: operation != null,
                onPressed: operation == null
                    ? () {
                        unawaited(controller.refreshLatestVipOrder());
                      }
                    : null,
              ),
            ] else if (pending != null && pendingLive) ...<Widget>[
              _VipPendingDetails(
                order: pending,
                copy: widget.copy,
                onExpired: () => setState(() {}),
              ),
            ] else ...<Widget>[
              _VipCreateSection(
                copy: widget.copy,
                latest: latest,
                operation: operation,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// The pending order section: amount, order number, payment details,
/// countdown, hint and refresh. It keeps the one-second ticking countdown.
class _VipPendingDetails extends StatefulWidget {
  const _VipPendingDetails({
    required this.order,
    required this.copy,
    required this.onExpired,
  });

  final VipOrder order;
  final AppStrings copy;

  /// Called once when the countdown reaches zero, so the panel re-routes to
  /// the create branch without waiting for a manual refresh.
  final VoidCallback onExpired;

  @override
  State<_VipPendingDetails> createState() => _VipPendingDetailsState();
}

class _VipPendingDetailsState extends State<_VipPendingDetails> {
  Timer? _ticker;
  int _remaining = 0;

  @override
  void initState() {
    super.initState();
    _remaining = remainingSeconds(widget.order.expiresAt);
    _startTicker();
  }

  @override
  void didUpdateWidget(covariant _VipPendingDetails oldWidget) {
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

  /// Ticks the countdown down once a second until it reaches zero, exactly
  /// like the Kotlin `LaunchedEffect` loop.
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
    final TextTheme textTheme = Theme.of(context).textTheme;
    final AppStateController controller = context.watch<AppStateController>();
    final AccountOperation? operation = controller.state.accountOperation;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        _VipAmountLine(order: widget.order, copy: widget.copy),
        Text(
          '${widget.copy.vipPayOrderNo}${widget.order.orderNo}',
          style: textTheme.bodyMedium?.copyWith(color: colors.textSecondary),
        ),
        _VipPaymentDetails(
          address: widget.order.receivingWalletAddress ??
              controller.state.vipCollectionAddress,
          copy: widget.copy,
          busy: operation != null,
        ),
        Text(
          '${widget.copy.vipPayExpiresIn} '
          '${_remaining ~/ 60}:${(_remaining % 60).toString().padLeft(2, '0')}',
          style: textTheme.bodyLarge?.copyWith(color: colors.accentPrimary),
        ),
        Text(
          widget.copy.vipPayHint,
          style: textTheme.bodySmall?.copyWith(color: colors.textSecondary),
        ),
        const SizedBox(height: _panelGap),
        _VipRefreshButton(
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

/// The confirmed order amount line.
class _VipAmountLine extends StatelessWidget {
  const _VipAmountLine({required this.order, required this.copy});

  final VipOrder order;
  final AppStrings copy;

  @override
  Widget build(BuildContext context) {
    final AppTypography typography =
        Theme.of(context).extension<AppTypography>()!;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    return Text(
      '${copy.vipPayAmount}${order.effectiveAmount} USDT',
      style: typography.numeric.copyWith(color: colors.accentPrimary),
    );
  }
}

/// The payment details: address label, address (or N/A), QR code and copy.
///
/// The QR payload is the raw Base58 address, generated locally by
/// `qr_flutter` - no HTTP QR service. When the encoder yields nothing the
/// area renders nothing.
class _VipPaymentDetails extends StatelessWidget {
  const _VipPaymentDetails({
    required this.address,
    required this.copy,
    required this.busy,
  });

  final String address;
  final AppStrings copy;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final bool hasAddress = address.trim().isNotEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        Text(
          copy.vipPayAddress,
          style: textTheme.bodyMedium?.copyWith(color: colors.textSecondary),
        ),
        Text(
          address.isEmpty ? 'N/A' : address,
          textAlign: TextAlign.center,
          style: textTheme.bodyLarge,
        ),
        if (hasAddress) ...<Widget>[
          const SizedBox(height: _panelGap),
          _QrCodeImage(address: address),
          const SizedBox(height: _panelGap),
          AppButton(
            label: copy.vipPayCopy,
            onPressed: busy
                ? null
                : () {
                    Clipboard.setData(ClipboardData(text: address));
                  },
            variant: AppButtonVariant.secondary,
            isExpanded: true,
          ),
        ],
      ],
    );
  }
}

/// The locally generated QR code.
///
/// `QrImageView` is a widget, not a service: the payload is the raw Base58
/// address itself. If the encoder throws, the error builder renders nothing,
/// satisfying the "encoder yields nothing, render nothing" contract.
class _QrCodeImage extends StatelessWidget {
  const _QrCodeImage({required this.address});

  final String address;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppStrings copy =
        strings(context.watch<AppStateController>().state.language);
    return Semantics(
      label: copy.vipPayQrHint,
      child: QrImageView(
        data: address,
        size: _qrSide,
        backgroundColor: Colors.transparent,
        eyeStyle: QrEyeStyle(
          eyeShape: QrEyeShape.square,
          color: colors.textPrimary,
        ),
        dataModuleStyle: QrDataModuleStyle(
          dataModuleShape: QrDataModuleShape.square,
          color: colors.textPrimary,
        ),
        errorStateBuilder: (BuildContext context, Object? error) =>
            const SizedBox.shrink(),
      ),
    );
  }
}

/// The not-active, not-pending section: prior-order status, price, unlock.
class _VipCreateSection extends StatelessWidget {
  const _VipCreateSection({
    required this.copy,
    required this.latest,
    required this.operation,
  });

  final AppStrings copy;
  final VipOrder? latest;
  final AccountOperation? operation;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    final VipOrder? order = latest;
    final bool canCreate = state.session != null &&
        !state.vipActive &&
        operation == null &&
        (order == null ||
            order.status == 'EXPIRED' ||
            order.status == 'REJECTED' ||
            order.status == 'PENDING');

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        if (order?.status == 'REJECTED')
          Text(
            copy.vipPayStatusRejected,
            style: textTheme.bodyMedium?.copyWith(color: colors.accentDanger),
          )
        else if (order?.status == 'EXPIRED')
          Text(
            copy.vipPayExpired,
            style: textTheme.bodyMedium?.copyWith(color: colors.accentDanger),
          ),
        Text(
          '${state.vipPriceUsdt.isEmpty ? '--' : state.vipPriceUsdt}'
          '${copy.accountVipPriceSuffix}',
          style: textTheme.titleMedium?.copyWith(color: colors.accentPrimary),
        ),
        const SizedBox(height: _panelGap),
        AppButton(
          label: operation != null ? copy.vipPayCreating : copy
              .accountVipUnlockAction,
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

/// The refresh action button shared by the confirmed and pending branches.
class _VipRefreshButton extends StatelessWidget {
  const _VipRefreshButton({
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
