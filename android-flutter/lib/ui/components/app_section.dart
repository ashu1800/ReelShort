/// Section chrome: the header above a block, and the card that groups one.
///
/// Every screen previously hand-built its section headers (a dot plus a
/// `display`-styled label in search, a "DETAIL" label in the old detail page, a
/// hand-rolled count pill in account), and `sectionHeaderTransform` — the
/// function the design system calls the single decider of upper-casing — had
/// zero call sites. These two widgets are the only place section headers are
/// built now, so the transform cannot leak into one screen and not another.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';
import 'app_panel.dart';

/// The all-caps label above a section, with an optional trailing action.
class AppSectionHeader extends StatelessWidget {
  /// Creates a section header.
  const AppSectionHeader({
    required this.title,
    this.actionLabel,
    this.onAction,
    this.trailing,
    super.key,
  });

  /// Section title; it is upper-cased through [sectionHeaderTransform].
  final String title;

  /// Optional text action on the trailing edge ("See all").
  final String? actionLabel;

  /// Callback for [actionLabel]. Without it the action renders as a plain
  /// label, never as a dead button.
  final VoidCallback? onAction;

  /// Optional trailing widget, used instead of [actionLabel] when a screen
  /// needs a count or a badge.
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final Widget title = Text(
      sectionHeaderTransform(this.title),
      style: roles.display.copyWith(color: colors.textSecondary),
    );
    final Widget? action = actionLabel == null
        ? null
        : TextButton(
            onPressed: onAction,
            style: TextButton.styleFrom(
              padding: EdgeInsets.symmetric(horizontal: spacing.space2),
              minimumSize: const Size(0, 44),
              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              visualDensity: VisualDensity.compact,
            ),
            child: Text(
              actionLabel!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          );

    // At large text scales or on a narrow screen the header stacks instead of
    // squeezing the title to nothing.
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final bool stack =
            constraints.maxWidth < 260 ||
            MediaQuery.textScalerOf(context).scale(1) > 1.6;

        if (stack) {
          return Semantics(
            header: true,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                title,
                if (trailing != null || action != null)
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: <Widget>[
                      ?trailing,
                      if (action != null) Flexible(child: action),
                    ],
                  ),
              ],
            ),
          );
        }

        return Semantics(
          header: true,
          child: Row(
            children: <Widget>[
              Expanded(child: title),
              ?trailing,
              if (action != null) Flexible(child: action),
            ],
          ),
        );
      },
    );
  }
}

/// A panel that groups one section: optional header, hairline separators
/// between children and none after the last one.
class AppSectionCard extends StatelessWidget {
  /// Creates a section card.
  const AppSectionCard({
    required this.children,
    this.title,
    this.actionLabel,
    this.onAction,
    this.trailing,
    this.padding,
    super.key,
  });

  /// Section body rows.
  final List<Widget> children;

  /// Optional section title rendered through [AppSectionHeader].
  final String? title;

  /// Optional trailing action of the header.
  final String? actionLabel;

  /// Callback for [actionLabel].
  final VoidCallback? onAction;

  /// Optional trailing widget of the header.
  final Widget? trailing;

  /// Overrides the panel padding (defaults to zero so rows own their padding).
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final List<Widget> body = <Widget>[];

    if (title != null) {
      body.add(
        Padding(
          padding: EdgeInsets.fromLTRB(
            spacing.panelPadding,
            spacing.space3,
            spacing.space2,
            spacing.space1,
          ),
          child: AppSectionHeader(
            title: title!,
            actionLabel: actionLabel,
            onAction: onAction,
            trailing: trailing,
          ),
        ),
      );
    }

    for (int i = 0; i < children.length; i++) {
      body.add(children[i]);
      if (i != children.length - 1) {
        body.add(
          Padding(
            padding: EdgeInsets.only(left: spacing.listDividerInset),
            child: Divider(
              height: shapes.strokeHairline,
              thickness: shapes.strokeHairline,
              color: colors.hairline,
            ),
          ),
        );
      }
    }

    return AppPanel(
      padding: padding ?? EdgeInsets.symmetric(vertical: spacing.space1),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: body,
      ),
    );
  }
}
