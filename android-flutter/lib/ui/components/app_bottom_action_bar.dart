/// The fixed action bar at the bottom of a screen or sheet.
///
/// Primary actions used to live inside the scrolling content (withdrawal's
/// confirm buttons, the auth sheet's CTA, the VIP paywall's CTA), so a long
/// form or a small screen could scroll the only way forward out of view. This
/// bar keeps the action visible, honours the bottom safe area and carries the
/// hairline that separates it from the content.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';

/// A surface bar with a hairline top border, safe-area aware, holding the
/// screen's primary actions.
class AppBottomActionBar extends StatelessWidget {
  /// Creates a bottom action bar.
  const AppBottomActionBar({
    required this.children,
    this.divider = true,
    this.padding,
    super.key,
  });

  /// Action widgets; they are laid out in a row and share the width equally.
  final List<Widget> children;

  /// Whether to draw the hairline above the bar.
  final bool divider;

  /// Overrides the inner padding.
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);

    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.surface,
        border: divider
            ? Border(
                top: BorderSide(
                  color: colors.hairline,
                  width: shapes.strokeHairline,
                ),
              )
            : null,
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding:
              padding ??
              EdgeInsets.symmetric(
                horizontal: spacing.pageHorizontal,
                vertical: spacing.space3,
              ),
          child: Row(
            children: <Widget>[
              for (int i = 0; i < children.length; i++) ...<Widget>[
                if (i != 0) SizedBox(width: spacing.space3),
                Expanded(child: children[i]),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
