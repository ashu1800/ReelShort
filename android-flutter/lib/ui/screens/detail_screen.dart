/// Detail: the drama page.
///
/// Contract parity with the Kotlin `DetailScreen`: episode rows key on
/// `"{chapterId}-{number}"`, row subtitles fall back from the episode
/// description to the book description, VIP locking starts after
/// [defaultVipFreeEpisodes] free episodes, the empty states reuse the shared
/// copy with a wired action, and every tap goes through
/// `AppStateController.openPlayer` (the controller API that owns the auth
/// prompt and the pending VIP episode; there is no `requestEpisodePlayback`).
/// The episode-spine signature stays on the poster card, which is where the
/// design system prescribes it for detail surfaces.
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_language.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_typography.dart';
import '../components/app_button.dart';
import '../components/app_empty_state.dart';
import '../components/poster_card.dart';

/// The drama detail screen.
class DetailScreen extends StatelessWidget {
  /// Creates the detail screen.
  const DetailScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppStateController controller = context.read<AppStateController>();
    final AppStrings copy = strings(state.language);

    if (state.selectedBook == null) {
      return _DetailEmptyState(
        title: copy.detailEmptyTitle,
        body: copy.detailEmptyMessage,
        actionLabel: copy.detailEmptyAction,
        onAction: controller.openHome,
      );
    }
    return _DetailContent(state: state);
  }
}

/// Hero, section header, empty state and the keyed episode list.
class _DetailContent extends StatelessWidget {
  const _DetailContent({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.read<AppStateController>();
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final BookSummary book = state.selectedBook!;
    final bool hasEpisodes = state.episodes.isNotEmpty;

    return Scaffold(
      backgroundColor: Colors.transparent,
      bottomSheet: hasEpisodes ? _PlayBar(state: state) : null,
      body: CustomScrollView(
        slivers: <Widget>[
          SliverAppBar(
            pinned: true,
            expandedHeight: 260,
            backgroundColor: colors.canvas,
            surfaceTintColor: Colors.transparent,
            leading: BackButton(onPressed: controller.openHome),
            flexibleSpace: FlexibleSpaceBar(
              background: PosterCard(
                title: book.filteredTitle,
                coverUrl: book.coverUrl,
                episodeCount: book.chapterCount,
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(book.filteredTitle, style: roles.displayLarge),
                  const SizedBox(height: 8),
                  Text(
                    book.description.trim().isNotEmpty
                        ? book.description
                        : '${book.chapterCount}${copy.bookRowFallbackSuffix}',
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                    style: roles.body.copyWith(color: colors.textSecondary),
                  ),
                  const SizedBox(height: 16),
                  _SocialRow(state: state),
                ],
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(copy.detailScreen, style: roles.display),
                  const SizedBox(height: 6),
                  Text(
                    '${copy.playerEpisodeSelectorTotalPrefix}'
                    '${state.episodes.length}'
                    '${copy.playerEpisodeSelectorTotalSuffix}',
                    style: roles.body.copyWith(color: colors.textSecondary),
                  ),
                ],
              ),
            ),
          ),
          if (!hasEpisodes)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                child: _DetailEmptyState(
                  title: copy.detailUnavailableTitle,
                  body:
                      '${copy.detailUnavailableMessagePrefix}${book.title}'
                      '${copy.detailUnavailableMessageSuffix}',
                  actionLabel: copy.detailUnavailableAction,
                  onAction: controller.openHome,
                ),
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (BuildContext context, int index) {
                    final EpisodeSummary episode = state.episodes[index];
                    return _EpisodeRow(
                      key: ValueKey<String>(
                        '${episode.chapterId}-${episode.number}',
                      ),
                      episode: episode,
                      bookDescription: book.description,
                      language: state.language,
                      locked:
                          !state.vipActive &&
                          episode.number > defaultVipFreeEpisodes,
                      onTap: () => controller.openPlayer(episode),
                    );
                  },
                  childCount: state.episodes.length,
                ),
              ),
            ),
          const SliverPadding(padding: EdgeInsets.only(bottom: 24)),
        ],
      ),
    );
  }
}

/// One episode row, mirroring the Kotlin `EpisodeRow` component.
class _EpisodeRow extends StatelessWidget {
  const _EpisodeRow({
    required this.episode,
    required this.bookDescription,
    required this.language,
    required this.locked,
    required this.onTap,
    super.key,
  });

  final EpisodeSummary episode;
  final String bookDescription;
  final AppLanguage language;
  final bool locked;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final AppStrings copy = strings(language);
    final String subtitle = episodeSubtitle(episode.description, bookDescription);

    return Semantics(
      button: true,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(shapes.radiusContainer),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: colors.surface,
            borderRadius: BorderRadius.circular(shapes.radiusContainer),
            border: Border.all(
              color: colors.hairline,
              width: shapes.strokeHairline,
            ),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
            child: Row(
              children: <Widget>[
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: colors.surfaceAlt,
                    borderRadius: BorderRadius.circular(shapes.radiusControl),
                  ),
                  child: Icon(
                    Icons.play_arrow_rounded,
                    size: 20,
                    color: colors.accentPrimary,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        episodeTitle(episode, language),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: textTheme.titleMedium!.copyWith(
                          color: colors.textPrimary,
                        ),
                      ),
                      if (subtitle.trim().isNotEmpty) ...<Widget>[
                        const SizedBox(height: 4),
                        Text(
                          subtitle,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: textTheme.bodySmall!.copyWith(
                            color: colors.textSecondary,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                if (locked) ...<Widget>[
                  const SizedBox(width: 10),
                  // The Kotlin source hardcodes the VIP badge label.
                  Semantics(
                    label: 'VIP',
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: colors.surfaceAlt,
                        borderRadius: BorderRadius.circular(shapes.radiusInline),
                        border: Border.all(
                          color: colors.outlineStrong,
                          width: shapes.strokeHairline,
                        ),
                      ),
                      child: Text(
                        'VIP',
                        style: textTheme.labelSmall!.copyWith(
                          color: colors.accentPrimary,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Icon(
                    Icons.lock_outline_rounded,
                    size: 20,
                    color: colors.accentPrimary,
                  ),
                ] else ...<Widget>[
                  const SizedBox(width: 10),
                  Text(
                    copy.playerPlayAction,
                    style: textTheme.bodyMedium!.copyWith(
                      color: colors.accentPrimary,
                    ),
                  ),
                  Icon(
                    Icons.chevron_right_rounded,
                    size: 20,
                    color: colors.textSecondary,
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Shared detail empty state: title, message and a wired action.
class _DetailEmptyState extends StatelessWidget {
  const _DetailEmptyState({
    required this.title,
    required this.body,
    required this.actionLabel,
    required this.onAction,
  });

  final String title;
  final String body;
  final String actionLabel;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    return AppEmptyState(
      icon: Icons.video_library_outlined,
      title: title,
      body: body,
      actionLabel: actionLabel,
      onAction: onAction,
    );
  }
}

/// Like / favorite row plus the episode-count meta pill.
class _SocialRow extends StatelessWidget {
  const _SocialRow({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.read<AppStateController>();
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppStrings copy = strings(state.language);

    return Row(
      children: <Widget>[
        _SocialAction(
          icon: state.interaction.liked ? Icons.favorite : Icons.favorite_outline,
          label: '${state.interaction.likeCount}',
          active: state.interaction.liked,
          activeColor: colors.accentDanger,
          onTap: controller.toggleLike,
        ),
        const SizedBox(width: 24),
        _SocialAction(
          icon: state.interaction.favorited
              ? Icons.bookmark
              : Icons.bookmark_outline,
          label: '${state.interaction.favoriteCount}',
          active: state.interaction.favorited,
          activeColor: colors.accentPrimary,
          onTap: controller.toggleFavorite,
        ),
        const Spacer(),
        Text(
          '${state.selectedBook?.chapterCount ?? 0}${copy.posterEpisodeUnit}',
          style: Theme.of(context)
              .textTheme
              .bodySmall!
              .copyWith(color: colors.textSecondary),
        ),
      ],
    );
  }
}

/// One icon + count social action.
class _SocialAction extends StatelessWidget {
  const _SocialAction({
    required this.icon,
    required this.label,
    required this.active,
    required this.activeColor,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final bool active;
  final Color activeColor;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final Color tint = active ? activeColor : colors.textSecondary;

    return Semantics(
      button: true,
      toggled: active,
      label: label,
      child: InkWell(
        onTap: onTap,
        child: Row(
          children: <Widget>[
            Icon(icon, size: 20, color: tint),
            const SizedBox(width: 6),
            Text(
              label,
              style: Theme.of(context)
                  .textTheme
                  .bodySmall!
                  .copyWith(color: tint),
            ),
          ],
        ),
      ),
    );
  }
}

/// Persistent play bar at the bottom of the screen.
class _PlayBar extends StatelessWidget {
  const _PlayBar({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.read<AppStateController>();
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppStrings copy = strings(state.language);

    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border(
          top: BorderSide(color: colors.hairline, width: shapes.strokeHairline),
        ),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: AppButton(
            label: copy.playerPlayAction,
            isExpanded: true,
            onPressed: state.selectedEpisode == null
                ? null
                : () => controller.openPlayer(state.selectedEpisode!),
          ),
        ),
      ),
    );
  }
}
