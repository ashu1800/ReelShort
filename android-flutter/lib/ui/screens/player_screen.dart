/// Player: the full-screen reel player.
///
/// Behavioural parity with the Kotlin `PlayerScreen` (the screen itself is new): 9:16 portrait
/// priority, autoplay with resume-from-history, the ~15 s silent progress window, completion
/// reporting, the VIP-only deterministic reward ring with its info sheet, the right action rail
/// (like / comments / favourite) with the comment sheet, the `+N pt` toast keyed off
/// `rewardAwardVersion`, the overlay priority ERROR > COVER_LOADING > BUFFERING with the
/// retry / next / back recovery set, the episode sheet with current/watched/percent cells and
/// the VIP lock, and the back-target contract via `playerReturnScreen`.
library;

import 'dart:async';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:screen_brightness/screen_brightness.dart';
import 'package:video_player/video_player.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/app_ui_copy.dart';
import '../../core/formatting/reward_badge_state.dart';
import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../state/playback_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_icons.dart';
import '../../theme/app_media.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_spacing.dart';
import '../../theme/app_typography.dart';
import '../components/app_button.dart';
import '../components/app_empty_state.dart';
import '../components/app_loading.dart';
import '../components/app_sheet.dart';
import '../components/episode_spine.dart';

/// Minimum target of every tappable element in the player chrome.
const double _minTargetExtent = 44;

/// Height of the bottom watched-progress bar.
const double _progressBarHeight = 3;

/// How far the action rail floats above the bottom copy block.
const double _railBottomInset = 120;

/// Touch target of the reward badge (its geometry is unchanged from the port).
const double _rewardRingTarget = 48;

/// Diameter of the reward ring itself.
const double _rewardRingDiameter = 44;

/// Width of the brightness/volume feedback track.
const double _gestureTrackWidth = 96;

/// Fraction of the screen height a player sheet may occupy.
const double _sheetHeightFactor = 0.7;

/// Right inset of the bottom copy block, so it never runs under the rail.
const double _bottomCopyRailInset = 84;

/// How often the position tick runs.
const Duration _tickInterval = Duration(seconds: 1);

/// How long the `+N pt` overlay stays up.
const Duration _rewardToastDuration = Duration(milliseconds: 1100);

/// How long the brightness/volume feedback pill stays after a drag ends.
const Duration _gestureFeedbackFade = Duration(milliseconds: 700);

/// Which behaviour a vertical drag maps to, decided by where it started:
/// the left edge drives brightness, the right edge drives volume, and the
/// middle of the surface swipes to the previous/next episode.
enum _PlayerDragZone { none, brightness, volume, episode }

/// Minimum travel before an episode swipe fires.
const double _episodeSwipeDistance = 80;

/// Pixels of drag travel per 1.0 step of brightness/volume.
const double _gesturePixelsPerUnit = 300;

/// `formatCount` of the Kotlin action rail: millions collapse to `%.1fM`,
/// thousands to a whole `Nk`, zero renders nothing.
String _formatCount(int count) {
  if (count >= 1000000) {
    return '${(count / 1000000.0).toStringAsFixed(1)}M';
  }
  if (count >= 1000) {
    return '${count ~/ 1000}k';
  }
  if (count > 0) {
    return '$count';
  }
  return '';
}

/// The player screen.
class PlayerScreen extends StatefulWidget {
  /// Creates the player. The shell instantiates it without arguments.
  const PlayerScreen({super.key});

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen>
    with WidgetsBindingObserver {
  late final AppStateController _controller;
  late AppUiState _state;
  VideoPlayerController? _video;
  Timer? _tick;
  Timer? _rewardToastTimer;
  bool _rewardToastVisible = false;
  int _rewardToastPoints = 0;

  /// Baseline of `rewardAwardVersion`, reset per (book, episode) like the Kotlin
  /// `remember(book?.id, episode?.number)` pair, so switching reels never toasts.
  String? _rewardBaselineBookId;
  int? _rewardBaselineEpisodeNumber;
  int _rewardBaselineVersion = 0;

  /// The media URL the current controller plays; a different URL swaps it.
  String? _attachedUrl;

  /// Incremented by the retry action to rebuild the player for the same URL.
  int _retryTrigger = 0;

  /// Mirrors the Kotlin `playerError` flag: raised by a failed initialize or a
  /// platform playback error, cleared by a successful retry.
  bool _playerError = false;

  /// Mirrors the Kotlin `hasFirstReady` flag gating the cover overlay.
  bool _hasFirstReady = false;

  /// Whether the controller reports playing; drives the pause overlay.
  bool _wasPlaying = false;

  /// Active vertical-drag behaviour and its scratch state.
  _PlayerDragZone _dragZone = _PlayerDragZone.none;
  double? _dragStartBrightness;
  double? _dragStartVolume;
  double _episodeDragDy = 0;

  /// Center-pill feedback for brightness/volume drags.
  Timer? _gestureHideTimer;
  IconData? _gestureIcon;
  String? _gestureLabel;
  double? _gestureValue;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // Captured once: dispose() runs after the element is deactivated, where an
    // inherited lookup would be unsafe.
    _controller = context.read<AppStateController>();
    _state = _controller.state;
    _resetRewardBaseline(_state);
    _attachVideo(_state);
    _controller.addListener(_onControllerChanged);
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    WidgetsBinding.instance.removeObserver(this);
    _tick?.cancel();
    _rewardToastTimer?.cancel();
    _gestureHideTimer?.cancel();
    // The brightness drag only altered this window's brightness; hand the
    // system value back so leaving the player never leaves a dim screen.
    if (_dragStartBrightness != null) {
      unawaited(
        ScreenBrightness.instance
            .resetApplicationScreenBrightness()
            .catchError((Object _) {}),
      );
    }
    _video?.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // ON_PAUSE pauses and ON_RESUME resumes, matching the Kotlin lifecycle
    // observer; autoplay is always on (`playerStartsAutomatically`).
    final VideoPlayerController? video = _video;
    if (video == null || !video.value.isInitialized) {
      return;
    }
    if (state == AppLifecycleState.paused) {
      video.pause();
    } else if (state == AppLifecycleState.resumed) {
      unawaited(video.play());
    }
  }

  void _onControllerChanged() {
    if (!mounted) {
      return;
    }
    final AppUiState next = _controller.state;
    setState(() => _state = next);
    _attachVideo(next);
    _syncRewardToast(next);
  }

  void _resetRewardBaseline(AppUiState state) {
    _rewardBaselineBookId = state.playback.book?.id;
    _rewardBaselineEpisodeNumber = state.playback.episode?.number;
    _rewardBaselineVersion = state.playback.rewardAwardVersion;
  }

  /// `rewardAwardVersion` advanced **and** `awardedPoints > 0` shows the
  /// `+N pt` overlay for 1100 ms; a reel switch silently re-baselines instead.
  void _syncRewardToast(AppUiState state) {
    final String? bookId = state.playback.book?.id;
    final int? episodeNumber = state.playback.episode?.number;
    if (bookId != _rewardBaselineBookId ||
        episodeNumber != _rewardBaselineEpisodeNumber) {
      _resetRewardBaseline(state);
      return;
    }
    final int version = state.playback.rewardAwardVersion;
    if (version == _rewardBaselineVersion) {
      return;
    }
    final int awarded = state.playback.awardedPoints;
    _rewardBaselineVersion = version;
    if (awarded <= 0) {
      return;
    }
    _rewardToastTimer?.cancel();
    setState(() {
      _rewardToastVisible = true;
      _rewardToastPoints = awarded;
    });
    _rewardToastTimer = Timer(_rewardToastDuration, () {
      if (mounted) {
        setState(() => _rewardToastVisible = false);
      }
    });
  }

  /// (Re)creates the video controller when the URL or the retry trigger moved.
  void _attachVideo(AppUiState state, {bool force = false}) {
    final String? url = state.playback.videoUrl?.url;
    if (url == null || url.isEmpty) {
      return;
    }
    if (!force && _video != null && identical(_attachedUrl, url)) {
      return;
    }
    _tick?.cancel();
    _video?.dispose();
    _video = null;
    _attachedUrl = url;
    _playerError = false;
    _hasFirstReady = false;
    final int resumeMs = state.playback.positionSeconds * 1000;
    final VideoPlayerController controller = VideoPlayerController.networkUrl(
      Uri.parse(url),
    );
    _video = controller;
    controller.addListener(() {
      if (!mounted || !identical(_video, controller)) {
        return;
      }
      final bool playing = controller.value.isPlaying;
      if (playing != _wasPlaying && mounted) {
        setState(() => _wasPlaying = playing);
      }
      if (controller.value.hasError && !_playerError && mounted) {
        setState(() => _playerError = true);
      }
    });
    controller.initialize().then((_) {
      if (!mounted || !identical(_video, controller)) {
        return;
      }
      setState(() {
        _hasFirstReady = true;
        _playerError = false;
      });
      // Seek to the history snapshot before the first play.
      if (resumeMs > 0) {
        unawaited(controller.seekTo(Duration(milliseconds: resumeMs)));
      }
      unawaited(controller.play());
      _tick = Timer.periodic(_tickInterval, (_) => _onTick());
    }).catchError((Object error) {
      if (!mounted || !identical(_video, controller)) {
        return;
      }
      setState(() => _playerError = true);
    });
  }

  /// The Kotlin retry: bump the trigger and rebuild the player even when the
  /// server returns the same URL.
  void _retryPlayback() {
    setState(() => _retryTrigger++);
    _attachVideo(_state, force: true);
  }

  void _onTick() {
    final VideoPlayerController? video = _video;
    final AppUiState state = _state;
    if (video == null || !video.value.isInitialized) {
      return;
    }
    // Claimed rewards stop the sampling loop entirely, like the Kotlin
    // `if (rewardClaimed) return@LaunchedEffect`.
    if (state.playback.rewardClaimed) {
      return;
    }
    final int positionMs = video.value.position.inMilliseconds;
    final int durationMs = _effectiveDurationMs(video, state);
    if (durationMs <= 0) {
      return;
    }
    final bool ended = positionMs >= durationMs;
    final PlaybackProgressSample sample = playbackProgressSample(
      positionMs,
      durationMs,
      ended,
    );
    _controller.updatePlaybackPosition(
      sample.positionSeconds,
      sample.durationSeconds,
    );
    // ~15 s window: `shouldPersistPlaybackProgress` decides whether the report
    // actually fires; completion is always saved and never duplicated.
    unawaited(
      _controller.reportProgressSilently(
        sample.positionSeconds,
        sample.durationSeconds,
      ),
    );
  }

  int _effectiveDurationMs(VideoPlayerController video, AppUiState state) {
    final int reported = video.value.duration.inMilliseconds;
    if (reported > 0) {
      return reported;
    }
    // Duration fallback to the server's authoritative seconds.
    return state.playback.durationSeconds * 1000;
  }

  // ---------------------------------------------------------------------------
  // Surface gestures: tap pauses, double-tap likes, vertical drags drive
  // brightness (left edge), volume (right edge) or episode swipe (middle).
  // ---------------------------------------------------------------------------

  bool get _surfaceInteractive =>
      !_playerError && _video != null && _video!.value.isInitialized;

  void _togglePlayPause() {
    final VideoPlayerController? video = _video;
    if (!_surfaceInteractive || video == null) {
      return;
    }
    if (video.value.isPlaying) {
      unawaited(video.pause());
    } else {
      unawaited(video.play());
    }
  }

  void _onDoubleTapLike() {
    if (!_surfaceInteractive) {
      return;
    }
    // Social failures surface through the global message host; a 401 opens
    // the auth sheet right here over the player (contract rule 29).
    unawaited(HapticFeedback.mediumImpact());
    unawaited(_controller.toggleLike());
  }

  void _onVerticalDragStart(DragStartDetails details) {
    _dragZone = _PlayerDragZone.none;
    if (!_surfaceInteractive) {
      return;
    }
    final double width = MediaQuery.sizeOf(context).width;
    final double x = details.localPosition.dx;
    if (x <= width * 0.25) {
      _dragZone = _PlayerDragZone.brightness;
      unawaited(
        ScreenBrightness.instance.application.then((double value) {
          _dragStartBrightness = value.clamp(0.05, 1.0);
        }).catchError((Object _) {
          _dragZone = _PlayerDragZone.none;
        }),
      );
    } else if (x >= width * 0.75) {
      _dragZone = _PlayerDragZone.volume;
      _dragStartVolume = _video!.value.volume;
    } else {
      _dragZone = _PlayerDragZone.episode;
      _episodeDragDy = 0;
    }
  }

  void _onVerticalDragUpdate(DragUpdateDetails details) {
    switch (_dragZone) {
      case _PlayerDragZone.brightness:
        final double? start = _dragStartBrightness;
        if (start == null) {
          return;
        }
        final double value = (start - details.delta.dy / _gesturePixelsPerUnit)
            .clamp(0.05, 1.0);
        unawaited(
          ScreenBrightness.instance
              .setApplicationScreenBrightness(value)
              .catchError((Object _) {}),
        );
        _showGestureFeedback(Icons.brightness_6_outlined, 'Brightness', value);
      case _PlayerDragZone.volume:
        final double? start = _dragStartVolume;
        final VideoPlayerController? video = _video;
        if (start == null || video == null) {
          return;
        }
        final double value =
            (start - details.delta.dy / _gesturePixelsPerUnit).clamp(0.0, 1.0);
        unawaited(video.setVolume(value));
        _showGestureFeedback(Icons.volume_up_outlined, 'Volume', value);
      case _PlayerDragZone.episode:
        _episodeDragDy += details.delta.dy;
      case _PlayerDragZone.none:
        break;
    }
  }

  void _onVerticalDragEnd(DragEndDetails details) {
    switch (_dragZone) {
      case _PlayerDragZone.episode:
        final double velocity = details.primaryVelocity ?? 0;
        if (velocity < -400 || _episodeDragDy < -_episodeSwipeDistance) {
          _swipeEpisode(1);
        } else if (velocity > 400 || _episodeDragDy > _episodeSwipeDistance) {
          _swipeEpisode(-1);
        }
      case _PlayerDragZone.brightness ||
      _PlayerDragZone.volume:
        _gestureHideTimer?.cancel();
        _gestureHideTimer = Timer(_gestureFeedbackFade, () {
          if (mounted) {
            setState(() {
              _gestureIcon = null;
              _gestureLabel = null;
              _gestureValue = null;
            });
          }
        });
      case _PlayerDragZone.none:
        break;
    }
    _dragZone = _PlayerDragZone.none;
  }

  /// `direction` 1 = next, -1 = previous, in list order — the same ordering
  /// `playerErrorNextEpisode` uses for the error-overlay shortcut.
  void _swipeEpisode(int direction) {
    final AppUiState state = _state;
    final EpisodeSummary? current = state.playback.episode;
    if (state.episodes.isEmpty || current == null) {
      return;
    }
    final int index = state.episodes.indexWhere(
      (EpisodeSummary episode) =>
          episode.number == current.number &&
          episode.chapterId == current.chapterId,
    );
    if (index < 0) {
      return;
    }
    final int target = index + direction;
    if (target < 0 || target >= state.episodes.length) {
      return;
    }
    unawaited(_controller.openPlayer(state.episodes[target]));
  }

  void _showGestureFeedback(IconData icon, String label, double value) {
    if (!mounted) {
      return;
    }
    setState(() {
      _gestureIcon = icon;
      _gestureLabel = label;
      _gestureValue = value;
    });
  }

  void _handleBack() {
    // Back target = `playerReturnScreen ?? home`, resolved by the state layer.
    _controller.backToPlaybackSource();
  }

  @override
  Widget build(BuildContext context) {
    final AppUiState state = _state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppMedia media = appMediaOf(context);
    final AppSpacing spacing = appSpacingOf(context);
    final PlaybackState playback = state.playback;
    final String? url = playback.videoUrl?.url;
    final VideoPlayerController? video = _video;
    final bool videoReady =
        !_playerError && video != null && video.value.isInitialized;

    final PlayerPhase phase = !videoReady
        ? PlayerPhase.idle
        : (video.value.isBuffering ? PlayerPhase.buffering : PlayerPhase.ready);
    // Overlay priority ERROR > COVER_LOADING > BUFFERING > NONE, derived from the
    // same projection the Kotlin screen used.
    final PlayerOverlayMode overlay = playerOverlayMode(
      url,
      phase,
      _hasFirstReady,
      _playerError,
    );

    final bool showRewardRing =
        state.vipActive && playback.videoUrl?.estimatedRewardPoints != null;
    final EpisodeSummary? errorNextEpisode = playerErrorNextEpisode(
      playback.episode,
      state.episodes,
    );

    return Scaffold(
      // The plane behind the video is the media backdrop in dark mode, the page
      // canvas otherwise - both tokens, never a raw black.
      backgroundColor: colors.brightness == Brightness.dark
          ? media.gradientBase
          : colors.canvas,
      body: Stack(
        children: <Widget>[
          // Video surface with its full-bleed overlays and gestures.
          Positioned.fill(
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              // Tap pauses, double-tap likes, edge drags drive brightness and
              // volume, and a middle drag swipes to the next/previous episode.
              onTap: _togglePlayPause,
              onDoubleTap: _onDoubleTapLike,
              onVerticalDragStart: _onVerticalDragStart,
              onVerticalDragUpdate: _onVerticalDragUpdate,
              onVerticalDragEnd: _onVerticalDragEnd,
              child: Stack(
                fit: StackFit.expand,
                children: <Widget>[
                  if (videoReady)
                    ClipRect(
                      // RESIZE_MODE_ZOOM: fill the surface and crop, no letterbox.
                      child: FittedBox(
                        fit: BoxFit.cover,
                        child: SizedBox(
                          width: video.value.size.width.toDouble(),
                          height: video.value.size.height.toDouble(),
                          child: VideoPlayer(video),
                        ),
                      ),
                    )
                  else
                    _CoverOverlay(state: state),
                  // Paused state: a large translucent play affordance.
                  if (videoReady && !_wasPlaying)
                    Center(
                      child: ExcludeSemantics(
                        child: Container(
                          width: 72,
                          height: 72,
                          decoration: BoxDecoration(
                            color: media.scrim,
                            shape: BoxShape.circle,
                          ),
                          child: Icon(
                            Icons.play_arrow_rounded,
                            color: media.onMedia,
                            size: 48,
                          ),
                        ),
                      ),
                    ),
                  // Brightness/volume drag feedback pill.
                  if (_gestureLabel != null)
                    Center(
                      child: _GestureFeedbackPill(
                        icon: _gestureIcon!,
                        label: _gestureLabel!,
                        value: _gestureValue!,
                      ),
                    ),
                  switch (overlay) {
                    PlayerOverlayMode.error => _ErrorOverlay(
                      onRetry: _retryPlayback,
                      onNext: errorNextEpisode == null
                          ? null
                          : () => _controller.openPlayer(errorNextEpisode),
                      onBack: _handleBack,
                    ),
                    PlayerOverlayMode.buffering => _BufferingOverlay(
                      state: state,
                    ),
                    PlayerOverlayMode.coverLoading ||
                    PlayerOverlayMode.none => const SizedBox.shrink(),
                  },
                ],
              ),
            ),
          ),
          // Top bar: back button, reward ring, `+N pt` toast.
          Positioned(
            top: 0,
            left: spacing.space2,
            right: spacing.space2,
            child: SafeArea(
              bottom: false,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  _CircleIconButton(
                    icon: Icons.arrow_back,
                    label: AppUiCopy.back,
                    onTap: _handleBack,
                  ),
                  const Spacer(),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: <Widget>[
                      if (showRewardRing) _RewardRing(state: state),
                      if (_rewardToastVisible)
                        Padding(
                          padding: EdgeInsets.only(
                            top: showRewardRing ? spacing.space1 : 0,
                            right: spacing.space1,
                          ),
                          child: _RewardToast(points: _rewardToastPoints),
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          // Reward report failures keep the video running, banner only.
          if (playback.rewardReportError)
            Positioned(
              top: 0,
              left: 0,
              right: 0,
              child: SafeArea(
                bottom: false,
                child: Align(
                  alignment: Alignment.topCenter,
                  child: Padding(
                    padding: EdgeInsets.only(top: spacing.space2),
                    child: _RewardErrorBanner(state: state),
                  ),
                ),
              ),
            ),
          // Right action rail: like, comments, favourite.
          Positioned(
            right: spacing.space4,
            bottom:
                MediaQuery.viewPaddingOf(context).bottom + _railBottomInset,
            child: _ActionRail(state: state),
          ),
          // Bottom info: titles, description, progress, episode selector.
          Positioned(left: 0, right: 0, bottom: 0, child: _BottomInfo(state: state)),
        ],
      ),
    );
  }
}

/// Cover plus scrim shown while the first frame is not ready.
class _CoverOverlay extends StatelessWidget {
  const _CoverOverlay({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppMedia media = appMediaOf(context);
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final String? coverUrl = state.playback.book?.coverUrl.coverUrlOrNull();
    final int episodeNumber = state.playback.episode?.number ?? 1;

    return Stack(
      fit: StackFit.expand,
      children: <Widget>[
        if (coverUrl != null)
          CachedNetworkImage(imageUrl: coverUrl, fit: BoxFit.cover)
        else
          DecoratedBox(
            // Placeholder when the book has no artwork: the media token pair,
            // so the chrome above it keeps its contrast contract.
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: <Color>[AppMedia.glassBase, media.gradientBase],
              ),
            ),
          ),
        ColoredBox(color: media.scrim),
        Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              SizedBox(
                width: spacing.space8,
                height: spacing.space8,
                child: const CircularProgressIndicator(strokeWidth: 2),
              ),
              SizedBox(height: spacing.space3),
              Text(
                playerLoadingLabel(episodeNumber, state.language),
                style: roles.onMediaBody.copyWith(
                  color: media.onMediaSecondary,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// Compact buffering pill over an already-started video.
class _BufferingOverlay extends StatelessWidget {
  const _BufferingOverlay({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppMedia media = appMediaOf(context);
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final int episodeNumber = state.playback.episode?.number ?? 1;

    return Center(
      child: Container(
        padding: EdgeInsets.symmetric(
          horizontal: spacing.space4,
          vertical: spacing.space3,
        ),
        decoration: BoxDecoration(
          color: media.glassStrong,
          borderRadius: BorderRadius.circular(shapes.radiusMedia),
          border: Border.all(color: media.divider),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            SizedBox(
              width: spacing.space5,
              height: spacing.space5,
              child: const CircularProgressIndicator(strokeWidth: 2),
            ),
            SizedBox(width: spacing.space3),
            Text(
              playerLoadingLabel(episodeNumber, state.language),
              style: roles.onMediaBody.copyWith(color: media.onMedia),
            ),
          ],
        ),
      ),
    );
  }
}

/// Error overlay with the full recovery set: retry rebuilds the player, next
/// appears only while a successor exists, back returns to the source screen.
class _ErrorOverlay extends StatelessWidget {
  const _ErrorOverlay({
    required this.onRetry,
    required this.onNext,
    required this.onBack,
  });

  final VoidCallback onRetry;
  final VoidCallback? onNext;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppStrings copy = strings(state.language);
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return Center(
      // The recovery card is a *surface* plane, not media glass: the three
      // actions are `AppButton`s, whose variants paint `textPrimary` and other
      // surface-plane foregrounds. On dark glass those are unreadable in the
      // light theme, so the card carries the surface colours the buttons were
      // designed against and the recovery set stays legible in both themes.
      child: Container(
        margin: EdgeInsets.symmetric(horizontal: spacing.space8),
        padding: EdgeInsets.all(spacing.space4),
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: BorderRadius.circular(shapes.radiusContainer),
          border: Border.all(
            color: colors.hairline,
            width: shapes.strokeHairline,
          ),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Text(
              copy.playerVideoError,
              textAlign: TextAlign.center,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: roles.body.copyWith(
                color: colors.textPrimary,
                fontWeight: FontWeight.bold,
              ),
            ),
            SizedBox(height: spacing.space3),
            // One filled action per region: Retry is the primary recovery, Next
            // is its outlined alternative and Back is the quiet escape. They
            // stack full width so the recovery set survives a 320 px screen at
            // 3.2x text instead of overflowing the row.
            AppButton(
              label: copy.playerRetryAction,
              isExpanded: true,
              onPressed: onRetry,
            ),
            if (onNext != null) ...<Widget>[
              SizedBox(height: spacing.space2),
              AppButton(
                label: copy.playerNextEpisodeAction,
                variant: AppButtonVariant.secondary,
                isExpanded: true,
                onPressed: onNext,
              ),
            ],
            SizedBox(height: spacing.space2),
            AppButton(
              label: copy.playerBackAction,
              variant: AppButtonVariant.tertiary,
              isExpanded: true,
              onPressed: onBack,
            ),
          ],
        ),
      ),
    );
  }
}

/// Round translucent icon button for the player chrome.
class _CircleIconButton extends StatelessWidget {
  const _CircleIconButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final AppIconSize icons = appIconSizeOf(context);
    final AppMedia media = appMediaOf(context);

    return Semantics(
      button: true,
      label: label,
      onTap: onTap,
      excludeSemantics: true,
      child: Tooltip(
        message: label,
        child: Material(
          color: media.glass,
          shape: const CircleBorder(),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onTap,
            child: SizedBox(
              // The chrome target is never smaller than the touch minimum.
              width: _minTargetExtent,
              height: _minTargetExtent,
              child: Icon(icon, color: media.onMedia, size: icons.md),
            ),
          ),
        ),
      ),
    );
  }
}

/// The VIP-only reward ring; tapping opens the reward explanation sheet.
class _RewardRing extends StatelessWidget {
  const _RewardRing({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final PlaybackState playback = state.playback;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppMedia media = appMediaOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    // Ring state priority: error > reporting > daily limit > unavailable > claimed > ready >
    // waiting — all derived inside the shared formatter from the same inputs the Kotlin badge
    // used. The centre shows the estimate before settlement and the awarded points after.
    final RewardBadgeState badge = rewardBadgeState(
      progressPercent: playback.progressPercent,
      estimatedRewardPoints: playback.videoUrl?.estimatedRewardPoints ?? 0,
      isReporting: playback.isRewardReporting,
      hasError: playback.rewardReportError,
      language: state.language,
      rewardClaimed: playback.rewardClaimed,
      rewardStatus: playback.rewardStatus,
      awardedPoints: playback.awardedPoints,
    );
    // Media-plane accents: the muted states use the media foreground rather
    // than the surface-plane `textSecondary`, which is a dark grey in the light
    // theme and vanishes over video.
    final Color ringColor = switch (badge.visualState) {
      RewardBadgeVisualState.error => colors.accentDanger,
      RewardBadgeVisualState.dailyLimit ||
      RewardBadgeVisualState.unavailable => media.onMediaSecondary,
      _ => colors.accentReward,
    };

    return Semantics(
      button: true,
      label: rewardBadgeContentDescription(badge, state.language),
      onTap: () => _openRewardInfoSheet(context),
      excludeSemantics: true,
      child: InkWell(
        onTap: () => _openRewardInfoSheet(context),
        customBorder: const CircleBorder(),
        child: Container(
          // Same geometry as before (48 target / 44 ring), now on the standard
          // chrome plate: the figure is text over arbitrary video, so the glass
          // makes its contrast deterministic instead of content-dependent.
          width: _rewardRingTarget,
          height: _rewardRingTarget,
          decoration: BoxDecoration(color: media.glass, shape: BoxShape.circle),
          child: Stack(
            alignment: Alignment.center,
            children: <Widget>[
              SizedBox(
                width: _rewardRingDiameter,
                height: _rewardRingDiameter,
                child: CircularProgressIndicator(
                  value: badge.ringProgress,
                  strokeWidth: 3,
                  color: ringColor,
                  backgroundColor: media.divider,
                ),
              ),
              Text(
                badge.pointsText,
                // Numeric role, so a 3-digit estimate still fits the ring; the
                // arc keeps the reward/state colour, the figure stays readable
                // over any frame.
                style: roles.numeric.copyWith(
                  color: media.onMedia,
                  fontSize: badge.pointsFontSizeSp,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _openRewardInfoSheet(BuildContext context) {
    final AppStrings copy = strings(state.language);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (BuildContext sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(18, 0, 18, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Row(
                children: <Widget>[
                  Expanded(
                    child: Text(
                      rewardBadgeInfoTitle(state.language),
                      style: roles.display,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    tooltip: copy.playerClose,
                    onPressed: () => Navigator.of(sheetContext).pop(),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                rewardBadgeInfoBody(state.language),
                style: roles.body.copyWith(color: colors.textSecondary),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Center pill shown while a brightness/volume drag is in progress.
class _GestureFeedbackPill extends StatelessWidget {
  const _GestureFeedbackPill({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final double value;

  @override
  Widget build(BuildContext context) {
    final AppMedia media = appMediaOf(context);
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: spacing.space4,
        vertical: spacing.space3,
      ),
      decoration: BoxDecoration(
        color: media.glassStrong,
        borderRadius: BorderRadius.circular(shapes.radiusPill),
        border: Border.all(color: media.divider),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Icon(icon, color: media.onMedia, size: appIconSizeOf(context).md),
          SizedBox(width: spacing.space2),
          SizedBox(
            width: _gestureTrackWidth,
            height: spacing.space1,
            child: ClipRRect(
              borderRadius: shapes.inlineRadius,
              child: Stack(
                fit: StackFit.expand,
                children: <Widget>[
                  ColoredBox(color: media.divider),
                  FractionallySizedBox(
                    alignment: Alignment.centerLeft,
                    widthFactor: value.clamp(0.0, 1.0),
                    child: ColoredBox(color: media.onMedia),
                  ),
                ],
              ),
            ),
          ),
          SizedBox(width: spacing.space2),
          Text(
            '${(value.clamp(0.0, 1.0) * 100).round()}%',
            style: roles.caption.copyWith(
              color: media.onMedia,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

/// The settled-reward overlay: `+N pt` from the shared award label.
class _RewardToast extends StatelessWidget {
  const _RewardToast({required this.points});

  final int points;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppUiState state = context.watch<AppStateController>().state;

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: spacing.space3,
        vertical: spacing.space1,
      ),
      decoration: BoxDecoration(
        color: colors.accentReward,
        borderRadius: BorderRadius.circular(shapes.radiusMedia),
      ),
      child: Text(
        rewardBadgeAwardLabel(points, state.language),
        style: roles.caption.copyWith(
          color: colors.onReward,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

/// Top-center banner for a failed reward report; playback is unaffected.
class _RewardErrorBanner extends StatelessWidget {
  const _RewardErrorBanner({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppMedia media = appMediaOf(context);
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: spacing.space4,
        vertical: spacing.space2,
      ),
      decoration: BoxDecoration(
        color: media.glassDanger,
        borderRadius: BorderRadius.circular(shapes.radiusControl),
      ),
      child: Text(
        // The danger tone is carried by the glass fill; the copy itself must
        // use the media foreground, because a surface-plane `accentDanger` is
        // near-black red in the light theme and disappears into the fill.
        strings(state.language).playerRewardError,
        style: roles.caption.copyWith(color: media.onMedia),
      ),
    );
  }
}

/// Right rail: like with `formatCount`, comments with the list size, favourite.
class _ActionRail extends StatelessWidget {
  const _ActionRail({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppMedia media = appMediaOf(context);
    final AppSpacing spacing = appSpacingOf(context);
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        _RailAction(
          icon: state.interaction.liked ? Icons.favorite : Icons.favorite_border,
          // Second channel: the filled glyph plus the accent, so a liked state
          // never rests on colour alone.
          color: state.interaction.liked
              ? colors.accentDanger
              : media.onMedia,
          label: _formatCount(state.interaction.likeCount),
          semanticLabel: _railSemanticLabel(
            AppUiCopy.playerLike,
            _formatCount(state.interaction.likeCount),
          ),
          onTap: () {
            unawaited(HapticFeedback.selectionClick());
            return controller.toggleLike();
          },
        ),
        SizedBox(height: spacing.space5),
        _RailAction(
          icon: Icons.mode_comment_outlined,
          color: media.onMedia,
          label: _formatCount(state.comments.length),
          semanticLabel: _railSemanticLabel(
            AppUiCopy.playerComments,
            _formatCount(state.comments.length),
          ),
          onTap: () async => _openCommentSheet(context),
        ),
        SizedBox(height: spacing.space5),
        _RailAction(
          icon: state.interaction.favorited
              ? Icons.bookmark
              : Icons.bookmark_border,
          color: state.interaction.favorited
              ? colors.accentPrimary
              : media.onMedia,
          label: copy.playerFavoriteLabel,
          semanticLabel: copy.playerFavoriteLabel,
          onTap: () {
            unawaited(HapticFeedback.selectionClick());
            return controller.toggleFavorite();
          },
        ),
      ],
    );
  }

  Future<void> _openCommentSheet(BuildContext context) {
    return AppSheet.show<void>(
      context,
      title: '${strings(state.language).playerCommentsTitlePrefix}'
          '${state.comments.length}',
      maxHeightFactor: _sheetHeightFactor,
      child: const _CommentSheet(),
    );
  }
}

/// Announced label of a rail action: the action word, then its count when there
/// is one. A bare count ("1.2k") never told a screen reader what it counted.
String _railSemanticLabel(String action, String count) =>
    count.isEmpty ? action : '$action, $count';

/// One rail button: a glass plate carrying the glyph, with its count below.
class _RailAction extends StatelessWidget {
  const _RailAction({
    required this.icon,
    required this.color,
    required this.label,
    required this.semanticLabel,
    required this.onTap,
  });

  final IconData icon;
  final Color color;
  final String label;
  final String semanticLabel;
  final Future<void> Function() onTap;

  @override
  Widget build(BuildContext context) {
    final AppMedia media = appMediaOf(context);
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;

    return Semantics(
      button: true,
      label: semanticLabel,
      onTap: () => unawaited(onTap()),
      excludeSemantics: true,
      child: InkWell(
        onTap: () => unawaited(onTap()),
        customBorder: const CircleBorder(),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Container(
              width: _minTargetExtent,
              height: _minTargetExtent,
              decoration: BoxDecoration(
                color: media.glass,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: color, size: appIconSizeOf(context).md),
            ),
            if (label.isNotEmpty) ...<Widget>[
              SizedBox(height: spacing.space1),
              Text(
                label,
                style: roles.caption.copyWith(color: media.onMedia),
                maxLines: 1,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Comments: list plus a draft field whose send is disabled while blank.
///
/// Submitting clears the draft immediately, exactly like the Kotlin sheet; the
/// state layer trims, validates and re-fetches the comments on success.
class _CommentSheet extends StatefulWidget {
  const _CommentSheet();

  @override
  State<_CommentSheet> createState() => _CommentSheetState();
}

class _CommentSheetState extends State<_CommentSheet> {
  final TextEditingController _draft = TextEditingController();

  @override
  void initState() {
    super.initState();
    _draft.addListener(() {
      if (mounted) {
        setState(() {});
      }
    });
  }

  @override
  void dispose() {
    _draft.dispose();
    super.dispose();
  }

  void _submit() {
    final String content = _draft.text;
    if (content.trim().isEmpty) {
      return;
    }
    // The Kotlin sheet clears the draft as soon as it hands the content over.
    _draft.clear();
    unawaited(context.read<AppStateController>().submitComment(content));
  }

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppIconSize icons = appIconSizeOf(context);
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppStrings copy = strings(state.language);
    final bool canSend = _draft.text.trim().isNotEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        if (state.isLoading && state.comments.isEmpty)
          // The first load is the only time the list can be "loading but
          // empty"; a submit-then-refresh keeps the rows on screen.
          Expanded(
            child: Center(
              child: AppInlineLoading(label: AppUiCopy.playerCommentsLoading),
            ),
          )
        else if (state.comments.isEmpty)
          Expanded(
            child: AppEmptyState(
              icon: Icons.mode_comment_outlined,
              title: AppUiCopy.playerCommentsEmptyTitle,
              body: AppUiCopy.playerCommentsEmptyBody,
            ),
          )
        else
          Expanded(
            child: ListView.separated(
              padding: EdgeInsets.zero,
              itemCount: state.comments.length,
              separatorBuilder: (_, _) => SizedBox(height: spacing.space3),
              itemBuilder: (BuildContext context, int index) {
                final Comment comment = state.comments[index];
                return _CommentItem(
                  key: ValueKey<String>(comment.id),
                  comment: comment,
                );
              },
            ),
          ),
        SizedBox(height: spacing.space2),
        SafeArea(
          top: false,
          child: Padding(
            padding: EdgeInsets.only(bottom: spacing.space2),
            child: Row(
              children: <Widget>[
                Expanded(
                  child: TextField(
                    controller: _draft,
                    decoration: InputDecoration(
                      hintText: copy.playerCommentPlaceholder,
                      isDense: true,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(shapes.radiusPill),
                        borderSide: BorderSide.none,
                      ),
                      filled: true,
                      fillColor: colors.surfaceAlt,
                    ),
                    textInputAction: TextInputAction.send,
                    onSubmitted: (_) => _submit(),
                  ),
                ),
                SizedBox(width: spacing.space2),
                // The send affordance keeps a full touch target and is disabled
                // (not hidden) while the draft is blank.
                Semantics(
                  button: true,
                  enabled: canSend,
                  label: AppUiCopy.playerCommentSend,
                  onTap: canSend ? _submit : null,
                  excludeSemantics: true,
                  child: InkWell(
                    onTap: canSend ? _submit : null,
                    customBorder: const CircleBorder(),
                    child: Container(
                      width: _minTargetExtent,
                      height: _minTargetExtent,
                      decoration: BoxDecoration(
                        color: canSend
                            ? colors.accentPrimary
                            : colors.surfaceAlt,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        Icons.send,
                        size: icons.sm,
                        color: canSend
                            ? colors.onAccent
                            : colors.textSecondary,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// One comment row: initial-avatar, username, content.
class _CommentItem extends StatelessWidget {
  const _CommentItem({required this.comment, super.key});

  final Comment comment;

  @override
  Widget build(BuildContext context) {
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final String initial = comment.username.isEmpty
        ? '?'
        : comment.username.characters.first.toUpperCase();

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        CircleAvatar(
          radius: 17,
          backgroundColor: colors.accentPrimary.withValues(alpha: 0.3),
          child: Text(
            initial,
            style: roles.body.copyWith(
              color: colors.accentPrimary,
              fontWeight: FontWeight.bold,
              fontSize: 14,
            ),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(
                comment.username,
                style: roles.body.copyWith(
                  color: colors.textSecondary,
                  fontSize: 12,
                ),
              ),
              const SizedBox(height: 2),
              Text(comment.content, style: roles.body),
            ],
          ),
        ),
      ],
    );
  }
}

/// Bottom info block: book/episode titles, description fallback, the watched
/// progress bar and the episode-selector entry.
class _BottomInfo extends StatelessWidget {
  const _BottomInfo({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppMedia media = appMediaOf(context);
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final PlaybackState playback = state.playback;
    final String bookTitle = playback.book?.title ?? '';
    final String episodeTitle = playback.episode?.title ?? '';
    final String episodeDescription =
        playback.episode?.description.trim().isNotEmpty == true
        ? playback.episode!.description
        : (playback.book?.description ?? '');
    final int progressPercent = playback.progressPercent.clamp(0, 100);

    // Ride above the system gesture area: the episode selector must never
    // sit under the navigation bar (Kotlin used `navigationBarsPadding`).
    final double navInset = MediaQuery.viewPaddingOf(context).bottom;

    return Container(
      width: double.infinity,
      decoration: BoxDecoration(gradient: media.bottomCopyGradient),
      padding: EdgeInsets.fromLTRB(
        spacing.space4,
        spacing.space8,
        _bottomCopyRailInset,
        spacing.space4 + navInset,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          if (bookTitle.isNotEmpty)
            Text(
              bookTitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: roles.onMediaBody.copyWith(
                color: media.onMedia,
                fontWeight: FontWeight.bold,
              ),
            ),
          if (episodeTitle.isNotEmpty)
            Text(
              episodeTitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              // Media foreground, not `accentPrimary`: the light-theme accent is
              // a dark purple that loses contrast on the copy gradient; the size
              // step under the bold book title carries the hierarchy instead.
              style: roles.body.copyWith(
                color: media.onMedia,
                fontWeight: FontWeight.w600,
              ),
            ),
          if (episodeDescription.isNotEmpty)
            Text(
              episodeDescription,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: roles.caption.copyWith(color: media.onMediaSecondary),
            ),
          SizedBox(height: spacing.space2),
          ClipRRect(
            borderRadius: shapes.inlineRadius,
            child: SizedBox(
              height: _progressBarHeight,
              width: double.infinity,
              child: Stack(
                fit: StackFit.expand,
                children: <Widget>[
                  ColoredBox(color: media.divider),
                  FractionallySizedBox(
                    alignment: Alignment.centerLeft,
                    widthFactor: progressPercent / 100,
                    child: ColoredBox(color: colors.accentPrimary),
                  ),
                ],
              ),
            ),
          ),
          if (state.episodes.isNotEmpty) ...<Widget>[
            SizedBox(height: spacing.space3),
            _EpisodeSelectorBar(state: state),
          ],
        ],
      ),
    );
  }
}

/// The episode-selector entry row opening the sheet.
class _EpisodeSelectorBar extends StatelessWidget {
  const _EpisodeSelectorBar({required this.state});

  final AppUiState state;

  @override
  Widget build(BuildContext context) {
    final AppStrings copy = strings(state.language);
    final EpisodeSummary? current = state.selectedEpisode;
    final String currentLabel = current == null
        ? ''
        : ' · ${episodeNumberLabel(current.number, state.language)}';

    // A named, full-width button instead of a bare row: the drawer entry now
    // says what it opens and what is playing. It is a *media* button, not an
    // `AppButton`: the component's variants paint surface-plane foregrounds
    // (near-black `textPrimary` in the light theme), which vanish over video.
    final String label = '${AppUiCopy.playerEpisodes}$currentLabel';
    return _MediaButton(
      icon: Icons.keyboard_arrow_up,
      label: label,
      semanticLabel: current == null
          ? AppUiCopy.playerEpisodes
          : '${AppUiCopy.playerEpisodes}, '
                '${copy.playerCurrentEpisodePrefix}'
                '${episodeNumberLabel(current.number, state.language)}',
      onPressed: () => _openEpisodeSheet(context),
    );
  }

  Future<void> _openEpisodeSheet(BuildContext context) {
    return AppSheet.show<void>(
      context,
      title: episodeSelectorLabel(state.episodes.length, state.language),
      maxHeightFactor: _sheetHeightFactor,
      child: const _EpisodeSheet(),
    );
  }
}

/// A full-width action that lives on the video surface.
///
/// The media plane is brightness-independent dark glass, so its chrome uses the
/// [AppMedia] foreground roles: `glass` fill, `divider` hairline and `onMedia`
/// text. The surface-plane `AppButton` variants cannot be used here - their
/// `textPrimary` foreground is near-black in the light theme, which is exactly
/// how the episode entry became unreadable on device.
class _MediaButton extends StatelessWidget {
  const _MediaButton({
    required this.icon,
    required this.label,
    required this.semanticLabel,
    required this.onPressed,
  });

  final IconData icon;
  final String label;
  final String semanticLabel;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final AppMedia media = appMediaOf(context);
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppIconSize icons = appIconSizeOf(context);

    return Semantics(
      button: true,
      label: semanticLabel,
      onTap: onPressed,
      excludeSemantics: true,
      child: Material(
        color: media.glass,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(shapes.radiusCell),
          side: BorderSide(color: media.divider),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onPressed,
          child: Container(
            width: double.infinity,
            constraints: const BoxConstraints(minHeight: _minTargetExtent),
            padding: EdgeInsets.symmetric(
              horizontal: spacing.space4,
              vertical: spacing.space3,
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                Icon(icon, color: media.onMedia, size: icons.md),
                SizedBox(width: spacing.space2),
                Flexible(
                  child: Text(
                    label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: roles.onMediaBody.copyWith(
                      color: media.onMedia,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// The episode sheet: shared watch-status projection, VIP lock above the free
/// threshold, re-selecting the current episode only closes.
///
/// Every row pairs an [EpisodeSpine] tick - the design system's signature rail,
/// and the screen-reader target with its 44 px square - with the episode number,
/// its title and the VIP lock. The row itself is tappable too, so a touch user
/// never has to aim at the tick.
class _EpisodeSheet extends StatelessWidget {
  const _EpisodeSheet();

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppIconSize icons = appIconSizeOf(context);
    final AppSpacing spacing = appSpacingOf(context);
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final EpisodeSummary? current = state.selectedEpisode;
    final String? bookId = state.playback.book?.id;

    return ListView.builder(
      padding: EdgeInsets.zero,
      itemCount: state.episodes.length,
      itemBuilder: (BuildContext context, int index) {
        final EpisodeSummary episode = state.episodes[index];
        final bool selected =
            episode.number == current?.number &&
            episode.chapterId == current?.chapterId;
        final EpisodeWatchStatus status = episodeWatchStatus(
          bookId,
          episode,
          current,
          state.watchHistory,
        );
        final bool locked =
            !state.vipActive && episode.number > defaultVipFreeEpisodes;
        final String statusLabel = selected
            ? copy.playerEpisodeStatusCurrent
            : episodeWatchStatusLabel(status, state.language);
        final String number = episodeNumberLabel(
          episode.number,
          state.language,
        );
        final String title = episode.title.trim();
        final EpisodeTickState tickState = selected
            ? EpisodeTickState.current
            : switch (status.type) {
                EpisodeWatchStatusType.watched => EpisodeTickState.watched,
                EpisodeWatchStatusType.inProgress =>
                  EpisodeTickState.inProgress,
                EpisodeWatchStatusType.current => EpisodeTickState.current,
                EpisodeWatchStatusType.none => EpisodeTickState.none,
              };

        void open() {
          // Re-selecting the current episode only closes the sheet.
          Navigator.of(context).pop();
          if (!selected) {
            controller.openPlayer(episode);
          }
        }

        return InkWell(
          key: ValueKey<String>('${episode.chapterId}:${episode.number}'),
          onTap: open,
          // The spine tick carries the announcement and the activation action,
          // so the row's own ink stays out of the semantics tree.
          excludeFromSemantics: true,
          child: Row(
            children: <Widget>[
              EpisodeSpine(
                ticks: <EpisodeTick>[
                  EpisodeTick(
                    number: episode.number,
                    state: tickState,
                    progress: selected ? 0 : status.progressPercent,
                    isVipLocked: locked && !selected,
                    onTap: open,
                  ),
                ],
                size: EpisodeSpineSize.full,
                language: state.language,
                // One tick per row: the row's own text carries the meaning, so
                // the rail must not repeat a one-episode summary per line.
                semanticsLabel: '',
              ),
              SizedBox(width: spacing.space3),
              Expanded(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: spacing.space2),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: <Widget>[
                      Text(
                        title.isEmpty ? number : '$number · $title',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: roles.body.copyWith(
                          color: selected
                              ? colors.accentPrimary
                              : colors.textPrimary,
                          fontWeight: selected ? FontWeight.w700 : null,
                        ),
                      ),
                      if (statusLabel.isNotEmpty)
                        Text(
                          statusLabel,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: roles.caption.copyWith(
                            color: colors.textSecondary,
                          ),
                        ),
                    ],
                  ),
                ),
              ),
              if (locked && !selected)
                Icon(
                  Icons.lock,
                  size: icons.sm,
                  color: colors.accentPrimary,
                  semanticLabel: 'VIP',
                ),
              SizedBox(width: spacing.space2),
            ],
          ),
        );
      },
    );
  }
}
