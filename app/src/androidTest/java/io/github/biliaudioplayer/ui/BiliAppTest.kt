package io.github.biliaudioplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.longClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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

    @Test fun version101SettingsIsEmptyAndBothListRailsJumpToEnd() {
        val ui = mutableStateOf(AppUiState(screen = Screen.HOME, checkingSession = false))
        compose.setContent { BiliApp(ui.value, actions(showSettings = { ui.value = ui.value.copy(screen = Screen.SETTINGS) })) }
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1) // Only the back button; settings content is empty.
        compose.runOnIdle {
            ui.value = AppUiState(screen = Screen.FOLDER, checkingSession = false,
                videos = (1..80).map { FavoriteVideo("BVfixture$it", "视频 $it") })
        }
        compose.onNodeWithContentDescription("快速滚动条").performTouchInput {
            swipe(center.copy(y = 1f), center.copy(y = height - 1f), durationMillis = 400)
        }
        compose.onNodeWithText("视频 80").assertIsDisplayed()
        compose.runOnIdle {
            ui.value = AppUiState(screen = Screen.PARTS, checkingSession = false,
                parts = (1..80).map { Track("BVfixture", it.toLong(), it, "曲目 $it", "合集") })
        }
        compose.onNodeWithContentDescription("快速滚动条").performTouchInput {
            swipe(center.copy(y = 1f), center.copy(y = height - 1f), durationMillis = 400)
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
        showQueue = {},
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
    )
}
