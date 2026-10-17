/// Search: the discovery screen.
///
/// Behavioural parity with the Kotlin `SearchScreen`: `trim()` on submit, blank
/// queries ignored without a side effect, the shared results-first decision,
/// discovery chips that submit immediately, wide-thumb result rows, the results
/// header with the query and the result count, and the empty-state copy with its
/// non-interactive "Search again" pill (parity gap G3 closed with `AppUiCopy`).
///
/// Structure contract (`docs/ui-redesign-notes.md` §7.8): one `screenTitle`, the
/// shared page inset, `AppSectionHeader` for every group title - which is also
/// what structurally removed the 320 px + 3.2x `RenderFlex` overflow the old
/// "dot + bare text in a Row" header produced - tokenized chips with 44 px
/// targets, a count pill in the results header, and result rows that announce as
/// one node.
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
import '../components/app_button.dart';
import '../components/app_empty_state.dart';
import '../components/app_loading.dart';
import '../components/app_section.dart';
import '../components/app_text_field.dart';
import '../components/poster_card.dart';

/// Width of the poster column in a result row.
const double _resultPosterWidth = 88;

/// Height of the poster column in a result row (2:3 art).
const double _resultPosterHeight = 132;

/// Minimum height of a discovery chip, so its target clears the touch minimum.
const double _chipMinHeight = 44;

/// Share of the content width the results count pill may occupy.
const double _countPillWidthFactor = 0.45;

/// Number of result skeletons while a query is in flight.
const int _skeletonRowCount = 3;

/// The search / discovery screen.
class SearchScreen extends StatelessWidget {
  /// Creates the search screen.
  const SearchScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppStrings copy = strings(state.language);
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final bool resultsFirst = searchShowsResultsFirst(
      state.searchQuery,
      state.searchResults.length,
    );
    final String query = state.searchQuery.trim();

    return SafeArea(
      bottom: false,
      child: Padding(
        // The single page inset of the system (18/12/18/28).
        padding: spacing.pagePadding,
        child: CustomScrollView(
          slivers: <Widget>[
            // One page-title role for the whole app; the screen used to open
            // straight into its search field.
            SliverToBoxAdapter(
              child: Text(copy.searchTab, style: roles.screenTitle),
            ),
            SliverToBoxAdapter(child: SizedBox(height: spacing.space4)),
            SliverToBoxAdapter(
              child: _SearchField(copy: copy, initial: state.searchQuery),
            ),
            if (resultsFirst) ...<Widget>[
              SliverToBoxAdapter(child: SizedBox(height: spacing.sectionGap)),
              SliverToBoxAdapter(
                child: LayoutBuilder(
                  builder: (BuildContext context, BoxConstraints constraints) {
                    return AppSectionHeader(
                      title: AppUiCopy.searchResultsSection,
                      trailing: _CountPill(
                        count: state.searchResults.length,
                        suffix: copy.searchResultCountSuffix,
                        // The header stacks its trailing widget at large text
                        // scales without giving it a flexible slot, so the pill
                        // caps itself and ellipsises instead of overflowing.
                        maxWidth: constraints.maxWidth * _countPillWidthFactor,
                      ),
                    );
                  },
                ),
              ),
              if (query.isNotEmpty)
                SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.only(top: spacing.space1),
                    child: Text(
                      query,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: roles.body.copyWith(color: colors.textSecondary),
                    ),
                  ),
                ),
              SliverToBoxAdapter(child: SizedBox(height: spacing.space3)),
              _ResultsSection(state: state),
            ] else ...<Widget>[
              SliverToBoxAdapter(child: SizedBox(height: spacing.sectionGap)),
              SliverToBoxAdapter(
                child: AppSectionHeader(title: copy.searchTagsTitle),
              ),
              SliverToBoxAdapter(child: SizedBox(height: spacing.space2)),
              const SliverToBoxAdapter(child: _DiscoveryGroups()),
              SliverToBoxAdapter(child: SizedBox(height: spacing.sectionGap)),
              // While a search is in flight, the lower region shows the shape of
              // the results that are coming instead of an empty prompt: the
              // shared loading flag is the only signal available this early.
              if (state.isLoading && state.searchResults.isEmpty)
                const SliverToBoxAdapter(child: _SearchResultSkeletons())
              else
                const SliverToBoxAdapter(
                  child: AppEmptyState(
                    icon: Icons.travel_explore_outlined,
                    title: AppUiCopy.searchStartTitle,
                    body: AppUiCopy.searchStartBody,
                  ),
                ),
            ],
            SliverToBoxAdapter(child: SizedBox(height: spacing.sectionGap)),
          ],
        ),
      ),
    );
  }
}

/// The query field plus the one primary action of the screen.
class _SearchField extends StatefulWidget {
  const _SearchField({required this.copy, required this.initial});

  final AppStrings copy;
  final String initial;

  @override
  State<_SearchField> createState() => _SearchFieldState();
}

class _SearchFieldState extends State<_SearchField> {
  late final TextEditingController _controller = TextEditingController(
    text: widget.initial,
  );

  @override
  void didUpdateWidget(_SearchField oldWidget) {
    super.didUpdateWidget(oldWidget);
    // The field mirrors the state layer's query and re-syncs on language
    // switches, but a rebuild whose `initial` did not move must never wipe
    // in-progress typing — the Kotlin `remember(state.searchQuery, language)`
    // only recreates the field when one of those two keys changes.
    if (widget.initial != oldWidget.initial &&
        widget.initial != _controller.text) {
      _controller.value = TextEditingValue(text: widget.initial);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit(String raw) {
    // Blank queries are ignored with no side effect, exactly like the Kotlin screen.
    final String query = raw.trim();
    if (query.isEmpty) {
      return;
    }
    context.read<AppStateController>().search(query);
  }

  @override
  Widget build(BuildContext context) {
    final AppSpacing spacing = appSpacingOf(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        AppTextField(
          controller: _controller,
          label: widget.copy.searchHint,
          textInputAction: TextInputAction.search,
          onSubmitted: _submit,
        ),
        SizedBox(height: spacing.space3),
        // The only filled action of the screen.
        AppButton(
          label: widget.copy.searchAction,
          isExpanded: true,
          onPressed: () => _submit(_controller.text),
        ),
      ],
    );
  }
}

/// The results count, as a compact status pill in the section header.
class _CountPill extends StatelessWidget {
  const _CountPill({
    required this.count,
    required this.suffix,
    required this.maxWidth,
  });

  final int count;
  final String suffix;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return ConstrainedBox(
      constraints: BoxConstraints(maxWidth: maxWidth),
      child: Container(
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
      ),
    );
  }
}

/// The discovery groups: a section header per group, then its tokenized chips.
///
/// The old group header was a purple dot plus a bare `Text` in a `Row` with no
/// flexible child, which produced four `RenderFlex` overflows at 320 px with
/// 3.2x text. [AppSectionHeader] stacks its title at that scale, so the overflow
/// is gone structurally rather than clipped away.
class _DiscoveryGroups extends StatelessWidget {
  const _DiscoveryGroups();

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppSpacing spacing = appSpacingOf(context);
    final AppStateController controller = context.read<AppStateController>();
    final List<SearchDiscoveryGroup> groups = searchDiscoveryGroups(
      state.language,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        for (final SearchDiscoveryGroup group in groups) ...<Widget>[
          // A second-level lane label, not another section header: repeating the
          // full header treatment here read as an empty section followed by a
          // new one on device.
          _GroupLabel(title: group.title),
          SizedBox(height: spacing.space2),
          LayoutBuilder(
            builder: (BuildContext context, BoxConstraints constraints) {
              return Wrap(
                spacing: spacing.space2,
                runSpacing: spacing.space2,
                children: <Widget>[
                  for (final String tag in group.tags)
                    _DiscoveryChip(
                      tag: tag,
                      // A chip never exceeds the lane width, so a long tag at a
                      // large scale ellipsises instead of overflowing the run.
                      maxWidth: constraints.maxWidth,
                      onTap: () => controller.search(tag),
                    ),
                ],
              );
            },
          ),
          SizedBox(height: spacing.space4),
        ],
      ],
    );
  }
}

/// A lane label: the second level under the single "Popular lanes" header.
///
/// The group names are rendered in sentence case with the caption role and the
/// secondary colour - no upper-casing, no accent decoration, no section-header
/// geometry - so "Popular lanes" stays the only section head. The row is flexed
/// and ellipsised, so a long lane name at a large text scale can never restore
/// the old bare-`Row` overflow.
class _GroupLabel extends StatelessWidget {
  const _GroupLabel({required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return Row(
      children: <Widget>[
        Flexible(
          child: Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: roles.caption.copyWith(
              color: colors.textSecondary,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ],
    );
  }
}

/// A tokenized discovery chip: 44 px tall, surface fill, hairline edge.
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
            borderRadius: BorderRadius.circular(shapes.radiusPill),
            side: BorderSide(
              color: colors.hairline,
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
                    color: colors.textPrimary,
                    fontWeight: FontWeight.w600,
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
  const _ResultsSection({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    if (state.isLoading && state.searchResults.isEmpty) {
      return const SliverToBoxAdapter(child: _SearchResultSkeletons());
    }
    if (state.searchResults.isEmpty) {
      return const SliverToBoxAdapter(
        child: AppEmptyState(
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
        (BuildContext context, int index) => _SearchResultRow(
          book: state.searchResults[index],
        ),
        childCount: state.searchResults.length,
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
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: colors.textSecondary,
                      ),
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
