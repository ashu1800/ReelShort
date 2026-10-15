/// Data boundary consumed by the state layer.
///
/// Port of `com.reelshort.app.data.AppDataSource`. The state controller depends
/// only on this interface, so tests substitute a fake without HTTP or storage.
library;

import 'models/app_language.dart';
import 'models/app_models.dart';

abstract interface class AppDataSource {
  String get apiBaseUrl;

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

  Future<List<BookSummary>> loadHomeShelf();

  Future<List<BookSummary>> loadCachedHomeShelf();

  Future<void> saveCachedHomeShelf(List<BookSummary> shelf);

  Future<List<BookSummary>> search(String query);

  Future<BookSummary> loadBook(String bookId);

  Future<List<EpisodeSummary>> loadEpisodes(BookSummary book);

  Future<VideoUrl> loadVideoUrl(BookSummary book, EpisodeSummary episode);

  Future<WatchEpisodeSnapshot> loadEpisodeSnapshot(
    BookSummary book,
    EpisodeSummary episode,
  );

  Future<WatchProgressReport> reportWatchProgress(
    BookSummary book,
    EpisodeSummary episode,
    int positionSeconds,
    int durationSeconds,
  );

  Future<List<WatchRecord>> loadWatchHistory();

  Future<PointAccount> loadPointAccount();

  Future<List<RechargeOrderSummary>> loadOrders();

  Future<WalletInfo> loadWallet();

  Future<WalletInfo> bindWallet(
    String network,
    String walletAddress,
    String password,
  );

  Future<WalletInfo> unbindWallet(String password);

  Future<VipOrder> createVipOrder();

  Future<List<VipOrder>> loadVipOrders();

  Future<VipOrder?> loadLatestVipOrder();

  Future<WithdrawalSummary> loadWithdrawalSummary();

  Future<List<WithdrawalRecord>> loadWithdrawals();

  Future<WithdrawalRecord> submitWithdrawal(int pointAmount);

  Future<SocialToggleResult> toggleLike(BookSummary book);

  Future<SocialToggleResult> loadLikeStatus(BookSummary book);

  Future<SocialToggleResult> toggleFavorite(BookSummary book);

  Future<SocialToggleResult> loadFavoriteStatus(BookSummary book);

  Future<Comment> addComment(BookSummary book, String content);

  Future<List<Comment>> listComments(BookSummary book);

  Future<List<BookSummary>> loadMyFavorites();

  Future<AuthSession?> restoreSession();

  Future<void> clearSession();

  Future<SavedCredentials?> loadSavedCredentials();

  Future<void> saveCredentials(SavedCredentials credentials);

  Future<void> clearSavedCredentials();

  Future<AppLanguage> loadLanguagePreference();

  Future<void> saveLanguagePreference(AppLanguage language);
}
