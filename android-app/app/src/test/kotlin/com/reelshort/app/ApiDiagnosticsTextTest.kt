package com.reelshort.app
import com.reelshort.app.ui.format.apiDiagnosticsText

import com.reelshort.app.data.ApiHealthStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiDiagnosticsTextTest {
    @Test
    fun unknownStatusPromptsManualCheck() {
        val text = apiDiagnosticsText(null)

        assertEquals("Unchecked", text.label)
        assertEquals("Tap refresh to verify the emulator can reach the Spring Boot API.", text.message)
        assertEquals(false, text.isUp)
    }

    @Test
    fun upStatusShowsConnectedService() {
        val text = apiDiagnosticsText(ApiHealthStatus(status = "UP", service = "reelshort-backend"))

        assertEquals("Connected", text.label)
        assertEquals("Backend reelshort-backend is responding normally.", text.message)
        assertEquals(true, text.isUp)
    }

    @Test
    fun nonUpStatusShowsBackendStatus() {
        val text = apiDiagnosticsText(ApiHealthStatus(status = "DOWN", service = null))

        assertEquals("Unavailable", text.label)
        assertEquals("Backend health is DOWN. Confirm Spring Boot is running.", text.message)
        assertEquals(false, text.isUp)
    }
}
