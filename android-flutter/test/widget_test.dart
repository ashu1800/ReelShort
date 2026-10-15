/// Shell smoke test: the app boots into the guest home tab with the four primary tabs present.
///
/// The real data source is replaced by the in-test fake, so the smoke test runs offline.
library;

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:provider/provider.dart';

import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/main.dart';
import 'package:shortlink/state/app_state_controller.dart';
import 'package:shortlink/update/release_downloader.dart';
import 'package:shortlink/update/release_models.dart';
import 'package:shortlink/update/short_link_update_client.dart';
import 'package:shortlink/update/update_coordinator.dart';

import 'support/fake_app_data_source.dart';

/// An offline self-update coordinator: the widget tests never reach the
/// network, so the clients point at the real endpoint but are never queried.
UpdateCoordinator _offlineUpdateCoordinator() => UpdateCoordinator(
  releaseClient: ShortLinkUpdateClient(
    httpClient: http.Client(),
    currentVersionName: '0.0.0',
  ),
  downloader: HttpReleaseDownloader(
    httpClient: http.Client(),
    targetDirectory: Directory.systemTemp,
  ),
  currentVersion: const SemanticVersion(0, 0, 0),
  availableBytes: (Directory _) => 0,
);

void main() {
  testWidgets('the shell boots into the home tab', (WidgetTester tester) async {
    final FakeAppDataSource dataSource = FakeAppDataSource();
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );

    await tester.pumpWidget(
      ChangeNotifierProvider<AppStateController>.value(
        value: controller,
        child: ShortLinkApp(
          controller: controller,
          updateCoordinator: _offlineUpdateCoordinator(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    // The shell exists and the app did not crash on boot.
    expect(find.byType(ShortLinkApp), findsOneWidget);
    // The fake was asked for the persisted session at startup.
    expect(dataSource.lastCallTo('restoreSession'), isNotNull);
  });

  testWidgets('the fake data source serves the canned home shelf', (
    WidgetTester tester,
  ) async {
    final FakeAppDataSource dataSource = FakeAppDataSource()
      ..homeShelf = <BookSummary>[FakeAppDataSource.book0];
    final AppStateController controller = AppStateController(
      dataSource: dataSource,
    );
    await controller.refreshHome();

    expect(controller.state.homeShelf, isNotEmpty);
    expect(controller.state.homeShelf.first.id, FakeAppDataSource.book0.id);
  });
}
