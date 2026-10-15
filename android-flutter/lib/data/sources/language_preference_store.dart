/// Language preference persistence boundary.
///
/// Port of `com.reelshort.app.session.LanguagePreferenceStore` and
/// `InMemoryLanguagePreferenceStore`.
library;

import '../models/app_language.dart';

abstract interface class LanguagePreferenceStore {
  Future<AppLanguage> loadLanguage();

  Future<void> saveLanguage(AppLanguage language);
}

class InMemoryLanguagePreferenceStore implements LanguagePreferenceStore {
  InMemoryLanguagePreferenceStore([
    this._language = AppLanguage.defaultLanguage,
  ]);

  AppLanguage _language;

  @override
  Future<AppLanguage> loadLanguage() async => _language;

  @override
  Future<void> saveLanguage(AppLanguage language) async {
    _language = language;
  }
}
