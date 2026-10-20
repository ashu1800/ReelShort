/// One cell of the episode grid.
///
/// The episode sheet replaced its per-episode list rows with a numeric grid:
/// 38 episodes are 38 small cells that fit six to a line, instead of 38 rows of
/// "EP 04 · title" that need several screens of scrolling. The cell carries the
/// number large and the state as a second channel, so the grid reads at a
/// glance: current is a filled accent cell, watched has a solid underline,
/// in-progress an underline filled to its percentage, locked is dimmed with a
/// lock glyph.
///
/// The title of an episode is not printed here — a 47 dp cell cannot hold it —
/// but it is part of the cell's semantic label, so a screen reader still gets
/// the full row it used to read.
library;

import 'package:flutter/material.dart';

import '../../core/formatting/app_ui_copy.dart';
import '../../core/formatting/app_strings.dart';
import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_language.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';
import 'episode_spine.dart';

/// Minimum side of one cell; the grid keeps columns at or above this.
const double episodeTileMinExtent = 44;

/// Height of the progress underline inside a cell.
const double _progressBarHeight = 2;

/// The label a screen reader announces for one cell.
///
/// `EP 08, The Reckoning, Watched, VIP locked` — number, title, watch state and
/// the lock, which is everything the old list row showed.
String episodeTileSemanticLabel({
  required int number,
  required String title,
  required EpisodeTickState state,
  required int progressPercent,
  required bool locked,
  required bool selected,
  AppLanguage language = AppLanguage.defaultLanguage,
}) {
  final AppStrings copy = strings(language);
  final String trimmed = title.trim();
  final String stateWord = selected
      ? copy.playerEpisodeStatusCurrent
      : switch (state) {
          EpisodeTickState.watched => copy.playerEpisodeStatusWatched,
          EpisodeTickState.inProgress => '${progressPercent.clamp(0, 100)}%',
          EpisodeTickState.current => copy.playerEpisodeStatusCurrent,
          EpisodeTickState.none => '',
        };
  return <String>[
    episodeNumberLabel(number, language),
    if (trimmed.isNotEmpty) trimmed,
    if (stateWord.isNotEmpty) stateWord,
    if (locked) AppUiCopy.episodeLocked,
  ].join(', ');
}

/// One episode cell of the grid.
class EpisodeTile extends StatelessWidget {
  /// Creates an episode cell.
  const EpisodeTile({
    required this.number,
    required this.state,
    required this.progressPercent,
    required this.locked,
    required this.semanticLabel,
    required this.onTap,
    this.selected = false,
    this.language = AppLanguage.defaultLanguage,
    super.key,
  });

  /// 1-based episode number; rendered zero-padded (`04`).
  final int number;

  /// Watch state, driving the fill and the underline.
  final EpisodeTickState state;

  /// Percentage watched, `0..100`.
  final int progressPercent;

  /// Whether the episode sits behind the VIP gate.
  final bool locked;

  /// What the screen reader announces; see [episodeTileSemanticLabel].
  final String semanticLabel;

  /// Tap handler. A locked cell still calls it: the player turns that into the
  /// paywall, which is the documented behaviour of the old lock rows.
  final VoidCallback onTap;

  /// Whether this is the episode currently playing.
  final bool selected;

  /// Language of the number prefix.
  final AppLanguage language;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    final bool current = selected || state == EpisodeTickState.current;
    final bool watched = state == EpisodeTickState.watched;
    final bool inProgress = state == EpisodeTickState.inProgress;

    final Color fill = current
        ? colors.accentPrimary
        : (locked ? colors.surfaceDisabled : colors.surface);
    final Color foreground = current
        ? colors.onAccent
        : (locked ? colors.onDisabled : colors.textPrimary);
    final BorderSide border = current || locked
        ? BorderSide.none
        : (inProgress
              ? BorderSide(
                  color: colors.accentPrimary,
                  width: shapes.strokeEmphasis,
                )
              : BorderSide(color: colors.hairline, width: shapes.strokeHairline));

    return Semantics(
      button: true,
      selected: current,
      label: semanticLabel,
      onTap: onTap,
      excludeSemantics: true,
      child: Material(
        color: fill,
        borderRadius: shapes.cellRadius,
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Container(
            constraints: const BoxConstraints(
              minWidth: episodeTileMinExtent,
              minHeight: episodeTileMinExtent,
            ),
            decoration: BoxDecoration(
              borderRadius: shapes.cellRadius,
              border: Border.fromBorderSide(border),
            ),
            child: Stack(
              children: <Widget>[
                Center(
                  child: Padding(
                    padding: EdgeInsets.symmetric(
                      horizontal: spacing.space1,
                      vertical: spacing.space2,
                    ),
                    // The number shrinks to fit instead of ellipsising: at the
                    // largest system text size a 47 dp cell cannot hold "EP 38"
                    // at 3.2x, and a truncated episode number is useless.
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Text(
                        episodeNumberLabel(number, language),
                        maxLines: 1,
                        style: roles.body.copyWith(
                          color: foreground,
                          fontWeight: current || watched
                              ? FontWeight.w700
                              : FontWeight.w500,
                        ),
                      ),
                    ),
                  ),
                ),
                if (locked)
                  Positioned(
                    top: 2,
                    right: 2,
                    child: Icon(
                      Icons.lock,
                      size: 10,
                      color: colors.textSecondary,
                    ),
                  ),
                // Second channel for watched / in progress: a bar on the bottom
                // edge, full when watched, filled to the percentage otherwise.
                // `heightFactor: 1` is required: inside a `Positioned` with an
                // explicit height the height constraint is unbounded, and a
                // `FractionallySizedBox` without it collapses the fill to zero.
                if (!current && (watched || inProgress))
                  Positioned(
                    left: 0,
                    right: 0,
                    bottom: 0,
                    height: _progressBarHeight,
                    child: FractionallySizedBox(
                      alignment: Alignment.centerLeft,
                      widthFactor: watched
                          ? 1
                          : (progressPercent.clamp(0, 100)) / 100,
                      heightFactor: 1,
                      child: ColoredBox(color: colors.accentPrimary),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
