/// The raised content plane of the design system.
///
/// Before this component the "surface + [AppShapes.radiusContainer] + hairline"
/// decoration was hand-written in nine places (two identical private `_Panel`
/// copies plus seven inline ones), which is why card padding and border weight
/// drifted between screens. [AppPanel] is that decoration, once.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';

/// A surface-coloured, hairline-bordered plane with the container radius.
///
/// When [onTap] is provided the whole panel becomes one tappable target: the
/// ink lives on the panel's own [Material], the activation action is exposed on
/// the merged semantics node (never on a descendant), and the panel wears the
/// focus ring while focused.
class AppPanel extends StatefulWidget {
  /// Creates a panel.
  const AppPanel({
    required this.child,
    this.onTap,
    this.padding,
    this.selected = false,
    this.semanticLabel,
    this.semanticsHint,
    super.key,
  });

  /// Panel content.
  final Widget child;

  /// Makes the whole panel tappable when set.
  final VoidCallback? onTap;

  /// Overrides the default inner padding ([AppSpacing.panelPadding]).
  final EdgeInsetsGeometry? padding;

  /// Renders the premium tint instead of the plain surface, for the current
  /// membership/selected block.
  final bool selected;

  /// Optional merged semantic label for a tappable panel.
  final String? semanticLabel;

  /// Optional merged semantic hint for a tappable panel.
  final String? semanticsHint;

  @override
  State<AppPanel> createState() => _AppPanelState();
}

class _AppPanelState extends State<AppPanel> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final Color fill = widget.selected ? colors.premiumSurface : colors.surface;
    final Color borderColor = _focused ? colors.focusRing : colors.hairline;
    final double borderWidth = _focused
        ? shapes.strokeEmphasis
        : shapes.strokeHairline;
    final EdgeInsetsGeometry padding =
        widget.padding ?? EdgeInsets.all(spacing.panelPadding);
    final BorderSide side = BorderSide(color: borderColor, width: borderWidth);

    if (widget.onTap == null) {
      return Container(
        decoration: BoxDecoration(
          color: fill,
          borderRadius: shapes.containerRadius,
          border: Border.fromBorderSide(side),
        ),
        padding: padding,
        child: widget.child,
      );
    }

    return Semantics(
      button: true,
      label: widget.semanticLabel,
      hint: widget.semanticsHint,
      // The action belongs to this node: `excludeSemantics` replaces the
      // descendants, so a descendant InkWell action would be dropped.
      onTap: widget.onTap,
      excludeSemantics: widget.semanticLabel != null,
      child: FocusableActionDetector(
        onShowFocusHighlight: (bool value) {
          if (value != _focused) {
            setState(() => _focused = value);
          }
        },
        child: Material(
          color: fill,
          clipBehavior: Clip.antiAlias,
          shape: RoundedRectangleBorder(
            borderRadius: shapes.containerRadius,
            side: side,
          ),
          child: InkWell(
            onTap: widget.onTap,
            child: Padding(padding: padding, child: widget.child),
          ),
        ),
      ),
    );
  }
}
