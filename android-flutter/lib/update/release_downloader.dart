/// Streaming release download plus the SHA-256 and atomic-move helpers.
///
/// Port of `OkHttpReleaseDownloader.kt` and of the `sha256`/`moveAtomically`
/// helpers of `UpdateCoordinator.kt`. Everything that blocks is either streamed
/// asynchronously or dispatched to a worker isolate, which is what the Kotlin
/// original expresses with `Dispatchers.IO`.
library;

import 'dart:convert';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;

import 'release_models.dart';

/// User-Agent of every asset download (`ShortLink-Android-Updater`).
const String updaterUserAgent = 'ShortLink-Android-Updater';

/// `MAX_CHECKSUM_BYTES`: the `.sha256` sidecar is rejected above this size.
const int maxChecksumBytes = 1024;

/// `CHECKSUM_PATTERN`: a 64-hex digest with an optional `sha256sum` file name.
final RegExp checksumPattern = RegExp(
  r'^([a-fA-F0-9]{64})(?:\s+\*?[^\r\n]+)?$',
);

/// Downloads release assets with `package:http`.
class HttpReleaseDownloader implements ReleaseDownloader {
  HttpReleaseDownloader({
    required this.httpClient,
    this.targetDirectory,
    this.isCancelled,
  });

  final http.Client httpClient;

  /// Port of the downloader's per-chunk `ensureActive()` check.
  ///
  /// Dart has no coroutine cancellation, so the coordinator's cancel request is
  /// polled through this hook instead of a `CancellationException` thrown from
  /// the coroutine context.
  final bool Function()? isCancelled;

  /// Default destination directory (`cacheDir/updates` in the Android layer).
  ///
  /// Settable so a caller — or a test — can point the downloader at a temp dir
  /// without rebuilding it.
  Directory? targetDirectory;

  @override
  Future<void> download(
    String url,
    File destination,
    int maxBytes,
    void Function(int downloaded, int? total) onProgress,
  ) async {
    await destination.parent.create(recursive: true);
    await deleteIfExists(destination);
    try {
      final http.StreamedResponse response = await _send(url);
      final int? contentLength = response.contentLength;
      if (contentLength != null && contentLength > maxBytes) {
        throw DownloadTooLargeException(maxBytes);
      }
      final IOSink sink = destination.openWrite();
      int downloaded = 0;
      try {
        await for (final List<int> chunk in response.stream) {
          if (isCancelled?.call() ?? false) {
            throw const DownloadCancelledException();
          }
          downloaded += chunk.length;
          if (downloaded > maxBytes) {
            throw DownloadTooLargeException(maxBytes);
          }
          sink.add(chunk);
          onProgress(downloaded, contentLength);
        }
        await sink.flush();
      } finally {
        await sink.close();
      }
    } catch (_) {
      // The Kotlin downloader deletes whatever it streamed before rethrowing.
      await deleteIfExists(destination);
      rethrow;
    }
  }

  @override
  Future<String> fetchSha256(String url) async {
    final http.StreamedResponse response = await _send(url);
    final int? contentLength = response.contentLength;
    if (contentLength != null && contentLength > maxChecksumBytes) {
      throw const InvalidChecksumException();
    }
    final BytesBuilder builder = BytesBuilder(copy: false);
    int total = 0;
    await for (final List<int> chunk in response.stream) {
      total += chunk.length;
      if (total > maxChecksumBytes) {
        throw const InvalidChecksumException();
      }
      builder.add(chunk);
    }
    final String value = utf8
        .decode(builder.takeBytes(), allowMalformed: true)
        .trim();
    final RegExpMatch? match = checksumPattern.firstMatch(value);
    final String? digest = match?.group(1)?.toLowerCase();
    if (digest == null) {
      throw const InvalidChecksumException();
    }
    return digest;
  }

  /// Streams [release]'s APK into [targetDirectory] as `<apkName>.part`.
  ///
  /// Convenience for callers that keep the download directory on the downloader;
  /// `UpdateCoordinator` resolves its own files exactly like the Kotlin original.
  Future<File> downloadRelease(
    ReleaseInfo release,
    void Function(int downloaded, int? total) onProgress, {
    Directory? targetDirectory,
    int maxBytes = maxApkBytesLimit,
  }) async {
    final Directory directory =
        targetDirectory ?? this.targetDirectory ?? Directory.systemTemp;
    final File partial = File(
      '${directory.path}${Platform.pathSeparator}${release.apkAsset.name}.part',
    );
    await download(release.apkAsset.downloadUrl, partial, maxBytes, onProgress);
    return partial;
  }

  /// `GET` [url] with the updater User-Agent, rejecting non-2xx responses.
  Future<http.StreamedResponse> _send(String url) async {
    final http.Request request = http.Request('GET', Uri.parse(url))
      ..headers['User-Agent'] = updaterUserAgent;
    final http.StreamedResponse response = await httpClient.send(request);
    if (response.statusCode < 200 || response.statusCode > 299) {
      throw ReleaseHttpException(response.statusCode);
    }
    return response;
  }
}

/// Deletes [file] when it exists, mirroring `java.io.File.delete()`'s silence.
Future<void> deleteIfExists(File file) async {
  try {
    await file.delete();
  } on FileSystemException {
    // Already gone (or never created): the Kotlin `delete()` returns false here.
  }
}

/// Moves [source] onto [target], preferring the atomic rename.
///
/// Port of the coordinator's `moveAtomically`: the atomic move is attempted
/// first and a copy-then-delete stands in for the
/// `AtomicMoveNotSupportedException` fallback.
Future<void> moveAtomically(File source, File target) async {
  try {
    await source.rename(target.path);
  } on FileSystemException {
    await source.copy(target.path);
    await deleteIfExists(source);
  }
}

/// Lowercase SHA-256 of [content] (`sha256(ByteArray)`).
String sha256HexOfBytes(List<int> content) =>
    sha256.convert(content).toString();

/// Lowercase SHA-256 of [file] (`sha256(File)`), computed off the calling isolate.
Future<String> sha256HexOfFile(File file) {
  final String path = file.path;
  return Isolate.run(() => _sha256HexOfFileSync(path));
}

/// Streams a file through the `crypto` digest sink in bounded chunks.
String _sha256HexOfFileSync(String path) {
  final RandomAccessFile reader = File(path).openSync();
  try {
    final _DigestCollector collector = _DigestCollector();
    final ByteConversionSink input = sha256.startChunkedConversion(collector);
    while (true) {
      final Uint8List chunk = reader.readSync(65536);
      if (chunk.isEmpty) {
        break;
      }
      input.add(chunk);
    }
    input.close();
    final Digest? digest = collector.value;
    if (digest == null) {
      throw StateError('SHA-256 sink produced no digest for $path');
    }
    return digest.toString();
  } finally {
    reader.closeSync();
  }
}

/// Collects the single [Digest] a chunked `crypto` conversion emits.
class _DigestCollector implements Sink<Digest> {
  Digest? value;

  @override
  void add(Digest data) {
    value = data;
  }

  @override
  void close() {}
}
