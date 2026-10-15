package io.github.biliaudioplayer.domain

import io.github.biliaudioplayer.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class QueueBuilderTest {
    private fun video(id: String, available: Boolean = true) = FavoriteVideo(id, id, available = available)
    private fun track(id: String, page: Int) = Track(id, page.toLong(), page, "$id P$page", id)

    @Test fun remoteOrderWinsOverParallelCompletionAndPartsAreOrdered() = runTest {
        val videos = listOf(video("first"), video("second"), video("third"))
        val builder = QueueBuilder { video ->
            if (video.bvid == "first") delay(100)
            listOf(track(video.bvid, 2), track(video.bvid, 1))
        }
        val result = builder.build(videos, track("second", 2))
        assertEquals(listOf("first:1", "first:2", "second:1", "second:2", "third:1", "third:2"), result.tracks.map { it.id })
        assertEquals(3, result.startIndex)
    }
    @Test fun invalidAndFailedVideosAreSkippedWithoutLosingSelection() = runTest {
        val result = QueueBuilder { video ->
            if (video.bvid == "failed") error("gone")
            listOf(track(video.bvid, 1))
        }.build(listOf(video("invalid", false), video("failed"), video("valid")), track("valid", 1))
        assertEquals(2, result.skippedVideos)
        assertEquals(0, result.startIndex)
        assertEquals(listOf("valid:1"), result.tracks.map { it.id })
    }
    @Test fun duplicateVideosAndPartsDoNotCreateDuplicateSongs() = runTest {
        val result = QueueBuilder { listOf(track(it.bvid, 1), track(it.bvid, 1)) }
            .build(listOf(video("one"), video("one")), track("one", 1))
        assertEquals(1, result.tracks.size)
    }
    @Test(expected = IllegalStateException::class) fun missingSelectedTrackFailsInsteadOfPlayingAnotherSong() = runTest {
        QueueBuilder { emptyList() }.build(listOf(video("one")), track("one", 1))
    }
    @Test(expected = CancellationException::class) fun cancellationIsNotSwallowedAsAnUnavailableVideo() = runTest {
        QueueBuilder { throw CancellationException("cancelled") }.build(listOf(video("one")), track("one", 1))
    }
}
