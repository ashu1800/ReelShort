package com.reelshort.app
import com.reelshort.app.ui.format.apiDiagnosticsText

import com.reelshort.app.data.ApiHealthStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiDiagnosticsTextTest {
    @Test
    fun unknownStatusPromptsManualCheck() {
        val text = apiDiagnosticsText(null)

        assertEquals("未檢測", text.label)
        assertEquals("點擊刷新，檢查模擬器是否能訪問 Spring Boot。", text.message)
        assertEquals(false, text.isUp)
    }

    @Test
    fun upStatusShowsConnectedService() {
        val text = apiDiagnosticsText(ApiHealthStatus(status = "UP", service = "reelshort-backend"))

        assertEquals("已連線", text.label)
        assertEquals("後端 reelshort-backend 正常響應。", text.message)
        assertEquals(true, text.isUp)
    }

    @Test
    fun nonUpStatusShowsBackendStatus() {
        val text = apiDiagnosticsText(ApiHealthStatus(status = "DOWN", service = null))

        assertEquals("連線異常", text.label)
        assertEquals("後端健康狀態為 DOWN，請確認 Spring Boot 是否啟動。", text.message)
        assertEquals(false, text.isUp)
    }
}
