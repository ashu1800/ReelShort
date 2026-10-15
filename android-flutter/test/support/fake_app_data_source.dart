/// Test double for [AppDataSource].
///
/// Hand-written on purpose: the state controller depends only on the interface, so a plain fake
/// with settable results, real failure injection and a call log keeps the tests dependency-free
/// (no mocking package, no code generation).
library;

import 'package:shortlink/data/app_data_source.dart';
import 'package:shortlink/data/models/app_language.dart';
import 'package:shortlink/data/models/app_models.dart';

/// One recorded invocation of the fake.
class RecordedCall {
  const RecordedCall(this.method, [this.arguments = const <String, Object?>{}]);

  final String method;

  /// Arguments the tests assert on. Only the interesting parameters are captured.
  final Map<String, Object?> arguments;

  @override
  String toString() =>
      arguments.isEmpty ? method : '$method(${arguments.entries.map((MapEntry<String, Object?> e) => '${e.key}: ${e.value}').join(', ')})';
}

class FakeAppDataSource implements AppDataSource {
  FakeAppDataSource({
    this.apiBaseUrl = 'https://shortlink.hjj888.cc/api/app',
    this.delay,
  });

  @override
  String apiBaseUrl;

  /// Optional artificial latency, keyed by method name. Use it to hold a call open and assert
  /// in-flight de-duplication or stale-response guards.
  Duration Function(String method)? delay;

  /// Every invocation in order.
  final List<RecordedCall> calls = <RecordedCall>[];

  /// Method name -> error thrown by that method. See [failWith].
  final Map<String, Object> failures = <String, Object>{};

  // --- settable canned results -------------------------------------------------------------

  ApiHealthStatus health = const ApiHealthStatus(status: 'UP');

  /// Returned by [restoreSession]; null models a logged-out guest.
  AuthSession? session = guestSession;

  bool failRestoreSession = false;

  CaptchaChallenge captcha = const CaptchaChallenge(
    captchaId: 'captcha-1',
    imageBase64: 'aGVsbG8=',
  );

  List<BookSummary> homeShelf = <BookSummary>[];
  List<BookSummary> cachedHomeShelf = <BookSummary>[];
  List<BookSummary> searchResults = <BookSummary>[];
  List<BookSummary> favorites = <BookSummary>[];
  BookSummary book = book0;
  List<EpisodeSummary> episodes = <EpisodeSummary>[];
  VideoUrl videoUrl = const VideoUrl(
    url: 'https://cdn.example.test/video.m3u8',
    contentType: 'application/vnd.apple.mpegurl',
    episode: 1,
    durationSeconds: 120,
    estimatedRewardPoints: 2,
  );
  WatchEpisodeSnapshot snapshot = const WatchEpisodeSnapshot.empty(
    bookId: 'book-1',
    episode: 1,
  );
  WatchProgressReport progressReport = const WatchProgressReport(
    bookId: 'book-1',
    bookTitle: 'Drama One',
    filteredTitle: 'drama-one',
    episode: 1,
    chapterId: 'ch-1',
    positionSeconds: 0,
    durationSeconds: 120,
    progressPercent: 0,
  );
  List<WatchRecord> watchHistory = <WatchRecord>[];
  PointAccount pointAccount = const PointAccount(
    balance: 100,
    records: <PointRecord>[],
    vipActive: false,
  );
  List<RechargeOrderSummary> orders = <RechargeOrderSummary>[];
  WalletInfo wallet = const WalletInfo(
    network: 'ERC20',
    walletAddress: null,
    updatedAt: null,
  );
  VipOrder vipOrder = vipOrder0;
  List<VipOrder> vipOrders = <VipOrder>[];
  VipOrder? latestVipOrder;
  WithdrawalSummary withdrawalSummary = const WithdrawalSummary(
    balance: 100,
    frozenPoints: 0,
    availablePoints: 100,
    minimumPoints: 3572,
    usdtPerPoint: '0.0028',
    usdtPer50Points: '0.14',
    minimumUsdt: '10.00',
    walletAddress: null,
  );
  List<WithdrawalRecord> withdrawals = <WithdrawalRecord>[];
  WithdrawalRecord withdrawalRecord = withdrawalRecord0;
  SocialToggleResult likeResult = const SocialToggleResult(active: false, count: 0);
  SocialToggleResult favoriteResult = const SocialToggleResult(active: false, count: 0);
  Comment comment = const Comment(
    id: 'comment-1',
    username: 'tester',
    content: 'hello',
    createdAt: '2026-10-14T00:00:00Z',
  );
  List<Comment> comments = <Comment>[];
  SavedCredentials? savedCredentials;
  AppLanguage language = AppLanguage.defaultLanguage;

  // --- fixtures ----------------------------------------------------------------------------

  static const AuthSession guestSession = AuthSession(
    username: 'tester',
    token: 'token-1',
    tokenType: 'Bearer',
  );

  static const BookSummary book0 = BookSummary(
    id: 'book-1',
    title: 'Drama One',
    filteredTitle: 'drama-one',
    coverUrl: null,
    description: 'A description.',
    chapterCount: 20,
  );

  static const VipOrder vipOrder0 = VipOrder(
    id: 'order-1',
    orderNo: 'NO-1',
    usdtAmount: '0.14',
    status: 'PENDING',
    paymentMethod: 'TRC20',
    txHash: null,
    createdAt: '2026-10-14T00:00:00Z',
    confirmedAt: null,
  );

  static const WithdrawalRecord withdrawalRecord0 = WithdrawalRecord(
    id: 'wd-1',
    pointAmount: 3572,
    usdtAmount: '10.00',
    usdtPerPoint: '0.0028',
    network: 'ERC20',
    walletAddress: '0x0000000000000000000000000000000000000001',
    status: 'PENDING',
    txHash: null,
    adminNote: null,
    createdAt: '2026-10-14T00:00:00Z',
    reviewedAt: null,
  );

  static BookSummary makeBook(String id, {int chapterCount = 20}) => BookSummary(
    id: id,
    title: 'Drama $id',
    filteredTitle: 'drama-$id',
    coverUrl: null,
    description: 'Description for $id.',
    chapterCount: chapterCount,
  );

  static EpisodeSummary makeEpisode(int number, {String? chapterId}) =>
      EpisodeSummary(number: number, chapterId: chapterId ?? 'ch-$number');

  // --- call log helpers --------------------------------------------------------------------

  int callsTo(String method) =>
      calls.where((RecordedCall call) => call.method == method).length;

  RecordedCall? lastCallTo(String method) {
    for (int index = calls.length - 1; index >= 0; index -= 1) {
      if (calls[index].method == method) {
        return calls[index];
      }
    }
    return null;
  }

  Map<String, Object?>? argumentsOf(String method) => lastCallTo(method)?.arguments;

  void failWith(String method, Object error) => failures[method] = error;

  void clearFailures() => failures.clear();

  void reset() {
    calls.clear();
    failures.clear();
  }

  // --- interface --------------------------------------------------------------------------

  @override
  Future<ApiHealthStatus> checkSystemHealth() =>
      _invoke('checkSystemHealth', () => health);

  @override
  Future<AuthSession> login(String username, String password) => _invoke(
    'login',
    () => session ?? guestSession,
    <String, Object?>{'username': username, 'password': password},
  );

  @override
  Future<AuthSession> register(
    String username,
    String password,
    String captchaId,
    String captchaAnswer,
  ) => _invoke(
    'register',
    () => session ?? guestSession,
    <String, Object?>{
      'username': username,
      'password': password,
      'captchaId': captchaId,
      'captchaAnswer': captchaAnswer,
    },
  );

  @override
  Future<CaptchaChallenge> fetchCaptcha() =>
      _invoke('fetchCaptcha', () => captcha);

  @override
  Future<void> changePassword(String oldPassword, String newPassword) => _invoke(
    'changePassword',
    () {},
    <String, Object?>{'oldPassword': oldPassword, 'newPassword': newPassword},
  );

  @override
  Future<List<BookSummary>> loadHomeShelf() =>
      _invoke('loadHomeShelf', () => homeShelf);

  @override
  Future<List<BookSummary>> loadCachedHomeShelf() =>
      _invoke('loadCachedHomeShelf', () => cachedHomeShelf);

  @override
  Future<void> saveCachedHomeShelf(List<BookSummary> shelf) => _invoke(
    'saveCachedHomeShelf',
    () => cachedHomeShelf = shelf,
    <String, Object?>{'count': shelf.length},
  );

  @override
  Future<List<BookSummary>> search(String query) => _invoke(
    'search',
    () => searchResults,
    <String, Object?>{'query': query},
  );

  @override
  Future<BookSummary> loadBook(String bookId) => _invoke(
    'loadBook',
    () => book,
    <String, Object?>{'bookId': bookId},
  );

  @override
  Future<List<EpisodeSummary>> loadEpisodes(BookSummary book) => _invoke(
    'loadEpisodes',
    () => episodes,
    <String, Object?>{'bookId': book.id},
  );

  @override
  Future<VideoUrl> loadVideoUrl(BookSummary book, EpisodeSummary episode) =>
      _invoke(
        'loadVideoUrl',
        () => videoUrl,
        <String, Object?>{'bookId': book.id, 'episode': episode.number},
      );

  @override
  Future<WatchEpisodeSnapshot> loadEpisodeSnapshot(
    BookSummary book,
    EpisodeSummary episode,
  ) => _invoke(
    'loadEpisodeSnapshot',
    () => snapshot,
    <String, Object?>{'bookId': book.id, 'episode': episode.number},
  );

  @override
  Future<WatchProgressReport> reportWatchProgress(
    BookSummary book,
    EpisodeSummary episode,
    int positionSeconds,
    int durationSeconds,
  ) => _invoke(
    'reportWatchProgress',
    () => progressReport,
    <String, Object?>{
      'bookId': book.id,
      'episode': episode.number,
      'positionSeconds': positionSeconds,
      'durationSeconds': durationSeconds,
    },
  );

  @override
  Future<List<WatchRecord>> loadWatchHistory() =>
      _invoke('loadWatchHistory', () => watchHistory);

  @override
  Future<PointAccount> loadPointAccount() =>
      _invoke('loadPointAccount', () => pointAccount);

  @override
  Future<List<RechargeOrderSummary>> loadOrders() =>
      _invoke('loadOrders', () => orders);

  @override
  Future<WalletInfo> loadWallet() => _invoke('loadWallet', () => wallet);

  @override
  Future<WalletInfo> bindWallet(
    String network,
    String walletAddress,
    String password,
  ) => _invoke(
    'bindWallet',
    () => wallet,
    <String, Object?>{
      'network': network,
      'walletAddress': walletAddress,
      'password': password,
    },
  );

  @override
  Future<WalletInfo> unbindWallet(String password) => _invoke(
    'unbindWallet',
    () => wallet,
    <String, Object?>{'password': password},
  );

  @override
  Future<VipOrder> createVipOrder() => _invoke('createVipOrder', () => vipOrder);

  @override
  Future<List<VipOrder>> loadVipOrders() =>
      _invoke('loadVipOrders', () => vipOrders);

  @override
  Future<VipOrder?> loadLatestVipOrder() =>
      _invoke('loadLatestVipOrder', () => latestVipOrder);

  @override
  Future<WithdrawalSummary> loadWithdrawalSummary() =>
      _invoke('loadWithdrawalSummary', () => withdrawalSummary);

  @override
  Future<List<WithdrawalRecord>> loadWithdrawals() =>
      _invoke('loadWithdrawals', () => withdrawals);

  @override
  Future<WithdrawalRecord> submitWithdrawal(int pointAmount) => _invoke(
    'submitWithdrawal',
    () => withdrawalRecord,
    <String, Object?>{'pointAmount': pointAmount},
  );

  @override
  Future<SocialToggleResult> toggleLike(BookSummary book) => _invoke(
    'toggleLike',
    () => likeResult,
    <String, Object?>{'bookId': book.id},
  );

  @override
  Future<SocialToggleResult> loadLikeStatus(BookSummary book) => _invoke(
    'loadLikeStatus',
    () => likeResult,
    <String, Object?>{'bookId': book.id},
  );

  @override
  Future<SocialToggleResult> toggleFavorite(BookSummary book) => _invoke(
    'toggleFavorite',
    () => favoriteResult,
    <String, Object?>{'bookId': book.id},
  );

  @override
  Future<SocialToggleResult> loadFavoriteStatus(BookSummary book) => _invoke(
    'loadFavoriteStatus',
    () => favoriteResult,
    <String, Object?>{'bookId': book.id},
  );

  @override
  Future<Comment> addComment(BookSummary book, String content) => _invoke(
    'addComment',
    () => comment,
    <String, Object?>{'bookId': book.id, 'content': content},
  );

  @override
  Future<List<Comment>> listComments(BookSummary book) => _invoke(
    'listComments',
    () => comments,
    <String, Object?>{'bookId': book.id},
  );

  @override
  Future<List<BookSummary>> loadMyFavorites() =>
      _invoke('loadMyFavorites', () => favorites);

  @override
  Future<AuthSession?> restoreSession() => _invoke('restoreSession', () {
    if (failRestoreSession) {
      throw StateError('restoreSession failed');
    }
    return session;
  });

  @override
  Future<void> clearSession() => _invoke('clearSession', () => session = null);

  @override
  Future<SavedCredentials?> loadSavedCredentials() =>
      _invoke('loadSavedCredentials', () => savedCredentials);

  @override
  Future<void> saveCredentials(SavedCredentials credentials) => _invoke(
    'saveCredentials',
    () => savedCredentials = credentials,
    <String, Object?>{
      'username': credentials.username,
      'rememberPassword': credentials.rememberPassword,
    },
  );

  @override
  Future<void> clearSavedCredentials() =>
      _invoke('clearSavedCredentials', () => savedCredentials = null);

  @override
  Future<AppLanguage> loadLanguagePreference() =>
      _invoke('loadLanguagePreference', () => language);

  @override
  Future<void> saveLanguagePreference(AppLanguage language) => _invoke(
    'saveLanguagePreference',
    () => this.language = language,
    <String, Object?>{'language': language.name},
  );

  /// Records the call, applies optional latency, then throws an injected failure or returns
  /// [body]'s value.
  Future<T> _invoke<T>(
    String method,
    T Function() body, [
    Map<String, Object?> arguments = const <String, Object?>{},
  ]) async {
    calls.add(RecordedCall(method, arguments));
    final Duration? wait = delay?.call(method);
    if (wait != null) {
      await Future<void>.delayed(wait);
    }
    final Object? failure = failures[method];
    if (failure != null) {
      throw failure;
    }
    return body();
  }
}
