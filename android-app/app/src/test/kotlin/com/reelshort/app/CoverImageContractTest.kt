package com.reelshort.app
import com.reelshort.app.ui.format.coverUrlOrNull

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverImageContractTest {
    @Test
    fun blankCoverUrlIsNotLoaded() {
        assertNull(null.coverUrlOrNull())
        assertNull("".coverUrlOrNull())
        assertNull("   ".coverUrlOrNull())
    }

    @Test
    fun nonBlankCoverUrlIsTrimmedBeforeLoading() {
        assertEquals(
            "https://example.com/cover.jpg",
            "  https://example.com/cover.jpg  ".coverUrlOrNull(),
        )
    }
}
