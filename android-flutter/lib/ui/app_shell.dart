/// App shell: the four primary tabs, the declarative page stack and the global overlays.
///
/// Parity with the Kotlin `MainShell` is behavioural, not visual: the tab set, the highlight
/// predicate (`state.screen == tab`), the `LOGIN` behaviour (nothing rendered while the bar stays),
/// the player living outside the shell and the auth / VIP-payment hosts are all contract. The
/// global message bar is the one host this route does *not* own: it lives above the Navigator in
/// `main.dart` so an open sheet can never cover it. The layout itself is new.
///
/// Phase 3 replaced the imperative `AnimatedSwitcher` with a declarative [Navigator] whose `pages`
/// come from `buildRouteStack(state)` — the pure contract in `routing/app_route_stack.dart`:
///
///  * the current tab is the single root page, so switching tabs *updates* the root instead of
///    stacking another page, and the sub-pages are real pushed pages that inherit the theme's
///    reel-change transition;
///  * the system back gesture has one priority order, resolved by `resolveBackOutcome(state)`
///    (open sheet, then pushed page, then `FUNDS`' Kotlin `BackHandler`, then exit) with
///    `NavigatorPopHandler`-style delegation to the page navigator for routes a screen pushed for
///    itself;
///  * `onDidRemovePage` is only reported for an *imperative* removal (a pop, a `removeRoute`, or a
///    page replacement); a `pages` shrink driven by the state is not reported at all. The handler
///    therefore re-derives the expected stack from the *current* state, so a return is only
///    dispatched while the state still asks for the removed page — a back press that lands in the
///    same turn as a tab switch must not undo the newer intent.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/formatting/app_strings.dart';
import '../core/formatting/navigation_contract.dart';
import '../state/app_state_controller.dart';
import '../state/app_ui_state.dart';
import '../theme/app_colors.dart';
import '../theme/app_motion.dart';
import '../theme/app_shapes.dart';
import '../theme/app_typography.dart';
import 'components/app_loading.dart';
import 'components/app_sheet.dart';
import 'routing/app_route_stack.dart';
import 'routing/app_router_pages.dart';
import 'sheets/auth_sheet.dart';
import 'sheets/vip_paywall_sheet.dart';

/// Key of the shell's [PopScope]: the widget that owns the system back priority.
///
/// Public so a widget test can assert the exit contract (`canPop` is true only
/// on a tab root with nothing above it) without depending on tree order.
const ValueKey<String> shellPopScopeKey = ValueKey<String>('shell-pop-scope');

/// Hosts every screen and the global overlays.
class AppShell extends StatefulWidget {
  /// Creates the shell around [controller].
  const AppShell({required this.controller, super.key});

  final AppStateController controller;

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  /// The nested navigator that renders the declarative page stack.
  ///
  /// It is the navigator every screen resolves with `Navigator.of(context)`,
  /// so a screen-owned modal (the wallet sheet, the episode picker) lands above
  /// the page that opened it and the back gesture returns to that page first.
  final GlobalKey<NavigatorState> _pages = GlobalKey<NavigatorState>();

  /// Content keys of the two global sheets, used to close *their own* modal
  /// route (never whatever happens to be on top of the root navigator).
  final GlobalKey _authSheetKey = GlobalKey();
  final GlobalKey _vipSheetKey = GlobalKey();

  /// Whether the page navigator holds routes above the tab root: a pushed
  /// sub-page, or a sheet a screen opened for itself. The system back gesture
  /// belongs to the navigator whenever this is true, which is how a sheet the
  /// shell does not own (the wallet form) keeps closing on back instead of the
  /// app leaving.
  final ValueNotifier<bool> _pageNavigatorOpen = ValueNotifier<bool>(false);

  /// Whether a global sheet route is currently pushed.
  bool _authRouteOpen = false;
  bool _vipRouteOpen = false;

  bool _sheetSyncScheduled = false;
  bool _returnDispatchedThisFrame = false;

  late final NavigatorObserver _pageObserver = _PageNavigatorObserver(
    _handlePageRoutesChanged,
  );

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_handleControllerChanged);
    // The first frame may already carry an open overlay (a restored guest
    // session that prompts for login), and a sheet cannot be pushed during a
    // build, so the first reconciliation runs once the frame is done.
    _scheduleSheetSync();
    // Cold start: restore the persisted session and render the cached shelf while the network
    // refresh runs. Kicked off once the first frame is scheduled so the shell can paint first.
    scheduleMicrotask(() {
      unawaited(widget.controller.restoreSession());
    });
  }

  @override
  void didUpdateWidget(AppShell oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.controller, widget.controller)) {
      oldWidget.controller.removeListener(_handleControllerChanged);
      widget.controller.addListener(_handleControllerChanged);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_handleControllerChanged);
    _pageNavigatorOpen.dispose();
    super.dispose();
  }

  void _handleControllerChanged() => _scheduleSheetSync();

  /// Schedules one overlay reconciliation per frame.
  ///
  /// Pushing or popping a route during a build is illegal, and a controller
  /// notification can arrive from a post-frame fetch, so the reconciliation
  /// always runs once the frame is done.
  void _scheduleSheetSync() {
    if (_sheetSyncScheduled) {
      return;
    }
    _sheetSyncScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _sheetSyncScheduled = false;
      if (mounted) {
        _syncSheetRoutes();
      }
    });
  }

  /// Keeps the two global sheets in step with the state machine.
  ///
  /// `authPromptVisible` / `vipPaywallVisible` remain the single source of
  /// truth: this only mirrors their transitions onto modal routes, and it never
  /// pushes or pops twice for one transition.
  void _syncSheetRoutes() {
    final AppUiState state = widget.controller.state;
    if (!state.authPromptVisible && _authRouteOpen) {
      _closeSheetRoute(_authSheetKey);
    }
    if (!state.vipPaywallVisible && _vipRouteOpen) {
      _closeSheetRoute(_vipSheetKey);
    }
    // Push order mirrors the back priority: when both flags are set the auth
    // panel is the outermost sheet, so it is the one the back gesture closes.
    if (state.vipPaywallVisible && !_vipRouteOpen) {
      _vipRouteOpen = true;
      unawaited(
        AppSheet.show<void>(
          // The shell's context sits above the page navigator, so the shared
          // sheet host pushes onto the *root* navigator: the sheet covers the
          // bottom bar and the player, and the route owns the back gesture.
          context,
          child: VipPaywallSheet(key: _vipSheetKey),
        ).whenComplete(_handleVipSheetClosed),
      );
    }
    if (state.authPromptVisible && !_authRouteOpen) {
      _authRouteOpen = true;
      unawaited(
        AppSheet.show<void>(
          context,
          child: AuthSheet(key: _authSheetKey),
        ).whenComplete(_handleAuthSheetClosed),
      );
    }
  }

  /// Closes the sheet route whose content carries [contentKey].
  ///
  /// The route is resolved from the content's own context, so closing a sheet
  /// never pops whatever else may be on top of the root navigator (the
  /// self-update dialog, for example).
  void _closeSheetRoute(GlobalKey contentKey) {
    final BuildContext? sheetContext = contentKey.currentContext;
    if (sheetContext == null) {
      return;
    }
    final ModalRoute<Object?>? route = ModalRoute.of(sheetContext);
    if (route == null) {
      return;
    }
    final NavigatorState navigator = Navigator.of(sheetContext);
    if (route.isCurrent) {
      navigator.pop();
    } else {
      navigator.removeRoute(route);
    }
  }

  /// The auth route left the tree: the state machine has to follow.
  ///
  /// When the controller closed the flag itself this is a no-op; when the user
  /// dismissed the route (drag, scrim, system back) the sheet must not stay
  /// "visible" in the state, or the next reconciliation would push it again.
  void _handleAuthSheetClosed() {
    _authRouteOpen = false;
    if (!mounted) {
      return;
    }
    if (widget.controller.state.authPromptVisible) {
      widget.controller.dismissAuthPrompt();
    } else {
      _scheduleSheetSync();
    }
  }

  /// The VIP paywall route left the tree; see [_handleAuthSheetClosed].
  void _handleVipSheetClosed() {
    _vipRouteOpen = false;
    if (!mounted) {
      return;
    }
    if (widget.controller.state.vipPaywallVisible) {
      widget.controller.dismissVipPaywall();
    } else {
      _scheduleSheetSync();
    }
  }

  /// The page navigator gained or lost a route above the tab root.
  void _handlePageRoutesChanged() {
    // The notification can land inside a build (the navigator adds its first
    // page while it is laid out), so the depth is re-read after the frame; the
    // back handler is its only consumer.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      final bool open = _pages.currentState?.canPop() ?? false;
      if (_pageNavigatorOpen.value != open) {
        _pageNavigatorOpen.value = open;
      }
    });
  }

  /// Runs the back priority for the current state.
  ///
  /// A route the page navigator can pop owns the gesture first — that covers
  /// both the pushed sub-pages and the sheets the screens open for themselves —
  /// and the pure contract resolves everything else.
  void _handleSystemBack() {
    final NavigatorState? pages = _pages.currentState;
    if (pages != null && pages.canPop()) {
      unawaited(pages.maybePop());
      return;
    }
    switch (resolveBackOutcome(widget.controller.state)) {
      case BackOutcome.closeAuthPrompt:
        widget.controller.dismissAuthPrompt();
      case BackOutcome.closeVipPaywall:
        widget.controller.dismissVipPaywall();
      case BackOutcome.popPage:
        // The state expects a pushed page the navigator has not built yet.
        if (pages != null) {
          unawaited(pages.maybePop());
        }
      case BackOutcome.navigateToAccount:
        widget.controller.backToAccount();
      case BackOutcome.exitApp:
        // Unreachable: `canPop` is true in that case, so the platform handles
        // the gesture instead of calling this handler.
        break;
    }
  }

  /// Maps a page the framework *imperatively* removed back to its controller
  /// transition.
  ///
  /// The framework calls back for an imperative removal (`Navigator.pop`,
  /// `maybePop`, `removeRoute`) and for a page replacement; a `pages` shrink
  /// driven purely by the state is not reported at all. So the one question
  /// left here is whether the state *still* asks for the removed page, judged
  /// against the current state rather than the last built frame: `maybePop`
  /// completes a microtask later, and a transition issued in that window (a tab
  /// switch) must win over the return the pop implies.
  void _handleDidRemovePage(Page<Object?> page) {
    final Object? key = page.key;
    if (key is! ValueKey<String>) {
      return;
    }
    final AppRouteDestination? destination = _expectedDestination(key.value);
    if (destination == null) {
      // The state moved on: this removal is not a user return, so nothing is
      // dispatched and the newer intent stands.
      return;
    }
    final ShellExit? exit = shellExitForPoppedDestination(destination);
    if (exit == null) {
      return;
    }
    // One return gesture dispatches at most one controller transition.
    if (_returnDispatchedThisFrame) {
      return;
    }
    _returnDispatchedThisFrame = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _returnDispatchedThisFrame = false;
    });
    switch (exit) {
      case ShellExit.playbackSource:
        widget.controller.backToPlaybackSource();
      case ShellExit.account:
        widget.controller.backToAccount();
      case ShellExit.funds:
        widget.controller.backToFunds();
      case ShellExit.home:
        unawaited(widget.controller.openHome());
    }
  }

  /// The destination [pageKey] has in the *current* state's page stack, or null
  /// when the state no longer asks for that page.
  AppRouteDestination? _expectedDestination(String pageKey) {
    for (final AppRouteNode node in buildRouteStack(widget.controller.state)) {
      if (node.pageKey == pageKey) {
        return node.destination;
      }
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final AppStateController controller = context.watch<AppStateController>();
    final AppUiState state = controller.state;
    final AppStrings copy = strings(state.language);
    final List<AppRouteNode> stack = buildRouteStack(state);

    final Widget content = Scaffold(
      backgroundColor: Theme.of(context).extension<AppColors>()!.canvas,
      body: Navigator(
        key: _pages,
        observers: <NavigatorObserver>[_pageObserver],
        onDidRemovePage: _handleDidRemovePage,
        pages: <Page<Object?>>[
          for (final AppRouteNode node in stack)
            appRoutePage(node, copy: copy, controller: controller),
        ],
      ),
      // Parity: only the player is full-screen; every other screen keeps the bar.
      bottomNavigationBar: shellShowsNavigationBar(state)
          ? _AppNavigationBar(
              screen: state.screen,
              onSelect: (AppScreen target) => _navigate(controller, target),
            )
          : null,
    );

    return ValueListenableBuilder<bool>(
      valueListenable: _pageNavigatorOpen,
      // The Stack is built once per shell build and reused while only the
      // navigator depth changes.
      child: Stack(
        children: <Widget>[
          Positioned.fill(child: content),
          // Thin progress bar instead of a blocking dialog: the loading gates in the state
          // layer are unchanged, only the presentation differs. Like the Kotlin loading host
          // it floats above every screen, player included.
          Positioned(
            left: 0,
            right: 0,
            top: 0,
            child: AppTopProgressBar(visible: state.isLoading),
          ),
          // The global message bar is *not* here: the auth panel and the VIP
          // paywall are modal routes on the root navigator now, so a bar hosted
          // inside this route would sit under their barrier (dimmed, and
          // excluded from the accessibility tree). It is hosted above the
          // Navigator in `main.dart`, like the Kotlin activity's z-index 2
          // message host.
        ],
      ),
      builder: (BuildContext context, bool pageOverlayOpen, Widget? child) {
        return PopScope(
          key: shellPopScopeKey,
          // Only a tab root with nothing above it leaves the app: an open
          // sheet or a pushed page always has a landing target, and `FUNDS`
          // keeps its Kotlin `BackHandler` target (`ACCOUNT`).
          canPop:
              resolveBackOutcome(state) == BackOutcome.exitApp &&
              !pageOverlayOpen,
          onPopInvokedWithResult: (bool didPop, Object? result) {
            if (didPop) {
              return;
            }
            _handleSystemBack();
          },
          child: child ?? const SizedBox.shrink(),
        );
      },
    );
  }

  void _navigate(AppStateController controller, AppScreen target) {
    switch (target) {
      case AppScreen.home:
        unawaited(controller.openHome());
      case AppScreen.search:
        controller.showSearch();
      case AppScreen.funds:
        unawaited(controller.openFunds());
      case AppScreen.account:
        unawaited(controller.openAccount());
      // The shell only ever offers the four primary tabs; the remaining screens are reached
      // from inside them and are pages of the declarative stack.
      default:
        break;
    }
  }
}

/// Notices when the page navigator gains or loses a route above the tab root.
class _PageNavigatorObserver extends NavigatorObserver {
  _PageNavigatorObserver(this.onRoutesChanged);

  final VoidCallback onRoutesChanged;

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    onRoutesChanged();
  }

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) {
    onRoutesChanged();
  }

  @override
  void didRemove(Route<dynamic> route, Route<dynamic>? previousRoute) {
    onRoutesChanged();
  }

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    onRoutesChanged();
  }
}

class _AppNavigationBar extends StatelessWidget {
  const _AppNavigationBar({required this.screen, required this.onSelect});

  final AppScreen screen;
  final ValueChanged<AppScreen> onSelect;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;

    return Container(
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border(
          top: BorderSide(color: colors.hairline, width: shapes.strokeHairline),
        ),
      ),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          // A floor, not a fixed height: the labels scale with the user's font
          // setting, and locking the bar to 64 px overflowed it at 3.2x. The
          // intrinsic height lets the bar grow with its labels; at normal scales
          // the content is exactly 64 px, so nothing moves.
          constraints: const BoxConstraints(minHeight: 64),
          child: IntrinsicHeight(
            child: Row(
              children: primaryTabs
                  .map(
                    (AppScreen tab) => Expanded(
                      child: _NavTab(
                        tab: tab,
                        selected: screen == tab,
                        onTap: () => onSelect(tab),
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
          ),
        ),
      ),
    );
  }
}

class _NavTab extends StatelessWidget {
  const _NavTab({
    required this.tab,
    required this.selected,
    required this.onTap,
  });

  final AppScreen tab;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppMotion motion = Theme.of(context).extension<AppMotion>()!;
    final Color accent = selected ? colors.accentPrimary : colors.textSecondary;

    return Semantics(
      button: true,
      selected: selected,
      label: navigationLabel(tab),
      child: InkWell(
        onTap: onTap,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            _TabGlyph(tab: tab, color: accent),
            const SizedBox(height: 4),
            Text(
              navigationLabel(tab),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: roles.body.copyWith(color: accent, fontSize: 11),
            ),
            const SizedBox(height: 3),
            AnimatedContainer(
              duration: motion.controlState,
              curve: motion.controlStateCurve,
              width: 16,
              height: 2,
              decoration: BoxDecoration(
                color: selected ? accent : Colors.transparent,
                borderRadius: BorderRadius.circular(shapes.radiusInline),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TabGlyph extends StatelessWidget {
  const _TabGlyph({required this.tab, required this.color});

  final AppScreen tab;
  final Color color;

  @override
  Widget build(BuildContext context) {
    switch (tab) {
      case AppScreen.home:
        return Icon(Icons.smart_display_outlined, color: color, size: 22);
      case AppScreen.search:
        return Icon(Icons.travel_explore_outlined, color: color, size: 22);
      case AppScreen.funds:
        return Icon(Icons.account_balance_wallet_outlined, color: color, size: 22);
      case AppScreen.account:
        return Icon(Icons.person_outline, color: color, size: 22);
      // Non-primary screens are never rendered as a tab.
      default:
        return const SizedBox.shrink();
    }
  }
}
