/// Failure raised by the API client.
///
/// Port of `com.reelshort.app.network.ApiClientException`. HTTP status, the
/// backend's business code and its message are all preserved because the state
/// controller classifies failures by them (401 session expiry, 403 current
/// password, "VIP required", blocked accounts, ...).
class ApiClientException implements Exception {
  const ApiClientException(this.statusCode, this.code, this.message);

  /// HTTP status code, or 0 when the failure happened before a response.
  final int statusCode;

  /// Business code from the `{code, message, data}` envelope, when available.
  final int? code;

  final String message;

  @override
  String toString() => 'ApiClientException($statusCode, $code, $message)';
}
