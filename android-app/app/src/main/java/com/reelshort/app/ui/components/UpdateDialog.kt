package com.reelshort.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.update.UpdateState
import com.reelshort.app.ui.format.failureMessage
import com.reelshort.app.ui.format.formatUpdateBytes
import com.reelshort.app.ui.format.releaseOrNull
import com.reelshort.app.ui.format.shouldShowUpdateDialog
import com.reelshort.app.ui.format.updateProgressPercent
import com.reelshort.app.ui.format.updateStrings
import com.reelshort.app.ui.theme.Panel
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary
import com.reelshort.app.ui.theme.TranslucentWhiteSurface

@Composable
internal fun UpdateDialog(
    state: UpdateState,
    language: AppLanguage,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!shouldShowUpdateDialog(state)) return
    val copy = updateStrings(language)
    val release = state.releaseOrNull() ?: return
    val busy = state is UpdateState.Downloading || state is UpdateState.Verifying
    val title = when (state) {
        is UpdateState.Available -> copy.availableTitle
        is UpdateState.Downloading -> copy.downloading
        is UpdateState.Verifying -> copy.verifying
        is UpdateState.PermissionRequired -> copy.permissionTitle
        is UpdateState.ReadyToInstall -> copy.readyTitle
        is UpdateState.Failed -> copy.checkFailed
        else -> copy.availableTitle
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Panel,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${release.title} · ${release.tagName}", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
                when (state) {
                    is UpdateState.Available -> {
                        Text("${copy.publishedLabel}: ${release.publishedAt}", color = TextSecondary)
                        Text("${copy.sizeLabel}: ${formatUpdateBytes(release.apkAsset.sizeBytes)}", color = TextSecondary)
                        if (release.body.isNotBlank()) {
                            Text(copy.releaseNotes, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(release.body, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is UpdateState.Downloading -> DownloadProgressContent(state, copy.downloading)
                    is UpdateState.Verifying -> {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
                            color = PrimaryGold,
                            trackColor = TranslucentWhiteSurface,
                        )
                        Text(copy.verifying, color = TextSecondary)
                    }
                    is UpdateState.PermissionRequired -> Text(copy.permissionBody, color = TextSecondary)
                    is UpdateState.ReadyToInstall -> Text(copy.readyBody, color = TextSecondary)
                    is UpdateState.Failed -> Text(copy.failureMessage(state.reason), color = TextSecondary)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> UpdateTextButton(copy.updateNow, onDownload)
                is UpdateState.PermissionRequired -> UpdateTextButton(copy.openSettings, onOpenSettings)
                is UpdateState.ReadyToInstall -> UpdateTextButton(copy.install, onInstall)
                is UpdateState.Failed -> UpdateTextButton(copy.retry, onDownload)
                else -> Unit
            }
        },
        dismissButton = {
            when (state) {
                is UpdateState.Downloading -> UpdateTextButton(copy.cancel, onCancelDownload)
                is UpdateState.Verifying -> Unit
                else -> UpdateTextButton(copy.later, onDismiss)
            }
        },
    )
}

@Composable
private fun DownloadProgressContent(state: UpdateState.Downloading, label: String) {
    val percent = updateProgressPercent(state.progress)
    if (percent == null) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
            color = PrimaryGold,
            trackColor = TranslucentWhiteSurface,
        )
    } else {
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
            color = PrimaryGold,
            trackColor = TranslucentWhiteSurface,
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        val amount = state.progress.totalBytes?.let {
            "${formatUpdateBytes(state.progress.downloadedBytes)} / ${formatUpdateBytes(it)}"
        } ?: formatUpdateBytes(state.progress.downloadedBytes)
        Text(
            if (percent == null) amount else "$percent% · $amount",
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UpdateTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(text, color = PrimaryGold)
    }
}
