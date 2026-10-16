/// Unit tests for the declarative route contract (`lib/ui/routing`).
///
/// These are pure-function tests: no widget is pumped, so a regression in the
/// navigation contract fails here with the exact state that produced it
/// instead of as a mystery in a widget test.
library;

import 'package:flutter_test/flutter_test.dart';

import 'package:shortlink/state/app_ui_state.dart';
import 'package:shortlink/ui/routing/app_route_stack.dart';

/// Convenience: a state with just the fields this contract reads.
AppUiState _state({
  AppScreen screen = AppScreen.home,
  AppScreen? playerReturnScreen,
  bool authPromptVisible = false,
  bool vipPaywallVisible = false,
}) => AppUiState(
  screen: screen,
  playerReturnScreen: playerReturnScreen,
  authPromptVisible: authPromptVisible,
  vipPaywallVisible: vipPaywallVisible,
);

List<AppRouteDestination> _destinations(AppUiState state) => buildRouteStack(
  state,
).map((AppRouteNode node) => node.destination).toList(growable: false);

void main() {
  test('every primary tab renders as a single root page', () {
    for (final AppScreen tab in <AppScreen>[
      AppScreen.home,
      AppScreen.search,
      AppScreen.funds,
      AppScreen.account,
    ]) {
      final List<AppRouteNode> stack = buildRouteStack(_state(screen: tab));
      expect(stack, hasLength(1), reason: '${tab.name} must be a tab root');
      expect(stack.single.isRoot, isTrue);
      expect(stack.single.pageKey, shellRootPageKey);
      expect(stack.single.isImmersive, isFalse);
    }
  });

  test('LOGIN keeps the bar, renders no content and highlights no tab', () {
    final List<AppRouteNode> stack = buildRouteStack(
      _state(screen: AppScreen.login),
    );
    expect(stack, hasLength(1));
    expect(stack.single.destination, AppRouteDestination.login);
    expect(stack.single.isRoot, isTrue);
    expect(stack.single.isImmersive, isFalse);
    expect(shellShowsNavigationBar(_state(screen: AppScreen.login)), isTrue);
    expect(
      resolveBackOutcome(_state(screen: AppScreen.login)),
      BackOutcome.exitApp,
    );
  });

  test('sub-pages push exactly one page above their tab root', () {
    final Map<AppScreen, AppRouteDestination> pushed =
        <AppScreen, AppRouteDestination>{
          AppScreen.favorites: AppRouteDestination.favorites,
          AppScreen.withdrawal: AppRouteDestination.withdrawal,
          AppScreen.vipRecharge: AppRouteDestination.vipRecharge,
          AppScreen.player: AppRouteDestination.player,
          AppScreen.detail: AppRouteDestination.detail,
        };
    pushed.forEach((AppScreen screen, AppRouteDestination destination) {
      final List<AppRouteNode> stack = buildRouteStack(_state(screen: screen));
      expect(stack, hasLength(2), reason: '${screen.name} pushes one page');
      expect(stack.first.isRoot, isTrue);
      expect(stack.last.destination, destination);
      expect(stack.last.pageKey, subPageKey(destination));
    });
  });

  test('the tab under a sub-page is the one it was opened from', () {
    expect(
      _destinations(_state(screen: AppScreen.favorites)).first,
      AppRouteDestination.account,
    );
    expect(
      _destinations(_state(screen: AppScreen.withdrawal)).first,
      AppRouteDestination.funds,
    );
    expect(
      _destinations(_state(screen: AppScreen.vipRecharge)).first,
      AppRouteDestination.funds,
    );
  });

  test('the player root follows playerReturnScreen', () {
    expect(
      _destinations(
        _state(screen: AppScreen.player, playerReturnScreen: AppScreen.search),
      ).first,
      AppRouteDestination.search,
    );
    expect(
      _destinations(
        _state(screen: AppScreen.player, playerReturnScreen: AppScreen.favorites),
      ).first,
      AppRouteDestination.account,
    );
    expect(
      _destinations(_state(screen: AppScreen.player)).first,
      AppRouteDestination.home,
    );
  });

  test('the player is the only immersive page and hides the bar', () {
    expect(
      buildRouteStack(_state(screen: AppScreen.player)).last.isImmersive,
      isTrue,
    );
    for (final AppScreen screen in AppScreen.values) {
      expect(
        shellShowsNavigationBar(_state(screen: screen)),
        screen != AppScreen.player,
        reason: 'bar visibility for ${screen.name}',
      );
    }
  });

  test('deck keys are distinct per pushed page and stable per screen', () {
    final List<AppRouteNode> once = buildRouteStack(
      _state(screen: AppScreen.withdrawal),
    );
    final List<AppRouteNode> twice = buildRouteStack(
      _state(screen: AppScreen.withdrawal),
    );
    expect(once, equals(twice));
    expect(once.map((AppRouteNode n) => n.pageKey).toSet(), hasLength(2));
  });

  test('back priority: overlay beats page, page beats root, funds beats exit', () {
    expect(
      resolveBackOutcome(
        _state(
          screen: AppScreen.player,
          playerReturnScreen: AppScreen.home,
          authPromptVisible: true,
        ),
      ),
      BackOutcome.closeAuthPrompt,
      reason: 'an open auth sheet owns the back gesture',
    );
    expect(
      resolveBackOutcome(
        _state(
          screen: AppScreen.player,
          vipPaywallVisible: true,
          authPromptVisible: true,
        ),
      ),
      BackOutcome.closeAuthPrompt,
      reason: 'the auth sheet sits above the paywall',
    );
    expect(
      resolveBackOutcome(_state(screen: AppScreen.player, vipPaywallVisible: true)),
      BackOutcome.closeVipPaywall,
    );
    expect(
      resolveBackOutcome(_state(screen: AppScreen.withdrawal)),
      BackOutcome.popPage,
    );
    expect(
      resolveBackOutcome(_state(screen: AppScreen.funds)),
      BackOutcome.navigateToAccount,
      reason: 'Kotlin BackHandler: FUNDS -> ACCOUNT',
    );
    for (final AppScreen screen in <AppScreen>[
      AppScreen.home,
      AppScreen.search,
      AppScreen.account,
      AppScreen.login,
    ]) {
      expect(
        resolveBackOutcome(_state(screen: screen)),
        BackOutcome.exitApp,
        reason: '${screen.name} is a tab root with no back target',
      );
    }
  });

  test('a popped page maps to the controller call that owns it', () {
    expect(
      shellExitForPoppedDestination(AppRouteDestination.favorites),
      ShellExit.account,
    );
    expect(
      shellExitForPoppedDestination(AppRouteDestination.withdrawal),
      ShellExit.funds,
    );
    expect(
      shellExitForPoppedDestination(AppRouteDestination.vipRecharge),
      ShellExit.funds,
    );
    expect(
      shellExitForPoppedDestination(AppRouteDestination.player),
      ShellExit.playbackSource,
    );
    for (final AppRouteDestination root in <AppRouteDestination>[
      AppRouteDestination.home,
      AppRouteDestination.search,
      AppRouteDestination.account,
      AppRouteDestination.funds,
      AppRouteDestination.login,
    ]) {
      expect(
        shellExitForPoppedDestination(root),
        isNull,
        reason: '${root.name} is a root page and is never popped on its own',
      );
    }
  });
}
