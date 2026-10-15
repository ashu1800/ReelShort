/// Manifest HTTP client of the self-update subsystem.
///
/// Port of `ShortLinkUpdateClient.kt`: one `GET` against the release endpoint
/// with a hard 65 536-byte body cap, strict manifest decoding and the asset URL
/// validation of `release_models.dart`.
library;

import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import 'release_models.dart';

/// Fetches `/api/app/release/latest` and validates the returned manifest.
class ShortLinkUpdateClient implements ReleaseUpdateClient {
  ShortLinkUpdateClient({
    required this.httpClient,
    this.endpoint = defaultEndpoint,
    required this.currentVersionName,
    this.cosBucket = defaultCosBucket,
    this.cosRegion = defaultCosRegion,
  });

  /// Production release endpoint.
  static const String defaultEndpoint =
      'https://shortlink.hjj888.cc/api/app/release/latest';

  /// `MAX_JSON_BYTES`: the manifest body is rejected above this size.
  static const int maxJsonBytes = 65536;

  /// Transport; its timeouts are the update client's policy, mirroring the
  /// dedicated OkHttp client the Android composition root builds.
  final http.Client httpClient;
  final String endpoint;

  /// Installed `versionName`, used as the manifest request's User-Agent suffix.
  final String currentVersionName;

  /// Accepted COS bucket; the composition root passes `BuildConfig` equivalents.
  final String cosBucket;

  /// Accepted COS region.
  final String cosRegion;

  /// `ShortLink-Android/<versionName>`, exactly as the Android layer builds it.
  String get userAgent => 'ShortLink-Android/$currentVersionName';

  /// Fetches the newest release, or `null` when the service reports none.
  ///
  /// Transport policy (connect/read/call timeouts) belongs to the injected
  /// [http.Client], mirroring the Android layer's dedicated update client.
  ///
  /// Kotlin's `empty response` reason is unreachable through `package:http`,
  /// whose streamed responses always carry a body: an empty payload therefore
  /// surfaces as `malformed JSON`, like any other undecodable body.
  @override
  Future<ReleaseInfo?> fetchLatestStable() async {
    final http.Request request = http.Request('GET', Uri.parse(endpoint))
      ..headers['Accept'] = 'application/json'
      ..headers['User-Agent'] = userAgent;
    if (kDebugMode) {
      debugPrint('[update] GET $endpoint');
    }
    final http.StreamedResponse response = await httpClient
        .send(request)
        .timeout(updateManifestTimeout);
    if (kDebugMode) {
      debugPrint('[update] <- ${response.statusCode}');
    }
    if (!_isSuccessful(response.statusCode)) {
      throw ReleaseHttpException(response.statusCode);
    }
    final UpdateManifest manifest;
    try {
      // The Kotlin original wraps `readUtf8Limited` *and* the decode in one
      // `runCatching`, so an oversized body also reports `malformed JSON`.
      manifest = UpdateManifest.parse(await _readUtf8Limited(response));
    } catch (_) {
      throw const InvalidReleaseException('malformed JSON');
    }
    // Validation runs outside the decode guard, so its exact reasons surface.
    return manifest.toReleaseInfo(cosBucket: cosBucket, cosRegion: cosRegion);
  }

  /// Reads the body, rejecting more than [maxJsonBytes] bytes.
  ///
  /// Okio's `readUtf8` replaces malformed sequences with U+FFFD instead of
  /// failing, which `allowMalformed` reproduces here.
  Future<String> _readUtf8Limited(http.StreamedResponse response) async {
    final int? contentLength = response.contentLength;
    if (contentLength != null && contentLength > maxJsonBytes) {
      throw const InvalidReleaseException('response is too large');
    }
    final BytesBuilder builder = BytesBuilder(copy: false);
    int total = 0;
    await for (final List<int> chunk in response.stream) {
      total += chunk.length;
      if (total > maxJsonBytes) {
        throw const InvalidReleaseException('response is too large');
      }
      builder.add(chunk);
    }
    return utf8.decode(builder.takeBytes(), allowMalformed: true);
  }

  /// OkHttp's `Response.isSuccessful`: any 2xx status.
  static bool _isSuccessful(int statusCode) =>
      statusCode >= 200 && statusCode <= 299;
}
