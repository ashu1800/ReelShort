package com.reelshort.app.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.ModeComment
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.Comment
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.state.AppUiState
import com.reelshort.app.ui.format.EpisodeWatchStatus
import com.reelshort.app.ui.format.EpisodeWatchStatusType
import com.reelshort.app.ui.format.PlayerOverlayMode
import com.reelshort.app.ui.format.RewardBadgeState
import com.reelshort.app.ui.format.RewardBadgeVisualState
import com.reelshort.app.ui.format.coverUrlOrNull
import com.reelshort.app.ui.format.episodeNumberLabel
import com.reelshort.app.ui.format.episodeSelectorLabel
import com.reelshort.app.ui.format.episodeWatchStatus
import com.reelshort.app.ui.format.episodeWatchStatusLabel
import com.reelshort.app.ui.format.playerErrorNextEpisode
import com.reelshort.app.ui.format.playerLoadingLabel
import com.reelshort.app.ui.format.playerOverlayMode
import com.reelshort.app.ui.format.rewardBadgeContentDescription
import com.reelshort.app.ui.format.rewardBadgeIncludesProgressRing
import com.reelshort.app.ui.format.rewardBadgeInfoBody
import com.reelshort.app.ui.format.rewardBadgeInfoTitle
import com.reelshort.app.ui.format.rewardBadgeAwardLabel
import com.reelshort.app.ui.format.rewardBadgeState
import com.reelshort.app.ui.format.playerStartsAutomatically
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.theme.DangerText
import com.reelshort.app.ui.theme.OnPrimaryDark
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary

@Composable
@UnstableApi
internal fun PlayerScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onUpdatePlaybackPosition: (Int, Int) -> Unit,
    onAutoReportProgress: (Int, Int) -> Unit,
    onToggleLike: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenPlayer: (EpisodeSummary) -> Unit,
    onSubmitComment: (String) -> Unit,
) {
    val playback = state.playback
    val book = playback.book
    val episode = playback.episode
    val playableUrl = playback.videoUrl?.url
    val copy = strings(state.language)
    var commentSheetVisible by remember { mutableStateOf(false) }
    var episodeSheetVisible by remember { mutableStateOf(false) }
    var rewardInfoSheetVisible by remember { mutableStateOf(false) }
    var visibleAwardPoints by remember { mutableStateOf<Int?>(null) }
    var lastRewardAwardVersion by remember(book?.id, episode?.number) {
        mutableStateOf(playback.rewardAwardVersion)
    }

    LaunchedEffect(playback.rewardAwardVersion, state.language) {
        if (playback.rewardAwardVersion > lastRewardAwardVersion && playback.awardedPoints > 0) {
            visibleAwardPoints = playback.awardedPoints
            kotlinx.coroutines.delay(1100)
            if (visibleAwardPoints == playback.awardedPoints) {
                visibleAwardPoints = null
            }
        }
        lastRewardAwardVersion = playback.rewardAwardVersion
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 视频层
        MediaPlayerSurface(
            playableUrl = playableUrl,
            coverUrl = book?.coverUrl,
            currentEpisode = episode,
            episodes = state.episodes,
            episodeNumber = episode?.number ?: 1,
            language = state.language,
            initialPositionSeconds = playback.positionSeconds,
            fallbackDurationSeconds = playback.durationSeconds,
            onProgress = onUpdatePlaybackPosition,
            onAutoReportProgress = onAutoReportProgress,
            onRetryPlayback = {
                if (episode != null) {
                    onOpenPlayer(episode)
                }
            },
            onOpenNextEpisode = onOpenPlayer,
            onBack = onBack,
        )

        // 顶部状态栏占位 + 返回键 + 奖励进度
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CircleIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = copy.playerScreen, onClick = onBack)
            val badgeState = rewardBadgeState(
                progressPercent = playback.progressPercent,
                isReporting = playback.isRewardReporting,
                hasError = playback.rewardReportError,
                language = state.language,
                rewardClaimed = playback.rewardClaimed,
                rewardStatus = playback.rewardStatus,
            )
            if (badgeState.visualState != RewardBadgeVisualState.WAITING || playback.progressPercent > 0) {
                Box(contentAlignment = Alignment.TopEnd) {
                    RewardBadgeChip(
                        state = badgeState,
                        language = state.language,
                        onClick = { rewardInfoSheetVisible = true },
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        visible = visibleAwardPoints != null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { -it / 2 },
                        modifier = Modifier.padding(top = 42.dp, end = 4.dp),
                    ) {
                        RewardAwardToast(points = visibleAwardPoints ?: 0, language = state.language)
                    }
                }
            }
        }

        // 右侧竖排动作栏
        ActionRail(
            liked = state.interaction.liked,
            likeCount = state.interaction.likeCount,
            favorited = state.interaction.favorited,
            commentCount = state.comments.size,
            language = state.language,
            onToggleLike = onToggleLike,
            onToggleFavorite = onToggleFavorite,
            onOpenComments = { commentSheetVisible = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .navigationBarsPadding(),
        )

        // 底部信息
        BottomInfo(
            bookTitle = book?.title.orEmpty(),
            episodeTitle = episode?.title.orEmpty(),
            episodeDescription = (episode?.description?.ifBlank { null } ?: book?.description).orEmpty(),
            episodes = state.episodes,
            selectedEpisode = state.selectedEpisode,
            language = state.language,
            progressPercent = playback.progressPercent.coerceIn(0, 100),
            onOpenEpisodeSheet = { episodeSheetVisible = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding(),
        )

        // 错误提示
        playback.rewardReportError.let { err ->
            if (err) {
                Surface(
                    color = Color(0xE63A1417),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp),
                ) {
                    Text(
                        copy.playerRewardError,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = DangerText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (commentSheetVisible) {
        CommentBottomSheet(
            comments = state.comments,
            language = state.language,
            onDismiss = { commentSheetVisible = false },
            onSubmit = { content ->
                onSubmitComment(content)
            },
        )
    }

    if (episodeSheetVisible) {
        EpisodeSelectorBottomSheet(
            bookId = book?.id,
            episodes = state.episodes,
            selectedEpisode = state.selectedEpisode,
            watchHistory = state.watchHistory,
            language = state.language,
            onDismiss = { episodeSheetVisible = false },
            onSelectEpisode = { selected ->
                episodeSheetVisible = false
                if (selected.number != state.selectedEpisode?.number || selected.chapterId != state.selectedEpisode?.chapterId) {
                    onOpenPlayer(selected)
                }
            },
        )
    }

    if (rewardInfoSheetVisible) {
        RewardInfoBottomSheet(
            language = state.language,
            onDismiss = { rewardInfoSheetVisible = false },
        )
    }
}

@Composable
@UnstableApi
private fun BoxScope.MediaPlayerSurface(
    playableUrl: String?,
    coverUrl: String?,
    currentEpisode: EpisodeSummary?,
    episodes: List<EpisodeSummary>,
    episodeNumber: Int,
    language: AppLanguage,
    initialPositionSeconds: Int,
    fallbackDurationSeconds: Int,
    onProgress: (Int, Int) -> Unit,
    onAutoReportProgress: (Int, Int) -> Unit,
    onRetryPlayback: () -> Unit,
    onOpenNextEpisode: (EpisodeSummary) -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .align(Alignment.Center),
        contentAlignment = Alignment.Center,
    ) {
        if (playableUrl == null) {
            PlayerCoverLoadingOverlay(
                coverUrl = coverUrl,
                label = playerLoadingLabel(episodeNumber, language),
            )
            return
        }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var playbackState by remember(playableUrl) { mutableStateOf(Player.STATE_IDLE) }
        var hasFirstReady by remember(playableUrl) { mutableStateOf(false) }
        var playerError by remember(playableUrl) { mutableStateOf(false) }
        val player = remember(playableUrl) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(playableUrl))
                playWhenReady = playerStartsAutomatically()
                prepare()
            }
        }
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(newPlaybackState: Int) {
                    playbackState = newPlaybackState
                    if (newPlaybackState == Player.STATE_READY) {
                        hasFirstReady = true
                        playerError = false
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    playerError = true
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
        LaunchedEffect(player) {
            if (initialPositionSeconds > 0) {
                player.seekTo(initialPositionSeconds * 1000L)
            }
        }
        DisposableEffect(player) {
            onDispose { player.release() }
        }
        DisposableEffect(player, lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> player.pause()
                    Lifecycle.Event.ON_RESUME -> if (playerStartsAutomatically()) player.play()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(player, fallbackDurationSeconds) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val positionMs = player.currentPosition
                val durationMs = player.duration.takeIf { it > 0 } ?: fallbackDurationSeconds * 1000L
                if (durationMs <= 0) continue
                val positionSeconds = (positionMs / 1000).toInt()
                val durationSeconds = (durationMs / 1000).toInt()
                onProgress(positionSeconds, durationSeconds)
                onAutoReportProgress(positionSeconds, durationSeconds)
            }
        }
        val overlayMode = playerOverlayMode(
            playableUrl = playableUrl,
            playbackState = playbackState,
            hasFirstReady = hasFirstReady,
            hasError = playerError,
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
            update = { view ->
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                view.player = player
            },
        )
        AnimatedVisibility(
            visible = overlayMode != PlayerOverlayMode.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            when (overlayMode) {
                PlayerOverlayMode.COVER_LOADING -> PlayerCoverLoadingOverlay(
                    coverUrl = coverUrl,
                    label = playerLoadingLabel(episodeNumber, language),
                )
                PlayerOverlayMode.BUFFERING -> PlayerBufferingOverlay(
                    label = playerLoadingLabel(episodeNumber, language),
                )
                PlayerOverlayMode.ERROR -> PlayerErrorOverlay(
                    language = language,
                    nextEpisode = playerErrorNextEpisode(currentEpisode, episodes),
                    onRetry = onRetryPlayback,
                    onNext = onOpenNextEpisode,
                    onBack = onBack,
                )
                PlayerOverlayMode.NONE -> Unit
            }
        }
    }
}

@Composable
private fun PlayerCoverLoadingOverlay(coverUrl: String?, label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val normalizedCoverUrl = coverUrl.coverUrlOrNull()
        if (normalizedCoverUrl != null) {
            val context = LocalContext.current
            AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(normalizedCoverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF2B2412),
                            0.52f to Color(0xFF10141D),
                            1f to Color.Black,
                        ),
                    ),
            )
        }
        Box(modifier = Modifier.matchParentSize().background(Color(0xB3000000)))
        PlayerPlaceholder(label)
    }
}

@Composable
private fun PlayerBufferingOverlay(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = Color(0xD611151E),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.46f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = PrimaryGold, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text(label, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    language: AppLanguage,
    nextEpisode: EpisodeSummary?,
    onRetry: () -> Unit,
    onNext: (EpisodeSummary) -> Unit,
    onBack: () -> Unit,
) {
    val copy = strings(language)
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xA6000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xE611151E),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier.padding(horizontal = 36.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    copy.playerVideoError,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayerRecoveryButton(
                        label = copy.playerRetryAction,
                        icon = Icons.Rounded.PlayArrow,
                        emphasized = true,
                        onClick = onRetry,
                    )
                    if (nextEpisode != null) {
                        PlayerRecoveryButton(
                            label = copy.playerNextEpisodeAction,
                            icon = Icons.Rounded.SkipNext,
                            emphasized = false,
                            onClick = { onNext(nextEpisode) },
                        )
                    }
                }
                PlayerRecoveryTextButton(label = copy.playerBackAction, onClick = onBack)
            }
        }
    }
}

@Composable
private fun PlayerRecoveryButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val background = if (emphasized) PrimaryGold else Color(0xFF252C3A)
    val foreground = if (emphasized) OnPrimaryDark else TextPrimary
    Surface(
        color = background,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.height(48.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
            Text(
                label,
                color = foreground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerRecoveryTextButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PlayerPlaceholder(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = PrimaryGold, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x66000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RewardBadgeChip(
    state: RewardBadgeState,
    language: AppLanguage,
    onClick: () -> Unit,
) {
    val ringColor = if (state.visualState == RewardBadgeVisualState.ERROR) DangerText else PrimaryGold
    Surface(
        color = Color(0xE611151E),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ringColor),
        modifier = Modifier
            .height(36.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Rounded.MonetizationOn,
                contentDescription = rewardBadgeContentDescription(state, language),
                tint = ringColor,
                modifier = Modifier.size(17.dp),
            )
            Text(
                state.displayText,
                color = ringColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (rewardBadgeIncludesProgressRing(state)) {
                CircularProgressIndicator(
                    progress = { state.ringProgress },
                    color = ringColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun RewardAwardToast(points: Int, language: AppLanguage) {
    Surface(
        color = Color(0xF0212510),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Rounded.MonetizationOn, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(16.dp))
            Text(
                rewardBadgeAwardLabel(points, language),
                color = PrimaryGold,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewardInfoBottomSheet(language: AppLanguage, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF11151E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MonetizationOn, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(22.dp))
                    Text(
                        rewardBadgeInfoTitle(language),
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = strings(language).playerClose,
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp).clickable(onClick = onDismiss),
                )
            }
            Text(
                rewardBadgeInfoBody(language),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun ActionRail(
    liked: Boolean,
    likeCount: Int,
    favorited: Boolean,
    commentCount: Int,
    language: AppLanguage,
    onToggleLike: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        RailAction(
            icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            tint = if (liked) Color(0xFFFF5C7A) else Color.White,
            label = formatCount(likeCount),
            onClick = onToggleLike,
        )
        RailAction(
            icon = Icons.Rounded.ModeComment,
            tint = Color.White,
            label = formatCount(commentCount),
            onClick = onOpenComments,
        )
        RailAction(
            icon = if (favorited) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            tint = if (favorited) PrimaryGold else Color.White,
            label = strings(language).playerFavoriteLabel,
            onClick = onToggleFavorite,
        )
    }
}

@Composable
private fun RailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(30.dp))
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatCount(count: Int): String = when {
    count >= 10000 -> "${count / 10000}w"
    count > 0 -> count.toString()
    else -> ""
}

@Composable
private fun BottomInfo(
    bookTitle: String,
    episodeTitle: String,
    episodeDescription: String,
    episodes: List<EpisodeSummary>,
    selectedEpisode: EpisodeSummary?,
    language: AppLanguage,
    progressPercent: Int,
    onOpenEpisodeSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xE6000000),
                ),
            )
            .padding(start = 16.dp, end = 84.dp, bottom = 16.dp, top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            bookTitle,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (episodeTitle.isNotBlank()) {
            Text(
                episodeTitle,
                color = PrimaryGold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (episodeDescription.isNotBlank()) {
            Text(
                episodeDescription,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        // 进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x44FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressPercent / 100f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PrimaryGold),
            )
        }
        if (episodes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            EpisodeSelectorBar(
                label = episodeSelectorLabel(episodes.size, language),
                selectedEpisode = selectedEpisode,
                language = language,
                onClick = onOpenEpisodeSheet,
            )
        }
    }
}

@Composable
private fun EpisodeSelectorBar(
    label: String,
    selectedEpisode: EpisodeSummary?,
    language: AppLanguage,
    onClick: () -> Unit,
) {
    Surface(
        color = Color(0xE61A1A1A),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selectedEpisode != null) {
                    Text(
                        "${strings(language).playerCurrentEpisodePrefix}${episodeNumberLabel(selectedEpisode.number, language)}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = strings(language).playerExpandEpisodes, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeSelectorBottomSheet(
    bookId: String?,
    episodes: List<EpisodeSummary>,
    selectedEpisode: EpisodeSummary?,
    watchHistory: List<WatchRecord>,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSelectEpisode: (EpisodeSummary) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF11151E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(episodeSelectorLabel(episodes.size, language), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = strings(language).playerClose,
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp).clickable(onClick = onDismiss),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                modifier = Modifier.fillMaxWidth().height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(episodes, key = { "${it.chapterId}:${it.number}" }) { episode ->
                    val selected = episode.chapterId == selectedEpisode?.chapterId && episode.number == selectedEpisode.number
                    val status = episodeWatchStatus(
                        bookId = bookId,
                        episode = episode,
                        selectedEpisode = selectedEpisode,
                        watchHistory = watchHistory,
                    )
                    EpisodeSelectorItem(
                        episode = episode,
                        selected = selected,
                        status = status,
                        language = language,
                        onClick = { onSelectEpisode(episode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeSelectorItem(
    episode: EpisodeSummary,
    selected: Boolean,
    status: EpisodeWatchStatus,
    language: AppLanguage,
    onClick: () -> Unit,
) {
    val statusLabel = episodeWatchStatusLabel(status, language)
    val background = when {
        selected -> PrimaryGold
        status.type == EpisodeWatchStatusType.WATCHED -> Color(0xFF20202A)
        else -> Color(0xFF1B202B)
    }
    val foreground = if (selected) OnPrimaryDark else TextPrimary
    val secondary = if (selected) OnPrimaryDark.copy(alpha = 0.72f) else TextSecondary
    val progress = status.progressPercent.coerceIn(0, 100)
    val border = if (status.type == EpisodeWatchStatusType.WATCHED && !selected) {
        androidx.compose.foundation.BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.58f))
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF))
    }
    Surface(
        modifier = Modifier
            .height(64.dp)
            .clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(10.dp),
        border = border,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    episode.number.coerceAtLeast(0).toString(),
                    color = foreground,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (statusLabel.isNotBlank()) {
                    Text(
                        statusLabel,
                        color = secondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (progress > 0 && !selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress / 100f)
                        .height(3.dp)
                        .background(PrimaryGold.copy(alpha = if (status.type == EpisodeWatchStatusType.WATCHED) 0.9f else 0.68f)),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentBottomSheet(
    comments: List<Comment>,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF11151E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${strings(language).playerCommentsTitlePrefix}${comments.size}", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = strings(language).playerClose,
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp).clickable(onClick = onDismiss),
                )
            }
            if (comments.isEmpty()) {
                Text(
                    strings(language).playerCommentsEmpty,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 28.dp).align(Alignment.CenterHorizontally),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(comments, key = { it.id }) { comment -> CommentItem(comment) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(strings(language).playerCommentPlaceholder, color = TextSecondary, style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF171C27),
                        unfocusedContainerColor = Color(0xFF171C27),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (draft.isBlank()) Color(0xFF252C3A) else PrimaryGold)
                        .clickable(enabled = draft.isNotBlank()) {
                            onSubmit(draft)
                            draft = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = strings(language).playerSend, tint = if (draft.isBlank()) TextSecondary else OnPrimaryDark, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CommentItem(comment: Comment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(PrimaryGold.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(comment.username.firstOrNull()?.uppercase() ?: "?", color = PrimaryGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(comment.username, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(comment.content, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
