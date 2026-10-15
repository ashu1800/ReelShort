/// The **Episode Spine** - the signature element of the ShortLink design
/// system.
///
/// A thin vertical rail carries one tick per episode: the rail is the series,
/// each tick is one episode, and the tick's fill is that episode's watch state.
/// A gate marker sits at the VIP boundary (episode 8), the current episode is
/// the only tick drawn at full width, watched ticks are solid, in-progress ticks
/// are partially filled, and untouched ticks use the minimum-contrast neutral
/// rather than the decorative hairline. It replaces the old episode grid.
///
/// Three sizes are provided:
///  * [EpisodeSpineSize.full] - the episodes sheet and the player episode
///    drawer, where each tick is a real tap target;
///  * [EpisodeSpineSize.mini] - a non-interactive spine inset into a poster
///    card, scaled down to whatever height the card can spare;
///  * [EpisodeSpineSize.inline] - a horizontal dash run for dense rows, where a
///    vertical rail would not fit.
library;

import 'package:flutter/material.dart';

import '../../core/formatting/app_strings.dart';
import '../../data/models/app_language.dart';
import '../../theme/app_colors.dart';

/// Watch state of one tick. Mirrors `EpisodeWatchStatusType` in
/// `ui_formats.dart`; the enum is re-declared here as a UI-layer enum so the
/// component does not depend on the formatting layer for its own vocabulary.
enum EpisodeTickState {
  /// Never opened.
  none,

  /// The episode the player is on. Draws at full tick width.
  current,

  /// Partially watched; [EpisodeTick.progress] carries the percentage.
  inProgress,

  /// Finished.
  watched,
}

/// One tick of the spine.
@immutable
class EpisodeTick {
  /// Creates a tick.
  const EpisodeTick({
    required this.number,
    required this.state,
    this.progress = 0,
    this.isVipLocked = false,
    this.onTap,
  });

  /// 1-based episode number.
  final int number;

  /// Watch state.
  final EpisodeTickState state;

  /// Percentage watched, `0..100`. Clamped on read.
  final int progress;

  /// Whether the episode sits behind the VIP gate.
  final bool isVipLocked;

  /// Tap handler. A non-null handler makes this tick interactive in
  /// [EpisodeSpineSize.full]; the mini and inline sizes ignore it.
  final VoidCallback? onTap;

  /// [progress] clamped to `0..100`.
  int get clampedProgress => progress.clamp(0, 100);

  /// Builds one tick per episode in `1..count`, all unwatched.
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

/// Which of the three spine sizes to render.
enum EpisodeSpineSize {
  /// Vertical rail with interactive ticks.
  full,

  /// Vertical rail scaled down inside a poster card.
  mini,

  /// Horizontal dash run for dense rows.
  inline,
}

/// Height of one full-size tick row.
const double _fullTickHeight = 16;

/// Height of one mini tick row, before [FittedBox] scaling.
const double _miniTickHeight = 12;

/// Width of the left gutter that holds the rail, so ticks sit to the right of
/// the rail instead of on top of it.
const double _leftGutter = 7;

/// Width of a current or gate tick, which is drawn wider than the rest.
const double _emphasisTickWidth = 16;

/// Rail thickness.
const double _railWidth = 2;

/// Width of the mini rail, which is thinner so it reads as a detail at card
/// size.
const double _miniRailWidth = 1.5;

/// Horizontal gap between inline dashes.
const double _inlineGap = 4;

/// Vertical gap between two inline rows.
const double _inlineRowGap = 4;

/// Width of one inline dash.
const double _inlineDashWidth = 14;

/// Height of one inline dash.
const double _inlineDashHeight = 3;

/// Extra height a VIP gate marker adds to a row.
const double _gateOverhang = 3;

/// The episode at which the VIP paywall starts, as in
/// `defaultVipFreeEpisodes`. Kept as a local constant so the component has no
/// data-layer dependency.
const int episodeSpineGateEpisode = 8;

/// The signature vertical (or horizontal) episode rail.
///
/// Every visual difference between states is a **shape or fill** difference as
/// well as a colour difference, so the spine is still readable in greyscale:
/// current is wider and outlined, watched is solid, in progress is a partly
/// filled bar, untouched is a faint dash, and the gate is wider and taller than
/// any other tick.
class EpisodeSpine extends StatelessWidget {
  /// Creates an episode spine.
  const EpisodeSpine({
    required this.ticks,
    this.size = EpisodeSpineSize.full,
    this.language = AppLanguage.defaultLanguage,
    this.availableHeight,
    this.semanticsLabel,
    super.key,
  });

  /// One tick per episode, in ascending episode order.
  final List<EpisodeTick> ticks;

  /// Which of the three sizes to render.
  final EpisodeSpineSize size;

  /// Language used for the generated semantic description.
  final AppLanguage language;

  /// Height the spine may occupy. [EpisodeSpineSize.mini] scales itself down to
  /// fit; the other sizes ignore it.
  final double? availableHeight;

  /// Overrides the generated semantic description.
  final String? semanticsLabel;

  /// Builds the summary announced to screen readers, for example
  /// `12 eps, 5 Watched, Current ep 03, 2 in progress`.
  ///
  /// The vocabulary comes entirely from [AppStrings] so the description stays
  /// localisable; no new user-visible wording is introduced by this component.
  static String describe(
    List<EpisodeTick> ticks, [
    AppLanguage language = AppLanguage.defaultLanguage,
  ]) {
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
    final EpisodeTick? current = ticks
        .cast<EpisodeTick?>()
        .firstWhere(
          (EpisodeTick? tick) => tick?.state == EpisodeTickState.current,
          orElse: () => null,
        );
    final List<String> parts = <String>[
      '${ticks.length}${copy.playerEpisodeUnit}',
      if (watched > 0) '$watched ${copy.playerEpisodeStatusWatched}',
      if (current != null)
        '${copy.playerEpisodeStatusCurrent} '
            '${copy.playerEpisodePrefix}'
            '${current.number.toString().padLeft(2, '0')}',
      if (inProgress > 0) '$inProgress in progress',
    ];
    return parts.join(', ');
  }

  @override
  Widget build(BuildContext context) {
    final String description = semanticsLabel ?? describe(ticks, language);
    final Widget spine = switch (size) {
      EpisodeSpineSize.full => _FullSpine(ticks: ticks, language: language),
      EpisodeSpineSize.mini => _MiniSpine(
        ticks: ticks,
        availableHeight: availableHeight,
      ),
      EpisodeSpineSize.inline => _InlineSpine(ticks: ticks),
    };
    if (description.isEmpty) {
      return spine;
    }
    // A full-size spine whose ticks are individually tappable must keep them as
    // separate, activatable semantics nodes; only the non-interactive sizes
    // collapse into this single summary node. Either way the summary is
    // announced first.
    final bool ticksAreInteractive =
        size == EpisodeSpineSize.full &&
        ticks.any((EpisodeTick tick) => tick.onTap != null);
    return Semantics(
      container: true,
      explicitChildNodes: ticksAreInteractive,
      excludeSemantics: !ticksAreInteractive,
      label: description,
      child: spine,
    );
  }
}

/// The vertical rail with full-size ticks.
class _FullSpine extends StatelessWidget {
  const _FullSpine({required this.ticks, required this.language});

  final List<EpisodeTick> ticks;
  final AppLanguage language;

  @override
  Widget build(BuildContext context) => Stack(
    children: <Widget>[
      Positioned(
        left: _leftGutter / 2 - _railWidth / 2,
        top: 0,
        bottom: 0,
        child: const _RailSegment(width: _railWidth, height: null),
      ),
      Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          for (final EpisodeTick tick in ticks)
            SizedBox(
              height: _fullTickHeight + _rowGateOverhang(tick),
              child: _TickRow(
                tick: tick,
                language: language,
                interactive: true,
                tickWidth: _emphasisTickWidth,
              ),
            ),
        ],
      ),
    ],
  );
}

/// The rail inset into a poster card. Non-interactive, and scaled down to the
/// height the caller can spare rather than overflowing the card.
class _MiniSpine extends StatelessWidget {
  const _MiniSpine({required this.ticks, this.availableHeight});

  final List<EpisodeTick> ticks;
  final double? availableHeight;

  @override
  Widget build(BuildContext context) {
    if (ticks.isEmpty) {
      return const SizedBox.shrink();
    }
    final double naturalHeight = ticks.fold<double>(0, (
      double sum,
      EpisodeTick tick,
    ) {
      return sum + _miniTickHeight + _rowGateOverhang(tick);
    });
    final Widget rail = Stack(
      children: <Widget>[
        Positioned(
          left: _leftGutter / 2 - _miniRailWidth / 2,
          top: 0,
          bottom: 0,
          child: const _RailSegment(width: _miniRailWidth, height: null),
        ),
        Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            for (final EpisodeTick tick in ticks)
              SizedBox(
                height: _miniTickHeight + _rowGateOverhang(tick),
                child: _TickRow(
                  tick: tick,
                  language: AppLanguage.defaultLanguage,
                  interactive: false,
                  tickWidth: _emphasisTickWidth,
                  mini: true,
                ),
              ),
          ],
        ),
      ],
    );
    final double? cap = availableHeight;
    if (cap == null || cap <= 0 || cap >= naturalHeight) {
      return rail;
    }
    return SizedBox(
      height: cap,
      child: FittedBox(
        fit: BoxFit.scaleDown,
        alignment: Alignment.topCenter,
        child: SizedBox(width: _emphasisTickWidth, child: rail),
      ),
    );
  }
}

/// The horizontal dash run used in dense rows.
class _InlineSpine extends StatelessWidget {
  const _InlineSpine({required this.ticks});

  final List<EpisodeTick> ticks;

  @override
  Widget build(BuildContext context) => Wrap(
    spacing: _inlineGap,
    runSpacing: _inlineRowGap,
    crossAxisAlignment: WrapCrossAlignment.center,
    children: <Widget>[
      for (final EpisodeTick tick in ticks)
        _InlineDash(tick: tick),
    ],
  );
}

/// One horizontal dash of the inline spine.
class _InlineDash extends StatelessWidget {
  const _InlineDash({required this.tick});

  final EpisodeTick tick;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final double width = tick.state == EpisodeTickState.current
        ? _inlineDashWidth * 1.4
        : _inlineDashWidth;
    final double height = tick.state == EpisodeTickState.current
        ? _inlineDashHeight + 2
        : _inlineDashHeight;
    return SizedBox(
      width: width,
      height: height + (tick.number == episodeSpineGateEpisode ? 2 : 0),
      child: Opacity(
        opacity: _tickOpacity(tick),
        child: Stack(
          alignment: Alignment.centerLeft,
          children: <Widget>[
            DecoratedBox(
              decoration: BoxDecoration(
                color: _tickTrack(colors),
                borderRadius: BorderRadius.circular(height / 2),
              ),
              child: SizedBox(width: width, height: height),
            ),
            if (tick.state == EpisodeTickState.inProgress ||
                tick.state == EpisodeTickState.watched)
              FractionallySizedBox(
                widthFactor: tick.state == EpisodeTickState.watched
                    ? 1
                    : tick.clampedProgress / 100,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: _tickFill(colors),
                    borderRadius: BorderRadius.circular(height / 2),
                  ),
                  child: SizedBox(width: width, height: height),
                ),
              ),
            if (tick.number == episodeSpineGateEpisode)
              Positioned(
                left: 0,
                bottom: 0,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: _tickGate(colors),
                    borderRadius: BorderRadius.circular(1),
                  ),
                  child: const SizedBox(width: 3, height: 1.5),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// One vertical tick row: rail segment, tick, fill and optional gate marker.
class _TickRow extends StatelessWidget {
  const _TickRow({
    required this.tick,
    required this.language,
    required this.interactive,
    required this.tickWidth,
    this.mini = false,
  });

  final EpisodeTick tick;
  final AppLanguage language;
  final bool interactive;
  final double tickWidth;
  final bool mini;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final double barHeight = mini ? 2.5 : 4;
    final double emphasisBarHeight = mini ? 3.5 : 5;
    final bool isCurrent = tick.state == EpisodeTickState.current;
    final bool isGate = tick.number == episodeSpineGateEpisode;
    final double width = isCurrent || isGate
        ? tickWidth
        : tickWidth * (mini ? 0.5 : 0.6);
    final double height = isCurrent || isGate ? emphasisBarHeight : barHeight;

    final Widget bar = Opacity(
      opacity: _tickOpacity(tick),
      child: SizedBox(
        width: width,
        height: height,
        child: Stack(
          alignment: Alignment.centerLeft,
          children: <Widget>[
            DecoratedBox(
              decoration: BoxDecoration(
                color: _tickTrack(colors),
                borderRadius: BorderRadius.circular(height / 2),
              ),
              child: SizedBox(width: width, height: height),
            ),
            if (tick.state == EpisodeTickState.inProgress ||
                tick.state == EpisodeTickState.watched)
              FractionallySizedBox(
                widthFactor: tick.state == EpisodeTickState.watched
                    ? 1
                    : tick.clampedProgress / 100,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: _tickFill(colors),
                    borderRadius: BorderRadius.circular(height / 2),
                  ),
                  child: SizedBox(width: width, height: height),
                ),
              ),
            if (isCurrent)
              DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(height / 2),
                  border: Border.all(color: _tickCurrent(colors), width: 1.5),
                ),
                child: SizedBox(width: width, height: height),
              ),
          ],
        ),
      ),
    );

    final Widget row = Padding(
      padding: const EdgeInsets.only(left: _leftGutter),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          bar,
          if (isGate)
            Padding(
              padding: const EdgeInsets.only(top: 1),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: _tickGate(colors),
                  borderRadius: BorderRadius.circular(1),
                ),
                child: SizedBox(width: tickWidth / 2, height: 1.5),
              ),
            ),
        ],
      ),
    );

    if (!interactive || tick.onTap == null) {
      return row;
    }
    final AppStrings copy = strings(language);
    final String stateWord = switch (tick.state) {
      EpisodeTickState.watched => copy.playerEpisodeStatusWatched,
      EpisodeTickState.current => copy.playerEpisodeStatusCurrent,
      EpisodeTickState.inProgress => '${tick.clampedProgress}%',
      EpisodeTickState.none => '',
    };
    final String label =
        '${copy.playerEpisodePrefix}'
        '${tick.number.toString().padLeft(2, '0')}'
        '${stateWord.isEmpty ? '' : ' - $stateWord'}';
    return Semantics(
      container: true,
      button: true,
      label: label,
      // The activation action lives on this node because `excludeSemantics`
      // below replaces the InkWell's own semantics, which would otherwise take
      // the tap action away from screen readers.
      onTap: tick.onTap,
      excludeSemantics: true,
      child: InkWell(
        onTap: tick.onTap,
        canRequestFocus: false,
        customBorder: const StadiumBorder(),
        child: row,
      ),
    );
  }
}

/// The vertical rail itself, drawn behind the ticks.
class _RailSegment extends StatelessWidget {
  const _RailSegment({required this.width, this.height});

  final double width;
  final double? height;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    return SizedBox(
      width: width,
      height: height,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: _railTrack(colors),
          borderRadius: BorderRadius.circular(width / 2),
        ),
        child: SizedBox(width: width, height: height),
      ),
    );
  }
}

/// Extra row height a VIP gate tick needs for its marker.
double _rowGateOverhang(EpisodeTick tick) =>
    tick.number == episodeSpineGateEpisode ? _gateOverhang : 0;

/// The rail's colour. Decorative scaffold only: the rail spans the whole series,
/// so it is never itself a state.
Color _railTrack(AppColors colors) => colors.hairline;

/// The colour of an un-watched tick.
///
/// Uses the minimum-contrast neutral rather than the hairline, because *which*
/// episodes exist is real information: at the hairline's ~1.3:1 an untouched
/// episode would be invisible.
Color _tickTrack(AppColors colors) => colors.outlineStrong;

/// Fill colour for watched and in-progress ticks.
Color _tickFill(AppColors colors) => colors.accentPrimary;

/// Outline colour for the current tick.
Color _tickCurrent(AppColors colors) => colors.accentPrimary;

/// Colour of the VIP gate marker, which also flags the locked run of episodes.
Color _tickGate(AppColors colors) => colors.accentWarning;

/// Opacity applied to a tick, so the locked run of episodes reads as dimmed
/// without changing any state colour.
double _tickOpacity(EpisodeTick tick) => tick.isVipLocked ? 0.7 : 1;
