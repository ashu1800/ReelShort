/// Page builders for the shell's declarative route stack (Phase 3).
///
/// `app_route_stack.dart` owns the *contract* — which pages the shell must
/// render for a given [AppUiState] and what a back gesture means. This file is
/// the other half: it turns one [AppRouteNode] into the [Page] the shell's
/// [Navigator] renders, and it turns one [AppRouteDestination] into the widget
/// behind it. Keeping both mappings here means the shell only ever talks about
/// nodes and destinations, and a new destination fails to compile until it has
/// a page and a widget.
///
/// Two rules come from the contract and are enforced by construction:
///
///  * the tab roots are [MaterialPage]s under the constant `shell-root` key, so
///    switching tabs *updates* the single root page instead of pushing another
///    one — the bottom bar keeps highlighting `state.screen == tab`. Because
///    that update happens inside one route, the parity tab crossfade has to
///    live inside the page as well: [RootTabSwitcher] is that crossfade;
///  * the pushed sub-pages are [MaterialPage]s, so they inherit the theme's
///    installed reel-change page transition, while the player gets its own
///    [PlayerPage] and keeps the full-screen slide-up + fade it had before the
///    shell became declarative.
library;

import 'package:flutter/material.dart';

import '../../core/formatting/app_strings.dart';
import '../../state/app_state_controller.dart';
import '../../theme/app_motion.dart';
import 'app_route_stack.dart';
import '../components/app_sub_page.dart';
import '../screens/account_screen.dart';
import '../screens/favorites_screen.dart';
import '../screens/funds_screen.dart';
import '../screens/home_screen.dart';
import '../screens/player_screen.dart';
import '../screens/search_screen.dart';
import '../screens/vip_recharge_screen.dart';
import '../screens/withdrawal_screen.dart';

/// The [Page] rendered for [node].
///
/// [copy] supplies the sub-page titles and [controller] the return transitions
/// the shell chrome dispatches, so the two sub-page decisions that need state
/// live here rather than in the screen bodies.
Page<void> appRoutePage(
  AppRouteNode node, {
  required AppStrings copy,
  required AppStateController controller,
}) {
  final Widget child = appRouteChild(
    node.destination,
    copy: copy,
    controller: controller,
  );
  // `ValueKey` over the contract's stable page key: the root keeps one key for
  // every tab (in-place update), each sub-page keeps its own (true push/pop).
  final ValueKey<String> key = ValueKey<String>(node.pageKey);
  if (node.destination == AppRouteDestination.player) {
    return PlayerPage(key: key, child: child);
  }
  return MaterialPage<void>(
    key: key,
    // The tab root is one route updated in place, so the tab-to-tab crossfade
    // rides inside the page; a pushed sub-page needs no wrapper.
    child: node.isRoot
        ? RootTabSwitcher(destination: node.destination, child: child)
        : child,
  );
}

/// Crossfades the tab root when the shell switches tabs.
///
/// The root page keeps a constant key, so a tab switch updates the same route
/// rather than pushing a new one; without this widget the new tab would replace
/// the old one inside a single frame. 220 ms of reel-change crossfade is the
/// parity behaviour (`parity-matrix` §4.2), and the duration goes through
/// [resolveAppMotion] so the platform's reduced-motion setting collapses it to
/// an instant swap instead of merely shortening it.
class RootTabSwitcher extends StatelessWidget {
  /// Creates the tab crossfade around [child].
  const RootTabSwitcher({
    required this.destination,
    required this.child,
    super.key,
  });

  /// The tab this root page belongs to; the switcher keys on it.
  final AppRouteDestination destination;

  /// The tab's content.
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final AppMotion motion = appMotionOf(context);
    return AnimatedSwitcher(
      duration: resolveAppMotion(context, motion.reelChange),
      switchInCurve: motion.reelChangeCurve,
      switchOutCurve: motion.reelChangeCurve.flipped,
      child: KeyedSubtree(
        key: ValueKey<AppRouteDestination>(destination),
        child: child,
      ),
    );
  }
}

/// The widget behind [destination], with the sub-page chrome where it applies.
Widget appRouteChild(
  AppRouteDestination destination, {
  required AppStrings copy,
  required AppStateController controller,
}) => switch (destination) {
  // Contract: `LOGIN` is not a screen. It keeps the navigation bar (the shell
  // renders it for every non-player screen) and no tab is highlighted, because
  // the highlight predicate is still `state.screen == tab`.
  AppRouteDestination.login => const SizedBox.shrink(),
  AppRouteDestination.home => const HomeScreen(),
  AppRouteDestination.search => const SearchScreen(),
  AppRouteDestination.account => const AccountScreen(),
  AppRouteDestination.funds => const FundsScreen(),
  // Parity gap G1: `AppScreen.detail` is never assigned by the state machine,
  // so this page is unreachable today; it keeps the switch exhaustive and the
  // home shelf is the placeholder the shell used before Phase 3.
  AppRouteDestination.detail => const HomeScreen(),
  // The three sub-pages are wrapped by the shell chrome, so their screens only
  // render content: no header, no private page padding, no safe area.
  AppRouteDestination.favorites => AppSubPage(
    title: copy.accountFavoritesTitle,
    onBack: controller.backToAccount,
    scrollable: true,
    child: const FavoritesScreen(),
  ),
  AppRouteDestination.withdrawal => AppSubPage(
    title: copy.accountWithdrawTitle,
    onBack: controller.backToFunds,
    scrollable: true,
    child: const WithdrawalScreen(),
  ),
  AppRouteDestination.vipRecharge => AppSubPage(
    title: copy.vipPayTitle,
    onBack: controller.backToFunds,
    scrollable: true,
    child: const VipRechargeScreen(),
  ),
  // The player is full-bleed: no sub-page chrome and no bottom bar.
  AppRouteDestination.player => const PlayerScreen(),
};

/// The player's page: a pushed, full-screen takeover.
class PlayerPage extends Page<void> {
  /// Creates the player page around [child].
  const PlayerPage({required this.child, super.key});

  /// The player screen.
  final Widget child;

  @override
  Route<void> createRoute(BuildContext context) => PlayerPageRoute(this);
}

/// The route behind [PlayerPage].
///
/// The theme's reel-change transition is a short vertical settle; the player is
/// the one page that is not a *settle* but a takeover, so it keeps the
/// slide-up + fade the shell used to build for it inside its `AnimatedSwitcher`.
/// Reduced-motion users get the page in place, like every other transition.
class PlayerPageRoute extends PageRoute<void>
    with MaterialRouteTransitionMixin<void> {
  /// Creates the route for [page].
  PlayerPageRoute(this.page) : super(settings: page);

  /// The page this route renders.
  final PlayerPage page;

  @override
  Widget buildContent(BuildContext context) => page.child;

  @override
  bool get maintainState => true;

  @override
  Duration get transitionDuration => AppMotion.standard.reelChange;

  @override
  Duration get reverseTransitionDuration => AppMotion.standard.reelChange;

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    if (MediaQuery.disableAnimationsOf(context)) {
      return child;
    }
    return SlideTransition(
      position: Tween<Offset>(
        begin: const Offset(0, 1),
        end: Offset.zero,
      ).animate(
        CurvedAnimation(
          parent: animation,
          curve: Curves.easeOutCubic,
          reverseCurve: Curves.easeInCubic,
        ),
      ),
      child: FadeTransition(opacity: animation, child: child),
    );
  }
}
