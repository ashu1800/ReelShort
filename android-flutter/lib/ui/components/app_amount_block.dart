/// The one place a hero figure (balance, points, price) is rendered.
///
/// The funds page pushed its four KPIs through `AppLedgerRow`, the VIP pages
/// concatenated `'${amount} USDT'` inline three times, and the withdrawal
/// confirm step did it again — three spellings of the same number. This widget
/// fixes the typography (numeric role, tabular figures), the unit treatment and
/// the semantics in one place.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';

/// Visual weight of an [AppAmountBlock].
enum AppAmountSize {
  /// Inline figure inside a row or card.
  inline,

  /// Panel-level figure.
  large,

  /// The one hero figure of a page.
  hero,
}

/// A label + value + unit block for money, points and counts.
class AppAmountBlock extends StatelessWidget {
  /// Creates an amount block.
  const AppAmountBlock({
    required this.value,
    this.label,
    this.unit,
    this.size = AppAmountSize.large,
    this.valueColor,
    this.trailing,
    this.semanticLabel,
    super.key,
  });

  /// The formatted figure, already localised (for example `123.45`).
  final String value;

  /// Caption above the figure.
  final String? label;

  /// Unit/caption after the figure (`USDT`, `pt`).
  final String? unit;

  /// Visual weight of the figure.
  final AppAmountSize size;

  /// Overrides the figure colour (rewards, danger, success).
  final Color? valueColor;

  /// Optional widget under the figure.
  final Widget? trailing;

  /// Overrides the merged semantic label (defaults to `label value unit`).
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppSpacing spacing = appSpacingOf(context);

    final TextStyle valueStyle = switch (size) {
      AppAmountSize.inline => roles.numeric,
      AppAmountSize.large => roles.numericLarge,
      AppAmountSize.hero => roles.numericHero,
    }.copyWith(color: valueColor ?? colors.textPrimary);

    final String label = <String?>[
      this.label,
      value,
      unit,
    ].where((String? part) => part != null && part.isNotEmpty).join(', ');

    return Semantics(
      label: semanticLabel ?? label,
      child: ExcludeSemantics(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            if (this.label != null) ...<Widget>[
              Text(
                this.label!,
                style: roles.caption.copyWith(color: colors.textSecondary),
              ),
              SizedBox(height: spacing.space1),
            ],
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Flexible(child: Text(value, style: valueStyle)),
                if (unit != null && unit!.isNotEmpty) ...<Widget>[
                  SizedBox(width: spacing.space1),
                  // The unit is flexed too: a long unit ("USDT / 30 days")
                  // ellipsises instead of overflowing the row at large text
                  // scales, which is what used to force callers to fold the
                  // unit into `value` as a workaround.
                  Flexible(
                    child: Text(
                      unit!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: roles.caption.copyWith(
                        color: colors.textSecondary,
                      ),
                    ),
                  ),
                ],
              ],
            ),
            if (trailing != null) ...<Widget>[
              SizedBox(height: spacing.space2),
              trailing!,
            ],
          ],
        ),
      ),
    );
  }
}
