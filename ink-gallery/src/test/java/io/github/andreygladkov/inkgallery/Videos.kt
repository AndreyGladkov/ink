package io.github.andreygladkov.inkgallery

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

/** Makes [uri] playable for the shadow player and gives it a first frame and size for the shadow retriever. */
fun registerVideo(context: Context, uri: Uri, durationMs: Int, frame: Bitmap = FakeMedia.ramp(48, 32)) {
    val source = DataSource.toDataSource(context, uri)
    ShadowMediaPlayer.addMediaInfo(source, ShadowMediaPlayer.MediaInfo(durationMs, 0))
    ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, frame.width.toString())
    ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, frame.height.toString())
    ShadowMediaMetadataRetriever.addFrame(source, 0, frame)
}
