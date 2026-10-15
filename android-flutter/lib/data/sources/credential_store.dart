/// Remembered-credentials persistence boundary.
///
/// Port of `com.reelshort.app.session.CredentialStore` and
/// `InMemoryCredentialStore`.
library;

import '../models/app_models.dart';

abstract interface class CredentialStore {
  Future<SavedCredentials?> loadCredentials();

  Future<void> saveCredentials(SavedCredentials credentials);

  Future<void> clearCredentials();
}

class InMemoryCredentialStore implements CredentialStore {
  SavedCredentials? _credentials;

  @override
  Future<SavedCredentials?> loadCredentials() async => _credentials;

  @override
  Future<void> saveCredentials(SavedCredentials credentials) async {
    _credentials = credentials;
  }

  @override
  Future<void> clearCredentials() async {
    _credentials = null;
  }
}
