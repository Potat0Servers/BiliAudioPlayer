package io.github.biliaudioplayer.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import io.github.biliaudioplayer.model.Track
import io.github.biliaudioplayer.model.FavoriteFolder

internal fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri("biliaudio://$bvid/$cid")
    .setMimeType(MimeTypes.AUDIO_MP4)
    .setMediaMetadata(MediaMetadata.Builder()
        .setTitle(displayTitle).setArtist(owner).setAlbumTitle(videoTitle)
        .setArtworkUri(cover.takeIf { it.isNotBlank() }?.let(Uri::parse))
        .setExtras(Bundle().apply {
            putString("bvid", bvid); putLong("cid", cid); putInt("page", page)
            putString("videoTitle", videoTitle); putInt("durationSeconds", durationSeconds)
            sourceFolder?.let { folder ->
                putLong("sourceFolderId", folder.id); putString("sourceFolderTitle", folder.title)
                putString("sourceFolderCover", folder.cover); putInt("sourceFolderCount", folder.mediaCount)
            }
        }).build()).build()

internal fun MediaItem.toTrack(): Track? {
    val extras = mediaMetadata.extras ?: return null
    val bvid = extras.getString("bvid") ?: return null
    return Track(bvid, extras.getLong("cid"), extras.getInt("page"), mediaMetadata.title?.toString().orEmpty(),
        extras.getString("videoTitle").orEmpty(), mediaMetadata.artworkUri?.toString().orEmpty(),
        mediaMetadata.artist?.toString().orEmpty(), extras.getInt("durationSeconds"),
        if (extras.containsKey("sourceFolderId")) FavoriteFolder(extras.getLong("sourceFolderId"),
            extras.getString("sourceFolderTitle").orEmpty(), extras.getString("sourceFolderCover").orEmpty(),
            extras.getInt("sourceFolderCount")) else null)
}
