/// Widget Previewer entry points for the Discover screen.
///
/// Run `flutter widget-preview start` from `android-flutter/` and open the
/// printed URL: every preview below renders in the browser and hot-reloads when
/// this file or any widget it builds changes.
///
/// The previewer runs on the web, so nothing here may touch `dart:io`, a
/// platform plugin or the network: the harness talks to an in-file
/// [AppDataSource] and every poster falls back to its placeholder plate.
library;

import 'package:flutter/material.dart';
import 'package:flutter/widget_previews.dart';
import 'package:provider/provider.dart';

import '../../data/app_data_source.dart';
import '../../data/models/app_language.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../theme/app_theme.dart';
import '../screens/search_screen.dart';

/// Shelf used by the previews that show the trending rail.
const List<BookSummary> _shelf = <BookSummary>[
  BookSummary(
    id: 'p1',
    title: 'The Billionaire’s Secret Bride',
    filteredTitle: 'billionaires-secret-bride',
    coverUrl: null,
    description: 'A contract marriage that was never supposed to become real.',
    chapterCount: 78,
  ),
  BookSummary(
    id: 'p2',
    title: 'Married to My Enemy CEO',
    filteredTitle: 'married-to-my-enemy-ceo',
    coverUrl: null,
    description: 'She married him to save her family. He married her for revenge.',
    chapterCount: 64,
  ),
  BookSummary(
    id: 'p3',
    title: 'Alpha’s Rejected Luna',
    filteredTitle: 'alphas-rejected-luna',
    coverUrl: null,
    description: 'Rejected at the altar, she came back with a different wolf.',
    chapterCount: 92,
  ),
  BookSummary(
    id: 'p4',
    title: 'Revenge Is a Contract',
    filteredTitle: 'revenge-is-a-contract',
    coverUrl: null,
    description: 'He signed the deal to destroy her, then fell first.',
    chapterCount: 55,
  ),
  BookSummary(
    id: 'p5',
    title: 'Pregnant After One Night',
    filteredTitle: 'pregnant-after-one-night',
    coverUrl: null,
    description: 'One night, one secret, and a father who wants it back.',
    chapterCount: 71,
  ),
  BookSummary(
    id: 'p6',
    title: 'The Mafia King’s Queen',
    filteredTitle: 'mafia-kings-queen',
    coverUrl: null,
    description: 'She walked into the club and never walked out.',
    chapterCount: 88,
  ),
];

/// Search results used by the results preview.
final List<BookSummary> _results = <BookSummary>[
  _shelf[0],
  _shelf[1],
  _shelf[3],
];

/// The smallest [AppDataSource] that can boot the Discover screen.
///
/// Everything the preview never triggers is left to `noSuchMethod`, so the
/// harness does not have to fake thirty methods to render one page.
class _PreviewDataSource implements AppDataSource {
  _PreviewDataSource({required this.shelf});

  final List<BookSummary> shelf;

  @override
  String get apiBaseUrl => 'https://preview.local/api/app';

  @override
  Future<List<BookSummary>> loadHomeShelf() async => shelf;

  @override
  Future<List<BookSummary>> loadCachedHomeShelf() async =>
      const <BookSummary>[];

  @override
  Future<void> saveCachedHomeShelf(List<BookSummary> books) async {}

  @override
  Future<List<BookSummary>> search(String query) async => _results;

  @override
  Future<AppLanguage> loadLanguagePreference() async => AppLanguage.english;

  @override
  Future<void> saveLanguagePreference(AppLanguage language) async {}

  @override
  Future<AuthSession?> restoreSession() async => null;

  @override
  Future<SavedCredentials?> loadSavedCredentials() async => null;

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('preview data source: ${invocation.memberName}');
}

/// Boots one Discover configuration and hands it to [SearchScreen].
class _DiscoverPreview extends StatefulWidget {
  const _DiscoverPreview({
    this.dark = false,
    this.showShelf = true,
    this.showHistory = false,
    this.showResults = false,
  });

  final bool dark;
  final bool showShelf;
  final bool showHistory;
  final bool showResults;

  @override
  State<_DiscoverPreview> createState() => _DiscoverPreviewState();
}

class _DiscoverPreviewState extends State<_DiscoverPreview> {
  late final AppStateController _controller = AppStateController(
    dataSource: _PreviewDataSource(
      shelf: widget.showShelf ? _shelf : const <BookSummary>[],
    ),
  );

  @override
  void initState() {
    super.initState();
    _seed();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _seed() async {
    await _controller.restoreSession();
    _controller.showSearch();
    if (widget.showHistory) {
      await _controller.search('love');
      _controller.clearSearch();
    }
    if (widget.showResults) {
      await _controller.search('love');
    }
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<AppStateController>.value(
      value: _controller,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: appTheme(Brightness.light),
        darkTheme: appTheme(Brightness.dark),
        themeMode: widget.dark ? ThemeMode.dark : ThemeMode.light,
        home: const Scaffold(body: SearchScreen()),
      ),
    );
  }
}

/// Discover with the trending rail and the recent queries.
@Preview(name: 'Lanes · trending + recent', group: 'Discover', size: Size(360, 800))
Widget discoverLanes() =>
    const _DiscoverPreview(showShelf: true, showHistory: true);

/// Discover with no shelf loaded: the start prompt sits above the lanes.
@Preview(name: 'Lanes · empty shelf', group: 'Discover', size: Size(360, 800))
Widget discoverEmptyShelf() => const _DiscoverPreview(showShelf: false);

/// Discover in the dark palette.
@Preview(name: 'Lanes · dark', group: 'Discover', size: Size(360, 800))
Widget discoverDark() => const _DiscoverPreview(
  dark: true,
  showShelf: true,
  showHistory: true,
);

/// The results state with the removable query pill.
@Preview(name: 'Results', group: 'Discover', size: Size(360, 800))
Widget discoverResults() =>
    const _DiscoverPreview(showResults: true, showShelf: true);
