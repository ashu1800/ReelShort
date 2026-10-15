/// Release manifest models, asset URL validation and typed update failures.
///
/// 1:1 port of `android-app/app-core/src/main/kotlin/com/reelshort/app/update/`:
/// `SemanticVersion.kt`, `ReleaseModels.kt` and the private `UpdateManifest`,
/// `validateAssetUrl` and `readUtf8Limited` helpers of `ShortLinkUpdateClient.kt`.
library;

import 'dart:convert';
import 'dart:io';

import '../data/network/json_read.dart';

/// Largest accepted value of one [SemanticVersion] component.
///
/// The Kotlin original parses each component with `String.toInt()` into a 32-bit
/// `Int` and returns `null` when the conversion overflows, so a component above
/// this bound must not parse.
const int maxSemanticVersionComponent = 0x7FFFFFFF;

/// Maximum number of UTF-16 code units kept from the release notes
/// (`ShortLinkUpdateClient.MAX_RELEASE_NOTES_CHARS`).
const int maxReleaseNotesChars = 4000;

/// Default COS bucket of the ShortLink update objects (`DEFAULT_COS_BUCKET`).
const String defaultCosBucket = 'update-1259596907';

/// Default COS region of the ShortLink update objects (`DEFAULT_COS_REGION`).
const String defaultCosRegion = 'ap-chengdu';

/// MIME type of the APK asset (`APK_CONTENT_TYPE`).
const String apkContentType = 'application/vnd.android.package-archive';

/// MIME type of the checksum sidecar asset.
const String checksumContentType = 'text/plain';

/// 250 MB APK ceiling (`UpdateCoordinator.MAX_APK_BYTES`).
const int maxApkBytesLimit = 262144000;

/// Free-space margin kept on the device (`MIN_FREE_SPACE_BYTES`, 32 MB).
const int freeSpaceMarginBytes = 33554432;

/// Exact `InvalidRelease` reason for a syntactically unusable asset URL.
const String invalidAssetUrlReason = 'asset URL is invalid';

/// Exact `InvalidRelease` reason for a non-HTTPS, ported or userinfo URL.
const String insecureAssetUrlReason =
    'asset URL must be HTTPS without port or userinfo';

/// Exact `InvalidRelease` reason for an asset hosted outside the ShortLink bucket.
const String foreignAssetHostReason =
    'asset URL must be the ShortLink COS bucket';

/// Exact `InvalidRelease` reason for a path that does not hold the expected object.
const String invalidAssetPathReason = 'asset URL path is invalid';

/// `bucket.cos.region.myqcloud.com`, the only accepted COS asset host.
String cosAssetHost({
  String bucket = defaultCosBucket,
  String region = defaultCosRegion,
}) => '$bucket.cos.$region.myqcloud.com';

/// A strict `MAJOR.MINOR.PATCH` version, parsed from a release tag.
///
/// Port of `SemanticVersion.kt`. Comparison is lexicographic on the three
/// components, exactly like Kotlin's `compareValuesBy`.
class SemanticVersion implements Comparable<SemanticVersion> {
  const SemanticVersion(this.major, this.minor, this.patch);

  /// Matches `v?X.Y.Z` with no leading zeroes (`SemanticVersion.pattern`).
  static final RegExp pattern = RegExp(
    r'^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$',
  );

  final int major;
  final int minor;
  final int patch;

  /// Parses `X.Y.Z` or `vX.Y.Z`, returning `null` for any other shape.
  static SemanticVersion? parse(String value) {
    final RegExpMatch? match = pattern.firstMatch(value);
    if (match == null) {
      return null;
    }
    final int? major = _component(match.group(1)!);
    final int? minor = _component(match.group(2)!);
    final int? patch = _component(match.group(3)!);
    if (major == null || minor == null || patch == null) {
      return null;
    }
    return SemanticVersion(major, minor, patch);
  }

  /// Parses one component, rejecting values a Kotlin 32-bit `Int` cannot hold.
  static int? _component(String digits) {
    final int? value = int.tryParse(digits);
    if (value == null || value > maxSemanticVersionComponent) {
      return null;
    }
    return value;
  }

  bool operator >(SemanticVersion other) => compareTo(other) > 0;

  bool operator <(SemanticVersion other) => compareTo(other) < 0;

  bool operator >=(SemanticVersion other) => compareTo(other) >= 0;

  bool operator <=(SemanticVersion other) => compareTo(other) <= 0;

  @override
  int compareTo(SemanticVersion other) {
    if (major != other.major) {
      return major.compareTo(other.major);
    }
    if (minor != other.minor) {
      return minor.compareTo(other.minor);
    }
    return patch.compareTo(other.patch);
  }

  @override
  bool operator ==(Object other) =>
      other is SemanticVersion &&
      other.major == major &&
      other.minor == minor &&
      other.patch == patch;

  @override
  int get hashCode => Object.hash(major, minor, patch);

  @override
  String toString() => '$major.$minor.$patch';
}

/// One downloadable file of a release (`ReleaseAsset`).
class ReleaseAsset {
  const ReleaseAsset({
    required this.name,
    required this.downloadUrl,
    required this.contentType,
    required this.sizeBytes,
  });

  final String name;
  final String downloadUrl;
  final String contentType;
  final int sizeBytes;

  @override
  bool operator ==(Object other) =>
      other is ReleaseAsset &&
      other.name == name &&
      other.downloadUrl == downloadUrl &&
      other.contentType == contentType &&
      other.sizeBytes == sizeBytes;

  @override
  int get hashCode => Object.hash(name, downloadUrl, contentType, sizeBytes);

  @override
  String toString() => 'ReleaseAsset($name, $downloadUrl, $sizeBytes)';
}

/// A validated release ready to be downloaded (`ReleaseInfo`).
class ReleaseInfo {
  const ReleaseInfo({
    required this.tagName,
    required this.version,
    required this.versionCode,
    required this.title,
    required this.body,
    required this.publishedAt,
    required this.apkAsset,
    required this.sha256Asset,
  });

  final String tagName;
  final SemanticVersion version;

  /// `versionCode` from the manifest, used to reject downgrade installs.
  final int versionCode;
  final String title;
  final String body;
  final String publishedAt;
  final ReleaseAsset apkAsset;
  final ReleaseAsset sha256Asset;

  @override
  bool operator ==(Object other) =>
      other is ReleaseInfo &&
      other.tagName == tagName &&
      other.version == version &&
      other.versionCode == versionCode &&
      other.title == title &&
      other.body == body &&
      other.publishedAt == publishedAt &&
      other.apkAsset == apkAsset &&
      other.sha256Asset == sha256Asset;

  @override
  int get hashCode => Object.hash(
    tagName,
    version,
    versionCode,
    title,
    body,
    publishedAt,
    apkAsset,
    sha256Asset,
  );

  @override
  String toString() => 'ReleaseInfo($tagName, $versionCode)';
}

/// Bytes transferred so far, with the total when the server declared it.
class DownloadProgress {
  const DownloadProgress(this.downloadedBytes, this.totalBytes);

  final int downloadedBytes;

  /// `null` when the response has no content length (chunked transfer).
  final int? totalBytes;

  @override
  bool operator ==(Object other) =>
      other is DownloadProgress &&
      other.downloadedBytes == downloadedBytes &&
      other.totalBytes == totalBytes;

  @override
  int get hashCode => Object.hash(downloadedBytes, totalBytes);

  @override
  String toString() => 'DownloadProgress($downloadedBytes, $totalBytes)';
}

/// Fetches the latest published release (`ReleaseUpdateClient`).
abstract interface class ReleaseUpdateClient {
  /// Returns the newest stable release, or `null` when the manifest reports none.
  Future<ReleaseInfo?> fetchLatestStable();
}

/// Streams release assets (`ReleaseDownloader`).
abstract interface class ReleaseDownloader {
  /// Streams [url] into [destination], aborting past [maxBytes].
  ///
  /// [onProgress] is invoked after every written chunk with the bytes written so
  /// far and the declared content length (or `null` when the server chunks).
  Future<void> download(
    String url,
    File destination,
    int maxBytes,
    void Function(int downloaded, int? total) onProgress,
  );

  /// Downloads and parses the `<apk>.sha256` sidecar, returning a lowercase digest.
  Future<String> fetchSha256(String url);
}

/// Base class of every typed update failure (`ReleaseUpdateException`).
sealed class ReleaseUpdateException implements Exception {
  const ReleaseUpdateException();

  /// Message mirroring the Kotlin `IOException.message`.
  String get message;

  @override
  String toString() => '$runtimeType: $message';
}

/// The update service answered with a non-2xx status (`Http`).
final class ReleaseHttpException extends ReleaseUpdateException {
  const ReleaseHttpException(this.statusCode);

  final int statusCode;

  @override
  String get message => 'Update service returned HTTP $statusCode';
}

/// The manifest or one of its assets is not usable (`InvalidRelease`).
final class InvalidReleaseException extends ReleaseUpdateException {
  const InvalidReleaseException(this.reason);

  /// Exact validation reason, without the `Invalid release: ` prefix.
  final String reason;

  @override
  String get message => 'Invalid release: $reason';
}

/// The streamed payload exceeds the configured ceiling (`DownloadTooLarge`).
final class DownloadTooLargeException extends ReleaseUpdateException {
  const DownloadTooLargeException(this.maxBytes);

  final int maxBytes;

  @override
  String get message => 'Download exceeds $maxBytes bytes';
}

/// The checksum sidecar is missing, oversized or not a SHA-256 digest.
final class InvalidChecksumException extends ReleaseUpdateException {
  const InvalidChecksumException();

  @override
  String get message => 'Invalid SHA-256 checksum asset';
}

/// Stand-in for the `java.lang.SecurityException` of the Kotlin coordinator.
///
/// The Kotlin size gate raises it when the streamed APK length disagrees with
/// the manifest, which the coordinator reports as a verification failure.
class SecurityException implements Exception {
  const SecurityException(this.message);

  final String message;

  @override
  String toString() => 'SecurityException: $message';
}

/// Stand-in for the `kotlinx.coroutines.CancellationException` of the port.
///
/// The Kotlin downloader calls `ensureActive()` after every chunk and the
/// coordinator rethrows the cancellation without touching the state machine, so
/// a cancelled download only deletes its partial files here as well.
class DownloadCancelledException implements Exception {
  const DownloadCancelledException([
    this.message = 'update download cancelled',
  ]);

  final String message;

  @override
  String toString() => 'DownloadCancelledException: $message';
}

/// The `UpdateManifest` payload of `ShortLinkUpdateClient.kt`.
///
/// Unknown keys are ignored (the Kotlin decoder uses
/// `Json { ignoreUnknownKeys = true }`), while a missing or wrongly typed
/// required field fails the decode and surfaces as `malformed JSON`.
class UpdateManifest {
  const UpdateManifest({
    required this.versionName,
    required this.versionCode,
    this.title = '',
    this.releaseNotes = '',
    this.publishedAt = '',
    required this.apkUrl,
    required this.sha256Url,
    required this.sizeBytes,
    required this.sha256SizeBytes,
    this.minimumVersionCode,
    this.mandatory = false,
  });

  final String versionName;
  final int versionCode;
  final String title;
  final String releaseNotes;
  final String publishedAt;
  final String apkUrl;
  final String sha256Url;
  final int sizeBytes;
  final int sha256SizeBytes;
  final int? minimumVersionCode;

  /// Optional flag; like the verified [readBoolOrDefault] helper, an absent or
  /// JSON-null value yields the Kotlin default (`false`).
  final bool mandatory;

  /// Decodes one manifest object, mirroring `decodeFromString<UpdateManifest>`.
  factory UpdateManifest.fromJson(Map<String, dynamic> json) => UpdateManifest(
    versionName: readString(json, 'versionName'),
    versionCode: readInt(json, 'versionCode'),
    title: readStringOrDefault(json, 'title'),
    releaseNotes: readStringOrDefault(json, 'releaseNotes'),
    publishedAt: readStringOrDefault(json, 'publishedAt'),
    apkUrl: readString(json, 'apkUrl'),
    sha256Url: readString(json, 'sha256Url'),
    sizeBytes: readInt(json, 'sizeBytes'),
    sha256SizeBytes: readInt(json, 'sha256SizeBytes'),
    minimumVersionCode: readIntOrNull(json, 'minimumVersionCode'),
    mandatory: readBoolOrDefault(json, 'mandatory', false),
  );

  /// Decodes the manifest body of the release endpoint.
  static UpdateManifest parse(String text) => UpdateManifest.fromJson(
    asJsonObject(jsonDecode(text), 'update manifest'),
  );

  /// Validates the manifest and derives the release assets.
  ///
  /// Throws [InvalidReleaseException] with the exact Kotlin reasons.
  ReleaseInfo toReleaseInfo({
    String cosBucket = defaultCosBucket,
    String cosRegion = defaultCosRegion,
  }) {
    final SemanticVersion? version = SemanticVersion.parse(versionName);
    if (version == null) {
      throw const InvalidReleaseException('versionName must use X.Y.Z');
    }
    if (versionName != version.toString()) {
      throw const InvalidReleaseException('versionName must be canonical');
    }
    if (versionCode <= 0) {
      throw const InvalidReleaseException('versionCode must be positive');
    }
    if (sizeBytes <= 0) {
      throw const InvalidReleaseException('APK size must be positive');
    }
    if (sha256SizeBytes <= 0) {
      throw const InvalidReleaseException('checksum size must be positive');
    }

    final String tagName = 'v$version';
    final String apkName = 'ShortLink-$tagName.apk';
    final String checksumName = '$apkName.sha256';
    validateAssetUrl(
      apkUrl,
      apkName,
      cosBucket: cosBucket,
      cosRegion: cosRegion,
    );
    validateAssetUrl(
      sha256Url,
      checksumName,
      cosBucket: cosBucket,
      cosRegion: cosRegion,
    );

    return ReleaseInfo(
      tagName: tagName,
      version: version,
      versionCode: versionCode,
      title: title.trim().isEmpty ? 'ShortLink $tagName' : title,
      body: releaseNotes.length > maxReleaseNotesChars
          ? releaseNotes.substring(0, maxReleaseNotesChars)
          : releaseNotes,
      publishedAt: publishedAt,
      apkAsset: ReleaseAsset(
        name: apkName,
        downloadUrl: apkUrl,
        contentType: apkContentType,
        sizeBytes: sizeBytes,
      ),
      sha256Asset: ReleaseAsset(
        name: checksumName,
        downloadUrl: sha256Url,
        contentType: checksumContentType,
        sizeBytes: sha256SizeBytes,
      ),
    );
  }
}

/// Validates that [value] points at `/<expectedName>` of the ShortLink COS bucket.
///
/// Only the exact `<bucket>.cos.<region>.myqcloud.com` host is accepted — no
/// suffix matching, so another account's bucket or `<bucket>...evil.example`
/// cannot be used as an install source. Presigned query parameters are allowed.
///
/// Throws [InvalidReleaseException] with the exact Kotlin reason strings.
void validateAssetUrl(
  String value,
  String expectedName, {
  String cosBucket = defaultCosBucket,
  String cosRegion = defaultCosRegion,
}) {
  // Java's `URI(String)` rejects characters that Dart's `Uri.parse` silently
  // percent-encodes, so those inputs must fail here with the same reason.
  if (_illegalUriCharacters.hasMatch(value)) {
    throw const InvalidReleaseException(invalidAssetUrlReason);
  }
  final Uri? uri = Uri.tryParse(value);
  if (uri == null) {
    throw const InvalidReleaseException(invalidAssetUrlReason);
  }
  // `hasPort` mirrors Java's `getPort() != -1` (Dart reports the scheme default
  // otherwise) and an absent userinfo is an empty string, not null.
  if (uri.scheme != 'https' || uri.hasPort || uri.userInfo.isNotEmpty) {
    throw const InvalidReleaseException(insecureAssetUrlReason);
  }
  if (uri.host.isEmpty ||
      uri.host != cosAssetHost(bucket: cosBucket, region: cosRegion)) {
    throw const InvalidReleaseException(foreignAssetHostReason);
  }
  if (uri.hasFragment || !_rawPath(value).endsWith('/$expectedName')) {
    throw const InvalidReleaseException(invalidAssetPathReason);
  }
}

/// Characters Java's URI parser refuses anywhere in a URI reference.
final RegExp _illegalUriCharacters = RegExp(r'[\x00-\x20\x7F<>"{}|\\^`]');

/// Java `URI.getRawPath()` equivalent.
///
/// [Uri.path] normalises unreserved percent-escapes (`%2E` becomes `.`), while
/// the Kotlin validator compares the raw path, so the encoded path is recovered
/// from the original text between the authority and the query or fragment.
String _rawPath(String value) {
  final int schemeIndex = value.indexOf('://');
  final int afterScheme = schemeIndex < 0 ? 0 : schemeIndex + 3;
  final int pathStart = _indexOfFirstOf(value, afterScheme, '/?#');
  if (pathStart < 0 || value[pathStart] != '/') {
    return '';
  }
  final int pathEnd = _indexOfFirstOf(value, pathStart, '?#');
  return pathEnd < 0
      ? value.substring(pathStart)
      : value.substring(pathStart, pathEnd);
}

/// Index of the first character of [characters] at or after [from], or `-1`.
int _indexOfFirstOf(String value, int from, String characters) {
  for (int index = from; index < value.length; index++) {
    if (characters.contains(value[index])) {
      return index;
    }
  }
  return -1;
}
