package com.reelshort.app

import com.reelshort.app.ui.format.accountCardsUseContentDrivenHeight
import com.reelshort.app.ui.format.accountSheetStartsFullyExpanded
import com.reelshort.app.ui.format.accountSheetUsesScrollableContent
import com.reelshort.app.ui.format.posterOverlayTitleMaxLines
import com.reelshort.app.ui.format.responsivePosterMinimumWidthDp
import com.reelshort.app.ui.format.posterCardContentDescription
import com.reelshort.app.ui.format.posterImageIsDecorativeInsideCard
import com.reelshort.app.data.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutContractTest {
    @Test
    fun accountFormsAreFullyExpandedAndScrollable() {
        assertTrue(accountSheetStartsFullyExpanded())
        assertTrue(accountSheetUsesScrollableContent())
    }

    @Test
    fun accountCardsAllowContentToGrowWithSystemFont() {
        assertTrue(accountCardsUseContentDrivenHeight())
    }

    @Test
    fun posterOverlayBecomesCompactForLargeFonts() {
        assertEquals(2, posterOverlayTitleMaxLines(fontScale = 1f))
        assertEquals(1, posterOverlayTitleMaxLines(fontScale = 1.5f))
    }

    @Test
    fun posterGridUsesAdaptiveMinimumWidth() {
        assertEquals(136, responsivePosterMinimumWidthDp())
    }

    @Test
    fun clickablePosterHasOneCombinedAccessibilityDescription() {
        assertEquals(
            "Alpha, 12 eps",
            posterCardContentDescription("Alpha", 12, AppLanguage.DEFAULT),
        )
        assertTrue(posterImageIsDecorativeInsideCard())
    }
}
