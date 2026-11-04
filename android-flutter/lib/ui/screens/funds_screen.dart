/// The funds centre tab: the point balance, the withdrawal and VIP recharge
/// entries, the point ledger, the VIP order history and the legacy recharge
/// order list.
///
/// Port of `android-app/.../ui/screens/account/FundsScreen.kt`, rebuilt with the
/// ShortLink design system (`docs/ui-redesign-notes.md` §7.4). The screen owns
/// no state of its own: state is read through
/// `context.watch<AppStateController>().state` and every action dispatches on
/// that controller.
///
/// Layout contract: one `screenTitle` page title, the page inset from
/// [AppSpacing.pagePadding], the balance hero carrying the membership treatment,
/// and one `AppSectionHeader` + `AppSectionCard` per section. Every colour,
/// radius, gap and type role is a token; the only literal numbers are the named
/// block sizes below, which the loading skeleton mirrors.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

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
import '../components/app_amount_block.dart';
import '../components/app_button.dart';
import '../components/app_list_row.dart';
import '../components/app_loading.dart';
import '../components/app_membership_chip.dart';
import '../components/app_section.dart';
import '../components/ledger_row.dart';

/// Key of the balance hero, so a test can assert the membership treatment and
/// the unknown-snapshot placeholder without reaching into a private type.
const Key fundsBalanceHeroKey = ValueKey<String>('funds-balance-hero');

/// Key of the hero-shaped block in the loading skeleton.
const Key fundsBalanceHeroPlaceholderKey = ValueKey<String>(
  'funds-hero-placeholder',
);

/// Height of the hero placeholder while the account snapshot loads.
const double _skeletonHeroHeight = 200;

/// Height of one action-tile placeholder.
const double _skeletonActionHeight = 80;

/// Height of one list-row placeholder.
const double _skeletonRowHeight = 56;

/// Size of the section-header placeholder.
const Size _skeletonHeaderSize = Size(96, 14);

/// Size of the hero figure placeholder, at 1x text scale.
const Size _skeletonFigureSize = Size(180, 40);

/// The funds centre tab.
///
/// Shows the available balance and the supporting metrics, entries into
/// withdrawal and VIP recharge, the point ledger, the VIP order history and the
/// recharge order list. While the account snapshot loads the page renders
/// content-shaped skeleton blocks; empty sections render the shared empty state.
class FundsScreen extends StatelessWidget {
  /// Creates the funds screen. The shell instantiates it without arguments.
  const FundsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    final AppStrings copy = strings(state.language);
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    final AppColors colors = Theme.of(context).extension<AppColors>()!;

    return SafeArea(
      bottom: false,
      child: RefreshIndicator.adaptive(
        color: colors.accentPrimary,
        backgroundColor: colors.surface,
        onRefresh: controller.openFunds,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          // The single page inset of the system (18/12/18/28).
          padding: spacing.pagePadding,
          children: <Widget>[
            // One page-title role for the whole app, so Funds matches Me and the
            // sub-pages instead of using the smaller `titleLarge`.
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Expanded(
                  child: Text(copy.fundsTitle, style: roles.screenTitle),
                ),
                // The page is money: a visible refresh beats hoping the user
                // discovers the pull gesture.
                IconButton(
                  onPressed: state.isLoading
                      ? null
                      : () => unawaited(controller.openFunds()),
                  tooltip: AppUiCopy.refresh,
                  color: colors.textSecondary,
                  icon: const Icon(Icons.refresh),
                ),
              ],
            ),
            Text(
              copy.fundsSubtitle,
              style: roles.caption.copyWith(color: colors.textSecondary),
            ),
            SizedBox(height: spacing.sectionGap),
            if (state.isLoading && state.pointAccount == null)
              const _FundsLoadingSkeleton()
            else ...<Widget>[
              if (state.isLoading) ...<Widget>[
                // A refresh keeps the numbers on screen and reports the work in
                // a hairline bar instead of flashing the page to skeletons.
                const LinearProgressIndicator(minHeight: 2),
                SizedBox(height: spacing.space3),
              ],
              _BalanceHero(state: state),
              SizedBox(height: spacing.sectionGap),
              _ActionsSection(state: state),
              SizedBox(height: spacing.sectionGap),
              _PointRecordsSection(state: state),
              SizedBox(height: spacing.sectionGap),
              _VipOrdersSection(state: state),
              if (state.orders.isNotEmpty) ...<Widget>[
                SizedBox(height: spacing.sectionGap),
                _RechargeOrdersSection(state: state),
              ],
            ],
          ],
        ),
      ),
    );
  }
}

/// The balance hero: membership state, the available figure and the three
/// supporting metrics.
///
/// A guest sees the page's promise plus the sign-in action and no metrics at
/// all. A logged-in member whose snapshot has not arrived yet sees neutral
/// plates instead of zeros or an inactive-membership claim - the same contract
/// the Me page follows.
class _BalanceHero extends StatelessWidget {
  const _BalanceHero({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final TextTheme textTheme = Theme.of(context).textTheme;
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final bool loggedIn = state.session == null ? false : true;
    final bool vip = state.vipActive;
    final String expiry = dateOnlyLabel(state.vipUntil);
    final PointAccount? account = state.pointAccount;
    // Before the snapshot lands the membership state is unknown: neither
    // "VIP not active" nor a zero balance may be claimed.
    final bool vipKnown = vip || expiry.isNotEmpty || account != null;
    final double textScale = MediaQuery.textScalerOf(context).scale(1);

    return Container(
      key: fundsBalanceHeroKey,
      padding: EdgeInsets.all(spacing.panelPadding),
      decoration: BoxDecoration(
        color: vip ? colors.premiumSurface : colors.surface,
        borderRadius: shapes.containerRadius,
        border: Border.all(
          // The hero is the page's only container: a decorative hairline is
          // not enough to make it read as a card on the near-white canvas.
          color: vip ? colors.accentPrimary : colors.outlineStrong,
          width: vip ? shapes.strokeEmphasis : shapes.strokeHairline,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          if (!loggedIn) ...<Widget>[
            Text(
              copy.fundsLoginRequired,
              style: textTheme.titleMedium?.copyWith(
                color: colors.accentPrimary,
              ),
            ),
            SizedBox(height: spacing.space4),
            AppButton(
              label: AppUiCopy.fundsGuestAction,
              isExpanded: true,
              onPressed: controller.showAuthPrompt,
            ),
          ] else ...<Widget>[
            Row(
              children: <Widget>[
                // Flexible on both sides: a non-flex chip would be laid out
                // with unbounded width and overflow the row at large text
                // scales.
                if (vipKnown)
                  Flexible(
                    child: AppMembershipChip(
                      active: vip,
                      label: vip
                          ? copy.accountVipStatusActive
                          : copy.accountVipStatusInactive,
                      // The status doubles as the shortcut into the membership
                      // flow: the page used to state the VIP state here and
                      // offer the only way to act on it two blocks below.
                      onTap: () => unawaited(controller.openVipRecharge()),
                    ),
                  )
                else
                  Flexible(
                    child: AppSkeleton(
                      width: membershipChipPlaceholderSize.width * textScale,
                      height: membershipChipPlaceholderSize.height * textScale,
                      radius: shapes.radiusInline,
                    ),
                  ),
                if (vip && expiry.isNotEmpty) ...<Widget>[
                  SizedBox(width: spacing.space3),
                  Flexible(
                    child: Text(
                      '${copy.accountVipExpiryPrefix}$expiry',
                      style: roles.caption,
                    ),
                  ),
                ],
              ],
            ),
            SizedBox(height: spacing.space5),
            if (account != null) ...<Widget>[
              _AnimatedPointFigure(
                points: account.availablePoints,
                label: copy.fundsAvailablePointsLabel,
                unit: copy.listPointsLabel,
              ),
              if (account.availablePoints <= 0) ...<Widget>[
                // A zero wallet is a state, not a figure: lead with the way out
                // instead of three more zeros.
                SizedBox(height: spacing.space3),
                Text(
                  AppUiCopy.fundsPointRecordsEmptyBody,
                  style: roles.body.copyWith(color: colors.textSecondary),
                ),
                SizedBox(height: spacing.space3),
                AppButton(
                  label: AppUiCopy.fundsEarnAction,
                  isExpanded: true,
                  onPressed: controller.showHome,
                ),
              ] else if (account.frozenPoints > 0) ...<Widget>[
                // Total only differs from Available while something is frozen,
                // so it appears with the row that explains the difference.
                SizedBox(height: spacing.space4),
                AppLedgerRow(
                  label: copy.fundsTotalPointsLabel,
                  value: groupedDigits(account.balance),
                ),
                if (account.todayCreditedPoints > 0)
                  AppLedgerRow(
                    label: copy.fundsTodayCreditedPointsLabel,
                    value: groupedDigits(account.todayCreditedPoints),
                  ),
                AppLedgerRow(
                  label: copy.fundsFrozenPointsLabel,
                  value: groupedDigits(account.frozenPoints),
                  // Frozen points are the one metric that can be a problem, so
                  // they take the warning accent while they are non-zero.
                  valueColor: colors.accentWarning,
                  divider: false,
                ),
              ] else if (account.todayCreditedPoints > 0) ...<Widget>[
                SizedBox(height: spacing.space4),
                AppLedgerRow(
                  label: copy.fundsTodayCreditedPointsLabel,
                  value: groupedDigits(account.todayCreditedPoints),
                  divider: false,
                ),
              ],
            ] else ...<Widget>[
              AppSkeleton(
                width: _skeletonFigureSize.width * textScale,
                height: _skeletonFigureSize.height * textScale,
                radius: shapes.radiusInline,
              ),
              SizedBox(height: spacing.space4),
              const AppSkeletonText(lines: 3),
            ],
          ],
        ],
      ),
    );
  }
}

/// The two money entries, as a 2-column button grid with their captions.
///
/// Behaviour is unchanged: a signed-in member opens the sub-page, a guest gets
/// the shared auth prompt.
class _ActionsSection extends StatelessWidget {
  const _ActionsSection({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppSpacing spacing = appSpacingOf(context);
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final bool loggedIn = state.session != null;
    final PointAccount? account = state.pointAccount;
    // Disable only when the balance is known to be zero: a guest or an unknown
    // snapshot keeps the entry live so the tap can still explain itself.
    final bool canWithdraw =
        !loggedIn || account == null || account.availablePoints > 0;

    void openWithdrawal() {
      if (loggedIn) {
        unawaited(controller.openWithdrawal());
      } else {
        controller.showAuthPrompt();
      }
    }

    void openVipRecharge() {
      if (loggedIn) {
        unawaited(controller.openVipRecharge());
      } else {
        controller.showAuthPrompt();
      }
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        const AppSectionHeader(title: AppUiCopy.fundsActionsSection),
        SizedBox(height: spacing.space3),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Expanded(
              child: _ActionTile(
                icon: Icons.account_balance_wallet,
                label: copy.fundsWithdrawalAction,
                caption: canWithdraw
                    ? copy.fundsWithdrawalSubtitle
                    : AppUiCopy.fundsWithdrawEmptyHint,
                // Withdraw is the primary money action of the page while there
                // is something to withdraw; a zero balance demotes it and says
                // why instead of letting the tap dead-end in the sub-page.
                variant: canWithdraw
                    ? AppButtonVariant.primary
                    : AppButtonVariant.secondary,
                onPressed: canWithdraw ? openWithdrawal : null,
              ),
            ),
            SizedBox(width: spacing.panelGap),
            Expanded(
              child: _ActionTile(
                icon: Icons.workspace_premium,
                label: copy.fundsVipRechargeAction,
                caption: copy.fundsVipRechargeSubtitle,
                variant: AppButtonVariant.secondary,
                onPressed: openVipRecharge,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

/// One action column: the button plus the caption that used to be its subtitle.
class _ActionTile extends StatelessWidget {
  const _ActionTile({
    required this.icon,
    required this.label,
    required this.caption,
    required this.variant,
    required this.onPressed,
  });

  final IconData icon;
  final String label;
  final String caption;

  /// Visual weight of the button. Passed explicitly: [AppButton] defaults to
  /// [AppButtonVariant.primary], and a region renders exactly one of those.
  final AppButtonVariant variant;

  /// Null disables the action (the zero-balance Withdraw case).
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        AppButton(
          label: label,
          icon: icon,
          variant: variant,
          isExpanded: true,
          onPressed: onPressed,
        ),
        // The caption belongs to the button: a section-sized gap here used to
        // make it read as the subtitle of the next section.
        SizedBox(height: spacing.space1),
        Text(
          caption,
          style: roles.caption.copyWith(color: colors.textSecondary),
        ),
      ],
    );
  }
}

/// The point ledger section: heading, read-only rows, or the shared empty state.
class _PointRecordsSection extends StatelessWidget {
  const _PointRecordsSection({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final List<PointRecord> records =
        state.pointAccount?.records ?? const <PointRecord>[];
    // A zero wallet already offers the earning path in the hero; repeating the
    // same button in the empty row puts two identical calls to action on one
    // screen.
    final bool heroOffersEarning =
        state.session != null && (state.pointAccount?.availablePoints ?? 1) <= 0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        AppSectionHeader(title: copy.accountPointRecordsTitle),
        SizedBox(height: spacing.space2),
        AppSectionCard(
          children: <Widget>[
            if (records.isEmpty)
              // A compact row with a working action, not a page-sized empty
              // state squeezed into a list card.
              AppListRow(
                title: AppUiCopy.fundsPointRecordsEmptyTitle,
                subtitle: AppUiCopy.fundsPointRecordsEmptyBody,
                trailingTakesIntrinsicWidth: true,
                trailing: heroOffersEarning
                    ? null
                    : TextButton(
                        onPressed: controller.showHome,
                        child: const Text(
                          AppUiCopy.fundsEarnAction,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
              )
            else
              for (final PointRecord record in records)
                AppLedgerRow(
                  label: (record.reason?.trim() ?? '').isEmpty
                      ? copy.listPointRecordSubtitle
                      : record.reason!.trim(),
                  value: '${record.amount}',
                  // Negative movements read as a loss without relying on the
                  // sign alone being noticed.
                  valueColor: record.amount < 0 ? colors.accentDanger : null,
                  divider: false,
                ),
          ],
        ),
      ],
    );
  }
}

/// The VIP order history: heading, one read-only row per order, or the empty
/// state. The status mapping and the amount wording are unchanged.
class _VipOrdersSection extends StatelessWidget {
  const _VipOrdersSection({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppStrings copy = strings(state.language);
    final TextTheme textTheme = Theme.of(context).textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        AppSectionHeader(title: copy.vipRecordsTitle),
        SizedBox(height: spacing.space2),
        AppSectionCard(
          children: <Widget>[
            if (state.vipOrders.isEmpty)
              AppListRow(
                title: AppUiCopy.fundsVipOrdersEmptyTitle,
                subtitle: AppUiCopy.fundsVipOrdersEmptyBody,
              )
            else
              for (final VipOrder order in state.vipOrders)
                AppListRow(
                  title: '${copy.vipPayAmount}${order.effectiveAmount} USDT',
                  // Order number and creation time are machine values: they sit
                  // in the subtitle, where they own the whole row width.
                  subtitle: '${order.orderNo}\n${order.createdAt}',
                  divider: false,
                  // The status is a short label: it keeps its own width and its
                  // own readable node instead of being squeezed.
                  trailingTakesIntrinsicWidth: true,
                  trailing: Text(
                    _vipOrderStatusLabel(order, copy),
                    style: textTheme.bodySmall?.copyWith(
                      color: _vipOrderStatusColor(order, colors),
                    ),
                  ),
                ),
          ],
        ),
      ],
    );
  }
}

/// The status word of a VIP order; the mapping is unchanged from the first
/// build (`PENDING` is the fallback, as before).
String _vipOrderStatusLabel(VipOrder order, AppStrings copy) =>
    switch (order.status.toUpperCase()) {
      'CONFIRMED' => copy.vipPayStatusConfirmed,
      'REJECTED' => copy.vipPayStatusRejected,
      _ => copy.vipPayStatusPending,
    };

/// The accent a VIP order status reads in.
Color _vipOrderStatusColor(VipOrder order, AppColors colors) =>
    switch (order.status.toUpperCase()) {
      'CONFIRMED' => colors.accentSuccess,
      'REJECTED' => colors.accentDanger,
      _ => colors.accentWarning,
    };

/// The legacy recharge orders: read-only, and only rendered when the backend
/// still returns historical rows (recharge creation is disabled).
class _RechargeOrdersSection extends StatelessWidget {
  const _RechargeOrdersSection({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppStrings copy = strings(state.language);
    final TextTheme textTheme = Theme.of(context).textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        AppSectionHeader(title: copy.accountOrdersTitle),
        SizedBox(height: spacing.space2),
        AppSectionCard(
          children: <Widget>[
            for (final RechargeOrderSummary order in state.orders)
              AppListRow(
                title: order.orderNo,
                subtitle: '${order.pointAmount} ${copy.listPointsLabel}',
                divider: false,
                trailingTakesIntrinsicWidth: true,
                trailing: Text(
                  order.status,
                  style: textTheme.labelSmall?.copyWith(
                    color: colors.textSecondary,
                  ),
                ),
              ),
          ],
        ),
      ],
    );
  }
}

/// The content-shaped loading state: a hero plate, the two action tiles and the
/// first section's rows, so the swap to real data barely moves the layout.
class _FundsLoadingSkeleton extends StatelessWidget {
  const _FundsLoadingSkeleton();

  @override
  Widget build(BuildContext context) {
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        AppSkeleton(
          key: fundsBalanceHeroPlaceholderKey,
          height: _skeletonHeroHeight,
          radius: shapes.radiusContainer,
        ),
        SizedBox(height: spacing.sectionGap),
        Row(
          children: <Widget>[
            Expanded(
              child: AppSkeleton(
                height: _skeletonActionHeight,
                radius: shapes.radiusControl,
              ),
            ),
            SizedBox(width: spacing.panelGap),
            Expanded(
              child: AppSkeleton(
                height: _skeletonActionHeight,
                radius: shapes.radiusControl,
              ),
            ),
          ],
        ),
        SizedBox(height: spacing.sectionGap),
        AppSkeleton(
          width: _skeletonHeaderSize.width,
          height: _skeletonHeaderSize.height,
          radius: shapes.radiusInline,
        ),
        SizedBox(height: spacing.space3),
        AppSkeleton(
          height: _skeletonRowHeight,
          radius: shapes.radiusControl,
        ),
        SizedBox(height: spacing.space2),
        const AppSkeletonText(lines: 2),
      ],
    );
  }
}

/// Smooth CountUp animation for the hero point figure on funds screen.
class _AnimatedPointFigure extends StatefulWidget {
  const _AnimatedPointFigure({
    required this.points,
    required this.label,
    required this.unit,
  });

  final int points;
  final String label;
  final String unit;

  @override
  State<_AnimatedPointFigure> createState() => _AnimatedPointFigureState();
}

class _AnimatedPointFigureState extends State<_AnimatedPointFigure> {
  int _oldPoints = 0;

  @override
  void initState() {
    super.initState();
    _oldPoints = widget.points;
  }

  @override
  void didUpdateWidget(covariant _AnimatedPointFigure oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.points != widget.points) {
      _oldPoints = oldWidget.points;
    }
  }

  @override
  Widget build(BuildContext context) {
    final bool disableAnimations = MediaQuery.maybeDisableAnimationsOf(context) ??
        WidgetsBinding.instance.platformDispatcher.accessibilityFeatures.disableAnimations;

    if (disableAnimations || _oldPoints == widget.points) {
      return AppAmountBlock(
        label: widget.label,
        value: groupedDigits(widget.points),
        unit: widget.unit,
        size: AppAmountSize.hero,
      );
    }

    return TweenAnimationBuilder<double>(
      key: ValueKey<int>(widget.points),
      tween: Tween<double>(
        begin: _oldPoints.toDouble(),
        end: widget.points.toDouble(),
      ),
      duration: const Duration(milliseconds: 650),
      curve: Curves.easeOutCubic,
      builder: (BuildContext context, double value, Widget? child) {
        return AppAmountBlock(
          label: widget.label,
          value: groupedDigits(value.round()),
          unit: widget.unit,
          size: AppAmountSize.hero,
        );
      },
    );
  }
}
