/// Domain models shared by the data, state and presentation layers.
///
/// These are a 1:1 port of `android-app/app-core/src/main/kotlin/com/reelshort/app/data/AppModels.kt`.
/// Field names, optionality and defaults must stay identical: the JSON mapping
/// and the state machine both depend on them.
library;

/// An authenticated session returned by login/register and persisted locally.
class AuthSession {
  const AuthSession({
    required this.username,
    required this.token,
    required this.tokenType,
    this.phoneE164,
  });

  final String username;
  final String token;
  final String tokenType;
  final String? phoneE164;

  AuthSession copyWith({
    String? username,
    String? token,
    String? tokenType,
    String? phoneE164,
  }) => AuthSession(
    username: username ?? this.username,
    token: token ?? this.token,
    tokenType: tokenType ?? this.tokenType,
    phoneE164: phoneE164 ?? this.phoneE164,
  );
}

/// Credentials remembered locally when the user opts in.
class SavedCredentials {
  const SavedCredentials({
    required this.username,
    required this.password,
    required this.rememberPassword,
  });

  final String username;
  final String password;
  final bool rememberPassword;
}

/// Health probe payload of `GET /api/system/health`.
class ApiHealthStatus {
  const ApiHealthStatus({required this.status, this.service});

  final String status;
  final String? service;
}

/// One captcha challenge: the id plus the base64 PNG shown to the user.
class CaptchaChallenge {
  const CaptchaChallenge({required this.captchaId, required this.imageBase64});

  final String captchaId;
  final String imageBase64;
}

/// A drama poster entry, used by the home shelf, search, favorites and history.
class BookSummary {
  const BookSummary({
    required this.id,
    required this.title,
    required this.filteredTitle,
    required this.coverUrl,
    required this.description,
    required this.chapterCount,
  });

  final String id;
  final String title;
  final String filteredTitle;
  final String? coverUrl;
  final String description;
  final int chapterCount;

  BookSummary copyWith({
    String? id,
    String? title,
    String? filteredTitle,
    String? coverUrl,
    String? description,
    int? chapterCount,
  }) => BookSummary(
    id: id ?? this.id,
    title: title ?? this.title,
    filteredTitle: filteredTitle ?? this.filteredTitle,
    coverUrl: coverUrl ?? this.coverUrl,
    description: description ?? this.description,
    chapterCount: chapterCount ?? this.chapterCount,
  );
}

/// One episode of a drama.
class EpisodeSummary {
  const EpisodeSummary({
    required this.number,
    required this.chapterId,
    this.title = '',
    this.description = '',
    this.durationSeconds = 0,
  });

  final int number;
  final String chapterId;
  final String title;
  final String description;
  final int durationSeconds;

  EpisodeSummary copyWith({
    int? number,
    String? chapterId,
    String? title,
    String? description,
    int? durationSeconds,
  }) => EpisodeSummary(
    number: number ?? this.number,
    chapterId: chapterId ?? this.chapterId,
    title: title ?? this.title,
    description: description ?? this.description,
    durationSeconds: durationSeconds ?? this.durationSeconds,
  );
}

/// Fallback free-episode count used only until the account snapshot arrives.
///
/// The real boundary is the backend's `vip.free-episodes` system config, which
/// rides along on `GET /api/app/points/account` as `freeEpisodes` and is applied
/// by the play endpoint. This constant matches its default (7) so a guest, or a
/// client talking to an older backend, behaves exactly as before.
const int defaultVipFreeEpisodes = 7;

/// Whether [episode] sits behind the VIP paywall at [freeEpisodes].
///
/// A pure function rather than an extension method on purpose: the boundary is
/// server configuration, so every caller has to name where its value came from.
bool episodeRequiresVip(EpisodeSummary episode, int freeEpisodes) =>
    episode.number > freeEpisodes;

/// A resolved, playable media URL plus the server-authoritative duration.
class VideoUrl {
  const VideoUrl({
    required this.url,
    required this.contentType,
    required this.episode,
    required this.durationSeconds,
    this.estimatedRewardPoints,
  });

  final String url;
  final String contentType;
  final int episode;
  final int durationSeconds;

  /// Points the backend estimates for this episode, computed live from the
  /// authoritative duration, the current point configuration, the user's
  /// decile mantissa and the remaining daily allowance.
  final int? estimatedRewardPoints;
}

/// Result of a watch-progress report, including the one-shot reward outcome.
class WatchProgressReport {
  const WatchProgressReport({
    required this.bookId,
    required this.bookTitle,
    required this.filteredTitle,
    required this.episode,
    required this.chapterId,
    required this.positionSeconds,
    required this.durationSeconds,
    required this.progressPercent,
    this.rewardClaimed = false,
    this.rewardStatus = WatchRewardStatus.notComplete,
    this.awardedPoints = 0,
  });

  final String bookId;
  final String bookTitle;
  final String filteredTitle;
  final int episode;
  final String chapterId;
  final int positionSeconds;
  final int durationSeconds;
  final int progressPercent;
  final bool rewardClaimed;
  final WatchRewardStatus rewardStatus;
  final int awardedPoints;
}

/// Reward settlement state for one episode.
enum WatchRewardStatus {
  notComplete('NOT_COMPLETE'),
  awarded('AWARDED'),
  awardedPartial('AWARDED_PARTIAL'),
  alreadyClaimed('ALREADY_CLAIMED'),
  dailyLimitReached('DAILY_LIMIT_REACHED'),
  durationUnavailable('DURATION_UNAVAILABLE'),
  unknown('UNKNOWN');

  const WatchRewardStatus(this.wireName);

  /// Exact backend enum name. Kept separate from [name] because the Dart enum
  /// uses camelCase while the API uses SCREAMING_SNAKE_CASE.
  final String wireName;

  /// Maps a backend status string.
  ///
  /// Blank values mean "not complete"; unrecognised values degrade to
  /// [unknown] instead of throwing.
  static WatchRewardStatus fromApi(String? value) {
    final String normalized = (value ?? '').trim().toUpperCase();
    if (normalized.isEmpty) {
      return WatchRewardStatus.notComplete;
    }
    for (final WatchRewardStatus status in WatchRewardStatus.values) {
      if (status.wireName == normalized) {
        return status;
      }
    }
    return WatchRewardStatus.unknown;
  }

  /// Whether the episode reward has already been settled.
  bool isClaimed() =>
      this == WatchRewardStatus.awarded ||
      this == WatchRewardStatus.awardedPartial ||
      this == WatchRewardStatus.alreadyClaimed ||
      this == WatchRewardStatus.dailyLimitReached;
}

/// One entry of the watch history list.
class WatchRecord {
  const WatchRecord({
    required this.bookId,
    required this.bookTitle,
    required this.episode,
    required this.progressPercent,
  });

  final String bookId;
  final String bookTitle;
  final int episode;
  final int progressPercent;
}

/// Per-episode playback snapshot used to resume and to seed the reward badge.
class WatchEpisodeSnapshot {
  const WatchEpisodeSnapshot({
    required this.bookId,
    required this.episode,
    required this.positionSeconds,
    required this.durationSeconds,
    required this.progressPercent,
    required this.awardedStages,
    this.rewardClaimed = false,
    this.rewardStatus = WatchRewardStatus.notComplete,
    this.awardedPoints = 0,
  });

  /// Placeholder used when the snapshot endpoint is unavailable; playback must
  /// continue from position 0 without blocking.
  const WatchEpisodeSnapshot.empty({
    required this.bookId,
    required this.episode,
  }) : positionSeconds = 0,
       durationSeconds = 0,
       progressPercent = 0,
       awardedStages = const <int>[],
       rewardClaimed = false,
       rewardStatus = WatchRewardStatus.notComplete,
       awardedPoints = 0;

  final String bookId;
  final int episode;
  final int positionSeconds;
  final int durationSeconds;
  final int progressPercent;
  final List<int> awardedStages;
  final bool rewardClaimed;
  final WatchRewardStatus rewardStatus;
  final int awardedPoints;

  WatchEpisodeSnapshot copyWith({
    String? bookId,
    int? episode,
    int? positionSeconds,
    int? durationSeconds,
    int? progressPercent,
    List<int>? awardedStages,
    bool? rewardClaimed,
    WatchRewardStatus? rewardStatus,
    int? awardedPoints,
  }) => WatchEpisodeSnapshot(
    bookId: bookId ?? this.bookId,
    episode: episode ?? this.episode,
    positionSeconds: positionSeconds ?? this.positionSeconds,
    durationSeconds: durationSeconds ?? this.durationSeconds,
    progressPercent: progressPercent ?? this.progressPercent,
    awardedStages: awardedStages ?? this.awardedStages,
    rewardClaimed: rewardClaimed ?? this.rewardClaimed,
    rewardStatus: rewardStatus ?? this.rewardStatus,
    awardedPoints: awardedPoints ?? this.awardedPoints,
  );
}

/// Point balance plus the most recent ledger records.
class PointAccount {
  const PointAccount({
    required this.balance,
    required this.records,
    this.frozenPoints = 0,
    int? availablePoints,
    this.vipActive = false,
    this.vipUntil,
    this.todayCreditedPoints = 0,
    this.freeEpisodes = defaultVipFreeEpisodes,
  }) : availablePoints = availablePoints ?? (balance - frozenPoints);

  final int balance;
  final int frozenPoints;

  /// Points that can actually be spent; defaults to `balance - frozenPoints`
  /// exactly like the Kotlin default argument.
  final int availablePoints;

  final List<PointRecord> records;
  final bool vipActive;
  final String? vipUntil;

  /// Positive points credited today, computed by the backend in Asia/Shanghai.
  final int todayCreditedPoints;

  /// Live `vip.free-episodes` from the backend: episodes above this number need
  /// VIP membership. Falls back to [defaultVipFreeEpisodes] when the server did
  /// not send it (older backend), which restores the pre-config behaviour.
  final int freeEpisodes;
}

/// One point ledger row.
class PointRecord {
  const PointRecord({required this.amount, this.reason});

  final int amount;
  final String? reason;
}

/// Legacy recharge order summary (recharge creation is disabled server-side).
class RechargeOrderSummary {
  const RechargeOrderSummary({
    required this.orderNo,
    required this.amountCents,
    required this.pointAmount,
    required this.status,
  });

  final String orderNo;
  final int amountCents;
  final int pointAmount;
  final String status;
}

/// Bound cold wallet plus VIP pricing/collection configuration.
class WalletInfo {
  const WalletInfo({
    required this.network,
    required this.walletAddress,
    required this.updatedAt,
    this.vipUntil,
    this.vipPriceUsdt,
    this.vipCollectionAddress,
  });

  final String network;
  final String? walletAddress;
  final String? updatedAt;
  final String? vipUntil;
  final String? vipPriceUsdt;
  final String? vipCollectionAddress;
}

/// A VIP payment order (USDT transfer with a short-lived address snapshot).
class VipOrder {
  const VipOrder({
    required this.id,
    required this.orderNo,
    required this.usdtAmount,
    required this.status,
    required this.paymentMethod,
    required this.txHash,
    required this.createdAt,
    required this.confirmedAt,
    this.payableAmount,
    this.expiresAt,
    this.receivingNetwork,
    this.receivingWalletAddress,
    this.tokenContractAddress,
    this.baseUsdtAmount,
  });

  final String id;
  final String orderNo;
  final String usdtAmount;
  final String? payableAmount;
  final String status;
  final String paymentMethod;
  final String? txHash;
  final String createdAt;
  final String? confirmedAt;
  final String? expiresAt;
  final String? receivingNetwork;
  final String? receivingWalletAddress;
  final String? tokenContractAddress;
  final String? baseUsdtAmount;

  /// Amount the user must actually pay: the payable snapshot wins over the
  /// base amount.
  String get effectiveAmount => payableAmount ?? usdtAmount;
}

/// Withdrawal configuration plus the current balance snapshot.
class WithdrawalSummary {
  const WithdrawalSummary({
    required this.balance,
    required this.frozenPoints,
    required this.availablePoints,
    required this.minimumPoints,
    required this.usdtPerPoint,
    required this.usdtPer50Points,
    required this.minimumUsdt,
    required this.walletAddress,
    this.feePercent = 0,
  });

  final int balance;
  final int frozenPoints;
  final int availablePoints;
  final int minimumPoints;
  final String usdtPerPoint;
  final String usdtPer50Points;
  final String minimumUsdt;
  final String? walletAddress;
  final int feePercent;
}

/// One withdrawal request, with its review outcome when available.
class WithdrawalRecord {
  const WithdrawalRecord({
    required this.id,
    required this.pointAmount,
    required this.usdtAmount,
    required this.usdtPerPoint,
    required this.network,
    required this.walletAddress,
    required this.status,
    required this.txHash,
    required this.adminNote,
    required this.createdAt,
    required this.reviewedAt,
  });

  final String id;
  final int pointAmount;
  final String usdtAmount;
  final String usdtPerPoint;
  final String network;
  final String walletAddress;
  final String status;
  final String? txHash;
  final String? adminNote;
  final String createdAt;
  final String? reviewedAt;
}

/// A single-level text comment.
class Comment {
  const Comment({
    required this.id,
    required this.username,
    required this.content,
    required this.createdAt,
  });

  final String id;
  final String username;
  final String content;
  final String createdAt;
}

/// Like/favorite state and counters for the currently opened drama.
class BookInteractionState {
  const BookInteractionState({
    this.liked = false,
    this.likeCount = 0,
    this.favorited = false,
    this.favoriteCount = 0,
  });

  final bool liked;
  final int likeCount;
  final bool favorited;
  final int favoriteCount;

  BookInteractionState copyWith({
    bool? liked,
    int? likeCount,
    bool? favorited,
    int? favoriteCount,
  }) => BookInteractionState(
    liked: liked ?? this.liked,
    likeCount: likeCount ?? this.likeCount,
    favorited: favorited ?? this.favorited,
    favoriteCount: favoriteCount ?? this.favoriteCount,
  );
}

/// Result of a like/favorite toggle.
class SocialToggleResult {
  const SocialToggleResult({required this.active, required this.count});

  final bool active;
  final int count;
}
