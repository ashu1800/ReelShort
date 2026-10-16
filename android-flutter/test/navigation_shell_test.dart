/// Phase 3 wiring tests: the declarative page stack, the shared sub-page
/// chrome, the back-gesture priority and the two global overlay hosts.
///
/// These are widget tests on purpose — the pure route contract is already
/// covered by `routing_contract_test.dart`, so what is asserted here is that
/// the shell actually *renders* `buildRouteStack(state)`, that the system back
/// gesture lands where `resolveBackOutcome(state)` says it must, that a tab
/// switch crossfades the root page, and that the global message bar is hosted
/// above the Navigator (so no modal sheet can cover it, dim it or swallow its
/// taps). The shared back driver is `tester.binding.handlePopRoute()`, the same
/// one `ui_wiring_test.dart` uses for the pre-Phase-3 back contract.
library;

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'package:shortlink/core/formatting/app_ui_copy.dart';
import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/main.dart';
import 'package:shortlink/state/app_state_controller.dart';
import 'package:shortlink/state/app_ui_state.dart';
import 'package:shortlink/theme/app_theme.dart';
import 'package:shortlink/ui/app_shell.dart';
import 'package:shortlink/ui/components/app_message_bar.dart';
import 'package:shortlink/ui/components/app_sub_page.dart';
import 'package:shortlink/ui/screens/home_screen.dart';
import 'package:shortlink/ui/screens/player_screen.dart';
import 'package:shortlink/ui/screens/search_screen.dart';
import 'package:shortlink/ui/screens/withdrawal_screen.dart';
import 'package:shortlink/ui/sheets/auth_sheet.dart';
import 'package:shortlink/ui/sheets/vip_paywall_sheet.dart';
import 'package:shortlink/update/release_downloader.dart';
import 'package:shortlink/update/release_models.dart';
import 'package:shortlink/update/short_link_update_client.dart';
import 'package:shortlink/update/update_coordinator.dart';

import 'support/fake_app_data_source.dart';

/// An offline self-update coordinator: these tests never reach the network.
UpdateCoordinator _offlineUpdateCoordinator() => UpdateCoordinator(
  releaseClient: ShortLinkUpdateClient(
    httpClient: http.Client(),
    currentVersionName: '0.0.0',
  ),
  downloader: HttpReleaseDownloader(
    httpClient: http.Client(),
    targetDirectory: Directory.systemTemp,
  ),
  currentVersion: const SemanticVersion(0, 0, 0),
  availableBytes: (Directory _) => 0,
);

Future<void> _pumpApp(WidgetTester tester, AppStateController controller) async {
  await tester.pumpWidget(
    ShortLinkApp(
      controller: controller,
      updateCoordinator: _offlineUpdateCoordinator(),
    ),
  );
  await tester.pumpAndSettle();
}

/// The shell's own `PopScope`: the widget that owns the exit contract.
PopScope<Object?> _shellPopScope(WidgetTester tester) =>
    tester.widget<PopScope<Object?>>(find.byKey(shellPopScopeKey));

/// The player keeps a spinner animating without a video decoder, so the
/// player tests settle with timed frames instead of `pumpAndSettle`.
Future<void> _settlePlayer(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
  await tester.pump(const Duration(milliseconds: 600));
}

/// The message bar owns a 4 s countdown strip, so `pumpAndSettle` would drain
/// the countdown and auto-dismiss the message a test is asserting on; the
/// message-bar test settles with timed frames instead. The fractions cover a
/// sheet's ~200 ms exit (which starts in the post-frame reconciliation) plus the
/// frame that unmounts the popped route, while staying inside the countdown.
Future<void> _settleMessage(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
  await tester.pump(const Duration(milliseconds: 300));
}

void main() {
  testWidgets('system back on the funds tab root returns to account, not out of the app', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openFunds();
    await tester.pumpAndSettle();
    expect(controller.state.screen, AppScreen.funds);
    // The funds root is a tab root with a back target, so it must not let the
    // platform close the app.
    expect(_shellPopScope(tester).canPop, isFalse);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(controller.state.screen, AppScreen.account);
  });

  testWidgets('system back on the withdrawal sub-page returns to funds', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openFunds();
    await tester.pumpAndSettle();
    await controller.openWithdrawal();
    // A pushed sub-page rides the theme's reel-change transition: the whole
    // page drops in from just above the viewport, so mid-entrance its top edge
    // is still above y = 0 and it settles at 0.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 80));
    expect(
      tester.getTopLeft(find.byType(AppSubPage)).dy,
      lessThan(0),
      reason: 'sub-pages inherit the reel-change page transition',
    );
    await tester.pumpAndSettle();
    expect(tester.getTopLeft(find.byType(AppSubPage)).dy, 0);
    expect(controller.state.screen, AppScreen.withdrawal);
    expect(find.byType(WithdrawalScreen), findsOneWidget);
    expect(_shellPopScope(tester).canPop, isFalse);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(controller.state.screen, AppScreen.funds);
    expect(find.byType(WithdrawalScreen), findsNothing);
  });

  testWidgets('system back on the favorites sub-page returns to account', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openAccount();
    await tester.pumpAndSettle();
    await controller.openFavorites();
    await tester.pumpAndSettle();
    expect(controller.state.screen, AppScreen.favorites);
    expect(find.byType(AppSubPage), findsOneWidget);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(controller.state.screen, AppScreen.account);
  });

  testWidgets('system back on the player returns to the screen playback started from', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..episodes = <EpisodeSummary>[
        const EpisodeSummary(number: 1, chapterId: 'ch-1', title: 'EP 1'),
        const EpisodeSummary(number: 2, chapterId: 'ch-2', title: 'EP 2'),
      ];
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    controller.showSearch();
    await tester.pumpAndSettle();
    await controller.openBook(FakeAppDataSource.book0);
    // The player is the one page that keeps the full-screen slide-up instead of
    // the reel-change settle, so mid-entrance it is still below the viewport.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 80));
    expect(
      tester.getTopLeft(find.byType(PlayerScreen)).dy,
      greaterThan(0),
      reason: 'the player keeps its slide-up + fade entrance',
    );
    await _settlePlayer(tester);
    expect(controller.state.screen, AppScreen.player);
    expect(controller.state.playerReturnScreen, AppScreen.search);

    await tester.binding.handlePopRoute();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(controller.state.screen, AppScreen.search);
  });

  testWidgets('system back with the auth panel open closes only the panel', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()..session = null;
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openAccount();
    await tester.pumpAndSettle();
    controller.showAuthPrompt();
    await tester.pumpAndSettle();
    expect(controller.state.authPromptVisible, isTrue);
    expect(find.byType(AuthSheet), findsOneWidget);
    expect(controller.state.screen, AppScreen.account);
    // Modal routes belong to the *root* navigator, not to the shell's page
    // navigator: that is what puts the sheet above the bottom bar and the
    // player, and what lets the route own the back gesture.
    expect(
      Navigator.of(tester.element(find.byType(AuthSheet))),
      same(ShortLinkApp.navigatorKey.currentState),
    );

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(controller.state.authPromptVisible, isFalse);
    expect(find.byType(AuthSheet), findsNothing);
    // The overlay owns the back gesture: the screen underneath did not move.
    expect(controller.state.screen, AppScreen.account);
  });

  testWidgets('system back with the VIP paywall open closes only the paywall', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openAccount();
    await tester.pumpAndSettle();
    await controller.showVipPaywall();
    await tester.pumpAndSettle();
    expect(controller.state.vipPaywallVisible, isTrue);
    expect(find.byType(VipPaywallSheet), findsOneWidget);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(controller.state.vipPaywallVisible, isFalse);
    expect(find.byType(VipPaywallSheet), findsNothing);
    expect(controller.state.screen, AppScreen.account);
  });

  testWidgets('the global message bar stays tappable above an open sheet', (
    WidgetTester tester,
  ) async {
    // Regression guard for the modal-route overlays: the bar is hosted above the
    // root Navigator (in `main.dart`), so it is painted and hit-tested above the
    // sheet's barrier. Hosted inside the shell route instead, this tap would hit
    // the scrim — which closes the panel and leaves the message in place, so the
    // assertions below fail.
    //
    // The bar owns a 4 s countdown strip, so these steps settle with timed
    // frames: `pumpAndSettle` would run the countdown out and auto-dismiss the
    // very message under test.
    final FakeAppDataSource dataSource = FakeAppDataSource()..session = null;
    dataSource.failWith('login', Exception('Wrong username or password.'));
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openAccount();
    await tester.pumpAndSettle();
    controller.showAuthPrompt();
    await tester.pumpAndSettle();
    expect(find.byType(AuthSheet), findsOneWidget);

    // A failed login surfaces through the global message bar (the sheet has no
    // inline errors) and leaves the panel open.
    await controller.login('tester', 'wrong-password');
    await _settleMessage(tester);
    expect(controller.state.errorMessage, isNotNull);
    expect(controller.state.authPromptVisible, isTrue);
    expect(find.byType(AppMessageBar), findsOneWidget);
    expect(
      find.ancestor(
        of: find.byType(AppMessageBar),
        matching: find.byType(Navigator),
      ),
      findsNothing,
      reason: 'the bar must live above the Navigator, not inside a route',
    );

    // The tap reaches the bar, not the scrim behind it.
    await tester.tap(find.byType(AppMessageBar));
    await _settleMessage(tester);
    expect(controller.state.errorMessage, isNull);
    expect(controller.state.authPromptVisible, isTrue);
    expect(find.byType(AuthSheet), findsOneWidget);
  });

  testWidgets('the message layer above the Navigator passes taps through', (
    WidgetTester tester,
  ) async {
    // The bar lives in its own overlay layer above the Navigator; that layer has
    // to stay transparent to input, or the whole app underneath would stop
    // responding while a message is on screen.
    final FakeAppDataSource dataSource = FakeAppDataSource()..session = null;
    dataSource.failWith('login', Exception('Wrong username or password.'));
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    // A guest login failure surfaces the bar without opening the auth panel.
    await controller.login('tester', 'wrong-password');
    await _settleMessage(tester);
    expect(controller.state.errorMessage, isNotNull);
    expect(controller.state.authPromptVisible, isFalse);
    expect(find.byType(AppMessageBar), findsOneWidget);

    await tester.tap(find.text('Discover'));
    await _settleMessage(tester);
    // The tap travelled through the message layer into the shell: the tab
    // switched, and switching tabs clears the message like any navigation does.
    expect(controller.state.screen, AppScreen.search);
    expect(controller.state.errorMessage, isNull);

    // Leave no 4 s countdown running behind the test.
    controller.clearError();
    await _settleMessage(tester);
    expect(controller.state.errorMessage, isNull);
  });

  testWidgets('a guest drama tap opens the auth panel and the state closes it', (
    WidgetTester tester,
  ) async {
    // The state machine is still the only thing that decides whether the sheet
    // exists; the shell only mirrors it onto a modal route. This covers the
    // "closed by the state" branch (a successful login, a dismissed intent),
    // which is the side the back-gesture tests do not reach.
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..session = null
      ..episodes = <EpisodeSummary>[
        const EpisodeSummary(number: 1, chapterId: 'ch-1', title: 'EP 1'),
      ];
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openBook(FakeAppDataSource.book0);
    await tester.pumpAndSettle();
    expect(controller.state.authPromptVisible, isTrue);
    expect(find.byType(AuthSheet), findsOneWidget);
    expect(controller.state.screen, AppScreen.home);

    controller.dismissAuthPrompt();
    await tester.pumpAndSettle();
    expect(controller.state.authPromptVisible, isFalse);
    expect(find.byType(AuthSheet), findsNothing);
    expect(controller.state.screen, AppScreen.home);
  });

  testWidgets('the sub-page chrome renders across the design-system matrix', (
    WidgetTester tester,
  ) async {
    // AppSubPage is the Phase 3 addition to the component set, so it carries
    // the same promise as the Phase 2 components: both brightnesses, from
    // 320 px to 1440 px wide, up to 3.2x text scale, zero layout exceptions.
    for (final Brightness brightness in Brightness.values) {
      for (final double width in <double>[320, 1440]) {
        for (final double scale in <double>[1, 2, 3.2]) {
          await tester.pumpWidget(
            MaterialApp(
              theme: appTheme(brightness),
              home: MediaQuery(
                data: MediaQueryData(
                  size: Size(width, 640),
                  textScaler: TextScaler.linear(scale),
                ),
                child: Scaffold(
                  body: AppSubPage(
                    title: 'Withdraw',
                    onBack: () {},
                    scrollable: true,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: const <Widget>[
                        SizedBox(height: 240),
                        Text('Page body'),
                        SizedBox(height: 240),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          );
          await tester.pump(const Duration(milliseconds: 50));
          expect(
            tester.takeException(),
            isNull,
            reason: 'AppSubPage at $brightness ${width}px scale $scale',
          );
        }
      }
    }
  });

  testWidgets('a tab root with nothing above it can be closed by the platform', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    // Home is the initial tab root.
    expect(controller.state.screen, AppScreen.home);
    expect(_shellPopScope(tester).canPop, isTrue);

    await controller.openAccount();
    await tester.pumpAndSettle();
    expect(controller.state.screen, AppScreen.account);
    expect(_shellPopScope(tester).canPop, isTrue);
  });

  testWidgets('tapping a tab from a funds sub-page shrinks the stack without a return', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openFunds();
    await tester.pumpAndSettle();
    await controller.openWithdrawal();
    await tester.pumpAndSettle();
    expect(controller.state.screen, AppScreen.withdrawal);

    // Record every screen the controller reports while the tab is switched: a
    // spurious `backToFunds()` dispatch from `onDidRemovePage` would surface
    // here as a `funds` frame.
    final List<AppScreen> observed = <AppScreen>[];
    void listener() => observed.add(controller.state.screen);
    controller.addListener(listener);
    await tester.tap(find.text('Home'));
    await tester.pumpAndSettle();
    controller.removeListener(listener);

    expect(controller.state.screen, AppScreen.home);
    expect(observed, isNot(contains(AppScreen.funds)));
    expect(find.byType(WithdrawalScreen), findsNothing);
  });

  testWidgets('a back press that lands with a same-turn tab switch keeps the newer tab', (
    WidgetTester tester,
  ) async {
    // Race guard: `NavigatorState.maybePop` awaits `route.willPop()`, so the pop
    // — and therefore `onDidRemovePage` — completes a microtask later than the
    // back gesture. A transition issued in that window (here a tab switch) is
    // the newer intent, and the return the pop implies must not be dispatched on
    // top of it. The dispatch is judged against the *current* state, so no
    // `backToFunds()` frame may appear after the switch.
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openFunds();
    await tester.pumpAndSettle();
    await controller.openWithdrawal();
    await tester.pumpAndSettle();
    expect(controller.state.screen, AppScreen.withdrawal);

    final List<AppScreen> observed = <AppScreen>[];
    void listener() => observed.add(controller.state.screen);
    controller.addListener(listener);
    // Not awaited before the switch: the back gesture is still in flight.
    final Future<void> back = tester.binding.handlePopRoute();
    controller.showSearch();
    // Let the in-flight pop land now that the state has already moved on.
    await back;
    await tester.pumpAndSettle();
    controller.removeListener(listener);

    expect(controller.state.screen, AppScreen.search);
    expect(
      observed,
      isNot(contains(AppScreen.funds)),
      reason: 'a stale return dispatch must not undo the newer tab switch',
    );
  });

  testWidgets('switching tabs crossfades the root page', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);
    expect(find.byType(HomeScreen), findsOneWidget);

    controller.showSearch();
    await tester.pump();
    // Both tab roots are mounted mid-transition: the outgoing shelf fades out
    // while the incoming discover tab fades in (the parity 220 ms crossfade,
    // not an instant swap and not a route push).
    expect(
      find.byType(HomeScreen),
      findsOneWidget,
      reason: 'the outgoing tab root must still be mounted while it fades out',
    );
    expect(find.byType(SearchScreen), findsOneWidget);

    await tester.pumpAndSettle();
    expect(find.byType(HomeScreen), findsNothing);
    expect(find.byType(SearchScreen), findsOneWidget);
    expect(controller.state.screen, AppScreen.search);
  });

  testWidgets('reduced motion collapses the tab crossfade to an instant swap', (
    WidgetTester tester,
  ) async {
    tester.platformDispatcher.accessibilityFeaturesTestValue =
        const FakeAccessibilityFeatures(disableAnimations: true);
    addTearDown(
      tester.platformDispatcher.clearAccessibilityFeaturesTestValue,
    );
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    expect(
      tester
          .widget<AnimatedSwitcher>(
            find.ancestor(
              of: find.byType(HomeScreen),
              matching: find.byType(AnimatedSwitcher),
            ),
          )
          .duration,
      Duration.zero,
      reason: 'the crossfade duration must go through AppMotion.resolve',
    );

    controller.showSearch();
    // Two zero-duration pumps: no frame ever shows two tab roots at once.
    await tester.pump();
    await tester.pump();
    expect(find.byType(HomeScreen), findsNothing);
    expect(find.byType(SearchScreen), findsOneWidget);
  });

  testWidgets('a pushed sub-page renders the shared chrome and its back returns', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openFunds();
    await tester.pumpAndSettle();
    await controller.openWithdrawal();
    await tester.pumpAndSettle();

    expect(find.byType(AppSubPage), findsOneWidget);
    expect(find.text('Withdraw'), findsWidgets);
    final Finder back = find.byTooltip(AppUiCopy.back);
    expect(back, findsOneWidget);
    expect(
      tester.getSize(back).width,
      greaterThanOrEqualTo(AppSubPage.backHitSize),
      reason: 'the back affordance must keep a >= 44 px touch target',
    );
    expect(
      tester.getSize(back).height,
      greaterThanOrEqualTo(AppSubPage.backHitSize),
      reason: 'the back affordance must keep a >= 44 px touch target',
    );

    await tester.tap(back);
    await tester.pumpAndSettle();
    expect(controller.state.screen, AppScreen.funds);
  });

  testWidgets('a sheet a screen opened for itself owns the back gesture', (
    WidgetTester tester,
  ) async {
    // Regression guard for the nested navigator: the wallet sheet is pushed by
    // the account screen onto the shell's page navigator, so the shell must let
    // that navigator pop it instead of treating the back press as an app exit.
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openAccount();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Bind wallet'));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNWidgets(2));
    expect(_shellPopScope(tester).canPop, isFalse);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNothing);
    expect(controller.state.screen, AppScreen.account);
  });
}
