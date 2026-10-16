/// The funds centre tab: the point ledger, the withdrawal and VIP recharge
/// entries, the VIP order history and the legacy recharge order list.
///
/// Port of `android-app/.../ui/screens/account/FundsScreen.kt`, rebuilt with the
/// ShortLink design system. The screen owns no state of its own: state is read
/// through `context.watch<AppStateController>().state` and every action
/// dispatches on that controller.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_typography.dart';
import '../../ui/components/app_empty_state.dart';
import '../../ui/components/app_loading.dart';
import '../../ui/components/ledger_row.dart';

/// Horizontal padding and bottom scroll inset of the funds page.
const EdgeInsets _pagePadding = EdgeInsets.fromLTRB(18, 16, 18, 28);

/// Top padding of the page title above the scroll area.
const EdgeInsets _titlePadding = EdgeInsets.fromLTRB(18, 16, 18, 0);

/// Vertical gap between the page sections.
const double _sectionGap = 14;

/// Padding inside a surface panel.
const EdgeInsets _panelPadding = EdgeInsets.all(16);

/// Gap between rows inside a panel.
const double _panelGap = 10;

/// Gap between the icon plate and the tile copy.
const double _tileGap = 14;

/// Height of one skeleton row while the account snapshot loads.
const double _skeletonItemHeight = 64;

/// The funds centre tab.
///
/// Shows the point balance, today's credited points, frozen and available
/// points, the VIP status, entries into withdrawal and VIP recharge, the point
/// ledger records, the VIP order history and the recharge order list. While the
/// account snapshot loads the page renders skeleton rows; an empty ledger
/// renders an empty state.
class FundsScreen extends StatelessWidget {
  /// Creates the funds screen. The shell instantiates it without arguments.
  const FundsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    final AppStrings copy = strings(state.language);
    final bool loggedIn = state.session != null;

    return SafeArea(
      bottom: false,
      child: Column(
        children: <Widget>[
          Padding(
            padding: _titlePadding,
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text(
                copy.fundsTitle,
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ),
          ),
          Expanded(
            child: state.isLoading
                ? AppSkeletonList(
                    itemHeight: _skeletonItemHeight,
                    itemCount: 5,
                    padding: _pagePadding,
                  )
                : ListView(
                    padding: _pagePadding,
                    children: <Widget>[
                      _BalancePanel(
                        state: state,
                        copy: copy,
                        loggedIn: loggedIn,
                      ),
                      const SizedBox(height: _sectionGap),
                      _FundsActionTile(
                        icon: Icons.account_balance_wallet,
                        title: copy.fundsWithdrawalAction,
                        subtitle: copy.fundsWithdrawalSubtitle,
                        onTap: () {
                          if (loggedIn) {
                            unawaited(controller.openWithdrawal());
                          } else {
                            controller.showAuthPrompt();
                          }
                        },
                      ),
                      const SizedBox(height: _sectionGap),
                      _FundsActionTile(
                        icon: Icons.workspace_premium,
                        title: copy.fundsVipRechargeAction,
                        subtitle: copy.fundsVipRechargeSubtitle,
                        onTap: () {
                          if (loggedIn) {
                            unawaited(controller.openVipRecharge());
                          } else {
                            controller.showAuthPrompt();
                          }
                        },
                      ),
                      const SizedBox(height: _sectionGap),
                      _LedgerSection(state: state, copy: copy),
                      const SizedBox(height: _sectionGap),
                      _VipOrdersSection(state: state, copy: copy),
                      if (state.orders.isNotEmpty) ...<Widget>[
                        const SizedBox(height: _sectionGap),
                        _RechargeOrdersSection(state: state, copy: copy),
                      ],
                    ],
                  ),
          ),
        ],
      ),
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

/// The balance panel: subtitle, VIP status and the four ledger metrics.
class _BalancePanel extends StatelessWidget {
  const _BalancePanel({
    required this.state,
    required this.copy,
    required this.loggedIn,
  });

  final AppUiState state;
  final AppStrings copy;
  final bool loggedIn;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final PointAccount? account = state.pointAccount;

    return _Panel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            copy.fundsSubtitle,
            style: textTheme.bodyMedium?.copyWith(color: colors.textSecondary),
          ),
          const SizedBox(height: _panelGap),
          if (!loggedIn)
            Text(
              copy.fundsLoginRequired,
              style: textTheme.titleMedium?.copyWith(
                color: colors.accentPrimary,
              ),
            )
          else ...<Widget>[
            _VipStatusBadge(state: state, copy: copy),
            const SizedBox(height: _panelGap),
            AppLedgerRow(
              label: copy.fundsTotalPointsLabel,
              value: groupedDigits(account?.balance ?? 0),
              valueColor: colors.accentPrimary,
            ),
            AppLedgerRow(
              label: copy.fundsTodayCreditedPointsLabel,
              value: groupedDigits(account?.todayCreditedPoints ?? 0),
            ),
            AppLedgerRow(
              label: copy.fundsFrozenPointsLabel,
              value: groupedDigits(account?.frozenPoints ?? 0),
            ),
            AppLedgerRow(
              label: copy.fundsAvailablePointsLabel,
              value: groupedDigits(account?.availablePoints ?? 0),
              divider: false,
            ),
          ],
        ],
      ),
    );
  }
}

/// The VIP active/inactive badge with its optional expiry date.
class _VipStatusBadge extends StatelessWidget {
  const _VipStatusBadge({required this.state, required this.copy});

  final AppUiState state;
  final AppStrings copy;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final String statusLabel = state.vipActive
        ? copy.accountVipStatusActive
        : copy.accountVipStatusInactive;

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Icon(
          Icons.workspace_premium,
          size: 18,
          color: state.vipActive ? colors.accentSuccess : colors.textSecondary,
        ),
        const SizedBox(width: 6),
        Flexible(
          child: Text(
            statusLabel,
            style: textTheme.labelLarge?.copyWith(
              color: state.vipActive
                  ? colors.accentSuccess
                  : colors.textSecondary,
            ),
          ),
        ),
        if (state.vipActive && state.vipUntil != null) ...<Widget>[
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              '${copy.accountVipExpiryPrefix}${dateOnlyLabel(state.vipUntil)}',
              style: textTheme.bodySmall?.copyWith(color: colors.textSecondary),
            ),
          ),
        ],
      ],
    );
  }
}

/// A tappable entry tile: icon plate, title, subtitle and a trailing chevron.
class _FundsActionTile extends StatelessWidget {
  const _FundsActionTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;

    return Semantics(
      button: true,
      onTap: onTap,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: shapes.containerRadius,
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: colors.surface,
              borderRadius: shapes.containerRadius,
              border: Border.all(
                color: colors.hairline,
                width: shapes.strokeHairline,
              ),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              child: Row(
                children: <Widget>[
                  DecoratedBox(
                    decoration: BoxDecoration(
                      color: colors.surfaceAlt,
                      borderRadius: shapes.inlineRadius,
                    ),
                    child: Padding(
                      padding: const EdgeInsets.all(10),
                      child: Icon(icon, size: 24, color: colors.accentPrimary),
                    ),
                  ),
                  const SizedBox(width: _tileGap),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text(title, style: textTheme.titleMedium),
                        const SizedBox(height: 2),
                        Text(
                          subtitle,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: textTheme.bodySmall?.copyWith(
                            color: colors.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Icon(Icons.chevron_right, color: colors.textSecondary),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// The point ledger section: heading, rows, or an empty state.
class _LedgerSection extends StatelessWidget {
  const _LedgerSection({required this.state, required this.copy});

  final AppUiState state;
  final AppStrings copy;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final List<PointRecord> records =
        state.pointAccount?.records ?? const <PointRecord>[];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(copy.accountPointRecordsTitle, style: textTheme.titleMedium),
        const SizedBox(height: _panelGap),
        if (records.isEmpty)
          AppEmptyState(
            icon: Icons.receipt_long,
            title: copy.accountPointRecordsTitle,
          )
        else
          for (final PointRecord record in records)
            _LedgerRecordRow(record: record, copy: copy, colors: colors),
      ],
    );
  }
}

/// One point ledger row; negative movements take the danger accent.
class _LedgerRecordRow extends StatelessWidget {
  const _LedgerRecordRow({
    required this.record,
    required this.copy,
    required this.colors,
  });

  final PointRecord record;
  final AppStrings copy;
  final AppColors colors;

  @override
  Widget build(BuildContext context) {
    final String reason = record.reason?.trim() ?? '';
    return AppLedgerRow(
      label: reason.isEmpty ? copy.listPointRecordSubtitle : reason,
      value: '${record.amount}',
      valueColor: record.amount < 0 ? colors.accentDanger : null,
    );
  }
}

/// The VIP order history section: heading and one card per order.
class _VipOrdersSection extends StatelessWidget {
  const _VipOrdersSection({required this.state, required this.copy});

  final AppUiState state;
  final AppStrings copy;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(copy.vipRecordsTitle, style: textTheme.titleMedium),
        const SizedBox(height: _panelGap),
        if (state.vipOrders.isEmpty)
          Text(
            copy.accountVipNoOrders,
            style: textTheme.bodyMedium?.copyWith(color: colors.textSecondary),
          )
        else
          for (final VipOrder order in state.vipOrders)
            _VipOrderCard(order: order, copy: copy),
      ],
    );
  }
}

/// One VIP order card: amount, order number, creation date and status.
class _VipOrderCard extends StatelessWidget {
  const _VipOrderCard({required this.order, required this.copy});

  final VipOrder order;
  final AppStrings copy;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography typography = Theme.of(context)
        .extension<AppTypography>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final String status = order.status.toUpperCase();
    final String statusLabel = switch (status) {
      'CONFIRMED' => copy.vipPayStatusConfirmed,
      'REJECTED' => copy.vipPayStatusRejected,
      _ => copy.vipPayStatusPending,
    };
    final Color statusColor = switch (status) {
      'CONFIRMED' => colors.accentSuccess,
      'REJECTED' => colors.accentDanger,
      _ => colors.accentWarning,
    };

    return Padding(
      padding: const EdgeInsets.only(bottom: _panelGap),
      child: _Panel(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    '${copy.vipPayAmount}${order.effectiveAmount} USDT',
                    style: typography.numeric.copyWith(
                      color: colors.accentPrimary,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    order.orderNo,
                    style: textTheme.bodySmall?.copyWith(
                      color: colors.textSecondary,
                    ),
                  ),
                  Text(order.createdAt, style: textTheme.labelSmall),
                ],
              ),
            ),
            Text(
              statusLabel,
              style: textTheme.bodySmall?.copyWith(color: statusColor),
            ),
          ],
        ),
      ),
    );
  }
}

/// The legacy recharge order section; recharge creation is disabled, so the
/// section only renders when the backend still returns historical orders.
class _RechargeOrdersSection extends StatelessWidget {
  const _RechargeOrdersSection({required this.state, required this.copy});

  final AppUiState state;
  final AppStrings copy;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(copy.accountOrdersTitle, style: textTheme.titleMedium),
        const SizedBox(height: _panelGap),
        _Panel(
          child: Column(
            children: <Widget>[
              for (final RechargeOrderSummary order in state.orders)
                AppLedgerRow(
                  label: order.orderNo,
                  value: '${order.pointAmount} ${copy.listPointsLabel}',
                  trailing: Text(
                    order.status,
                    style: textTheme.labelSmall?.copyWith(
                      color: colors.textSecondary,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
