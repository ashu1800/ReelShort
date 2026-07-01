package com.reelshort.app.session

import com.reelshort.app.data.AppLanguage

class InMemoryLanguagePreferenceStore(
    private var language: AppLanguage = AppLanguage.DEFAULT,
) : LanguagePreferenceStore {
    override suspend fun loadLanguage(): AppLanguage = language

    override suspend fun saveLanguage(language: AppLanguage) {
        this.language = language
    }
}
