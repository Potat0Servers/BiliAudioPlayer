package io.github.biliaudioplayer.playback

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.biliaudioplayer.BiliApplication
import io.github.biliaudioplayer.MainActivity
import io.github.biliaudioplayer.model.PlaybackUiState
import io.github.biliaudioplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in playback integration test asserting seek, transition, background continuity and controls. */
@UnstableApi
class PlaybackLiveTest {
    @Test fun publicMultiPartAudioSeeksAdvancesAndContinuesInBackground() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveNetwork") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BiliApplication
        val first = Track("BV1hv411e77b", 306996376, 6, "过场广告音乐", "公开接口实播验收", durationSeconds = 19)
        val second = Track("BV1hv411e77b", 316663804, 2, "分 P 切换验收", "公开接口实播验收", durationSeconds = 115)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val connection = withContext(Dispatchers.Main) { app.playback }
            withContext(Dispatchers.Main) { connection.playQueue(listOf(first, second), 0) }
            val started = await(connection) { it.error != null || (it.currentId == first.id && it.isPlaying && it.durationMs > 0) }
            assertNull("Public AAC stream must start: ${started.error}", started.error)
            Log.i("PlaybackVerification", "Audio started: $started")
            withContext(Dispatchers.Main) { connection.seek(10_000) }
            val seeked = await(connection) { it.error != null || (it.positionMs >= 9_500 && it.isPlaying) }
            assertNull(seeked.error)
            withContext(Dispatchers.Main) { connection.seek(16_000) }
            val advanced = await(connection) { it.error != null || (it.currentId == second.id && it.isPlaying) }
            assertNull("Natural end must advance to the next P: ${advanced.error}", advanced.error)
            assertEquals(second.id, advanced.currentId)
            Log.i("PlaybackVerification", "Advanced to second P: $advanced")

            scenario.moveToState(Lifecycle.State.CREATED)
            val positionBefore = connection.state.value.positionMs
            val background = await(connection) {
                it.error != null || (it.isPlaying && it.positionMs > positionBefore + 1_000)
            }
            Log.i("PlaybackVerification", "Background progress: $background")
            assertNull("Background playback error: $background", background.error)
            assertTrue("Audio should continue with the activity stopped: $background", background.isPlaying)
            val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            assertTrue("A media playback notification should be posted", notifications.activeNotifications.isNotEmpty())

            scenario.moveToState(Lifecycle.State.RESUMED)
            withContext(Dispatchers.Main) { connection.togglePlay() }
            await(connection) { !it.isPlaying }
            withContext(Dispatchers.Main) { connection.playIndex(0) }
            val replayed = await(connection) { it.error != null || (it.currentId == first.id && it.isPlaying) }
            assertNull(replayed.error)
            assertEquals(first.id, replayed.currentId)
        } finally {
            withContext(Dispatchers.Main) { app.playback.clear() }
            scenario.close()
        }
    }

    private suspend fun await(connection: PlaybackConnection, predicate: (PlaybackUiState) -> Boolean): PlaybackUiState =
        withTimeout(45_000) { connection.state.first(predicate) }
}
