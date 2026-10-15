/// The single content/store language of the platform.
///
/// The backend, the content provider and the Android client are all English
/// single-language: the `locale` query parameter is still sent, but every value
/// resolves to `en`. The Flutter rewrite keeps the same shape so a second
/// language could be added without touching call sites.
enum AppLanguage {
  english('en', 'English');

  const AppLanguage(this.locale, this.displayName);

  /// Value sent as the `locale` query parameter.
  final String locale;

  /// Human readable name shown in the account summary.
  final String displayName;

  /// Fallback used when a persisted or requested locale is unknown.
  static const AppLanguage defaultLanguage = english;

  /// Resolves a persisted locale, exactly matching the Kotlin implementation:
  /// the comparison is case sensitive and unknown values fall back to
  /// [defaultLanguage] rather than throwing.
  static AppLanguage fromLocale(String? locale) {
    for (final AppLanguage language in AppLanguage.values) {
      if (language.locale == locale) {
        return language;
      }
    }
    return defaultLanguage;
  }
}
