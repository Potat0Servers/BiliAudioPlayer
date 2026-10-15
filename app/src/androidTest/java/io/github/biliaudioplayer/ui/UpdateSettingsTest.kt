package io.github.biliaudioplayer.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.biliaudioplayer.update.UpdatePhase
import io.github.biliaudioplayer.update.UpdateRelease
import io.github.biliaudioplayer.update.UpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdateSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun buttonsFollowCheckDownloadInstallAndRetryStates() {
        val state = mutableStateOf(UpdateUiState(currentVersion = "0.1.3", currentVersionCode = 4))
        val calls = mutableListOf<String>()
        val release = UpdateRelease("0.1.4", 5, "next.apk", "unused-here", "unused-here")
        compose.setContent {
            BiliTheme { UpdateSettings(state.value, { calls += "check" }, { calls += "download" }, { calls += "install" }, { calls += "back" }) }
        }
        compose.onNodeWithText("当前版本 0.1.3").assertIsDisplayed()
        compose.onNodeWithText("检查更新").performClick()
        compose.runOnIdle { state.value = state.value.copy(phase = UpdatePhase.CHECKING) }
        compose.onNodeWithText("正在检查更新…").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(phase = UpdatePhase.ERROR, message = "网络错误") }
        compose.onNodeWithText("网络错误").assertIsDisplayed()
        compose.onNodeWithText("重试检查更新").performClick()
        compose.runOnIdle { state.value = state.value.copy(phase = UpdatePhase.AVAILABLE, release = release, message = null) }
        compose.onNodeWithText("下载更新").performClick()
        compose.runOnIdle { state.value = state.value.copy(phase = UpdatePhase.DOWNLOADING, progress = 0.5f) }
        compose.onNodeWithText("正在下载…").assertIsNotEnabled()
        compose.onNodeWithText("50%").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(phase = UpdatePhase.ERROR, message = "下载失败") }
        compose.onNodeWithText("重新下载").performClick()
        compose.runOnIdle { state.value = state.value.copy(phase = UpdatePhase.READY, message = "请允许安装此来源的应用，返回后点击“安装更新”") }
        compose.onNodeWithText("安装更新", substring = false).performClick()
        compose.runOnIdle { assertEquals(listOf("check", "check", "download", "download", "install"), calls) }
    }
}
