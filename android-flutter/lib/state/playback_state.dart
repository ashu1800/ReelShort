/// Playback progress state and the pure rules that guard reporting.
///
/// Port of `android-app/app-core/src/main/kotlin/com/reelshort/app/state/PlaybackState.kt`.
/// The top-level functions keep the Kotlin parameter order so the ported unit
/// tests read the same as the originals.
library;

import 'dart:math' as math;

import '../data/models/app_models.dart';

enum PlaybackStatus { idle, ready }

class PlaybackState {
  const PlaybackState({
    this.status = PlaybackStatus.idle,
    this.book,
    this.episode,
    this.videoUrl,
    this.positionSeconds = 0,
    this.durationSeconds = 0,
    this.progressPercent = 0,
    this.lastReportedPositionSeconds = 0,
    this.lastPersistedProgressPercent = 0,
    this.isRewardReporting = false,
    this.rewardReportError = false,
    this.rewardClaimed = false,
    this.rewardStatus = WatchRewardStatus.notComplete,
    this.awardedPoints = 0,
    this.rewardAwardVersion = 0,
  });

  final PlaybackStatus status;
  final BookSummary? book;
  final EpisodeSummary? episode;
  final VideoUrl? videoUrl;
  final int positionSeconds;
  final int durationSeconds;
  final int progressPercent;
  final int lastReportedPositionSeconds;
  final int lastPersistedProgressPercent;
  final bool isRewardReporting;
  final bool rewardReportError;
  final bool rewardClaimed;
  final WatchRewardStatus rewardStatus;
  final int awardedPoints;
  final int rewardAwardVersion;

  PlaybackState copyWith({
    PlaybackStatus? status,
    Object? book = _noChange,
    Object? episode = _noChange,
    Object? videoUrl = _noChange,
    int? positionSeconds,
    int? durationSeconds,
    int? progressPercent,
    int? lastReportedPositionSeconds,
    int? lastPersistedProgressPercent,
    bool? isRewardReporting,
    bool? rewardReportError,
    bool? rewardClaimed,
    WatchRewardStatus? rewardStatus,
    int? awardedPoints,
    int? rewardAwardVersion,
  }) => PlaybackState(
    status: status ?? this.status,
    book: identical(book, _noChange) ? this.book : book as BookSummary?,
    episode: identical(episode, _noChange)
        ? this.episode
        : episode as EpisodeSummary?,
    videoUrl: identical(videoUrl, _noChange)
        ? this.videoUrl
        : videoUrl as VideoUrl?,
    positionSeconds: positionSeconds ?? this.positionSeconds,
    durationSeconds: durationSeconds ?? this.durationSeconds,
    progressPercent: progressPercent ?? this.progressPercent,
    lastReportedPositionSeconds:
        lastReportedPositionSeconds ?? this.lastReportedPositionSeconds,
    lastPersistedProgressPercent:
        lastPersistedProgressPercent ?? this.lastPersistedProgressPercent,
    isRewardReporting: isRewardReporting ?? this.isRewardReporting,
    rewardReportError: rewardReportError ?? this.rewardReportError,
    rewardClaimed: rewardClaimed ?? this.rewardClaimed,
    rewardStatus: rewardStatus ?? this.rewardStatus,
    awardedPoints: awardedPoints ?? this.awardedPoints,
    rewardAwardVersion: rewardAwardVersion ?? this.rewardAwardVersion,
  );

  static const Object _noChange = Object();

  PlaybackState withPosition([
    int positionSeconds = 0,
    int? durationSeconds,
  ]) {
    final int rawDuration = durationSeconds ?? this.durationSeconds;
    final int safeDuration = math.max(rawDuration, 0);
    final int safePosition = safeDuration == 0
        ? math.max(positionSeconds, 0)
        : positionSeconds.clamp(0, safeDuration);
    final int percent = safeDuration == 0
        ? 0
        : ((safePosition.toDouble() / safeDuration.toDouble()) * 100)
              .round()
              .clamp(0, 100);
    return copyWith(
      positionSeconds: safePosition,
      durationSeconds: safeDuration,
      progressPercent: percent,
    );
  }

  PlaybackState withVideoUrl(VideoUrl videoUrl) =>
      copyWith(videoUrl: videoUrl).withPosition(
        positionSeconds,
        videoUrl.durationSeconds,
      );

  PlaybackState withReportedProgress({
    required int positionSeconds,
    required int progressPercent,
    bool? rewardClaimed,
    WatchRewardStatus? rewardStatus,
    int? awardedPoints,
  }) {
    final bool claimed = rewardClaimed ?? this.rewardClaimed;
    final WatchRewardStatus status = rewardStatus ?? this.rewardStatus;
    final int points = math.max(awardedPoints ?? this.awardedPoints, 0);
    return copyWith(
      lastReportedPositionSeconds: math.max(positionSeconds, 0),
      lastPersistedProgressPercent: progressPercent.clamp(0, 100),
      rewardReportError: false,
      rewardClaimed: claimed || status.isClaimed(),
      rewardStatus: status,
      awardedPoints: points,
    );
  }

  PlaybackState withSnapshot(WatchEpisodeSnapshot snapshot) => withPosition(
    snapshot.positionSeconds,
    snapshot.durationSeconds > 0 ? snapshot.durationSeconds : durationSeconds,
  ).withReportedProgress(
    positionSeconds: snapshot.positionSeconds,
    progressPercent: snapshot.progressPercent,
    rewardClaimed: snapshot.rewardClaimed,
    rewardStatus: snapshot.rewardStatus,
    awardedPoints: snapshot.awardedPoints,
  );

  PlaybackState withProgressReport(WatchProgressReport report) {
    final int awarded = math.max(report.awardedPoints, 0);
    final PlaybackState updated = withPosition(
      report.positionSeconds,
      report.durationSeconds > 0 ? report.durationSeconds : durationSeconds,
    ).withReportedProgress(
      positionSeconds: report.positionSeconds,
      progressPercent: report.progressPercent,
      rewardClaimed: report.rewardClaimed,
      rewardStatus: report.rewardStatus,
      awardedPoints: awarded,
    );
    return updated.copyWith(
      rewardAwardVersion: awarded > 0
          ? updated.rewardAwardVersion + 1
          : updated.rewardAwardVersion,
    );
  }

  PlaybackState withRewardReporting(bool isReporting) => copyWith(
    isRewardReporting: isReporting,
    rewardReportError: isReporting ? false : rewardReportError,
  );

  PlaybackState withRewardReportError() =>
      copyWith(isRewardReporting: false, rewardReportError: true);

  static PlaybackState ready({
    required BookSummary book,
    required EpisodeSummary episode,
    required VideoUrl videoUrl,
  }) => PlaybackState(
    status: PlaybackStatus.ready,
    book: book,
    episode: episode,
    videoUrl: videoUrl,
    durationSeconds: videoUrl.durationSeconds,
  );
}

/// Silent progress reports are only sent once the position moved this far.
const int playbackProgressSaveIntervalSeconds = 15;

class PlaybackProgressSample {
  const PlaybackProgressSample({
    required this.positionSeconds,
    required this.durationSeconds,
  });

  final int positionSeconds;
  final int durationSeconds;
}

PlaybackProgressSample playbackProgressSample(
  int positionMilliseconds,
  int durationMilliseconds,
  bool completed,
) {
  final int durationSeconds = (math.max(durationMilliseconds, 0) / 1000).ceil();
  final int positionSeconds = completed
      ? durationSeconds
      : math.min(
          math.max(positionMilliseconds, 0) ~/ 1000,
          durationSeconds,
        );
  return PlaybackProgressSample(
    positionSeconds: positionSeconds,
    durationSeconds: durationSeconds,
  );
}

bool shouldPersistPlaybackProgress(
  int positionSeconds,
  int durationSeconds,
  int lastReportedPositionSeconds,
  bool rewardClaimed,
) {
  if (durationSeconds <= 0 || positionSeconds < 0) {
    return false;
  }
  final int safePosition = positionSeconds.clamp(0, durationSeconds);
  final bool completed = safePosition >= durationSeconds;
  if (completed) {
    return !rewardClaimed || safePosition != lastReportedPositionSeconds;
  }
  return (safePosition - lastReportedPositionSeconds).abs() >=
      playbackProgressSaveIntervalSeconds;
}

/// Retry ladder after a failed progress report: 5s -> 15s -> 60s, then 60s.
const List<int> playbackProgressRetryBackoffSeconds = <int>[5, 15, 60];

/// Ceiling for the ladder so a future edit can never exceed five minutes.
const int playbackProgressRetryBackoffMaxSeconds = 5 * 60;

/// Wait time after [failureCount] consecutive failures; 0 means "no wait".
int progressReportBackoffSeconds(int failureCount) {
  if (failureCount <= 0) {
    return 0;
  }
  final int index = math.min(
    failureCount - 1,
    playbackProgressRetryBackoffSeconds.length - 1,
  );
  return math.min(
    playbackProgressRetryBackoffSeconds[index],
    playbackProgressRetryBackoffMaxSeconds,
  );
}
