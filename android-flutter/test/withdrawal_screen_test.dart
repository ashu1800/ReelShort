/// Phase 4 regression suite for the redesigned withdrawal sub-page.
///
/// Locks the frozen contract of `android-flutter/docs/ui-redesign-notes.md`
/// §7.10: the available-points header, the two-step form, the status pills and
/// the history card. The submit gate, the digits-only field, the placeholder
/// swap and the status mapping stay the port's own.
library;

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:shortlink/core/formatting/app_strings.dart';
import 'package:shortlink/core/formatting/app_ui_copy.dart';
import 'package:shortlink/core/formatting/ui_formats.dart';
import 'package:shortlink/data/models/app_language.dart';
import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/state/app_state_controller.dart';
import 'package:shortlink/theme/app_spacing.dart';
import 'package:shortlink/theme/app_theme.dart';
import 'package:shortlink/ui/components/app_amount_block.dart';
import 'package:shortlink/ui/components/app_button.dart';
import 'package:shortlink/ui/components/app_empty_state.dart';
import 'package:shortlink/ui/components/app_section.dart';
import 'package:shortlink/ui/components/ledger_row.dart';
import 'package:shortlink/ui/screens/withdrawal_screen.dart';

import 'support/fake_app_data_source.dart';

/// The English copy the screen renders in these tests.
final AppStrings _copy = strings(AppLanguage.defaultLanguage);

/// A bound ERC20 wallet address fixture.
const String _wallet = '0x1111111111111111111111111111111111111111';

/// A withdrawal summary: 10,000 points available, 3,572 minimum.
const WithdrawalSummary _summary = WithdrawalSummary(
  balance: 10000,
  frozenPoints: 0,
  availablePoints: 10000,
  minimumPoints: 3572,
  usdtPerPoint: '0.0028',
  usdtPer50Points: '0.14',
  minimumUsdt: '10.00',
  walletAddress: _wallet,
  feePercent: 1,
);

/// A withdrawal record fixture.
WithdrawalRecord _record(String status, {String amount = '3572'}) =>
    WithdrawalRecord(
      id: 'w-$status',
      pointAmount: int.parse(amount),
      usdtAmount: '10.00',
      usdtPerPoint: '0.0028',
      network: 'ERC20',
      walletAddress: _wallet,
      status: status,
      txHash: status == 'APPROVED' ? '0xdeadbeef' : null,
      adminNote: status == 'REJECTED' ? 'Address mismatch' : null,
      createdAt: '2026-10-16T00:00:00Z',
      reviewedAt: status == 'PENDING' ? null : '2026-10-17T00:00:00Z',
    );

/// Mounts the withdrawal content inside the theme and a scrollable page.
Future<AppStateController> _pumpWithdrawal(
  WidgetTester tester, {
  bool loggedIn = true,
  bool loadSnapshot = true,
  WithdrawalSummary summary = _summary,
  List<WithdrawalRecord> withdrawals = const <WithdrawalRecord>[],
  double width = 360,
  double height = 900,
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
    ..withdrawalSummary = summary
    ..withdrawals = withdrawals
    ..wallet = const WalletInfo(
      network: 'ERC20',
      walletAddress: _wallet,
      updatedAt: null,
    );
  if (!loggedIn) {
    source.session = null;
  }
  final AppStateController controller = AppStateController(dataSource: source);
  addTearDown(controller.dispose);
  if (loggedIn) {
    await controller.restoreSession();
    if (loadSnapshot) {
      await controller.openWithdrawal();
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
              child: const WithdrawalScreen(),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pump();
  return controller;
}

/// Types [amount] into the point field.
Future<void> _enterAmount(WidgetTester tester, String amount) async {
  await tester.enterText(find.byType(TextField), amount);
  await tester.pump();
}

/// The submit button of the amount step.
AppButton _submitButton(WidgetTester tester) => tester.widget<AppButton>(
  find.ancestor(
    of: find.text(_copy.accountWithdrawSubmitAction),
    matching: find.byType(AppButton),
  ),
);

void main() {
  testWidgets('the page opens with the available figure and the records', (
    WidgetTester tester,
  ) async {
    await _pumpWithdrawal(tester);

    final AppAmountBlock available = tester.widget<AppAmountBlock>(
      find.byType(AppAmountBlock),
    );
    expect(available.label, _copy.accountWithdrawAvailableLabel);
    expect(available.value, groupedDigits(_summary.availablePoints));
    expect(available.unit, _copy.listPointsLabel);
    // The section head above the history card.
    final List<AppSectionHeader> headers = tester
        .widgetList<AppSectionHeader>(find.byType(AppSectionHeader))
        .toList();
    expect(headers, hasLength(1));
    expect(headers.single.title, _copy.accountWithdrawalRecordsTitle);
    // Empty history uses the shared copy.
    expect(find.byType(AppEmptyState), findsOneWidget);
    expect(find.text(AppUiCopy.withdrawalRecordsEmptyTitle), findsOneWidget);
    // Exactly one primary action: submit.
    final List<AppButton> buttons = tester
        .widgetList<AppButton>(find.byType(AppButton))
        .toList();
    expect(buttons, hasLength(1));
    expect(buttons.single.variant, AppButtonVariant.primary);
    expect(buttons.single.label, _copy.accountWithdrawSubmitAction);
  });

  testWidgets('a guest sees the sign-in gate and no form', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpWithdrawal(
      tester,
      loggedIn: false,
    );

    expect(find.text(_copy.fundsLoginRequired), findsOneWidget);
    expect(find.text(_copy.accountGuestSignIn), findsOneWidget);
    expect(find.byType(AppAmountBlock), findsNothing);
    expect(find.byType(TextField), findsNothing);

    await tester.tap(find.text(_copy.accountGuestSignIn));
    await tester.pump();
    expect(controller.state.authPromptVisible, isTrue);
  });

  testWidgets('the submit gate follows the minimum and the balance', (
    WidgetTester tester,
  ) async {
    await _pumpWithdrawal(tester);

    // Below the dynamic minimum: closed.
    await _enterAmount(tester, '100');
    expect(_submitButton(tester).onPressed, isNull);
    // Above the available balance: closed.
    await _enterAmount(tester, '20000');
    expect(_submitButton(tester).onPressed, isNull);
    // Inside the window: open.
    await _enterAmount(tester, '5000');
    expect(_submitButton(tester).onPressed, isNotNull);
  });

  testWidgets('a missing wallet keeps the gate closed and warns', (
    WidgetTester tester,
  ) async {
    await _pumpWithdrawal(
      tester,
      summary: const WithdrawalSummary(
        balance: 10000,
        frozenPoints: 0,
        availablePoints: 10000,
        minimumPoints: 3572,
        usdtPerPoint: '0.0028',
        usdtPer50Points: '0.14',
        minimumUsdt: '10.00',
        walletAddress: null,
        feePercent: 1,
      ),
    );

    expect(find.text(_copy.accountWithdrawWalletRequired), findsOneWidget);
    expect(find.byIcon(Icons.error_outline), findsOneWidget);
    await _enterAmount(tester, '5000');
    expect(_submitButton(tester).onPressed, isNull);
  });

  testWidgets('the field ignores non-digit input', (WidgetTester tester) async {
    await _pumpWithdrawal(tester);

    await _enterAmount(tester, '12ab34');
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      '1234',
    );
  });

  testWidgets('the form shows the fee and arrival lines once an amount is set', (
    WidgetTester tester,
  ) async {
    await _pumpWithdrawal(tester);

    // Before typing only the wallet line is there (the summary line lives in
    // the header).
    expect(find.textContaining('Estimated'), findsNothing);
    await _enterAmount(tester, '5000');
    expect(find.textContaining('Estimated'), findsOneWidget);
    expect(find.textContaining('Fee:'), findsOneWidget);
  });

  testWidgets('the review step restates the amount and offers confirm/edit', (
    WidgetTester tester,
  ) async {
    await _pumpWithdrawal(tester);
    await _enterAmount(tester, '5000');
    await tester.tap(find.text(_copy.accountWithdrawSubmitAction));
    await tester.pump();

    // The confirmation title and the amount as a ledger row.
    expect(
      find.text(accountConfirmationTitle(AppLanguage.defaultLanguage)),
      findsOneWidget,
    );
    expect(find.byType(AppLedgerRow), findsOneWidget);
    final AppLedgerRow row = tester.widget<AppLedgerRow>(
      find.byType(AppLedgerRow),
    );
    expect(row.label, _copy.accountWithdrawSubmitAction);
    expect(row.value, '5000${_copy.accountPointsSuffix}');
    // One primary (confirm) and one secondary (edit).
    final List<AppButton> buttons = tester
        .widgetList<AppButton>(find.byType(AppButton))
        .toList();
    expect(buttons, hasLength(2));
    expect(buttons.first.variant, AppButtonVariant.primary);
    expect(buttons.first.label, accountConfirmationConfirm(AppLanguage.defaultLanguage));
    expect(buttons.last.variant, AppButtonVariant.secondary);
    expect(buttons.last.label, AppUiCopy.withdrawalEditAmount);

    // Edit returns to the amount step without losing the draft.
    await tester.tap(find.text(AppUiCopy.withdrawalEditAmount));
    await tester.pump();
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      '5000',
    );
  });

  testWidgets('confirming dispatches the typed points', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..session = const AuthSession(
        username: 'ashu180',
        token: 'token-1',
        tokenType: 'Bearer',
      )
      ..withdrawalSummary = _summary
      ..wallet = const WalletInfo(
        network: 'ERC20',
        walletAddress: _wallet,
        updatedAt: null,
      );
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    addTearDown(controller.dispose);
    await controller.restoreSession();
    await controller.openWithdrawal();
    await tester.pumpWidget(
      ChangeNotifierProvider<AppStateController>.value(
        value: controller,
        child: MaterialApp(
          theme: appTheme(Brightness.light),
          home: const Scaffold(
            body: SingleChildScrollView(child: WithdrawalScreen()),
          ),
        ),
      ),
    );
    await tester.pump();

    await _enterAmount(tester, '5000');
    await tester.tap(find.text(_copy.accountWithdrawSubmitAction));
    await tester.pump();
    await tester.tap(find.text(accountConfirmationConfirm(AppLanguage.defaultLanguage)));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(dataSource.callsTo('submitWithdrawal'), 1);
    final RecordedCall call = dataSource.lastCallTo('submitWithdrawal')!;
    expect(call.arguments['pointAmount'], 5000);
  });

  testWidgets('the history maps each status to its pill and tone', (
    WidgetTester tester,
  ) async {
    await _pumpWithdrawal(
      tester,
      withdrawals: <WithdrawalRecord>[
        _record('PENDING'),
        _record('APPROVED'),
        _record('REJECTED'),
      ],
    );

    expect(find.text('Pending'), findsOneWidget);
    expect(find.text('Completed'), findsOneWidget);
    expect(find.text('Rejected'), findsOneWidget);
    // Second channel: each pill carries its own glyph.
    expect(find.byIcon(Icons.schedule), findsOneWidget);
    expect(find.byIcon(Icons.check_circle_outline), findsOneWidget);
    expect(find.byIcon(Icons.error_outline), findsOneWidget);
    // The amount and the machine-value line are present, newest first.
    expect(find.text('3572 pts · 10.00 USDT'), findsNWidgets(3));
    expect(
      find.textContaining('ERC20 · $_wallet · 2026-10-16T00:00:00Z'),
      findsNWidgets(3),
    );
  });

  testWidgets('an approved record shows its settlement detail', (
    WidgetTester tester,
  ) async {
    await _pumpWithdrawal(
      tester,
      withdrawals: <WithdrawalRecord>[_record('APPROVED')],
    );
    expect(find.textContaining('0xdeadbeef'), findsOneWidget);
  });

  testWidgets('the page survives 320x640 at 3.2x text', (
    WidgetTester tester,
  ) async {
    await _pumpWithdrawal(
      tester,
      withdrawals: <WithdrawalRecord>[_record('PENDING')],
      width: 320,
      height: 640,
      textScale: 3.2,
      disableAnimations: true,
    );

    expect(tester.takeException(), isNull);
    await tester.drag(find.byType(SingleChildScrollView), const Offset(0, -400));
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  testWidgets('the page lays out on a 1440px surface', (
    WidgetTester tester,
  ) async {
    await _pumpWithdrawal(tester, width: 1440, height: 900);
    expect(tester.takeException(), isNull);
    expect(find.byType(AppAmountBlock), findsOneWidget);
  });

  test('the withdrawal screen paints only from tokens', () {
    final String source = File(
      'lib/ui/screens/withdrawal_screen.dart',
    ).readAsStringSync();
    // A bare `Colors.` literal, not the `AppColors.` token extension.
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
