package io.github.biliaudioplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.longClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.biliaudioplayer.model.AppActions
import io.github.biliaudioplayer.model.AppUiState
import io.github.biliaudioplayer.model.FavoriteFolder
import io.github.biliaudioplayer.model.FavoriteVideo
import io.github.biliaudioplayer.model.Screen
import io.github.biliaudioplayer.model.Track
import io.github.biliaudioplayer.model.PlaybackUiState
import io.github.biliaudioplayer.model.LoginMethod
import io.github.biliaudioplayer.model.ListTarget
import io.github.biliaudioplayer.playback.toMediaItem
import io.github.biliaudioplayer.playback.toTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Offline fixtures verify routing at the UI boundary; no account or Bilibili access is used. */
class BiliAppTest {
    @get:Rule val compose = createComposeRule()

    private val first = Track("BVfixture", 101, 1, "第一首", "现场合集", durationSeconds = 123)
    private val second = Track("BVfixture", 102, 2, "第二首", "现场合集", durationSeconds = 240)

    @Test fun pinnedPartMovesUpAndPinDoesNotStartPlayback() {
        var played: Track? = null
        var pinned: Track? = null
        compose.setContent {
            BiliApp(
                AppUiState(
                    screen = Screen.PARTS,
                    checkingSession = false,
                    selectedVideo = FavoriteVideo("BVfixture", "现场合集"),
                    parts = listOf(first, second),
                    pinnedParts = setOf(second.id),
                ),
                actions(playPart = { played = it }, pinPart = { pinned = it }),
            )
        }
        val secondTop = compose.onNodeWithText("第二首").fetchSemanticsNode().boundsInRoot.top
        val firstTop = compose.onNodeWithText("第一首").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Pinned part should be displayed before the first part", secondTop < firstTop)
        compose.onNodeWithText("第一首").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("完整标题").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithContentDescription("更多操作 第一首").performScrollTo().performClick()
        compose.onNodeWithText("复制源链接").assertIsDisplayed()
        compose.onNodeWithText("直接跳转 B 站 App").assertIsDisplayed()
        compose.onNodeWithText("置顶").performClick()
        compose.runOnIdle { assertEquals(first, pinned); assertNull(played) }
        compose.onNodeWithText("第二首").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(second, played) }
    }

    @Test fun queueClickRetainsOriginalIndexWhenPartIsPinned() {
        var selectedIndex = -1
        compose.setContent {
            BiliApp(
                AppUiState(screen = Screen.QUEUE, checkingSession = false, queue = listOf(first, second), pinnedParts = setOf(second.id), queueTitle = "收藏音乐"),
                actions(playQueueIndex = { selectedIndex = it }),
            )
        }
        val firstTop = compose.onNodeWithText("第一首").fetchSemanticsNode().boundsInRoot.top
        val secondTop = compose.onNodeWithText("第二首").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Queue must preserve Bilibili order", firstTop < secondTop)
        compose.onNodeWithText("第二首").performClick()
        compose.runOnIdle { assertEquals(1, selectedIndex) }
    }

    @Test fun unavailableVideoDoesNotOpen() {
        var opened = false
        compose.setContent {
            BiliApp(
                AppUiState(
                    screen = Screen.FOLDER,
                    checkingSession = false,
                    selectedFolder = FavoriteFolder(1, "我的收藏"),
                    videos = listOf(FavoriteVideo("BVmissing", "已失效的收藏", available = false)),
                ),
                actions(openVideo = { opened = true }),
            )
        }
        compose.onNodeWithText("已失效的收藏").performClick()
        compose.runOnIdle { assertTrue(!opened) }
        compose.onNodeWithText("视频已失效").assertIsDisplayed()
    }

    @Test fun queueBuildCanBeCancelled() {
        var cancelled = false
        compose.setContent {
            BiliApp(
                AppUiState(screen = Screen.HOME, checkingSession = false, buildingQueue = true, queueProgress = "已读取 2 / 8 个视频"),
                actions(cancelQueueBuild = { cancelled = true }),
            )
        }
        compose.onNodeWithText("已读取 2 / 8 个视频").assertIsDisplayed()
        compose.onNodeWithText("取消加载").performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun qrRefreshAndSamePhoneInstructionsAreAvailable() {
        var refreshed = false
        compose.setContent {
            BiliApp(AppUiState(checkingSession = false, loginMethod = LoginMethod.QR), actions(refreshQr = { refreshed = true }))
        }
        compose.onNodeWithText("生成登录二维码").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(refreshed) }
        compose.onNodeWithText("使用哔哩哔哩 App 扫一扫，并确认登录。\n也可以截图，在 B 站扫一扫中从相册识别。").performScrollTo().assertIsDisplayed()
    }

    @Test fun pageBackCallsNavigationAction() {
        var navigatedBack = false
        compose.setContent {
            BiliApp(AppUiState(screen = Screen.QUEUE, checkingSession = false), actions(back = { navigatedBack = true }))
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertTrue(navigatedBack) }
    }

    @Test fun foldersRemainReachableOnSmallScreenWithLargeText() {
        val folders = (1L..12L).map { FavoriteFolder(it, "收藏夹 $it") }
        var opened: FavoriteFolder? = null
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.6f)) {
                Box(Modifier.size(width = 320.dp, height = 480.dp)) {
                    BiliApp(
                        AppUiState(screen = Screen.HOME, checkingSession = false, folders = folders),
                        actions(openFolder = { opened = it }),
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("收藏夹列表").performScrollToNode(hasText("收藏夹 12"))
        compose.onNodeWithText("收藏夹 12").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(folders.last(), opened) }
    }

    @Test fun version101PlayerFitsCompactViewportWithoutScrolling() {
        compose.setContent {
            Box(Modifier.size(width = 360.dp, height = 600.dp)) {
                BiliApp(AppUiState(screen = Screen.PLAYER, checkingSession = false,
                    queue = listOf(first), queueTitle = "我的收藏",
                    playback = PlaybackUiState(connected = true, currentId = first.id)), actions())
            }
        }
        compose.onNodeWithContentDescription("播放进度").assertIsDisplayed()
        compose.onNodeWithContentDescription("上一曲").assertIsDisplayed()
        compose.onNodeWithContentDescription("下一曲").assertIsDisplayed()
        val range = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("Player must fit without vertical scrolling", 0f, range.maxValue(), 1f)
    }

    @Test fun version014BothRailsPreserveContentWidthAndJumpToEnd() {
        val ui = mutableStateOf(AppUiState(screen = Screen.FOLDER, checkingSession = false,
            videos = (1..80).map { FavoriteVideo("BVfixture$it", "视频 $it") }))
        compose.setContent { BiliApp(ui.value, actions()) }
        assertMenuMeetsRail("更多操作 视频 1")
        compose.onNodeWithTag("fast-scroll-thumb", useUnmergedTree = true).assertWidthIsEqualTo(24.dp)
        compose.onNodeWithContentDescription("快速滚动条").assertWidthIsEqualTo(24.dp).performTouchInput {
            swipe(center.copy(y = 1f), center.copy(y = height - 1f), durationMillis = 400)
        }
        compose.onNodeWithText("视频 80").assertIsDisplayed()
        compose.runOnIdle {
            ui.value = AppUiState(screen = Screen.PARTS, checkingSession = false,
                parts = (1..80).map { Track("BVfixture", it.toLong(), it, "曲目 $it", "合集") })
        }
        assertMenuMeetsRail("更多操作 曲目 1")
        compose.onNodeWithTag("fast-scroll-thumb", useUnmergedTree = true).assertWidthIsEqualTo(24.dp)
        compose.onNodeWithContentDescription("快速滚动条").assertWidthIsEqualTo(24.dp).performTouchInput {
            swipe(center.copy(y = 1f), center.copy(y = height - 1f), durationMillis = 400)
        }
        compose.onNodeWithText("曲目 80").assertIsDisplayed()
    }

    @Test fun version014ThumbAnimatesBetweenProgrammaticScrollPositions() {
        compose.setContent {
            BiliApp(AppUiState(screen = Screen.FOLDER, checkingSession = false,
                videos = (1..80).map { FavoriteVideo("BVfixture$it", "视频 $it") }), actions())
        }
        val rail = compose.onNodeWithContentDescription("快速滚动条")
        val thumb = compose.onNodeWithTag("fast-scroll-thumb", useUnmergedTree = true)
        val initialTop = thumb.fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        try {
            rail.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            val startingTop = thumb.fetchSemanticsNode().boundsInRoot.top
            compose.mainClock.advanceTimeBy(60)
            val middleTop = thumb.fetchSemanticsNode().boundsInRoot.top
            compose.mainClock.advanceTimeBy(200)
            val finalBounds = thumb.fetchSemanticsNode().boundsInRoot
            val railBounds = rail.fetchSemanticsNode().boundsInRoot
            assertTrue("Thumb should move gradually toward the bottom", middleTop > startingTop)
            assertTrue("Animation should have a visible intermediate position", middleTop < finalBounds.top)
            assertTrue("Thumb must move away from the starting position", finalBounds.top > initialTop)
            assertEquals("Thumb should reach the bottom after the animation", railBounds.bottom, finalBounds.bottom, 1f)
            compose.onNodeWithText("视频 80").assertIsDisplayed()
        } finally { compose.mainClock.autoAdvance = true }
    }

    private fun assertMenuMeetsRail(description: String) {
        val menuNode = compose.onNodeWithContentDescription(description).performScrollTo().fetchSemanticsNode()
        val menu = menuNode.boundsInRoot
        val row = generateSequence(menuNode.parent) { it.parent }
            .firstOrNull { it.config.contains(SemanticsActions.OnLongClick) }
            ?.boundsInRoot ?: error("Expected menu to be nested in a combined-clickable entry row")
        val rail = compose.onNodeWithContentDescription("快速滚动条").fetchSemanticsNode().boundsInRoot
        assertEquals("Entry row should end at the original 24dp rail boundary", rail.left, row.right, 0.5f)
        assertTrue("Menu should remain entirely inside its entry row",
            menu.left >= row.left - 0.5f && menu.right <= row.right + 0.5f &&
                menu.top >= row.top - 0.5f && menu.bottom <= row.bottom + 0.5f)
    }

    @Test fun version013PlayerLocationAndQueueButtonsHaveIndependentActions() {
        var located = 0
        var openedQueue = 0
        compose.setContent {
            BiliApp(AppUiState(screen = Screen.PLAYER, checkingSession = false,
                queue = listOf(first), queueTitle = "我的收藏",
                playback = PlaybackUiState(connected = true, currentId = first.id)),
                actions(locateCurrent = { located++ }, showQueue = { openedQueue++ }))
        }
        compose.onNodeWithContentDescription("定位当前曲目").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, located); assertEquals(0, openedQueue) }
        compose.onNodeWithContentDescription("打开播放队列").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, located); assertEquals(1, openedQueue) }
    }

    @Test fun version013PlaybackMetadataRetainsSourceFolder() {
        val source = FavoriteFolder(42, "播放来源", "https://example.invalid/cover", 80)
        val track = second.copy(sourceFolder = source)
        assertEquals(track, track.toMediaItem().toTrack())
        assertEquals(second, second.toMediaItem().toTrack())
    }

    @Test fun version013LocationScrollsToTargetAfterPinnedOrderingOnBothLists() {
        val videos = (1..80).map { FavoriteVideo("BVfixture$it", "视频 $it") }
        val parts = (1..80).map { Track("BVfixture", it.toLong(), it, "曲目 $it", "合集") }
        val ui = mutableStateOf(AppUiState(screen = Screen.FOLDER, checkingSession = false,
            selectedFolder = FavoriteFolder(1, "来源收藏夹"), videos = videos,
            pinnedVideos = videos.takeLast(20).map { it.bvid }.toSet(),
            listTarget = ListTarget(Screen.FOLDER, videos.last().bvid, 1)))
        compose.setContent { BiliApp(ui.value, actions()) }
        compose.onNodeWithText("视频 80").assertIsDisplayed()
        compose.runOnIdle {
            ui.value = AppUiState(screen = Screen.PARTS, checkingSession = false,
                selectedVideo = FavoriteVideo("BVfixture", "合集"), parts = parts,
                pinnedParts = parts.takeLast(20).map { it.id }.toSet(),
                listTarget = ListTarget(Screen.PARTS, parts.last().id, 2))
        }
        compose.onNodeWithText("曲目 80").assertIsDisplayed()
    }

    @Test fun version012SearchIsLocalToListTitlesAndAbsentFromHome() {
        val video = FavoriteVideo("BVtarget", "Morning Piano", owner = "演奏者")
        val otherVideo = FavoriteVideo("BVother", "其他稿件", owner = "Morning")
        val part = Track(video.bvid, 201, 1, "独立分P标题", "合集", owner = "演奏者")
        val otherPart = Track(video.bvid, 202, 2, "其他分P", "独立分P标题合集", owner = "独立分P标题")
        val ui = mutableStateOf(AppUiState(screen = Screen.HOME, checkingSession = false))
        var openedVideo: FavoriteVideo? = null
        var playedPart: Track? = null
        compose.setContent {
            BiliApp(ui.value, actions(
                videoQuery = { ui.value = ui.value.copy(videoQuery = it) },
                partQuery = { ui.value = ui.value.copy(partQuery = it) },
                openVideo = { openedVideo = it }, playPart = { playedPart = it },
            ))
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertCountEquals(0)
        compose.runOnIdle {
            ui.value = AppUiState(screen = Screen.FOLDER, checkingSession = false,
                selectedFolder = FavoriteFolder(1, "Morning 收藏夹"), videos = listOf(video, otherVideo), parts = listOf(part))
        }
        compose.onNodeWithText("搜索稿件标题").performTextInput("morning")
        compose.onNodeWithText("Morning Piano").assertIsDisplayed().performClick()
        compose.onAllNodes(hasText("其他稿件")).assertCountEquals(0)
        compose.runOnIdle { assertEquals(video, openedVideo); assertEquals(2, ui.value.videos.size) }
        compose.onNodeWithText("搜索稿件标题").performTextReplacement("独立分P标题")
        compose.onNodeWithText("没有匹配的稿件").assertIsDisplayed()
        compose.onNodeWithContentDescription("清空稿件搜索").performClick()
        compose.onNodeWithText("其他稿件").assertIsDisplayed()
        compose.runOnIdle {
            ui.value = AppUiState(screen = Screen.PARTS, checkingSession = false,
                selectedVideo = video, parts = listOf(part, otherPart))
        }
        compose.onNodeWithText("搜索分P标题").performTextInput("独立分P标题")
        compose.onAllNodes(hasText("其他分P")).assertCountEquals(0)
        compose.onNode(hasText("独立分P标题") and SemanticsMatcher("非搜索输入框") {
            !it.config.contains(SemanticsProperties.EditableText)
        }).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(part, playedPart); assertEquals(2, ui.value.parts.size) }
        compose.onNodeWithContentDescription("清空分P搜索").performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText("其他分P"))
        compose.onNodeWithText("其他分P").assertIsDisplayed()
    }

    private fun actions(
        back: () -> Unit = {},
        refreshQr: () -> Unit = {},
        openFolder: (FavoriteFolder) -> Unit = {},
        openVideo: (FavoriteVideo) -> Unit = {},
        playPart: (Track) -> Unit = {},
        pinPart: (Track) -> Unit = {},
        playQueueIndex: (Int) -> Unit = {},
        cancelQueueBuild: () -> Unit = {},
        showSettings: () -> Unit = {},
        videoQuery: (String) -> Unit = {},
        partQuery: (String) -> Unit = {},
        locateCurrent: () -> Unit = {},
        showQueue: () -> Unit = {},
    ) = AppActions(
        back = back,
        refresh = {},
        refreshQr = refreshQr,
        logout = {},
        openFolder = openFolder,
        openVideo = openVideo,
        playPart = playPart,
        pinFolder = {},
        pinPart = pinPart,
        showPlayer = {},
        showQueue = showQueue,
        playQueueIndex = playQueueIndex,
        togglePlay = {},
        previous = {},
        next = {},
        seek = {},
        cycleMode = {},
        dismissMessage = {},
        cancelQueueBuild = cancelQueueBuild,
        showSettings = showSettings,
        videoQuery = videoQuery,
        partQuery = partQuery,
        locateCurrent = locateCurrent,
    )
}
