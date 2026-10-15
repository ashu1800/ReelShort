/// Motion tokens for the ShortLink design system.
///
/// The app has exactly three motion signatures, and nothing else animates:
///  * the **reel change** - a vertical wipe, 220 ms, used for screen and
///    sub-page transitions (the film-reel metaphor: one frame drops away and
///    the next drops in);
///  * the **spring-filled reward ring** - the deterministic progress ring that
///    fills to the settled point total;
///  * the **rising `+N pt` reward toast** - a short upward slide with a fade.
///
/// Every token is exposed as a brightness-independent [ThemeExtension] so the
/// numbers live in one place, and every consumer must run its durations through
/// [AppMotion.resolve] (or [resolveAppMotion]) so that
/// `MediaQuery.of(context).disableAnimations` collapses motion to
/// [Duration.zero] instead of merely shortening it.
library;

import 'package:flutter/material.dart';

/// Duration of the reel-change vertical wipe.
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

/// Curve of the reel-change wipe: fast out, settled in.
const Curve _reelChangeCurve = Cubic(0.22, 1, 0.36, 1);

/// Curve of the reward ring fill: a spring that overshoots slightly and settles.
const Curve _rewardRingCurve = Cubic(0.18, 1.32, 0.4, 1);

/// Curve of the rising reward toast.
const Curve _rewardToastCurve = Curves.easeOutCubic;

/// Curve of control state changes and the skeleton shimmer loop.
const Curve _controlStateCurve = Curves.easeOut;

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
