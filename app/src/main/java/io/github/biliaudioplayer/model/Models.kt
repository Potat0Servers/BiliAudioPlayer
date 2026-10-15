package io.github.biliaudioplayer.model

import io.github.biliaudioplayer.update.UpdateUiState

data class UserProfile(val mid: Long, val name: String, val avatar: String = "")
data class FavoriteFolder(val id: Long, val title: String, val cover: String = "", val mediaCount: Int = 0)
data class FavoriteVideo(
    val bvid: String, val title: String, val cover: String = "", val owner: String = "",
    val durationSeconds: Int = 0, val available: Boolean = true,
)
data class Track(
    val bvid: String, val cid: Long, val page: Int, val title: String, val videoTitle: String,
    val cover: String = "", val owner: String = "", val durationSeconds: Int = 0,
    val sourceFolder: FavoriteFolder? = null,
) {
    val id: String get() = "$bvid:$cid"
    val displayTitle: String get() = title.ifBlank { videoTitle }
}
data class QrTicket(val url: String, val key: String)
enum class QrStatus { WAITING, SCANNED, EXPIRED, SUCCESS }
enum class LoginMethod { APP, QR }
data class QrLoginResult(val status: QrStatus, val message: String)
data class AudioStream(val url: String, val backupUrls: List<String> = emptyList(), val mimeType: String = "audio/mp4")

enum class Screen { LOGIN, HOME, FOLDER, PARTS, PLAYER, QUEUE, SETTINGS }
enum class PlayMode(val label: String) { SEQUENTIAL("顺序播放"), LOOP("列表循环"), ONE("单曲循环"), SHUFFLE("随机播放") }

data class ListTarget(val screen: Screen, val id: String, val requestId: Long)
data class PlaybackUiState(
    val connected: Boolean = false, val currentId: String? = null, val isPlaying: Boolean = false,
    val isBuffering: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0,
    val mode: PlayMode = PlayMode.SEQUENTIAL, val error: String? = null,
)
data class AppUiState(
    val screen: Screen = Screen.LOGIN, val user: UserProfile? = null, val checkingSession: Boolean = true,
    val busy: Boolean = false, val folders: List<FavoriteFolder> = emptyList(),
    val selectedFolder: FavoriteFolder? = null, val videos: List<FavoriteVideo> = emptyList(),
    val selectedVideo: FavoriteVideo? = null, val parts: List<Track> = emptyList(),
    val pinnedFolders: Set<String> = emptySet(), val pinnedParts: Set<String> = emptySet(),
    val pinnedVideos: Set<String> = emptySet(),
    val qr: QrTicket? = null, val qrMessage: String = "", val qrExpired: Boolean = false,
    val loginMethod: LoginMethod = LoginMethod.APP,
    val videoQuery: String = "", val partQuery: String = "",
    val queue: List<Track> = emptyList(), val queueTitle: String = "", val buildingQueue: Boolean = false,
    val queueProgress: String = "", val playback: PlaybackUiState = PlaybackUiState(), val message: String? = null,
    val listTarget: ListTarget? = null,
    val update: UpdateUiState = UpdateUiState(),
) {
    val currentTrack: Track? get() = queue.firstOrNull { it.id == playback.currentId }
}

data class AppActions(
    val back: () -> Unit, val refresh: () -> Unit, val refreshQr: () -> Unit, val logout: () -> Unit,
    val openFolder: (FavoriteFolder) -> Unit, val openVideo: (FavoriteVideo) -> Unit,
    val playPart: (Track) -> Unit, val pinFolder: (FavoriteFolder) -> Unit, val pinPart: (Track) -> Unit,
    val showPlayer: () -> Unit, val showQueue: () -> Unit, val playQueueIndex: (Int) -> Unit,
    val togglePlay: () -> Unit, val previous: () -> Unit, val next: () -> Unit,
    val seek: (Long) -> Unit, val cycleMode: () -> Unit, val dismissMessage: () -> Unit,
    val cancelQueueBuild: () -> Unit,
    val showSettings: () -> Unit = {},
    val pinVideo: (FavoriteVideo) -> Unit = {},
    val copySource: (String, Int) -> Unit = { _, _ -> },
    val openSource: (String, Int) -> Unit = { _, _ -> },
    val videoQuery: (String) -> Unit = {},
    val partQuery: (String) -> Unit = {},
    val openAppLogin: () -> Unit = {},
    val useQrLogin: () -> Unit = {},
    val locateCurrent: () -> Unit = {},
    val checkUpdate: () -> Unit = {},
    val downloadUpdate: () -> Unit = {},
    val installUpdate: () -> Unit = {},
)
