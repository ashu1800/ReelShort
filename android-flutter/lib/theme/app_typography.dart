/// Typography tokens for the ShortLink design system.
///
/// The app ships no font files and performs no runtime font downloading, so
/// every role is expressed through the platform's default family and made
/// distinct through weight, size, tracking, case and figure style instead.
///
/// The roles carry the hierarchy:
///  * [AppTypography.screenTitle] - the single page-title role: tab page
///    headers and sub-page app-bar titles;
///  * [AppTypography.displayLarge] - hero figures and hero titles;
///  * [AppTypography.display] - heavy weight, tight tracking, used for all-caps
///    section headers;
///  * [AppTypography.body] - regular weight, comfortable line height, used for
///    every reading paragraph;
///  * [AppTypography.caption] - dense metadata (timestamps, meta rows);
///  * [AppTypography.numeric] / [AppTypography.numericLarge] /
///    [AppTypography.numericHero] - regular weight plus
///    [FontFeature.tabularFigures], used for **all** money, point, count and
///    timer figures so columns of numbers line up digit for digit;
///  * [AppTypography.onMediaBody] - body copy painted on artwork or video,
///    using the media foreground colour rather than the theme's text colour.
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

/// Secondary text colour in light mode. Mirrors `AppColors.textSecondary`.
const Color _lightTextSecondary = Color(0xFF5A6070);

/// Secondary text colour in dark mode. Mirrors `AppColors.textSecondary`.
const Color _darkTextSecondary = Color(0xFF9AA1AE);

/// Foreground colour of copy painted over artwork. Mirrors `AppMedia.onMedia`;
/// media chrome is brightness-independent, so this is one constant.
const Color _onMediaText = Color(0xFFFFFFFF);

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
  /// Creates the role set. Every role is required so a half-specified set
  /// cannot compile.
  const AppTypography({
    required this.screenTitle,
    required this.display,
    required this.displayLarge,
    required this.body,
    required this.caption,
    required this.numeric,
    required this.numericLarge,
    required this.numericHero,
    required this.onMediaBody,
  });

  /// Light-mode roles.
  static const AppTypography light = AppTypography(
    screenTitle: TextStyle(
      color: _lightText,
      fontSize: 22,
      fontWeight: _displayHeavyWeight,
      letterSpacing: -0.4,
      height: _displayHeight,
    ),
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
    caption: TextStyle(
      color: _lightTextSecondary,
      fontSize: 12,
      fontWeight: FontWeight.w500,
      letterSpacing: 0.2,
      height: 1.35,
    ),
    numeric: TextStyle(
      color: _lightText,
      fontSize: 15,
      fontWeight: FontWeight.w600,
      letterSpacing: 0.0,
      height: 1.3,
      fontFeatures: _tabularFigures,
    ),
    numericLarge: TextStyle(
      color: _lightText,
      fontSize: 20,
      fontWeight: FontWeight.w700,
      letterSpacing: -0.2,
      height: 1.2,
      fontFeatures: _tabularFigures,
    ),
    numericHero: TextStyle(
      color: _lightText,
      fontSize: 28,
      fontWeight: _displayHeavyWeight,
      letterSpacing: -0.4,
      height: 1.15,
      fontFeatures: _tabularFigures,
    ),
    onMediaBody: TextStyle(
      color: _onMediaText,
      fontSize: 15,
      fontWeight: _bodyWeight,
      letterSpacing: 0.1,
      height: 1.4,
    ),
  );

  /// Dark-mode roles.
  static const AppTypography dark = AppTypography(
    screenTitle: TextStyle(
      color: _darkText,
      fontSize: 22,
      fontWeight: _displayHeavyWeight,
      letterSpacing: -0.4,
      height: _displayHeight,
    ),
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
    caption: TextStyle(
      color: _darkTextSecondary,
      fontSize: 12,
      fontWeight: FontWeight.w500,
      letterSpacing: 0.2,
      height: 1.35,
    ),
    numeric: TextStyle(
      color: _darkText,
      fontSize: 15,
      fontWeight: FontWeight.w600,
      letterSpacing: 0.0,
      height: 1.3,
      fontFeatures: _tabularFigures,
    ),
    numericLarge: TextStyle(
      color: _darkText,
      fontSize: 20,
      fontWeight: FontWeight.w700,
      letterSpacing: -0.2,
      height: 1.2,
      fontFeatures: _tabularFigures,
    ),
    numericHero: TextStyle(
      color: _darkText,
      fontSize: 28,
      fontWeight: _displayHeavyWeight,
      letterSpacing: -0.4,
      height: 1.15,
      fontFeatures: _tabularFigures,
    ),
    onMediaBody: TextStyle(
      color: _onMediaText,
      fontSize: 15,
      fontWeight: _bodyWeight,
      letterSpacing: 0.1,
      height: 1.4,
    ),
  );

  /// The single page-title role: tab page headers and sub-page app-bar titles.
  final TextStyle screenTitle;

  /// Small display style for all-caps section headers ("CONTINUE WATCHING").
  ///
  /// Callers are expected to pass already-uppercased text; see
  /// [sectionHeaderTransform] for the shared transform.
  final TextStyle display;

  /// Display style for hero titles and hero figures.
  final TextStyle displayLarge;

  /// Body style for paragraphs, list subtitles and hints.
  final TextStyle body;

  /// Dense metadata style: timestamps, meta rows, helper copy under a value.
  final TextStyle caption;

  /// Numeric style. Used for inline points, USDT amounts, counts and dates.
  final TextStyle numeric;

  /// Larger numeric style for panel-level figures.
  final TextStyle numericLarge;

  /// Largest numeric style for the one hero figure on a page (balance, price).
  final TextStyle numericHero;

  /// Body copy painted over artwork or video; the colour comes from the media
  /// tokens, never from [AppColors.textPrimary].
  final TextStyle onMediaBody;

  @override
  AppTypography copyWith({
    TextStyle? screenTitle,
    TextStyle? display,
    TextStyle? displayLarge,
    TextStyle? body,
    TextStyle? caption,
    TextStyle? numeric,
    TextStyle? numericLarge,
    TextStyle? numericHero,
    TextStyle? onMediaBody,
  }) => AppTypography(
    screenTitle: screenTitle ?? this.screenTitle,
    display: display ?? this.display,
    displayLarge: displayLarge ?? this.displayLarge,
    body: body ?? this.body,
    caption: caption ?? this.caption,
    numeric: numeric ?? this.numeric,
    numericLarge: numericLarge ?? this.numericLarge,
    numericHero: numericHero ?? this.numericHero,
    onMediaBody: onMediaBody ?? this.onMediaBody,
  );

  @override
  AppTypography lerp(covariant AppTypography? other, double t) {
    if (other == null) {
      return this;
    }
    return AppTypography(
      screenTitle: TextStyle.lerp(screenTitle, other.screenTitle, t)!,
      display: TextStyle.lerp(display, other.display, t)!,
      displayLarge: TextStyle.lerp(displayLarge, other.displayLarge, t)!,
      body: TextStyle.lerp(body, other.body, t)!,
      caption: TextStyle.lerp(caption, other.caption, t)!,
      numeric: TextStyle.lerp(numeric, other.numeric, t)!,
      numericLarge: TextStyle.lerp(numericLarge, other.numericLarge, t)!,
      numericHero: TextStyle.lerp(numericHero, other.numericHero, t)!,
      onMediaBody: TextStyle.lerp(onMediaBody, other.onMediaBody, t)!,
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
