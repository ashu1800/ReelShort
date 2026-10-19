/// Presentation-facing formatting rules shared by every screen.
///
/// Port of `android-app/app/src/main/java/com/reelshort/app/ui/format/UiFormats.kt`.
/// These functions are pure so the ported contract tests assert the same values
/// as the Kotlin originals.
///
/// Two deliberate deviations from the Kotlin file, both recorded in
/// `docs/parity-matrix.md`:
///  * the Media3 integer player state is replaced by [PlayerPhase], which keeps
///    the observable overlay decision but drops the Media3 dependency;
///  * monetary arithmetic uses an exact scaled-decimal helper instead of
///    `BigDecimal`, producing identical strings without a dependency.
library;

import '../../data/models/app_language.dart';
import '../../data/models/app_models.dart';
import '../../state/app_ui_state.dart';
import 'app_strings.dart';

String appBrandName() => 'ShortLink';

extension NullableStringFormats on String? {
  /// Trimmed cover URL, or null when blank.
  String? coverUrlOrNull() {
    final String? value = this?.trim();
    return (value == null || value.isEmpty) ? null : value;
  }

  /// Trimmed media URL restricted to http(s).
  String? playableMediaUrlOrNull() {
    final String? value = this?.trim();
    if (value == null) {
      return null;
    }
    final String lowered = value.toLowerCase();
    if (lowered.startsWith('https://') || lowered.startsWith('http://')) {
      return value;
    }
    return null;
  }
}

/// Milliseconds to whole seconds, clamped at zero.
int mediaPositionSeconds(int positionMs) =>
    (positionMs < 0 ? 0 : positionMs) ~/ 1000;

/// Media3 reports [timeUnset] for "not known yet"; the server value is used then.
const int timeUnset = -9223372036854775808;

int mediaDurationSeconds(int durationMs, int fallbackDurationSeconds) {
  if (durationMs == timeUnset) {
    return fallbackDurationSeconds < 0 ? 0 : fallbackDurationSeconds;
  }
  return (durationMs < 0 ? 0 : durationMs) ~/ 1000;
}

String episodeNumberLabel(
  int number, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  final AppStrings copy = strings(language);
  final int safe = number < 0 ? 0 : number;
  return '${copy.playerEpisodePrefix}'
      '${safe.toString().padLeft(2, '0')}'
      '${copy.playerEpisodeUnit}';
}

String episodeTitle(
  EpisodeSummary episode, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  final String title = episode.title.trim();
  final String label = episodeNumberLabel(episode.number, language);
  return title.isNotEmpty ? '$label · $title' : label;
}

String episodeSubtitle(String episodeDescription, String bookDescription) {
  final String primary = episodeDescription.trim();
  return primary.isNotEmpty ? primary : bookDescription.trim();
}

String episodeRowActionLabel([
  AppLanguage language = AppLanguage.defaultLanguage,
]) => strings(language).playerPlayAction;

/// The player is full-bleed on phones; this ratio is only used by tests and
/// previews, exactly as in the Kotlin source.
double playerSurfaceAspectRatio() => 9 / 16;

bool playerStartsAutomatically() => true;

String playerLoadingLabel(
  int episodeNumber, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  final AppStrings copy = strings(language);
  return '${copy.playerLoadingPrefix}'
      '${episodeNumberLabel(episodeNumber, language)}'
      '${copy.playerLoadingSuffix}';
}

/// Minimal projection of the player state that drives the overlay decision.
enum PlayerPhase { idle, buffering, ready, ended }

enum PlayerOverlayMode { none, coverLoading, buffering, error }

PlayerOverlayMode playerOverlayMode(
  String? playableUrl,
  PlayerPhase phase,
  bool hasFirstReady,
  bool hasError,
) {
  if (hasError) {
    return PlayerOverlayMode.error;
  }
  if (playableUrl == null ||
      phase == PlayerPhase.idle ||
      !hasFirstReady) {
    return PlayerOverlayMode.coverLoading;
  }
  if (phase == PlayerPhase.buffering) {
    return PlayerOverlayMode.buffering;
  }
  return PlayerOverlayMode.none;
}

bool playerLoadingOverlayVisible(
  String? playableUrl,
  PlayerPhase phase,
  bool hasFirstReady,
  bool hasError,
) =>
    playerOverlayMode(playableUrl, phase, hasFirstReady, hasError) !=
    PlayerOverlayMode.none;

/// Next episode by `(number, chapterId)`, or null on the last episode.
EpisodeSummary? playerErrorNextEpisode(
  EpisodeSummary? currentEpisode,
  List<EpisodeSummary> episodes,
) {
  final EpisodeSummary? current = currentEpisode;
  if (current == null) {
    return null;
  }
  final int currentIndex = episodes.indexWhere(
    (EpisodeSummary item) =>
        item.number == current.number && item.chapterId == current.chapterId,
  );
  if (currentIndex < 0 || currentIndex >= episodes.length - 1) {
    return null;
  }
  return episodes[currentIndex + 1];
}

String episodeSelectorLabel(
  int totalEpisodes, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  final AppStrings copy = strings(language);
  final int total = totalEpisodes < 0 ? 0 : totalEpisodes;
  return '${copy.playerEpisodeSelectorPrefix} · '
      '${copy.playerEpisodeSelectorCompleted} · '
      '${copy.playerEpisodeSelectorTotalPrefix}'
      '$total'
      '${copy.playerEpisodeSelectorTotalSuffix}';
}

enum EpisodeWatchStatusType { none, current, inProgress, watched }

class EpisodeWatchStatus {
  const EpisodeWatchStatus(this.type, [this.progressPercent = 0]);

  final EpisodeWatchStatusType type;
  final int progressPercent;
}

EpisodeWatchStatus episodeWatchStatus(
  String? bookId,
  EpisodeSummary episode,
  EpisodeSummary? selectedEpisode,
  List<WatchRecord> watchHistory,
) {
  final WatchRecord? record = watchHistory
      .cast<WatchRecord?>()
      .firstWhere(
        (WatchRecord? item) =>
            item != null &&
            item.bookId == bookId &&
            item.episode == episode.number,
        orElse: () => null,
      );
  final int recordProgress = record == null
      ? 0
      : record.progressPercent.clamp(0, 100);
  final EpisodeSummary? selected = selectedEpisode;
  final bool isSelected =
      selected != null &&
      selected.number == episode.number &&
      selected.chapterId == episode.chapterId;
  if (isSelected) {
    return EpisodeWatchStatus(EpisodeWatchStatusType.current, recordProgress);
  }
  if (recordProgress >= 100) {
    return const EpisodeWatchStatus(EpisodeWatchStatusType.watched, 100);
  }
  if (recordProgress > 0) {
    return EpisodeWatchStatus(
      EpisodeWatchStatusType.inProgress,
      recordProgress,
    );
  }
  return const EpisodeWatchStatus(EpisodeWatchStatusType.none);
}

String episodeWatchStatusLabel(
  EpisodeWatchStatus status, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) => switch (status.type) {
  EpisodeWatchStatusType.current => strings(language).playerEpisodeStatusCurrent,
  EpisodeWatchStatusType.watched => strings(language).playerEpisodeStatusWatched,
  EpisodeWatchStatusType.inProgress =>
    '${status.progressPercent.clamp(0, 100)}%',
  EpisodeWatchStatusType.none => '',
};

List<String> playerSecondaryActionLabels([
  AppLanguage language = AppLanguage.defaultLanguage,
]) => <String>[strings(language).playerRefreshAction];

List<String> guestAccountEntryLabels([
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  final AppStrings copy = strings(language);
  return <String>[copy.accountGuestSignIn, copy.accountGuestRegister];
}

List<AuthMode> guestAccountEntryAuthModes() =>
    <AuthMode>[AuthMode.login, AuthMode.register];

String authPromptTitle(
  bool hasPendingPlayback, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) => hasPendingPlayback
    ? strings(language).authPromptPlayback
    : strings(language).authPromptAccount;

String rememberPasswordLabel([
  AppLanguage language = AppLanguage.defaultLanguage,
]) => strings(language).rememberPassword;

String authCaptchaAnswerLabel([
  AppLanguage language = AppLanguage.defaultLanguage,
]) => strings(language).authCaptchaAnswerLabel;

bool authSinglePrimaryAction() => true;

bool authBottomSheetAvoidsNavigationBar() => true;

class AuthSheetCopy {
  const AuthSheetCopy({
    required this.title,
    required this.subtitle,
    required this.primaryAction,
    required this.secondaryAction,
  });

  final String title;
  final String subtitle;
  final String primaryAction;
  final String secondaryAction;
}

AuthSheetCopy authSheetCopy(
  AuthMode mode,
  bool hasPendingPlayback, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  final AppStrings copy = strings(language);
  return switch (mode) {
    AuthMode.login => AuthSheetCopy(
      title: authPromptTitle(hasPendingPlayback, language),
      subtitle: hasPendingPlayback
          ? copy.authBottomSheetPlaybackSubtitle
          : copy.authBottomSheetAccountSubtitle,
      primaryAction: copy.authLoginAction,
      secondaryAction: copy.authRegisterSecondary,
    ),
    AuthMode.register => AuthSheetCopy(
      title: copy.authRegisterTitle,
      subtitle: copy.authRegisterSubtitle,
      primaryAction: copy.authRegisterAction,
      secondaryAction: copy.authLoginSecondary,
    ),
  };
}

/// Register is only submittable with a username, an 8+ character password that
/// matches its confirmation, and a loaded captcha with an answer.
bool authRegisterEnabled(
  bool isLoading,
  String username,
  String password,
  String confirmPassword,
  String captchaAnswer,
  bool captchaLoaded,
) =>
    !isLoading &&
    username.trim().isNotEmpty &&
    password.length >= 8 &&
    password == confirmPassword &&
    captchaAnswer.trim().isNotEmpty &&
    captchaLoaded;

bool commercialSheetAutoDismissesAfterSubmit() => false;

/// Placeholder shown while the withdrawal amount field is empty and unfocused.
String? withdrawalPointAmountPlaceholder(
  String points,
  bool isFocused,
  AppLanguage language,
) => (points.trim().isEmpty && !isFocused)
    ? strings(language).accountWithdrawArrivalHint
    : null;

List<String> withdrawalConversionLines(
  WithdrawalSummary? summary,
  int pointAmount,
  AppLanguage language,
) {
  final AppStrings copy = strings(language);
  if (summary == null) {
    return <String>[
      '${copy.accountWithdrawAvailableLabel} 0 ${copy.listPointsLabel} · '
          '${copy.accountWithdrawMinimumLabel} 0 ${copy.listPointsLabel} / 0.01 USDT',
    ];
  }
  final _Decimal minimumUsdt =
      _Decimal.tryParsePositive(summary.minimumUsdt) ??
      _Decimal.parse('0.01');
  final List<String> lines = <String>[
    '${copy.accountWithdrawAvailableLabel} ${summary.availablePoints} '
        '${copy.listPointsLabel} · '
        '${copy.accountWithdrawMinimumLabel} ${summary.minimumPoints} '
        '${copy.listPointsLabel} / '
        '${minimumUsdt.toUiDecimal()} USDT',
  ];
  if (pointAmount > 0) {
    // Long arithmetic so a large amount cannot overflow, as in the Kotlin source.
    final int fee = summary.feePercent > 0
        ? ((pointAmount * summary.feePercent) + 99) ~/ 100
        : 0;
    final int withdrawable = pointAmount - fee;
    final _Decimal? usdtPerPoint = _Decimal.tryParsePositive(
      summary.usdtPerPoint,
    );
    final _Decimal? usdtAmount = usdtPerPoint?.multiply(
      _Decimal.fromInt(withdrawable),
    ).setScale(2, _Rounding.down);
    if (usdtAmount != null) {
      lines.add('Estimated ${usdtAmount.toUiDecimal()} USDT');
    }
    if (fee > 0) {
      lines.add(
        'Fee: ${summary.feePercent}% ($fee pts) · You receive: $withdrawable pts '
        '· Total deduct: $pointAmount pts',
      );
    }
  }
  return lines;
}

String withdrawalStatusLabel(String status, AppLanguage language) =>
    switch (status.toUpperCase()) {
      'PENDING' => 'Pending',
      'APPROVED' => 'Completed',
      'REJECTED' => 'Rejected',
      _ => status,
    };

String? withdrawalRecordDetail(
  String status,
  String? adminNote,
  String? txHash,
  AppLanguage language,
) {
  final String upper = status.toUpperCase();
  if (upper == 'REJECTED' && adminNote != null && adminNote.trim().isNotEmpty) {
    return 'Reason: $adminNote';
  }
  if (upper == 'APPROVED' && txHash != null && txHash.trim().isNotEmpty) {
    return 'TX: $txHash';
  }
  return null;
}

enum AccountAction {
  sendVerification,
  walletBindOrReplace,
  walletUnbind,
  pointTransfer,
  withdrawal,
}

bool accountActionRequiresConfirmation(AccountAction action) =>
    action == AccountAction.walletUnbind ||
    action == AccountAction.pointTransfer ||
    action == AccountAction.withdrawal;

bool accountSheetStartsFullyExpanded() => true;

bool accountSheetUsesScrollableContent() => true;

bool accountCardsUseContentDrivenHeight() => true;

int posterOverlayTitleMaxLines(double fontScale) => fontScale >= 1.3 ? 1 : 2;

int responsivePosterMinimumWidthDp() => 136;

String posterCardContentDescription(
  String title,
  int episodeCount,
  AppLanguage language,
) => '$title, $episodeCount${strings(language).posterEpisodeUnit}';

/// The label on the poster's episode-count badge, for example `12 eps`.
///
/// Unlike [posterCardContentDescription] this is not a sentence: it is a compact
/// tag competing with artwork for a corner of the card. A single episode is
/// counted in the singular so "1 eps" never ships.
String episodeCountBadgeLabel(
  int episodeCount, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  if (episodeCount <= 0) {
    return '';
  }
  final AppStrings copy = strings(language);
  return '$episodeCount'
      '${episodeCount == 1 ? copy.posterEpisodeUnitSingular : copy.posterEpisodeUnit}';
}

bool posterImageIsDecorativeInsideCard() => true;

enum MessageVisualTone { success, error, info }

MessageVisualTone messageVisualTone(UiMessageType type) => switch (type) {
  UiMessageType.success => MessageVisualTone.success,
  UiMessageType.error => MessageVisualTone.error,
  UiMessageType.info => MessageVisualTone.info,
};

bool topMessageUsesLiveRegion() => true;

String accountConfirmationTitle(AppLanguage language) => 'Confirm action';

String accountConfirmationBody(String summary, AppLanguage language) =>
    'Please confirm: $summary. This action may not be reversible.';

String accountConfirmationConfirm(AppLanguage language) => 'Confirm';

String accountConfirmationCancel(AppLanguage language) => 'Cancel';

/// Drives the "close the wallet sheet after a successful mutation" rule.
bool walletSheetShouldDismiss(
  bool visible,
  int lastHandledVersion,
  int currentVersion,
) => visible && currentVersion > lastHandledVersion;

extension PosterInitials on String {
  /// First letter of the first two words, uppercased, falling back to `RS`.
  String posterInitials() {
    final List<String> words = trim()
        .split(RegExp(r'\s+'))
        .where((String word) => word.trim().isNotEmpty)
        .take(2)
        .toList(growable: false);
    if (words.isEmpty) {
      return 'RS';
    }
    return words.map((String word) => word[0].toUpperCase()).join();
  }
}

// -----------------------------------------------------------------------------
// Exact decimal arithmetic
// -----------------------------------------------------------------------------

enum _Rounding { down, halfUp }

/// Minimal exact fixed-point decimal.
///
/// The Kotlin source uses `BigDecimal` with `RoundingMode.DOWN` (USDT truncation)
/// and `HALF_UP` (display scale) plus `stripTrailingZeros`. Binary floating point
/// cannot reproduce those strings, so the same operations are implemented over
/// scaled integers.
class _Decimal implements Comparable<_Decimal> {
  const _Decimal(this.unscaled, this.scale);

  final int unscaled;
  final int scale;

  static _Decimal parse(String text) {
    final _Decimal? value = tryParse(text);
    if (value == null) {
      throw FormatException('not a decimal: $text');
    }
    return value;
  }

  static _Decimal fromInt(int value) => _Decimal(value, 0);

  static _Decimal? tryParse(String? text) {
    if (text == null) {
      return null;
    }
    final RegExpMatch? match = RegExp(
      r'^([+-]?)(\d+)(?:\.(\d+))?$',
    ).firstMatch(text.trim());
    if (match == null) {
      return null;
    }
    final String sign = match.group(1) ?? '';
    final String integerPart = match.group(2)!;
    final String fractionPart = match.group(3) ?? '';
    int value = int.parse('$integerPart$fractionPart');
    if (sign == '-') {
      value = -value;
    }
    return _Decimal(value, fractionPart.length);
  }

  /// Parses a decimal that must be strictly positive.
  static _Decimal? tryParsePositive(String? text) {
    final _Decimal? value = tryParse(text);
    if (value == null || value.unscaled <= 0) {
      return null;
    }
    return value;
  }

  _Decimal multiply(_Decimal other) =>
      _Decimal(unscaled * other.unscaled, scale + other.scale);

  _Decimal setScale(int newScale, _Rounding rounding) {
    if (newScale >= scale) {
      return _Decimal(unscaled * _pow10(newScale - scale), newScale);
    }
    final int divisor = _pow10(scale - newScale);
    final int quotient = unscaled ~/ divisor;
    if (rounding == _Rounding.down) {
      return _Decimal(quotient, newScale);
    }
    final int remainder = (unscaled % divisor).abs();
    if (remainder * 2 >= divisor) {
      return _Decimal(
        unscaled.isNegative ? quotient - 1 : quotient + 1,
        newScale,
      );
    }
    return _Decimal(quotient, newScale);
  }

  _Decimal stripTrailingZeros() {
    int value = unscaled;
    int currentScale = scale;
    while (currentScale > 0 && value % 10 == 0) {
      value ~/= 10;
      currentScale--;
    }
    return _Decimal(value, currentScale);
  }

  String toPlainString() {
    final String digits = unscaled.abs().toString();
    if (scale == 0) {
      return unscaled.isNegative ? '-$digits' : digits;
    }
    final String padded = digits.padLeft(scale + 1, '0');
    final String integerPart = padded.substring(0, padded.length - scale);
    final String fractionPart = padded.substring(padded.length - scale);
    return '${unscaled.isNegative ? '-' : ''}$integerPart.$fractionPart';
  }

  /// `setScale(6, HALF_UP).stripTrailingZeros().toPlainString()`.
  String toUiDecimal() =>
      setScale(6, _Rounding.halfUp).stripTrailingZeros().toPlainString();

  static int _pow10(int exponent) {
    int result = 1;
    for (int index = 0; index < exponent; index++) {
      result *= 10;
    }
    return result;
  }

  @override
  int compareTo(_Decimal other) {
    final int commonScale = scale > other.scale ? scale : other.scale;
    return setScale(commonScale, _Rounding.down).unscaled.compareTo(
      other.setScale(commonScale, _Rounding.down).unscaled,
    );
  }

  @override
  String toString() => toPlainString();
}


/// Groups the digits of an integer with thousands separators (`29184` ->
/// `29,184`).
///
/// Balances and point totals are rendered straight from the API integer today;
/// an ungrouped five-digit figure is unreadable at a glance and reads as a raw
/// database value rather than as money.
String groupedDigits(int value) {
  final bool negative = value < 0;
  final String digits = value.abs().toString();
  final StringBuffer buffer = StringBuffer();
  for (int i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 == 0) {
      buffer.write(',');
    }
    buffer.write(digits[i]);
  }
  return negative ? '-$buffer' : buffer.toString();
}

/// Date-only form of an ISO-ish timestamp (`2026-11-13 00:00:00` ->
/// `2026-11-13`).
///
/// A membership expiry is a date, not a clock time: the API timestamp is
/// trimmed to its date part instead of being rendered raw with the midnight
/// component the user never chose.
String dateOnlyLabel(String? value) {
  final String trimmed = value?.trim() ?? '';
  if (trimmed.isEmpty) {
    return '';
  }
  return trimmed.split(RegExp(r'[T ]')).first;
}