/// Wiring tests for the UI hosts fixed during the parity audit: the account
/// screen's guest entries and wallet sheet lifecycle, the full watch-history
/// sheet, the player's action rail and error overlay, and the system-back
/// contract on the immersive screens.
library;

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/main.dart';
import 'package:shortlink/state/app_state_controller.dart';
import 'package:shortlink/state/app_ui_state.dart';
import 'package:shortlink/update/release_downloader.dart';
import 'package:shortlink/update/release_models.dart';
import 'package:shortlink/update/short_link_update_client.dart';
import 'package:shortlink/update/update_coordinator.dart';
import 'package:shortlink/ui/screens/player_screen.dart';

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

void main() {
  testWidgets('guest account page offers sign-in and register entries and no wallet row', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()..session = null;
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openAccount();
    await tester.pumpAndSettle();

    expect(find.text('Sign in'), findsWidgets);
    expect(find.text('Create account'), findsOneWidget);
    expect(find.text('Bind wallet'), findsNothing);
  });

  testWidgets('the wallet sheet closes after a successful bind even when the wallet stays unbound', (
    WidgetTester tester,
  ) async {
    // The fake keeps `wallet.walletAddress` null after the bind; the sheet must
    // still close on the `walletMutationVersion` advance (the unbind path of
    // the Kotlin `walletSheetShouldDismiss` contract).
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

    await tester.enterText(find.byType(TextField).at(0), '0xabc');
    await tester.enterText(find.byType(TextField).at(1), 'password-1');
    // One frame so the submit gate re-evaluates with the typed values.
    await tester.pump();
    await tester.tap(find.text('Bind wallet').last);
    await tester.pumpAndSettle();

    // The sheet is gone and the mutation landed exactly once.
    expect(dataSource.callsTo('bindWallet'), 1);
    expect(controller.state.walletMutationVersion, greaterThanOrEqualTo(1));
    expect(find.byType(TextField), findsNothing);
  });

  testWidgets('the watch-history quick entry opens the full history sheet', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..watchHistory = <WatchRecord>[
        const WatchRecord(
          bookId: 'book-1',
          bookTitle: 'Drama One',
          episode: 3,
          progressPercent: 25,
        ),
      ]
      ..episodes = <EpisodeSummary>[
        const EpisodeSummary(number: 1, chapterId: 'ch-1', title: 'EP 1'),
        const EpisodeSummary(number: 2, chapterId: 'ch-2', title: 'EP 2'),
        const EpisodeSummary(number: 3, chapterId: 'ch-3', title: 'EP 3'),
      ];
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await controller.openAccount();
    await tester.pumpAndSettle();

    await tester.tap(find.text('Watch history'));
    await tester.pumpAndSettle();

    // The full record list is in the sheet, not the two-row preview.
    expect(find.text('Drama One'), findsWidgets);
    expect(find.byIcon(Icons.chevron_right), findsWidgets);

    // Tapping a record resumes that drama; the player's cover spinner keeps
    // animating, so settle with timed frames instead of pumpAndSettle.
    await tester.tap(find.text('Drama One').last);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(controller.state.screen, AppScreen.player);
    controller.backToPlaybackSource();
    await tester.pump(const Duration(milliseconds: 300));
  });

  testWidgets('the player shows the action rail and bottom info', (
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

    // The test environment has no video decoder: the cover overlay keeps its
    // spinner animating, so settle with timed frames instead of pumpAndSettle.
    await controller.openBook(FakeAppDataSource.book0);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 600));

    expect(controller.state.screen, AppScreen.player);
    expect(find.byType(PlayerScreen), findsOneWidget);
    expect(find.byIcon(Icons.favorite_border), findsOneWidget);
    expect(find.byIcon(Icons.bookmark_border), findsOneWidget);
    expect(find.byIcon(Icons.mode_comment_outlined), findsOneWidget);
    expect(find.text('Favorite'), findsOneWidget);
    // Bottom info: the book title and the episode selector entry.
    expect(find.text('Drama One'), findsWidgets);

    // Leave the player so its video controller disposes inside this test.
    controller.backToPlaybackSource();
    await tester.pump(const Duration(milliseconds: 300));
  });

  testWidgets('system back on funds returns to account, not out of the app', (
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

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(controller.state.screen, AppScreen.account);
  });

  testWidgets('the discover tab mounts the search screen with groups and field', (
    WidgetTester tester,
  ) async {
    // Regression: the discovery groups Column once sat directly in the
    // CustomScrollView slivers list, which throws
    // "RenderFlex is not a subtype of RenderSliver" on first mount and leaves
    // the tab blank on real devices.
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await _pumpApp(tester, controller);

    await tester.tap(find.text('Discover'));
    await tester.pumpAndSettle();

    expect(controller.state.screen, AppScreen.search);
    // The mount itself is the assertion: the sliver/box mismatch throws during
    // the first build, failing the test before this line.
    expect(find.byType(TextField), findsOneWidget);
    expect(find.text('Search'), findsWidgets);
  });
}
