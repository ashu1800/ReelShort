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
import 'package:provider/provider.dart';
import 'package:video_player/video_player.dart';

import '../../core/formatting/app_strings.dart';
import '../../core/formatting/reward_badge_state.dart';
import '../../core/formatting/ui_formats.dart';
import '../../data/models/app_models.dart';
import '../../state/app_state_controller.dart';
import '../../state/app_ui_state.dart';
import '../../state/playback_state.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_shapes.dart';
import '../../theme/app_typography.dart';
import '../components/app_button.dart';

/// How often the position tick runs.
const Duration _tickInterval = Duration(seconds: 1);

/// How long the `+N pt` overlay stays up.
const Duration _rewardToastDuration = Duration(milliseconds: 1100);

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
      if (!mounted ||
          !identical(_video, controller) ||
          !controller.value.hasError ||
          _playerError) {
        return;
      }
      setState(() => _playerError = true);
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

  void _handleBack() {
    // Back target = `playerReturnScreen ?? home`, resolved by the state layer.
    _controller.backToPlaybackSource();
  }

  @override
  Widget build(BuildContext context) {
    final AppUiState state = _state;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
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
      backgroundColor:
          colors.brightness == Brightness.dark ? Colors.black : colors.canvas,
      body: Stack(
        children: <Widget>[
          // Video surface with its full-bleed overlays.
          Positioned.fill(
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
                switch (overlay) {
                  PlayerOverlayMode.error => _ErrorOverlay(
                    onRetry: _retryPlayback,
                    onNext: errorNextEpisode == null
                        ? null
                        : () => _controller.openPlayer(errorNextEpisode),
                    onBack: _handleBack,
                  ),
                  PlayerOverlayMode.buffering => _BufferingOverlay(state: state),
                  PlayerOverlayMode.coverLoading ||
                  PlayerOverlayMode.none => const SizedBox.shrink(),
                },
              ],
            ),
          ),
          // Top bar: back button, reward ring, `+N pt` toast.
          Positioned(
            top: 0,
            left: 8,
            right: 8,
            child: SafeArea(
              bottom: false,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  _CircleIconButton(
                    icon: Icons.arrow_back,
                    label: strings(state.language).playerScreen,
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
                            top: showRewardRing ? 4 : 0,
                            right: 4,
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
                    padding: const EdgeInsets.only(top: 8),
                    child: _RewardErrorBanner(state: state),
                  ),
                ),
              ),
            ),
          // Right action rail: like, comments, favourite.
          Positioned(right: 12, bottom: 140, child: _ActionRail(state: state)),
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
    final String? coverUrl = state.playback.book?.coverUrl.coverUrlOrNull();
    final int episodeNumber = state.playback.episode?.number ?? 1;

    return Stack(
      fit: StackFit.expand,
      children: <Widget>[
        if (coverUrl != null)
          CachedNetworkImage(imageUrl: coverUrl, fit: BoxFit.cover)
        else
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: <Color>[
                  Color(0xFF2B2412),
                  Color(0xFF10141D),
                  Colors.black,
                ],
              ),
            ),
          ),
        const ColoredBox(color: Color(0xB3000000)),
        Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const SizedBox(
                width: 36,
                height: 36,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
              const SizedBox(height: 12),
              Text(
                playerLoadingLabel(episodeNumber, state.language),
                style: const TextStyle(color: Colors.white70),
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
    final int episodeNumber = state.playback.episode?.number ?? 1;

    return Center(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: const Color(0xD611151E),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: Colors.white24),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
            const SizedBox(width: 10),
            Text(
              playerLoadingLabel(episodeNumber, state.language),
              style: const TextStyle(color: Colors.white),
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

    return Center(
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 36),
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: const Color(0xE611151E),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: Colors.white24),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Text(
              copy.playerVideoError,
              textAlign: TextAlign.center,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                AppButton(label: copy.playerRetryAction, onPressed: onRetry),
                if (onNext != null) ...<Widget>[
                  const SizedBox(width: 10),
                  AppButton(
                    label: copy.playerNextEpisodeAction,
                    variant: AppButtonVariant.secondary,
                    onPressed: onNext,
                  ),
                ],
              ],
            ),
            const SizedBox(height: 6),
            TextButton(
              onPressed: onBack,
              child: Text(
                copy.playerBackAction,
                style: TextStyle(color: colors.textSecondary),
              ),
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
    return Semantics(
      button: true,
      label: label,
      child: Material(
        color: Colors.black38,
        shape: const CircleBorder(),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: SizedBox(
            width: 40,
            height: 40,
            child: Icon(icon, color: Colors.white, size: 22),
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
    final Color ringColor = switch (badge.visualState) {
      RewardBadgeVisualState.error => colors.accentDanger,
      RewardBadgeVisualState.dailyLimit ||
      RewardBadgeVisualState.unavailable => colors.textSecondary,
      _ => colors.accentReward,
    };

    return Semantics(
      button: true,
      label: rewardBadgeContentDescription(badge, state.language),
      child: InkWell(
        onTap: () => _openRewardInfoSheet(context),
        customBorder: const CircleBorder(),
        child: SizedBox(
          width: 44,
          height: 44,
          child: Stack(
            alignment: Alignment.center,
            children: <Widget>[
              CircularProgressIndicator(
                value: badge.ringProgress,
                strokeWidth: 3,
                color: ringColor,
                backgroundColor: Colors.white24,
              ),
              Text(
                badge.pointsText,
                style: TextStyle(
                  color: ringColor,
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

/// The settled-reward overlay: `+N pt` from the shared award label.
class _RewardToast extends StatelessWidget {
  const _RewardToast({required this.points});

  final int points;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppUiState state = context.watch<AppStateController>().state;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: colors.accentReward,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Text(
        rewardBadgeAwardLabel(points, state.language),
        style: TextStyle(
          color: colors.onReward,
          fontSize: 12,
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
    final AppColors colors = Theme.of(context).extension<AppColors>()!;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: const Color(0xE63A1417),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        strings(state.language).playerRewardError,
        style: TextStyle(color: colors.accentDanger, fontSize: 12),
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
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        // The Kotlin rail exposes only the formatted count as the icon's
        // content description for like and comments.
        _RailAction(
          icon: state.interaction.liked ? Icons.favorite : Icons.favorite_border,
          color: state.interaction.liked
              ? const Color(0xFFFF5C7A)
              : Colors.white,
          label: _formatCount(state.interaction.likeCount),
          semanticLabel: _formatCount(state.interaction.likeCount),
          onTap: controller.toggleLike,
        ),
        const SizedBox(height: 22),
        _RailAction(
          icon: Icons.mode_comment_outlined,
          color: Colors.white,
          label: _formatCount(state.comments.length),
          semanticLabel: _formatCount(state.comments.length),
          onTap: () async => _openCommentSheet(context),
        ),
        const SizedBox(height: 22),
        _RailAction(
          icon: state.interaction.favorited
              ? Icons.bookmark
              : Icons.bookmark_border,
          color: state.interaction.favorited
              ? colors.accentPrimary
              : Colors.white,
          label: copy.playerFavoriteLabel,
          semanticLabel: copy.playerFavoriteLabel,
          onTap: controller.toggleFavorite,
        ),
      ],
    );
  }

  Future<void> _openCommentSheet(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => const _CommentSheet(),
    );
  }
}

/// One rail button: icon above the count/label, both readable by screen readers.
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
    return Semantics(
      button: true,
      label: semanticLabel,
      child: InkWell(
        onTap: () => unawaited(onTap()),
        customBorder: const CircleBorder(),
        child: SizedBox(
          width: 48,
          height: 52,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              Icon(icon, color: color, size: 26),
              if (label.isNotEmpty)
                Text(
                  label,
                  style: const TextStyle(color: Colors.white, fontSize: 11),
                  maxLines: 1,
                ),
            ],
          ),
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
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppStrings copy = strings(state.language);
    final bool canSend = _draft.text.trim().isNotEmpty;

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.7,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 8, 12),
              child: Row(
                children: <Widget>[
                  Expanded(
                    child: Text(
                      '${copy.playerCommentsTitlePrefix}${state.comments.length}',
                      style: roles.display,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    tooltip: copy.playerClose,
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
            ),
            if (state.comments.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 28),
                child: Center(
                  child: Text(
                    copy.playerCommentsEmpty,
                    style: roles.body.copyWith(color: colors.textSecondary),
                  ),
                ),
              )
            else
              Expanded(
                child: ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  itemCount: state.comments.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 12),
                  itemBuilder: (BuildContext context, int index) {
                    final Comment comment = state.comments[index];
                    return _CommentItem(
                      key: ValueKey<String>(comment.id),
                      comment: comment,
                    );
                  },
                ),
              ),
            SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
                child: Row(
                  children: <Widget>[
                    Expanded(
                      child: TextField(
                        controller: _draft,
                        decoration: InputDecoration(
                          hintText: copy.playerCommentPlaceholder,
                          isDense: true,
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(22),
                            borderSide: BorderSide.none,
                          ),
                          filled: true,
                          fillColor: colors.surfaceAlt,
                        ),
                        textInputAction: TextInputAction.send,
                        onSubmitted: (_) => _submit(),
                      ),
                    ),
                    const SizedBox(width: 8),
                    InkWell(
                      onTap: canSend ? _submit : null,
                      borderRadius: BorderRadius.circular(22),
                      child: Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          color: canSend
                              ? colors.accentPrimary
                              : colors.surfaceAlt,
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          Icons.send,
                          size: 20,
                          color: canSend
                              ? colors.onAccent
                              : colors.textSecondary,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
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
    final PlaybackState playback = state.playback;
    final String bookTitle = playback.book?.title ?? '';
    final String episodeTitle = playback.episode?.title ?? '';
    final String episodeDescription =
        playback.episode?.description.trim().isNotEmpty == true
        ? playback.episode!.description
        : (playback.book?.description ?? '');
    final int progressPercent = playback.progressPercent.clamp(0, 100);

    return Container(
      width: double.infinity,
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: <Color>[Colors.transparent, Color(0xE6000000)],
        ),
      ),
      padding: const EdgeInsets.fromLTRB(16, 32, 84, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          if (bookTitle.isNotEmpty)
            Text(
              bookTitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
                fontSize: 16,
              ),
            ),
          if (episodeTitle.isNotEmpty)
            Text(
              episodeTitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: colors.accentPrimary,
                fontSize: 14,
              ),
            ),
          if (episodeDescription.isNotEmpty)
            Text(
              episodeDescription,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Colors.white70, fontSize: 12),
            ),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(2),
            child: SizedBox(
              height: 3,
              width: double.infinity,
              child: Stack(
                fit: StackFit.expand,
                children: <Widget>[
                  const ColoredBox(color: Color(0x44FFFFFF)),
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
            const SizedBox(height: 10),
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
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppStrings copy = strings(state.language);
    final EpisodeSummary? current = state.selectedEpisode;

    return InkWell(
      onTap: () => _openEpisodeSheet(context),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        height: 54,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        decoration: BoxDecoration(
          color: const Color(0xE61A1A1A),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: <Widget>[
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    episodeSelectorLabel(state.episodes.length, state.language),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                    ),
                  ),
                  if (current != null)
                    Text(
                      '${copy.playerCurrentEpisodePrefix}'
                      '${episodeNumberLabel(current.number, state.language)}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: colors.textSecondary, fontSize: 11),
                    ),
                ],
              ),
            ),
            Icon(
              Icons.keyboard_arrow_up,
              color: Colors.white,
              size: 24,
              semanticLabel: copy.playerExpandEpisodes,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _openEpisodeSheet(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => const _EpisodeSheet(),
    );
  }
}

/// The episode sheet: shared watch-status projection, VIP lock above the free
/// threshold, re-selecting the current episode only closes.
class _EpisodeSheet extends StatelessWidget {
  const _EpisodeSheet();

  @override
  Widget build(BuildContext context) {
    final AppUiState state = context.watch<AppStateController>().state;
    final AppTypography roles = Theme.of(context).extension<AppTypography>()!;
    final AppStrings copy = strings(state.language);
    final AppStateController controller = context.read<AppStateController>();
    final EpisodeSummary? current = state.selectedEpisode;
    final String? bookId = state.playback.book?.id;
    final double sheetHeight = MediaQuery.sizeOf(context).height * 0.65;

    return SizedBox(
      height: sheetHeight,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 8, 12),
            child: Row(
              children: <Widget>[
                Expanded(
                  child: Text(
                    episodeSelectorLabel(state.episodes.length, state.language),
                    style: roles.display,
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  tooltip: copy.playerClose,
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
          ),
          Expanded(
            child: GridView.builder(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: 88,
                mainAxisSpacing: 10,
                crossAxisSpacing: 10,
                childAspectRatio: 1.125,
              ),
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
                return _EpisodeCell(
                  key: ValueKey<String>(
                    '${episode.chapterId}:${episode.number}',
                  ),
                  number: episode.number,
                  statusLabel: selected
                      ? copy.playerEpisodeStatusCurrent
                      : episodeWatchStatusLabel(status, state.language),
                  progressPercent: selected ? 0 : status.progressPercent,
                  selected: selected,
                  locked: locked && !selected,
                  watched: status.type == EpisodeWatchStatusType.watched,
                  onTap: () {
                    // Re-selecting the current episode only closes the sheet.
                    Navigator.of(context).pop();
                    if (!selected) {
                      controller.openPlayer(episode);
                    }
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

/// One episode cell: number, optional VIP lock, status label and a thin
/// bottom progress strip.
class _EpisodeCell extends StatelessWidget {
  const _EpisodeCell({
    required this.number,
    required this.statusLabel,
    required this.progressPercent,
    required this.selected,
    required this.locked,
    required this.watched,
    required this.onTap,
    super.key,
  });

  final int number;
  final String statusLabel;
  final int progressPercent;
  final bool selected;
  final bool locked;
  final bool watched;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final AppColors colors = Theme.of(context).extension<AppColors>()!;
    final AppShapes shapes = Theme.of(context).extension<AppShapes>()!;
    final Color background = selected
        ? colors.accentPrimary
        : (watched ? const Color(0xFF20202A) : const Color(0xFF1B202B));
    final Color foreground = selected ? colors.onAccent : colors.textPrimary;
    final Color secondary = selected
        ? colors.onAccent.withValues(alpha: 0.72)
        : colors.textSecondary;

    return InkWell(
      onTap: onTap,
      borderRadius: shapes.inlineRadius,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 4),
        decoration: BoxDecoration(
          color: background,
          borderRadius: shapes.inlineRadius,
          border: Border.all(
            color: watched && !selected
                ? colors.accentPrimary.withValues(alpha: 0.58)
                : Colors.white24,
          ),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            if (locked) ...<Widget>[
              Icon(
                Icons.lock,
                size: 14,
                color: colors.accentPrimary,
                semanticLabel: 'VIP',
              ),
              const SizedBox(height: 2),
            ],
            Text(
              '$number',
              maxLines: 1,
              style: TextStyle(
                color: locked ? colors.textSecondary : foreground,
                fontWeight: FontWeight.bold,
                fontSize: 14,
              ),
            ),
            if (statusLabel.isNotEmpty)
              Text(
                statusLabel,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(color: secondary, fontSize: 10),
              ),
          ],
        ),
      ),
    );
  }
}
