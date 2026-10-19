/// Layout regression tests for the update dialog's download figure.
///
/// The dialog renders real device values whose length nobody controls (sizes,
/// percents). Two failure modes have shipped from this widget:
///
///  * a `RenderFlex` overflow, which in debug builds paints the red
///    "RIGHT OVERFLOWED BY N PIXELS" banner over the dialog copy;
///  * truncation, where a bounded single line ellipsised the total away
///    (`29% · 16.2 MB / 54.…`) so the user could not read the remaining bytes.
///
/// The figure is exercised on its own — `DownloadProgressRow` is public precisely
/// so this contract can be tested without wiring the coordinator's async
/// download plumbing.
library;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:shortlink/core/formatting/update_ui_format.dart';
import 'package:shortlink/theme/app_theme.dart';
import 'package:shortlink/ui/update/update_dialog.dart';
import 'package:shortlink/update/release_models.dart';

/// Renders the figure at a given width and text scale.
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
                  amountColor: Colors.black,
                  secondaryColor: Colors.grey,
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
  testWidgets('percent and total bytes are both readable in full', (
    WidgetTester tester,
  ) async {
    const DownloadProgress progress = DownloadProgress(44433333, 56668800);
    await _pump(tester, progress);

    expect(tester.takeException(), isNull);

    final int percent = updateProgressPercent(progress)!;
    expect(find.text('$percent%'), findsOneWidget);
    // The whole amount is on screen, not ellipsised: this is the regression the
    // single-line layout caused.
    expect(
      find.text(
        '${formatUpdateBytes(44433333)} of ${formatUpdateBytes(56668800)}',
      ),
      findsOneWidget,
    );
    // The static "Downloading update" label moved to the dialog title; the
    // figure no longer repeats it.
    expect(find.text(updateStrings().downloading), findsNothing);
  });

  testWidgets('an unknown total keeps the single byte figure', (
    WidgetTester tester,
  ) async {
    await _pump(tester, const DownloadProgress(44433333, null));

    expect(tester.takeException(), isNull);
    expect(find.text('of'), findsNothing);
    expect(find.text(formatUpdateBytes(44433333)), findsOneWidget);
    // With no percentage the figure falls back to the state label.
    expect(find.text(updateStrings().downloading), findsOneWidget);
  });

  testWidgets('the narrowest dialog at 3.2x text still shows the full amount', (
    WidgetTester tester,
  ) async {
    // Worst realistic case: a small dialog and the largest font setting. The
    // bytes line may wrap to two lines, but it must not overflow or truncate.
    const DownloadProgress progress = DownloadProgress(44433333, 56668800);
    await _pump(tester, progress, width: 140, textScale: 3.2);

    expect(tester.takeException(), isNull);
    final Finder bytes = find.text(
      '${formatUpdateBytes(44433333)} of ${formatUpdateBytes(56668800)}',
    );
    expect(bytes, findsOneWidget);
  });
}
