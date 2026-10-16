/// VIP paywall sheet: the shared TRC20 payment modal.
///
/// Behavioural parity with the Kotlin `VipPaymentBottomSheet`: the four
/// branches (VIP active / CONFIRMED order / live PENDING order / create), the
/// 10-second polling loop while a live order exists, a per-second countdown
/// that formats `M:SS` from the order expiry (a parse failure counts as 0,
/// mirroring Kotlin `computeRemainingSeconds`), a locally generated QR over
/// the raw Base58 address validated with `QrValidator` before rendering
/// (invalid content renders nothing, like the Kotlin null-bitmap fallback),
/// copy to clipboard, and the exact `canCreateOrder` gate. Order confirmation
/// closes the sheet and resumes the pending episode — that transition lives in
/// the state layer; the sheet only reflects it.
///
/// Phase 3: the paywall is a modal route (`AppSheet.show` from the shell)
/// instead of a `Stack` child, so the route owns the barrier, the drag handle
/// and the system back gesture while this widget is only the sheet *body*: the
/// route's bottom sheet provides the surface, the shape and the drag handle,
/// and `AppSheet` provides the page padding, the keyboard inset and the route
/// semantics. The [PopScope] keeps the busy gate the close button also has.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_typography.dart';
import '../components/app_button.dart';

/// How often the sheet re-checks the latest VIP order, matching Kotlin.
const Duration _pollInterval = Duration(seconds: 10);

/// Square side of the payment QR code, in logical pixels.
const double _qrSide = 184;

/// The VIP payment sheet.
class VipPaywallSheet extends StatefulWidget {
  /// Creates the paywall sheet. The shell instantiates it without arguments.
  const VipPaywallSheet({super.key});

  @override
  State<VipPaywallSheet> createState() => _VipPaywallSheetState();
}

class _VipPaywallSheetState extends State<VipPaywallSheet> {
  Timer? _pollTimer;
  Timer? _countdownTicker;

  @override
  void dispose() {
    _pollTimer?.cancel();
    _countdownTicker?.cancel();
    super.dispose();
  }

  /// Keeps the poll and countdown timers alive only while a live order
  /// (PENDING or CONFIRMED) exists, mirroring the Kotlin
  /// `LaunchedEffect(refreshableOrder?.id, refreshableOrder?.status)` loop.
  void _setPolling(bool live) {
    if (live && _pollTimer == null) {
      _pollTimer = Timer.periodic(_pollInterval, (Timer timer) {
        if (!mounted) {
          timer.cancel();
          return;
        }
        context.read<AppStateController>().refreshLatestVipOrder();
      });
    } else if (!live && _pollTimer != null) {
      _pollTimer?.cancel();
      _pollTimer = null;
    }
    if (live && _countdownTicker == null) {
      _countdownTicker = Timer.periodic(const Duration(seconds: 1), (Timer _) {
        if (mounted) {
          setState(() {});
        }
      });
    } else if (!live && _countdownTicker != null) {
      _countdownTicker?.cancel();
      _countdownTicker = null;
    }
  }

  /// Remaining seconds until `expiresAt`; a parse failure or a past deadline
  /// counts as 0, mirroring Kotlin `computeRemainingSeconds`.
  static int _remainingSeconds(String? expiresAt, DateTime now) {
    final DateTime? parsed =
        expiresAt == null || expiresAt.isEmpty ? null : DateTime.tryParse(expiresAt);
    if (parsed == null) {
      return 0;
    }
    final Duration remaining = parsed.difference(now);
    return remaining.isNegative ? 0 : remaining.inSeconds;
  }

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();

    final VipOrder? latest = state.latestVipOrder;
    final VipOrder? pendingOrder =
        !state.vipActive && latest?.status == 'PENDING' ? latest : null;
    final VipOrder? confirmedOrder =
        !state.vipActive && latest?.status == 'CONFIRMED' ? latest : null;
    final int remainingSeconds =
        _remainingSeconds(pendingOrder?.expiresAt, DateTime.now());
    final bool pendingOrderLive =
        pendingOrder != null &&
        (pendingOrder.expiresAt == null || remainingSeconds > 0);
    final VipOrder? activePendingOrder = pendingOrderLive ? pendingOrder : null;
    final VipOrder? refreshableOrder = activePendingOrder ?? confirmedOrder;
    _setPolling(refreshableOrder != null);
    // Kotlin MainActivity: vipPaywallLoading || accountOperation != null — any
    // account operation freezes every sheet action while it runs.
    final bool paywallLoading =
        state.vipPaywallLoading || state.accountOperation != null;

    final bool canCreateOrder =
        !paywallLoading &&
        !state.vipActive &&
        (latest == null ||
            latest.status == 'EXPIRED' ||
            latest.status == 'REJECTED' ||
            (latest.status == 'PENDING' &&
                latest.expiresAt != null &&
                remainingSeconds <= 0));
    final String paymentAddress =
        pendingOrder?.receivingWalletAddress ?? state.vipCollectionAddress;

    return PopScope(
      // Busy gate, mirroring the auth sheet: while an account operation or a
      // payment refresh runs, the route's dismiss gestures are locked.
      canPop: !paywallLoading,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            _Header(
              state: state,
              copy: copy,
              colors: colors,
              roles: roles,
              onDismiss: controller.dismissVipPaywall,
            ),
            const SizedBox(height: 14),
            if (state.vipActive)
              _ActiveSection(
                vipUntil: state.vipUntil,
                copy: copy,
                colors: colors,
                roles: roles,
              )
            else if (confirmedOrder != null)
              _ConfirmedSection(
                order: confirmedOrder,
                busy: paywallLoading,
                copy: copy,
                colors: colors,
                roles: roles,
                onRefresh: controller.refreshLatestVipOrder,
              )
            else if (activePendingOrder != null)
              _PendingSection(
                order: activePendingOrder,
                address: paymentAddress,
                remainingSeconds: remainingSeconds,
                busy: paywallLoading,
                copy: copy,
                colors: colors,
                roles: roles,
                onRefresh: controller.refreshLatestVipOrder,
              )
            else
              _CreateSection(
                latest: latest,
                remainingSeconds: remainingSeconds,
                canCreateOrder: canCreateOrder,
                busy: paywallLoading,
                priceUsdt: state.vipPriceUsdt,
                copy: copy,
                colors: colors,
                roles: roles,
                onCreate: controller.createVipOrder,
              ),
          ],
        ),
      ),
    );
  }
}

/// Lock icon, title (episode-specific when an episode is waiting) and close.
class _Header extends StatelessWidget {
  const _Header({
    required this.state,
    required this.copy,
    required this.colors,
    required this.roles,
    required this.onDismiss,
  });

  final AppUiState state;
  final AppStrings copy;
  final AppColors colors;
  final AppTypography roles;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    final EpisodeSummary? pendingEpisode = state.pendingVipEpisode;
    final String title = pendingEpisode != null
        ? '${copy.vipPayUnlockEpisodePrefix}'
            '${episodeNumberLabel(pendingEpisode.number, state.language)}'
        : copy.vipPayTitle;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Icon(Icons.lock_outline_rounded, size: 24, color: colors.accentPrimary),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(
                title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: roles.displayLarge,
              ),
              if (pendingEpisode != null) ...<Widget>[
                const SizedBox(height: 4),
                Text(
                  copy.vipPayLockedHint,
                  style: roles.body.copyWith(color: colors.textSecondary),
                ),
              ],
            ],
          ),
        ),
        IconButton(
          onPressed: onDismiss,
          tooltip: copy.authClose,
          icon: Icon(Icons.close_rounded, color: colors.textSecondary),
        ),
      ],
    );
  }
}

/// VIP already active: status line plus optional expiry.
class _ActiveSection extends StatelessWidget {
  const _ActiveSection({
    required this.vipUntil,
    required this.copy,
    required this.colors,
    required this.roles,
  });

  final String? vipUntil;
  final AppStrings copy;
  final AppColors colors;
  final AppTypography roles;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(
          copy.vipActiveLabel,
          style: roles.body.copyWith(color: colors.accentSuccess),
        ),
        if (vipUntil != null && vipUntil!.trim().isNotEmpty) ...<Widget>[
          const SizedBox(height: 6),
          Text(
            '${copy.vipExpiryLabel}$vipUntil',
            style: roles.body.copyWith(color: colors.textSecondary),
          ),
        ],
      ],
    );
  }
}

/// Confirmed order: amount, order number, status and a refresh action.
class _ConfirmedSection extends StatelessWidget {
  const _ConfirmedSection({
    required this.order,
    required this.busy,
    required this.copy,
    required this.colors,
    required this.roles,
    required this.onRefresh,
  });

  final VipOrder order;
  final bool busy;
  final AppStrings copy;
  final AppColors colors;
  final AppTypography roles;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(
          '${copy.vipPayAmount}${order.effectiveAmount} USDT',
          style: roles.numeric.copyWith(color: colors.accentPrimary),
        ),
        const SizedBox(height: 6),
        Text(
          '${copy.vipPayOrderNo}${order.orderNo}',
          style: roles.body.copyWith(color: colors.textSecondary),
        ),
        const SizedBox(height: 6),
        Text(
          copy.vipPayStatusConfirmed,
          style: roles.body.copyWith(color: colors.accentSuccess),
        ),
        const SizedBox(height: 14),
        AppButton(
          label: busy ? copy.vipPayRefreshing : copy.vipPayRefresh,
          isExpanded: true,
          isLoading: busy,
          loadingLabel: copy.vipPayRefreshing,
          onPressed: busy ? null : onRefresh,
        ),
      ],
    );
  }
}

/// Pending order: amount, order number, address, QR, countdown, hint, refresh.
///
/// The QR renders nothing when the address cannot be encoded.
class _PendingSection extends StatelessWidget {
  const _PendingSection({
    required this.order,
    required this.address,
    required this.remainingSeconds,
    required this.busy,
    required this.copy,
    required this.colors,
    required this.roles,
    required this.onRefresh,
  });

  final VipOrder order;
  final String address;
  final int remainingSeconds;
  final bool busy;
  final AppStrings copy;
  final AppColors colors;
  final AppTypography roles;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final bool hasAddress = address.trim().isNotEmpty;
    final int minutes = remainingSeconds ~/ 60;
    final int seconds = remainingSeconds % 60;
    final String countdown =
        '$minutes:${seconds.toString().padLeft(2, '0')}';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(
          '${copy.vipPayAmount}${order.effectiveAmount} USDT',
          style: roles.numeric.copyWith(color: colors.accentPrimary),
        ),
        const SizedBox(height: 6),
        Text(
          '${copy.vipPayOrderNo}${order.orderNo}',
          style: roles.body.copyWith(color: colors.textSecondary),
        ),
        const SizedBox(height: 10),
        Text(
          copy.vipPayAddress,
          style: roles.body.copyWith(color: colors.textSecondary),
        ),
        Text(
          address.trim().isEmpty ? 'N/A' : address,
          textAlign: TextAlign.center,
          style: roles.body,
        ),
        const SizedBox(height: 10),
        Text(
          '${copy.vipPayExpiresIn} $countdown',
          style: roles.numeric.copyWith(color: colors.accentPrimary),
        ),
        if (hasAddress) ...<Widget>[
          const SizedBox(height: 12),
          Center(child: _QrCode(address: address)),
          const SizedBox(height: 12),
          AppButton(
            label: copy.vipPayCopy,
            variant: AppButtonVariant.secondary,
            isExpanded: true,
            onPressed: busy
                ? null
                : () {
                    Clipboard.setData(ClipboardData(text: address));
                  },
          ),
        ],
        const SizedBox(height: 10),
        Text(
          copy.vipPayHint,
          style: roles.body.copyWith(color: colors.textSecondary),
        ),
        const SizedBox(height: 14),
        AppButton(
          label: busy ? copy.vipPayRefreshing : copy.vipPayRefresh,
          isExpanded: true,
          isLoading: busy,
          loadingLabel: copy.vipPayRefreshing,
          onPressed: busy ? null : onRefresh,
        ),
      ],
    );
  }
}

/// The locally generated QR code. The payload is the raw Base58 address;
/// `QrValidator.validate` gates rendering so unencodable content draws
/// nothing, exactly like the Kotlin null-bitmap fallback.
class _QrCode extends StatelessWidget {
  const _QrCode({required this.address});

  final String address;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppStrings copy = strings(
      context.watch<AppStateController>().state.language,
    );
    final QrValidationResult validation = QrValidator.validate(
      data: address,
      version: QrVersions.auto,
      errorCorrectionLevel: QrErrorCorrectLevel.M,
    );
    if (!validation.isValid || validation.qrCode == null) {
      return const SizedBox.shrink();
    }

    return Semantics(
      label: copy.vipPayQrHint,
      child: QrImageView.withQr(
        qr: validation.qrCode!,
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
      ),
    );
  }
}

/// Not active and no live order: prior-order status line, price and unlock.
class _CreateSection extends StatelessWidget {
  const _CreateSection({
    required this.latest,
    required this.remainingSeconds,
    required this.canCreateOrder,
    required this.busy,
    required this.priceUsdt,
    required this.copy,
    required this.colors,
    required this.roles,
    required this.onCreate,
  });

  final VipOrder? latest;
  final int remainingSeconds;
  final bool canCreateOrder;
  final bool busy;
  final String priceUsdt;
  final AppStrings copy;
  final AppColors colors;
  final AppTypography roles;
  final VoidCallback onCreate;

  @override
  Widget build(BuildContext context) {
    final String? statusMessage = switch (latest?.status) {
      'EXPIRED' => copy.vipPayExpired,
      'REJECTED' => copy.vipPayStatusRejected,
      'PENDING' when latest!.expiresAt != null && remainingSeconds <= 0 =>
        copy.vipPayExpired,
      _ => null,
    };
    final String price = '${priceUsdt.trim().isEmpty ? '--' : priceUsdt}'
        '${copy.accountVipPriceSuffix}';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        if (statusMessage != null) ...<Widget>[
          Text(
            statusMessage,
            style: roles.body.copyWith(color: colors.accentDanger),
          ),
          const SizedBox(height: 6),
        ],
        Text(
          price,
          style: roles.numeric.copyWith(color: colors.accentPrimary),
        ),
        const SizedBox(height: 14),
        AppButton(
          label: busy ? copy.vipPayCreating : copy.accountVipUnlockAction,
          isExpanded: true,
          isLoading: busy,
          loadingLabel: copy.vipPayCreating,
          onPressed: canCreateOrder ? onCreate : null,
        ),
      ],
    );
  }
}
