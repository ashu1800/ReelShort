/// The ledger row: a label/value pair with a hairline divider and tabular
/// figures, used by the funds, points and withdrawal lists.
///
/// The value column is the reason this component exists: every figure in the
/// app is rendered with `FontFeature.tabularFigures()`, so a column of rows
/// lines up digit for digit regardless of the numbers involved.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';

/// Vertical padding inside a ledger row.
const EdgeInsets _rowPadding = EdgeInsets.symmetric(vertical: 12);

/// Gap between the label and the value when they sit side by side.
const double _inlineGap = 16;

/// Gap between the label and the value when they are stacked.
const double _stackedGap = 4;

/// Below this available width the row stacks instead of splitting into columns,
/// which is what keeps a 320 px screen with 3.2x text from overflowing.
const double _stackBreakpoint = 260;

/// Text scale above which the row stacks regardless of width.
const double _stackFontScale = 1.6;

/// A single label/value row.
///
/// The label is secondary copy; the value uses the numeric role and is aligned
/// to the trailing edge when the row is wide enough. Layout is responsive from
/// 320 px to 1440 px: the value never truncates a figure, it wraps to its own
/// line instead.
class AppLedgerRow extends StatelessWidget {
  /// Creates a ledger row.
  const AppLedgerRow({
    required this.label,
    required this.value,
    this.divider = true,
    this.valueColor,
    this.trailing,
    this.semanticLabel,
    super.key,
  });

  /// Row label, for example "Available".
  final String label;

  /// Row value, already formatted by the caller. Rendered with tabular
  /// figures.
  final String value;

  /// Whether to draw the hairline divider below the row.
  final bool divider;

  /// Overrides the value colour, for example to mark a negative movement.
  final Color? valueColor;

  /// Optional widget after the value, for example a status chip.
  final Widget? trailing;

  /// Overrides the merged announcement, which defaults to
  /// `"<label>, <value>"`.
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    final double fontScale = MediaQuery.textScalerOf(context).scale(1.0);

    final Widget labelText = Text(
      label,
      style: textTheme.bodyMedium?.copyWith(color: colors.textSecondary),
    );
    final Widget valueText = Text(
      value,
      style: textTheme.labelLarge?.copyWith(
        color: valueColor ?? colors.textPrimary,
        fontFeatures: const <FontFeature>[FontFeature.tabularFigures()],
      ),
    );

    final Widget body = LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final bool stack =
            !constraints.maxWidth.isFinite ||
            constraints.maxWidth < _stackBreakpoint ||
            fontScale > _stackFontScale;
        if (stack) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              labelText,
              const SizedBox(height: _stackedGap),
              Row(
                crossAxisAlignment: CrossAxisAlignment.baseline,
                textBaseline: TextBaseline.alphabetic,
                children: <Widget>[
                  Flexible(child: valueText),
                  if (trailing != null) ...<Widget>[
                    const SizedBox(width: 8),
                    trailing!,
                  ],
                ],
              ),
            ],
          );
        }
        return Row(
          crossAxisAlignment: CrossAxisAlignment.baseline,
          textBaseline: TextBaseline.alphabetic,
          children: <Widget>[
            Expanded(child: labelText),
            const SizedBox(width: _inlineGap),
            Flexible(
              child: Align(
                alignment: Alignment.centerRight,
                child: valueText,
              ),
            ),
            if (trailing != null) ...<Widget>[
              const SizedBox(width: 8),
              trailing!,
            ],
          ],
        );
      },
    );

    return Semantics(
      container: true,
      label: semanticLabel ?? '$label, $value',
      excludeSemantics: true,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Padding(padding: _rowPadding, child: body),
          if (divider)
            Divider(
              height: shapes.strokeHairline,
              thickness: shapes.strokeHairline,
              color: colors.hairline,
            ),
        ],
      ),
    );
  }
}
