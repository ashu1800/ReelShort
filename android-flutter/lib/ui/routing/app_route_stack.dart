/// Declarative route contract for the app shell (Phase 3).
///
/// The shell used to switch screens imperatively (`AnimatedSwitcher` keyed by
/// `state.screen`) and answer the system back gesture with a hard-coded
/// `switch` over the five immersive screens. That left three gaps:
///
///  * no route stack, so a sub-page could not be *pushed* — every screen swap
///    was a crossfade and the designed reel-change transition never ran;
///  * the back gesture had no priority order, so an open auth sheet or VIP
///    paywall lost the back press to the screen underneath it;
///  * the tab roots and the sub-pages were indistinguishable to the shell, so
///    each sub-page hand-rolled its own header and page padding.
///
/// This file is the pure, framework-free half of the fix: a function from
/// [AppUiState] to the page stack the shell must render, a function from
/// [AppUiState] to what a back gesture must do, and the mapping from a popped
/// page back to the controller call that owns that transition. Everything here
/// is synchronous and side-effect free, so the whole navigation contract is
/// unit-testable without pumping a widget.
///
/// Parity notes (see `docs/parity-matrix.md`):
///  * the four primary tabs stay tab roots — they are never pushed, so the
///    bottom bar keeps highlighting `state.screen == tab`;
///  * `FUNDS` is a tab root *and* has a back target (`ACCOUNT`), which is why
///    [BackOutcome.navigateToAccount] exists next to [BackOutcome.popPage];
///  * `PLAYER` is a pushed, full-screen page: the bar hides, the page is not
///    wrapped in the sub-page chrome, and back returns to the playback source.
library;

import 'package:flutter/foundation.dart';

import '../../state/app_ui_state.dart';

/// Key of the single tab-root page. It is constant because the root page is
/// replaced, never pushed: switching tabs swaps the root, it does not stack.
const String shellRootPageKey = 'shell-root';

/// Every page the shell can render.
enum AppRouteDestination {
  /// Tab root: the home shelf.
  home,

  /// `LOGIN` is not a screen: parity keeps the navigation bar and renders no
  /// content while the tab highlight stays on nothing. It is modelled as a
  /// root destination so the shell keeps that behaviour instead of falling
  /// back to the home shelf.
  login,

  /// Tab root: discover/search.
  search,

  /// Tab root: the account dashboard.
  account,

  /// Tab root: the funds centre.
  funds,

  /// Pushed sub-page: withdrawal form and records.
  withdrawal,

  /// Pushed sub-page: VIP recharge.
  vipRecharge,

  /// Pushed sub-page: saved dramas.
  favorites,

  /// Pushed full-screen page: the player. Not wrapped in the sub-page chrome.
  player,

  /// Pushed sub-page: drama detail. Unreachable today (parity gap G1); the
  /// route exists so the shell stays exhaustive when Phase 4 wires it.
  detail,
}

/// One entry of the shell's page stack.
@immutable
class AppRouteNode {
  /// Creates a route node.
  const AppRouteNode({
    required this.destination,
    required this.pageKey,
    required this.isRoot,
  });

  /// What is rendered.
  final AppRouteDestination destination;

  /// Stable identity of the page inside the shell's [Navigator].
  final String pageKey;

  /// Whether this is the tab-root page (exactly one per stack, always first).
  final bool isRoot;

  /// Whether the page is a full-bleed immersive page: no sub-page chrome and
  /// no bottom navigation bar.
  bool get isImmersive => destination == AppRouteDestination.player;

  @override
  bool operator ==(Object other) =>
      other is AppRouteNode &&
      other.destination == destination &&
      other.pageKey == pageKey &&
      other.isRoot == isRoot;

  @override
  int get hashCode => Object.hash(destination, pageKey, isRoot);

  @override
  String toString() =>
      'AppRouteNode(${destination.name}, $pageKey, root: $isRoot)';
}

/// Page key of a pushed [destination]; the root uses [shellRootPageKey].
String subPageKey(AppRouteDestination destination) =>
    'shell-page-${destination.name}';

/// The primary tab that owns the current screen.
///
/// `playerReturnScreen` decides which tab sits under the player, so popping the
/// player lands on the tab the playback started from.
AppRouteDestination shellRootDestination(AppUiState state) {
  AppScreen screen = state.screen;
  if (screen == AppScreen.player) {
    screen = state.playerReturnScreen ?? AppScreen.home;
  }
  return switch (screen) {
    AppScreen.login => AppRouteDestination.login,
    AppScreen.home => AppRouteDestination.home,
    AppScreen.search || AppScreen.detail => AppRouteDestination.search,
    AppScreen.account || AppScreen.favorites => AppRouteDestination.account,
    AppScreen.funds ||
    AppScreen.withdrawal ||
    AppScreen.vipRecharge => AppRouteDestination.funds,
    // Defensive: a player never returns to itself, so this is unreachable.
    AppScreen.player => AppRouteDestination.home,
  };
}

/// The pages pushed above the tab root, outermost last.
List<AppRouteDestination> shellSubPageDestinations(AppUiState state) =>
    switch (state.screen) {
      AppScreen.favorites => const <AppRouteDestination>[
        AppRouteDestination.favorites,
      ],
      AppScreen.withdrawal => const <AppRouteDestination>[
        AppRouteDestination.withdrawal,
      ],
      AppScreen.vipRecharge => const <AppRouteDestination>[
        AppRouteDestination.vipRecharge,
      ],
      AppScreen.player => const <AppRouteDestination>[
        AppRouteDestination.player,
      ],
      AppScreen.detail => const <AppRouteDestination>[AppRouteDestination.detail],
      AppScreen.home ||
      AppScreen.search ||
      AppScreen.account ||
      AppScreen.funds ||
      AppScreen.login => const <AppRouteDestination>[],
    };

/// The declarative page stack for [state]: exactly one root page, then zero or
/// more pushed pages.
List<AppRouteNode> buildRouteStack(AppUiState state) {
  final List<AppRouteNode> stack = <AppRouteNode>[
    AppRouteNode(
      destination: shellRootDestination(state),
      pageKey: shellRootPageKey,
      isRoot: true,
    ),
    for (final AppRouteDestination destination in shellSubPageDestinations(state))
      AppRouteNode(
        destination: destination,
        pageKey: subPageKey(destination),
        isRoot: false,
      ),
  ];
  return List<AppRouteNode>.unmodifiable(stack);
}

/// What the system back gesture must do in [state], in priority order.
///
/// Priority: an open overlay wins over the screen underneath it, a pushed page
/// wins over the tab root, and `FUNDS` keeps its Kotlin `BackHandler` target
/// (`ACCOUNT`) instead of leaving the app.
enum BackOutcome {
  /// Close the auth sheet; the screen underneath does not move.
  closeAuthPrompt,

  /// Close the VIP paywall; the screen underneath does not move.
  closeVipPaywall,

  /// Pop the top pushed page.
  popPage,

  /// `FUNDS` is a tab root with a back target: go to `ACCOUNT`.
  navigateToAccount,

  /// Nothing left to close or pop: let the platform close the app.
  exitApp,
}

/// Resolves the back priority for [state].
BackOutcome resolveBackOutcome(AppUiState state) {
  if (state.authPromptVisible) {
    return BackOutcome.closeAuthPrompt;
  }
  if (state.vipPaywallVisible) {
    return BackOutcome.closeVipPaywall;
  }
  if (shellSubPageDestinations(state).isNotEmpty) {
    return BackOutcome.popPage;
  }
  if (state.screen == AppScreen.funds) {
    return BackOutcome.navigateToAccount;
  }
  return BackOutcome.exitApp;
}

/// The controller transition a popped page maps to.
enum ShellExit {
  /// `backToPlaybackSource()`: the player's own return target.
  playbackSource,

  /// `backToAccount()`.
  account,

  /// `backToFunds()`.
  funds,

  /// `openHome()` / a plain return to the shelf.
  home,
}

/// Where the shell navigates after [destination] was popped by the framework.
///
/// Returns null for destinations that are never popped on their own (the tab
/// roots), which tells the shell to ignore the removal: it was a state-driven
/// rebuild, not a user back gesture.
ShellExit? shellExitForPoppedDestination(AppRouteDestination destination) =>
    switch (destination) {
      AppRouteDestination.favorites => ShellExit.account,
      AppRouteDestination.withdrawal ||
      AppRouteDestination.vipRecharge => ShellExit.funds,
      AppRouteDestination.player => ShellExit.playbackSource,
      // `detail` is unreachable today; Phase 4 must give it a real return
      // target (the search or home tab it was opened from) rather than home.
      AppRouteDestination.detail => ShellExit.home,
      AppRouteDestination.home ||
      AppRouteDestination.search ||
      AppRouteDestination.account ||
      AppRouteDestination.funds ||
      AppRouteDestination.login => null,
    };

/// Whether the bottom navigation bar stays visible for [state].
///
/// Parity: only the player is full-screen; every other screen keeps the bar.
bool shellShowsNavigationBar(AppUiState state) =>
    state.screen != AppScreen.player;
