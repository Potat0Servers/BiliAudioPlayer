package io.github.biliaudioplayer.domain

import io.github.biliaudioplayer.data.MusicRepository
import io.github.biliaudioplayer.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LibrarySearchTest {
    @Test fun searchesUnopenedFoldersAcrossTypesAndReusesIndex() = runTest {
        var videoReads = 0
        var partReads = 0
        val repository = object : MusicRepository {
            override suspend fun folders(mid: Long) = listOf(FavoriteFolder(1, "钢琴收藏"), FavoriteFolder(2, "现场收藏"))
            override suspend fun videos(folderId: Long): List<FavoriteVideo> {
                videoReads++
                return listOf(FavoriteVideo("BVshared", "Concert $folderId", owner = "演奏者"))
            }
            override suspend fun parts(video: FavoriteVideo): List<Track> {
                partReads++
                return listOf(Track(video.bvid, 10, 1, "钢琴独奏", video.title))
            }
            override suspend fun currentUser(): UserProfile? = null
            override suspend fun generateQr(): QrTicket = error("unused")
            override suspend fun pollQr(key: String): QrLoginResult = error("unused")
            override suspend fun audio(bvid: String, cid: Long): AudioStream = error("unused")
            override fun cookieHeader() = ""
            override fun logout() = Unit
        }
        val engine = LibrarySearchEngine(repository)
        val foldersOnly = engine.search(1, "钢琴", setOf(SearchKind.FOLDER))
        assertEquals(listOf(SearchKind.FOLDER), foldersOnly.hits.map { it.kind })
        assertEquals(0, videoReads)
        val parts = engine.search(1, "独奏", setOf(SearchKind.PART))
        assertEquals(setOf(1L, 2L), parts.hits.map { it.folder.id }.toSet())
        assertEquals(2, parts.hits.map { it.key }.distinct().size)
        assertEquals(2, videoReads)
        assertEquals(1, partReads)
        val videos = engine.search(1, "CONCERT", setOf(SearchKind.VIDEO))
        assertEquals(2, videos.hits.size)
        assertTrue(videos.hits.all { it.kind == SearchKind.VIDEO })
        assertEquals(2, videoReads)
        assertEquals(1, partReads)
        assertTrue(engine.filter(2, "独奏", setOf(SearchKind.PART)).hits.isEmpty())
    }
}
