package jp.nico2525.sukashimap.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import jp.nico2525.sukashimap.model.CropFrameSettings
import jp.nico2525.sukashimap.model.ExportFormat
import jp.nico2525.sukashimap.model.OverlayStyle
import jp.nico2525.sukashimap.model.TransportInfo
import jp.nico2525.sukashimap.render.MapDatasetSlice
import jp.nico2525.sukashimap.render.OverlayCanvasRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

data class MediaMetadataExtract(
    val lat: Double?,
    val lon: Double?,
    val year: Int?,
    val month: Int?,
    val day: Int?,
    val hour: Int?,
    val minute: Int?,
    val isVideo: Boolean,
    val width: Int,
    val height: Int
)

object MediaExporter {

    suspend fun extractMediaMetadata(context: Context, uri: Uri): MediaMetadataExtract = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val isVideo = mime.startsWith("video/") || uri.toString().endsWith(".mp4", ignoreCase = true)

        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
                val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val (effW, effH) = if (rot == 90 || rot == 270) h to w else w to h
                val locStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                val (lat, lon) = parseIso6709Location(locStr)
                return@withContext MediaMetadataExtract(lat, lon, null, null, null, null, null, true, effW, effH)
            } catch (_: Exception) {
                return@withContext MediaMetadataExtract(null, null, null, null, null, null, null, true, 1920, 1080)
            } finally {
                runCatching { retriever.release() }
            }
        }

        var lat: Double? = null
        var lon: Double? = null
        var yr: Int? = null
        var mo: Int? = null
        var dy: Int? = null
        var hr: Int? = null
        var mn: Int? = null
        var imgW = 1920
        var imgH = 1440

        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                imgW = opts.outWidth
                imgH = opts.outHeight
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.latLong?.let {
                    lat = it[0]
                    lon = it[1]
                }
                val orient = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                if (orient == ExifInterface.ORIENTATION_ROTATE_90 || orient == ExifInterface.ORIENTATION_ROTATE_270) {
                    val tmp = imgW; imgW = imgH; imgH = tmp
                }
                val dt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (!dt.isNullOrBlank() && dt.length >= 16) {
                    yr = dt.substring(0, 4).toIntOrNull()
                    mo = dt.substring(5, 7).toIntOrNull()
                    dy = dt.substring(8, 10).toIntOrNull()
                    hr = dt.substring(11, 13).toIntOrNull()
                    mn = dt.substring(14, 16).toIntOrNull()
                }
            }
        } catch (_: Exception) {
        }

        MediaMetadataExtract(lat, lon, yr, mo, dy, hr, mn, false, imgW, imgH)
    }

    internal fun computeSourceCropRect(srcW: Int, srcH: Int, settings: CropFrameSettings): Rect {
        val targetAspect = settings.effectiveAspect(srcW, srcH)
        val zoom = settings.cropZoom.coerceIn(1f, 5f)
        val srcAspect = srcW.toFloat() / srcH.coerceAtLeast(1)
        val baseCropW: Float
        val baseCropH: Float
        if (srcAspect > targetAspect) {
            baseCropH = srcH.toFloat()
            baseCropW = baseCropH * targetAspect
        } else {
            baseCropW = srcW.toFloat()
            baseCropH = baseCropW / targetAspect
        }
        val cropW = (baseCropW / zoom).coerceAtLeast(32f)
        val cropH = (baseCropH / zoom).coerceAtLeast(32f)
        val minCx = cropW * 0.5f
        val maxCx = (srcW - cropW * 0.5f).coerceAtLeast(minCx)
        val minCy = cropH * 0.5f
        val maxCy = (srcH - cropH * 0.5f).coerceAtLeast(minCy)
        val cx = (settings.cropCenterX.coerceIn(0f, 1f) * srcW).coerceIn(minCx, maxCx)
        val cy = (settings.cropCenterY.coerceIn(0f, 1f) * srcH).coerceIn(minCy, maxCy)

        return Rect(
            (cx - cropW * 0.5f).roundToInt().coerceAtLeast(0),
            (cy - cropH * 0.5f).roundToInt().coerceAtLeast(0),
            (cx + cropW * 0.5f).roundToInt().coerceAtMost(srcW),
            (cy + cropH * 0.5f).roundToInt().coerceAtMost(srcH)
        )
    }

    fun applyCropAndFrameToBitmap(source: Bitmap, settings: CropFrameSettings): Bitmap {
        val srcRect = computeSourceCropRect(source.width, source.height, settings)
        val outW = srcRect.width().coerceAtLeast(64)
        val outH = srcRect.height().coerceAtLeast(64)
        val outBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)

        val margin = settings.frameMarginRatio.coerceIn(0f, 0.49f)
        if (margin > 0.001f) {
            canvas.drawColor(settings.frameColorArgb)
            val innerScale = 1f - margin
            val innerW = outW * innerScale
            val innerH = outH * innerScale
            val left = (outW - innerW) * 0.5f
            val top = (outH - innerH) * 0.5f
            val dstRect = RectF(left, top, left + innerW, top + innerH)
            canvas.drawBitmap(source, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            canvas.drawBitmap(source, srcRect, Rect(0, 0, outW, outH), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return outBmp
    }

    suspend fun decodeOrientedBitmap(context: Context, uri: Uri, maxLongEdge: Int = 4096): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongEdge) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: return@withContext null

                val orient = context.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL

                val matrix = Matrix()
                when (orient) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                }
                if (matrix.isIdentity) {
                    raw
                } else {
                    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                    if (rotated !== raw) raw.recycle()
                    rotated
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun exportPhotoToMediaStore(
        context: Context,
        sourceUri: Uri,
        info: TransportInfo,
        style: OverlayStyle,
        cropFrame: CropFrameSettings,
        mapSlice: MapDatasetSlice?,
        format: ExportFormat,
        preserveUltraHdr: Boolean
    ): Uri? = withContext(Dispatchers.IO) {
        val srcBitmap = decodeOrientedBitmap(context, sourceUri, maxLongEdge = 4600) ?: return@withContext null
        val srcCropRect = computeSourceCropRect(srcBitmap.width, srcBitmap.height, cropFrame)
        val stagedBitmap = applyCropAndFrameToBitmap(srcBitmap, cropFrame)

        // Render overlay onto a separate transparent ARGB_8888 bitmap so we can ALSO neutralize Ultra HDR gainmap boost under SDR text/lines!
        val overlayLayer = Bitmap.createBitmap(stagedBitmap.width, stagedBitmap.height, Bitmap.Config.ARGB_8888)
        val overlayCanvas = Canvas(overlayLayer)
        OverlayCanvasRenderer.render(
            context = context,
            canvas = overlayCanvas,
            canvasWidth = stagedBitmap.width.toFloat(),
            canvasHeight = stagedBitmap.height.toFloat(),
            info = info,
            style = style,
            mapSlice = mapSlice
        )

        // Composite overlay onto staged photo
        Canvas(stagedBitmap).drawBitmap(overlayLayer, 0f, 0f, null)

        // Crop-aligned & SDR-overlay-neutralized Ultra HDR Gainmap preservation (API 34+)
        if (preserveUltraHdr &&
            format == ExportFormat.JPEG_95 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            srcBitmap.gainmap?.let { gm ->
                val origGmBmp = gm.gainmapContents
                val gmSrcRect = Rect(
                    (srcCropRect.left.toFloat() / srcBitmap.width * origGmBmp.width).roundToInt().coerceIn(0, origGmBmp.width - 1),
                    (srcCropRect.top.toFloat() / srcBitmap.height * origGmBmp.height).roundToInt().coerceIn(0, origGmBmp.height - 1),
                    (srcCropRect.right.toFloat() / srcBitmap.width * origGmBmp.width).roundToInt().coerceIn(1, origGmBmp.width),
                    (srcCropRect.bottom.toFloat() / srcBitmap.height * origGmBmp.height).roundToInt().coerceIn(1, origGmBmp.height)
                )
                val gmW = (stagedBitmap.width / 4).coerceAtLeast(1)
                val gmH = (stagedBitmap.height / 4).coerceAtLeast(1)
                val newGmBmp = Bitmap.createBitmap(gmW, gmH, Bitmap.Config.ARGB_8888)
                val gmCanvas = Canvas(newGmBmp)
                // Black (0) in log gainmap = neutral 1.0x SDR headroom (prevents blowing out white frame & SDR text!)
                gmCanvas.drawColor(Color.BLACK)

                val margin = cropFrame.frameMarginRatio.coerceIn(0f, 0.49f)
                val dstGmRect = if (margin > 0.001f) {
                    val innerScale = 1f - margin
                    val iw = gmW * innerScale
                    val ih = gmH * innerScale
                    RectF((gmW - iw) * 0.5f, (gmH - ih) * 0.5f, (gmW + iw) * 0.5f, (gmH + ih) * 0.5f)
                } else {
                    RectF(0f, 0f, gmW.toFloat(), gmH.toFloat())
                }
                // Color.WHITE ensures single-channel ALPHA_8 Ultra HDR gainmaps write (g, g, g) instead of black!
                val gmPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = Color.WHITE }
                gmCanvas.drawBitmap(origGmBmp, gmSrcRect, dstGmRect, gmPaint)

                // Neutralize gainmap wherever overlay alpha > 64 so white text/coastlines stay crisp
                val scaledOverlay = Bitmap.createScaledBitmap(overlayLayer, gmW, gmH, true)
                val gmPixels = IntArray(gmW * gmH)
                val ovPixels = IntArray(gmW * gmH)
                newGmBmp.getPixels(gmPixels, 0, gmW, 0, 0, gmW, gmH)
                scaledOverlay.getPixels(ovPixels, 0, gmW, 0, 0, gmW, gmH)
                for (i in gmPixels.indices) {
                    if ((ovPixels[i] ushr 24) > 64) {
                        gmPixels[i] = Color.BLACK
                    }
                }
                newGmBmp.setPixels(gmPixels, 0, gmW, 0, 0, gmW, gmH)
                scaledOverlay.recycle()

                gm.gainmapContents = newGmBmp
                stagedBitmap.gainmap = gm
            }
        }

        overlayLayer.recycle()
        if (stagedBitmap !== srcBitmap) srcBitmap.recycle()

        val savedUri = saveBitmapWithExif(
            context = context,
            bitmap = stagedBitmap,
            format = format,
            info = info
        )
        stagedBitmap.recycle()
        savedUri
    }

    suspend fun saveBitmapWithExif(
        context: Context,
        bitmap: Bitmap,
        format: ExportFormat,
        info: TransportInfo?,
        filenamePrefix: String = "SukashiMap"
    ): Uri? = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "${filenamePrefix}_$stamp.${format.extension}"

        val tmpDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val tmpFile = File(tmpDir, displayName)
        try {
            tmpFile.outputStream().use { out ->
                val compressFmt = if (format == ExportFormat.PNG_LOSSLESS) {
                    Bitmap.CompressFormat.PNG
                } else {
                    Bitmap.CompressFormat.JPEG
                }
                bitmap.compress(compressFmt, 95, out)
            }

            if (format == ExportFormat.JPEG_95) {
                runCatching {
                    val exif = ExifInterface(tmpFile)
                    exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Sukashi Map")
                    if (info != null) {
                        val dtStr = String.format(
                            Locale.US,
                            "%04d:%02d:%02d %02d:%02d:00",
                            info.year,
                            info.month,
                            info.day,
                            info.hour,
                            info.minute
                        )
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dtStr)
                        exif.setAttribute(ExifInterface.TAG_DATETIME, dtStr)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dtStr)
                        val lat = info.photoLat ?: info.startCoord()?.lat
                        val lon = info.photoLon ?: info.startCoord()?.lon
                        if (lat != null && lon != null) {
                            exif.setLatLong(lat, lon)
                        }
                    }
                    exif.saveAttributes()
                }
            }

            val dateTakenMillis = if (info != null) {
                Calendar.getInstance().apply {
                    set(info.year, (info.month - 1).coerceIn(0, 11), info.day, info.hour, info.minute, 0)
                }.timeInMillis
            } else {
                System.currentTimeMillis()
            }

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                put(MediaStore.Images.Media.DATE_TAKEN, dateTakenMillis)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SukashiMap")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val destUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
            resolver.openOutputStream(destUri)?.use { out ->
                tmpFile.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)
            destUri
        } finally {
            runCatching { tmpFile.delete() }
        }
    }

    /**
     * Original-Resolution Video Export (no 1080p downscale cap,
     * with HDR10/HLG preservation + automatic OpenGL tone-map SDR fallback).
     */
    suspend fun exportVideoOriginalQuality(
        context: Context,
        sourceVideoUri: Uri,
        info: TransportInfo,
        style: OverlayStyle,
        cropFrame: CropFrameSettings,
        mapSlice: MapDatasetSlice?,
        preserveHdr: Boolean
    ): Uri? {
        val meta = extractMediaMetadata(context, sourceVideoUri)
        val vidW = meta.width.coerceAtLeast(320)
        val vidH = meta.height.coerceAtLeast(320)

        val overlayBitmap = withContext(Dispatchers.IO) {
            val bmp = Bitmap.createBitmap(vidW, vidH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            // Optional social matte border punch-out on video
            val margin = cropFrame.frameMarginRatio.coerceIn(0f, 0.49f)
            if (margin > 0.001f) {
                val borderPaint = Paint().apply {
                    color = cropFrame.frameColorArgb
                    this.style = Paint.Style.FILL
                }
                val padX = (vidW * margin * 0.5f)
                val padY = (vidH * margin * 0.5f)
                canvas.drawRect(0f, 0f, vidW.toFloat(), padY, borderPaint)
                canvas.drawRect(0f, vidH - padY, vidW.toFloat(), vidH.toFloat(), borderPaint)
                canvas.drawRect(0f, padY, padX, vidH - padY, borderPaint)
                canvas.drawRect(vidW - padX, padY, vidW.toFloat(), vidH - padY, borderPaint)
            }
            OverlayCanvasRenderer.render(
                context = context,
                canvas = canvas,
                canvasWidth = vidW.toFloat(),
                canvasHeight = vidH.toFloat(),
                info = info,
                style = style,
                mapSlice = mapSlice
            )
            bmp
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(File(context.cacheDir, "exports").apply { mkdirs() }, "SukashiMap_$stamp.mp4")

        suspend fun runTransform(hdrMode: Int) = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap)
                val overlayEffect = OverlayEffect(listOf<TextureOverlay>(bitmapOverlay))
                val effects = Effects(emptyList(), listOf<Effect>(overlayEffect))
                val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(sourceVideoUri))
                    .setEffects(effects)
                    .build()
                val composition = Composition.Builder(EditedMediaItemSequenceCompat.single(editedItem))
                    .setHdrMode(hdrMode)
                    .build()

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()

                transformer.start(composition, outFile.absolutePath)
                cont.invokeOnCancellation { transformer.cancel() }
            }
        }

        try {
            val primaryHdrMode = if (preserveHdr) {
                Composition.HDR_MODE_KEEP_HDR
            } else {
                Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            }
            try {
                runTransform(primaryHdrMode)
            } catch (hdrErr: Exception) {
                if (preserveHdr) {
                    outFile.delete()
                    runTransform(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                } else {
                    throw hdrErr
                }
            }
        } finally {
            overlayBitmap.recycle()
        }

        return withContext(Dispatchers.IO) {
            try {
                if (!outFile.exists()) return@withContext null
                val dateTakenMillis = Calendar.getInstance().apply {
                    set(info.year, (info.month - 1).coerceIn(0, 11), info.day, info.hour, info.minute, 0)
                }.timeInMillis
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, outFile.name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.DATE_TAKEN, dateTakenMillis)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/SukashiMap")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val destUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
                resolver.openOutputStream(destUri)?.use { out ->
                    outFile.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(destUri, values, null, null)
                destUri
            } finally {
                runCatching { outFile.delete() }
            }
        }
    }

    fun launchShareChooser(context: Context, mediaUri: Uri, mimeType: String = "image/*") {
        val shareUri = if (mediaUri.scheme == "file") {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(mediaUri.path!!))
        } else {
            mediaUri
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Sukashi Map").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun parseIso6709Location(raw: String?): Pair<Double?, Double?> {
        if (raw.isNullOrBlank()) return null to null
        val regex = Regex("""([+-]\d+\.\d+)([+-]\d+\.\d+)""")
        val match = regex.find(raw) ?: return null to null
        val lat = match.groupValues[1].toDoubleOrNull()
        val lon = match.groupValues[2].toDoubleOrNull()
        return lat to lon
    }
}

private object EditedMediaItemSequenceCompat {
    fun single(item: EditedMediaItem): androidx.media3.transformer.EditedMediaItemSequence {
        return androidx.media3.transformer.EditedMediaItemSequence.Builder(item).build()
    }
}
