/// Shape tokens - corner radii, stroke widths and elevation - for the
/// ShortLink design system.
///
/// The system is intentionally **sparser and flatter** than Material's
/// defaults: three radii, two stroke weights, and an elevation scale whose top
/// step is a whisper rather than a drop shadow. Cards separate from the canvas
/// through a hairline border and a surface change, not through shadow.
library;

import 'package:flutter/material.dart';

/// Corner radius for chips, ticks and other inline micro elements.
const double _radiusInline = 4;

/// Corner radius for buttons, fields and every tappable control.
const double _radiusControl = 10;

/// Corner radius for cards, sheets and dialogs.
const double _radiusContainer = 16;

/// Corner radius for the full-bleed player surface and immersive media.
const double _radiusMedia = 20;

/// Stroke width of the hairline borders that replace Material dividers.
const double _strokeHairline = 1;

/// Stroke width of emphasised borders (focused field, selected chip).
const double _strokeEmphasis = 1.5;

/// Elevation for content resting directly on the canvas.
const double _elevationFlat = 0;

/// Elevation for sticky chrome such as app bars and navigation bars.
const double _elevationSticky = 1;

/// Elevation for content floating above the page (sheets, dialogs, menus).
const double _elevationFloating = 6;

/// Corner-radius and elevation tokens, resolved per [Brightness].
///
/// Radii and stroke widths are brightness-independent by design - geometry must
/// not shift when the user switches theme - but the values stay on a
/// [ThemeExtension] so a future density variant can retune them per platform
/// without touching call sites.
@immutable
class AppShapes extends ThemeExtension<AppShapes> {
  /// Creates the shape token set.
  const AppShapes({
    required this.radiusInline,
    required this.radiusControl,
    required this.radiusContainer,
    required this.radiusMedia,
    required this.strokeHairline,
    required this.strokeEmphasis,
    required this.elevationFlat,
    required this.elevationSticky,
    required this.elevationFloating,
  });

  /// Light-mode shape tokens.
  static const AppShapes light = AppShapes(
    radiusInline: _radiusInline,
    radiusControl: _radiusControl,
    radiusContainer: _radiusContainer,
    radiusMedia: _radiusMedia,
    strokeHairline: _strokeHairline,
    strokeEmphasis: _strokeEmphasis,
    elevationFlat: _elevationFlat,
    elevationSticky: _elevationSticky,
    elevationFloating: _elevationFloating,
  );

  /// Dark-mode shape tokens. Radii match [light] so geometry is theme-stable.
  static const AppShapes dark = light;

  /// Corner radius for inline micro elements.
  final double radiusInline;

  /// Corner radius for controls.
  final double radiusControl;

  /// Corner radius for containers.
  final double radiusContainer;

  /// Corner radius for immersive media.
  final double radiusMedia;

  /// Hairline stroke width.
  final double strokeHairline;

  /// Emphasised stroke width.
  final double strokeEmphasis;

  /// Elevation for flat content.
  final double elevationFlat;

  /// Elevation for sticky chrome.
  final double elevationSticky;

  /// Elevation for floating content.
  final double elevationFloating;

  /// [BorderRadius] for inline micro elements.
  BorderRadius get inlineRadius => BorderRadius.circular(radiusInline);

  /// [BorderRadius] for controls.
  BorderRadius get controlRadius => BorderRadius.circular(radiusControl);

  /// [BorderRadius] for containers.
  BorderRadius get containerRadius => BorderRadius.circular(radiusContainer);

  /// [BorderRadius] for immersive media.
  BorderRadius get mediaRadius => BorderRadius.circular(radiusMedia);

  /// The shape used by cards, sheets and dialogs.
  RoundedRectangleBorder containerBorder({Color? sideColor}) =>
      RoundedRectangleBorder(
        borderRadius: containerRadius,
        side: sideColor == null
            ? BorderSide.none
            : BorderSide(color: sideColor, width: strokeHairline),
      );

  @override
  AppShapes copyWith({
    double? radiusInline,
    double? radiusControl,
    double? radiusContainer,
    double? radiusMedia,
    double? strokeHairline,
    double? strokeEmphasis,
    double? elevationFlat,
    double? elevationSticky,
    double? elevationFloating,
  }) => AppShapes(
    radiusInline: radiusInline ?? this.radiusInline,
    radiusControl: radiusControl ?? this.radiusControl,
    radiusContainer: radiusContainer ?? this.radiusContainer,
    radiusMedia: radiusMedia ?? this.radiusMedia,
    strokeHairline: strokeHairline ?? this.strokeHairline,
    strokeEmphasis: strokeEmphasis ?? this.strokeEmphasis,
    elevationFlat: elevationFlat ?? this.elevationFlat,
    elevationSticky: elevationSticky ?? this.elevationSticky,
    elevationFloating: elevationFloating ?? this.elevationFloating,
  );

  @override
  AppShapes lerp(covariant AppShapes? other, double t) {
    if (other == null) {
      return this;
    }
    return AppShapes(
      radiusInline: _lerpDouble(radiusInline, other.radiusInline, t),
      radiusControl: _lerpDouble(radiusControl, other.radiusControl, t),
      radiusContainer: _lerpDouble(radiusContainer, other.radiusContainer, t),
      radiusMedia: _lerpDouble(radiusMedia, other.radiusMedia, t),
      strokeHairline: _lerpDouble(strokeHairline, other.strokeHairline, t),
      strokeEmphasis: _lerpDouble(strokeEmphasis, other.strokeEmphasis, t),
      elevationFlat: _lerpDouble(elevationFlat, other.elevationFlat, t),
      elevationSticky: _lerpDouble(elevationSticky, other.elevationSticky, t),
      elevationFloating: _lerpDouble(
        elevationFloating,
        other.elevationFloating,
        t,
      ),
    );
  }

  static double _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}
