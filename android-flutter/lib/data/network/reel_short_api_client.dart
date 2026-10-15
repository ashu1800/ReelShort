/// Contract of the ShortLink backend client.
///
/// Port of `com.reelshort.app.network.ReelShortApiClient`. Every method maps to
/// one backend endpoint; the state layer only sees this interface, so tests can
/// substitute a fake without touching HTTP.
library;

import '../models/app_models.dart';

abstract interface class ReelShortApiClient {
  Future<ApiHealthStatus> checkSystemHealth();

  Future<AuthSession> login(String username, String password);

  Future<AuthSession> register(
    String username,
    String password,
    String captchaId,
    String captchaAnswer,
  );

  Future<CaptchaChallenge> fetchCaptcha();

  Future<void> changePassword(String oldPassword, String newPassword);

  Future<List<BookSummary>> getHomeShelf(String locale);

  Future<List<BookSummary>> search(String query, String locale);

  Future<BookSummary> getBook(String bookId, String locale);

  Future<List<EpisodeSummary>> getEpisodes(
    String bookId,
    String filteredTitle,
    String locale,
  );

  Future<VideoUrl> getVideoUrl(
    String bookId,
    int episode,
    String filteredTitle,
    String chapterId,
    String locale,
  );

  Future<WatchEpisodeSnapshot> getEpisodeSnapshot(String bookId, int episode);

  Future<WatchProgressReport> reportWatchProgress({
    required String bookId,
    required String bookTitle,
    required String filteredTitle,
    required int episode,
    required String chapterId,
    required int positionSeconds,
    required int durationSeconds,
  });

  Future<List<WatchRecord>> getWatchHistory();

  Future<PointAccount> getPointAccount();

  Future<List<RechargeOrderSummary>> getOrders();

  Future<WalletInfo> getWallet();

  Future<WalletInfo> bindWallet(
    String network,
    String walletAddress,
    String password,
  );

  Future<WalletInfo> unbindWallet(String password);

  Future<VipOrder> createVipOrder();

  Future<List<VipOrder>> getVipOrders();

  Future<VipOrder?> getLatestVipOrder();

  Future<WithdrawalSummary> getWithdrawalSummary();

  Future<List<WithdrawalRecord>> getWithdrawals();

  Future<WithdrawalRecord> submitWithdrawal(int pointAmount);

  Future<SocialToggleResult> toggleLike(String bookId);

  Future<SocialToggleResult> getLikeStatus(String bookId);

  Future<SocialToggleResult> toggleFavorite({
    required String bookId,
    required String bookTitle,
    required String filteredTitle,
    required String? coverUrl,
    required int chapterCount,
  });

  Future<SocialToggleResult> getFavoriteStatus(String bookId);

  Future<Comment> addComment(String bookId, String content);

  Future<List<Comment>> listComments(String bookId);

  Future<List<BookSummary>> listMyFavorites();
}
