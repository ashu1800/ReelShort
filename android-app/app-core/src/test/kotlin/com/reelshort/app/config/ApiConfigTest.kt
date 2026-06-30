package com.reelshort.app.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiConfigTest {
    @Test
    fun defaultConfigDerivesSystemHealthUrl() {
        val config = ApiConfig.default()

        assertEquals("https://reelshort.hjj888.cc/api/app", config.baseUrl)
        assertEquals("https://reelshort.hjj888.cc/api/system/health", config.systemHealthUrl)
    }

    @Test
    fun trimsTrailingSlashBeforeDerivingSystemHealthUrl() {
        val config = ApiConfig("http://localhost:8080/api/app/")

        assertEquals("http://localhost:8080/api/app", config.baseUrl)
        assertEquals("http://localhost:8080/api/system/health", config.systemHealthUrl)
    }

    @Test
    fun appendsSystemHealthForNonStandardBaseUrl() {
        val config = ApiConfig("http://localhost:8080/custom")

        assertEquals("http://localhost:8080/custom/system/health", config.systemHealthUrl)
    }
}
