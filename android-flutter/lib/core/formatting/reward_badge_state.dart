/// Reward ring/badge projection for the player.
///
/// Port of `android-app/app/src/main/java/com/reelshort/app/ui/format/RewardBadgeState.kt`.
library;

import '../../data/models/app_language.dart';
import '../../data/models/app_models.dart';

enum RewardBadgeVisualState {
  waiting,
  ready,
  reporting,
  completed,
  dailyLimit,
  unavailable,
  error,
}

/// One-shot `+N pt` overlay state.
class RewardAwardToastState {
  const RewardAwardToastState({this.points = 0, this.visible = false});

  final int points;
  final bool visible;

  RewardAwardToastState show(int awardedPoints) => awardedPoints > 0
      ? RewardAwardToastState(points: awardedPoints, visible: true)
      : this;

  RewardAwardToastState hide() =>
      RewardAwardToastState(points: points, visible: false);
}

class RewardBadgeState {
  const RewardBadgeState({
    required this.accessibilityText,
    required this.pointsText,
    required this.pointsFontSizeSp,
    required this.ringProgress,
    required this.visualState,
  });

  final String accessibilityText;
  final String pointsText;
  final double pointsFontSizeSp;
  final double ringProgress;
  final RewardBadgeVisualState visualState;
}

RewardBadgeState rewardBadgeState({
  required int progressPercent,
  required int estimatedRewardPoints,
  required bool isReporting,
  required bool hasError,
  AppLanguage language = AppLanguage.defaultLanguage,
  bool rewardClaimed = false,
  WatchRewardStatus rewardStatus = WatchRewardStatus.notComplete,
  int awardedPoints = 0,
}) {
  final int progress = progressPercent.clamp(0, 100);
  final RewardBadgeVisualState visualState;
  if (hasError) {
    visualState = RewardBadgeVisualState.error;
  } else if (isReporting) {
    visualState = RewardBadgeVisualState.reporting;
  } else if (rewardStatus == WatchRewardStatus.dailyLimitReached) {
    visualState = RewardBadgeVisualState.dailyLimit;
  } else if (rewardStatus == WatchRewardStatus.durationUnavailable) {
    visualState = RewardBadgeVisualState.unavailable;
  } else if (rewardClaimed ||
      rewardStatus == WatchRewardStatus.awarded ||
      rewardStatus == WatchRewardStatus.awardedPartial ||
      rewardStatus == WatchRewardStatus.alreadyClaimed) {
    visualState = RewardBadgeVisualState.completed;
  } else if (progress >= 100) {
    visualState = RewardBadgeVisualState.ready;
  } else {
    visualState = RewardBadgeVisualState.waiting;
  }
  final String accessibilityText = switch (visualState) {
    RewardBadgeVisualState.reporting => rewardBadgeSyncingLabel(language),
    RewardBadgeVisualState.error => rewardBadgeRetryLabel(language),
    RewardBadgeVisualState.ready => rewardBadgeReadyLabel(language),
    RewardBadgeVisualState.completed => rewardBadgeCompleteLabel(language),
    RewardBadgeVisualState.dailyLimit => rewardBadgeDailyLimitLabel(language),
    RewardBadgeVisualState.unavailable => rewardBadgeUnavailableLabel(language),
    RewardBadgeVisualState.waiting => rewardBadgeWaitingLabel(language),
  };
  // After settlement the ring always shows what actually arrived: a daily-limit
  // hit awards 0, and showing the estimate there would contradict `+N pt`.
  final int displayedPoints = rewardClaimed
      ? awardedPoints
      : estimatedRewardPoints;
  final String pointsText = (displayedPoints < 0 ? 0 : displayedPoints)
      .toString();
  return RewardBadgeState(
    accessibilityText: accessibilityText,
    pointsText: pointsText,
    pointsFontSizeSp: switch (pointsText.length) {
      1 || 2 => 12,
      3 => 10,
      _ => 8,
    },
    ringProgress: (progress / 100).clamp(0.0, 1.0),
    visualState: visualState,
  );
}

String rewardBadgeContentDescription(
  RewardBadgeState state,
  AppLanguage language,
) {
  final String unit = state.pointsText == '1' ? 'point' : 'points';
  return '${rewardBadgeInfoTitle(language)}: ${state.accessibilityText}, '
      '${state.pointsText} $unit';
}

String rewardBadgeInfoTitle(AppLanguage language) => 'Reward progress';

String rewardBadgeInfoBody(AppLanguage language) =>
    'Finish the episode to receive points automatically. '
    'If syncing fails, keep watching and we will retry.';

String rewardBadgeAwardLabel(int points, AppLanguage language) =>
    '+${points < 0 ? 0 : points} pt';

String rewardBadgeWaitingLabel(AppLanguage language) =>
    'Finish watching to earn';

String rewardBadgeReadyLabel(AppLanguage language) => 'Episode complete';

String rewardBadgeSyncingLabel(AppLanguage language) => 'Syncing';

String rewardBadgeCompleteLabel(AppLanguage language) => 'Points claimed';

String rewardBadgeDailyLimitLabel(AppLanguage language) => 'Daily limit reached';

String rewardBadgeUnavailableLabel(AppLanguage language) => 'Reward unavailable';

String rewardBadgeRetryLabel(AppLanguage language) => 'Retry later';
