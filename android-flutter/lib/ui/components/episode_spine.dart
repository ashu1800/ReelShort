/// The **Episode Spine** - the signature element of the ShortLink design
/// system, now rendered as one full-width overview bar.
///
/// One segment per episode fills the width: the bar *is* the series, and a
/// segment's fill is that episode's watch state. The current episode is the only
/// segment that grows, watched segments are solid, in-progress segments are
/// partly filled, untouched segments use the minimum-contrast neutral, locked
/// segments are dimmed, and the VIP boundary carries a small warning notch
/// beneath it.
///
/// The earlier version drew a 16 px vertical rail with a 44 px tap target per
/// tick and was inset into the episode drawer's row header. Inside a 38-episode
/// list that is 38 rows of chrome for information the numeric grid shows in two
/// lines, so the spine was reduced to what it says best: a summary of the whole
/// season at a glance. Individual episodes are tapped in the grid
/// ([EpisodeTile]), and [EpisodeSpine.describe] still produces the sentence a
/// screen reader announces.
library;

import 'package:flutter/material.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/app_ui_copy.dart';
import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_language.dart';
import '../../theme/app_colors.dart';

/// Watch state of one segment.
enum EpisodeTickState {
  /// Never opened.
  none,

  /// The episode the player is on. Draws taller than the rest.
  current,

  /// Partially watched; [EpisodeTick.progress] carries the percentage.
  inProgress,

  /// Finished.
  watched,
}

/// One segment of the spine.
@immutable
class EpisodeTick {
  /// Creates a tick.
  const EpisodeTick({
    required this.number,
    required this.state,
    this.progress = 0,
    this.isVipLocked = false,
    this.isGate = false,
  });

  /// 1-based episode number.
  final int number;

  /// Watch state.
  final EpisodeTickState state;

  /// Percentage watched, `0..100`. Clamped on read.
  final int progress;

  /// Whether the episode sits behind the VIP gate.
  final bool isVipLocked;

  /// Whether this segment is the first locked one, i.e. the paywall landmark.
  final bool isGate;

  /// [progress] clamped to `0..100`.
  int get clampedProgress => progress.clamp(0, 100);

  /// Builds one segment per episode in `1..count`, all unwatched.
  ///
  /// Simplifies the old API: the caller no longer supplies a lock predicate,
  /// because the spine's own state model already carries [isVipLocked] and the
  /// episode-sheet builder computes both flags from the same boundary.
  static List<EpisodeTick> unwatched(
    int count, {
    bool Function(int episode)? isVipLocked,
  }) => <EpisodeTick>[
    for (int episode = 1; episode <= count; episode++)
      EpisodeTick(
        number: episode,
        state: EpisodeTickState.none,
        isVipLocked: isVipLocked?.call(episode) ?? false,
      ),
  ];
}

/// Height of a normal segment.
const double _segmentHeight = 6;

/// Height of the current episode's segment.
const double _currentSegmentHeight = 10;

/// Gap between two segments.
const double _segmentGap = 2;

/// Width of the VIP notch under the gate segment.
const double _gateNotchWidth = 10;

/// Height of the VIP notch.
const double _gateNotchHeight = 2;

/// Opacity of a locked segment; the locked grid below repeats the message in
/// words, so the spine only has to dim.
const double _lockedOpacity = 0.45;

/// The full-width, one-segment-per-episode overview bar.
///
/// Every visual difference between states is a **shape or fill** difference as
/// well as a colour difference, so the bar is still readable in greyscale:
/// current is taller, watched is solid, in progress is a partly filled bar,
/// untouched is the faint neutral, and the gate carries a notch underneath.
class EpisodeSpine extends StatelessWidget {
  /// Creates an episode spine.
  const EpisodeSpine({
    required this.ticks,
    this.language = AppLanguage.defaultLanguage,
    this.semanticsLabel,
    super.key,
  });

  /// One segment per episode, in ascending episode order.
  final List<EpisodeTick> ticks;

  /// Language used for the generated semantic description.
  final AppLanguage language;

  /// Overrides the generated semantic description. An empty string drops the
  /// summary node entirely (the caller already announced it in text).
  final String? semanticsLabel;

  /// Builds the summary announced to screen readers, for example
  /// `38 eps, 5 Watched, Current EP 03, 2 in progress`.
  ///
  /// The vocabulary comes entirely from [AppStrings] and [AppUiCopy] so the
  /// description stays localisable; no new user-visible wording is introduced
  /// by this component. The episode count reuses [episodeCountBadgeLabel], the
  /// same `eps` / `ep` pair the poster badge prints, so the two never disagree.
  static String describe(
    List<EpisodeTick> ticks, {
    AppLanguage language = AppLanguage.defaultLanguage,
    String separator = ', ',
  }) {
    if (ticks.isEmpty) {
      return '';
    }
    final AppStrings copy = strings(language);
    final int watched = ticks
        .where((EpisodeTick tick) => tick.state == EpisodeTickState.watched)
        .length;
    final int inProgress = ticks
        .where((EpisodeTick tick) => tick.state == EpisodeTickState.inProgress)
        .length;
    final int locked = ticks.where((EpisodeTick tick) => tick.isVipLocked).length;
    final EpisodeTick? current = ticks
        .cast<EpisodeTick?>()
        .firstWhere(
          (EpisodeTick? tick) => tick?.state == EpisodeTickState.current,
          orElse: () => null,
        );
    final List<String> parts = <String>[
      episodeCountBadgeLabel(ticks.length, language),
      if (watched > 0) '$watched ${copy.playerEpisodeStatusWatched}',
      if (current != null)
        '${copy.playerEpisodeStatusCurrent} '
            '${copy.playerEpisodePrefix}'
            '${current.number.toString().padLeft(2, '0')}',
      if (inProgress > 0) '$inProgress ${AppUiCopy.episodeInProgress}',
      if (locked > 0) '$locked ${AppUiCopy.episodeLocked}',
    ];
    return parts.join(separator);
  }

  @override
  Widget build(BuildContext context) {
    final String description =
        semanticsLabel ?? describe(ticks, language: language);
    final Widget bar = _SpineBar(ticks: ticks);
    if (description.isEmpty) {
      return bar;
    }
    // The bar is a picture of data with no text of its own, so it collapses
    // into a single summary node: there is nothing inside to walk to.
    return Semantics(
      container: true,
      excludeSemantics: true,
      label: description,
      child: bar,
    );
  }
}

/// The segments themselves.
class _SpineBar extends StatelessWidget {
  const _SpineBar({required this.ticks});

  final List<EpisodeTick> ticks;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    if (ticks.isEmpty) {
      return const SizedBox.shrink();
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      spacing: _segmentGap,
      children: <Widget>[
        for (final EpisodeTick tick in ticks)
          Expanded(child: _Segment(tick: tick, colors: colors)),
      ],
    );
  }
}

/// One segment: a rounded bar plus, at the gate, its notch underneath.
class _Segment extends StatelessWidget {
  const _Segment({required this.tick, required this.colors});

  final EpisodeTick tick;
  final AppColors colors;

  @override
  Widget build(BuildContext context) {
    final bool isCurrent = tick.state == EpisodeTickState.current;
    final double height = isCurrent ? _currentSegmentHeight : _segmentHeight;
    // The notch hangs under the bar without changing the row's height: the
    // locked run of segments already tells the story, the notch only marks
    // where it starts.
    return SizedBox(
      height: _currentSegmentHeight + _gateNotchHeight + 1,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        children: <Widget>[
          Opacity(
            opacity: tick.isVipLocked ? _lockedOpacity : 1,
            child: SizedBox(
              height: height,
              width: double.infinity,
              child: Stack(
                fit: StackFit.expand,
                children: <Widget>[
                  DecoratedBox(
                    decoration: BoxDecoration(
                      color: _trackColor(colors, tick),
                      borderRadius: BorderRadius.circular(height / 2),
                    ),
                  ),
                  if (tick.state == EpisodeTickState.inProgress ||
                      tick.state == EpisodeTickState.watched)
                    FractionallySizedBox(
                      alignment: Alignment.centerLeft,
                      widthFactor: tick.state == EpisodeTickState.watched
                          ? 1
                          : tick.clampedProgress / 100,
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          color: colors.accentPrimary,
                          borderRadius: BorderRadius.circular(height / 2),
                        ),
                      ),
                    ),
                  if (isCurrent)
                    DecoratedBox(
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(height / 2),
                        border: Border.all(
                          color: colors.accentPrimary,
                          width: 1.5,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 1),
          SizedBox(
            height: _gateNotchHeight,
            child: tick.isGate
                ? Center(
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: colors.accentWarning,
                        borderRadius: BorderRadius.circular(_gateNotchHeight / 2),
                      ),
                      child: const SizedBox(
                        width: _gateNotchWidth,
                        height: _gateNotchHeight,
                      ),
                    ),
                  )
                : null,
          ),
        ],
      ),
    );
  }

  /// The unfilled track of one segment.
  ///
  /// Untouched episodes use the minimum-contrast neutral rather than the
  /// hairline, because *which* episodes exist is real information: at the
  /// hairline's ~1.3:1 an untouched episode would be invisible. The current
  /// segment uses the accent so its outline reads even when it is also watched.
  static Color _trackColor(AppColors colors, EpisodeTick tick) {
    if (tick.state == EpisodeTickState.current) {
      return colors.accentPrimary.withValues(alpha: 0.22);
    }
    if (tick.state == EpisodeTickState.watched) {
      return colors.outlineStrong;
    }
    return colors.outlineStrong.withValues(alpha: 0.55);
  }
}
