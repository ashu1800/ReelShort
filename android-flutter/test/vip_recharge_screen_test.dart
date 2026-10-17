/// Phase 4 regression suite for the redesigned VIP recharge sub-page.
///
/// Locks the frozen contract of `android-flutter/docs/ui-redesign-notes.md`
/// §7.10: the membership header, the section-card structure, the amount blocks
/// and ledger rows, the status pills, and the four order states (`create` /
/// `pending` / `confirmed` / guest). The order decision tree and every button
/// action stay the port's own.
library;

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';

import 'package:shortlink/core/formatting/app_strings.dart';
import 'package:shortlink/core/formatting/app_ui_copy.dart';
import 'package:shortlink/data/models/app_language.dart';
import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/state/app_state_controller.dart';
import 'package:shortlink/theme/app_spacing.dart';
import 'package:shortlink/theme/app_theme.dart';
import 'package:shortlink/ui/components/app_amount_block.dart';
import 'package:shortlink/ui/components/app_button.dart';
import 'package:shortlink/ui/components/app_loading.dart';
import 'package:shortlink/ui/components/app_section.dart';
import 'package:shortlink/ui/components/app_status_pill.dart';
import 'package:shortlink/ui/components/ledger_row.dart';
import 'package:shortlink/ui/screens/vip_recharge_screen.dart';

import 'support/fake_app_data_source.dart';

/// The English copy the screen renders in these tests.
final AppStrings _copy = strings(AppLanguage.defaultLanguage);

/// Matches the pending-order countdown line (`Expires in M:SS`).
final RegExp _countdownPattern = RegExp(
  '${strings(AppLanguage.defaultLanguage).vipPayExpiresIn} \\d+:\\d{2}',
);

/// A collection address fixture.
const String _address = 'TQ5NMqJjv5nN8o4Xb1cRkFv3i1n3oX9aBc';

/// A pending order whose countdown is still running.
VipOrder _pendingOrder({String? expiresAt}) => VipOrder(
  id: 'order-pending',
  orderNo: 'VIP-0001',
  usdtAmount: '0.14',
  status: 'PENDING',
  paymentMethod: 'TRC20',
  txHash: null,
  createdAt: '2026-10-17T00:00:00Z',
  confirmedAt: null,
  expiresAt: expiresAt ?? _futureIso(),
  receivingNetwork: 'TRC20',
  receivingWalletAddress: _address,
);

/// An order in a terminal state.
VipOrder _order(String status, {String amount = '0.14'}) => VipOrder(
  id: 'order-$status',
  orderNo: 'VIP-$status',
  usdtAmount: amount,
  status: status,
  paymentMethod: 'TRC20',
  txHash: status == 'CONFIRMED' ? 'tx-1' : null,
  createdAt: '2026-10-17T00:00:00Z',
  confirmedAt: status == 'CONFIRMED' ? '2026-10-17T00:05:00Z' : null,
  expiresAt: null,
);

String _futureIso() => DateTime.now()
    .add(const Duration(minutes: 9))
    .toUtc()
    .toIso8601String();

String _pastIso() => DateTime.now()
    .subtract(const Duration(minutes: 9))
    .toUtc()
    .toIso8601String();

/// Mounts the VIP recharge content inside the theme and a scrollable page.
Future<AppStateController> _pumpVip(
  WidgetTester tester, {
  bool loggedIn = true,
  bool vipActive = false,
  bool loadSnapshot = true,
  VipOrder? latestOrder,
  String vipPriceUsdt = '0.14',
  double width = 360,
  double height = 800,
  double textScale = 1,
  bool disableAnimations = false,
}) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = Size(width, height);
  addTearDown(tester.view.reset);

  final FakeAppDataSource source = FakeAppDataSource()
    ..session = const AuthSession(
      username: 'ashu180',
      token: 'token-1',
      tokenType: 'Bearer',
    )
    ..pointAccount = PointAccount(
      balance: 100,
      records: const <PointRecord>[],
      vipActive: vipActive,
      vipUntil: vipActive ? '2026-11-13 00:00:00' : null,
    )
    ..latestVipOrder = latestOrder
    ..wallet = WalletInfo(
      network: 'ERC20',
      walletAddress: null,
      updatedAt: null,
      vipPriceUsdt: vipPriceUsdt,
      vipCollectionAddress: _address,
    );
  if (!loggedIn) {
    source.session = null;
  }
  final AppStateController controller = AppStateController(dataSource: source);
  addTearDown(controller.dispose);
  if (loggedIn) {
    await controller.restoreSession();
    if (loadSnapshot) {
      await controller.openVipRecharge();
      if (latestOrder != null) {
        // The screen learns the order state through the refresh path, exactly
        // like the real flow.
        await controller.refreshLatestVipOrder();
      }
    }
  }

  await tester.pumpWidget(
    ChangeNotifierProvider<AppStateController>.value(
      value: controller,
      child: MaterialApp(
        theme: appTheme(Brightness.light),
        home: MediaQuery(
          data: MediaQueryData(
            size: Size(width, height),
            textScaler: TextScaler.linear(textScale),
            disableAnimations: disableAnimations,
          ),
          child: Scaffold(
            body: SingleChildScrollView(
              padding: AppSpacing.standard.pagePadding,
              child: const VipRechargeScreen(),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pump();
  return controller;
}

/// Unmounts the screen so a pending countdown timer is cancelled inside the
/// test instead of leaking into the next one.
Future<void> _dismiss(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump();
}

void main() {
  testWidgets('the page opens with the membership header and the price', (
    WidgetTester tester,
  ) async {
    await _pumpVip(tester);

    // Header: the shared membership chip, inactive and without an expiry.
    expect(find.text(_copy.accountVipStatusInactive), findsOneWidget);
    expect(find.textContaining(_copy.accountVipExpiryPrefix), findsNothing);
    // One section head above the order card.
    final List<AppSectionHeader> headers = tester
        .widgetList<AppSectionHeader>(find.byType(AppSectionHeader))
        .toList();
    expect(headers, hasLength(1));
    expect(headers.single.title, _copy.vipPayTitle);
    // Price as the page's amount block, with the existing suffix as its unit.
    final AppAmountBlock price = tester.widget<AppAmountBlock>(
      find.byType(AppAmountBlock),
    );
    expect(price.label, AppUiCopy.vipPrice);
    // The whole existing price line rides in the value, so it wraps instead of
    // overflowing at large text scales.
    expect(price.value, '0.14${_copy.accountVipPriceSuffix}');
    expect(price.unit, isNull);
    // The one primary action of the page: unlock.
    final List<AppButton> buttons = tester
        .widgetList<AppButton>(find.byType(AppButton))
        .toList();
    expect(buttons, hasLength(1));
    expect(buttons.single.label, _copy.accountVipUnlockAction);
    expect(buttons.single.variant, AppButtonVariant.primary);
  });

  testWidgets('an active member sees the active chip and the expiry', (
    WidgetTester tester,
  ) async {
    await _pumpVip(tester, vipActive: true);

    expect(find.text(_copy.accountVipStatusActive), findsWidgets);
    expect(
      find.textContaining(_copy.accountVipExpiryPrefix),
      findsOneWidget,
    );
    expect(find.text(_copy.accountVipUnlockAction), findsNothing);
  });

  testWidgets('the membership header stays neutral until the snapshot lands', (
    WidgetTester tester,
  ) async {
    await _pumpVip(tester, loadSnapshot: false);
    expect(
      find.byType(AppSkeleton),
      findsWidgets,
      reason: 'the unknown state is a plate, not an inactive claim',
    );
    expect(find.text(_copy.accountVipStatusInactive), findsNothing);
  });

  testWidgets('a pending order shows the amount, validity, QR and countdown', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpVip(
      tester,
      latestOrder: _pendingOrder(),
    );

    expect(find.byType(AppAmountBlock), findsOneWidget);
    expect(find.text('0.14 USDT'), findsOneWidget);
    expect(find.text('0.14 USDT'), findsOneWidget);
    // Ledger rows: order number and the validity date.
    final List<AppLedgerRow> rows = tester
        .widgetList<AppLedgerRow>(find.byType(AppLedgerRow))
        .toList();
    expect(rows.first.label, _copy.vipPayOrderNo);
    expect(rows.first.value, 'VIP-0001');
    expect(rows[1].label, AppUiCopy.vipValidity);
    // The locally generated QR sits on its own plate.
    expect(find.byType(QrImageView), findsOneWidget);
    expect(find.text(_address), findsOneWidget);
    // The countdown runs in M:SS.
    expect(
      find.textContaining(_countdownPattern),
      findsOneWidget,
    );
    // Refresh is the only action here, and it is the secondary variant.
    final AppButton refresh = tester.widget<AppButton>(
      find.ancestor(
        of: find.text(_copy.vipPayRefresh),
        matching: find.byType(AppButton),
      ),
    );
    expect(refresh.variant, AppButtonVariant.secondary);
    expect(controller.state.accountOperation, isNull);

    await _dismiss(tester);
  });

  testWidgets('the pending countdown ticks down', (WidgetTester tester) async {
    await _pumpVip(tester, latestOrder: _pendingOrder());

    final String before = tester
        .widget<Text>(
          find.textContaining(_countdownPattern),
        )
        .data!;
    await tester.pump(const Duration(seconds: 2));
    final String after = tester
        .widget<Text>(
          find.textContaining(_countdownPattern),
        )
        .data!;
    expect(before, isNot(after));
    await _dismiss(tester);
  });

  testWidgets('an expired pending order falls back to the create branch', (
    WidgetTester tester,
  ) async {
    await _pumpVip(
      tester,
      latestOrder: _pendingOrder(expiresAt: _pastIso()),
    );

    expect(find.text(_copy.accountVipUnlockAction), findsOneWidget);
    expect(
      find.textContaining(_countdownPattern),
      findsNothing,
    );
    expect(find.byType(QrImageView), findsNothing);
  });

  testWidgets('a confirmed order shows the success pill and the refresh', (
    WidgetTester tester,
  ) async {
    await _pumpVip(tester, latestOrder: _order('CONFIRMED'));

    expect(find.text(_copy.vipPayStatusConfirmed), findsOneWidget);
    // Second channel: the pill carries a glyph as well as the tone.
    expect(find.byIcon(Icons.check_circle_outline), findsOneWidget);
    expect(find.text(_copy.vipPayRefresh), findsOneWidget);
    expect(find.text(_copy.accountVipUnlockAction), findsNothing);
    expect(
      tester
          .widgetList<AppButton>(find.byType(AppButton))
          .where(
            (AppButton button) => button.variant == AppButtonVariant.primary,
          ),
      isEmpty,
    );
  });

  testWidgets('a rejected order shows its pill next to the create action', (
    WidgetTester tester,
  ) async {
    await _pumpVip(tester, latestOrder: _order('REJECTED'));

    expect(find.text(_copy.vipPayStatusRejected), findsOneWidget);
    expect(find.byIcon(Icons.error_outline), findsOneWidget);
    expect(find.text(_copy.accountVipUnlockAction), findsOneWidget);
  });

  testWidgets('an expired order uses the short pill and keeps the notice', (
    WidgetTester tester,
  ) async {
    // The expired notice is a full sentence: it must not be squeezed into the
    // capsule. The pill carries the short word; the sentence stays as a caption.
    await _pumpVip(
      tester,
      latestOrder: _order('EXPIRED'),
      width: 360,
      height: 640,
    );

    expect(tester.takeException(), isNull);
    expect(find.text(AppUiCopy.statusExpired), findsOneWidget);
    expect(find.text(_copy.vipPayExpired), findsOneWidget);
    expect(find.byType(AppStatusPill), findsOneWidget);
    expect(find.text(_copy.accountVipUnlockAction), findsOneWidget);
  });

  testWidgets('the pill states never overflow at 320x640 with 3.2x text', (
    WidgetTester tester,
  ) async {
    // The original defect: the status capsule sat in a non-flex slot and its
    // label never ellipsised. Confirmed, rejected and active were all measured
    // overflowing here.
    await _pumpVip(
      tester,
      latestOrder: _order('CONFIRMED'),
      width: 320,
      height: 640,
      textScale: 3.2,
      disableAnimations: true,
    );
    expect(tester.takeException(), isNull, reason: 'confirmed pill');
    await _dismiss(tester);

    await _pumpVip(
      tester,
      latestOrder: _order('REJECTED'),
      width: 320,
      height: 640,
      textScale: 3.2,
      disableAnimations: true,
    );
    expect(tester.takeException(), isNull, reason: 'rejected pill');
    await _dismiss(tester);

    await _pumpVip(
      tester,
      vipActive: true,
      width: 320,
      height: 640,
      textScale: 3.2,
      disableAnimations: true,
    );
    expect(tester.takeException(), isNull, reason: 'active pill');
    await _dismiss(tester);
  });

  testWidgets('an active member sees the status word twice, not three times', (
    WidgetTester tester,
  ) async {
    // Header chip + status pill; the duplicate body text next to the pill is
    // gone.
    await _pumpVip(tester, vipActive: true);
    expect(find.text(_copy.accountVipStatusActive), findsNWidgets(2));
  });

  testWidgets('tapping unlock creates the order', (WidgetTester tester) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..session = const AuthSession(
        username: 'ashu180',
        token: 'token-1',
        tokenType: 'Bearer',
      );
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    addTearDown(controller.dispose);
    await controller.restoreSession();
    await controller.openVipRecharge();
    await tester.pumpWidget(
      ChangeNotifierProvider<AppStateController>.value(
        value: controller,
        child: MaterialApp(
          theme: appTheme(Brightness.light),
          home: const Scaffold(
            body: SingleChildScrollView(child: VipRechargeScreen()),
          ),
        ),
      ),
    );
    await tester.pump();

    await tester.tap(find.text(_copy.accountVipUnlockAction));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(dataSource.callsTo('createVipOrder'), 1);
  });

  testWidgets('tapping refresh re-reads the latest order', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..session = const AuthSession(
        username: 'ashu180',
        token: 'token-1',
        tokenType: 'Bearer',
      )
      ..latestVipOrder = _order('CONFIRMED');
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    addTearDown(controller.dispose);
    await controller.restoreSession();
    await controller.openVipRecharge();
    // The screen learns the order state through the refresh path.
    await controller.refreshLatestVipOrder();
    await tester.pumpWidget(
      ChangeNotifierProvider<AppStateController>.value(
        value: controller,
        child: MaterialApp(
          theme: appTheme(Brightness.light),
          home: const Scaffold(
            body: SingleChildScrollView(child: VipRechargeScreen()),
          ),
        ),
      ),
    );
    await tester.pump();
    final int before = dataSource.callsTo('loadLatestVipOrder');

    await tester.tap(find.text(_copy.vipPayRefresh));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(dataSource.callsTo('loadLatestVipOrder'), greaterThan(before));
  });

  testWidgets('the copy action copies the receiving address', (
    WidgetTester tester,
  ) async {
    final List<String?> copied = <String?>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (MethodCall call) async {
        if (call.method == 'Clipboard.setData') {
          copied.add((call.arguments as Map<Object?, Object?>)['text'] as String?);
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );

    await _pumpVip(tester, latestOrder: _pendingOrder());
    await tester.tap(find.text(_copy.vipPayCopy));
    await tester.pump();
    expect(copied, <String?>[_address]);
    await _dismiss(tester);
  });

  testWidgets('a guest sees only the sign-in gate', (WidgetTester tester) async {
    final AppStateController controller = await _pumpVip(
      tester,
      loggedIn: false,
    );

    expect(find.text(_copy.fundsLoginRequired), findsOneWidget);
    expect(find.text(_copy.accountGuestSignIn), findsOneWidget);
    expect(find.text(_copy.accountVipUnlockAction), findsNothing);
    expect(find.text(_copy.vipPayRefresh), findsNothing);
    final List<AppButton> buttons = tester
        .widgetList<AppButton>(find.byType(AppButton))
        .toList();
    expect(buttons, hasLength(1));
    expect(buttons.single.variant, AppButtonVariant.primary);

    await tester.tap(find.text(_copy.accountGuestSignIn));
    await tester.pump();
    expect(controller.state.authPromptVisible, isTrue);
  });

  testWidgets('the page survives 320x640 at 3.2x text', (
    WidgetTester tester,
  ) async {
    await _pumpVip(
      tester,
      latestOrder: _pendingOrder(),
      width: 320,
      height: 640,
      textScale: 3.2,
      disableAnimations: true,
    );

    expect(tester.takeException(), isNull);
    await tester.drag(find.byType(SingleChildScrollView), const Offset(0, -400));
    await tester.pump();
    expect(tester.takeException(), isNull);
    await _dismiss(tester);
  });

  testWidgets('the page lays out on a 1440px surface', (
    WidgetTester tester,
  ) async {
    await _pumpVip(tester, width: 1440, height: 900);
    expect(tester.takeException(), isNull);
    expect(find.text(_copy.accountVipUnlockAction), findsOneWidget);
  });

  test('the VIP screen paints only from tokens', () {
    final String source = File(
      'lib/ui/screens/vip_recharge_screen.dart',
    ).readAsStringSync();
    // A bare Colors. literal, not the AppColors. token extension.
    expect(
      RegExp(r'(?<![A-Za-z])Colors\.[A-Za-z]+').hasMatch(source),
      isFalse,
    );
    expect(source.contains('Color(0x'), isFalse);
    expect(
      RegExp(r'EdgeInsets\.[A-Za-z]+\([^)\n]*[(:,]\s*\d').hasMatch(source),
      isFalse,
    );
  });
}
