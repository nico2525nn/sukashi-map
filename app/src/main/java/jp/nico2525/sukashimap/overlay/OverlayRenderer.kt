package jp.nico2525.sukashimap.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import jp.nico2525.sukashimap.data.LocationDisplayMode
import jp.nico2525.sukashimap.data.OverlayPosition
import jp.nico2525.sukashimap.data.TransportInfo
import jp.nico2525.sukashimap.map.MapRenderer
import java.time.format.DateTimeFormatter

data class OverlayStyle(
    val font: Typeface? = null,
    val textColor: Int = Color.WHITE,
    val mapLineColor: Int = Color.parseColor("#FF5252"),
    val mapBorderColor: Int = Color.WHITE,
    val position: OverlayPosition = OverlayPosition.TOP_LEFT,
    val locationMode: LocationDisplayMode = LocationDisplayMode.REGION_NAME,
)

data class ExifOverlay(
    val makeModel: String = "",
    val dateTime: String = "",
    val fNumber: String = "",
    val exposure: String = "",
    val iso: String = "",
    val focal: String = "",
) {
    fun isEmpty() = makeModel.isBlank() && dateTime.isBlank() && fNumber.isBlank()
}

/**
 * Renders the text block + map + EXIF + logo bitmaps composited onto photos/videos.
 * Layout mirrors the reference overlay: headers big, route biggest, names/location/date small.
 */
class OverlayRenderer(private val context: Context) {
    private val mapRenderer = MapRenderer(context)

    data class OverlayBitmaps(
        val text: Bitmap?,
        val map: Bitmap?,
        val exif: Bitmap?,
        val watermark: Bitmap?,
    )

    suspend fun render(
        info: TransportInfo,
        imgW: Int,
        imgH: Int,
        style: OverlayStyle,
        exif: ExifOverlay = ExifOverlay(),
    ): OverlayBitmaps {
        val text = renderText(info, imgW, imgH, style)
        val map = if (info.showMap) renderMap(info, imgW, imgH, style) else null
        val exifBmp = if (!exif.isEmpty()) renderExif(exif, imgW) else null
        val watermark = if (info.showWatermark) renderWatermark(imgW) else null
        return OverlayBitmaps(text, map, exifBmp, watermark)
    }

    private data class Line(val text: String, val ratio: Float, val bold: Boolean)

    private fun textLines(info: TransportInfo, style: OverlayStyle): List<Line> {
        val headers = when (info) {
            is TransportInfo.Train -> listOf(info.headerLine1(), info.headerLine2())
            is TransportInfo.Airplane -> listOf(info.transportLabel)
            is TransportInfo.Other -> listOf(info.transportLabel)
            is TransportInfo.Spot -> emptyList()
        }.filter { it.isNotBlank() }
        val route = info.routeDisplay()
        val names = listOf(info.departureName, info.arrivalName)
            .filter { it.isNotBlank() }.joinToString("  ")
        val location = info.locationDisplay(style.locationMode)?.let { "📍 $it" } ?: ""
        val date = info.dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val out = mutableListOf<Line>()
        for (h in headers) out += Line(h, 0.058f, true)
        if (route.isNotBlank()) out += Line(route, 0.066f, true)
        if (names.isNotBlank()) out += Line(names, 0.030f, false)
        if (location.isNotBlank()) out += Line(location, 0.034f, false)
        if (date.isNotBlank()) out += Line(date, 0.034f, false)
        return out
    }

    private fun renderText(info: TransportInfo, imgW: Int, imgH: Int, style: OverlayStyle): Bitmap? {
        val lines = textLines(info, style)
        if (lines.isEmpty()) return null
        val scale = imgW / 1080f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.textColor
            style.font?.let { typeface = it }
            // soft drop shadow for legibility over bright skies
            setShadowLayer(10f * scale, 2f * scale, 2f * scale, Color.argb(200, 0, 0, 0))
        }
        var totalH = 0f
        var maxW = 0f
        val sizes = lines.map { it.ratio * imgW }
        lines.forEachIndexed { i, line ->
            paint.textSize = sizes[i]
            maxW = maxOf(maxW, paint.measureText(line.text))
            totalH += sizes[i] * 1.22f
        }
        val pad = 20f * scale
        val shadowPad = 12f * scale
        val maxTextW = imgW * 0.60f
        val scaleAdjust = if (maxW + pad * 2 > maxTextW) maxTextW / (maxW + pad * 2) else 1f
        val bmp = Bitmap.createBitmap(
            ((maxW + (pad + shadowPad) * 2) * scaleAdjust).toInt().coerceAtLeast(8),
            ((totalH + (pad + shadowPad) * 2) * scaleAdjust).toInt().coerceAtLeast(8),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bmp)
        canvas.scale(scaleAdjust, scaleAdjust)
        val leftAligned = style.position == OverlayPosition.TOP_LEFT || style.position == OverlayPosition.BOTTOM_LEFT
        paint.textAlign = if (leftAligned) Paint.Align.LEFT else Paint.Align.RIGHT
        var y = pad + shadowPad
        lines.forEachIndexed { i, line ->
            paint.textSize = sizes[i]
            paint.isFakeBoldText = line.bold
            y += sizes[i] * 1.22f
            val x = if (leftAligned) pad + shadowPad else bmp.width / scaleAdjust - pad - shadowPad
            canvas.drawText(line.text, x, y - sizes[i] * 0.18f, paint)
        }
        return bmp
    }

    private suspend fun renderMap(info: TransportInfo, imgW: Int, imgH: Int, style: OverlayStyle): Bitmap? {
        val bmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
        mapRenderer.render(Canvas(bmp), info, imgW, imgH, style.mapLineColor, style.mapBorderColor)
        return bmp
    }

    /**
     * Bottom-left camera stamp: "OLYMPUS E-M5MarkII" bold + "2026.10.13 14:12:12 | f/5.6 · 1/1000s · ISO 200 · 28mm".
     */
    private fun renderExif(exif: ExifOverlay, imgW: Int): Bitmap {
        val scale = imgW / 1080f
        val mainSize = 40f * scale
        val subSize = 30f * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(8f * scale, 2f * scale, 2f * scale, Color.argb(200, 0, 0, 0))
        }
        val sub = listOf(
            exif.dateTime,
            listOf(exif.fNumber, exif.exposure, exif.iso, exif.focal)
                .filter { it.isNotBlank() }.joinToString(" · "),
        ).filter { it.isNotBlank() }.joinToString(" | ")
        paint.textSize = mainSize
        paint.isFakeBoldText = true
        val w1 = if (exif.makeModel.isNotBlank()) paint.measureText(exif.makeModel) else 0f
        paint.textSize = subSize
        paint.isFakeBoldText = false
        val w2 = if (sub.isNotBlank()) paint.measureText(sub) else 0f
        val pad = 16f * scale
        val shadowPad = 10f * scale
        val bmp = Bitmap.createBitmap(
            (maxOf(w1, w2) + (pad + shadowPad) * 2).toInt().coerceAtLeast(8),
            ((mainSize + subSize) * 1.22f + (pad + shadowPad) * 2).toInt().coerceAtLeast(8),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bmp)
        paint.textAlign = Paint.Align.LEFT
        var y = pad + shadowPad
        if (exif.makeModel.isNotBlank()) {
            paint.textSize = mainSize
            paint.isFakeBoldText = true
            y += mainSize * 1.22f
            canvas.drawText(exif.makeModel, pad + shadowPad, y - mainSize * 0.18f, paint)
        }
        if (sub.isNotBlank()) {
            paint.textSize = subSize
            paint.isFakeBoldText = false
            y += subSize * 1.22f
            canvas.drawText(sub, pad + shadowPad, y - subSize * 0.18f, paint)
        }
        return bmp
    }

    private fun renderWatermark(imgW: Int): Bitmap {
        val scale = imgW / 1080f
        val text = "Sukashi"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 44f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            setShadowLayer(6f * scale, 1f * scale, 1f * scale, Color.argb(180, 0, 0, 0))
        }
        val pad = 12f * scale
        val shadowPad = 8f * scale
        val w = (paint.measureText(text) + (pad + shadowPad) * 2).toInt()
        val h = (paint.textSize * 1.5f + (pad + shadowPad) * 2).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(text, pad + shadowPad, pad + shadowPad + paint.textSize * 1.1f, paint)
        return bmp
    }
}
