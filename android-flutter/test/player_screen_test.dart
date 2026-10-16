/// Phase 4 regression suite for the redesigned player chrome.
///
/// Locks the frozen contract of `android-flutter/docs/ui-redesign-notes.md`
/// §7.6: the top bar's touch target and label, the glass action rail with its
/// two-channel selected state, the bottom copy block and its named episode
/// entry, the episode drawer built on `EpisodeSpine.full`, the comment drawer
/// shell, the overlay priority, and the rule that the player paints only from
/// `AppMedia`/`AppColors` tokens.
library;

import 'dart:async';
import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:shortlink/core/formatting/app_strings.dart';
import 'package:shortlink/data/models/app_language.dart';
import 'package:shortlink/core/formatting/app_ui_copy.dart';
import 'package:shortlink/core/formatting/ui_formats.dart';
import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/state/app_state_controller.dart';
import 'package:shortlink/state/app_ui_state.dart';
import 'package:shortlink/theme/app_colors.dart';
import 'package:shortlink/theme/app_media.dart';
import 'package:shortlink/theme/app_theme.dart';
import 'package:shortlink/ui/components/app_button.dart';
import 'package:shortlink/ui/components/app_loading.dart';
import 'package:shortlink/ui/components/app_sheet.dart';
import 'package:shortlink/ui/components/episode_spine.dart';
import 'package:shortlink/ui/screens/player_screen.dart';

import 'support/fake_app_data_source.dart';

/// A data source whose health probe never answers, used to hold the controller
/// in its loading state without leaving a pending timer.
class _StalledAppDataSource extends FakeAppDataSource {
  final Completer<ApiHealthStatus> _stalled = Completer<ApiHealthStatus>();

  @override
  Future<ApiHealthStatus> checkSystemHealth() => _stalled.future;
}

/// A data source whose like status starts unliked and toggles to a liked state,
/// so the rail's selected treatment can be exercised end to end.
class _ToggleableLikeDataSource extends FakeAppDataSource {
  /// Answer of the status load performed when the book opens.
  SocialToggleResult status = const SocialToggleResult(active: false, count: 0);

  /// Answer of the like toggle.
  SocialToggleResult onToggle = const SocialToggleResult(
    active: true,
    count: 1200,
  );

  @override
  Future<SocialToggleResult> loadLikeStatus(BookSummary book) async => status;

  @override
  Future<SocialToggleResult> toggleLike(BookSummary book) async => onToggle;
}

/// `count` episodes, numbered from 1, with the standard chapter ids.
List<EpisodeSummary> _episodes(int count) => <EpisodeSummary>[
  for (int number = 1; number <= count; number++)
    EpisodeSummary(
      number: number,
      chapterId: 'ch-$number',
      title: 'Episode $number title',
    ),
];

/// Mounts the player through its real entry point and settles its chrome.
///
/// The test platform has no video decoder, so the first frame never arrives:
/// the screen stays on its cover overlay. Frames are pumped with explicit
/// durations instead of `pumpAndSettle`.
Future<AppStateController> _pumpPlayer(
  WidgetTester tester, {
  List<EpisodeSummary>? episodes,
  bool vip = false,
  int startEpisode = 1,
  List<Comment> comments = const <Comment>[],
  FakeAppDataSource? dataSource,
  Brightness brightness = Brightness.light,
  double width = 360,
  double height = 800,
  double textScale = 1,
  bool disableAnimations = false,
}) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = Size(width, height);
  addTearDown(tester.view.reset);

  final List<EpisodeSummary> list = episodes ?? _episodes(3);
  final FakeAppDataSource source = dataSource ?? FakeAppDataSource()
    ..episodes = list
    ..comments = comments
    ..pointAccount = PointAccount(
      balance: 100,
      records: const <PointRecord>[],
      vipActive: vip,
    );
  final AppStateController controller = AppStateController(
    dataSource: source,
  );
  addTearDown(controller.dispose);

  // Playback is protected: the player only reaches the media for a member.
  source.session = const AuthSession(
    username: 'ashu180',
    token: 'token-1',
    tokenType: 'Bearer',
  );
  await controller.restoreSession();
  await controller.openBook(FakeAppDataSource.book0);
  await controller.openPlayer(
    list.firstWhere(
      (EpisodeSummary episode) => episode.number == startEpisode,
      orElse: () => list.first,
    ),
  );

  await tester.pumpWidget(
    ChangeNotifierProvider<AppStateController>.value(
      value: controller,
      child: MaterialApp(
        theme: appTheme(brightness),
        home: MediaQuery(
          data: MediaQueryData(
            size: Size(width, height),
            textScaler: TextScaler.linear(textScale),
            disableAnimations: disableAnimations,
          ),
          child: const PlayerScreen(),
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
  await tester.pump(const Duration(milliseconds: 600));
  return controller;
}

/// Leaves the player so its video controller disposes inside the test.
Future<void> _leavePlayer(
  WidgetTester tester,
  AppStateController controller,
) async {
  controller.backToPlaybackSource();
  await tester.pump(const Duration(milliseconds: 300));
}

/// The size of the tap target that owns [icon].
Size _targetAround(WidgetTester tester, Finder icon) => tester.getSize(
  find.ancestor(of: icon, matching: find.byType(InkWell)).first,
);

/// WCAG relative luminance of an opaque colour.
double _luminance(Color color) {
  double channel(double value) => value <= 0.03928
      ? value / 12.92
      : math.pow((value + 0.055) / 1.055, 2.4).toDouble();
  return 0.2126 * channel(color.r) +
      0.7152 * channel(color.g) +
      0.0722 * channel(color.b);
}

/// WCAG contrast ratio between two opaque colours.
double _contrastRatio(Color a, Color b) {
  final double first = _luminance(a);
  final double second = _luminance(b);
  final double lighter = math.max(first, second);
  final double darker = math.min(first, second);
  return (lighter + 0.05) / (darker + 0.05);
}

/// The button that owns [label].
Finder _buttonFor(String label) => find.ancestor(
  of: find.text(label),
  matching: find.byType(AppButton),
);

/// The resolved label colour of a button.
Color _buttonLabelColor(WidgetTester tester, String label) => tester
    .widget<Text>(
      find
          .descendant(of: _buttonFor(label), matching: find.byType(Text))
          .first,
    )
    .style!
    .color!;

/// The English copy the player renders in these tests.
final AppStrings _copy = strings(AppLanguage.defaultLanguage);

void main() {
  testWidgets('the top bar keeps a 44px back target with the shared label', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(tester);

    final Finder back = find.byTooltip(AppUiCopy.back);
    expect(back, findsOneWidget);
    final Size target = tester.getSize(back);
    expect(target.width, greaterThanOrEqualTo(44));
    expect(target.height, greaterThanOrEqualTo(44));
    expect(find.bySemanticsLabel(AppUiCopy.back), findsWidgets);
    // The old label was the screen name, which never said "go back".
    expect(find.bySemanticsLabel('Player'), findsNothing);

    await tester.tap(back);
    await tester.pump(const Duration(milliseconds: 300));
    expect(controller.state.screen, isNot(AppScreen.player));
  });

  testWidgets('the action rail keeps three 44px actions and their counts', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(
      tester,
      comments: const <Comment>[
        Comment(
          id: 'c1',
          username: 'ashu',
          content: 'Great episode',
          createdAt: '2026-10-01T00:00:00Z',
        ),
      ],
    );

    for (final IconData icon in <IconData>[
      Icons.favorite_border,
      Icons.mode_comment_outlined,
      Icons.bookmark_border,
    ]) {
      final Finder finder = find.byIcon(icon);
      expect(finder, findsOneWidget);
      final Size size = _targetAround(tester, finder);
      expect(size.width, greaterThanOrEqualTo(44), reason: '$icon width');
      expect(size.height, greaterThanOrEqualTo(44), reason: '$icon height');
    }
    // The comment count is the list size; the favourite keeps its label.
    expect(find.text('1'), findsOneWidget);
    expect(find.text('Favorite'), findsOneWidget);
    // The announced labels name the action instead of a bare count.
    expect(find.bySemanticsLabel(AppUiCopy.playerComments), findsNothing);
    expect(find.bySemanticsLabel('${AppUiCopy.playerComments}, 1'), findsOneWidget);
    expect(find.bySemanticsLabel(AppUiCopy.playerLike), findsOneWidget);
    await _leavePlayer(tester, controller);
  });

  testWidgets('a liked rail item switches to the filled glyph and the accent', (
    WidgetTester tester,
  ) async {
    final _ToggleableLikeDataSource dataSource = _ToggleableLikeDataSource();
    final AppStateController controller = await _pumpPlayer(
      tester,
      dataSource: dataSource,
    );
    // Unliked to start: the outline glyph in the media foreground.
    final Icon before = tester.widget<Icon>(find.byIcon(Icons.favorite_border));
    expect(before.color, AppMedia.standard.onMedia);

    await controller.toggleLike();
    await tester.pump();

    final Finder liked = find.byIcon(Icons.favorite);
    expect(liked, findsOneWidget);
    expect(find.byIcon(Icons.favorite_border), findsNothing);
    // Second channel: the glyph shape changes *and* the accent lands.
    expect(tester.widget<Icon>(liked).color, AppColors.light.accentDanger);
    // The count still goes through `formatCount`.
    expect(find.text('1k'), findsOneWidget);
    await _leavePlayer(tester, controller);
  });

  testWidgets('the bottom copy names the book and opens the episode drawer', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(tester);

    expect(find.text('Drama One'), findsWidgets);
    final Finder entry = find.textContaining(AppUiCopy.playerEpisodes);
    expect(entry, findsOneWidget);
    expect(find.text('Episodes · EP 01'), findsOneWidget);

    await tester.tap(entry);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byType(AppSheet), findsOneWidget);
    await _leavePlayer(tester, controller);
  });

  testWidgets('the reward ring reads its figure on a chrome plate', (
    WidgetTester tester,
  ) async {
    await _pumpPlayer(tester, vip: true);

    // Geometry is unchanged; the plate is what makes the figure deterministic.
    final Finder plate = find.ancestor(
      of: find.byType(CircularProgressIndicator),
      matching: find.byType(Container),
    );
    expect(
      (tester.widget<Container>(plate.first).decoration! as BoxDecoration).color,
      AppMedia.standard.glass,
    );
    final Container disc = tester.widget<Container>(plate.first);
    expect(tester.getSize(plate.first), const Size(48, 48));
    expect(disc.decoration, isNotNull);

    final Text figure = tester.widget<Text>(
      find
          .descendant(
            of: plate.first,
            matching: find.byType(Text),
          )
          .first,
    );
    expect(figure.style?.color, AppMedia.standard.onMedia);
    expect(
      _contrastRatio(AppMedia.standard.onMedia, AppMedia.glassBase),
      greaterThanOrEqualTo(4.5),
    );
  });

  testWidgets('the episode entry is a readable media-plane button', (
    WidgetTester tester,
  ) async {
    // On device the surface-plane `secondary` variant painted a near-black
    // label over the video, so the entry became invisible.
    final AppStateController controller = await _pumpPlayer(tester);

    final Finder label = find.text('Episodes · EP 01');
    expect(label, findsOneWidget);
    expect(tester.widget<Text>(label).style?.color, AppMedia.standard.onMedia);

    final Material plate = tester.widget<Material>(
      find.ancestor(of: label, matching: find.byType(Material)).first,
    );
    expect(plate.color, AppMedia.standard.glass);
    final RoundedRectangleBorder shape =
        plate.shape! as RoundedRectangleBorder;
    expect(shape.side.color, AppMedia.standard.divider);

    // Still a full touch target that opens the drawer.
    final Size target = tester.getSize(
      find.ancestor(of: label, matching: find.byType(InkWell)).first,
    );
    expect(target.width, greaterThanOrEqualTo(44));
    expect(target.height, greaterThanOrEqualTo(44));
    await tester.tap(label);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byType(AppSheet), findsOneWidget);
    await _leavePlayer(tester, controller);
  });

  for (final Brightness brightness in <Brightness>[
    Brightness.light,
    Brightness.dark,
  ]) {
    testWidgets('the error recovery card stays readable in $brightness', (
      WidgetTester tester,
    ) async {
      final AppStateController controller = await _pumpPlayer(
        tester,
        brightness: brightness,
      );
      final AppColors colors = brightness == Brightness.dark
          ? AppColors.dark
          : AppColors.light;

      // The card is a surface plane, so the three variants keep the contrast
      // they were designed for instead of putting `textPrimary` on dark glass.
      final Container card = tester.widget<Container>(
        find
            .ancestor(
              of: find.text(_copy.playerVideoError),
              matching: find.byType(Container),
            )
            .first,
      );
      expect((card.decoration! as BoxDecoration).color, colors.surface);

      final String retry = _copy.playerRetryAction;
      final String next = _copy.playerNextEpisodeAction;
      final String back = _copy.playerBackAction;
      expect(tester.widget<AppButton>(_buttonFor(retry)).variant,
          AppButtonVariant.primary);
      expect(tester.widget<AppButton>(_buttonFor(next)).variant,
          AppButtonVariant.secondary);
      expect(tester.widget<AppButton>(_buttonFor(back)).variant,
          AppButtonVariant.tertiary);
      expect(
        tester
            .widgetList<AppButton>(find.byType(AppButton))
            .where(
              (AppButton button) =>
                  button.variant == AppButtonVariant.primary,
            ),
        hasLength(1),
      );

      // Each label is measured against the plane it actually sits on: the
      // filled primary on the accent, the outlined/quiet pair on the card.
      final Map<String, Color> plane = <String, Color>{
        retry: colors.accentPrimary,
        next: colors.surface,
        back: colors.surface,
      };
      for (final String label in <String>[retry, next, back]) {
        expect(
          tester.getSize(_buttonFor(label)).height,
          greaterThanOrEqualTo(44),
          reason: '$label target',
        );
        expect(
          _contrastRatio(_buttonLabelColor(tester, label), plane[label]!),
          greaterThanOrEqualTo(4.5),
          reason: '$label label contrast in $brightness',
        );
      }
      await _leavePlayer(tester, controller);
    });
  }

  testWidgets('the episode drawer is a spine of 44px ticks', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(
      tester,
      episodes: _episodes(4),
    );
    await tester.tap(find.textContaining(AppUiCopy.playerEpisodes));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    final Finder spines = find.byType(EpisodeSpine);
    expect(spines, findsNWidgets(4));
    for (int index = 0; index < 4; index++) {
      expect(
        tester.widget<EpisodeSpine>(spines.at(index)).size,
        EpisodeSpineSize.full,
        reason: 'the drawer is the component\'s documented full-size consumer',
      );
    }
    // Every row advertises the episode: number, title and status.
    expect(find.text('EP 01 · Episode 1 title'), findsOneWidget);
    expect(find.text('EP 04 · Episode 4 title'), findsOneWidget);
    expect(find.text(_copy.playerEpisodeStatusCurrent), findsOneWidget);

    final Finder ticks = find.descendant(
      of: spines,
      matching: find.byType(InkWell),
    );
    expect(ticks, findsNWidgets(4));
    for (int index = 0; index < 4; index++) {
      final Size size = tester.getSize(ticks.at(index));
      expect(size.width, greaterThanOrEqualTo(44));
      expect(size.height, greaterThanOrEqualTo(44));
    }

    // Tapping another episode switches to it and closes the drawer.
    await tester.tap(find.byKey(const ValueKey<String>('ch-3:3')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byType(AppSheet), findsNothing);
    expect(controller.state.playback.episode?.number, 3);

    await _leavePlayer(tester, controller);
  });

  testWidgets('re-selecting the current episode only closes the drawer', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(tester);
    await tester.tap(find.textContaining(AppUiCopy.playerEpisodes));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    await tester.tap(find.byKey(const ValueKey<String>('ch-1:1')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(find.byType(AppSheet), findsNothing);
    expect(controller.state.playback.episode?.number, 1);
    await _leavePlayer(tester, controller);
  });

  testWidgets('a VIP-locked episode routes to the paywall', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(
      tester,
      episodes: _episodes(10),
    );
    await tester.tap(find.textContaining(AppUiCopy.playerEpisodes));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    // Episodes above the free threshold carry the lock marker.
    // Episodes 8 and above sit behind the gate: 8, 9 and 10 here.
    expect(find.byIcon(Icons.lock), findsNWidgets(3));

    await tester.tap(find.byKey(const ValueKey<String>('ch-8:8')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(controller.state.vipPaywallVisible, isTrue);
    await _leavePlayer(tester, controller);
  });

  testWidgets('the comment drawer keeps its states and clears the draft', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(tester);
    await tester.tap(find.byIcon(Icons.mode_comment_outlined));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    // Empty state from the shared component and copy.
    expect(find.byType(AppSheet), findsOneWidget);
    expect(find.text(AppUiCopy.playerCommentsEmptyTitle), findsOneWidget);
    expect(find.text(AppUiCopy.playerCommentsEmptyBody), findsOneWidget);

    // The send affordance carries its own label and clears the draft.
    await tester.enterText(find.byType(TextField), 'Hello there');
    await tester.pump();
    final Finder send = find.bySemanticsLabel(AppUiCopy.playerCommentSend);
    expect(send, findsOneWidget);
    expect(tester.getSize(send).width, greaterThanOrEqualTo(44));
    await tester.tap(send);
    await tester.pump();
    expect(find.text('Hello there'), findsNothing);
    await _leavePlayer(tester, controller);
  });

  testWidgets('the comment drawer shows the inline loading row', (
    WidgetTester tester,
  ) async {
    // A stalled health probe flips the shared loading flag without any pending
    // timer, which is exactly the "loading and still empty" case.
    final _StalledAppDataSource dataSource = _StalledAppDataSource()
      ..episodes = _episodes(2);
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    addTearDown(controller.dispose);
    await controller.openBook(FakeAppDataSource.book0);
    await controller.openPlayer(_episodes(2).first);
    unawaited(controller.checkApiHealth());

    await tester.pumpWidget(
      ChangeNotifierProvider<AppStateController>.value(
        value: controller,
        child: MaterialApp(
          theme: appTheme(Brightness.light),
          home: const Scaffold(body: PlayerScreen()),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(controller.state.isLoading, isTrue);

    await tester.tap(find.byIcon(Icons.mode_comment_outlined));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byType(AppInlineLoading), findsOneWidget);
    expect(find.bySemanticsLabel(AppUiCopy.playerCommentsLoading), findsOneWidget);
    await _leavePlayer(tester, controller);
  });

  test('the overlay priority projection is error > cover > buffering', () {
    expect(
      playerOverlayMode(null, PlayerPhase.idle, false, false),
      PlayerOverlayMode.coverLoading,
    );
    expect(
      playerOverlayMode('https://cdn.test/a.m3u8', PlayerPhase.idle, false, false),
      PlayerOverlayMode.coverLoading,
    );
    expect(
      playerOverlayMode(
        'https://cdn.test/a.m3u8',
        PlayerPhase.buffering,
        true,
        false,
      ),
      PlayerOverlayMode.buffering,
    );
    // An error outranks a loading cover, which outranks the buffering pill.
    expect(
      playerOverlayMode('https://cdn.test/a.m3u8', PlayerPhase.buffering, false, true),
      PlayerOverlayMode.error,
    );
    expect(
      playerOverlayMode('https://cdn.test/a.m3u8', PlayerPhase.ready, true, true),
      PlayerOverlayMode.error,
    );
  });

  testWidgets('the error overlay offers one primary action and two seconds', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(tester);
    // The test platform cannot initialise a decoder, so the recovery overlay is
    // what the screen shows.
    expect(find.text(_copy.playerVideoError), findsOneWidget);

    final String retry = _copy.playerRetryAction;
    final String next = _copy.playerNextEpisodeAction;
    final String back = _copy.playerBackAction;
    AppButton buttonFor(String label) => tester.widget<AppButton>(
      find.ancestor(of: find.text(label), matching: find.byType(AppButton)),
    );
    expect(buttonFor(retry).variant, AppButtonVariant.primary);
    expect(buttonFor(next).variant, AppButtonVariant.secondary);
    expect(buttonFor(back).variant, AppButtonVariant.tertiary);
    expect(
      tester
          .widgetList<AppButton>(find.byType(AppButton))
          .where((AppButton button) => button.variant == AppButtonVariant.primary),
      hasLength(1),
      reason: 'one primary action per region',
    );

    // Retry re-attaches the player without leaving the screen.
    await tester.tap(find.text(retry));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.byType(PlayerScreen), findsOneWidget);

    // Back leaves the player; Next is offered while a successor exists.
    await tester.tap(find.text(back));
    await tester.pump(const Duration(milliseconds: 300));
    expect(controller.state.screen, isNot(AppScreen.player));
  });

  testWidgets('the last episode loses the next shortcut', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(
      tester,
      episodes: _episodes(2),
      startEpisode: 2,
    );
    expect(find.text(_copy.playerVideoError), findsOneWidget);
    expect(find.text(_copy.playerNextEpisodeAction), findsNothing);
    await _leavePlayer(tester, controller);
  });

  testWidgets('the chrome lays out at 320x640 with 3.2x text', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(
      tester,
      width: 320,
      height: 640,
      textScale: 3.2,
      disableAnimations: true,
    );

    expect(tester.takeException(), isNull);
    expect(find.byIcon(Icons.favorite_border), findsOneWidget);
    expect(find.textContaining(AppUiCopy.playerEpisodes), findsOneWidget);
    await _leavePlayer(tester, controller);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the chrome lays out on a 1080x1920 surface', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpPlayer(
      tester,
      width: 1080,
      height: 1920,
      disableAnimations: true,
    );

    expect(tester.takeException(), isNull);
    expect(find.text('Drama One'), findsWidgets);
    // The rail stays anchored to the right edge of a large surface.
    final Size rail = _targetAround(tester, find.byIcon(Icons.bookmark_border));
    expect(rail.width, greaterThanOrEqualTo(44));
    await _leavePlayer(tester, controller);
  });

  test('the player chrome paints only from tokens', () {
    final String source = File(
      'lib/ui/screens/player_screen.dart',
    ).readAsStringSync();
    final List<String> literals = RegExp(r'Colors\.[A-Za-z]+')
        .allMatches(source)
        .map((RegExpMatch match) => match.group(0)!)
        .where((String literal) => literal != 'Colors.transparent')
        .toList();
    expect(literals, isEmpty, reason: 'media chrome uses AppMedia/AppColors');
    expect(
      source.contains('Color(0x'),
      isFalse,
      reason: 'no raw colour literals outside the token files',
    );
  });
}
