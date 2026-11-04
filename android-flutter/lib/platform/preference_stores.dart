/// Plain-preferences stores: the language choice and the recent search list.
///
/// Neither value is a secret, so they deliberately do **not** go through the
/// encrypted store — this matches the Android implementation, which used a
/// plain `SharedPreferences` file.
library;

import 'package:shared_preferences/shared_preferences.dart';

import '../data/models/app_language.dart';
import '../data/sources/language_preference_store.dart';
import '../data/sources/search_history_store.dart';

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

/// Recent search queries persisted in plain preferences.
class PreferenceSearchHistoryStore implements SearchHistoryStore {
  PreferenceSearchHistoryStore(this._preferences);

  /// Preference key holding the newest-first query list.
  static const String historyKey = 'search_history';

  final SharedPreferences _preferences;

  @override
  Future<List<String>> loadHistory() async => List<String>.unmodifiable(
    _preferences.getStringList(historyKey) ?? const <String>[],
  );

  @override
  Future<void> saveHistory(List<String> queries) async {
    await _preferences.setStringList(historyKey, queries);
  }
}
