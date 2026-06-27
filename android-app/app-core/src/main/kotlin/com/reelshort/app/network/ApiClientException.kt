package com.reelshort.app.network

class ApiClientException(
    val statusCode: Int,
    val code: Int?,
    override val message: String,
) : RuntimeException(message)
