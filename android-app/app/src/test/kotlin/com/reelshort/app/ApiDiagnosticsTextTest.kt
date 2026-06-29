package com.reelshort.app

import com.reelshort.app.data.ApiHealthStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiDiagnosticsTextTest {
    @Test
    fun unknownStatusPromptsManualCheck() {
        val text = apiDiagnosticsText(null)

        assertEquals("未检测", text.label)
        assertEquals("点击刷新，检查雷电模拟器是否能访问本机 Spring Boot。", text.message)
        assertEquals(false, text.isUp)
    }

    @Test
    fun upStatusShowsConnectedService() {
        val text = apiDiagnosticsText(ApiHealthStatus(status = "UP", service = "reelshort-backend"))

        assertEquals("已连接", text.label)
        assertEquals("后端 reelshort-backend 正常响应。", text.message)
        assertEquals(true, text.isUp)
    }

    @Test
    fun nonUpStatusShowsBackendStatus() {
        val text = apiDiagnosticsText(ApiHealthStatus(status = "DOWN", service = null))

        assertEquals("连接异常", text.label)
        assertEquals("后端健康状态为 DOWN，请确认 Spring Boot 是否启动。", text.message)
        assertEquals(false, text.isUp)
    }
}
