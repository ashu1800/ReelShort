package com.reelshort.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.VipOrder
import com.reelshort.app.state.AppUiState
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.theme.Divider
import com.reelshort.app.ui.theme.PanelSoft
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.DangerText
import com.reelshort.app.ui.theme.SuccessText
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary
import com.reelshort.app.ui.theme.TranslucentWhiteSurface

@Composable
internal fun FundsScreen(
    state: AppUiState,
    vipOrders: List<VipOrder>,
    onOpenWithdrawal: () -> Unit,
    onOpenVipRecharge: () -> Unit,
    onShowAuthPrompt: () -> Unit,
    language: AppLanguage,
) {
    val copy = strings(language)
    val account = state.pointAccount
    val loggedIn = state.session != null
    Column {
        Text(
            copy.fundsTitle,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = PanelSoft,
                    border = BorderStroke(1.dp, Divider),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(copy.fundsSubtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        if (!loggedIn) {
                            Text(copy.fundsLoginRequired, color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
                        } else {
                            FundsMetric(copy.fundsTotalPointsLabel, account?.balance ?: 0)
                            FundsMetric(copy.fundsTodayCreditedPointsLabel, account?.todayCreditedPoints ?: 0)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                FundsMetric(copy.fundsFrozenPointsLabel, account?.frozenPoints ?: 0, Modifier.weight(1f))
                                FundsMetric(copy.fundsAvailablePointsLabel, account?.availablePoints ?: 0, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item {
                FundsActionRow(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    title = copy.fundsWithdrawalAction,
                    subtitle = copy.fundsWithdrawalSubtitle,
                    onClick = if (loggedIn) onOpenWithdrawal else onShowAuthPrompt,
                )
            }
            item {
                FundsActionRow(
                    icon = Icons.Rounded.Star,
                    title = copy.fundsVipRechargeAction,
                    subtitle = copy.fundsVipRechargeSubtitle,
                    onClick = if (loggedIn) onOpenVipRecharge else onShowAuthPrompt,
                )
            }
            item {
                Text(
                    copy.vipRecordsTitle,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (vipOrders.isEmpty()) {
                item { Text(copy.accountVipNoOrders, color = TextSecondary) }
            } else {
                items(vipOrders, key = { it.id }) { order -> FundsVipOrderRow(order, language) }
            }
        }
    }
}

@Composable
private fun FundsVipOrderRow(order: VipOrder, language: AppLanguage) {
    val copy = strings(language)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PanelSoft,
        border = BorderStroke(1.dp, Divider),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${copy.vipPayAmount}${order.payableAmount ?: order.usdtAmount} USDT",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(order.orderNo, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(order.createdAt, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            val (statusColor, statusText) = when (order.status) {
                "CONFIRMED" -> SuccessText to copy.vipPayStatusConfirmed
                "REJECTED" -> DangerText to copy.vipPayStatusRejected
                else -> PrimaryGold to copy.vipPayStatusPending
            }
            Text(statusText, color = statusColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FundsMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value.toString(), color = PrimaryGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FundsActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = PanelSoft,
        border = BorderStroke(1.dp, Divider),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(color = TranslucentWhiteSurface, shape = MaterialTheme.shapes.small) {
                Icon(icon, contentDescription = null, tint = PrimaryGold, modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
