/// Motion tokens for the ShortLink design system.
///
/// The app has three signature animations, and everything else is chrome:
///  * the **reel change** - a 220 ms fade with a slight vertical travel, used
///    for screen and sub-page transitions (the film-reel metaphor: one frame
///    settles away and the next settles in);
///  * the **spring-filled reward ring** - the deterministic progress ring that
///    fills to the settled point total;
///  * the **rising `+N pt` reward toast** - a short upward slide with a fade.
///
/// Chrome motion exists so overlays do not appear instantly out of nowhere: a
/// bottom sheet, its barrier and a toast each get an entrance and an exit
/// duration from this file rather than a literal in the calling widget.
///
/// Every token is exposed as a brightness-independent [ThemeExtension] so the
/// numbers live in one place, and every consumer must run its durations through
/// [AppMotion.resolve] (or [resolveAppMotion]) so that
/// `MediaQuery.of(context).disableAnimations` collapses motion to
/// [Duration.zero] instead of merely shortening it.
library;

import 'package:flutter/material.dart';

/// Duration of the reel-change transition.
const Duration _reelChangeDuration = Duration(milliseconds: 220);

/// Duration of the reward ring's spring fill.
const Duration _rewardRingFillDuration = Duration(milliseconds: 900);

/// Duration of the rising `+N pt` reward toast, including its exit.
const Duration _rewardToastDuration = Duration(milliseconds: 1600);

/// Duration of the standard control state cross-fade.
const Duration _controlStateDuration = Duration(milliseconds: 120);

/// Duration of the skeleton shimmer cycle.
const Duration _shimmerCycleDuration = Duration(milliseconds: 1400);

/// Duration of the auto-dismiss countdown of the top message bar.
const Duration _messageAutoDismissDuration = Duration(milliseconds: 4000);

/// Duration of a bottom sheet's entrance.
const Duration _sheetEnterDuration = Duration(milliseconds: 260);

/// Duration of a bottom sheet's exit.
const Duration _sheetExitDuration = Duration(milliseconds: 180);

/// Duration of a modal barrier's fade.
const Duration _barrierFadeDuration = Duration(milliseconds: 200);

/// Duration of a toast's entrance.
const Duration _toastEnterDuration = Duration(milliseconds: 200);

/// Duration of a toast's exit.
const Duration _toastExitDuration = Duration(milliseconds: 160);

/// Curve of the reel-change transition: fast out, settled in.
const Curve _reelChangeCurve = Cubic(0.22, 1, 0.36, 1);

/// Curve of the reward ring fill: a spring that overshoots slightly and settles.
const Curve _rewardRingCurve = Cubic(0.18, 1.32, 0.4, 1);

/// Curve of the rising reward toast.
const Curve _rewardToastCurve = Curves.easeOutCubic;

/// Curve of control state changes and the skeleton shimmer loop.
const Curve _controlStateCurve = Curves.easeOut;

/// Curve of a sheet's entrance: decelerating, no overshoot.
const Curve _sheetEnterCurve = Cubic(0.16, 1, 0.3, 1);

/// Curve of a sheet's exit: accelerating away.
const Curve _sheetExitCurve = Curves.easeInCubic;

/// Curve of a toast's entrance.
const Curve _toastEnterCurve = Curves.easeOutCubic;

/// Curve of a toast's exit.
const Curve _toastExitCurve = Curves.easeInCubic;

/// The motion signature tokens, resolved per [Brightness].
///
/// Durations and curves do not depend on brightness, but the extension keeps
/// them addressable from the theme tree alongside the colour, typography and
/// shape tokens, and gives a future platform variant (for example a desktop
/// build with faster transitions) a single retune point.
@immutable
class AppMotion extends ThemeExtension<AppMotion> {
  /// Creates the motion token set.
  const AppMotion({
    required this.reelChange,
    required this.reelChangeCurve,
    required this.rewardRingFill,
    required this.rewardRingCurve,
    required this.rewardToast,
    required this.rewardToastCurve,
    required this.controlState,
    required this.controlStateCurve,
    required this.shimmerCycle,
    required this.messageAutoDismiss,
    required this.sheetEnter,
    required this.sheetEnterCurve,
    required this.sheetExit,
    required this.sheetExitCurve,
    required this.barrierFade,
    required this.toastEnter,
    required this.toastEnterCurve,
    required this.toastExit,
    required this.toastExitCurve,
  });

  /// Light-mode motion tokens.
  static const AppMotion standard = AppMotion(
    reelChange: _reelChangeDuration,
    reelChangeCurve: _reelChangeCurve,
    rewardRingFill: _rewardRingFillDuration,
    rewardRingCurve: _rewardRingCurve,
    rewardToast: _rewardToastDuration,
    rewardToastCurve: _rewardToastCurve,
    controlState: _controlStateDuration,
    controlStateCurve: _controlStateCurve,
    shimmerCycle: _shimmerCycleDuration,
    messageAutoDismiss: _messageAutoDismissDuration,
    sheetEnter: _sheetEnterDuration,
    sheetEnterCurve: _sheetEnterCurve,
    sheetExit: _sheetExitDuration,
    sheetExitCurve: _sheetExitCurve,
    barrierFade: _barrierFadeDuration,
    toastEnter: _toastEnterDuration,
    toastEnterCurve: _toastEnterCurve,
    toastExit: _toastExitDuration,
    toastExitCurve: _toastExitCurve,
  );

  /// Dark-mode motion tokens. Identical to [standard]: motion is theme-stable.
  static const AppMotion dark = standard;

  /// Duration of the reel-change vertical wipe.
  final Duration reelChange;

  /// Curve of the reel-change vertical wipe.
  final Curve reelChangeCurve;

  /// Duration of the reward ring's spring fill.
  final Duration rewardRingFill;

  /// Curve of the reward ring's spring fill.
  final Curve rewardRingCurve;

  /// Duration of the rising `+N pt` reward toast.
  final Duration rewardToast;

  /// Curve of the rising `+N pt` reward toast.
  final Curve rewardToastCurve;

  /// Duration of a control's state cross-fade.
  final Duration controlState;

  /// Curve of a control's state cross-fade.
  final Curve controlStateCurve;

  /// Duration of one skeleton shimmer cycle.
  final Duration shimmerCycle;

  /// How long the top message bar stays before auto-dismissing.
  final Duration messageAutoDismiss;

  /// Duration of a bottom sheet's entrance.
  final Duration sheetEnter;

  /// Curve of a bottom sheet's entrance.
  final Curve sheetEnterCurve;

  /// Duration of a bottom sheet's exit.
  final Duration sheetExit;

  /// Curve of a bottom sheet's exit.
  final Curve sheetExitCurve;

  /// Duration of a modal barrier's fade.
  final Duration barrierFade;

  /// Duration of a toast's entrance.
  final Duration toastEnter;

  /// Curve of a toast's entrance.
  final Curve toastEnterCurve;

  /// Duration of a toast's exit.
  final Duration toastExit;

  /// Curve of a toast's exit.
  final Curve toastExitCurve;

  /// Collapses [duration] to [Duration.zero] when the platform asks for reduced
  /// motion, and returns it unchanged otherwise.
  ///
  /// Reduced motion is read from [MediaQuery.disableAnimationsOf], which is the
  /// same flag the accessibility "remove animations" setting sets on Android and
  /// iOS, and which widget tests can force through `MediaQueryData`.
  static Duration resolve(BuildContext context, Duration duration) =>
      MediaQuery.disableAnimationsOf(context) ? Duration.zero : duration;

  @override
  AppMotion copyWith({
    Duration? reelChange,
    Curve? reelChangeCurve,
    Duration? rewardRingFill,
    Curve? rewardRingCurve,
    Duration? rewardToast,
    Curve? rewardToastCurve,
    Duration? controlState,
    Curve? controlStateCurve,
    Duration? shimmerCycle,
    Duration? messageAutoDismiss,
    Duration? sheetEnter,
    Curve? sheetEnterCurve,
    Duration? sheetExit,
    Curve? sheetExitCurve,
    Duration? barrierFade,
    Duration? toastEnter,
    Curve? toastEnterCurve,
    Duration? toastExit,
    Curve? toastExitCurve,
  }) => AppMotion(
    reelChange: reelChange ?? this.reelChange,
    reelChangeCurve: reelChangeCurve ?? this.reelChangeCurve,
    rewardRingFill: rewardRingFill ?? this.rewardRingFill,
    rewardRingCurve: rewardRingCurve ?? this.rewardRingCurve,
    rewardToast: rewardToast ?? this.rewardToast,
    rewardToastCurve: rewardToastCurve ?? this.rewardToastCurve,
    controlState: controlState ?? this.controlState,
    controlStateCurve: controlStateCurve ?? this.controlStateCurve,
    shimmerCycle: shimmerCycle ?? this.shimmerCycle,
    messageAutoDismiss: messageAutoDismiss ?? this.messageAutoDismiss,
    sheetEnter: sheetEnter ?? this.sheetEnter,
    sheetEnterCurve: sheetEnterCurve ?? this.sheetEnterCurve,
    sheetExit: sheetExit ?? this.sheetExit,
    sheetExitCurve: sheetExitCurve ?? this.sheetExitCurve,
    barrierFade: barrierFade ?? this.barrierFade,
    toastEnter: toastEnter ?? this.toastEnter,
    toastEnterCurve: toastEnterCurve ?? this.toastEnterCurve,
    toastExit: toastExit ?? this.toastExit,
    toastExitCurve: toastExitCurve ?? this.toastExitCurve,
  );

  @override
  AppMotion lerp(covariant AppMotion? other, double t) {
    if (other == null || t < 0.5) {
      return this;
    }
    return other;
  }
}

/// Reads the [AppMotion] tokens from [context], falling back to
/// [AppMotion.standard] when no theme extension is present.
AppMotion appMotionOf(BuildContext context) =>
    Theme.of(context).extension<AppMotion>() ?? AppMotion.standard;

/// Collapses [duration] to [Duration.zero] when the platform disables
/// animations.
///
/// This is the helper that every animated widget in the design system calls
/// before handing a duration to an [AnimationController] or an implicit
/// animation widget; `app_motion.dart` never exposes a raw duration that a
/// caller is expected to use directly.
Duration resolveAppMotion(BuildContext context, Duration duration) =>
    AppMotion.resolve(context, duration);
