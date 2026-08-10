package com.reelshort.app.ui.screens.account

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.WithdrawalRecord
import com.reelshort.app.state.AccountOperation
import com.reelshort.app.state.AppUiState
import com.reelshort.app.ui.components.PrimaryActionButton
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.format.withdrawalConversionLines
import com.reelshort.app.ui.format.withdrawalStatusLabel
import com.reelshort.app.ui.theme.DangerText
import com.reelshort.app.ui.theme.Panel
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.SuccessText
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary

@Composable
internal fun WithdrawalScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onSubmitWithdrawal: (Int) -> Unit,
    onShowAuthPrompt: () -> Unit,
    language: AppLanguage,
) {
    val copy = strings(language)
    val summary = state.withdrawalSummary
    var pointsText by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    val points = pointsText.toIntOrNull() ?: 0
    val loggedIn = state.session != null
    val submitting = state.accountOperation == AccountOperation.WITHDRAWAL
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowBack, copy.accountWithdrawTitle, tint = TextPrimary) }
            Text(copy.accountWithdrawTitle, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SurfacePanel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!loggedIn) {
                            Text(copy.fundsLoginRequired, color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
                            PrimaryActionButton(copy.accountGuestSignIn, true, onShowAuthPrompt)
                        } else {
                            withdrawalConversionLines(summary, points, language).forEach { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodyMedium) }
                            Text(
                                summary?.walletAddress?.let { "${state.wallet?.network ?: "ERC20"} · $it" } ?: copy.accountWithdrawWalletRequired,
                                color = if (summary?.walletAddress == null) DangerText else TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedTextField(
                                value = pointsText,
                                onValueChange = { pointsText = it.filter(Char::isDigit) },
                                label = { Text(copy.accountWithdrawPointAmountLabel) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            val minimum = summary?.minimumPoints ?: Int.MAX_VALUE
                            val available = summary?.availablePoints ?: 0
                            PrimaryActionButton(
                                text = copy.accountWithdrawSubmitAction,
                                enabled = !submitting && summary?.walletAddress != null && points >= minimum && points <= available,
                                onClick = { showConfirm = true },
                            )
                        }
                    }
                }
            }
            item { Text(copy.accountWithdrawalRecordsTitle, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (state.withdrawals.isEmpty()) {
                item { Text(copy.accountWithdrawalRecordsEmpty, color = TextSecondary) }
            } else {
                items(state.withdrawals, key = { it.id }) { WithdrawalHistoryRow(it, language) }
            }
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { if (!submitting) showConfirm = false },
            title = { Text(copy.accountWithdrawSubmitAction) },
            text = { Text("Withdraw $points ${copy.accountPointsSuffix}") },
            confirmButton = { TextButton(enabled = !submitting, onClick = { showConfirm = false; onSubmitWithdrawal(points) }) { Text(copy.authContinue, color = PrimaryGold) } },
            dismissButton = { TextButton(enabled = !submitting, onClick = { showConfirm = false }) { Text(copy.authClose, color = TextSecondary) } },
            containerColor = Panel,
        )
    }
}

@Composable
private fun WithdrawalHistoryRow(record: WithdrawalRecord, language: AppLanguage) {
    SurfacePanel {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${record.pointAmount} pts · ${record.usdtAmount} USDT", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                val status = withdrawalStatusLabel(record.status, language)
                Text(status, color = if (record.status == "CONFIRMED") SuccessText else if (record.status == "REJECTED") DangerText else PrimaryGold)
            }
            Text(record.walletAddress, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
