/// HTTP implementation of [ReelShortApiClient].
///
/// Port of `android-app/app-core/src/main/kotlin/com/reelshort/app/network/OkHttpReelShortApiClient.kt`.
/// Endpoint paths, query parameters, request bodies, authentication rules and
/// error classification are identical to the Kotlin client; see
/// `docs/parity-matrix.md` for the two documented deviations (query component
/// escaping and the client-side request deadline).
library;

import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../../config/api_config.dart';
import '../models/app_models.dart';
import 'api_client_exception.dart';
import 'api_dtos.dart';
import 'json_read.dart';
import 'reel_short_api_client.dart';

/// Backend error envelope projection.
class _BackendError {
  const _BackendError(this.code, this.message);

  final int? code;
  final String message;
}

/// The prepared request plus its authentication flag.
///
/// The flag rides along on the prepared request because every call site
/// already writes `_send(_request(..., authenticated: true))`; a bare
/// `http.Request` has nowhere to keep it and the earlier draft dropped it
/// on the floor, which left every GET endpoint permanently anonymous.
typedef _PreparedRequest = (http.Request request, bool authenticated);

class HttpReelShortApiClient implements ReelShortApiClient {
  HttpReelShortApiClient({
    ApiConfig? config,
    http.Client? httpClient,
    Future<String?> Function()? tokenProvider,
    this.requestTimeout = const Duration(seconds: 60),
  }) : _config = config ?? ApiConfig.defaultConfig(),
       _httpClient = httpClient ?? http.Client(),
       _tokenProvider = tokenProvider ?? _noToken;

  static const String _jsonMediaType = 'application/json; charset=utf-8';

  /// Whole-request deadline.
  ///
  /// OkHttp applies a 10 s read timeout by default while the `http` package has
  /// none; a bounded deadline is used instead of an unbounded wait so the UI can
  /// never hang forever. The value is comfortably above the backend's own 60 s
  /// upstream read timeout.
  final Duration requestTimeout;

  final ApiConfig _config;
  final http.Client _httpClient;
  final Future<String?> Function() _tokenProvider;

  static Future<String?> _noToken() async => null;

  // ---------------------------------------------------------------------------
  // Endpoints
  // ---------------------------------------------------------------------------

  @override
  Future<ApiHealthStatus> checkSystemHealth() async {
    final Object? data = await _send(
      _request('GET', Uri.parse(_config.systemHealthUrl)),
    );
    return ApiHealthStatusDto.fromJson(
      asJsonObject(data, 'health'),
    ).toDomain();
  }

  @override
  Future<AuthSession> login(String username, String password) async {
    final Object? data = await _postJson(
      '/auth/login',
      AuthRequestDto(username: username, password: password).toJson(),
    );
    return AuthSessionDto.fromJson(asJsonObject(data, 'session')).toDomain();
  }

  @override
  Future<AuthSession> register(
    String username,
    String password,
    String captchaId,
    String captchaAnswer,
  ) async {
    final Object? data = await _postJson(
      '/auth/register',
      RegisterRequestDto(
        username: username,
        password: password,
        captchaId: captchaId,
        captchaAnswer: captchaAnswer,
      ).toJson(),
    );
    return AuthSessionDto.fromJson(asJsonObject(data, 'session')).toDomain();
  }

  @override
  Future<CaptchaChallenge> fetchCaptcha() async {
    final Object? data = await _send(
      _request('GET', _segmentedUri(const <String>['auth', 'captcha'])),
    );
    return CaptchaResponseDto.fromJson(
      asJsonObject(data, 'captcha'),
    ).toDomain();
  }

  @override
  Future<void> changePassword(String oldPassword, String newPassword) async {
    await _postJson(
      '/auth/password/change',
      PasswordChangeRequestDto(
        oldPassword: oldPassword,
        newPassword: newPassword,
      ).toJson(),
      authenticated: true,
    );
  }

  @override
  Future<List<BookSummary>> getHomeShelf(String locale) async {
    final Object? data = await _send(
      _request(
        'GET',
        _segmentedUri(const <String>['home', 'recommend'], <String, String>{
          'locale': locale,
        }),
      ),
    );
    return _mapList(data, 'home shelf', ContentBookDto.fromJson)
        .map((ContentBookDto dto) => dto.toDomain())
        .toList(growable: false);
  }

  @override
  Future<List<BookSummary>> search(String query, String locale) async {
    final Object? data = await _send(
      _request(
        'GET',
        _segmentedUri(const <String>['content', 'search'], <String, String>{
          'keywords': query,
          'locale': locale,
        }),
      ),
    );
    return _mapList(data, 'search results', ContentBookDto.fromJson)
        .map((ContentBookDto dto) => dto.toDomain())
        .toList(growable: false);
  }

  @override
  Future<BookSummary> getBook(String bookId, String locale) async {
    final Object? data = await _send(
      _request(
        'GET',
        _segmentedUri(<String>['content', 'books', bookId], <String, String>{
          'locale': locale,
        }),
      ),
    );
    return ContentBookDto.fromJson(asJsonObject(data, 'book')).toDomain();
  }

  @override
  Future<List<EpisodeSummary>> getEpisodes(
    String bookId,
    String filteredTitle,
    String locale,
  ) async {
    final Object? data = await _send(
      _request(
        'GET',
        _segmentedUri(<String>[
          'content',
          'books',
          bookId,
          'episodes',
        ], <String, String>{'filteredTitle': filteredTitle, 'locale': locale}),
      ),
    );
    return _mapList(data, 'episodes', ContentEpisodeDto.fromJson)
        .map((ContentEpisodeDto dto) => dto.toDomain())
        .toList(growable: false);
  }

  @override
  Future<VideoUrl> getVideoUrl(
    String bookId,
    int episode,
    String filteredTitle,
    String chapterId,
    String locale,
  ) async {
    final Object? data = await _send(
      _request(
        'GET',
        _segmentedUri(<String>[
          'content',
          'books',
          bookId,
          'episodes',
          '$episode',
          'play',
        ], <String, String>{
          'filteredTitle': filteredTitle,
          'chapterId': chapterId,
          'locale': locale,
        }),
        authenticated: true,
      ),
    );
    return ContentVideoDto.fromJson(asJsonObject(data, 'video')).toDomain();
  }

  @override
  Future<WatchEpisodeSnapshot> getEpisodeSnapshot(
    String bookId,
    int episode,
  ) async {
    final Object? data = await _send(
      _request(
        'GET',
        _segmentedUri(<String>[
          'watch',
          'books',
          bookId,
          'episodes',
          '$episode',
          'snapshot',
        ]),
        authenticated: true,
      ),
    );
    return WatchEpisodeSnapshotDto.fromJson(
      asJsonObject(data, 'snapshot'),
    ).toDomain();
  }

  @override
  Future<WatchProgressReport> reportWatchProgress({
    required String bookId,
    required String bookTitle,
    required String filteredTitle,
    required int episode,
    required String chapterId,
    required int positionSeconds,
    required int durationSeconds,
  }) async {
    final Object? data = await _postJson(
      '/watch/progress',
      WatchProgressRequestDto(
        bookId: bookId,
        bookTitle: bookTitle,
        filteredTitle: filteredTitle,
        episodeNum: episode,
        chapterId: chapterId,
        positionSeconds: positionSeconds,
        durationSeconds: durationSeconds,
      ).toJson(),
      authenticated: true,
    );
    return WatchRecordDto.fromJson(
      asJsonObject(data, 'watch progress'),
    ).toProgressReport();
  }

  @override
  Future<List<WatchRecord>> getWatchHistory() async {
    final Object? data = await _send(
      _request('GET', _stringUri('/watch/history'), authenticated: true),
    );
    return _mapList(data, 'watch history', WatchRecordDto.fromJson)
        .map((WatchRecordDto dto) => dto.toWatchRecord())
        .toList(growable: false);
  }

  @override
  Future<PointAccount> getPointAccount() async {
    final Object? accountData = await _send(
      _request('GET', _stringUri('/points/account'), authenticated: true),
    );
    final Object? recordsData = await _send(
      _request('GET', _stringUri('/points/records'), authenticated: true),
    );
    final PointAccountDto account = PointAccountDto.fromJson(
      asJsonObject(accountData, 'point account'),
    );
    final List<PointRecordDto> records = _mapList(
      recordsData,
      'point records',
      PointRecordDto.fromJson,
    );
    return PointAccount(
      balance: account.balance,
      frozenPoints: account.frozenPoints,
      availablePoints: account.availablePoints,
      records: records
          .map((PointRecordDto dto) => dto.toDomain())
          .toList(growable: false),
      vipActive: account.vip,
      vipUntil: account.vipUntil,
      todayCreditedPoints: account.todayCreditedPoints,
    );
  }

  @override
  Future<List<RechargeOrderSummary>> getOrders() async {
    final Object? data = await _send(
      _request('GET', _stringUri('/orders'), authenticated: true),
    );
    return _mapList(data, 'orders', RechargeOrderDto.fromJson)
        .map((RechargeOrderDto dto) => dto.toDomain())
        .toList(growable: false);
  }

  @override
  Future<WalletInfo> getWallet() async {
    final Object? data = await _send(
      _request('GET', _stringUri('/wallet'), authenticated: true),
    );
    return WalletResponseDto.fromJson(asJsonObject(data, 'wallet')).toDomain();
  }

  @override
  Future<WalletInfo> bindWallet(
    String network,
    String walletAddress,
    String password,
  ) async {
    final Object? data = await _send(
      _request(
        'PUT',
        _stringUri('/wallet'),
        body: jsonEncode(
          WalletBindRequestDto(
            network: network,
            walletAddress: walletAddress,
            password: password,
          ).toJson(),
        ),
        authenticated: true,
      ),
    );
    return WalletResponseDto.fromJson(asJsonObject(data, 'wallet')).toDomain();
  }

  @override
  Future<WalletInfo> unbindWallet(String password) async {
    final Object? data = await _postJson(
      '/wallet/unbind',
      WalletUnbindRequestDto(password: password).toJson(),
      authenticated: true,
    );
    return WalletResponseDto.fromJson(asJsonObject(data, 'wallet')).toDomain();
  }

  @override
  Future<VipOrder> createVipOrder() async {
    final Object? data = await _send(
      _request('POST', _stringUri('/vip/orders'), body: '', authenticated: true),
    );
    return VipOrderDto.fromJson(asJsonObject(data, 'vip order')).toDomain();
  }

  @override
  Future<List<VipOrder>> getVipOrders() async {
    final Object? data = await _send(
      _request('GET', _stringUri('/vip/orders'), authenticated: true),
    );
    return _mapList(data, 'vip orders', VipOrderDto.fromJson)
        .map((VipOrderDto dto) => dto.toDomain())
        .toList(growable: false);
  }

  @override
  Future<VipOrder?> getLatestVipOrder() async {
    // The Kotlin client requests `BackendApiResponse<VipOrder?>` but its generic
    // `execute` still rejects a null `data`; a user without VIP orders therefore
    // surfaces as an error rather than a null result. The same behaviour is kept
    // here, so this never returns null in practice.
    final Object? data = await _send(
      _request('GET', _stringUri('/vip/orders/latest'), authenticated: true),
    );
    return VipOrderDto.fromJson(asJsonObject(data, 'vip order')).toDomain();
  }

  @override
  Future<WithdrawalSummary> getWithdrawalSummary() async {
    final Object? data = await _send(
      _request('GET', _stringUri('/withdrawals/summary'), authenticated: true),
    );
    return WithdrawalSummaryDto.fromJson(
      asJsonObject(data, 'withdrawal summary'),
    ).toDomain();
  }

  @override
  Future<List<WithdrawalRecord>> getWithdrawals() async {
    final Object? data = await _send(
      _request('GET', _stringUri('/withdrawals'), authenticated: true),
    );
    return _mapList(data, 'withdrawals', WithdrawalDto.fromJson)
        .map((WithdrawalDto dto) => dto.toDomain())
        .toList(growable: false);
  }

  @override
  Future<WithdrawalRecord> submitWithdrawal(int pointAmount) async {
    final Object? data = await _postJson(
      '/withdrawals',
      WithdrawalCreateRequestDto(pointAmount: pointAmount).toJson(),
      authenticated: true,
    );
    return WithdrawalDto.fromJson(
      asJsonObject(data, 'withdrawal'),
    ).toDomain();
  }

  @override
  Future<SocialToggleResult> toggleLike(String bookId) async {
    final Object? data = await _postJson(
      _socialPath(bookId, 'like'),
      const <String, dynamic>{},
      authenticated: true,
    );
    return SocialToggleDto.fromJson(asJsonObject(data, 'like')).toDomain();
  }

  @override
  Future<SocialToggleResult> getLikeStatus(String bookId) async {
    final Object? data = await _send(
      _request(
        'GET',
        _segmentedUri(<String>['social', 'books', bookId, 'like-status']),
        authenticated: true,
      ),
    );
    return SocialToggleDto.fromJson(asJsonObject(data, 'like status'))
        .toDomain();
  }

  @override
  Future<SocialToggleResult> toggleFavorite({
    required String bookId,
    required String bookTitle,
    required String filteredTitle,
    required String? coverUrl,
    required int chapterCount,
  }) async {
    final Object? data = await _postJson(
      _socialPath(bookId, 'favorite'),
      FavoriteRequestDto(
        bookTitle: bookTitle,
        filteredTitle: filteredTitle,
        coverUrl: coverUrl,
        chapterCount: chapterCount,
      ).toJson(),
      authenticated: true,
    );
    return SocialToggleDto.fromJson(asJsonObject(data, 'favorite')).toDomain();
  }

  @override
  Future<SocialToggleResult> getFavoriteStatus(String bookId) async {
    final Object? data = await _send(
      _request(
        'GET',
        _segmentedUri(<String>['social', 'books', bookId, 'favorite-status']),
        authenticated: true,
      ),
    );
    return SocialToggleDto.fromJson(asJsonObject(data, 'favorite status'))
        .toDomain();
  }

  @override
  Future<Comment> addComment(String bookId, String content) async {
    final Object? data = await _postJson(
      _socialPath(bookId, 'comments'),
      CommentRequestDto(content: content).toJson(),
      authenticated: true,
    );
    return CommentDto.fromJson(asJsonObject(data, 'comment')).toDomain();
  }

  @override
  Future<List<Comment>> listComments(String bookId) async {
    final Object? data = await _send(
      _request('GET', _segmentedUri(<String>[
        'social',
        'books',
        bookId,
        'comments',
      ])),
    );
    // Pagination is intentionally unused, matching the Kotlin client.
    return CommentPageDto.fromJson(asJsonObject(data, 'comments')).comments
        .map((CommentDto dto) => dto.toDomain())
        .toList(growable: false);
  }

  @override
  Future<List<BookSummary>> listMyFavorites() async {
    final Object? data = await _send(
      _request(
        'GET',
        _segmentedUri(const <String>['social', 'my', 'favorites']),
        authenticated: true,
      ),
    );
    return _mapList(data, 'favorites', FavoriteBookDto.fromJson)
        .map((FavoriteBookDto dto) => dto.toDomain())
        .toList(growable: false);
  }

  // ---------------------------------------------------------------------------
  // Request plumbing
  // ---------------------------------------------------------------------------

  /// `"/social/books/$bookId/$action"`, built by concatenation exactly like the
  /// Kotlin helper (book ids are never escaped on this path).
  String _socialPath(String bookId, String action) =>
      '/social/books/$bookId/$action';

  _PreparedRequest _request(
    String method,
    Uri url, {
    String? body,
    bool authenticated = false,
  }) {
    final http.Request request = http.Request(method, url);
    if (body != null) {
      request.headers['Content-Type'] = _jsonMediaType;
      request.bodyBytes = utf8.encode(body);
    }
    request.headers['Accept'] = 'application/json';
    return (request, authenticated);
  }

  Uri _stringUri(String path) => Uri.parse('${_config.baseUrl}$path');

  /// Builds a URL from path segments and query parameters the way OkHttp's
  /// `HttpUrl.Builder` does: each segment is escaped individually and repeated
  /// names keep insertion order.
  Uri _segmentedUri(
    List<String> segments, [
    Map<String, String> query = const <String, String>{},
  ]) {
    final Uri base = Uri.parse(_config.baseUrl);
    final StringBuffer path = StringBuffer();
    for (final String segment in base.pathSegments) {
      if (segment.isEmpty) {
        continue;
      }
      path.write('/');
      path.write(Uri.encodeComponent(segment));
    }
    for (final String segment in segments) {
      path.write('/');
      path.write(Uri.encodeComponent(segment));
    }
    final StringBuffer url = StringBuffer()
      ..write(base.scheme)
      ..write('://')
      ..write(base.authority)
      ..write(path);
    if (query.isNotEmpty) {
      url.write('?');
      url.write(_encodeQuery(query));
    }
    return Uri.parse(url.toString());
  }

  String _encodeQuery(Map<String, String> query) => query.entries
      .map(
        (MapEntry<String, String> entry) =>
            '${_encodeQueryComponent(entry.key)}='
            '${_encodeQueryComponent(entry.value)}',
      )
      .join('&');

  /// Escapes one query component.
  ///
  /// OkHttp escapes a space as `%20`; Dart's [Uri.encodeQueryComponent] uses `+`
  /// for spaces in query strings, so that one case is normalised back to `%20`
  /// to keep requests byte-identical.
  String _encodeQueryComponent(String value) =>
      Uri.encodeQueryComponent(value).replaceAll('+', '%20');

  Future<Object?> _send(_PreparedRequest prepared) async {
    final (http.Request request, bool authenticated) = prepared;
    if (authenticated) {
      final String? token = await _tokenProvider();
      if (token == null) {
        throw const ApiClientException(0, null, 'missing bearer token');
      }
      request.headers['Authorization'] = 'Bearer $token';
    }
    if (kDebugMode) {
      final String tokenHead = authenticated
          ? (request.headers['Authorization']?.substring(0, 28) ?? 'none')
          : 'anon';
      debugPrint('[api] ${request.method} ${request.url.path} $tokenHead');
    }
    final http.Response response = await http.Response.fromStream(
      await _httpClient.send(request),
    ).timeout(requestTimeout);
    final String body = utf8.decode(response.bodyBytes, allowMalformed: true);
    if (kDebugMode && (response.statusCode < 200 || response.statusCode >= 300)) {
      debugPrint(
        '[api] <- ${response.statusCode} ${request.url.path} '
        'body=${body.length > 160 ? body.substring(0, 160) : body}',
      );
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      final _BackendError? backendError = _parseBackendError(body);
      throw ApiClientException(
        response.statusCode,
        backendError?.code,
        backendError?.message ??
            (body.trim().isEmpty
                ? (response.reasonPhrase ?? '')
                : body),
      );
    }
    final BackendApiResponse envelope = BackendApiResponse.fromJson(
      asJsonObject(jsonDecode(body), 'response'),
    );
    if (envelope.code != 0) {
      throw ApiClientException(
        response.statusCode,
        envelope.code,
        envelope.message,
      );
    }
    final Object? data = envelope.data;
    if (data == null) {
      throw ApiClientException(
        response.statusCode,
        envelope.code,
        'empty response data',
      );
    }
    return data;
  }

  Future<Object?> _postJson(
    String path,
    Map<String, dynamic> body, {
    bool authenticated = false,
  }) => _send(
    _request(
      'POST',
      _stringUri(path),
      body: jsonEncode(body),
      authenticated: authenticated,
    ),
  );

  _BackendError? _parseBackendError(String body) {
    try {
      final Map<String, dynamic> json = asJsonObject(jsonDecode(body), 'error');
      final Object? message = json['message'];
      final String? text = switch (message) {
        final String value => value,
        final num value => value.toString(),
        _ => null,
      };
      if (text == null || text.trim().isEmpty) {
        return null;
      }
      final Object? code = json['code'];
      return _BackendError(code is int ? code : null, text);
    } catch (_) {
      return null;
    }
  }

  List<T> _mapList<T>(
    Object? data,
    String what,
    T Function(Map<String, dynamic>) parse,
  ) => asJsonArray(data, what)
      .map((Object? item) => parse(asJsonObject(item, what)))
      .toList(growable: false);
}
