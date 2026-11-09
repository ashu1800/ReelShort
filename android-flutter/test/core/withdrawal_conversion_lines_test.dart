/// Unit tests for the withdrawal conversion lines (`withdrawalConversionLines`).
///
/// The rule these lock: the fee is taken off the **USDT amount**, never off the
/// points. The submitted points convert in full to a gross USDT figure, the
/// percentage is cut from that money, and the two lines always add up to the
/// gross. A regression here is a money bug that a screen test would only notice
/// as changed copy.
library;

import 'package:flutter_test/flutter_test.dart';

import 'package:shortlink/core/formatting/ui_formats.dart';
import 'package:shortlink/data/models/app_language.dart';
import 'package:shortlink/data/models/app_models.dart';

const AppLanguage _language = AppLanguage.defaultLanguage;

WithdrawalSummary _summary({int feePercent = 10}) => WithdrawalSummary(
  balance: 100000,
  frozenPoints: 0,
  availablePoints: 100000,
  minimumPoints: 3572,
  usdtPerPoint: '0.0028',
  usdtPer50Points: '0.14',
  minimumUsdt: '10.00',
  walletAddress: '0x1111111111111111111111111111111111111111',
  feePercent: feePercent,
);

void main() {
  test('the fee comes off the USDT amount, not off the points', () {
    // 4000 points -> 11.20 USDT gross; the 10% fee is 1.12 USDT and the payout
    // is 10.08 USDT. No "you receive: 3600 pts" line may come back.
    final List<String> lines = withdrawalConversionLines(
      _summary(),
      4000,
      _language,
    );

    expect(lines.skip(1), <String>[
      'Estimated 11.2 USDT',
      'Fee: 10% (1.12 USDT) · You receive: 10.08 USDT',
    ]);
  });

  test('the minimum withdrawal pays out ten percent less money', () {
    final List<String> lines = withdrawalConversionLines(
      _summary(),
      3572,
      _language,
    );

    // 3572 points -> 10.00 USDT gross -> 9.00 USDT after the fee.
    expect(lines.skip(1), <String>[
      'Estimated 10 USDT',
      'Fee: 10% (1 USDT) · You receive: 9 USDT',
    ]);
  });

  test('a zero fee pays the whole gross amount', () {
    final List<String> lines = withdrawalConversionLines(
      _summary(feePercent: 0),
      4000,
      _language,
    );

    expect(lines.skip(1), <String>['Estimated 11.2 USDT']);
  });
}
