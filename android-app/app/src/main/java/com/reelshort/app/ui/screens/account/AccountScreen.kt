package com.reelshort.app.ui.screens.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.ui.components.AvatarGradient
import com.reelshort.app.ui.components.GoldOutlinedButton
import com.reelshort.app.ui.components.MetaPill
import com.reelshort.app.ui.components.OrderRow
import com.reelshort.app.ui.components.PosterBlock
import com.reelshort.app.ui.components.PointRecordRow
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.components.verticalGradient
import com.reelshort.app.ui.format.AccountDetailSheet
import com.reelshort.app.ui.format.accountDetailSheetTitle
import com.reelshort.app.ui.format.accountContinueWatchingLimit
import com.reelshort.app.ui.format.accountPrimaryActionSheet
import com.reelshort.app.ui.format.apiDiagnosticsText
import com.reelshort.app.ui.format.guestAccountEntryLabels
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.theme.AccountHeroScrim
import com.reelshort.app.ui.theme.DangerText
import com.reelshort.app.ui.theme.Divider
import com.reelshort.app.ui.theme.GoldStroke
import com.reelshort.app.ui.theme.GoldSurfaceSoft
import com.reelshort.app.ui.theme.GoldSurfaceStrong
import com.reelshort.app.ui.theme.OnPrimaryDark
import com.reelshort.app.ui.theme.Panel
import com.reelshort.app.ui.theme.PanelSoft
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary
import com.reelshort.app.ui.theme.TranslucentWhiteSurface
import com.reelshort.app.ui.theme.WhiteEdge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountScreen(
    records: List<WatchRecord>,
    continueWatchingBooks: Map<String, BookSummary>,
    isLoggedIn: Boolean,
    username: String,
    balance: Int,
    pointRecords: List<PointRecord>,
    orders: List<RechargeOrderSummary>,
    apiBaseUrl: String,
    apiHealthStatus: ApiHealthStatus?,
    onCheckApiHealth: () -> Unit,
    onShowAuthPrompt: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenWatchRecord: (WatchRecord) -> Unit,
    onLogout: () -> Unit,
    language: AppLanguage,
    onSetLanguage: (AppLanguage) -> Unit,
) {
    val diagnostics = apiDiagnosticsText(apiHealthStatus, language)
    var showDiagnostics by remember { mutableStateOf(false) }
    var languageSheetVisible by remember { mutableStateOf(false) }
    var detailSheet by remember { mutableStateOf<AccountDetailSheet?>(null) }
    val copy = strings(language)

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AccountHero(
                username = username,
                balance = balance,
                isLoggedIn = isLoggedIn,
                language = language,
                onShowAuthPrompt = onShowAuthPrompt,
            )
        }

        item {
            AccountPrimaryActions(
                isLoggedIn = isLoggedIn,
                balance = balance,
                records = records,
                pointRecords = pointRecords,
                orders = orders,
                language = language,
                onOpenFavorites = onOpenFavorites,
                onShowAuthPrompt = onShowAuthPrompt,
                onOpenDetailSheet = { detailSheet = it },
            )
        }

        if (isLoggedIn && records.isNotEmpty()) {
            item {
                ContinueWatchingPanel(
                    records = records.take(accountContinueWatchingLimit()),
                    books = continueWatchingBooks,
                    language = language,
                    onOpenWatchRecord = onOpenWatchRecord,
                )
            }
        } else if (!isLoggedIn) {
            item {
                GuestActionPanel(language = language, onShowAuthPrompt = onShowAuthPrompt)
            }
        }

        item {
            AccountMenuGroup {
                AccountMenuRow(
                    icon = Icons.Rounded.Language,
                    title = copy.languageTitle,
                    subtitle = copy.languageSubtitle,
                    trailing = language.displayName,
                    onClick = { languageSheetVisible = true },
                )
                AccountMenuDivider()
                AccountMenuRow(
                    icon = Icons.Rounded.Settings,
                    title = copy.diagnosticsTitle,
                    subtitle = diagnostics.label,
                    trailing = if (showDiagnostics) copy.diagnosticsCollapse else copy.diagnosticsExpand,
                    onClick = { showDiagnostics = !showDiagnostics },
                )
                AnimatedVisibility(visible = showDiagnostics) {
                    Column(
                        modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(apiBaseUrl.ifBlank { copy.diagnosticsApiMissing }, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(diagnostics.message, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        GoldOutlinedButton(
                            text = copy.diagnosticsRefresh,
                            enabled = true,
                            onClick = onCheckApiHealth,
                            contentColor = PrimaryGold,
                        )
                    }
                }
                if (isLoggedIn) {
                    AccountMenuDivider()
                    AccountMenuRow(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = copy.accountSignOutTitle,
                        subtitle = copy.accountSignOutSubtitle,
                        titleColor = DangerText,
                        onClick = onLogout,
                    )
                }
            }
        }
    }

    if (detailSheet != null) {
        AccountDetailBottomSheet(
            sheet = detailSheet!!,
            records = records,
            pointRecords = pointRecords,
            orders = orders,
            language = language,
            onDismiss = { detailSheet = null },
            onOpenWatchRecord = {
                detailSheet = null
                onOpenWatchRecord(it)
            },
        )
    }

    if (languageSheetVisible) {
        ModalBottomSheet(onDismissRequest = { languageSheetVisible = false }, containerColor = Panel) {
            Column(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(copy.languageTitle, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                AppLanguage.entries.forEach { option ->
                    AccountMenuRow(
                        icon = Icons.Rounded.Language,
                        title = option.displayName,
                        subtitle = option.locale,
                        trailing = if (option == language) copy.accountSelectedLanguage else "",
                        highlight = option == language,
                        onClick = {
                            languageSheetVisible = false
                            onSetLanguage(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountHero(
    username: String,
    balance: Int,
    isLoggedIn: Boolean,
    language: AppLanguage,
    onShowAuthPrompt: () -> Unit,
) {
    val copy = strings(language)
    val displayName = if (isLoggedIn) username.ifBlank { copy.accountUserFallback } else copy.accountGuestUser
    SurfacePanel(contentPadding = PaddingValues(0.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(AccountHeroScrim, GoldSurfaceSoft, androidx.compose.ui.graphics.Color.Transparent),
                    ),
                )
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(verticalGradient(AvatarGradient))
                            .border(1.dp, WhiteEdge, RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            displayName.take(1).uppercase(),
                            color = OnPrimaryDark,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MetaPill(if (isLoggedIn) copy.accountSignInSuccessHint else copy.accountGuestPill)
                            MetaPill(language.displayName)
                        }
                        Text(
                            displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (isLoggedIn) copy.accountLoggedInStatus else copy.accountGuestStatus,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountHeroMetric(
                        label = copy.accountPointsTitle,
                        value = if (isLoggedIn) balance.toString() else "--",
                        modifier = Modifier.weight(1f),
                    )
                    AccountHeroMetric(
                        label = if (isLoggedIn) copy.accountLoggedInRewardHint else copy.accountGuestRewardHint,
                        value = if (isLoggedIn) "ReelShort" else copy.accountGuestPill,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!isLoggedIn) {
                    GoldOutlinedButton(
                        text = copy.accountGuestSignIn,
                        enabled = true,
                        onClick = onShowAuthPrompt,
                        modifier = Modifier.fillMaxWidth(),
                        contentColor = PrimaryGold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountHeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = TranslucentWhiteSurface,
        border = BorderStroke(1.dp, Divider),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = PrimaryGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = TextSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AccountPrimaryActions(
    isLoggedIn: Boolean,
    balance: Int,
    records: List<WatchRecord>,
    pointRecords: List<PointRecord>,
    orders: List<RechargeOrderSummary>,
    language: AppLanguage,
    onOpenFavorites: () -> Unit,
    onShowAuthPrompt: () -> Unit,
    onOpenDetailSheet: (AccountDetailSheet) -> Unit,
) {
    val copy = strings(language)
    fun openSheetForAction(label: String) {
        accountPrimaryActionSheet(label, language)?.let(onOpenDetailSheet)
    }
    val latestPointRecord = pointRecords.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountActionTile(
                icon = Icons.Rounded.BookmarkBorder,
                title = copy.accountFavoritesTitle,
                subtitle = copy.accountFavoritesSubtitle,
                highlight = true,
                modifier = Modifier.weight(1f),
                onClick = if (isLoggedIn) onOpenFavorites else onShowAuthPrompt,
            )
            AccountActionTile(
                icon = Icons.Rounded.MonetizationOn,
                title = copy.accountPointsTitle,
                subtitle = if (isLoggedIn) "$balance${copy.accountPointsSuffix}" else copy.accountGuestPill,
                trailing = latestPointRecord?.let(::pointRecordAmountLabel).orEmpty(),
                highlight = true,
                modifier = Modifier.weight(1f),
                onClick = if (isLoggedIn) {
                    { openSheetForAction(copy.accountPointsTitle) }
                } else {
                    onShowAuthPrompt
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountActionTile(
                icon = Icons.Rounded.History,
                title = copy.accountWatchHistoryTitle,
                subtitle = if (isLoggedIn) "${copy.accountRecentCountPrefix}${records.size}${copy.accountRecordsSuffix}" else copy.accountGuestSignInSubtitle,
                trailing = records.firstOrNull()?.let { "${it.progressPercent}%" }.orEmpty(),
                modifier = Modifier.weight(1f),
                onClick = if (isLoggedIn) {
                    { openSheetForAction(copy.accountWatchHistoryTitle) }
                } else {
                    onShowAuthPrompt
                },
            )
            AccountActionTile(
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                title = copy.accountOrdersTitle,
                subtitle = if (isLoggedIn && orders.isNotEmpty()) {
                    "${copy.accountRecentCountPrefix}${orders.size}${copy.accountOrdersRecentSuffix}"
                } else {
                    copy.accountOrdersReserved
                },
                trailing = orders.firstOrNull()?.status.orEmpty(),
                modifier = Modifier.weight(1f),
                onClick = if (isLoggedIn) {
                    { openSheetForAction(copy.accountOrdersTitle) }
                } else {
                    onShowAuthPrompt
                },
            )
        }
    }
}

private fun pointRecordAmountLabel(record: PointRecord): String =
    if (record.amount > 0) "+${record.amount}" else "${record.amount}"

@Composable
private fun AccountActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: String = "",
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .height(118.dp)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        color = if (highlight) GoldSurfaceStrong else Panel.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, if (highlight) GoldStroke else Divider),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (highlight) PrimaryGold else TranslucentWhiteSurface, shape = RoundedCornerShape(13.dp)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp).size(21.dp),
                        tint = if (highlight) OnPrimaryDark else PrimaryGold,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (trailing.isNotBlank()) {
                    Text(trailing, color = PrimaryGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ContinueWatchingPanel(
    records: List<WatchRecord>,
    books: Map<String, BookSummary>,
    language: AppLanguage,
    onOpenWatchRecord: (WatchRecord) -> Unit,
) {
    val copy = strings(language)
    SurfacePanel {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(copy.accountRecentWatchTitle, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(copy.accountRecentWatchSubtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                MetaPill("${records.size}/${accountContinueWatchingLimit()}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                records.forEach { record ->
                    ContinueWatchingPosterCard(
                        record = record,
                        book = books[record.bookId],
                        language = language,
                        onClick = { onOpenWatchRecord(record) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (records.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingPosterCard(
    record: WatchRecord,
    book: BookSummary?,
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = strings(language)
    val title = book?.title?.takeIf { it.isNotBlank() } ?: record.bookTitle
    val progress = record.progressPercent.coerceIn(0, 100)
    Surface(
        modifier = modifier
            .height(224.dp)
            .semantics {
                contentDescription =
                    "$title, ${copy.listEpisodePrefix}${record.episode}${if (language == AppLanguage.ENGLISH) "" else copy.playerEpisodeUnit}, ${progress}%"
            }
            .clickable(onClick = onClick),
        color = Panel.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Divider),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 8.dp, top = 8.dp, end = 8.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                PosterBlock(title, book?.coverUrl, Modifier.matchParentSize())
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(GoldSurfaceStrong, RoundedCornerShape(999.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        "${copy.listEpisodePrefix}${record.episode}${if (language == AppLanguage.ENGLISH) "" else copy.playerEpisodeUnit}",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    color = GoldSurfaceStrong,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                        tint = PrimaryGold,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = PrimaryGold,
                    trackColor = TranslucentWhiteSurface,
                )
                Text(
                    "${progress}%",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(record: WatchRecord, language: AppLanguage, onClick: () -> Unit) {
    val copy = strings(language)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(color = GoldSurfaceStrong, shape = RoundedCornerShape(15.dp)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.padding(11.dp).size(22.dp), tint = PrimaryGold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.bookTitle, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${copy.listEpisodePrefix}${record.episode}${if (language == AppLanguage.ENGLISH) "" else copy.playerEpisodeUnit} · ${record.progressPercent}%",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDetailBottomSheet(
    sheet: AccountDetailSheet,
    records: List<WatchRecord>,
    pointRecords: List<PointRecord>,
    orders: List<RechargeOrderSummary>,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onOpenWatchRecord: (WatchRecord) -> Unit,
) {
    val copy = strings(language)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    accountDetailSheetTitle(sheet, language),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            when (sheet) {
                AccountDetailSheet.POINT_RECORDS -> {
                    if (pointRecords.isEmpty()) {
                        item { AccountEmptyDetail(copy.accountPointRecordsTitle) }
                    } else {
                        items(pointRecords) { PointRecordRow(it, language) }
                    }
                }
                AccountDetailSheet.WATCH_HISTORY -> {
                    if (records.isEmpty()) {
                        item { AccountEmptyDetail(copy.accountRecentWatchSubtitle) }
                    } else {
                        items(records, key = { "${it.bookId}-${it.episode}" }) { record ->
                            ContinueWatchingRow(record = record, language = language, onClick = { onOpenWatchRecord(record) })
                        }
                    }
                }
                AccountDetailSheet.ORDERS -> {
                    if (orders.isEmpty()) {
                        item { AccountEmptyDetail(copy.accountOrdersReserved) }
                    } else {
                        items(orders, key = { it.orderNo }) { OrderRow(it, language) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountEmptyDetail(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelSoft.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, Divider),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun GuestActionPanel(language: AppLanguage, onShowAuthPrompt: () -> Unit) {
    val copy = strings(language)
    AccountMenuGroup {
        guestAccountEntryLabels(language).forEachIndexed { index, label ->
            AccountMenuRow(
                icon = if (index == 0) Icons.Rounded.AccountCircle else Icons.Rounded.PlayArrow,
                title = label,
                subtitle = if (index == 0) copy.accountGuestSignInSubtitle else copy.accountGuestRegisterSubtitle,
                onClick = onShowAuthPrompt,
            )
            if (index < guestAccountEntryLabels(language).lastIndex) {
                AccountMenuDivider()
            }
        }
    }
}

@Composable
private fun AccountMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelSoft.copy(alpha = 0.84f),
        contentColor = TextPrimary,
        border = BorderStroke(1.dp, Divider),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun AccountMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: String = "",
    highlight: Boolean = false,
    titleColor: androidx.compose.ui.graphics.Color = TextPrimary,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            color = if (highlight) GoldSurfaceStrong else TranslucentWhiteSurface,
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(22.dp),
                tint = if (titleColor == DangerText) DangerText else PrimaryGold,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = titleColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing.isNotBlank()) {
            Text(
                trailing,
                color = if (highlight) PrimaryGold else TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AccountMenuDivider() {
    Box(
        modifier = Modifier
            .padding(start = 68.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Divider),
    )
}
