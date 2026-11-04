/// Phase 4 + §13 regression suite for the redesigned Discover / search screen.
///
/// Locks the frozen contract of `android-flutter/docs/ui-redesign-notes.md`
/// §7.8 and §13: the page-title role and shared inset, the single pinned search
/// field with its inline action, the discovery lanes as their own heading level,
/// the trending rail and the recent queries above the lanes, the removable
/// query pill under the results header, the result rows as one semantic node,
/// the content-shaped loading skeleton, and the `AppUiCopy` empty states.
///
/// The behaviour guards stay in `ui_wiring_test.dart`; this suite pins the new
/// presentation contract and the submit semantics that must not regress.
library;

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:shortlink/core/formatting/app_strings.dart';
import 'package:shortlink/core/formatting/app_ui_copy.dart';
import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/data/models/app_language.dart';
import 'package:shortlink/data/network/api_client_exception.dart';
import 'package:shortlink/data/sources/search_history_store.dart';
import 'package:shortlink/state/app_state_controller.dart';
import 'package:shortlink/theme/app_colors.dart';
import 'package:shortlink/theme/app_shapes.dart';
import 'package:shortlink/theme/app_spacing.dart';
import 'package:shortlink/theme/app_theme.dart';
import 'package:shortlink/theme/app_typography.dart';
import 'package:shortlink/ui/components/app_loading.dart';
import 'package:shortlink/ui/components/app_section.dart';
import 'package:shortlink/ui/screens/search_screen.dart';

import 'support/fake_app_data_source.dart';

/// A data source whose search never answers, so the shared loading flag stays
/// up without leaving a pending timer.
class _StalledSearchDataSource extends FakeAppDataSource {
  final Completer<List<BookSummary>> _stalled = Completer<List<BookSummary>>();

  @override
  Future<List<BookSummary>> search(String query) => _stalled.future;
}

/// A data source whose search fails immediately, so the screen can be observed
/// after a settled-but-failed request.
class _FailingSearchDataSource extends FakeAppDataSource {
  _FailingSearchDataSource() {
    failWith('search', const ApiClientException(503, null, 'Service paused'));
  }
}

/// The English copy the screen renders in these tests.
final AppStrings _copy = strings(AppLanguage.defaultLanguage);

/// The discovery groups the screen renders in English.
final List<SearchDiscoveryGroup> _groups = searchDiscoveryGroups(
  AppLanguage.defaultLanguage,
);

/// A result fixture.
const BookSummary _result = BookSummary(
  id: 'book-9',
  title: 'The Alpha Billionaire',
  filteredTitle: 'alpha-billionaire',
  coverUrl: 'https://cdn.test/cover.jpg',
  description: 'A revenge story about a contract marriage.',
  chapterCount: 42,
);

/// Mounts the discover tab inside the app theme.
Future<AppStateController> _pumpSearch(
  WidgetTester tester, {
  FakeAppDataSource? dataSource,
  List<BookSummary> results = const <BookSummary>[],
  List<BookSummary> shelf = const <BookSummary>[],
  String? submitQuery,
  double width = 360,
  double height = 800,
  double textScale = 1,
  bool disableAnimations = false,
}) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = Size(width, height);
  addTearDown(tester.view.reset);

  final FakeAppDataSource source = dataSource ?? FakeAppDataSource();
  if (dataSource == null) {
    source.searchResults = results;
    source.homeShelf = shelf;
  }
  // Playback is protected: opening a result only reaches the player for a
  // member, so the row test signs in like the real flow does.
  source.session = const AuthSession(
    username: 'ashu180',
    token: 'token-1',
    tokenType: 'Bearer',
  );
  // A tapped result only opens the player when the book has episodes.
  if (source.episodes.isEmpty) {
    source.episodes = const <EpisodeSummary>[
      EpisodeSummary(number: 1, chapterId: 'ch-1', title: 'EP 1'),
    ];
  }
  final AppStateController controller = AppStateController(dataSource: source);
  addTearDown(controller.dispose);
  await controller.restoreSession();
  controller.showSearch();
  if (submitQuery != null) {
    await controller.search(submitQuery);
  }

  await _mount(
    tester,
    controller,
    width: width,
    height: height,
    textScale: textScale,
    disableAnimations: disableAnimations,
  );
  return controller;
}

/// Mounts [SearchScreen] for [controller] at one viewport configuration.
Future<void> _mount(
  WidgetTester tester,
  AppStateController controller, {
  double width = 360,
  double height = 800,
  double textScale = 1,
  bool disableAnimations = false,
}) async {
  await tester.pumpWidget(
    ChangeNotifierProvider<AppStateController>.value(
      value: controller,
      child: MaterialApp(
        theme: appTheme(Brightness.light),
        home: MediaQuery(
          data: MediaQueryData(
            size: Size(width, height),
            textScaler: TextScaler.linear(textScale),
            disableAnimations: disableAnimations,
          ),
          child: const Scaffold(body: SearchScreen()),
        ),
      ),
    ),
  );
  await tester.pump();
}

void main() {
  testWidgets('the discover page uses the title role and the shared inset', (
    WidgetTester tester,
  ) async {
    await _pumpSearch(tester);

    final Text title = tester.widget<Text>(find.text('Discover'));
    expect(title.style?.fontSize, AppTypography.light.screenTitle.fontSize);
    // The page scroll view is the one carrying the shared inset.
    final Padding page = tester.widget<Padding>(
      find
          .ancestor(
            of: find.byType(CustomScrollView),
            matching: find.byType(Padding),
          )
          .first,
    );
    expect(page.padding, AppSpacing.standard.pagePadding);

    // One field and one magnifier: the screen no longer spends a third of the
    // row on a separate filled button that repeats the field's own icon.
    expect(find.byType(TextField), findsOneWidget);
    expect(find.byIcon(Icons.search), findsOneWidget);
    expect(find.text(_copy.searchAction), findsNothing);
  });

  testWidgets('a blank submit never reaches the data source', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = await _pumpSearch(
      tester,
      dataSource: dataSource,
    );

    await tester.enterText(find.byType(TextField), '   ');
    await tester.pump();
    // The inline action is disabled while the trimmed value is empty, so the
    // tap cannot become a dead press.
    await tester.tap(find.byIcon(Icons.search));
    await tester.pump();

    expect(dataSource.callsTo('search'), 0);
    expect(controller.state.searchQuery, isEmpty);
  });

  testWidgets('submitting trims the query and normalises the field', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..searchResults = <BookSummary>[_result];
    final AppStateController controller = await _pumpSearch(
      tester,
      dataSource: dataSource,
    );

    await tester.enterText(find.byType(TextField), '  alpha  ');
    await tester.pump();
    await tester.tap(find.byIcon(Icons.search));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(controller.state.searchQuery, 'alpha');
    expect(dataSource.callsTo('search'), 1);
    // The field shows the query that was sent, not the typed padding.
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      'alpha',
    );
  });

  testWidgets('the keyboard search action submits the trimmed query', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..searchResults = <BookSummary>[_result];
    final AppStateController controller = await _pumpSearch(
      tester,
      dataSource: dataSource,
    );

    await tester.enterText(find.byType(TextField), ' beta ');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(controller.state.searchQuery, 'beta');
    expect(dataSource.callsTo('search'), 1);
  });

  testWidgets('a rebuild never wipes unsubmitted typing', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpSearch(tester);

    await tester.enterText(find.byType(TextField), 'half typed');
    await tester.pump();
    // Any state notification rebuilds the screen; the field keeps the draft.
    controller.showSearch();
    await tester.pump();

    expect(find.text('half typed'), findsOneWidget);
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      'half typed',
    );
  });

  testWidgets('the draft survives the screen being rebuilt from scratch', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpSearch(tester);

    await tester.enterText(find.byType(TextField), 'half typed');
    await tester.pump();

    // Switching tabs disposes the screen; the field text has to come back from
    // the controller's draft, not from widget state.
    await tester.pumpWidget(const SizedBox.shrink());
    await _mount(tester, controller);

    expect(find.text('half typed'), findsOneWidget);
  });

  testWidgets('a lane tap writes its tag into the field before the response', (
    WidgetTester tester,
  ) async {
    // The stalled source keeps the request in flight, which is exactly the
    // window in which the field used to stay empty.
    final _StalledSearchDataSource dataSource = _StalledSearchDataSource();
    final AppStateController controller = await _pumpSearch(
      tester,
      dataSource: dataSource,
    );

    final String tag = _groups.first.tags.first;
    await tester.tap(find.text(tag));
    await tester.pump();

    expect(controller.state.isLoading, isTrue);
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      tag,
    );
    // The results header already names the query being answered.
    expect(find.text(tag), findsWidgets);
  });

  testWidgets('the lanes carry their own heading and no section header', (
    WidgetTester tester,
  ) async {
    await _pumpSearch(tester);

    // The "Popular lanes" section head is gone: it named the only thing on the
    // page and left the lane labels one point of type below it.
    final List<AppSectionHeader> headers = tester
        .widgetList<AppSectionHeader>(find.byType(AppSectionHeader))
        .toList();
    expect(
      headers.where(
        (AppSectionHeader header) => header.title == _copy.searchTagsTitle,
      ),
      isEmpty,
    );

    // Each lane renders once, in sentence case (no upper-casing).
    for (final SearchDiscoveryGroup group in _groups) {
      expect(
        find.text(group.title),
        findsOneWidget,
        reason: 'the lane label keeps its sentence case',
      );
      expect(find.text(group.title.toUpperCase()), findsNothing);
    }
  });

  testWidgets('a lane label is the 14 dp heading, not a caption', (
    WidgetTester tester,
  ) async {
    await _pumpSearch(tester);

    final Text text = tester.widget<Text>(find.text(_groups.first.title));
    final TextTheme theme = appTheme(Brightness.light).textTheme;
    expect(text.style?.fontSize, theme.titleMedium?.fontSize);
    expect(text.style?.fontWeight, FontWeight.w700);
    expect(text.style?.color, AppColors.light.textPrimary);
  });

  testWidgets('every discovery chip is a 44 px target with a real boundary', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..searchResults = <BookSummary>[_result];
    final AppStateController controller = await _pumpSearch(
      tester,
      dataSource: dataSource,
    );

    final String tag = _groups.first.tags.first;
    final Finder chip = find.text(tag);
    expect(chip, findsOneWidget);
    final Size target = tester.getSize(
      find.ancestor(of: chip, matching: find.byType(InkWell)).first,
    );
    expect(target.height, greaterThanOrEqualTo(44));

    // The boundary is the control neutral, not the decorative hairline: a
    // 1.07:1 fill and a 1.15:1 edge are not perceivable as a control.
    final Material material = tester.widget<Material>(
      find.ancestor(of: chip, matching: find.byType(Material)).first,
    );
    final RoundedRectangleBorder shape =
        material.shape! as RoundedRectangleBorder;
    expect(shape.side.color, AppColors.light.outlineStrong);
    expect(
      shape.borderRadius,
      BorderRadius.circular(AppShapes.light.radiusControl),
    );

    await tester.tap(chip);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(controller.state.searchQuery, tag);
    expect(dataSource.callsTo('search'), 1);
  });

  testWidgets('the search field stays pinned while the page scrolls', (
    WidgetTester tester,
  ) async {
    // A short viewport guarantees the page scrolls past the title.
    await _pumpSearch(tester, height: 400);

    final double before = tester.getRect(find.byType(TextField)).top;
    await tester.drag(find.byType(CustomScrollView), const Offset(0, -200));
    await tester.pump();
    final double pinned = tester.getRect(find.byType(TextField)).top;
    // The title scrolled away; the field did not.
    expect(pinned, lessThan(before));

    await tester.drag(find.byType(CustomScrollView), const Offset(0, -150));
    await tester.pump();
    expect(tester.getRect(find.byType(TextField)).top, closeTo(pinned, 0.5));
  });

  testWidgets('the trending rail shows the shelf and opens a book', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..homeShelf = <BookSummary>[FakeAppDataSource.book0];
    final AppStateController controller = await _pumpSearch(
      tester,
      dataSource: dataSource,
    );

    expect(
      find.text(AppUiCopy.searchTrendingSection.toUpperCase()),
      findsOneWidget,
    );
    expect(find.text(FakeAppDataSource.book0.title), findsOneWidget);

    await tester.tap(find.text(FakeAppDataSource.book0.title));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(controller.state.playback.book?.id, FakeAppDataSource.book0.id);
  });

  testWidgets('a search is remembered and the recent list can be cleared', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..searchResults = <BookSummary>[_result];
    final AppStateController controller = await _pumpSearch(
      tester,
      dataSource: dataSource,
    );

    await tester.enterText(find.byType(TextField), 'alpha');
    await tester.pump();
    await tester.tap(find.byIcon(Icons.search));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(controller.searchHistory, <String>['alpha']);

    // Back to the lanes through the removable pill.
    await tester.tap(find.byKey(activeQueryPillKey));
    await tester.pump();
    expect(
      find.text(AppUiCopy.searchRecentSection.toUpperCase()),
      findsOneWidget,
    );
    expect(find.text('alpha'), findsOneWidget);

    await tester.tap(find.text(AppUiCopy.searchRecentClear));
    await tester.pump();
    expect(controller.searchHistory, isEmpty);
    expect(find.text('alpha'), findsNothing);
  });

  test('the recent list dedupes case-insensitively and caps its length', () {
    expect(mergedSearchHistory(<String>[], 'Love'), <String>['Love']);
    expect(mergedSearchHistory(<String>['Love', 'CEO'], 'love'), <String>[
      'love',
      'CEO',
    ]);
    expect(mergedSearchHistory(<String>['Love'], '  '), <String>['Love']);
    final List<String> capped = mergedSearchHistory(
      List<String>.generate(10, (int index) => 'q$index'),
      'new',
    );
    expect(capped, hasLength(searchHistoryLimit));
    expect(capped.first, 'new');
  });

  testWidgets('the results header carries the query and the count pill', (
    WidgetTester tester,
  ) async {
    await _pumpSearch(
      tester,
      results: <BookSummary>[_result],
      submitQuery: 'alpha',
    );

    expect(
      tester
          .widgetList<AppSectionHeader>(find.byType(AppSectionHeader))
          .map((AppSectionHeader header) => header.title),
      contains(AppUiCopy.searchResultsSection),
    );
    expect(find.byKey(activeQueryPillKey), findsOneWidget);
    expect(find.text('alpha'), findsWidgets);
    expect(find.text('1 ${_copy.searchResultCountSuffix}'), findsOneWidget);
  });

  testWidgets('clearing the query pill returns to the lanes', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpSearch(
      tester,
      results: <BookSummary>[_result],
      submitQuery: 'alpha',
    );

    await tester.tap(find.byKey(activeQueryPillKey));
    await tester.pump();

    expect(controller.state.searchQuery, isEmpty);
    expect(controller.state.searchResults, isEmpty);
    expect(find.byKey(activeQueryPillKey), findsNothing);
    expect(find.text(_groups.first.title), findsOneWidget);
  });

  testWidgets('a result row is one semantic node that opens the book', (
    WidgetTester tester,
  ) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    final AppStateController controller = await _pumpSearch(
      tester,
      results: <BookSummary>[_result],
      submitQuery: 'alpha',
    );

    // Title, episode count and synopsis are visible content.
    expect(find.text(_result.title), findsWidgets);
    expect(find.text('42 eps'), findsOneWidget);
    expect(find.text(_result.description), findsOneWidget);

    // They announce once, through the row's merged node, which owns the tap.
    final SemanticsData row = tester.semantics
        .find(find.bySemanticsLabel('${_result.title}, 42 eps'))
        .getSemanticsData();
    expect(row.hasAction(SemanticsAction.tap), isTrue);

    await tester.tap(find.text(_result.title).first);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(controller.state.playback.book?.id, _result.id);
    handle.dispose();
  });

  testWidgets('an in-flight search renders a result-shaped skeleton', (
    WidgetTester tester,
  ) async {
    // A stalled search keeps the shared loading flag up with no pending timer.
    final _StalledSearchDataSource dataSource = _StalledSearchDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    addTearDown(controller.dispose);
    controller.showSearch();
    await _mount(tester, controller);

    // Submit through the screen: the pending query is screen state, so a
    // direct `controller.search()` would test a path the UI never takes.
    await tester.enterText(find.byType(TextField), 'alpha');
    await tester.pump();
    await tester.tap(find.byIcon(Icons.search));
    await tester.pump();

    expect(controller.state.isLoading, isTrue);
    expect(find.byType(AppSkeletonList), findsNothing);
    expect(find.byType(AppSkeleton), findsWidgets);
    expect(find.byType(AppSkeletonText), findsWidgets);
    // The first search moves straight to the results area: the discovery
    // content and the start prompt are gone while the request is in flight, so
    // nothing is painted twice when the rows land.
    expect(find.text(AppUiCopy.searchStartTitle), findsNothing);
    expect(find.text(_groups.first.title), findsNothing);
    expect(
      tester
          .widgetList<AppSectionHeader>(find.byType(AppSectionHeader))
          .map((AppSectionHeader header) => header.title),
      contains(AppUiCopy.searchResultsSection),
    );
    expect(find.text('alpha'), findsWidgets);
    // The skeleton is full width and never collapses to zero.
    final Finder blocks = find.byType(AppSkeleton);
    for (int index = 0; index < blocks.evaluate().length; index++) {
      final Size size = tester.getSize(blocks.at(index));
      expect(size.width, greaterThan(0));
      expect(size.height, greaterThan(0));
    }
    // The empty state is not claimed while the request is in flight.
    expect(find.text(AppUiCopy.searchNoResultsTitle), findsNothing);
  });

  testWidgets('a failed search returns to the discovery lanes', (
    WidgetTester tester,
  ) async {
    // The state layer never echoes a query it did not answer, so a failed
    // request leaves `searchQuery` empty. The screen drops its pending query
    // with the request: claiming "no results" would say the search ran and
    // found nothing, when the shell's message bar is saying it failed.
    final _FailingSearchDataSource dataSource = _FailingSearchDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    addTearDown(controller.dispose);
    controller.showSearch();
    await _mount(tester, controller);

    await tester.enterText(find.byType(TextField), 'gamma');
    await tester.tap(find.byIcon(Icons.search));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(controller.state.isLoading, isFalse);
    expect(controller.state.searchResults, isEmpty);
    expect(
      tester
          .widgetList<AppSectionHeader>(find.byType(AppSectionHeader))
          .map((AppSectionHeader header) => header.title),
      isNot(contains(AppUiCopy.searchResultsSection)),
    );
    expect(find.text(_groups.first.title), findsOneWidget);
    expect(find.text(AppUiCopy.searchNoResultsTitle), findsNothing);
    expect(find.text(AppUiCopy.searchStartTitle), findsOneWidget);
  });

  testWidgets('the no-results state uses the shared copy and a status pill', (
    WidgetTester tester,
  ) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    await _pumpSearch(
      tester,
      results: const <BookSummary>[],
      submitQuery: 'zzz',
    );

    expect(find.text(AppUiCopy.searchNoResultsTitle), findsOneWidget);
    expect(find.text(AppUiCopy.searchNoResultsBody), findsOneWidget);
    expect(find.text(AppUiCopy.searchAgain), findsOneWidget);
    // The retry affordance is a status pill, not a control.
    expect(
      tester.semantics
          .find(find.bySemanticsLabel(AppUiCopy.searchAgain))
          .getSemanticsData()
          .hasAction(SemanticsAction.tap),
      isFalse,
    );
    handle.dispose();
  });

  testWidgets(
    'the start state prompts from AppUiCopy when the shelf is empty',
    (WidgetTester tester) async {
      await _pumpSearch(tester);

      expect(find.text(AppUiCopy.searchStartTitle), findsOneWidget);
      expect(find.text(AppUiCopy.searchStartBody), findsOneWidget);
      expect(find.text(AppUiCopy.searchNoResultsTitle), findsNothing);
    },
  );

  testWidgets('the discover page survives 320x640 at 3.2x text', (
    WidgetTester tester,
  ) async {
    // The historical overflow point: the old "dot + bare Row text" group
    // headers produced four RenderFlex overflows here.
    await _pumpSearch(
      tester,
      width: 320,
      height: 640,
      textScale: 3.2,
      disableAnimations: true,
    );

    expect(tester.takeException(), isNull);
    // Walk every lane: each label and its first chip must render without an
    // exception, and nothing may overflow on the way (the historical defect).
    for (final SearchDiscoveryGroup group in _groups) {
      await tester.scrollUntilVisible(
        find.text(group.tags.first),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text(group.tags.first), findsOneWidget);
      expect(tester.takeException(), isNull, reason: group.title);
    }

    // Scroll through the whole lane stack: no exception may surface on the way.
    await tester.drag(find.byType(CustomScrollView), const Offset(0, -400));
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  testWidgets('the results view lays out at 320x640 with 3.2x text', (
    WidgetTester tester,
  ) async {
    await _pumpSearch(
      tester,
      results: <BookSummary>[_result],
      submitQuery: 'alpha',
      width: 320,
      height: 640,
      textScale: 3.2,
      disableAnimations: true,
    );

    expect(tester.takeException(), isNull);
    // The row is below the fold at this scale: scroll to it before asserting.
    // The episode count is the row's unique text (the poster repeats the title).
    await tester.scrollUntilVisible(
      find.text('42 eps'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text(_result.title), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the discover page lays out at 360x640 under reduced motion', (
    WidgetTester tester,
  ) async {
    await _pumpSearch(
      tester,
      results: <BookSummary>[_result],
      submitQuery: 'alpha',
      width: 360,
      height: 640,
      disableAnimations: true,
    );

    expect(tester.takeException(), isNull);
    expect(find.text('1 ${_copy.searchResultCountSuffix}'), findsOneWidget);
  });

  testWidgets('the discover page lays out on a 1440px surface', (
    WidgetTester tester,
  ) async {
    await _pumpSearch(tester, width: 1440, height: 900);

    expect(tester.takeException(), isNull);
    expect(find.text('Discover'), findsOneWidget);
    expect(find.text(_groups.first.title), findsOneWidget);
  });

  test('the search screen paints only from tokens', () {
    final String source = File('lib/ui/screens/search_screen.dart')
        .readAsStringSync();
    expect(RegExp(r'Colors\.[A-Za-z]+').hasMatch(source), isFalse);
    expect(source.contains('Color(0x'), isFalse);
    // No inline magic spacing in the layout: every gap and inset is a token.
    expect(
      RegExp(r'EdgeInsets\.[A-Za-z]+\([^)\n]*[(:,]\s*\d').hasMatch(source),
      isFalse,
    );
    expect(RegExp(r'SizedBox\((height|width): \d').hasMatch(source), isFalse);
    expect(RegExp(r'SizedBox\(width: \d').hasMatch(source), isFalse);
  });
}
