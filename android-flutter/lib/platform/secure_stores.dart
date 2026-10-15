/// Platform-encrypted persistence for the session and the remembered credentials.
///
/// Mirrors the Android implementation, which stores both through AndroidX Security Crypto
/// (`EncryptedSharedPreferences`). `flutter_secure_storage` v11 is backed by the same Android
/// Keystore/`EncryptedSharedPreferences` machinery, so the storage class of the token on disk is
/// unchanged.
///
/// Both stores are **fail-closed**: when the encrypted backend throws, the session degrades to a
/// process-lifetime in-memory session and the credentials degrade to an always-empty store. A
/// plaintext Bearer token is never written anywhere.
library;

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../data/models/app_models.dart';
import '../data/sources/credential_store.dart';
import '../data/sources/session_store.dart';

/// Encrypted session store with an in-process fallback once the backend fails.
///
/// Semantics carried over from `AndroidSessionStore`:
///
/// * a session needs all of `username`, `token` and `tokenType` to be considered valid;
/// * a read failure is indistinguishable from "no session" for the UI, because the app must keep
///   starting as a guest;
/// * once the backend fails, every later operation stays in memory for the rest of the process, so
///   a transient keystore error can never silently start persisting plaintext.
class SecureSessionStore implements SessionStore {
  SecureSessionStore(this._storage, {this.onUnavailable});

  static const String usernameKey = 'username';
  static const String phoneE164Key = 'phoneE164';
  static const String tokenKey = 'token';
  static const String tokenTypeKey = 'tokenType';

  final FlutterSecureStorage _storage;

  /// Invoked once when the encrypted backend turns out to be unusable.
  final void Function(Object error)? onUnavailable;

  bool _degraded = false;
  AuthSession? _fallback;

  /// Whether the encrypted backend stopped working during this process.
  bool get isDegraded => _degraded;

  /// Reads the token once to find out whether the encrypted backend is usable.
  ///
  /// Returns false (and permanently degrades this instance) when it is not, which is what makes
  /// [FailClosedSessionStoreFactory] pick the in-memory store.
  Future<bool> probe() async {
    try {
      await _storage.read(key: tokenKey);
      return true;
    } on Object catch (error) {
      _degrade(error);
      return false;
    }
  }

  @override
  Future<AuthSession?> loadSession() async {
    if (_degraded) {
      return _fallback;
    }
    try {
      final String? token = await _storage.read(key: tokenKey);
      if (token == null || token.isEmpty) {
        return null;
      }
      final String? username = await _storage.read(key: usernameKey);
      final String? tokenType = await _storage.read(key: tokenTypeKey);
      if (username == null || username.isEmpty || tokenType == null || tokenType.isEmpty) {
        return null;
      }
      return AuthSession(
        username: username,
        token: token,
        tokenType: tokenType,
        phoneE164: await _storage.read(key: phoneE164Key),
      );
    } on Object catch (error) {
      _degrade(error);
      return null;
    }
  }

  @override
  Future<void> saveSession(AuthSession session) async {
    if (_degraded) {
      _fallback = session;
      return;
    }
    try {
      await _storage.write(key: usernameKey, value: session.username);
      await _storage.write(key: tokenKey, value: session.token);
      await _storage.write(key: tokenTypeKey, value: session.tokenType);
      final String? phone = session.phoneE164;
      if (phone == null) {
        await _storage.delete(key: phoneE164Key);
      } else {
        await _storage.write(key: phoneE164Key, value: phone);
      }
    } on Object catch (error) {
      _degrade(error);
      _fallback = session;
    }
  }

  @override
  Future<void> clearSession() async {
    if (_degraded) {
      _fallback = null;
      return;
    }
    try {
      await _storage.delete(key: usernameKey);
      await _storage.delete(key: tokenKey);
      await _storage.delete(key: tokenTypeKey);
      await _storage.delete(key: phoneE164Key);
    } on Object catch (error) {
      _degrade(error);
      _fallback = null;
    }
  }

  void _degrade(Object error) {
    if (_degraded) {
      return;
    }
    _degraded = true;
    onUnavailable?.call(error);
  }
}

/// Encrypted remembered-credential store.
///
/// When the encrypted backend is unavailable this behaves like the Android fallback: reads always
/// report "nothing remembered" and writes are ignored, so the user is simply asked to log in again.
class SecureCredentialStore implements CredentialStore {
  SecureCredentialStore(this._storage, {this.onUnavailable});

  static const String usernameKey = 'credentialUsername';
  static const String passwordKey = 'credentialPassword';
  static const String rememberKey = 'credentialRemember';

  final FlutterSecureStorage _storage;
  final void Function(Object error)? onUnavailable;

  bool _degraded = false;

  /// Whether the encrypted backend stopped working during this process.
  bool get isDegraded => _degraded;

  @override
  Future<SavedCredentials?> loadCredentials() async {
    if (_degraded) {
      return null;
    }
    try {
      final String? username = await _storage.read(key: usernameKey);
      final String? password = await _storage.read(key: passwordKey);
      if (username == null || password == null) {
        return null;
      }
      return SavedCredentials(
        username: username,
        password: password,
        rememberPassword: await _storage.read(key: rememberKey) == 'true',
      );
    } on Object catch (error) {
      _degrade(error);
      return null;
    }
  }

  @override
  Future<void> saveCredentials(SavedCredentials credentials) async {
    if (_degraded) {
      return;
    }
    try {
      await _storage.write(key: usernameKey, value: credentials.username);
      await _storage.write(key: passwordKey, value: credentials.password);
      await _storage.write(
        key: rememberKey,
        value: credentials.rememberPassword.toString(),
      );
    } on Object catch (error) {
      _degrade(error);
    }
  }

  @override
  Future<void> clearCredentials() async {
    if (_degraded) {
      return;
    }
    try {
      await _storage.delete(key: usernameKey);
      await _storage.delete(key: passwordKey);
      await _storage.delete(key: rememberKey);
    } on Object catch (error) {
      _degrade(error);
    }
  }

  void _degrade(Object error) {
    if (_degraded) {
      return;
    }
    _degraded = true;
    onUnavailable?.call(error);
  }
}
