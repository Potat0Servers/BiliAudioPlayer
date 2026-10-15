package io.github.biliaudioplayer.domain

import io.github.biliaudioplayer.data.MusicRepository
import io.github.biliaudioplayer.model.FavoriteFolder
import io.github.biliaudioplayer.model.FavoriteVideo
import io.github.biliaudioplayer.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SearchKind { FOLDER, VIDEO, PART }

data class SearchHit(
    val folder: FavoriteFolder,
    val video: FavoriteVideo? = null,
    val track: Track? = null,
    val kind: SearchKind,
    val label: String,
    val subtitle: String,
) {
    // Include the folder so a shared video retains each of its library locations.
    val key: String get() = "${folder.id}:$kind:${video?.bvid.orEmpty()}:${track?.id.orEmpty()}"
}

data class SearchSnapshot(
    val hits: List<SearchHit> = emptyList(),
    val loading: Boolean = false,
    val progress: String = "",
    val error: String? = null,
)

/** Account-scoped in-memory index. Call [invalidate] after refresh or account changes. */
class LibrarySearchEngine(private val repository: MusicRepository) {
    private class AccountIndex {
        var folders: List<FavoriteFolder>? = null
        val videos = mutableMapOf<Long, List<FavoriteVideo>>()
        val parts = mutableMapOf<String, List<Track>>()
    }

    private val stateLock = Any()
    private val buildLock = Mutex()
    private val accounts = mutableMapOf<Long, AccountIndex>()

    fun invalidate(accountId: Long? = null) = synchronized(stateLock) {
        if (accountId == null) accounts.clear() else accounts.remove(accountId)
        Unit
    }

    /** Pure local filtering, including any successfully indexed results from an interrupted build. */
    fun filter(accountId: Long, query: String, kinds: Set<SearchKind>): SearchSnapshot = synchronized(stateLock) {
        SearchSnapshot(hits = accounts[accountId]?.let { matches(it, query, kinds) }.orEmpty())
    }

    /**
     * Builds only missing levels, with at most three repository calls in flight. A folder-only
     * search never reads videos or parts. [folders] can reuse the already loaded complete folder list.
     * Updates run on the caller's coroutine; cancellation propagates and preserves completed work.
     */
    suspend fun search(
        accountId: Long,
        query: String,
        kinds: Set<SearchKind>,
        folders: List<FavoriteFolder>? = null,
        onUpdate: (SearchSnapshot) -> Unit = {},
    ): SearchSnapshot {
        if (query.isBlank() || kinds.isEmpty()) return SearchSnapshot()
        return buildLock.withLock {
            val index = synchronized(stateLock) { accounts.getOrPut(accountId) { AccountIndex() } }
            val failures = mutableListOf<String>()
            suspend fun publish(loading: Boolean, progress: String = ""): SearchSnapshot {
                currentCoroutineContext().ensureActive()
                val snapshot = synchronized(stateLock) {
                    if (accounts[accountId] !== index) throw CancellationException("搜索索引已刷新")
                    SearchSnapshot(matches(index, query, kinds), loading, progress, failures.firstOrNull())
                }
                onUpdate(snapshot)
                return snapshot
            }
            try {
                if (folders != null) synchronized(stateLock) { index.folders = folders.distinctBy { it.id } }
                if (synchronized(stateLock) { index.folders == null }) {
                    publish(true, "正在读取全部收藏夹…")
                    val loaded = repository.folders(accountId).distinctBy { it.id }
                    synchronized(stateLock) { index.folders = loaded }
                }
                val allFolders = synchronized(stateLock) { index.folders.orEmpty() }
                if (SearchKind.VIDEO in kinds || SearchKind.PART in kinds) {
                    val missing = synchronized(stateLock) { allFolders.filter { it.id !in index.videos } }
                    var done = allFolders.size - missing.size
                    if (missing.isNotEmpty()) publish(true, "正在检索收藏夹 $done/${allFolders.size}")
                    for (batch in missing.chunked(3)) {
                        val results = coroutineScope {
                            batch.map { folder -> async {
                                try {
                                    val videos = repository.videos(folder.id).distinctBy { it.bvid }
                                    synchronized(stateLock) { index.videos[folder.id] = videos }
                                    null
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (error: Exception) { "部分收藏夹读取失败：${error.message ?: "请刷新重试"}" }
                            } }.awaitAll()
                        }
                        failures.addAll(results.filterNotNull())
                        done += batch.size
                        publish(true, "正在检索收藏夹 $done/${allFolders.size}")
                    }
                }
                if (SearchKind.PART in kinds) {
                    val videos = synchronized(stateLock) {
                        allFolders.flatMap { index.videos[it.id].orEmpty() }.filter { it.available }.distinctBy { it.bvid }
                    }
                    val missing = synchronized(stateLock) { videos.filter { it.bvid !in index.parts } }
                    var done = videos.size - missing.size
                    if (missing.isNotEmpty()) publish(true, "正在检索分P $done/${videos.size}")
                    for (batch in missing.chunked(3)) {
                        val results = coroutineScope {
                            batch.map { video -> async {
                                try {
                                    val parts = repository.parts(video).distinctBy { it.id }
                                    synchronized(stateLock) { index.parts[video.bvid] = parts }
                                    null
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (error: Exception) { "部分分P读取失败：${error.message ?: "请刷新重试"}" }
                            } }.awaitAll()
                        }
                        failures.addAll(results.filterNotNull())
                        done += batch.size
                        publish(true, "正在检索分P $done/${videos.size}")
                    }
                }
                publish(false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                failures.add("搜索读取失败：${error.message ?: "请刷新重试"}")
                publish(false)
            }
        }
    }

    private fun matches(index: AccountIndex, query: String, kinds: Set<SearchKind>): List<SearchHit> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty() || kinds.isEmpty()) return emptyList()
        fun accepts(vararg values: String): Boolean {
            val text = values.joinToString(" ")
            return terms.all { text.contains(it, ignoreCase = true) }
        }
        return buildList {
            for (folder in index.folders.orEmpty()) {
                if (SearchKind.FOLDER in kinds && accepts(folder.title)) {
                    add(SearchHit(folder, kind = SearchKind.FOLDER, label = folder.title, subtitle = "收藏夹 · ${folder.mediaCount} 个稿件"))
                }
                for (video in index.videos[folder.id].orEmpty()) {
                    if (SearchKind.VIDEO in kinds && accepts(video.title, video.owner, video.bvid, folder.title)) {
                        add(SearchHit(folder, video, kind = SearchKind.VIDEO, label = video.title,
                            subtitle = listOf("稿件", folder.title, video.owner, video.bvid).filter { it.isNotBlank() }.joinToString(" · ")))
                    }
                    if (SearchKind.PART in kinds) for (part in index.parts[video.bvid].orEmpty()) {
                        if (accepts(part.displayTitle, part.videoTitle, part.owner, video.title, video.owner, video.bvid, folder.title)) {
                            add(SearchHit(folder, video, part, SearchKind.PART, part.displayTitle,
                                listOf("P${part.page}", video.title, folder.title, video.owner).filter { it.isNotBlank() }.joinToString(" · ")))
                        }
                    }
                }
            }
        }
    }
}
