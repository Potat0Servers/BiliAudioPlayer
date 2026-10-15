package io.github.biliaudioplayer.data

import io.github.biliaudioplayer.model.FavoriteVideo
import io.github.biliaudioplayer.model.QrStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class BiliRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemorySessionStore
    private lateinit var repository: BiliRepository

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        store = MemorySessionStore()
        repository = BiliRepository(OkHttpClient(), store, server.url("/"), server.url("/"), true, 0)
    }
    @After fun cleanup() { server.shutdown() }
    private fun respond(body: String) { server.enqueue(MockResponse().setBody(body).addHeader("Content-Type", "application/json")) }

    @Test fun collectsEveryFolderPageAndDeduplicates() = runBlocking {
        respond("""{"code":0,"data":{"list":[{"id":1,"title":"一"},{"id":2,"title":"二"}],"has_more":true}}""")
        respond("""{"code":0,"data":{"list":[{"id":2,"title":"二"},{"id":3,"title":"三"}],"has_more":false}}""")
        assertEquals(listOf(1L, 2L, 3L), repository.folders(123).map { it.id })
        assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("pn"))
        assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("pn"))
    }

    @Test fun rejectsNonAdvancingPagination() = runBlocking {
        repeat(2) { respond("""{"code":0,"data":{"list":[{"id":1}],"has_more":true}}""") }
        val error = runCatching { repository.folders(123) }.exceptionOrNull()
        assertTrue(error is BiliApiException)
        assertTrue(error!!.message!!.contains("分页异常"))
    }

    @Test fun handlesEmptyFolderAndNonVideoEntries() = runBlocking {
        respond("""{"code":0,"data":{"medias":[{"id":1,"type":12},{"id":2,"type":2,"bvid":"BV1xx411c7mD","title":"已失效视频","attr":1}],"has_more":false}}""")
        val videos = repository.videos(55)
        assertEquals(1, videos.size)
        assertFalse(videos.single().available)
        respond("""{"code":0,"data":{"medias":null,"has_more":false,"info":{"media_count":0}}}""")
        assertTrue(repository.videos(56).isEmpty())
    }

    @Test fun doesNotMisreportNonemptyButUnavailableFolderAsEmpty() = runBlocking {
        respond("""{"code":0,"data":{"medias":null,"has_more":false,"info":{"media_count":15}}}""")
        assertTrue(runCatching { repository.videos(56) }.exceptionOrNull() is BiliApiException)
    }

    @Test fun clearsExpiredSessionAndDoesNotExposeCredential() = runBlocking {
        store.value = "SESSDATA=expired-session"
        repository = BiliRepository(OkHttpClient(), store, server.url("/"), server.url("/"), true, 0)
        respond("""{"code":-101,"data":{"isLogin":false}}""")
        assertNull(repository.currentUser())
        assertEquals("", store.value)
        assertEquals("", repository.cookieHeader())
        assertEquals("SESSDATA=expired-session", server.takeRequest().getHeader("Cookie"))
    }

    @Test fun logoutCannotBeUndoneByInFlightLogin() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"code":0,"url":""}}""")
            .addHeader("Set-Cookie", "SESSDATA=late-session; Path=/"))
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"isLogin":true,"mid":123,"uname":"测试"}}""")
            .setBodyDelay(300, TimeUnit.MILLISECONDS))
        val attempt = async(Dispatchers.Default) { runCatching { repository.pollQr("test") } }
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        repository.logout()
        assertTrue(attempt.await().exceptionOrNull() is BiliApiException)
        assertEquals("", store.value)
        assertEquals("", repository.cookieHeader())
    }

    @Test fun inFlightRequestCanBeCancelled() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val request = launch(Dispatchers.Default) { repository.folders(123) }
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
        withTimeout(3000) { request.cancelAndJoin() }
        assertTrue(request.isCancelled)
    }

    @Test fun ignoresCookieOnPendingQrResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"code":86101}}""")
            .addHeader("Set-Cookie", "SESSDATA=must-not-save; Path=/"))
        assertEquals(QrStatus.WAITING, repository.pollQr("test").status)
        assertEquals("", store.value)
        assertEquals("", repository.cookieHeader())
    }

    @Test fun validatesLoginBeforeSavingCookie() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"code":0,"url":""}}""")
            .addHeader("Set-Cookie", "SESSDATA=test-session; Path=/; Secure; HttpOnly")
            .addHeader("Set-Cookie", "bili_jct=test-csrf; Path=/; Secure"))
        respond("""{"code":0,"data":{"isLogin":true,"mid":123,"uname":"测试"}}""")
        assertEquals(QrStatus.SUCCESS, repository.pollQr("test-key").status)
        assertEquals("SESSDATA=test-session; bili_jct=test-csrf", store.value)
        assertNull(server.takeRequest().getHeader("Cookie"))
        assertEquals(store.value, server.takeRequest().getHeader("Cookie"))
    }

    @Test fun rejectedLoginDoesNotPersistCredential() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"code":0,"url":""}}""")
            .addHeader("Set-Cookie", "SESSDATA=invalid; Path=/"))
        respond("""{"code":-101,"message":"raw server content is not shown"}""")
        assertTrue(runCatching { repository.pollQr("test") }.exceptionOrNull() is SessionExpiredException)
        assertEquals("", store.value)
    }

    @Test fun acceptsTrustedLegacyCallbackCookieQuery() = runBlocking {
        respond("""{"code":0,"data":{"code":0,"url":"https://passport.bilibili.com/callback?SESSDATA=query-session&bili_jct=query-csrf"}}""")
        respond("""{"code":0,"data":{"isLogin":true,"mid":123,"uname":"测试"}}""")
        assertEquals(QrStatus.SUCCESS, repository.pollQr("test").status)
        assertTrue(store.value.contains("SESSDATA=query-session"))
        assertEquals(2, server.requestCount)
    }

    @Test fun doesNotUseUntrustedCallbackCookieQuery() = runBlocking {
        respond("""{"code":0,"data":{"code":0,"url":"https://bilibili.com.evil.invalid/callback?SESSDATA=bad"}}""")
        assertTrue(runCatching { repository.pollQr("test") }.exceptionOrNull() is BiliApiException)
        assertEquals("", store.value)
        assertEquals(1, server.requestCount)
    }

    @Test fun preservesSessionEscapingForSingleAndDoubleEncodedQueries() = runBlocking {
        for (query in listOf("session%2Cexpires%2Csignature", "session%252Cexpires%252Csignature")) {
            respond("""{"code":0,"data":{"code":0,"url":"https://passport.bilibili.com/callback?SESSDATA=$query"}}""")
            respond("""{"code":0,"data":{"isLogin":true,"mid":123,"uname":"测试"}}""")
            repository.pollQr("test")
            assertEquals("SESSDATA=session%2Cexpires%2Csignature", store.value)
            server.takeRequest()
            assertEquals(store.value, server.takeRequest().getHeader("Cookie"))
        }
    }

    @Test fun malformedQrDataUsesSafeError() = runBlocking {
        respond("""{"code":0,"data":"secret-response"}""")
        val error = runCatching { repository.generateQr() }.exceptionOrNull()!!
        assertTrue(error is BiliApiException)
        assertFalse(error.message!!.contains("secret-response"))
    }

    @Test fun choosesHighestBitrateAacAndRejectsInsecureBackups() {
        val stream = BiliRepository.parseAudio(JSONObject("""{"dash":{"audio":[
            {"codecs":"mp4a.40.2","bandwidth":64000,"baseUrl":"https://cdn.example/low.m4s"},
            {"codecs":"ec-3","bandwidth":384000,"baseUrl":"https://cdn.example/dolby.m4s"},
            {"codecs":"mp4a.40.2","bandwidth":192000,"base_url":"https://cdn.example/best.m4s","backup_url":["http://cdn.example/insecure.m4s","https://backup.example/best.m4s"]}
        ]}}"""))
        assertEquals("https://cdn.example/best.m4s", stream.url)
        assertEquals(listOf("https://backup.example/best.m4s"), stream.backupUrls)
        assertEquals("audio/mp4", stream.mimeType)
    }

    @Test fun neverTreatsMuxedDurlAsIndependentAudio() {
        val error = runCatching { BiliRepository.parseAudio(JSONObject("""{"durl":[{"url":"https://cdn.example/movie.mp4"}]}""")) }.exceptionOrNull()
        assertTrue(error is BiliApiException)
        assertTrue(error!!.message!!.contains("独立音轨"))
    }

    @Test fun mapsPartsToStableIdentityAndSortsByPage() = runBlocking {
        respond("""{"code":0,"data":{"title":"专辑","pic":"http://i0.hdslb.com/cover.jpg","owner":{"name":"UP"},"pages":[
            {"cid":22,"page":2,"part":"歌曲 B","duration":200},{"cid":11,"page":1,"part":"歌曲 A","duration":100},{"cid":11,"page":1,"part":"歌曲 A"}
        ]}}""")
        val tracks = repository.parts(FavoriteVideo("BV1xx411c7mD", "旧标题"))
        assertEquals(listOf("BV1xx411c7mD:11", "BV1xx411c7mD:22"), tracks.map { it.id })
        assertEquals("https://i0.hdslb.com/cover.jpg", tracks.first().cover)
        assertEquals("专辑", tracks.first().videoTitle)
    }

    @Test fun apiErrorDoesNotExposeServerMessageOrSensitiveUrl() = runBlocking {
        respond("""{"code":-403,"message":"SESSDATA=secret https://cdn.example/file?token=secret"}""")
        val error = runCatching { repository.folders(123) }.exceptionOrNull()!!
        assertFalse(error.message!!.contains("secret"))
        assertFalse(error.message!!.contains("https://"))
    }

    @Test fun apiRedirectDoesNotForwardAuthentication() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://untrusted.invalid/"))
        assertTrue(runCatching { repository.folders(123) }.exceptionOrNull() is BiliApiException)
        assertEquals(1, server.requestCount)
    }

    private class MemorySessionStore : SessionStore {
        var value = ""
        override fun read() = value
        override fun write(cookie: String) { value = cookie }
        override fun clear() { value = "" }
    }
}
