/// Icon-size tokens for the ShortLink design system.
///
/// The screens previously used seven distinct icon sizes (16/18/20/22/24/26/48)
/// written inline, so two rows that should have looked identical did not. The
/// ramp below keeps four steps: an inline glyph, a control glyph, a row/
/// navigation glyph and a hero glyph.
library;

import 'package:flutter/material.dart';

/// Small inline glyph: chips, inline loading, dense trailing icons.
const double _sm = 16;

/// Control glyph: list-row leading icons, buttons, message bar.
const double _md = 20;

/// Navigation and header glyph: app-bar actions, tab glyphs, rail actions.
const double _lg = 24;

/// Hero glyph: empty-state plates, large call-to-action affordances.
const double _hero = 48;

/// The design system's icon sizes.
///
/// Brightness-independent, like the shape tokens: an icon must not change size
/// when the theme changes.
@immutable
class AppIconSize extends ThemeExtension<AppIconSize> {
  /// Creates the icon-size token set.
  const AppIconSize({
    required this.sm,
    required this.md,
    required this.lg,
    required this.hero,
  });

  /// The single icon-size token set.
  static const AppIconSize standard = AppIconSize(
    sm: _sm,
    md: _md,
    lg: _lg,
    hero: _hero,
  );

  /// Light-mode icon sizes. Identical to [standard].
  static const AppIconSize light = standard;

  /// Dark-mode icon sizes. Identical to [standard].
  static const AppIconSize dark = standard;

  /// Small inline glyph (16 dp).
  final double sm;

  /// Control glyph (20 dp).
  final double md;

  /// Navigation/header glyph (24 dp).
  final double lg;

  /// Hero glyph (48 dp).
  final double hero;

  @override
  AppIconSize copyWith({double? sm, double? md, double? lg, double? hero}) =>
      AppIconSize(
        sm: sm ?? this.sm,
        md: md ?? this.md,
        lg: lg ?? this.lg,
        hero: hero ?? this.hero,
      );

  @override
  AppIconSize lerp(covariant AppIconSize? other, double t) {
    if (other == null) {
      return this;
    }
    return AppIconSize(
      sm: _lerpDouble(sm, other.sm, t),
      md: _lerpDouble(md, other.md, t),
      lg: _lerpDouble(lg, other.lg, t),
      hero: _lerpDouble(hero, other.hero, t),
    );
  }

  static double _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}

/// Reads the [AppIconSize] tokens from [context], falling back to
/// [AppIconSize.standard] when no theme extension is present.
AppIconSize appIconSizeOf(BuildContext context) =>
    Theme.of(context).extension<AppIconSize>() ?? AppIconSize.standard;
