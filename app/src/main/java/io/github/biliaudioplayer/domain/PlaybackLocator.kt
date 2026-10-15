package io.github.biliaudioplayer.domain

import io.github.biliaudioplayer.model.*

data class PlaybackLocation(
    val folder: FavoriteFolder, val videos: List<FavoriteVideo>,
    val video: FavoriteVideo, val parts: List<Track>, val track: Track,
) {
    val screen: Screen get() = if (parts.size == 1) Screen.FOLDER else Screen.PARTS
    val targetId: String get() = if (parts.size == 1) video.bvid else track.id
}

/** Resolves from the playing queue, independently of whichever collection is currently being browsed. */
class PlaybackLocator(
    private val videos: suspend (Long) -> List<FavoriteVideo>,
    private val parts: suspend (FavoriteVideo) -> List<Track>,
) {
    suspend fun resolve(track: Track): PlaybackLocation {
        val folder = checkNotNull(track.sourceFolder) { "这首曲目没有来源记录，请从收藏夹重新选歌后定位" }
        val entries = videos(folder.id)
        val video = checkNotNull(entries.firstOrNull { it.bvid == track.bvid && it.available }) { "当前稿件已不在来源收藏夹中或不可访问" }
        val tracks = parts(video)
        check(tracks.any { it.id == track.id }) { "当前分 P 已不可访问，请刷新稿件后重试" }
        return PlaybackLocation(folder, entries, video, tracks, track)
    }
}
