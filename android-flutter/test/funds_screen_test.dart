/// Phase 4 regression suite for the redesigned Funds page.
///
/// Locks the frozen structure of `android-flutter/docs/ui-redesign-notes.md`
/// §7.4: the page-title role, the balance hero (VIP / non-VIP / guest / unknown
/// snapshot), the ACTIONS grid, the three sections with their empty states, the
/// VIP order status mapping, and a loading skeleton shaped like the content that
/// replaces it.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:shortlink/core/formatting/app_ui_copy.dart';
import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/state/app_state_controller.dart';
import 'package:shortlink/state/app_ui_state.dart';
import 'package:shortlink/theme/app_colors.dart';
import 'package:shortlink/theme/app_spacing.dart';
import 'package:shortlink/theme/app_theme.dart';
import 'package:shortlink/theme/app_typography.dart';
import 'package:shortlink/ui/components/app_amount_block.dart';
import 'package:shortlink/ui/components/app_button.dart';
import 'package:shortlink/ui/components/app_loading.dart';
import 'package:shortlink/ui/components/ledger_row.dart';
import 'package:shortlink/ui/screens/funds_screen.dart';

import 'support/fake_app_data_source.dart';

/// A data source whose health probe never answers: it holds the controller in
/// its loading state without leaving a timer behind.
class _StalledAppDataSource extends FakeAppDataSource {
  final Completer<ApiHealthStatus> _stalled = Completer<ApiHealthStatus>();

  @override
  Future<ApiHealthStatus> checkSystemHealth() => _stalled.future;
}

/// One VIP order fixture.
VipOrder _vipOrder(String id, String amount, String status) => VipOrder(
  id: id,
  orderNo: 'NO-$id',
  usdtAmount: amount,
  status: status,
  paymentMethod: 'TRC20',
  txHash: null,
  createdAt: '2026-10-0${id.substring(1)}T00:00:00Z',
  confirmedAt: null,
);

/// The mounted funds page inside the app's theme and provider scope.
Widget _host(
  AppStateController controller, {
  double width = 360,
  double height = 900,
  double textScale = 1,
  bool disableAnimations = false,
}) => ChangeNotifierProvider<AppStateController>.value(
  value: controller,
  child: MaterialApp(
    theme: appTheme(Brightness.light),
    home: MediaQuery(
      data: MediaQueryData(
        size: Size(width, height),
        textScaler: TextScaler.linear(textScale),
        disableAnimations: disableAnimations,
      ),
      child: const Scaffold(body: FundsScreen()),
    ),
  ),
);

/// Mounts the funds page for a fresh controller and returns it.
Future<AppStateController> _pumpFunds(
  WidgetTester tester, {
  bool loggedIn = true,
  bool vip = false,
  bool loadSnapshot = true,
  int balance = 100,
  int todayCredited = 0,
  int frozen = 0,
  List<PointRecord> records = const <PointRecord>[],
  List<VipOrder> vipOrders = const <VipOrder>[],
  List<RechargeOrderSummary> orders = const <RechargeOrderSummary>[],
  double width = 360,
  double height = 900,
  double textScale = 1,
  bool disableAnimations = false,
}) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = Size(width, height);
  addTearDown(tester.view.reset);

  final FakeAppDataSource dataSource = FakeAppDataSource()
    ..session = loggedIn
        ? const AuthSession(
            username: 'ashu180',
            token: 'token-1',
            tokenType: 'Bearer',
          )
        : null
    ..pointAccount = PointAccount(
      balance: balance,
      records: records,
      frozenPoints: frozen,
      vipActive: vip,
      vipUntil: vip ? '2026-11-13 00:00:00' : null,
      todayCreditedPoints: todayCredited,
    )
    ..vipOrders = vipOrders
    ..orders = orders;
  final AppStateController controller = AppStateController(
    dataSource: dataSource,
  );
  addTearDown(controller.dispose);
  if (loggedIn) {
    await controller.restoreSession();
  }
  if (loadSnapshot) {
    // The real entry point: it navigates to the tab and loads the snapshot.
    await controller.openFunds();
  }
  await tester.pumpWidget(
    _host(
      controller,
      width: width,
      height: height,
      textScale: textScale,
      disableAnimations: disableAnimations,
    ),
  );
  await tester.pump();
  return controller;
}

/// The hero's resolved decoration, which is where the membership state lives.
BoxDecoration _heroDecoration(WidgetTester tester) {
  final Container hero = tester.widget<Container>(
    find.byKey(fundsBalanceHeroKey),
  );
  return hero.decoration! as BoxDecoration;
}

/// The hero's border colour.
Color _heroBorder(WidgetTester tester) =>
    (_heroDecoration(tester).border! as Border).top.color;

/// Scrolls the page until [finder] is mounted.
///
/// The page is a [ListView], so a section near the end of it does not exist in
/// the tree before the list is scrolled to it.
Future<void> _scrollTo(WidgetTester tester, Finder finder) async {
  await tester.scrollUntilVisible(
    finder,
    200,
    scrollable: find.byType(Scrollable).first,
  );
}

/// Every piece of text the semantics tree exposes, in traversal order.
///
/// A label merged into an ancestor is simply absent, which is what makes this a
/// collapse detector; both label and value channels are collected.
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

void main() {
  testWidgets('the funds page uses the one page-title role and the page inset', (
    WidgetTester tester,
  ) async {
    await _pumpFunds(tester, vip: true);

    // The shared `screenTitle` role (22 px), not the old `titleLarge` (16 px).
    final Text title = tester.widget<Text>(find.text('Funds center'));
    expect(title.style?.fontSize, AppTypography.light.screenTitle.fontSize);
    expect(
      title.style?.fontSize,
      isNot(appTheme(Brightness.light).textTheme.titleLarge!.fontSize),
      reason: 'the page used to render its title in titleLarge',
    );

    final ListView list = tester.widget<ListView>(find.byType(ListView).first);
    expect(list.padding, AppSpacing.standard.pagePadding);

    // Sections come from the shared header, which upper-cases.
    expect(find.text('ACTIONS'), findsOneWidget);
    expect(find.text('POINT RECORDS'), findsOneWidget);
    // The last section sits below the fold of a phone-sized page.
    await _scrollTo(tester, find.text('VIP ORDERS'));
    expect(find.text('VIP ORDERS'), findsOneWidget);
  });

  testWidgets('the VIP hero wears the premium fill, the chip and the metrics', (
    WidgetTester tester,
  ) async {
    await _pumpFunds(
      tester,
      vip: true,
      balance: 29184,
      todayCredited: 245,
      frozen: 10,
    );

    expect(_heroDecoration(tester).color, AppColors.light.premiumSurface);
    expect(_heroBorder(tester), AppColors.light.accentPrimary);
    expect(find.text('VIP active'), findsOneWidget);
    expect(find.text('Expires 2026-11-13'), findsOneWidget);
    expect(find.byIcon(Icons.verified_outlined), findsOneWidget);

    // Available points lead the hero; total/credited/frozen support it.
    expect(find.text('Available points'), findsOneWidget);
    expect(find.text('29,174'), findsOneWidget);
    expect(find.text('pts'), findsOneWidget);
    expect(find.text('Total points'), findsOneWidget);
    expect(find.text('29,184'), findsOneWidget);
    expect(find.text('Credited today'), findsOneWidget);
    expect(find.text('245'), findsOneWidget);
    expect(find.text('Frozen points'), findsOneWidget);

    final AppLedgerRow frozenRow = tester.widget<AppLedgerRow>(
      find.ancestor(
        of: find.text('Frozen points'),
        matching: find.byType(AppLedgerRow),
      ),
    );
    expect(
      frozenRow.valueColor,
      AppColors.light.accentWarning,
      reason: 'non-zero frozen points take the warning accent',
    );
    expect(find.byType(AppAmountBlock), findsOneWidget);
  });

  testWidgets('a non-VIP member gets the plain hero and the inactive chip', (
    WidgetTester tester,
  ) async {
    await _pumpFunds(tester, vip: false, frozen: 0);

    expect(_heroDecoration(tester).color, AppColors.light.surface);
    expect(_heroBorder(tester), AppColors.light.hairline);
    expect(find.text('VIP not active'), findsOneWidget);
    expect(find.text('Expires 2026-11-13'), findsNothing);
    expect(find.text('Available points'), findsOneWidget);
  });

  testWidgets('the guest hero offers the sign-in action and no metrics', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpFunds(
      tester,
      loggedIn: false,
    );

    expect(find.text('Points, withdrawals, and VIP membership'), findsOneWidget);
    expect(find.text('Sign in to view your points'), findsOneWidget);
    expect(find.text(AppUiCopy.fundsGuestAction), findsOneWidget);
    // No metrics and no membership claim at all.
    expect(find.byType(AppAmountBlock), findsNothing);
    expect(find.byType(AppLedgerRow), findsNothing);
    expect(find.text('Available points'), findsNothing);
    expect(find.text('Total points'), findsNothing);
    expect(find.text('VIP active'), findsNothing);
    expect(find.text('VIP not active'), findsNothing);

    await tester.tap(find.text(AppUiCopy.fundsGuestAction));
    await tester.pump();
    expect(controller.state.authPromptVisible, isTrue);
  });

  testWidgets('the hero stays neutral until the snapshot arrives', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpFunds(
      tester,
      loadSnapshot: false,
    );
    expect(controller.state.isLoading, isFalse);
    expect(controller.state.pointAccount, isNull);

    final Finder hero = find.byKey(fundsBalanceHeroKey);
    expect(
      find.descendant(of: hero, matching: find.byType(AppSkeleton)),
      findsWidgets,
      reason: 'the unknown state is a neutral plate, not a zero',
    );
    expect(find.byType(AppAmountBlock), findsNothing);
    expect(find.text('VIP not active'), findsNothing);
    expect(find.text('VIP active'), findsNothing);
    expect(find.text('0'), findsNothing);

    // Once the snapshot lands the plates become real figures.
    await controller.openFunds();
    await tester.pump();
    expect(
      find.descendant(of: hero, matching: find.byType(AppSkeleton)),
      findsNothing,
    );
    expect(find.text('VIP not active'), findsOneWidget);
    expect(find.text('Available points'), findsOneWidget);
    expect(find.text('100'), findsWidgets);
  });

  testWidgets('the action tiles keep their signed-in behaviour', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpFunds(tester);
    expect(find.text('Withdraw'), findsOneWidget);
    expect(find.text('Convert points to USDT'), findsOneWidget);
    expect(find.text('VIP recharge'), findsOneWidget);
    expect(find.text('Open VIP membership'), findsOneWidget);

    // Withdraw is the one filled action of the region; the VIP entry is its
    // outlined alternative. Both used to default to `primary`.
    AppButton buttonFor(String label) => tester.widget<AppButton>(
      find.ancestor(of: find.text(label), matching: find.byType(AppButton)),
    );
    expect(buttonFor('Withdraw').variant, AppButtonVariant.primary);
    expect(buttonFor('VIP recharge').variant, AppButtonVariant.secondary);
    // A signed-in page renders exactly these two buttons, so this count is the
    // ACTIONS region's count.
    final List<AppButton> buttons = tester
        .widgetList<AppButton>(find.byType(AppButton))
        .toList();
    expect(buttons, hasLength(2));
    expect(
      buttons.where(
        (AppButton button) => button.variant == AppButtonVariant.primary,
      ),
      hasLength(1),
    );

    await tester.tap(find.text('Withdraw'));
    await tester.pump();
    expect(controller.state.screen, AppScreen.withdrawal);

    await controller.openFunds();
    await tester.pump();
    await tester.tap(find.text('VIP recharge'));
    await tester.pump();
    expect(controller.state.screen, AppScreen.vipRecharge);
  });

  testWidgets('the action tiles send a guest to the auth prompt', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpFunds(
      tester,
      loggedIn: false,
    );

    await tester.tap(find.text('Withdraw'));
    await tester.pump();
    expect(controller.state.authPromptVisible, isTrue);
    expect(controller.state.screen, AppScreen.funds);

    // The guest page has a hero CTA as well, but the action tiles keep their
    // own weights.
    expect(
      tester
          .widget<AppButton>(
            find.ancestor(
              of: find.text('VIP recharge'),
              matching: find.byType(AppButton),
            ),
          )
          .variant,
      AppButtonVariant.secondary,
    );

    controller.dismissAuthPrompt();
    await tester.pump();
    await tester.tap(find.text('VIP recharge'));
    await tester.pump();
    expect(controller.state.authPromptVisible, isTrue);
    expect(controller.state.screen, AppScreen.funds);
  });

  testWidgets('the sections render the shared empty states', (
    WidgetTester tester,
  ) async {
    await _pumpFunds(tester);

    expect(find.text(AppUiCopy.fundsPointRecordsEmptyTitle), findsOneWidget);
    expect(find.text(AppUiCopy.fundsPointRecordsEmptyBody), findsOneWidget);
    expect(find.text(AppUiCopy.fundsVipOrdersEmptyTitle), findsOneWidget);
    expect(find.text(AppUiCopy.fundsVipOrdersEmptyBody), findsOneWidget);
    // The legacy section is absent while the backend returns no old orders.
    expect(find.text('VIP ORDERS'), findsOneWidget);
  });

  testWidgets('the ledger rows keep their signs, reasons and negative accent', (
    WidgetTester tester,
  ) async {
    await _pumpFunds(
      tester,
      records: const <PointRecord>[
        PointRecord(amount: 120, reason: 'Watch reward'),
        PointRecord(amount: -50),
      ],
    );

    expect(find.text('Watch reward'), findsOneWidget);
    expect(find.text('120'), findsOneWidget);
    // A record without a reason falls back to the shared label.
    expect(find.text('Point record'), findsOneWidget);
    expect(find.text('-50'), findsOneWidget);

    final AppLedgerRow negative = tester.widget<AppLedgerRow>(
      find.ancestor(
        of: find.text('Point record'),
        matching: find.byType(AppLedgerRow),
      ),
    );
    expect(negative.valueColor, AppColors.light.accentDanger);
  });

  testWidgets('VIP orders keep their status mapping and amount wording', (
    WidgetTester tester,
  ) async {
    await _pumpFunds(
      tester,
      vipOrders: <VipOrder>[
        _vipOrder('o1', '0.14', 'PENDING'),
        _vipOrder('o2', '0.28', 'CONFIRMED'),
        _vipOrder('o3', '0.42', 'REJECTED'),
      ],
    );

    expect(find.text('Amount: 0.14 USDT'), findsOneWidget);
    expect(find.text('Amount: 0.28 USDT'), findsOneWidget);
    expect(find.text('Amount: 0.42 USDT'), findsOneWidget);
    expect(find.text('Awaiting confirmation'), findsOneWidget);
    expect(find.text('Confirmed'), findsOneWidget);
    expect(find.text('Rejected'), findsOneWidget);
    expect(find.text('NO-o1\n2026-10-01T00:00:00Z'), findsOneWidget);

    expect(
      tester.widget<Text>(find.text('Awaiting confirmation')).style?.color,
      AppColors.light.accentWarning,
    );
    expect(
      tester.widget<Text>(find.text('Confirmed')).style?.color,
      AppColors.light.accentSuccess,
    );
    expect(
      tester.widget<Text>(find.text('Rejected')).style?.color,
      AppColors.light.accentDanger,
    );
    // No empty state while orders exist.
    expect(find.text(AppUiCopy.fundsVipOrdersEmptyTitle), findsNothing);
  });

  testWidgets('legacy recharge orders render read-only when present', (
    WidgetTester tester,
  ) async {
    await _pumpFunds(
      tester,
      orders: const <RechargeOrderSummary>[
        RechargeOrderSummary(
          orderNo: 'R-1',
          amountCents: 100,
          pointAmount: 12,
          status: 'PENDING',
        ),
      ],
    );

    // The legacy section reuses `accountOrdersTitle`, which is the same copy as
    // the VIP orders header (pre-existing), so the header appears twice while
    // both sections are on screen.
    await _scrollTo(tester, find.text('R-1'));
    expect(find.text('R-1'), findsOneWidget);
    expect(find.text('12 pts'), findsOneWidget);
    expect(find.text('PENDING'), findsOneWidget);
    expect(find.text('VIP ORDERS'), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the loading state is content-shaped and never zero width', (
    WidgetTester tester,
  ) async {
    final _StalledAppDataSource dataSource = _StalledAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    addTearDown(controller.dispose);
    unawaited(controller.checkApiHealth());
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 640);
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_host(controller, width: 360));
    await tester.pump();

    expect(controller.state.isLoading, isTrue);
    // The generic list is gone: the skeleton is shaped like the arriving page.
    expect(find.byType(AppSkeletonList), findsNothing);
    expect(find.byType(AppSkeleton), findsWidgets);
    expect(find.byType(AppSkeletonText), findsWidgets);
    // A hero-shaped plate stands where the balance hero will land.
    final Finder heroPlaceholder = find.byKey(fundsBalanceHeroPlaceholderKey);
    expect(heroPlaceholder, findsOneWidget);
    expect(tester.getRect(heroPlaceholder).width, greaterThan(0));
    final Finder blocks = find.byType(AppSkeleton);
    for (int index = 0; index < blocks.evaluate().length; index++) {
      final Size size = tester.getSize(blocks.at(index));
      expect(size.width, greaterThan(0), reason: 'block $index is visible');
      expect(size.width, lessThanOrEqualTo(360));
    }
    // The real content is not rendered while loading.
    expect(find.text('ACTIONS'), findsNothing);
    expect(find.byKey(fundsBalanceHeroKey), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the sections keep their rows and trailing values readable', (
    WidgetTester tester,
  ) async {
    final SemanticsHandle handle = tester.ensureSemantics();
    await _pumpFunds(
      tester,
      records: const <PointRecord>[
        PointRecord(amount: -50, reason: 'Withdrawal'),
      ],
      vipOrders: <VipOrder>[_vipOrder('o1', '0.14', 'PENDING')],
    );

    final List<String> top = semanticsTexts(tester);
    expect(top, contains('Total points, 100'));
    // The read-only rows expose no tap; the money actions do.
    final SemanticsData total = tester.semantics
        .find(find.bySemanticsLabel('Total points, 100'))
        .getSemanticsData();
    expect(total.hasAction(SemanticsAction.tap), isFalse);
    final SemanticsData withdraw = tester.semantics
        .find(find.bySemanticsLabel('Withdraw'))
        .getSemanticsData();
    expect(withdraw.hasAction(SemanticsAction.tap), isTrue);

    // The lower sections are lazily built: scroll to them before reading.
    await _scrollTo(tester, find.text('Awaiting confirmation'));
    final List<String> texts = semanticsTexts(tester);
    expect(texts, contains('Withdrawal, -50'));
    // The order row stays one announcement: amount, order number, date and the
    // status word (a plain trailing text merges into the row's own node).
    expect(
      texts.any(
        (String text) =>
            text.contains('Amount: 0.14 USDT') &&
            text.contains('NO-o1') &&
            text.contains('Awaiting confirmation'),
      ),
      isTrue,
    );
    handle.dispose();
  });

  testWidgets('the page lays out at 320px with 3.2x text under reduced motion', (
    WidgetTester tester,
  ) async {
    await _pumpFunds(
      tester,
      vip: true,
      balance: 29184,
      todayCredited: 245,
      frozen: 10,
      records: const <PointRecord>[PointRecord(amount: 120, reason: 'Watch reward')],
      vipOrders: <VipOrder>[_vipOrder('o1', '0.14', 'PENDING')],
      width: 320,
      height: 720,
      textScale: 3.2,
      disableAnimations: true,
    );

    expect(tester.takeException(), isNull);
    expect(find.byKey(fundsBalanceHeroKey), findsOneWidget);
    final ScrollableState scrollable = tester.state<ScrollableState>(
      find.byType(Scrollable).first,
    );
    expect(scrollable.position.maxScrollExtent, greaterThan(0));
    // The hero owns the first screen at this scale; the sections follow.
    await _scrollTo(tester, find.text('ACTIONS'));
    expect(find.text('ACTIONS'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the page lays out on a 1440px tablet', (
    WidgetTester tester,
  ) async {
    await _pumpFunds(
      tester,
      vip: false,
      balance: 29184,
      records: const <PointRecord>[
        PointRecord(amount: 120, reason: 'Watch reward'),
      ],
      width: 1440,
      height: 900,
      disableAnimations: true,
    );

    expect(tester.takeException(), isNull);
    expect(find.text('POINT RECORDS'), findsOneWidget);
    expect(find.text('Watch reward'), findsOneWidget);
  });
}
