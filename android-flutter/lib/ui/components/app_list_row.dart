/// The standard tappable list row.
///
/// The screens hand-rolled "leading icon + title + subtitle + chevron" six
/// times (account alone has four variants), which is why row heights, chevron
/// colours and semantic labels disagreed. This is that row, once, with the
/// design system's contract attached:
///
///  * the row is at least [AppSpacing.rowMinHeight] tall, so every row clears
///    the 44 px touch target;
///  * the activation action is exposed on the row's own merged semantics node,
///    not on a descendant (see `docs/design-system.md` §7.4);
///  * a disabled row passes `onTap: null` and therefore exposes no action.
///
/// Semantics inside a card (`docs/ui-redesign-notes.md` §7.2): every row is its
/// own node (`container: true`), so a card that mixes tappable and plain rows
/// does not collapse into one announcement. The descriptive half (leading,
/// title, subtitle) is excluded from the tree and announced by that node, while
/// a [trailing] control keeps its own readable, tappable node - which is what a
/// row-level "Check" button or a selectable value needs.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_icons.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';

/// Text scale above which a labelled [AppListRow.trailing] action gives up its
/// intrinsic width and shares the row with the title column.
///
/// The same threshold `AppSectionHeader` and `AppLedgerRow` use to switch to
/// their stacked layout.
const double trailingIntrinsicMaxTextScale = 1.6;

/// One row of a settings/entry list.
class AppListRow extends StatelessWidget {
  /// Creates a list row.
  const AppListRow({
    required this.title,
    this.subtitle,
    this.subtitleMaxLines,
    this.leading,
    this.leadingIcon,
    this.trailing,
    this.trailingTakesIntrinsicWidth = false,
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

  /// Maximum lines of [subtitle]; null lets it wrap freely.
  ///
  /// A row whose subtitle is a long machine value (a wallet address) sets this
  /// to 1 so the row keeps its regular height and the value ellipsises instead
  /// of pushing the row into a paragraph.
  final int? subtitleMaxLines;

  /// Custom leading widget; takes precedence over [leadingIcon].
  final Widget? leading;

  /// Leading icon rendered in a tinted plate.
  final IconData? leadingIcon;

  /// Widget after the title column (a value, a switch, a badge).
  final Widget? trailing;

  /// Whether [trailing] is laid out at its own width instead of sharing the
  /// row's free space with the title column.
  ///
  /// The default splits the free space evenly, which is right for a value that
  /// should shrink (a version, a date). A row whose trailing is a labelled
  /// action sets this to true, so the action is not clipped in the middle at
  /// reading sizes; the control is still capped at half the row (the title
  /// column keeps the rest, so the row can never overflow), and above the 1.6x
  /// text scale the row returns to the shared split - the same threshold the
  /// section header uses - because there the title column needs its share more
  /// than the action needs its full wording.
  final bool trailingTakesIntrinsicWidth;

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

    // An action label keeps its own width while the ambient scale is close to
    // reading size; past [trailingIntrinsicMaxTextScale] the shared split is the
    // safer shape (see [trailingTakesIntrinsicWidth]).
    final bool intrinsicTrailing =
        trailingTakesIntrinsicWidth &&
        trailing != null &&
        MediaQuery.textScalerOf(context).scale(1) <=
            trailingIntrinsicMaxTextScale;

    final Widget body = LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        // Half the row is the most an intrinsically sized action may take: the
        // title column keeps the rest, so the row can never overflow.
        final double trailingCap = constraints.maxWidth.isFinite
            ? constraints.maxWidth / 2
            : double.infinity;
        return ConstrainedBox(
          constraints: BoxConstraints(minHeight: spacing.rowMinHeight),
          child: Padding(
            padding: EdgeInsets.symmetric(
              horizontal: spacing.space4,
              vertical: spacing.space3,
            ),
            child: Row(
              children: <Widget>[
                // Everything the row node announces is excluded from the tree,
                // so the label is read once; the trailing control below stays
                // out of that exclusion and keeps its own node.
                if (leading != null) ...<Widget>[
                  ExcludeSemantics(child: leading!),
                  SizedBox(width: spacing.space4),
                ] else if (leadingIcon != null) ...<Widget>[
                  ExcludeSemantics(
                    child: _LeadingPlate(icon: leadingIcon!, enabled: enabled),
                  ),
                  SizedBox(width: spacing.space4),
                ],
                Expanded(
                  child: ExcludeSemantics(
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
                            maxLines: subtitleMaxLines,
                            overflow: subtitleMaxLines == null
                                ? null
                                : TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(color: subtitleColor),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
                if (trailing != null) ...<Widget>[
                  SizedBox(width: spacing.space3),
                  if (intrinsicTrailing)
                    ConstrainedBox(
                      constraints: BoxConstraints(maxWidth: trailingCap),
                      child: trailing!,
                    )
                  else
                    // Flexible so a long value (a version, a date) shrinks or
                    // wraps instead of overflowing the row at large text scales.
                    Flexible(child: trailing!),
                ],
                if (showChevron) ...<Widget>[
                  SizedBox(width: spacing.space2),
                  ExcludeSemantics(
                    child: Icon(
                      Icons.chevron_right,
                      size: icons.lg,
                      color: enabled ? colors.textSecondary : colors.onDisabled,
                    ),
                  ),
                ],
              ],
            ),
          ),
        );
      },
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
            child: InkWell(
              onTap: onTap,
              // The activation action belongs to the row node below; without
              // this the ink would add a second, duplicate node.
              excludeFromSemantics: true,
              child: row,
            ),
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

    // `container: true` is what keeps every row its own node inside a card; a
    // plain row exposes no tap action at all.
    return Semantics(
      container: true,
      button: enabled,
      label: effectiveLabel,
      hint: semanticsHint,
      onTap: enabled ? onTap : null,
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
