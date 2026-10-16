/// Phase 4 regression suite for the redesigned Me page.
///
/// Locks the frozen structure of `android-flutter/docs/ui-redesign-notes.md`
/// §7.1: one `screenTitle` page title, the identity hero (VIP / non-VIP / guest),
/// the two `AppSectionCard` sections, the continue-watching header pill, the
/// settings rows, the destructive sign-out - plus the behaviours the older
/// suites guard (`Sign in`, `Create account`, `Bind wallet`, `Watch history`).
library;

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:provider/provider.dart';

import 'package:shortlink/core/formatting/app_strings.dart';
import 'package:shortlink/core/formatting/app_ui_copy.dart';
import 'package:shortlink/core/formatting/navigation_contract.dart';
import 'package:shortlink/core/formatting/update_ui_format.dart';
import 'package:shortlink/data/models/app_language.dart';
import 'package:shortlink/data/models/app_models.dart';
import 'package:shortlink/state/app_state_controller.dart';
import 'package:shortlink/state/app_ui_state.dart';
import 'package:shortlink/theme/app_colors.dart';
import 'package:shortlink/theme/app_spacing.dart';
import 'package:shortlink/theme/app_theme.dart';
import 'package:shortlink/theme/app_typography.dart';
import 'package:shortlink/ui/components/app_button.dart';
import 'package:shortlink/ui/components/app_list_row.dart';
import 'package:shortlink/ui/components/app_loading.dart';
import 'package:shortlink/ui/components/app_membership_chip.dart';
import 'package:shortlink/ui/components/app_section.dart';
import 'package:shortlink/ui/components/poster_card.dart';
import 'package:shortlink/ui/screens/account_screen.dart';
import 'package:shortlink/ui/update/update_dialog.dart';
import 'package:shortlink/update/release_downloader.dart';
import 'package:shortlink/update/release_models.dart';
import 'package:shortlink/update/short_link_update_client.dart';
import 'package:shortlink/update/update_coordinator.dart';

import 'support/fake_app_data_source.dart';

/// The wallet address used by the "bound" cases.
const String _address = '0x1111111111111111111111111111111111111111';

/// An offline self-update coordinator: the version row only needs a coordinator
/// to exist, and no test here reaches the network.
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

/// Three records, so the continue-watching preview has to trim to its limit.
List<WatchRecord> _history() => <WatchRecord>[
  const WatchRecord(
    bookId: 'book-1',
    bookTitle: 'Drama One',
    episode: 3,
    progressPercent: 25,
  ),
  const WatchRecord(
    bookId: 'book-2',
    bookTitle: 'Drama Two',
    episode: 2,
    progressPercent: 100,
  ),
  const WatchRecord(
    bookId: 'book-3',
    bookTitle: 'Drama Three',
    episode: 1,
    progressPercent: 10,
  ),
];

/// Mounts the Me page for [controller] and returns it.
Future<AppStateController> _pumpAccount(
  WidgetTester tester, {
  bool loggedIn = true,
  bool vip = false,
  String? walletAddress,
  List<WatchRecord> history = const <WatchRecord>[],
  double width = 360,
  double height = 900,
  double textScale = 1,
  bool disableAnimations = false,
  bool withUpdateCoordinator = false,
  bool loadSnapshot = true,
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
      balance: 1000,
      records: const <PointRecord>[],
      vipActive: vip,
      vipUntil: vip ? '2026-11-13 00:00:00' : null,
    )
    ..wallet = WalletInfo(
      network: 'ERC20',
      walletAddress: walletAddress,
      updatedAt: null,
    )
    ..watchHistory = history
    ..episodes = <EpisodeSummary>[
      const EpisodeSummary(number: 1, chapterId: 'ch-1'),
      const EpisodeSummary(number: 2, chapterId: 'ch-2'),
      const EpisodeSummary(number: 3, chapterId: 'ch-3'),
    ];
  final AppStateController controller = AppStateController(
    dataSource: dataSource,
  );
  addTearDown(controller.dispose);
  if (loggedIn) {
    await controller.restoreSession();
  }
  if (loadSnapshot) {
    await controller.openAccount();
  }

  Widget app = MaterialApp(
    theme: appTheme(Brightness.light),
    home: MediaQuery(
      data: MediaQueryData(
        size: Size(width, height),
        textScaler: TextScaler.linear(textScale),
        disableAnimations: disableAnimations,
      ),
      child: const AccountScreen(),
    ),
  );
  if (withUpdateCoordinator) {
    app = UpdateDialogScope(
      coordinator: _offlineUpdateCoordinator(),
      child: app,
    );
  }
  await tester.pumpWidget(
    ChangeNotifierProvider<AppStateController>.value(
      value: controller,
      child: app,
    ),
  );
  await tester.pump();
  return controller;
}

/// The hero's resolved decoration, which is where the membership state lives.
BoxDecoration _heroDecoration(WidgetTester tester) {
  final Container hero = tester.widget<Container>(
    find.byKey(accountIdentityHeroKey),
  );
  return hero.decoration! as BoxDecoration;
}

/// The width [finder]'s text needs to render without an ellipsis.
///
/// Widget tests use a font whose glyphs are about 1.4x wider than Roboto's, so
/// comparing the rendered box with this number is the only metric-independent
/// way to assert "not truncated".
double _intrinsicWidth(WidgetTester tester, Finder finder) {
  final Text text = tester.widget<Text>(finder);
  final TextPainter painter = TextPainter(
    text: TextSpan(text: text.data, style: text.style),
    textDirection: TextDirection.ltr,
    textScaler: MediaQuery.textScalerOf(tester.element(finder)),
    maxLines: text.maxLines,
  )..layout();
  return painter.width;
}

/// The width a `SelectableText` needs to render its whole value.
///
/// `find.text` matches the [EditableText] inside a `SelectableText`, so the
/// controller's text and its style are what a painter needs.
double intrinsicTextWidth(WidgetTester tester, Finder finder) {
  final EditableText field = tester.widget<EditableText>(finder);
  return (TextPainter(
    text: TextSpan(text: field.controller.text, style: field.style),
    textDirection: TextDirection.ltr,
    textScaler: MediaQuery.textScalerOf(tester.element(finder)),
    maxLines: 1,
  )..layout()).width;
}

/// The hero's border colour.
Color _heroBorder(WidgetTester tester) =>
    (_heroDecoration(tester).border! as Border).top.color;

/// Scrolls the page until [finder] is mounted.
///
/// The page is a [ListView], so a target at the end of it (the sign-out button)
/// does not exist in the tree before the list is scrolled to it. In widget tests
/// every glyph is one em wide, which makes the test page noticeably taller than
/// the same page on a device.
Future<void> _scrollTo(WidgetTester tester, Finder finder) async {
  await tester.scrollUntilVisible(
    finder,
    200,
    scrollable: find.byType(Scrollable).first,
  );
}

/// Every piece of text the semantics tree exposes, in traversal order.
///
/// Both channels are collected: the selectable support email is announced as the
/// *value* of its read-only text-field node, not as a label. A label merged into
/// an ancestor is simply absent, which is what makes this a collapse detector.
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

/// The bank-card notice, resolved through the generated table.
String _bankCardNotice() =>
    strings(AppLanguage.defaultLanguage).accountBankCardUnsupported;

void main() {
  testWidgets('the logged-in Me page uses one title role and two section cards', (
    WidgetTester tester,
  ) async {
    await _pumpAccount(tester, vip: true, walletAddress: _address);

    // The page title is the shared `screenTitle` role (22 px), not the old
    // `displayLarge` (26 px) the card used before the redesign.
    final Text title = tester.widget<Text>(find.text('Me'));
    expect(title.style?.fontSize, AppTypography.light.screenTitle.fontSize);
    expect(title.style?.fontWeight, AppTypography.light.screenTitle.fontWeight);
    expect(
      title.style?.fontSize,
      isNot(AppTypography.light.displayLarge.fontSize),
      reason: 'the old page title used displayLarge',
    );

    // Shortcuts + Settings, and the section headers come from the shared
    // component (which upper-cases through sectionHeaderTransform).
    expect(find.byType(AppSectionCard), findsAtLeast(2));
    expect(find.text('SHORTCUTS'), findsOneWidget);
    expect(find.text('SETTINGS'), findsOneWidget);

    // Exactly the three parity quick entries.
    expect(find.text('Favorites'), findsOneWidget);
    expect(find.text('Watch history'), findsOneWidget);
    expect(find.text('Change password'), findsOneWidget);

    // The settings card carries the four rows of the spec.
    expect(find.text('Cold wallet'), findsOneWidget);
    expect(find.text(_address), findsOneWidget);
    expect(find.text('Replace wallet'), findsOneWidget);
    expect(find.text('Bank card'), findsOneWidget);
    expect(find.text('Contact support'), findsOneWidget);
    expect(find.text('shortlink@gmail.com'), findsOneWidget);
    expect(find.text(updateStrings(AppLanguage.defaultLanguage).versionTitle), findsOneWidget);

    // Sign-out is the destructive variant and still has no confirmation step.
    await _scrollTo(tester, find.text('Sign out'));
    final Finder signOut = find.ancestor(
      of: find.text('Sign out'),
      matching: find.byType(AppButton),
    );
    expect(signOut, findsOneWidget);
    expect(
      tester.widget<AppButton>(signOut).variant,
      AppButtonVariant.destructive,
    );
  });

  testWidgets('a VIP hero wears the premium fill, the accent border, the chip '
      'and the expiry', (WidgetTester tester) async {
    await _pumpAccount(tester, vip: true, walletAddress: _address);

    expect(_heroDecoration(tester).color, AppColors.light.premiumSurface);
    expect(_heroBorder(tester), AppColors.light.accentPrimary);
    expect(find.text('ashu180'), findsOneWidget);
    expect(find.text('VIP active'), findsOneWidget);
    expect(find.text('Expires 2026-11-13'), findsOneWidget);
    // State never rides on colour alone: the chip carries a glyph too.
    expect(find.byIcon(Icons.verified_outlined), findsOneWidget);
  });

  testWidgets('a non-VIP member gets the plain hero and the inactive chip', (
    WidgetTester tester,
  ) async {
    await _pumpAccount(tester, vip: false, walletAddress: _address);

    expect(_heroDecoration(tester).color, AppColors.light.surface);
    expect(_heroBorder(tester), AppColors.light.hairline);
    expect(find.text('VIP not active'), findsOneWidget);
    expect(find.byIcon(Icons.lock_outline), findsOneWidget);
    expect(find.text('Expires 2026-11-13'), findsNothing);
  });

  testWidgets('the guest hero carries the only two calls to action', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpAccount(
      tester,
      loggedIn: false,
    );

    // `ui_wiring_test` depends on exactly these two labels: at least one
    // `Sign in`, exactly one `Create account`.
    expect(find.text('Sign in'), findsOneWidget);
    expect(find.text('Create account'), findsOneWidget);
    expect(find.text('Guest'), findsOneWidget);
    // The monogram uses the guest label's initial, not a question mark.
    expect(find.text('G'), findsOneWidget);
    expect(find.text('?'), findsNothing);

    // A guest has no account sections at all.
    expect(find.text('Favorites'), findsNothing);
    expect(find.text('Watch history'), findsNothing);
    expect(find.text('Cold wallet'), findsNothing);
    expect(find.text('Bind wallet'), findsNothing);
    expect(find.text('Bank card'), findsNothing);
    expect(find.text('Sign out'), findsNothing);
    expect(find.byType(AppSectionCard), findsOneWidget);
    // Exactly the two account-free settings rows: the support row (its own
    // layout, so the mailbox keeps the full width) and the app version.
    expect(find.byType(AppListRow), findsOneWidget);
    expect(find.text('shortlink@gmail.com'), findsOneWidget);
    expect(_heroDecoration(tester).color, AppColors.light.surface);
    expect(_heroBorder(tester), AppColors.light.hairline);

    // The two entries still drive the shared auth prompt.
    await tester.tap(find.text('Create account'));
    await tester.pump();
    expect(controller.state.authPromptVisible, isTrue);
    expect(controller.state.authMode, AuthMode.register);
  });

  testWidgets('the shortcut rows keep their behaviour', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpAccount(
      tester,
      walletAddress: _address,
      history: _history(),
    );

    await tester.tap(find.text('Favorites'));
    await tester.pumpAndSettle();
    expect(controller.state.screen, AppScreen.favorites);

    await controller.openAccount();
    await tester.pumpAndSettle();
    await tester.tap(find.text('Watch history'));
    await tester.pumpAndSettle();
    // The row title plus the sheet's own header, and the record list inside.
    expect(find.text('Watch history'), findsNWidgets(2));
    expect(find.text('Drama One'), findsWidgets);
    await tester.tap(find.byIcon(Icons.close));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Change password'));
    await tester.pumpAndSettle();
    // The shortcut row, the sheet's own title and its submit button all carry
    // the same label, so three occurrences are the sheet being open.
    expect(find.text('Change password'), findsNWidgets(3));
    expect(find.byType(TextField), findsNWidgets(2));
  });

  testWidgets('the wallet row opens the sheet from the row and from its button', (
    WidgetTester tester,
  ) async {
    await _pumpAccount(tester);

    expect(find.text('Bind wallet'), findsOneWidget);
    await tester.tap(find.text('Bind wallet'));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNWidgets(2));
    Navigator.of(tester.element(find.byType(TextField).first)).pop();
    await tester.pumpAndSettle();

    // The row itself is a target too, like every other entry on the page.
    await tester.tap(find.text('Cold wallet'));
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNWidgets(2));
  });

  testWidgets('the wallet address stays on one line with a trailing action', (
    WidgetTester tester,
  ) async {
    await _pumpAccount(tester, walletAddress: _address);

    // The 42-character address ellipsises instead of pushing the row into a
    // paragraph (the sheet is where the full value is read), and the trailing
    // action never wraps either.
    final Text address = tester.widget<Text>(find.text(_address));
    expect(address.maxLines, 1);
    expect(address.overflow, TextOverflow.ellipsis);
    final Text action = tester.widget<Text>(find.text('Replace wallet'));
    expect(action.maxLines, 1);
    expect(action.overflow, TextOverflow.ellipsis);

    final Rect row = tester.getRect(
      find.ancestor(
        of: find.text('Cold wallet'),
        matching: find.byType(AppListRow),
      ),
    );
    // One line of the value plus one line of the title, whatever the ambient
    // font measures: before the fix the address alone occupied six lines.
    expect(tester.getSize(find.text(_address)).height, lessThan(30));
    expect(
      row.height,
      lessThan(140),
      reason: 'a single-line value keeps the row at its regular height',
    );
  });

  testWidgets('the hero does not claim a membership state before the snapshot', (
    WidgetTester tester,
  ) async {
    // On device the session lands seconds before the account snapshot; the
    // hero used to announce "VIP not active" for that window.
    final AppStateController controller = await _pumpAccount(
      tester,
      loadSnapshot: false,
    );
    expect(controller.state.session, isNotNull);
    expect(controller.state.pointAccount, isNull);

    expect(find.text('Vip not active'), findsNothing);
    expect(find.text('VIP not active'), findsNothing);
    expect(find.text('VIP active'), findsNothing);
    expect(find.textContaining('Expires'), findsNothing);
    // A neutral plate stands in for the chip, and the hero keeps the plain
    // (non-premium) surface until the state is known.
    expect(find.byType(AppSkeleton), findsOneWidget);
    expect(_heroDecoration(tester).color, AppColors.light.surface);
    expect(_heroBorder(tester), AppColors.light.hairline);

    // Once the snapshot lands the chip replaces the plate in the same place.
    await controller.openAccount();
    await tester.pump();
    expect(find.byType(AppSkeleton), findsNothing);
    expect(find.text('VIP not active'), findsOneWidget);
  });

  testWidgets('the settings card keeps its rows and controls in the semantics '
      'tree', (WidgetTester tester) async {
    // Regression for the collapse: with a tappable and a plain row in one card,
    // the whole card became one node and the support mailbox / Check button
    // vanished from the accessibility tree.
    await _pumpAccount(
      tester,
      walletAddress: _address,
      withUpdateCoordinator: true,
    );
    final SemanticsHandle handle = tester.ensureSemantics();
    final UpdateStrings updateCopy = updateStrings(AppLanguage.defaultLanguage);
    await _scrollTo(tester, find.text(updateCopy.versionTitle));
    final List<String> texts = semanticsTexts(tester);
    // Every row is its own node, and neither the card nor the list swallowed
    // them.
    expect(texts, contains('Cold wallet, $_address'));
    expect(texts, contains('Bank card, ${_bankCardNotice()}'));
    expect(texts, contains('Contact support'));
    expect(texts, contains('${updateCopy.versionTitle}, --'));
    // The two controls that used to disappear.
    expect(texts, contains('shortlink@gmail.com'));
    expect(texts, contains(updateCopy.checkForUpdates));

    // ... and they are reachable, while the plain rows expose no tap at all.
    final SemanticsData check = tester.semantics
        .find(find.bySemanticsLabel(updateCopy.checkForUpdates))
        .getSemanticsData();
    expect(check.hasAction(SemanticsAction.tap), isTrue);
    final SemanticsData support = tester.semantics
        .find(find.bySemanticsLabel('Contact support'))
        .getSemanticsData();
    expect(
      support.value,
      'shortlink@gmail.com',
      reason: 'the selectable mailbox is reachable as the row node value',
    );
    expect(support.hasAction(SemanticsAction.tap), isFalse);
    final SemanticsData wallet = tester.semantics
        .find(find.bySemanticsLabel('Cold wallet, $_address'))
        .getSemanticsData();
    expect(wallet.hasAction(SemanticsAction.tap), isTrue);
    expect(
      texts.where((String text) => text.contains('Cold wallet')).length,
      1,
      reason: 'the row description must not be announced twice',
    );
    handle.dispose();
  });

  testWidgets('the wallet row keeps its action wording readable', (
    WidgetTester tester,
  ) async {
    // The widget-test font is ~1.4x wider than Roboto, so 480 dp here stands in
    // for the 360 dp device on which "Replace wallet" used to render as
    // "Replace wa…": the action must keep its own width there.
    await _pumpAccount(tester, walletAddress: _address, width: 480);

    final AppListRow row = tester.widget<AppListRow>(
      find.ancestor(
        of: find.text('Cold wallet'),
        matching: find.byType(AppListRow),
      ),
    );
    expect(
      row.trailingTakesIntrinsicWidth,
      isTrue,
      reason: 'the Me page opts the wallet action into its own width',
    );

    final Finder action = find.text('Replace wallet');
    final double intrinsic = _intrinsicWidth(tester, action);
    expect(
      tester.getSize(action).width,
      greaterThanOrEqualTo(intrinsic - 0.5),
      reason: 'the action must render in full, not ellipsised',
    );
    final Size button = tester.getSize(find.byType(TextButton).first);
    expect(button.height, greaterThanOrEqualTo(44));
    expect(button.width, greaterThanOrEqualTo(intrinsic));

    // The address still ellipsises to one line, so the row keeps its height.
    final Text address = tester.widget<Text>(find.text(_address));
    expect(address.maxLines, 1);
    expect(address.overflow, TextOverflow.ellipsis);
    expect(tester.getSize(find.text(_address)).height, lessThan(30));
  });

  testWidgets('a 320px page may ellipsise the wallet action but never overflows', (
    WidgetTester tester,
  ) async {
    await _pumpAccount(tester, walletAddress: _address, width: 320);

    // The floor of the supported range: the label may ellipsise, but the row
    // must stay inside the page and keep the address on one line.
    final Finder action = find.text('Replace wallet');
    expect(tester.getSize(action).width, lessThan(_intrinsicWidth(tester, action)));
    expect(
      tester.widget<Text>(action).overflow,
      TextOverflow.ellipsis,
      reason: 'truncation is an ellipsis, never a hard clip',
    );
    expect(tester.takeException(), isNull);
    expect(tester.getSize(find.text(_address)).height, lessThan(30));
    final Rect row = tester.getRect(
      find.ancestor(
        of: find.text('Cold wallet'),
        matching: find.byType(AppListRow),
      ),
    );
    final Rect page = tester.getRect(find.byType(ListView).first);
    expect(row.left, greaterThanOrEqualTo(page.left));
    expect(row.right, lessThanOrEqualTo(page.right));
  });

  testWidgets('the hero barely resizes when the membership state lands', (
    WidgetTester tester,
  ) async {
    // The placeholder used to be a fixed 26 px tall while an uncapped chip label
    // wrapped at 3.2x, so the hero jumped by 166 px when the snapshot arrived.
    for (final double scale in <double>[1, 3.2]) {
      final AppStateController controller = await _pumpAccount(
        tester,
        loadSnapshot: false,
        width: 360,
        textScale: scale,
      );
      final double before = tester.getSize(
        find.byKey(accountIdentityHeroKey),
      ).height;
      final Size placeholder = tester.getSize(find.byType(AppSkeleton));
      expect(
        placeholder.height,
        closeTo(membershipChipPlaceholderSize.height * scale, 0.5),
        reason: 'the plate follows the text scale at $scale',
      );

      await controller.openAccount();
      await tester.pump();
      final double after = tester.getSize(
        find.byKey(accountIdentityHeroKey),
      ).height;
      expect(
        (after - before).abs(),
        lessThanOrEqualTo(40),
        reason: 'hero resize at ${scale}x must stay small',
      );
    }
  });

  testWidgets('the support mailbox keeps one line on the full row width', (
    WidgetTester tester,
  ) async {
    // On the emulator (360 dp) the mailbox sat in a trailing slot that took
    // half the row, so it wrapped and broke mid-word ("shortlink@gmail.c" /
    // "om"). In the subtitle it has the whole row and renders on one line.
    final SemanticsHandle handle = tester.ensureSemantics();
    await _pumpAccount(
      tester,
      walletAddress: _address,
      withUpdateCoordinator: true,
    );

    final Finder email = find.text('shortlink@gmail.com');
    expect(email, findsOneWidget);
    expect(tester.takeException(), isNull);
    expect(
      tester.getSize(email).height,
      lessThan(30),
      reason: 'a single line: the mailbox must not break in the middle',
    );

    // The mailbox spans the whole row content width (card minus the row
    // padding, the leading plate and its gap) instead of a trailing slot's
    // half-share, which is what used to squeeze it into two lines.
    const AppSpacing spacing = AppSpacing.standard;
    final Rect card = tester.getRect(
      find.ancestor(of: email, matching: find.byType(AppSectionCard)),
    );
    final Rect mailbox = tester.getRect(email);
    expect(mailbox.left, greaterThan(card.left));
    expect(mailbox.left - card.left, lessThan(120));
    expect(
      mailbox.width,
      greaterThanOrEqualTo(card.width - spacing.space4 * 3 - spacing.space10 - 4),
    );

    // Readable through the accessibility tree, and the row still has no action.
    final UpdateStrings updateCopy = updateStrings(AppLanguage.defaultLanguage);
    await _scrollTo(tester, find.text(updateCopy.versionTitle));
    expect(semanticsTexts(tester), contains('shortlink@gmail.com'));
    final SemanticsData support = tester.semantics
        .find(find.bySemanticsLabel('Contact support'))
        .getSemanticsData();
    expect(support.value, 'shortlink@gmail.com');
    expect(support.hasAction(SemanticsAction.tap), isFalse);
    // The version row's action is untouched by this change.
    expect(
      tester.semantics
          .find(find.bySemanticsLabel(updateCopy.checkForUpdates))
          .getSemanticsData()
          .hasAction(SemanticsAction.tap),
      isTrue,
    );
    handle.dispose();
  });

  testWidgets('the support mailbox renders in full at a device-equivalent width', (
    WidgetTester tester,
  ) async {
    // The widget-test font is about 1.5x wider than Roboto, so 480 dp here
    // stands in for the 360 dp device: the mailbox must have room for the whole
    // address, which it never did in a trailing slot.
    await _pumpAccount(tester, width: 480);

    final Finder email = find.text('shortlink@gmail.com');
    expect(email, findsOneWidget);
    expect(
      tester.getSize(email).width,
      greaterThanOrEqualTo(intrinsicTextWidth(tester, email) - 0.5),
      reason: 'the address is neither clipped nor broken',
    );
    expect(tester.getSize(email).height, lessThan(30));
  });

  testWidgets('the support mailbox still fits at 320px', (
    WidgetTester tester,
  ) async {
    await _pumpAccount(tester, width: 320);

    final Finder email = find.text('shortlink@gmail.com');
    expect(email, findsOneWidget);
    expect(tester.getSize(email).height, lessThan(30));
    const AppSpacing spacing = AppSpacing.standard;
    final Rect card = tester.getRect(
      find.ancestor(of: email, matching: find.byType(AppSectionCard)),
    );
    expect(
      tester.getRect(email).width,
      greaterThanOrEqualTo(card.width - spacing.space4 * 3 - spacing.space10 - 4),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('continue watching keeps the size/limit pill and the poster track', (
    WidgetTester tester,
  ) async {
    final AppStateController controller = await _pumpAccount(
      tester,
      history: _history(),
    );

    expect(accountContinueWatchingLimit(), 2);
    expect(find.text('2/2'), findsOneWidget);
    expect(find.byType(PosterCard), findsNWidgets(2));

    await tester.tap(find.byType(PosterCard).first);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(controller.state.screen, AppScreen.player);
  });

  testWidgets('the version row keeps its manual update check', (
    WidgetTester tester,
  ) async {
    await _pumpAccount(tester, withUpdateCoordinator: true);

    final UpdateStrings updateCopy = updateStrings(AppLanguage.defaultLanguage);
    await _scrollTo(tester, find.text(updateCopy.versionTitle));
    expect(find.text(updateCopy.versionTitle), findsOneWidget);
    expect(find.text(updateCopy.checkForUpdates), findsOneWidget);
    expect(find.text('--'), findsOneWidget);
  });

  testWidgets('signing out clears the session', (WidgetTester tester) async {
    final AppStateController controller = await _pumpAccount(
      tester,
      walletAddress: _address,
    );
    expect(controller.state.session, isNotNull);

    await _scrollTo(tester, find.text('Sign out'));
    await tester.tap(find.text('Sign out'));
    await tester.pumpAndSettle();
    expect(controller.state.session, isNull);
  });

  testWidgets('the page lays out at 320px with 3.2x text under reduced motion', (
    WidgetTester tester,
  ) async {
    await _pumpAccount(
      tester,
      vip: true,
      walletAddress: _address,
      history: _history(),
      width: 320,
      height: 720,
      textScale: 3.2,
      // The review device has developer-options animations off.
      disableAnimations: true,
    );
    expect(tester.takeException(), isNull);
    expect(find.byType(ListView), findsWidgets);
    final ScrollableState scrollable = tester.state<ScrollableState>(
      find.byType(Scrollable).first,
    );
    expect(
      scrollable.position.maxScrollExtent,
      greaterThan(0),
      reason: 'the page scrolls instead of clipping',
    );
    await _scrollTo(tester, find.text('Sign out'));
    expect(find.text('Sign out'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the page lays out on a 1440px tablet', (
    WidgetTester tester,
  ) async {
    await _pumpAccount(
      tester,
      vip: false,
      walletAddress: _address,
      history: _history(),
      width: 1440,
      height: 900,
      disableAnimations: true,
    );
    expect(tester.takeException(), isNull);
    expect(find.byType(ListView), findsWidgets);
    expect(find.text('SHORTCUTS'), findsOneWidget);
    expect(find.text('SETTINGS'), findsOneWidget);
  });

  testWidgets('the guest hero lays out at 320px with 3.2x text', (
    WidgetTester tester,
  ) async {
    await _pumpAccount(
      tester,
      loggedIn: false,
      width: 320,
      height: 720,
      textScale: 3.2,
      disableAnimations: true,
    );
    expect(tester.takeException(), isNull);
    expect(find.text('Sign in'), findsOneWidget);
    expect(find.text('Create account'), findsOneWidget);
    await _scrollTo(
      tester,
      find.text(updateStrings(AppLanguage.defaultLanguage).versionTitle),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('the page uses the shared page inset and token spacing', (
    WidgetTester tester,
  ) async {
    await _pumpAccount(tester);

    // The ListView carries `AppSpacing.pagePadding` (18/12/18/28).
    final ListView list = tester.widget<ListView>(find.byType(ListView).first);
    expect(
      list.padding,
      AppSpacing.standard.pagePadding,
      reason: 'the page inset must come from the spacing token',
    );
    expect(AppUiCopy.accountShortcutsSection, 'Shortcuts');
    expect(AppUiCopy.accountSettingsSection, 'Settings');
  });
}
