package com.reelshort.app.network

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T,
    val requestId: String,
    val timestamp: String,
)
