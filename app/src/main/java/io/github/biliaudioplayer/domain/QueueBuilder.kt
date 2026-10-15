package io.github.biliaudioplayer.domain

import io.github.biliaudioplayer.model.FavoriteVideo
import io.github.biliaudioplayer.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

data class QueueBuildResult(val tracks: List<Track>, val startIndex: Int, val skippedVideos: Int)

/** Stable ordering follows the remote collection, irrespective of request completion or pin order. */
class QueueBuilder(private val resolve: suspend (FavoriteVideo) -> List<Track>) {
    suspend fun build(videos: List<FavoriteVideo>, selected: Track, progress: (Int, Int) -> Unit = { _, _ -> }): QueueBuildResult = coroutineScope {
        val completed = AtomicInteger()
        val limit = Semaphore(3)
        val uniqueVideos = videos.distinctBy { it.bvid }
        val entries = uniqueVideos.map { video ->
            async {
                limit.withPermit {
                    val result = if (!video.available) emptyList() else try {
                        resolve(video).sortedBy { it.page }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                    progress(completed.incrementAndGet(), uniqueVideos.size)
                    result
                }
            }
        }.awaitAll()
        val tracks = entries.flatten().distinctBy { it.id }
        val index = tracks.indexOfFirst { it.id == selected.id }
        check(index >= 0) { "所选分 P 无法读取，请刷新后重试" }
        QueueBuildResult(tracks, index, entries.count { it.isEmpty() })
    }
}
