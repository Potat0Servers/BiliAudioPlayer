package io.github.biliaudioplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.biliaudioplayer.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@UnstableApi
class PlaybackConnection(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackUiState())
    val state = mutableState.asStateFlow()
    private val mutableQueue = MutableStateFlow<List<Track>>(emptyList())
    val queue = mutableQueue.asStateFlow()
    private val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
    private var controller: MediaController? = null
    private val ready = CompletableDeferred<MediaController>()
    private var clearWhenConnected = false
    private var errorMessage: String? = null
    private var mode = PlayMode.SEQUENTIAL

    init {
        future.addListener({
            runCatching { future.get() }.onSuccess { player ->
                controller = player
                if (clearWhenConnected) { player.stop(); player.clearMediaItems(); clearWhenConnected = false }
                player.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { update(events.contains(Player.EVENT_TIMELINE_CHANGED)) }
                    override fun onPlayerError(error: PlaybackException) {
                        errorMessage = "播放失败，请检查网络或登录状态，点击播放可重试（${error.errorCode}）"
                        update()
                    }
                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                        errorMessage = null
                    }
                })
                ready.complete(player)
                update(rebuildQueue = true)
            }.onFailure {
                ready.completeExceptionally(IllegalStateException("无法连接播放服务，请重新打开应用"))
                mutableState.value = mutableState.value.copy(error = "无法连接播放服务，请重新打开应用")
            }
        }, ContextCompat.getMainExecutor(context))
        scope.launch { while (isActive) { if ((controller?.mediaItemCount ?: 0) > 0) update(); delay(500) } }
    }
    private fun update(rebuildQueue: Boolean = false) {
        val player = controller ?: return
        if (rebuildQueue) {
            val tracks = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toTrack() }
            if (tracks != mutableQueue.value) mutableQueue.value = tracks
        }
        mode = when {
            player.shuffleModeEnabled -> PlayMode.SHUFFLE
            player.repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.ONE
            player.repeatMode == Player.REPEAT_MODE_ALL -> PlayMode.LOOP
            else -> PlayMode.SEQUENTIAL
        }
        mutableState.value = PlaybackUiState(true, player.currentMediaItem?.mediaId, player.isPlaying,
            player.playbackState == Player.STATE_BUFFERING, player.currentPosition.coerceAtLeast(0),
            player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0) ?: 0, mode, errorMessage)
    }
    suspend fun playQueue(tracks: List<Track>, startIndex: Int) {
        val player = ready.await()
        errorMessage = null
        player.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0)
        applyMode(player)
        player.prepare()
        player.play()
        update(rebuildQueue = true)
    }
    fun togglePlay() { controller?.let {
        if (it.isPlaying) it.pause() else {
            errorMessage = null
            if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition()
            if (it.playbackState == Player.STATE_IDLE || it.playerError != null) it.prepare()
            it.play()
        }
        update()
    } }
    fun next() { controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem(); update() } }
    fun previous() { controller?.let { it.seekToPrevious(); update() } }
    fun seek(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0)); update() }
    fun playIndex(index: Int) { controller?.let {
        if (index in 0 until it.mediaItemCount) { errorMessage = null; it.seekToDefaultPosition(index); it.prepare(); it.play(); update() }
    } }
    fun cycleMode() { mode = PlayMode.entries[(mode.ordinal + 1) % PlayMode.entries.size]; controller?.let(::applyMode); update() }
    private fun applyMode(player: Player) {
        player.shuffleModeEnabled = mode == PlayMode.SHUFFLE
        player.repeatMode = when (mode) { PlayMode.ONE -> Player.REPEAT_MODE_ONE; PlayMode.LOOP, PlayMode.SHUFFLE -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF }
    }
    fun clear() {
        if (controller == null) clearWhenConnected = true else controller?.run { stop(); clearMediaItems() }
        mutableQueue.value = emptyList()
        errorMessage = null
        update(rebuildQueue = true)
    }
}
