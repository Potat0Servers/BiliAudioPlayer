package io.github.biliaudioplayer

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import io.github.biliaudioplayer.domain.QueueBuilder
import io.github.biliaudioplayer.data.SessionExpiredException
import io.github.biliaudioplayer.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@UnstableApi
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BiliApplication
    private val repository = app.repository
    private val playback = app.playback
    private val mutableState = MutableStateFlow(AppUiState())
    val state = mutableState.asStateFlow()
    private val history = ArrayDeque<Screen>()
    private val partsCache = mutableMapOf<String, List<Track>>()
    private var loadingJob: Job? = null
    private var qrJob: Job? = null
    private var queueJob: Job? = null
    private var loadingVersion = 0L
    private var queueVersion = 0L
    private var qrVersion = 0L
    private var nativeLoginPending = false

    val actions = AppActions(
        ::back, ::refresh, ::refreshQr, ::logout, ::openFolder, ::openVideo, ::playPart,
        ::pinFolder, ::pinPart, { cancelLoading(); navigate(Screen.PLAYER) }, { cancelLoading(); navigate(Screen.QUEUE) }, playback::playIndex,
        playback::togglePlay, playback::previous, playback::next, playback::seek, playback::cycleMode,
        { mutableState.update { it.copy(message = null) } }, ::cancelQueueBuild,
        { cancelLoading(); navigate(Screen.SETTINGS) },
        ::pinVideo, ::copySource, ::openSource,
        { query -> mutableState.update { it.copy(videoQuery = query) } },
        { query -> mutableState.update { it.copy(partQuery = query) } },
        ::openAppLogin, ::useQrLogin,
    )

    init {
        viewModelScope.launch { playback.state.collect { value -> mutableState.update { it.copy(playback = value) } } }
        viewModelScope.launch { playback.queue.collect { value -> mutableState.update { it.copy(queue = value) } } }
        restoreSession()
    }
    private fun report(error: Throwable) {
        if (error is CancellationException) throw error
        if (error is SessionExpiredException) logout()
        mutableState.update { it.copy(message = error.message?.take(200) ?: "操作失败，请稍后重试") }
    }
    private fun cancelLoading() {
        loadingVersion++
        loadingJob?.cancel()
        mutableState.update { it.copy(busy = false, checkingSession = false) }
    }
    private fun load(checkSession: Boolean = false, block: suspend () -> Unit) {
        cancelLoading()
        val version = loadingVersion
        mutableState.update { it.copy(busy = !checkSession, checkingSession = checkSession) }
        loadingJob = viewModelScope.launch {
            try { block() }
            catch (e: Exception) { report(e) }
            finally {
                if (version == loadingVersion) mutableState.update {
                    it.copy(busy = if (checkSession) it.busy else false, checkingSession = false)
                }
            }
        }
    }
    private fun restoreSession() {
        load(checkSession = true) {
            val user = repository.currentUser()
            if (user == null) {
                mutableState.update { it.copy(checkingSession = false) }
                nativeLoginPending = true
                refreshQr()
            } else finishLogin(user)
        }
    }
    private fun finishLogin(user: UserProfile) {
        history.clear()
        mutableState.update { it.copy(user = user, screen = Screen.HOME, qr = null, qrMessage = "", checkingSession = false,
            pinnedFolders = app.pins.folders(user.mid), pinnedParts = app.pins.parts(user.mid), busy = true) }
        mutableState.update { it.copy(pinnedVideos = app.pins.videos(user.mid)) }
        refresh()
    }
    private fun refreshQr() {
        val version = ++qrVersion
        qrJob?.cancel()
        qrJob = viewModelScope.launch {
            mutableState.update { it.copy(qr = null, qrExpired = false, qrMessage = "正在生成登录二维码…", busy = true) }
            try {
                val ticket = repository.generateQr()
                mutableState.update { it.copy(qr = ticket, qrMessage = "请用哔哩哔哩 App 扫码并确认登录", busy = false) }
                if (nativeLoginPending && state.value.loginMethod == LoginMethod.APP) launchNativeLogin(ticket)
                val started = SystemClock.elapsedRealtime()
                while (isActive && SystemClock.elapsedRealtime() - started < 180_000) {
                    delay(2_500)
                    val result = try { repository.pollQr(ticket.key) } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        mutableState.update { it.copy(qrMessage = "网络暂时不可用，正在重试…") }
                        delay(3_000)
                        continue
                    }
                    mutableState.update { it.copy(qrMessage = if (it.loginMethod == LoginMethod.APP && result.status == QrStatus.WAITING) "请在 B 站 App 内确认登录，返回后将自动同步" else result.message) }
                    when (result.status) {
                        QrStatus.SUCCESS -> {
                            val user = repository.currentUser() ?: error("登录尚未生效，请刷新二维码重试")
                            finishLogin(user)
                            return@launch
                        }
                        QrStatus.EXPIRED -> break
                        else -> Unit
                    }
                }
                mutableState.update { it.copy(qrExpired = true, qrMessage = "二维码已过期，请刷新") }
            } catch (e: Exception) {
                report(e)
                mutableState.update { it.copy(qrExpired = true, qrMessage = "登录连接失败，请重试", busy = false) }
            } finally {
                if (version == qrVersion && state.value.screen == Screen.LOGIN) mutableState.update { it.copy(busy = false) }
            }
        }
    }
    private fun refresh() {
        when (state.value.screen) {
            Screen.LOGIN -> restoreSession()
            Screen.HOME -> {
                val user = state.value.user ?: return
                load {
                    val folders = repository.folders(user.mid)
                    mutableState.update { it.copy(folders = folders) }
                }
            }
            Screen.FOLDER -> state.value.selectedFolder?.let(::openFolder)
            Screen.PARTS -> state.value.selectedVideo?.let { partsCache.remove(it.bvid); openVideo(it) }
            else -> Unit
        }
    }
    private fun navigate(screen: Screen) {
        if (screen == state.value.screen) return
        history.addLast(state.value.screen)
        mutableState.update { it.copy(screen = screen) }
    }
    private fun back() {
        cancelLoading()
        if (history.isNotEmpty()) mutableState.update { it.copy(screen = history.removeLast()) }
    }
    private fun openFolder(folder: FavoriteFolder) {
        navigate(Screen.FOLDER)
        mutableState.update { it.copy(selectedFolder = folder, videos = emptyList(), parts = emptyList(), selectedVideo = null, busy = true,
            videoQuery = if (it.selectedFolder?.id == folder.id) it.videoQuery else "", partQuery = "") }
        load {
            val videos = repository.videos(folder.id)
            partsCache.clear()
            mutableState.update { it.copy(videos = videos) }
        }
    }
    private suspend fun resolveParts(video: FavoriteVideo): List<Track> = partsCache[video.bvid] ?: repository.parts(video).also { partsCache[video.bvid] = it }
    private fun openVideo(video: FavoriteVideo) {
        if (!video.available) { mutableState.update { it.copy(message = "这个视频已失效或不可访问") }; return }
        mutableState.update { it.copy(selectedVideo = video, parts = emptyList(), busy = true,
            partQuery = if (it.selectedVideo?.bvid == video.bvid) it.partQuery else "") }
        load {
            val parts = resolveParts(video)
            mutableState.update { it.copy(parts = parts) }
            if (parts.size == 1) playPart(parts.first()) else navigate(Screen.PARTS)
        }
    }
    private fun playPart(track: Track) {
        val folder = state.value.selectedFolder ?: return
        val videos = state.value.videos.toList()
        val version = ++queueVersion
        queueJob?.cancel()
        queueJob = viewModelScope.launch {
            mutableState.update { it.copy(buildingQueue = true, queueProgress = "正在展开收藏夹中的分 P…") }
            try {
                val result = QueueBuilder(::resolveParts).build(videos, track) { done, total ->
                    mutableState.update { it.copy(queueProgress = "已读取 $done / $total 个视频") }
                }
                ensureActive()
                playback.playQueue(result.tracks, result.startIndex)
                mutableState.update { it.copy(queueTitle = folder.title, message = null) }
                navigate(Screen.PLAYER)
            } catch (e: Exception) { report(e) }
            finally { if (version == queueVersion) mutableState.update { it.copy(buildingQueue = false, queueProgress = "") } }
        }
    }
    private fun cancelQueueBuild() { queueVersion++; queueJob?.cancel(); mutableState.update { it.copy(buildingQueue = false, queueProgress = "") } }
    private fun pinFolder(folder: FavoriteFolder) {
        val mid = state.value.user?.mid ?: return
        app.pins.toggleFolder(mid, folder.id.toString())
        mutableState.update { it.copy(pinnedFolders = app.pins.folders(mid)) }
    }
    private fun pinPart(track: Track) {
        val mid = state.value.user?.mid ?: return
        app.pins.togglePart(mid, track.id)
        mutableState.update { it.copy(pinnedParts = app.pins.parts(mid)) }
    }
    private fun pinVideo(video: FavoriteVideo) {
        val mid = state.value.user?.mid ?: return
        app.pins.toggleVideo(mid, video.bvid)
        mutableState.update { it.copy(pinnedVideos = app.pins.videos(mid)) }
    }
    private fun sourceLink(bvid: String, page: Int) = "https://www.bilibili.com/video/$bvid/?p=${page.coerceAtLeast(1)}"
    private fun copySource(bvid: String, page: Int) {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("B站源链接", sourceLink(bvid, page)))
        mutableState.update { it.copy(message = "已复制源链接") }
    }
    private fun openSource(bvid: String, page: Int) {
        val uri = Uri.parse(sourceLink(bvid, page))
        for (packageName in listOf("tv.danmaku.bili", "tv.danmaku.bilibilihd", "com.bilibili.app.in")) {
            try {
                app.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) { }
        }
        mutableState.update { it.copy(message = "未找到可打开链接的哔哩哔哩 App") }
    }
    private fun useQrLogin() {
        nativeLoginPending = false
        mutableState.update { it.copy(loginMethod = LoginMethod.QR) }
        if (state.value.qr == null || state.value.qrExpired) refreshQr()
    }
    private fun openAppLogin() {
        mutableState.update { it.copy(loginMethod = LoginMethod.APP) }
        val ticket = state.value.qr
        if (ticket == null || state.value.qrExpired) { nativeLoginPending = true; refreshQr() }
        else launchNativeLogin(ticket)
    }
    private fun launchNativeLogin(ticket: QrTicket) {
        nativeLoginPending = false
        val uri = Uri.Builder().scheme("bilibili").authority("browser").appendQueryParameter("url", ticket.url).build()
        for (packageName in listOf("tv.danmaku.bili", "tv.danmaku.bilibilihd", "com.bilibili.app.in")) {
            try {
                app.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                mutableState.update { it.copy(qrMessage = "请在 B 站 App 内确认授权，返回后将自动登录") }
                return
            } catch (_: ActivityNotFoundException) { }
        }
        mutableState.update { it.copy(loginMethod = LoginMethod.QR, qrMessage = "未找到本机 B 站 App，请使用扫码登录") }
    }
    private fun logout() {
        cancelLoading(); qrVersion++; qrJob?.cancel(); cancelQueueBuild()
        playback.clear()
        repository.logout()
        nativeLoginPending = false
        partsCache.clear(); history.clear()
        mutableState.value = AppUiState(checkingSession = false, playback = playback.state.value)
        refreshQr()
    }
}
