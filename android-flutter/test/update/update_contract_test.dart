/// Contract tests of the ported self-update subsystem.
///
/// Ports the assertions of
/// `android-app/app-core/src/test/kotlin/com/reelshort/app/update/`
/// (`SemanticVersionTest.kt`, `ShortLinkUpdateClientTest.kt`,
/// `OkHttpReleaseDownloaderTest.kt`, `UpdateCoordinatorTest.kt`) and of
/// `android-app/app/src/test/kotlin/com/reelshort/app/ui/format/UpdateUiFormatTest.kt`.
library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shortlink/core/formatting/update_ui_format.dart';
import 'package:shortlink/update/release_downloader.dart';
import 'package:shortlink/update/release_models.dart';
import 'package:shortlink/update/short_link_update_client.dart';
import 'package:shortlink/update/update_coordinator.dart';
import 'package:shortlink/update/update_state.dart';

const String cosHost = 'update-1259596907.cos.ap-chengdu.myqcloud.com';
const String apkUrl =
    'https://$cosHost/releases/android/ShortLink-v0.4.2.apk?q-sign-algorithm=sha1';
const String checksumUrl =
    'https://$cosHost/releases/android/ShortLink-v0.4.2.apk.sha256?q-sign-algorithm=sha1';
const String manifestEndpoint =
    'https://shortlink.hjj888.cc/api/app/release/latest';

const ReleaseAsset apkAsset = ReleaseAsset(
  name: 'ShortLink-v0.2.0.apk',
  downloadUrl: 'https://github.com/app.apk',
  contentType: 'application/vnd.android.package-archive',
  sizeBytes: 3,
);

const ReleaseAsset checksumAsset = ReleaseAsset(
  name: 'ShortLink-v0.2.0.apk.sha256',
  downloadUrl: 'https://github.com/app.sha256',
  contentType: 'text/plain',
  sizeBytes: 64,
);

const ReleaseInfo release = ReleaseInfo(
  tagName: 'v0.2.0',
  version: SemanticVersion(0, 2, 0),
  versionCode: 7,
  title: 'ShortLink v0.2.0',
  body: 'Notes',
  publishedAt: '2026-07-13T12:00:00Z',
  apkAsset: apkAsset,
  sha256Asset: checksumAsset,
);

/// Release bytes used by the coordinator fakes: `apk` hashes to the asset digest.
final List<int> apkBytes = utf8.encode('apk');

String manifestJson({
  String versionName = '0.4.2',
  String apk = apkUrl,
  String sha256 = checksumUrl,
  int versionCode = 6,
  int sizeBytes = 12345678,
  int sha256SizeBytes = 65,
  bool includeSizeBytes = true,
}) => jsonEncode(<String, Object?>{
  'versionName': versionName,
  'versionCode': versionCode,
  'title': 'ShortLink v0.4.2',
  'releaseNotes': 'Bug fixes',
  'publishedAt': '2026-07-14T10:00:00Z',
  'apkUrl': apk,
  'sha256Url': sha256,
  if (includeSizeBytes) 'sizeBytes': sizeBytes,
  'sha256SizeBytes': sha256SizeBytes,
  'apkSha256':
      'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789',
  'minimumVersionCode': 1,
  'mandatory': false,
});

ShortLinkUpdateClient updateClient(
  String body, {
  int statusCode = 200,
  List<http.Request>? seen,
}) => ShortLinkUpdateClient(
  httpClient: MockClient((http.Request request) async {
    seen?.add(request);
    return http.Response(body, statusCode);
  }),
  endpoint: manifestEndpoint,
  currentVersionName: '0.4.2',
);

void main() {
  group('SemanticVersion', () {
    test('parses version name and release tag', () {
      expect(SemanticVersion.parse('0.2.0'), const SemanticVersion(0, 2, 0));
      expect(
        SemanticVersion.parse('v12.34.56'),
        const SemanticVersion(12, 34, 56),
      );
    });

    test('rejects malformed or pre-release versions', () {
      for (final String value in <String>[
        '',
        'v1',
        '1.2',
        'v1.2.3-beta',
        '01.2.3',
        '1.02.3',
        '1.2.03',
        '1.2.3.4',
      ]) {
        expect(SemanticVersion.parse(value), isNull, reason: value);
      }
    });

    test('rejects components a Kotlin 32-bit Int cannot hold', () {
      expect(
        SemanticVersion.parse('2147483647.0.0'),
        const SemanticVersion(2147483647, 0, 0),
      );
      expect(SemanticVersion.parse('2147483648.0.0'), isNull);
      expect(SemanticVersion.parse('99999999999999999999.0.0'), isNull);
    });

    test('compares major minor and patch components', () {
      expect(
        const SemanticVersion(1, 0, 0) > const SemanticVersion(0, 99, 99),
        isTrue,
      );
      expect(
        const SemanticVersion(1, 2, 0) > const SemanticVersion(1, 1, 99),
        isTrue,
      );
      expect(
        const SemanticVersion(1, 2, 4) > const SemanticVersion(1, 2, 3),
        isTrue,
      );
      expect(
        const SemanticVersion(
          1,
          2,
          3,
        ).compareTo(const SemanticVersion(1, 2, 3)),
        0,
      );
      expect(
        const SemanticVersion(1, 2, 3) < const SemanticVersion(1, 2, 4),
        isTrue,
      );
    });

    test('renders the canonical X.Y.Z form', () {
      expect(const SemanticVersion(12, 34, 56).toString(), '12.34.56');
      expect(SemanticVersion.parse('v0.4.2')!.toString(), '0.4.2');
    });
  });

  group('asset URL validation', () {
    test('accepts the ShortLink COS bucket with presigned query', () {
      expect(
        () => validateAssetUrl(apkUrl, 'ShortLink-v0.4.2.apk'),
        returnsNormally,
      );
      expect(
        () => validateAssetUrl(checksumUrl, 'ShortLink-v0.4.2.apk.sha256'),
        returnsNormally,
      );
    });

    test('rejects non-HTTPS', () {
      expect(
        () => validateAssetUrl(
          'http://$cosHost/releases/android/ShortLink-v0.4.2.apk',
          'ShortLink-v0.4.2.apk',
        ),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            insecureAssetUrlReason,
          ),
        ),
      );
    });

    test('rejects an explicit port and userinfo', () {
      for (final String value in <String>[
        'https://$cosHost:8443/releases/android/ShortLink-v0.4.2.apk',
        'https://user:secret@$cosHost/releases/android/ShortLink-v0.4.2.apk',
      ]) {
        expect(
          () => validateAssetUrl(value, 'ShortLink-v0.4.2.apk'),
          throwsA(
            isA<InvalidReleaseException>().having(
              (InvalidReleaseException error) => error.reason,
              'reason',
              insecureAssetUrlReason,
            ),
          ),
          reason: value,
        );
      }
    });

    test('rejects a non-COS host', () {
      expect(
        () => validateAssetUrl(
          'https://evil.example.com/releases/android/ShortLink-v0.4.2.apk',
          'ShortLink-v0.4.2.apk',
        ),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            foreignAssetHostReason,
          ),
        ),
      );
    });

    test(
      'rejects other COS buckets even when they share the account suffix',
      () {
        expect(
          () => validateAssetUrl(
            'https://other-bucket-1259596907.cos.ap-chengdu.myqcloud.com/'
                'releases/android/ShortLink-v0.4.2.apk',
            'ShortLink-v0.4.2.apk',
          ),
          throwsA(isA<InvalidReleaseException>()),
        );
      },
    );

    test('rejects the same bucket name in another COS region', () {
      expect(
        () => validateAssetUrl(
          'https://update-1259596907.cos.ap-shanghai.myqcloud.com/'
              'releases/android/ShortLink-v0.4.2.apk',
          'ShortLink-v0.4.2.apk',
        ),
        throwsA(isA<InvalidReleaseException>()),
      );
    });

    test('rejects the bucket host used as a prefix of another domain', () {
      expect(
        () => validateAssetUrl(
          'https://$cosHost.evil.example/releases/android/ShortLink-v0.4.2.apk',
          'ShortLink-v0.4.2.apk',
        ),
        throwsA(isA<InvalidReleaseException>()),
      );
    });

    test('rejects an asset URL not ending in the expected file name', () {
      expect(
        () => validateAssetUrl(
          'https://$cosHost/releases/android/different.apk',
          'ShortLink-v0.4.2.apk',
        ),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            invalidAssetPathReason,
          ),
        ),
      );
    });

    test('rejects a fragment and a normalized percent-escaped path', () {
      for (final String value in <String>[
        'https://$cosHost/releases/android/ShortLink-v0.4.2.apk#fragment',
        'https://$cosHost/releases/android/ShortLink-v0.4.2%2Eapk',
      ]) {
        expect(
          () => validateAssetUrl(value, 'ShortLink-v0.4.2.apk'),
          throwsA(
            isA<InvalidReleaseException>().having(
              (InvalidReleaseException error) => error.reason,
              'reason',
              invalidAssetPathReason,
            ),
          ),
          reason: value,
        );
      }
    });

    test('rejects a syntactically unusable URL', () {
      for (final String value in <String>[
        'not a url',
        'https://a.example/a b.apk',
      ]) {
        expect(
          () => validateAssetUrl(value, 'ShortLink-v0.4.2.apk'),
          throwsA(
            isA<InvalidReleaseException>().having(
              (InvalidReleaseException error) => error.reason,
              'reason',
              invalidAssetUrlReason,
            ),
          ),
          reason: value,
        );
      }
    });

    test('rejects an empty URL as insecure, like java.net.URI("")', () {
      // Java parses the empty URI reference successfully, so the Kotlin
      // validator reaches its scheme check and reports the insecure reason.
      expect(
        () => validateAssetUrl('', 'ShortLink-v0.4.2.apk'),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            insecureAssetUrlReason,
          ),
        ),
      );
    });

    test('keeps the exact Kotlin exception messages', () {
      expect(
        const InvalidReleaseException(invalidAssetUrlReason).message,
        'Invalid release: asset URL is invalid',
      );
      expect(
        const InvalidReleaseException(invalidAssetUrlReason).reason,
        'asset URL is invalid',
      );
      expect(
        const ReleaseHttpException(502).message,
        'Update service returned HTTP 502',
      );
      expect(
        const InvalidChecksumException().message,
        'Invalid SHA-256 checksum asset',
      );
      expect(
        const DownloadTooLargeException(1024).message,
        'Download exceeds 1024 bytes',
      );
    });
  });

  group('UpdateManifest', () {
    test('derives the release names and assets', () {
      final UpdateManifest manifest = UpdateManifest.fromJson(
        jsonDecode(manifestJson()) as Map<String, dynamic>,
      );

      final ReleaseInfo parsed = manifest.toReleaseInfo();

      expect(parsed.tagName, 'v0.4.2');
      expect(parsed.version, const SemanticVersion(0, 4, 2));
      expect(parsed.versionCode, 6);
      expect(parsed.apkAsset.name, 'ShortLink-v0.4.2.apk');
      expect(parsed.sha256Asset.name, 'ShortLink-v0.4.2.apk.sha256');
      expect(parsed.apkAsset.sizeBytes, 12345678);
      expect(parsed.apkAsset.contentType, apkContentType);
      expect(parsed.sha256Asset.contentType, checksumContentType);
      expect(parsed.apkAsset.downloadUrl, apkUrl);
      expect(cosAssetHost(), 'update-1259596907.cos.ap-chengdu.myqcloud.com');
    });

    test('falls back to the derived title and truncates long notes', () {
      final UpdateManifest manifest = UpdateManifest.fromJson(<String, dynamic>{
        'versionName': '0.4.2',
        'versionCode': 6,
        'title': '   ',
        'releaseNotes': 'x'.padRight(4200, 'x'),
        'apkUrl': apkUrl,
        'sha256Url': checksumUrl,
        'sizeBytes': 10,
        'sha256SizeBytes': 65,
      });

      final ReleaseInfo parsed = manifest.toReleaseInfo();

      expect(parsed.title, 'ShortLink v0.4.2');
      expect(parsed.body.length, maxReleaseNotesChars);
      expect(parsed.publishedAt, '');
      expect(manifest.minimumVersionCode, isNull);
      expect(manifest.mandatory, isFalse);
    });

    test('keeps the exact validation reasons', () {
      UpdateManifest build({
        String versionName = '0.4.2',
        int versionCode = 6,
        int sizeBytes = 10,
        int sha256SizeBytes = 65,
        String apk = apkUrl,
        String sha256 = checksumUrl,
      }) => UpdateManifest(
        versionName: versionName,
        versionCode: versionCode,
        apkUrl: apk,
        sha256Url: sha256,
        sizeBytes: sizeBytes,
        sha256SizeBytes: sha256SizeBytes,
      );

      expect(
        () => build(versionName: '0.4').toReleaseInfo(),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            'versionName must use X.Y.Z',
          ),
        ),
      );
      expect(
        () => build(versionName: 'v0.4.2').toReleaseInfo(),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            'versionName must be canonical',
          ),
        ),
      );
      expect(
        () => build(versionCode: 0).toReleaseInfo(),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            'versionCode must be positive',
          ),
        ),
      );
      expect(
        () => build(sizeBytes: 0).toReleaseInfo(),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            'APK size must be positive',
          ),
        ),
      );
      expect(
        () => build(sha256SizeBytes: 0).toReleaseInfo(),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            'checksum size must be positive',
          ),
        ),
      );
    });

    test('validates the checksum URL against the derived object name', () {
      expect(
        () => UpdateManifest(
          versionName: '0.4.2',
          versionCode: 6,
          apkUrl: apkUrl,
          sha256Url:
              'https://other-bucket-1259596907.cos.ap-chengdu.myqcloud.com/'
              'releases/android/ShortLink-v0.4.2.apk.sha256',
          sizeBytes: 10,
          sha256SizeBytes: 65,
        ).toReleaseInfo(),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            foreignAssetHostReason,
          ),
        ),
      );
    });
  });

  group('ShortLinkUpdateClient', () {
    test('parses a manifest with COS presigned URLs', () async {
      final List<http.Request> seen = <http.Request>[];
      final ReleaseInfo? parsed = await updateClient(
        manifestJson(),
        seen: seen,
      ).fetchLatestStable();

      expect(parsed, isNotNull);
      expect(parsed!.tagName, 'v0.4.2');
      expect(parsed.version, const SemanticVersion(0, 4, 2));
      expect(parsed.versionCode, 6);
      expect(parsed.apkAsset.name, 'ShortLink-v0.4.2.apk');
      expect(parsed.sha256Asset.name, 'ShortLink-v0.4.2.apk.sha256');
      expect(parsed.apkAsset.sizeBytes, 12345678);
      expect(parsed.apkAsset.downloadUrl, apkUrl);
      expect(seen.single.headers['Accept'], 'application/json');
      expect(seen.single.headers['User-Agent'], 'ShortLink-Android/0.4.2');
      expect(seen.single.url.toString(), manifestEndpoint);
    });

    test('maps an HTTP failure to the typed status exception', () async {
      final ShortLinkUpdateClient client = updateClient(
        'bad gateway',
        statusCode: 502,
      );

      await expectLater(
        client.fetchLatestStable(),
        throwsA(
          isA<ReleaseHttpException>().having(
            (ReleaseHttpException error) => error.statusCode,
            'statusCode',
            502,
          ),
        ),
      );
    });

    test('rejects every invalid asset URL shape', () async {
      final List<String> invalidApkUrls = <String>[
        'http://$cosHost/releases/android/ShortLink-v0.4.2.apk',
        'https://evil.example.com/releases/android/ShortLink-v0.4.2.apk',
        'https://other-bucket-1259596907.cos.ap-chengdu.myqcloud.com/'
            'releases/android/ShortLink-v0.4.2.apk',
        'https://update-1259596907.cos.ap-shanghai.myqcloud.com/'
            'releases/android/ShortLink-v0.4.2.apk',
        'https://$cosHost.evil.example/releases/android/ShortLink-v0.4.2.apk',
        'https://$cosHost/releases/android/different.apk',
      ];

      for (final String apk in invalidApkUrls) {
        await expectLater(
          updateClient(manifestJson(apk: apk)).fetchLatestStable(),
          throwsA(isA<InvalidReleaseException>()),
          reason: apk,
        );
      }
    });

    test('rejects a checksum URL from another COS bucket', () async {
      await expectLater(
        updateClient(
          manifestJson(
            sha256:
                'https://other-bucket-1259596907.cos.ap-chengdu.myqcloud.com/'
                'releases/android/ShortLink-v0.4.2.apk.sha256',
          ),
        ).fetchLatestStable(),
        throwsA(isA<InvalidReleaseException>()),
      );
    });

    test('rejects a non-canonical version name', () async {
      await expectLater(
        updateClient(manifestJson(versionName: '0.4')).fetchLatestStable(),
        throwsA(isA<InvalidReleaseException>()),
      );
      await expectLater(
        updateClient(manifestJson(versionName: 'v0.4.2')).fetchLatestStable(),
        throwsA(isA<InvalidReleaseException>()),
      );
    });

    test('reports undecodable payloads as malformed JSON', () async {
      await expectLater(
        updateClient('{ not json').fetchLatestStable(),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            'malformed JSON',
          ),
        ),
      );
      await expectLater(
        updateClient(manifestJson(includeSizeBytes: false)).fetchLatestStable(),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            'malformed JSON',
          ),
        ),
      );
    });

    test('caps the manifest body at 65536 bytes', () async {
      final String oversized = jsonEncode(<String, Object?>{
        ...jsonDecode(manifestJson()) as Map<String, dynamic>,
        'releaseNotes': 'x'.padRight(ShortLinkUpdateClient.maxJsonBytes, 'x'),
      });

      await expectLater(
        updateClient(oversized).fetchLatestStable(),
        throwsA(
          isA<InvalidReleaseException>().having(
            (InvalidReleaseException error) => error.reason,
            'reason',
            'malformed JSON',
          ),
        ),
      );
    });
  });

  group('HttpReleaseDownloader', () {
    late Directory downloadDir;

    setUp(() {
      downloadDir = Directory.systemTemp.createTempSync('update-download');
    });

    tearDown(() {
      if (downloadDir.existsSync()) {
        downloadDir.deleteSync(recursive: true);
      }
    });

    File destination() =>
        File('${downloadDir.path}${Platform.pathSeparator}app.apk.part');

    test('streams a file and reports known progress', () async {
      final List<int> content = List<int>.generate(
        48000,
        (int index) => index % 251,
      );
      final HttpReleaseDownloader downloader = HttpReleaseDownloader(
        httpClient: MockClient(
          (http.Request request) async => http.Response.bytes(content, 200),
        ),
      );
      final List<DownloadProgress> progress = <DownloadProgress>[];
      final File target = destination();

      await downloader.download(
        'https://$cosHost/releases/android/ShortLink-v0.2.0.apk',
        target,
        100000,
        (int downloaded, int? total) =>
            progress.add(DownloadProgress(downloaded, total)),
      );

      expect(target.readAsBytesSync(), content);
      expect(progress.last.downloadedBytes, content.length);
      expect(progress.last.totalBytes, content.length);
    });

    test('reports an unknown total for a chunked response', () async {
      final HttpReleaseDownloader downloader = HttpReleaseDownloader(
        httpClient: MockClient.streaming(
          (http.BaseRequest request, http.ByteStream body) async =>
              http.StreamedResponse(
                Stream<List<int>>.fromIterable(<List<int>>[
                  utf8.encode('chunked response'),
                ]),
                200,
              ),
        ),
      );
      final List<DownloadProgress> progress = <DownloadProgress>[];

      await downloader.download(
        'https://$cosHost/releases/android/ShortLink-v0.2.0.apk',
        destination(),
        100000,
        (int downloaded, int? total) =>
            progress.add(DownloadProgress(downloaded, total)),
      );

      expect(
        progress.last.downloadedBytes,
        utf8.encode('chunked response').length,
      );
      expect(progress.last.totalBytes, isNull);
    });

    test('sends the updater User-Agent', () async {
      final List<http.Request> seen = <http.Request>[];
      final HttpReleaseDownloader downloader = HttpReleaseDownloader(
        httpClient: MockClient((http.Request request) async {
          seen.add(request);
          return http.Response.bytes(<int>[1, 2, 3], 200);
        }),
      );

      await downloader.download(
        'https://$cosHost/releases/android/ShortLink-v0.2.0.apk',
        destination(),
        100000,
        (int downloaded, int? total) {},
      );

      expect(seen.single.headers['User-Agent'], 'ShortLink-Android-Updater');
    });

    test(
      'rejects an oversized download and deletes the partial file',
      () async {
        for (final bool knownLength in <bool>[true, false]) {
          final File target = destination();
          final HttpReleaseDownloader downloader = HttpReleaseDownloader(
            httpClient: knownLength
                ? MockClient(
                    (http.Request request) async =>
                        http.Response.bytes(List<int>.filled(2048, 0), 200),
                  )
                : MockClient.streaming(
                    (http.BaseRequest request, http.ByteStream body) async =>
                        http.StreamedResponse(
                          Stream<List<int>>.fromIterable(<List<int>>[
                            List<int>.filled(2048, 0),
                          ]),
                          200,
                        ),
                  ),
          );

          await expectLater(
            downloader.download(
              'https://$cosHost/releases/android/ShortLink-v0.2.0.apk',
              target,
              1024,
              (int downloaded, int? total) {},
            ),
            throwsA(isA<DownloadTooLargeException>()),
          );
          expect(target.existsSync(), isFalse);
        }
      },
    );

    test('maps a non-2xx download to the typed status exception', () async {
      final HttpReleaseDownloader downloader = HttpReleaseDownloader(
        httpClient: MockClient(
          (http.Request request) async => http.Response('nope', 404),
        ),
      );

      await expectLater(
        downloader.download(
          'https://$cosHost/releases/android/ShortLink-v0.2.0.apk',
          destination(),
          100000,
          (int downloaded, int? total) {},
        ),
        throwsA(
          isA<ReleaseHttpException>().having(
            (ReleaseHttpException error) => error.statusCode,
            'statusCode',
            404,
          ),
        ),
      );
    });

    test('parses the sha256 sidecar and rejects invalid content', () async {
      final String digest = 'a'.padRight(64, 'a');
      final List<http.Request> seen = <http.Request>[];
      final HttpReleaseDownloader downloader = HttpReleaseDownloader(
        httpClient: MockClient((http.Request request) async {
          seen.add(request);
          return http.Response(
            request.url.path.endsWith('/valid.sha256')
                ? '$digest  ShortLink-v0.2.0.apk\n'
                : 'not-a-digest',
            200,
          );
        }),
      );

      expect(
        await downloader.fetchSha256(
          'https://$cosHost/releases/android/valid.sha256',
        ),
        digest,
      );
      expect(seen.single.headers['User-Agent'], 'ShortLink-Android-Updater');
      await expectLater(
        downloader.fetchSha256(
          'https://$cosHost/releases/android/invalid.sha256',
        ),
        throwsA(isA<InvalidChecksumException>()),
      );
    });

    test('accepts the binary marker form and lowercases the digest', () async {
      final String digest = 'A'.padRight(64, 'A');
      final HttpReleaseDownloader downloader = HttpReleaseDownloader(
        httpClient: MockClient(
          (http.Request request) async =>
              http.Response('$digest *ShortLink-v0.2.0.apk', 200),
        ),
      );

      expect(
        await downloader.fetchSha256(
          'https://$cosHost/releases/android/app.sha256',
        ),
        digest.toLowerCase(),
      );
    });

    test('caps the checksum sidecar at 1024 bytes', () async {
      final HttpReleaseDownloader downloader = HttpReleaseDownloader(
        httpClient: MockClient(
          (http.Request request) async =>
              http.Response('a'.padRight(maxChecksumBytes + 1, 'a'), 200),
        ),
      );

      await expectLater(
        downloader.fetchSha256('https://$cosHost/releases/android/app.sha256'),
        throwsA(isA<InvalidChecksumException>()),
      );
    });

    test('streams into the configured target directory', () async {
      final HttpReleaseDownloader downloader = HttpReleaseDownloader(
        httpClient: MockClient(
          (http.Request request) async =>
              http.Response.bytes(<int>[7, 7, 7], 200),
        ),
        targetDirectory: downloadDir,
      );

      final File partial = await downloader.downloadRelease(
        release,
        (int downloaded, int? total) {},
        maxBytes: 100000,
      );

      expect(
        partial.path,
        '${downloadDir.path}${Platform.pathSeparator}ShortLink-v0.2.0.apk.part',
      );
      expect(partial.readAsBytesSync(), <int>[7, 7, 7]);
    });
  });

  group('hashing and atomic move', () {
    test('hashes bytes and files identically', () async {
      final Directory dir = Directory.systemTemp.createTempSync('update-hash');
      addTearDown(() => dir.deleteSync(recursive: true));
      final File file = File('${dir.path}${Platform.pathSeparator}payload.bin');
      await file.writeAsBytes(apkBytes);

      expect(sha256HexOfBytes(apkBytes), sha256.convert(apkBytes).toString());
      expect(await sha256HexOfFile(file), sha256HexOfBytes(apkBytes));
      expect(
        sha256HexOfBytes(<int>[]),
        'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      );
    });

    test(
      'moves a file onto an existing target and deletes the source',
      () async {
        final Directory dir = Directory.systemTemp.createTempSync(
          'update-move',
        );
        addTearDown(() => dir.deleteSync(recursive: true));
        final String separator = Platform.pathSeparator;
        final File source = File('${dir.path}${separator}new.part');
        final File target = File('${dir.path}${separator}new.apk');
        await source.writeAsBytes(<int>[1, 2, 3]);
        await target.writeAsBytes(<int>[9]);

        await moveAtomically(source, target);

        expect(source.existsSync(), isFalse);
        expect(target.readAsBytesSync(), <int>[1, 2, 3]);
        await deleteIfExists(target);
        expect(target.existsSync(), isFalse);
        // Deleting an absent file is silent, like java.io.File.delete().
        await deleteIfExists(target);
      },
    );
  });

  group('UpdateStrings and formatting', () {
    final UpdateStrings copy = updateStrings();

    test('exposes the English update copy', () {
      expect(copy.versionTitle, 'App version');
      expect(copy.checkForUpdates, 'Check');
      expect(copy.checking, 'Checking…');
      expect(copy.latestVersion, "You're using the latest version.");
      expect(
        copy.checkFailed,
        "Couldn't check for updates. Check your connection and try again.",
      );
      expect(copy.availableTitle, 'A new version is available');
      expect(copy.publishedLabel, 'Published');
      expect(copy.sizeLabel, 'Download size');
      expect(copy.updateNow, 'Update now');
      expect(copy.later, 'Later');
      expect(copy.downloading, 'Downloading update');
      expect(copy.verifying, 'Verifying update…');
      expect(copy.cancel, 'Cancel');
      expect(copy.retry, 'Retry');
      expect(copy.permissionTitle, 'Allow app installation');
      expect(
        copy.permissionBody,
        'Android needs permission to install unknown apps from ShortLink. '
        'Enable it in Settings, then return here.',
      );
      expect(copy.permissionBody.contains('unknown apps'), isTrue);
      expect(copy.openSettings, 'Open settings');
      expect(copy.readyTitle, 'Ready to install');
      expect(
        copy.readyBody,
        'The APK passed the download integrity check. Android will ask you to '
        'confirm installation.',
      );
      expect(copy.install, 'Install');
      expect(
        copy.storageError,
        "There isn't enough storage space for this update.",
      );
      expect(
        copy.verificationError,
        'The downloaded APK failed security verification and was deleted.',
      );
      expect(
        copy.invalidReleaseError,
        'This update package is incomplete or invalid. Please try again later.',
      );
      expect(
        copy.downloadError,
        "The update couldn't be downloaded. Try again.",
      );
      expect(
        copy.installError,
        "Android couldn't open the installer. Check the installation permission "
        'and try again.',
      );
    });

    test('formats byte counts', () {
      expect(formatUpdateBytes(1536), '1.5 KB');
      expect(formatUpdateBytes(10485760), '10.0 MB');
      expect(formatUpdateBytes(1024), '1.0 KB');
      expect(formatUpdateBytes(1048576), '1.0 MB');
      expect(formatUpdateBytes(1023), '1023 B');
      expect(formatUpdateBytes(0), '0 B');
      expect(formatUpdateBytes(-5), '0 B');
      expect(formatUpdateBytes(maxApkBytesLimit), '250.0 MB');
    });

    test('computes progress percent', () {
      expect(updateProgressPercent(const DownloadProgress(25, 100)), 25);
      expect(updateProgressPercent(const DownloadProgress(25, null)), isNull);
      expect(updateProgressPercent(const DownloadProgress(25, 0)), isNull);
      expect(updateProgressPercent(const DownloadProgress(25, -10)), isNull);
      expect(updateProgressPercent(const DownloadProgress(150, 100)), 100);
      expect(updateProgressPercent(const DownloadProgress(-4, 100)), 0);
      expect(updateProgressPercent(const DownloadProgress(0, 0)), isNull);
    });

    test('maps every failure reason to its message', () {
      expect(
        copy.failureMessage(UpdateFailureReason.network),
        copy.downloadError,
      );
      expect(
        copy.failureMessage(UpdateFailureReason.download),
        copy.downloadError,
      );
      expect(
        copy.failureMessage(UpdateFailureReason.invalidRelease),
        copy.invalidReleaseError,
      );
      expect(
        copy.failureMessage(UpdateFailureReason.storage),
        copy.storageError,
      );
      expect(
        copy.failureMessage(UpdateFailureReason.verification),
        copy.verificationError,
      );
      expect(
        copy.failureMessage(UpdateFailureReason.install),
        copy.installError,
      );
    });

    test('only shows the dialog for actionable release states', () {
      final File apk = File('app.apk');
      expect(shouldShowUpdateDialog(const Idle()), isFalse);
      expect(shouldShowUpdateDialog(const Checking(manual: true)), isFalse);
      expect(
        shouldShowUpdateDialog(const UpToDate(SemanticVersion(0, 2, 0))),
        isFalse,
      );
      expect(shouldShowUpdateDialog(const Available(release)), isTrue);
      expect(
        shouldShowUpdateDialog(
          const Downloading(release, DownloadProgress(1, 2)),
        ),
        isTrue,
      );
      expect(shouldShowUpdateDialog(const Verifying(release)), isTrue);
      expect(shouldShowUpdateDialog(ReadyToInstall(release, apk)), isTrue);
      expect(shouldShowUpdateDialog(PermissionRequired(release, apk)), isTrue);
      expect(
        shouldShowUpdateDialog(
          const Failed(
            UpdateFailureReason.download,
            manual: true,
            release: release,
          ),
        ),
        isTrue,
      );
      expect(
        shouldShowUpdateDialog(
          const Failed(UpdateFailureReason.network, manual: true),
        ),
        isFalse,
      );
    });

    test('resolves the release of every state', () {
      final File apk = File('app.apk');
      expect(const Idle().releaseOrNull(), isNull);
      expect(const Checking(manual: false).releaseOrNull(), isNull);
      expect(const UpToDate(SemanticVersion(0, 2, 0)).releaseOrNull(), isNull);
      expect(const Available(release).releaseOrNull(), release);
      expect(const Verifying(release).releaseOrNull(), release);
      expect(ReadyToInstall(release, apk).releaseOrNull(), release);
      expect(PermissionRequired(release, apk).releaseOrNull(), release);
      expect(
        const Failed(
          UpdateFailureReason.download,
          manual: true,
        ).releaseOrNull(),
        isNull,
      );
    });
  });

  group('UpdateCoordinator', () {
    late Directory cacheDir;

    setUp(() {
      cacheDir = Directory.systemTemp.createTempSync('update-coordinator');
    });

    tearDown(() {
      if (cacheDir.existsSync()) {
        cacheDir.deleteSync(recursive: true);
      }
    });

    UpdateCoordinator coordinator({
      ReleaseUpdateClient? client,
      ReleaseDownloader? downloader,
      int currentVersionCode = 7,
      int Function(Directory directory)? availableBytes,
      int maxApkBytes = maxApkBytesLimit,
      UpdateInstaller? installer,
    }) => UpdateCoordinator(
      releaseClient: client ?? FakeReleaseClient(release: release),
      downloader: downloader ?? FakeDownloader(apkBytes),
      currentVersion: const SemanticVersion(0, 1, 0),
      currentVersionCode: currentVersionCode,
      availableBytes:
          availableBytes ?? (Directory directory) => 0x7FFFFFFFFFFFFFFF,
      maxApkBytes: maxApkBytes,
      installer: installer,
    );

    List<File> cacheFiles() =>
        cacheDir.listSync(recursive: true).whereType<File>().toList();

    test(
      'an automatic failure returns to idle but a manual failure is visible',
      () async {
        final UpdateCoordinator update = coordinator(
          client: FakeReleaseClient(error: const ReleaseHttpException(403)),
        );

        await update.checkForUpdate(manual: false);
        expect(update.state.value, const Idle());

        await update.checkForUpdate(manual: true);
        final Failed failed = update.state.value as Failed;
        expect(failed.reason, UpdateFailureReason.network);
        expect(failed.manual, isTrue);
        expect(failed.release, isNull);
      },
    );

    test('reports up to date only for a manual check', () async {
      final UpdateCoordinator update = coordinator(
        client: FakeReleaseClient(
          release: ReleaseInfo(
            tagName: release.tagName,
            version: const SemanticVersion(0, 1, 0),
            versionCode: release.versionCode,
            title: release.title,
            body: release.body,
            publishedAt: release.publishedAt,
            apkAsset: release.apkAsset,
            sha256Asset: release.sha256Asset,
          ),
        ),
      );

      await update.checkForUpdate(manual: true);

      expect(update.state.value, const UpToDate(SemanticVersion(0, 1, 0)));
    });

    test(
      'a manual null release is up to date while an automatic one is idle',
      () async {
        final UpdateCoordinator manual = coordinator(
          client: FakeReleaseClient(release: null),
        );
        await manual.checkForUpdate(manual: true);
        expect(manual.state.value, const UpToDate(SemanticVersion(0, 1, 0)));

        final UpdateCoordinator automatic = coordinator(
          client: FakeReleaseClient(release: null),
        );
        await automatic.checkForUpdate(manual: false);
        expect(automatic.state.value, const Idle());
      },
    );

    test(
      'exposes an available release when the remote version is newer',
      () async {
        final UpdateCoordinator update = coordinator();

        await update.checkForUpdate(manual: false);

        expect(update.state.value, const Available(release));
      },
    );

    test(
      'an older remote version is idle automatically and up to date manually',
      () async {
        final ReleaseInfo older = ReleaseInfo(
          tagName: 'v0.0.9',
          version: const SemanticVersion(0, 0, 9),
          versionCode: release.versionCode,
          title: release.title,
          body: release.body,
          publishedAt: release.publishedAt,
          apkAsset: release.apkAsset,
          sha256Asset: release.sha256Asset,
        );

        final UpdateCoordinator automatic = coordinator(
          client: FakeReleaseClient(release: older),
        );
        await automatic.checkForUpdate(manual: false);
        expect(automatic.state.value, const Idle());

        final UpdateCoordinator manual = coordinator(
          client: FakeReleaseClient(release: older),
        );
        await manual.checkForUpdate(manual: true);
        expect(manual.state.value, const UpToDate(SemanticVersion(0, 1, 0)));
      },
    );

    test(
      'rejects a release with a lower version code than the installed build',
      () async {
        final ReleaseInfo downgrade = ReleaseInfo(
          tagName: release.tagName,
          version: release.version,
          versionCode: 6,
          title: release.title,
          body: release.body,
          publishedAt: release.publishedAt,
          apkAsset: release.apkAsset,
          sha256Asset: release.sha256Asset,
        );
        final UpdateCoordinator update = coordinator(
          client: FakeReleaseClient(release: downgrade),
        );

        await update.checkForUpdate(manual: true);

        final Failed failed = update.state.value as Failed;
        expect(failed.reason, UpdateFailureReason.invalidRelease);
        expect(failed.release, isNull);
      },
    );

    test('a downgrade rejection stays silent for automatic checks', () async {
      final UpdateCoordinator update = coordinator(
        client: FakeReleaseClient(
          release: ReleaseInfo(
            tagName: release.tagName,
            version: release.version,
            versionCode: 6,
            title: release.title,
            body: release.body,
            publishedAt: release.publishedAt,
            apkAsset: release.apkAsset,
            sha256Asset: release.sha256Asset,
          ),
        ),
      );

      await update.checkForUpdate(manual: false);

      expect(update.state.value, const Idle());
    });

    test(
      'accepts a release with the same version code as the installed build',
      () async {
        final UpdateCoordinator update = coordinator(
          client: FakeReleaseClient(
            release: ReleaseInfo(
              tagName: release.tagName,
              version: release.version,
              versionCode: 7,
              title: release.title,
              body: release.body,
              publishedAt: release.publishedAt,
              apkAsset: release.apkAsset,
              sha256Asset: release.sha256Asset,
            ),
          ),
          currentVersionCode: 7,
        );

        await update.checkForUpdate(manual: false);

        expect(update.state.value, isA<Available>());
      },
    );

    test('ignores a second check while one is already running', () async {
      final Completer<void> gate = Completer<void>();
      final FakeReleaseClient client = FakeReleaseClient(
        release: release,
        gate: gate,
      );
      final UpdateCoordinator update = coordinator(client: client);

      final Future<void> pending = update.checkForUpdate(manual: false);
      expect(update.state.value, const Checking(manual: false));

      await update.checkForUpdate(manual: false);
      expect(client.calls, 1);
      expect(update.state.value, const Checking(manual: false));

      gate.complete();
      await pending;
      expect(update.state.value, const Available(release));
    });

    test('downloads, checks integrity and prepares the install', () async {
      final FakeDownloader downloader = FakeDownloader(apkBytes);
      final UpdateCoordinator update = coordinator(downloader: downloader);
      await update.checkForUpdate(manual: false);

      await update.downloadAvailable(cacheDir);

      final ReadyToInstall ready = update.state.value as ReadyToInstall;
      expect(
        ready.apkFile.path,
        '${cacheDir.path}${Platform.pathSeparator}updates'
        '${Platform.pathSeparator}ShortLink-v0.2.0.apk',
      );
      expect(ready.apkFile.readAsBytesSync(), apkBytes);
      expect(downloader.sha256Calls, 1);
      expect(downloader.progress.last.downloadedBytes, apkBytes.length);
      expect(File('${ready.apkFile.path}.part').existsSync(), isFalse);

      expect(update.prepareInstall(false), const RequestPermission());
      expect(update.state.value, isA<PermissionRequired>());
      expect(update.onInstallPermissionResult(true), Install(ready.apkFile));
      expect(update.state.value, isA<ReadyToInstall>());

      update.installationFailed();
      expect(
        (update.state.value as Failed).reason,
        UpdateFailureReason.install,
      );
      expect((update.state.value as Failed).manual, isTrue);
      expect((update.state.value as Failed).release, release);
    });

    test(
      'returns to ready to install when the permission is already granted',
      () async {
        final UpdateCoordinator update = coordinator();
        await update.checkForUpdate(manual: false);
        await update.downloadAvailable(cacheDir);
        final ReadyToInstall ready = update.state.value as ReadyToInstall;

        expect(update.prepareInstall(false), const RequestPermission());
        expect(update.prepareInstall(false), const RequestPermission());
        expect(update.state.value, isA<PermissionRequired>());
        expect(update.prepareInstall(true), Install(ready.apkFile));
        expect(update.state.value, isA<ReadyToInstall>());
        expect(update.onInstallPermissionResult(false), isNull);
        expect(update.state.value, isA<ReadyToInstall>());
      },
    );

    test('ignores install preparation without a verified download', () async {
      final UpdateCoordinator update = coordinator();

      expect(update.prepareInstall(true), isNull);
      expect(update.onInstallPermissionResult(true), isNull);
      update.installationFailed();
      expect(update.state.value, const Idle());
    });

    test('a checksum mismatch deletes the files and allows a retry', () async {
      final UpdateCoordinator update = coordinator(
        downloader: FakeDownloader(apkBytes, checksum: '0' * 64),
      );
      await update.checkForUpdate(manual: false);

      await update.downloadAvailable(cacheDir);

      final Failed failed = update.state.value as Failed;
      expect(failed.reason, UpdateFailureReason.verification);
      expect(failed.release, release);
      expect(cacheFiles(), isEmpty);
      expect(update.prepareInstall(true), isNull);
    });

    test('a downloaded size mismatch is a verification failure', () async {
      final UpdateCoordinator update = coordinator(
        downloader: FakeDownloader(utf8.encode('four')),
      );
      await update.checkForUpdate(manual: false);

      await update.downloadAvailable(cacheDir);

      expect(
        (update.state.value as Failed).reason,
        UpdateFailureReason.verification,
      );
      expect(cacheFiles(), isEmpty);
    });

    test('an undersized free space is a storage failure', () async {
      final UpdateCoordinator update = coordinator(
        availableBytes: (Directory directory) => 0,
      );
      await update.checkForUpdate(manual: false);

      await update.downloadAvailable(cacheDir);

      expect(
        (update.state.value as Failed).reason,
        UpdateFailureReason.storage,
      );
      expect((update.state.value as Failed).release, release);
      expect(cacheFiles(), isEmpty);
    });

    test('a release above the APK ceiling is a storage failure', () async {
      final UpdateCoordinator update = coordinator(maxApkBytes: 2);
      await update.checkForUpdate(manual: false);

      await update.downloadAvailable(cacheDir);

      expect(
        (update.state.value as Failed).reason,
        UpdateFailureReason.storage,
      );
      expect(cacheFiles(), isEmpty);
    });

    test('an HTTP download failure is a network failure', () async {
      final UpdateCoordinator update = coordinator(
        downloader: FakeDownloader(
          apkBytes,
          downloadError: const ReleaseHttpException(500),
        ),
      );
      await update.checkForUpdate(manual: false);

      await update.downloadAvailable(cacheDir);

      expect(
        (update.state.value as Failed).reason,
        UpdateFailureReason.network,
      );
    });

    test('an unexpected download failure is a download failure', () async {
      final UpdateCoordinator update = coordinator(
        downloader: FakeDownloader(
          apkBytes,
          downloadError: SocketException('reset'),
        ),
      );
      await update.checkForUpdate(manual: false);

      await update.downloadAvailable(cacheDir);

      expect(
        (update.state.value as Failed).reason,
        UpdateFailureReason.download,
      );
    });

    test('downloads again after a failure that kept the release', () async {
      final FakeDownloader failing = FakeDownloader(
        apkBytes,
        checksum: '0' * 64,
      );
      final UpdateCoordinator update = coordinator(downloader: failing);
      await update.checkForUpdate(manual: false);
      await update.downloadAvailable(cacheDir);
      expect(update.state.value, isA<Failed>());

      failing.checksum = null;
      await update.downloadAvailable(cacheDir);

      expect(update.state.value, isA<ReadyToInstall>());
    });

    test('cannot download without an available or failed release', () async {
      final FakeDownloader downloader = FakeDownloader(apkBytes);
      final UpdateCoordinator update = coordinator(downloader: downloader);

      await update.downloadAvailable(cacheDir);
      expect(downloader.downloads, 0);
      expect(update.state.value, const Idle());

      final UpdateCoordinator upToDate = coordinator(
        client: FakeReleaseClient(release: null),
        downloader: downloader,
      );
      await upToDate.checkForUpdate(manual: true);
      expect(upToDate.state.value, isA<UpToDate>());
      await upToDate.downloadAvailable(cacheDir);
      expect(downloader.downloads, 0);

      final UpdateCoordinator idle = coordinator(
        client: FakeReleaseClient(release: null),
        downloader: downloader,
      );
      await idle.checkForUpdate(manual: false);
      expect(idle.state.value, const Idle());
      await idle.downloadAvailable(cacheDir);
      expect(downloader.downloads, 0);
    });

    test('a failure without a release cannot download', () async {
      final FakeDownloader downloader = FakeDownloader(apkBytes);
      final UpdateCoordinator update = coordinator(
        client: FakeReleaseClient(error: const ReleaseHttpException(500)),
        downloader: downloader,
      );
      await update.checkForUpdate(manual: true);
      expect(update.state.value, isA<Failed>());

      await update.downloadAvailable(cacheDir);

      expect(downloader.downloads, 0);
      expect(update.state.value, isA<Failed>());
    });

    test('drops a second download while one is running', () async {
      final Completer<void> gate = Completer<void>();
      final FakeDownloader downloader = FakeDownloader(apkBytes, gate: gate);
      final UpdateCoordinator update = coordinator(downloader: downloader);
      await update.checkForUpdate(manual: false);

      final Future<void> pending = update.downloadAvailable(cacheDir);
      final Downloading downloading = await waitForState<Downloading>(update);
      expect(update.downloadInFlight, isTrue);
      expect(downloading.release, release);
      expect(downloading.progress, const DownloadProgress(0, 3));

      await update.downloadAvailable(cacheDir);
      expect(downloader.downloads, 1);

      gate.complete();
      await pending;
      expect(update.downloadInFlight, isFalse);
      expect(update.state.value, isA<ReadyToInstall>());
    });

    test(
      'cancellation returns to available and deletes the partial file',
      () async {
        final UpdateCoordinator update = coordinator(
          downloader: FakeDownloader(apkBytes, cancel: true),
        );
        await update.checkForUpdate(manual: false);

        try {
          await update.downloadAvailable(cacheDir);
          fail('cancellation must rethrow');
        } on DownloadCancelledException {
          update.downloadCancelled();
        }

        expect(update.state.value, const Available(release));
        expect(cacheFiles(), isEmpty);
      },
    );

    test('cancelling a running download returns to available', () async {
      final Completer<void> gate = Completer<void>();
      late final UpdateCoordinator update;
      final FakeDownloader downloader = FakeDownloader(
        apkBytes,
        gate: gate,
        isCancelled: () => update.cancelRequested,
      );
      update = coordinator(downloader: downloader);
      await update.checkForUpdate(manual: false);

      final Future<void> pending = update.downloadAvailable(cacheDir);
      await waitForState<Downloading>(update);
      expect(update.cancelRequested, isFalse);

      update.cancelDownload();
      expect(update.cancelRequested, isTrue);
      expect(update.state.value, const Available(release));

      gate.complete();
      await expectLater(pending, throwsA(isA<DownloadCancelledException>()));

      expect(update.state.value, const Available(release));
      expect(cacheFiles(), isEmpty);
    });

    test(
      'downloadCancelled only reacts to downloading and verifying',
      () async {
        final UpdateCoordinator update = coordinator();
        update.downloadCancelled();
        expect(update.state.value, const Idle());

        await update.checkForUpdate(manual: false);
        update.downloadCancelled();
        expect(update.state.value, const Available(release));
      },
    );

    test('dismiss hides every pending state', () async {
      final UpdateCoordinator available = coordinator();
      await available.checkForUpdate(manual: false);
      expect(available.state.value, const Available(release));
      available.dismiss();
      expect(available.state.value, const Idle());

      final UpdateCoordinator failed = coordinator();
      await failed.checkForUpdate(manual: false);
      await failed.downloadAvailable(cacheDir);
      failed.installationFailed();
      failed.dismiss();
      expect(failed.state.value, const Idle());

      final UpdateCoordinator permission = coordinator();
      await permission.checkForUpdate(manual: false);
      await permission.downloadAvailable(cacheDir);
      permission.prepareInstall(false);
      expect(permission.state.value, isA<PermissionRequired>());
      permission.dismiss();
      expect(permission.state.value, const Idle());
    });

    test('a running download is not dismissible', () async {
      final Completer<void> gate = Completer<void>();
      final FakeDownloader downloader = FakeDownloader(apkBytes, gate: gate);
      final UpdateCoordinator update = coordinator(downloader: downloader);
      await update.checkForUpdate(manual: false);

      final Future<void> pending = update.downloadAvailable(cacheDir);
      await waitForState<Downloading>(update);

      update.dismiss();
      expect(update.state.value, isA<Downloading>());

      gate.complete();
      await pending;
      expect(update.state.value, isA<ReadyToInstall>());
    });

    test('delegates the install flow to the platform installer', () async {
      final FakeInstaller installer = FakeInstaller();
      final UpdateCoordinator update = coordinator(installer: installer);
      await update.checkForUpdate(manual: false);
      await update.downloadAvailable(cacheDir);

      await update.handleInstallRequest(
        await update.prepareInstallWithInstaller(),
      );

      expect(installer.permissionChecks, 1);
      expect(
        installer.installed.map((File file) => file.path).toList(),
        <String>[
          '${cacheDir.path}${Platform.pathSeparator}updates'
              '${Platform.pathSeparator}ShortLink-v0.2.0.apk',
        ],
      );
      expect(update.state.value, isA<ReadyToInstall>());
    });

    test('reports a failed installer start as an install failure', () async {
      final FakeInstaller installer = FakeInstaller(failInstall: true);
      final UpdateCoordinator update = coordinator(installer: installer);
      await update.checkForUpdate(manual: false);
      await update.downloadAvailable(cacheDir);

      await update.handleInstallRequest(
        await update.prepareInstallWithInstaller(),
      );

      expect(
        (update.state.value as Failed).reason,
        UpdateFailureReason.install,
      );
    });

    test('requests the install permission through the installer', () async {
      final FakeInstaller installer = FakeInstaller(canInstall: false);
      final UpdateCoordinator update = coordinator(installer: installer);
      await update.checkForUpdate(manual: false);
      await update.downloadAvailable(cacheDir);

      final InstallRequest? request = await update
          .prepareInstallWithInstaller();
      expect(request, const RequestPermission());
      await update.requestInstallPermission();

      expect(installer.permissionRequests, 1);
      expect(update.state.value, isA<PermissionRequired>());
    });
  });
}

/// Waits until the coordinator reaches [T], polling briefly.
///
/// The download path performs real file I/O before it announces [Downloading],
/// so a fixed number of event-loop turns is not enough to observe that state.
Future<T> waitForState<T extends UpdateState>(UpdateCoordinator update) async {
  for (int attempt = 0; attempt < 400; attempt++) {
    final UpdateState value = update.state.value;
    if (value is T) {
      return value;
    }
    await Future<void>.delayed(const Duration(milliseconds: 5));
  }
  throw StateError('state never became $T, still ${update.state.value}');
}

/// Release client double mirroring `UpdateCoordinatorTest.FakeReleaseClient`.
class FakeReleaseClient implements ReleaseUpdateClient {
  FakeReleaseClient({this.release, this.error, this.gate});

  final ReleaseInfo? release;
  final Object? error;

  /// When set, the first call waits for it, so a test can inspect `Checking`.
  final Completer<void>? gate;

  int calls = 0;

  @override
  Future<ReleaseInfo?> fetchLatestStable() async {
    calls++;
    await gate?.future;
    final Object? failure = error;
    if (failure != null) {
      throw failure;
    }
    return release;
  }
}

/// Downloader double mirroring `UpdateCoordinatorTest.FakeDownloader`.
class FakeDownloader implements ReleaseDownloader {
  FakeDownloader(
    this.content, {
    this.checksum,
    this.cancel = false,
    this.gate,
    this.downloadError,
    this.isCancelled,
  });

  final List<int> content;
  String? checksum;
  final bool cancel;
  final Completer<void>? gate;
  final Object? downloadError;

  /// Port of the real downloader's per-chunk `ensureActive()` poll.
  final bool Function()? isCancelled;

  int downloads = 0;
  int sha256Calls = 0;
  final List<DownloadProgress> progress = <DownloadProgress>[];

  @override
  Future<void> download(
    String url,
    File destination,
    int maxBytes,
    void Function(int downloaded, int? total) onProgress,
  ) async {
    downloads++;
    await gate?.future;
    if (isCancelled?.call() ?? false) {
      throw const DownloadCancelledException();
    }
    final Object? failure = downloadError;
    if (failure != null) {
      throw failure;
    }
    await destination.parent.create(recursive: true);
    await destination.writeAsBytes(content);
    progress.add(DownloadProgress(content.length, content.length));
    onProgress(content.length, content.length);
    if (cancel) {
      await destination.delete();
      throw const DownloadCancelledException();
    }
  }

  @override
  Future<String> fetchSha256(String url) async {
    sha256Calls++;
    return checksum ?? sha256.convert(content).toString();
  }
}

/// Installer double standing in for `AndroidUpdateInstaller`.
class FakeInstaller implements UpdateInstaller {
  FakeInstaller({this.canInstall = true, this.failInstall = false});

  final bool canInstall;
  final bool failInstall;

  int permissionChecks = 0;
  int permissionRequests = 0;
  final List<File> installed = <File>[];

  @override
  Future<bool> canRequestPackageInstalls() async {
    permissionChecks++;
    return canInstall;
  }

  @override
  Future<void> requestInstallPermission() async {
    permissionRequests++;
  }

  @override
  Future<void> install(File apk) async {
    if (failInstall) {
      throw SocketException('installer missing');
    }
    installed.add(apk);
  }
}
