/// Real-device acceptance for the authentication and playback loops.
///
/// Runs against the production object graph and the real backend
/// (`flutter test integration_test -d <device>`), so every request below is a
/// real network call. The acceptance account is injected at build time and
/// never committed:
///
/// ```
/// flutter test integration_test -d <device> \
///   --dart-define=ACCEPTANCE_USERNAME=... \
///   --dart-define=ACCEPTANCE_PASSWORD=...
/// ```
///
/// Note: `flutter test` uninstalls the app when the run finishes, so anything
/// this file persists cannot be checked from a later run (or from a cold
/// start) — cross-process session persistence needs a manual install-and-use
/// pass instead.
library;

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:integration_test/integration_test.dart';

import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/main.dart';
import 'package:shortlink/platform/app_environment.dart';
import 'package:shortlink/state/app_state_controller.dart';
import 'package:shortlink/state/app_ui_state.dart';
import 'package:shortlink/update/release_downloader.dart';
import 'package:shortlink/update/release_models.dart';
import 'package:shortlink/update/short_link_update_client.dart';
import 'package:shortlink/update/update_coordinator.dart';

/// The dedicated acceptance account, injected via `--dart-define`.
const String _username = String.fromEnvironment('ACCEPTANCE_USERNAME');
const String _password = String.fromEnvironment('ACCEPTANCE_PASSWORD');

void _requireCredentials() {
  if (_username.isEmpty || _password.isEmpty) {
    fail(
      'Acceptance credentials missing. Pass '
      '--dart-define=ACCEPTANCE_USERNAME=... and '
      '--dart-define=ACCEPTANCE_PASSWORD=....',
    );
  }
}

Future<UpdateCoordinator> _realCoordinator() async {
  return UpdateCoordinator(
    releaseClient: ShortLinkUpdateClient(
      httpClient: http.Client(),
      currentVersionName: '0.9.2',
    ),
    downloader: HttpReleaseDownloader(
      httpClient: http.Client(),
      targetDirectory: Directory.systemTemp,
    ),
    currentVersion: const SemanticVersion(9, 2, 0),
    currentVersionCode: 44,
    availableBytes: (Directory _) => 1 << 40,
    // No installer in the test harness: the install bridges become no-ops.
  );
}

void main() {
  final IntegrationTestWidgetsFlutterBinding binding =
      IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('login establishes a stable session on the Me tab', (
    WidgetTester tester,
  ) async {
    _requireCredentials();
    final AppEnvironment environment = await AppEnvironment.bootstrap();
    // Warm the secure storage before the shell boots: its first read takes
    // seconds on real hardware, and a late restore must never race the login
    // below. Wipe any stale persisted session as well.
    await environment.dataSource.restoreSession();
    await environment.dataSource.clearSession();

    final AppStateController controller = AppStateController(
      dataSource: environment.dataSource,
    );

    await tester.pumpWidget(
      ShortLinkApp(
        controller: controller,
        updateCoordinator: await _realCoordinator(),
      ),
    );
    await pumpForNetwork(tester, const Duration(seconds: 4));

    // A stale session from a previous run would already be logged in; start
    // every run from the guest state.
    if (controller.state.session != null) {
      unawaited(controller.logout());
      await pumpForNetwork(tester, const Duration(seconds: 3));
    }
    expect(controller.state.session, isNull);

    unawaited(controller.openAccount());
    await pumpForNetwork(tester, const Duration(seconds: 4));
    controller.showAuthPrompt();
    await pumpForNetwork(tester, const Duration(seconds: 2));

    await tester.enterText(
      find.widgetWithText(TextField, 'Username'),
      _username,
    );
    await tester.pump(const Duration(milliseconds: 300));
    await tester.enterText(
      find.widgetWithText(TextField, 'Password'),
      _password,
    );
    await tester.pump(const Duration(milliseconds: 300));

    await tester.tap(find.text('Sign in').last);
    await pumpForNetwork(tester, const Duration(seconds: 12));


    debugPrint(
      'after login: session=${controller.state.session?.username} '
      'error=${controller.state.errorMessage} '
      'prompt=${controller.state.authPromptVisible} '
      'screen=${controller.state.screen}',
    );
    // THE assertion under test: a successful login must leave a live session
    // (the audit suspect is a post-login 401 wiping it).
    expect(controller.state.session?.username, _username);
    expect(controller.state.authPromptVisible, isFalse);
    binding.takeException();
  });

  testWidgets('openBook reaches the player with a playable URL', (
    WidgetTester tester,
  ) async {
    _requireCredentials();
    final AppEnvironment environment = await AppEnvironment.bootstrap();
    await environment.dataSource.restoreSession();
    await environment.dataSource.clearSession();

    final AppStateController controller = AppStateController(
      dataSource: environment.dataSource,
    );
    await tester.pumpWidget(
      ShortLinkApp(
        controller: controller,
        updateCoordinator: await _realCoordinator(),
      ),
    );
    await pumpForNetwork(tester, const Duration(seconds: 4));

    if (controller.state.session == null) {
      await _login(tester, controller);
    }
    expect(controller.state.session, isNotNull);

    await controller.refreshHome();
    await pumpForNetwork(tester, const Duration(seconds: 10));
    expect(controller.state.homeShelf, isNotEmpty);

    final BookSummary book = controller.state.homeShelf.first;
    await controller.openBook(book);
    await pumpForNetwork(tester, const Duration(seconds: 25));

    debugPrint(
      'player: screen=${controller.state.screen} '
      'hasUrl=${controller.state.playback.videoUrl?.url != null} '
      'episodes=${controller.state.episodes.length} '
      'error=${controller.state.errorMessage}',
    );
    expect(controller.state.screen, AppScreen.player);
    expect(controller.state.playback.videoUrl?.url, isNotNull);
    binding.takeException();
  });
}

Future<void> _login(WidgetTester tester, AppStateController controller) async {
  unawaited(controller.openAccount());
  await pumpForNetwork(tester, const Duration(seconds: 4));
  controller.showAuthPrompt();
  await pumpForNetwork(tester, const Duration(seconds: 2));
  await tester.enterText(
    find.widgetWithText(TextField, 'Username'),
    _username,
  );
  await tester.pump(const Duration(milliseconds: 300));
  await tester.enterText(
    find.widgetWithText(TextField, 'Password'),
    _password,
  );
  await tester.pump(const Duration(milliseconds: 300));
  await tester.tap(find.text('Sign in').last);
  await pumpForNetwork(tester, const Duration(seconds: 12));
}

/// Pumps repeatedly for [duration] so real network calls settle even while
/// widgets keep animating (spinners never let `pumpAndSettle` finish).
Future<void> pumpForNetwork(WidgetTester tester, Duration duration) async {
  final DateTime end = DateTime.now().add(duration);
  while (DateTime.now().isBefore(end)) {
    await tester.pump(const Duration(milliseconds: 200));
  }
}
