package com.reelshort.app

import android.content.Context
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.session.LanguagePreferenceStore

class AndroidLanguagePreferenceStore(context: Context) : LanguagePreferenceStore {
    private val preferences = context.getSharedPreferences("reelshort-language", Context.MODE_PRIVATE)

    override suspend fun loadLanguage(): AppLanguage =
        AppLanguage.fromLocale(preferences.getString(KEY_LOCALE, AppLanguage.DEFAULT.locale))

    override suspend fun saveLanguage(language: AppLanguage) {
        preferences.edit().putString(KEY_LOCALE, language.locale).apply()
    }

    private companion object {
        const val KEY_LOCALE = "locale"
    }
}
