/// A two-to-four option segmented control.
///
/// Used for the auth sheet's login/register modes and the search page's
/// category filter. Material's `SegmentedButton` is close, but the design system
/// needs three things it does not give here: a checkmark on the selected option
/// (state must never be carried by colour alone — §7.2), a 44 px minimum height
/// and the neutral surface ramp instead of the Material tint.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_icons.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';

/// One option of an [AppSegmentedControl].
class AppSegmentedOption<T> {
  /// Creates an option.
  const AppSegmentedOption({required this.value, required this.label, this.icon});

  /// Value reported to `onChanged` when the option is chosen.
  final T value;

  /// Visible label.
  final String label;

  /// Optional leading icon.
  final IconData? icon;
}

/// A single-select segmented control.
class AppSegmentedControl<T> extends StatelessWidget {
  /// Creates a segmented control.
  const AppSegmentedControl({
    required this.value,
    required this.options,
    required this.onChanged,
    this.semanticLabel,
    super.key,
  });

  /// Currently selected value.
  final T value;

  /// Options, in visual order.
  final List<AppSegmentedOption<T>> options;

  /// Called with the tapped option's value.
  final ValueChanged<T> onChanged;

  /// Optional semantic label for the group.
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppIconSize icons = appIconSizeOf(context);

    return Semantics(
      container: true,
      label: semanticLabel,
      child: Container(
        padding: EdgeInsets.all(spacing.space1 / 2),
        decoration: BoxDecoration(
          color: colors.surfaceAlt,
          borderRadius: shapes.controlRadius,
        ),
        child: Row(
          children: <Widget>[
            for (final AppSegmentedOption<T> option in options)
              Expanded(
                child: _Segment<T>(
                  option: option,
                  selected: option.value == value,
                  onTap: () => onChanged(option.value),
                  checkIcon: icons.sm,
                  radius: shapes.controlRadius,
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _Segment<T> extends StatelessWidget {
  const _Segment({
    required this.option,
    required this.selected,
    required this.onTap,
    required this.checkIcon,
    required this.radius,
  });

  final AppSegmentedOption<T> option;
  final bool selected;
  final VoidCallback onTap;
  final double checkIcon;
  final BorderRadius radius;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppIconSize icons = appIconSizeOf(context);
    final Color foreground = selected
        ? colors.accentPrimary
        : colors.textSecondary;

    return Semantics(
      button: true,
      selected: selected,
      label: option.label,
      onTap: onTap,
      excludeSemantics: true,
      child: ConstrainedBox(
        constraints: BoxConstraints(minHeight: spacing.space10 + spacing.space1),
        child: Material(
          color: selected ? colors.surface : Colors.transparent,
          borderRadius: radius,
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onTap,
            child: Padding(
              padding: EdgeInsets.symmetric(horizontal: spacing.space3),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  if (selected) ...<Widget>[
                    Icon(Icons.check, size: checkIcon, color: foreground),
                    SizedBox(width: spacing.space1),
                  ] else if (option.icon != null) ...<Widget>[
                    Icon(option.icon, size: icons.md, color: foreground),
                    SizedBox(width: spacing.space1),
                  ],
                  Flexible(
                    child: Text(
                      option.label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.labelLarge?.copyWith(
                        color: foreground,
                        fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
