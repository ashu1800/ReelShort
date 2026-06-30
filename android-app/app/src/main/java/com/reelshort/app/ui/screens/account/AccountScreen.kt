package com.reelshort.app.ui.screens.account

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
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.ui.components.AvatarGradient
import com.reelshort.app.ui.components.GoldOutlinedButton
import com.reelshort.app.ui.components.MetaPill
import com.reelshort.app.ui.components.SectionHeader
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.components.WatchRecordRow
import com.reelshort.app.ui.components.verticalGradient
import com.reelshort.app.ui.format.apiDiagnosticsText
import com.reelshort.app.ui.format.guestAccountEntryLabels
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.theme.DangerText
import com.reelshort.app.ui.theme.Divider
import com.reelshort.app.ui.theme.GoldSurfaceStrong
import com.reelshort.app.ui.theme.OnPrimaryDark
import com.reelshort.app.ui.theme.Panel
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.PrimaryGoldDark
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary
import com.reelshort.app.ui.theme.WhiteEdge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountScreen(
    records: List<WatchRecord>,
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
    onLogout: () -> Unit,
    language: AppLanguage,
    onSetLanguage: (AppLanguage) -> Unit,
) {
    val diagnostics = apiDiagnosticsText(apiHealthStatus)
    var showDiagnostics by remember { mutableStateOf(false) }
    var languageSheetVisible by remember { mutableStateOf(false) }
    val copy = strings(language)

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            AccountHero(username = username, balance = balance, isLoggedIn = isLoggedIn)
        }
        if (!isLoggedIn) {
            item {
                AccountMenuGroup {
                    guestAccountEntryLabels().forEachIndexed { index, label ->
                        AccountMenuRow(
                            icon = if (label == "登录") Icons.Rounded.AccountCircle else Icons.Rounded.PlayArrow,
                            title = label,
                            subtitle = if (label == "登录") "登录后可播放短剧并获取积分" else "创建账号保存观看进度",
                            onClick = onShowAuthPrompt,
                        )
                        if (index < guestAccountEntryLabels().lastIndex) {
                            AccountMenuDivider()
                        }
                    }
                }
            }
        }
        if (isLoggedIn) item {
            AccountMenuGroup {
                AccountMenuRow(
                    icon = Icons.Rounded.BookmarkBorder,
                    title = "我的收藏",
                    subtitle = "收藏的短剧",
                    trailing = "查看",
                    onClick = onOpenFavorites,
                )
            }
        }
        if (isLoggedIn) item {
            AccountMenuGroup {
                AccountMenuRow(
                    icon = Icons.Rounded.MonetizationOn,
                    title = "积分余额",
                    subtitle = "$balance 积分",
                    trailing = "$balance",
                    highlight = true,
                )
                AccountMenuDivider()
                AccountMenuRow(
                    icon = Icons.Rounded.History,
                    title = "观看记录",
                    subtitle = "最近 ${records.size} 条",
                    trailing = records.firstOrNull()?.let { "${it.progressPercent}%" }.orEmpty(),
                )
                AccountMenuDivider()
                AccountMenuRow(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = "积分流水",
                    subtitle = "最近 ${pointRecords.size} 条",
                    trailing = pointRecords.firstOrNull()?.let { if (it.amount > 0) "+${it.amount}" else "${it.amount}" }.orEmpty(),
                )
                AccountMenuDivider()
                AccountMenuRow(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = "充值订单",
                    subtitle = if (orders.isEmpty()) "商业化预留" else "最近 ${orders.size} 笔",
                    trailing = orders.firstOrNull()?.status.orEmpty(),
                )
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
                    title = "开发诊断",
                    subtitle = diagnostics.label,
                    trailing = if (showDiagnostics) "收起" else "查看",
                    onClick = { showDiagnostics = !showDiagnostics },
                )
                androidx.compose.animation.AnimatedVisibility(visible = showDiagnostics) {
                    Column(
                        modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(apiBaseUrl.ifBlank { "API 地址未配置" }, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(diagnostics.message, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        GoldOutlinedButton(
                            text = "刷新诊断",
                            enabled = true,
                            onClick = onCheckApiHealth,
                            contentColor = PrimaryGold,
                        )
                    }
                }
            }
        }
        if (isLoggedIn) item {
            AccountMenuGroup {
                AccountMenuRow(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    title = "退出登录",
                    subtitle = "退出当前账号",
                    titleColor = DangerText,
                    onClick = onLogout,
                )
            }
        }
        if (isLoggedIn && records.isNotEmpty()) {
            item { SectionHeader("最近观看", "继续上次的短剧进度") }
            items(records.take(3), key = { "${it.bookId}-${it.episode}" }) { record -> WatchRecordRow(record) }
        }
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
                        trailing = if (option == language) "Selected" else "",
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
private fun AccountHero(username: String, balance: Int, isLoggedIn: Boolean) {
    val displayName = if (isLoggedIn) username.ifBlank { "ReelShort 用户" } else "游客"
    SurfacePanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(verticalGradient(AvatarGradient))
                    .border(1.dp, WhiteEdge, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    displayName.take(1).uppercase(),
                    color = OnPrimaryDark,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text(
                    if (isLoggedIn) "已登录 · ReelShort" else "登录后可保存观看进度和积分",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaPill(if (isLoggedIn) "积分 $balance" else "未登录")
                    Text(
                        if (isLoggedIn) "持续观看可获得奖励" else "可先浏览首页和搜索",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Panel.copy(alpha = 0.92f),
        contentColor = TextPrimary,
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
    titleColor: Color = TextPrimary,
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
            color = if (highlight) GoldSurfaceStrong else Color(0x1FFFFFFF),
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
            Text(title, color = titleColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
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
