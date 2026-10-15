/// Language preference persisted in plain preferences.
///
/// The language choice is not a secret, so it deliberately does **not** go through the encrypted
/// store — this matches the Android implementation, which used a plain `SharedPreferences` file.
library;

import 'package:shared_preferences/shared_preferences.dart';

import '../data/models/app_language.dart';
import '../data/sources/language_preference_store.dart';

class PreferenceLanguageStore implements LanguagePreferenceStore {
  PreferenceLanguageStore(this._preferences);

  /// Preference key, identical to the Android key.
  static const String localeKey = 'locale';

  final SharedPreferences _preferences;

  @override
  Future<AppLanguage> loadLanguage() async =>
      AppLanguage.fromLocale(_preferences.getString(localeKey));

  @override
  Future<void> saveLanguage(AppLanguage language) async {
    await _preferences.setString(localeKey, language.locale);
  }
}
