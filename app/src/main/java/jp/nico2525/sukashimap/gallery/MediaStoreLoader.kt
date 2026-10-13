package jp.nico2525.sukashimap.gallery

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One row from MediaStore Images or Video. */
data class GalleryItem(
    val key: String,
    val uri: Uri,
    val isVideo: Boolean,
    val displayName: String?,
    val relativePath: String?,
    val dateAddedSec: Long,
    val dateTakenMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

enum class GallerySort { BY_ADDED, BY_TAKEN }

/**
 * MediaStore Images + Video loader.
 *
 * Own-photo filter: RELATIVE_PATH or DISPLAY_NAME containing "sukashi"
 * (case-insensitive). Everything else is "all photos" mode.
 */
class MediaStoreLoader(private val context: Context) {

    suspend fun load(ownOnly: Boolean, sort: GallerySort): List<GalleryItem> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<GalleryItem>()
            out += queryImages(ownOnly)
            out += queryVideos(ownOnly)
            when (sort) {
                GallerySort.BY_ADDED -> out.sortByDescending { it.dateAddedSec }
                GallerySort.BY_TAKEN -> out.sortByDescending { effectiveTaken(it) }
            }
            out
        }

    /** EXIF GPS fallback for items whose MediaStore lat/lon columns are empty. */
    suspend fun ensureLocation(item: GalleryItem): GalleryItem =
        if (item.hasLocation || item.isVideo) item
        else withContext(Dispatchers.IO) {
            val latLon = readExifLatLon(item.uri) ?: return@withContext item
            item.copy(latitude = latLon[0], longitude = latLon[1])
        }

    fun observe(onChange: () -> Unit): ContentObserver {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        val cr = context.contentResolver
        cr.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, obs)
        cr.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, obs)
        return obs
    }

    fun unobserve(obs: ContentObserver) {
        context.contentResolver.unregisterContentObserver(obs)
    }

    private fun queryImages(ownOnly: Boolean): List<GalleryItem> {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.LATITUDE,
            MediaStore.Images.Media.LONGITUDE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
        )
        val out = ArrayList<GalleryItem>()
        context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
            val id = c.idx(MediaStore.Images.Media._ID)
            while (c.moveToNext()) {
                val name = c.strOrNull(MediaStore.Images.Media.DISPLAY_NAME)
                val path = c.strOrNull(MediaStore.Images.Media.RELATIVE_PATH)
                if (ownOnly && !isOwn(name, path)) continue
                val rowId = c.getLong(id)
                out += GalleryItem(
                    key = "image:$rowId",
                    uri = Uri.withAppendedPath(uri, rowId.toString()),
                    isVideo = false,
                    displayName = name,
                    relativePath = path,
                    dateAddedSec = c.longOrZero(MediaStore.Images.Media.DATE_ADDED),
                    dateTakenMs = c.longOrZero(MediaStore.Images.Media.DATE_TAKEN),
                    latitude = c.doubleOrNull(MediaStore.Images.Media.LATITUDE),
                    longitude = c.doubleOrNull(MediaStore.Images.Media.LONGITUDE),
                    durationMs = 0L,
                    width = c.intOrZero(MediaStore.Images.Media.WIDTH),
                    height = c.intOrZero(MediaStore.Images.Media.HEIGHT),
                    sizeBytes = c.longOrZero(MediaStore.Images.Media.SIZE),
                )
            }
        }
        return out
    }

    private fun queryVideos(ownOnly: Boolean): List<GalleryItem> {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.LATITUDE,
            MediaStore.Video.Media.LONGITUDE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
        )
        val out = ArrayList<GalleryItem>()
        context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
            val id = c.idx(MediaStore.Video.Media._ID)
            while (c.moveToNext()) {
                val name = c.strOrNull(MediaStore.Video.Media.DISPLAY_NAME)
                val path = c.strOrNull(MediaStore.Video.Media.RELATIVE_PATH)
                if (ownOnly && !isOwn(name, path)) continue
                val rowId = c.getLong(id)
                out += GalleryItem(
                    key = "video:$rowId",
                    uri = Uri.withAppendedPath(uri, rowId.toString()),
                    isVideo = true,
                    displayName = name,
                    relativePath = path,
                    dateAddedSec = c.longOrZero(MediaStore.Video.Media.DATE_ADDED),
                    dateTakenMs = c.longOrZero(MediaStore.Video.Media.DATE_TAKEN),
                    latitude = c.doubleOrNull(MediaStore.Video.Media.LATITUDE),
                    longitude = c.doubleOrNull(MediaStore.Video.Media.LONGITUDE),
                    durationMs = c.longOrZero(MediaStore.Video.Media.DURATION),
                    width = c.intOrZero(MediaStore.Video.Media.WIDTH),
                    height = c.intOrZero(MediaStore.Video.Media.HEIGHT),
                    sizeBytes = c.longOrZero(MediaStore.Video.Media.SIZE),
                )
            }
        }
        return out
    }

    private fun readExifLatLon(uri: Uri): DoubleArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                ExifInterface(ins).latLong
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun isOwn(displayName: String?, relativePath: String?): Boolean {
            val n = (displayName ?: "").lowercase()
            val p = (relativePath ?: "").lowercase()
            return n.contains("sukashi") || p.contains("sukashi")
        }

        fun effectiveTaken(item: GalleryItem): Long =
            if (item.dateTakenMs > 0) item.dateTakenMs else item.dateAddedSec * 1000L
    }
}

private fun Cursor.idx(column: String): Int = getColumnIndexOrThrow(column)

private fun Cursor.strOrNull(column: String): String? {
    val i = getColumnIndex(column)
    return if (i < 0 || isNull(i)) null else getString(i)
}

private fun Cursor.longOrZero(column: String): Long {
    val i = getColumnIndex(column)
    return if (i < 0 || isNull(i)) 0L else getLong(i)
}

private fun Cursor.intOrZero(column: String): Int {
    val i = getColumnIndex(column)
    return if (i < 0 || isNull(i)) 0 else getInt(i)
}

private fun Cursor.doubleOrNull(column: String): Double? {
    val i = getColumnIndex(column)
    return if (i < 0 || isNull(i)) null else getDouble(i)
}
