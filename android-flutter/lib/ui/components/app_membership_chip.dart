/// The membership status chip, shared by every page whose hero reports a VIP
/// state (the Me identity hero and the Funds balance hero).
///
/// Before this component the chip existed twice - once per screen - and the
/// Funds page even imported the Me screen just to reuse its placeholder size.
/// The chip is a *status*, not a control: it exposes no tap action and no focus
/// node, and its state is never carried by the tint alone (the glyph and the
/// word carry it too).
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_icons.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';

/// Footprint of the neutral plate that stands in for the chip while the
/// membership state is unknown, at 1x text scale.
///
/// A hero swaps this plate for the real chip once the account snapshot lands, so
/// the plate is scaled by the ambient text scale: with the chip's label capped
/// at one line, the swap barely moves the layout (measured under 20 px even at
/// 3.2x, where an uncapped label used to add 166 px).
const Size membershipChipPlaceholderSize = Size(112, 22);

/// A compact membership pill: a tinted plate, a state glyph and a word.
///
/// Semantics contract (`docs/ui-redesign-notes.md` §7.2): the chip announces its
/// label and exposes no action, so a screen reader never invites the user to
/// activate a status. The label is capped at one line at every text scale - the
/// full wording stays in the announcement - which keeps a hero's height stable
/// when the placeholder below is replaced by the real chip.
class AppMembershipChip extends StatelessWidget {
  /// Creates a membership chip.
  const AppMembershipChip({required this.active, required this.label, super.key});

  /// Whether the membership is currently active. Drives the accent role and the
  /// glyph, never the wording (the caller passes the already-localised label).
  final bool active;

  /// Chip text, from `AppStrings.accountVipStatusActive` / `...Inactive`.
  final String label;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppIconSize icons = appIconSizeOf(context);
    final AppSpacing spacing = appSpacingOf(context);
    final Color accent = active ? colors.accentSuccess : colors.accentWarning;

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: spacing.space3,
        vertical: spacing.space1,
      ),
      decoration: BoxDecoration(
        color: accent.withValues(alpha: 0.14),
        borderRadius: shapes.inlineRadius,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Icon(
            active ? Icons.verified_outlined : Icons.lock_outline,
            size: icons.sm,
            color: accent,
          ),
          SizedBox(width: spacing.space1),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: accent,
                fontWeight: FontWeight.w700,
                letterSpacing: 1,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
