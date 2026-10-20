/// Contract suite for the redesigned episode sheet.
///
/// The sheet replaced 38 list rows ("EP 04 · title" plus a slice of the episode
/// spine) with one season overview bar, a numeric grid and — while the viewer
/// is not a member — a split at the VIP boundary with an unlock banner.
///
/// The boundary itself is the point of this suite. It used to be the client
/// constant `defaultVipFreeEpisodes = 7` in three separate screens, which drifted
/// from the backend's `vip.free-episodes` system config as soon as an operator
/// changed it; the play endpoint would then reject an episode the app had drawn
/// as free. Every test here drives the boundary through the account snapshot the
/// backend now returns.
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:shortlink/core/formatting/app_strings.dart';
import 'package:shortlink/core/formatting/app_ui_copy.dart';
import 'package:shortlink/data/models/app_language.dart';
import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/state/app_state_controller.dart';
import 'package:shortlink/theme/app_theme.dart';
import 'package:shortlink/ui/components/app_button.dart';
import 'package:shortlink/ui/components/app_section.dart';
import 'package:shortlink/ui/components/episode_spine.dart';
import 'package:shortlink/ui/components/episode_tile.dart';
import 'package:shortlink/ui/screens/player_screen.dart';

import 'support/fake_app_data_source.dart';

/// Episodes 1..`count` with stable chapter ids.
List<EpisodeSummary> _episodes(int count) => <EpisodeSummary>[
  for (int number = 1; number <= count; number++)
    EpisodeSummary(number: number, chapterId: 'ch-$number', title: ''),
];

/// A controller already positioned on the first episode of `episodes`, then a
/// mounted player, so the sheet can be opened through the real entry point.
Future<AppStateController> _pumpPlayer(
  WidgetTester tester, {
  required int count,
  bool vip = false,
  int freeEpisodes = defaultVipFreeEpisodes,
  double width = 360,
  double height = 800,
  double textScale = 1,
}) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = Size(width, height);
  addTearDown(tester.view.reset);

  final List<EpisodeSummary> episodes = _episodes(count);
  final FakeAppDataSource source = FakeAppDataSource()
    ..episodes = episodes
    ..session = const AuthSession(
      username: 'ashu180',
      token: 'token-1',
      tokenType: 'Bearer',
    )
    ..pointAccount = PointAccount(
      balance: 100,
      records: const <PointRecord>[],
      vipActive: vip,
      freeEpisodes: freeEpisodes,
    );
  final AppStateController controller = AppStateController(dataSource: source);
  addTearDown(controller.dispose);

  await controller.restoreSession();
  await controller.openBook(FakeAppDataSource.book0);
  await controller.openPlayer(episodes.first);

  await tester.pumpWidget(
    ChangeNotifierProvider<AppStateController>.value(
      value: controller,
      child: MaterialApp(
        theme: appTheme(Brightness.light),
        home: MediaQuery(
          data: MediaQueryData(
            size: Size(width, height),
            textScaler: TextScaler.linear(textScale),
          ),
          child: const PlayerScreen(),
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
  return controller;
}

/// Opens the episode sheet from the player's bottom entry.
Future<void> _openSheet(WidgetTester tester) async {
  await tester.tap(find.textContaining(AppUiCopy.playerEpisodes));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
}

/// The section headers currently on the sheet, by title.
List<String> _headers(WidgetTester tester) => tester
    .widgetList<AppSectionHeader>(find.byType(AppSectionHeader))
    .map((AppSectionHeader header) => header.title)
    .toList();

void main() {
  final AppStrings copy = strings(AppLanguage.defaultLanguage);

  testWidgets('every episode is one grid cell and the bar is a single line', (
    WidgetTester tester,
  ) async {
    await _pumpPlayer(tester, count: 38, vip: true);
    await _openSheet(tester);

    // A member sees the whole season at once: 38 cells, no split headers and no
    // banner, because nothing is gated for them. (The player behind the sheet
    // may own `AppButton`s of its own, so the check names the unlock action.)
    expect(find.byType(EpisodeTile), findsNWidgets(38));
    expect(find.byType(EpisodeSpine), findsOneWidget);
    // Membership lifts the gate for every cell: not one lock glyph, including
    // episodes past the free run.
    expect(find.byIcon(Icons.lock), findsNothing);
    expect(_headers(tester), isEmpty);
    expect(find.text(AppUiCopy.vipUnlockRangePrefix), findsNothing);
    expect(
      find.widgetWithText(AppButton, copy.accountVipUnlockAction),
      findsNothing,
    );
  });

  testWidgets('cells keep the 44dp touch minimum at 320 and 360 dp', (
    WidgetTester tester,
  ) async {
    for (final double width in <double>[320, 360]) {
      await _pumpPlayer(tester, count: 12, vip: true, width: width);
      await _openSheet(tester);

      final Finder tiles = find.byType(EpisodeTile);
      expect(tiles, findsNWidgets(12), reason: 'width $width');
      for (int index = 0; index < 12; index++) {
        final Size size = tester.getSize(tiles.at(index));
        expect(
          size.width,
          greaterThanOrEqualTo(episodeTileMinExtent),
          reason: 'width $width cell $index',
        );
        expect(
          size.height,
          greaterThanOrEqualTo(episodeTileMinExtent),
          reason: 'width $width cell $index',
        );
      }
      // Close the sheet before the next pass mounts a second player.
      await tester.tap(find.bySemanticsLabel(AppUiCopy.close));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
    }
  });

  testWidgets('the lock follows the backend free-episode count, not episode 8', (
    WidgetTester tester,
  ) async {
    // The regression this sheet exists for: the operator lowered the free run
    // to 5, so episodes 6 and up are gated even though the client constant says
    // 7. The play endpoint enforces 5, and the grid must agree.
    final AppStateController controller = await _pumpPlayer(
      tester,
      count: 10,
      freeEpisodes: 5,
    );
    await _openSheet(tester);

    expect(_headers(tester), <String>[
      AppUiCopy.episodesFreeSection,
      AppUiCopy.episodesVipSection,
    ]);
    // Free run: EP 01–05, gated run: EP 06–10.
    expect(find.text('EP 01–05'), findsOneWidget);
    expect(find.text('EP 06–10'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byType(EpisodeTile),
        matching: find.byIcon(Icons.lock),
      ),
      findsNWidgets(5),
    );
    // The banner names the same boundary the grid draws.
    expect(
      find.text('${AppUiCopy.vipUnlockRangePrefix}EP 06–10'),
      findsOneWidget,
    );
    expect(find.text(copy.vipPayLockedHint), findsOneWidget);

    // EP 06 is the first gated cell: tapping it opens the paywall for exactly
    // that episode.
    final Finder gate = find.byKey(const ValueKey<String>('ch-6:6'));
    await tester.ensureVisible(gate);
    await tester.pump();
    await tester.tap(gate);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(controller.state.vipPaywallVisible, isTrue);
    expect(controller.state.pendingVipEpisode?.number, 6);
  });

  testWidgets('the default boundary gates from episode 8', (
    WidgetTester tester,
  ) async {
    await _pumpPlayer(tester, count: 10);
    await _openSheet(tester);

    expect(find.text('EP 01–07'), findsOneWidget);
    expect(find.text('EP 08–10'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byType(EpisodeTile),
        matching: find.byIcon(Icons.lock),
      ),
      findsNWidgets(3),
    );
  });

  testWidgets('a free run covering every episode drops the split entirely', (
    WidgetTester tester,
  ) async {
    await _pumpPlayer(tester, count: 6, freeEpisodes: 20);
    await _openSheet(tester);

    expect(_headers(tester), isEmpty);
    expect(find.text(AppUiCopy.vipUnlockRangePrefix), findsNothing);
    expect(find.byIcon(Icons.lock), findsNothing);
    expect(find.byType(EpisodeTile), findsNWidgets(6));
  });

  testWidgets('the unlock action asks for the first gated episode', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(
      tester,
      count: 12,
      freeEpisodes: 4,
    );
    await _openSheet(tester);

    final Finder unlock = find.widgetWithText(
      AppButton,
      copy.accountVipUnlockAction,
    );
    expect(unlock, findsOneWidget);
    final Finder banner = find.text(
      '${AppUiCopy.vipUnlockRangePrefix}EP 05–12',
    );
    await tester.ensureVisible(banner);
    await tester.pump();
    await tester.tap(unlock);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(controller.state.vipPaywallVisible, isTrue);
    expect(controller.state.pendingVipEpisode?.number, 5);
  });

  testWidgets('tapping a free cell switches episode and closes the sheet', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(tester, count: 6);
    await _openSheet(tester);

    await tester.tap(find.byKey(const ValueKey<String>('ch-3:3')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(find.byType(EpisodeTile), findsNothing);
    expect(controller.state.playback.episode?.number, 3);
  });

  testWidgets('re-selecting the current cell only closes the sheet', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(tester, count: 6);
    await _openSheet(tester);

    await tester.tap(find.byKey(const ValueKey<String>('ch-1:1')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(find.byType(EpisodeTile), findsNothing);
    expect(controller.state.playback.episode?.number, 1);
  });

  testWidgets('the grid survives 320dp at 3.2x text without overflow', (
    WidgetTester tester,
  ) async {
    await _pumpPlayer(
      tester,
      count: 20,
      width: 320,
      textScale: 3.2,
    );
    await _openSheet(tester);

    expect(tester.takeException(), isNull);
    // The number shrinks to fit rather than ellipsising out of the cell.
    expect(find.byType(EpisodeTile), findsNWidgets(20));
    expect(find.text('EP 01'), findsOneWidget);
  });

  testWidgets('watched and in-progress cells paint a visible underline', (
    WidgetTester tester,
  ) async {
    // The watch state used to ride on the underline alone; a zero-height fill
    // (FractionallySizedBox without `heightFactor` inside a Positioned) silently
    // erased it and every watched episode looked untouched.
    await tester.pumpWidget(
      MaterialApp(
        theme: appTheme(Brightness.light),
        home: Scaffold(
          body: Center(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                SizedBox(
                  width: 49,
                  height: 49,
                  child: EpisodeTile(
                    number: 1,
                    state: EpisodeTickState.watched,
                    progressPercent: 100,
                    locked: false,
                    semanticLabel: 'EP 01',
                    onTap: () {},
                  ),
                ),
                SizedBox(
                  width: 49,
                  height: 49,
                  child: EpisodeTile(
                    number: 2,
                    state: EpisodeTickState.inProgress,
                    progressPercent: 40,
                    locked: false,
                    semanticLabel: 'EP 02',
                    onTap: () {},
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pump();

    final List<Size> bars = <Size>[
      for (final Element element in find
          .descendant(
            of: find.byType(EpisodeTile),
            matching: find.byType(ColoredBox),
          )
          .evaluate())
        tester.getSize(find.byWidget(element.widget)),
    ];
    expect(bars, hasLength(2), reason: 'one underline per watched/started cell');
    // The hairline border insets the cell content by 1 dp on each side.
    final double inner = tester.getSize(find.byType(EpisodeTile).first).width - 2;
    expect(bars[0].height, greaterThanOrEqualTo(2));
    expect(
      bars[0].width,
      closeTo(inner, 0.5),
      reason: 'a finished episode is full',
    );
    expect(bars[1].height, greaterThanOrEqualTo(2));
    expect(
      bars[1].width,
      closeTo(inner * 0.4, 1),
      reason: 'a started episode is filled to its percentage',
    );
  });

  testWidgets('the accessible label keeps the title the grid stopped printing', (
    WidgetTester tester,
  ) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    final List<EpisodeSummary> episodes = <EpisodeSummary>[
      const EpisodeSummary(
        number: 3,
        chapterId: 'ch-3',
        title: 'The Reckoning',
      ),
    ];
    final FakeAppDataSource source = FakeAppDataSource()
      ..episodes = episodes
      ..session = const AuthSession(
        username: 'ashu180',
        token: 'token-1',
        tokenType: 'Bearer',
      );
    final AppStateController controller = AppStateController(
      dataSource: source,
    );
    addTearDown(controller.dispose);

    await controller.restoreSession();
    await controller.openBook(FakeAppDataSource.book0);
    await controller.openPlayer(episodes.first);

    await tester.pumpWidget(
      ChangeNotifierProvider<AppStateController>.value(
        value: controller,
        child: MaterialApp(
          theme: appTheme(Brightness.light),
          home: MediaQuery(
            data: const MediaQueryData(size: Size(360, 800)),
            child: const PlayerScreen(),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    await _openSheet(tester);

    expect(
      find.bySemanticsLabel(
        episodeTileSemanticLabel(
          number: 3,
          title: 'The Reckoning',
          state: EpisodeTickState.current,
          progressPercent: 0,
          locked: false,
          selected: true,
        ),
      ),
      findsOneWidget,
    );
    handle.dispose();
  });
}
