package com.reelshort.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.VipOrder
import com.reelshort.app.ui.format.episodeNumberLabel
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.theme.DangerText
import com.reelshort.app.ui.theme.Panel
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.SuccessText
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.OffsetDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VipPaymentBottomSheet(
    visible: Boolean,
    language: AppLanguage,
    vipActive: Boolean,
    vipUntil: String?,
    vipPriceUsdt: String,
    vipCollectionAddress: String,
    latestVipOrder: VipOrder?,
    pendingVipEpisode: EpisodeSummary?,
    vipPaywallLoading: Boolean,
    onCreateOrder: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val copy = strings(language)
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pendingOrder = if (!vipActive && latestVipOrder?.status == "PENDING") latestVipOrder else null
    val confirmedOrder = if (!vipActive && latestVipOrder?.status == "CONFIRMED") latestVipOrder else null
    var remainingSeconds by remember(pendingOrder?.expiresAt) {
        mutableIntStateOf(computeRemainingSeconds(pendingOrder?.expiresAt))
    }
    LaunchedEffect(pendingOrder?.expiresAt) {
        while (remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds--
        }
    }
    val activePendingOrder = pendingOrder?.takeIf { it.expiresAt == null || remainingSeconds > 0 }
    val refreshableOrder = activePendingOrder ?: confirmedOrder
    val currentRefreshableOrder by rememberUpdatedState(refreshableOrder)
    LaunchedEffect(refreshableOrder?.id, refreshableOrder?.status) {
        while (currentRefreshableOrder != null) {
            delay(10_000)
            if (currentRefreshableOrder == null) break
            onRefresh()
        }
    }

    val canCreateOrder = !vipPaywallLoading && !vipActive &&
        (latestVipOrder == null ||
            latestVipOrder.status in setOf("EXPIRED", "REJECTED") ||
            latestVipOrder.status == "PENDING" && latestVipOrder.expiresAt != null && remainingSeconds <= 0)
    val paymentAddress = pendingOrder?.receivingWalletAddress ?: vipCollectionAddress

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Panel,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.size(24.dp),
                    )
                    Column {
                        Text(
                            text = pendingVipEpisode?.let {
                                "${copy.vipPayUnlockEpisodePrefix}${episodeNumberLabel(it.number, language)}"
                            } ?: copy.vipPayTitle,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (pendingVipEpisode != null) {
                            Text(
                                text = copy.vipPayLockedHint,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = copy.authClose, tint = TextSecondary)
                }
            }

            when {
                vipActive -> {
                    Text(copy.vipActiveLabel, color = SuccessText, style = MaterialTheme.typography.titleMedium)
                    vipUntil?.let {
                        Text("${copy.vipExpiryLabel}$it", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                confirmedOrder != null -> {
                    Text(
                        "${copy.vipPayAmount}${confirmedOrder.payableAmount ?: confirmedOrder.usdtAmount} USDT",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${copy.vipPayOrderNo}${confirmedOrder.orderNo}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        copy.vipPayStatusConfirmed,
                        color = SuccessText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PrimaryActionButton(
                        text = if (vipPaywallLoading) copy.vipPayRefreshing else copy.vipPayRefresh,
                        enabled = !vipPaywallLoading,
                        onClick = onRefresh,
                    )
                }

                activePendingOrder != null -> {
                    Text(
                        "${copy.vipPayAmount}${activePendingOrder.payableAmount ?: activePendingOrder.usdtAmount} USDT",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("${copy.vipPayOrderNo}${activePendingOrder.orderNo}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text(copy.vipPayAddress, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        paymentAddress.ifBlank { "N/A" },
                        modifier = Modifier.fillMaxWidth(),
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "${copy.vipPayExpiresIn} ${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')}",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (paymentAddress.isNotBlank()) {
                        AsyncImage(
                            model = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=$paymentAddress",
                            contentDescription = copy.vipPayQrHint,
                            modifier = Modifier.size(184.dp),
                        )
                        GoldOutlinedButton(
                            text = copy.vipPayCopy,
                            enabled = !vipPaywallLoading,
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(paymentAddress))
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            contentColor = PrimaryGold,
                        )
                    }
                    Text(copy.vipPayHint, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    PrimaryActionButton(
                        text = if (vipPaywallLoading) copy.vipPayRefreshing else copy.vipPayRefresh,
                        enabled = !vipPaywallLoading,
                        onClick = onRefresh,
                    )
                }

                else -> {
                    if (latestVipOrder?.status == "EXPIRED" ||
                        latestVipOrder?.status == "PENDING" && latestVipOrder.expiresAt != null && remainingSeconds <= 0
                    ) {
                        Text(copy.vipPayExpired, color = DangerText, style = MaterialTheme.typography.bodyMedium)
                    } else if (latestVipOrder?.status == "REJECTED") {
                        Text(copy.vipPayStatusRejected, color = DangerText, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "${vipPriceUsdt.ifBlank { "--" }}${copy.accountVipPriceSuffix}",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    PrimaryActionButton(
                        text = if (vipPaywallLoading) copy.vipPayCreating else copy.accountVipUnlockAction,
                        enabled = canCreateOrder,
                        onClick = onCreateOrder,
                    )
                }
            }
        }
    }
}

private fun computeRemainingSeconds(expiresAt: String?): Int {
    if (expiresAt == null) return 0
    return try {
        maxOf(0, Duration.between(OffsetDateTime.now(), OffsetDateTime.parse(expiresAt)).seconds.toInt())
    } catch (_: Exception) {
        0
    }
}
