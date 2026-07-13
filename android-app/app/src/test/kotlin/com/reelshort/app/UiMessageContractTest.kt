package com.reelshort.app

import com.reelshort.app.state.UiMessageType
import com.reelshort.app.ui.format.MessageVisualTone
import com.reelshort.app.ui.format.messageVisualTone
import com.reelshort.app.ui.format.topMessageUsesLiveRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiMessageContractTest {
    @Test
    fun successAndErrorMessagesUseDifferentVisualTones() {
        assertEquals(MessageVisualTone.SUCCESS, messageVisualTone(UiMessageType.SUCCESS))
        assertEquals(MessageVisualTone.ERROR, messageVisualTone(UiMessageType.ERROR))
        assertEquals(MessageVisualTone.INFO, messageVisualTone(UiMessageType.INFO))
    }

    @Test
    fun topMessageIsAnnouncedByAccessibilityServices() {
        assertTrue(topMessageUsesLiveRegion())
    }
}
