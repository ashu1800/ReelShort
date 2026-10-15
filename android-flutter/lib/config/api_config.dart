/// Base URL and derived endpoints.
///
/// Port of `com.reelshort.app.config.ApiConfig`.
class ApiConfig {
  ApiConfig(String rawBaseUrl)
    : baseUrl = rawBaseUrl.trim().replaceAll(RegExp(r'/+$'), '');

  /// Base URL without a trailing slash.
  final String baseUrl;

  /// Health probe endpoint.
  ///
  /// When the base URL ends with the app API prefix the prefix is replaced,
  /// otherwise `/system/health` is appended.
  String get systemHealthUrl {
    if (baseUrl.endsWith('/api/app')) {
      return '${baseUrl.substring(0, baseUrl.length - '/api/app'.length)}/api/system/health';
    }
    return '$baseUrl/system/health';
  }

  /// Production endpoint of the ShortLink backend.
  static ApiConfig defaultConfig() =>
      ApiConfig('https://shortlink.hjj888.cc/api/app');

  @override
  bool operator ==(Object other) =>
      other is ApiConfig && baseUrl == other.baseUrl;

  @override
  int get hashCode => baseUrl.hashCode;

  @override
  String toString() => 'ApiConfig(baseUrl=$baseUrl)';
}
