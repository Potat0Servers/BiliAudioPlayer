package io.github.biliaudioplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import io.github.biliaudioplayer.model.AppActions
import io.github.biliaudioplayer.model.AppUiState
import io.github.biliaudioplayer.model.FavoriteFolder
import io.github.biliaudioplayer.model.PlayMode
import io.github.biliaudioplayer.model.PlaybackUiState
import io.github.biliaudioplayer.model.Screen
import io.github.biliaudioplayer.model.Track
import io.github.biliaudioplayer.model.LoginMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BiliApp(state: AppUiState, actions: AppActions) {
    BiliTheme {
        val snackbar = remember { SnackbarHostState() }
        var confirmLogout by remember { mutableStateOf(false) }
        var fullTitle by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it)
                actions.dismissMessage()
            }
        }
        Scaffold(
            containerColor = Cream,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (state.screen != Screen.LOGIN && state.screen != Screen.PLAYER && state.screen != Screen.SETTINGS && state.currentTrack != null) {
                    MiniPlayer(state.currentTrack!!, state.playback, actions)
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.checkingSession -> LoadingState("正在恢复登录状态")
                    state.screen == Screen.LOGIN -> LoginScreen(state, actions)
                    state.screen == Screen.HOME -> HomeScreen(state, actions) { confirmLogout = true }
                    state.screen == Screen.FOLDER -> FolderScreen(state, actions) { fullTitle = it }
                    state.screen == Screen.PARTS -> PartsScreen(state, actions) { fullTitle = it }
                    state.screen == Screen.PLAYER -> PlayerScreen(state, actions)
                    state.screen == Screen.QUEUE -> QueueScreen(state, actions)
                    state.screen == Screen.SETTINGS -> UpdateSettings(state.update, actions.checkUpdate, actions.downloadUpdate, actions.installUpdate, actions.back)
                }
            }
        }
        if (confirmLogout) {
            AlertDialog(
                onDismissRequest = { confirmLogout = false },
                title = { Text("退出登录？") },
                text = { Text("退出后将停止播放。下次使用需要重新扫码登录。") },
                confirmButton = { TextButton(onClick = { confirmLogout = false; actions.logout() }) { Text("退出登录") } },
                dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("继续听歌") } },
            )
        }
        if (fullTitle != null) AlertDialog(
            onDismissRequest = { fullTitle = null },
            title = { Text("完整标题") },
            text = { SelectionContainer { Text(fullTitle.orEmpty(), Modifier.verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onClick = { fullTitle = null }) { Text("关闭") } },
        )
        if (state.buildingQueue) {
            AlertDialog(
                onDismissRequest = {},
                icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Coral) },
                title = { Text("正在准备播放队列") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(state.queueProgress.ifBlank { "正在读取收藏夹与分 P 信息…" })
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("首次加载较大的收藏夹可能需要一些时间。", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                },
                confirmButton = { TextButton(onClick = actions.cancelQueueBuild) { Text("取消加载") } },
            )
        }
    }
}

@Composable
private fun LoginScreen(state: AppUiState, actions: AppActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(66.dp).clip(RoundedCornerShape(22.dp)).background(Coral), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("把收藏，听成歌单", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("B 站分 P 音乐播放器", style = MaterialTheme.typography.bodyLarge, color = Muted)
        Spacer(Modifier.height(32.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(28.dp), modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (state.loginMethod == LoginMethod.APP) "使用本机 B 站登录" else "扫码登录 B 站", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(20.dp))
                if (state.loginMethod == LoginMethod.QR) QrCode(state.qr?.url, state.qrExpired, state.busy)
                else Column(Modifier.padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Coral, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("在哔哩哔哩 App 中确认授权", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.qrMessage.ifBlank { if (state.qr == null) "生成二维码后开始登录" else "等待扫码" },
                    color = if (state.qrExpired) MaterialTheme.colorScheme.error else Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = if (state.loginMethod == LoginMethod.APP) actions.openAppLogin else actions.refreshQr,
                    enabled = !state.busy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.loginMethod == LoginMethod.APP) "打开 B 站 App 登录" else if (state.qr == null) "生成登录二维码" else "刷新二维码")
                }
                TextButton(onClick = if (state.loginMethod == LoginMethod.APP) actions.useQrLogin else actions.openAppLogin) {
                    Text(if (state.loginMethod == LoginMethod.APP) "改用扫码登录" else "使用本机 B 站 App 登录")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(if (state.loginMethod == LoginMethod.QR) "使用哔哩哔哩 App 扫一扫，并确认登录。\n也可以截图，在 B 站扫一扫中从相册识别。" else "确认授权后返回播放器，将自动同步收藏夹。\n也可以选择扫码登录。", style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Text("登录凭据仅保存在本机", style = MaterialTheme.typography.labelMedium, color = Muted)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun QrCode(url: String?, expired: Boolean, busy: Boolean) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = if (url == null) null else withContext(Dispatchers.Default) {
            runCatching {
                val matrix = MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 640, 640, mapOf(EncodeHintType.MARGIN to 2))
                val pixels = IntArray(matrix.width * matrix.height) { index ->
                    if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                }
                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            }.getOrNull()
        }
    }
    Box(Modifier.widthIn(max = 230.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), "B 站登录二维码", Modifier.fillMaxSize().alpha(if (expired) 0.16f else 1f), contentScale = ContentScale.Fit)
        } else if (busy || url != null) {
            CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
        } else {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(78.dp))
        }
        if (expired) Text("二维码已过期", style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

@Composable
private fun HomeScreen(state: AppUiState, actions: AppActions, logout: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().semantics { contentDescription = "收藏夹列表" }, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(state.user?.avatar.orEmpty(), Modifier.size(43.dp), circle = true)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("欢迎回来", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Text(state.user?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = actions.refresh, enabled = !state.busy) { Icon(Icons.Default.Refresh, "刷新收藏夹", tint = Muted) }
                IconButton(onClick = actions.showSettings) { Icon(Icons.Default.Settings, "设置", tint = Muted) }
                IconButton(onClick = logout) { Icon(Icons.Default.Logout, "退出登录", tint = Muted) }
            }
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("我的音乐收藏", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text("从熟悉的收藏夹，开始今天的聆听。", style = MaterialTheme.typography.bodyMedium, color = Muted)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("收藏夹", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    CountChip(state.folders.size.toString())
                    Spacer(Modifier.weight(1f))
                    Text("置顶优先", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (state.busy && state.folders.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        when {
            state.busy && state.folders.isEmpty() -> item { LoadingState("正在读取收藏夹", Modifier.height(260.dp)) }
            state.folders.isEmpty() -> item { EmptyState("暂未读取到收藏夹", "在 B 站收藏喜欢的视频，再回来刷新。", Modifier.heightIn(min = 260.dp), actionLabel = "刷新", action = actions.refresh) }
            else -> itemsIndexed(state.folders.sortedByDescending { it.id.toString() in state.pinnedFolders }, key = { _, folder -> folder.id }) { _, folder ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    FolderCard(folder, folder.id.toString() in state.pinnedFolders, { actions.openFolder(folder) }, { actions.pinFolder(folder) })
                }
            }
        }
    }
}

@Composable
private fun FolderCard(folder: FavoriteFolder, pinned: Boolean, open: () -> Unit, pin: () -> Unit) {
    Surface(onClick = open, color = Color.White, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(folder.cover, Modifier.size(76.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 4.dp)) {
                Text(folder.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text("${folder.mediaCount} 个视频", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            IconButton(onClick = pin) {
                Icon(Icons.Default.PushPin, if (pinned) "取消置顶 ${folder.title}" else "置顶 ${folder.title}", tint = if (pinned) Coral else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FolderScreen(state: AppUiState, actions: AppActions, showFullTitle: (String) -> Unit) {
    val visibleVideos = remember(state.videos, state.videoQuery, state.pinnedVideos) {
        state.videos.filter { it.title.contains(state.videoQuery.trim(), ignoreCase = true) }
            .sortedByDescending { it.bvid in state.pinnedVideos }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("收藏夹", actions.back, actions.refresh, state.busy)
        TitleSearchField(state.videoQuery, "稿件", actions.videoQuery)
        if (state.busy && state.videos.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        val target = state.listTarget?.takeIf { it.screen == Screen.FOLDER }
        val targetIndex = target?.let { request -> visibleVideos.indexOfFirst { it.bvid == request.id }.takeIf { it >= 0 }?.plus(1) }
        FastScrollList(resetKey = state.videoQuery, targetIndex = targetIndex, targetRequest = target?.requestId) {
            item {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(state.selectedFolder?.cover.orEmpty(), Modifier.size(98.dp))
                    Column(Modifier.weight(1f).padding(start = 18.dp)) {
                        Text(state.selectedFolder?.title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Text(if (state.videoQuery.isBlank()) "${state.videos.size} 个视频 · 点击查看分 P" else "${visibleVideos.size} / ${state.videos.size} 个稿件", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                SectionLabel("收藏的视频")
            }
            if (state.busy && state.videos.isEmpty()) item { LoadingState("正在读取视频", Modifier.height(260.dp)) }
            else if (state.videos.isEmpty()) item { EmptyState("暂无可显示的视频", "收藏视频后刷新即可查看。", Modifier.heightIn(min = 260.dp), actionLabel = "刷新", action = actions.refresh) }
            else if (visibleVideos.isEmpty()) item { EmptyState("没有匹配的稿件", "试试其他标题关键词。", Modifier.heightIn(min = 220.dp), actionLabel = "清空搜索", action = { actions.videoQuery("") }) }
            itemsIndexed(visibleVideos, key = { _, video -> video.bvid }) { index, video ->
                Row(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = { if (video.available) actions.openVideo(video) },
                        onLongClickLabel = "查看完整标题", onLongClick = { showFullTitle(video.title) },
                    ).padding(start = 24.dp, end = 4.dp, top = 12.dp, bottom = 12.dp).alpha(if (video.available) 1f else 0.45f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(video.cover, Modifier.size(64.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(if (video.available) "${video.owner.ifBlank { "视频" }} · ${formatTime(video.durationSeconds.toLong())}" else "视频已失效", style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    EntryMenu(video.title, video.bvid in state.pinnedVideos,
                        { actions.pinVideo(video) }, { actions.copySource(video.bvid, 1) }, { actions.openSource(video.bvid, 1) })
                }
                if (index != visibleVideos.lastIndex) HorizontalDivider(Modifier.padding(start = 102.dp, end = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PartsScreen(state: AppUiState, actions: AppActions, showFullTitle: (String) -> Unit) {
    val visibleParts = remember(state.parts, state.partQuery, state.pinnedParts) {
        state.parts.filter { it.title.contains(state.partQuery.trim(), ignoreCase = true) }
            .sortedByDescending { it.id in state.pinnedParts }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("分 P 曲目", actions.back, actions.refresh, state.busy)
        TitleSearchField(state.partQuery, "分P", actions.partQuery)
        if (state.busy && state.parts.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        val target = state.listTarget?.takeIf { it.screen == Screen.PARTS }
        val targetIndex = target?.let { request -> visibleParts.indexOfFirst { it.id == request.id }.takeIf { it >= 0 }?.plus(1) }
        FastScrollList(resetKey = state.partQuery, targetIndex = targetIndex, targetRequest = target?.requestId) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Artwork(state.selectedVideo?.cover.orEmpty(), Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Spacer(Modifier.height(20.dp))
                    Text(state.selectedVideo?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(7.dp))
                    Text(state.selectedVideo?.owner.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Muted)
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("全部曲目", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        CountChip(if (state.partQuery.isBlank()) state.parts.size.toString() else "${visibleParts.size}/${state.parts.size}")
                        Spacer(Modifier.weight(1f))
                        Text("置顶仅保存在本机", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
            }
            if (state.busy && state.parts.isEmpty()) item { LoadingState("正在读取分 P", Modifier.height(220.dp)) }
            else if (state.parts.isEmpty()) item { EmptyState("暂时没有曲目", "请刷新重试。", Modifier.heightIn(min = 220.dp), actionLabel = "刷新", action = actions.refresh) }
            else if (visibleParts.isEmpty()) item { EmptyState("没有匹配的分P", "试试其他标题关键词。", Modifier.heightIn(min = 220.dp), actionLabel = "清空搜索", action = { actions.partQuery("") }) }
            itemsIndexed(visibleParts, key = { _, track -> track.id }) { _, track ->
                val current = track.id == state.playback.currentId
                Row(
                    Modifier.fillMaxWidth().background(if (current) SoftCoral.copy(alpha = 0.65f) else Color.Transparent)
                        .combinedClickable(onClick = { actions.playPart(track) }, onLongClickLabel = "查看完整标题", onLongClick = { showFullTitle(track.displayTitle) })
                        .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(color = if (current) Coral else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            if (current) Icon(Icons.Default.GraphicEq, "正在播放", tint = Color.White, modifier = Modifier.size(22.dp))
                            else Text("P${track.page}", style = MaterialTheme.typography.labelLarge, color = Muted)
                        }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(track.displayTitle, style = MaterialTheme.typography.titleMedium, color = if (current) Coral else Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text("P${track.page} · ${formatTime(track.durationSeconds.toLong())}", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    EntryMenu(track.displayTitle, track.id in state.pinnedParts,
                        { actions.pinPart(track) }, { actions.copySource(track.bvid, track.page) }, { actions.openSource(track.bvid, track.page) })
                }
            }
        }
    }
}

@Composable
private fun TitleSearchField(query: String, kind: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onQueryChange, singleLine = true,
        label = { Text("搜索${kind}标题") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Default.Clear, "清空${kind}搜索")
            }
        },
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun EntryMenu(title: String, pinned: Boolean, pin: () -> Unit, copy: () -> Unit, open: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, "更多操作 $title", tint = if (pinned) Coral else Muted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(if (pinned) "取消置顶" else "置顶") }, onClick = { expanded = false; pin() })
            DropdownMenuItem(text = { Text("复制源链接") }, onClick = { expanded = false; copy() })
            DropdownMenuItem(text = { Text("直接跳转 B 站 App") }, onClick = { expanded = false; open() })
        }
    }
}

@Composable
private fun PlayerScreen(state: AppUiState, actions: AppActions) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("正在播放", actions.back, locate = actions.locateCurrent, busy = state.busy)
        val track = state.currentTrack
        if (track == null) {
            EmptyState("还没有正在播放的曲目", "从收藏夹选择一首喜欢的歌。", Modifier.weight(1f))
            return
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val viewportHeight = with(LocalDensity.current) { maxHeight.roundToPx() }
            AdaptivePlayerLayout(viewportHeight, Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(state.queueTitle.ifBlank { "我的收藏" }, color = Muted, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Surface(shape = RoundedCornerShape(30.dp), shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                Artwork(track.cover, Modifier.fillMaxSize(), rounded = false)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.displayTitle, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text("${track.owner.ifBlank { track.videoTitle }} · P${track.page}", style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { actions.pinPart(track) }) {
                    val pinned = track.id in state.pinnedParts
                    Icon(Icons.Default.PushPin, if (pinned) "取消置顶当前曲目" else "置顶当前曲目", tint = if (pinned) Coral else MaterialTheme.colorScheme.outline)
                }
            }
            Column { SeekBar(track, state.playback, actions.seek) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.cycleMode, modifier = Modifier.size(48.dp)) { Icon(modeIcon(state.playback.mode), "切换播放模式，当前${state.playback.mode.label}", tint = Coral, modifier = Modifier.size(23.dp)) }
                IconButton(onClick = actions.previous, enabled = state.playback.connected, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.SkipPrevious, "上一曲", modifier = Modifier.size(36.dp)) }
                PlayButton(state.playback, actions.togglePlay, large = true)
                IconButton(onClick = actions.next, enabled = state.playback.connected, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.SkipNext, "下一曲", modifier = Modifier.size(36.dp)) }
                IconButton(onClick = actions.showQueue, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "打开播放队列", tint = Muted, modifier = Modifier.size(25.dp)) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    state.playback.error != null -> state.playback.error
                    !state.playback.connected -> "正在连接播放器…"
                    state.playback.isBuffering -> "正在缓冲…"
                    else -> state.playback.mode.label
                }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.playback.error != null) MaterialTheme.colorScheme.error else Muted,
                textAlign = TextAlign.Center,
            )
            if (state.playback.error != null) TextButton(onClick = actions.togglePlay, enabled = state.playback.connected) { Text("重试播放") }
            }
            }
        }
    }
}

@Composable
private fun SeekBar(track: Track, playback: PlaybackUiState, seek: (Long) -> Unit) {
    val duration = playback.durationMs.takeIf { it > 0 } ?: (track.durationSeconds.toLong() * 1000)
    var dragging by remember(track.id) { mutableStateOf<Float?>(null) }
    val maximum = duration.coerceAtLeast(1).toFloat()
    val position = dragging ?: playback.positionMs.toFloat().coerceIn(0f, maximum)
    Slider(
        value = position,
        onValueChange = { dragging = it },
        onValueChangeFinished = { dragging?.let { seek(it.toLong()) }; dragging = null },
        valueRange = 0f..maximum,
        enabled = playback.connected && duration > 0,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "播放进度" },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(position.toLong() / 1000), color = Muted, style = MaterialTheme.typography.labelMedium)
        Text(formatTime(duration / 1000), color = Muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun QueueScreen(state: AppUiState, actions: AppActions) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("播放队列", actions.back)
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 20.dp)) {
            Text(state.queueTitle.ifBlank { "当前队列" }, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${state.queue.size} 首曲目", style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = actions.cycleMode, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(modeIcon(state.playback.mode), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(state.playback.mode.label)
                }
            }
        }
        if (state.queue.isEmpty()) EmptyState("播放队列还是空的", "选择分 P 曲目后，收藏夹会自动展开为队列。", Modifier.weight(1f))
        else LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
            itemsIndexed(state.queue, key = { _, track -> track.id }) { index, track ->
                val current = track.id == state.playback.currentId
                Row(
                    Modifier.fillMaxWidth().background(if (current) SoftCoral else Color.Transparent).clickable { actions.playQueueIndex(index) }.padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                        if (current) Icon(Icons.Default.GraphicEq, "当前曲目", tint = Coral, modifier = Modifier.size(21.dp))
                        else Text((index + 1).toString(), color = Muted, fontSize = 13.sp)
                    }
                    Artwork(track.cover, Modifier.padding(start = 8.dp).size(48.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(track.displayTitle, style = MaterialTheme.typography.titleMedium, color = if (current) Coral else Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text("P${track.page} · ${track.videoTitle}", style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatTime(track.durationSeconds.toLong()), style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(track: Track, playback: PlaybackUiState, actions: AppActions) {
    Surface(color = Cream, modifier = Modifier.navigationBarsPadding()) {
        Surface(
            onClick = actions.showPlayer,
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 5.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column {
                Row(Modifier.padding(start = 10.dp, top = 10.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track.cover, Modifier.size(48.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 2.dp)) {
                        Text(track.displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (playback.isBuffering) "正在缓冲…" else "${track.owner.ifBlank { track.videoTitle }} · P${track.page}", style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PlayButton(playback, actions.togglePlay)
                    IconButton(onClick = actions.showQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "打开播放队列", tint = Muted) }
                }
                val duration = playback.durationMs.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Coral,
                    trackColor = SoftCoral,
                )
            }
        }
    }
}

@Composable
private fun PlayButton(playback: PlaybackUiState, toggle: () -> Unit, large: Boolean = false) {
    Box(contentAlignment = Alignment.Center) {
        Surface(color = if (large) Coral else SoftCoral, shape = CircleShape, modifier = Modifier.size(if (large) 76.dp else 44.dp)) {
            IconButton(onClick = toggle, enabled = playback.connected, modifier = Modifier.fillMaxSize()) {
                Icon(
                    if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playback.isPlaying) "暂停" else "播放",
                    tint = if (large) Color.White else Coral,
                    modifier = Modifier.size(if (large) 40.dp else 26.dp),
                )
            }
        }
        if (playback.isBuffering) CircularProgressIndicator(Modifier.size(if (large) 82.dp else 46.dp), strokeWidth = 2.dp, color = Coral)
    }
}

@Composable
private fun PageHeader(title: String, back: () -> Unit, refresh: (() -> Unit)? = null, busy: Boolean = false, queue: (() -> Unit)? = null, locate: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        when {
            locate != null -> IconButton(onClick = locate, enabled = !busy) { Icon(Icons.Default.MyLocation, "定位当前曲目", tint = Muted) }
            refresh != null -> IconButton(onClick = refresh, enabled = !busy) { Icon(Icons.Default.Refresh, "刷新", tint = Muted) }
            queue != null -> IconButton(onClick = queue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "打开播放队列", tint = Muted) }
            else -> Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun Artwork(url: String, modifier: Modifier, circle: Boolean = false, rounded: Boolean = true) {
    Box(modifier.then(if (rounded) Modifier.clip(if (circle) CircleShape else RoundedCornerShape(16.dp)) else Modifier).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxSize(0.4f))
        val imageUrl = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> url.replaceFirst("http://", "https://")
            else -> url
        }
        if (imageUrl.isNotBlank()) AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun CountChip(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
}

@Composable
private fun LoadingState(label: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier, actionLabel: String? = null, action: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(70.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Muted, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
        if (action != null && actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = action) { Text(actionLabel) }
        }
    }
}

private fun modeIcon(mode: PlayMode): ImageVector = when (mode) {
    PlayMode.SEQUENTIAL -> Icons.Default.PlaylistPlay
    PlayMode.LOOP -> Icons.Default.Repeat
    PlayMode.ONE -> Icons.Default.RepeatOne
    PlayMode.SHUFFLE -> Icons.Default.Shuffle
}

private fun formatTime(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = safe % 3600 / 60
    val remaining = safe % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remaining) else "%d:%02d".format(minutes, remaining)
}
