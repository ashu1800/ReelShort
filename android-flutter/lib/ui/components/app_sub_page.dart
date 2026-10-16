/// The shell's shared chrome for a pushed sub-page.
///
/// Phase 3 turned `favorites`, `withdrawal` and `vipRecharge` into real pushed
/// pages. Before that each of them hand-rolled its own chrome: two private
/// headers with two slightly different paddings, one page with no header at
/// all, three private `_pagePadding` constants, two different title roles and
/// one back affordance with no minimum hit size. [AppSubPage] is that chrome in
/// one place, so a sub-page only owns its *content*:
///
///  * one page-title role ([AppTypography.screenTitle]) instead of three
///    interchangeable text styles;
///  * a back affordance that is always at least [backHitSize] square, labelled
///    and tooltipped with [AppUiCopy.back];
///  * the top safe area handled once, so no screen needs its own [SafeArea];
///  * [AppSpacing.pageBottomInset] reserved under the content, so the last row
///    never hides behind the shell's navigation bar;
///  * optional trailing [actions] and an optional [bottomBar] for a sticky
///    action, both laid out by the same column.
///
/// It deliberately does **not** build a [Scaffold]: the shell owns the canvas
/// background and the navigation bar, and a nested scaffold would paint a
/// second background over the shell's and answer the keyboard with its own
/// resize. It also never introduces a raw colour: every colour comes from
/// [AppColors] through the theme.
library;

import 'package:flutter/material.dart';

import '../../core/formatting/app_ui_copy.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_icons.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';

/// Wraps a sub-page's [child] in the shared page chrome.
class AppSubPage extends StatelessWidget {
  /// Creates the sub-page chrome around [child].
  const AppSubPage({
    required this.title,
    required this.child,
    this.onBack,
    this.showBack = true,
    this.actions = const <Widget>[],
    this.scrollable = false,
    this.bottomBar,
    this.padded = true,
    super.key,
  });

  /// The page title, rendered once with the [AppTypography.screenTitle] role.
  final String title;

  /// The page body.
  final Widget child;

  /// Called by the back affordance; defaults to popping the enclosing route.
  final VoidCallback? onBack;

  /// Whether the header offers a back affordance.
  final bool showBack;

  /// Trailing header actions, laid out after the title.
  final List<Widget> actions;

  /// Whether [child] is wrapped in a vertical scroll view.
  ///
  /// When true the page padding travels *inside* the scroll view, so content
  /// scrolls under the header instead of being clipped by an outer padding.
  final bool scrollable;

  /// Optional bar pinned under the scrolling content (a sticky action row).
  final Widget? bottomBar;

  /// Whether the page padding ([AppSpacing.pagePadding] roles) is applied.
  final bool padded;

  /// Minimum side of the back affordance's touch target, in logical pixels.
  ///
  /// Kept public so the accessibility contract (>= 44 x 44) is assertable from
  /// a widget test without duplicating the literal.
  static const double backHitSize = 44;

  @override
  Widget build(BuildContext context) {
    final AppSpacing spacing = appSpacingOf(context);
    // The shell's Scaffold consumes the keyboard inset by resizing its body, so
    // this is 0 in the app; it keeps the page readable when the chrome is used
    // somewhere that does not resize (a dialog host, a preview).
    final double keyboardInset = MediaQuery.viewInsetsOf(context).bottom;
    final EdgeInsets pagePadding = EdgeInsets.fromLTRB(
      spacing.pageHorizontal,
      spacing.pageTopInset,
      spacing.pageHorizontal,
      spacing.pageBottomInset + keyboardInset,
    );

    final Widget body;
    if (scrollable) {
      body = SingleChildScrollView(
        padding: padded ? pagePadding : EdgeInsets.zero,
        child: child,
      );
    } else if (padded) {
      body = Padding(padding: pagePadding, child: child);
    } else {
      body = child;
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        SafeArea(
          bottom: false,
          child: _SubPageHeader(
            title: title,
            onBack: onBack,
            showBack: showBack,
            actions: actions,
          ),
        ),
        Expanded(child: body),
        ?bottomBar,
      ],
    );
  }
}

/// The header row: back affordance, page title and optional trailing actions.
class _SubPageHeader extends StatelessWidget {
  const _SubPageHeader({
    required this.title,
    required this.onBack,
    required this.showBack,
    required this.actions,
  });

  final String title;
  final VoidCallback? onBack;
  final bool showBack;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppIconSize icons = appIconSizeOf(context);
    final AppSpacing spacing = appSpacingOf(context);

    return Padding(
      padding: EdgeInsets.fromLTRB(
        spacing.space2,
        spacing.space2,
        spacing.space4,
        0,
      ),
      child: Row(
        children: <Widget>[
          if (showBack)
            IconButton(
              // Default to popping the enclosing route; the shell passes the
              // controller transition that owns each sub-page instead.
              onPressed: onBack ?? () => Navigator.of(context).maybePop(),
              icon: const Icon(Icons.arrow_back),
              iconSize: icons.lg,
              color: colors.textPrimary,
              tooltip: AppUiCopy.back,
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(
                minWidth: AppSubPage.backHitSize,
                minHeight: AppSubPage.backHitSize,
              ),
            ),
          Expanded(
            child: Semantics(
              namesRoute: true,
              header: true,
              child: Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: roles.screenTitle.copyWith(color: colors.textPrimary),
              ),
            ),
          ),
          ...actions,
        ],
      ),
    );
  }
}
