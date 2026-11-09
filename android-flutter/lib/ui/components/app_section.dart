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

/// Share of the header width a [AppSectionHeader.trailing] widget may occupy
/// next to the title.
///
/// The title keeps an `Expanded` slot, so the trailing widget cannot be
/// `Flexible` without stealing half the row from the title. Capping its width
/// instead gives it a bounded slot: a count pill or badge then ellipsises or
/// scrolls internally instead of overflowing the header. Every screen used to
/// patch this from the outside (Discover's count pill was wrapped in its own
/// `LayoutBuilder` cap), which is exactly the caller-side workaround this
/// constant replaces.
const double sectionHeaderTrailingWidthFactor = 0.5;

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
  ///
  /// It is capped to [sectionHeaderTrailingWidthFactor] of the header width next
  /// to the title (and to the full width when the header stacks), so a wide
  /// pill is the widget's own ellipsis problem and never a header overflow.
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
        // A bounded slot in both branches: the trailing widget sees a finite
        // max width, so its own ellipsis applies instead of a RenderFlex
        // overflow.
        final Widget? boundedTrailing = trailing == null
            ? null
            : ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: stack
                      ? constraints.maxWidth
                      : constraints.maxWidth *
                            sectionHeaderTrailingWidthFactor,
                ),
                child: trailing!,
              );

        if (stack) {
          return Semantics(
            header: true,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                title,
                if (boundedTrailing != null || action != null)
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: <Widget>[
                      ?boundedTrailing,
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
              ?boundedTrailing,
              if (action != null) Flexible(child: action),
            ],
          ),
        );
      },
    );
  }
}

/// A panel that groups one section: optional header, hairline separators
/// between consecutive rows and none anywhere else.
class AppSectionCard extends StatelessWidget {
  /// Creates a section card.
  const AppSectionCard({
    required this.children,
    this.title,
    this.actionLabel,
    this.onAction,
    this.trailing,
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

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final List<Widget> body = <Widget>[];
    bool previousWasRow = false;

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
      final Widget child = children[i];
      final bool isRow = child is AppListSectionRow;
      // A hairline separates two consecutive rows. A block of copy or a form is
      // one unit, so a rule above it would cut it off from the row it belongs
      // to - and a hero figure never wants a line drawn through its card.
      if (i != 0 && isRow && previousWasRow) {
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
      previousWasRow = isRow;
      // Exactly one inner content edge: a row paints its own [AppSpacing.panelPadding]
      // inset (and its own vertical rhythm), so the card leaves it untouched.
      // Everything else (an amount block, copy, a form, a button) is inset here,
      // and the first/last child also carries the card's vertical breathing
      // room - otherwise a figure or a field used to draw straight under the
      // card's own border.
      body.add(
        child is AppSelfInsetRow
            ? child
            : Padding(
                padding: EdgeInsets.fromLTRB(
                  spacing.panelPadding,
                  i == 0 ? spacing.space3 : 0,
                  spacing.panelPadding,
                  i == children.length - 1 ? spacing.space3 : 0,
                ),
                child: child,
              ),
      );
    }
    return AppPanel(
      // Every inner edge belongs to the children above.
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: body,
      ),
    );
  }
}
