package com.reelshort.app.ui.format

import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AppLanguage
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

internal fun apiDiagnosticsText(
    status: ApiHealthStatus?,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
): ApiDiagnosticsText {
    val copy = strings(language)
    if (status == null) {
        return ApiDiagnosticsText(
            label = copy.diagnosticsUnknownLabel,
            message = copy.diagnosticsUnknownMessage,
            isUp = false,
        )
    }
    return if (status.status.equals("UP", ignoreCase = true)) {
        val service = status.service?.takeIf { it.isNotBlank() } ?: "Spring Boot"
        ApiDiagnosticsText(
            label = copy.diagnosticsUpLabel,
            message = "${copy.diagnosticsUpMessagePrefix}$service ${if (language == AppLanguage.ENGLISH) "is responding normally." else "正常響應。"}",
            isUp = true,
        )
    } else {
        ApiDiagnosticsText(
            label = copy.diagnosticsDownLabel,
            message = "${copy.diagnosticsDownMessagePrefix}${status.status}${if (language == AppLanguage.ENGLISH) ". Confirm Spring Boot is running." else "，請確認 Spring Boot 是否啟動。"}",
            isUp = false,
        )
    }
}

internal fun homeEmptyState(language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE): ContentEmptyState =
    ContentEmptyState(
        title = strings(language).homeEmptyTitle,
        message = strings(language).homeEmptyMessage,
        actionLabel = strings(language).homeEmptyAction,
    )

internal fun favoritesEmptyState(language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE): ContentEmptyState =
    ContentEmptyState(
        title = strings(language).favoritesEmptyTitle,
        message = strings(language).favoritesEmptyMessage,
    )

internal fun searchEmptyState(
    query: String,
    resultCount: Int,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
): ContentEmptyState? {
    val copy = strings(language)
    if (resultCount > 0) {
        return null
    }
    val normalizedQuery = query.trim()
    return if (normalizedQuery.isEmpty()) {
        ContentEmptyState(
            title = copy.searchDefaultEmptyTitle,
            message = copy.searchDefaultEmptyMessage,
        )
    } else {
        ContentEmptyState(
            title = copy.searchNoResultsTitle,
            message = "${copy.searchNoResultsMessagePrefix}$normalizedQuery${copy.searchNoResultsMessageSuffix}",
            actionLabel = copy.searchRetryAction,
        )
    }
}

internal fun detailEmptyState(
    book: BookSummary?,
    episodeCount: Int,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
): ContentEmptyState? {
    val copy = strings(language)
    if (book != null && episodeCount > 0) {
        return null
    }
    return if (book == null) {
        ContentEmptyState(
            title = copy.detailEmptyTitle,
            message = copy.detailEmptyMessage,
            actionLabel = copy.detailEmptyAction,
        )
    } else {
        ContentEmptyState(
            title = copy.detailUnavailableTitle,
            message = "${copy.detailUnavailableMessagePrefix}${book.title}${copy.detailUnavailableMessageSuffix}",
            actionLabel = copy.detailUnavailableAction,
        )
    }
}
