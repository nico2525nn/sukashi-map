package jp.nico2525.sukashimap.ui

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.location.LocationManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.nico2525.sukashimap.data.AirlineEntry
import jp.nico2525.sukashimap.data.AirlinesCatalog
import jp.nico2525.sukashimap.data.AirportRecord
import jp.nico2525.sukashimap.data.AirportRepository
import jp.nico2525.sukashimap.data.MapVectorRepository
import jp.nico2525.sukashimap.data.PlaceCandidate
import jp.nico2525.sukashimap.data.PreferencesRepository
import jp.nico2525.sukashimap.data.RailwayLineEntry
import jp.nico2525.sukashimap.data.RailwayRepository
import jp.nico2525.sukashimap.data.RoutingAndPlaceService
import jp.nico2525.sukashimap.data.StationRecord
import jp.nico2525.sukashimap.data.UserPreferencesState
import jp.nico2525.sukashimap.export.MediaExporter
import jp.nico2525.sukashimap.model.CropFrameSettings
import jp.nico2525.sukashimap.model.ExportFormat
import jp.nico2525.sukashimap.model.NormOffset
import jp.nico2525.sukashimap.model.OverlayStyle
import jp.nico2525.sukashimap.model.TransportInfo
import jp.nico2525.sukashimap.model.TransportMode
import jp.nico2525.sukashimap.render.MapDatasetSlice
import jp.nico2525.sukashimap.render.OverlayCanvasRenderer
import jp.nico2525.sukashimap.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class EditorUiState(
    val mediaUri: Uri? = null,
    val isVideo: Boolean = false,
    val rawBitmap: Bitmap? = null,
    val stagedBitmap: Bitmap? = null,
    val mediaWidth: Int = 1920,
    val mediaHeight: Int = 1440,
    val transportInfo: TransportInfo = TransportInfo(),
    val overlayStyle: OverlayStyle = OverlayStyle(),
    val cropFrame: CropFrameSettings = CropFrameSettings(),
    val mapSlice: MapDatasetSlice? = null,
    val isComputingRoute: Boolean = false,
    val isExporting: Boolean = false,
    val busyMessage: String = ""
)

class SukashiViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    val airportRepository = AirportRepository(appContext)
    val railwayRepository = RailwayRepository(appContext)
    val mapVectorRepository = MapVectorRepository(appContext)
    val routingAndPlaceService = RoutingAndPlaceService(appContext, mapVectorRepository)
    val preferencesRepository = PreferencesRepository(appContext)

    val prefsState: StateFlow<UserPreferencesState> = preferencesRepository.stateFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferencesState())

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private var routeJob: Job? = null
    private var mediaJob: Job? = null
    private var mediaLoadId: Long = 0L

    /**
     * Monotonic revision for the committed TransportInfo. Slow map/route jobs capture the
     * revision they started with and never write results after a newer edit.
     */
    private var infoRevision: Long = 0L

    init {
        viewModelScope.launch {
            mapVectorRepository.preloadCoreMaps()
            refreshMapSlice(_editorState.value.transportInfo)
        }
        viewModelScope.launch {
            preferencesRepository.stateFlow.collect { prefs ->
                if (_editorState.value.mediaUri == null) {
                    _editorState.update { it.copy(overlayStyle = prefs.savedStyle) }
                }
            }
        }
    }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    fun openMediaUri(uri: Uri, fromCamera: Boolean = false, onReady: () -> Unit = {}) {
        mediaJob?.cancel()
        val loadId = ++mediaLoadId
        mediaJob = viewModelScope.launch {
            _editorState.update { it.copy(isExporting = true, busyMessage = "読み込み中…") }
            try {
                val meta = MediaExporter.extractMediaMetadata(appContext, uri)
                val rawBmp = if (!meta.isVideo) {
                    MediaExporter.decodeOrientedBitmap(appContext, uri, maxLongEdge = 2400)
                } else null
                if (loadId != mediaLoadId) return@launch

                val cal = Calendar.getInstance()
                var lat = meta.lat
                var lon = meta.lon
                if (lat == null && lon == null && fromCamera && prefsState.value.autoLocationEnabled) {
                    val devLoc = readLastKnownDeviceLocation()
                    if (devLoc != null) {
                        lat = devLoc.first
                        lon = devLoc.second
                    }
                }

                val geo = if (lat != null && lon != null) {
                    routingAndPlaceService.reverseGeocode(lat, lon)
                } else null
                if (loadId != mediaLoadId) return@launch

                val curInfo = _editorState.value.transportInfo
                val updatedInfo = curInfo.copy(
                    year = meta.year ?: cal.get(Calendar.YEAR),
                    month = meta.month ?: (cal.get(Calendar.MONTH) + 1),
                    day = meta.day ?: cal.get(Calendar.DAY_OF_MONTH),
                    hour = meta.hour ?: cal.get(Calendar.HOUR_OF_DAY),
                    minute = meta.minute ?: cal.get(Calendar.MINUTE),
                    photoLat = lat,
                    photoLon = lon,
                    photoLocationName = geo?.locationLabel.orEmpty(),
                    photoCountryCode = geo?.countryCode.orEmpty()
                )
                val revision = ++infoRevision
                val slice = OverlayCanvasRenderer.prepareMapSlice(updatedInfo, mapVectorRepository)
                if (loadId != mediaLoadId) return@launch
                _editorState.update {
                    it.copy(
                        mediaUri = uri,
                        isVideo = meta.isVideo,
                        rawBitmap = rawBmp,
                        stagedBitmap = rawBmp,
                        mediaWidth = rawBmp?.width ?: meta.width,
                        mediaHeight = rawBmp?.height ?: meta.height,
                        transportInfo = updatedInfo,
                        cropFrame = CropFrameSettings(),
                        mapSlice = slice,
                        isExporting = false,
                        busyMessage = ""
                    )
                }
                recomputeRouteIfNeeded(updatedInfo, revision = revision)
                onReady()
            } finally {
                // Only the newest load owns the busy overlay; a superseded load must not
                // clear the spinner that the new load is still showing.
                if (loadId == mediaLoadId) {
                    _editorState.update {
                        if (it.isExporting) it.copy(isExporting = false, busyMessage = "") else it
                    }
                }
            }
        }
    }

    fun applyTransportInfo(newInfo: TransportInfo, saveToHistory: Boolean = true) {
        _editorState.update { it.copy(transportInfo = newInfo) }
        val revision = ++infoRevision
        viewModelScope.launch {
            refreshMapSlice(newInfo, revision)
            if (revision != infoRevision) return@launch
            recomputeRouteIfNeeded(newInfo, saveToHistory = saveToHistory, revision = revision)
        }
    }

    private suspend fun refreshMapSlice(info: TransportInfo, revision: Long = infoRevision) {
        val slice = OverlayCanvasRenderer.prepareMapSlice(info, mapVectorRepository)
        if (revision == infoRevision) {
            _editorState.update { it.copy(mapSlice = slice) }
        }
    }

    fun recomputeRouteIfNeeded(
        info: TransportInfo,
        saveToHistory: Boolean = false,
        revision: Long = infoRevision
    ) {
        if (revision != infoRevision) return
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            if (revision != infoRevision) return@launch
            when (info.mode) {
                TransportMode.TRAIN -> {
                    if (info.isDomesticJapanRail &&
                        info.departureStationLat != null && info.departureStationLon != null &&
                        info.arrivalStationLat != null && info.arrivalStationLon != null
                    ) {
                        _editorState.update { it.copy(isComputingRoute = true) }
                        val res = railwayRepository.computeTrainRoute(
                            depStationName = info.departureStationName,
                            depLat = info.departureStationLat,
                            depLon = info.departureStationLon,
                            depGroupCode = info.departureStationGroupCode,
                            arrStationName = info.arrivalStationName,
                            arrLat = info.arrivalStationLat,
                            arrLon = info.arrivalStationLon,
                            arrGroupCode = info.arrivalStationGroupCode,
                            preferredLineName = info.railwayLineName,
                            selectedViaLines = info.selectedViaLines,
                            isAutoShortest = info.isAutoShortestVia
                        )
                        if (revision != infoRevision) return@launch
                        val merged = info.copy(
                            routePolylines = res.polylines,
                            candidateViaLines = res.candidateViaLines,
                            railwayLineName = info.railwayLineName.ifBlank { res.autoDetectedLineName.orEmpty() },
                            railwayCompany = info.railwayCompany.ifBlank { res.autoDetectedCompany.orEmpty() }
                        )
                        val slice = OverlayCanvasRenderer.prepareMapSlice(merged, mapVectorRepository)
                        if (revision != infoRevision) return@launch
                        _editorState.update {
                            it.copy(transportInfo = merged, mapSlice = slice, isComputingRoute = false)
                        }
                        if (saveToHistory && revision == infoRevision) {
                            preferencesRepository.recordHistory(merged)
                        }
                    } else {
                        if (revision != infoRevision) return@launch
                        val cleaned = info.copy(routePolylines = emptyList(), candidateViaLines = emptyList())
                        _editorState.update { it.copy(transportInfo = cleaned, isComputingRoute = false) }
                        if (saveToHistory && revision == infoRevision) {
                            preferencesRepository.recordHistory(cleaned)
                        }
                    }
                }

                TransportMode.OTHER -> {
                    val dLat = info.departurePlaceLat
                    val dLon = info.departurePlaceLon
                    val aLat = info.destinationPlaceLat
                    val aLon = info.destinationPlaceLon
                    if (dLat != null && dLon != null && aLat != null && aLon != null) {
                        _editorState.update { it.copy(isComputingRoute = true) }
                        val road = routingAndPlaceService.fetchDrivingRoute(
                            depLat = dLat,
                            depLon = dLon,
                            destLat = aLat,
                            destLon = aLon,
                            viaPhotoLat = info.photoLat,
                            viaPhotoLon = info.photoLon
                        )
                        if (revision != infoRevision) return@launch
                        val merged = info.copy(routePolylines = road)
                        val slice = OverlayCanvasRenderer.prepareMapSlice(merged, mapVectorRepository)
                        if (revision != infoRevision) return@launch
                        _editorState.update {
                            it.copy(transportInfo = merged, mapSlice = slice, isComputingRoute = false)
                        }
                        if (saveToHistory && revision == infoRevision) {
                            preferencesRepository.recordHistory(merged)
                        }
                    } else {
                        if (revision != infoRevision) return@launch
                        _editorState.update { it.copy(isComputingRoute = false) }
                        if (saveToHistory && revision == infoRevision) {
                            preferencesRepository.recordHistory(info)
                        }
                    }
                }

                TransportMode.AIRPLANE, TransportMode.SPOT -> {
                    val cleaned = info.copy(routePolylines = emptyList())
                    val slice = OverlayCanvasRenderer.prepareMapSlice(cleaned, mapVectorRepository)
                    if (revision != infoRevision) return@launch
                    _editorState.update { it.copy(transportInfo = cleaned, mapSlice = slice, isComputingRoute = false) }
                    if (saveToHistory && revision == infoRevision) {
                        preferencesRepository.recordHistory(cleaned)
                    }
                }
            }
        }
    }

    fun updateOverlayStyle(transform: (OverlayStyle) -> OverlayStyle, persist: Boolean = true) {
        val updated = transform(_editorState.value.overlayStyle)
        _editorState.update { it.copy(overlayStyle = updated) }
        if (persist) {
            viewModelScope.launch {
                preferencesRepository.saveOverlayStyle(updated)
            }
        }
    }

    fun snapLayoutReset() {
        updateOverlayStyle({
            it.copy(
                textFreePos = null,
                mapFreePos = null,
                textScale = 1.0f,
                mapBoxScale = 1.0f,
                mapInternalZoom = 1.0f,
                mapInternalPanX = 0f,
                mapInternalPanY = 0f
            )
        })
        emitMessage("透かしの位置とサイズをリセットしました")
    }

    fun updateTextPositionAndScale(pos: NormOffset?, scale: Float) {
        updateOverlayStyle({
            it.copy(textFreePos = pos, textScale = scale.coerceIn(0.5f, 2.5f))
        }, persist = false)
    }

    fun updateMapPositionAndScale(pos: NormOffset?, boxScale: Float) {
        updateOverlayStyle({
            it.copy(mapFreePos = pos, mapBoxScale = boxScale.coerceIn(0.5f, 2.5f))
        }, persist = false)
    }

    fun updateMapInternalViewport(zoom: Float, panX: Float, panY: Float) {
        updateOverlayStyle({
            it.copy(
                mapInternalZoom = zoom.coerceIn(0.5f, 5.0f),
                mapInternalPanX = panX.coerceIn(-1.5f, 1.5f),
                mapInternalPanY = panY.coerceIn(-1.5f, 1.5f)
            )
        }, persist = false)
    }

    fun commitInteractiveStyle() {
        viewModelScope.launch {
            preferencesRepository.saveOverlayStyle(_editorState.value.overlayStyle)
        }
    }

    fun applyCropFrame(settings: CropFrameSettings) {
        viewModelScope.launch(Dispatchers.Default) {
            val raw = _editorState.value.rawBitmap
            val staged = if (raw != null) {
                MediaExporter.applyCropAndFrameToBitmap(raw, settings)
            } else null
            _editorState.update {
                it.copy(
                    cropFrame = settings,
                    stagedBitmap = staged ?: it.rawBitmap,
                    mediaWidth = staged?.width ?: it.mediaWidth,
                    mediaHeight = staged?.height ?: it.mediaHeight
                )
            }
        }
    }

    fun exportCurrentMedia(formatOverride: ExportFormat? = null, shareAfterSave: Boolean = false) {
        val state = _editorState.value
        val uri = state.mediaUri ?: run {
            emitMessage("写真または動画を選択してください")
            return
        }
        viewModelScope.launch {
            val fmt = formatOverride ?: prefsState.value.defaultExportFormat
            _editorState.update {
                it.copy(
                    isExporting = true,
                    busyMessage = if (state.isVideo) "動画を書き出し中…" else "画像を保存中…"
                )
            }
            try {
                val savedUri = if (state.isVideo) {
                    MediaExporter.exportVideoOriginalQuality(
                        context = appContext,
                        sourceVideoUri = uri,
                        info = state.transportInfo,
                        style = state.overlayStyle,
                        cropFrame = state.cropFrame,
                        mapSlice = state.mapSlice,
                        preserveHdr = prefsState.value.preserveUltraHdr
                    )
                } else {
                    MediaExporter.exportPhotoToMediaStore(
                        context = appContext,
                        sourceUri = uri,
                        info = state.transportInfo,
                        style = state.overlayStyle,
                        cropFrame = state.cropFrame,
                        mapSlice = state.mapSlice,
                        format = fmt,
                        preserveUltraHdr = prefsState.value.preserveUltraHdr
                    )
                }
                preferencesRepository.recordHistory(state.transportInfo)
                if (savedUri != null) {
                    emitMessage(if (state.isVideo) "Movies/SukashiMap に保存しました" else "Pictures/SukashiMap に保存しました")
                    if (shareAfterSave) {
                        MediaExporter.launchShareChooser(
                            appContext,
                            savedUri,
                            if (state.isVideo) "video/mp4" else fmt.mimeType
                        )
                    }
                } else {
                    emitMessage("保存に失敗しました")
                }
            } catch (e: Exception) {
                emitMessage("書き出しエラー: ${e.localizedMessage ?: "不明なエラー"}")
            } finally {
                _editorState.update { it.copy(isExporting = false, busyMessage = "") }
            }
        }
    }

    fun searchAirlines(query: String): List<AirlineEntry> = AirlinesCatalog.search(query)
    suspend fun searchAirports(query: String): List<AirportRecord> = airportRepository.searchAirports(query)
    suspend fun searchLines(query: String): List<RailwayLineEntry> {
        val s = _editorState.value.transportInfo
        return railwayRepository.searchLines(query, s.photoLat, s.photoLon)
    }

    suspend fun searchStations(query: String, lineName: String): List<StationRecord> {
        val s = _editorState.value.transportInfo
        return railwayRepository.searchStations(query, lineName, s.photoLat, s.photoLon)
    }

    suspend fun searchPlaces(query: String): List<PlaceCandidate> {
        val s = _editorState.value.transportInfo
        return routingAndPlaceService.searchPlaces(query, s.photoLat, s.photoLon)
    }

    /** Resolves a display label for a manually entered / device GPS coordinate. */
    suspend fun reverseGeocodeLocation(lat: Double, lon: Double) =
        routingAndPlaceService.reverseGeocode(lat, lon)

    /** Last known device position; callers must hold a location runtime permission. */
    suspend fun lastKnownDeviceLocation(): Pair<Double, Double>? = readLastKnownDeviceLocation()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.updateThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateDynamicColor(enabled) }
    }

    fun setAutoLocation(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateAutoLocation(enabled) }
    }

    fun setPreserveHdr(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updatePreserveHdr(enabled) }
    }

    fun setDefaultExportFormat(fmt: ExportFormat) {
        viewModelScope.launch { preferencesRepository.updateExportFormat(fmt) }
    }

    fun saveStylePreset(name: String) {
        viewModelScope.launch {
            preferencesRepository.saveCustomPreset(name, _editorState.value.overlayStyle)
            emitMessage("プリセット「$name」を保存しました")
        }
    }

    fun deleteStylePreset(id: String) {
        viewModelScope.launch { preferencesRepository.deletePreset(id) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun readLastKnownDeviceLocation(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        runCatching {
            val lm = appContext.getSystemService(LocationManager::class.java) ?: return@runCatching null
            val providers = lm.getProviders(true)
            providers.mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let { it.latitude to it.longitude }
        }.getOrNull()
    }
}
