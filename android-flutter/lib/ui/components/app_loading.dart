/// Loading affordances for the ShortLink design system: skeleton shimmer
/// placeholders and a thin indeterminate top progress bar.
///
/// These replace the old blocking centre dialog: loading is expressed in the
/// shape of the content that is about to arrive, so the layout never jumps and
/// the user is never locked out of the screen.
library;

import 'package:flutter/material.dart';

import '../../theme/app_colors.dart';
import '../../theme/app_motion.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';
import 'poster_card.dart';

/// Height of the thin indeterminate top progress bar.
const double topProgressBarHeight = 3;

/// Radius applied to a skeleton block when the caller does not override it.
const double _skeletonRadius = 6;

/// Height of one line in [AppSkeletonText].
const double skeletonTextLineHeight = 14;

/// Vertical gap between two lines of [AppSkeletonText].
const double skeletonTextLineGap = 8;

/// Fraction of the full line width the last line of [AppSkeletonText] keeps.
///
/// A paragraph's last line is never full width; leaving it short is what makes
/// the placeholder read as text rather than as a filled bar.
const double skeletonTextLastLineFactor = 0.6;

/// Diameter of the default [AppSkeletonCircle] avatar/icon slot.
const double skeletonCircleDiameter = 40;

/// Minimum poster column width used by [AppSkeletonPosterGrid].
///
/// Mirrors the adaptive poster grid on Home (`GridCells.Adaptive(136.dp)`) so
/// the placeholder occupies exactly the tiles the real grid will fill.
const double posterSkeletonMinColumnWidth = 136;

/// Upper bound on the poster skeleton's column count, matching Home.
const int posterSkeletonMaxColumns = 6;

/// Height reserved under each poster tile for the overlaid title strip.
///
/// Home derives its tile height as `tileWidth * 1.5 + 48`; the skeleton uses
/// the same reserve so the swap to real posters does not shift the grid.
const double posterSkeletonOverlayHeight = 48;

/// Number of poster columns [AppSkeletonPosterGrid] draws for [availableWidth].
///
/// Degenerate widths (zero, negative, unbounded) collapse to a single column
/// instead of throwing.
int posterSkeletonColumns(double availableWidth) {
  if (!availableWidth.isFinite || availableWidth <= 0) {
    return 1;
  }
  return (availableWidth / posterSkeletonMinColumnWidth)
      .floor()
      .clamp(1, posterSkeletonMaxColumns);
}

/// A single shimmering placeholder block.
///
/// The block reserves exactly the space the real content will occupy, so the
/// swap from skeleton to content does not shift the layout. Under reduced motion
/// the shimmer stops and the block renders as a flat [AppColors.surfaceAlt]
/// plate, which still communicates "not yet loaded" without moving.
///
/// With no [width] the block spans the available width, which is what lets a
/// skeleton match a full-width row or panel; an explicit width is kept as
/// given. Either way the parent has to bound the block's cross axis.
class AppSkeleton extends StatefulWidget {
  /// Creates a skeleton block.
  const AppSkeleton({
    this.width,
    this.height = 16,
    this.radius,
    this.child,
    super.key,
  });

  /// Block width; null stretches to the available width.
  ///
  /// A null width fills the incoming constraint, so the parent must bound the
  /// cross axis: inside a horizontally unbounded box (a bare [Row], a
  /// horizontal [ListView]) pass an explicit width instead, or the plate would
  /// ask for an infinite one.
  final double? width;

  /// Block height.
  final double height;

  /// Corner radius; defaults to a small inline radius.
  final double? radius;

  /// Optional content rendered above the shimmer plate, for example a glyph
  /// that will survive the swap to real content.
  final Widget? child;

  @override
  State<AppSkeleton> createState() => _AppSkeletonState();
}

class _AppSkeletonState extends State<AppSkeleton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: AppMotion.standard.shimmerCycle,
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final Duration cycle = AppMotion.resolve(
      context,
      appMotionOf(context).shimmerCycle,
    );
    if (cycle == Duration.zero) {
      _controller.stop();
      _controller.value = 0;
      return;
    }
    _controller.duration = cycle;
    if (!_controller.isAnimating) {
      _controller.repeat();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final double radius = widget.radius ?? _skeletonRadius;
    final BorderRadius borderRadius = BorderRadius.circular(radius);
    final Widget plate = DecoratedBox(
      decoration: BoxDecoration(
        color: colors.surfaceAlt,
        borderRadius: borderRadius,
      ),
      child: SizedBox(
        // A null width must fill the incoming constraint: a plain
        // `SizedBox(width: null)` collapses to zero under loose constraints,
        // which made every [AppSkeletonList] row invisible on device.
        width: widget.width ?? double.infinity,
        height: widget.height,
        child: widget.child,
      ),
    );

    if (AppMotion.resolve(context, appMotionOf(context).shimmerCycle) ==
        Duration.zero) {
      return plate;
    }

    return AnimatedBuilder(
      animation: _controller,
      builder: (BuildContext context, Widget? child) {
        final double slide = _controller.value * 2 - 1;
        return ShaderMask(
          blendMode: BlendMode.srcATop,
          shaderCallback: (Rect bounds) => LinearGradient(
            begin: Alignment(-1 + slide, -0.3),
            end: Alignment(1 + slide, 0.3),
            colors: <Color>[
              colors.surfaceAlt,
              colors.hairline.withValues(alpha: 0.9),
              colors.surfaceAlt,
            ],
            stops: const <double>[0.35, 0.5, 0.65],
          ).createShader(bounds),
          child: child,
        );
      },
      child: plate,
    );
  }
}

/// A group of skeleton blocks with a shared shimmer, used for a whole list or
/// card while its data loads.
///
/// Passing the real [itemCount] is what makes the placeholder match the layout
/// that replaces it; when it is not known yet, [itemCount] defaults to 4.
///
/// Every row spans the parent's width minus [padding] and keeps [itemHeight],
/// so the group belongs in a bounded-width parent (a sliver, a page body, an
/// `Expanded`) - the same rule as [AppSkeleton] itself.
class AppSkeletonList extends StatelessWidget {
  /// Creates a skeleton list.
  const AppSkeletonList({
    required this.itemHeight,
    this.itemCount = 4,
    this.spacing = 12,
    this.padding = const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
    this.borderRadius,
    super.key,
  });

  /// Height of one placeholder row.
  final double itemHeight;

  /// Number of placeholder rows.
  final int itemCount;

  /// Vertical gap between rows.
  final double spacing;

  /// Padding around the group.
  final EdgeInsetsGeometry padding;

  /// Optional corner radius for every row.
  final double? borderRadius;

  @override
  Widget build(BuildContext context) => Padding(
    padding: padding,
    child: Column(
      children: <Widget>[
        for (int index = 0; index < itemCount; index++) ...<Widget>[
          if (index > 0) SizedBox(height: spacing),
          AppSkeleton(height: itemHeight, radius: borderRadius),
        ],
      ],
    ),
  );
}

/// A paragraph-shaped skeleton: [lines] full-width bars with a short last line.
///
/// Stretches to the available width, so it belongs in a bounded-width parent
/// (a panel, a list row, a sheet). Every line is an [AppSkeleton], so the
/// shimmer and the reduced-motion collapse are the very same code path as the
/// single block: with `MediaQuery.disableAnimations` set, the whole paragraph
/// degrades to flat [AppColors.surfaceAlt] plates.
class AppSkeletonText extends StatelessWidget {
  /// Creates a text-shaped skeleton.
  const AppSkeletonText({
    this.lines = 3,
    this.lineHeight = skeletonTextLineHeight,
    this.spacing = skeletonTextLineGap,
    this.lastLineFactor = skeletonTextLastLineFactor,
    this.radius,
    super.key,
  });

  /// Number of placeholder lines; at least one is always drawn.
  final int lines;

  /// Height of one line.
  final double lineHeight;

  /// Vertical gap between two lines.
  final double spacing;

  /// Width of the last line as a fraction of the full width, `0.1..1`.
  final double lastLineFactor;

  /// Optional corner radius for every line.
  final double? radius;

  @override
  Widget build(BuildContext context) {
    final int count = lines < 1 ? 1 : lines;
    final double factor = lastLineFactor.clamp(0.1, 1).toDouble();
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        for (int index = 0; index < count; index++) ...<Widget>[
          if (index > 0) SizedBox(height: spacing),
          if (index == count - 1 && factor < 1)
            Align(
              alignment: Alignment.centerLeft,
              child: FractionallySizedBox(
                widthFactor: factor,
                child: AppSkeleton(height: lineHeight, radius: radius),
              ),
            )
          else
            AppSkeleton(height: lineHeight, radius: radius),
        ],
      ],
    );
  }
}

/// A circular skeleton for an avatar or icon slot.
class AppSkeletonCircle extends StatelessWidget {
  /// Creates a circular skeleton.
  const AppSkeletonCircle({
    this.diameter = skeletonCircleDiameter,
    super.key,
  });

  /// Diameter of the circle.
  final double diameter;

  @override
  Widget build(BuildContext context) => AppSkeleton(
    width: diameter,
    height: diameter,
    radius: diameter / 2,
  );
}

/// A poster-grid skeleton whose column count follows the available width.
///
/// The columns are derived with [posterSkeletonColumns] - the same adaptive
/// rule as the real Home shelf - and every tile reserves the poster aspect
/// ratio plus the title overlay, so the grid that replaces it lands exactly on
/// these tiles. Tiles are [AppSkeleton]s, so the shimmer is shared and reduced
/// motion flattens the whole grid into static plates.
class AppSkeletonPosterGrid extends StatelessWidget {
  /// Creates a poster-grid skeleton.
  const AppSkeletonPosterGrid({this.itemCount = 4, this.spacing, super.key});

  /// Number of placeholder tiles.
  final int itemCount;

  /// Gap between tiles; defaults to [AppSpacing.gutter].
  final double? spacing;

  @override
  Widget build(BuildContext context) {
    final double gutter = spacing ?? appSpacingOf(context).gutter;
    final double tileRadius = Theme.of(context)
        .extension<AppShapes>()!
        .radiusContainer;
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final double available = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : posterSkeletonMinColumnWidth;
        final int columns = posterSkeletonColumns(available);
        final double tileWidth =
            (available - gutter * (columns - 1)) / columns;
        final double tileHeight =
            tileWidth / posterAspectRatio + posterSkeletonOverlayHeight;
        final int rowCount = (itemCount + columns - 1) ~/ columns;
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            for (int row = 0; row < rowCount; row++) ...<Widget>[
              if (row > 0) SizedBox(height: gutter),
              SizedBox(
                height: tileHeight,
                child: Row(
                  children: <Widget>[
                    for (int column = 0; column < columns; column++) ...<Widget>[
                      if (column > 0) SizedBox(width: gutter),
                      Expanded(
                        child: row * columns + column < itemCount
                            ? AppSkeleton(height: tileHeight, radius: tileRadius)
                            : const SizedBox.shrink(),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ],
        );
      },
    );
  }
}

/// The thin indeterminate bar shown at the very top of a refreshing screen.
///
/// It is intentionally not a blocking indicator: the screen underneath stays
/// scrollable and interactive while the bar runs. Under reduced motion the
/// indeterminate sweep is stopped and the bar renders as a static accent
/// hairline, which still marks the screen as busy.
class AppTopProgressBar extends StatelessWidget {
  /// Creates the top progress bar.
  const AppTopProgressBar({this.visible = true, super.key});

  /// Whether the bar is shown.
  final bool visible;

  @override
  Widget build(BuildContext context) {
    if (!visible) {
      return const SizedBox.shrink();
    }
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final bool still = MediaQuery.disableAnimationsOf(context);

    return SizedBox(
      height: topProgressBarHeight,
      child: still
          ? ColoredBox(
              color: colors.accentPrimary.withValues(alpha: 0.55),
              child: const SizedBox(
                height: topProgressBarHeight,
                width: double.infinity,
              ),
            )
          : ClipRRect(
              borderRadius: shapes.inlineRadius,
              child: LinearProgressIndicator(
                minHeight: topProgressBarHeight,
                backgroundColor: colors.surfaceAlt,
                color: colors.accentPrimary,
              ),
            ),
    );
  }
}

/// A compact inline loading row: a small spinner plus a label, for the places
/// where a skeleton would be ambiguous (a button-sized action, a sheet footer).
class AppInlineLoading extends StatelessWidget {
  /// Creates an inline loading row.
  const AppInlineLoading({
    required this.label,
    this.size = 16,
    super.key,
  });

  /// Accessible and visible label describing what is loading.
  final String label;

  /// Diameter of the spinner.
  final double size;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    return Semantics(
      container: true,
      liveRegion: true,
      label: label,
      excludeSemantics: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          SizedBox(
            width: size,
            height: size,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              color: colors.accentPrimary,
            ),
          ),
          const SizedBox(width: 10),
          Flexible(
            child: Text(
              label,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(color: colors.textSecondary),
            ),
          ),
        ],
      ),
    );
  }
}
