/// The ShortLink poster card: cover art, a gradient-scrimmed title and an
/// episode-count badge.
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

/// Poster aspect ratio, the standard 2:3 drama key art.
const double posterAspectRatio = 2 / 3;

/// Horizontal padding for the overlaid title.
const EdgeInsets _overlayPadding = EdgeInsets.fromLTRB(10, 10, 10, 9);

/// Corner radius applied to the fallback plate glyph.
const double _fallbackGlyphSize = 30;

/// A tappable drama poster.
///
/// Accessibility contract:
///  * the whole card is **one merged** [Semantics] node whose label is
///    [posterCardContentDescription], so a screen reader reads
///    "title, 12 episodes" once instead of reading the image, the title and the
///    count separately;
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
    this.showEpisodeBadge = true,
    super.key,
  });

  /// Drama title, shown over the scrim and read by the merged semantics node.
  final String title;

  /// Cover URL; null or blank renders the fallback plate.
  final String? coverUrl;

  /// Total episodes, used by the description and by the episode badge.
  final int episodeCount;

  /// Language used for the generated content description and the badge.
  final AppLanguage language;

  /// Episode the user is on. Kept for the merged description and for callers
  /// that surface progress themselves; the badge itself only reports the total.
  final int? currentEpisode;

  /// How many leading episodes are already watched. Same note as
  /// [currentEpisode]: the badge reports the total, not the progress.
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

  /// Whether the episode-count badge is painted on the poster.
  ///
  /// Defaults to true. A row that prints the episode count beside the card
  /// (the search result row) passes false so the same number is not shown
  /// twice in one row.
  final bool showEpisodeBadge;

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
            // Bottom-left title, top-right episode count. The two never share a
            // line, so a long title cannot squeeze the badge and the badge never
            // sits on top of artwork the title needs.
            Padding(
              padding: _overlayPadding,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Align(
                    alignment: Alignment.topRight,
                    child: showEpisodeBadge
                        ? _EpisodeBadge(
                            count: episodeCount,
                            language: language,
                          )
                        : const SizedBox.shrink(),
                  ),
                  const Spacer(),
                  if (showTitleOverlay)
                    Text(
                      title,
                      maxLines: titleMaxLines,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        color: Colors.white,
                      ),
                    ),
                ],
              ),
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
}

/// The episode-count badge in the poster's top-right corner.
///
/// A near-opaque pill rather than bare text or a light tint: covers are
/// arbitrary artwork, and the top-right corner is exactly where key-art titles
/// sit. At the previous 70% black the artwork's own lettering showed through and
/// collided with the count; 88% reads as a deliberate label on a pale sky, a
/// bright golden frame and a black one alike.
/// It is decorative for accessibility - the merged card label already ends with
/// the same count.
class _EpisodeBadge extends StatelessWidget {
  const _EpisodeBadge({required this.count, required this.language});

  final int count;
  final AppLanguage language;

  @override
  Widget build(BuildContext context) {
    if (count <= 0) {
      return const SizedBox.shrink();
    }
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final double fontScale = MediaQuery.textScalerOf(context).scale(1.0);

    return ExcludeSemantics(
      child: ConstrainedBox(
        // Never wider than the card can afford: at a large text scale the badge
        // shrinks instead of pushing the title out of the poster.
        constraints: BoxConstraints(maxWidth: fontScale > 1.6 ? 120 : 96),
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: const Color(0xE0000000),
            borderRadius: BorderRadius.circular(shapes.radiusPill),
            border: Border.all(color: const Color(0x33FFFFFF)),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            // The badge is a compact tag on the artwork, not body copy, so it
            // does not follow the system font scale: at 3.2x a scaled label
            // would ellipsise to "38 e…" and defeat its own purpose. The count
            // is still announced in full by the card's merged semantics label.
            child: MediaQuery.withNoTextScaling(
              child: Text(
                episodeCountBadgeLabel(count, language),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: Colors.white,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
        ),
      ),
    );
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
