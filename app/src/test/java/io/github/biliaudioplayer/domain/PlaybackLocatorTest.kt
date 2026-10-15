package io.github.biliaudioplayer.domain

import io.github.biliaudioplayer.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlaybackLocatorTest {
    private val source = FavoriteFolder(42, "播放来源")
    private val video = FavoriteVideo("BVsource", "合集")
    private val first = Track(video.bvid, 1, 1, "第一 P", video.title, sourceFolder = source)
    private val second = first.copy(cid = 2, page = 2, title = "第二 P")

    @Test fun multiplePartsResolveUsingPlayingSourceAndCurrentPartId() = runTest {
        var requestedFolder: Long? = null
        val entries = listOf(FavoriteVideo("BVother", "其他稿件"), video)
        val result = PlaybackLocator({ requestedFolder = it; entries }, { listOf(first, second) }).resolve(second)
        assertEquals(source.id, requestedFolder)
        assertEquals(source, result.folder)
        assertEquals(entries, result.videos)
        assertEquals(video, result.video)
        assertEquals(listOf(first, second), result.parts)
        assertEquals(Screen.PARTS, result.screen)
        assertEquals(second.id, result.targetId)
    }

    @Test fun singlePartTargetsSourceVideoEvenWhenOriginalPageWasNotOne() = runTest {
        val result = PlaybackLocator({ listOf(video) }, { listOf(second) }).resolve(second)
        assertEquals(Screen.FOLDER, result.screen)
        assertEquals(video.bvid, result.targetId)
    }

    @Test(expected = IllegalStateException::class) fun missingSourceFailsBeforeReadingLibrary() = runTest {
        PlaybackLocator({ fail("No request should occur without a source"); emptyList() }, { emptyList() })
            .resolve(first.copy(sourceFolder = null))
    }

    @Test(expected = IllegalStateException::class) fun deletedVideoFailsInsteadOfOpeningAnotherVideo() = runTest {
        PlaybackLocator({ listOf(FavoriteVideo("BVother", "其他稿件")) }, {
            fail("Deleted video must not request parts"); emptyList()
        }).resolve(first)
    }

    @Test(expected = IllegalStateException::class) fun unavailableVideoFailsBeforeReadingParts() = runTest {
        PlaybackLocator({ listOf(video.copy(available = false)) }, {
            fail("Unavailable video must not request parts"); emptyList()
        }).resolve(first)
    }

    @Test(expected = IllegalStateException::class) fun deletedPartFailsInsteadOfLocatingRemainingPart() = runTest {
        PlaybackLocator({ listOf(video) }, { listOf(first) }).resolve(second)
    }

    @Test fun requestFailuresAndCancellationPropagateWithoutFallback() = runTest {
        val network = IllegalArgumentException("request failed")
        val cancellation = CancellationException("cancelled")
        for (failure in listOf(network, cancellation)) {
            val folderFailure = runCatching {
                PlaybackLocator({ throw failure }, { listOf(first) }).resolve(first)
            }.exceptionOrNull()
            val partsFailure = runCatching {
                PlaybackLocator({ listOf(video) }, { throw failure }).resolve(first)
            }.exceptionOrNull()
            assertSame(failure, folderFailure)
            assertSame(failure, partsFailure)
        }
    }
}
