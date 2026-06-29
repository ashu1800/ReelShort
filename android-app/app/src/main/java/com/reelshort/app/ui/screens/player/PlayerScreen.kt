package com.reelshort.app.ui.screens.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.reelshort.app.state.AppUiState
import com.reelshort.app.state.PlaybackStatus
import com.reelshort.app.state.nextUnreportedRewardStage
import com.reelshort.app.ui.components.GoldOutlinedButton
import com.reelshort.app.ui.components.SectionHeader
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.components.PlayerGradient
import com.reelshort.app.ui.components.verticalGradient
import com.reelshort.app.ui.format.RewardBadgeState
import com.reelshort.app.ui.format.episodeSubtitle
import com.reelshort.app.ui.format.episodeTitle
import com.reelshort.app.ui.format.mediaDurationSeconds
import com.reelshort.app.ui.format.mediaPositionSeconds
import com.reelshort.app.ui.format.playableMediaUrlOrNull
import com.reelshort.app.ui.format.playerSecondaryActionLabels
import com.reelshort.app.ui.format.playerStartsAutomatically
import com.reelshort.app.ui.format.playerSurfaceAspectRatio
import com.reelshort.app.ui.format.rewardBadgeState
import com.reelshort.app.ui.theme.DangerText
import com.reelshort.app.ui.theme.Divider
import com.reelshort.app.ui.theme.GoldStroke
import com.reelshort.app.ui.theme.OnPrimaryDark
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
internal fun PlayerScreen(
    state: AppUiState,
    onUpdatePlaybackPosition: (Int, Int) -> Unit,
    onRefreshPlaybackUrl: () -> Unit,
    onAutoReportProgress: (Int, Int) -> Unit,
) {
    val playback = state.playback
    val book = playback.book ?: state.selectedBook
    val episode = playback.episode ?: state.selectedEpisode
    val videoUrl = playback.videoUrl?.url
    val ready = playback.status == PlaybackStatus.READY && episode != null && videoUrl != null
    val duration = playback.durationSeconds.takeIf { it > 0 } ?: episode?.durationSeconds ?: 0
    val badgeState = rewardBadgeState(
        progressPercent = playback.progressPercent,
        lastReportedProgressPercent = playback.lastReportedProgressPercent,
        isReporting = playback.isRewardReporting,
        hasError = playback.rewardReportError,
    )

    LaunchedEffect(
        playback.positionSeconds,
        playback.durationSeconds,
        playback.progressPercent,
        playback.lastReportedProgressPercent,
    ) {
        if (
            playback.status == PlaybackStatus.READY &&
            !playback.isRewardReporting &&
            playback.positionSeconds > 0 &&
            playback.durationSeconds > 0 &&
            nextUnreportedRewardStage(playback.progressPercent, playback.lastReportedProgressPercent) != null
        ) {
            onAutoReportProgress(playback.positionSeconds, playback.durationSeconds)
        }
    }

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            MediaPlayerSurface(
                videoUrl = videoUrl,
                episodeNumber = episode?.number,
                fallbackDurationSeconds = duration,
                initialPositionSeconds = playback.positionSeconds,
                rewardBadgeState = badgeState,
                onProgress = onUpdatePlaybackPosition,
            )
        }
        item {
            SectionHeader(
                book?.title ?: "未选择剧集",
                "${episode?.let { episodeTitle(it) } ?: "分集"} · 进度 ${playback.progressPercent}%",
            )
        }
        item {
            SurfacePanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(episode?.let { episodeTitle(it) } ?: "当前分集", fontWeight = FontWeight.SemiBold)
                    val description = episode?.let { episodeSubtitle(it.description, book?.description.orEmpty()) }.orEmpty()
                    if (description.isNotBlank()) {
                        Text(description, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Text("已领取 ${playback.lastReportedProgressPercent}%", color = TextSecondary)
                    if (playback.rewardReportError) {
                        Text("积分同步失败，继续播放时会自动重试。", color = DangerText)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        playerSecondaryActionLabels().forEach { label ->
                            GoldOutlinedButton(
                                text = label,
                                enabled = ready,
                                onClick = onRefreshPlaybackUrl,
                                contentColor = TextPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPlayerSurface(
    videoUrl: String?,
    episodeNumber: Int?,
    fallbackDurationSeconds: Int,
    initialPositionSeconds: Int,
    rewardBadgeState: RewardBadgeState,
    onProgress: (positionSeconds: Int, durationSeconds: Int) -> Unit,
) {
    val playableUrl = videoUrl.playableMediaUrlOrNull()

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp)
                .aspectRatio(playerSurfaceAspectRatio())
                .clip(MaterialTheme.shapes.large)
                .background(verticalGradient(PlayerGradient))
                .border(1.dp, Divider, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            if (playableUrl == null) {
                PlayerPlaceholder(episodeNumber)
            } else {
                val context = LocalContext.current
                val player = remember(playableUrl) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(playableUrl))
                        playWhenReady = playerStartsAutomatically()
                        prepare()
                    }
                }
                DisposableEffect(player) {
                    onDispose { player.release() }
                }
                LaunchedEffect(player) {
                    if (initialPositionSeconds > 0) {
                        player.seekTo(initialPositionSeconds * 1_000L)
                    }
                }
                LaunchedEffect(player, fallbackDurationSeconds) {
                    while (true) {
                        val durationSeconds = mediaDurationSeconds(player.duration, fallbackDurationSeconds)
                        if (durationSeconds > 0) {
                            onProgress(mediaPositionSeconds(player.currentPosition), durationSeconds)
                        }
                        delay(1_000)
                    }
                }
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = player
                        }
                    },
                    update = { view ->
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        view.player = player
                    },
                    onRelease = { view -> view.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            RewardProgressBadge(
                state = rewardBadgeState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun PlayerPlaceholder(episodeNumber: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("等待播放地址", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
        Text("第 ${episodeNumber ?: 0} 集", color = TextSecondary)
    }
}

@Composable
private fun RewardProgressBadge(state: RewardBadgeState, modifier: Modifier = Modifier) {
    val ringColor: Color
    val backgroundColor: Color
    val textColor: Color
    when (state.visualState) {
        com.reelshort.app.ui.format.RewardBadgeVisualState.WAITING -> {
            ringColor = PrimaryGold; backgroundColor = Color(0xCC080A0F); textColor = TextSecondary
        }
        com.reelshort.app.ui.format.RewardBadgeVisualState.READY -> {
            ringColor = PrimaryGold; backgroundColor = Color(0xE611151E); textColor = PrimaryGold
        }
        com.reelshort.app.ui.format.RewardBadgeVisualState.REPORTING -> {
            ringColor = PrimaryGold; backgroundColor = Color(0xE611151E); textColor = PrimaryGold
        }
        com.reelshort.app.ui.format.RewardBadgeVisualState.COMPLETED -> {
            ringColor = PrimaryGold; backgroundColor = Color(0xE60F1410); textColor = PrimaryGold
        }
        com.reelshort.app.ui.format.RewardBadgeVisualState.ERROR -> {
            ringColor = DangerText; backgroundColor = Color(0xE63A1417); textColor = DangerText
        }
    }
    Surface(
        modifier = modifier.size(52.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, GoldStroke),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { state.ringProgress },
                color = ringColor,
                strokeWidth = 3.dp,
                modifier = Modifier.fillMaxSize().padding(3.dp),
            )
            Text(
                state.displayText,
                color = textColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
