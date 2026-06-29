package com.reelshort.app.ui.format

import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.BookSummary

internal data class ContentEmptyState(
    val title: String,
    val message: String,
    val actionLabel: String? = null,
)

internal data class ApiDiagnosticsText(
    val label: String,
    val message: String,
    val isUp: Boolean,
)

internal fun apiDiagnosticsText(status: ApiHealthStatus?): ApiDiagnosticsText {
    if (status == null) {
        return ApiDiagnosticsText(
            label = "未检测",
            message = "点击刷新，检查雷电模拟器是否能访问本机 Spring Boot。",
            isUp = false,
        )
    }
    return if (status.status.equals("UP", ignoreCase = true)) {
        val service = status.service?.takeIf { it.isNotBlank() } ?: "Spring Boot"
        ApiDiagnosticsText(
            label = "已连接",
            message = "后端 $service 正常响应。",
            isUp = true,
        )
    } else {
        ApiDiagnosticsText(
            label = "连接异常",
            message = "后端健康状态为 ${status.status}，请确认 Spring Boot 是否启动。",
            isUp = false,
        )
    }
}

internal fun homeEmptyState(): ContentEmptyState =
    ContentEmptyState(
        title = "今日暂无推荐",
        message = "内容源暂时没有返回推荐短剧，可以先搜索片名或关键词。",
        actionLabel = "去搜索",
    )

internal fun searchEmptyState(query: String, resultCount: Int): ContentEmptyState? {
    if (resultCount > 0) {
        return null
    }
    val normalizedQuery = query.trim()
    return if (normalizedQuery.isEmpty()) {
        ContentEmptyState(
            title = "发现短剧",
            message = "输入剧名、角色或关键词，快速找到想看的短剧。",
        )
    } else {
        ContentEmptyState(
            title = "没有找到相关短剧",
            message = "没有匹配“$normalizedQuery”的内容，换个关键词再试。",
            actionLabel = "重新搜索",
        )
    }
}

internal fun detailEmptyState(book: BookSummary?, episodeCount: Int): ContentEmptyState? {
    if (book != null && episodeCount > 0) {
        return null
    }
    return if (book == null) {
        ContentEmptyState(
            title = "先选择一部短剧",
            message = "从首页推荐或搜索结果进入详情后，这里会展示分集列表。",
            actionLabel = "返回首页",
        )
    } else {
        ContentEmptyState(
            title = "分集暂不可用",
            message = "“${book.title}”暂时没有可播放分集，可以稍后刷新或选择其他短剧。",
            actionLabel = "换一部",
        )
    }
}
