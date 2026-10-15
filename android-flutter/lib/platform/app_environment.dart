/// Composition root: resolves the host platform's storage locations and builds the object graph.
///
/// Ordering matters and mirrors the Android composition root:
///
/// 1. probe the encrypted backend, then run the **fail-closed** session factory, which removes the
///    legacy plaintext session file before anything else can read it;
/// 2. build the API client with a late-bound token provider (the repository owns the token, the
///    client reads it per request);
/// 3. build the repository over the resolved stores.
///
/// If the platform storage directories are unavailable the whole graph degrades to in-memory
/// stores so the app still starts, exactly like the Android fallbacks.
library;

import 'dart:io';

import 'package:flutter/foundation.dart';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/api_config.dart';
import '../data/app_data_source.dart';
import '../data/app_repository.dart';
import '../data/network/http_reel_short_api_client.dart';
import '../data/sources/credential_store.dart';
import '../data/sources/home_shelf_store.dart';
import '../data/sources/language_preference_store.dart';
import '../data/sources/session_store.dart';
import 'preference_stores.dart';
import 'secure_stores.dart';

/// Reads the repository's current token lazily, breaking the client/repository cycle.
class _LateBoundToken {
  AppRepository? _repository;

  void bind(AppRepository repository) => _repository = repository;

  Future<String?> read() async => _repository?.currentToken;
}

/// The resolved object graph plus the storage health facts the UI may want to surface.
class AppEnvironment {
  AppEnvironment({
    required this.dataSource,
    required this.sessionStore,
    required this.credentialStore,
    required this.languageStore,
    required this.homeShelfStore,
    required this.apiBaseUrl,
    required this.encryptedStorageAvailable,
    required this.durableStorageAvailable,
  });

  /// Cache file name, identical to the Android file name.
  static const String homeShelfCacheFileName = 'home-shelf-cache.json';

  /// Legacy plaintext session file that must be removed on first launch.
  static const String legacyPlaintextSessionFileName = 'session.json';

  final AppDataSource dataSource;
  final SessionStore sessionStore;
  final CredentialStore credentialStore;
  final LanguagePreferenceStore languageStore;
  final HomeShelfStore homeShelfStore;
  final String apiBaseUrl;

  /// False when the Android Keystore/encrypted preferences backend could not be used, so the
  /// session lives in memory only for this process.
  final bool encryptedStorageAvailable;

  /// False when the platform storage directories were unavailable and every store is in memory.
  final bool durableStorageAvailable;

  static Future<AppEnvironment> bootstrap({ApiConfig? config}) async {
    final ApiConfig resolvedConfig = config ?? ApiConfig.defaultConfig();

    final Directory? supportDirectory = await _supportDirectoryOrNull();

    final bool durableStorageAvailable = supportDirectory != null;
    final SessionStore sessionStore = durableStorageAvailable
        ? await _buildSessionStore(supportDirectory)
        : InMemorySessionStore();
    // The fail-closed factory returns the in-memory store when the encrypted backend is unusable,
    // so the concrete type is the availability signal.
    final bool encryptedStorageAvailable =
        durableStorageAvailable && sessionStore is! InMemorySessionStore;

    final CredentialStore credentialStore = durableStorageAvailable
        ? await _buildCredentialStore()
        : InMemoryCredentialStore();

    final LanguagePreferenceStore languageStore = durableStorageAvailable
        ? await _buildLanguageStore()
        : InMemoryLanguagePreferenceStore();

    final HomeShelfStore homeShelfStore = durableStorageAvailable
        ? FileHomeShelfStore(
            File(
              '${supportDirectory.path}${Platform.pathSeparator}'
              '$homeShelfCacheFileName',
            ),
          )
        : InMemoryHomeShelfStore();

    final _LateBoundToken token = _LateBoundToken();
    final HttpReelShortApiClient apiClient = HttpReelShortApiClient(
      config: resolvedConfig,
      tokenProvider: token.read,
    );
    final AppRepository repository = AppRepository(
      apiClient: apiClient,
      sessionStore: sessionStore,
      credentialStore: credentialStore,
      homeShelfStore: homeShelfStore,
      languagePreferenceStore: languageStore,
      apiBaseUrl: resolvedConfig.baseUrl,
    );
    token.bind(repository);

    return AppEnvironment(
      dataSource: repository,
      sessionStore: sessionStore,
      credentialStore: credentialStore,
      languageStore: languageStore,
      homeShelfStore: homeShelfStore,
      apiBaseUrl: resolvedConfig.baseUrl,
      encryptedStorageAvailable: encryptedStorageAvailable,
      durableStorageAvailable: durableStorageAvailable,
    );
  }

  static Future<Directory?> _supportDirectoryOrNull() async {
    try {
      return await getApplicationSupportDirectory();
    } on Object {
      // Missing platform support (or a broken path_provider channel) must not stop startup; the
      // in-memory stores keep the app usable for this session.
      return null;
    }
  }

  /// Probes the encrypted backend, then runs the fail-closed factory so a legacy plaintext session
  /// file is deleted before it could be read.
  static Future<SessionStore> _buildSessionStore(Directory directory) async {
    final SecureSessionStore candidate = SecureSessionStore(
      const FlutterSecureStorage(),
      onUnavailable: kDebugMode
          ? (Object error) =>
                debugPrint('[secure-store] session backend unavailable: $error')
          : null,
    );
    final bool available = await candidate.probe();
    return FailClosedSessionStoreFactory.create(
      available ? candidate : null,
      legacyPlaintextFile: File(
        '${directory.path}${Platform.pathSeparator}'
        '$legacyPlaintextSessionFileName',
      ),
    );
  }

  static Future<CredentialStore> _buildCredentialStore() async {
    final SecureCredentialStore store = SecureCredentialStore(
      const FlutterSecureStorage(),
      onUnavailable: kDebugMode
          ? (Object error) =>
                debugPrint('[secure-store] credential backend unavailable: $error')
          : null,
    );
    // A read of a missing key still round-trips through the encrypted backend, which is what makes
    // this a real availability probe: if the backend throws, the store degrades itself and every
    // later read reports "nothing remembered" while writes are ignored.
    await store.loadCredentials();
    return store;
  }

  static Future<LanguagePreferenceStore> _buildLanguageStore() async {
    final SharedPreferences preferences = await SharedPreferences.getInstance();
    return PreferenceLanguageStore(preferences);
  }
}
