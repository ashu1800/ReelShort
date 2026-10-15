/// Typography tokens for the ShortLink design system.
///
/// The app ships no font files and performs no runtime font downloading, so
/// every role is expressed through the platform's default family and made
/// distinct through weight, size, tracking, case and figure style instead.
///
/// Three roles carry the hierarchy:
///  * [AppTypography.display] - heavy weight, tight tracking, used for all-caps
///    section headers and screen titles;
///  * [AppTypography.body] - regular weight, comfortable line height, used for
///    every reading paragraph;
///  * [AppTypography.numeric] - regular weight plus
///    [FontFeature.tabularFigures], used for **all** money, point and count
///    figures so columns of numbers line up digit for digit.
library;

import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Text colour used by the light-mode roles. Mirrors the `textPrimary` token of
/// the light palette in `app_colors.dart`; duplicated as a top-level constant
/// because a const constructor cannot read a field off another const object.
/// [buildAppTextTheme] re-applies the live token so the two can never actually
/// diverge on screen.
const Color _lightText = Color(0xFF14161B);

/// Text colour used by the dark-mode roles. Mirrors the `textPrimary` token of
/// the dark palette in `app_colors.dart`.
const Color _darkText = Color(0xFFF4F5F7);

/// Weight used by every display-role style.
const FontWeight _displayWeight = FontWeight.w700;

/// Extra-heavy display weight for the largest screen titles.
const FontWeight _displayHeavyWeight = FontWeight.w800;

/// Weight used by every body-role style.
const FontWeight _bodyWeight = FontWeight.w400;

/// Comfortable reading line height for body copy.
const double _bodyHeight = 1.5;

/// Tighter line height for display copy, which is short and stacked.
const double _displayHeight = 1.2;

/// Tabular figures: equal advance width for every digit, so money and point
/// columns align.
const List<FontFeature> _tabularFigures = <FontFeature>[
  FontFeature.tabularFigures(),
];

/// The three text-style roles, resolved per [Brightness].
///
/// Registered as a [ThemeExtension] by `appTheme`; the full Material
/// [TextTheme] is derived from these roles in `app_typography.dart`.
@immutable
class AppTypography extends ThemeExtension<AppTypography> {
  /// Creates the role set. All three roles are required.
  const AppTypography({
    required this.display,
    required this.displayLarge,
    required this.body,
    required this.numeric,
  });

  /// Light-mode roles.
  static const AppTypography light = AppTypography(
    display: TextStyle(
      color: _lightText,
      fontSize: 13,
      fontWeight: _displayWeight,
      letterSpacing: 1.0,
      height: _displayHeight,
    ),
    displayLarge: TextStyle(
      color: _lightText,
      fontSize: 26,
      fontWeight: _displayHeavyWeight,
      letterSpacing: -0.6,
      height: _displayHeight,
    ),
    body: TextStyle(
      color: _lightText,
      fontSize: 15,
      fontWeight: _bodyWeight,
      letterSpacing: 0.1,
      height: _bodyHeight,
    ),
    numeric: TextStyle(
      color: _lightText,
      fontSize: 15,
      fontWeight: FontWeight.w600,
      letterSpacing: 0.0,
      height: 1.3,
      fontFeatures: _tabularFigures,
    ),
  );

  /// Dark-mode roles.
  static const AppTypography dark = AppTypography(
    display: TextStyle(
      color: _darkText,
      fontSize: 13,
      fontWeight: _displayWeight,
      letterSpacing: 1.0,
      height: _displayHeight,
    ),
    displayLarge: TextStyle(
      color: _darkText,
      fontSize: 26,
      fontWeight: _displayHeavyWeight,
      letterSpacing: -0.6,
      height: _displayHeight,
    ),
    body: TextStyle(
      color: _darkText,
      fontSize: 15,
      fontWeight: _bodyWeight,
      letterSpacing: 0.1,
      height: _bodyHeight,
    ),
    numeric: TextStyle(
      color: _darkText,
      fontSize: 15,
      fontWeight: FontWeight.w600,
      letterSpacing: 0.0,
      height: 1.3,
      fontFeatures: _tabularFigures,
    ),
  );

  /// Small display style for all-caps section headers ("CONTINUE WATCHING").
  ///
  /// Callers are expected to pass already-uppercased text; see
  /// [sectionHeaderTransform] for the shared transform.
  final TextStyle display;

  /// Display style for screen titles and hero numbers.
  final TextStyle displayLarge;

  /// Body style for paragraphs, list subtitles and hints.
  final TextStyle body;

  /// Numeric style. Always used for points, USDT amounts, counts and dates.
  final TextStyle numeric;

  @override
  AppTypography copyWith({
    TextStyle? display,
    TextStyle? displayLarge,
    TextStyle? body,
    TextStyle? numeric,
  }) => AppTypography(
    display: display ?? this.display,
    displayLarge: displayLarge ?? this.displayLarge,
    body: body ?? this.body,
    numeric: numeric ?? this.numeric,
  );

  @override
  AppTypography lerp(covariant AppTypography? other, double t) {
    if (other == null) {
      return this;
    }
    return AppTypography(
      display: TextStyle.lerp(display, other.display, t)!,
      displayLarge: TextStyle.lerp(displayLarge, other.displayLarge, t)!,
      body: TextStyle.lerp(body, other.body, t)!,
      numeric: TextStyle.lerp(numeric, other.numeric, t)!,
    );
  }
}

/// Uppercases section-header copy so the display role reads as a label rather
/// than as a sentence.
String sectionHeaderTransform(String text) => text.toUpperCase();

/// Builds the Material [TextTheme] for one brightness from the three roles.
///
/// `ThemeData` merges this over the platform default text theme, so any role
/// left null keeps Flutter's own sizing while the roles the design system owns
/// are fully replaced.
TextTheme buildAppTextTheme(Brightness brightness) {
  final AppColors colors = brightness == Brightness.light
      ? AppColors.light
      : AppColors.dark;
  final AppTypography roles = brightness == Brightness.light
      ? AppTypography.light
      : AppTypography.dark;

  final TextStyle display = roles.display.copyWith(color: colors.textPrimary);
  final TextStyle displayLarge = roles.displayLarge.copyWith(
    color: colors.textPrimary,
  );
  final TextStyle body = roles.body.copyWith(color: colors.textPrimary);
  final TextStyle bodySmall = roles.body.copyWith(
    color: colors.textSecondary,
    fontSize: 13,
    height: 1.4,
  );

  return TextTheme(
    displayLarge: displayLarge,
    displayMedium: displayLarge.copyWith(fontSize: 22),
    displaySmall: displayLarge.copyWith(fontSize: 20),
    headlineLarge: displayLarge.copyWith(fontSize: 24),
    headlineMedium: displayLarge.copyWith(fontSize: 20),
    headlineSmall: displayLarge.copyWith(fontSize: 17),
    titleLarge: display.copyWith(
      fontSize: 16,
      letterSpacing: -0.1,
      height: 1.3,
    ),
    titleMedium: display.copyWith(
      fontSize: 14,
      letterSpacing: -0.05,
      height: 1.3,
    ),
    titleSmall: display.copyWith(fontSize: 13, letterSpacing: 0.2, height: 1.3),
    bodyLarge: body.copyWith(fontSize: 16),
    bodyMedium: body,
    bodySmall: bodySmall,
    labelLarge: display.copyWith(fontSize: 14, letterSpacing: 0.3, height: 1.3),
    labelMedium: display.copyWith(fontSize: 12, letterSpacing: 0.5, height: 1.3),
    labelSmall: display.copyWith(
      fontSize: 11,
      letterSpacing: 0.8,
      height: 1.3,
      color: colors.textSecondary,
    ),
  );
}
