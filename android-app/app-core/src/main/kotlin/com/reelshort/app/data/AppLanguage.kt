package com.reelshort.app.data

enum class AppLanguage(val locale: String, val displayName: String) {
    ENGLISH("en", "English"),
    TRADITIONAL_CHINESE("zh-TW", "繁體中文");

    companion object {
        val DEFAULT: AppLanguage = ENGLISH

        fun fromLocale(locale: String?): AppLanguage =
            entries.firstOrNull { it.locale == locale } ?: DEFAULT
    }
}
