package com.reelshort.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reelshort.app.config.ApiConfig
import com.reelshort.app.data.AppRepository
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.network.OkHttpReelShortApiClient
import com.reelshort.app.session.FileHomeShelfStore
import com.reelshort.app.state.AppStateController
import com.reelshort.app.state.AppUiState
import com.reelshort.app.BuildConfig
import com.reelshort.app.AndroidSessionStore
import com.reelshort.app.update.AndroidUpdateInstaller
import com.reelshort.app.update.AndroidUpdatePackageVerifier
import com.reelshort.app.update.InstallRequest
import com.reelshort.app.update.OkHttpReleaseDownloader
import com.reelshort.app.update.SemanticVersion
import com.reelshort.app.update.ShortLinkUpdateClient
import com.reelshort.app.update.UpdateCoordinator
import com.reelshort.app.update.UpdateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * App 的 ViewModel。在 Android 层用 [viewModelScope] 承载协程，旋屏时自动保留；
 * 业务状态机仍由 app-core 的 [AppStateController] 驱动（保持核心逻辑纯 JVM 可测）。
 * 替代此前与 controller 重复的 AppUiActions 透传代理。
 */
class ReelShortViewModel(
    private val controller: AppStateController,
    private val updateCoordinator: UpdateCoordinator,
    private val updateInstaller: AndroidUpdateInstaller,
    private val updateCacheDir: File,
) : ViewModel() {

    val state: StateFlow<AppUiState> = controller.state
    val updateState: StateFlow<UpdateState> = updateCoordinator.state
    private var startupUpdateCheckStarted = false
    private var downloadJob: Job? = null

    fun bootstrap() {
        viewModelScope.launch { controller.restoreSession() }
        if (!startupUpdateCheckStarted) {
            startupUpdateCheckStarted = true
            viewModelScope.launch { updateCoordinator.checkForUpdate(manual = false) }
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch { updateCoordinator.checkForUpdate(manual = true) }
    }

    fun downloadUpdate() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            try {
                updateCoordinator.downloadAvailable(updateCacheDir)
            } catch (_: CancellationException) {
                updateCoordinator.downloadCancelled()
            }
        }
    }

    fun cancelUpdateDownload() {
        downloadJob?.cancel()
        updateCoordinator.downloadCancelled()
    }

    fun dismissUpdate() = updateCoordinator.dismiss()

    fun installUpdate() {
        handleInstallRequest(updateCoordinator.prepareInstall(updateInstaller.canRequestPackageInstalls()))
    }

    fun onInstallPermissionResult() {
        handleInstallRequest(
            updateCoordinator.onInstallPermissionResult(updateInstaller.canRequestPackageInstalls()),
        )
    }

    fun unknownSourcesSettingsIntent() = updateInstaller.unknownSourcesSettingsIntent()

    private fun handleInstallRequest(request: InstallRequest?) {
        if (request is InstallRequest.Install) {
            runCatching { updateInstaller.install(request.apkFile) }
                .onFailure { updateCoordinator.installationFailed() }
        }
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

    fun login(username: String, password: String, rememberPassword: Boolean) {
        viewModelScope.launch { controller.login(username, password, rememberPassword) }
    }

    fun register(username: String, password: String, captchaId: String, captchaAnswer: String) {
        viewModelScope.launch { controller.register(username, password, captchaId, captchaAnswer) }
    }

    fun fetchCaptcha() {
        viewModelScope.launch { controller.fetchCaptcha() }
    }

    fun bindWallet(network: String, walletAddress: String, password: String) {
        viewModelScope.launch { controller.bindWallet(network, walletAddress, password) }
    }

    fun unbindWallet(password: String) {
        viewModelScope.launch { controller.unbindWallet(password) }
    }

    fun createVipOrder() {
        viewModelScope.launch { controller.createVipOrder() }
    }

    fun refreshLatestVipOrder() {
        viewModelScope.launch { controller.refreshLatestVipOrder() }
    }

    fun refreshAccount() {
        viewModelScope.launch { controller.loadAccountSnapshot() }
    }

    fun submitBankCard(holderName: String, cardNumber: String, expiryMonth: String, expiryYear: String, cvv: String) {
        viewModelScope.launch { controller.submitBankCard(holderName, cardNumber, expiryMonth, expiryYear, cvv) }
    }

    fun submitWithdrawal(pointAmount: Int) {
        viewModelScope.launch { controller.submitWithdrawal(pointAmount) }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch { controller.changePassword(oldPassword, newPassword) }
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

    fun showAuthPrompt() = controller.showAuthPrompt()

    fun showRegisterAuthMode() = controller.showRegisterAuthMode()

    fun showLoginAuthMode() = controller.showLoginAuthMode()

    fun dismissAuthPrompt() = controller.dismissAuthPrompt()

    fun clearError() = controller.clearError()

    override fun onCleared() {
        downloadJob?.cancel()
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
                        legacyPlaintextFile = File(filesDir, "reelshort-session.json"),
                    )
                    val homeShelfStore = FileHomeShelfStore(File(filesDir, "home-shelf-cache.json"))
                    val credentialStore = com.reelshort.app.AndroidCredentialStore.create(context)
                    val languagePreferenceStore = com.reelshort.app.AndroidLanguagePreferenceStore(context)
                    val apiConfig = ApiConfig(BuildConfig.REELSHORT_API_BASE_URL)
                    val updateHttpClient = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .callTimeout(15, TimeUnit.MINUTES)
                        .build()
                    val currentVersion = SemanticVersion.parse(BuildConfig.VERSION_NAME)
                        ?: error("VERSION_NAME must use X.Y.Z")
                    val updateCoordinator = UpdateCoordinator(
                        releaseClient = ShortLinkUpdateClient(
                            httpClient = updateHttpClient,
                            userAgent = "ShortLink-Android/${BuildConfig.VERSION_NAME}",
                        ),
                        downloader = OkHttpReleaseDownloader(updateHttpClient),
                        packageVerifier = AndroidUpdatePackageVerifier.create(context),
                        currentVersion = currentVersion,
                    )
                    val updateInstaller = AndroidUpdateInstaller(context)
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
                    return ReelShortViewModel(
                        controller = controller,
                        updateCoordinator = updateCoordinator,
                        updateInstaller = updateInstaller,
                        updateCacheDir = context.cacheDir,
                    ) as T
                }
            }
    }
}
