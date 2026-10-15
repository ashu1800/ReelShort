/// The design system's button, in primary, secondary and tertiary variants,
/// with an in-button loading state.
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';

/// Visual weight of an [AppButton].
enum AppButtonVariant {
  /// Filled with the primary accent; exactly one per screen region.
  primary,

  /// Hairline outline on the surface plane; the default alternative action.
  secondary,

  /// Text only; for tertiary actions inside dense content.
  tertiary,
}

/// Size of the button's leading glyph.
const double _iconSize = 18;

/// Diameter of the button-level progress spinner.
const double _spinnerDiameter = 18;

/// Stroke width of the button-level progress spinner.
const double _spinnerStroke = 2;

/// Minimum hit target guaranteed by the widget itself, independent of the
/// ambient theme, so a compact toolbar cannot shrink it.
const double _minimumHeight = 44;

/// Horizontal padding inside every variant.
const EdgeInsets _horizontalPadding = EdgeInsets.symmetric(
  horizontal: 20,
  vertical: 12,
);

/// A themed action button.
///
/// While [isLoading] is true the button is disabled, keeps its measured size so
/// the surrounding layout does not jump, and shows a small spinner in place of
/// its leading icon. A [Semantics] wrapper marks it busy and exposes
/// [loadingLabel] to screen readers.
///
/// All three variants are custom-painted rather than delegated to Material's
/// buttons, so hover, keyboard focus, the focus ring and the loading spinner are
/// identical across variants and grow with the user's text scale.
class AppButton extends StatefulWidget {
  /// Creates an action button.
  const AppButton({
    required this.label,
    required this.onPressed,
    this.variant = AppButtonVariant.primary,
    this.icon,
    this.isLoading = false,
    this.isExpanded = false,
    this.loadingLabel,
    this.semanticLabel,
    this.focusNode,
    this.autofocus = false,
    super.key,
  });

  /// Visible button text. Always required: an icon-only button still needs a
  /// text label for accessibility, passed through [semanticLabel].
  final String label;

  /// Called on activation. A null callback disables the button.
  final VoidCallback? onPressed;

  /// Visual weight.
  final AppButtonVariant variant;

  /// Optional leading glyph.
  final IconData? icon;

  /// Whether the action is in flight.
  final bool isLoading;

  /// Whether the button fills the available width.
  final bool isExpanded;

  /// Announced to screen readers while [isLoading] is true.
  final String? loadingLabel;

  /// Overrides the announced label, which defaults to [label].
  final String? semanticLabel;

  /// Optional external focus node for roving-focus containers.
  final FocusNode? focusNode;

  /// Whether the button takes focus when the route opens.
  final bool autofocus;

  @override
  State<AppButton> createState() => _AppButtonState();
}

class _AppButtonState extends State<AppButton> {
  bool _hovered = false;
  bool _focused = false;

  bool get _isEnabled => widget.onPressed != null && !widget.isLoading;

  void _setHovered(bool value) {
    if (_hovered != value) {
      setState(() => _hovered = value);
    }
  }

  void _setFocused(bool value) {
    if (_focused != value) {
      setState(() => _focused = value);
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final Color foreground = _foregroundColor(colors);

    final Widget content = Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: <Widget>[
        if (widget.isLoading)
          SizedBox(
            width: _spinnerDiameter,
            height: _spinnerDiameter,
            child: CircularProgressIndicator(
              strokeWidth: _spinnerStroke,
              color: foreground,
            ),
          )
        else if (widget.icon != null)
          Icon(widget.icon, size: _iconSize, color: foreground),
        if (widget.isLoading || widget.icon != null) const SizedBox(width: 8),
        Flexible(
          child: Text(
            widget.label,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: textTheme.labelLarge?.copyWith(color: foreground),
          ),
        ),
      ],
    );

    final String announced = widget.isLoading
        ? (widget.loadingLabel ?? widget.label)
        : (widget.semanticLabel ?? widget.label);

    return Semantics(
      container: true,
      button: true,
      enabled: _isEnabled,
      label: announced,
      // The activation action must live on this node: `excludeSemantics`
      // replaces the descendant semantics, so the InkWell's own tap action would
      // otherwise never reach a screen reader.
      onTap: _isEnabled ? widget.onPressed : null,
      excludeSemantics: true,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: _minimumHeight),
        child: SizedBox(
          width: widget.isExpanded ? double.infinity : null,
          child: _FocusableSurface(
            focusNode: widget.focusNode,
            autofocus: widget.autofocus,
            shape: RoundedRectangleBorder(borderRadius: shapes.controlRadius),
            decoration: _decoration(colors, shapes),
            onPressed: _isEnabled ? widget.onPressed : null,
            onHoverChanged: _setHovered,
            onFocusChanged: _setFocused,
            child: content,
          ),
        ),
      ),
    );
  }

  /// Foreground colour for the current variant and enabled state.
  Color _foregroundColor(AppColors colors) {
    if (!_isEnabled) {
      return colors.textSecondary;
    }
    return switch (widget.variant) {
      AppButtonVariant.primary => colors.onAccent,
      AppButtonVariant.secondary => colors.textPrimary,
      AppButtonVariant.tertiary => colors.accentPrimary,
    };
  }

  /// The colour a hover/focus wash is mixed from for the current variant.
  Color _washColor(AppColors colors) => switch (widget.variant) {
    AppButtonVariant.primary => colors.onAccent,
    AppButtonVariant.secondary => colors.textPrimary,
    AppButtonVariant.tertiary => colors.accentPrimary,
  };

  BoxDecoration _decoration(AppColors colors, AppShapes shapes) {
    if (!_isEnabled) {
      return BoxDecoration(
        color: colors.surfaceAlt,
        borderRadius: shapes.controlRadius,
        border: Border.all(
          color: colors.hairline,
          width: shapes.strokeHairline,
        ),
      );
    }
    final Color fill = switch (widget.variant) {
      AppButtonVariant.primary => colors.accentPrimary,
      AppButtonVariant.secondary => _hovered || _focused
          ? _washColor(colors).withValues(alpha: 0.06)
          : Colors.transparent,
      AppButtonVariant.tertiary => _hovered || _focused
          ? _washColor(colors).withValues(alpha: 0.06)
          : Colors.transparent,
    };
    final bool outlined = widget.variant == AppButtonVariant.secondary;
    return BoxDecoration(
      color: fill,
      borderRadius: shapes.controlRadius,
      border: outlined || _focused
          ? Border.all(
              // A control boundary is real information, so it uses the
              // minimum-contrast neutral rather than the decorative hairline.
              color: _focused ? colors.focusRing : colors.outlineStrong,
              width: _focused ? shapes.strokeEmphasis : shapes.strokeHairline,
            )
          : null,
    );
  }
}

/// A tappable, hoverable, keyboard-focusable surface painted by this design
/// system rather than by a Material button.
///
/// Activation happens exactly once per gesture or key press: pointer input goes
/// to the [InkWell], keyboard input goes to the ancestor
/// [FocusableActionDetector]'s [ActivateIntent], and the [InkWell] never
/// requests focus itself.
class _FocusableSurface extends StatelessWidget {
  const _FocusableSurface({
    required this.shape,
    required this.decoration,
    required this.onPressed,
    required this.onHoverChanged,
    required this.onFocusChanged,
    required this.child,
    this.focusNode,
    this.autofocus = false,
  });

  /// Shape used to clip the ink splash.
  final ShapeBorder shape;

  /// Background, border and focus ring.
  final Decoration decoration;

  /// Activation callback; null disables the surface.
  final VoidCallback? onPressed;

  /// Reports pointer hover so the owner can repaint its wash.
  final ValueChanged<bool> onHoverChanged;

  /// Reports keyboard focus so the owner can draw the focus ring.
  final ValueChanged<bool> onFocusChanged;

  /// Button content.
  final Widget child;

  /// Optional external focus node.
  final FocusNode? focusNode;

  /// Whether the surface takes focus when its route opens.
  final bool autofocus;

  @override
  Widget build(BuildContext context) => FocusableActionDetector(
    focusNode: focusNode,
    autofocus: autofocus,
    enabled: onPressed != null,
    onShowHoverHighlight: onHoverChanged,
    onShowFocusHighlight: onFocusChanged,
    shortcuts: const <ShortcutActivator, Intent>{
      SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
      SingleActivator(LogicalKeyboardKey.numpadEnter): ActivateIntent(),
      SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
    },
    actions: <Type, Action<Intent>>{
      ActivateIntent: CallbackAction<ActivateIntent>(
        onInvoke: (ActivateIntent intent) {
          onPressed?.call();
          return null;
        },
      ),
    },
    child: Material(
      color: Colors.transparent,
      child: Ink(
        decoration: decoration,
        child: InkWell(
          onTap: onPressed,
          canRequestFocus: false,
          excludeFromSemantics: true,
          customBorder: shape,
          child: Padding(padding: _horizontalPadding, child: child),
        ),
      ),
    ),
  );
}
