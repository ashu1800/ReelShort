/// Strict JSON readers mirroring kotlinx.serialization's default behaviour.
///
/// The Kotlin client decodes with `Json { ignoreUnknownKeys = true }`: unknown
/// keys are ignored, but a missing or wrongly typed *required* field fails the
/// decode. These helpers reproduce that contract so the Flutter client reacts to
/// malformed payloads exactly like the original.
///
/// Port of `android-app/app-core/src/main/kotlin/com/reelshort/app/network` decode rules.
library;

/// Raised when a payload does not match the expected shape.
class JsonShapeException implements Exception {
  const JsonShapeException(this.message);

  final String message;

  @override
  String toString() => 'JsonShapeException: $message';
}

/// Casts a decoded value to a JSON object.
Map<String, dynamic> asJsonObject(Object? value, String what) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    return value.cast<String, dynamic>();
  }
  throw JsonShapeException('$what must be a JSON object');
}

/// Casts a decoded value to a JSON array.
List<Object?> asJsonArray(Object? value, String what) {
  if (value is List) {
    return value;
  }
  throw JsonShapeException('$what must be a JSON array');
}

/// Reads a required string field.
String readString(Map<String, dynamic> json, String key) {
  final Object? value = json[key];
  if (value is String) {
    return value;
  }
  if (value == null) {
    throw JsonShapeException('missing required field "$key"');
  }
  throw JsonShapeException('field "$key" must be a string');
}

/// Reads an optional string field; a missing key or JSON null yields `null`.
String? readStringOrNull(Map<String, dynamic> json, String key) {
  final Object? value = json[key];
  if (value == null) {
    return null;
  }
  if (value is String) {
    return value;
  }
  throw JsonShapeException('field "$key" must be a string');
}

/// Reads a string field, applying [fallback] when the key is absent.
///
/// A JSON null is an error, mirroring Kotlin's non-nullable `String` with a
/// default value (a default only applies when the key is missing).
String readStringOrDefault(
  Map<String, dynamic> json,
  String key, [
  String fallback = '',
]) {
  if (!json.containsKey(key)) {
    return fallback;
  }
  return readString(json, key);
}

/// Reads a required integer field.
int readInt(Map<String, dynamic> json, String key) {
  final Object? value = json[key];
  if (value is int) {
    return value;
  }
  if (value == null) {
    throw JsonShapeException('missing required field "$key"');
  }
  throw JsonShapeException('field "$key" must be an integer');
}

/// Reads an optional integer field.
int? readIntOrNull(Map<String, dynamic> json, String key) {
  final Object? value = json[key];
  if (value == null) {
    return null;
  }
  if (value is int) {
    return value;
  }
  throw JsonShapeException('field "$key" must be an integer');
}

/// Reads an integer field with a default for a missing key.
int readIntOrDefault(Map<String, dynamic> json, String key, int fallback) {
  if (!json.containsKey(key)) {
    return fallback;
  }
  return readInt(json, key);
}

/// Reads a boolean field with a default for a missing key.
bool readBoolOrDefault(Map<String, dynamic> json, String key, bool fallback) {
  final Object? value = json[key];
  if (value == null) {
    return fallback;
  }
  if (value is bool) {
    return value;
  }
  throw JsonShapeException('field "$key" must be a boolean');
}

/// Reads a list of integers, defaulting to an empty list when the key is absent.
List<int> readIntListOrDefault(
  Map<String, dynamic> json,
  String key, [
  List<int> fallback = const <int>[],
]) {
  final Object? value = json[key];
  if (value == null) {
    return fallback;
  }
  final List<Object?> raw = asJsonArray(value, 'field "$key"');
  return raw.map((Object? item) {
    if (item is int) {
      return item;
    }
    throw JsonShapeException('field "$key" must contain integers');
  }).toList(growable: false);
}
