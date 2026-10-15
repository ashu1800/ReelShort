/// Session persistence boundary.
///
/// Port of `com.reelshort.app.session.SessionStore`, `InMemorySessionStore`,
/// `FileSessionStore` and `FailClosedSessionStoreFactory`.
library;

import 'dart:convert';
import 'dart:io';

import '../models/app_models.dart';
import '../network/json_read.dart';

abstract interface class SessionStore {
  Future<AuthSession?> loadSession();

  Future<void> saveSession(AuthSession session);

  Future<void> clearSession();
}

/// Process-lifetime session used when encryption is unavailable.
class InMemorySessionStore implements SessionStore {
  AuthSession? _session;

  @override
  Future<AuthSession?> loadSession() async => _session;

  @override
  Future<void> saveSession(AuthSession session) async {
    _session = session;
  }

  @override
  Future<void> clearSession() async {
    _session = null;
  }
}

/// Plain JSON-file session, kept for tests and non-Android hosts.
///
/// Writing is atomic: a sibling `.tmp` file is written, the previous file is
/// removed, then the temp file is renamed. Reading self-heals by deleting a
/// corrupt file and reporting "no session" instead of failing startup.
class FileSessionStore implements SessionStore {
  FileSessionStore(this.sessionFile);

  final File sessionFile;

  File get _tempFile =>
      File('${sessionFile.parent.path}${Platform.pathSeparator}'
          '${sessionFile.uri.pathSegments.last}.tmp');

  @override
  Future<AuthSession?> loadSession() async {
    if (!sessionFile.existsSync()) {
      return null;
    }
    try {
      final Map<String, dynamic> json = asJsonObject(
        jsonDecode(await sessionFile.readAsString()),
        'session',
      );
      return AuthSession(
        username: readString(json, 'username'),
        token: readString(json, 'token'),
        tokenType: readString(json, 'tokenType'),
        phoneE164: readStringOrNull(json, 'phoneE164'),
      );
    } on FileSystemException {
      await _deleteSessionFile();
      return null;
    } on FormatException {
      await _deleteSessionFile();
      return null;
    } on JsonShapeException {
      await _deleteSessionFile();
      return null;
    } on ArgumentError {
      await _deleteSessionFile();
      return null;
    }
  }

  @override
  Future<void> saveSession(AuthSession session) async {
    sessionFile.parent.createSync(recursive: true);
    // Key order and explicit nulls mirror kotlinx.serialization output.
    final String encoded = jsonEncode(<String, Object?>{
      'username': session.username,
      'phoneE164': session.phoneE164,
      'token': session.token,
      'tokenType': session.tokenType,
    });
    final File temp = _tempFile;
    await temp.writeAsString(encoded);
    if (sessionFile.existsSync()) {
      try {
        sessionFile.deleteSync();
      } on FileSystemException {
        await temp.delete();
        throw const FileSystemException('failed to replace session file');
      }
    }
    try {
      temp.renameSync(sessionFile.path);
    } on FileSystemException {
      await temp.delete();
      throw const FileSystemException('failed to persist session file');
    }
  }

  @override
  Future<void> clearSession() => _deleteSessionFile();

  Future<void> _deleteSessionFile() async {
    if (sessionFile.existsSync()) {
      await sessionFile.delete();
    }
    final File temp = _tempFile;
    if (temp.existsSync()) {
      await temp.delete();
    }
  }
}

/// Guarantees the app never keeps a plaintext token on disk.
///
/// The legacy plaintext session file is deleted first; if it cannot be removed
/// the app refuses to start rather than silently downgrading to plaintext (the
/// Kotlin `check(...)` behaves the same way). A missing secure store means
/// encryption is unavailable, in which case only an in-process session is used.
class FailClosedSessionStoreFactory {
  const FailClosedSessionStoreFactory._();

  static SessionStore create(
    SessionStore? secureStore, {
    required File legacyPlaintextFile,
  }) {
    if (legacyPlaintextFile.existsSync()) {
      try {
        legacyPlaintextFile.deleteSync();
      } on FileSystemException {
        throw StateError('Unable to remove legacy plaintext session');
      }
    }
    return secureStore ?? InMemorySessionStore();
  }
}
