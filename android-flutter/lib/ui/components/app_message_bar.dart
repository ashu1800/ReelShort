/// The top message host for the ShortLink design system.
///
/// Replaces the old blocking centre dialog: a message appears at the top of the
/// content area, is announced through a live region, and dismisses itself.
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../core/formatting/ui_formats.dart';
import '../../state/app_ui_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_motion.dart';
import '../../theme/app_shapes.dart';

/// Glyph per tone. The glyph plus [toneLabel] carry the meaning; the accent
/// colour only reinforces it.
const Map<MessageVisualTone, IconData> _toneIcons =
    <MessageVisualTone, IconData>{
      MessageVisualTone.success: Icons.check_circle_outline,
      MessageVisualTone.error: Icons.error_outline,
      MessageVisualTone.info: Icons.info_outline,
    };

/// Short text badge per tone, so the state survives greyscale.
const Map<MessageVisualTone, String> _toneLabels = <MessageVisualTone, String>{
  MessageVisualTone.success: 'Success',
  MessageVisualTone.error: 'Error',
  MessageVisualTone.info: 'Info',
};

/// How long the bar stays on screen when no theme token and no override is
/// available. Mirrors [AppMotion.standard]`.messageAutoDismiss`, which is the
/// value the theme supplies in practice.
const Duration messageAutoDismissFallback = Duration(milliseconds: 4000);

/// Vertical travel of the message bar's entrance, in logical pixels.
const double _entranceOffset = 8;

/// Diameter of the leading tone glyph badge.
const double _badgeSize = 26;

/// Height of the auto-dismiss countdown strip.
const double _countdownHeight = 2;

/// Padding inside the bar.
const EdgeInsets _barPadding = EdgeInsets.fromLTRB(12, 10, 8, 10);

/// The top message host.
///
/// Contract:
///  * the tone is derived through [messageVisualTone], never passed as a colour;
///  * the message auto-dismisses after [autoDismiss] (4 s by default) and can be
///    dismissed early by tapping anywhere on the bar;
///  * it is announced through `Semantics(liveRegion: true, ...)`, gated by
///    [topMessageUsesLiveRegion];
///  * it never relies on colour alone - every tone has its own glyph and its own
///    tone word next to the message;
///  * [onDismissed] fires exactly once, whether the timeout or a tap ended it.
class AppMessageBar extends StatefulWidget {
  /// Creates a message bar.
  const AppMessageBar({
    required this.message,
    required this.type,
    this.onDismissed,
    this.autoDismiss,
    super.key,
  });

  /// Message copy.
  final String message;

  /// The state's message kind; converted to a visual tone by
  /// [messageVisualTone].
  final UiMessageType type;

  /// Called once when the message goes away.
  final VoidCallback? onDismissed;

  /// Overrides the default auto-dismiss delay.
  final Duration? autoDismiss;

  @override
  State<AppMessageBar> createState() => _AppMessageBarState();
}

class _AppMessageBarState extends State<AppMessageBar> {
  Timer? _timer;
  bool _dismissed = false;

  /// The auto-dismiss delay resolved from the theme and any override. Resolved
  /// in [didChangeDependencies] because it needs the ambient [MediaQuery].
  Duration _autoDismiss = messageAutoDismissFallback;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final Duration resolved =
        widget.autoDismiss ?? appMotionOf(context).messageAutoDismiss;
    if (_dismissed && _timer == null) {
      return;
    }
    _autoDismiss = resolved;
    _scheduleDismiss();
  }

  @override
  void didUpdateWidget(AppMessageBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.message != widget.message || oldWidget.type != widget.type) {
      _dismissed = false;
      _scheduleDismiss();
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _scheduleDismiss() {
    _timer?.cancel();
    _timer = Timer(_autoDismiss, _dismiss);
  }

  void _dismiss() {
    if (_dismissed) {
      return;
    }
    _dismissed = true;
    _timer?.cancel();
    widget.onDismissed?.call();
  }

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final MessageVisualTone tone = messageVisualTone(widget.type);
    final Color accent = _accentFor(colors, tone);

    final Widget bar = Material(
      color: colors.surface,
      elevation: shapes.elevationSticky,
      shadowColor: colors.shadow,
      borderRadius: shapes.controlRadius,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: _dismiss,
        child: DecoratedBox(
          decoration: BoxDecoration(
            border: Border(
              left: BorderSide(color: accent, width: 3),
              top: BorderSide(
                color: colors.hairline,
                width: shapes.strokeHairline,
              ),
              right: BorderSide(
                color: colors.hairline,
                width: shapes.strokeHairline,
              ),
              bottom: BorderSide(
                color: colors.hairline,
                width: shapes.strokeHairline,
              ),
            ),
          ),
          child: Padding(
            padding: _barPadding,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                _ToneBadge(icon: _toneIcons[tone]!, accent: accent),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        _toneLabels[tone]!,
                        style: textTheme.labelSmall?.copyWith(
                          color: colors.textSecondary,
                          letterSpacing: 1.0,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(widget.message, style: textTheme.bodyMedium),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: _dismiss,
                  icon: const Icon(Icons.close),
                  iconSize: 18,
                  color: colors.textSecondary,
                  tooltip: 'Dismiss message',
                  visualDensity: VisualDensity.compact,
                ),
              ],
            ),
          ),
        ),
      ),
    );

    final Widget animated = TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: 1),
      duration: AppMotion.resolve(context, appMotionOf(context).controlState),
      curve: Curves.easeOut,
      builder: (BuildContext context, double value, Widget? child) => Opacity(
        opacity: value,
        child: Transform.translate(
          offset: Offset(0, (1 - value) * -_entranceOffset),
          child: child,
        ),
      ),
      child: bar,
    );

    final Widget positioned = Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
      child: Stack(
        children: <Widget>[
          animated,
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: _CountdownStrip(color: accent, duration: _autoDismiss),
          ),
        ],
      ),
    );

    if (!topMessageUsesLiveRegion()) {
      return positioned;
    }
    return Semantics(
      container: true,
      liveRegion: true,
      label: '${_toneLabels[tone]}: ${widget.message}',
      // The whole bar is the dismiss target, so the activation action lives on
      // this node; the close button below is decorative for screen readers,
      // which `excludeSemantics` already handles.
      onTap: _dismiss,
      excludeSemantics: true,
      child: positioned,
    );
  }

  /// Accent role for a tone. Never the only signal: see [_toneIcons] and
  /// [_toneLabels].
  Color _accentFor(AppColors colors, MessageVisualTone tone) => switch (tone) {
    MessageVisualTone.success => colors.accentSuccess,
    MessageVisualTone.error => colors.accentDanger,
    MessageVisualTone.info => colors.accentPrimary,
  };
}

/// The circular tinted glyph that distinguishes a tone without colour.
class _ToneBadge extends StatelessWidget {
  const _ToneBadge({required this.icon, required this.accent});

  final IconData icon;
  final Color accent;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: _badgeSize,
    height: _badgeSize,
    child: DecoratedBox(
      decoration: BoxDecoration(
        color: accent.withValues(alpha: 0.14),
        shape: BoxShape.circle,
      ),
      child: Icon(icon, size: 16, color: accent),
    ),
  );
}

/// A depleting strip that shows how much of the auto-dismiss window is left.
///
/// Purely decorative: the countdown is already expressed by the bar going away,
/// and screen readers get the message itself through the live region.
class _CountdownStrip extends StatelessWidget {
  const _CountdownStrip({required this.color, required this.duration});

  final Color color;
  final Duration duration;

  @override
  Widget build(BuildContext context) {
    final Duration resolved = AppMotion.resolve(context, duration);
    if (resolved == Duration.zero) {
      return const SizedBox(height: _countdownHeight);
    }
    return SizedBox(
      height: _countdownHeight,
      child: TweenAnimationBuilder<double>(
        tween: Tween<double>(begin: 1, end: 0),
        duration: resolved,
        curve: Curves.linear,
        builder: (BuildContext context, double value, Widget? child) =>
            Align(
              alignment: Alignment.centerLeft,
              child: FractionallySizedBox(
                widthFactor: value.clamp(0, 1).toDouble(),
                child: ColoredBox(
                  color: color.withValues(alpha: 0.8),
                  child: const SizedBox(height: _countdownHeight),
                ),
              ),
            ),
      ),
    );
  }
}
