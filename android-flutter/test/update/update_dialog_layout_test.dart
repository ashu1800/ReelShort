/// Layout regression tests for the update dialog's download row.
///
/// The dialog renders real device values whose length nobody controls (sizes,
/// percents). A `RenderFlex` overflow there is not cosmetic: in debug builds
/// Flutter paints the red "RIGHT OVERFLOWED BY N PIXELS" banner straight over
/// the dialog copy, which is what the 0.9.6 screenshot showed.
///
/// The row is exercised on its own — `DownloadProgressRow` is public precisely so
/// this layout contract can be tested without wiring the coordinator's async
/// download plumbing.
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:shortlink/core/formatting/update_ui_format.dart';
import 'package:shortlink/theme/app_theme.dart';
import 'package:shortlink/ui/update/update_dialog.dart';
import 'package:shortlink/update/release_models.dart';

/// Renders the row at the dialog's narrowest realistic content width.
Future<void> _pump(
  WidgetTester tester,
  DownloadProgress progress, {
  double width = 220,
  double textScale = 1,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      theme: appTheme(Brightness.light),
      home: Scaffold(
        body: Center(
          child: SizedBox(
            width: width,
            child: MediaQuery(
              data: MediaQueryData(textScaler: TextScaler.linear(textScale)),
              child: Builder(
                builder: (BuildContext context) => DownloadProgressRow(
                  progress: progress,
                  textTheme: Theme.of(context).textTheme,
                  labelColor: Colors.grey,
                  amountColor: Colors.black,
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pump();
}

void main() {
  testWidgets('a long amount ellipsises instead of overflowing', (
    WidgetTester tester,
  ) async {
    // 78% · 42.4 MB / 54.0 MB — an amount long enough to overflow the row.
    await _pump(
      tester,
      const DownloadProgress(44433333, 56668800),
    );

    expect(
      tester.takeException(),
      isNull,
      reason: 'the label/amount row must not overflow the dialog',
    );
    expect(find.text(updateStrings().downloading), findsOneWidget);
    expect(
      find.text('${updateProgressPercent(const DownloadProgress(44433333, 56668800))}% · '
          '${formatUpdateBytes(44433333)} / ${formatUpdateBytes(56668800)}'),
      findsOneWidget,
    );
  });

  testWidgets('an unknown total keeps the plain amount', (
    WidgetTester tester,
  ) async {
    await _pump(tester, const DownloadProgress(44433333, null));

    expect(tester.takeException(), isNull);
    expect(find.textContaining('%'), findsNothing);
    expect(find.text(formatUpdateBytes(44433333)), findsOneWidget);
  });

  testWidgets('the row survives a narrow width at 3.2x text', (
    WidgetTester tester,
  ) async {
    // The worst realistic case: a small dialog and the largest font setting.
    await _pump(
      tester,
      const DownloadProgress(44433333, 56668800),
      width: 160,
      textScale: 3.2,
    );

    expect(tester.takeException(), isNull);
  });
}
