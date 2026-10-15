/// Default [AppDataSource]: API client plus persistence boundaries.
///
/// Port of `com.reelshort.app.data.AppRepository`.
library;

import 'app_data_source.dart';
import 'models/app_language.dart';
import 'models/app_models.dart';
import 'network/reel_short_api_client.dart';
import 'sources/credential_store.dart';
import 'sources/home_shelf_store.dart';
import 'sources/language_preference_store.dart';
import 'sources/session_store.dart';

class AppRepository implements AppDataSource {
  AppRepository({
    required this.apiClient,
    SessionStore? sessionStore,
    CredentialStore? credentialStore,
    HomeShelfStore? homeShelfStore,
    LanguagePreferenceStore? languagePreferenceStore,
    this.apiBaseUrl = '',
  }) : _sessionStore = sessionStore ?? InMemorySessionStore(),
       _credentialStore = credentialStore ?? InMemoryCredentialStore(),
       _homeShelfStore = homeShelfStore ?? InMemoryHomeShelfStore(),
       _languagePreferenceStore =
           languagePreferenceStore ?? InMemoryLanguagePreferenceStore();

  final ReelShortApiClient apiClient;
  final SessionStore _sessionStore;
  final CredentialStore _credentialStore;
  final HomeShelfStore _homeShelfStore;
  final LanguagePreferenceStore _languagePreferenceStore;

  @override
  final String apiBaseUrl;

  /// Bearer token handed to the API client; updated on login/register/restore.
  String? currentToken;

  @override
  Future<ApiHealthStatus> checkSystemHealth() =>
      apiClient.checkSystemHealth();

  @override
  Future<AuthSession> login(String username, String password) async {
    final AuthSession session = await apiClient.login(username, password);
    await _sessionStore.saveSession(session);
    currentToken = session.token;
    return session;
  }

  @override
  Future<AuthSession> register(
    String username,
    String password,
    String captchaId,
    String captchaAnswer,
  ) async {
    final AuthSession session = await apiClient.register(
      username,
      password,
      captchaId,
      captchaAnswer,
    );
    await _sessionStore.saveSession(session);
    currentToken = session.token;
    return session;
  }

  @override
  Future<CaptchaChallenge> fetchCaptcha() => apiClient.fetchCaptcha();

  @override
  Future<void> changePassword(String oldPassword, String newPassword) =>
      apiClient.changePassword(oldPassword, newPassword);

  @override
  Future<List<BookSummary>> loadHomeShelf() async =>
      apiClient.getHomeShelf((await loadLanguagePreference()).locale);

  @override
  Future<List<BookSummary>> loadCachedHomeShelf() =>
      _homeShelfStore.loadHomeShelf();

  @override
  Future<void> saveCachedHomeShelf(List<BookSummary> shelf) =>
      _homeShelfStore.saveHomeShelf(shelf);

  @override
  Future<List<BookSummary>> search(String query) async =>
      apiClient.search(query, (await loadLanguagePreference()).locale);

  @override
  Future<BookSummary> loadBook(String bookId) async =>
      apiClient.getBook(bookId, (await loadLanguagePreference()).locale);

  @override
  Future<List<EpisodeSummary>> loadEpisodes(BookSummary book) async =>
      apiClient.getEpisodes(
        book.id,
        book.filteredTitle,
        (await loadLanguagePreference()).locale,
      );

  @override
  Future<VideoUrl> loadVideoUrl(BookSummary book, EpisodeSummary episode) async =>
      apiClient.getVideoUrl(
        book.id,
        episode.number,
        book.filteredTitle,
        episode.chapterId,
        (await loadLanguagePreference()).locale,
      );

  @override
  Future<WatchEpisodeSnapshot> loadEpisodeSnapshot(
    BookSummary book,
    EpisodeSummary episode,
  ) => apiClient.getEpisodeSnapshot(book.id, episode.number);

  @override
  Future<WatchProgressReport> reportWatchProgress(
    BookSummary book,
    EpisodeSummary episode,
    int positionSeconds,
    int durationSeconds,
  ) => apiClient.reportWatchProgress(
    bookId: book.id,
    bookTitle: book.title,
    filteredTitle: book.filteredTitle,
    episode: episode.number,
    chapterId: episode.chapterId,
    positionSeconds: positionSeconds,
    durationSeconds: durationSeconds,
  );

  @override
  Future<List<WatchRecord>> loadWatchHistory() => apiClient.getWatchHistory();

  @override
  Future<PointAccount> loadPointAccount() => apiClient.getPointAccount();

  @override
  Future<List<RechargeOrderSummary>> loadOrders() => apiClient.getOrders();

  @override
  Future<WalletInfo> loadWallet() => apiClient.getWallet();

  @override
  Future<WalletInfo> bindWallet(
    String network,
    String walletAddress,
    String password,
  ) => apiClient.bindWallet(network, walletAddress, password);

  @override
  Future<WalletInfo> unbindWallet(String password) =>
      apiClient.unbindWallet(password);

  @override
  Future<VipOrder> createVipOrder() => apiClient.createVipOrder();

  @override
  Future<List<VipOrder>> loadVipOrders() => apiClient.getVipOrders();

  @override
  Future<VipOrder?> loadLatestVipOrder() => apiClient.getLatestVipOrder();

  @override
  Future<WithdrawalSummary> loadWithdrawalSummary() =>
      apiClient.getWithdrawalSummary();

  @override
  Future<List<WithdrawalRecord>> loadWithdrawals() =>
      apiClient.getWithdrawals();

  @override
  Future<WithdrawalRecord> submitWithdrawal(int pointAmount) =>
      apiClient.submitWithdrawal(pointAmount);

  @override
  Future<SocialToggleResult> toggleLike(BookSummary book) =>
      apiClient.toggleLike(book.id);

  @override
  Future<SocialToggleResult> loadLikeStatus(BookSummary book) =>
      apiClient.getLikeStatus(book.id);

  @override
  Future<SocialToggleResult> toggleFavorite(BookSummary book) =>
      apiClient.toggleFavorite(
        bookId: book.id,
        bookTitle: book.title,
        filteredTitle: book.filteredTitle,
        coverUrl: book.coverUrl,
        chapterCount: book.chapterCount,
      );

  @override
  Future<SocialToggleResult> loadFavoriteStatus(BookSummary book) =>
      apiClient.getFavoriteStatus(book.id);

  @override
  Future<Comment> addComment(BookSummary book, String content) =>
      apiClient.addComment(book.id, content);

  @override
  Future<List<Comment>> listComments(BookSummary book) =>
      apiClient.listComments(book.id);

  @override
  Future<List<BookSummary>> loadMyFavorites() => apiClient.listMyFavorites();

  @override
  Future<AuthSession?> restoreSession() async {
    final AuthSession? session = await _sessionStore.loadSession();
    if (currentToken != null) {
      // A login raced ahead of the (slow) secure-storage read; its token is
      // newer than anything persisted, so keep it and report no stored
      // session. Without this guard the late restore wipes the fresh token
      // and every subsequent request goes out unauthenticated.
      return null;
    }
    currentToken = session?.token;
    return session;
  }

  @override
  Future<void> clearSession() async {
    await _sessionStore.clearSession();
    currentToken = null;
  }

  @override
  Future<SavedCredentials?> loadSavedCredentials() =>
      _credentialStore.loadCredentials();

  @override
  Future<void> saveCredentials(SavedCredentials credentials) =>
      _credentialStore.saveCredentials(credentials);

  @override
  Future<void> clearSavedCredentials() => _credentialStore.clearCredentials();

  @override
  Future<AppLanguage> loadLanguagePreference() =>
      _languagePreferenceStore.loadLanguage();

  @override
  Future<void> saveLanguagePreference(AppLanguage language) =>
      _languagePreferenceStore.saveLanguage(language);
}
