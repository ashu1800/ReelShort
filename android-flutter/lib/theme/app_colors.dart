/// Role-based colour tokens for the ShortLink design system.
///
/// The palette is deliberately a **neutral chrome**: canvas, surface and text
/// tokens are near-greyscale so that the only saturated colour on screen comes
/// from the content posters themselves. Accent roles are reserved for state
/// (primary action, reward, success, warning, danger) instead of decoration.
///
/// Every widget reads these tokens from `Theme.of(context).extension<AppColors>()`
/// and never from a raw hex literal, so a role can be retuned in one place.
library;

import 'package:flutter/material.dart';

/// Alpha applied to [AppColors.accentPrimary] for [AppColors.premiumSurface].
const double _premiumAlphaLight = 0.08;

/// Alpha applied to [AppColors.accentPrimary] for [AppColors.premiumSurface].
const double _premiumAlphaDark = 0.14;

/// The design system's colour roles, resolved per [Brightness].
///
/// Registered as a [ThemeExtension] by `appTheme`; see `docs/design-system.md`
/// for the full token tables and the contrast targets each role must meet.
@immutable
class AppColors extends ThemeExtension<AppColors> {
  /// Creates a colour token set. Every role is required so a half-specified
  /// palette cannot compile.
  const AppColors({
    required this.brightness,
    required this.canvas,
    required this.surface,
    required this.surfaceAlt,
    required this.textPrimary,
    required this.textSecondary,
    required this.hairline,
    required this.outlineStrong,
    required this.accentPrimary,
    required this.accentReward,
    required this.accentSuccess,
    required this.accentWarning,
    required this.accentDanger,
    required this.onAccent,
    required this.onReward,
    required this.onSuccess,
    required this.onWarning,
    required this.onDanger,
    required this.scrim,
    required this.shadow,
    required this.focusRing,
    required this.surfaceDisabled,
    required this.onDisabled,
  });

  /// Light-mode palette: cool paper greys with a violet primary.
  ///
  /// The light accents are deliberately deeper than a typical "bright accent"
  /// ramp. On a near-white canvas a bright teal or amber cannot clear both
  /// requirements at once - 4.5:1 as text on the canvas *and* 4.5:1 for white
  /// text on the accent as a fill - so the light accents are chosen to satisfy
  /// both simultaneously. The dark accents keep the brighter values, where the
  /// canvas does the work instead. See `docs/design-system.md` for the measured
  /// ratios.
  static const AppColors light = AppColors(
    brightness: Brightness.light,
    canvas: Color(0xFFF7F8FA),
    surface: Color(0xFFFFFFFF),
    surfaceAlt: Color(0xFFEEF0F4),
    textPrimary: Color(0xFF14161B),
    textSecondary: Color(0xFF5A6070),
    hairline: Color(0xFFDDE1E8),
    outlineStrong: Color(0xFF7E8492),
    accentPrimary: Color(0xFF6C4DFF),
    accentReward: Color(0xFFC2410C),
    accentSuccess: Color(0xFF00776C),
    accentWarning: Color(0xFF8A6100),
    accentDanger: Color(0xFFC62A44),
    onAccent: Color(0xFFFFFFFF),
    onReward: Color(0xFFFFFFFF),
    onSuccess: Color(0xFFFFFFFF),
    onWarning: Color(0xFFFFFFFF),
    onDanger: Color(0xFFFFFFFF),
    scrim: Color(0xFF0B0D11),
    shadow: Color(0xFF5A6070),
    focusRing: Color(0xFF6C4DFF),
    surfaceDisabled: Color(0xFFE9EBF0),
    onDisabled: Color(0xFF858B98),
  );

  /// Dark-mode palette: blue-black greys with a lifted violet primary.
  static const AppColors dark = AppColors(
    brightness: Brightness.dark,
    canvas: Color(0xFF0B0D11),
    surface: Color(0xFF14171D),
    surfaceAlt: Color(0xFF1C2027),
    textPrimary: Color(0xFFF4F5F7),
    textSecondary: Color(0xFF9AA1AE),
    hairline: Color(0xFF262B33),
    outlineStrong: Color(0xFF727886),
    accentPrimary: Color(0xFF9C8BFF),
    accentReward: Color(0xFFFF8A72),
    accentSuccess: Color(0xFF3AD3C4),
    accentWarning: Color(0xFFFFC24D),
    accentDanger: Color(0xFFFF6B84),
    onAccent: Color(0xFF0B0D11),
    onReward: Color(0xFF0B0D11),
    onSuccess: Color(0xFF0B0D11),
    onWarning: Color(0xFF0B0D11),
    onDanger: Color(0xFF0B0D11),
    scrim: Color(0xFF000000),
    shadow: Color(0xFF000000),
    focusRing: Color(0xFF9C8BFF),
    surfaceDisabled: Color(0xFF20242B),
    onDisabled: Color(0xFF767C88),
  );

  /// Palette this token set was resolved for.
  final Brightness brightness;

  /// The scrollable page background behind every surface.
  final Color canvas;

  /// The default raised content plane (cards, sheets, bars).
  final Color surface;

  /// A recessed plane used for grouped/secondary regions inside [surface].
  final Color surfaceAlt;

  /// Default body and heading text colour.
  final Color textPrimary;

  /// Supporting text: captions, subtitles, meta rows.
  final Color textSecondary;

  /// The 1 px separator colour; replaces Material's default divider grey.
  ///
  /// Decorative only: at 1.3:1 it must never be the sole boundary of a
  /// control or the sole carrier of a state. Use [outlineStrong] for those.
  final Color hairline;

  /// The minimum-contrast neutral for boundaries and indicators that must be
  /// perceivable on their own: control outlines, input borders, and the
  /// un-watched ticks of the episode spine. Meets 3:1 against every neutral
  /// surface in its brightness.
  final Color outlineStrong;

  /// Brand/primary-action accent.
  final Color accentPrimary;

  /// Reward accent, used by the reward ring and the `+N pt` toast.
  final Color accentReward;

  /// Success accent for confirmations and settled rewards.
  final Color accentSuccess;

  /// Warning accent for pending/limited states.
  final Color accentWarning;

  /// Danger accent for destructive actions and failures.
  final Color accentDanger;

  /// Foreground that meets contrast on [accentPrimary].
  final Color onAccent;

  /// Foreground that meets contrast on [accentReward].
  final Color onReward;

  /// Foreground that meets contrast on [accentSuccess].
  final Color onSuccess;

  /// Foreground that meets contrast on [accentWarning].
  final Color onWarning;

  /// Foreground that meets contrast on [accentDanger].
  final Color onDanger;

  /// Barrier colour for modal sheets and dialogs.
  final Color scrim;

  /// Elevation shadow colour.
  final Color shadow;

  /// Keyboard-focus ring colour.
  final Color focusRing;

  /// Fill of a disabled control.
  ///
  /// Deliberately distinct from [surfaceAlt]: a disabled button painted in the
  /// same tone as the panel it sits on disappears (see `AppButton`).
  final Color surfaceDisabled;

  /// Foreground of a disabled control and its disabled label.
  ///
  /// Inactive components are exempt from the WCAG contrast minimum, so this
  /// role is intentionally below the 4.5:1 text target while staying at or
  /// above 3:1 against [surfaceDisabled] so it is still perceivable.
  final Color onDisabled;

  /// Alpha applied to [scrim] for a modal barrier over a page.
  static const double modalScrimAlpha = 0.55;

  /// A tinted plane for membership/premium blocks: [accentPrimary] at 8 % alpha
  /// in light mode and 14 % in dark mode, so the tint carries the same visual
  /// weight against either canvas.
  Color get premiumSurface => accentPrimary.withValues(
    alpha: brightness == Brightness.light
        ? _premiumAlphaLight
        : _premiumAlphaDark,
  );

  /// Alpha applied to artwork that is not available to the current user (a VIP
  /// locked episode or poster).
  static const double lockedOverlayAlpha = 0.58;

  /// Dimming overlay for locked artwork, in the accent colour so the lock reads
  /// as an interactive gate rather than as damage.
  Color get lockedOverlay =>
      accentPrimary.withValues(alpha: lockedOverlayAlpha);

  /// Alpha of the avatar plate tint.
  static const double avatarTintAlpha = 0.14;

  /// Tinted plate behind an initial-based avatar.
  Color get avatarTint => accentPrimary.withValues(alpha: avatarTintAlpha);

  /// The Material [ColorScheme] this token set maps onto.
  ///
  /// Only roles the design system actually owns are derived from tokens; the
  /// remaining Material roles are filled from the same neutral ramp so
  /// unmodified Material widgets stay inside the palette.
  ColorScheme get colorScheme => ColorScheme(
    brightness: brightness,
    primary: accentPrimary,
    onPrimary: onAccent,
    primaryContainer: premiumSurface,
    onPrimaryContainer: textPrimary,
    secondary: accentSuccess,
    onSecondary: onSuccess,
    secondaryContainer: surfaceAlt,
    onSecondaryContainer: textPrimary,
    tertiary: accentReward,
    onTertiary: onReward,
    tertiaryContainer: surfaceAlt,
    onTertiaryContainer: textPrimary,
    error: accentDanger,
    onError: onDanger,
    errorContainer: surfaceAlt,
    onErrorContainer: accentDanger,
    surface: surface,
    onSurface: textPrimary,
    surfaceDim: canvas,
    surfaceBright: surface,
    surfaceContainerLowest: canvas,
    surfaceContainerLow: surface,
    surfaceContainer: surfaceAlt,
    surfaceContainerHigh: surfaceAlt,
    surfaceContainerHighest: hairline,
    onSurfaceVariant: textSecondary,
    outline: outlineStrong,
    outlineVariant: outlineStrong,
    shadow: shadow,
    scrim: scrim,
    inverseSurface: textPrimary,
    onInverseSurface: surface,
    inversePrimary: accentPrimary,
    surfaceTint: Colors.transparent,
  );

  @override
  AppColors copyWith({
    Brightness? brightness,
    Color? canvas,
    Color? surface,
    Color? surfaceAlt,
    Color? textPrimary,
    Color? textSecondary,
    Color? hairline,
    Color? outlineStrong,
    Color? accentPrimary,
    Color? accentReward,
    Color? accentSuccess,
    Color? accentWarning,
    Color? accentDanger,
    Color? onAccent,
    Color? onReward,
    Color? onSuccess,
    Color? onWarning,
    Color? onDanger,
    Color? scrim,
    Color? shadow,
    Color? focusRing,
    Color? surfaceDisabled,
    Color? onDisabled,
  }) => AppColors(
    brightness: brightness ?? this.brightness,
    canvas: canvas ?? this.canvas,
    surface: surface ?? this.surface,
    surfaceAlt: surfaceAlt ?? this.surfaceAlt,
    textPrimary: textPrimary ?? this.textPrimary,
    textSecondary: textSecondary ?? this.textSecondary,
    hairline: hairline ?? this.hairline,
    outlineStrong: outlineStrong ?? this.outlineStrong,
    accentPrimary: accentPrimary ?? this.accentPrimary,
    accentReward: accentReward ?? this.accentReward,
    accentSuccess: accentSuccess ?? this.accentSuccess,
    accentWarning: accentWarning ?? this.accentWarning,
    accentDanger: accentDanger ?? this.accentDanger,
    onAccent: onAccent ?? this.onAccent,
    onReward: onReward ?? this.onReward,
    onSuccess: onSuccess ?? this.onSuccess,
    onWarning: onWarning ?? this.onWarning,
    onDanger: onDanger ?? this.onDanger,
    scrim: scrim ?? this.scrim,
    shadow: shadow ?? this.shadow,
    focusRing: focusRing ?? this.focusRing,
    surfaceDisabled: surfaceDisabled ?? this.surfaceDisabled,
    onDisabled: onDisabled ?? this.onDisabled,
  );

  @override
  AppColors lerp(covariant AppColors? other, double t) {
    if (other == null) {
      return this;
    }
    return AppColors(
      brightness: t < 0.5 ? brightness : other.brightness,
      canvas: Color.lerp(canvas, other.canvas, t)!,
      surface: Color.lerp(surface, other.surface, t)!,
      surfaceAlt: Color.lerp(surfaceAlt, other.surfaceAlt, t)!,
      textPrimary: Color.lerp(textPrimary, other.textPrimary, t)!,
      textSecondary: Color.lerp(textSecondary, other.textSecondary, t)!,
      hairline: Color.lerp(hairline, other.hairline, t)!,
      outlineStrong: Color.lerp(outlineStrong, other.outlineStrong, t)!,
      accentPrimary: Color.lerp(accentPrimary, other.accentPrimary, t)!,
      accentReward: Color.lerp(accentReward, other.accentReward, t)!,
      accentSuccess: Color.lerp(accentSuccess, other.accentSuccess, t)!,
      accentWarning: Color.lerp(accentWarning, other.accentWarning, t)!,
      accentDanger: Color.lerp(accentDanger, other.accentDanger, t)!,
      onAccent: Color.lerp(onAccent, other.onAccent, t)!,
      onReward: Color.lerp(onReward, other.onReward, t)!,
      onSuccess: Color.lerp(onSuccess, other.onSuccess, t)!,
      onWarning: Color.lerp(onWarning, other.onWarning, t)!,
      onDanger: Color.lerp(onDanger, other.onDanger, t)!,
      scrim: Color.lerp(scrim, other.scrim, t)!,
      shadow: Color.lerp(shadow, other.shadow, t)!,
      focusRing: Color.lerp(focusRing, other.focusRing, t)!,
      surfaceDisabled: Color.lerp(
        surfaceDisabled,
        other.surfaceDisabled,
        t,
      )!,
      onDisabled: Color.lerp(onDisabled, other.onDisabled, t)!,
    );
  }
}
