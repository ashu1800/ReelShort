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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import coil.compose.AsyncImage
import com.reelshort.app.data.AppLanguage
import kotlinx.coroutines.delay
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.VipOrder
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.data.WalletInfo
import com.reelshort.app.data.WithdrawalRecord
import com.reelshort.app.data.WithdrawalSummary
import com.reelshort.app.state.AuthMode
import com.reelshort.app.state.AccountOperation
import com.reelshort.app.ui.components.AvatarGradient
import com.reelshort.app.ui.components.GoldOutlinedButton
import com.reelshort.app.ui.components.LoginTextField
import com.reelshort.app.ui.components.TextFieldKind
import com.reelshort.app.ui.components.ListRow
import com.reelshort.app.ui.components.MetaPill
import com.reelshort.app.ui.components.OrderRow
import com.reelshort.app.ui.components.PosterBlock
import com.reelshort.app.ui.components.PrimaryActionButton
import com.reelshort.app.ui.components.PointRecordRow
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.components.verticalGradient
import com.reelshort.app.ui.format.AccountDetailSheet
import com.reelshort.app.ui.format.accountDetailSheetTitle
import com.reelshort.app.ui.format.accountContinueWatchingLimit
import com.reelshort.app.ui.format.accountPrimaryActionSheet
import com.reelshort.app.ui.format.commercialSheetAutoDismissesAfterSubmit
import com.reelshort.app.ui.format.guestAccountEntryAuthModes
import com.reelshort.app.ui.format.guestAccountEntryLabels
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.format.updateStrings
import com.reelshort.app.ui.format.walletSheetShouldDismiss
import com.reelshort.app.ui.format.withdrawalConversionLines
import com.reelshort.app.ui.format.withdrawalRecordDetail
import com.reelshort.app.ui.format.withdrawalStatusLabel
import com.reelshort.app.ui.format.accountConfirmationBody
import com.reelshort.app.ui.format.accountConfirmationCancel
import com.reelshort.app.ui.format.accountConfirmationConfirm
import com.reelshort.app.ui.format.accountConfirmationTitle
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
import com.reelshort.app.ui.theme.SuccessText
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
    frozenPoints: Int,
    availablePoints: Int,
    pointRecords: List<PointRecord>,
    orders: List<RechargeOrderSummary>,
    vipOrders: List<VipOrder> = emptyList(),
    wallet: WalletInfo?,
    walletMutationVersion: Long,
    withdrawalSubmissionVersion: Long,
    vipUntil: String?,
    vipPriceUsdt: String,
    vipCollectionAddress: String,
    latestVipOrder: VipOrder?,
    accountOperation: AccountOperation?,
    withdrawalSummary: WithdrawalSummary?,
    withdrawals: List<WithdrawalRecord>,
    appVersionLabel: String,
    isCheckingForUpdate: Boolean,
    onCheckForUpdate: () -> Unit,
    onShowAuthPrompt: () -> Unit,
    onShowRegisterAuthPrompt: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenWatchRecord: (WatchRecord) -> Unit,
    onBindWallet: (String, String, String) -> Unit,
    onUnbindWallet: (String) -> Unit,
    onCreateVipOrder: () -> Unit,
    onRefreshVipOrder: () -> Unit,
    onRefreshAccount: () -> Unit,
    onSubmitWithdrawal: (Int) -> Unit,
    onChangePassword: (String, String) -> Unit,
    onSubmitBankCard: (String, String, String, String, String) -> Unit,
    onLogout: () -> Unit,
    language: AppLanguage,
) {
    var detailSheet by remember { mutableStateOf<AccountDetailSheet?>(null) }
    var walletSheetVisible by remember { mutableStateOf(false) }
    var lastHandledWalletMutationVersion by remember { mutableStateOf(walletMutationVersion) }
    var lastHandledWithdrawalSubmissionVersion by remember { mutableStateOf(withdrawalSubmissionVersion) }
    var passwordSheetVisible by remember { mutableStateOf(false) }
    var bankCardSheetVisible by remember { mutableStateOf(false) }
    var vipSheetVisible by remember { mutableStateOf(false) }
    var pendingConfirmation by remember { mutableStateOf<PendingAccountConfirmation?>(null) }
    val copy = strings(language)
    val updateCopy = updateStrings(language)

    LaunchedEffect(walletMutationVersion) {
        if (
            walletSheetShouldDismiss(
                visible = walletSheetVisible,
                lastHandledVersion = lastHandledWalletMutationVersion,
                currentVersion = walletMutationVersion,
            )
        ) {
            walletSheetVisible = false
        }
        lastHandledWalletMutationVersion = walletMutationVersion
    }

    LaunchedEffect(withdrawalSubmissionVersion) {
        if (
            detailSheet == AccountDetailSheet.WITHDRAWALS &&
            withdrawalSubmissionVersion > lastHandledWithdrawalSubmissionVersion
        ) {
            detailSheet = null
        }
        lastHandledWithdrawalSubmissionVersion = withdrawalSubmissionVersion
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AccountHero(
                username = username,
                balance = balance,
                frozenPoints = frozenPoints,
                availablePoints = availablePoints,
                isLoggedIn = isLoggedIn,
                language = language,
                onShowAuthPrompt = onShowAuthPrompt,
            )
        }

        item {
            AccountPrimaryActions(
                isLoggedIn = isLoggedIn,
                balance = balance,
                availablePoints = availablePoints,
                records = records,
                pointRecords = pointRecords,
                withdrawalSummary = withdrawalSummary,
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
                GuestActionPanel(
                    language = language,
                    onShowAuthPrompt = onShowAuthPrompt,
                    onShowRegisterAuthPrompt = onShowRegisterAuthPrompt,
                )
            }
        }

        if (isLoggedIn) {
            item {
                AccountMenuGroup {
                    AccountMenuRow(
                        icon = Icons.Rounded.MonetizationOn,
                        title = copy.accountWalletTitle,
                        subtitle = wallet?.let { "${it.network} · ${it.walletAddress}" } ?: "ERC20 / TRC20",
                        onClick = { walletSheetVisible = true },
                    )
                    AccountMenuDivider()
                    AccountMenuRow(
                        icon = Icons.Rounded.Star,
                        title = if (vipUntil.isNullOrBlank()) copy.accountVipStatusInactive else copy.accountVipStatusActive,
                        subtitle = vipUntil?.let { "${copy.vipExpiryLabel}$it" }
                            ?: "${vipPriceUsdt.ifBlank { "" }}${copy.accountVipPriceSuffix}".trim(),
                        onClick = { onRefreshAccount(); vipSheetVisible = true },
                    )
                    AccountMenuDivider()
                    AccountMenuRow(
                        icon = Icons.Rounded.Settings,
                        title = copy.accountChangePasswordTitle,
                        subtitle = copy.accountPasswordNewLabel,
                        onClick = { passwordSheetVisible = true },
                    )
                    AccountMenuDivider()
                    AccountMenuRow(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = copy.accountBankCardTitle,
                        subtitle = copy.accountBankCardSubtitle,
                        onClick = { bankCardSheetVisible = true },
                    )
                    AccountMenuDivider()
                    AccountMenuRow(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = copy.accountOrdersTitle,
                        subtitle = if (vipOrders.isEmpty()) copy.accountOrdersReserved else "${vipOrders.size}",
                        onClick = { detailSheet = AccountDetailSheet.ORDERS },
                    )
                }
            }
        }

        item {
            AccountMenuGroup {
                AccountMenuRow(
                    icon = Icons.Rounded.SystemUpdate,
                    title = updateCopy.versionTitle,
                    subtitle = appVersionLabel,
                    trailing = if (isCheckingForUpdate) updateCopy.checking else updateCopy.checkForUpdates,
                    onClick = if (isCheckingForUpdate) null else onCheckForUpdate,
                )
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

    if (detailSheet != null && detailSheet != AccountDetailSheet.WITHDRAWALS) {
        AccountDetailBottomSheet(
            sheet = detailSheet!!,
            records = records,
            pointRecords = pointRecords,
            orders = orders,
            withdrawals = withdrawals,
            language = language,
            onDismiss = { detailSheet = null },
            onOpenWatchRecord = {
                detailSheet = null
                onOpenWatchRecord(it)
            },
        )
    }

    if (walletSheetVisible) {
        WalletBottomSheet(
            wallet = wallet,
            language = language,
            onDismiss = { walletSheetVisible = false },
            onBindWallet = onBindWallet,
            onUnbindWallet = { password -> pendingConfirmation = PendingAccountConfirmation.WalletUnbind(password) },
            isSubmitting = accountOperation != null,
        )
    }

    if (detailSheet == AccountDetailSheet.WITHDRAWALS) {
        WithdrawalBottomSheet(
            summary = withdrawalSummary,
            withdrawals = withdrawals,
            walletNetwork = wallet?.network,
            language = language,
            onDismiss = { detailSheet = null },
            onSubmitWithdrawal = { pendingConfirmation = PendingAccountConfirmation.Withdrawal(it) },
            isSubmitting = accountOperation != null,
        )
    }


    if (passwordSheetVisible) {
        PasswordBottomSheet(
            language = language,
            onDismiss = { passwordSheetVisible = false },
            onChangePassword = onChangePassword,
            isSubmitting = accountOperation != null,
        )
    }

    if (bankCardSheetVisible) {
        BankCardBottomSheet(
            language = language,
            isLoading = false,
            onSubmit = { holder, number, month, year, cvv ->
                onSubmitBankCard(holder, number, month, year, cvv)
            },
            onDismiss = { bankCardSheetVisible = false },
        )
    }

    if (vipSheetVisible) {
        VipBottomSheet(
            language = language,
            isVip = !vipUntil.isNullOrBlank(),
            vipUntil = vipUntil,
            vipPriceUsdt = vipPriceUsdt,
            vipCollectionAddress = vipCollectionAddress,
            latestVipOrder = latestVipOrder,
            onCreateOrder = {
                onCreateVipOrder()
            },
            onRefresh = onRefreshVipOrder,
            onDismiss = { vipSheetVisible = false },
        )
    }

    pendingConfirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { if (accountOperation == null) pendingConfirmation = null },
            title = { Text(accountConfirmationTitle(language)) },
            text = { Text(accountConfirmationBody(confirmation.summary(language), language)) },
            confirmButton = {
                TextButton(
                    enabled = accountOperation == null,
                    onClick = {
                        pendingConfirmation = null
                        when (confirmation) {
                            is PendingAccountConfirmation.WalletUnbind -> onUnbindWallet(confirmation.password)
                            is PendingAccountConfirmation.Withdrawal -> onSubmitWithdrawal(confirmation.points)
                        }
                    },
                ) { Text(accountConfirmationConfirm(language), color = DangerText) }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmation = null }, enabled = accountOperation == null) {
                    Text(accountConfirmationCancel(language))
                }
            },
            containerColor = Panel,
        )
    }
}

@Composable
private fun AccountHero(
    username: String,
    balance: Int,
    frozenPoints: Int,
    availablePoints: Int,
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
                        label = if (isLoggedIn) copy.accountFrozenAvailableLabel else copy.accountGuestRewardHint,
                        value = if (isLoggedIn) "$frozenPoints / $availablePoints" else copy.accountGuestPill,
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
    availablePoints: Int,
    records: List<WatchRecord>,
    pointRecords: List<PointRecord>,
    withdrawalSummary: WithdrawalSummary?,
    orders: List<RechargeOrderSummary>,
    vipOrders: List<VipOrder> = emptyList(),
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
                title = copy.accountWithdrawTitle,
                subtitle = if (isLoggedIn) {
                    "${availablePoints}${copy.accountPointsSuffix} · ${copy.accountWithdrawMinimumLabel} ${withdrawalSummary?.minimumPoints ?: 0}"
                } else {
                    copy.accountGuestSignInSubtitle
                },
                trailing = withdrawalSummary?.usdtPerPoint?.let { "USDT $it" }.orEmpty(),
                modifier = Modifier.weight(1f),
                onClick = if (isLoggedIn) {
                    { openSheetForAction(copy.accountWithdrawTitle) }
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
            .heightIn(min = 118.dp)
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
            .heightIn(min = 224.dp)
            .semantics {
                contentDescription =
                    "$title, ${copy.listEpisodePrefix}${record.episode}, ${progress}%"
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
                        "${copy.listEpisodePrefix}${record.episode}",
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
private fun VipOrderRow(order: VipOrder, language: AppLanguage) {
    val copy = strings(language)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${copy.vipPayAmount}${order.payableAmount ?: order.usdtAmount} USDT",
                color = PrimaryGold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(order.orderNo, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        val (statusColor, statusText) = when (order.status) {
            "CONFIRMED" -> SuccessText to copy.vipPayStatusConfirmed
            "REJECTED" -> DangerText to copy.vipPayStatusRejected
            else -> PrimaryGold to copy.vipPayStatusPending
        }
        Text(statusText, color = statusColor, style = MaterialTheme.typography.bodySmall)
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
                "${copy.listEpisodePrefix}${record.episode} · ${record.progressPercent}%",
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
    vipOrders: List<VipOrder> = emptyList(),
    withdrawals: List<WithdrawalRecord>,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onOpenWatchRecord: (WatchRecord) -> Unit,
) {
    val copy = strings(language)
    AccountFormBottomSheet(onDismiss = onDismiss) {
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
                    if (vipOrders.isEmpty()) {
                        item { AccountEmptyDetail(copy.accountOrdersReserved) }
                    } else {
                        items(vipOrders, key = { it.id }) { order ->
                            VipOrderRow(order, language)
                        }
                    }
                }
                AccountDetailSheet.WITHDRAWALS -> {
                    if (withdrawals.isEmpty()) {
                        item { AccountEmptyDetail(copy.accountWithdrawalsTitle) }
                    } else {
                        items(withdrawals, key = { it.id }) { WithdrawalRow(it, language) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WithdrawalRow(record: WithdrawalRecord, language: AppLanguage) {
    val copy = strings(language)
    val detail = withdrawalRecordDetail(record.status, record.adminNote, record.txHash, language)
    ListRow(
        title = "${record.pointAmount} ${copy.listPointsLabel} · ${record.usdtAmount} USDT",
        subtitle = listOfNotNull("${record.network} · ${record.walletAddress}", detail).joinToString("\n"),
        trailing = withdrawalStatusLabel(record.status, language),
        highlight = record.status == "APPROVED",
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletBottomSheet(
    wallet: WalletInfo?,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onBindWallet: (String, String, String) -> Unit,
    onUnbindWallet: (String) -> Unit,
    isSubmitting: Boolean,
) {
    val copy = strings(language)
    var walletAddress by remember(wallet) { mutableStateOf(wallet?.walletAddress.orEmpty()) }
    var selectedNetwork by remember(wallet) { mutableStateOf(wallet?.network ?: "ERC20") }
    var password by remember { mutableStateOf("") }
    AccountFormBottomSheet(onDismiss = onDismiss) {
        SheetForm(title = copy.accountWalletTitle) {
            Text(wallet?.walletAddress ?: copy.accountWalletNoBound, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            // 网络类型选择器（TRC20 / ERC20）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("TRC20", "ERC20").forEach { network ->
                    FilterChip(
                        selected = selectedNetwork == network,
                        onClick = { selectedNetwork = network },
                        label = { Text(network) },
                    )
                }
            }
            LoginTextField(walletAddress, { walletAddress = it.trim() }, "$selectedNetwork wallet address", enabled = true)
            LoginTextField(password, { password = it }, "Current password", enabled = true, isPassword = true)
            PrimaryWalletActionRow(
                primary = if (wallet?.walletAddress.isNullOrBlank()) copy.accountWalletBindAction else copy.accountWalletReplaceAction,
                primaryEnabled = walletAddress.isNotBlank() && password.isNotBlank() && !isSubmitting,
                onPrimary = {
                    onBindWallet(selectedNetwork, walletAddress, password)
                },
                secondary = copy.accountWalletUnbindAction,
                secondaryEnabled = !wallet?.walletAddress.isNullOrBlank() && password.isNotBlank() && !isSubmitting,
                onSecondary = { onUnbindWallet(password) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithdrawalBottomSheet(
    summary: WithdrawalSummary?,
    withdrawals: List<WithdrawalRecord>,
    walletNetwork: String?,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSubmitWithdrawal: (Int) -> Unit,
    isSubmitting: Boolean,
) {
    val copy = strings(language)
    var points by remember { mutableStateOf("") }
    val amount = points.toIntOrNull() ?: 0
    val minimumPoints = summary?.minimumPoints ?: Int.MAX_VALUE
    val availablePoints = summary?.availablePoints ?: 0
    AccountFormBottomSheet(onDismiss = onDismiss) {
        SheetForm(title = copy.accountWithdrawTitle) {
            withdrawalConversionLines(summary, amount, language).forEach { line ->
                Text(
                    line,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                summary?.walletAddress?.let { addr ->
                    val net = walletNetwork ?: "ERC20"
                    "$net · $addr"
                } ?: copy.accountWithdrawWalletRequired,
                color = if (summary?.walletAddress == null) DangerText else TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            LoginTextField(points, { points = it.filter(Char::isDigit) }, copy.accountWithdrawPointAmountLabel, enabled = true, kind = TextFieldKind.POINT_AMOUNT)
            PrimaryActionButton(
                text = copy.accountWithdrawSubmitAction,
                enabled = !isSubmitting && summary?.walletAddress != null && amount >= minimumPoints && amount <= availablePoints,
                onClick = {
                    onSubmitWithdrawal(amount)
                    if (commercialSheetAutoDismissesAfterSubmit()) {
                        onDismiss()
                    }
                },
            )
            withdrawals.take(3).forEach { WithdrawalRow(it, language) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordBottomSheet(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    isSubmitting: Boolean,
) {
    val copy = strings(language)
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    AccountFormBottomSheet(onDismiss = onDismiss) {
        SheetForm(title = copy.accountChangePasswordTitle) {
            LoginTextField(oldPassword, { oldPassword = it }, copy.authPasswordLabel, enabled = true, isPassword = true)
            LoginTextField(newPassword, { newPassword = it }, copy.accountPasswordNewLabel, enabled = true, isPassword = true)
            PrimaryActionButton(
                text = copy.accountChangePasswordTitle,
                enabled = !isSubmitting && oldPassword.isNotBlank() && newPassword.length >= 6,
                onClick = {
                    onChangePassword(oldPassword, newPassword)
                    if (commercialSheetAutoDismissesAfterSubmit()) {
                        onDismiss()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankCardBottomSheet(
    language: AppLanguage,
    isLoading: Boolean,
    onSubmit: (String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = strings(language)
    var holderName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var expiryMonth by remember { mutableStateOf("") }
    var expiryYear by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var faceStep by remember { mutableStateOf(false) }
    AccountFormBottomSheet(onDismiss = onDismiss) {
        SheetForm(title = copy.accountBankCardTitle) {
            if (!faceStep) {
                LabeledField(copy.accountBankCardHolderLabel, holderName, { holderName = it })
                Spacer(Modifier.height(8.dp))
                LabeledField(copy.accountBankCardNumberLabel, cardNumber, { cardNumber = it.filter { c -> c.isDigit() } })
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledField("MM", expiryMonth, { expiryMonth = it.filter { c -> c.isDigit() }.take(2) }, Modifier.weight(1f))
                    LabeledField("YY", expiryYear, { expiryYear = it.filter { c -> c.isDigit() }.take(2) }, Modifier.weight(1f))
                    LabeledField("CVV", cvv, { cvv = it.filter { c -> c.isDigit() }.take(4) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                GoldOutlinedButton(
                    copy.authContinue, true,
                    {
                        if (holderName.isNotBlank() && cardNumber.length >= 13 && expiryMonth.length == 2 && expiryYear.length == 2 && cvv.length >= 3) {
                            faceStep = true
                        }
                    },
                    Modifier.fillMaxWidth(), PrimaryGold,
                )
            } else {
                Text(
                    "Performing face verification...",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(color = PrimaryGold, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    "Verification failed",
                    color = DangerText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                GoldOutlinedButton(
                    "Retry", !isLoading,
                    {
                        onSubmit(holderName, cardNumber, expiryMonth, expiryYear, cvv)
                    },
                    Modifier.fillMaxWidth(), PrimaryGold,
                )
                Spacer(Modifier.height(8.dp))
                GoldOutlinedButton(copy.authClose, true, onDismiss, Modifier.fillMaxWidth(), PrimaryGold)
            }
        }
    }
}

private sealed interface PendingAccountConfirmation {
    data class WalletUnbind(val password: String) : PendingAccountConfirmation

    data class Withdrawal(val points: Int) : PendingAccountConfirmation
}

private fun PendingAccountConfirmation.summary(language: AppLanguage): String =
    when (this) {
        is PendingAccountConfirmation.WalletUnbind -> "unbind the wallet"
        is PendingAccountConfirmation.Withdrawal -> "withdraw $points points"
    }

@Composable
private fun SheetForm(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFormBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Panel,
        content = content,
    )
}

@Composable
private fun PrimaryWalletActionRow(
    primary: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondary: String,
    secondaryEnabled: Boolean,
    onSecondary: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GoldOutlinedButton(primary, primaryEnabled, onPrimary, Modifier.weight(1f), PrimaryGold)
        GoldOutlinedButton(secondary, secondaryEnabled, onSecondary, Modifier.weight(1f), DangerText)
    }
}

@Composable
private fun GuestActionPanel(
    language: AppLanguage,
    onShowAuthPrompt: () -> Unit,
    onShowRegisterAuthPrompt: () -> Unit,
) {
    val copy = strings(language)
    val actions = guestAccountEntryLabels(language).zip(guestAccountEntryAuthModes())
    AccountMenuGroup {
        actions.forEachIndexed { index, (label, mode) ->
            AccountMenuRow(
                icon = if (index == 0) Icons.Rounded.AccountCircle else Icons.Rounded.PlayArrow,
                title = label,
                subtitle = if (index == 0) copy.accountGuestSignInSubtitle else copy.accountGuestRegisterSubtitle,
                onClick = if (mode == AuthMode.REGISTER) onShowRegisterAuthPrompt else onShowAuthPrompt,
            )
            if (index < actions.lastIndex) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VipBottomSheet(
    language: AppLanguage,
    isVip: Boolean,
    vipUntil: String?,
    vipPriceUsdt: String,
    vipCollectionAddress: String,
    latestVipOrder: VipOrder?,
    onCreateOrder: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = strings(language)
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pendingOrder = if (!isVip && latestVipOrder?.status == "PENDING") latestVipOrder else null
    var remainingSeconds by remember(pendingOrder?.expiresAt) {
        mutableStateOf(computeRemaining(pendingOrder?.expiresAt))
    }
    LaunchedEffect(pendingOrder?.expiresAt) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
    }
    // L2: 用 rememberUpdatedState 捕获最新值，订单 CONFIRMED/EXPIRED 后停止轮询
    val currentPendingOrder by rememberUpdatedState(pendingOrder)
    val currentRemaining by rememberUpdatedState(remainingSeconds)
    // Auto-refresh order status every 10 seconds while sheet is open and order is pending
    LaunchedEffect(pendingOrder?.id) {
        while (currentPendingOrder != null && currentRemaining > 0) {
            delay(10_000)
            if (currentPendingOrder == null || currentRemaining <= 0) break
            onRefresh()
        }
    }
    val orderExpired = !isVip && latestVipOrder?.status == "EXPIRED"
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Panel) {
        Column(
            modifier = Modifier
                .padding(start = 18.dp, end = 18.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (isVip) copy.vipDialogTitle else copy.vipPayTitle,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
            if (isVip) {
                // State 1: Already VIP — show status and expiry
                Text("${copy.vipActiveLabel}", color = SuccessText, style = MaterialTheme.typography.bodyLarge)
                vipUntil?.let {
                    Text("${copy.vipExpiryLabel}$it", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                latestVipOrder?.let { order ->
                    Spacer(Modifier.height(4.dp))
                    Text("${copy.vipPayOrderNo}${order.orderNo}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("${copy.vipPayAmount}${order.usdtAmount} USDT", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        when (order.status) {
                            "CONFIRMED" -> copy.vipPayStatusConfirmed
                            "REJECTED" -> copy.vipPayStatusRejected
                            else -> copy.vipPayStatusPending
                        },
                        color = if (order.status == "CONFIRMED") SuccessText else TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else if (latestVipOrder != null && latestVipOrder.status == "PENDING") {
                // State 2: Non-VIP with pending order — show payment info
                val paymentAddress = latestVipOrder.receivingWalletAddress ?: vipCollectionAddress
                Text("${copy.vipPayAmount}${latestVipOrder.payableAmount ?: latestVipOrder.usdtAmount} USDT", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
                Text("${copy.vipPayOrderNo}${latestVipOrder.orderNo}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(copy.vipPayAddress, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    paymentAddress.ifBlank { "N/A" },
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (latestVipOrder.expiresAt != null) {
                    if (remainingSeconds > 0) {
                        Text(
                            "${copy.vipPayExpiresIn} ${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')}",
                            color = PrimaryGold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(copy.vipPayExpired, color = DangerText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (paymentAddress.isNotBlank()) {
                    AsyncImage(
                        model = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=$paymentAddress",
                        contentDescription = copy.vipPayQrHint,
                        modifier = Modifier.size(200.dp),
                    )
                    GoldOutlinedButton(copy.vipPayCopy, true, {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(paymentAddress))
                    }, Modifier.fillMaxWidth(), PrimaryGold)
                }
                Spacer(Modifier.height(4.dp))
                Text(copy.vipPayHint, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text("${copy.vipPayStatusPending}", color = PrimaryGold, style = MaterialTheme.typography.bodyMedium)
                if (remainingSeconds <= 0 && latestVipOrder.expiresAt != null) {
                    GoldOutlinedButton(copy.accountVipUnlockAction, true, onCreateOrder, Modifier.fillMaxWidth(), PrimaryGold)
                } else {
                    GoldOutlinedButton(
                        "Refresh status",
                        true, onRefresh, Modifier.fillMaxWidth(), PrimaryGold,
                    )
                }
            } else {
                // State 3: Non-VIP, no pending order (or expired) — create order
                if (orderExpired) {
                    Text(copy.vipPayExpired, color = DangerText, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "${vipPriceUsdt.ifBlank { "" }}${copy.accountVipPriceSuffix}".trim(),
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (vipCollectionAddress.isNotBlank()) {
                    Text(copy.vipPayAddress, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(vipCollectionAddress, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    AsyncImage(
                        model = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=$vipCollectionAddress",
                        contentDescription = copy.vipPayQrHint,
                        modifier = Modifier.size(200.dp),
                    )
                }
                latestVipOrder?.let { order ->
                    Text("${copy.vipPayOrderNo}${order.orderNo}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        when (order.status) {
                            "CONFIRMED" -> copy.vipPayStatusConfirmed
                            "REJECTED" -> copy.vipPayStatusRejected
                            else -> copy.vipPayStatusPending
                        },
                        color = if (order.status == "REJECTED") DangerText else TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                GoldOutlinedButton(copy.accountVipUnlockAction, true, onCreateOrder, Modifier.fillMaxWidth(), PrimaryGold)
            }
            GoldOutlinedButton(copy.authClose, true, onDismiss, Modifier.fillMaxWidth(), PrimaryGold)
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun computeRemaining(expiresAt: String?): Int {
    if (expiresAt == null) return 0
    return try {
        val expiry = java.time.OffsetDateTime.parse(expiresAt)
        val now = java.time.OffsetDateTime.now()
        val diff = java.time.Duration.between(now, expiry).seconds.toInt()
        maxOf(0, diff)
    } catch (_: Exception) {
        0
    }
}
