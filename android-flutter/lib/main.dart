/// App entry point.
///
/// Boots the platform object graph, builds the state controller and hands it to the widget tree.
/// Everything below this file is either the theme, the state layer or the redesigned UI.
library;

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';

import 'core/formatting/update_ui_format.dart';
import 'platform/app_environment.dart';
import 'state/app_state_controller.dart';
import 'state/app_ui_state.dart';
import 'theme/app_theme.dart';
import 'ui/app_shell.dart';
import 'ui/components/app_message_bar.dart';
import 'ui/update/update_dialog.dart';
import 'update/android_update_installer.dart';
import 'update/release_downloader.dart';
import 'update/release_models.dart';
import 'update/short_link_update_client.dart';
import 'update/update_coordinator.dart';
import 'update/update_state.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Edge-to-edge drawing: the app paints its own canvas colour behind the system bars, so the
  // status/navigation bar never flashes a mismatched background during startup.
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);

  final AppEnvironment environment = await AppEnvironment.bootstrap();
  final AppStateController controller = AppStateController(
    dataSource: environment.dataSource,
  );

  // Self-update subsystem: the coordinator is assembled once here, mirroring the
  // Android composition root. Without an installer the install bridges are no-ops,
  // which is exactly the behaviour on platforms without a package installer.
  final PackageInfo packageInfo = await PackageInfo.fromPlatform();
  final UpdateCoordinator coordinator = UpdateCoordinator(
    releaseClient: ShortLinkUpdateClient(
      httpClient: http.Client(),
      currentVersionName: packageInfo.version,
    ),
    downloader: HttpReleaseDownloader(
      httpClient: http.Client(),
      targetDirectory: await _updatesDirectory(),
    ),
    currentVersion:
        SemanticVersion.parse(packageInfo.version) ??
        const SemanticVersion(0, 0, 0),
    currentVersionCode: int.tryParse(packageInfo.buildNumber) ?? 0,
    availableBytes: (Directory directory) {
      // `dart:io` exposes no usable-space API; the storage gate treats an
      // unknown free space as sufficient rather than blocking updates.
      return 1 << 40;
    },
    installer: await _platformInstaller(),
  );
  // The dialog resolves the coordinator through this composition-root override;
  // a scope widget would need to sit above the Navigator, which MaterialApp owns.
  UpdateDialog.coordinatorOverride = coordinator;
  // Startup check fires once per cold start, matching the Kotlin bootstrap.
  unawaited(
    coordinator.checkForUpdate(manual: false).catchError((Object _) {}),
  );

  runApp(ShortLinkApp(controller: controller, updateCoordinator: coordinator));
}

Future<Directory> _updatesDirectory() async {
  final Directory base = await getTemporaryDirectory();
  return Directory('${base.path}${Platform.pathSeparator}updates');
}

/// Returns the platform installer on Android, `null` elsewhere.
Future<AndroidUpdateInstaller?> _platformInstaller() async {
  if (Platform.isAndroid) {
    return AndroidUpdateInstaller();
  }
  return null;
}

class ShortLinkApp extends StatelessWidget {
  const ShortLinkApp({
    required this.controller,
    required this.updateCoordinator,
    super.key,
  });

  final AppStateController controller;
  final UpdateCoordinator updateCoordinator;

  /// The dialog host sits above the Navigator (to overlay every route), so it
  /// cannot resolve a Navigator from its own context; this key reaches the
  /// Navigator's context for `showDialog`.
  static final GlobalKey<NavigatorState> navigatorKey =
      GlobalKey<NavigatorState>();

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<AppStateController>.value(
      value: controller,
      child: MaterialApp(
        navigatorKey: navigatorKey,
        title: 'ShortLink',
        debugShowCheckedModeBanner: false,
        // The system setting decides the theme; both palettes are first-class in the design
        // system rather than one being a derived variant.
        theme: lightTheme,
        darkTheme: darkTheme,
        themeMode: ThemeMode.system,
        // Content and interface copy are English-only, matching the backend contract.
        supportedLocales: const <Locale>[Locale('en')],
        localizationsDelegates: const <LocalizationsDelegate<Object>>[
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        // The scope sits above the Navigator so both the modal dialog and the
        // account screen can resolve the coordinator; the hosts layer the
        // global message bar and the self-update UI over every route, like the
        // Kotlin activity boxes.
        builder: (BuildContext context, Widget? child) => AnnotatedRegion<SystemUiOverlayStyle>(
          // The Kotlin activity pins a dark edge-to-edge scrim; the redesign
          // follows the active palette instead, keeping the icons readable.
          value: Theme.of(context).brightness == Brightness.dark
              ? SystemUiOverlayStyle.light.copyWith(
                  systemNavigationBarIconBrightness: Brightness.light,
                )
              : SystemUiOverlayStyle.dark.copyWith(
                  systemNavigationBarIconBrightness: Brightness.dark,
                ),
          child: UpdateDialogScope(
            coordinator: updateCoordinator,
            // Outermost first: the message bar sits above the self-update toast
            // (a failure message must not be covered by a transient "up to
            // date" toast), and both sit above every route because the
            // Navigator is the innermost child.
            child: _GlobalMessageHost(
              child: _UpdateHost(
                coordinator: updateCoordinator,
                navigatorKey: navigatorKey,
                child: child ?? const SizedBox.shrink(),
              ),
            ),
          ),
        ),
        home: AppShell(controller: controller),
      ),
    );
  }
}

/// Hosts the global top message bar above every route.
///
/// The bar used to live inside `AppShell`, which was correct while every
/// overlay was a `Stack` child of the shell. Now that the auth panel and the
/// VIP paywall are modal routes on the root navigator, a bar hosted inside the
/// shell route would be painted *under* their barrier: dimmed, untappable (the
/// barrier swallowed the tap and closed the sheet instead) and excluded from
/// the accessibility tree by the barrier's `BlockSemantics`. The Kotlin
/// activity composes its message host above `MainShell` at z-index 2, so the
/// Flutter client hosts it the same way — directly above the Navigator that
/// `MaterialApp.builder` hands in.
class _GlobalMessageHost extends StatelessWidget {
  const _GlobalMessageHost({required this.child});

  /// The Navigator (and everything pushed onto it).
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: <Widget>[
        Positioned.fill(child: child),
        // The bar needs an [Overlay] to live in: sitting above the Navigator
        // puts it outside the Navigator's own overlay, and the design-system
        // bar owns a tooltip on its dismiss affordance, which floats in one.
        // The layer is fully transparent to input: an [Overlay] only hit-tests
        // its entries, so a tap that misses the bar still reaches the app.
        Positioned.fill(
          child: Overlay(
            initialEntries: <OverlayEntry>[
              OverlayEntry(
                builder: (BuildContext context) => const _GlobalMessageBar(),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// The bar itself, reading the controller so the overlay entry around it never
/// has to be rebuilt from the outside (an [OverlayEntry]'s builder runs once).
class _GlobalMessageBar extends StatelessWidget {
  const _GlobalMessageBar();

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    // No message, no bar: the host renders nothing rather than an empty bar.
    if (state.errorMessage == null) {
      return const SizedBox.shrink();
    }
    return Stack(
      children: <Widget>[
        Positioned(
          left: 16,
          right: 16,
          top: 0,
          child: SafeArea(
            top: true,
            bottom: false,
            child: AppMessageBar(
              key: ValueKey<int>(state.messageTrigger),
              message: state.errorMessage!,
              type: state.messageType ?? UiMessageType.error,
              // The bar disappears visually on its own; clearing the state keeps a
              // repeated identical message re-showable, like the Kotlin 4 s effect.
              onDismissed: controller.clearError,
            ),
          ),
        ),
      ],
    );
  }
}

/// Hosts the self-update UI over every route.
///
/// Port of the Kotlin `MainActivity` composition around the update state machine:
/// the modal dialog opens while `shouldShowUpdateDialog` holds, the unknown-sources
/// settings screen launches itself when the state reaches `PermissionRequired`, the
/// permission decision is re-evaluated when the user comes back from settings, and
/// a manual check that ends in `UpToDate` / `Failed` (no release) surfaces a 4 s
/// toast before dropping back to `Idle`.
class _UpdateHost extends StatefulWidget {
  const _UpdateHost({
    required this.coordinator,
    required this.navigatorKey,
    required this.child,
  });

  final UpdateCoordinator coordinator;

  /// Navigator of the app; dialogs must be pushed from below it.
  final GlobalKey<NavigatorState> navigatorKey;

  final Widget child;

  @override
  State<_UpdateHost> createState() => _UpdateHostState();
}

class _UpdateHostState extends State<_UpdateHost> {
  bool _dialogOpen = false;
  bool _settingsLaunched = false;
  String? _toastMessage;
  UiMessageType _toastType = UiMessageType.info;
  int _toastSerial = 0;
  Timer? _toastTimer;
  late final AppLifecycleListener _lifecycleListener;

  @override
  void initState() {
    super.initState();
    widget.coordinator.state.addListener(_onUpdateStateChanged);
    _lifecycleListener = AppLifecycleListener(onResume: _onResumed);
    // The startup check may already have produced a visible state before this
    // host mounted; evaluate once after the first frame so it is not missed.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _evaluate(widget.coordinator.state.value);
      }
    });
  }

  @override
  void dispose() {
    widget.coordinator.state.removeListener(_onUpdateStateChanged);
    _lifecycleListener.dispose();
    _toastTimer?.cancel();
    super.dispose();
  }

  void _onUpdateStateChanged() {
    _evaluate(widget.coordinator.state.value);
  }

  void _evaluate(UpdateState state) {
    if (!mounted) {
      return;
    }
    if (shouldShowUpdateDialog(state)) {
      final BuildContext? navigatorContext = widget.navigatorKey.currentContext;
      if (!_dialogOpen && navigatorContext != null) {
        _dialogOpen = true;
        showUpdateDialog(navigatorContext).whenComplete(() {
          _dialogOpen = false;
          // The dialog may have closed while a visible state was already set;
          // re-evaluate so the dialog reopens instead of waiting for a change.
          _evaluate(widget.coordinator.state.value);
        });
      }
    } else {
      _settingsLaunched = false;
    }
    switch (state) {
      case PermissionRequired():
        if (!_settingsLaunched) {
          _settingsLaunched = true;
          unawaited(_launchPermissionSettings());
        }
      case UpToDate() || Failed(manual: true, release: null):
        _showUpdateToast(state);
      default:
        break;
    }
  }

  /// Opens the system "install unknown apps" screen, mirroring the Kotlin
  /// `LaunchedEffect(PermissionRequired)` auto-launch.
  Future<void> _launchPermissionSettings() async {
    try {
      await widget.coordinator.requestInstallPermission();
    } catch (_) {
      widget.coordinator.installationFailed();
    }
  }

  /// Re-evaluates the install permission when the user returns from settings.
  Future<void> _onResumed() async {
    try {
      final bool granted = await widget.coordinator.canRequestPackageInstalls();
      final InstallRequest? request = widget.coordinator
          .onInstallPermissionResult(granted);
      await widget.coordinator.handleInstallRequest(request);
    } catch (_) {
      // A broken installer bridge keeps the current state; the dialog still
      // offers the manual install path.
    }
  }

  /// `UpToDate` and check-only failures surface a transient toast, then drop
  /// back to `Idle` — the Kotlin 4 s `TopErrorToast` + `dismissUpdate()` pair.
  void _showUpdateToast(UpdateState state) {
    final UpdateStrings copy = updateStrings();
    final String message = switch (state) {
      UpToDate() => copy.latestVersion,
      Failed() => copy.checkFailed,
      _ => '',
    };
    if (message.isEmpty) {
      return;
    }
    _toastTimer?.cancel();
    setState(() {
      _toastMessage = message;
      _toastType = state is UpToDate ? UiMessageType.success : UiMessageType.error;
      _toastSerial++;
    });
    _toastTimer = Timer(const Duration(milliseconds: 4000), () {
      if (!mounted) {
        return;
      }
      setState(() => _toastMessage = null);
      widget.coordinator.dismiss();
    });
  }

  void _dismissToast() {
    _toastTimer?.cancel();
    setState(() => _toastMessage = null);
    widget.coordinator.dismiss();
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: <Widget>[
        Positioned.fill(child: widget.child),
        if (_toastMessage != null)
          Positioned(
            left: 16,
            right: 16,
            top: 0,
            child: SafeArea(
              top: true,
              bottom: false,
              child: AppMessageBar(
                key: ValueKey<int>(_toastSerial),
                message: _toastMessage!,
                type: _toastType,
                onDismissed: _dismissToast,
              ),
            ),
          ),
      ],
    );
  }
}
