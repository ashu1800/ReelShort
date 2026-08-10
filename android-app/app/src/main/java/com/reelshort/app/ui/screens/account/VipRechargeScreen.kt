package com.reelshort.app.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.state.AccountOperation
import com.reelshort.app.state.AppUiState
import com.reelshort.app.ui.components.GoldOutlinedButton
import com.reelshort.app.ui.components.PrimaryActionButton
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.theme.DangerText
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.SuccessText
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.OffsetDateTime

@Composable
internal fun VipRechargeScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onCreateOrder: () -> Unit,
    onRefresh: () -> Unit,
    onShowAuthPrompt: () -> Unit,
    language: AppLanguage,
) {
    val copy = strings(language)
    val loggedIn = state.session != null
    val operation = state.accountOperation
    val latest = state.latestVipOrder
    val pending = latest?.takeIf { it.status == "PENDING" }
    val clipboard = LocalClipboardManager.current
    var remaining by remember(pending?.expiresAt) { mutableIntStateOf(remainingSeconds(pending?.expiresAt)) }
    LaunchedEffect(pending?.expiresAt) {
        while (remaining > 0) { delay(1_000); remaining-- }
    }
    val activePending = pending?.takeIf { it.expiresAt == null || remaining > 0 }
    val canCreate = loggedIn && !state.vipActive && operation == null &&
        (latest == null || latest.status in setOf("EXPIRED", "REJECTED") || latest.status == "PENDING" && activePending == null)
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, copy.vipPayTitle, tint = TextPrimary) }
            Text(copy.vipPayTitle, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                SurfacePanel {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!loggedIn) {
                            Text(copy.fundsLoginRequired, color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
                            PrimaryActionButton(copy.accountGuestSignIn, true, onShowAuthPrompt)
                        } else if (state.vipActive) {
                            Text(copy.vipActiveLabel, color = SuccessText, style = MaterialTheme.typography.titleMedium)
                            state.vipUntil?.let { Text("${copy.vipExpiryLabel}$it", color = TextSecondary) }
                        } else if (latest?.status == "CONFIRMED") {
                            Text("${copy.vipPayAmount}${latest.payableAmount ?: latest.usdtAmount} USDT", color = PrimaryGold, fontWeight = FontWeight.Bold)
                            Text("${copy.vipPayOrderNo}${latest.orderNo}", color = TextSecondary)
                            Text(copy.vipPayStatusConfirmed, color = SuccessText)
                            PrimaryActionButton(if (operation != null) copy.vipPayRefreshing else copy.vipPayRefresh, operation == null, onRefresh)
                        } else if (activePending != null) {
                            Text("${copy.vipPayAmount}${activePending.payableAmount ?: activePending.usdtAmount} USDT", color = PrimaryGold, fontWeight = FontWeight.Bold)
                            Text("${copy.vipPayOrderNo}${activePending.orderNo}", color = TextSecondary)
                            VipPaymentDetails(activePending.receivingWalletAddress ?: state.vipCollectionAddress, copy, clipboard, operation != null)
                            Text("${copy.vipPayExpiresIn} ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", color = PrimaryGold)
                            Text(copy.vipPayHint, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            PrimaryActionButton(if (operation != null) copy.vipPayRefreshing else copy.vipPayRefresh, operation == null, onRefresh)
                        } else {
                            latest?.status?.let { if (it == "REJECTED") Text(copy.vipPayStatusRejected, color = DangerText) else if (it == "EXPIRED") Text(copy.vipPayExpired, color = DangerText) }
                            Text("${state.vipPriceUsdt.ifBlank { "--" }}${copy.accountVipPriceSuffix}", color = PrimaryGold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            PrimaryActionButton(if (operation != null) copy.vipPayCreating else copy.accountVipUnlockAction, canCreate, onCreateOrder)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VipPaymentDetails(address: String, copy: com.reelshort.app.ui.format.AppStrings, clipboard: androidx.compose.ui.platform.ClipboardManager, busy: Boolean) {
    Text(copy.vipPayAddress, color = TextSecondary, modifier = Modifier.fillMaxWidth())
    Text(address.ifBlank { "N/A" }, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (address.isNotBlank()) {
        AsyncImage("https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=$address", copy.vipPayQrHint, Modifier.size(184.dp))
        GoldOutlinedButton(copy.vipPayCopy, !busy, { clipboard.setText(AnnotatedString(address)) }, Modifier.fillMaxWidth().size(48.dp), PrimaryGold)
    }
}

private fun remainingSeconds(expiresAt: String?): Int = try {
    if (expiresAt == null) 0 else maxOf(0, Duration.between(OffsetDateTime.now(), OffsetDateTime.parse(expiresAt)).seconds.toInt())
} catch (_: Exception) { 0 }
