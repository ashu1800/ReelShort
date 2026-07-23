package com.reelshort.app.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UpdateCoordinatorTest {
    private val release = ReleaseInfo(
        tagName = "v0.2.0",
        version = SemanticVersion(0, 2, 0),
        title = "ShortLink v0.2.0",
        body = "Notes",
        publishedAt = "2026-07-13T12:00:00Z",
        apkAsset = ReleaseAsset("ShortLink-v0.2.0.apk", "https://github.com/app.apk", "application/vnd.android.package-archive", 3),
        sha256Asset = ReleaseAsset("ShortLink-v0.2.0.apk.sha256", "https://github.com/app.sha256", "text/plain", 64),
    )

    @Test
    fun automaticFailureReturnsToIdleButManualFailureIsVisible() = runTest {
        val client = FakeReleaseClient(error = ReleaseUpdateException.Http(403))
        val coordinator = coordinator(client = client)

        coordinator.checkForUpdate(manual = false)
        assertIs<UpdateState.Idle>(coordinator.state.value)

        coordinator.checkForUpdate(manual = true)
        val failed = assertIs<UpdateState.Failed>(coordinator.state.value)
        assertEquals(UpdateFailureReason.NETWORK, failed.reason)
        assertEquals(true, failed.manual)
    }

    @Test
    fun reportsUpToDateOnlyForManualCheck() = runTest {
        val coordinator = coordinator(client = FakeReleaseClient(release.copy(version = SemanticVersion(0, 1, 0))))

        coordinator.checkForUpdate(manual = true)

        assertIs<UpdateState.UpToDate>(coordinator.state.value)
    }

    @Test
    fun exposesAvailableReleaseWhenRemoteVersionIsNewer() = runTest {
        val coordinator = coordinator(client = FakeReleaseClient(release))

        coordinator.checkForUpdate(manual = false)

        assertEquals(release, assertIs<UpdateState.Available>(coordinator.state.value).release)
    }

    @Test
    fun downloadsChecksIntegrityAndPreparesInstall() = runTest {
        val cache = Files.createTempDirectory("update-coordinator").toFile()
        val downloader = FakeDownloader("apk".toByteArray())
        val coordinator = coordinator(downloader = downloader)
        coordinator.checkForUpdate(manual = false)

        coordinator.downloadAvailable(cache)

        val ready = assertIs<UpdateState.ReadyToInstall>(coordinator.state.value)
        assertEquals("ShortLink-v0.2.0.apk", ready.apkFile.name)
        assertEquals(InstallRequest.RequestPermission, coordinator.prepareInstall(canRequestPackageInstalls = false))
        assertIs<UpdateState.PermissionRequired>(coordinator.state.value)
        assertEquals(InstallRequest.Install(ready.apkFile), coordinator.onInstallPermissionResult(granted = true))
        coordinator.installationFailed()
        assertEquals(
            UpdateFailureReason.INSTALL,
            assertIs<UpdateState.Failed>(coordinator.state.value).reason,
        )
    }

    @Test
    fun checksumMismatchDeletesFilesAndAllowsRetry() = runTest {
        val cache = Files.createTempDirectory("update-coordinator").toFile()
        val downloader = FakeDownloader("apk".toByteArray(), checksum = "0".repeat(64))
        val coordinator = coordinator(downloader = downloader)
        coordinator.checkForUpdate(manual = false)

        coordinator.downloadAvailable(cache)

        val failed = assertIs<UpdateState.Failed>(coordinator.state.value)
        assertEquals(UpdateFailureReason.VERIFICATION, failed.reason)
        assertEquals(release, failed.release)
        assertEquals(emptyList(), cache.walkTopDown().filter(File::isFile).toList())
    }

    @Test
    fun downloadedSizeMismatchIsTreatedAsVerificationFailure() = runTest {
        val cache = Files.createTempDirectory("update-coordinator").toFile()
        val coordinator = coordinator(downloader = FakeDownloader("four".toByteArray()))
        coordinator.checkForUpdate(manual = false)

        coordinator.downloadAvailable(cache)

        assertEquals(
            UpdateFailureReason.VERIFICATION,
            assertIs<UpdateState.Failed>(coordinator.state.value).reason,
        )
    }

    @Test
    fun cancellationReturnsToAvailableAndDeletesPartialFile() = runTest {
        val cache = Files.createTempDirectory("update-coordinator").toFile()
        val downloader = FakeDownloader("apk".toByteArray(), cancel = true)
        val coordinator = coordinator(downloader = downloader)
        coordinator.checkForUpdate(manual = false)

        try {
            coordinator.downloadAvailable(cache)
        } catch (_: CancellationException) {
            coordinator.downloadCancelled()
        }

        assertIs<UpdateState.Available>(coordinator.state.value)
        assertEquals(emptyList(), cache.walkTopDown().filter(File::isFile).toList())
    }

    private fun coordinator(
        client: ReleaseUpdateClient = FakeReleaseClient(release),
        downloader: ReleaseDownloader = FakeDownloader("apk".toByteArray()),
    ) = UpdateCoordinator(
        releaseClient = client,
        downloader = downloader,
        currentVersion = SemanticVersion(0, 1, 0),
        availableBytes = { Long.MAX_VALUE },
    )

    private class FakeReleaseClient(
        private val release: ReleaseInfo? = null,
        private val error: Throwable? = null,
    ) : ReleaseUpdateClient {
        override suspend fun fetchLatestStable(): ReleaseInfo? {
            error?.let { throw it }
            return release
        }
    }

    private class FakeDownloader(
        private val content: ByteArray,
        private val checksum: String = sha256(content),
        private val cancel: Boolean = false,
    ) : ReleaseDownloader {
        override suspend fun download(url: String, destination: File, maxBytes: Long, onProgress: (DownloadProgress) -> Unit) {
            destination.parentFile?.mkdirs()
            destination.writeBytes(content)
            onProgress(DownloadProgress(content.size.toLong(), content.size.toLong()))
            if (cancel) {
                destination.delete()
                throw CancellationException("cancelled")
            }
        }

        override suspend fun fetchSha256(url: String): String = checksum
    }
}
