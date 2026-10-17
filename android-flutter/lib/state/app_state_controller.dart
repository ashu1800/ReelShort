/// The app's complete UI state machine.
///
/// Port of `android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppStateController.kt`.
/// It owns authentication, the home shelf, search, detail, playback, rewards,
/// points, wallet, VIP, withdrawals, comments, likes/favourites, language and
/// the self-update-adjacent flags.
///
/// The Kotlin class is a `MutableStateFlow` holder plus a set of coroutine
/// mutexes. The Dart port is a [ChangeNotifier] exposing the same immutable
/// [AppUiState] value: every mutation goes through `_set`, which assigns the
/// new state and calls `notifyListeners()` - the equivalent of the Kotlin
/// `mutableState.update { it.copy(...) }`.
///
/// Kotlin `Mutex`/`withLock`/`tryLock` are reimplemented by the private
/// [_AsyncMutex] in this file because the port may not add packages.
///
/// All user-facing copy lives in the single private table at the bottom of
/// this file; the Chinese literals of `app-core` and their English
/// replacements are audited in `docs/controller-parity.md`.
library;

import 'dart:async';

import 'package:flutter/foundation.dart';

import '../data/app_data_source.dart';
import '../data/models/app_language.dart';
import '../data/models/app_models.dart';
import '../data/network/api_client_exception.dart';
import 'app_ui_state.dart';
import 'playback_state.dart';

/// State machine behind every screen of the app.
///
/// The controller depends only on [AppDataSource], so a test double can drive
/// it without HTTP or storage.
class AppStateController extends ChangeNotifier {
  /// Creates the controller for one [dataSource].
  ///
  /// [nowMillis] is the clock used by the progress-report backoff; it defaults
  /// to the system clock and exists so tests can control time.
  AppStateController({
    required AppDataSource dataSource,
    int Function()? nowMillis,
  }) : _dataSource = dataSource,
       _nowMillis = nowMillis ?? _systemNowMillis,
       _state = AppUiState(apiBaseUrl: dataSource.apiBaseUrl);

  /// Wall-clock default for the injectable [nowMillis] clock.
  static int _systemNowMillis() => DateTime.now().millisecondsSinceEpoch;

  final AppDataSource _dataSource;
  final int Function() _nowMillis;

  /// The single mutable state value; only `_set` may replace it.
  AppUiState _state;

  // Ported verbatim from the Kotlin private fields.
  bool _rewardReportInFlight = false;
  _PendingCompletionReport? _pendingCompletionReport;
  bool _sessionRestored = false;
  int _progressReportFailureCount = 0;
  int _progressReportRetryAtMillis = 0;
  int _searchRequestVersion = 0;
  int _playbackRequestVersion = 0;
  int? _pendingVipEpisodeRequestVersion;
  int _vipPaywallGeneration = 0;
  int _accountRequestVersion = 0;
  int _favoritesRequestVersion = 0;
  final _AsyncMutex _accountOperationMutex = _AsyncMutex();
  final _AsyncMutex _vipPaymentRefreshMutex = _AsyncMutex();

  /// The current immutable UI state.
  AppUiState get state => _state;

  /// Replaces the state and notifies listeners - the one mutation helper.
  void _set(AppUiState next) {
    _state = next;
    notifyListeners();
  }

  // ---------------------------------------------------------------------------
  // Session, authentication and language
  // ---------------------------------------------------------------------------

  /// Restores the persisted session, remembered credentials, language and the
  /// cached home shelf.
  ///
  /// A non-empty cached shelf renders in this single update with
  /// `isLoading = false`, then a silent background refresh replaces it and
  /// writes the fresh shelf back to the cache. An empty cache loads from the
  /// network; a failure there keeps the session but shows content-error copy.
  Future<void> restoreSession() => _runWithLoading(_ErrorContext.content, () async {
    if (_sessionRestored) {
      _set(_state.copyWith(isLoading: false));
      return;
    }
    _sessionRestored = true;
    final AppLanguage language = await _dataSource.loadLanguagePreference();
    final AuthSession? session = await _dataSource.restoreSession();
    final SavedCredentials? savedCredentials = await _dataSource
        .loadSavedCredentials();
    final List<BookSummary> cachedHomeShelf = await _dataSource
        .loadCachedHomeShelf();
    // The secure-storage read is slow enough for a login to race ahead of it;
    // that fresh session must not be clobbered by whatever was persisted.
    final bool racedLogin = _state.session != null;
    if (cachedHomeShelf.isNotEmpty) {
      // Cold-start fast path: paint the cached shelf immediately, then refresh
      // in the background and write the fresh shelf back to the cache.
      _set(
        _state.copyWith(
          language: language,
          session: racedLogin ? _state.session : session,
          savedCredentials: racedLogin
              ? _state.savedCredentials
              : savedCredentials,
          homeShelf: cachedHomeShelf,
          isLoading: false,
        ),
      );
      await refreshHomeSilently();
      return;
    }
    try {
      final List<BookSummary> homeShelf = await _dataSource.loadHomeShelf();
      _set(
        _state.copyWith(
          language: language,
          session: racedLogin ? _state.session : session,
          savedCredentials: racedLogin
              ? _state.savedCredentials
              : savedCredentials,
          homeShelf: homeShelf,
          isLoading: false,
        ),
      );
      await _dataSource.saveCachedHomeShelf(homeShelf);
    } on Object catch (_) {
      // Kotlin rethrows CancellationException here and reports every other
      // failure; Dart has no cancellation, so all failures take this path.
      _set(
        _state.copyWith(
          language: language,
          session: racedLogin ? _state.session : session,
          savedCredentials: racedLogin
              ? _state.savedCredentials
              : savedCredentials,
          homeShelf: const <BookSummary>[],
          isLoading: false,
          errorMessage: _copy.contentLoadFailed,
          messageType: UiMessageType.error,
        ),
      );
    }
  });

  /// Reloads the remembered credentials into the state.
  Future<void> loadSavedCredentials() =>
      _runWithLoading(_ErrorContext.diagnostic, () async {
        final SavedCredentials? savedCredentials = await _dataSource
            .loadSavedCredentials();
        _set(
          _state.copyWith(
            savedCredentials: savedCredentials,
            isLoading: false,
          ),
        );
      });

  /// Signs in and continues whatever the guest was trying to do.
  Future<void> login(
    String username,
    String password, {
    bool rememberPassword = false,
  }) => _runWithLoading(_ErrorContext.login, () async {
    final AuthSession session = await _dataSource.login(username, password);
    await _updateSavedCredentials(username, password, rememberPassword);
    await _openHomeAfterAuthentication(session);
  });

  /// Registers a new account and continues whatever the guest was doing.
  Future<void> register(
    String username,
    String password,
    String captchaId,
    String captchaAnswer,
  ) => _runWithLoading(_ErrorContext.register, () async {
    final AuthSession session = await _dataSource.register(
      username,
      password,
      captchaId,
      captchaAnswer,
    );
    await _updateSavedCredentials(username, password, false);
    _set(
      _state.copyWith(
        isLoading: false,
        authPromptVisible: false,
        captchaId: '',
        captchaImageBase64: '',
        errorMessage: null,
      ),
    );
    await _openHomeAfterAuthentication(session);
  });

  /// Fetches a fresh graphical captcha challenge.
  Future<void> fetchCaptcha() =>
      _runWithLoading(_ErrorContext.register, () async {
        final CaptchaChallenge challenge = await _dataSource.fetchCaptcha();
        _set(
          _state.copyWith(
            isLoading: false,
            captchaId: challenge.captchaId,
            captchaImageBase64: challenge.imageBase64,
          ),
        );
      });

  /// Persists or clears the remembered credentials according to the opt-in.
  Future<void> _updateSavedCredentials(
    String username,
    String password,
    bool rememberPassword,
  ) async {
    if (rememberPassword) {
      final SavedCredentials credentials = SavedCredentials(
        username: username,
        password: password,
        rememberPassword: true,
      );
      await _dataSource.saveCredentials(credentials);
      _set(_state.copyWith(savedCredentials: credentials));
    } else {
      await _dataSource.clearSavedCredentials();
      _set(_state.copyWith(savedCredentials: null));
    }
  }

  /// Continues the action that was interrupted by the auth prompt.
  ///
  /// Priority: pending VIP episode, then pending normal episode (resuming from
  /// the watch history), then an in-place account refresh when the user was on
  /// an account screen, then returning home and reloading the shelf.
  Future<void> _openHomeAfterAuthentication(AuthSession session) async {
    final EpisodeSummary? pendingVipEpisode = _state.pendingVipEpisode;
    if (pendingVipEpisode != null) {
      _set(
        _state.copyWith(
          session: session,
          authPromptVisible: false,
          pendingPlaybackEpisode: null,
          isLoading: false,
          errorMessage: null,
        ),
      );
      await _openVipPaywall(
        episode: pendingVipEpisode,
        requestVersion: _pendingVipEpisodeRequestVersion,
      );
      return;
    }
    final EpisodeSummary? pendingEpisode = _state.pendingPlaybackEpisode;
    if (pendingEpisode != null) {
      final AppUiState current = _state;
      final int requestVersion = ++_playbackRequestVersion;
      _set(
        _state.copyWith(
          session: session,
          authPromptVisible: false,
          pendingPlaybackEpisode: null,
          isLoading: true,
          errorMessage: null,
        ),
      );
      final BookSummary book = current.selectedBook ?? _requireSelectedBook();
      final List<EpisodeSummary> episodes = current.episodes;
      final EpisodeSummary? fallbackEpisode = _playbackFallbackEpisode(
        episodes,
        pendingEpisode,
      );
      final EpisodeSummary? episode;
      if (episodes.isNotEmpty) {
        final EpisodeSummary? resume = await _chooseResumeEpisode(
          book,
          episodes,
          fallbackEpisode,
          requestVersion,
        );
        episode = resume ?? fallbackEpisode;
      } else {
        episode = pendingEpisode;
      }
      if (!_isActivePlaybackRequest(requestVersion)) {
        return;
      }
      if (episode == null) {
        _set(
          _state.copyWith(
            isLoading: false,
            errorMessage: _copy.contentLoadFailed,
            messageType: UiMessageType.error,
          ),
        );
        return;
      }
      await _requestEpisodePlayback(book, episode, requestVersion);
      return;
    }
    if (_state.screen == AppScreen.account) {
      _set(
        _state.copyWith(
          session: session,
          authPromptVisible: false,
          pendingPlaybackEpisode: null,
          isLoading: false,
          errorMessage: null,
        ),
      );
      await loadAccountSnapshot();
      return;
    }
    List<BookSummary>? homeShelf;
    try {
      homeShelf = await _dataSource.loadHomeShelf();
    } on Object catch (_) {
      // Kotlin rethrows CancellationException and maps every other failure to a
      // null shelf; Dart has no cancellation, so every failure is null.
      homeShelf = null;
    }
    _set(
      _state.copyWith(
        screen: AppScreen.home,
        session: session,
        authPromptVisible: false,
        pendingPlaybackEpisode: null,
        homeShelf: homeShelf ?? const <BookSummary>[],
        isLoading: false,
        errorMessage: homeShelf == null ? _copy.contentLoadFailed : null,
        messageType: homeShelf == null ? UiMessageType.error : null,
      ),
    );
    if (homeShelf != null) {
      await _dataSource.saveCachedHomeShelf(homeShelf);
    }
  }

  // ---------------------------------------------------------------------------
  // Home shelf, search and language
  // ---------------------------------------------------------------------------

  /// Reloads the home shelf behind the global loading indicator.
  Future<void> refreshHome() =>
      _runWithLoading(_ErrorContext.content, () async {
        final List<BookSummary> homeShelf = await _dataSource.loadHomeShelf();
        _set(_state.copyWith(homeShelf: homeShelf, isLoading: false));
        await _dataSource.saveCachedHomeShelf(homeShelf);
      });

  /// Pull-to-refresh: the top indicator only, no global dialog, and a failure
  /// keeps the old shelf and shows a top error message.
  Future<void> refreshHomeWithPull() async {
    _set(
      _state.copyWith(
        screen: AppScreen.home,
        isHomeRefreshing: true,
        errorMessage: null,
        messageType: null,
      ),
    );
    try {
      final List<BookSummary> homeShelf = await _dataSource.loadHomeShelf();
      _set(_state.copyWith(homeShelf: homeShelf, isHomeRefreshing: false));
      await _dataSource.saveCachedHomeShelf(homeShelf);
    } on Object catch (error) {
      // Kotlin rethrows CancellationException after clearing the indicator;
      // Dart has no cancellation, so this is the reported failure path.
      _set(
        _state.copyWith(
          isHomeRefreshing: false,
          errorMessage: _userFacingErrorMessage(error, _ErrorContext.content),
          messageType: UiMessageType.error,
        ),
      );
    }
  }

  /// Shows the home tab, loading or silently refreshing the shelf as needed.
  Future<void> openHome() async {
    _set(
      _state.copyWith(
        screen: AppScreen.home,
        errorMessage: null,
        messageType: null,
      ),
    );
    if (_state.homeShelf.isEmpty) {
      await refreshHome();
      return;
    }
    await refreshHomeSilently();
  }

  /// Background shelf refresh: no loading indicator, failures are swallowed.
  Future<void> refreshHomeSilently() async {
    try {
      final List<BookSummary> homeShelf = await _dataSource.loadHomeShelf();
      _set(_state.copyWith(homeShelf: homeShelf));
      await _dataSource.saveCachedHomeShelf(homeShelf);
    } on Object catch (error) {
      _ignoreFailure(error);
    }
  }

  /// Runs a content search with request-version protection.
  Future<void> search(String query) =>
      _runWithLoading(_ErrorContext.content, () async {
        final int requestVersion = ++_searchRequestVersion;
        final List<BookSummary> results = await _dataSource.search(query);
        if (requestVersion != _searchRequestVersion) {
          return;
        }
        _set(
          _state.copyWith(
            screen: AppScreen.search,
            searchQuery: query,
            searchResults: results,
            isLoading: false,
          ),
        );
      });

  /// Switches to the search tab, clearing any message.
  void showSearch() {
    _set(
      _state.copyWith(
        screen: AppScreen.search,
        errorMessage: null,
        messageType: null,
      ),
    );
  }

  /// Switches the content language and reloads the shelf for it.
  Future<void> setLanguage(AppLanguage language) async {
    _set(
      _state.copyWith(
        language: language,
        screen: AppScreen.home,
        searchQuery: '',
        searchResults: const <BookSummary>[],
        isLoading: true,
        errorMessage: null,
      ),
    );
    try {
      await _dataSource.saveLanguagePreference(language);
      final List<BookSummary> homeShelf = await _dataSource.loadHomeShelf();
      _set(
        _state.copyWith(
          homeShelf: homeShelf,
          isLoading: false,
          errorMessage: null,
        ),
      );
      await _dataSource.saveCachedHomeShelf(homeShelf);
    } on Object catch (error) {
      // Kotlin rethrows CancellationException after clearing the loading flag;
      // Dart has no cancellation, so every failure is reported here.
      _set(
        _state.copyWith(
          isLoading: false,
          errorMessage: _userFacingErrorMessage(error, _ErrorContext.content),
          messageType: UiMessageType.error,
        ),
      );
    }
  }

  // ---------------------------------------------------------------------------
  // Detail, playback entry and progress reporting
  // ---------------------------------------------------------------------------

  /// Opens a drama: loads its episodes and starts playback (or the auth prompt).
  Future<void> openBook(BookSummary book) =>
      _runWithLoading(_ErrorContext.content, () async {
        final int requestVersion = ++_playbackRequestVersion;
        final AppScreen returnScreen = _playbackReturnScreenFor(_state.screen);
        final List<EpisodeSummary> episodes = await _dataSource.loadEpisodes(
          book,
        );
        if (!_isActivePlaybackRequest(requestVersion)) {
          return;
        }
        final EpisodeSummary? firstEpisode = episodes.isEmpty
            ? null
            : episodes.first;
        if (firstEpisode == null) {
          _set(
            _state.copyWith(
              selectedBook: book,
              episodes: const <EpisodeSummary>[],
              selectedEpisode: null,
              currentVideoUrl: null,
              playback: const PlaybackState(),
              playerReturnScreen: returnScreen,
              isLoading: false,
              errorMessage: _copy.contentLoadFailed,
              messageType: UiMessageType.error,
            ),
          );
          return;
        }
        final AuthSession? session = _state.session;
        _set(
          _state.copyWith(
            selectedBook: book,
            episodes: episodes,
            selectedEpisode: null,
            currentVideoUrl: null,
            playback: const PlaybackState(),
            playerReturnScreen: returnScreen,
            isLoading: session != null,
          ),
        );
        if (session == null) {
          _set(
            _state.copyWith(
              authPromptVisible: true,
              pendingPlaybackEpisode: firstEpisode.requiresVip()
                  ? null
                  : firstEpisode,
              pendingVipEpisode: firstEpisode.requiresVip()
                  ? firstEpisode
                  : null,
              isLoading: false,
            ),
          );
          _pendingVipEpisodeRequestVersion = firstEpisode.requiresVip()
              ? requestVersion
              : null;
          return;
        }
        final EpisodeSummary? resume = await _chooseResumeEpisode(
          book,
          episodes,
          firstEpisode,
          requestVersion,
        );
        final EpisodeSummary resumeEpisode = resume ?? firstEpisode;
        if (!_isActivePlaybackRequest(requestVersion)) {
          return;
        }
        await _requestEpisodePlayback(book, resumeEpisode, requestVersion);
      });

  /// Plays one episode of the currently selected drama.
  Future<void> openPlayer(EpisodeSummary episode) =>
      _runWithLoading(_ErrorContext.playback, () async {
        final BookSummary book = _requireSelectedBook();
        final int requestVersion = ++_playbackRequestVersion;
        if (_state.session == null) {
          _set(
            _state.copyWith(
              authPromptVisible: true,
              pendingPlaybackEpisode: episode.requiresVip() ? null : episode,
              pendingVipEpisode: episode.requiresVip() ? episode : null,
              isLoading: false,
            ),
          );
          _pendingVipEpisodeRequestVersion = episode.requiresVip()
              ? requestVersion
              : null;
          return;
        }
        await _requestEpisodePlayback(book, episode, requestVersion);
      });

  /// Continues a drama from the account screen's watch-history card.
  Future<void> openWatchRecord(WatchRecord record) =>
      _runWithLoading(_ErrorContext.content, () async {
        if (_state.session == null) {
          _set(
            _state.copyWith(
              screen: AppScreen.account,
              authPromptVisible: true,
              isLoading: false,
              errorMessage: null,
            ),
          );
          return;
        }
        final int requestVersion = ++_playbackRequestVersion;
        final BookSummary book = await _dataSource.loadBook(record.bookId);
        if (!_isActivePlaybackRequest(requestVersion)) {
          return;
        }
        final List<EpisodeSummary> episodes = await _dataSource.loadEpisodes(
          book,
        );
        if (!_isActivePlaybackRequest(requestVersion)) {
          return;
        }
        final EpisodeSummary? resumed = _resumeEpisodeForRecord(episodes, record);
        final EpisodeSummary? episode = resumed ?? (episodes.isEmpty ? null : episodes.first);
        if (episode == null) {
          _set(
            _state.copyWith(
              screen: AppScreen.account,
              selectedBook: book,
              episodes: const <EpisodeSummary>[],
              selectedEpisode: null,
              currentVideoUrl: null,
              playback: const PlaybackState(),
              playerReturnScreen: AppScreen.account,
              isLoading: false,
              errorMessage: _copy.contentLoadFailed,
              messageType: UiMessageType.error,
            ),
          );
          return;
        }
        _set(
          _state.copyWith(
            selectedBook: book,
            episodes: episodes,
            selectedEpisode: null,
            currentVideoUrl: null,
            playback: const PlaybackState(),
            playerReturnScreen: AppScreen.account,
            isLoading: true,
          ),
        );
        await _requestEpisodePlayback(book, episode, requestVersion);
      });

  /// Resolves the playable URL for one episode, gating VIP episodes behind the
  /// shared paywall.
  Future<void> _requestEpisodePlayback(
    BookSummary book,
    EpisodeSummary episode,
    int requestVersion,
  ) async {
    if (!_isActivePlaybackRequest(requestVersion)) {
      return;
    }
    PointAccount? pointAccount = _state.pointAccount;
    pointAccount ??= await _loadPointAccountSilently();
    if (!_isActivePlaybackRequest(requestVersion)) {
      return;
    }
    if (episode.requiresVip() && pointAccount?.vipActive != true) {
      await _openVipPaywall(episode: episode, requestVersion: requestVersion);
      return;
    }
    try {
      await _openPlayerForAuthenticatedUser(book, episode, requestVersion);
    } on Object catch (error) {
      // Kotlin rethrows CancellationException before this branch; Dart has no
      // cancellation, so this single path covers every failure.
      if (_isActivePlaybackRequest(requestVersion) &&
          _isVipRequiredError(error)) {
        await _openVipPaywall(
          episode: episode,
          requestVersion: requestVersion,
          allowImmediateResume: false,
        );
        return;
      }
      rethrow;
    }
  }

  /// Loads the snapshot, media URL, interaction and comments, then enters the
  /// player screen.
  Future<void> _openPlayerForAuthenticatedUser(
    BookSummary book,
    EpisodeSummary episode,
    int requestVersion,
  ) async {
    final WatchEpisodeSnapshot? snapshot = await _silently(
      () => _dataSource.loadEpisodeSnapshot(book, episode),
    );
    if (!_isActivePlaybackRequest(requestVersion)) {
      return;
    }
    final VideoUrl videoUrl = await _dataSource.loadVideoUrl(book, episode);
    if (!_isActivePlaybackRequest(requestVersion)) {
      return;
    }
    final int initialPosition = snapshot?.positionSeconds ?? 0;
    final int snapshotDuration = snapshot == null ? 0 : snapshot.durationSeconds;
    final int initialDuration = snapshotDuration > 0
        ? snapshotDuration
        : videoUrl.durationSeconds;
    WatchEpisodeSnapshot? playbackSnapshot;
    if (snapshot != null) {
      // A snapshot already at 100% restarts the episode from the beginning.
      playbackSnapshot = snapshot.progressPercent >= 100
          ? snapshot.copyWith(positionSeconds: 0, progressPercent: 0)
          : snapshot;
    }
    final BookInteractionState interaction = await _loadInteractionSilently(
      book,
    );
    if (!_isActivePlaybackRequest(requestVersion)) {
      return;
    }
    final List<Comment> comments = await _loadCommentsSilently(book);
    if (!_isActivePlaybackRequest(requestVersion)) {
      return;
    }
    final PlaybackState base = PlaybackState.ready(
      book: book,
      episode: episode,
      videoUrl: videoUrl,
    ).withPosition(initialPosition, initialDuration);
    final PlaybackState playback = playbackSnapshot == null
        ? base.withReportedProgress(
            positionSeconds: initialPosition,
            progressPercent: 0,
          )
        : base.withSnapshot(playbackSnapshot);
    _set(
      _state.copyWith(
        screen: AppScreen.player,
        selectedEpisode: episode,
        currentVideoUrl: videoUrl,
        playback: playback,
        interaction: interaction,
        comments: comments,
        isLoading: false,
        authPromptVisible: false,
        pendingPlaybackEpisode: null,
        pendingVipEpisode: null,
        errorMessage: snapshot == null ? _copy.rewardStatusLoadFailed : null,
      ),
    );
    _pendingVipEpisodeRequestVersion = null;
  }

  /// Loads like/favourite counters, falling back to zeros on failure.
  Future<BookInteractionState> _loadInteractionSilently(BookSummary book) async {
    final SocialToggleResult? like = await _silently(
      () => _dataSource.loadLikeStatus(book),
    );
    final SocialToggleResult? favorite = await _silently(
      () => _dataSource.loadFavoriteStatus(book),
    );
    return BookInteractionState(
      liked: like?.active ?? false,
      likeCount: like?.count ?? 0,
      favorited: favorite?.active ?? false,
      favoriteCount: favorite?.count ?? 0,
    );
  }

  /// Loads the comment list, falling back to an empty list on failure.
  Future<List<Comment>> _loadCommentsSilently(BookSummary book) async {
    final List<Comment>? comments = await _silently(
      () => _dataSource.listComments(book),
    );
    return comments ?? const <Comment>[];
  }

  /// Picks the episode to resume from the watch history, falling back to the
  /// first episode. Returns null when a newer playback request superseded this
  /// one.
  Future<EpisodeSummary?> _chooseResumeEpisode(
    BookSummary book,
    List<EpisodeSummary> episodes,
    EpisodeSummary? fallbackEpisode,
    int requestVersion,
  ) async {
    final List<WatchRecord>? history = await _silently(
      () => _dataSource.loadWatchHistory(),
    );
    if (history == null) {
      return _isActivePlaybackRequest(requestVersion)
          ? _playbackFallbackEpisode(episodes, fallbackEpisode)
          : null;
    }
    if (!_isActivePlaybackRequest(requestVersion)) {
      return null;
    }
    final int index = history.indexWhere(
      (WatchRecord record) => record.bookId == book.id,
    );
    if (index < 0) {
      return _playbackFallbackEpisode(episodes, fallbackEpisode);
    }
    return _resumeEpisodeForRecord(episodes, history[index]) ??
        _playbackFallbackEpisode(episodes, fallbackEpisode);
  }

  /// Mirrors the player's local position without touching the network.
  void updatePlaybackPosition(int positionSeconds, int durationSeconds) {
    if (_state.playback.status != PlaybackStatus.ready) {
      // Kotlin's `update { it }` leaves the value unchanged, so no listener is
      // notified; bail out instead of emitting a redundant notification.
      return;
    }
    _set(
      _state.copyWith(
        playback: _state.playback.withPosition(
          positionSeconds,
          durationSeconds,
        ),
      ),
    );
  }

  /// Reports watch progress; silently skipped or deferred when needed.
  ///
  /// In-flight de-duplication, the failure backoff ladder and the staged 100%
  /// completion report all match the Kotlin implementation.
  Future<void> reportProgressSilently(
    int positionSeconds,
    int durationSeconds,
  ) async {
    final AppUiState current = _state;
    final PlaybackState playback = current.playback;
    final int requestVersion = _playbackRequestVersion;
    if (playback.status != PlaybackStatus.ready) {
      return;
    }
    if (positionSeconds <= 0 || durationSeconds <= 0) {
      return;
    }
    final PlaybackState localProgress = playback.withPosition(
      positionSeconds,
      durationSeconds,
    );
    if (_rewardReportInFlight) {
      // A report is already in flight: only the local progress moves, and a
      // finished episode is staged so its reward cannot be dropped.
      _set(_state.copyWith(playback: localProgress));
      if (localProgress.progressPercent >= 100 && !playback.rewardClaimed) {
        _pendingCompletionReport = _PendingCompletionReport(
          requestVersion,
          positionSeconds,
          durationSeconds,
        );
      }
      return;
    }
    // After a failure the ladder is 5s, 15s, 60s (capped at 5 minutes) so a
    // weak network cannot turn per-second polling into per-second failures;
    // local progress keeps updating during the wait.
    if (_nowMillis() < _progressReportRetryAtMillis) {
      _set(_state.copyWith(playback: localProgress));
      return;
    }
    if (!shouldPersistPlaybackProgress(
      localProgress.positionSeconds,
      localProgress.durationSeconds,
      playback.lastReportedPositionSeconds,
      playback.rewardClaimed,
    )) {
      _set(_state.copyWith(playback: localProgress));
      return;
    }
    final BookSummary? book = current.selectedBook;
    if (book == null) {
      return;
    }
    final EpisodeSummary? episode = current.selectedEpisode;
    if (episode == null) {
      return;
    }

    _rewardReportInFlight = true;
    final bool isRewardAttempt =
        localProgress.progressPercent >= 100 && !playback.rewardClaimed;
    _set(
      _state.copyWith(
        playback: localProgress.withRewardReporting(isRewardAttempt),
      ),
    );
    try {
      final WatchProgressReport progress = await _dataSource.reportWatchProgress(
        book,
        episode,
        positionSeconds,
        durationSeconds,
      );
      if (!_isActivePlaybackRequest(requestVersion)) {
        return;
      }
      _progressReportFailureCount = 0;
      _progressReportRetryAtMillis = 0;
      _set(
        _state.copyWith(
          watchHistory: _upsertWatchHistory(_state.watchHistory, progress),
          playback: _state.playback
              .withProgressReport(progress)
              .withRewardReporting(false),
        ),
      );
      if (progress.awardedPoints > 0) {
        try {
          final PointAccount points = await _dataSource.loadPointAccount();
          if (_isActivePlaybackRequest(requestVersion)) {
            _set(_state.copyWith(pointAccount: points));
          }
        } on Object catch (error) {
          _ignoreFailure(error);
        }
      }
    } on Object catch (_) {
      // Kotlin's cancellation branch cleared the in-flight reward flag without
      // counting a failure and then rethrew; Dart has no cancellation, so every
      // failure is treated as a retryable report failure here.
      _progressReportFailureCount += 1;
      _progressReportRetryAtMillis =
          _nowMillis() +
          progressReportBackoffSeconds(_progressReportFailureCount) * 1000;
      if (_isActivePlaybackRequest(requestVersion)) {
        final PlaybackState moved = _state.playback.withPosition(
          positionSeconds,
          durationSeconds,
        );
        _set(
          _state.copyWith(
            playback: isRewardAttempt
                ? moved.withRewardReportError()
                : moved.withRewardReporting(false),
          ),
        );
      }
    } finally {
      _rewardReportInFlight = false;
      final _PendingCompletionReport? pending = _pendingCompletionReport;
      _pendingCompletionReport = null;
      // The staged report is re-sent with depth 1: the field was cleared above,
      // so the recursive call cannot stage another one.
      if (pending != null && _isActivePlaybackRequest(pending.requestVersion)) {
        await reportProgressSilently(
          pending.positionSeconds,
          pending.durationSeconds,
        );
      }
    }
  }

  /// Moves the reported episode to the front of the watch history.
  List<WatchRecord> _upsertWatchHistory(
    List<WatchRecord> history,
    WatchProgressReport progress,
  ) {
    final WatchRecord updated = WatchRecord(
      bookId: progress.bookId,
      bookTitle: progress.bookTitle,
      episode: progress.episode,
      progressPercent: progress.progressPercent,
    );
    return <WatchRecord>[
      updated,
      ...history.where(
        (WatchRecord record) =>
            !(record.bookId == progress.bookId &&
                record.episode == progress.episode),
      ),
    ];
  }

  /// Re-resolves the media URL of the current episode (player retry action).
  Future<void> refreshPlaybackUrl() =>
      _runWithLoading(_ErrorContext.playback, () async {
        final AppUiState current = _state;
        final BookSummary? selectedBook = current.selectedBook;
        if (selectedBook == null) {
          throw _AppStateException(_copy.noDramaSelected);
        }
        final EpisodeSummary? selectedEpisode = current.selectedEpisode;
        if (selectedEpisode == null) {
          throw _AppStateException(_copy.noEpisodeSelected);
        }
        final VideoUrl videoUrl = await _dataSource.loadVideoUrl(
          selectedBook,
          selectedEpisode,
        );
        _set(
          _state.copyWith(
            currentVideoUrl: videoUrl,
            playback: _state.playback.withVideoUrl(videoUrl),
            isLoading: false,
          ),
        );
      });

  // ---------------------------------------------------------------------------
  // Social actions, favorites and navigation
  // ---------------------------------------------------------------------------

  /// Toggles the like state of the selected drama.
  Future<void> toggleLike() => _runSocialAction(() async {
    final BookSummary book = _requireSelectedBook();
    final SocialToggleResult result = await _dataSource.toggleLike(book);
    _set(
      _state.copyWith(
        interaction: _state.interaction.copyWith(
          liked: result.active,
          likeCount: result.count,
        ),
      ),
    );
  });

  /// Toggles the favourite state of the selected drama.
  Future<void> toggleFavorite() => _runSocialAction(() async {
    final BookSummary book = _requireSelectedBook();
    final SocialToggleResult result = await _dataSource.toggleFavorite(book);
    _set(
      _state.copyWith(
        interaction: _state.interaction.copyWith(
          favorited: result.active,
          favoriteCount: result.count,
        ),
      ),
    );
  });

  /// Posts a comment and reloads the list.
  Future<void> submitComment(String content) => _runSocialAction(() async {
    final String trimmed = content.trim();
    if (trimmed.isEmpty) {
      throw _AppStateException(_copy.commentEmpty);
    }
    final BookSummary book = _requireSelectedBook();
    await _dataSource.addComment(book, trimmed);
    final List<Comment> comments = await _dataSource.listComments(book);
    _set(_state.copyWith(comments: comments));
  });

  /// Opens the favourites screen; a signed-out user simply sees an empty list.
  Future<void> openFavorites() =>
      _runWithLoading(_ErrorContext.account, () async {
        final int requestVersion = ++_favoritesRequestVersion;
        if (_state.session == null) {
          _set(
            _state.copyWith(
              screen: AppScreen.favorites,
              favorites: const <BookSummary>[],
              errorMessage: null,
              isLoading: false,
            ),
          );
          return;
        }
        _set(_state.copyWith(screen: AppScreen.favorites));
        final List<BookSummary> favorites = await _dataSource.loadMyFavorites();
        if (requestVersion != _favoritesRequestVersion ||
            _state.screen != AppScreen.favorites) {
          return;
        }
        _set(
          _state.copyWith(
            screen: AppScreen.favorites,
            favorites: favorites,
            errorMessage: null,
            isLoading: false,
          ),
        );
      });

  /// Leaves the player and returns to the screen it was opened from.
  void backToDetail() {
    backToPlaybackSource();
  }

  /// Returns to the screen that opened the player.
  void backToPlaybackSource() {
    _set(
      _state.copyWith(
        screen: _state.playerReturnScreen ?? AppScreen.home,
        isLoading: false,
        errorMessage: null,
      ),
    );
  }

  /// Returns to the account tab and invalidates an in-flight favourites load.
  void backToAccount() {
    _favoritesRequestVersion++;
    _set(
      _state.copyWith(
        screen: AppScreen.account,
        isLoading: false,
        errorMessage: null,
      ),
    );
  }

  /// Returns to the funds tab.
  void backToFunds() {
    _set(
      _state.copyWith(
        screen: AppScreen.funds,
        isLoading: false,
        errorMessage: null,
      ),
    );
  }

  /// Runs a social action, mapping failures to an error message (and, for a
  /// 401, to the auth prompt).
  Future<void> _runSocialAction(Future<void> Function() block) async {
    _set(_state.copyWith(errorMessage: null, messageType: null));
    try {
      await block();
    } on Object catch (error) {
      // Kotlin rethrows CancellationException before this branch; Dart has no
      // cancellation, so every failure lands in the error path.
      _set(
        _state.copyWith(
          errorMessage: _userFacingErrorMessage(error, _ErrorContext.social),
          messageType: UiMessageType.error,
          authPromptVisible: _shouldPromptForLogin(error, _ErrorContext.social)
              ? true
              : _state.authPromptVisible,
        ),
      );
    }
  }

  // ---------------------------------------------------------------------------
  // Account, funds, VIP and withdrawals
  // ---------------------------------------------------------------------------

  /// Loads the whole account snapshot behind the global loading indicator.
  Future<void> loadAccountSnapshot() async {
    final int requestVersion = ++_accountRequestVersion;
    _set(_state.copyWith(screen: AppScreen.account));
    await _loadAccountSnapshotWithLoading(requestVersion);
  }

  /// Opens the account tab, loading or silently refreshing the snapshot.
  Future<void> openAccount() async {
    if (_state.session == null) {
      _set(
        _state.copyWith(
          screen: AppScreen.account,
          errorMessage: null,
          isLoading: false,
        ),
      );
      return;
    }
    if (!_hasAccountSnapshot(_state)) {
      final int requestVersion = ++_accountRequestVersion;
      _set(_state.copyWith(screen: AppScreen.account));
      await _loadAccountSnapshotWithLoading(requestVersion);
      return;
    }

    final int requestVersion = ++_accountRequestVersion;
    _set(_state.copyWith(screen: AppScreen.account, errorMessage: null));
    await _refreshAccountSilently(requestVersion);
  }

  /// Opens the funds tab.
  Future<void> openFunds() => _openAccountSection(AppScreen.funds);

  /// Opens the withdrawal tab.
  Future<void> openWithdrawal() => _openAccountSection(AppScreen.withdrawal);

  /// Opens the VIP recharge tab.
  Future<void> openVipRecharge() =>
      _openAccountSection(AppScreen.vipRecharge);

  /// Shared entry point of the three funds screens.
  Future<void> _openAccountSection(AppScreen targetScreen) async {
    if (_state.session == null) {
      _set(
        _state.copyWith(
          screen: targetScreen,
          errorMessage: null,
          isLoading: false,
        ),
      );
      return;
    }
    if (!_hasAccountSnapshot(_state)) {
      final int requestVersion = ++_accountRequestVersion;
      _set(_state.copyWith(screen: targetScreen));
      await _loadAccountSnapshotWithLoading(requestVersion, targetScreen);
      return;
    }

    final int requestVersion = ++_accountRequestVersion;
    _set(_state.copyWith(screen: targetScreen, errorMessage: null));
    await _refreshAccountSilently(requestVersion);
  }

  /// Wraps the parallel snapshot load in the global loading indicator.
  Future<void> _loadAccountSnapshotWithLoading(
    int requestVersion, [
    AppScreen targetScreen = AppScreen.account,
  ]) => _runWithLoading(
    _ErrorContext.account,
    () => _loadAccountSnapshotForAuthenticatedUser(requestVersion, targetScreen),
  );

  /// Loads every account-scoped resource in parallel.
  Future<void> _loadAccountSnapshotForAuthenticatedUser(
    int requestVersion, [
    AppScreen targetScreen = AppScreen.account,
  ]) async {
    // Parallelize all network requests to avoid 5+ second sequential loading.
    final Future<List<WatchRecord>> historyFuture = _dataSource
        .loadWatchHistory();
    final Future<PointAccount> pointsFuture = _dataSource.loadPointAccount();
    final Future<List<RechargeOrderSummary>> ordersFuture = _dataSource
        .loadOrders();
    final Future<WalletInfo?> walletFuture = _loadOptionalAccountData<WalletInfo?>(
      null,
      () => _dataSource.loadWallet(),
    );
    final Future<List<VipOrder>> vipOrdersFuture =
        _loadOptionalAccountData<List<VipOrder>>(
          const <VipOrder>[],
          () => _dataSource.loadVipOrders(),
        );
    final Future<WithdrawalSummary?> withdrawalSummaryFuture =
        _loadOptionalAccountData<WithdrawalSummary?>(
          null,
          () => _dataSource.loadWithdrawalSummary(),
        );
    final Future<List<WithdrawalRecord>> withdrawalsFuture =
        _loadOptionalAccountData<List<WithdrawalRecord>>(
          const <WithdrawalRecord>[],
          () => _dataSource.loadWithdrawals(),
        );
    // Kotlin's coroutineScope cancels the sibling requests when one of them
    // fails. Dart cannot cancel, so the three unguarded requests are marked as
    // observed to keep an abandoned failure out of the unhandled-error path;
    // the awaited result still throws exactly like `await` on the original.
    historyFuture.ignore();
    pointsFuture.ignore();
    ordersFuture.ignore();

    final List<WatchRecord> history = await historyFuture;
    final PointAccount points = await pointsFuture;
    final List<RechargeOrderSummary> orders = await ordersFuture;
    final WalletInfo? wallet = await walletFuture;
    final List<VipOrder> vipOrders = await vipOrdersFuture;
    final WithdrawalSummary? withdrawalSummary = await withdrawalSummaryFuture;
    final List<WithdrawalRecord> withdrawals = await withdrawalsFuture;
    if (requestVersion != _accountRequestVersion ||
        !_isAccountAreaScreen(_state.screen)) {
      return;
    }
    final Map<String, BookSummary> continueWatchingBooks =
        await _loadContinueWatchingBooks(history, requestVersion);
    if (requestVersion != _accountRequestVersion ||
        !_isAccountAreaScreen(_state.screen)) {
      return;
    }
    _set(
      _state.copyWith(
        screen: targetScreen,
        watchHistory: history,
        continueWatchingBooks: continueWatchingBooks,
        pointAccount: points,
        vipActive: points.vipActive,
        orders: orders,
        wallet: wallet,
        vipUntil: points.vipActive
            ? points.vipUntil ?? wallet?.vipUntil
            : null,
        vipPriceUsdt: wallet?.vipPriceUsdt ?? _state.vipPriceUsdt,
        vipCollectionAddress:
            wallet?.vipCollectionAddress ?? _state.vipCollectionAddress,
        vipOrders: vipOrders,
        withdrawalSummary: withdrawalSummary,
        withdrawals: withdrawals,
        isLoading: false,
      ),
    );
  }

  /// Sequential silent refresh used when a snapshot is already on screen; it
  /// aborts as soon as the request version or the screen changes.
  Future<void> _refreshAccountSilently(int requestVersion) async {
    final AppScreen targetScreen = _isAccountAreaScreen(_state.screen)
        ? _state.screen
        : AppScreen.account;
    try {
      final List<WatchRecord> history = await _dataSource.loadWatchHistory();
      if (requestVersion != _accountRequestVersion ||
          !_isAccountAreaScreen(_state.screen)) {
        return;
      }
      final PointAccount points = await _dataSource.loadPointAccount();
      if (requestVersion != _accountRequestVersion ||
          !_isAccountAreaScreen(_state.screen)) {
        return;
      }
      final List<RechargeOrderSummary> orders = await _dataSource.loadOrders();
      if (requestVersion != _accountRequestVersion ||
          !_isAccountAreaScreen(_state.screen)) {
        return;
      }
      final WalletInfo? wallet = await _loadOptionalAccountData<WalletInfo?>(
        null,
        () => _dataSource.loadWallet(),
      );
      if (requestVersion != _accountRequestVersion ||
          !_isAccountAreaScreen(_state.screen)) {
        return;
      }
      final WithdrawalSummary? withdrawalSummary =
          await _loadOptionalAccountData<WithdrawalSummary?>(
            null,
            () => _dataSource.loadWithdrawalSummary(),
          );
      if (requestVersion != _accountRequestVersion ||
          !_isAccountAreaScreen(_state.screen)) {
        return;
      }
      final List<WithdrawalRecord> withdrawals =
          await _loadOptionalAccountData<List<WithdrawalRecord>>(
            const <WithdrawalRecord>[],
            () => _dataSource.loadWithdrawals(),
          );
      if (requestVersion != _accountRequestVersion ||
          !_isAccountAreaScreen(_state.screen)) {
        return;
      }
      final Map<String, BookSummary> continueWatchingBooks =
          await _loadContinueWatchingBooks(history, requestVersion);
      if (requestVersion != _accountRequestVersion ||
          !_isAccountAreaScreen(_state.screen)) {
        return;
      }
      _set(
        _state.copyWith(
          screen: targetScreen,
          watchHistory: history,
          continueWatchingBooks: continueWatchingBooks,
          pointAccount: points,
          vipActive: points.vipActive,
          orders: orders,
          wallet: wallet,
          vipUntil: points.vipActive
              ? points.vipUntil ?? wallet?.vipUntil
              : null,
          vipPriceUsdt: wallet?.vipPriceUsdt ?? _state.vipPriceUsdt,
          withdrawalSummary: withdrawalSummary,
          withdrawals: withdrawals,
        ),
      );
    } on Object catch (error) {
      _ignoreFailure(error);
    }
  }

  /// Binds a cold wallet, then reloads the snapshot and closes the sheet.
  Future<void> bindWallet(
    String network,
    String walletAddress,
    String password,
  ) => _runAccountOperation(AccountOperation.walletMutation, () async {
    _requireAuthenticatedAccount();
    await _dataSource.bindWallet(network, walletAddress, password);
    await _reloadAccountSnapshotAfterAction(
      _copy.walletUpdatedMessage,
      walletMutationCompleted: true,
    );
  });

  /// Unbinds the cold wallet, then reloads the snapshot and closes the sheet.
  Future<void> unbindWallet(String password) =>
      _runAccountOperation(AccountOperation.walletMutation, () async {
        _requireAuthenticatedAccount();
        await _dataSource.unbindWallet(password);
        await _reloadAccountSnapshotAfterAction(
          _copy.walletUpdatedMessage,
          walletMutationCompleted: true,
        );
      });

  /// Creates the single active VIP order and refreshes the payment state.
  Future<void> createVipOrder() => _runAccountOperation(
    AccountOperation.walletMutation,
    () async {
      _requireAuthenticatedAccount();
      final int generation = _vipPaywallGeneration;
      final VipOrder createdOrder = await _dataSource.createVipOrder();
      if (generation != _vipPaywallGeneration) {
        return;
      }
      _set(_state.copyWith(latestVipOrder: createdOrder));
      final bool refreshed = await _refreshVipPaymentState(
        autoResume: true,
        generation: generation,
        waitForInFlight: true,
      );
      if (refreshed) {
        _set(
          _state.copyWith(
            isLoading: false,
            errorMessage: _copy.vipOrderCreatedMessage,
            messageType: UiMessageType.success,
          ),
        );
      }
    },
    showGlobalLoading: false,
  );

  /// Polls the VIP payment state (paywall "refresh" action).
  Future<void> refreshLatestVipOrder() =>
      _runWithoutLoading(_ErrorContext.account, () async {
        _requireAuthenticatedAccount();
        await _refreshVipPaymentState(
          autoResume: true,
          generation: _vipPaywallGeneration,
        );
      });

  /// Opens the global VIP paywall from the account area.
  Future<void> showVipPaywall() =>
      _runWithoutLoading(_ErrorContext.account, () async {
        _requireAuthenticatedAccount();
        await _openVipPaywall();
      });

  /// Closes the VIP paywall and invalidates in-flight payment refreshes.
  void dismissVipPaywall() {
    _set(
      _state.copyWith(
        vipPaywallVisible: false,
        vipPaywallLoading: false,
        pendingVipEpisode: null,
      ),
    );
    _pendingVipEpisodeRequestVersion = null;
    _vipPaywallGeneration++;
  }

  /// Submits a withdrawal request, then reloads the snapshot.
  Future<void> submitWithdrawal(int pointAmount) =>
      _runAccountOperation(AccountOperation.withdrawal, () async {
        _requireAuthenticatedAccount();
        await _dataSource.submitWithdrawal(pointAmount);
        await _reloadAccountSnapshotAfterAction(
          _copy.withdrawalSubmittedMessage,
          withdrawalSubmitted: true,
        );
      });

  /// Changes the password, which invalidates the local session and the saved
  /// credentials (the backend revokes every token).
  Future<void> changePassword(String oldPassword, String newPassword) =>
      _runAccountOperation(AccountOperation.passwordChange, () async {
        _requireAuthenticatedAccount();
        await _dataSource.changePassword(oldPassword, newPassword);
        await _dataSource.clearSession();
        await _dataSource.clearSavedCredentials();
        _set(
          _state.copyWith(
            isLoading: false,
            session: null,
            savedCredentials: null,
            pointAccount: null,
            orders: const <RechargeOrderSummary>[],
            wallet: null,
            vipActive: false,
            vipUntil: null,
            vipPaywallVisible: false,
            vipPaywallLoading: false,
            pendingVipEpisode: null,
            latestVipOrder: null,
            vipOrders: const <VipOrder>[],
            withdrawalSummary: null,
            withdrawals: const <WithdrawalRecord>[],
            watchHistory: const <WatchRecord>[],
            continueWatchingBooks: const <String, BookSummary>{},
            errorMessage: _copy.passwordChangedMessage,
            messageType: UiMessageType.success,
          ),
        );
        _pendingVipEpisodeRequestVersion = null;
        _vipPaywallGeneration++;
      });

  // ---------------------------------------------------------------------------
  // Global UI actions and diagnostics
  // ---------------------------------------------------------------------------

  /// Clears the top message.
  void clearError() {
    _set(_state.copyWith(errorMessage: null, messageType: null));
  }

  /// Shows the bottom auth panel.
  void showAuthPrompt() {
    _set(_state.copyWith(authPromptVisible: true));
  }

  /// Switches the auth panel to the register form.
  void showRegisterAuthMode() {
    _set(_state.copyWith(authMode: AuthMode.register));
  }

  /// Switches the auth panel back to the login form.
  void showLoginAuthMode() {
    _set(_state.copyWith(authMode: AuthMode.login));
  }

  /// Closes the auth panel and drops the queued playback/VIP intent.
  void dismissAuthPrompt() {
    _set(
      _state.copyWith(
        authPromptVisible: false,
        pendingPlaybackEpisode: null,
        pendingVipEpisode: null,
        authMode: AuthMode.login,
        captchaId: '',
        captchaImageBase64: '',
      ),
    );
    _pendingVipEpisodeRequestVersion = null;
    _vipPaywallGeneration++;
  }

  /// Probes the backend health endpoint.
  Future<void> checkApiHealth() async {
    _set(_state.copyWith(isLoading: true, errorMessage: null, messageType: null));
    try {
      final ApiHealthStatus status = await _dataSource.checkSystemHealth();
      _set(_state.copyWith(apiHealthStatus: status, isLoading: false));
    } on Object catch (error) {
      // Kotlin rethrows CancellationException after clearing the loading flag;
      // Dart has no cancellation, so this is the single failure path.
      final String message = _userFacingErrorMessage(
        error,
        _ErrorContext.diagnostic,
      );
      _set(
        _state.copyWith(
          apiHealthStatus: ApiHealthStatus(status: 'DOWN', service: message),
          errorMessage: message,
          isLoading: false,
        ),
      );
    }
  }

  /// Signs out: clears the local session but keeps the remembered credentials
  /// and the language preference.
  Future<void> logout() =>
      _runWithLoading(_ErrorContext.account, () async {
        await _dataSource.clearSession();
        _set(
          AppUiState(
            apiBaseUrl: _dataSource.apiBaseUrl,
            savedCredentials: _state.savedCredentials,
            language: _state.language,
          ),
        );
        _pendingVipEpisodeRequestVersion = null;
        _vipPaywallGeneration++;
      });

  // ---------------------------------------------------------------------------
  // Request plumbing
  // ---------------------------------------------------------------------------

  /// Runs [block] behind the global loading indicator and dispatches failures.
  Future<void> _runWithLoading(
    _ErrorContext errorContext,
    Future<void> Function() block,
  ) async {
    _set(_state.copyWith(isLoading: true, errorMessage: null, messageType: null));
    try {
      await block();
    } on Object catch (error) {
      // Kotlin rethrows CancellationException here so a cancelled coroutine is
      // never reported as a request failure. Dart has no cancellation, so an
      // abandoned request simply takes the normal error path.
      await _handleRequestError(error, errorContext);
    }
  }

  /// Runs [block] without the global loading indicator.
  Future<void> _runWithoutLoading(
    _ErrorContext errorContext,
    Future<void> Function() block,
  ) async {
    try {
      await block();
    } on Object catch (error) {
      // Same cancellation substitution as _runWithLoading.
      await _handleRequestError(error, errorContext);
    }
  }

  /// Central failure classification: session clearing, VIP payment state
  /// clearing, message text, message trigger and the auth prompt.
  Future<void> _handleRequestError(
    Object error,
    _ErrorContext errorContext,
  ) async {
    final bool blockedAccount = _isBlockedAccountError(error);
    final bool paywallAuthenticationFailed =
        _state.vipPaywallVisible &&
        error is ApiClientException &&
        error.statusCode == 401;
    // A 401 from a protected call means the local Bearer token is gone, so the
    // local session must be cleared (otherwise the account tab keeps claiming
    // the user is signed in). The remembered credentials are kept.
    final bool expiredSession = _isExpiredSessionError(error, errorContext);
    final bool clearVipPaymentState =
        blockedAccount || paywallAuthenticationFailed;
    final bool clearLocalSession = blockedAccount || expiredSession;
    if (clearLocalSession) {
      await _dataSource.clearSession();
    }
    bool authPromptVisible = _state.authPromptVisible;
    if (blockedAccount) {
      authPromptVisible = false;
    } else if (_shouldPromptForLogin(error, errorContext)) {
      authPromptVisible = true;
    }
    _set(
      _state.copyWith(
        session: clearLocalSession ? null : _state.session,
        isLoading: false,
        errorMessage: _userFacingErrorMessage(error, errorContext),
        messageTrigger: _state.messageTrigger + 1,
        messageType: UiMessageType.error,
        authPromptVisible: authPromptVisible,
        pendingPlaybackEpisode: blockedAccount
            ? null
            : _state.pendingPlaybackEpisode,
        pointAccount: clearVipPaymentState ? null : _state.pointAccount,
        vipActive: clearVipPaymentState ? false : _state.vipActive,
        vipUntil: clearVipPaymentState ? null : _state.vipUntil,
        vipPaywallVisible: clearVipPaymentState
            ? false
            : _state.vipPaywallVisible,
        vipPaywallLoading: clearVipPaymentState
            ? false
            : _state.vipPaywallLoading,
        pendingVipEpisode: clearVipPaymentState
            ? null
            : _state.pendingVipEpisode,
        latestVipOrder: clearVipPaymentState ? null : _state.latestVipOrder,
        vipOrders: clearVipPaymentState
            ? const <VipOrder>[]
            : _state.vipOrders,
      ),
    );
    if (clearVipPaymentState) {
      _pendingVipEpisodeRequestVersion = null;
      _vipPaywallGeneration++;
    }
  }

  /// Serializes account mutations.
  ///
  /// A second call while one is running returns silently instead of queueing -
  /// the Kotlin `tryLock` behaviour that stops duplicate submissions.
  Future<void> _runAccountOperation(
    AccountOperation operation,
    Future<void> Function() block, {
    bool showGlobalLoading = true,
  }) async {
    if (!_accountOperationMutex.tryLock()) {
      return;
    }
    _set(_state.copyWith(accountOperation: operation));
    try {
      if (showGlobalLoading) {
        await _runWithLoading(_ErrorContext.account, block);
      } else {
        await _runWithoutLoading(_ErrorContext.account, block);
      }
    } finally {
      _set(_state.copyWith(accountOperation: null));
      _accountOperationMutex.unlock();
    }
  }

  /// Loads the point account for the playback gate.
  ///
  /// A 401/403 must reach the caller (it means the session or the VIP state is
  /// wrong); every other failure degrades to "no snapshot".
  Future<PointAccount?> _loadPointAccountSilently() async {
    PointAccount? pointAccount;
    try {
      pointAccount = await _dataSource.loadPointAccount();
    } on ApiClientException catch (error) {
      if (error.statusCode == 401 || error.statusCode == 403) {
        rethrow;
      }
      pointAccount = null;
    } on Object catch (_) {
      // Kotlin rethrows CancellationException before this branch; Dart has no
      // cancellation, so an abandoned request simply yields no snapshot.
      pointAccount = null;
    }
    final PointAccount? loaded = pointAccount;
    if (loaded != null) {
      _set(
        _state.copyWith(
          pointAccount: loaded,
          vipActive: loaded.vipActive,
          vipUntil: loaded.vipActive ? loaded.vipUntil : null,
        ),
      );
    }
    return loaded;
  }

  /// Opens the shared VIP paywall and refreshes its payment state.
  Future<void> _openVipPaywall({
    EpisodeSummary? episode,
    int? requestVersion,
    bool allowImmediateResume = true,
  }) async {
    final int generation = ++_vipPaywallGeneration;
    _pendingVipEpisodeRequestVersion = requestVersion;
    _set(
      _state.copyWith(
        vipPaywallVisible: true,
        vipPaywallLoading: true,
        pendingVipEpisode: episode,
        isLoading: false,
        errorMessage: null,
        messageType: null,
      ),
    );
    await _refreshVipPaymentState(
      autoResume: allowImmediateResume && episode != null,
      generation: generation,
      waitForInFlight: true,
    );
  }

  /// Reloads the VIP payment state under a paywall generation guard.
  ///
  /// Returns whether the refresh actually applied (a stale generation returns
  /// false, a skipped concurrent refresh returns true).
  Future<bool> _refreshVipPaymentState({
    required bool autoResume,
    required int generation,
    bool waitForInFlight = false,
  }) async {
    if (waitForInFlight) {
      await _vipPaymentRefreshMutex.lock();
    } else if (!_vipPaymentRefreshMutex.tryLock()) {
      return true;
    }
    if (generation == _vipPaywallGeneration) {
      _set(_state.copyWith(vipPaywallLoading: true));
    }
    EpisodeSummary? episodeToResume;
    bool refreshed = false;
    try {
      final PointAccount pointAccount = await _dataSource.loadPointAccount();
      final VipOrder? latest = await _dataSource.loadLatestVipOrder();
      final WalletInfo? wallet = await _loadOptionalAccountData<WalletInfo?>(
        _state.wallet,
        () => _dataSource.loadWallet(),
      );
      final List<VipOrder> vipOrders =
          await _loadOptionalAccountData<List<VipOrder>>(
            _state.vipOrders,
            () => _dataSource.loadVipOrders(),
          );
      if (generation != _vipPaywallGeneration) {
        refreshed = false;
      } else {
        final EpisodeSummary? pendingEpisode = _state.pendingVipEpisode;
        final int? pendingRequestVersion = _pendingVipEpisodeRequestVersion;
        _set(
          _state.copyWith(
            pointAccount: pointAccount,
            vipActive: pointAccount.vipActive,
            vipUntil: pointAccount.vipActive
                ? pointAccount.vipUntil ?? wallet?.vipUntil
                : null,
            wallet: wallet,
            vipPriceUsdt: wallet?.vipPriceUsdt ?? _state.vipPriceUsdt,
            vipCollectionAddress:
                wallet?.vipCollectionAddress ?? _state.vipCollectionAddress,
            latestVipOrder: latest,
            vipOrders: vipOrders,
            vipPaywallLoading: false,
            isLoading: false,
          ),
        );
        if (autoResume && pointAccount.vipActive) {
          episodeToResume =
              pendingEpisode != null &&
                  pendingRequestVersion == _playbackRequestVersion
              ? pendingEpisode
              : null;
          _set(
            _state.copyWith(
              vipPaywallVisible: false,
              vipPaywallLoading: false,
              pendingVipEpisode: null,
            ),
          );
          _pendingVipEpisodeRequestVersion = null;
        }
        refreshed = true;
      }
    } on ApiClientException catch (error) {
      if (generation != _vipPaywallGeneration) {
        refreshed = false;
      } else {
        _set(_state.copyWith(vipPaywallLoading: false, isLoading: false));
        if (error.statusCode == 401 || error.statusCode == 403) {
          rethrow;
        }
        _set(
          _state.copyWith(
            errorMessage: _userFacingErrorMessage(error, _ErrorContext.account),
            messageTrigger: _state.messageTrigger + 1,
            messageType: UiMessageType.error,
          ),
        );
        refreshed = false;
      }
    } on Object catch (error) {
      // Kotlin's CancellationException branch restored the loading flags and
      // rethrew; without cancellation in Dart this is the generic failure path.
      if (generation != _vipPaywallGeneration) {
        refreshed = false;
      } else {
        _set(
          _state.copyWith(
            vipPaywallLoading: false,
            isLoading: false,
            errorMessage: _userFacingErrorMessage(error, _ErrorContext.account),
            messageTrigger: _state.messageTrigger + 1,
            messageType: UiMessageType.error,
          ),
        );
        refreshed = false;
      }
    } finally {
      _vipPaymentRefreshMutex.unlock();
    }
    if (episodeToResume != null) {
      await openPlayer(episodeToResume);
    }
    return refreshed;
  }

  /// The selected drama, or an internal contract error.
  BookSummary _requireSelectedBook() {
    final BookSummary? book = _state.selectedBook;
    if (book == null) {
      throw _AppStateException(_copy.noDramaSelected);
    }
    return book;
  }

  /// Whether [requestVersion] is still the newest playback request.
  bool _isActivePlaybackRequest(int requestVersion) =>
      requestVersion == _playbackRequestVersion;

  /// Whether the account tabs already have something to render.
  bool _hasAccountSnapshot(AppUiState state) =>
      state.pointAccount != null ||
      state.watchHistory.isNotEmpty ||
      state.orders.isNotEmpty ||
      state.wallet != null;

  /// Whether [screen] belongs to the account/funds area.
  bool _isAccountAreaScreen(AppScreen screen) =>
      screen == AppScreen.account ||
      screen == AppScreen.funds ||
      screen == AppScreen.withdrawal ||
      screen == AppScreen.vipRecharge;

  /// Requires a signed-in user; otherwise opens the auth panel and fails.
  void _requireAuthenticatedAccount() {
    if (_state.session == null) {
      _set(_state.copyWith(authPromptVisible: true, isLoading: false));
      throw _AppStateException(_copy.sessionExpired);
    }
  }

  /// Reloads the account snapshot after a successful mutation, then reports the
  /// success message and the sheet-closing version counters.
  Future<void> _reloadAccountSnapshotAfterAction(
    String successMessage, {
    bool walletMutationCompleted = false,
    bool withdrawalSubmitted = false,
  }) async {
    final int requestVersion = ++_accountRequestVersion;
    final AppScreen targetScreen = _isAccountAreaScreen(_state.screen)
        ? _state.screen
        : AppScreen.account;
    _set(_state.copyWith(screen: targetScreen));
    try {
      await _loadAccountSnapshotForAuthenticatedUser(
        requestVersion,
        targetScreen,
      );
    } on Object catch (error) {
      _ignoreFailure(error);
    }
    if (requestVersion == _accountRequestVersion &&
        _isAccountAreaScreen(_state.screen)) {
      _set(
        _state.copyWith(
          errorMessage: successMessage,
          messageType: UiMessageType.success,
          isLoading: false,
          walletMutationVersion: walletMutationCompleted
              ? _state.walletMutationVersion + 1
              : _state.walletMutationVersion,
          withdrawalSubmissionVersion: withdrawalSubmitted
              ? _state.withdrawalSubmissionVersion + 1
              : _state.withdrawalSubmissionVersion,
        ),
      );
    }
  }

  /// Loads optional account data, falling back to [fallback] on any failure.
  Future<T> _loadOptionalAccountData<T>(
    T fallback,
    Future<T> Function() loader,
  ) async {
    try {
      return await loader();
    } on Object catch (_) {
      // Kotlin rethrows CancellationException before returning the fallback;
      // Dart has no cancellation, so every failure degrades to the fallback.
      return fallback;
    }
  }

  /// Loads the posters of the first two distinct dramas of the watch history.
  Future<Map<String, BookSummary>> _loadContinueWatchingBooks(
    List<WatchRecord> history,
    int requestVersion,
  ) async {
    final Map<String, BookSummary> books = <String, BookSummary>{};
    final List<String> bookIds = <String>[];
    for (final WatchRecord record in history) {
      if (bookIds.contains(record.bookId)) {
        continue;
      }
      bookIds.add(record.bookId);
      if (bookIds.length == 2) {
        break;
      }
    }
    for (final String bookId in bookIds) {
      if (requestVersion != _accountRequestVersion ||
          !_isAccountAreaScreen(_state.screen)) {
        return books;
      }
      try {
        books[bookId] = await _dataSource.loadBook(bookId);
      } on Object catch (error) {
        _ignoreFailure(error);
      }
    }
    return books;
  }

  /// Resumes the recorded episode, or the next one once it is finished.
  EpisodeSummary? _resumeEpisodeForRecord(
    List<EpisodeSummary> episodes,
    WatchRecord record,
  ) {
    final int currentIndex = episodes.indexWhere(
      (EpisodeSummary episode) => episode.number == record.episode,
    );
    if (currentIndex < 0) {
      return null;
    }
    if (record.progressPercent < 100) {
      return episodes[currentIndex];
    }
    return currentIndex + 1 < episodes.length
        ? episodes[currentIndex + 1]
        : episodes[currentIndex];
  }

  /// The screen the player returns to when it is closed.
  AppScreen _playbackReturnScreenFor(AppScreen screen) {
    switch (screen) {
      case AppScreen.search:
        return AppScreen.search;
      case AppScreen.favorites:
        return AppScreen.favorites;
      case AppScreen.account:
        return AppScreen.account;
      case AppScreen.login:
      case AppScreen.home:
      case AppScreen.detail:
      case AppScreen.player:
      case AppScreen.funds:
      case AppScreen.withdrawal:
      case AppScreen.vipRecharge:
        return AppScreen.home;
    }
  }

  /// Falls back to the first episode when the resume pick is unusable.
  EpisodeSummary? _playbackFallbackEpisode(
    List<EpisodeSummary> episodes,
    EpisodeSummary? fallbackEpisode,
  ) {
    if (episodes.isEmpty) {
      return fallbackEpisode;
    }
    if (fallbackEpisode == null) {
      return episodes.first;
    }
    final int index = episodes.indexWhere(
      (EpisodeSummary episode) =>
          episode.number == fallbackEpisode.number &&
          episode.chapterId == fallbackEpisode.chapterId,
    );
    return index >= 0 ? episodes[index] : episodes.first;
  }

  /// Whether a failure should open the auth panel.
  bool _shouldPromptForLogin(Object error, _ErrorContext context) =>
      error is ApiClientException &&
      error.statusCode == 401 &&
      (context == _ErrorContext.account ||
          context == _ErrorContext.playback ||
          context == _ErrorContext.social);

  /// A wallet/withdrawal "current password" failure is not an expired session.
  bool _isInvalidCurrentPasswordError(Object error) =>
      error is ApiClientException &&
      error.message.toLowerCase().contains('invalid current password');

  /// Whether a 401 means the local session is gone.
  ///
  /// Only protected actions qualify: login/register failures and the "current
  /// password" errors keep the existing session.
  bool _isExpiredSessionError(Object error, _ErrorContext context) =>
      error is ApiClientException &&
      error.statusCode == 401 &&
      context != _ErrorContext.login &&
      context != _ErrorContext.register &&
      !_isInvalidCurrentPasswordError(error);

  /// Whether the backend refused the call because the episode needs VIP.
  bool _isVipRequiredError(Object error) =>
      error is ApiClientException &&
      error.statusCode == 403 &&
      error.message.toLowerCase().contains('vip required');

  /// Whether the account was disabled or blacklisted.
  bool _isBlockedAccountError(Object error) {
    final String message = error is ApiClientException
        ? error.message.toLowerCase()
        : '';
    return error is ApiClientException &&
        error.statusCode == 403 &&
        (message.contains('disabled') ||
            message.contains('blacklisted') ||
            message.contains('user status'));
  }

  /// The user-facing copy for [error] under [context].
  String _userFacingErrorMessage(Object error, _ErrorContext context) {
    final String rawMessage = _rawMessageOf(error).trim();
    final String normalizedMessage = rawMessage.toLowerCase();
    if (_isBlockedAccountError(error)) {
      return _copy.accountBlocked;
    }
    if (context == _ErrorContext.content) {
      return _copy.contentLoadFailed;
    }
    if (context == _ErrorContext.register &&
        (normalizedMessage.contains('captcha') ||
            normalizedMessage.contains('invalid verification code'))) {
      return _copy.invalidCaptchaMessage;
    }
    if (context == _ErrorContext.register &&
        error is ApiClientException &&
        error.statusCode == 400 &&
        normalizedMessage.contains('bad request')) {
      return _copy.registerBadRequestMessage;
    }
    if (error is ApiClientException &&
        error.statusCode == 401 &&
        (context == _ErrorContext.login || context == _ErrorContext.register)) {
      return _copy.credentialErrorMessage;
    }
    if (error is ApiClientException &&
        error.statusCode == 409 &&
        context == _ErrorContext.register) {
      return _copy.usernameExistsMessage;
    }
    if (_isInvalidCurrentPasswordError(error)) {
      return _copy.invalidCurrentPasswordMessage;
    }
    if (error is ApiClientException && error.statusCode == 401) {
      return _copy.sessionExpired;
    }
    if ((context == _ErrorContext.login || context == _ErrorContext.register) &&
        (normalizedMessage.contains('invalid username or password') ||
            normalizedMessage.contains('invalid credentials'))) {
      return _copy.credentialErrorMessage;
    }
    // Last resort: the copy has to be human-readable. A body that leaked
    // through a gateway (a JSON document, an HTML error page, a stack trace) or
    // one that is simply too long is never shown verbatim - the state layer
    // substitutes its own sentence. A 5xx says the server is the problem, other
    // codes keep the generic request failure.
    final String safeMessage = sanitizeUpstreamMessage(rawMessage);
    if (safeMessage.isEmpty) {
      return error is ApiClientException && error.statusCode >= 500
          ? _copy.contentLoadFailed
          : _copy.requestFailed;
    }
    return safeMessage;
  }

  /// The raw message of [error]; Kotlin reads `Throwable.message`.
  String _rawMessageOf(Object error) {
    if (error is ApiClientException) {
      return error.message;
    }
    if (error is _AppStateException) {
      return error.message;
    }
    return error.toString();
  }
}

/// The body of the staged 100% completion report.
class _PendingCompletionReport {
  const _PendingCompletionReport(
    this.requestVersion,
    this.positionSeconds,
    this.durationSeconds,
  );

  final int requestVersion;
  final int positionSeconds;
  final int durationSeconds;
}

/// The private error the controller raises for its own contract violations.
///
/// Stands in for the Kotlin `IllegalStateException`/`IllegalArgumentException`:
/// its message is user-facing copy and is surfaced by
/// `_userFacingErrorMessage` exactly like the original.
class _AppStateException implements Exception {
  const _AppStateException(this.message);

  final String message;

  @override
  String toString() => message;
}

/// A minimal async mutex with the Kotlin `Mutex` semantics used here.
///
/// `kotlinx.coroutines.sync.Mutex` is not available in Dart and this port may
/// not add packages, so `lock`, `tryLock` and `unlock` are hand-written. The
/// waiters retry on release, so acquisition order is not guaranteed - the
/// controller only relies on mutual exclusion, never on fairness.
class _AsyncMutex {
  Completer<void>? _held;

  /// Acquires the mutex, waiting until the current holder releases it.
  Future<void> lock() async {
    while (!tryLock()) {
      final Completer<void>? held = _held;
      if (held != null) {
        await held.future;
      }
    }
  }

  /// Acquires the mutex only when it is free.
  ///
  /// Returns whether the caller now holds it; a `false` result means another
  /// operation is running and the caller must not queue.
  bool tryLock() {
    if (_held != null) {
      return false;
    }
    _held = Completer<void>();
    return true;
  }

  /// Releases the mutex and wakes up every waiter.
  void unlock() {
    final Completer<void>? held = _held;
    _held = null;
    if (held != null && !held.isCompleted) {
      held.complete();
    }
  }
}

/// Runs a request that must fail silently, returning null instead.
///
/// Mirrors the Kotlin `try { ... } catch (_: Throwable) { null }` blocks used
/// for optional side requests; Kotlin rethrows `CancellationException` first
/// and Dart has no cancellation, so every failure lands here.
Future<T?> _silently<T>(Future<T> Function() request) async {
  try {
    return await request();
  } on Object catch (_) {
    return null;
  }
}

/// Swallows [error], mirroring the Kotlin `catch (_: Throwable) {}` blocks that
/// let a silent or best-effort request fail without surfacing anything.
void _ignoreFailure(Object error) {}

/// The error-context enum of the Kotlin controller.
enum _ErrorContext { login, register, content, account, playback, social, diagnostic }

/// Every user-facing string the controller can emit.
///
/// `app-core` leaks Chinese user-facing literals for the first eight entries;
/// this app is English-only, so each one is translated in place. The trigger
/// conditions, message presence, [UiMessageType] and session-clearing
/// behaviour are unchanged, and `docs/controller-parity.md` lists every
/// original literal next to its replacement for the parity audit. The order
/// below matches the Kotlin source.
class _ControllerCopy {
  const _ControllerCopy();

  /// Kotlin: `"内容暂时加载失败，可以稍后刷新。"` (6 call sites).
  final String contentLoadFailed = 'Content failed to load. Try refreshing later.';

  /// Kotlin: `"积分状态暂时加载失败，播放不受影响。"` (player snapshot).
  final String rewardStatusLoadFailed =
      'Reward status failed to load. Playback is unaffected.';

  /// Kotlin: `"未选择剧集"` (`refreshPlaybackUrl`, `requireSelectedBook`).
  final String noDramaSelected = 'No drama selected.';

  /// Kotlin: `"未选择分集"` (`refreshPlaybackUrl`).
  final String noEpisodeSelected = 'No episode selected.';

  /// Kotlin: `"评论内容不能为空"` (`submitComment`).
  final String commentEmpty = 'Comment cannot be empty.';

  /// Kotlin: `"登录状态已失效，请重新登录。"` (`requireAuthenticatedAccount`,
  /// `userFacingErrorMessage` 401 fallback).
  final String sessionExpired = 'Your session has expired. Sign in again.';

  /// Kotlin: `"账号不可操作，请联系支持。"` (blocked account 403).
  final String accountBlocked = 'This account is unavailable. Contact support.';

  /// Kotlin: `"请求失败"` (blank-message fallback).
  final String requestFailed = 'Request failed.';

  /// Kotlin: `invalidCaptchaMessage()` - already English.
  final String invalidCaptchaMessage = 'Captcha answer is incorrect.';

  /// Kotlin: `registerBadRequestMessage()` - already English.
  final String registerBadRequestMessage =
      'Check username, password, and captcha answer.';

  /// Kotlin: `credentialErrorMessage()` - already English.
  final String credentialErrorMessage = 'Invalid username or password.';

  /// Kotlin: `usernameExistsMessage()` - already English.
  final String usernameExistsMessage = 'Username already exists.';

  /// Kotlin: `invalidCurrentPasswordMessage()` - already English.
  final String invalidCurrentPasswordMessage = 'Current password is incorrect.';

  /// Kotlin: `vipOrderCreatedMessage()` - already English.
  final String vipOrderCreatedMessage =
      'VIP order created. Submit payment to activate VIP.';

  /// Kotlin: `passwordChangedMessage()` - already English.
  final String passwordChangedMessage =
      'Password changed. Sign in again with the new password.';

  /// Kotlin: `walletUpdatedMessage()` - already English.
  final String walletUpdatedMessage = 'Wallet updated.';

  /// Kotlin: `withdrawalSubmittedMessage()` - already English.
  final String withdrawalSubmittedMessage = 'Withdrawal submitted.';
}

/// The single copy table instance used by the controller.
const _ControllerCopy _copy = _ControllerCopy();
