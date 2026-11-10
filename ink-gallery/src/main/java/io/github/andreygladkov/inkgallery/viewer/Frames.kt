package io.github.andreygladkov.inkgallery.viewer

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import io.github.andreygladkov.ink.eink.GreyDither

/** A photo decoded at some size; [photo] is the full size, already turned upright by its EXIF orientation. */
class Decoded(val bitmap: Bitmap, val photo: Size)

object Frames {
    /** [sizeFor] picks the decoded size from the photo's full size, known only once its header is read. */
    fun decode(resolver: ContentResolver, uri: Uri, sizeFor: (Size) -> Size): Decoded {
        var photo: Size? = null
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            photo = info.size
            val target = sizeFor(info.size)
            if (target != info.size) decoder.setTargetSize(target.width, target.height)
            // Pixels are read back for dithering, which a hardware bitmap does not allow.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return Decoded(bitmap, requireNotNull(photo))
    }

    /**
     * The first frame of a video as a picture, since a video is shown in the viewer still and plays on a screen of
     * its own. Whether the frame comes already turned by the video's rotation is not documented, so a frame lying
     * across the upright size is turned here.
     */
    fun decodeVideoFrame(context: Context, uri: Uri, sizeFor: (Size) -> Size): Decoded {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val width = requireNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)).toInt()
            val height = requireNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)).toInt()
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val video = if (rotation == 90 || rotation == 270) Size(height, width) else Size(width, height)
            val frame = requireNotNull(retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
            val lyingAcross = (frame.width > frame.height) != (video.width > video.height) && frame.width != frame.height
            val upright = if (lyingAcross) Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { setRotate(rotation.toFloat()) }, true) else frame
            val target = sizeFor(video)
            return Decoded(upright.scale(target.width, target.height), video)
        } finally {
            retriever.release()
        }
    }

    fun render(source: Decoded, matrix: Matrix, view: Size, background: Int, dither: Boolean): Bitmap {
        val frame = createBitmap(view.width, view.height)
        val draw = Matrix().apply {
            setScale(source.photo.width.toFloat() / source.bitmap.width, source.photo.height.toFloat() / source.bitmap.height)
            postConcat(matrix)
        }
        Canvas(frame).apply {
            drawColor(background)
            drawBitmap(source.bitmap, draw, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (dither) GreyDither.quantize(frame)
        return frame
    }
}
