/// Favorites: the user's bookmarked dramas.
///
/// Behavioural parity with the Kotlin `FavoritesScreen`: a fixed two-column grid, books keyed by
/// `id`, a tap routing through `openBook`, and the guest state rendering the same empty list
/// reusing the "No favorites yet" copy. The Kotlin `onBack` parameter is never invoked, so no back
/// affordance is added here either.
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/formatting/app_strings.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../components/app_empty_state.dart';
import '../components/app_loading.dart';
import '../components/poster_card.dart';

/// The favorites screen.
class FavoritesScreen extends StatelessWidget {
  /// Creates the favorites screen. The shell instantiates it without arguments.
  const FavoritesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppStrings copy = strings(state.language);

    return Scaffold(
      backgroundColor: colors.canvas,
      body: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
          child: state.isLoading && state.favorites.isEmpty
              ? const AppSkeletonList(itemHeight: 204, itemCount: 3)
              : state.favorites.isEmpty
                  ? AppEmptyState(
                      icon: Icons.bookmark_border_outlined,
                      title: copy.favoritesEmptyTitle,
                      body: copy.favoritesEmptyMessage,
                    )
                  : LayoutBuilder(
                      builder: (BuildContext context, BoxConstraints constraints) {
                        const double tileGap = 12;
                        // Fixed two columns, like the Kotlin screen.
                        final double tileWidth =
                            (constraints.maxWidth - tileGap) / 2;
                        return GridView.count(
                          crossAxisCount: 2,
                          crossAxisSpacing: tileGap,
                          mainAxisSpacing: tileGap,
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          childAspectRatio: tileWidth / (tileWidth * 1.5 + 48),
                          children: <Widget>[
                            for (final BookSummary book in state.favorites)
                              PosterCard(
                                key: ValueKey<String>('favorite-${book.id}'),
                                title: book.filteredTitle,
                                coverUrl: book.coverUrl,
                                episodeCount: book.chapterCount,
                                onTap: () => context
                                    .read<AppStateController>()
                                    .openBook(book),
                              ),
                          ],
                        );
                      },
                    ),
        ),
      ),
    );
  }
}
