/// Media-overlay colour tokens for the ShortLink design system.
///
/// The player, the episode drawer and every chrome element that sits **on top
/// of artwork or video** cannot use the neutral `AppColors` ramp: those roles
/// are tuned against `canvas`/`surface`, and a light-mode `textPrimary`
/// (#14161B) painted over a dark video scrim is unreadable. Before this
/// extension the player carried 40+ raw `Color(0x..)`/`Colors.white*` literals,
/// which is exactly the defect this file removes.
///
/// Every role here is **brightness-independent**: media chrome is always dark
/// glass with white text, because the surface underneath is the video, not the
/// page. The one exception is text that sits on a theme-coloured plane, which
/// belongs to `AppColors` instead.
library;

import 'package:flutter/material.dart';

/// Alpha of the secondary on-media foreground (70 %).
const double _onMediaSecondaryAlpha = 0.70;

/// Alpha of the standard glass fill (72 %).
const double _glassAlpha = 0.72;

/// Alpha of the strong glass fill used by sheets over video (88 %).
const double _glassStrongAlpha = 0.88;

/// Alpha of the danger glass fill (90 %).
const double _glassDangerAlpha = 0.90;

/// Alpha of the decorative divider over media (24 %).
const double _dividerAlpha = 0.24;

/// Alpha of the media scrim (45 %).
const double _scrimAlpha = 0.45;

/// Base colour of the glass fills.
const Color _glassBase = Color(0xFF101219);

/// Base colour of the danger glass fill.
const Color _glassDangerBase = Color(0xFF3A1417);

/// Idle episode-drawer cell background.
const Color _cellIdle = Color(0xFF1B202B);

/// Selected/current episode-drawer cell background.
const Color _cellSelected = Color(0xFF2A3040);

/// The design system's media-overlay roles.
@immutable
class AppMedia extends ThemeExtension<AppMedia> {
  /// Creates the media token set.
  const AppMedia({
    required this.onMedia,
    required this.onMediaSecondary,
    required this.glass,
    required this.glassStrong,
    required this.glassDanger,
    required this.divider,
    required this.scrim,
    required this.cellIdle,
    required this.cellSelected,
    required this.gradientBase,
  });

  /// The single media token set. Media chrome does not vary by brightness.
  static const AppMedia standard = AppMedia(
    onMedia: Color(0xFFFFFFFF),
    onMediaSecondary: Color(0xB3FFFFFF),
    glass: Color(0xB8101219),
    glassStrong: Color(0xE0101219),
    glassDanger: Color(0xE63A1417),
    divider: Color(0x3DFFFFFF),
    scrim: Color(0x73000000),
    cellIdle: _cellIdle,
    cellSelected: _cellSelected,
    gradientBase: Color(0xFF000000),
  );

  /// Light-mode media tokens. Identical to [standard].
  static const AppMedia light = standard;

  /// Dark-mode media tokens. Identical to [standard].
  static const AppMedia dark = standard;

  /// Primary text and icons painted over artwork or video.
  final Color onMedia;

  /// Supporting text over artwork (70 % white).
  final Color onMediaSecondary;

  /// Standard translucent chrome fill (pills, buffering card, reward banner).
  final Color glass;

  /// Opaque-enough chrome fill for surfaces that carry body copy or inputs.
  final Color glassStrong;

  /// Chrome fill for error/banner states over media.
  final Color glassDanger;

  /// Decorative separator over media. Like `AppColors.hairline` it must stay
  /// below 3:1 and is never a control boundary.
  final Color divider;

  /// Dimming layer between the video and overlay chrome.
  final Color scrim;

  /// Idle episode-drawer cell background.
  final Color cellIdle;

  /// Current/selected episode-drawer cell background.
  final Color cellSelected;

  /// Base colour of the bottom copy gradient.
  final Color gradientBase;

  /// Gradient under the player's bottom copy: transparent at the top, 90 %
  /// opaque at the bottom, so titles stay legible on any frame while the video
  /// is not covered above the copy block.
  LinearGradient get bottomCopyGradient => LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: <Color>[
      gradientBase.withValues(alpha: 0),
      gradientBase.withValues(alpha: 0.55),
      gradientBase.withValues(alpha: 0.92),
    ],
    stops: const <double>[0, 0.55, 1],
  );

  /// Alpha applied to the media scrim for a modal barrier over video.
  static const double scrimAlpha = _scrimAlpha;

  /// Alpha applied to the glass fill.
  static const double glassAlpha = _glassAlpha;

  /// Alpha applied to the secondary on-media foreground.
  static const double onMediaSecondaryAlpha = _onMediaSecondaryAlpha;

  /// Alpha applied to the strong glass fill.
  static const double glassStrongAlpha = _glassStrongAlpha;

  /// Alpha applied to the danger glass fill.
  static const double glassDangerAlpha = _glassDangerAlpha;

  /// Alpha applied to the decorative divider.
  static const double dividerAlpha = _dividerAlpha;

  /// Base colour behind [glass] and [glassStrong].
  static const Color glassBase = _glassBase;

  /// Base colour behind [glassDanger].
  static const Color glassDangerBase = _glassDangerBase;

  @override
  AppMedia copyWith({
    Color? onMedia,
    Color? onMediaSecondary,
    Color? glass,
    Color? glassStrong,
    Color? glassDanger,
    Color? divider,
    Color? scrim,
    Color? cellIdle,
    Color? cellSelected,
    Color? gradientBase,
  }) => AppMedia(
    onMedia: onMedia ?? this.onMedia,
    onMediaSecondary: onMediaSecondary ?? this.onMediaSecondary,
    glass: glass ?? this.glass,
    glassStrong: glassStrong ?? this.glassStrong,
    glassDanger: glassDanger ?? this.glassDanger,
    divider: divider ?? this.divider,
    scrim: scrim ?? this.scrim,
    cellIdle: cellIdle ?? this.cellIdle,
    cellSelected: cellSelected ?? this.cellSelected,
    gradientBase: gradientBase ?? this.gradientBase,
  );

  @override
  AppMedia lerp(covariant AppMedia? other, double t) {
    if (other == null) {
      return this;
    }
    return AppMedia(
      onMedia: Color.lerp(onMedia, other.onMedia, t)!,
      onMediaSecondary: Color.lerp(
        onMediaSecondary,
        other.onMediaSecondary,
        t,
      )!,
      glass: Color.lerp(glass, other.glass, t)!,
      glassStrong: Color.lerp(glassStrong, other.glassStrong, t)!,
      glassDanger: Color.lerp(glassDanger, other.glassDanger, t)!,
      divider: Color.lerp(divider, other.divider, t)!,
      scrim: Color.lerp(scrim, other.scrim, t)!,
      cellIdle: Color.lerp(cellIdle, other.cellIdle, t)!,
      cellSelected: Color.lerp(cellSelected, other.cellSelected, t)!,
      gradientBase: Color.lerp(gradientBase, other.gradientBase, t)!,
    );
  }
}

/// Reads the [AppMedia] tokens from [context], falling back to
/// [AppMedia.standard] when no theme extension is present.
AppMedia appMediaOf(BuildContext context) =>
    Theme.of(context).extension<AppMedia>() ?? AppMedia.standard;
