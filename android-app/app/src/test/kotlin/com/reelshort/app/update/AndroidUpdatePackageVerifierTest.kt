package com.reelshort.app.update

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AndroidUpdatePackageVerifierTest {
    private val apk = Files.createTempFile("shortlink-update", ".apk").toFile()
    private val current = PackageIdentity("com.reelshort.app", 1, setOf("certificate-a"))

    @Test
    fun acceptsSamePackageNewerVersionAndSameSigningCertificate() = runTest {
        val verifier = verifier(PackageIdentity("com.reelshort.app", 2, setOf("certificate-a")))

        verifier.verify(apk)
    }

    @Test
    fun rejectsDifferentPackageName() = runTest {
        val verifier = verifier(PackageIdentity("com.example.other", 2, setOf("certificate-a")))

        assertFailsWith<SecurityException> { verifier.verify(apk) }
    }

    @Test
    fun rejectsSameOrOlderVersionCode() = runTest {
        assertFailsWith<SecurityException> { verifier(current).verify(apk) }
        assertFailsWith<SecurityException> {
            verifier(PackageIdentity("com.reelshort.app", 0, setOf("certificate-a"))).verify(apk)
        }
    }

    @Test
    fun rejectsMissingOrDifferentSigningCertificate() = runTest {
        assertFailsWith<SecurityException> {
            verifier(PackageIdentity("com.reelshort.app", 2, emptySet())).verify(apk)
        }
        assertFailsWith<SecurityException> {
            verifier(PackageIdentity("com.reelshort.app", 2, setOf("certificate-b"))).verify(apk)
        }
    }

    @Test
    fun rejectsUnreadableApk() = runTest {
        val verifier = AndroidUpdatePackageVerifier(currentIdentity = { current }, archiveIdentity = { null })

        assertFailsWith<SecurityException> { verifier.verify(apk) }
    }

    private fun verifier(archive: PackageIdentity) = AndroidUpdatePackageVerifier(
        currentIdentity = { current },
        archiveIdentity = { archive },
    )
}
