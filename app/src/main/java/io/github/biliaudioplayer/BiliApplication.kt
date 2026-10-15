package io.github.biliaudioplayer

import android.app.Application
import io.github.biliaudioplayer.data.BiliRepository
import io.github.biliaudioplayer.data.MusicRepository
import io.github.biliaudioplayer.playback.PlaybackConnection
import io.github.biliaudioplayer.store.PinStore

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BiliApplication : Application() {
    val repository: MusicRepository by lazy { BiliRepository(this) }
    val pins by lazy { PinStore(this) }
    val playback by lazy { PlaybackConnection(this) }
}
