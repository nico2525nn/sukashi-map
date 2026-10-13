package jp.nico2525.sukashimap.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import jp.nico2525.sukashimap.overlay.OverlayItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Persists composed photos and plain video exports to MediaStore. */
class MediaSaver(private val context: Context) {

    data class SavedPhoto(val uri: Uri, val width: Int, val height: Int)

    suspend fun savePhoto(
        base: Bitmap,
        overlays: List<OverlayItem>,
        latitude: Double?,
        longitude: Double?,
    ): SavedPhoto? = withContext(Dispatchers.IO) {
        val composed = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composed)
        canvas.drawBitmap(base, 0f, 0f, null)
        for (item in overlays) {
            canvas.save()
            canvas.translate(item.x, item.y)
            canvas.scale(item.scale, item.scale)
            canvas.drawBitmap(item.bitmap, 0f, 0f, null)
            canvas.restore()
        }
        val name = "SUKASHI_${System.currentTimeMillis()}.jpg"
        val takenMs = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, takenMs)
            put(MediaStore.Images.Media.DATE_ADDED, takenMs / 1000)
            if (latitude != null && longitude != null) {
                put(MediaStore.Images.Media.LATITUDE, latitude)
                put(MediaStore.Images.Media.LONGITUDE, longitude)
            }
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SukashiMap")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        runCatching {
            resolver.openOutputStream(uri)?.use { composed.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            if (latitude != null && longitude != null) {
                runCatching { writeExifGps(uri, latitude, longitude) }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        }.onFailure {
            runCatching { resolver.delete(uri, null, null) }
            return@withContext null
        }
        if (composed != base) composed.recycle()
        SavedPhoto(uri, base.width, base.height)
    }

    private fun writeExifGps(uri: Uri, lat: Double, lon: Double) {
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            exif.setLatLong(lat, lon)
            exif.saveAttributes()
        }
    }

    /**
     * Export a recorded clip. Overlay burn-in is a future step; for now the
     * file is passed through Transformer so export always succeeds.
     */
    // TODO: burn overlay + logo into video frames once an OverlayEffect path is wired.
    suspend fun exportVideo(input: Uri): Uri? {
        // Transformer must be created/started on the app main thread.
        val outFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(input)).setEffects(Effects.EMPTY).build()
        val done: Uri? = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context).addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (cont.isActive) {
                                cont.resume(registerExportedVideo(outFile))
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                ).build()
                transformer.start(
                    Composition.Builder(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited))).build(),
                    outFile.absolutePath,
                )
                cont.invokeOnCancellation { transformer.cancel() }
            }
        }
        return done
    }

    private fun registerExportedVideo(file: File): Uri? {
        if (!file.exists()) return null
        val name = "SUKASHI_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SukashiMap")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            }
        }.onFailure {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        runCatching { file.delete() }
        return uri
    }
}
