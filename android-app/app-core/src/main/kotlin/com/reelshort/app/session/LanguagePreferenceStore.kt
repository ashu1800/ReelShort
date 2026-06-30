package com.reelshort.app.session

import com.reelshort.app.data.AppLanguage

interface LanguagePreferenceStore {
    suspend fun loadLanguage(): AppLanguage

    suspend fun saveLanguage(language: AppLanguage)
}
