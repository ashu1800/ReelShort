/// Search: the discovery screen.
///
/// Behavioural parity with the Kotlin `SearchScreen`: `trim()` on submit, blank
/// queries ignored without a side effect, the shared results-first decision,
/// discovery chips that submit immediately, wide-thumb result rows, the results
/// header with the query and the result count, and the empty-state copy with its
/// non-interactive "Search again" pill.
///
/// Structure contract (`docs/ui-redesign-notes.md` §7.8 and §13): one
/// `screenTitle`, the shared page inset, a pinned search field, a poster rail
/// and the recent queries above the lane chips, tokenized 44 px chips on the
/// 10 dp control radius, and result rows that announce as one node.
///
/// The screen owns the field's [TextEditingController] (not the field widget):
/// tapping a lane has to write the query into the field before the request
/// lands, and the draft survives a tab switch through the controller.
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/app_ui_copy.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';
import '../components/app_empty_state.dart';
import '../components/app_loading.dart';
import '../components/app_section.dart';
import '../components/app_text_field.dart';
import '../components/poster_card.dart';

/// Key of the removable query pill under the results header.
///
/// Public so a widget test can tap the pill without guessing between it and the
/// identical text sitting in the search field.
const Key activeQueryPillKey = Key('search-active-query');

/// Width of the poster column in a result row.
const double _resultPosterWidth = 88;

/// Height of the poster column in a result row (2:3 art).
const double _resultPosterHeight = 132;

/// Minimum height of a discovery chip, so its target clears the touch minimum.
const double _chipMinHeight = 44;

/// Chip label size: one step below the lane label, so the lane name leads.
const double _chipFontSize = 14;

/// Size of the search field's inline actions.
const double _searchActionExtent = 44;

/// Glyph size inside a search field action.
const double _searchActionIconSize = 20;

/// Glyph size of the removable query pill's close mark.
const double _queryPillIconSize = 16;

/// Width of one poster in the trending rail.
const double _trendingCardWidth = 112;

/// How many posters the trending rail shows.
const int _trendingCardLimit = 8;

/// Number of result skeletons while a query is in flight.
const int _skeletonRowCount = 3;

/// The field's own vertical content padding, from the input decoration theme
/// (14 dp above and below). Used to size the pinned header.
const double _fieldContentPadding = 28;

/// One body line of the field at 1x, before text scaling.
const double _fieldLineHeight = 24;

/// Rounding guard for the pinned header: the measured field is a fraction
/// taller than the token math at large scales (105.0 vs 104.8 at 3.2x), so the
/// header keeps a hair of slack instead of clipping the field's border.
const double _fieldExtentSlack = 1;

/// Height the search field needs at the current text scale.
///
/// Calibrated against the real [AppTextField] (52 dp at 1x, 105 dp at 3.2x):
/// content padding plus one scaled body line. The scaled line comes from
/// [TextScaler] rather than `scale(1) * 24`, because Android's nonlinear text
/// scaling does not scale a 1x factor linearly.
double _searchFieldExtent(BuildContext context) =>
    _fieldContentPadding +
    MediaQuery.textScalerOf(context).scale(_fieldLineHeight) +
    _fieldExtentSlack;

/// The search / discovery screen.
class SearchScreen extends StatefulWidget {
  /// Creates the search screen.
  const SearchScreen({super.key});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  /// The field text, owned here so a lane tap can write into it and so the
  /// draft survives the tab switch that destroys this state.
  late final TextEditingController _queryController;

  /// The query this screen submitted before the state layer echoes it back.
  ///
  /// The controller writes `searchQuery` only when the response lands, so
  /// without this the first search would paint the discovery content and the
  /// start prompt, then jump to the results area when the request returned.
  String? _submittedQuery;

  /// Whether the outstanding request was started on this screen.
  ///
  /// `isLoading` is a global flag; a login or account refresh elsewhere must not
  /// paint a search skeleton here.
  bool _awaitingResults = false;

  @override
  void initState() {
    super.initState();
    _queryController = TextEditingController(
      text: context.read<AppStateController>().searchDraft,
    );
    _queryController.addListener(_rememberDraft);
  }

  @override
  void dispose() {
    _queryController.removeListener(_rememberDraft);
    _queryController.dispose();
    super.dispose();
  }

  /// Keeps the controller's draft in sync on every keystroke.
  ///
  /// Deliberately does not notify: the field repaints itself, and a state
  /// notification per character would rebuild the whole shell.
  void _rememberDraft() {
    context.read<AppStateController>().rememberSearchDraft(
      _queryController.text,
    );
  }

  void _submit(String raw) {
    // Blank queries are ignored with no side effect, exactly like the Kotlin screen.
    final String query = raw.trim();
    if (query.isEmpty) {
      return;
    }
    // A lane tap submits its own tag: show it in the field before the request
    // lands, otherwise the field stays empty until the response echoes back.
    if (_queryController.text != query) {
      _queryController.value = TextEditingValue(
        text: query,
        selection: TextSelection.collapsed(offset: query.length),
      );
    }
    FocusScope.of(context).unfocus();
    final AppStateController controller = context.read<AppStateController>();
    setState(() {
      _submittedQuery = query;
      _awaitingResults = true;
    });
    controller.search(query);
  }

  /// Drops the answered query and returns to the lanes.
  void _clearSearch() {
    _queryController.clear();
    context.read<AppStateController>().clearSearch();
    setState(() {
      _submittedQuery = null;
      _awaitingResults = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    // The request settled (success or failure), so a global loading flag no
    // longer means this screen's search is in flight. This is bookkeeping for
    // the decisions below, not widget state - it never schedules a rebuild.
    if (_awaitingResults && !state.isLoading) {
      _awaitingResults = false;
      // A failure leaves `searchQuery` untouched, so the pending query goes
      // with it: claiming "no results" would contradict the failure message.
      if (state.searchQuery.trim().isEmpty) {
        _submittedQuery = null;
      }
    }
    final bool awaitingResults = _awaitingResults && state.isLoading;
    final AppStrings copy = strings(state.language);
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    // Only the first search needs the pending query: the results area does not
    // exist yet and the discovery content would otherwise paint and then vanish.
    // With rows already on screen the header keeps the query those rows answer,
    // so the header and the list never disagree while the next one is in flight.
    final String displayQuery =
        awaitingResults && state.searchQuery.trim().isEmpty
        ? (_submittedQuery ?? '')
        : state.searchQuery;
    final bool resultsFirst = searchShowsResultsFirst(
      displayQuery,
      state.searchResults.length,
    );
    final String query = displayQuery.trim();
    final List<BookSummary> trending = state.homeShelf
        .take(_trendingCardLimit)
        .toList(growable: false);

    return SafeArea(
      bottom: false,
      child: Padding(
        // The single page inset of the system (18/12/18/28).
        padding: spacing.pagePadding,
        child: CustomScrollView(
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          slivers: <Widget>[
            // One page-title role for the whole app; the title scrolls away and
            // the field below it pins to the top instead.
            SliverToBoxAdapter(
              child: Text(copy.searchTab, style: roles.screenTitle),
            ),
            SliverToBoxAdapter(child: SizedBox(height: spacing.space4)),
            SliverPersistentHeader(
              pinned: true,
              delegate: _PinnedSearchHeader(
                extent: spacing.space2 * 2 + _searchFieldExtent(context),
                child: _SearchField(
                  controller: _queryController,
                  copy: copy,
                  onSubmit: _submit,
                  onClear: _queryController.clear,
                ),
              ),
            ),
            if (resultsFirst) ...<Widget>[
              SliverToBoxAdapter(child: SizedBox(height: spacing.space4)),
              // The header itself bounds its trailing slot now (see
              // `AppSectionHeader`), so the count pill no longer needs a
              // caller-side width cap.
              SliverToBoxAdapter(
                child: AppSectionHeader(
                  title: AppUiCopy.searchResultsSection,
                  trailing: _CountPill(
                    count: state.searchResults.length,
                    suffix: copy.searchResultCountSuffix,
                  ),
                ),
              ),
              if (query.isNotEmpty)
                SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.only(top: spacing.space2),
                    child: Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: _ActiveQueryPill(
                        query: query,
                        onClear: _clearSearch,
                      ),
                    ),
                  ),
                ),
              SliverToBoxAdapter(child: SizedBox(height: spacing.space3)),
              _ResultsSection(
                results: state.searchResults,
                awaitingResults: awaitingResults,
              ),
            ] else ...<Widget>[
              SliverToBoxAdapter(child: SizedBox(height: spacing.space4)),
              if (controller.searchHistory.isNotEmpty) ...<Widget>[
                SliverToBoxAdapter(
                  child: AppSectionHeader(
                    title: AppUiCopy.searchRecentSection,
                    actionLabel: AppUiCopy.searchRecentClear,
                    onAction: controller.clearSearchHistory,
                  ),
                ),
                SliverToBoxAdapter(child: SizedBox(height: spacing.space2)),
                SliverToBoxAdapter(
                  child: _QueryChips(
                    queries: controller.searchHistory,
                    onSelected: _submit,
                  ),
                ),
                SliverToBoxAdapter(child: SizedBox(height: spacing.space6)),
              ],
              if (trending.isNotEmpty) ...<Widget>[
                SliverToBoxAdapter(
                  child: AppSectionHeader(
                    title: AppUiCopy.searchTrendingSection,
                  ),
                ),
                SliverToBoxAdapter(child: SizedBox(height: spacing.space3)),
                SliverToBoxAdapter(
                  child: _TrendingRail(
                    books: trending,
                    onOpen: controller.openBook,
                  ),
                ),
                SliverToBoxAdapter(child: SizedBox(height: spacing.space6)),
              ] else
                // No shelf to browse yet: the start prompt is the only thing
                // that can help, and it now sits above the lanes instead of
                // below the fold.
                SliverToBoxAdapter(
                  child: AppEmptyState(
                    icon: Icons.travel_explore_outlined,
                    title: AppUiCopy.searchStartTitle,
                    body: AppUiCopy.searchStartBody,
                  ),
                ),
              SliverToBoxAdapter(
                child: _DiscoveryGroups(onTagSelected: _submit),
              ),
            ],
            SliverToBoxAdapter(child: SizedBox(height: spacing.space6)),
          ],
        ),
      ),
    );
  }
}

/// The search field plus its one action, pinned to the top of the viewport.
class _PinnedSearchHeader extends SliverPersistentHeaderDelegate {
  const _PinnedSearchHeader({required this.extent, required this.child});

  /// Height of the pinned slot, including the field's vertical padding.
  final double extent;

  /// The field.
  final Widget child;

  @override
  double get minExtent => extent;

  @override
  double get maxExtent => extent;

  @override
  Widget build(
    BuildContext context,
    double shrinkOffset,
    bool overlapsContent,
  ) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    return SizedBox(
      height: extent,
      child: DecoratedBox(
        // The canvas plane keeps the scrolled content from showing through,
        // and the hairline is what tells the user the field is now floating.
        decoration: BoxDecoration(
          color: colors.canvas,
          border: Border(
            bottom: BorderSide(
              color: colors.hairline,
              width: shapes.strokeHairline,
            ),
          ),
        ),
        child: Padding(
          padding: EdgeInsets.only(top: spacing.space2, bottom: spacing.space2),
          child: child,
        ),
      ),
    );
  }

  @override
  bool shouldRebuild(covariant _PinnedSearchHeader oldDelegate) =>
      oldDelegate.extent != extent || oldDelegate.child != child;
}

/// The query field plus its inline clear and submit actions.
///
/// One field, one magnifier: the submit action is the only search glyph on the
/// screen, and it is disabled while the field is blank, so the control never
/// invites a tap that does nothing.
class _SearchField extends StatelessWidget {
  const _SearchField({
    required this.controller,
    required this.copy,
    required this.onSubmit,
    required this.onClear,
  });

  final TextEditingController controller;
  final AppStrings copy;

  /// Submits a trimmed, non-blank query. The screen owns the call so it can
  /// remember the query while the request is in flight.
  final ValueChanged<String> onSubmit;

  /// Clears the field text only; the answered query stays on screen.
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: controller,
      builder: (BuildContext context, TextEditingValue value, Widget? _) {
        final bool hasInput = value.text.trim().isNotEmpty;
        return AppTextField(
          controller: controller,
          label: copy.searchHint,
          textInputAction: TextInputAction.search,
          suffix: Row(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              if (value.text.isNotEmpty)
                _FieldAction(
                  icon: Icons.close,
                  label: AppUiCopy.clearSearch,
                  color: colors.textSecondary,
                  onTap: onClear,
                ),
              _FieldAction(
                icon: Icons.search,
                label: copy.searchAction,
                color: hasInput ? colors.accentPrimary : colors.onDisabled,
                onTap: hasInput ? () => onSubmit(controller.text) : null,
              ),
            ],
          ),
          onSubmitted: onSubmit,
        );
      },
    );
  }
}

/// One 44 px icon action inside the field's suffix slot.
class _FieldAction extends StatelessWidget {
  const _FieldAction({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  final IconData icon;

  /// Accessible name; the glyph alone carries no text.
  final String label;

  final Color color;

  /// Null disables the action: no tap target, no ripple, no dead press.
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: label,
      onTap: onTap,
      excludeSemantics: true,
      child: SizedBox(
        width: _searchActionExtent,
        height: _searchActionExtent,
        child: Material(
          type: MaterialType.transparency,
          child: InkWell(
            onTap: onTap,
            customBorder: const CircleBorder(),
            child: Center(
              child: Icon(icon, size: _searchActionIconSize, color: color),
            ),
          ),
        ),
      ),
    );
  }
}

/// The removable query pill under the results header.
///
/// It is the visible record of what is on screen and the way back to the lanes:
/// the field can be edited, but only this pill says "this list answers this
/// query" and can undo it in one tap.
class _ActiveQueryPill extends StatelessWidget {
  const _ActiveQueryPill({required this.query, required this.onClear});

  final String query;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return Semantics(
      button: true,
      label: AppUiCopy.clearResults,
      onTap: onClear,
      excludeSemantics: true,
      child: Material(
        key: activeQueryPillKey,
        color: colors.premiumSurface,
        shape: RoundedRectangleBorder(
          borderRadius: shapes.controlRadius,
          side: BorderSide(
            color: colors.accentPrimary,
            width: shapes.strokeHairline,
          ),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onClear,
          child: Padding(
            padding: EdgeInsets.symmetric(
              horizontal: spacing.space3,
              vertical: spacing.space1,
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Flexible(
                  child: Text(
                    query,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: roles.caption.copyWith(
                      color: colors.accentPrimary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                SizedBox(width: spacing.space1),
                Icon(
                  Icons.close,
                  size: _queryPillIconSize,
                  color: colors.accentPrimary,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// The recent queries, as chips that submit on tap.
class _QueryChips extends StatelessWidget {
  const _QueryChips({required this.queries, required this.onSelected});

  final List<String> queries;
  final ValueChanged<String> onSelected;

  @override
  Widget build(BuildContext context) {
    final AppSpacing spacing = appSpacingOf(context);
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) => Wrap(
        spacing: spacing.space2,
        runSpacing: spacing.space2,
        children: <Widget>[
          for (final String query in queries)
            _DiscoveryChip(
              tag: query,
              maxWidth: constraints.maxWidth,
              onTap: () => onSelected(query),
            ),
        ],
      ),
    );
  }
}

/// The horizontal poster rail that gives the page something to browse.
class _TrendingRail extends StatelessWidget {
  const _TrendingRail({required this.books, required this.onOpen});

  final List<BookSummary> books;
  final ValueChanged<BookSummary> onOpen;

  @override
  Widget build(BuildContext context) {
    final AppSpacing spacing = appSpacingOf(context);
    return SizedBox(
      // The poster's own aspect ratio decides the rail height, so the cards can
      // never be clipped or letterboxed.
      height: _trendingCardWidth / posterAspectRatio,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: books.length,
        separatorBuilder: (BuildContext context, int index) =>
            SizedBox(width: spacing.space3),
        itemBuilder: (BuildContext context, int index) {
          final BookSummary book = books[index];
          return SizedBox(
            width: _trendingCardWidth,
            child: PosterCard(
              title: book.title,
              coverUrl: book.coverUrl,
              episodeCount: book.chapterCount,
              onTap: () => onOpen(book),
            ),
          );
        },
      ),
    );
  }
}

/// The results count, as a compact status pill in the section header.
///
/// Its width is bounded by `AppSectionHeader`'s trailing slot, so the label
/// ellipsises there instead of needing a width cap of its own.
class _CountPill extends StatelessWidget {
  const _CountPill({required this.count, required this.suffix});

  final int count;
  final String suffix;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: spacing.space3,
        vertical: spacing.space1,
      ),
      decoration: BoxDecoration(
        color: colors.surfaceAlt,
        borderRadius: BorderRadius.circular(shapes.radiusPill),
        border: Border.all(
          color: colors.hairline,
          width: shapes.strokeHairline,
        ),
      ),
      child: Text(
        '$count $suffix',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: roles.caption.copyWith(
          color: colors.textSecondary,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

/// The discovery groups: a lane label, then its tokenized chips.
///
/// The lane label carries the second level on its own now: the page used to put
/// a "Popular lanes" section header above four labels that differed from it by
/// one point of type, which read as five peers rather than as a hierarchy.
class _DiscoveryGroups extends StatelessWidget {
  const _DiscoveryGroups({required this.onTagSelected});

  /// Submits the tapped tag through the screen, which records the query before
  /// the response lands.
  final ValueChanged<String> onTagSelected;

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppSpacing spacing = appSpacingOf(context);
    final List<SearchDiscoveryGroup> groups = searchDiscoveryGroups(
      state.language,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        for (int index = 0; index < groups.length; index++) ...<Widget>[
          // Between groups, not after every group: the gap that separates the
          // lanes must be visibly larger than the gap inside one.
          if (index > 0) SizedBox(height: spacing.space6),
          _GroupLabel(title: groups[index].title),
          SizedBox(height: spacing.space2),
          LayoutBuilder(
            builder: (BuildContext context, BoxConstraints constraints) {
              return Wrap(
                spacing: spacing.space2,
                runSpacing: spacing.space2,
                children: <Widget>[
                  for (final String tag in groups[index].tags)
                    _DiscoveryChip(
                      tag: tag,
                      // A chip never exceeds the lane width, so a long tag at a
                      // large scale ellipsises instead of overflowing the run.
                      maxWidth: constraints.maxWidth,
                      onTap: () => onTagSelected(tag),
                    ),
                ],
              );
            },
          ),
        ],
      ],
    );
  }
}

/// A lane label: the level between the page title and the chips.
///
/// It uses the 14 dp title role in the primary text colour, which is what makes
/// it read as a heading rather than as a caption that happens to sit above the
/// chips. The row is flexed and ellipsised, so a long lane name at a large text
/// scale can never restore the old bare-`Row` overflow.
class _GroupLabel extends StatelessWidget {
  const _GroupLabel({required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    return Row(
      children: <Widget>[
        Flexible(
          child: Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleMedium
                ?.copyWith(color: colors.textPrimary),
          ),
        ),
      ],
    );
  }
}

/// A tokenized discovery chip: 44 px tall, control radius, real boundary.
///
/// The border is the control neutral (`outlineStrong`), not the decorative
/// hairline: at 1.07:1 against the canvas a hairline edge is not perceivable as
/// a control boundary (see the `hairline` contract in `app_colors.dart`), and
/// the chips read as colour blobs instead of buttons.
class _DiscoveryChip extends StatelessWidget {
  const _DiscoveryChip({
    required this.tag,
    required this.maxWidth,
    required this.onTap,
  });

  final String tag;
  final double maxWidth;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return Semantics(
      button: true,
      label: tag,
      onTap: onTap,
      excludeSemantics: true,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: maxWidth,
          minHeight: _chipMinHeight,
        ),
        child: Material(
          color: colors.surfaceAlt,
          shape: RoundedRectangleBorder(
            borderRadius: shapes.controlRadius,
            side: BorderSide(
              color: colors.outlineStrong,
              width: shapes.strokeHairline,
            ),
          ),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onTap,
            child: Padding(
              padding: EdgeInsets.symmetric(
                horizontal: spacing.space3,
                vertical: spacing.space2,
              ),
              child: Center(
                widthFactor: 1,
                child: Text(
                  tag,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: roles.body.copyWith(
                    fontSize: _chipFontSize,
                    color: colors.textPrimary,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// The results list, its content-shaped loading skeleton, or the empty state.
class _ResultsSection extends StatelessWidget {
  const _ResultsSection({required this.results, required this.awaitingResults});

  final List<BookSummary> results;

  /// Set only while this screen's own search is in flight.
  final bool awaitingResults;

  @override
  Widget build(BuildContext context) {
    if (results.isEmpty) {
      return SliverToBoxAdapter(
        child: awaitingResults
            ? const _SearchResultSkeletons()
            : const AppEmptyState(
                icon: Icons.travel_explore_outlined,
                title: AppUiCopy.searchNoResultsTitle,
                body: AppUiCopy.searchNoResultsBody,
                // A status pill, never a control: a new search starts in the field.
                pillLabel: AppUiCopy.searchAgain,
              ),
      );
    }
    return SliverList(
      delegate: SliverChildBuilderDelegate(
        (BuildContext context, int index) =>
            _SearchResultRow(book: results[index]),
        childCount: results.length,
      ),
    );
  }
}

/// One result row: poster, title, episode count and, when the model carries it,
/// the description. The whole row is one semantic node: the label already contains
/// the poster and the counts, so the inner content is excluded.
class _SearchResultRow extends StatelessWidget {
  const _SearchResultRow({required this.book});

  final BookSummary book;

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.read<AppStateController>();
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final String description = book.description.trim();
    void open() => controller.openBook(book);

    return Padding(
      padding: EdgeInsets.only(bottom: spacing.space3),
      child: Semantics(
        button: true,
        label: '${book.title}, ${book.chapterCount} eps',
        onTap: open,
        // One announcement per result: the poster caption, title and count all
        // live inside the label above, so their nodes are excluded instead of
        // being read twice.
        excludeSemantics: true,
        child: InkWell(
          borderRadius: BorderRadius.circular(shapes.radiusControl),
          onTap: open,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              SizedBox(
                width: _resultPosterWidth,
                height: _resultPosterHeight,
                child: PosterCard(
                  title: book.title,
                  coverUrl: book.coverUrl,
                  episodeCount: book.chapterCount,
                  // The row prints the title beside the card already, so the
                  // poster does not repeat it over the artwork.
                  showTitleOverlay: false,
                  // The row prints the episode count below the title too.
                  showEpisodeBadge: false,
                ),
              ),
              SizedBox(width: spacing.space3),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    SizedBox(height: spacing.space1),
                    Text(
                      book.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: roles.body.copyWith(
                        color: colors.textPrimary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    SizedBox(height: spacing.space2),
                    Text(
                      '${book.chapterCount} eps',
                      style: Theme.of(context).textTheme.bodySmall
                          ?.copyWith(color: colors.textSecondary),
                    ),
                    if (description.isNotEmpty) ...<Widget>[
                      SizedBox(height: spacing.space1),
                      Text(
                        description,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: roles.caption.copyWith(
                          color: colors.textSecondary,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// A result-shaped placeholder: the poster block plus two text lines.
class _SearchResultSkeleton extends StatelessWidget {
  const _SearchResultSkeleton();

  @override
  Widget build(BuildContext context) {
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);

    return Padding(
      padding: EdgeInsets.only(bottom: spacing.space3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          AppSkeleton(
            width: _resultPosterWidth,
            height: _resultPosterHeight,
            radius: shapes.radiusControl,
          ),
          SizedBox(width: spacing.space3),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(top: spacing.space1),
              child: const AppSkeletonText(lines: 2),
            ),
          ),
        ],
      ),
    );
  }
}

/// A short run of result-shaped placeholders.
class _SearchResultSkeletons extends StatelessWidget {
  const _SearchResultSkeletons();

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        for (int index = 0; index < _skeletonRowCount; index++)
          const _SearchResultSkeleton(),
      ],
    );
  }
}
