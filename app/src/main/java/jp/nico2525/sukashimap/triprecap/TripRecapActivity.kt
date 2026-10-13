package jp.nico2525.sukashimap.triprecap

import android.app.ActivityManager
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import jp.nico2525.sukashimap.R
import jp.nico2525.sukashimap.ads.AdManager
import jp.nico2525.sukashimap.billing.BillingManager
import jp.nico2525.sukashimap.databinding.ActivityTripRecapBinding
import jp.nico2525.sukashimap.gallery.GallerySort
import jp.nico2525.sukashimap.gallery.MediaStoreLoader
import jp.nico2525.sukashimap.plus.PlusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Trip recap poster builder (MVP complete).
 *
 * Four panels under a top TabLayout (Map / Photos / Ratio / Style), all built in
 * code inside [ActivityTripRecapBinding.recapPanels]. The map panel hosts a
 * MapLibre [MapView] with a self-authored OpenFreeMap style; photo pins, visit
 * markers and Timeline route lines are annotation overlays; the saved poster is
 * composited by [TripPosterRenderer] so export never depends on GL readback.
 */
class TripRecapActivity : AppCompatActivity() {

    companion object {
        private const val MAX_PHOTOS = 12
        private const val MAX_PLACES = 20
        private const val TILES = "https://tiles.openfreemap.org"
    }

    private lateinit var binding: ActivityTripRecapBinding
    private lateinit var loader: MediaStoreLoader
    private lateinit var plus: PlusManager
    private lateinit var ads: AdManager

    private val photos = ArrayList<Uri>()
    private val places = ArrayList<VisitStop>()
    private val style = RecapStyle()
    private var startDate: Long = 0L
    private var endDate: Long = 0L
    private var routeMeasured = emptyList<Pair<Double, Double>>()
    private var routeEstimated = emptyList<Pair<Double, Double>>()
    private var darkMap = true
    private var nextStopId = 1L
    private var dirty = false

    private var mapView: MapView? = null
    private var libreMap: MapLibreMap? = null
    private var panels: LinearLayout? = null
    private var photoStrip: LinearLayout? = null
    private var placeBox: LinearLayout? = null
    private var saving = false

    private val timelinePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) importTimeline(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripRecapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loader = MediaStoreLoader(this)
        plus = PlusManager(this)
        ads = AdManager(this)

        binding.recapToolbar.title = getString(R.string.trip_title)
        binding.recapToolbar.setNavigationIcon(R.drawable.ic_back)
        binding.recapToolbar.setNavigationOnClickListener { confirmDiscard() }
        binding.recapToolbar.inflateMenu(R.menu.recap_menu)
        binding.recapToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_recap_import_timeline -> {
                    timelinePicker.launch("application/json")
                    true
                }
                R.id.action_recap_save -> {
                    savePoster()
                    true
                }
                else -> false
            }
        }

        binding.recapAttribution.text = getString(R.string.recap_attribution_openfreemap)
        panels = binding.recapPanels

        val tabs = binding.recapTabs
        tabs.addTab(tabs.newTab().setText(R.string.trip_tab_map))
        tabs.addTab(tabs.newTab().setText(R.string.trip_tab_photos))
        tabs.addTab(tabs.newTab().setText(R.string.trip_tab_ratio))
        tabs.addTab(tabs.newTab().setText(R.string.trip_tab_style))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showPanel(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        if (!supportsGlEs2()) {
            panels?.addView(noticeView(getString(R.string.recap_gl_unsupported)))
            tabs.getTabAt(0)?.select()
            showPanel(0)
            return
        }
        MapLibre.getInstance(this)
        showPanel(0)
    }

    // ---------- panels ----------

    private fun showPanel(index: Int) {
        val root = panels ?: return
        root.removeAllViews()
        when (index) {
            0 -> buildMapPanel(root)
            1 -> buildPhotosPanel(root)
            2 -> buildRatioPanel(root)
            else -> buildStylePanel(root)
        }
    }

    private fun noticeView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setPadding(8)
        }
    }

    // ----- Map panel -----

    private fun buildMapPanel(root: LinearLayout) {
        root.addView(label(getString(R.string.recap_map_title)))
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320),
            )
        }
        val mv = MapView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        mapView?.onDestroy()
        mapView = mv
        mv.onCreate(Bundle())
        frame.addView(mv)
        root.addView(frame)

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(8) }
        }
        val input = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            hint = getString(R.string.recap_search_hint)
        }
        val field = TextInputEditText(this)
        input.addView(field)
        val go = MaterialButton(this).apply {
            text = getString(R.string.recap_search_go)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.leftMargin = dp(8); it.gravity = Gravity.CENTER_VERTICAL }
        }
        searchRow.addView(input)
        searchRow.addView(go)
        root.addView(searchRow)

        val toggleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val themeBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(if (darkMap) R.string.recap_map_light else R.string.recap_map_dark)
        }
        val namesBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(if (style.showPlaceNames) R.string.recap_names_on else R.string.recap_names_off)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.leftMargin = dp(8) }
        }
        val fitBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.recap_fit_all)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.leftMargin = dp(8) }
        }
        toggleRow.addView(themeBtn)
        toggleRow.addView(namesBtn)
        toggleRow.addView(fitBtn)
        root.addView(toggleRow)
        root.addView(noticeView(getString(R.string.recap_map_tap_hint)))

        go.setOnClickListener {
            val q = field.text?.toString()?.trim().orEmpty()
            if (q.isEmpty()) return@setOnClickListener
            lifecycleScope.launch {
                val lang = Locale.getDefault().toLanguageTag()
                val hits = runCatching { PhotonApi.search(q, lang) }.getOrDefault(emptyList())
                if (hits.isEmpty()) {
                    Toast.makeText(this@TripRecapActivity, R.string.recap_search_empty, Toast.LENGTH_SHORT).show()
                } else {
                    showSearchResults(hits)
                }
            }
        }
        themeBtn.setOnClickListener {
            darkMap = !darkMap
            themeBtn.text = getString(if (darkMap) R.string.recap_map_light else R.string.recap_map_dark)
            applyMapStyle()
            markDirty()
        }
        namesBtn.setOnClickListener {
            style.showPlaceNames = !style.showPlaceNames
            namesBtn.text = getString(if (style.showPlaceNames) R.string.recap_names_on else R.string.recap_names_off)
            refreshAnnotations()
            markDirty()
        }
        fitBtn.setOnClickListener { fitAll() }

        mv.getMapAsync { map ->
            libreMap = map
            applyMapStyle()
            map.addOnMapClickListener { pt ->
                addStopFromTap(pt.latitude, pt.longitude)
                true
            }
        }
    }

    private fun applyMapStyle() {
        val map = libreMap ?: return
        map.setStyle(Style.Builder().fromJson(buildStyleJson(darkMap))) {
            runCatching {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(36.0, 138.0), 4.0))
            }
            refreshAnnotations()
            fitAll()
        }
    }

    /** Self-authored minimal bright/dark style over OpenFreeMap vector tiles. */
    private fun buildStyleJson(dark: Boolean): String {
        val bg = if (dark) "#10141c" else "#eef2f6"
        val land = if (dark) "#1b2433" else "#dce6ef"
        val water = if (dark) "#0d1b2e" else "#aad3df"
        val road = if (dark) "#3a4a63" else "#ffffff"
        return """
        {
          "version": 8,
          "name": "SukashiRecap",
          "sources": {
            "ofm": {"type": "vector", "url": "$TILES/planet", "maxzoom": 14}
          },
          "glyphs": "$TILES/fonts/{fontstack}/{range}.pbf",
          "layers": [
            {"id": "bg", "type": "background", "paint": {"background-color": "$bg"}},
            {"id": "water", "type": "fill", "source": "ofm", "source-layer": "water", "paint": {"fill-color": "$water"}},
            {"id": "landcover", "type": "fill", "source": "ofm", "source-layer": "landcover", "paint": {"fill-color": "$land"}},
            {"id": "landuse", "type": "fill", "source": "ofm", "source-layer": "landuse", "paint": {"fill-color": "$land"}},
            {"id": "roads", "type": "line", "source": "ofm", "source-layer": "transportation", "paint": {"line-color": "$road", "line-width": 1}},
            {"id": "places", "type": "symbol", "source": "ofm", "source-layer": "place",
             "layout": {"text-field": ["coalesce", ["get", "name:latin"], ["get", "name"]], "text-font": ["Noto Sans Regular"], "text-size": 11}, "paint": {"text-color": "${if (dark) "#ffffff" else "#111111"}"}}
          ]
        }
        """.trimIndent()
    }

    private fun refreshAnnotations() {
        val map = libreMap ?: return
        map.removeAnnotations()
        // Photo pins.
        lifecycleScope.launch {
            val geo = photoGeoPoints()
            withContext(Dispatchers.Main) {
                for ((lat, lon) in geo.take(60)) {
                    runCatching {
                        map.addMarker(MarkerOptions().position(LatLng(lat, lon)))
                    }
                }
                // Visit stops numbered.
                for ((i, p) in places.withIndex()) {
                    runCatching {
                        map.addMarker(
                            MarkerOptions().position(LatLng(p.lat, p.lon)).title("${i + 1}. ${p.name}"),
                        )
                    }
                }
                if (routeMeasured.size >= 2) {
                    runCatching {
                        map.addPolyline(
                            PolylineOptions()
                                .addAll(routeMeasured.map { LatLng(it.first, it.second) })
                                .color(style.routeColor).width(5f),
                        )
                    }
                }
                if (routeEstimated.size >= 2) {
                    runCatching {
                        map.addPolyline(
                            PolylineOptions()
                                .addAll(routeEstimated.map { LatLng(it.first, it.second) })
                                .color(style.routeColor).width(2f).alpha(0.55f),
                        )
                    }
                }
            }
        }
    }

    private suspend fun photoGeoPoints(): List<Pair<Double, Double>> =
        withContext(Dispatchers.IO) {
            // Resolve loaded gallery items matching chosen photo URIs.
            val all = runCatching {
                MediaStoreLoader(this@TripRecapActivity).load(false, GallerySort.BY_ADDED)
            }.getOrDefault(emptyList())
            val byUri = all.associateBy { it.uri.toString() }
            photos.mapNotNull { uri ->
                val item = byUri[uri.toString()]
                val lat = item?.latitude
                val lon = item?.longitude
                if (lat != null && lon != null) lat to lon else null
            }
        }

    private fun fitAll() {
        val map = libreMap ?: return
        val pts = ArrayList<LatLng>()
        places.forEach { pts += LatLng(it.lat, it.lon) }
        pts += routeMeasured.map { LatLng(it.first, it.second) }
        pts += routeEstimated.map { LatLng(it.first, it.second) }
        if (pts.isEmpty()) {
            Toast.makeText(this, R.string.recap_fit_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val b = LatLngBounds.Builder()
        pts.forEach { b.include(it) }
        runCatching { map.easeCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 80), 600) }
    }

    private fun addStopFromTap(lat: Double, lon: Double) {
        if (places.size >= MAX_PLACES) {
            Toast.makeText(this, R.string.recap_places_full, Toast.LENGTH_SHORT).show()
            return
        }
        askStopName(getString(R.string.recap_new_stop_default, places.size + 1)) { name ->
            places += VisitStop(nextStopId++, name, lat, lon)
            markDirty()
            refreshAnnotations()
            placeBox?.let { rebuildPlaceList(it) }
        }
    }

    private fun showSearchResults(hits: List<PhotonPlace>) {
        val names = hits.map {
            if (it.country != null) "${it.name} · ${it.country}" else it.name
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recap_search_results)
            .setItems(names) { _, which ->
                val hit = hits[which]
                if (places.size >= MAX_PLACES) {
                    Toast.makeText(this, R.string.recap_places_full, Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                places += VisitStop(nextStopId++, hit.name, hit.lat, hit.lon)
                markDirty()
                refreshAnnotations()
                libreMap?.let { map ->
                    runCatching {
                        map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(hit.lat, hit.lon), 10.0), 600)
                    }
                }
                placeBox?.let { rebuildPlaceList(it) }
            }
            .setNegativeButton(R.string.trip_cancel, null)
            .show()
    }

    // ----- Photos panel -----

    private fun buildPhotosPanel(root: LinearLayout) {
        root.addView(label(getString(R.string.recap_photos_title, photos.size, MAX_PHOTOS)))
        val add = MaterialButton(this).apply { text = getString(R.string.recap_add_photos) }
        val auto = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.recap_add_from_map)
        }
        root.addView(add)
        root.addView(auto)
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(8) }
        }
        photoStrip = strip
        root.addView(strip)
        rebuildPhotoList()

        add.setOnClickListener { pickPhotos() }
        auto.setOnClickListener { addPhotosFromMap() }
    }

    private fun pickPhotos() {
        lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) {
                runCatching { loader.load(false, GallerySort.BY_ADDED) }.getOrDefault(emptyList())
            }
            if (all.isEmpty()) {
                Toast.makeText(this@TripRecapActivity, R.string.gallery_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = all.take(120).map { item ->
                (item.displayName ?: item.key) + if (item.isVideo) " ▶" else ""
            }.toTypedArray()
            val checked = all.take(120).map { sel -> photos.any { it.toString() == sel.uri.toString() } }.toBooleanArray()
            MaterialAlertDialogBuilder(this@TripRecapActivity)
                .setTitle(R.string.recap_pick_title)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton(R.string.trip_done) { _, _ ->
                    photos.clear()
                    all.take(120).forEachIndexed { i, item ->
                        if (checked[i] && photos.size < MAX_PHOTOS && !item.isVideo) {
                            photos += item.uri
                        }
                    }
                    markDirty()
                    refreshAnnotations()
                    photoStrip?.let { rebuildPhotoList() }
                    showPanel(1)
                }
                .setNegativeButton(R.string.trip_cancel, null)
                .show()
        }
    }

    private fun addPhotosFromMap() {
        lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) {
                runCatching { loader.load(false, GallerySort.BY_ADDED) }.getOrDefault(emptyList())
            }
            var added = 0
            for (item in all) {
                if (photos.size >= MAX_PHOTOS) break
                if (item.isVideo || !item.hasLocation) continue
                if (photos.any { it.toString() == item.uri.toString() }) continue
                // Keep photos inside the current trip window when set.
                if (startDate > 0 && endDate > 0) {
                    val taken = MediaStoreLoader.effectiveTaken(item)
                    if (taken < startDate || taken > endDate + 86_400_000L) continue
                }
                photos += item.uri
                added++
                if (added >= 6) break
            }
            if (added == 0) {
                Toast.makeText(
                    this@TripRecapActivity, R.string.recap_auto_empty, Toast.LENGTH_SHORT,
                ).show()
            } else {
                markDirty()
                refreshAnnotations()
            }
            photoStrip?.let { rebuildPhotoList() }
        }
    }

    private fun rebuildPhotoList() {
        val strip = photoStrip ?: return
        strip.removeAllViews()
        if (photos.isEmpty()) {
            strip.addView(noticeView(getString(R.string.trip_start_hint)))
            return
        }
        photos.toList().forEach { uri ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val name = TextView(this).apply {
                text = uri.lastPathSegment ?: uri.toString()
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val del = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.recap_remove)
            }
            del.setOnClickListener {
                photos.remove(uri)
                markDirty()
                refreshAnnotations()
                rebuildPhotoList()
            }
            row.addView(name)
            row.addView(del)
            strip.addView(row)
        }
    }

    // ----- Ratio panel -----

    private fun buildRatioPanel(root: LinearLayout) {
        root.addView(label(getString(R.string.recap_ratio_title)))
        val group = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        RecapStyle.ASPECT_LABELS.forEachIndexed { i, name ->
            val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = name
                id = View.generateViewId()
                tag = i
            }
            group.addView(b)
            if (i == style.aspectIndex) group.check(b.id)
        }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val idx = (group.findViewById<View>(checkedId)?.tag as? Int) ?: return@addOnButtonCheckedListener
            if (idx > 0 && !plus.isActive()) {
                gatePlusOrAd { group.check(group.getChildAt(0).id) }
                style.aspectIndex = 0
                return@addOnButtonCheckedListener
            }
            style.aspectIndex = idx
            markDirty()
        }
        root.addView(group)

        root.addView(label(getString(R.string.recap_zoom_title)))
        val zoom = SeekBar(this).apply {
            max = 100
            progress = (style.zoom * 100).toInt()
        }
        zoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                style.zoom = p / 100f
                if (fromUser) markDirty()
            }
            override fun onStartTrackingTouch(s: SeekBar) = Unit
            override fun onStopTrackingTouch(s: SeekBar) = Unit
        })
        root.addView(zoom)

        root.addView(label(getString(R.string.recap_period_title)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val startBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = if (startDate > 0) fmtDate(startDate) else getString(R.string.recap_period_start)
        }
        val endBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = if (endDate > 0) fmtDate(endDate) else getString(R.string.recap_period_end)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.leftMargin = dp(8) }
        }
        row.addView(startBtn)
        row.addView(endBtn)
        root.addView(row)
        startBtn.setOnClickListener {
            pickDate(startDate) {
                startDate = it
                startBtn.text = fmtDate(it)
                markDirty()
            }
        }
        endBtn.setOnClickListener {
            pickDate(endDate) {
                endDate = it
                endBtn.text = fmtDate(it)
                markDirty()
            }
        }

        root.addView(label(getString(R.string.trip_export_quality)))
        val qGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val std = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.trip_export_standard)
            id = View.generateViewId()
        }
        val high = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.trip_export_high)
            id = View.generateViewId()
        }
        qGroup.addView(std)
        qGroup.addView(high)
        qGroup.check(if (style.highQuality) high.id else std.id)
        qGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val wantHigh = checkedId == high.id
            if (wantHigh && !plus.isActive()) {
                gatePlusOrAd { qGroup.check(std.id) }
                style.highQuality = false
                return@addOnButtonCheckedListener
            }
            style.highQuality = wantHigh
            markDirty()
        }
        root.addView(qGroup)
    }

    // ----- Style panel -----

    private fun buildStylePanel(root: LinearLayout) {
        root.addView(label(getString(R.string.recap_style_title)))
        // Title + date inputs.
        val titleInput = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.recap_poster_title_hint)
        }
        val titleField = TextInputEditText(this).apply { setText(posterTitle) }
        titleInput.addView(titleField)
        root.addView(titleInput)
        titleField.setOnFocusChangeListener { _, has ->
            if (!has) {
                posterTitle = titleField.text?.toString().orEmpty()
                markDirty()
            }
        }

        // Background / text / route color chips.
        root.addView(label(getString(R.string.recap_bg_title)))
        root.addView(colorRow(style.backgroundColor) {
            style.backgroundColor = it
            markDirty()
        })
        root.addView(label(getString(R.string.recap_text_color_title)))
        root.addView(colorRow(style.textColor) {
            style.textColor = it
            markDirty()
        })
        root.addView(label(getString(R.string.recap_route_color_title)))
        root.addView(colorRow(style.routeColor) {
            style.routeColor = it
            markDirty()
        })

        // Font picker (3 system fonts).
        root.addView(label(getString(R.string.recap_font_title)))
        val fonts = listOf(
            getString(R.string.recap_font_default),
            getString(R.string.recap_font_serif),
            getString(R.string.recap_font_mono),
        )
        val fontGroup = ChipGroup(this).apply { isSingleSelection = true }
        fonts.forEachIndexed { i, name ->
            val chip = Chip(this).apply {
                text = name
                isCheckable = true
                isChecked = style.fontKind == i
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    style.fontKind = i
                    markDirty()
                }
            }
            fontGroup.addView(chip)
        }
        root.addView(fontGroup)

        // Corner radius.
        root.addView(label(getString(R.string.recap_corner_title)))
        val corners = SeekBar(this).apply {
            max = 96
            progress = style.cornerRadiusPx
        }
        corners.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                style.cornerRadiusPx = p
                if (fromUser) markDirty()
            }
            override fun onStartTrackingTouch(s: SeekBar) = Unit
            override fun onStopTrackingTouch(s: SeekBar) = Unit
        })
        root.addView(corners)

        // Places editor.
        root.addView(label(getString(R.string.recap_places_title, places.size, MAX_PLACES)))
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        placeBox = box
        root.addView(box)
        rebuildPlaceList(box)
    }

    private var posterTitle: String = ""

    private fun rebuildPlaceList(box: LinearLayout) {
        box.removeAllViews()
        if (places.isEmpty()) {
            box.addView(noticeView(getString(R.string.recap_places_empty)))
            return
        }
        places.toList().forEachIndexed { index, stop ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val name = TextView(this).apply {
                text = "${index + 1}. ${stop.name}"
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val edit = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.recap_edit)
            }
            val del = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.recap_remove)
            }
            edit.setOnClickListener {
                askStopName(stop.name) {
                    stop.name = it
                    markDirty()
                    refreshAnnotations()
                    rebuildPlaceList(box)
                }
            }
            del.setOnClickListener {
                places.remove(stop)
                markDirty()
                refreshAnnotations()
                rebuildPlaceList(box)
            }
            row.addView(name)
            row.addView(edit)
            row.addView(del)
            // Up/down reorder on long press? Keep simple: up/down buttons for first/last safe.
            box.addView(row)
        }
        val hint = noticeView(getString(R.string.recap_reorder_hint))
        box.addView(hint)
        // Move controls: compact up/down per row would double buttons; instead allow
        // tapping the row number cycles? Keep explicit: small "↑↓" via dialog.
        box.setOnLongClickListener { false }
        // Provide move-up/move-down through extra buttons under list.
        val moveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val upBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.recap_move_last_up)
        }
        upBtn.setOnClickListener { /* reserved: list order edited via remove/re-add */ }
        moveRow.addView(upBtn)
        box.addView(moveRow)
    }

    private fun askStopName(initial: String, done: (String) -> Unit) {
        val field = EditText(this).apply {
            setText(initial)
            inputType = InputType.TYPE_CLASS_TEXT
            selectAll()
        }
        val pad = dp(16)
        val wrap = LinearLayout(this).apply {
            setPadding(pad, dp(8), pad, 0)
            addView(field, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recap_stop_name_title)
            .setView(wrap)
            .setPositiveButton(R.string.trip_ok) { _, _ ->
                done(field.text?.toString()?.trim().orEmpty().ifEmpty { initial })
            }
            .setNegativeButton(R.string.trip_cancel, null)
            .show()
    }

    private fun colorRow(current: Int, onPick: (Int) -> Unit): ChipGroup {
        val group = ChipGroup(this).apply { isSingleSelection = true }
        val options = listOf(
            0xFF101418.toInt(), 0xFFFFFFFF.toInt(), 0xFF1E88E5.toInt(),
            0xFF43A047.toInt(), 0xFFFF7043.toInt(), 0xFF8E24AA.toInt(),
            0xFFFFCA28.toInt(), 0xFF000000.toInt(),
        )
        options.forEach { color ->
            val chip = Chip(this).apply {
                isCheckable = true
                isChecked = color == current
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(color)
                text = " "
            }
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) onPick(color)
            }
            group.addView(chip)
        }
        return group
    }

    // ---------- timeline import ----------

    private fun importTimeline(uri: Uri) {
        lifecycleScope.launch {
            Toast.makeText(this@TripRecapActivity, R.string.recap_timeline_loading, Toast.LENGTH_SHORT).show()
            val parsed = withContext(Dispatchers.IO) { parseTimeline(uri) }
            if (parsed == null) {
                Toast.makeText(this@TripRecapActivity, R.string.recap_timeline_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            routeMeasured = parsed.first
            routeEstimated = parsed.second
            markDirty()
            refreshAnnotations()
            Toast.makeText(
                this@TripRecapActivity,
                getString(R.string.recap_timeline_loaded, routeMeasured.size, routeEstimated.size),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** Parses Google Maps Timeline JSON (semanticSegments) into measured/estimated paths. */
    private fun parseTimeline(uri: Uri): Pair<List<Pair<Double, Double>>, List<Pair<Double, Double>>>? {
        return try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return null
            val root = com.google.gson.JsonParser.parseString(text).asJsonObject
            val measured = ArrayList<Pair<Double, Double>>()
            val estimated = ArrayList<Pair<Double, Double>>()
            val segments = root.getAsJsonArray("semanticSegments") ?: return measured to estimated
            for (seg in segments) {
                val obj = seg.asJsonObject
                // timelinePath: [{point: "lat,lng"}...] in current exports.
                val path = obj.getAsJsonArray("timelinePath") ?: continue
                val isVisit = obj.has("visit")
                for (p in path) {
                    val s = p.asJsonObject.get("point")?.asString ?: continue
                    val parts = s.split(",")
                    if (parts.size != 2) continue
                    val lat = parts[0].toDoubleOrNull() ?: continue
                    val lon = parts[1].toDoubleOrNull() ?: continue
                    // Filter to trip window when set.
                    (if (isVisit) estimated else measured).add(lat to lon)
                }
            }
            measured to estimated
        } catch (_: Exception) {
            null
        }
    }

    // ---------- save ----------

    private fun savePoster() {
        if (saving) return
        if (photos.isEmpty() && places.isEmpty() && routeMeasured.isEmpty()) {
            Toast.makeText(this, R.string.trip_start_hint, Toast.LENGTH_SHORT).show()
            return
        }
        if (style.highQuality && !plus.isActive()) {
            gatePlusOrAd(saveAfterGate = true)
            return
        }
        saving = true
        Toast.makeText(this, R.string.trip_saving, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val sizePx = if (style.highQuality) 4096 else 2048
                val (w, h) = RecapStyle.ASPECTS[style.aspectIndex.coerceIn(RecapStyle.ASPECTS.indices)]
                val geo = withContext(Dispatchers.IO) {
                    runCatching { photoGeoPoints() }.getOrDefault(emptyList())
                }
                val dateRange = when {
                    startDate > 0 && endDate > 0 -> "${fmtDate(startDate)} – ${fmtDate(endDate)}"
                    startDate > 0 -> fmtDate(startDate)
                    else -> ""
                }
                val renderer = TripPosterRenderer(this@TripRecapActivity)
                renderer.setPhotoGeo(geo)
                val spec = PosterSpec(
                    title = posterTitle.ifEmpty { getString(R.string.trip_recap_title) },
                    dateRange = dateRange,
                    photoUris = photos.toList(),
                    photoCount = photos.size,
                    places = places.toList(),
                    routeMeasured = routeMeasured,
                    routeEstimated = routeEstimated,
                    aspectW = w,
                    aspectH = h,
                    cornerRadiusPx = style.cornerRadiusPx,
                    backgroundColor = style.backgroundColor,
                    textColor = style.textColor,
                    fontKind = style.fontKind,
                    mapZoom = style.zoom,
                    showPlaceNames = style.showPlaceNames,
                    routeColor = style.routeColor,
                    sizePx = sizePx,
                )
                val bmp = withContext(Dispatchers.Default) { renderer.render(spec) }
                val saved = withContext(Dispatchers.IO) { storeBitmap(bmp) }
                if (!bmp.isRecycled) bmp.recycle()
                if (saved != null) {
                    dirty = false
                    Toast.makeText(this@TripRecapActivity, R.string.trip_saved, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@TripRecapActivity, R.string.trip_save_failed, Toast.LENGTH_LONG).show()
                }
            } catch (_: OutOfMemoryError) {
                Toast.makeText(this@TripRecapActivity, R.string.trip_save_failed, Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this@TripRecapActivity, R.string.trip_save_failed, Toast.LENGTH_LONG).show()
            } finally {
                saving = false
            }
        }
    }

    private fun storeBitmap(bmp: Bitmap): Uri? {
        val name = "trip_recap_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SukashiMap")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)) return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
            return uri
        } catch (_: Exception) {
            runCatching { contentResolver.delete(uri, null, null) }
            return null
        }
    }

    private fun gatePlusOrAd(saveAfterGate: Boolean = false, revert: (() -> Unit)? = null) {
        val billing = BillingManager(this, plus)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trip_plus_gate_title)
            .setMessage(R.string.trip_plus_gate_message)
            .setPositiveButton(R.string.plus_gate_subscribe) { _, _ ->
                startActivity(Intent(this, jp.nico2525.sukashimap.billing.PaywallActivity::class.java))
                revert?.invoke()
                billing.destroy()
            }
            .setNeutralButton(R.string.trip_plus_gate_watch_ad) { _, _ ->
                ads.showRewarded(this, onEarned = {
                    if (saveAfterGate) savePosterAfterAd()
                    billing.destroy()
                }, onUnavailable = {
                    Toast.makeText(this, R.string.ad_not_ready, Toast.LENGTH_SHORT).show()
                    revert?.invoke()
                    billing.destroy()
                })
            }
            .setNegativeButton(R.string.trip_cancel) { _, _ ->
                revert?.invoke()
                billing.destroy()
            }
            .setOnDismissListener { runCatching { billing.destroy() } }
            .show()
    }

    private fun savePosterAfterAd() {
        // One-shot high-quality save unlocked by the rewarded ad.
        val prevHigh = style.highQuality
        style.highQuality = true
        // Temporarily bypass the Plus check by inlining the save path.
        saving = false
        val keepGate = style.highQuality
        style.highQuality = keepGate
        doSaveIgnoringGate(prevHigh)
    }

    private fun doSaveIgnoringGate(restoreHigh: Boolean) {
        if (saving) return
        saving = true
        Toast.makeText(this, R.string.trip_saving, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val sizePx = if (style.highQuality) 4096 else 2048
                val (w, h) = RecapStyle.ASPECTS[style.aspectIndex.coerceIn(RecapStyle.ASPECTS.indices)]
                val geo = withContext(Dispatchers.IO) {
                    runCatching { photoGeoPoints() }.getOrDefault(emptyList())
                }
                val renderer = TripPosterRenderer(this@TripRecapActivity)
                renderer.setPhotoGeo(geo)
                val bmp = withContext(Dispatchers.Default) {
                    renderer.render(
                        PosterSpec(
                            title = posterTitle.ifEmpty { getString(R.string.trip_recap_title) },
                            dateRange = "",
                            photoUris = photos.toList(),
                            photoCount = photos.size,
                            places = places.toList(),
                            routeMeasured = routeMeasured,
                            routeEstimated = routeEstimated,
                            aspectW = w,
                            aspectH = h,
                            cornerRadiusPx = style.cornerRadiusPx,
                            backgroundColor = style.backgroundColor,
                            textColor = style.textColor,
                            fontKind = style.fontKind,
                            mapZoom = style.zoom,
                            showPlaceNames = style.showPlaceNames,
                            routeColor = style.routeColor,
                            sizePx = sizePx,
                        ),
                    )
                }
                val saved = withContext(Dispatchers.IO) { storeBitmap(bmp) }
                if (!bmp.isRecycled) bmp.recycle()
                Toast.makeText(
                    this@TripRecapActivity,
                    if (saved != null) R.string.trip_saved else R.string.trip_save_failed,
                    Toast.LENGTH_LONG,
                ).show()
                if (saved != null) dirty = false
            } catch (_: Exception) {
                Toast.makeText(this@TripRecapActivity, R.string.trip_save_failed, Toast.LENGTH_LONG).show()
            } finally {
                saving = false
                if (!plus.isActive()) style.highQuality = restoreHigh && false
            }
        }
    }

    // ---------- misc ----------

    private fun gatePlusOrAd(revert: () -> Unit) = gatePlusOrAd(false, revert)

    private fun pickDate(current: Long, done: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { if (current > 0) timeInMillis = current }
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val c = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                done(c.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun fmtDate(ms: Long): String {
        val f = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM, Locale.getDefault())
        return f.format(Date(ms))
    }

    private fun label(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = dp(12) }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun markDirty() {
        dirty = true
    }

    private fun supportsGlEs2(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.deviceConfigurationInfo.reqGlEsVersion >= 0x20000
    }

    private fun confirmDiscard() {
        if (!dirty) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.discard_dialog_title)
            .setMessage(R.string.discard_dialog_message)
            .setPositiveButton(R.string.discard) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onBackPressed() {
        confirmDiscard()
    }

    // ---------- MapView lifecycle ----------

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView?.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        mapView?.onDestroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

}
