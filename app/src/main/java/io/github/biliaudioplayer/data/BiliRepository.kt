package io.github.biliaudioplayer.data

import android.content.Context
import io.github.biliaudioplayer.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class BiliApiException(val code: Int? = null, message: String) : IOException(message)
class SessionExpiredException : BiliApiException(-101, "登录已失效，请重新扫码登录")

/** Small, read-only Bilibili web API adapter. Signed media URLs are deliberately not persisted. */
class BiliRepository internal constructor(
    client: OkHttpClient,
    private val sessionStore: SessionStore,
    private val apiBaseUrl: HttpUrl = "https://api.bilibili.com/".toHttpUrl(),
    private val passportBaseUrl: HttpUrl = "https://passport.bilibili.com/".toHttpUrl(),
    private val allowLocalTestServer: Boolean = false,
    private val pageDelayMillis: Long = 250,
) : MusicRepository {
    constructor(context: Context) : this(
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).build(), EncryptedSessionStore(context),
    )

    // Do not forward authenticated requests through arbitrary redirects.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    @Volatile private var cookie = sessionStore.read()
    private val sessionLock = Any()
    private var sessionGeneration = 0L

    override fun cookieHeader(): String = cookie

    override fun logout() = synchronized(sessionLock) {
        sessionGeneration++
        cookie = ""
        sessionStore.clear()
    }

    override suspend fun currentUser(): UserProfile? {
        val savedCookie = cookie
        if (savedCookie.isBlank()) return null
        return try {
            profile(requestJson("x/web-interface/nav", authentication = savedCookie))
        } catch (_: SessionExpiredException) {
            synchronized(sessionLock) { if (cookie == savedCookie) logout() }
            null
        }
    }

    override suspend fun generateQr(): QrTicket {
        val data = requestJson("x/passport-login/web/qrcode/generate", passport = true, authentication = "")
            .optJSONObject("data") ?: malformed()
        val url = data.optString("url")
        val key = data.optString("qrcode_key")
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || !parsed.isHttps || !isBilibiliHost(parsed.host) || key.isBlank()) malformed()
        return QrTicket(url, key)
    }

    override suspend fun pollQr(key: String): QrLoginResult {
        val generation = synchronized(sessionLock) { sessionGeneration }
        val reply = request("x/passport-login/web/qrcode/poll", mapOf("qrcode_key" to key), passport = true, authentication = "")
        val root = parseJson(reply.body)
        checkCode(root)
        val data = root.optJSONObject("data") ?: malformed()
        return when (data.optInt("code", -1)) {
            86101 -> QrLoginResult(QrStatus.WAITING, "请使用哔哩哔哩 App 扫码")
            86090 -> QrLoginResult(QrStatus.SCANNED, "已扫码，请在哔哩哔哩 App 确认登录")
            86038 -> QrLoginResult(QrStatus.EXPIRED, "二维码已过期，请刷新")
            0 -> {
                val candidate = linkedMapOf<String, String>()
                mergeSetCookies(candidate, reply.cookies)
                val callback = data.optString("url").toHttpUrlOrNull()
                if (callback != null && trustedLoginCallback(callback)) {
                    mergeQueryCookies(candidate, callback)
                    if (candidate["SESSDATA"].isNullOrBlank()) {
                        // Current responses may issue cookies only on the one-time SSO callback.
                        val callbackReply = execute(callback, "", allowRedirectResponse = true)
                        mergeSetCookies(candidate, callbackReply.cookies)
                    }
                }
                val candidateHeader = candidate.entries.joinToString("; ") { "${it.key}=${it.value}" }
                if (candidate["SESSDATA"].isNullOrBlank()) {
                    throw BiliApiException(message = "扫码已确认，但未取得登录凭据，请刷新二维码重试")
                }
                // A success flag alone does not establish a usable session.
                profile(requestJson("x/web-interface/nav", authentication = candidateHeader))
                withContext(Dispatchers.IO) {
                    synchronized(sessionLock) {
                        if (sessionGeneration != generation) throw BiliApiException(message = "本次登录已取消")
                        sessionStore.write(candidateHeader)
                        cookie = candidateHeader
                    }
                }
                QrLoginResult(QrStatus.SUCCESS, "登录成功")
            }
            else -> throw BiliApiException(data.optInt("code"), "扫码状态异常，请刷新二维码重试")
        }
    }

    override suspend fun folders(mid: Long): List<FavoriteFolder> {
        require(mid > 0) { "用户 ID 无效" }
        return paginate("收藏夹", { page ->
            requestJson("x/v3/fav/folder/created/list", mapOf("up_mid" to "$mid", "pn" to "$page", "ps" to "20"))
                .optJSONObject("data") ?: malformed()
        }, "list", { it.optLong("id").takeIf { id -> id > 0 }?.toString() }) { item ->
            FavoriteFolder(item.getLong("id"), item.optString("title").ifBlank { "未命名收藏夹" },
                imageUrl(item.optString("cover")), item.optInt("media_count"))
        }
    }

    override suspend fun videos(folderId: Long): List<FavoriteVideo> {
        require(folderId > 0) { "收藏夹 ID 无效" }
        return paginate("收藏夹内容", { page ->
            requestJson("x/v3/fav/resource/list", mapOf("media_id" to "$folderId", "pn" to "$page", "ps" to "20",
                "platform" to "web", "order" to "mtime", "type" to "0"))
                .optJSONObject("data") ?: malformed()
        }, "medias", { item ->
            // Count non-video records for pagination progress but omit them from the playback library.
            "${item.optInt("type")}:${item.optLong("id")}:${item.optString("bvid", item.optString("bv_id"))}"
        }) { item ->
            if (item.optInt("type") != 2) return@paginate null
            val bvid = item.optString("bvid").ifBlank { item.optString("bv_id") }
            if (!BV_PATTERN.matches(bvid)) return@paginate null
            FavoriteVideo(bvid, item.optString("title").ifBlank { "已失效视频" }, imageUrl(item.optString("cover")),
                item.optJSONObject("upper")?.optString("name").orEmpty(), item.optInt("duration"),
                item.optInt("attr") and 1 == 0 && item.optString("title") != "已失效视频")
        }.distinctBy { it.bvid }
    }

    override suspend fun parts(video: FavoriteVideo): List<Track> {
        if (!video.available) throw BiliApiException(message = "该视频已失效，无法获取分 P")
        validateBvid(video.bvid)
        val data = requestJson("x/web-interface/view", mapOf("bvid" to video.bvid)).optJSONObject("data") ?: malformed()
        if (data.optInt("state", 0) < 0) throw BiliApiException(message = "该视频暂不可播放")
        val pages = data.optJSONArray("pages") ?: malformed()
        val title = data.optString("title").ifBlank { video.title }
        val cover = imageUrl(data.optString("pic")).ifBlank { video.cover }
        val owner = data.optJSONObject("owner")?.optString("name").orEmpty().ifBlank { video.owner }
        val tracks = (0 until pages.length()).mapNotNull { index ->
            val page = pages.optJSONObject(index) ?: return@mapNotNull null
            val cid = page.optLong("cid")
            if (cid <= 0) return@mapNotNull null
            Track(video.bvid, cid, page.optInt("page", index + 1), page.optString("part").ifBlank { title },
                title, cover, owner, page.optInt("duration"))
        }.distinctBy { it.id }.sortedBy { it.page }
        if (tracks.isEmpty()) throw BiliApiException(message = "这个视频没有可用分 P")
        return tracks
    }

    override suspend fun audio(bvid: String, cid: Long): AudioStream {
        validateBvid(bvid)
        require(cid > 0) { "分 P ID 无效" }
        val query = mapOf("bvid" to bvid, "cid" to "$cid", "fnval" to "16", "fnver" to "0", "qn" to "64")
        val data = requestJson("x/player/playurl", query).optJSONObject("data") ?: malformed()
        return parseAudio(data)
    }

    private suspend fun <T> paginate(
        label: String, load: suspend (Int) -> JSONObject, arrayKey: String,
        identity: (JSONObject) -> String?, transform: (JSONObject) -> T?,
    ): List<T> {
        val result = mutableListOf<T>()
        val seen = mutableSetOf<String>()
        for (page in 1..MAX_PAGES) {
            val data = load(page)
            val rows = data.optJSONArray(arrayKey) ?: JSONArray()
            val more = data.optBoolean("has_more", false)
            if (page == 1 && rows.length() == 0 && (data.optJSONObject("info")?.optInt("media_count") ?: 0) > 0) {
                throw BiliApiException(message = "$label 暂未返回内容，请刷新或重新登录")
            }
            var added = 0
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val id = identity(item) ?: continue
                if (seen.add(id)) {
                    added++
                    transform(item)?.let { result.add(it) }
                }
            }
            if (!more) return result
            if (rows.length() == 0 || added == 0) throw BiliApiException(message = "$label 分页异常，请稍后刷新")
            if (page < MAX_PAGES && pageDelayMillis > 0) delay(pageDelayMillis)
        }
        throw BiliApiException(message = "$label 超出单次同步上限，请缩小收藏夹后重试")
    }

    private suspend fun requestJson(path: String, query: Map<String, String> = emptyMap(), passport: Boolean = false,
        authentication: String = cookie): JSONObject {
        val result = request(path, query, passport, authentication)
        return parseJson(result.body).also { checkCode(it) }
    }

    private suspend fun request(path: String, query: Map<String, String> = emptyMap(), passport: Boolean = false,
        authentication: String = cookie): ApiResponse {
        val base = if (passport) passportBaseUrl else apiBaseUrl
        val builder = base.newBuilder().addPathSegments(path)
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return execute(builder.build(), authentication)
    }

    private suspend fun execute(url: HttpUrl, authentication: String, allowRedirectResponse: Boolean = false): ApiResponse {
        val testHost = allowLocalTestServer && url.host in setOf("localhost", "127.0.0.1", "::1")
        if (!url.isHttps && !testHost) throw BiliApiException(message = "拒绝不安全的网络连接")
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Referer", "https://www.bilibili.com/")
        if (authentication.isNotBlank() && (url.host == "api.bilibili.com" || url.host == "passport.bilibili.com" || testHost)) {
            request.header("Cookie", authentication)
        }
        val call = client.newCall(request.build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(BiliApiException(message = "网络连接失败，请检查网络后重试"))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!response.isSuccessful && !(allowRedirectResponse && response.code in 300..399)) {
                                val message = when (response.code) {
                                    412, 429 -> "请求暂时受限，请稍后重试"
                                    403 -> "资源访问被拒绝，请刷新或重新登录"
                                    else -> "网络请求失败（HTTP ${response.code}）"
                                }
                                throw BiliApiException(response.code, message)
                            }
                            val result = ApiResponse(response.body?.string().orEmpty(), response.headers.values("Set-Cookie"))
                            if (continuation.isActive) continuation.resume(result)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(
                                e as? BiliApiException ?: BiliApiException(message = "读取服务器响应失败，请重试"))
                        }
                    }
                }
            })
        }
    }

    private data class ApiResponse(val body: String, val cookies: List<String>)

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        internal const val MAX_PAGES = 500
        private val BV_PATTERN = Regex("BV[0-9A-Za-z]{10}")
        private val COOKIE_NAMES = setOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")

        private fun validateBvid(bvid: String) {
            require(BV_PATTERN.matches(bvid)) { "视频 BV 号无效" }
        }
        private fun malformed(): Nothing = throw BiliApiException(message = "服务器返回了无法识别的数据，请稍后重试")
        private fun parseJson(body: String): JSONObject = try { JSONObject(body) } catch (_: Exception) { malformed() }
        private fun checkCode(root: JSONObject) {
            val code = root.optInt("code", Int.MIN_VALUE)
            if (code == 0) return
            if (code == -101) throw SessionExpiredException()
            val message = when (code) {
                -403 -> "没有访问权限，内容可能已失效或需要登录"
                -404, 62002, 62004 -> "视频不存在或已删除"
                -352, -412, 412, 429 -> "请求暂时受限，请稍后重试"
                -400 -> "请求参数无效，请刷新后重试"
                else -> "B站接口暂不可用（错误码 $code）"
            }
            throw BiliApiException(code, message)
        }
        private fun profile(root: JSONObject): UserProfile {
            val data = root.optJSONObject("data") ?: malformed()
            if (!data.optBoolean("isLogin") || data.optLong("mid") <= 0) throw SessionExpiredException()
            return UserProfile(data.getLong("mid"), data.optString("uname").ifBlank { "B站用户" }, imageUrl(data.optString("face")))
        }
        private fun isBilibiliHost(host: String): Boolean = host == "bilibili.com" || host.endsWith(".bilibili.com")
        private fun trustedLoginCallback(url: HttpUrl): Boolean = url.isHttps &&
            (isBilibiliHost(url.host) || url.host == "passport.biligame.com")
        private fun validCookieValue(value: String): Boolean = value.isNotBlank() && value.all { it.code in 33..126 && it != ';' }
        internal fun mergeSetCookies(target: MutableMap<String, String>, headers: List<String>) {
            headers.forEach { header ->
                val pair = header.substringBefore(';').split('=', limit = 2)
                if (pair.size == 2 && pair[0].trim() in COOKIE_NAMES && validCookieValue(pair[1])) target[pair[0].trim()] = pair[1]
            }
        }
        private fun mergeQueryCookies(target: MutableMap<String, String>, url: HttpUrl) {
            COOKIE_NAMES.forEach { name ->
                url.queryParameter(name)?.takeIf { validCookieValue(it) }?.let { decoded ->
                    // HttpUrl decodes once. Keep existing %XX escapes; canonicalize raw comma separators.
                    val value = if (name == "SESSDATA") decoded.replace(",", "%2C") else decoded
                    target.putIfAbsent(name, value)
                }
            }
        }
        internal fun imageUrl(value: String): String {
            val candidate = if (value.startsWith("//")) "https:$value" else value
            val url = candidate.toHttpUrlOrNull() ?: return ""
            return url.newBuilder().scheme("https").build().toString()
        }
        internal fun parseAudio(data: JSONObject): AudioStream {
            val audio = data.optJSONObject("dash")?.optJSONArray("audio") ?: JSONArray()
            val candidates = (0 until audio.length()).mapNotNull { audio.optJSONObject(it) }
                .filter { it.optString("codecs").startsWith("mp4a") || it.optString("codecs").isBlank() }
                .sortedByDescending { it.optLong("bandwidth") }
            for (candidate in candidates) {
                val main = mediaUrl(candidate.optString("baseUrl").ifBlank { candidate.optString("base_url") }) ?: continue
                val backup = candidate.optJSONArray("backupUrl") ?: candidate.optJSONArray("backup_url") ?: JSONArray()
                return AudioStream(main, (0 until backup.length()).mapNotNull { mediaUrl(backup.optString(it)) }.distinct(), "audio/mp4")
            }
            // durl can be muxed video and segmented. Do not silently download video in an audio-only app.
            if ((data.optJSONArray("durl")?.length() ?: 0) > 0) {
                throw BiliApiException(message = "该内容暂未提供独立音轨，暂不能纯音频播放")
            }
            throw BiliApiException(message = "没有可播放的音轨，内容可能受权限或地区限制")
        }
        private fun mediaUrl(value: String): String? {
            val url = value.toHttpUrlOrNull() ?: return null
            if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
            return url.toString()
        }
    }
}
