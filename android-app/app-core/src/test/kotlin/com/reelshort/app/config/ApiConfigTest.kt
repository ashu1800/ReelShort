package com.reelshort.app.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiConfigTest {
    @Test
    fun defaultBaseUrlTargetsSpringBootAppApi() {
        assertEquals("http://10.0.2.2:8080/api/app", ApiConfig.default().baseUrl)
    }

    @Test
    fun constructorRemovesTrailingSlashesFromBaseUrl() {
        assertEquals(
            "http://localhost:8080/api/app",
            ApiConfig("http://localhost:8080/api/app///").baseUrl,
        )
    }

    @Test
    fun normalizedBaseUrlDefinesEquality() {
        assertEquals(
            ApiConfig("http://localhost:8080/api/app"),
            ApiConfig("http://localhost:8080/api/app/"),
        )
    }
}
