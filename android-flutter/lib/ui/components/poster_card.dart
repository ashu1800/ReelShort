/// The ShortLink poster card: cover art, gradient-scrimmed title, and a mini
/// episode spine.
///
/// This is the only component in the design system that is allowed to be
/// colourful - the chrome around it is neutral precisely so the artwork carries
/// the palette.
library;

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_language.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import 'episode_spine.dart';

/// Poster aspect ratio, the standard 2:3 drama key art.
const double posterAspectRatio = 2 / 3;

/// Horizontal padding for the overlaid title.
const EdgeInsets _overlayPadding = EdgeInsets.fromLTRB(10, 10, 10, 9);

/// Width reserved on the right of the overlay for the mini spine.
const double _spineLaneWidth = 26;

/// Largest episode count the mini spine will draw. Beyond this the spine would
/// be finer than a single physical pixel on a phone, so it renders a window of
/// the series around the current episode instead of every episode.
const int _maxSpineTicks = 30;

/// Corner radius applied to the fallback plate glyph.
const double _fallbackGlyphSize = 30;

/// A tappable drama poster.
///
/// Accessibility contract:
///  * the whole card is **one merged** [Semantics] node whose label is
///    [posterCardContentDescription], so a screen reader reads
///    "title, 12 episodes" once instead of reading the image, the title and the
///    spine separately;
///  * the cover image is marked decorative through
///    [posterImageIsDecorativeInsideCard], because the merged label already
///    names the drama;
///  * the overlaid title is limited to [posterOverlayTitleMaxLines] lines for
///    the ambient font scale, so a 3.2x scale cannot push the title out of the
///    card.
class PosterCard extends StatelessWidget {
  /// Creates a poster card.
  const PosterCard({
    required this.title,
    required this.coverUrl,
    required this.episodeCount,
    this.language = AppLanguage.defaultLanguage,
    this.currentEpisode,
    this.watchedEpisodeCount = 0,
    this.onTap,
    this.fallbackIcon = Icons.movie_outlined,
    this.showTitleOverlay = true,
    super.key,
  });

  /// Drama title, shown over the scrim and read by the merged semantics node.
  final String title;

  /// Cover URL; null or blank renders the fallback plate.
  final String? coverUrl;

  /// Total episodes, used by the description and by the mini spine.
  final int episodeCount;

  /// Language used for the generated content description.
  final AppLanguage language;

  /// Episode the user is on, emphasised by the mini spine.
  final int? currentEpisode;

  /// How many leading episodes are already watched.
  final int watchedEpisodeCount;

  /// Tap handler; a null handler renders a non-interactive card.
  final VoidCallback? onTap;

  /// Glyph shown on the fallback plate when there is no cover.
  final IconData fallbackIcon;

  /// Whether the title is painted over the artwork.
  ///
  /// Defaults to true, the poster-grid treatment. A list row that already shows
  /// the title beside the card passes false, so the same words are not printed
  /// twice; the merged accessibility name is unaffected either way.
  final bool showTitleOverlay;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final double fontScale = MediaQuery.textScalerOf(context).scale(1.0);
    final int titleMaxLines = posterOverlayTitleMaxLines(fontScale);
    final bool decorativeImage = posterImageIsDecorativeInsideCard();
    final String description = posterCardContentDescription(
      title,
      episodeCount,
      language,
    );
    final String? url = coverUrl.coverUrlOrNull();

    final Widget art = url == null
        ? _FallbackPlate(colors: colors, icon: fallbackIcon)
        : CachedNetworkImage(
            imageUrl: url,
            fit: BoxFit.cover,
            width: double.infinity,
            height: double.infinity,
            fadeInDuration: const Duration(milliseconds: 180),
            placeholder: (BuildContext context, String _) =>
                ColoredBox(color: colors.surfaceAlt),
            errorWidget: (BuildContext context, String _, Object _) =>
                _FallbackPlate(colors: colors, icon: fallbackIcon),
          );

    final Widget card = DecoratedBox(
      decoration: BoxDecoration(
        color: colors.surfaceAlt,
        borderRadius: shapes.containerRadius,
        border: Border.all(
          color: colors.hairline,
          width: shapes.strokeHairline,
        ),
      ),
      child: ClipRRect(
        borderRadius: shapes.containerRadius,
        child: Stack(
          fit: StackFit.expand,
          children: <Widget>[
            if (decorativeImage) ExcludeSemantics(child: art) else art,
            const _Scrim(),
            // The mini spine is offered the card's real inner height so it can
            // scale itself down; a 60-episode series must never overflow the
            // artwork it annotates.
            LayoutBuilder(
              builder: (BuildContext context, BoxConstraints constraints) {
                final double laneHeight =
                    constraints.maxHeight - _overlayPadding.vertical;
                return Padding(
                  padding: _overlayPadding,
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: <Widget>[
                      // The title is optional so a caller that renders it beside
                      // the card does not print it twice; the flex slot stays so
                      // the spine keeps its bottom-right corner.
                      if (showTitleOverlay)
                        Expanded(
                          child: Align(
                            alignment: Alignment.bottomLeft,
                            child: Text(
                              title,
                              maxLines: titleMaxLines,
                              overflow: TextOverflow.ellipsis,
                              style: Theme.of(
                                context,
                              ).textTheme.titleSmall?.copyWith(
                                color: Colors.white,
                              ),
                            ),
                          ),
                        )
                      else
                        const Spacer(),
                      const SizedBox(width: 6),
                      SizedBox(
                        width: _spineLaneWidth,
                        child: Align(
                          alignment: Alignment.bottomRight,
                          child: EpisodeSpine(
                            ticks: _ticks(),
                            size: EpisodeSpineSize.mini,
                            language: language,
                            availableHeight: laneHeight,
                            semanticsLabel: '',
                          ),
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );

    final Widget sized = AspectRatio(
      aspectRatio: posterAspectRatio,
      child: card,
    );

    final Widget interactive = onTap == null
        ? sized
        : Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: onTap,
              borderRadius: shapes.containerRadius,
              child: sized,
            ),
          );

    return Semantics(
      container: true,
      button: onTap != null,
      label: description,
      onTap: onTap,
      excludeSemantics: true,
      child: interactive,
    );
  }

  /// Builds the mini spine's ticks: at most [_maxSpineTicks] of them, windowed
  /// around the episode the user is on when the series is longer than that.
  List<EpisodeTick> _ticks() {
    if (episodeCount <= 0) {
      return const <EpisodeTick>[];
    }
    final int? current = currentEpisode;
    final int first;
    final int last;
    if (episodeCount <= _maxSpineTicks) {
      first = 1;
      last = episodeCount;
    } else {
      final int focus = (current ?? watchedEpisodeCount + 1).clamp(
        1,
        episodeCount,
      );
      first = (focus - _maxSpineTicks ~/ 2).clamp(
        1,
        episodeCount - _maxSpineTicks + 1,
      );
      last = first + _maxSpineTicks - 1;
    }
    return <EpisodeTick>[
      for (int episode = first; episode <= last; episode++)
        EpisodeTick(
          number: episode,
          state: _stateFor(episode, current),
          isVipLocked: episode > episodeSpineGateEpisode,
        ),
    ];
  }

  EpisodeTickState _stateFor(int episode, int? current) {
    if (current != null && episode == current) {
      return EpisodeTickState.current;
    }
    if (episode <= watchedEpisodeCount) {
      return EpisodeTickState.watched;
    }
    return EpisodeTickState.none;
  }
}

/// The bottom-up scrim that keeps the overlaid title legible on any artwork.
///
/// Deliberately not theme-aware in its base colour: posters can be pale, so the
/// scrim must always darken towards the bottom.
class _Scrim extends StatelessWidget {
  const _Scrim();

  @override
  Widget build(BuildContext context) => const DecoratedBox(
    decoration: BoxDecoration(
      gradient: LinearGradient(
        begin: Alignment.bottomCenter,
        end: Alignment.topCenter,
        colors: <Color>[
          Color(0xE6000000),
          Color(0x99000000),
          Color(0x00000000),
        ],
        stops: <double>[0, 0.45, 1],
      ),
    ),
    child: SizedBox.expand(),
  );
}

/// The plate shown when a drama has no cover, or when its cover fails to load.
class _FallbackPlate extends StatelessWidget {
  const _FallbackPlate({required this.colors, required this.icon});

  final AppColors colors;
  final IconData icon;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: <Color>[colors.surfaceAlt, colors.hairline],
      ),
    ),
    child: Center(
      child: Icon(
        icon,
        size: _fallbackGlyphSize,
        color: colors.textSecondary,
      ),
    ),
  );
}
