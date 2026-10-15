package io.github.biliaudioplayer.data

import io.github.biliaudioplayer.model.*

interface MusicRepository {
    suspend fun currentUser(): UserProfile?
    suspend fun generateQr(): QrTicket
    suspend fun pollQr(key: String): QrLoginResult
    suspend fun folders(mid: Long): List<FavoriteFolder>
    suspend fun videos(folderId: Long): List<FavoriteVideo>
    suspend fun parts(video: FavoriteVideo): List<Track>
    suspend fun audio(bvid: String, cid: Long): AudioStream
    fun cookieHeader(): String
    fun logout()
}
