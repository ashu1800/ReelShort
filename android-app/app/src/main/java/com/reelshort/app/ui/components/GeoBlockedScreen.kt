package com.reelshort.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelshort.app.ui.theme.AppBackgroundColor
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary

/**
 * Full-screen geo-block page shown when the app detects a mainland China IP. No navigation,
 * no bypass — the user can only exit the app.
 */
@Composable
fun GeoBlockedScreen(title: String, message: String, onExit: () -> Unit) {
    BackHandler { onExit() }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = PrimaryGold,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        }
    }
}
