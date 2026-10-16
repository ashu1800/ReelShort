/// Spacing tokens for the ShortLink design system.
///
/// Before this extension every screen carried its own private spacing
/// constants (`_pagePadding`, `_panelGap`, `_panelPadding`, `_sectionGap`), so
/// four screens had four different grids. The scale below is the single source
/// of truth: a raw pixel value in a screen is now a defect, not a preference.
///
/// The scale is a 4 dp ramp (`space1`..`space10`); the semantic aliases name
/// the places the ramp is used so a screen reads `spacing.panelPadding` instead
/// of `spacing.space4`.
library;

import 'package:flutter/material.dart';

/// Horizontal page inset shared by every scrolling page.
const double _pageHorizontal = 18;

/// Distance from the top safe area to the first piece of page content.
const double _pageTopInset = 12;

/// Bottom inset every page keeps so the bottom navigation bar never crowds the
/// last row of content.
const double _pageBottomInset = 28;

/// Gap between two poster tiles in a grid.
const double _gutter = 12;

/// Gap between two page sections.
const double _sectionGap = 16;

/// Gap between panels inside one section.
const double _panelGap = 12;

/// Inner padding of a panel/card.
const double _panelPadding = 16;

/// Minimum height of a tappable list row.
const double _rowMinHeight = 56;

/// Horizontal padding inside a bottom sheet.
const double _sheetPaddingX = 18;

/// Top padding inside a bottom sheet, below the drag handle.
const double _sheetPaddingTop = 20;

/// Bottom padding inside a bottom sheet, above the system inset.
const double _sheetPaddingBottom = 24;

/// Leading inset of a list divider so it starts after the row's leading slot.
const double _listDividerInset = 16;

/// The design system's spacing roles.
///
/// Brightness-independent by design: layout must not move when the user
/// switches theme, so [light] and [dark] are the same instance.
@immutable
class AppSpacing extends ThemeExtension<AppSpacing> {
  /// Creates the spacing token set.
  const AppSpacing({
    required this.space1,
    required this.space2,
    required this.space3,
    required this.space4,
    required this.space5,
    required this.space6,
    required this.space8,
    required this.space10,
    required this.pageHorizontal,
    required this.pageTopInset,
    required this.pageBottomInset,
    required this.gutter,
    required this.sectionGap,
    required this.panelGap,
    required this.panelPadding,
    required this.rowMinHeight,
    required this.sheetPaddingX,
    required this.sheetPaddingTop,
    required this.sheetPaddingBottom,
    required this.listDividerInset,
  });

  /// The single spacing token set.
  static const AppSpacing standard = AppSpacing(
    space1: 4,
    space2: 8,
    space3: 12,
    space4: 16,
    space5: 20,
    space6: 24,
    space8: 32,
    space10: 40,
    pageHorizontal: _pageHorizontal,
    pageTopInset: _pageTopInset,
    pageBottomInset: _pageBottomInset,
    gutter: _gutter,
    sectionGap: _sectionGap,
    panelGap: _panelGap,
    panelPadding: _panelPadding,
    rowMinHeight: _rowMinHeight,
    sheetPaddingX: _sheetPaddingX,
    sheetPaddingTop: _sheetPaddingTop,
    sheetPaddingBottom: _sheetPaddingBottom,
    listDividerInset: _listDividerInset,
  );

  /// Light-mode spacing. Identical to [standard]: layout is theme-stable.
  static const AppSpacing light = standard;

  /// Dark-mode spacing. Identical to [standard]: layout is theme-stable.
  static const AppSpacing dark = standard;

  /// 4 dp - hairline gaps, icon-to-label inside a dense control.
  final double space1;

  /// 8 dp - related elements inside one block.
  final double space2;

  /// 12 dp - grid gutter, default gap between sibling rows.
  final double space3;

  /// 16 dp - panel padding, section gap.
  final double space4;

  /// 20 dp - sheet top padding, roomy block gap.
  final double space5;

  /// 24 dp - gap between major blocks.
  final double space6;

  /// 32 dp - page-level breathing room.
  final double space8;

  /// 40 dp - hero spacing before a large block.
  final double space10;

  /// Horizontal page inset every scrolling page uses.
  final double pageHorizontal;

  /// Distance from the top safe area to the first piece of content.
  final double pageTopInset;

  /// Bottom inset that keeps the navigation bar clear of the last row.
  final double pageBottomInset;

  /// Gap between two poster tiles.
  final double gutter;

  /// Gap between two page sections.
  final double sectionGap;

  /// Gap between panels inside one section.
  final double panelGap;

  /// Inner padding of a panel.
  final double panelPadding;

  /// Minimum height of a tappable list row.
  final double rowMinHeight;

  /// Horizontal padding inside a bottom sheet.
  final double sheetPaddingX;

  /// Top padding inside a bottom sheet.
  final double sheetPaddingTop;

  /// Bottom padding inside a bottom sheet.
  final double sheetPaddingBottom;

  /// Leading inset of a list divider.
  final double listDividerInset;

  /// Standard page padding: horizontal inset plus the top and bottom insets.
  EdgeInsets get pagePadding => EdgeInsets.fromLTRB(
    pageHorizontal,
    pageTopInset,
    pageHorizontal,
    pageBottomInset,
  );

  @override
  AppSpacing copyWith({
    double? space1,
    double? space2,
    double? space3,
    double? space4,
    double? space5,
    double? space6,
    double? space8,
    double? space10,
    double? pageHorizontal,
    double? pageTopInset,
    double? pageBottomInset,
    double? gutter,
    double? sectionGap,
    double? panelGap,
    double? panelPadding,
    double? rowMinHeight,
    double? sheetPaddingX,
    double? sheetPaddingTop,
    double? sheetPaddingBottom,
    double? listDividerInset,
  }) => AppSpacing(
    space1: space1 ?? this.space1,
    space2: space2 ?? this.space2,
    space3: space3 ?? this.space3,
    space4: space4 ?? this.space4,
    space5: space5 ?? this.space5,
    space6: space6 ?? this.space6,
    space8: space8 ?? this.space8,
    space10: space10 ?? this.space10,
    pageHorizontal: pageHorizontal ?? this.pageHorizontal,
    pageTopInset: pageTopInset ?? this.pageTopInset,
    pageBottomInset: pageBottomInset ?? this.pageBottomInset,
    gutter: gutter ?? this.gutter,
    sectionGap: sectionGap ?? this.sectionGap,
    panelGap: panelGap ?? this.panelGap,
    panelPadding: panelPadding ?? this.panelPadding,
    rowMinHeight: rowMinHeight ?? this.rowMinHeight,
    sheetPaddingX: sheetPaddingX ?? this.sheetPaddingX,
    sheetPaddingTop: sheetPaddingTop ?? this.sheetPaddingTop,
    sheetPaddingBottom: sheetPaddingBottom ?? this.sheetPaddingBottom,
    listDividerInset: listDividerInset ?? this.listDividerInset,
  );

  @override
  AppSpacing lerp(covariant AppSpacing? other, double t) {
    if (other == null) {
      return this;
    }
    return AppSpacing(
      space1: _lerpDouble(space1, other.space1, t),
      space2: _lerpDouble(space2, other.space2, t),
      space3: _lerpDouble(space3, other.space3, t),
      space4: _lerpDouble(space4, other.space4, t),
      space5: _lerpDouble(space5, other.space5, t),
      space6: _lerpDouble(space6, other.space6, t),
      space8: _lerpDouble(space8, other.space8, t),
      space10: _lerpDouble(space10, other.space10, t),
      pageHorizontal: _lerpDouble(pageHorizontal, other.pageHorizontal, t),
      pageTopInset: _lerpDouble(pageTopInset, other.pageTopInset, t),
      pageBottomInset: _lerpDouble(pageBottomInset, other.pageBottomInset, t),
      gutter: _lerpDouble(gutter, other.gutter, t),
      sectionGap: _lerpDouble(sectionGap, other.sectionGap, t),
      panelGap: _lerpDouble(panelGap, other.panelGap, t),
      panelPadding: _lerpDouble(panelPadding, other.panelPadding, t),
      rowMinHeight: _lerpDouble(rowMinHeight, other.rowMinHeight, t),
      sheetPaddingX: _lerpDouble(sheetPaddingX, other.sheetPaddingX, t),
      sheetPaddingTop: _lerpDouble(sheetPaddingTop, other.sheetPaddingTop, t),
      sheetPaddingBottom: _lerpDouble(
        sheetPaddingBottom,
        other.sheetPaddingBottom,
        t,
      ),
      listDividerInset: _lerpDouble(
        listDividerInset,
        other.listDividerInset,
        t,
      ),
    );
  }

  static double _lerpDouble(double a, double b, double t) => a + (b - a) * t;
}

/// Reads the [AppSpacing] tokens from [context], falling back to
/// [AppSpacing.standard] when no theme extension is present.
AppSpacing appSpacingOf(BuildContext context) =>
    Theme.of(context).extension<AppSpacing>() ?? AppSpacing.standard;
