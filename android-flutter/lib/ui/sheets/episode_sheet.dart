/// The player's episode sheet: whole-series overview, numeric grid, VIP split.
///
/// This replaces a list of 38 "EP 04 · title" rows that each carried a slice of
/// the episode spine. The sheet now answers the two questions people open it
/// for — *where am I* and *what do I tap next* — in two lines and a grid:
///
/// ```text
/// │▎▍▍▎▍▎▍▍▎▍▎▍▍▎▍▎▍▍▎▍▎▍│             overview, one segment per episode
/// 38 eps · 5 Watched · Current EP 04
///
/// FREE                                1–7
/// [01][02][03][04][05][06]
/// [07]
///
/// 🔒 VIP unlocks EP 08–38
///    VIP membership is required to continue watching.
///    [            Unlock VIP            ]
///
/// VIP                                8–38
/// [08][09][10][11][12][13]
/// ```
///
/// The free/VIP boundary is the backend's live `vip.free-episodes`
/// ([AppStateController.freeEpisodes]) — the same value the play endpoint
/// enforces — so the lock never disagrees with what the server will serve. It
/// used to be a hard-coded 7 in three places.
///
/// Tapping a gated cell is not blocked here: it closes the sheet and calls the
/// ordinary playback entry, which opens the paywall and resumes the episode
/// after payment. That flow predates this sheet and is unchanged.
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/app_ui_copy.dart';
import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_language.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';
import '../components/app_button.dart';
import '../components/app_section.dart';
import '../components/episode_spine.dart';
import '../components/episode_tile.dart';

/// Maximum cell width; the grid packs as many columns of at least
/// [episodeTileMinExtent] as fit. 56 dp yields six columns on a 360 dp phone
/// and five on a 320 dp one, so a 38-episode season is seven rows instead of
/// ten — the whole point of replacing the list.
const double _maxTileExtent = 56;

/// Gap between two cells.
const double _tileGap = 6;

/// Vertical rhythm between the sheet's blocks.
const double _blockGap = 12;

/// The episode sheet body. Mount it as the child of `AppSheet.show`.
class EpisodeSheet extends StatelessWidget {
  /// Creates the episode sheet body.
  const EpisodeSheet({super.key});

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.read<AppStateController>();
    final AppUiState state = context.watch<AppStateController>().state;
    final List<EpisodeSummary> episodes = state.episodes;
    if (episodes.isEmpty) {
      return const SizedBox.shrink();
    }

    final EpisodeSummary? current = state.selectedEpisode;
    final int freeEpisodes = controller.freeEpisodes;
    final bool gated =
        !state.vipActive &&
        episodes.any(
          (EpisodeSummary episode) => episodeRequiresVip(episode, freeEpisodes),
        );

    final List<EpisodeSummary> free = gated
        ? episodes
              .where((EpisodeSummary e) => !episodeRequiresVip(e, freeEpisodes))
              .toList(growable: false)
        : const <EpisodeSummary>[];
    final List<EpisodeSummary> locked = gated
        ? episodes
              .where((EpisodeSummary e) => episodeRequiresVip(e, freeEpisodes))
              .toList(growable: false)
        : const <EpisodeSummary>[];

    final List<EpisodeTick> ticks = <EpisodeTick>[
      for (final EpisodeSummary episode in episodes)
        _tickFor(state, episode, current, freeEpisodes),
    ];

    return ListView(
      padding: EdgeInsets.zero,
      children: <Widget>[
        EpisodeSpine(ticks: ticks, language: state.language),
        SizedBox(height: _blockGap / 2),
        _OverviewLine(ticks: ticks, language: state.language),
        SizedBox(height: _blockGap),
        if (!gated)
          _EpisodeGrid(
            episodes: episodes,
            state: state,
            current: current,
            locked: false,
          )
        else ...<Widget>[
          _FreeHeader(episodes: free, language: state.language),
          SizedBox(height: _blockGap / 2),
          _EpisodeGrid(
            episodes: free,
            state: state,
            current: current,
            locked: false,
          ),
          SizedBox(height: _blockGap),
          _VipUnlockBanner(firstLocked: locked.first.number, state: state),
          SizedBox(height: _blockGap),
          _VipHeader(episodes: locked, language: state.language),
          SizedBox(height: _blockGap / 2),
          _EpisodeGrid(
            episodes: locked,
            state: state,
            current: current,
            locked: true,
          ),
        ],
      ],
    );
  }
}

/// The watch-state tick of one episode.
EpisodeTick _tickFor(
  AppUiState state,
  EpisodeSummary episode,
  EpisodeSummary? current,
  int freeEpisodes,
) {
  final bool selected = _isSelected(episode, current);
  final EpisodeWatchStatus status = episodeWatchStatus(
    state.playback.book?.id,
    episode,
    current,
    state.watchHistory,
  );
  final EpisodeTickState tickState =
      selected || status.type == EpisodeWatchStatusType.current
      ? EpisodeTickState.current
      : switch (status.type) {
          EpisodeWatchStatusType.watched => EpisodeTickState.watched,
          EpisodeWatchStatusType.inProgress => EpisodeTickState.inProgress,
          _ => EpisodeTickState.none,
        };
  // Membership lifts the gate everywhere: a VIP sees one undimmed season.
  final bool locked =
      !state.vipActive &&
      !selected &&
      episodeRequiresVip(episode, freeEpisodes);
  return EpisodeTick(
    number: episode.number,
    state: tickState,
    progress: status.progressPercent,
    isVipLocked: locked,
  );
}

bool _isSelected(EpisodeSummary episode, EpisodeSummary? current) =>
    current != null &&
    current.number == episode.number &&
    current.chapterId == episode.chapterId;

/// The visible summary line under the bar (`38 eps · 5 Watched · Current EP 04`).
///
/// The bar itself announces the same sentence to a screen reader, so this line
/// is decoration and is excluded from the semantics tree.
class _OverviewLine extends StatelessWidget {
  const _OverviewLine({required this.ticks, required this.language});

  final List<EpisodeTick> ticks;
  final AppLanguage language;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    return ExcludeSemantics(
      child: Text(
        EpisodeSpine.describe(ticks, language: language, separator: ' · '),
        maxLines: 2,
        style: roles.caption.copyWith(color: colors.textSecondary),
      ),
    );
  }
}

/// The `FREE ... 1–7` header, present only while the grid is split.
class _FreeHeader extends StatelessWidget {
  const _FreeHeader({required this.episodes, required this.language});

  final List<EpisodeSummary> episodes;
  final AppLanguage language;

  @override
  Widget build(BuildContext context) => AppSectionHeader(
    title: AppUiCopy.episodesFreeSection,
    trailing: _RangePill(
      label: episodeRangeLabel(
        episodes.first.number,
        episodes.last.number,
        language,
      ),
    ),
  );
}

/// The `VIP ... 8–38` header, present only while the grid is split.
class _VipHeader extends StatelessWidget {
  const _VipHeader({required this.episodes, required this.language});

  final List<EpisodeSummary> episodes;
  final AppLanguage language;

  @override
  Widget build(BuildContext context) => AppSectionHeader(
    title: AppUiCopy.episodesVipSection,
    trailing: _RangePill(
      label: episodeRangeLabel(
        episodes.first.number,
        episodes.last.number,
        language,
      ),
    ),
  );
}

/// The episode range of a section, as a quiet capsule.
class _RangePill extends StatelessWidget {
  const _RangePill({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: spacing.space2,
        vertical: spacing.space1 / 2,
      ),
      decoration: BoxDecoration(
        color: colors.surfaceAlt,
        borderRadius: BorderRadius.circular(shapes.radiusPill),
      ),
      child: Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: roles.caption.copyWith(color: colors.textSecondary),
      ),
    );
  }
}

/// The paywall banner between the free run and the gated run.
class _VipUnlockBanner extends StatelessWidget {
  const _VipUnlockBanner({required this.firstLocked, required this.state});

  /// First gated episode; tapping the action asks to play exactly that one, so
  /// a confirmed payment resumes at the start of the locked run rather than
  /// somewhere arbitrary in the middle of it.
  final int firstLocked;

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final EpisodeSummary? target = state.episodes
        .cast<EpisodeSummary?>()
        .firstWhere(
          (EpisodeSummary? episode) => episode?.number == firstLocked,
          orElse: () => null,
        );

    final String range = episodeRangeLabel(
      firstLocked,
      state.episodes.last.number,
      state.language,
    );

    return Container(
      padding: EdgeInsets.all(spacing.space4),
      decoration: BoxDecoration(
        color: colors.premiumSurface,
        borderRadius: shapes.containerRadius,
        border: Border.all(
          color: colors.accentPrimary.withValues(alpha: 0.35),
          width: shapes.strokeHairline,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Icon(Icons.lock, size: 18, color: colors.accentPrimary),
              SizedBox(width: spacing.space2),
              Expanded(
                child: Text(
                  '${AppUiCopy.vipUnlockRangePrefix}$range',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: roles.body.copyWith(
                    color: colors.textPrimary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          SizedBox(height: spacing.space1),
          Text(
            copy.vipPayLockedHint,
            style: roles.caption.copyWith(color: colors.textSecondary),
          ),
          SizedBox(height: spacing.space3),
          // The sheet has exactly one primary action, and it is this one.
          AppButton(
            label: copy.accountVipUnlockAction,
            isExpanded: true,
            onPressed: target == null
                ? null
                : () {
                    Navigator.of(context).pop();
                    controller.openPlayer(target);
                  },
          ),
        ],
      ),
    );
  }
}

/// The tap-to-play grid of one section.
class _EpisodeGrid extends StatelessWidget {
  const _EpisodeGrid({
    required this.episodes,
    required this.state,
    required this.current,
    required this.locked,
  });

  final List<EpisodeSummary> episodes;
  final AppUiState state;
  final EpisodeSummary? current;

  /// Whether this grid *is* the gated run. Only the sheet's split branch ever
  /// passes true, so a member — or a free run covering the whole season — gets
  /// one undimmed grid.
  final bool locked;

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.read<AppStateController>();
    return GridView.builder(
      padding: EdgeInsets.zero,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
        maxCrossAxisExtent: _maxTileExtent,
        crossAxisSpacing: _tileGap,
        mainAxisSpacing: _tileGap,
        childAspectRatio: 1,
      ),
      itemCount: episodes.length,
      itemBuilder: (BuildContext context, int index) {
        final EpisodeSummary episode = episodes[index];
        final bool selected = _isSelected(episode, current);
        final EpisodeWatchStatus status = episodeWatchStatus(
          state.playback.book?.id,
          episode,
          current,
          state.watchHistory,
        );
        final EpisodeTickState tileState = selected
            ? EpisodeTickState.current
            : switch (status.type) {
                EpisodeWatchStatusType.watched => EpisodeTickState.watched,
                EpisodeWatchStatusType.inProgress =>
                  EpisodeTickState.inProgress,
                EpisodeWatchStatusType.current => EpisodeTickState.current,
                EpisodeWatchStatusType.none => EpisodeTickState.none,
              };

        void open() {
          // Re-selecting the current episode only closes the sheet.
          Navigator.of(context).pop();
          if (!selected) {
            controller.openPlayer(episode);
          }
        }

        return EpisodeTile(
          key: ValueKey<String>('${episode.chapterId}:${episode.number}'),
          number: episode.number,
          state: tileState,
          progressPercent: status.progressPercent,
          // The sheet already decided the split; a cell only repeats it.
          locked: locked,
          selected: selected,
          language: state.language,
          semanticLabel: episodeTileSemanticLabel(
            number: episode.number,
            title: episode.title,
            state: tileState,
            progressPercent: status.progressPercent,
            locked: locked,
            selected: selected,
            language: state.language,
          ),
          onTap: open,
        );
      },
    );
  }
}
