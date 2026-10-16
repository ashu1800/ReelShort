/// The design system's empty/placeholder state.
///
/// Mirrors the original contract exactly: when an action callback is supplied
/// the state renders a real button, and when it is absent it renders a
/// **non-interactive pill** that carries the same words as a status label
/// instead of a dead control.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import 'app_button.dart';

/// Diameter of the tinted glyph plate above the title.
const double _glyphPlateSize = 56;

/// Size of the glyph inside the plate.
const double _glyphSize = 26;

/// Corner radius of the non-interactive status pill.
const double _pillRadius = 999;

/// Horizontal padding inside the status pill.
const EdgeInsets _pillPadding = EdgeInsets.symmetric(
  horizontal: 14,
  vertical: 8,
);

/// Vertical rhythm between the state's parts.
const double _gap = 10;

/// A centred empty state: glyph, title, optional body, and either an action
/// button or a status pill.
///
/// The widget never overflows at large text scales: it is wrapped in a
/// vertically scrollable, width-bounded column so a 3.2x scale simply scrolls
/// instead of clipping.
class AppEmptyState extends StatelessWidget {
  /// Creates an empty state.
  const AppEmptyState({
    required this.icon,
    required this.title,
    this.body,
    this.actionLabel,
    this.onAction,
    this.pillLabel,
    super.key,
  });

  /// Leading glyph, rendered inside a tinted plate.
  final IconData icon;

  /// Headline copy. Always required.
  final String title;

  /// Optional supporting copy.
  final String? body;

  /// Label of the action button. Required to use [onAction].
  final String? actionLabel;

  /// Action callback. A button is rendered only when this is non-null **and**
  /// [actionLabel] carries non-whitespace text; a callback without a usable
  /// label renders no control at all (see [appEmptyStateShowsAction]), and a
  /// label without a callback renders as the non-interactive status pill.
  final VoidCallback? onAction;

  /// Overrides the pill text when no action is rendered. Falls back to
  /// [actionLabel], so the words the user was offered still reach them.
  final String? pillLabel;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final TextTheme textTheme = Theme.of(context).textTheme;
    // A button only exists when there is both a callback and words for it; a
    // label with no callback becomes the non-interactive pill instead, and a
    // callback with no usable label renders no control at all.
    final bool interactive = appEmptyStateShowsAction(
      onAction: onAction,
      actionLabel: actionLabel,
    );
    final String? buttonText = interactive ? _nonEmpty(actionLabel) : null;
    final String? resolvedPill = appEmptyStatePillLabel(
      onAction: onAction,
      actionLabel: actionLabel,
      pillLabel: pillLabel,
    );

    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        // Keep the copy readable on a 1440 px tablet instead of stretching a
        // single line across the whole viewport.
        final double maxWidth = constraints.maxWidth.isFinite
            ? constraints.maxWidth.clamp(0, 420).toDouble()
            : 420;
        return SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
          child: Center(
            child: ConstrainedBox(
              constraints: BoxConstraints(maxWidth: maxWidth),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.center,
                children: <Widget>[
                  ExcludeSemantics(
                    child: SizedBox(
                      width: _glyphPlateSize,
                      height: _glyphPlateSize,
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          color: colors.surfaceAlt,
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: colors.hairline,
                            width: shapes.strokeHairline,
                          ),
                        ),
                        child: Icon(
                          icon,
                          size: _glyphSize,
                          color: colors.textSecondary,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: _gap + 6),
                  Text(
                    title,
                    textAlign: TextAlign.center,
                    style: textTheme.titleMedium,
                  ),
                  if (body != null && body!.trim().isNotEmpty) ...<Widget>[
                    const SizedBox(height: _gap - 2),
                    Text(
                      body!,
                      textAlign: TextAlign.center,
                      style: textTheme.bodyMedium?.copyWith(
                        color: colors.textSecondary,
                      ),
                    ),
                  ],
                  if (buttonText != null) ...<Widget>[
                    const SizedBox(height: _gap + 8),
                    AppButton(
                      label: buttonText,
                      onPressed: onAction,
                      variant: AppButtonVariant.secondary,
                      icon: icon,
                    ),
                  ] else if (resolvedPill != null) ...<Widget>[
                    const SizedBox(height: _gap + 8),
                    _StatusPill(label: resolvedPill),
                  ],
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Returns [value] when it carries non-whitespace text, and null otherwise.
String? _nonEmpty(String? value) {
  final String? trimmed = value?.trim();
  return (trimmed == null || trimmed.isEmpty) ? null : trimmed;
}

/// Whether [AppEmptyState] renders a real action button.
///
/// The button needs **both** a callback and words for it. A callback with a
/// blank or missing label renders no button at all - not a disabled one, which
/// would still be announced as a control the user cannot use.
bool appEmptyStateShowsAction({
  required VoidCallback? onAction,
  String? actionLabel,
}) => onAction != null && _nonEmpty(actionLabel) != null;

/// Text of [AppEmptyState]'s non-interactive status pill, or null when the
/// state has nothing to say.
///
/// Priority: an interactive action wins and the pill is not drawn; otherwise
/// [pillLabel]; otherwise [actionLabel], so a label that lost its callback
/// still reaches the user as a status word instead of a dead control.
String? appEmptyStatePillLabel({
  required VoidCallback? onAction,
  String? actionLabel,
  String? pillLabel,
}) => appEmptyStateShowsAction(onAction: onAction, actionLabel: actionLabel)
    ? null
    : (_nonEmpty(pillLabel) ?? _nonEmpty(actionLabel));

/// A non-interactive status pill.
///
/// Rendered with [Semantics] but no tap handler, no focus node and no ink
/// response: it announces itself as a status, not as a control, so a screen
/// reader user is never invited to activate something that does nothing.
class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    return Semantics(
      container: true,
      label: label,
      excludeSemantics: true,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: colors.surfaceAlt,
          borderRadius: BorderRadius.circular(_pillRadius),
          border: Border.all(
            color: colors.hairline,
            width: shapes.strokeHairline,
          ),
        ),
        child: Padding(
          padding: _pillPadding,
          child: Text(
            label,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
              color: colors.textSecondary,
            ),
          ),
        ),
      ),
    );
  }
}
