/// Failure raised by the API client.
///
/// Port of `com.reelshort.app.network.ApiClientException`. HTTP status, the
/// backend's business code and its message are all preserved because the state
/// controller classifies failures by them (401 session expiry, 403 current
/// password, "VIP required", blocked accounts, ...).
library;

/// Longest upstream message that may reach the message bar verbatim.
///
/// Anything longer is a body dump (a gateway page, a stack trace, a JSON
/// document), not a message a person can act on.
const int maxUserFacingMessageLength = 200;

/// Whether [value] looks like a serialized document rather than a sentence.
///
/// A JSON object (`{...}`), an HTML page (`<...`), or a JSON array (`[...`) all
/// mean the body was a protocol payload that leaked through: never show it.
bool looksStructuredMessage(String value) {
  final String trimmed = value.trimLeft();
  if (trimmed.isEmpty) {
    return false;
  }
  final String first = trimmed.substring(0, 1);
  return first == '{' || first == '[' || first == '<';
}

/// Filters an upstream free-text message before it becomes user-facing copy.
///
/// Returns the message unchanged when it is a short, plain sentence; otherwise
/// returns an empty string, which the caller turns into its own fallback copy.
/// The rule is shared by the API client (which sees the HTTP body) and the state
/// controller (which sees whatever a lower layer produced), so a structured or
/// oversized body can never reach the message bar from either side.
String sanitizeUpstreamMessage(
  String raw, {
  int maxLength = maxUserFacingMessageLength,
}) {
  final String trimmed = raw.trim();
  if (trimmed.isEmpty) {
    return '';
  }
  if (trimmed.length > maxLength) {
    return '';
  }
  if (looksStructuredMessage(trimmed)) {
    return '';
  }
  // Control characters mean the "message" is a body paste, not copy.
  if (trimmed.contains(RegExp(r'[\u0000-\u0008\u000B\u000C\u000E-\u001F]'))) {
    return '';
  }
  return trimmed;
}

/// Failure raised by the API client.
class ApiClientException implements Exception {
  const ApiClientException(this.statusCode, this.code, this.message);

  /// HTTP status code, or 0 when the failure happened before a response.
  final int statusCode;

  /// Business code from the `{code, message, data}` envelope, when available.
  final int? code;

  /// The upstream message, already filtered through
  /// [sanitizeUpstreamMessage]; empty when the body was structured or oversized.
  final String message;

  @override
  String toString() => 'ApiClientException($statusCode, $code, $message)';
}
