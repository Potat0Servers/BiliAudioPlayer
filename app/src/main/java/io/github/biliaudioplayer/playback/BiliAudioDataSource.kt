package io.github.biliaudioplayer.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.common.util.UnstableApi
import io.github.biliaudioplayer.data.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Resolution runs on ExoPlayer's loader thread. Stream addresses never enter saved media items. */
@UnstableApi
class BiliAudioDataSource(private val repository: MusicRepository) : DataSource {
    private val upstream = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 BiliAudioPlayer/1.0")
        .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com/"))
        .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000).createDataSource()

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val bvid = uri.host.orEmpty()
        val cid = uri.lastPathSegment?.toLongOrNull()
        if (uri.scheme != "biliaudio" || !bvid.matches(Regex("BV[0-9A-Za-z]{10}")) || cid == null || cid <= 0) {
            throw IOException("无效的歌曲标识")
        }
        // A new play/seek/retry gets a fresh signed URL; one more resolution also covers expired CDN URLs.
        repeat(2) {
            val stream = try { runBlocking(Dispatchers.IO) { repository.audio(bvid, cid) } }
            catch (e: Exception) { throw IOException(e.message ?: "无法获取音频，请检查登录状态") }
            for (url in (listOf(stream.url) + stream.backupUrls).distinct()) {
                if (!url.startsWith("https://")) continue
                try { return upstream.open(dataSpec.withUri(Uri.parse(url))) }
                catch (_: IOException) { runCatching { upstream.close() } }
            }
        }
        throw IOException("音频连接失败，请检查网络或重新登录后重试")
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)
    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun close() = upstream.close()
}
