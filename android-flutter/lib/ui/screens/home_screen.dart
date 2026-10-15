/// Home: the shelf grid.
///
/// Parity is behavioural: pull-to-refresh only here, `GridCells.Adaptive(136.dp)` column math,
/// poster overlay title density from the shared formatter, books keyed by `id`, and skeletons
/// instead of a blocking dialog. The layout itself is new.
library;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:provider/provider.dart';

import '../../core/formatting/app_strings.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../components/app_empty_state.dart';
import '../components/app_loading.dart';
import '../components/poster_card.dart';

/// The primary shelf screen.
class HomeScreen extends StatelessWidget {
  /// Creates the home screen.
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppStrings copy = strings(state.language);

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: RefreshIndicator.adaptive(
        onRefresh: () => context.read<AppStateController>().refreshHomeWithPull(),
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: <Widget>[
            const SliverToBoxAdapter(child: SizedBox(height: 8)),
            if (state.isLoading && state.homeShelf.isEmpty)
              const SliverToBoxAdapter(child: AppSkeletonList(itemHeight: 204, itemCount: 4))
            else if (state.homeShelf.isEmpty)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 48),
                  child: Center(
                    child: AppEmptyState(
                      icon: Icons.movie_filter_outlined,
                      // The shared shelf-empty copy with a working "go to
                      // search" action, like the Kotlin `homeEmptyState`.
                      title: copy.homeEmptyTitle,
                      body: copy.homeEmptyMessage,
                      actionLabel: copy.homeEmptyAction,
                      onAction: context.read<AppStateController>().showSearch,
                    ),
                  ),
                ),
              )
            else
              _ShelfGrid(books: state.homeShelf),
            const SliverToBoxAdapter(child: SizedBox(height: 24)),
          ],
        ),
      ),
    );
  }
}

class _ShelfGrid extends StatelessWidget {
  const _ShelfGrid({required this.books});

  final List<BookSummary> books;

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.read<AppStateController>();

    return SliverLayoutBuilder(
      builder: (BuildContext context, SliverConstraints constraints) {
        // GridCells.Adaptive(136.dp): floor(availableWidth / 136dp), at least one column.
        final int columns =
            (constraints.crossAxisExtent / _gridColumnsMinWidth()).floor().clamp(1, 6);
        final double tileWidth = constraints.crossAxisExtent / columns;
        // 2:3 poster area plus the reserved overlay, matching the Kotlin card proportions.
        final double tileHeight = tileWidth * 1.5 + 48;

        return SliverGrid(
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: columns,
            mainAxisSpacing: 12,
            crossAxisSpacing: 12,
            childAspectRatio: tileWidth / tileHeight,
          ),
          delegate: SliverChildBuilderDelegate((BuildContext context, int index) {
            final BookSummary book = books[index];
            return PosterCard(
              key: ValueKey<String>('home-book-${book.id}'),
              title: book.filteredTitle,
              coverUrl: book.coverUrl,
              episodeCount: book.chapterCount,
              onTap: () => controller.openBook(book),
            );
          }, childCount: books.length),
        );
      },
    );
  }
}

/// Minimum column width for the adaptive shelf grid, mirroring `GridCells.Adaptive(136.dp)`.
double _gridColumnsMinWidth() => 136;
