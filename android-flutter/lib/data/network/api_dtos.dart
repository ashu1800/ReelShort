/// Wire data transfer objects and their mapping to domain models.
///
/// Port of `android-app/app-core/src/main/kotlin/com/reelshort/app/network/dto/AppApiDtos.kt`
/// plus the private `toDomain()` mappings of `OkHttpReelShortApiClient`. JSON
/// keys, optionality and defaults are identical to the Kotlin declarations.
library;

import '../models/app_models.dart';
import 'json_read.dart';

/// The backend envelope: `{code, message, data}`.
class BackendApiResponse {
  const BackendApiResponse({
    required this.code,
    required this.message,
    this.data,
  });

  final int code;
  final String message;
  final Object? data;

  static BackendApiResponse fromJson(Map<String, dynamic> json) =>
      BackendApiResponse(
        code: readInt(json, 'code'),
        message: readString(json, 'message'),
        data: json['data'],
      );
}

class AuthRequestDto {
  const AuthRequestDto({required this.username, required this.password});

  final String username;
  final String password;

  Map<String, dynamic> toJson() => <String, dynamic>{
    'username': username,
    'password': password,
  };
}

class RegisterRequestDto {
  const RegisterRequestDto({
    required this.username,
    required this.password,
    required this.captchaId,
    required this.captchaAnswer,
  });

  final String username;
  final String password;
  final String captchaId;
  final String captchaAnswer;

  Map<String, dynamic> toJson() => <String, dynamic>{
    'username': username,
    'password': password,
    'captchaId': captchaId,
    'captchaAnswer': captchaAnswer,
  };
}

class AuthSessionDto {
  const AuthSessionDto({
    required this.username,
    required this.token,
    required this.tokenType,
    this.userId,
    this.phoneE164,
  });

  final String username;
  final String token;
  final String tokenType;
  final String? userId;
  final String? phoneE164;

  static AuthSessionDto fromJson(Map<String, dynamic> json) => AuthSessionDto(
    username: readString(json, 'username'),
    token: readString(json, 'token'),
    tokenType: readString(json, 'tokenType'),
    userId: readStringOrNull(json, 'userId'),
    phoneE164: readStringOrNull(json, 'phoneE164'),
  );

  AuthSession toDomain() => AuthSession(
    username: username,
    token: token,
    tokenType: tokenType,
    phoneE164: phoneE164,
  );
}

class CaptchaResponseDto {
  const CaptchaResponseDto({
    required this.captchaId,
    required this.imageBase64,
  });

  final String captchaId;
  final String imageBase64;

  static CaptchaResponseDto fromJson(Map<String, dynamic> json) =>
      CaptchaResponseDto(
        captchaId: readString(json, 'captchaId'),
        imageBase64: readString(json, 'imageBase64'),
      );

  CaptchaChallenge toDomain() =>
      CaptchaChallenge(captchaId: captchaId, imageBase64: imageBase64);
}

class PasswordChangeRequestDto {
  const PasswordChangeRequestDto({
    required this.oldPassword,
    required this.newPassword,
  });

  final String oldPassword;
  final String newPassword;

  Map<String, dynamic> toJson() => <String, dynamic>{
    'oldPassword': oldPassword,
    'newPassword': newPassword,
  };
}

class ApiHealthStatusDto {
  const ApiHealthStatusDto({required this.status, this.service});

  final String status;
  final String? service;

  static ApiHealthStatusDto fromJson(Map<String, dynamic> json) =>
      ApiHealthStatusDto(
        status: readString(json, 'status'),
        service: readStringOrNull(json, 'service'),
      );

  ApiHealthStatus toDomain() => ApiHealthStatus(status: status, service: service);
}

class ContentBookDto {
  const ContentBookDto({
    required this.bookId,
    required this.title,
    required this.filteredTitle,
    required this.chapterCount,
    this.coverUrl,
    this.description = '',
  });

  final String bookId;
  final String title;
  final String filteredTitle;
  final String? coverUrl;
  final String description;
  final int chapterCount;

  static ContentBookDto fromJson(Map<String, dynamic> json) => ContentBookDto(
    bookId: readString(json, 'bookId'),
    title: readString(json, 'title'),
    filteredTitle: readString(json, 'filteredTitle'),
    coverUrl: readStringOrNull(json, 'coverUrl'),
    description: readStringOrDefault(json, 'description'),
    chapterCount: readInt(json, 'chapterCount'),
  );

  BookSummary toDomain() => BookSummary(
    id: bookId,
    title: title,
    filteredTitle: filteredTitle,
    coverUrl: coverUrl,
    description: description,
    chapterCount: chapterCount,
  );
}

class ContentEpisodeDto {
  const ContentEpisodeDto({
    required this.episode,
    required this.chapterId,
    this.title = '',
    this.description = '',
  });

  final int episode;
  final String chapterId;
  final String title;
  final String description;

  static ContentEpisodeDto fromJson(Map<String, dynamic> json) =>
      ContentEpisodeDto(
        episode: readInt(json, 'episode'),
        chapterId: readString(json, 'chapterId'),
        title: readStringOrDefault(json, 'title'),
        description: readStringOrDefault(json, 'description'),
      );

  EpisodeSummary toDomain() => EpisodeSummary(
    number: episode,
    chapterId: chapterId,
    title: title,
    description: description,
  );
}

class ContentVideoDto {
  const ContentVideoDto({
    required this.videoUrl,
    required this.episode,
    required this.duration,
    this.estimatedRewardPoints,
    this.nextEpisode,
  });

  final String videoUrl;
  final int episode;
  final int duration;
  final int? estimatedRewardPoints;
  final ContentEpisodeDto? nextEpisode;

  static ContentVideoDto fromJson(Map<String, dynamic> json) {
    final Object? next = json['nextEpisode'];
    return ContentVideoDto(
      videoUrl: readString(json, 'videoUrl'),
      episode: readInt(json, 'episode'),
      duration: readInt(json, 'duration'),
      estimatedRewardPoints: readIntOrNull(json, 'estimatedRewardPoints'),
      nextEpisode: next == null
          ? null
          : ContentEpisodeDto.fromJson(asJsonObject(next, 'nextEpisode')),
    );
  }

  /// The backend always serves HLS; the content type is a client constant.
  VideoUrl toDomain() => VideoUrl(
    url: videoUrl,
    contentType: 'application/vnd.apple.mpegurl',
    episode: episode,
    durationSeconds: duration,
    estimatedRewardPoints: estimatedRewardPoints,
  );
}

class WatchProgressRequestDto {
  const WatchProgressRequestDto({
    required this.bookId,
    required this.bookTitle,
    required this.filteredTitle,
    required this.episodeNum,
    required this.chapterId,
    required this.positionSeconds,
    required this.durationSeconds,
  });

  final String bookId;
  final String bookTitle;
  final String filteredTitle;
  final int episodeNum;
  final String chapterId;
  final int positionSeconds;
  final int durationSeconds;

  Map<String, dynamic> toJson() => <String, dynamic>{
    'bookId': bookId,
    'bookTitle': bookTitle,
    'filteredTitle': filteredTitle,
    'episodeNum': episodeNum,
    'chapterId': chapterId,
    'positionSeconds': positionSeconds,
    'durationSeconds': durationSeconds,
  };
}

class WatchRecordDto {
  const WatchRecordDto({
    required this.bookId,
    required this.bookTitle,
    required this.filteredTitle,
    required this.episodeNum,
    required this.chapterId,
    required this.positionSeconds,
    required this.durationSeconds,
    required this.progressPercent,
    this.rewardClaimed = false,
    this.rewardStatus,
    this.awardedPoints = 0,
  });

  final String bookId;
  final String bookTitle;
  final String filteredTitle;
  final int episodeNum;
  final String chapterId;
  final int positionSeconds;
  final int durationSeconds;
  final int progressPercent;
  final bool rewardClaimed;
  final String? rewardStatus;
  final int awardedPoints;

  static WatchRecordDto fromJson(Map<String, dynamic> json) => WatchRecordDto(
    bookId: readString(json, 'bookId'),
    bookTitle: readString(json, 'bookTitle'),
    filteredTitle: readString(json, 'filteredTitle'),
    episodeNum: readInt(json, 'episodeNum'),
    chapterId: readString(json, 'chapterId'),
    positionSeconds: readInt(json, 'positionSeconds'),
    durationSeconds: readInt(json, 'durationSeconds'),
    progressPercent: readInt(json, 'progressPercent'),
    rewardClaimed: readBoolOrDefault(json, 'rewardClaimed', false),
    rewardStatus: readStringOrNull(json, 'rewardStatus'),
    awardedPoints: readIntOrDefault(json, 'awardedPoints', 0),
  );

  WatchRecord toWatchRecord() => WatchRecord(
    bookId: bookId,
    bookTitle: bookTitle,
    episode: episodeNum,
    progressPercent: progressPercent,
  );
}

class WatchEpisodeSnapshotDto {
  const WatchEpisodeSnapshotDto({
    required this.bookId,
    required this.episodeNum,
    required this.positionSeconds,
    required this.durationSeconds,
    required this.progressPercent,
    this.awardedStages = const <int>[],
    this.rewardClaimed = false,
    this.rewardStatus,
    this.awardedPoints = 0,
  });

  final String bookId;
  final int episodeNum;
  final int positionSeconds;
  final int durationSeconds;
  final int progressPercent;
  final List<int> awardedStages;
  final bool rewardClaimed;
  final String? rewardStatus;
  final int awardedPoints;

  static WatchEpisodeSnapshotDto fromJson(Map<String, dynamic> json) =>
      WatchEpisodeSnapshotDto(
        bookId: readString(json, 'bookId'),
        episodeNum: readInt(json, 'episodeNum'),
        positionSeconds: readInt(json, 'positionSeconds'),
        durationSeconds: readInt(json, 'durationSeconds'),
        progressPercent: readInt(json, 'progressPercent'),
        awardedStages: readIntListOrDefault(json, 'awardedStages'),
        rewardClaimed: readBoolOrDefault(json, 'rewardClaimed', false),
        rewardStatus: readStringOrNull(json, 'rewardStatus'),
        awardedPoints: readIntOrDefault(json, 'awardedPoints', 0),
      );
}

class PointAccountDto {
  const PointAccountDto({
    required this.balance,
    required this.availablePoints,
    this.frozenPoints = 0,
    this.vip = false,
    this.vipUntil,
    this.todayCreditedPoints = 0,
  });

  final int balance;
  final int frozenPoints;

  /// Backend-computed spendable amount; the DTO default mirrors the Kotlin
  /// `availablePoints = balance - frozenPoints`.
  final int availablePoints;
  final bool vip;
  final String? vipUntil;
  final int todayCreditedPoints;

  static PointAccountDto fromJson(Map<String, dynamic> json) {
    final int balance = readInt(json, 'balance');
    final int frozenPoints = readIntOrDefault(json, 'frozenPoints', 0);
    return PointAccountDto(
      balance: balance,
      frozenPoints: frozenPoints,
      availablePoints: readIntOrDefault(
        json,
        'availablePoints',
        balance - frozenPoints,
      ),
      vip: readBoolOrDefault(json, 'vip', false),
      vipUntil: readStringOrNull(json, 'vipUntil'),
      todayCreditedPoints: readIntOrDefault(json, 'todayCreditedPoints', 0),
    );
  }
}

class PointRecordDto {
  const PointRecordDto({required this.amount, this.reason});

  final int amount;
  final String? reason;

  static PointRecordDto fromJson(Map<String, dynamic> json) => PointRecordDto(
    amount: readInt(json, 'amount'),
    reason: readStringOrNull(json, 'reason'),
  );

  PointRecord toDomain() => PointRecord(amount: amount, reason: reason);
}

class RechargeOrderDto {
  const RechargeOrderDto({
    required this.orderNo,
    required this.amountCents,
    required this.pointAmount,
    required this.status,
  });

  final String orderNo;
  final int amountCents;
  final int pointAmount;
  final String status;

  static RechargeOrderDto fromJson(Map<String, dynamic> json) =>
      RechargeOrderDto(
        orderNo: readString(json, 'orderNo'),
        amountCents: readInt(json, 'amountCents'),
        pointAmount: readInt(json, 'pointAmount'),
        status: readString(json, 'status'),
      );

  RechargeOrderSummary toDomain() => RechargeOrderSummary(
    orderNo: orderNo,
    amountCents: amountCents,
    pointAmount: pointAmount,
    status: status,
  );
}

class WalletResponseDto {
  const WalletResponseDto({
    required this.network,
    this.walletAddress,
    this.updatedAt,
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

  static WalletResponseDto fromJson(Map<String, dynamic> json) =>
      WalletResponseDto(
        network: readString(json, 'network'),
        walletAddress: readStringOrNull(json, 'walletAddress'),
        updatedAt: readStringOrNull(json, 'updatedAt'),
        vipUntil: readStringOrNull(json, 'vipUntil'),
        vipPriceUsdt: readStringOrNull(json, 'vipPriceUsdt'),
        vipCollectionAddress: readStringOrNull(json, 'vipCollectionAddress'),
      );

  WalletInfo toDomain() => WalletInfo(
    network: network,
    walletAddress: walletAddress,
    updatedAt: updatedAt,
    vipUntil: vipUntil,
    vipPriceUsdt: vipPriceUsdt,
    vipCollectionAddress: vipCollectionAddress,
  );
}

class WalletBindRequestDto {
  const WalletBindRequestDto({
    required this.network,
    required this.walletAddress,
    required this.password,
  });

  final String network;
  final String walletAddress;
  final String password;

  Map<String, dynamic> toJson() => <String, dynamic>{
    'network': network,
    'walletAddress': walletAddress,
    'password': password,
  };
}

class WalletUnbindRequestDto {
  const WalletUnbindRequestDto({required this.password});

  final String password;

  Map<String, dynamic> toJson() => <String, dynamic>{'password': password};
}

class VipOrderDto {
  const VipOrderDto({
    required this.id,
    required this.orderNo,
    required this.usdtAmount,
    required this.status,
    required this.paymentMethod,
    required this.createdAt,
    this.payableAmount,
    this.txHash,
    this.confirmedAt,
    this.expiresAt,
    this.receivingNetwork,
    this.receivingAddress,
    this.tokenContract,
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
  final String? receivingAddress;
  final String? tokenContract;

  static VipOrderDto fromJson(Map<String, dynamic> json) => VipOrderDto(
    id: readString(json, 'id'),
    orderNo: readString(json, 'orderNo'),
    usdtAmount: readString(json, 'usdtAmount'),
    payableAmount: readStringOrNull(json, 'payableAmount'),
    status: readString(json, 'status'),
    paymentMethod: readString(json, 'paymentMethod'),
    txHash: readStringOrNull(json, 'txHash'),
    createdAt: readString(json, 'createdAt'),
    confirmedAt: readStringOrNull(json, 'confirmedAt'),
    expiresAt: readStringOrNull(json, 'expiresAt'),
    receivingNetwork: readStringOrNull(json, 'receivingNetwork'),
    receivingAddress: readStringOrNull(json, 'receivingAddress'),
    tokenContract: readStringOrNull(json, 'tokenContract'),
  );

  VipOrder toDomain() => VipOrder(
    id: id,
    orderNo: orderNo,
    usdtAmount: usdtAmount,
    payableAmount: payableAmount,
    status: status,
    paymentMethod: paymentMethod,
    txHash: txHash,
    createdAt: createdAt,
    confirmedAt: confirmedAt,
    expiresAt: expiresAt,
    receivingNetwork: receivingNetwork,
    receivingWalletAddress: receivingAddress,
    tokenContractAddress: tokenContract,
    baseUsdtAmount: usdtAmount,
  );
}

class WithdrawalSummaryDto {
  const WithdrawalSummaryDto({
    required this.balance,
    required this.frozenPoints,
    required this.availablePoints,
    required this.minimumPoints,
    required this.usdtPerPoint,
    required this.usdtPer50Points,
    required this.minimumUsdt,
    this.walletAddress,
    this.feePercent,
  });

  final int balance;
  final int frozenPoints;
  final int availablePoints;
  final int minimumPoints;
  final String usdtPerPoint;
  final String usdtPer50Points;
  final String minimumUsdt;
  final String? walletAddress;
  final int? feePercent;

  static WithdrawalSummaryDto fromJson(Map<String, dynamic> json) =>
      WithdrawalSummaryDto(
        balance: readInt(json, 'balance'),
        frozenPoints: readInt(json, 'frozenPoints'),
        availablePoints: readInt(json, 'availablePoints'),
        minimumPoints: readInt(json, 'minimumPoints'),
        usdtPerPoint: readString(json, 'usdtPerPoint'),
        usdtPer50Points: readString(json, 'usdtPer50Points'),
        minimumUsdt: readString(json, 'minimumUsdt'),
        walletAddress: readStringOrNull(json, 'walletAddress'),
        feePercent: readIntOrNull(json, 'feePercent'),
      );

  WithdrawalSummary toDomain() => WithdrawalSummary(
    balance: balance,
    frozenPoints: frozenPoints,
    availablePoints: availablePoints,
    minimumPoints: minimumPoints,
    usdtPerPoint: usdtPerPoint,
    usdtPer50Points: usdtPer50Points,
    minimumUsdt: minimumUsdt,
    walletAddress: walletAddress,
    feePercent: feePercent ?? 0,
  );
}

class WithdrawalDto {
  const WithdrawalDto({
    required this.id,
    required this.pointAmount,
    required this.usdtAmount,
    required this.usdtPerPoint,
    required this.network,
    required this.walletAddress,
    required this.status,
    required this.createdAt,
    this.txHash,
    this.adminNote,
    this.reviewedAt,
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

  static WithdrawalDto fromJson(Map<String, dynamic> json) => WithdrawalDto(
    id: readString(json, 'id'),
    pointAmount: readInt(json, 'pointAmount'),
    usdtAmount: readString(json, 'usdtAmount'),
    usdtPerPoint: readString(json, 'usdtPerPoint'),
    network: readString(json, 'network'),
    walletAddress: readString(json, 'walletAddress'),
    status: readString(json, 'status'),
    txHash: readStringOrNull(json, 'txHash'),
    adminNote: readStringOrNull(json, 'adminNote'),
    createdAt: readString(json, 'createdAt'),
    reviewedAt: readStringOrNull(json, 'reviewedAt'),
  );

  WithdrawalRecord toDomain() => WithdrawalRecord(
    id: id,
    pointAmount: pointAmount,
    usdtAmount: usdtAmount,
    usdtPerPoint: usdtPerPoint,
    network: network,
    walletAddress: walletAddress,
    status: status,
    txHash: txHash,
    adminNote: adminNote,
    createdAt: createdAt,
    reviewedAt: reviewedAt,
  );
}

class WithdrawalCreateRequestDto {
  const WithdrawalCreateRequestDto({required this.pointAmount});

  final int pointAmount;

  Map<String, dynamic> toJson() => <String, dynamic>{
    'pointAmount': pointAmount,
  };
}

class SocialToggleDto {
  const SocialToggleDto({required this.active, required this.count});

  final bool active;
  final int count;

  static SocialToggleDto fromJson(Map<String, dynamic> json) => SocialToggleDto(
    active: readBoolOrDefault(json, 'active', false),
    count: readInt(json, 'count'),
  );

  SocialToggleResult toDomain() =>
      SocialToggleResult(active: active, count: count);
}

class FavoriteRequestDto {
  const FavoriteRequestDto({
    required this.bookTitle,
    required this.filteredTitle,
    required this.chapterCount,
    this.coverUrl,
  });

  final String bookTitle;
  final String filteredTitle;
  final String? coverUrl;
  final int chapterCount;

  Map<String, dynamic> toJson() => <String, dynamic>{
    'bookTitle': bookTitle,
    'filteredTitle': filteredTitle,
    'coverUrl': coverUrl,
    'chapterCount': chapterCount,
  };
}

class CommentRequestDto {
  const CommentRequestDto({required this.content});

  final String content;

  Map<String, dynamic> toJson() => <String, dynamic>{'content': content};
}

class CommentDto {
  const CommentDto({
    required this.id,
    required this.username,
    required this.content,
    required this.createdAt,
  });

  final String id;
  final String username;
  final String content;
  final String createdAt;

  static CommentDto fromJson(Map<String, dynamic> json) => CommentDto(
    id: readString(json, 'id'),
    username: readString(json, 'username'),
    content: readString(json, 'content'),
    createdAt: readString(json, 'createdAt'),
  );

  Comment toDomain() =>
      Comment(id: id, username: username, content: content, createdAt: createdAt);
}

/// Paged comment response.
///
/// The backend returns `{comments, hasMore, page, size}`; the client only uses
/// [comments] because pagination is not wired up yet (same as the Kotlin client).
class CommentPageDto {
  const CommentPageDto({
    required this.comments,
    this.hasMore = false,
    this.page = 0,
    this.size = 20,
  });

  final List<CommentDto> comments;
  final bool hasMore;
  final int page;
  final int size;

  static CommentPageDto fromJson(Map<String, dynamic> json) {
    final List<Object?> raw = asJsonArray(json['comments'], 'comments');
    return CommentPageDto(
      comments: raw
          .map(
            (Object? item) =>
                CommentDto.fromJson(asJsonObject(item, 'comment')),
          )
          .toList(growable: false),
      hasMore: readBoolOrDefault(json, 'hasMore', false),
      page: readIntOrDefault(json, 'page', 0),
      size: readIntOrDefault(json, 'size', 20),
    );
  }
}

class FavoriteBookDto {
  const FavoriteBookDto({
    required this.bookId,
    required this.bookTitle,
    required this.filteredTitle,
    required this.chapterCount,
    required this.createdAt,
    this.coverUrl,
  });

  final String bookId;
  final String bookTitle;
  final String filteredTitle;
  final String? coverUrl;
  final int chapterCount;
  final String createdAt;

  static FavoriteBookDto fromJson(Map<String, dynamic> json) => FavoriteBookDto(
    bookId: readString(json, 'bookId'),
    bookTitle: readString(json, 'bookTitle'),
    filteredTitle: readString(json, 'filteredTitle'),
    coverUrl: readStringOrNull(json, 'coverUrl'),
    chapterCount: readInt(json, 'chapterCount'),
    createdAt: readString(json, 'createdAt'),
  );

  /// Favorites carry no description; the original client passes an empty one.
  BookSummary toDomain() => BookSummary(
    id: bookId,
    title: bookTitle,
    filteredTitle: filteredTitle,
    coverUrl: coverUrl,
    description: '',
    chapterCount: chapterCount,
  );
}

/// Maps a reward status, treating "100% but no explicit status" as already
/// claimed.
WatchRewardStatus rewardStatusFromApi(String? value, int progressPercent) {
  if ((value == null || value.trim().isEmpty) && progressPercent >= 100) {
    return WatchRewardStatus.alreadyClaimed;
  }
  return WatchRewardStatus.fromApi(value);
}

extension WatchRecordDtoMapping on WatchRecordDto {
  WatchProgressReport toProgressReport() {
    final WatchRewardStatus mappedStatus = rewardStatusFromApi(
      rewardStatus,
      progressPercent,
    );
    return WatchProgressReport(
      bookId: bookId,
      bookTitle: bookTitle,
      filteredTitle: filteredTitle,
      episode: episodeNum,
      chapterId: chapterId,
      positionSeconds: positionSeconds,
      durationSeconds: durationSeconds,
      progressPercent: progressPercent,
      rewardClaimed: rewardClaimed || mappedStatus.isClaimed(),
      rewardStatus: mappedStatus,
      awardedPoints: awardedPoints,
    );
  }
}

extension WatchEpisodeSnapshotDtoMapping on WatchEpisodeSnapshotDto {
  WatchEpisodeSnapshot toDomain() {
    final WatchRewardStatus mappedStatus = rewardStatusFromApi(
      rewardStatus,
      progressPercent,
    );
    return WatchEpisodeSnapshot(
      bookId: bookId,
      episode: episodeNum,
      positionSeconds: positionSeconds,
      durationSeconds: durationSeconds,
      progressPercent: progressPercent,
      awardedStages: awardedStages,
      rewardClaimed: rewardClaimed || mappedStatus.isClaimed(),
      rewardStatus: mappedStatus,
      awardedPoints: awardedPoints,
    );
  }
}
