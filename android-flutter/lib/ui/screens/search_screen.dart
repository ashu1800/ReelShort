/// Search: the discovery screen.
///
/// Behavioural parity with the Kotlin `SearchScreen`: `trim()` on submit, blank queries ignored
/// without a side effect, the shared results-first decision, discovery chips that submit
/// immediately, wide-thumb result rows, the results header with the query and the result count,
/// and the empty-state copy override with a non-interactive "Search again" pill (parity gap G3).
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/formatting/app_strings.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_typography.dart';
import '../components/app_button.dart';
import '../components/app_empty_state.dart';
import '../components/app_text_field.dart';
import '../components/poster_card.dart';

/// The search / discovery screen.
class SearchScreen extends StatelessWidget {
  /// Creates the search screen.
  const SearchScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);

    return SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: CustomScrollView(
          slivers: <Widget>[
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.only(top: 12, bottom: 16),
                child: _SearchField(copy: copy, initial: state.searchQuery),
              ),
            ),
            if (searchShowsResultsFirst(
              state.searchQuery,
              state.searchResults.length,
            )) ...<Widget>[
              SliverToBoxAdapter(
                child: _ResultsHeader(
                  copy: copy,
                  roles: roles,
                  query: state.searchQuery,
                  resultCount: state.searchResults.length,
                ),
              ),
              const SliverToBoxAdapter(child: SizedBox(height: 12)),
              _ResultsSection(state: state),
            ] else ...<Widget>[
              SliverToBoxAdapter(
                child: Text(copy.searchTagsTitle, style: roles.display),
              ),
              const SliverToBoxAdapter(child: SizedBox(height: 12)),
              // A Column is a box, not a sliver: it must ride in a box adapter.
              const SliverToBoxAdapter(child: _DiscoveryGroups()),
              const SliverToBoxAdapter(child: SizedBox(height: 32)),
              SliverToBoxAdapter(
                child: AppEmptyState(
                  icon: Icons.travel_explore_outlined,
                  // Parity gap G3: the shared empty copy is overridden here with hardcoded English
                  // and the retry affordance stays a non-interactive pill.
                  title: 'Find your next drama',
                  body: 'No dramas matched this search.',
                  pillLabel: 'Search again',
                ),
              ),
            ],
            const SliverToBoxAdapter(child: SizedBox(height: 24)),
          ],
        ),
      ),
    );
  }
}

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
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        AppTextField(
          controller: _controller,
          label: widget.copy.searchHint,
          textInputAction: TextInputAction.search,
          onSubmitted: _submit,
        ),
        const SizedBox(height: 10),
        AppButton(
          label: widget.copy.searchAction,
          isExpanded: true,
          onPressed: () => _submit(_controller.text),
        ),
      ],
    );
  }
}

/// The results header: `Results`, the live query and the `N dramas` count.
class _ResultsHeader extends StatelessWidget {
  const _ResultsHeader({
    required this.copy,
    required this.roles,
    required this.query,
    required this.resultCount,
  });

  final AppStrings copy;
  final AppTypography roles;
  final String query;
  final int resultCount;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: <Widget>[
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(copy.searchResultTitle, style: roles.display),
              if (query.trim().isNotEmpty) ...<Widget>[
                const SizedBox(height: 2),
                Text(
                  query,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: roles.body.copyWith(color: colors.textSecondary),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(width: 12),
        Text(
          '$resultCount ${copy.searchResultCountSuffix}',
          style: roles.body.copyWith(
            color: colors.accentPrimary,
            fontWeight: FontWeight.bold,
          ),
        ),
      ],
    );
  }
}

/// The discovery groups: each group keeps its title above its chips, and a
/// chip writes the query and submits immediately.
class _DiscoveryGroups extends StatelessWidget {
  const _DiscoveryGroups();

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStateController controller = context.read<AppStateController>();
    final List<SearchDiscoveryGroup> groups = searchDiscoveryGroups(
      state.language,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        for (final SearchDiscoveryGroup group in groups) ...<Widget>[
          Row(
            children: <Widget>[
              Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: colors.accentPrimary,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                group.title,
                style: roles.body.copyWith(
                  color: colors.textSecondary,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: group.tags
                .map(
                  (String tag) => ActionChip(
                    label: Text(tag, maxLines: 1, overflow: TextOverflow.ellipsis),
                    backgroundColor: colors.surfaceAlt,
                    side: BorderSide(
                      color: colors.hairline,
                      width: shapes.strokeHairline,
                    ),
                    onPressed: () => controller.search(tag),
                  ),
                )
                .toList(growable: false),
          ),
          const SizedBox(height: 12),
        ],
      ],
    );
  }
}

class _ResultsSection extends StatelessWidget {
  const _ResultsSection({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    if (state.searchResults.isEmpty) {
      return const SliverFillRemaining(
        hasScrollBody: false,
        child: AppEmptyState(
          icon: Icons.travel_explore_outlined,
          title: 'Find your next drama',
          body: 'No dramas matched this search.',
          pillLabel: 'Search again',
        ),
      );
    }
    return SliverList(
      delegate: SliverChildBuilderDelegate(
        (BuildContext context, int index) =>
            _SearchResultRow(book: state.searchResults[index]),
        childCount: state.searchResults.length,
      ),
    );
  }
}

class _SearchResultRow extends StatelessWidget {
  const _SearchResultRow({required this.book});

  final BookSummary book;

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.read<AppStateController>();
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Semantics(
        button: true,
        label: book.title,
        child: InkWell(
          borderRadius: BorderRadius.circular(shapes.radiusControl),
          onTap: () => controller.openBook(book),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              SizedBox(
                width: 88,
                height: 132,
                child: PosterCard(
                  title: book.title,
                  coverUrl: book.coverUrl,
                  episodeCount: book.chapterCount,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    const SizedBox(height: 4),
                    Text(
                      book.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: roles.body.copyWith(color: colors.textPrimary),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      '${book.chapterCount} eps',
                      style: Theme.of(context)
                          .textTheme
                          .bodySmall!
                          .copyWith(color: colors.textSecondary),
                    ),
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
