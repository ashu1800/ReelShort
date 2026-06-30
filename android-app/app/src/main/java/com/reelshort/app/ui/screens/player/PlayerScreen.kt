package com.reelshort.app.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.reelshort.app.data.Comment
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.state.AppUiState
import com.reelshort.app.ui.format.RewardBadgeState
import com.reelshort.app.ui.format.RewardBadgeVisualState
import com.reelshort.app.ui.format.episodeNumberLabel
import com.reelshort.app.ui.format.episodeSelectorLabel
import com.reelshort.app.ui.format.rewardBadgeState
import com.reelshort.app.ui.format.playerStartsAutomatically
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
    var commentSheetVisible by remember { mutableStateOf(false) }
    var episodeSheetVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 视频层
        MediaPlayerSurface(
            playableUrl = playableUrl,
            episodeNumber = episode?.number ?: 1,
            initialPositionSeconds = playback.positionSeconds,
            fallbackDurationSeconds = playback.durationSeconds,
            onProgress = onUpdatePlaybackPosition,
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
            CircleIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", onClick = onBack)
            val badgeState = rewardBadgeState(
                progressPercent = playback.progressPercent,
                lastReportedProgressPercent = playback.lastReportedProgressPercent,
                isReporting = playback.isRewardReporting,
                hasError = playback.rewardReportError,
            )
            if (badgeState.visualState != RewardBadgeVisualState.WAITING || playback.progressPercent > 0) {
                RewardBadgeChip(state = badgeState)
            }
        }

        // 右侧竖排动作栏
        ActionRail(
            liked = state.interaction.liked,
            likeCount = state.interaction.likeCount,
            favorited = state.interaction.favorited,
            commentCount = state.comments.size,
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
                        "奖励领取失败，可继续观看",
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
            onDismiss = { commentSheetVisible = false },
            onSubmit = { content ->
                onSubmitComment(content)
            },
        )
    }

    if (episodeSheetVisible) {
        EpisodeSelectorBottomSheet(
            episodes = state.episodes,
            selectedEpisode = state.selectedEpisode,
            onDismiss = { episodeSheetVisible = false },
            onSelectEpisode = { selected ->
                episodeSheetVisible = false
                if (selected.number != state.selectedEpisode?.number || selected.chapterId != state.selectedEpisode?.chapterId) {
                    onOpenPlayer(selected)
                }
            },
        )
    }
}

@Composable
@UnstableApi
private fun BoxScope.MediaPlayerSurface(
    playableUrl: String?,
    episodeNumber: Int,
    initialPositionSeconds: Int,
    fallbackDurationSeconds: Int,
    onProgress: (Int, Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .align(Alignment.Center),
        contentAlignment = Alignment.Center,
    ) {
        if (playableUrl == null) {
            PlayerPlaceholder(episodeNumber)
            return
        }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val player = remember(playableUrl) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(playableUrl))
                playWhenReady = playerStartsAutomatically()
                prepare()
            }
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
            var lastPercent = -1
            while (true) {
                kotlinx.coroutines.delay(1000)
                val positionMs = player.currentPosition
                val durationMs = player.duration.takeIf { it > 0 } ?: fallbackDurationSeconds * 1000L
                if (durationMs <= 0) continue
                val positionSeconds = (positionMs / 1000).toInt()
                val durationSeconds = (durationMs / 1000).toInt()
                val percent = (positionSeconds * 100 / durationSeconds).coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(positionSeconds, durationSeconds)
                }
            }
        }
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
    }
}

@Composable
private fun PlayerPlaceholder(episodeNumber: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = PrimaryGold, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
        Text("加载第 $episodeNumber 集…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
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
private fun RewardBadgeChip(state: RewardBadgeState) {
    val ringColor = if (state.visualState == RewardBadgeVisualState.ERROR) DangerText else PrimaryGold
    val text = when (state.visualState) {
        RewardBadgeVisualState.COMPLETED -> "✓"
        else -> state.displayText
    }
    Surface(
        color = Color(0xE611151E),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ringColor),
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.visualState == RewardBadgeVisualState.REPORTING) {
                CircularProgressIndicator(
                    progress = { state.ringProgress },
                    color = PrimaryGold,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(text, color = ringColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionRail(
    liked: Boolean,
    likeCount: Int,
    favorited: Boolean,
    commentCount: Int,
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
            label = "收藏",
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
                label = episodeSelectorLabel(episodes.size),
                selectedEpisode = selectedEpisode,
                onClick = onOpenEpisodeSheet,
            )
        }
    }
}

@Composable
private fun EpisodeSelectorBar(
    label: String,
    selectedEpisode: EpisodeSummary?,
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
                        "当前 ${episodeNumberLabel(selectedEpisode.number)}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "展开选集", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeSelectorBottomSheet(
    episodes: List<EpisodeSummary>,
    selectedEpisode: EpisodeSummary?,
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
                Text(episodeSelectorLabel(episodes.size), color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp).clickable(onClick = onDismiss),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                modifier = Modifier.fillMaxWidth().height(320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(episodes, key = { "${it.chapterId}:${it.number}" }) { episode ->
                    val selected = episode.chapterId == selectedEpisode?.chapterId && episode.number == selectedEpisode.number
                    EpisodeSelectorItem(episode = episode, selected = selected, onClick = { onSelectEpisode(episode) })
                }
            }
        }
    }
}

@Composable
private fun EpisodeSelectorItem(
    episode: EpisodeSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) PrimaryGold else Color(0xFF1B202B)
    val foreground = if (selected) OnPrimaryDark else TextPrimary
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            episode.number.coerceAtLeast(0).toString(),
            color = foreground,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentBottomSheet(
    comments: List<Comment>,
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
                Text("评论 ${comments.size}", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp).clickable(onClick = onDismiss),
                )
            }
            if (comments.isEmpty()) {
                Text(
                    "还没有评论，快来抢沙发吧",
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
                    placeholder = { Text("写下你的评论…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium) },
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
                    Icon(Icons.Rounded.Send, contentDescription = "发送", tint = if (draft.isBlank()) TextSecondary else OnPrimaryDark, modifier = Modifier.size(20.dp))
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
