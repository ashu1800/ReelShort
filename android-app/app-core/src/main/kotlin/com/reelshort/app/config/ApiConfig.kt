package com.reelshort.app.config

class ApiConfig(rawBaseUrl: String) {
    val baseUrl: String = rawBaseUrl.trim().trimEnd('/')

    override fun equals(other: Any?): Boolean =
        other is ApiConfig && baseUrl == other.baseUrl

    override fun hashCode(): Int = baseUrl.hashCode()

    override fun toString(): String = "ApiConfig(baseUrl=$baseUrl)"

    companion object {
        fun default(): ApiConfig = ApiConfig("http://10.0.2.2:8080/api/app")
    }
}
