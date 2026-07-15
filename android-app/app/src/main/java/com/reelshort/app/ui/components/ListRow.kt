package com.reelshort.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.theme.Panel
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary

/**
 * 统一的列表行卡片，供观看记录 / 积分流水 / 订单复用。
 */
@Composable
internal fun ListRow(title: String, subtitle: String, trailing: String, highlight: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Panel,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(trailing, color = if (highlight) PrimaryGold else TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun WatchRecordRow(
    record: WatchRecord,
    language: AppLanguage = AppLanguage.ENGLISH,
) {
    val copy = strings(language)
    ListRow(
        title = record.bookTitle,
        subtitle = "${copy.listEpisodePrefix}${record.episode}",
        trailing = "${record.progressPercent}%",
    )
}

@Composable
internal fun PointRecordRow(
    record: PointRecord,
    language: AppLanguage = AppLanguage.ENGLISH,
) {
    val copy = strings(language)
    ListRow(
        title = record.reason ?: copy.listPointsChangeFallback,
        subtitle = copy.listPointRecordSubtitle,
        trailing = if (record.amount > 0) "+${record.amount}" else "${record.amount}",
        highlight = record.amount > 0,
    )
}

@Composable
internal fun OrderRow(
    order: RechargeOrderSummary,
    language: AppLanguage = AppLanguage.ENGLISH,
) {
    val copy = strings(language)
    ListRow(
        title = order.orderNo,
        subtitle = "${copy.listCurrencySymbol}${order.amountCents / 100}.${(order.amountCents % 100).toString().padStart(2, '0')} · ${order.pointAmount} ${copy.listPointsLabel}",
        trailing = order.status,
    )
}
