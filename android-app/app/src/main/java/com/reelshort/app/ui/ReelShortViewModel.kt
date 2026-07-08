package com.reelshort.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reelshort.app.config.ApiConfig
import com.reelshort.app.data.AppRepository
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.SmsVerificationPurpose
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.network.OkHttpReelShortApiClient
import com.reelshort.app.session.FileHomeShelfStore
import com.reelshort.app.session.FileSessionStore
import com.reelshort.app.state.AppStateController
import com.reelshort.app.state.AppUiState
import com.reelshort.app.BuildConfig
import com.reelshort.app.AndroidSessionStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * App 的 ViewModel。在 Android 层用 [viewModelScope] 承载协程，旋屏时自动保留；
 * 业务状态机仍由 app-core 的 [AppStateController] 驱动（保持核心逻辑纯 JVM 可测）。
 * 替代此前与 controller 重复的 AppUiActions 透传代理。
 */
class ReelShortViewModel(
    private val controller: AppStateController,
) : ViewModel() {

    val state: StateFlow<AppUiState> = controller.state

    fun bootstrap() {
        viewModelScope.launch { controller.restoreSession() }
    }

    fun selectScreen(screen: com.reelshort.app.state.AppScreen) {
        viewModelScope.launch {
            when (screen) {
                com.reelshort.app.state.AppScreen.HOME -> controller.openHome()
                com.reelshort.app.state.AppScreen.SEARCH -> controller.showSearch()
                com.reelshort.app.state.AppScreen.ACCOUNT -> controller.openAccount()
                else -> Unit
            }
        }
    }

    fun login(countryCode: String, phoneNumber: String, password: String, rememberPassword: Boolean) {
        viewModelScope.launch { controller.login(countryCode, phoneNumber, password, rememberPassword) }
    }

    fun register(countryCode: String, phoneNumber: String, password: String, verificationCode: String) {
        viewModelScope.launch { controller.register(countryCode, phoneNumber, password, verificationCode) }
    }

    fun sendAuthSms(countryCode: String, phoneNumber: String) {
        viewModelScope.launch { controller.sendAuthSms(countryCode, phoneNumber) }
    }

    fun sendWalletVerification(purpose: SmsVerificationPurpose) {
        viewModelScope.launch { controller.sendWalletVerification(purpose) }
    }

    fun sendPasswordChangeVerification() {
        viewModelScope.launch { controller.sendPasswordChangeVerification() }
    }

    fun bindWallet(walletAddress: String, verificationCode: String) {
        viewModelScope.launch { controller.bindWallet(walletAddress, verificationCode) }
    }

    fun unbindWallet(verificationCode: String) {
        viewModelScope.launch { controller.unbindWallet(verificationCode) }
    }

    fun submitBankCard(holderName: String, cardNumber: String) {
        viewModelScope.launch { controller.submitBankCard(holderName, cardNumber) }
    }

    fun submitWithdrawal(pointAmount: Int) {
        viewModelScope.launch { controller.submitWithdrawal(pointAmount) }
    }

    fun transferPoints(recipientAccount: String, pointAmount: Int) {
        viewModelScope.launch { controller.transferPoints(recipientAccount, pointAmount) }
    }

    fun changePassword(oldPassword: String, newPassword: String, verificationCode: String) {
        viewModelScope.launch { controller.changePassword(oldPassword, newPassword, verificationCode) }
    }

    fun search(query: String) {
        viewModelScope.launch { controller.search(query) }
    }

    fun openBook(book: BookSummary) {
        viewModelScope.launch { controller.openBook(book) }
    }

    fun openWatchRecord(record: WatchRecord) {
        viewModelScope.launch { controller.openWatchRecord(record) }
    }

    fun openPlayer(episode: EpisodeSummary) {
        viewModelScope.launch { controller.openPlayer(episode) }
    }

    fun updatePlaybackPosition(positionSeconds: Int, durationSeconds: Int) {
        controller.updatePlaybackPosition(positionSeconds, durationSeconds)
    }

    fun reportProgressSilently(positionSeconds: Int, durationSeconds: Int) {
        viewModelScope.launch { controller.reportProgressSilently(positionSeconds, durationSeconds) }
    }

    fun toggleLike() {
        viewModelScope.launch { controller.toggleLike() }
    }

    fun toggleFavorite() {
        viewModelScope.launch { controller.toggleFavorite() }
    }

    fun submitComment(content: String) {
        viewModelScope.launch { controller.submitComment(content) }
    }

    fun openFavorites() {
        viewModelScope.launch { controller.openFavorites() }
    }

    fun refreshHome() {
        viewModelScope.launch { controller.refreshHomeWithPull() }
    }

    fun backFromPlayer() = controller.backToPlaybackSource()

    fun backFromFavorites() = controller.backToAccount()

    fun checkApiHealth() {
        viewModelScope.launch { controller.checkApiHealth() }
    }

    fun logout() {
        viewModelScope.launch { controller.logout() }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { controller.setLanguage(language) }
    }

    fun showAuthPrompt() = controller.showAuthPrompt()

    fun dismissAuthPrompt() = controller.dismissAuthPrompt()

    fun clearError() = controller.clearError()

    override fun onCleared() {
        super.onCleared()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val context = application.applicationContext
                    val filesDir = context.filesDir
                    val sessionStore = AndroidSessionStore.create(
                        context = context,
                        fallback = FileSessionStore(File(filesDir, "reelshort-session.json")),
                    )
                    val homeShelfStore = FileHomeShelfStore(File(filesDir, "home-shelf-cache.json"))
                    val credentialStore = com.reelshort.app.AndroidCredentialStore.create(context)
                    val languagePreferenceStore = com.reelshort.app.AndroidLanguagePreferenceStore(context)
                    val apiConfig = ApiConfig(BuildConfig.REELSHORT_API_BASE_URL)
                    lateinit var repository: AppRepository
                    val apiClient = OkHttpReelShortApiClient(
                        config = apiConfig,
                        tokenProvider = { repository.currentToken },
                    )
                    repository = AppRepository(
                        apiClient = apiClient,
                        sessionStore = sessionStore,
                        credentialStore = credentialStore,
                        homeShelfStore = homeShelfStore,
                        languagePreferenceStore = languagePreferenceStore,
                        apiBaseUrl = apiConfig.baseUrl,
                    )
                    val controller = AppStateController(repository)
                    return ReelShortViewModel(controller) as T
                }
            }
    }
}
