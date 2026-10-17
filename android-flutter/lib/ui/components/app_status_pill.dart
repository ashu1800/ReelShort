/// The status pill: a tinted capsule that carries a state with a glyph and a
/// short word.
///
/// Two channels on purpose (design-system §5): the tone maps to a semantic
/// accent *and* the glyph names the state, so neither a colour-blind user nor a
/// screen reader depends on the tint alone. A status is not a control: the pill
/// exposes no tap action and no focus node.
///
/// Layout contract: the capsule sits in a flexible slot inside the component, so
/// a bounded parent makes it ellipsise its label instead of overflowing. A
/// caller that places it in a `Row`'s non-flex slot hands out unbounded width
/// and no widget can shrink there - wrap it in `Flexible` first.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_icons.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';

/// The semantic tone of an [AppStatusPill].
enum AppStatusTone {
  /// Completed, confirmed, active.
  success,

  /// In flight, awaiting confirmation.
  warning,

  /// Failed, rejected, expired.
  danger,

  /// Neutral/informational.
  neutral,
}

/// Alpha of the tone tint behind the pill.
const double _tintAlpha = 0.12;

/// A tinted status capsule with a glyph and a one-line word.
class AppStatusPill extends StatelessWidget {
  /// Creates a status pill.
  const AppStatusPill({
    required this.tone,
    required this.icon,
    required this.label,
    super.key,
  });

  /// Semantic tone of the state.
  final AppStatusTone tone;

  /// Glyph that names the state, so the tone is never the only channel.
  final IconData icon;

  /// Short state word (a word, not a sentence).
  final String label;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppIconSize icons = appIconSizeOf(context);
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final Color accent = switch (tone) {
      AppStatusTone.success => colors.accentSuccess,
      AppStatusTone.warning => colors.accentWarning,
      AppStatusTone.danger => colors.accentDanger,
      AppStatusTone.neutral => colors.textSecondary,
    };

    return Semantics(
      container: true,
      label: label,
      excludeSemantics: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          // The flexible slot is internal: in a bounded parent the capsule
          // shrinks and the label ellipsises.
          Flexible(
            child: Container(
              padding: EdgeInsets.symmetric(
                horizontal: spacing.space2,
                vertical: spacing.space1,
              ),
              decoration: BoxDecoration(
                color: accent.withValues(alpha: _tintAlpha),
                borderRadius: BorderRadius.circular(shapes.radiusPill),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Icon(icon, size: icons.sm, color: accent),
                  SizedBox(width: spacing.space1),
                  Flexible(
                    child: Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: roles.caption.copyWith(
                        color: accent,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
