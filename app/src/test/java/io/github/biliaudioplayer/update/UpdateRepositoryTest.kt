package io.github.biliaudioplayer.update

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.security.MessageDigest

class UpdateRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var repository: UpdateRepository
    private val apkName = "BiliAudioPlayer-0.1.4-debug.apk"
    private val apk = "downloaded-apk-body"
    private val digest get() = MessageDigest.getInstance("SHA-256").digest(apk.toByteArray()).joinToString("") { "%02x".format(it) }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        repository = UpdateRepository(latestUrl = server.url("/latest"), requireHttps = false)
    }

    @After fun tearDown() { repository.close(); server.shutdown() }

    @Test fun versionCodeDeterminesUpdateAndClientIsAnonymous() {
        enqueueRelease()
        val release = repository.check(4)!!
        assertEquals(5L, release.versionCode)
        assertEquals("0.1.4", release.versionName)
        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie"))
        server.takeRequest()
        enqueueRelease()
        assertNull(repository.check(5))
        enqueueRelease()
        assertNull(repository.check(6))
    }

    @Test fun missingMetadataOrApkCannotOfferAnUpdate() {
        server.enqueue(MockResponse().setBody("{\"assets\":[]}"))
        assertTrue(expectFailure { repository.check(4) }.message!!.contains("update.json"))
        enqueueRelease(includeApk = false)
        assertTrue(expectFailure { repository.check(4) }.message!!.contains("APK"))
    }

    @Test fun verifiedDownloadRemovesOldPartialAndReportsCompletion() {
        enqueueRelease()
        val release = repository.check(4)!!
        server.enqueue(MockResponse().setBody(apk))
        val directory = temporary.newFolder()
        directory.resolve("old.apk.part").writeText("stale")
        var progress: Float? = null
        val result = repository.download(release, directory) { progress = it }
        assertEquals(apk, result.readText())
        assertEquals(1f, progress)
        assertEquals(listOf(apkName), directory.list()!!.toList())
    }

    @Test fun checksumMismatchDeletesPartialAndCompleteFiles() {
        enqueueRelease()
        val release = repository.check(4)!!
        server.enqueue(MockResponse().setBody("tampered"))
        val directory = temporary.newFolder()
        assertTrue(expectFailure { repository.download(release, directory) }.message!!.contains("校验失败"))
        assertTrue(directory.list()!!.isEmpty())
    }

    @Test fun interruptedDownloadCleansPartialAndCanBeRetried() {
        enqueueRelease()
        val release = repository.check(4)!!
        server.enqueue(MockResponse().setBody(apk).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        val directory = temporary.newFolder()
        expectFailure { repository.download(release, directory) }
        assertTrue(directory.list()!!.isEmpty())
        server.enqueue(MockResponse().setBody(apk))
        assertEquals(apk, repository.download(release, directory).readText())
    }

    private fun enqueueRelease(includeApk: Boolean = true) {
        val assets = JSONArray().put(JSONObject().put("name", "update.json").put("browser_download_url", server.url("/update.json")))
        if (includeApk) assets.put(JSONObject().put("name", apkName).put("browser_download_url", server.url("/$apkName")))
        server.enqueue(MockResponse().setBody(JSONObject().put("assets", assets).toString()))
        server.enqueue(MockResponse().setBody(JSONObject().put("versionName", "0.1.4").put("versionCode", 5).put("apkName", apkName).put("sha256", digest).toString()))
    }

    private fun expectFailure(block: () -> Unit): IOException {
        try { block(); fail("Expected download/check failure") } catch (error: IOException) { return error }
        error("unreachable")
    }
}
