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

/// Height of the thin indeterminate top progress bar.
const double topProgressBarHeight = 3;

/// Radius applied to a skeleton block when the caller does not override it.
const double _skeletonRadius = 6;

/// A single shimmering placeholder block.
///
/// The block reserves exactly the space the real content will occupy, so the
/// swap from skeleton to content does not shift the layout. Under reduced motion
/// the shimmer stops and the block renders as a flat [AppColors.surfaceAlt]
/// plate, which still communicates "not yet loaded" without moving.
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
        width: widget.width,
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
