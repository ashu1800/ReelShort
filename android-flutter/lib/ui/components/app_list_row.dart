/// The standard tappable list row.
///
/// The screens hand-rolled "leading icon + title + subtitle + chevron" six
/// times (account alone has four variants), which is why row heights, chevron
/// colours and semantic labels disagreed. This is that row, once, with the
/// design system's contract attached:
///
///  * the row is at least [AppSpacing.rowMinHeight] tall, so every row clears
///    the 44 px touch target;
///  * the activation action is exposed on the merged semantics node, not on a
///    descendant (see `docs/design-system.md` §7.4);
///  * a disabled row passes `onTap: null` and therefore exposes no action.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_icons.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';

/// One row of a settings/entry list.
class AppListRow extends StatelessWidget {
  /// Creates a list row.
  const AppListRow({
    required this.title,
    this.subtitle,
    this.leading,
    this.leadingIcon,
    this.trailing,
    this.showChevron = false,
    this.onTap,
    this.divider = true,
    this.selected = false,
    this.semanticLabel,
    this.semanticsHint,
    super.key,
  });

  /// Primary label.
  final String title;

  /// Optional supporting label under [title].
  final String? subtitle;

  /// Custom leading widget; takes precedence over [leadingIcon].
  final Widget? leading;

  /// Leading icon rendered in a tinted plate.
  final IconData? leadingIcon;

  /// Widget after the title column (a value, a switch, a badge).
  final Widget? trailing;

  /// Whether to draw the disclosure chevron.
  final bool showChevron;

  /// Makes the row tappable. `null` renders a non-interactive row.
  final VoidCallback? onTap;

  /// Whether to draw the hairline under the row. The last row of a group
  /// passes `false` so a card never ends on a dangling separator.
  final bool divider;

  /// Renders the row on the premium tint.
  final bool selected;

  /// Merged semantic label; defaults to [title] plus [subtitle].
  final String? semanticLabel;

  /// Optional merged semantic hint.
  final String? semanticsHint;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppIconSize icons = appIconSizeOf(context);
    final bool enabled = onTap != null;
    // One announcement per row: the title plus its subtitle, unless the caller
    // supplies a better label.
    final String effectiveLabel =
        semanticLabel ??
        (subtitle == null || subtitle!.isEmpty ? title : '$title, $subtitle');
    final Color titleColor = enabled ? colors.textPrimary : colors.onDisabled;
    final Color subtitleColor = enabled
        ? colors.textSecondary
        : colors.onDisabled;

    final Widget body = ConstrainedBox(
      constraints: BoxConstraints(minHeight: spacing.rowMinHeight),
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: spacing.space4,
          vertical: spacing.space3,
        ),
        child: Row(
          children: <Widget>[
            if (leading != null) ...<Widget>[
              leading!,
              SizedBox(width: spacing.space4),
            ] else if (leadingIcon != null) ...<Widget>[
              _LeadingPlate(icon: leadingIcon!, enabled: enabled),
              SizedBox(width: spacing.space4),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  Text(
                    title,
                    style: Theme.of(
                      context,
                    ).textTheme.titleMedium?.copyWith(color: titleColor),
                  ),
                  if (subtitle != null) ...<Widget>[
                    SizedBox(height: spacing.space1),
                    Text(
                      subtitle!,
                      style: Theme.of(
                        context,
                      ).textTheme.bodySmall?.copyWith(color: subtitleColor),
                    ),
                  ],
                ],
              ),
            ),
            if (trailing != null) ...<Widget>[
              SizedBox(width: spacing.space3),
              // Flexible so a long value (a version, a date) shrinks or wraps
              // instead of overflowing the row at large text scales.
              Flexible(child: trailing!),
            ],
            if (showChevron) ...<Widget>[
              SizedBox(width: spacing.space2),
              Icon(
                Icons.chevron_right,
                size: icons.lg,
                color: enabled ? colors.textSecondary : colors.onDisabled,
              ),
            ],
          ],
        ),
      ),
    );

    final Widget row = selected
        ? ColoredBox(color: colors.premiumSurface, child: body)
        : body;

    final Widget content = Column(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        if (enabled)
          Material(
            color: Colors.transparent,
            child: InkWell(onTap: onTap, child: row),
          )
        else
          row,
        if (divider)
          Padding(
            padding: EdgeInsets.only(left: spacing.listDividerInset),
            child: Divider(
              height: shapes.strokeHairline,
              thickness: shapes.strokeHairline,
              color: colors.hairline,
            ),
          ),
      ],
    );

    if (!enabled) {
      return Semantics(
        label: effectiveLabel,
        hint: semanticsHint,
        excludeSemantics: true,
        child: content,
      );
    }

    return Semantics(
      button: true,
      label: effectiveLabel,
      hint: semanticsHint,
      onTap: onTap,
      excludeSemantics: true,
      child: content,
    );
  }
}

/// The tinted plate behind a leading icon.
class _LeadingPlate extends StatelessWidget {
  const _LeadingPlate({required this.icon, required this.enabled});

  final IconData icon;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppIconSize icons = appIconSizeOf(context);
    final AppSpacing spacing = appSpacingOf(context);

    return Container(
      width: spacing.space10,
      height: spacing.space10,
      decoration: BoxDecoration(
        color: enabled ? colors.premiumSurface : colors.surfaceDisabled,
        borderRadius: shapes.controlRadius,
      ),
      child: Icon(
        icon,
        size: icons.md,
        color: enabled ? colors.accentPrimary : colors.onDisabled,
      ),
    );
  }
}
