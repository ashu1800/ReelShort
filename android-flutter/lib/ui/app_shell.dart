/// App shell: the four primary tabs, the global overlays and the full-screen player.
///
/// Parity with the Kotlin `MainShell` is behavioural, not visual: the tab set, the highlight
/// predicate (`state.screen == tab`), the `LOGIN` behaviour (nothing rendered while the bar stays),
/// the player living outside the shell and the global message/ auth/ VIP-payment hosts are all
/// contract. The layout itself is new.
///
/// The Kotlin activity also registers four `BackHandler`s so the immersive screens return one
/// level instead of leaving the app; the [PopScope] here is that contract.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/formatting/navigation_contract.dart';
import '../state/app_state_controller.dart';
import '../state/app_ui_state.dart';
import '../theme/app_colors.dart';
import '../theme/app_motion.dart';
import '../theme/app_shapes.dart';
import '../theme/app_typography.dart';
import 'components/app_loading.dart';
import 'components/app_message_bar.dart';
import 'screens/account_screen.dart';
import 'screens/favorites_screen.dart';
import 'screens/funds_screen.dart';
import 'screens/home_screen.dart';
import 'screens/player_screen.dart';
import 'screens/search_screen.dart';
import 'screens/withdrawal_screen.dart';
import 'screens/vip_recharge_screen.dart';
import 'sheets/auth_sheet.dart';
import 'sheets/vip_paywall_sheet.dart';

/// Hosts every screen and the global overlays.
class AppShell extends StatefulWidget {
  /// Creates the shell around [controller].
  const AppShell({required this.controller, super.key});

  final AppStateController controller;

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  @override
  void initState() {
    super.initState();
    // Cold start: restore the persisted session and render the cached shelf while the network
    // refresh runs. Kicked off once the first frame is scheduled so the shell can paint first.
    scheduleMicrotask(() {
      unawaited(widget.controller.restoreSession());
    });
  }

  /// Screens whose system back gesture must return one level instead of leaving
  /// the app — the Kotlin `BackHandler` set.
  bool _interceptsSystemBack(AppScreen screen) => switch (screen) {
    AppScreen.player ||
    AppScreen.favorites ||
    AppScreen.funds ||
    AppScreen.withdrawal ||
    AppScreen.vipRecharge => true,
    _ => false,
  };

  /// The Kotlin `BackHandler` targets: player → its return screen, favourites and
  /// funds → account, the funds children → funds.
  void _handleSystemBack(AppStateController controller, AppScreen screen) {
    switch (screen) {
      case AppScreen.player:
        controller.backToPlaybackSource();
      case AppScreen.favorites:
      case AppScreen.funds:
        controller.backToAccount();
      case AppScreen.withdrawal:
      case AppScreen.vipRecharge:
        controller.backToFunds();
      default:
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;

    final Widget content = state.screen == AppScreen.player
        // The player owns the whole surface, including the system navigation area.
        ? const PlayerScreen()
        : Scaffold(
            backgroundColor: Theme.of(context).extension<AppColors>()!.canvas,
            body: _ShellBody(state: state),
            bottomNavigationBar: _AppNavigationBar(
              screen: state.screen,
              onSelect: (AppScreen target) => _navigate(controller, target),
            ),
          );

    return PopScope(
      canPop: !_interceptsSystemBack(state.screen),
      onPopInvokedWithResult: (bool didPop, Object? result) {
        if (!didPop) {
          _handleSystemBack(controller, state.screen);
        }
      },
      child: Stack(
        children: <Widget>[
          Positioned.fill(child: content),
          // Thin progress bar instead of a blocking dialog: the loading gates in the state
          // layer are unchanged, only the presentation differs. Like the Kotlin loading host
          // it floats above every screen, player included.
          Positioned(
            left: 0,
            right: 0,
            top: 0,
            child: AppTopProgressBar(visible: state.isLoading),
          ),
          // The message host, auth sheet and VIP paywall cover every screen — the Kotlin
          // activity composes them above `MainShell` at z-index 2, player included.
          if (state.errorMessage != null)
            Positioned(
              left: 16,
              right: 16,
              top: 0,
              child: SafeArea(
                top: true,
                bottom: false,
                child: AppMessageBar(
                  key: ValueKey<int>(state.messageTrigger),
                  message: state.errorMessage!,
                  type: state.messageType ?? UiMessageType.error,
                  // The bar disappears visually on its own; clearing the state keeps a
                  // repeated identical message re-showable, like the Kotlin 4 s effect.
                  onDismissed: controller.clearError,
                ),
              ),
            ),
          if (state.authPromptVisible) const AuthSheet(),
          if (state.vipPaywallVisible) const VipPaywallSheet(),
        ],
      ),
    );
  }

  void _navigate(AppStateController controller, AppScreen target) {
    switch (target) {
      case AppScreen.home:
        unawaited(controller.openHome());
      case AppScreen.search:
        controller.showSearch();
      case AppScreen.funds:
        unawaited(controller.openFunds());
      case AppScreen.account:
        unawaited(controller.openAccount());
      // The shell only ever offers the four primary tabs; the remaining screens are reached
      // from inside them and are rendered by [_ShellBody] as detail routes.
      default:
        break;
    }
  }
}

class _ShellBody extends StatelessWidget {
  const _ShellBody({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppMotion motion = Theme.of(context).extension<AppMotion>()!;

    return AnimatedSwitcher(
      duration: motion.reelChange,
      // The "reel change" is a short crossfade: screens are not related enough in this design
      // for a directional wipe to read as navigation rather than as noise.
      switchInCurve: motion.reelChangeCurve,
      switchOutCurve: motion.reelChangeCurve.flipped,
      child: KeyedSubtree(
        key: ValueKey<AppScreen>(state.screen),
        child: _ScreenFor(screen: state.screen),
      ),
    );
  }
}

class _ScreenFor extends StatelessWidget {
  const _ScreenFor({required this.screen});

  final AppScreen screen;

  @override
  Widget build(BuildContext context) {
    switch (screen) {
      case AppScreen.login:
        // Contract: `LOGIN` renders nothing but keeps the navigation bar and no highlighted tab.
        return const SizedBox.shrink();
      case AppScreen.home:
        return const HomeScreen();
      case AppScreen.search:
        return const SearchScreen();
      case AppScreen.detail:
        // `AppScreen.detail` is never assigned by the state machine (parity gap G1), so this
        // branch is unreachable in practice but must still exist for exhaustiveness.
        return const HomeScreen();
      case AppScreen.player:
        return const PlayerScreen();
      case AppScreen.account:
        return const AccountScreen();
      case AppScreen.funds:
        return const FundsScreen();
      case AppScreen.withdrawal:
        return const WithdrawalScreen();
      case AppScreen.vipRecharge:
        return const VipRechargeScreen();
      case AppScreen.favorites:
        return const FavoritesScreen();
    }
  }
}

class _AppNavigationBar extends StatelessWidget {
  const _AppNavigationBar({required this.screen, required this.onSelect});

  final AppScreen screen;
  final ValueChanged<AppScreen> onSelect;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;

    return Container(
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border(
          top: BorderSide(color: colors.hairline, width: shapes.strokeHairline),
        ),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 64,
          child: Row(
            children: primaryTabs
                .map(
                  (AppScreen tab) => Expanded(
                    child: _NavTab(
                      tab: tab,
                      selected: screen == tab,
                      onTap: () => onSelect(tab),
                    ),
                  ),
                )
                .toList(growable: false),
          ),
        ),
      ),
    );
  }
}

class _NavTab extends StatelessWidget {
  const _NavTab({
    required this.tab,
    required this.selected,
    required this.onTap,
  });

  final AppScreen tab;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppMotion motion = Theme.of(context).extension<AppMotion>()!;
    final Color accent = selected ? colors.accentPrimary : colors.textSecondary;

    return Semantics(
      button: true,
      selected: selected,
      label: navigationLabel(tab),
      child: InkWell(
        onTap: onTap,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            _TabGlyph(tab: tab, color: accent),
            const SizedBox(height: 4),
            Text(
              navigationLabel(tab),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: roles.body.copyWith(color: accent, fontSize: 11),
            ),
            const SizedBox(height: 3),
            AnimatedContainer(
              duration: motion.controlState,
              curve: motion.controlStateCurve,
              width: 16,
              height: 2,
              decoration: BoxDecoration(
                color: selected ? accent : Colors.transparent,
                borderRadius: BorderRadius.circular(shapes.radiusInline),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TabGlyph extends StatelessWidget {
  const _TabGlyph({required this.tab, required this.color});

  final AppScreen tab;
  final Color color;

  @override
  Widget build(BuildContext context) {
    switch (tab) {
      case AppScreen.home:
        return Icon(Icons.smart_display_outlined, color: color, size: 22);
      case AppScreen.search:
        return Icon(Icons.travel_explore_outlined, color: color, size: 22);
      case AppScreen.funds:
        return Icon(Icons.account_balance_wallet_outlined, color: color, size: 22);
      case AppScreen.account:
        return Icon(Icons.person_outline, color: color, size: 22);
      // Non-primary screens are never rendered as a tab.
      default:
        return const SizedBox.shrink();
    }
  }
}
