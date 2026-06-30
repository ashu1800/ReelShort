package com.reelshort.app.config

class ApiConfig(rawBaseUrl: String) {
    val baseUrl: String = rawBaseUrl.trim().trimEnd('/')
    val systemHealthUrl: String = if (baseUrl.endsWith("/api/app")) {
        baseUrl.removeSuffix("/api/app") + "/api/system/health"
    } else {
        "$baseUrl/system/health"
    }

    override fun equals(other: Any?): Boolean =
        other is ApiConfig && baseUrl == other.baseUrl

    override fun hashCode(): Int = baseUrl.hashCode()

    override fun toString(): String = "ApiConfig(baseUrl=$baseUrl)"

    companion object {
        fun default(): ApiConfig = ApiConfig("https://reelshort.hjj888.cc/api/app")
    }
}
