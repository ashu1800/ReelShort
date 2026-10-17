/// Regression suite for the ShortLink design system.
///
/// Covers the contracts documented in `docs/design-system.md`: the token
/// extensions and their contrast targets, the render matrix across both
/// brightnesses and from 320 px to 1440 px at up to 3.2x text scale, merged
/// semantics and screen-reader activation for every interactive component, the
/// episode-spine description and VIP gate boundaries, and the reduced-motion
/// collapse.
library;

import 'dart:math' as math;

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:shortlink/core/formatting/app_strings.dart';
import 'package:shortlink/core/formatting/app_ui_copy.dart';
import 'package:shortlink/core/formatting/ui_formats.dart';
import 'package:shortlink/data/models/app_language.dart';
import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/state/app_ui_state.dart';
import 'package:shortlink/theme/app_colors.dart';
import 'package:shortlink/theme/app_icons.dart';
import 'package:shortlink/theme/app_media.dart';
import 'package:shortlink/theme/app_motion.dart';
import 'package:shortlink/theme/app_shapes.dart';
import 'package:shortlink/theme/app_spacing.dart';
import 'package:shortlink/theme/app_theme.dart';
import 'package:shortlink/theme/app_typography.dart';
import 'package:shortlink/ui/components/app_amount_block.dart';
import 'package:shortlink/ui/components/app_bottom_action_bar.dart';
import 'package:shortlink/ui/components/app_button.dart';
import 'package:shortlink/ui/components/app_confirm_dialog.dart';
import 'package:shortlink/ui/components/app_empty_state.dart';
import 'package:shortlink/ui/components/app_list_row.dart';
import 'package:shortlink/ui/components/app_loading.dart';
import 'package:shortlink/ui/components/app_membership_chip.dart';
import 'package:shortlink/ui/components/app_message_bar.dart';
import 'package:shortlink/ui/components/app_panel.dart';
import 'package:shortlink/ui/components/app_section.dart';
import 'package:shortlink/ui/components/app_segmented_control.dart';
import 'package:shortlink/ui/components/app_sheet.dart';
import 'package:shortlink/ui/components/app_text_field.dart';
import 'package:shortlink/ui/components/episode_spine.dart';
import 'package:shortlink/ui/components/ledger_row.dart';
import 'package:shortlink/ui/components/poster_card.dart';

/// Every piece of text the current semantics tree exposes, in traversal order.
///
/// The tree is read through the semantics owner rather than through a finder, so
/// a label that was merged into an ancestor simply does not appear: that is what
/// makes this a proof that a card did not collapse into one node. Both channels
/// are collected, because a read-only text field (the selectable support email)
/// reports its text as a value rather than as a label.
List<String> semanticsTexts(WidgetTester tester) {
  final Iterable<RenderView> views = tester.binding.renderViews;
  final SemanticsNode? root = views.isEmpty
      ? null
      : views.first.owner?.semanticsOwner?.rootSemanticsNode;
  if (root == null) {
    return const <String>[];
  }
  final List<String> texts = <String>[];
  void walk(SemanticsNode node) {
    final SemanticsData data = node.getSemanticsData();
    if (data.label.isNotEmpty) {
      texts.add(data.label);
    }
    if (data.value.isNotEmpty) {
      texts.add(data.value);
    }
    node.visitChildren((SemanticsNode child) {
      walk(child);
      return true;
    });
  }

  walk(root);
  return texts;
}

Widget host(
  Widget child, {
  Brightness brightness = Brightness.light,
  double width = 360,
  double height = 720,
  double textScale = 1,
  bool disableAnimations = false,
  Key? appKey,
}) {
  return MaterialApp(
      key: appKey,
      theme: appTheme(brightness),
      // Inside MaterialApp on purpose: MaterialApp builds its own MediaQuery
      // from the view, so an outer one would be ambiguous.
      home: MediaQuery(
        data: MediaQueryData(
          size: Size(width, height),
          textScaler: TextScaler.linear(textScale),
          disableAnimations: disableAnimations,
        ),
        child: Scaffold(
          body: SizedBox(
            width: width,
            height: height,
            child: SingleChildScrollView(child: child),
          ),
        ),
      ),
    );
}

void main() {
  test('theme exposes every token extension in both brightnesses', () {
    for (final Brightness b in Brightness.values) {
      final ThemeData t = appTheme(b);
      expect(t.extension<AppColors>(), isNotNull);
      expect(t.extension<AppTypography>(), isNotNull);
      expect(t.extension<AppShapes>(), isNotNull);
      expect(t.extension<AppMotion>(), isNotNull);
      expect(t.extension<AppSpacing>(), isNotNull);
      expect(t.extension<AppIconSize>(), isNotNull);
      expect(t.extension<AppMedia>(), isNotNull);
      expect(t.colorScheme.brightness, b);
      expect(t.scaffoldBackgroundColor, t.extension<AppColors>()!.canvas);
      // Sub-page app bars must use the single page-title role, not the Material
      // default (which the screens disagreed about before the redesign).
      expect(
        t.appBarTheme.titleTextStyle?.fontSize,
        AppTypography.light.screenTitle.fontSize,
      );
      // Every text style must carry an explicit colour (ThemeData merges over
      // the platform theme, whose defaults are black54/white).
      for (final TextStyle? s in <TextStyle?>[
        t.textTheme.displayLarge,
        t.textTheme.titleLarge,
        t.textTheme.bodyMedium,
        t.textTheme.bodySmall,
        t.textTheme.labelLarge,
        t.textTheme.labelSmall,
      ]) {
        expect(s?.color, isNotNull, reason: 'text style colour must be set');
      }
      // Extension-only roles must not inherit a colour either.
      for (final TextStyle s in <TextStyle>[
        t.extension<AppTypography>()!.screenTitle,
        t.extension<AppTypography>()!.caption,
        t.extension<AppTypography>()!.numericLarge,
        t.extension<AppTypography>()!.numericHero,
        t.extension<AppTypography>()!.onMediaBody,
      ]) {
        expect(s.color, isNotNull);
      }
    }
  });

  test('presentation helpers group digits and trim timestamps', () {
    expect(groupedDigits(0), '0');
    expect(groupedDigits(999), '999');
    expect(groupedDigits(1000), '1,000');
    expect(groupedDigits(29184), '29,184');
    expect(groupedDigits(-1234567), '-1,234,567');
    expect(dateOnlyLabel('2026-11-13 00:00:00'), '2026-11-13');
    expect(dateOnlyLabel('2026-11-13T00:00:00Z'), '2026-11-13');
    expect(dateOnlyLabel(''), '');
    expect(dateOnlyLabel(null), '');
  });

  test('spacing, icon and media tokens are complete and stable', () {
    final AppSpacing spacing = AppSpacing.standard;
    expect(
      <double>[
        spacing.space1,
        spacing.space2,
        spacing.space3,
        spacing.space4,
        spacing.space5,
        spacing.space6,
        spacing.space8,
        spacing.space10,
      ],
      orderedEquals(<double>[4, 8, 12, 16, 20, 24, 32, 40]),
    );
    expect(spacing.pageHorizontal, 18);
    expect(spacing.gutter, 12);
    expect(spacing.rowMinHeight, greaterThanOrEqualTo(44));
    expect(
      spacing.pagePadding,
      EdgeInsets.fromLTRB(
        spacing.pageHorizontal,
        spacing.pageTopInset,
        spacing.pageHorizontal,
        spacing.pageBottomInset,
      ),
    );

    // Layout must not shift when the theme changes.
    expect(AppSpacing.light, AppSpacing.dark);
    expect(AppIconSize.light, AppIconSize.dark);
    expect(AppMedia.light, AppMedia.dark);

    final AppIconSize icons = AppIconSize.standard;
    expect(
      <double>[icons.sm, icons.md, icons.lg],
      orderedEquals(<double>[16, 20, 24]),
    );
    expect(icons.hero, 48);

    // copyWith/lerp are total for the three new extensions.
    expect(AppSpacing.standard.copyWith().space4, AppSpacing.standard.space4);
    expect(AppIconSize.standard.copyWith().lg, AppIconSize.standard.lg);
    expect(AppMedia.standard.copyWith().onMedia, AppMedia.standard.onMedia);
    expect(AppSpacing.standard.lerp(AppSpacing.standard, 0.5).space4, 16);
    expect(AppIconSize.standard.lerp(AppIconSize.standard, 0.5).lg, 24);
    expect(
      AppMedia.standard.lerp(AppMedia.standard, 0.5).onMedia,
      const Color(0xFFFFFFFF),
    );
    expect(AppSpacing.standard.copyWith(space4: 20).space4, 20);
    expect(AppIconSize.standard.copyWith(md: 18).md, 18);
    expect(
      AppMedia.standard.copyWith(cellIdle: const Color(0xFF000000)).cellIdle,
      const Color(0xFF000000),
    );
  });

  test('colour tokens lerp and copyWith are total', () {
    final AppColors lerped = AppColors.light.lerp(AppColors.dark, 0.5);
    expect(lerped.canvas, isNot(AppColors.light.canvas));
    expect(AppColors.light.copyWith().canvas, AppColors.light.canvas);
    expect(AppColors.light.premiumSurface.a, closeTo(0.08, 0.01));
    expect(AppColors.dark.premiumSurface.a, closeTo(0.14, 0.01));
    final AppTypography typ = AppTypography.light.lerp(AppTypography.dark, 0.5);
    expect(typ.body.fontSize, AppTypography.light.body.fontSize);
    final AppShapes sh = AppShapes.light.lerp(AppShapes.dark, 0.5);
    expect(sh.radiusControl, 10);
    expect(AppMotion.standard.lerp(AppMotion.dark, 0.9).reelChange.inMilliseconds, 220);
  });

  testWidgets('every component renders in light+dark, narrow+wide, huge text', (
    WidgetTester tester,
  ) async {
    final List<EpisodeTick> ticks = <EpisodeTick>[
      for (int i = 1; i <= 12; i++)
        EpisodeTick(
          number: i,
          state: i == 3
              ? EpisodeTickState.current
              : i == 4
              ? EpisodeTickState.inProgress
              : i < 3
              ? EpisodeTickState.watched
              : EpisodeTickState.none,
          progress: i == 4 ? 40 : 0,
          isVipLocked: i >= 8,
          onTap: () {},
        ),
    ];

    final Map<String, Widget> cases = <String, Widget>{
      'buttons': Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          for (final AppButtonVariant v in AppButtonVariant.values)
            AppButton(
              label: 'Primary action label',
              onPressed: () {},
              variant: v,
              icon: Icons.play_arrow,
            ),
          const AppButton(
            label: 'Loading state',
            onPressed: null,
            isLoading: true,
          ),
          AppButton(
            label: 'Expanded',
            onPressed: () {},
            isExpanded: true,
          ),
        ],
      ),
      'text field': Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          AppTextField(
            label: 'Username',
            helperText: 'At least 8 characters',
            errorText: 'Password too short',
            textInputAction: TextInputAction.done,
            onSubmitted: (String _) {},
          ),
          const SizedBox(height: 16),
          AppTextField(
            label: 'Password',
            obscureText: true,
            textInputAction: TextInputAction.done,
            onSubmitted: (String _) {},
          ),
          const SizedBox(height: 16),
          const AppTextField(
            label: 'Without the reveal toggle',
            obscureText: true,
            showObscureToggle: false,
          ),
        ],
      ),
      'message bars': Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          for (final UiMessageType t in UiMessageType.values)
            AppMessageBar(
              message: 'Tone ${t.name} long enough to need wrapping here',
              type: t,
              autoDismiss: const Duration(seconds: 30),
            ),
        ],
      ),
      'loading': const Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          AppSkeleton(width: 120, height: 20),
          AppSkeletonList(itemHeight: 48, itemCount: 3),
          AppTopProgressBar(),
          AppInlineLoading(label: 'Loading episodes'),
        ],
      ),
      'loading variants': const Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          AppSkeletonText(lines: 3),
          SizedBox(height: 16),
          AppSkeletonCircle(),
          SizedBox(height: 16),
          AppSkeletonPosterGrid(itemCount: 5),
          SizedBox(height: 16),
          AppSkeletonText(
            lines: 6,
            lineHeight: 12,
            lastLineFactor: 0.35,
            radius: 4,
          ),
        ],
      ),
      'empty with action': AppEmptyState(
        icon: Icons.search_off,
        title: 'No results',
        body: 'Try a different keyword.',
        actionLabel: 'Clear filters',
        onAction: () {},
      ),
      'empty with pill': const AppEmptyState(
        icon: Icons.lock_outline,
        title: 'VIP required',
        body: 'Episode 8 and later need VIP.',
        pillLabel: 'VIP only',
      ),
      'empty with long pill': const AppEmptyState(
        icon: Icons.lock_outline,
        title: 'VIP required',
        body: 'Episode 8 and later need VIP.',
        pillLabel: 'VIP membership is required for this episode',
      ),
      'empty with a label but no callback': const AppEmptyState(
        icon: Icons.search_off,
        title: 'Nothing here yet',
        actionLabel: 'Clear filters',
      ),
      'poster': const SizedBox(
        width: 136,
        child: PosterCard(
          title: 'A Very Long Drama Title That Wraps',
          coverUrl: null,
          episodeCount: 40,
          currentEpisode: 5,
          watchedEpisodeCount: 4,
        ),
      ),
      'spine full': EpisodeSpine(ticks: ticks),
      'spine mini capped': EpisodeSpine(
        ticks: ticks,
        size: EpisodeSpineSize.mini,
        availableHeight: 120,
      ),
      'spine inline': EpisodeSpine(
        ticks: ticks,
        size: EpisodeSpineSize.inline,
      ),
      'ledger': const Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          AppLedgerRow(label: 'Available balance', value: '1,234,567 pts'),
          AppLedgerRow(label: 'Rate', value: '\$0.14'),
          AppLedgerRow(
            label: 'Submitted',
            value: '2026-10-14 13:45',
            divider: false,
          ),
        ],
      ),
      'panels': Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const AppPanel(child: Text('Panel body copy that can wrap')),
          const SizedBox(height: 12),
          AppPanel(
            onTap: () {},
            semanticLabel: 'Tappable panel',
            child: const Text('Tappable panel'),
          ),
          const SizedBox(height: 12),
          const AppPanel(selected: true, child: Text('Selected panel')),
        ],
      ),
      'list rows': Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          AppListRow(
            title: 'Watch history',
            subtitle: 'Pick up from your last episode',
            leadingIcon: Icons.history,
            showChevron: true,
            onTap: () {},
          ),
          const AppListRow(
            title: 'Disabled row',
            subtitle: 'No action is exposed',
            leadingIcon: Icons.lock_outline,
          ),
          AppListRow(
            title: 'Last row of a group',
            leadingIcon: Icons.person_outline,
            showChevron: true,
            divider: false,
            trailing: const Text('v0.9.2'),
            onTap: () {},
          ),
          AppListRow(
            title: 'Single-line value',
            subtitle: '0x1111111111111111111111111111111111111111',
            subtitleMaxLines: 1,
            leadingIcon: Icons.account_balance_wallet_outlined,
            divider: false,
            trailingTakesIntrinsicWidth: true,
            trailing: TextButton(
              onPressed: () {},
              child: const Text('Replace wallet'),
            ),
          ),
        ],
      ),
      'section card mixing rows': AppSectionCard(
        children: <Widget>[
          AppListRow(
            title: 'Tappable row',
            subtitle: 'Opens something',
            leadingIcon: Icons.history,
            divider: false,
            onTap: () {},
          ),
          AppListRow(
            title: 'Plain row',
            subtitle: 'No action is exposed',
            leadingIcon: Icons.lock_outline,
            divider: false,
            trailing: TextButton(
              onPressed: () {},
              child: const Text('Row action'),
            ),
          ),
        ],
      ),
      'membership chips': const Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          AppMembershipChip(active: true, label: 'VIP active'),
          SizedBox(height: 12),
          AppMembershipChip(active: false, label: 'VIP not active'),
        ],
      ),
      'section card': AppSectionCard(
        title: 'Details',
        actionLabel: AppUiCopy.seeAll,
        onAction: () {},
        children: <Widget>[
          const AppListRow(title: 'Points', trailing: Text('1,234'), divider: false),
          const AppListRow(title: 'Orders', trailing: Text('3'), divider: false),
        ],
      ),
      'amounts': const Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          AppAmountBlock(label: 'Available balance', value: '1,234,567', unit: 'pt'),
          SizedBox(height: 12),
          AppAmountBlock(value: '12.34', unit: 'USDT', size: AppAmountSize.inline),
          SizedBox(height: 12),
          AppAmountBlock(
            label: 'Points earned today',
            value: '245',
            size: AppAmountSize.hero,
          ),
        ],
      ),
      'bottom action bar': AppBottomActionBar(
        children: <Widget>[
          AppButton(
            label: 'Cancel',
            variant: AppButtonVariant.secondary,
            onPressed: () {},
          ),
          AppButton(
            label: 'Confirm',
            variant: AppButtonVariant.destructive,
            onPressed: () {},
          ),
        ],
      ),
      'segmented control': AppSegmentedControl<String>(
        value: 'login',
        semanticLabel: 'Authentication mode',
        options: const <AppSegmentedOption<String>>[
          AppSegmentedOption<String>(value: 'login', label: 'Sign in'),
          AppSegmentedOption<String>(value: 'register', label: 'Create account'),
        ],
        onChanged: (String _) {},
      ),
      'confirm dialog': AppConfirmDialog(
        title: 'Sign out?',
        body:
            'You can sign in again at any time; your points and orders stay on your account.',
        confirmLabel: 'Sign out',
        cancelLabel: 'Cancel',
        destructive: true,
        onConfirm: () {},
        onCancel: () {},
      ),
      'sheet body': AppSheet(
        title: 'Wallet',
        onClose: () {},
        child: const AppListRow(
          title: 'ERC20 wallet',
          subtitle: 'Bind, replace or unbind',
          divider: false,
        ),
      ),
    };

    for (final MapEntry<String, Widget> entry in cases.entries) {
      for (final Brightness b in Brightness.values) {
        for (final double w in <double>[320, 1440]) {
          for (final double scale in <double>[1, 2, 3.2]) {
            await tester.pumpWidget(
              host(
                entry.value,
                brightness: b,
                width: w,
                textScale: scale,
                appKey: ValueKey<String>('${entry.key}-$b-$w-$scale'),
              ),
            );
            await tester.pump(const Duration(milliseconds: 50));
            expect(
              tester.takeException(),
              isNull,
              reason: '${entry.key} at $b ${w}px scale $scale',
            );
          }
        }
      }
    }
  });

  testWidgets('AppButton fires once per tap and per keyboard activation', (
    WidgetTester tester,
  ) async {
    int taps = 0;
    await tester.pumpWidget(
      host(AppButton(label: 'Go', onPressed: () => taps++), width: 320),
    );
    await tester.tap(find.text('Go'));
    expect(taps, 1);

    // Reset for the keyboard path.
    taps = 0;
    await tester.pumpWidget(
      host(
        AppButton(
          label: 'Go',
          onPressed: () => taps++,
          autofocus: true,
          variant: AppButtonVariant.secondary,
        ),
        width: 320,
      ),
    );
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pump();
    expect(taps, 1, reason: 'Enter must activate exactly once');
  });

  testWidgets('disabled button never fires', (WidgetTester tester) async {
    await tester.pumpWidget(
      host(
        const AppButton(label: 'Go', onPressed: null),
        width: 320,
      ),
    );
    await tester.tap(find.text('Go'));
    expect(tester.takeException(), isNull);
  });

  testWidgets('PosterCard merges into one semantics node with the contract '
      'description and hides the image', (WidgetTester tester) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    await tester.pumpWidget(
      host(
        SizedBox(
          width: 160,
          child: PosterCard(
            title: 'Drama',
            coverUrl: null,
            episodeCount: 12,
            onTap: () {},
          ),
        ),
        width: 320,
      ),
    );
    final String expected = posterCardContentDescription(
      'Drama',
      12,
      AppLanguage.defaultLanguage,
    );
    expect(expected, 'Drama, 12${strings(AppLanguage.defaultLanguage).posterEpisodeUnit}');
    expect(find.bySemanticsLabel(expected), findsOneWidget);
    expect(posterImageIsDecorativeInsideCard(), isTrue);
    handle.dispose();
  });

  testWidgets('PosterCard can hide its title overlay and keeps its label', (
    WidgetTester tester,
  ) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    final String expected = posterCardContentDescription(
      'Drama',
      12,
      AppLanguage.defaultLanguage,
    );

    // Default: the poster-grid treatment paints the title on the artwork.
    await tester.pumpWidget(
      host(
        const SizedBox(
          width: 160,
          child: PosterCard(title: 'Drama', coverUrl: null, episodeCount: 12),
        ),
        width: 320,
      ),
    );
    expect(find.text('Drama'), findsOneWidget);

    // A row that prints the title beside the card passes false: the artwork no
    // longer repeats it, while the merged accessibility name stays complete.
    await tester.pumpWidget(
      host(
        const SizedBox(
          width: 160,
          child: PosterCard(
            title: 'Drama',
            coverUrl: null,
            episodeCount: 12,
            showTitleOverlay: false,
          ),
        ),
        width: 320,
      ),
    );
    await tester.pump();
    expect(find.text('Drama'), findsNothing);
    expect(find.bySemanticsLabel(expected), findsOneWidget);
    // The spine and the fallback plate still render in the card.
    expect(find.byType(EpisodeSpine), findsOneWidget);
    expect(find.byIcon(Icons.movie_outlined), findsOneWidget);
    expect(tester.takeException(), isNull);
    handle.dispose();
  });

  testWidgets('PosterCard title line count follows posterOverlayTitleMaxLines', (
    WidgetTester tester,
  ) async {
    for (final double scale in <double>[1, 1.4]) {
      await tester.pumpWidget(
        host(
          const SizedBox(
            width: 160,
            child: PosterCard(
              title: 'A Very Long Drama Title Indeed',
              coverUrl: null,
              episodeCount: 12,
            ),
          ),
          width: 320,
          textScale: scale,
        ),
      );
      final Text title = tester.widget<Text>(
        find.text('A Very Long Drama Title Indeed'),
      );
      expect(title.maxLines, posterOverlayTitleMaxLines(scale));
    }
  });

  testWidgets('PosterCard falls back gracefully when there is no usable cover', (
    WidgetTester tester,
  ) async {
    expect('not a url'.coverUrlOrNull(), 'not a url');
    expect('   '.coverUrlOrNull(), isNull);
    expect(null.coverUrlOrNull(), isNull);
    for (final String? url in <String?>[null, '   ', '']) {
      await tester.pumpWidget(
        host(
          SizedBox(
            width: 160,
            child: PosterCard(title: 'Drama', coverUrl: url, episodeCount: 3),
          ),
          width: 320,
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull);
      expect(
        find.byIcon(Icons.movie_outlined),
        findsOneWidget,
        reason: 'no cover -> fallback plate for $url',
      );
      expect(find.byType(CachedNetworkImage), findsNothing);
    }
    // A non-blank URL is handed to the cache; a load failure swaps in the same
    // plate via errorWidget, which the widget test HTTP stub triggers for us.
    await tester.pumpWidget(
      host(
        const SizedBox(
          width: 160,
          child: PosterCard(
            title: 'Drama',
            coverUrl: 'https://example.test/cover.jpg',
            episodeCount: 3,
          ),
        ),
        width: 320,
      ),
    );
    await tester.pump();
    expect(find.byType(CachedNetworkImage), findsOneWidget);
    await tester.pump(const Duration(seconds: 1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('AppMessageBar is a live region, dismisses on tap and on timeout', (
    WidgetTester tester,
  ) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    int dismissed = 0;
    await tester.pumpWidget(
      host(
        AppMessageBar(
          message: 'Saved',
          type: UiMessageType.info,
          autoDismiss: const Duration(milliseconds: 200),
          onDismissed: () => dismissed++,
        ),
        width: 320,
      ),
    );
    expect(topMessageUsesLiveRegion(), isTrue);
    final Semantics live = tester.widget<Semantics>(
      find
          .descendant(
            of: find.byType(AppMessageBar),
            matching: find.byWidgetPredicate(
              (Widget w) => w is Semantics && w.properties.liveRegion == true,
            ),
          )
          .first,
    );
    expect(live.properties.liveRegion, isTrue);
    expect(live.properties.label, 'Info: Saved');
    expect(messageVisualTone(UiMessageType.info), MessageVisualTone.info);
    expect(messageVisualTone(UiMessageType.error), MessageVisualTone.error);
    expect(messageVisualTone(UiMessageType.success), MessageVisualTone.success);

    // The tone glyph and the tone word are both present, so tone is never
    // carried by colour alone.
    expect(find.text('Info'), findsOneWidget);
    expect(find.byIcon(Icons.info_outline), findsOneWidget);

    await tester.pump(const Duration(milliseconds: 400));
    expect(dismissed, 1, reason: 'timeout must dismiss');

    // A new message resets the lifecycle and a tap dismisses immediately.
    await tester.pumpWidget(
      host(
        AppMessageBar(
          message: 'Changed',
          type: UiMessageType.error,
          autoDismiss: const Duration(seconds: 30),
          onDismissed: () => dismissed++,
        ),
        width: 320,
      ),
    );
    await tester.tap(find.text('Changed'));
    await tester.pump();
    expect(dismissed, 2);
    handle.dispose();
  });

  testWidgets('reduced motion collapses every duration to zero', (
    WidgetTester tester,
  ) async {
    Duration? seen;
    await tester.pumpWidget(
      host(
        Builder(
          builder: (BuildContext context) {
            seen = resolveAppMotion(
              context,
              appMotionOf(context).reelChange,
            );
            return const SizedBox.shrink();
          },
        ),
        disableAnimations: true,
      ),
    );
    expect(seen, Duration.zero);

    // Skeletons and the top bar must still build with motion disabled. The
    // indeterminate spinner keeps spinning on purpose: it is the only signal
    // that a background action is in flight, so it is exempt from the
    // reduced-motion collapse (documented in docs/design-system.md).
    await tester.pumpWidget(
      host(
        const Column(
          children: <Widget>[
            AppSkeleton(width: 80, height: 16),
            AppTopProgressBar(),
            AppInlineLoading(label: 'Loading'),
          ],
        ),
        disableAnimations: true,
      ),
    );
    await tester.pump(const Duration(milliseconds: 200));
    expect(tester.takeException(), isNull);
  });

  test('EpisodeSpine describes its state and marks the VIP gate at episode 8', () {
    expect(episodeSpineGateEpisode, 8);
    expect(defaultVipFreeEpisodes, 7);
    final AppStrings copy = strings(AppLanguage.defaultLanguage);
    final String described = EpisodeSpine.describe(<EpisodeTick>[
      for (int i = 1; i <= 12; i++)
        EpisodeTick(
          number: i,
          state: i == 3
              ? EpisodeTickState.current
              : i == 4
              ? EpisodeTickState.inProgress
              : i < 3
              ? EpisodeTickState.watched
              : EpisodeTickState.none,
          progress: i == 4 ? 40 : 0,
        ),
    ]);
    expect(described.contains('12${copy.playerEpisodeUnit}'), isTrue);
    expect(described.contains('2 ${copy.playerEpisodeStatusWatched}'), isTrue);
    expect(
      described.contains(
        '${copy.playerEpisodeStatusCurrent} ${copy.playerEpisodePrefix}03',
      ),
      isTrue,
    );
    expect(EpisodeSpine.describe(const <EpisodeTick>[]), '');
    expect(EpisodeTick.unwatched(3).length, 3);
    expect(EpisodeTick.unwatched(3, isVipLocked: (int e) => e >= 2).last.isVipLocked, isTrue);
    expect(const EpisodeTick(number: 1, state: EpisodeTickState.inProgress, progress: 250).clampedProgress, 100);
    expect(const EpisodeTick(number: 1, state: EpisodeTickState.none, progress: -4).clampedProgress, 0);
  });

  testWidgets('EpisodeSpine ticks are individually labelled and tappable', (
    WidgetTester tester,
  ) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    final List<int> tapped = <int>[];
    final AppStrings copy = strings(AppLanguage.defaultLanguage);
    await tester.pumpWidget(
      host(
        EpisodeSpine(
          ticks: <EpisodeTick>[
            EpisodeTick(
              number: 8,
              state: EpisodeTickState.watched,
              onTap: () => tapped.add(8),
            ),
            EpisodeTick(number: 9, state: EpisodeTickState.none, onTap: () {}),
          ],
        ),
        width: 320,
      ),
    );
    final String label =
        '${copy.playerEpisodePrefix}08 - ${copy.playerEpisodeStatusWatched}';
    expect(find.bySemanticsLabel(label), findsOneWidget);
    await tester.tap(find.bySemanticsLabel(label));
    await tester.pump();
    expect(tapped, <int>[8]);
    handle.dispose();
  });

  testWidgets('AppLedgerRow keeps figures on one line and stacks when narrow', (
    WidgetTester tester,
  ) async {
    for (final double width in <double>[200, 320, 1440]) {
      await tester.pumpWidget(
        host(
          const AppLedgerRow(label: 'Available balance', value: '1,234,567 pts'),
          width: width,
          textScale: width == 200 ? 3.2 : 1,
        ),
      );
      expect(tester.takeException(), isNull);
      final Text value = tester.widget<Text>(find.text('1,234,567 pts'));
      expect(
        value.style?.fontFeatures,
        contains(const FontFeature.tabularFigures()),
      );
    }
  });

  testWidgets('screen-reader activation reaches every interactive component', (
    WidgetTester tester,
  ) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    int buttonTaps = 0;
    int cardTaps = 0;
    int tickTaps = 0;
    int dismissals = 0;

    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            AppButton(label: 'Confirm', onPressed: () => buttonTaps++),
            SizedBox(
              width: 160,
              child: PosterCard(
                title: 'Drama',
                coverUrl: null,
                episodeCount: 9,
                onTap: () => cardTaps++,
              ),
            ),
            EpisodeSpine(
              ticks: <EpisodeTick>[
                EpisodeTick(
                  number: 1,
                  state: EpisodeTickState.none,
                  onTap: () => tickTaps++,
                ),
              ],
            ),
            AppMessageBar(
              message: 'Saved',
              type: UiMessageType.success,
              autoDismiss: const Duration(seconds: 30),
              onDismissed: () => dismissals++,
            ),
          ],
        ),
        width: 360,
      ),
    );
    await tester.pump();

    // Fires the accessibility tap action on the node carrying [label]; this
    // throws if the node does not actually support the action, which is the
    // assertion that matters.
    void activate(String label) {
      tester.semantics.tap(find.semantics.byLabel(label));
    }

    activate('Confirm');
    await tester.pump();
    expect(buttonTaps, 1, reason: 'AppButton must expose a tap action');

    activate(
      posterCardContentDescription(
        'Drama',
        9,
        AppLanguage.defaultLanguage,
      ),
    );
    await tester.pump();
    expect(cardTaps, 1, reason: 'PosterCard must expose a tap action');

    final AppStrings copy = strings(AppLanguage.defaultLanguage);
    activate('${copy.playerEpisodePrefix}01');
    await tester.pump();
    expect(tickTaps, 1, reason: 'EpisodeSpine tick must expose a tap action');

    activate('Success: Saved');
    await tester.pump();
    expect(dismissals, 1, reason: 'AppMessageBar must be dismissible');

    handle.dispose();
  });

  testWidgets('a disabled AppButton exposes no tap action', (
    WidgetTester tester,
  ) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    await tester.pumpWidget(
      host(const AppButton(label: 'Confirm', onPressed: null), width: 320),
    );
    final SemanticsData data = tester.semantics
        .find(find.bySemanticsLabel('Confirm'))
        .getSemanticsData();
    // It is still announced as a button, so the user knows what the control is,
    // but it offers no way to activate it.
    expect(data.flagsCollection.isButton, isTrue);
    expect(data.hasAction(SemanticsAction.tap), isFalse);
    handle.dispose();
  });

  testWidgets('AppEmptyState renders a real button with a callback and a '
      'non-interactive pill without one', (WidgetTester tester) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    int taps = 0;
    await tester.pumpWidget(
      host(
        AppEmptyState(
          icon: Icons.search_off,
          title: 'No results',
          actionLabel: 'Clear filters',
          onAction: () => taps++,
        ),
        width: 320,
      ),
    );
    await tester.tap(find.text('Clear filters'));
    await tester.pump();
    expect(taps, 1);

    await tester.pumpWidget(
      host(
        const AppEmptyState(
          icon: Icons.lock_outline,
          title: 'VIP required',
          actionLabel: 'VIP only',
        ),
        width: 320,
      ),
    );
    await tester.pump();
    final SemanticsData pill = tester.semantics
        .find(find.bySemanticsLabel('VIP only'))
        .getSemanticsData();
    expect(pill.hasAction(SemanticsAction.tap), isFalse);
    expect(pill.flagsCollection.isButton, isFalse);
    expect(find.byType(AppButton), findsNothing);
    handle.dispose();
  });

  test('every colour role meets its WCAG target', () {
    /// Relative luminance per WCAG 2.1.
    double luminance(Color c) {
      double channel(double v) => v <= 0.03928
          ? v / 12.92
          : math.pow((v + 0.055) / 1.055, 2.4).toDouble();
      return 0.2126 * channel(c.r) +
          0.7152 * channel(c.g) +
          0.0722 * channel(c.b);
    }

    /// WCAG contrast ratio between two opaque colours.
    double ratio(Color a, Color b) {
      final double la = luminance(a);
      final double lb = luminance(b);
      final double hi = math.max(la, lb);
      final double lo = math.min(la, lb);
      return (hi + 0.05) / (lo + 0.05);
    }

    for (final AppColors c in <AppColors>[AppColors.light, AppColors.dark]) {
      final String which = c.brightness.name;
      // Text: 4.5:1 on every neutral surface.
      for (final Color bg in <Color>[c.canvas, c.surface, c.surfaceAlt]) {
        expect(
          ratio(c.textPrimary, bg),
          greaterThanOrEqualTo(4.5),
          reason: '$which textPrimary on $bg',
        );
        expect(
          ratio(c.textSecondary, bg),
          greaterThanOrEqualTo(4.5),
          reason: '$which textSecondary on $bg',
        );
      }
      // Accents must work as text on the surfaces AND as fills carrying their
      // own on-colour, so both directions must clear 4.5:1.
      final Map<String, List<Color>> accents = <String, List<Color>>{
        'accentPrimary': <Color>[c.accentPrimary, c.onAccent],
        'accentReward': <Color>[c.accentReward, c.onReward],
        'accentSuccess': <Color>[c.accentSuccess, c.onSuccess],
        'accentWarning': <Color>[c.accentWarning, c.onWarning],
        'accentDanger': <Color>[c.accentDanger, c.onDanger],
      };
      accents.forEach((String role, List<Color> pair) {
        final Color accent = pair[0];
        final Color on = pair[1];
        expect(
          ratio(accent, c.surface),
          greaterThanOrEqualTo(4.5),
          reason: '$which $role as text on surface',
        );
        expect(
          ratio(accent, c.canvas),
          greaterThanOrEqualTo(4.5),
          reason: '$which $role as text on canvas',
        );
        expect(
          ratio(on, accent),
          greaterThanOrEqualTo(4.5),
          reason: '$which on-colour for $role on its own fill',
        );
      });
      // Non-text boundaries and state indicators: 3:1.
      for (final Color bg in <Color>[c.canvas, c.surface, c.surfaceAlt]) {
        expect(
          ratio(c.outlineStrong, bg),
          greaterThanOrEqualTo(3.0),
          reason: '$which outlineStrong boundary on $bg',
        );
        expect(
          ratio(c.focusRing, bg),
          greaterThanOrEqualTo(3.0),
          reason: '$which focusRing on $bg',
        );
      }
      // The premium plate must still carry readable copy. It is a translucent
      // tint, so it has to be composited over the plane it sits on before any
      // contrast can be claimed.
      expect(
        c.premiumSurface.a,
        lessThan(1.0),
        reason: '$which premiumSurface is a translucent tint',
      );
      for (final Color plane in <Color>[c.surface, c.canvas]) {
        final Color premium = Color.alphaBlend(c.premiumSurface, plane);
        expect(
          ratio(c.textPrimary, premium),
          greaterThanOrEqualTo(4.5),
          reason: '$which textPrimary on premiumSurface over $plane',
        );
        expect(
          ratio(c.textSecondary, premium),
          greaterThanOrEqualTo(4.5),
          reason: '$which textSecondary on premiumSurface over $plane',
        );
      }
      // The hairline is decorative by contract: assert it is NOT strong enough
      // to be mistaken for a control boundary, so nobody retunes it into one.
      expect(
        ratio(c.hairline, c.surface),
        lessThan(3.0),
        reason: '$which hairline must stay decorative',
      );
      // A disabled control is exempt from the WCAG text minimum, but its label
      // must still be perceivable: assert it stays at or above 2.8:1 and below
      // the active-text target, so the two tones cannot silently merge.
      expect(
        ratio(c.onDisabled, c.surfaceDisabled),
        greaterThanOrEqualTo(2.8),
        reason: '$which disabled label must stay perceivable',
      );
      expect(
        ratio(c.onDisabled, c.surfaceDisabled),
        lessThan(4.5),
        reason: '$which disabled label must stay below the active text target',
      );
      expect(c.surfaceDisabled, isNot(c.surfaceAlt));
    }

    // Media chrome is brightness-independent: it must clear the text targets on
    // its own glass/cell surfaces in both themes.
    final AppMedia media = AppMedia.standard;
    expect(ratio(media.onMedia, media.glass), greaterThanOrEqualTo(4.5));
    expect(ratio(media.onMedia, media.glassStrong), greaterThanOrEqualTo(4.5));
    // Translucent roles must be composited over their plane before any ratio is
    // claimed, exactly like premiumSurface above.
    expect(
      ratio(
        Color.alphaBlend(media.onMediaSecondary, media.glass),
        media.glass,
      ),
      greaterThanOrEqualTo(4.5),
    );
    expect(
      ratio(
        Color.alphaBlend(media.onMediaSecondary, media.glassStrong),
        media.glassStrong,
      ),
      greaterThanOrEqualTo(4.5),
    );
    expect(ratio(media.onMedia, media.glassDanger), greaterThanOrEqualTo(4.5));
    expect(ratio(media.onMedia, media.cellIdle), greaterThanOrEqualTo(4.5));
    expect(ratio(media.onMedia, media.cellSelected), greaterThanOrEqualTo(4.5));
    // The media divider is decorative, exactly like the page hairline.
    expect(
      ratio(
        Color.alphaBlend(media.divider, media.glassStrong),
        media.glassStrong,
      ),
      lessThan(3.0),
    );
    expect(media.scrim.a, lessThan(1.0));
    // Locked artwork dims the accent rather than hiding it.
    expect(AppColors.light.lockedOverlay.a, closeTo(0.58, 0.01));
    expect(AppColors.light.avatarTint.a, closeTo(0.14, 0.01));
    // The bottom copy gradient must end opaque enough to carry white copy.
    expect(media.bottomCopyGradient.colors.last.a, greaterThanOrEqualTo(0.9));
    expect(media.bottomCopyGradient.colors.first.a, 0);
  });

  testWidgets('reduced motion removes the page transition entirely', (
    WidgetTester tester,
  ) async {
    // Asserted against the builder directly: a route's own animation is driven
    // by the framework, so the only way to honour reduced motion here is to
    // return the child untouched.
    for (final bool disableAnimations in <bool>[true, false]) {
      Widget? result;
      Widget? subject;
      await tester.pumpWidget(
        MaterialApp(
          theme: appTheme(Brightness.light),
          home: MediaQuery(
            data: MediaQueryData(disableAnimations: disableAnimations),
            child: Builder(
              builder: (BuildContext context) {
                subject = const SizedBox(key: ValueKey<String>('subject'));
                result = const ReelChangePageTransitionsBuilder()
                    .buildTransitions<void>(
                      MaterialPageRoute<void>(
                        builder: (BuildContext context) => subject!,
                      ),
                      context,
                      const AlwaysStoppedAnimation<double>(0.5),
                      const AlwaysStoppedAnimation<double>(0.5),
                      subject!,
                    );
                return const SizedBox.shrink();
              },
            ),
          ),
        ),
      );
      expect(result, isNotNull);
      if (disableAnimations) {
        expect(
          identical(result, subject),
          isTrue,
          reason: 'reduced motion must return the child untouched',
        );
      } else {
        expect(
          identical(result, subject),
          isFalse,
          reason: 'normal motion must wrap the child in the reel change',
        );
        expect(result, isA<ClipRect>());
      }
    }
  });

  testWidgets('reel change transition builder is installed on every platform', (
    WidgetTester tester,
  ) async {
    final ThemeData theme = appTheme(Brightness.light);
    for (final TargetPlatform p in TargetPlatform.values) {
      expect(
        theme.pageTransitionsTheme.builders[p],
        isA<ReelChangePageTransitionsBuilder>(),
        reason: 'missing builder for $p',
      );
    }
    // And it actually mounts when a route pushes.
    await tester.pumpWidget(
      MaterialApp(
        theme: theme,
        home: Builder(
          builder: (BuildContext context) => TextButton(
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (BuildContext context) =>
                    const Scaffold(body: Text('second')),
              ),
            ),
            child: const Text('push'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('push'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('second'), findsOneWidget);
    expect(find.byType(SlideTransition), findsWidgets);
    expect(tester.takeException(), isNull);
    await tester.pumpAndSettle();
  });

  testWidgets('list rows expose exactly one activation, disabled rows none', (
    WidgetTester tester,
  ) async {
    int taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: appTheme(Brightness.light),
        home: Scaffold(
          body: Column(
            children: <Widget>[
              AppListRow(
                title: 'Watch history',
                subtitle: 'Pick up from your last episode',
                leadingIcon: Icons.history,
                showChevron: true,
                semanticLabel: 'Watch history',
                onTap: () => taps++,
              ),
              const AppListRow(
                title: 'Disabled row',
                leadingIcon: Icons.lock_outline,
              ),
            ],
          ),
        ),
      ),
    );

    final SemanticsHandle handle = tester.ensureSemantics();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Watch history'));
    expect(taps, 1);

    // One merged node per row, with the action on that node; the disabled row
    // exposes no action at all (§7.3, §7.9).
    final SemanticsProperties enabled = tester
        .widget<Semantics>(
          find.byWidgetPredicate(
            (Widget widget) =>
                widget is Semantics &&
                widget.properties.label == 'Watch history',
          ),
        )
        .properties;
    expect(enabled.onTap, isNotNull);
    expect(enabled.button, isTrue);

    final SemanticsProperties disabled = tester
        .widget<Semantics>(
          find.byWidgetPredicate(
            (Widget widget) =>
                widget is Semantics &&
                widget.properties.label == 'Disabled row',
          ),
        )
        .properties;
    expect(disabled.onTap, isNull);
    expect(disabled.button, isNot(true));
    handle.dispose();
  });

  testWidgets('a labelled trailing action keeps its wording when the row opts '
      'in', (WidgetTester tester) async {
    // 480 dp with the widget-test font stands in for a 360 dp device with
    // Roboto (~1.4x narrower): sharing the row 1:1 clips "Replace wallet"
    // mid-word, while `trailingTakesIntrinsicWidth` renders it in full.
    addTearDown(tester.view.reset);
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(480, 900);

    Future<double> renderedActionWidth({required bool intrinsic}) async {
      await tester.pumpWidget(
        host(
          AppSectionCard(
            children: <Widget>[
              AppListRow(
                title: 'Cold wallet',
                subtitle: '0x1111111111111111111111111111111111111111',
                subtitleMaxLines: 1,
                leadingIcon: Icons.account_balance_wallet_outlined,
                divider: false,
                trailingTakesIntrinsicWidth: intrinsic,
                trailing: TextButton(
                  onPressed: () {},
                  child: const Text('Replace wallet'),
                ),
              ),
            ],
          ),
          width: 480,
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull);
      return tester.getSize(find.text('Replace wallet')).width;
    }

    double intrinsicWidth() {
      final Text text = tester.widget<Text>(find.text('Replace wallet'));
      return (TextPainter(
        text: TextSpan(text: text.data, style: text.style),
        textDirection: TextDirection.ltr,
        textScaler: MediaQuery.textScalerOf(
          tester.element(find.text('Replace wallet')),
        ),
        maxLines: 1,
      )..layout()).width;
    }

    final double shared = await renderedActionWidth(intrinsic: false);
    final double ownWidth = await renderedActionWidth(intrinsic: true);
    final double needed = intrinsicWidth();
    expect(
      shared,
      lessThan(needed),
      reason: 'the default split still shares the row and clips the action',
    );
    expect(
      ownWidth,
      greaterThanOrEqualTo(needed - 0.5),
      reason: 'the opt-in row renders the action in full',
    );
    expect(
      tester.getSize(find.byType(TextButton)).height,
      greaterThanOrEqualTo(44),
      reason: 'the wider label still meets the touch-target minimum',
    );
  });

  testWidgets('AppMembershipChip carries its state in tint, glyph and word', (
    WidgetTester tester,
  ) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    // The heroes reserve this plate while the account snapshot is unknown;
    // changing it moves them.
    expect(membershipChipPlaceholderSize, const Size(112, 22));

    Future<BoxDecoration> plateFor({required bool active}) async {
      final String label = active ? 'VIP active' : 'VIP not active';
      await tester.pumpWidget(
        host(
          AppMembershipChip(active: active, label: label),
          width: 320,
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull);
      final Container plate = tester.widget<Container>(
        find
            .ancestor(of: find.text(label), matching: find.byType(Container))
            .first,
      );
      expect(find.text(label), findsOneWidget);
      return plate.decoration! as BoxDecoration;
    }

    final BoxDecoration active = await plateFor(active: true);
    expect(
      active.color,
      AppColors.light.accentSuccess.withValues(alpha: 0.14),
    );
    expect(active.borderRadius, AppShapes.light.inlineRadius);
    expect(find.byIcon(Icons.verified_outlined), findsOneWidget);
    // A status, not a control: readable and with no activation.
    final SemanticsData activeData = tester.semantics
        .find(find.bySemanticsLabel('VIP active'))
        .getSemanticsData();
    expect(activeData.hasAction(SemanticsAction.tap), isFalse);
    expect(activeData.flagsCollection.isButton, isFalse);

    final BoxDecoration inactive = await plateFor(active: false);
    expect(
      inactive.color,
      AppColors.light.accentWarning.withValues(alpha: 0.14),
    );
    expect(inactive.borderRadius, AppShapes.light.inlineRadius);
    expect(find.byIcon(Icons.lock_outline), findsOneWidget);
    expect(find.byIcon(Icons.verified_outlined), findsNothing);

    // One line at 3.2x on a 320 px screen: a hero swapping the placeholder for
    // the chip does not resize.
    await tester.pumpWidget(
      host(
        const AppMembershipChip(active: false, label: 'VIP not active'),
        width: 320,
        textScale: 3.2,
      ),
    );
    await tester.pump();
    expect(tester.takeException(), isNull);
    final Text label = tester.widget<Text>(find.text('VIP not active'));
    expect(label.maxLines, 1);
    expect(label.overflow, TextOverflow.ellipsis);
    handle.dispose();
  });

  testWidgets('a card of rows keeps every row and its trailing control as its '
      'own semantic node', (WidgetTester tester) async {
    // Regression for the Me-page defect: a plain row used to replace its
    // descendants with one label (excludeSemantics, container: false), so a card
    // that mixed tappable and plain rows collapsed into a single announcement
    // and the trailing controls disappeared from the tree. See
    // `docs/ui-redesign-notes.md` §7.2.
    final SemanticsHandle handle = tester.ensureSemantics();
    int rowTaps = 0;
    int trailingTaps = 0;
    await tester.pumpWidget(
      host(
        AppSectionCard(
          children: <Widget>[
            AppListRow(
              title: 'Tappable row',
              subtitle: 'Opens something',
              leadingIcon: Icons.history,
              divider: false,
              onTap: () => rowTaps++,
            ),
            AppListRow(
              title: 'Plain row',
              subtitle: 'No action is exposed',
              leadingIcon: Icons.lock_outline,
              divider: false,
              trailing: TextButton(
                onPressed: () => trailingTaps++,
                child: const Text('Row action'),
              ),
            ),
          ],
        ),
        width: 320,
      ),
    );
    await tester.pump();

    final List<String> texts = semanticsTexts(tester);
    expect(texts, contains('Tappable row, Opens something'));
    expect(texts, contains('Plain row, No action is exposed'));
    expect(texts, contains('Row action'));
    // The description is announced by the row node, not once more by the texts
    // inside it.
    expect(
      texts.where((String text) => text.contains('Tappable row')).length,
      1,
    );

    final SemanticsData tappable = tester.semantics
        .find(find.bySemanticsLabel('Tappable row, Opens something'))
        .getSemanticsData();
    expect(tappable.hasAction(SemanticsAction.tap), isTrue);
    expect(tappable.flagsCollection.isButton, isTrue);

    final SemanticsData plain = tester.semantics
        .find(find.bySemanticsLabel('Plain row, No action is exposed'))
        .getSemanticsData();
    expect(plain.hasAction(SemanticsAction.tap), isFalse);
    expect(plain.flagsCollection.isButton, isFalse);

    final SemanticsData action = tester.semantics
        .find(find.bySemanticsLabel('Row action'))
        .getSemanticsData();
    expect(action.hasAction(SemanticsAction.tap), isTrue);

    // Both actions still reach their own widget, and the plain row's tap (the
    // row node has none) is never the one that fires.
    tester.semantics.tap(
      find.semantics.byLabel('Tappable row, Opens something'),
    );
    await tester.pump();
    expect(rowTaps, 1);
    tester.semantics.tap(find.semantics.byLabel('Row action'));
    await tester.pump();
    expect(trailingTaps, 1);
    expect(rowTaps, 1, reason: 'activating the trailing control is not the row');
    handle.dispose();
  });

  testWidgets('segmented control marks the selection with a checkmark', (
    WidgetTester tester,
  ) async {
    String value = 'login';
    await tester.pumpWidget(
      MaterialApp(
        theme: appTheme(Brightness.light),
        home: Scaffold(
          body: AppSegmentedControl<String>(
            value: value,
            options: const <AppSegmentedOption<String>>[
              AppSegmentedOption<String>(value: 'login', label: 'Sign in'),
              AppSegmentedOption<String>(
                value: 'register',
                label: 'Create account',
              ),
            ],
            onChanged: (String next) => value = next,
          ),
        ),
      ),
    );

    // State is never carried by colour alone: the selected option carries a
    // checkmark and the `selected` semantic flag.
    expect(find.byIcon(Icons.check), findsOneWidget);
    final SemanticsHandle handle = tester.ensureSemantics();
    await tester.pumpAndSettle();
    final SemanticsProperties selected = tester
        .widget<Semantics>(
          find.byWidgetPredicate(
            (Widget widget) =>
                widget is Semantics && widget.properties.label == 'Sign in',
          ),
        )
        .properties;
    expect(selected.selected, isTrue);
    expect(selected.onTap, isNotNull);
    await tester.tap(find.text('Create account'));
    expect(value, 'register');
    handle.dispose();
  });

  testWidgets('sheeted content is a route: back cancels it and a barrier '
      'blocks the page behind', (WidgetTester tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: appTheme(Brightness.light),
        home: Scaffold(
          body: Builder(
            builder: (BuildContext context) => Center(
              child: AppButton(
                label: 'Open wallet',
                onPressed: () => AppSheet.show<void>(
                  context,
                  title: 'Wallet',
                  child: const Text('Wallet sheet body'),
                ),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Open wallet'));
    await tester.pumpAndSettle();
    expect(find.text('Wallet sheet body'), findsOneWidget);
    expect(find.byType(ModalBarrier), findsWidgets);
    // The sheet is a route that scopes the reading order to itself, so a screen
    // reader cannot walk into the page behind it.
    expect(
      find.byWidgetPredicate(
        (Widget widget) => widget is Semantics && widget.properties.scopesRoute == true,
      ),
      findsWidgets,
    );

    // The system back gesture must close the sheet instead of leaving the app:
    // that was the audited defect of the old always-mounted Stack overlays.
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.text('Wallet sheet body'), findsNothing);
    expect(find.text('Open wallet'), findsOneWidget);
  });

  testWidgets('confirmation dialog keeps its actions reachable at 2x text', (
    WidgetTester tester,
  ) async {
    bool confirmed = false;
    await tester.pumpWidget(
      MaterialApp(
        theme: appTheme(Brightness.light),
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(320, 640),
            textScaler: TextScaler.linear(2),
          ),
          child: Scaffold(
            body: AppConfirmDialog(
              title: 'Sign out?',
              body: 'You can sign in again at any time.',
              confirmLabel: 'Sign out',
              cancelLabel: 'Cancel',
              destructive: true,
              onConfirm: () => confirmed = true,
              onCancel: () {},
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Sign out'));
    expect(confirmed, isTrue);
  });

  testWidgets('AppSkeleton variants shimmer as one system and flatten under '
      'reduced motion', (WidgetTester tester) async {
    // The poster columns follow the same adaptive rule as the real shelf.
    expect(posterSkeletonColumns(100), 1);
    expect(posterSkeletonColumns(320), 2);
    expect(posterSkeletonColumns(1440), posterSkeletonMaxColumns);
    expect(posterSkeletonColumns(0), 1);
    expect(posterSkeletonColumns(-10), 1);
    expect(posterSkeletonColumns(double.infinity), 1);

    const Widget variants = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        AppSkeletonText(lines: 3),
        SizedBox(height: 12),
        AppSkeletonCircle(),
        SizedBox(height: 12),
        AppSkeletonPosterGrid(itemCount: 5),
      ],
    );

    // Every variant shimmers through the same ShaderMask as AppSkeleton: 3 text
    // lines + 1 circle + 5 poster tiles.
    await tester.pumpWidget(host(variants, width: 320));
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.byType(ShaderMask), findsNWidgets(9));
    expect(tester.takeException(), isNull);

    // The last text line is short, and the circle is a real circle: both are
    // part of "the placeholder has the shape of the arriving content".
    final Finder lines = find.byType(AppSkeleton);
    final double fullLine = tester.getSize(lines.at(0)).width;
    final double lastLine = tester.getSize(lines.at(2)).width;
    expect(lastLine, lessThan(fullLine));
    expect(
      lastLine,
      closeTo(fullLine * skeletonTextLastLineFactor, 0.5),
      reason: 'the last line keeps a fraction of the paragraph width',
    );
    expect(
      tester.getSize(lines.at(3)),
      const Size(skeletonCircleDiameter, skeletonCircleDiameter),
    );
    final DecoratedBox circle = tester.widget<DecoratedBox>(
      find
          .descendant(of: lines.at(3), matching: find.byType(DecoratedBox))
          .first,
    );
    expect(
      (circle.decoration as BoxDecoration).borderRadius,
      BorderRadius.circular(skeletonCircleDiameter / 2),
    );

    // Reduced motion: the shimmer is gone entirely, so nothing can rotate or
    // blink, and every variant is a static surfaceAlt plate.
    await tester.pumpWidget(
      host(variants, width: 320, disableAnimations: true),
    );
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.byType(ShaderMask), findsNothing);
    expect(tester.takeException(), isNull);
    final DecoratedBox plate = tester.widget<DecoratedBox>(
      find
          .descendant(
            of: find.byType(AppSkeleton),
            matching: find.byType(DecoratedBox),
          )
          .first,
    );
    expect(
      (plate.decoration as BoxDecoration).color,
      AppColors.light.surfaceAlt,
    );
  });

  testWidgets('AppSkeleton fills a bounded width instead of collapsing to zero', (
    WidgetTester tester,
  ) async {
    // Device defect: `width: null` laid out as a zero-width plate, so every
    // AppSkeletonList row (Home / Favorites / Funds) was invisible while the
    // data loaded. Shimmer and reduced motion share the same plate, so both
    // branches are asserted - the review device had animations turned off.
    for (final bool still in <bool>[false, true]) {
      final String mode = 'disableAnimations=$still';

      await tester.pumpWidget(
        host(
          const AppSkeleton(height: 24),
          width: 320,
          disableAnimations: still,
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull, reason: mode);
      expect(
        tester.getSize(find.byType(AppSkeleton)),
        const Size(320, 24),
        reason: '$mode: a null width must fill the available width',
      );

      await tester.pumpWidget(
        host(
          const AppSkeletonList(itemHeight: 64, itemCount: 3),
          width: 360,
          disableAnimations: still,
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull, reason: mode);
      for (int index = 0; index < 3; index++) {
        final Size row = tester.getSize(find.byType(AppSkeleton).at(index));
        expect(
          row.width,
          360 - 32,
          reason: '$mode: row $index must span the parent minus its padding',
        );
        expect(row.width, greaterThan(0), reason: '$mode: row $index is visible');
        expect(row.height, 64, reason: mode);
      }

      // The paragraph variant keeps its full-width lines and its 0.6 last line.
      await tester.pumpWidget(
        host(
          const AppSkeletonText(lines: 3),
          width: 320,
          disableAnimations: still,
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull, reason: mode);
      expect(tester.getSize(find.byType(AppSkeleton).at(0)).width, 320);
      expect(
        tester.getSize(find.byType(AppSkeleton).at(2)).width,
        closeTo(320 * skeletonTextLastLineFactor, 0.5),
        reason: '$mode: the last line keeps its ratio',
      );

      // An explicit width is still honoured verbatim: `Center` is what gives the
      // block loose constraints, since the test host hands its child a tight
      // width the way a scrolling page does.
      await tester.pumpWidget(
        host(
          const Center(child: AppSkeleton(width: 120, height: 20)),
          width: 320,
          disableAnimations: still,
        ),
      );
      await tester.pump();
      expect(
        tester.getSize(find.byType(AppSkeleton)),
        const Size(120, 20),
        reason: '$mode: an explicit width must not be stretched',
      );
    }
  });

  testWidgets('AppSkeletonPosterGrid derives its columns from the width', (
    WidgetTester tester,
  ) async {
    // `host` can only place what the test view actually offers, so the wide
    // case needs a real viewport instead of a MediaQuery override.
    addTearDown(tester.view.reset);
    tester.view.devicePixelRatio = 1;
    for (final (double width, int columns) in <(double, int)>[
      (320, 2),
      (600, 4),
      (1440, posterSkeletonMaxColumns),
    ]) {
      tester.view.physicalSize = Size(width, 900);
      await tester.pumpWidget(
        host(const AppSkeletonPosterGrid(itemCount: 3), width: width),
      );
      await tester.pump();
      expect(tester.takeException(), isNull, reason: 'width $width');
      final double expectedTile =
          (width - AppSpacing.standard.gutter * (columns - 1)) / columns;
      expect(
        tester.getSize(find.byType(AppSkeleton).first).width,
        closeTo(expectedTile, 0.01),
        reason: 'width $width must use $columns columns',
      );
    }
  });

  test('AppEmptyState resolves its action and its pill by the stated priority', () {
    // Priority 1: a callback plus words is a real action, and never a pill.
    expect(
      appEmptyStateShowsAction(onAction: () {}, actionLabel: 'Clear'),
      isTrue,
    );
    expect(
      appEmptyStatePillLabel(
        onAction: () {},
        actionLabel: 'Clear',
        pillLabel: 'VIP only',
      ),
      isNull,
    );
    // Priority 2: pillLabel; priority 3: the action label that lost its action.
    expect(
      appEmptyStatePillLabel(
        onAction: null,
        actionLabel: 'Clear',
        pillLabel: 'VIP only',
      ),
      'VIP only',
    );
    expect(
      appEmptyStatePillLabel(onAction: null, actionLabel: 'Clear'),
      'Clear',
    );
    // A callback with a blank or missing label is not an action...
    expect(appEmptyStateShowsAction(onAction: () {}, actionLabel: '   '), isFalse);
    expect(appEmptyStateShowsAction(onAction: () {}, actionLabel: null), isFalse);
    // ...and the words that are available still reach the user as a status.
    expect(
      appEmptyStatePillLabel(
        onAction: () {},
        actionLabel: '   ',
        pillLabel: 'VIP only',
      ),
      'VIP only',
    );
    expect(
      appEmptyStatePillLabel(onAction: () {}, actionLabel: null, pillLabel: ' '),
      isNull,
    );
  });

  testWidgets('AppEmptyState never renders a dead button and its pill is a '
      'status, not a control', (WidgetTester tester) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    int taps = 0;
    // A callback whose label is blank must not become a disabled button: there
    // is no focusable or tappable descendant, only the status pill.
    await tester.pumpWidget(
      host(
        AppEmptyState(
          icon: Icons.lock_outline,
          title: 'VIP required',
          actionLabel: '   ',
          pillLabel: 'VIP only',
          onAction: () => taps++,
        ),
        width: 320,
      ),
    );
    await tester.pump();
    expect(find.byType(AppButton), findsNothing);
    expect(find.text('VIP only'), findsOneWidget);
    expect(
      find.descendant(of: find.byType(AppEmptyState), matching: find.byType(InkWell)),
      findsNothing,
    );
    expect(
      find.descendant(of: find.byType(AppEmptyState), matching: find.byType(Focus)),
      findsNothing,
    );
    expect(find.bySemanticsLabel('VIP only'), findsOneWidget);
    final SemanticsData pill = tester.semantics
        .find(find.bySemanticsLabel('VIP only'))
        .getSemanticsData();
    expect(pill.hasAction(SemanticsAction.tap), isFalse);
    expect(pill.hasAction(SemanticsAction.focus), isFalse);
    expect(pill.flagsCollection.isButton, isFalse);
    // `isFocused` is a Tristate: `none` means focus is not applicable at all,
    // i.e. the node is not focusable.
    expect(
      pill.flagsCollection.isFocused.name,
      'none',
      reason: 'the pill must not be focusable',
    );
    await tester.tap(find.text('VIP only'));
    await tester.pump();
    expect(taps, 0, reason: 'the pill must not be activatable');

    // With no words anywhere there is nothing to render at all: still no
    // button, and no orphan pill either.
    await tester.pumpWidget(
      host(
        AppEmptyState(
          icon: Icons.search_off,
          title: 'Nothing here',
          actionLabel: '  ',
          onAction: () => taps++,
        ),
        width: 320,
      ),
    );
    await tester.pump();
    expect(taps, 0);
    expect(
      appEmptyStatePillLabel(onAction: () {}, actionLabel: '  '),
      isNull,
    );
    expect(find.byType(AppButton), findsNothing);
    handle.dispose();
  });

  testWidgets('AppEmptyState pill wraps and grows at 3.2x on a 320px screen', (
    WidgetTester tester,
  ) async {
    const String pill = 'VIP membership is required for this episode';
    Future<double> pillHeight(double scale) async {
      await tester.pumpWidget(
        host(
          const AppEmptyState(
            icon: Icons.lock_outline,
            title: 'VIP required',
            body: 'Episode 8 and later need VIP.',
            pillLabel: pill,
          ),
          width: 320,
          textScale: scale,
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull, reason: 'scale $scale');
      return tester.getSize(find.bySemanticsLabel(pill)).height;
    }

    // getSize needs a semantics tree; bySemanticsLabel is the only stable
    // handle on the private pill widget.
    final SemanticsHandle handle = tester.ensureSemantics();
    final double normal = await pillHeight(1);
    final double huge = await pillHeight(3.2);
    expect(
      huge,
      greaterThan(normal),
      reason: 'the pill must grow with wrapped lines instead of clipping',
    );
    final Text label = tester.widget<Text>(find.text(pill));
    expect(label.maxLines, isNull, reason: 'the pill text is free to wrap');
    expect(tester.getSize(find.text(pill)).width, lessThanOrEqualTo(320));
    handle.dispose();
  });

  testWidgets('AppTextField reveals and masks again without touching text, '
      'caret or focus', (WidgetTester tester) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    final TextEditingController controller = TextEditingController(
      text: 'secret123',
    );
    final FocusNode focusNode = FocusNode();

    await tester.pumpWidget(
      host(
        AppTextField(
          label: 'Password',
          controller: controller,
          focusNode: focusNode,
          obscureText: true,
          textInputAction: TextInputAction.done,
        ),
        width: 320,
      ),
    );
    await tester.pump();

    TextField field() => tester.widget<TextField>(find.byType(TextField));
    expect(field().obscureText, isTrue);

    focusNode.requestFocus();
    await tester.pump();
    expect(focusNode.hasFocus, isTrue);
    controller.selection = const TextSelection.collapsed(offset: 3);
    await tester.pump();

    final Finder reveal = find.bySemanticsLabel(AppUiCopy.showPassword);
    expect(reveal, findsOneWidget, reason: 'masked field shows the toggle');
    final Size target = tester.getSize(reveal);
    expect(target.width, greaterThanOrEqualTo(44));
    expect(target.height, greaterThanOrEqualTo(44));
    // The toggle is a real node in the semantics tree with its own activation
    // action, so a screen reader can reach the value it unmasks.
    final SemanticsData toggle = tester.semantics
        .find(reveal)
        .getSemanticsData();
    expect(toggle.hasAction(SemanticsAction.tap), isTrue);
    expect(toggle.flagsCollection.isButton, isTrue);

    await tester.tap(reveal);
    await tester.pump();
    expect(field().obscureText, isFalse, reason: 'the mask must be lifted');
    expect(controller.text, 'secret123');
    expect(
      controller.selection,
      const TextSelection.collapsed(offset: 3),
      reason: 'the caret must survive the toggle',
    );
    expect(focusNode.hasFocus, isTrue, reason: 'the toggle must not steal focus');

    final Finder conceal = find.bySemanticsLabel(AppUiCopy.hidePassword);
    expect(conceal, findsOneWidget);
    await tester.tap(conceal);
    await tester.pump();
    expect(field().obscureText, isTrue);
    expect(controller.text, 'secret123');
    expect(focusNode.hasFocus, isTrue);

    // The toggle is opt-out, and a plain field never grows one.
    await tester.pumpWidget(
      host(
        const AppTextField(
          label: 'Password',
          obscureText: true,
          showObscureToggle: false,
        ),
        width: 320,
      ),
    );
    await tester.pump();
    expect(find.bySemanticsLabel(AppUiCopy.showPassword), findsNothing);
    expect(find.bySemanticsLabel(AppUiCopy.hidePassword), findsNothing);
    handle.dispose();
    // Unmount before disposing the caller-owned objects, so the field's own
    // teardown cannot touch a disposed node.
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    focusNode.dispose();
  });

  testWidgets('AppTextField close the keyboard on an outside tap for every '
      'pointer kind, and only when asked to', (WidgetTester tester) async {
    final FocusNode focusNode = FocusNode();
    addTearDown(focusNode.dispose);
    // A real target beside the field: the tap must land on something, exactly
    // like a label or a list row in the app.
    Widget outsideArea() => GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () {},
      child: const SizedBox(height: 240, width: double.infinity),
    );
    await tester.pumpWidget(
      host(
        Column(
          children: <Widget>[
            AppTextField(
              label: 'Password',
              focusNode: focusNode,
              obscureText: true,
              textInputAction: TextInputAction.done,
            ),
            outsideArea(),
          ],
        ),
        width: 320,
      ),
    );
    await tester.pump();

    // EditableText installs its `onTapOutside` callback from a focus listener,
    // so the focus change needs its frame *and* the rebuild frame that follows
    // it. In the app those frames are separated by real time; here they must be
    // pumped explicitly or the tap can land before the callback exists.
    Future<void> focusField(FocusNode node) async {
      node.requestFocus();
      await tester.pump();
      await tester.pump();
      expect(node.hasFocus, isTrue);
    }

    // Touch: the framework's own default leaves a mobile field focused, which is
    // exactly the stuck-IME defect this contract removes.
    await focusField(focusNode);
    await tester.tapAt(const Offset(160, 200));
    await tester.pump();
    expect(focusNode.hasFocus, isFalse, reason: 'touch outside must unfocus');

    // Mouse: same outcome, so phone and desktop agree.
    await focusField(focusNode);
    final TestGesture mouse = await tester.startGesture(
      const Offset(160, 200),
      kind: PointerDeviceKind.mouse,
    );
    await mouse.up();
    await tester.pump();
    expect(focusNode.hasFocus, isFalse, reason: 'mouse outside must unfocus');

    // The toggle lives inside the field's own tap region: using it must not be
    // mistaken for a tap outside.
    final SemanticsHandle handle = tester.ensureSemantics();
    await focusField(focusNode);
    await tester.tap(find.bySemanticsLabel(AppUiCopy.showPassword));
    await tester.pump();
    expect(focusNode.hasFocus, isTrue);
    handle.dispose();

    // Opting out means "never dismiss on my behalf", even on desktop rules.
    // The first field is unmounted first: a field captures its focus node when
    // its state is created, exactly like the forms in the app that never swap it.
    await tester.pumpWidget(const SizedBox.shrink());
    final FocusNode optOut = FocusNode();
    addTearDown(optOut.dispose);
    await tester.pumpWidget(
      host(
        Column(
          children: <Widget>[
            AppTextField(
              label: 'Amount',
              focusNode: optOut,
              dismissKeyboardOnTapOutside: false,
            ),
            outsideArea(),
          ],
        ),
        width: 320,
      ),
    );
    await tester.pump();
    await focusField(optOut);
    await tester.tapAt(const Offset(160, 200));
    await tester.pump();
    expect(optOut.hasFocus, isTrue, reason: 'the opt-out must keep the focus');
  });

  testWidgets('AppTextField still submits, dismisses and advances focus', (
    WidgetTester tester,
  ) async {
    final TextEditingController controller = TextEditingController();
    addTearDown(controller.dispose);
    final FocusNode first = FocusNode();
    addTearDown(first.dispose);
    final FocusNode second = FocusNode();
    addTearDown(second.dispose);
    int submitted = 0;

    await tester.pumpWidget(
      host(
        Column(
          children: <Widget>[
            AppTextField(
              label: 'First',
              controller: controller,
              focusNode: first,
              obscureText: true,
              textInputAction: TextInputAction.next,
            ),
            AppTextField(
              label: 'Second',
              focusNode: second,
              obscureText: true,
              textInputAction: TextInputAction.done,
              onSubmitted: (String _) => submitted++,
            ),
          ],
        ),
        width: 320,
      ),
    );
    await tester.pump();

    // `next` still moves to the following field: the reveal toggle of the first
    // field must not sit between the two in the traversal order.
    first.requestFocus();
    await tester.pump();
    await tester.pump();
    expect(first.hasFocus, isTrue);
    await tester.testTextInput.receiveAction(TextInputAction.next);
    await tester.pump();
    expect(second.hasFocus, isTrue, reason: 'next must advance to field two');
    expect(first.hasFocus, isFalse);

    // `done` submits and closes the keyboard.
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pump();
    expect(submitted, 1);
    expect(second.hasFocus, isFalse, reason: 'done must close the keyboard');
  });

  testWidgets('AppTextField removes the listener it registered for '
      'selectAllOnFocus', (WidgetTester tester) async {
    // A caller's focus node outlives the field. `selectAllOnFocus` registers a
    // listener on it, so a rebuild that turns the flag off must not make the
    // field forget to remove it. Two judgements are used: direct (the node
    // reports no listener once the field is unmounted) and behavioural
    // (re-focusing the reused node must not write through the field's already
    // disposed controller, which is what a leaked listener does).
    final _ListenerProbeFocusNode node = _ListenerProbeFocusNode();

    await tester.pumpWidget(
      host(
        AppTextField(label: 'Wallet', focusNode: node, selectAllOnFocus: true),
        width: 320,
      ),
    );
    await tester.pump();
    await tester.enterText(find.byType(TextField), '0xabcdef');
    await tester.pump();
    expect(node.hasAnyListeners, isTrue, reason: 'the field listens while mounted');

    // Rebuild with the flag off, then unmount: ownership is decided at
    // registration, not by the current widget.
    await tester.pumpWidget(
      host(AppTextField(label: 'Wallet', focusNode: node), width: 320),
    );
    await tester.pump();
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();
    expect(tester.takeException(), isNull);
    expect(
      node.hasAnyListeners,
      isFalse,
      reason: 'the field must remove the listener it registered',
    );

    // The node stays usable for whatever the caller mounts next.
    await tester.pumpWidget(
      MaterialApp(
        theme: appTheme(Brightness.light),
        home: Scaffold(body: TextField(focusNode: node)),
      ),
    );
    await tester.pump();
    node.requestFocus();
    await tester.pump();
    await tester.pump();
    expect(node.hasFocus, isTrue);
    expect(
      tester.takeException(),
      isNull,
      reason: 'a stale listener would touch the disposed controller here',
    );

    await tester.pumpWidget(const SizedBox.shrink());
    node.dispose();
  });

  testWidgets('EpisodeSpine full ticks keep a 44px target that never overlaps', (
    WidgetTester tester,
  ) async {
    final List<int> tapped = <int>[];
    await tester.pumpWidget(
      host(
        EpisodeSpine(
          ticks: <EpisodeTick>[
            for (int episode = 1; episode <= 4; episode++)
              EpisodeTick(
                number: episode,
                state: episode == 2
                    ? EpisodeTickState.current
                    : EpisodeTickState.none,
                onTap: () => tapped.add(episode),
              ),
          ],
        ),
        width: 320,
      ),
    );
    await tester.pump();

    expect(episodeSpineMinTargetExtent, 44);
    final Finder targets = find.descendant(
      of: find.byType(EpisodeSpine),
      matching: find.byType(InkWell),
    );
    expect(targets, findsNWidgets(4));
    final List<Rect> rects = <Rect>[
      for (int index = 0; index < 4; index++) tester.getRect(targets.at(index)),
    ];
    for (int index = 0; index < rects.length; index++) {
      expect(
        rects[index].width,
        greaterThanOrEqualTo(episodeSpineMinTargetExtent),
        reason: 'tick $index target width',
      );
      expect(
        rects[index].height,
        greaterThanOrEqualTo(episodeSpineMinTargetExtent),
        reason: 'tick $index target height',
      );
    }
    for (int a = 0; a < rects.length; a++) {
      for (int b = a + 1; b < rects.length; b++) {
        expect(
          rects[a].overlaps(rects[b]),
          isFalse,
          reason: 'targets $a and $b must not overlap',
        );
      }
    }
    expect(
      rects[1].top - rects[0].top,
      episodeSpineMinTargetExtent,
      reason: 'the row pitch is the target extent',
    );

    // The whole square is live: a corner the visual tick never covers still
    // fires that tick and only that tick.
    await tester.tapAt(rects[0].topLeft + const Offset(2, 2));
    await tester.pump();
    expect(tapped, <int>[1]);

    // The visual geometry is untouched: the same tick sizes as before, still
    // flush with the rail's gutter, now centred inside the taller row.
    final Finder bars = find.descendant(
      of: find.byType(EpisodeSpine),
      matching: find.byType(Opacity),
    );
    expect(tester.getSize(bars.at(0)).width, closeTo(9.6, 0.01));
    expect(tester.getSize(bars.at(0)).height, closeTo(4, 0.01));
    expect(tester.getSize(bars.at(1)).width, closeTo(16, 0.01));
    expect(tester.getSize(bars.at(1)).height, closeTo(5, 0.01));
    expect(tester.getTopLeft(bars.at(1)).dx - rects[1].left, 7);
    expect(
      tester.getTopLeft(bars.at(1)).dy - rects[1].top,
      closeTo((episodeSpineMinTargetExtent - 5) / 2, 0.01),
      reason: 'the visual tick is centred in the 44px row',
    );
  });

  testWidgets('EpisodeSpine keeps its 16px rows when nothing is tappable', (
    WidgetTester tester,
  ) async {
    // A full spine without handlers is informational: it must not grow.
    await tester.pumpWidget(
      host(EpisodeSpine(ticks: EpisodeTick.unwatched(3)), width: 320),
    );
    await tester.pump();
    expect(tester.getSize(find.byType(EpisodeSpine)).height, 48);
    expect(
      find.descendant(
        of: find.byType(EpisodeSpine),
        matching: find.byType(InkWell),
      ),
      findsNothing,
    );

    // The mini spine is unchanged too, at its own 12px pitch.
    await tester.pumpWidget(
      host(
        EpisodeSpine(
          ticks: EpisodeTick.unwatched(3),
          size: EpisodeSpineSize.mini,
        ),
        width: 320,
      ),
    );
    await tester.pump();
    expect(tester.getSize(find.byType(EpisodeSpine)).height, 36);
  });

  test('EpisodeSpine describes the in-progress state through AppUiCopy', () {
    final AppStrings copy = strings(AppLanguage.defaultLanguage);
    final String described = EpisodeSpine.describe(<EpisodeTick>[
      const EpisodeTick(
        number: 1,
        state: EpisodeTickState.inProgress,
        progress: 40,
      ),
      const EpisodeTick(number: 2, state: EpisodeTickState.none),
    ]);
    expect(AppUiCopy.episodeInProgress, 'in progress');
    expect(
      described,
      '2${copy.playerEpisodeUnit}, 1 ${AppUiCopy.episodeInProgress}',
    );
  });

  testWidgets('AppMessageBar sources tone words and close tooltip from '
      'AppUiCopy, with a 44px dismiss target', (WidgetTester tester) async {
    int dismissed = 0;
    await tester.pumpWidget(
      host(
        AppMessageBar(
          message: 'Saved',
          type: UiMessageType.success,
          autoDismiss: const Duration(seconds: 30),
          onDismissed: () => dismissed++,
        ),
        width: 320,
      ),
    );
    await tester.pump();

    expect(find.text(AppUiCopy.toneSuccess), findsOneWidget);
    expect(find.byIcon(Icons.check_circle_outline), findsOneWidget);
    final Finder close = find.byTooltip(AppUiCopy.dismissMessage);
    expect(close, findsOneWidget);
    final Rect target = tester.getRect(close);
    expect(target.width, greaterThanOrEqualTo(44));
    expect(target.height, greaterThanOrEqualTo(44));

    // The target is real, not just a box: tapping the corner beside the 18px
    // glyph dismisses the bar.
    await tester.tapAt(target.centerLeft + const Offset(3, 0));
    await tester.pump();
    expect(dismissed, 1);
    await tester.tapAt(target.centerLeft + const Offset(3, 0));
    await tester.pump();
    expect(dismissed, 1, reason: 'a dismissed message must not fire twice');
    expect(messageAutoDismissFallback, const Duration(seconds: 4));

    // Every tone still gets its own word and glyph.
    for (final (UiMessageType type, String word) in <(UiMessageType, String)>[
      (UiMessageType.error, AppUiCopy.toneError),
      (UiMessageType.info, AppUiCopy.toneInfo),
    ]) {
      await tester.pumpWidget(
        host(
          AppMessageBar(
            message: 'Changed',
            type: type,
            autoDismiss: const Duration(seconds: 30),
          ),
          width: 320,
        ),
      );
      await tester.pump();
      expect(find.text(word), findsOneWidget);
    }
  });
}

/// A [FocusNode] that reports whether anything still listens to it.
///
/// `ChangeNotifier.hasListeners` is `@protected`, so the suite reaches it
/// through a subclass rather than tripping `invalid_use_of_protected_member`.
/// Once a field is unmounted every legitimate listener is gone too, so `false`
/// here means the field did not leak one.
class _ListenerProbeFocusNode extends FocusNode {
  /// Whether at least one listener is still registered on this node.
  bool get hasAnyListeners => hasListeners;
}
