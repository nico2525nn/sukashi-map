package jp.nico2525.sukashimap.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import jp.nico2525.sukashimap.MainActivity
import jp.nico2525.sukashimap.R
import jp.nico2525.sukashimap.data.Airline
import jp.nico2525.sukashimap.data.Airport
import jp.nico2525.sukashimap.data.AirlineRepository
import jp.nico2525.sukashimap.data.AirportRepository
import jp.nico2525.sukashimap.data.DateTimeDisplayMode
import jp.nico2525.sukashimap.data.DirectionsService
import jp.nico2525.sukashimap.data.HistoryManager
import jp.nico2525.sukashimap.data.OverlayPosition
import jp.nico2525.sukashimap.data.RailwayRepository
import jp.nico2525.sukashimap.data.Station
import jp.nico2525.sukashimap.data.TransportInfo
import jp.nico2525.sukashimap.data.TransportMode
import jp.nico2525.sukashimap.databinding.LayoutTransportBottomSheetBinding
import jp.nico2525.sukashimap.prefs.SettingsStore
import jp.nico2525.sukashimap.util.PhotonApi
import jp.nico2525.sukashimap.util.PhotonPlace
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transport info editor bottom sheet: 4 mode tabs, history chips, date/time,
 * display/position tabs, map switch, style knobs and overlay-photo EXIF import.
 * Returns the result via parentFragmentManager.setFragmentResult("transport_result", bundle).
 */
class TransportBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutTransportBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var airportRepo: AirportRepository
    private lateinit var airlineRepo: AirlineRepository
    private lateinit var railwayRepo: RailwayRepository
    private lateinit var history: HistoryManager
    private lateinit var settings: SettingsStore

    private var mode: TransportMode = TransportMode.AIRPLANE
    private var date: LocalDate = LocalDate.now()
    private var time: LocalTime = LocalTime.now().withSecond(0).withNano(0)
    private var displayMode: DateTimeDisplayMode = DateTimeDisplayMode.DATE_TIME
    private var overlayPos: OverlayPosition = OverlayPosition.TOP_LEFT
    private var mapPos: OverlayPosition = OverlayPosition.TOP_RIGHT

    private var pickedAirline: Airline? = null
    private var depAirport: Airport? = null
    private var arrAirport: Airport? = null
    private var depStation: Station? = null
    private var arrStation: Station? = null
    private var depPlace: PhotonPlace? = null
    private var destPlace: PhotonPlace? = null
    private val viaLines = mutableSetOf<String>()
    private var pickedOperator: String = ""

    /** "Tokyo Station (JR東日本)" style; falls back to typed text. */
    private fun stationFull(st: Station?, typed: String): String {
        if (st == null) return typed
        val op = shortOperator(st.operator.ifBlank { pickedOperator })
        return if (op.isBlank()) st.name else "${st.name} ($op)"
    }

    /** 東日本旅客鉄道→JR東日本 etc. for compact overlay lines. */
    private fun shortOperator(op: String): String {
        val t = op.trim()
        if (t.isEmpty()) return ""
        JR_SHORT[t]?.let { return it }
        // Generic: strip 旅客鉄道/鉄道 suffixes for compactness.
        return t.replace("旅客鉄道", "").replace("鉄道", "")
    }

    private var textColor: Int = Color.WHITE
    private var mapLineColor: Int = Color.parseColor("#FF5252")
    private var mapBorderColor: Int = Color.WHITE
    private var fontIndex: Int = 0

    private var searchJob: Job? = null

    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
            val raw = result.data?.getStringExtra(MainActivity.EXTRA_CAPTURED_URI)
                ?: return@registerForActivityResult
            loadPhotoInfo(android.net.Uri.parse(raw))
        }

    private fun loadPhotoInfo(uri: android.net.Uri) {
        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { ins ->
                val exif = ExifInterface(ins)
                val dt = exif.dateTime ?: exif.dateTimeOriginal
                if (dt != null && dt > 0) {
                    val ldt = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(dt), java.time.ZoneId.systemDefault(),
                    )
                    date = ldt.toLocalDate()
                    time = ldt.toLocalTime().withSecond(0).withNano(0)
                    refreshDateTime()
                    Toast.makeText(requireContext(), R.string.transport_photo_info_loaded, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = LayoutTransportBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        airportRepo = AirportRepository(ctx)
        airlineRepo = AirlineRepository(ctx)
        railwayRepo = RailwayRepository(ctx)
        history = HistoryManager(ctx)
        settings = SettingsStore(ctx)
        fontIndex = settings.overlayFontIndex("all")
        textColor = settings.textColor("all")
        mapLineColor = settings.mapLineColor("all")
        mapBorderColor = settings.mapBorderColor("all")

        seedFromArguments()
        setupTabs()
        setupAutocomplete()
        setupDates()
        setupColors()
        setupFonts()
        refreshDateTime()
        showForm(0)
        refreshHistory()

        binding.btnPickOverlayPhoto.setOnClickListener {
            photoPicker.launch(
                android.content.Intent(requireContext(), jp.nico2525.sukashimap.GalleryActivity::class.java)
                    .putExtra(jp.nico2525.sukashimap.GalleryActivity.EXTRA_PICK_MODE, true)
                    .putExtra(jp.nico2525.sukashimap.GalleryActivity.EXTRA_OWN_ONLY, false),
            )
        }
        binding.btnTransportConfirm.setOnClickListener { confirm() }
    }

    private fun seedFromArguments() {
        val a = arguments ?: return
        runCatching { mode = TransportMode.valueOf(a.getString(TransportResult.KEY_MODE, mode.name)!!) }
        runCatching { date = LocalDate.parse(a.getString(TransportResult.KEY_DATE, date.toString())) }
        runCatching { time = LocalTime.parse(a.getString(TransportResult.KEY_TIME, time.toString())) }
        runCatching { displayMode = DateTimeDisplayMode.valueOf(a.getString(TransportResult.KEY_DATE_TIME_DISPLAY_MODE, displayMode.name)!!) }
        runCatching { overlayPos = OverlayPosition.valueOf(a.getString(TransportResult.KEY_OVERLAY_POSITION, overlayPos.name)!!) }
        runCatching { mapPos = OverlayPosition.valueOf(a.getString(TransportResult.KEY_MAP_POSITION, mapPos.name)!!) }
        binding.switchShowMap.isChecked = a.getBoolean(TransportResult.KEY_SHOW_MAP, true)
        binding.switchShowWatermark.isChecked = a.getBoolean(TransportResult.KEY_SHOW_WATERMARK, false)
    }

    // ---- tabs ----
    private fun setupTabs() {
        binding.transportModeTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { showForm(tab.position); refreshHistory() }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.transportModeTabs.getTabAt(mode.ordinal)?.select()
        val displayOrder = listOf(DateTimeDisplayMode.DATE_TIME, DateTimeDisplayMode.DATE_ONLY, DateTimeDisplayMode.NONE)
        binding.dateTimeDisplayTabs.getTabAt(displayOrder.indexOf(displayMode).coerceAtLeast(0))?.select()
        binding.dateTimeDisplayTabs.addOnTabSelectedListener(simpleTab { displayMode = displayOrder[it.coerceIn(0, 2)] })
        selectPosTab(binding.overlayPositionTabs, overlayPos)
        binding.overlayPositionTabs.addOnTabSelectedListener(simpleTab { overlayPos = OverlayPosition.entries[it] })
        selectPosTab(binding.mapPositionTabs, mapPos)
        binding.mapPositionTabs.addOnTabSelectedListener(simpleTab { mapPos = OverlayPosition.entries[it] })
    }

    private fun simpleTab(onSelect: (Int) -> Unit) = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) = onSelect(tab.position)
        override fun onTabUnselected(tab: TabLayout.Tab) {}
        override fun onTabReselected(tab: TabLayout.Tab) {}
    }

    private fun selectPosTab(tabs: TabLayout, pos: OverlayPosition) {
        tabs.getTabAt(OverlayPosition.entries.indexOf(pos).coerceAtLeast(0))?.select()
    }

    private fun showForm(position: Int) {
        mode = TransportMode.entries[position.coerceIn(0, 3)]
        binding.formAirplane.visibility = if (mode == TransportMode.AIRPLANE) View.VISIBLE else View.GONE
        binding.formTrain.visibility = if (mode == TransportMode.TRAIN) View.VISIBLE else View.GONE
        binding.formOther.visibility = if (mode == TransportMode.OTHER) View.VISIBLE else View.GONE
        binding.formSpot.visibility = if (mode == TransportMode.SPOT) View.VISIBLE else View.GONE
        binding.tvMapDisclaimer.setText(
            if (mode == TransportMode.TRAIN) R.string.map_disclaimer_train_jp else R.string.map_disclaimer,
        )
    }

    // ---- inputs ----
    private fun setupAutocomplete() {
        airportAuto(binding.inputDepAirport) { depAirport = it; binding.inputDepAirport.setText(it.display()) }
        airportAuto(binding.inputArrAirport) { arrAirport = it; binding.inputArrAirport.setText(it.display()) }
        viewLifecycleOwner.lifecycleScope.launch {
            val airlines = airlineRepo.all()
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, airlines.map { it.display() })
            binding.inputAirline.setAdapter(adapter)
            binding.inputAirline.setOnItemClickListener { _, _, pos, _ ->
                val label = adapter.getItem(pos) ?: return@setOnItemClickListener
                pickedAirline = airlines.firstOrNull { it.display() == label }
            }
        }
        binding.inputAirline.setOnClickListener { (it as? AutoCompleteTextView)?.showDropDown() }

        binding.inputRailLine.threshold = 1
        binding.inputRailLine.addTextChangedListener(simpleWatcher { q ->
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                val lines = railwayRepo.searchLines(q)
                binding.inputRailLine.setAdapter(
                    ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, lines.map { it.name }),
                )
                binding.inputRailLine.showDropDown()
                renderViaChips(lines.map { it.name }.take(8))
                binding.inputRailLine.setOnItemClickListener { _, _, pos, _ ->
                    lines.getOrNull(pos)?.let { picked ->
                        // Suppress the watcher re-query while committing the pick.
                        binding.inputRailLine.setText(picked.name, false)
                        binding.inputRailLine.dismissDropDown()
                        pickedOperator = picked.operator
                        depStation = null
                        arrStation = null
                        binding.inputDepStation.setText("")
                        binding.inputArrStation.setText("")
                        maybeGeometries()
                    }
                }
            }
        })
        stationAuto(binding.inputDepStation) { depStation = it }
        stationAuto(binding.inputArrStation) { arrStation = it }
        binding.switchShortestRoute.setOnCheckedChangeListener { _, checked ->
            if (checked) autoShortest()
        }
        photonAuto(binding.inputDepPlace) { depPlace = it }
        photonAuto(binding.inputDestination) { destPlace = it }
    }

    private fun airportAuto(field: AutoCompleteTextView, onPick: (Airport) -> Unit) {
        field.threshold = 1
        var lastHits: List<Airport> = emptyList()
        field.addTextChangedListener(simpleWatcher { q ->
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                val hits = airportRepo.search(q)
                lastHits = hits
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, hits.map { it.display() })
                field.setAdapter(adapter)
                field.showDropDown()
                field.setOnItemClickListener { _, _, pos, _ ->
                    hits.getOrNull(pos)?.let(onPick)
                }
            }
        })
        // If the user typed a code without tapping a suggestion, resolve on focus loss.
        field.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val typed = field.text?.toString().orEmpty()
                val exact = lastHits.firstOrNull {
                    it.code().equals(typed, true) || it.display().equals(typed, true)
                } ?: lastHits.firstOrNull { it.code().equals(typed.substringBefore(" ").trim(), true) }
                if (exact != null) onPick(exact)
            }
        }
    }

    private var suppressStationWatcher = false

    private fun stationAuto(field: AutoCompleteTextView, onPick: (Station) -> Unit) {
        field.threshold = 1
        field.addTextChangedListener(simpleWatcher { q ->
            if (suppressStationWatcher) return@simpleWatcher
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                showStationHits(field, onPick, q.ifBlank { null })
            }
        })
        // Focus with empty text lists every station of the chosen line.
        field.setOnFocusChangeListener { _, hasFocus ->
            if (suppressStationWatcher) return@setOnFocusChangeListener
            if (hasFocus && field.text?.toString().isNullOrBlank()) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    showStationHits(field, onPick, null)
                }
            }
        }
    }

    private var stationDialog: androidx.appcompat.app.AlertDialog? = null

    private suspend fun showStationHits(field: AutoCompleteTextView, onPick: (Station) -> Unit, query: String?) {
        val line = binding.inputRailLine.text?.toString().orEmpty().ifBlank { null }
        val hits = if (query == null) {
            if (line == null) railwayRepo.stations().take(60) else railwayRepo.stationsOfLine(line)
        } else {
            railwayRepo.searchStations(query, line)
        }
        if (hits.isEmpty()) {
            Toast.makeText(requireContext(), R.string.transport_no_stations, Toast.LENGTH_SHORT).show()
            return
        }
        // BottomSheet + soft-keyboard steals AutoComplete dropdowns; use a dialog instead.
        val labels = hits.map { "${it.name} (${it.line})" }.toTypedArray()
        withContext(Dispatchers.Main) {
            stationDialog?.dismiss()
            stationDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (query == null) R.string.transport_pick_station else R.string.transport_pick_station_match)
                .setItems(labels) { _, which ->
                    hits.getOrNull(which)?.let {
                        suppressStationWatcher = true
                        field.setText(it.name)
                        field.dismissDropDown()
                        onPick(it)
                        maybeGeometries()
                        autoShortest()
                        field.post { suppressStationWatcher = false }
                    }
                }
                .setNegativeButton(R.string.crop_cancel, null)
                .show()
        }
    }

    private fun photonAuto(field: AutoCompleteTextView, onPick: (PhotonPlace) -> Unit) {
        field.threshold = 1
        field.addTextChangedListener(simpleWatcher { q ->
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                val hits = PhotonApi.search(q, lang = resources.configuration.locales.get(0)?.language ?: "en")
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, hits.map { it.displayName })
                field.setAdapter(adapter)
                if (hits.isNotEmpty()) field.showDropDown()
                field.setOnItemClickListener { _, _, pos, _ ->
                    hits.getOrNull(pos)?.let {
                        field.setText(it.displayName)
                        onPick(it)
                    }
                }
            }
        })
    }

    private fun simpleWatcher(onText: (String) -> Unit) = object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) = onText(s?.toString().orEmpty())
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    private fun renderViaChips(candidates: List<String>) {
        binding.viaLineChips.removeAllViews()
        for (name in candidates) {
            val chip = Chip(requireContext()).apply {
                text = name
                isCheckable = true
                isChecked = viaLines.contains(name)
                setOnClickListener {
                    // First tap commits the main line if empty/different; tap again to toggle via.
                    val current = binding.inputRailLine.text?.toString().orEmpty()
                    if (current != name) {
                        binding.inputRailLine.setText(name, false)
                        viewLifecycleOwner.lifecycleScope.launch {
                            pickedOperator = railwayRepo.lines().firstOrNull { it.name == name }?.operator.orEmpty()
                        }
                        depStation = null
                        arrStation = null
                        binding.inputDepStation.setText("")
                        binding.inputArrStation.setText("")
                        maybeGeometries()
                    }
                    isChecked = !isChecked
                    if (isChecked) viaLines.add(name) else viaLines.remove(name)
                    maybeGeometries()
                }
            }
            binding.viaLineChips.addView(chip)
        }
        if (candidates.isEmpty()) {
            val hint = Chip(requireContext()).apply {
                text = getString(R.string.transport_via_hint)
                isEnabled = false
            }
            binding.viaLineChips.addView(hint)
        }
    }

    private fun maybeGeometries() {
        viewLifecycleOwner.lifecycleScope.launch {
            val line = binding.inputRailLine.text?.toString().orEmpty()
            if (line.isBlank()) return@launch
            val geo = railwayRepo.geometriesOfLine(line).toMutableList()
            for (v in viaLines) geo += railwayRepo.geometriesOfLine(v)
            pendingGeometries = geo
        }
    }

    private var pendingGeometries: List<String> = emptyList()

    /** Same line + ordered stations on that line => shortest-route flag true. */
    private fun autoShortest() {
        val d = depStation
        val a = arrStation
        if (d == null || a == null || !binding.switchShortestRoute.isChecked) return
        viewLifecycleOwner.lifecycleScope.launch {
            val line = binding.inputRailLine.text?.toString().orEmpty()
            val ordered = railwayRepo.stationsOfLine(line).map { it.name }
            val i = ordered.indexOf(d.name)
            val j = ordered.indexOf(a.name)
            binding.switchShortestRoute.isChecked = line.isNotBlank() && d.line == a.line &&
                (line == d.line || viaLines.isEmpty()) && i >= 0 && j >= 0
        }
    }

    private fun setupDates() {
        binding.inputDate.setOnClickListener {
            val picker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.hint_date))
                .setSelection(
                    date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                )
                .build()
            picker.addOnPositiveButtonClickListener { ms ->
                date = java.time.Instant.ofEpochMilli(ms)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                refreshDateTime()
            }
            picker.show(parentFragmentManager, "date")
        }
        binding.inputTime.setOnClickListener {
            val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTitleText(getString(R.string.hint_time))
                .setHour(time.hour)
                .setMinute(time.minute)
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                .build()
            picker.addOnPositiveButtonClickListener {
                time = LocalTime.of(picker.hour, picker.minute)
                refreshDateTime()
            }
            picker.show(parentFragmentManager, "time")
        }
    }

    private fun refreshDateTime() {
        binding.inputDate.setText(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        binding.inputTime.setText(time.format(DateTimeFormatter.ofPattern("HH:mm")))
    }

    private val palette = listOf(
        Color.WHITE, Color.BLACK, Color.parseColor("#FF5252"), Color.parseColor("#4FC3F7"),
        Color.parseColor("#69F0AE"), Color.parseColor("#FFD740"), Color.parseColor("#7C4DFF"),
    )

    private fun setupColors() {
        colorRow(binding.textColorRow, textColor) { textColor = it }
        colorRow(binding.mapLineColorRow, mapLineColor) { mapLineColor = it }
        colorRow(binding.mapBorderColorRow, mapBorderColor) { mapBorderColor = it }
    }

    private fun colorRow(row: ViewGroup, selected: Int, onPick: (Int) -> Unit) {
        row.removeAllViews()
        for (c in palette) {
            val dot = View(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(96, 96).apply { marginEnd = 16 }
                setBackgroundColor(c)
                isSelected = c == selected
                setOnClickListener { onPick(c); setupColors() }
                contentDescription = getString(R.string.settings_text_color_section)
            }
            row.addView(dot)
        }
    }

    private fun setupFonts() {
        val labels = listOf(
            getString(R.string.transport_font_default), getString(R.string.transport_font_serif),
            getString(R.string.transport_font_sans), getString(R.string.transport_font_mono),
        )
        binding.spinnerFont.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerFont.setSelection(fontIndex.coerceIn(0, 3))
        binding.spinnerFont.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { fontIndex = pos }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }

    private fun refreshHistory() {
        binding.historyChips.removeAllViews()
        val entries = history.load(mode)
        if (entries.isEmpty()) {
            binding.historyChips.addView(Chip(requireContext()).apply {
                text = getString(R.string.transport_history_empty); isEnabled = false
            })
            return
        }
        for (e in entries) {
            binding.historyChips.addView(Chip(requireContext()).apply {
                text = e.title
                setOnClickListener { applyHistory(e.payload) }
            })
        }
    }

    private fun applyHistory(payload: Map<String, String>) {
        suppressStationWatcher = true
        payload["dep"]?.let { binding.inputDepAirport.setText(it) }
        payload["arr"]?.let { binding.inputArrAirport.setText(it) }
        payload["operator"]?.let { pickedOperator = shortOperator(it) }
        payload["line"]?.let { binding.inputRailLine.setText(it, false) }
        payload["train"]?.let { binding.inputTrainName.setText(it) }
        payload["vehicle"]?.let { binding.inputVehicleModel.setText(it) }
        payload["depStation"]?.let {
            binding.inputDepStation.setText(it)
            resolveStationAsync(it, binding.inputRailLine.text?.toString().orEmpty()) { depStation = it }
        }
        payload["arrStation"]?.let {
            binding.inputArrStation.setText(it)
            resolveStationAsync(it, binding.inputRailLine.text?.toString().orEmpty()) { arrStation = it }
        }
        payload["transport"]?.let { binding.inputTransportName.setText(it) }
        payload["dest"]?.let { binding.inputDestination.setText(it) }
        payload["spot1"]?.let { binding.inputSpot1.setText(it) }
        payload["spot2"]?.let { binding.inputSpot2.setText(it) }
        payload["spot3"]?.let { binding.inputSpot3.setText(it) }
        binding.inputRailLine.post { suppressStationWatcher = false }
    }

    private fun resolveStationAsync(name: String, line: String, done: (Station?) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val hits = railwayRepo.searchStations(name, line.ifBlank { null })
            done(hits.firstOrNull { it.name == name } ?: hits.firstOrNull())
            maybeGeometries()
        }
    }

    private fun confirm() {
        viewLifecycleOwner.lifecycleScope.launch {
            val info = buildInfo() ?: return@launch
            settings.setOverlayFontIndex("all", fontIndex)
            settings.setTextColor("all", textColor)
            settings.setMapLineColor("all", mapLineColor)
            settings.setMapBorderColor("all", mapBorderColor)
            history.save(
                HistoryManager.Entry(
                    mode = mode,
                    title = info.routeDisplay().ifBlank { info.transportLabel }.ifBlank { info.formattedDate() },
                    payload = historyPayload(info),
                ),
            )
            val bundle = TransportResult.toBundle(info, overlayPos, fontIndex, textColor, mapLineColor, mapBorderColor)
            parentFragmentManager.setFragmentResult(TransportResult.REQUEST_KEY, bundle)
            dismiss()
        }
    }

    private suspend fun buildInfo(): TransportInfo? {
        val dateTime = java.time.LocalDateTime.of(date, time)
        val showMap = binding.switchShowMap.isChecked
        val showWm = binding.switchShowWatermark.isChecked
        return when (mode) {
            TransportMode.AIRPLANE -> TransportInfo.Airplane(
                airline = pickedAirline ?: airlineFrom(binding.inputAirline.text?.toString().orEmpty()),
                flightNumber = binding.inputFlightNumber.text?.toString().orEmpty(),
                registrationNumber = binding.inputRegNumber.text?.toString().orEmpty(),
                departureAirport = depAirport?.code() ?: binding.inputDepAirport.text?.toString().orEmpty(),
                departureAirportName = depAirport?.name.orEmpty(),
                departureLatitude = depAirport?.lat,
                departureLongitude = depAirport?.lon,
                arrivalAirport = arrAirport?.code() ?: binding.inputArrAirport.text?.toString().orEmpty(),
                arrivalAirportName = arrAirport?.name.orEmpty(),
                arrivalLatitude = arrAirport?.lat,
                arrivalLongitude = arrAirport?.lon,
                dateTime = dateTime, dateTimeDisplayMode = displayMode,
                showMap = showMap, showWatermark = showWm, mapPosition = mapPos,
            )
            TransportMode.TRAIN -> TransportInfo.Train(
                isDomesticRoute = binding.switchDomesticRail.isChecked,
                operator = shortOperator(pickedOperator),
                lineName = binding.inputRailLine.text?.toString().orEmpty(),
                trainName = binding.inputTrainName.text?.toString().orEmpty(),
                vehicleModel = binding.inputVehicleModel.text?.toString().orEmpty(),
                departureStation = depStation?.name ?: binding.inputDepStation.text?.toString().orEmpty(),
                departureStationFull = stationFull(depStation, binding.inputDepStation.text?.toString().orEmpty()),
                departureLatitude = depStation?.lat,
                departureLongitude = depStation?.lon,
                arrivalStation = arrStation?.name ?: binding.inputArrStation.text?.toString().orEmpty(),
                arrivalStationFull = stationFull(arrStation, binding.inputArrStation.text?.toString().orEmpty()),
                arrivalLatitude = arrStation?.lat,
                arrivalLongitude = arrStation?.lon,
                geometries = pendingGeometries.ifEmpty {
                    railwayRepo.geometriesOfLine(binding.inputRailLine.text?.toString().orEmpty())
                },
                viaLines = viaLines.toList(),
                isShortestRouteSelected = binding.switchShortestRoute.isChecked,
                dateTime = dateTime, dateTimeDisplayMode = displayMode,
                showMap = showMap, showWatermark = showWm, mapPosition = mapPos,
            )
            TransportMode.OTHER -> {
                val route = fetchRoute()
                TransportInfo.Other(
                    transportName = binding.inputTransportName.text?.toString().orEmpty(),
                    departurePlace = depPlace?.displayName ?: binding.inputDepPlace.text?.toString().orEmpty(),
                    departureLatitude = depPlace?.latitude,
                    departureLongitude = depPlace?.longitude,
                    departureCountryCode = depPlace?.countryCode,
                    destination = destPlace?.displayName ?: binding.inputDestination.text?.toString().orEmpty(),
                    destinationLatitude = destPlace?.latitude,
                    destinationLongitude = destPlace?.longitude,
                    destinationCountryCode = destPlace?.countryCode,
                    routePolyline = route,
                    dateTime = dateTime, dateTimeDisplayMode = displayMode,
                    showMap = showMap, showWatermark = showWm, mapPosition = mapPos,
                )
            }
            TransportMode.SPOT -> TransportInfo.Spot(
                line1 = binding.inputSpot1.text?.toString().orEmpty(),
                line2 = binding.inputSpot2.text?.toString().orEmpty(),
                line3 = binding.inputSpot3.text?.toString().orEmpty(),
                dateTime = dateTime, dateTimeDisplayMode = displayMode,
                showMap = showMap, showWatermark = showWm, mapPosition = mapPos,
            )
        }
    }

    private suspend fun airlineFrom(text: String): Airline? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val byCode = airlineRepo.findByCode(t)
        if (byCode != null) return byCode
        return airlineRepo.search(t).firstOrNull()?.also { pickedAirline = it }
            ?: Airline("", t).also { pickedAirline = null }
    }

    private suspend fun fetchRoute(): List<String>? {
        val from = depPlace
        val to = destPlace
        if (from == null || to == null) return null
        Toast.makeText(requireContext(), R.string.fetching_route, Toast.LENGTH_SHORT).show()
        return runCatching {
            DirectionsService.fetchDrivingRoutePolyline(from.latitude, from.longitude, to.latitude, to.longitude)
        }.getOrNull() ?: run {
            Toast.makeText(requireContext(), R.string.transport_route_fallback, Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun historyPayload(info: TransportInfo): Map<String, String> {
        val ts = System.currentTimeMillis().toString()
        val base = mutableMapOf("ts" to ts)
        when (info) {
            is TransportInfo.Airplane -> {
                base["dep"] = info.departureAirport
                base["arr"] = info.arrivalAirport
            }
            is TransportInfo.Train -> {
                base["operator"] = info.operator
                base["line"] = info.lineName
                base["train"] = info.trainName
                base["vehicle"] = info.vehicleModel
                base["depStation"] = info.departureStation
                base["arrStation"] = info.arrivalStation
            }
            is TransportInfo.Other -> {
                base["transport"] = info.transportName
                base["dep"] = info.departurePlace
                base["dest"] = info.destination
            }
            is TransportInfo.Spot -> {
                base["spot1"] = info.line1
                base["spot2"] = info.line2
                base["spot3"] = info.line3
            }
        }
        return base
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(seed: Bundle? = null): TransportBottomSheet =
            TransportBottomSheet().apply { arguments = seed }

        private val JR_SHORT = mapOf(
            "北海道旅客鉄道" to "JR北海道",
            "東日本旅客鉄道" to "JR東日本",
            "東海旅客鉄道" to "JR東海",
            "西日本旅客鉄道" to "JR西日本",
            "四国旅客鉄道" to "JR四国",
            "九州旅客鉄道" to "JR九州",
            "日本貨物鉄道" to "JR貨物",
        )
    }
}
