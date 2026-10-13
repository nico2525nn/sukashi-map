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

data class OverlayStyle(
    val font: Typeface? = null,
    val textColor: Int = Color.WHITE,
    val mapLineColor: Int = Color.parseColor("#FF5252"),
    val mapBorderColor: Int = Color.WHITE,
    val position: OverlayPosition = OverlayPosition.TOP_LEFT,
    val locationMode: LocationDisplayMode = LocationDisplayMode.REGION_NAME,
)

/**
 * Renders the text block + map + watermark bitmaps that get composited
 * onto captured photos / videos.
 */
class OverlayRenderer(private val context: Context) {
    private val mapRenderer = MapRenderer(context)

    data class OverlayBitmaps(
        val text: Bitmap?,
        val map: Bitmap?,
        val watermark: Bitmap?,
    )

    suspend fun render(info: TransportInfo, imgW: Int, imgH: Int, style: OverlayStyle): OverlayBitmaps {
        val text = renderText(info, imgW, imgH, style)
        val map = if (info.showMap) renderMap(info, imgW, imgH, style) else null
        val watermark = if (info.showWatermark) renderWatermark(imgW) else null
        return OverlayBitmaps(text, map, watermark)
    }

    private fun renderText(info: TransportInfo, imgW: Int, imgH: Int, style: OverlayStyle): Bitmap? {
        val label = info.transportLabel
        val route = info.routeDisplay()
        val names = listOf(info.departureName, info.arrivalName).filter { it.isNotBlank() }.joinToString(" → ")
        val location = info.locationDisplay(style.locationMode)?.let { "📍 $it" } ?: ""
        val date = info.formattedDate()
        val lines = listOf(
            Triple(label, 0.052f, true),
            Triple(route, 0.058f, true),
            Triple(names, 0.030f, false),
            Triple(location, 0.030f, false),
            Triple(date, 0.030f, false),
        ).filter { it.first.isNotBlank() }
        if (lines.isEmpty()) return null

        val scale = imgW / 1080f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.textColor
            style.font?.let { typeface = it }
            setShadowLayer(8f * scale, 0f, 0f, Color.argb(180, 0, 0, 0))
        }
        // measure
        var totalH = 0f
        var maxW = 0f
        val sizes = lines.map { (_, ratio, _) -> ratio * imgW }
        lines.forEachIndexed { i, (text, _, _) ->
            paint.textSize = sizes[i]
            maxW = maxOf(maxW, paint.measureText(text))
            totalH += sizes[i] * 1.18f
        }
        val pad = 20f * scale
        val bmp = Bitmap.createBitmap((maxW + pad * 2).toInt().coerceAtLeast(8), (totalH + pad * 2).toInt().coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val leftAligned = style.position == OverlayPosition.TOP_LEFT || style.position == OverlayPosition.BOTTOM_LEFT
        paint.textAlign = if (leftAligned) Paint.Align.LEFT else Paint.Align.RIGHT
        var y = pad
        lines.forEachIndexed { i, (text, _, bold) ->
            paint.textSize = sizes[i]
            paint.isFakeBoldText = bold
            y += sizes[i] * 1.18f
            val x = if (leftAligned) pad else bmp.width - pad
            canvas.drawText(text, x, y - sizes[i] * 0.18f, paint)
        }
        return bmp
    }

    private suspend fun renderMap(info: TransportInfo, imgW: Int, imgH: Int, style: OverlayStyle): Bitmap? {
        val bmp = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
        mapRenderer.render(Canvas(bmp), info, imgW, imgH, style.mapLineColor, style.mapBorderColor)
        return bmp
    }

    private fun renderWatermark(imgW: Int): Bitmap {
        val scale = imgW / 1080f
        val text = "Sukashi Map"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 30f * scale
            setShadowLayer(4f * scale, 0f, 0f, Color.argb(160, 0, 0, 0))
        }
        val w = (paint.measureText(text) + 24 * scale).toInt()
        val h = (44 * scale).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, 12 * scale, 32 * scale, paint)
        return bmp
    }
}
