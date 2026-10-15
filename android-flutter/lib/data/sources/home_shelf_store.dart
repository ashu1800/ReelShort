/// Home-shelf cache persistence boundary.
///
/// Port of `com.reelshort.app.session.HomeShelfStore`, `InMemoryHomeShelfStore`
/// and `FileHomeShelfStore`. The cache exists so a cold start can render
/// instantly while the state layer refreshes in the background; a corrupt cache
/// self-heals to an empty list instead of failing startup.
library;

import 'dart:convert';
import 'dart:io';

import '../models/app_models.dart';
import '../network/json_read.dart';

abstract interface class HomeShelfStore {
  Future<List<BookSummary>> loadHomeShelf();

  Future<void> saveHomeShelf(List<BookSummary> shelf);

  Future<void> clearHomeShelf();
}

class InMemoryHomeShelfStore implements HomeShelfStore {
  List<BookSummary> _shelf = const <BookSummary>[];

  @override
  Future<List<BookSummary>> loadHomeShelf() async => _shelf;

  @override
  Future<void> saveHomeShelf(List<BookSummary> shelf) async {
    _shelf = List<BookSummary>.unmodifiable(shelf);
  }

  @override
  Future<void> clearHomeShelf() async {
    _shelf = const <BookSummary>[];
  }
}

class FileHomeShelfStore implements HomeShelfStore {
  FileHomeShelfStore(this.cacheFile);

  final File cacheFile;

  File get _tempFile => File('${cacheFile.parent.path}'
      '${Platform.pathSeparator}${cacheFile.uri.pathSegments.last}.tmp');

  @override
  Future<List<BookSummary>> loadHomeShelf() async {
    if (!cacheFile.existsSync()) {
      return const <BookSummary>[];
    }
    try {
      final List<Object?> raw = asJsonArray(
        jsonDecode(await cacheFile.readAsString()),
        'home shelf cache',
      );
      return raw
          .map((Object? item) => _bookFromJson(asJsonObject(item, 'book')))
          .toList(growable: false);
    } on FileSystemException {
      await _deleteCacheFile();
      return const <BookSummary>[];
    } on FormatException {
      await _deleteCacheFile();
      return const <BookSummary>[];
    } on JsonShapeException {
      await _deleteCacheFile();
      return const <BookSummary>[];
    } on ArgumentError {
      await _deleteCacheFile();
      return const <BookSummary>[];
    }
  }

  @override
  Future<void> saveHomeShelf(List<BookSummary> shelf) async {
    cacheFile.parent.createSync(recursive: true);
    final String encoded = jsonEncode(
      shelf.map(_bookToJson).toList(growable: false),
    );
    final File temp = _tempFile;
    await temp.writeAsString(encoded);
    if (cacheFile.existsSync()) {
      try {
        cacheFile.deleteSync();
      } on FileSystemException {
        await temp.delete();
        throw const FileSystemException(
          'failed to replace home shelf cache file',
        );
      }
    }
    try {
      temp.renameSync(cacheFile.path);
    } on FileSystemException {
      await temp.delete();
      throw const FileSystemException(
        'failed to persist home shelf cache file',
      );
    }
  }

  @override
  Future<void> clearHomeShelf() => _deleteCacheFile();

  Future<void> _deleteCacheFile() async {
    if (cacheFile.existsSync()) {
      await cacheFile.delete();
    }
    final File temp = _tempFile;
    if (temp.existsSync()) {
      await temp.delete();
    }
  }

  static BookSummary _bookFromJson(Map<String, dynamic> json) => BookSummary(
    id: readString(json, 'id'),
    title: readString(json, 'title'),
    filteredTitle: readString(json, 'filteredTitle'),
    coverUrl: readStringOrNull(json, 'coverUrl'),
    description: readString(json, 'description'),
    chapterCount: readInt(json, 'chapterCount'),
  );

  static Map<String, Object?> _bookToJson(BookSummary book) => <String, Object?>{
    'id': book.id,
    'title': book.title,
    'filteredTitle': book.filteredTitle,
    'coverUrl': book.coverUrl,
    'description': book.description,
    'chapterCount': book.chapterCount,
  };
}
