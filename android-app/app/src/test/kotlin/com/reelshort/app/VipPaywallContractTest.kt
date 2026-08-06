package com.reelshort.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class VipPaywallContractTest {
    @Test
    fun vipPaywallIsRenderedOnceAtTheApplicationRoot() {
        val activitySource = readSourceFile(
            "app/src/main/java/com/reelshort/app/MainActivity.kt",
            "android-app/app/src/main/java/com/reelshort/app/MainActivity.kt",
        )
        val componentSource = readSourceFile(
            "app/src/main/java/com/reelshort/app/ui/components/VipPaymentBottomSheet.kt",
            "android-app/app/src/main/java/com/reelshort/app/ui/components/VipPaymentBottomSheet.kt",
        )
        val accountSource = readSourceFile(
            "app/src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt",
            "android-app/app/src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt",
        )

        assertTrue(activitySource.contains("VipPaymentBottomSheet"))
        assertTrue(componentSource.contains("pendingVipEpisode"))
        assertTrue(componentSource.contains("vipPaywallLoading"))
        assertTrue(componentSource.contains("latestVipOrder?.status == \"CONFIRMED\""))
        assertFalse(accountSource.contains("VipBottomSheet("))
    }

    @Test
    fun detailAndPlayerUseTheSharedVipEpisodeThreshold() {
        val detailSource = readSourceFile(
            "app/src/main/java/com/reelshort/app/ui/screens/detail/DetailScreen.kt",
            "android-app/app/src/main/java/com/reelshort/app/ui/screens/detail/DetailScreen.kt",
        )
        val playerSource = readSourceFile(
            "app/src/main/java/com/reelshort/app/ui/screens/player/PlayerScreen.kt",
            "android-app/app/src/main/java/com/reelshort/app/ui/screens/player/PlayerScreen.kt",
        )

        assertTrue(detailSource.contains("DEFAULT_VIP_FREE_EPISODES"))
        assertTrue(playerSource.contains("DEFAULT_VIP_FREE_EPISODES"))
    }

    private fun readSourceFile(vararg candidates: String): String {
        val roots = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        roots.forEach { root ->
            candidates.forEach { candidate ->
                val path = root.resolve(candidate)
                if (Files.exists(path)) return String(Files.readAllBytes(path))
            }
        }
        error("Could not find source file: ${candidates.joinToString()}")
    }
}
