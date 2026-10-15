package io.github.biliaudioplayer.update

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.Closeable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateRelease(val versionName: String, val versionCode: Long, val apkName: String, val sha256: String, val apkUrl: String)

/** Uses a separate anonymous HTTP client; Bilibili cookies and GitHub credentials never enter requests. */
class UpdateRepository(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build(),
    private val latestUrl: HttpUrl = "https://api.github.com/repos/Potat0Servers/BiliAudioPlayer/releases/latest".toHttpUrl(),
    private val requireHttps: Boolean = true,
    private val fallbackMetadataUrl: HttpUrl = "https://github.com/Potat0Servers/BiliAudioPlayer/releases/latest/download/update.json".toHttpUrl(),
    private val taggedDownloadBaseUrl: HttpUrl = "https://github.com/Potat0Servers/BiliAudioPlayer/releases/download/".toHttpUrl(),
) : Closeable {
    fun check(currentVersionCode: Long): UpdateRelease? {
        val json = request(latestUrl.toString()).use { response ->
            if (response.code == 403 || response.code == 429) return@use null
            if (response.code == 404) throw IOException("暂未发布可用更新")
            requireSuccess(response)
            JSONObject(readSmallBody(response))
        }
        if (json == null) return checkWithoutApi(currentVersionCode)
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) throw IOException("暂未发布正式更新")
        val assets = json.optJSONArray("assets") ?: throw IOException("更新附件缺失")
        val assetUrls = (0 until assets.length()).associate { index ->
            val asset = assets.getJSONObject(index)
            asset.optString("name") to asset.optString("browser_download_url")
        }
        val metadataUrl = assetUrls["update.json"] ?: throw IOException("缺少 update.json 更新说明")
        val metadata = request(metadataUrl).use { response -> requireSuccess(response); JSONObject(readSmallBody(response)) }
        return parseMetadata(metadata, currentVersionCode) { _, apkName ->
            assetUrls[apkName] ?: throw IOException("缺少更新 APK：$apkName")
        }
    }

    private fun checkWithoutApi(currentVersionCode: Long): UpdateRelease? {
        val metadata = request(fallbackMetadataUrl.toString()).use { response ->
            requireSuccess(response)
            JSONObject(readSmallBody(response))
        }
        return parseMetadata(metadata, currentVersionCode) { versionName, apkName ->
            taggedDownloadBaseUrl.newBuilder().addPathSegment("v$versionName").addPathSegment(apkName).build().toString()
        }
    }

    private fun parseMetadata(metadata: JSONObject, currentVersionCode: Long, apkAddress: (String, String) -> String): UpdateRelease? {
        val name = metadata.optString("versionName").trim()
        val code = metadata.optLong("versionCode", -1)
        val apkName = metadata.optString("apkName")
        val sha = metadata.optString("sha256").lowercase()
        if (!name.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) || code <= 0 || !apkName.matches(Regex("[A-Za-z0-9._-]+\\.apk")) || !sha.matches(Regex("[a-f0-9]{64}"))) {
            throw IOException("更新说明格式无效")
        }
        val apkUrl = apkAddress(name, apkName)
        validateUrl(apkUrl)
        return if (code > currentVersionCode) UpdateRelease(name, code, apkName, sha, apkUrl) else null
    }

    fun download(release: UpdateRelease, directory: File, onProgress: (Float?) -> Unit = {}): File {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("无法创建更新目录")
        val partial = File(directory, "${release.apkName}.part")
        val complete = File(directory, release.apkName)
        directory.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
        complete.delete()
        try {
            request(release.apkUrl).use { response ->
                requireSuccess(response)
                val body = response.body ?: throw IOException("下载内容为空")
                val size = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastProgress = -1
                onProgress(if (size > 0) 0f else null)
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val bytes = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(bytes)
                            if (count < 0) break
                            downloaded += count
                            if (downloaded > 512L * 1024 * 1024) throw IOException("更新包超出允许大小")
                            output.write(bytes, 0, count)
                            digest.update(bytes, 0, count)
                            val progress = if (size > 0) ((downloaded * 100 / size).toInt()).coerceIn(0, 100) else -1
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(if (progress >= 0) progress / 100f else null)
                            }
                        }
                    }
                }
                if (downloaded == 0L || (size >= 0 && size != downloaded)) throw IOException("更新包下载不完整，请重试")
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (actual != release.sha256) throw IOException("更新包校验失败，请重新下载")
                if (!partial.renameTo(complete)) throw IOException("无法保存更新包")
                onProgress(1f)
                return complete
            }
        } catch (error: Exception) {
            partial.delete()
            complete.delete()
            throw error
        }
    }

    private fun request(url: String): Response {
        validateUrl(url)
        return client.newCall(Request.Builder().url(url).header("Accept", "application/vnd.github+json").header("User-Agent", "BiliAudioPlayer-Update").build()).execute()
    }

    private fun validateUrl(url: String) {
        val parsed = runCatching { url.toHttpUrl() }.getOrNull() ?: throw IOException("更新下载地址无效")
        if (requireHttps && !parsed.isHttps) throw IOException("更新地址必须使用 HTTPS")
    }

    private fun requireSuccess(response: Response) {
        if (response.code == 403 || response.code == 429) throw IOException("GitHub 请求受限，请稍后重试")
        if (!response.isSuccessful) throw IOException("更新请求失败（HTTP ${response.code}）")
        if (requireHttps && !response.request.url.isHttps) throw IOException("更新连接必须使用 HTTPS")
    }

    private fun readSmallBody(response: Response): String {
        val body = response.body ?: throw IOException("更新说明为空")
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 1024 * 1024) throw IOException("更新说明内容过大")
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    override fun close() { client.dispatcher.cancelAll() }
}
