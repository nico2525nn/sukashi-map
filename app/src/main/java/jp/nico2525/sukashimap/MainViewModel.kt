package jp.nico2525.sukashimap

import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import androidx.lifecycle.ViewModel
import jp.nico2525.sukashimap.data.DateTimeDisplayMode
import jp.nico2525.sukashimap.data.LocationDisplayMode
import jp.nico2525.sukashimap.data.OverlayPosition
import jp.nico2525.sukashimap.data.TransportInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Holds the in-progress capture session for MainActivity. */
class MainViewModel : ViewModel() {
    private val _transportInfo = MutableStateFlow<TransportInfo?>(null)
    val transportInfo: StateFlow<TransportInfo?> = _transportInfo

    private val _overlayPosition = MutableStateFlow(OverlayPosition.TOP_LEFT)
    val overlayPosition: StateFlow<OverlayPosition> = _overlayPosition

    private val _mapPosition = MutableStateFlow(OverlayPosition.TOP_RIGHT)
    val mapPosition: StateFlow<OverlayPosition> = _mapPosition

    private val _locationMode = MutableStateFlow(LocationDisplayMode.REGION_NAME)
    val locationMode: StateFlow<LocationDisplayMode> = _locationMode

    private val _photoBitmap = MutableStateFlow<Bitmap?>(null)
    val photoBitmap: StateFlow<Bitmap?> = _photoBitmap

    private val _videoUri = MutableStateFlow<Uri?>(null)
    val videoUri: StateFlow<Uri?> = _videoUri

    private val _chosenFont = MutableStateFlow<Typeface?>(null)
    val chosenFont: StateFlow<Typeface?> = _chosenFont

    var textColor: Int = -1
    var mapLineColor: Int = 0xFFFF5252.toInt()
    var mapBorderColor: Int = -1
    var currentLat: Double? = null
    var currentLon: Double? = null
    var currentPlace: String? = null
    var currentCountry: String? = null
    var exifOverlay: jp.nico2525.sukashimap.overlay.ExifOverlay =
        jp.nico2525.sukashimap.overlay.ExifOverlay()
    var dateTimeMode: DateTimeDisplayMode = DateTimeDisplayMode.DATE_TIME
    var showWatermark: Boolean = true

    fun setTransport(info: TransportInfo?) {
        _transportInfo.value = info
        info?.let {
            _overlayPosition.value = OverlayPosition.TOP_LEFT
            _mapPosition.value = it.mapPosition
            dateTimeMode = it.dateTimeDisplayMode
            showWatermark = it.showWatermark
        }
    }

    fun setOverlayPosition(p: OverlayPosition) {
        _overlayPosition.value = p
    }

    fun setMapPosition(p: OverlayPosition) {
        _mapPosition.value = p
    }

    fun toggleLocationMode() {
        _locationMode.value = if (_locationMode.value == LocationDisplayMode.REGION_NAME) {
            LocationDisplayMode.COORDINATES
        } else {
            LocationDisplayMode.REGION_NAME
        }
    }

    fun setPhoto(b: Bitmap?) {
        _photoBitmap.value = b
    }

    fun setVideo(u: Uri?) {
        _videoUri.value = u
    }

    fun setFont(t: Typeface?) {
        _chosenFont.value = t
    }

    fun clearCapture() {
        _photoBitmap.value?.recycle()
        _photoBitmap.value = null
        _videoUri.value = null
    }

    override fun onCleared() {
        _photoBitmap.value?.recycle()
        _photoBitmap.value = null
        super.onCleared()
    }
}
