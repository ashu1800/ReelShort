/// `ThemeData` assembly for the ShortLink design system.
///
/// [appTheme] is the single entry point: it resolves the token extensions for a
/// [Brightness] and wires every Material component theme the app uses on top of
/// them, so a widget can be dropped into either theme without a brightness
/// check of its own.
library;

import 'package:flutter/material.dart';

import 'app_colors.dart';
import 'app_icons.dart';
import 'app_media.dart';
import 'app_motion.dart';
import 'app_shapes.dart';
import 'app_spacing.dart';
import 'app_typography.dart';

/// The light theme.
ThemeData get lightTheme => appTheme(Brightness.light);

/// The dark theme.
ThemeData get darkTheme => appTheme(Brightness.dark);

/// Builds the complete [ThemeData] for [brightness].
///
/// Both themes are assembled from the same code path so they cannot drift:
/// only [AppColors] differs between them, while typography roles, geometry and
/// motion stay identical.
ThemeData appTheme(Brightness brightness) {
  final bool isLight = brightness == Brightness.light;
  final AppColors colors = isLight ? AppColors.light : AppColors.dark;
  final AppTypography typography = isLight
      ? AppTypography.light
      : AppTypography.dark;
  final AppShapes shapes = isLight ? AppShapes.light : AppShapes.dark;
  final AppMotion motion = isLight ? AppMotion.standard : AppMotion.dark;
  final AppSpacing spacing = AppSpacing.standard;
  final AppIconSize icons = AppIconSize.standard;
  final AppMedia media = AppMedia.standard;
  final ColorScheme scheme = colors.colorScheme;
  final TextTheme textTheme = buildAppTextTheme(brightness);

  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    textTheme: textTheme,
    scaffoldBackgroundColor: colors.canvas,
    canvasColor: colors.canvas,
    cardColor: colors.surface,
    dividerColor: colors.hairline,
    shadowColor: colors.shadow,
    splashColor: colors.accentPrimary.withValues(alpha: 0.10),
    highlightColor: colors.accentPrimary.withValues(alpha: 0.06),
    hoverColor: colors.accentPrimary.withValues(alpha: 0.05),
    focusColor: colors.accentPrimary.withValues(alpha: 0.12),
    splashFactory: InkSparkle.splashFactory,
    visualDensity: VisualDensity.standard,
    materialTapTargetSize: MaterialTapTargetSize.padded,
    applyElevationOverlayColor: false,
    pageTransitionsTheme: const PageTransitionsTheme(
      builders: <TargetPlatform, PageTransitionsBuilder>{
        TargetPlatform.android: ReelChangePageTransitionsBuilder(),
        TargetPlatform.iOS: ReelChangePageTransitionsBuilder(),
        TargetPlatform.fuchsia: ReelChangePageTransitionsBuilder(),
        TargetPlatform.linux: ReelChangePageTransitionsBuilder(),
        TargetPlatform.macOS: ReelChangePageTransitionsBuilder(),
        TargetPlatform.windows: ReelChangePageTransitionsBuilder(),
      },
    ),
    extensions: <ThemeExtension<dynamic>>[
      colors,
      typography,
      shapes,
      motion,
      spacing,
      icons,
      media,
    ],
    appBarTheme: _appBarTheme(colors, shapes, typography, icons),
    bottomSheetTheme: _bottomSheetTheme(colors, shapes, spacing, textTheme),
    snackBarTheme: _snackBarTheme(colors, shapes, textTheme),
    inputDecorationTheme: _inputDecorationTheme(colors, shapes, textTheme),
    filledButtonTheme: FilledButtonThemeData(
      style: _filledButtonStyle(colors, shapes, textTheme),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: _outlinedButtonStyle(colors, shapes, textTheme),
    ),
    textButtonTheme: TextButtonThemeData(
      style: _textButtonStyle(colors, shapes, textTheme),
    ),
    cardTheme: CardThemeData(
      color: colors.surface,
      surfaceTintColor: Colors.transparent,
      shadowColor: colors.shadow,
      elevation: shapes.elevationFlat,
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: shapes.containerRadius,
        side: BorderSide(
          color: colors.hairline,
          width: shapes.strokeHairline,
        ),
      ),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: colors.surface,
      surfaceTintColor: Colors.transparent,
      shadowColor: colors.shadow,
      elevation: shapes.elevationFloating,
      clipBehavior: Clip.antiAlias,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
      barrierColor: colors.scrim.withValues(alpha: 0.55),
      shape: RoundedRectangleBorder(borderRadius: shapes.containerRadius),
      iconColor: colors.textSecondary,
      titleTextStyle: textTheme.headlineSmall,
      contentTextStyle: textTheme.bodyMedium,
      actionsPadding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
      constraints: const BoxConstraints(minWidth: 280, maxWidth: 460),
    ),
    chipTheme: ChipThemeData(
      brightness: brightness,
      backgroundColor: colors.surfaceAlt,
      selectedColor: colors.premiumSurface,
      disabledColor: colors.surfaceAlt,
      deleteIconColor: colors.textSecondary,
      checkmarkColor: colors.accentPrimary,
      surfaceTintColor: Colors.transparent,
      shadowColor: Colors.transparent,
      showCheckmark: true,
      elevation: 0,
      pressElevation: 0,
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      labelPadding: const EdgeInsets.symmetric(horizontal: 4),
      side: BorderSide(color: colors.outlineStrong, width: shapes.strokeHairline),
      shape: RoundedRectangleBorder(borderRadius: shapes.controlRadius),
      labelStyle: textTheme.labelLarge,
      secondaryLabelStyle: textTheme.labelLarge,
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: colors.accentPrimary,
      linearTrackColor: colors.surfaceAlt,
      linearMinHeight: 3,
      circularTrackColor: colors.surfaceAlt,
      circularTrackPadding: EdgeInsets.zero,
      stopIndicatorColor: Colors.transparent,
      stopIndicatorRadius: 0,
      borderRadius: shapes.inlineRadius,
    ),
    dividerTheme: DividerThemeData(
      color: colors.hairline,
      space: shapes.strokeHairline,
      thickness: shapes.strokeHairline,
      radius: shapes.inlineRadius,
    ),
    listTileTheme: ListTileThemeData(
      iconColor: colors.textSecondary,
      textColor: colors.textPrimary,
      selectedColor: colors.accentPrimary,
      selectedTileColor: colors.premiumSurface,
      shape: RoundedRectangleBorder(borderRadius: shapes.controlRadius),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      minVerticalPadding: 10,
    ),
    tooltipTheme: TooltipThemeData(
      decoration: BoxDecoration(
        color: colors.textPrimary,
        borderRadius: shapes.inlineRadius,
      ),
      textStyle: textTheme.labelMedium?.copyWith(color: colors.surface),
      waitDuration: const Duration(milliseconds: 600),
    ),
    textSelectionTheme: TextSelectionThemeData(
      cursorColor: colors.accentPrimary,
      selectionColor: colors.accentPrimary.withValues(alpha: 0.22),
      selectionHandleColor: colors.accentPrimary,
    ),
  );
}

/// The flat, hairline-bordered top bar shared by every screen.
AppBarThemeData _appBarTheme(
  AppColors colors,
  AppShapes shapes,
  AppTypography typography,
  AppIconSize icons,
) => AppBarThemeData(
  backgroundColor: colors.canvas,
  foregroundColor: colors.textPrimary,
  surfaceTintColor: Colors.transparent,
  shadowColor: Colors.transparent,
  elevation: shapes.elevationFlat,
  scrolledUnderElevation: shapes.elevationFlat,
  centerTitle: false,
  titleSpacing: AppSpacing.standard.space4,
  leadingWidth: 56,
  toolbarHeight: 56,
  iconTheme: IconThemeData(color: colors.textPrimary, size: icons.lg),
  actionsIconTheme: IconThemeData(
    color: colors.textSecondary,
    size: icons.lg,
  ),
  // One page-title role for every sub-page; before this the screens answered
  // with displayLarge / titleLarge / display interchangeably.
  titleTextStyle: typography.screenTitle.copyWith(color: colors.textPrimary),
  toolbarTextStyle: typography.body.copyWith(color: colors.textPrimary),
  shape: Border(
    bottom: BorderSide(color: colors.hairline, width: shapes.strokeHairline),
  ),
  actionsPadding: const EdgeInsets.only(right: 8),
);

/// Bottom sheets: rounded top corners, hairline handle, page-coloured barrier.
///
/// The sheet radius is [AppShapes.radiusMedia] (20): the value the design
/// system reserves for full-bleed media surfaces, which a bottom sheet is.
BottomSheetThemeData _bottomSheetTheme(
  AppColors colors,
  AppShapes shapes,
  AppSpacing spacing,
  TextTheme textTheme,
) => BottomSheetThemeData(
  backgroundColor: colors.surface,
  modalBackgroundColor: colors.surface,
  surfaceTintColor: Colors.transparent,
  shadowColor: colors.shadow,
  modalBarrierColor: colors.scrim.withValues(alpha: AppColors.modalScrimAlpha),
  elevation: shapes.elevationFlat,
  modalElevation: shapes.elevationFloating,
  clipBehavior: Clip.antiAlias,
  showDragHandle: true,
  dragHandleColor: colors.hairline,
  dragHandleSize: const Size(36, 4),
  constraints: const BoxConstraints(minWidth: 320, maxWidth: 720),
  shape: RoundedRectangleBorder(
    borderRadius: BorderRadius.vertical(
      top: Radius.circular(shapes.radiusMedia),
    ),
  ),
);

/// Snack bars used for transient background outcomes.
SnackBarThemeData _snackBarTheme(
  AppColors colors,
  AppShapes shapes,
  TextTheme textTheme,
) => SnackBarThemeData(
  backgroundColor: colors.surface,
  contentTextStyle: textTheme.bodyMedium,
  actionTextColor: colors.accentPrimary,
  disabledActionTextColor: colors.textSecondary,
  closeIconColor: colors.textSecondary,
  elevation: shapes.elevationFloating,
  behavior: SnackBarBehavior.floating,
  showCloseIcon: true,
  insetPadding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
  shape: RoundedRectangleBorder(
    borderRadius: shapes.controlRadius,
    side: BorderSide(color: colors.hairline, width: shapes.strokeHairline),
  ),
);

/// Labelled fields: filled with [AppColors.surfaceAlt], hairline border that
/// thickens and turns accent-coloured on focus.
InputDecorationThemeData _inputDecorationTheme(
  AppColors colors,
  AppShapes shapes,
  TextTheme textTheme,
) {
  OutlineInputBorder border(Color color, double width) => OutlineInputBorder(
    borderRadius: shapes.controlRadius,
    borderSide: BorderSide(color: color, width: width),
  );

  return InputDecorationThemeData(
    filled: true,
    fillColor: colors.surfaceAlt,
    isDense: false,
    contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
    border: border(colors.outlineStrong, shapes.strokeHairline),
    enabledBorder: border(colors.outlineStrong, shapes.strokeHairline),
    focusedBorder: border(colors.accentPrimary, shapes.strokeEmphasis),
    errorBorder: border(colors.accentDanger, shapes.strokeHairline),
    focusedErrorBorder: border(colors.accentDanger, shapes.strokeEmphasis),
    disabledBorder: border(colors.hairline, shapes.strokeHairline),
    labelStyle: textTheme.bodyMedium?.copyWith(color: colors.textSecondary),
    floatingLabelStyle: textTheme.labelMedium?.copyWith(
      color: colors.accentPrimary,
    ),
    hintStyle: textTheme.bodyMedium?.copyWith(color: colors.textSecondary),
    helperStyle: textTheme.bodySmall,
    helperMaxLines: 3,
    errorStyle: textTheme.bodySmall?.copyWith(color: colors.accentDanger),
    errorMaxLines: 3,
    prefixIconColor: colors.textSecondary,
    suffixIconColor: colors.textSecondary,
    prefixIconConstraints: const BoxConstraints(minWidth: 44, minHeight: 44),
    suffixIconConstraints: const BoxConstraints(minWidth: 44, minHeight: 44),
    counterStyle: textTheme.bodySmall,
    alignLabelWithHint: true,
  );
}

/// The primary call to action.
ButtonStyle _filledButtonStyle(
  AppColors colors,
  AppShapes shapes,
  TextTheme textTheme,
) {
  final ButtonStyle base = FilledButton.styleFrom(
    backgroundColor: colors.accentPrimary,
    foregroundColor: colors.onAccent,
    disabledBackgroundColor: colors.surfaceAlt,
    disabledForegroundColor: colors.textSecondary,
    elevation: shapes.elevationFlat,
    shadowColor: Colors.transparent,
    surfaceTintColor: Colors.transparent,
    minimumSize: const Size(64, 48),
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
    shape: RoundedRectangleBorder(borderRadius: shapes.controlRadius),
    textStyle: textTheme.labelLarge,
    animationDuration: AppMotion.standard.controlState,
  );
  return base.copyWith(overlayColor: _overlayFor(colors.onAccent));
}

/// The secondary action: hairline outline on the surface plane.
ButtonStyle _outlinedButtonStyle(
  AppColors colors,
  AppShapes shapes,
  TextTheme textTheme,
) {
  final ButtonStyle base = OutlinedButton.styleFrom(
    foregroundColor: colors.textPrimary,
    disabledForegroundColor: colors.textSecondary,
    backgroundColor: Colors.transparent,
    side: BorderSide(color: colors.outlineStrong, width: shapes.strokeHairline),
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
    shape: RoundedRectangleBorder(borderRadius: shapes.controlRadius),
    textStyle: textTheme.labelLarge,
    minimumSize: const Size(64, 48),
    animationDuration: AppMotion.standard.controlState,
  );
  return base.copyWith(overlayColor: _overlayFor(colors.textPrimary));
}

/// The tertiary action: text only, accent coloured.
ButtonStyle _textButtonStyle(
  AppColors colors,
  AppShapes shapes,
  TextTheme textTheme,
) {
  final ButtonStyle base = TextButton.styleFrom(
    foregroundColor: colors.accentPrimary,
    disabledForegroundColor: colors.textSecondary,
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
    shape: RoundedRectangleBorder(borderRadius: shapes.controlRadius),
    textStyle: textTheme.labelLarge,
    minimumSize: const Size(48, 44),
    animationDuration: AppMotion.standard.controlState,
  );
  return base.copyWith(overlayColor: _overlayFor(colors.accentPrimary));
}

/// State overlays derived from [foreground] so a disabled button never paints
/// an overlay, and pressed/hovered states stay inside the palette.
WidgetStateProperty<Color?> _overlayFor(Color foreground) =>
    WidgetStateProperty.resolveWith<Color?>((Set<WidgetState> states) {
      if (states.contains(WidgetState.disabled)) {
        return null;
      }
      if (states.contains(WidgetState.pressed)) {
        return foreground.withValues(alpha: 0.14);
      }
      if (states.contains(WidgetState.hovered) ||
          states.contains(WidgetState.focused)) {
        return foreground.withValues(alpha: 0.08);
      }
      return null;
    });

/// The "reel change" page transition: the incoming frame drops in from just
/// above the viewport, over the route's own duration, while the outgoing frame
/// is pushed up slightly and dimmed. Vertical, short and clipped, so it reads as
/// a film frame change rather than as a platform slide.
///
/// The travel is a fraction of the viewport rather than a fixed pixel count, so
/// the effect is proportionally identical on a 320 px phone and a 1440 px
/// tablet.
class ReelChangePageTransitionsBuilder extends PageTransitionsBuilder {
  /// Creates the reel-change transition builder.
  const ReelChangePageTransitionsBuilder();

  /// Fraction of the viewport height the incoming page travels.
  static const double travelFraction = 0.06;

  /// Fraction of the viewport height the outgoing page travels.
  static const double exitTravelFraction = 0.025;

  @override
  Widget buildTransitions<T>(
    PageRoute<T> route,
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    // A route's own animation is driven by the framework, so the reduced-motion
    // collapse cannot come from shortening a duration here: the transitions
    // themselves have to be dropped. Returning the child directly means the new
    // page appears in place with no travel, fade or clip.
    if (MediaQuery.disableAnimationsOf(context)) {
      return child;
    }
    final Animation<double> incoming = CurvedAnimation(
      parent: animation,
      curve: AppMotion.standard.reelChangeCurve,
      reverseCurve: AppMotion.standard.reelChangeCurve.flipped,
    );
    final Animation<double> outgoing = CurvedAnimation(
      parent: secondaryAnimation,
      curve: AppMotion.standard.reelChangeCurve,
      reverseCurve: AppMotion.standard.reelChangeCurve.flipped,
    );
    return ClipRect(
      child: FadeTransition(
        opacity: Tween<double>(begin: 0.88, end: 1).animate(incoming),
        child: SlideTransition(
          position: Tween<Offset>(
            begin: const Offset(0, -travelFraction),
            end: Offset.zero,
          ).animate(incoming),
          child: SlideTransition(
            position: Tween<Offset>(
              begin: Offset.zero,
              end: const Offset(0, -exitTravelFraction),
            ).animate(outgoing),
            child: child,
          ),
        ),
      ),
    );
  }
}
