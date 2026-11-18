package jp.nico2525.sukashimap.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FlightLand
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.nico2525.sukashimap.data.AirlineEntry
import jp.nico2525.sukashimap.data.AirportRecord
import jp.nico2525.sukashimap.data.PlaceCandidate
import jp.nico2525.sukashimap.data.RailwayLineEntry
import jp.nico2525.sukashimap.data.RailwayRepository
import jp.nico2525.sukashimap.data.StationRecord
import jp.nico2525.sukashimap.model.DateTimeDisplayMode
import jp.nico2525.sukashimap.model.LocationDisplayMode
import jp.nico2525.sukashimap.model.OverlayCorner
import jp.nico2525.sukashimap.model.OverlayStyle
import jp.nico2525.sukashimap.model.TransportInfo
import jp.nico2525.sukashimap.model.TransportMode
import jp.nico2525.sukashimap.ui.SukashiViewModel
import jp.nico2525.sukashimap.ui.components.ExpressiveConnectedSegmentedRow
import jp.nico2525.sukashimap.ui.components.ExpressiveSectionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private enum class SearchDialogKind {
    AIRLINE,
    DEP_AIRPORT,
    ARR_AIRPORT,
    RAILWAY_LINE,
    DEP_STATION,
    ARR_STATION,
    DEP_PLACE,
    DEST_PLACE
}

@Composable
fun TransportInfoSheet(
    viewModel: SukashiViewModel,
    initialInfo: TransportInfo,
    currentStyle: OverlayStyle,
    recentHistory: List<TransportInfo>,
    onDismiss: () -> Unit,
    onApply: (TransportInfo, OverlayStyle) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var draft by remember(initialInfo) { mutableStateOf(initialInfo) }
    var styleDraft by remember(currentStyle) { mutableStateOf(currentStyle) }
    var activeSearchKind by remember { mutableStateOf<SearchDialogKind?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Live-compute candidate via lines when Japanese rail stations change
    LaunchedEffect(
        draft.mode,
        draft.isDomesticJapanRail,
        draft.departureStationLat,
        draft.departureStationLon,
        draft.arrivalStationLat,
        draft.arrivalStationLon,
        draft.railwayLineName
    ) {
        if (draft.mode == TransportMode.TRAIN &&
            draft.isDomesticJapanRail &&
            draft.departureStationLat != null && draft.departureStationLon != null &&
            draft.arrivalStationLat != null && draft.arrivalStationLon != null
        ) {
            val res = viewModel.railwayRepository.computeTrainRoute(
                depStationName = draft.departureStationName,
                depLat = draft.departureStationLat!!,
                depLon = draft.departureStationLon!!,
                depGroupCode = draft.departureStationGroupCode,
                arrStationName = draft.arrivalStationName,
                arrLat = draft.arrivalStationLat!!,
                arrLon = draft.arrivalStationLon!!,
                arrGroupCode = draft.arrivalStationGroupCode,
                preferredLineName = draft.railwayLineName,
                selectedViaLines = draft.selectedViaLines,
                isAutoShortest = draft.isAutoShortestVia
            )
            draft = draft.copy(
                candidateViaLines = res.candidateViaLines,
                railwayLineName = draft.railwayLineName.ifBlank { res.autoDetectedLineName.orEmpty() },
                railwayCompany = draft.railwayCompany.ifBlank { res.autoDetectedCompany.orEmpty() }
            )
        }
    }

    val modeHistory = remember(recentHistory, draft.mode) {
        recentHistory.filter { it.mode == draft.mode }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "透かし情報",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = { onApply(draft, styleDraft) },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("適用")
                }
            }

            ExpressiveConnectedSegmentedRow(
                options = TransportMode.entries,
                selected = draft.mode,
                onSelect = { draft = draft.copy(mode = it) },
                labelProvider = {
                    when (it) {
                        TransportMode.AIRPLANE -> "フライト"
                        TransportMode.TRAIN -> "鉄道"
                        TransportMode.OTHER -> "移動"
                        TransportMode.SPOT -> "スポット"
                    }
                },
                iconProvider = {
                    when (it) {
                        TransportMode.AIRPLANE -> Icons.Rounded.FlightTakeoff
                        TransportMode.TRAIN -> Icons.Rounded.Train
                        TransportMode.OTHER -> Icons.Rounded.DirectionsCar
                        TransportMode.SPOT -> Icons.Rounded.Place
                    }
                }
            )

            if (modeHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    modeHistory.forEach { h ->
                        AssistChip(
                            onClick = {
                                draft = h.copy(
                                    year = draft.year,
                                    month = draft.month,
                                    day = draft.day,
                                    hour = draft.hour,
                                    minute = draft.minute,
                                    photoLat = draft.photoLat,
                                    photoLon = draft.photoLon,
                                    photoLocationName = draft.photoLocationName,
                                    photoCountryCode = draft.photoCountryCode
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(h.summaryLabel(), maxLines = 1) }
                        )
                    }
                }
            }

            when (draft.mode) {
                TransportMode.AIRPLANE -> {
                    ExpressiveSectionCard(
                        title = "フライト情報",
                        trailingHeader = {
                            IconButton(
                                onClick = {
                                    draft = draft.copy(
                                        departureAirportCode = draft.arrivalAirportCode,
                                        departureAirportName = draft.arrivalAirportName,
                                        departureAirportCity = draft.arrivalAirportCity,
                                        departureAirportCountry = draft.arrivalAirportCountry,
                                        departureLat = draft.arrivalLat,
                                        departureLon = draft.arrivalLon,
                                        arrivalAirportCode = draft.departureAirportCode,
                                        arrivalAirportName = draft.departureAirportName,
                                        arrivalAirportCity = draft.departureAirportCity,
                                        arrivalAirportCountry = draft.departureAirportCountry,
                                        arrivalLat = draft.departureLat,
                                        arrivalLon = draft.departureLon
                                    )
                                },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(Icons.Rounded.SwapVert, contentDescription = "出発・到着を入れ替え")
                            }
                        }
                    ) {
                        PickerFieldCard(
                            label = "出発空港",
                            value = if (draft.departureAirportCode.isNotBlank()) {
                                "${draft.departureAirportCode} · ${draft.departureAirportName}"
                            } else "",
                            placeholder = "空港を検索 (例: HND, KIX)",
                            icon = Icons.Rounded.FlightTakeoff,
                            onClick = { activeSearchKind = SearchDialogKind.DEP_AIRPORT }
                        )

                        PickerFieldCard(
                            label = "到着空港",
                            value = if (draft.arrivalAirportCode.isNotBlank()) {
                                "${draft.arrivalAirportCode} · ${draft.arrivalAirportName}"
                            } else "",
                            placeholder = "空港を検索 (例: OKA,CTS)",
                            icon = Icons.Rounded.FlightLand,
                            onClick = { activeSearchKind = SearchDialogKind.ARR_AIRPORT }
                        )

                        PickerFieldCard(
                            label = "航空会社",
                            value = if (draft.airlineCode.isNotBlank()) {
                                "${draft.airlineCode} · ${draft.airlineDisplayName}".trimEnd(' ', '·')
                            } else "",
                            placeholder = "航空会社を選択 (任意)",
                            icon = Icons.Rounded.Search,
                            onClick = { activeSearchKind = SearchDialogKind.AIRLINE },
                            onClear = if (draft.airlineCode.isNotBlank()) {
                                { draft = draft.copy(airlineCode = "", airlineDisplayName = "") }
                            } else null
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = draft.flightNumber,
                                onValueChange = { draft = draft.copy(flightNumber = it) },
                                label = { Text("便名") },
                                placeholder = { Text("1731") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = draft.registrationNumber,
                                onValueChange = { draft = draft.copy(registrationNumber = it.uppercase()) },
                                label = { Text("機体記号 (任意)") },
                                placeholder = { Text("JA812A") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                TransportMode.TRAIN -> {
                    ExpressiveSectionCard(
                        title = "列車・路線情報",
                        trailingHeader = {
                            IconButton(
                                onClick = {
                                    draft = draft.copy(
                                        departureStationName = draft.arrivalStationName,
                                        departureStationGroupCode = draft.arrivalStationGroupCode,
                                        departureStationLat = draft.arrivalStationLat,
                                        departureStationLon = draft.arrivalStationLon,
                                        departureStationCountry = draft.arrivalStationCountry,
                                        arrivalStationName = draft.departureStationName,
                                        arrivalStationGroupCode = draft.departureStationGroupCode,
                                        arrivalStationLat = draft.departureStationLat,
                                        arrivalStationLon = draft.departureStationLon,
                                        arrivalStationCountry = draft.departureStationCountry
                                    )
                                },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(Icons.Rounded.SwapVert, contentDescription = "発着駅を入れ替え")
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("日本の鉄道路線・駅DBを使用", style = MaterialTheme.typography.bodyMediumEmphasized)
                            Switch(
                                checked = draft.isDomesticJapanRail,
                                onCheckedChange = { draft = draft.copy(isDomesticJapanRail = it) }
                            )
                        }

                        if (draft.isDomesticJapanRail) {
                            PickerFieldCard(
                                label = "路線名",
                                value = draft.railwayLineName,
                                placeholder = "路線を検索（駅選択で自動判定も可）",
                                icon = Icons.Rounded.Train,
                                onClick = { activeSearchKind = SearchDialogKind.RAILWAY_LINE },
                                onClear = if (draft.railwayLineName.isNotBlank()) {
                                    { draft = draft.copy(railwayLineName = "", railwayCompany = "") }
                                } else null
                            )
                            PickerFieldCard(
                                label = "出発駅",
                                value = draft.departureStationName,
                                placeholder = "駅を検索",
                                icon = Icons.Rounded.Place,
                                onClick = { activeSearchKind = SearchDialogKind.DEP_STATION }
                            )
                            PickerFieldCard(
                                label = "到着駅",
                                value = draft.arrivalStationName,
                                placeholder = "駅を検索",
                                icon = Icons.Rounded.Place,
                                onClick = { activeSearchKind = SearchDialogKind.ARR_STATION }
                            )
                        } else {
                            OutlinedTextField(
                                value = draft.railwayLineName,
                                onValueChange = { draft = draft.copy(railwayLineName = it) },
                                label = { Text("路線名") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            PickerFieldCard(
                                label = "出発駅",
                                value = draft.departureStationName,
                                placeholder = "駅・都市名を検索",
                                icon = Icons.Rounded.Place,
                                onClick = { activeSearchKind = SearchDialogKind.DEP_PLACE }
                            )
                            PickerFieldCard(
                                label = "到着駅",
                                value = draft.arrivalStationName,
                                placeholder = "駅・都市名を検索",
                                icon = Icons.Rounded.Place,
                                onClick = { activeSearchKind = SearchDialogKind.DEST_PLACE }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = draft.trainName,
                                onValueChange = { draft = draft.copy(trainName = it) },
                                label = { Text("列車名 (任意)") },
                                placeholder = { Text("はくたか 570") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = draft.vehicleModel,
                                onValueChange = { draft = draft.copy(vehicleModel = it) },
                                label = { Text("車両型式 (任意)") },
                                placeholder = { Text("E7系") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (draft.isDomesticJapanRail && draft.candidateViaLines.isNotEmpty()) {
                            Text("経由路線", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = draft.isAutoShortestVia,
                                    onClick = { draft = draft.copy(isAutoShortestVia = true, selectedViaLines = emptyList()) },
                                    label = { Text("自動（最短）") }
                                )
                                draft.candidateViaLines.forEach { key ->
                                    val selected = !draft.isAutoShortestVia && key in draft.selectedViaLines
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            val cur = draft.selectedViaLines.toMutableSet()
                                            if (selected) cur.remove(key) else cur.add(key)
                                            draft = draft.copy(
                                                isAutoShortestVia = cur.isEmpty(),
                                                selectedViaLines = cur.toList()
                                            )
                                        },
                                        label = { Text(RailwayRepository.formatViaChipLabel(key)) }
                                    )
                                }
                            }
                        }
                    }
                }

                TransportMode.OTHER -> {
                    ExpressiveSectionCard(
                        title = "移動ルート情報",
                        trailingHeader = {
                            IconButton(
                                onClick = {
                                    draft = draft.copy(
                                        departurePlaceName = draft.destinationPlaceName,
                                        departurePlaceLat = draft.destinationPlaceLat,
                                        departurePlaceLon = draft.destinationPlaceLon,
                                        departurePlaceCountry = draft.destinationPlaceCountry,
                                        destinationPlaceName = draft.departurePlaceName,
                                        destinationPlaceLat = draft.departurePlaceLat,
                                        destinationPlaceLon = draft.departurePlaceLon,
                                        destinationPlaceCountry = draft.departurePlaceCountry
                                    )
                                },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(Icons.Rounded.SwapVert, contentDescription = "出発・目的地を入れ替え")
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = draft.transportName,
                            onValueChange = { draft = draft.copy(transportName = it) },
                            label = { Text("移動手段・タイトル (任意)") },
                            placeholder = { Text("Road Trip / Ferry") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PickerFieldCard(
                            label = "出発地",
                            value = draft.departurePlaceName,
                            placeholder = "場所を検索",
                            icon = Icons.Rounded.Place,
                            onClick = { activeSearchKind = SearchDialogKind.DEP_PLACE }
                        )
                        PickerFieldCard(
                            label = "目的地",
                            value = draft.destinationPlaceName,
                            placeholder = "場所を検索",
                            icon = Icons.Rounded.Place,
                            onClick = { activeSearchKind = SearchDialogKind.DEST_PLACE }
                        )
                    }
                }

                TransportMode.SPOT -> {
                    ExpressiveSectionCard(title = "スポットテキスト") {
                        OutlinedTextField(
                            value = draft.spotLine1,
                            onValueChange = { draft = draft.copy(spotLine1 = it) },
                            label = { Text("1行目（メイン見出し）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft.spotLine2,
                            onValueChange = { draft = draft.copy(spotLine2 = it) },
                            label = { Text("2行目（サブ見出し・任意）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft.spotLine3,
                            onValueChange = { draft = draft.copy(spotLine3 = it) },
                            label = { Text("3行目（補足・任意）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            ExpressiveSectionCard(title = "日時・撮影地") {
                ExpressiveConnectedSegmentedRow(
                    options = DateTimeDisplayMode.entries,
                    selected = draft.dateTimeDisplayMode,
                    onSelect = { draft = draft.copy(dateTimeDisplayMode = it) },
                    labelProvider = {
                        when (it) {
                            DateTimeDisplayMode.DATE_ONLY -> "日付のみ"
                            DateTimeDisplayMode.DATE_AND_TIME -> "日時"
                            DateTimeDisplayMode.NONE -> "非表示"
                        }
                    }
                )

                if (draft.dateTimeDisplayMode != DateTimeDisplayMode.NONE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { showDatePicker = true },
                            leadingIcon = {
                                Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(String.format(Locale.US, "%04d/%02d/%02d", draft.year, draft.month, draft.day))
                            }
                        )
                        if (draft.dateTimeDisplayMode == DateTimeDisplayMode.DATE_AND_TIME) {
                            AssistChip(
                                onClick = { showTimePicker = true },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = {
                                    Text(String.format(Locale.US, "%02d:%02d", draft.hour, draft.minute))
                                }
                            )
                        }
                    }
                }

                Text("撮影地ラベル", style = MaterialTheme.typography.bodyMediumEmphasized)
                ExpressiveConnectedSegmentedRow(
                    options = LocationDisplayMode.entries,
                    selected = draft.locationDisplayMode,
                    onSelect = { draft = draft.copy(locationDisplayMode = it) },
                    labelProvider = {
                        when (it) {
                            LocationDisplayMode.NAME -> "地名"
                            LocationDisplayMode.COORDINATES -> "緯度・経度"
                            LocationDisplayMode.NONE -> "非表示"
                        }
                    }
                )
                if (draft.locationDisplayMode == LocationDisplayMode.NAME) {
                    OutlinedTextField(
                        value = draft.photoLocationName,
                        onValueChange = { draft = draft.copy(photoLocationName = it) },
                        label = { Text("表示する地名") },
                        placeholder = { Text("Osaka, Japan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            ExpressiveSectionCard(title = "配置と地図") {
                Text("テキストの基準位置", style = MaterialTheme.typography.bodyMediumEmphasized)
                ExpressiveConnectedSegmentedRow(
                    options = OverlayCorner.entries,
                    selected = styleDraft.textCorner,
                    onSelect = { corner ->
                        val opposite = when (corner) {
                            OverlayCorner.BOTTOM_RIGHT -> OverlayCorner.BOTTOM_LEFT
                            OverlayCorner.BOTTOM_LEFT -> OverlayCorner.BOTTOM_RIGHT
                            OverlayCorner.TOP_RIGHT -> OverlayCorner.TOP_LEFT
                            OverlayCorner.TOP_LEFT -> OverlayCorner.TOP_RIGHT
                        }
                        styleDraft = styleDraft.copy(
                            textCorner = corner,
                            mapCorner = opposite,
                            textFreePos = null,
                            mapFreePos = null
                        )
                    },
                    labelProvider = {
                        when (it) {
                            OverlayCorner.TOP_LEFT -> "左上"
                            OverlayCorner.TOP_RIGHT -> "右上"
                            OverlayCorner.BOTTOM_LEFT -> "左下"
                            OverlayCorner.BOTTOM_RIGHT -> "右下"
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("地図透かしを表示", style = MaterialTheme.typography.bodyMediumEmphasized)
                    Switch(
                        checked = styleDraft.showMap,
                        onCheckedChange = { styleDraft = styleDraft.copy(showMap = it) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(draft.year, draft.month, draft.day) {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(draft.year, (draft.month - 1).coerceIn(0, 11), draft.day, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { ms ->
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                            draft = draft.copy(
                                year = cal.get(Calendar.YEAR),
                                month = cal.get(Calendar.MONTH) + 1,
                                day = cal.get(Calendar.DAY_OF_MONTH)
                            )
                        }
                        showDatePicker = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text("決定") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("キャンセル") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = draft.hour.coerceIn(0, 23),
            initialMinute = draft.minute.coerceIn(0, 59),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("時刻を選択") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        draft = draft.copy(hour = timePickerState.hour, minute = timePickerState.minute)
                        showTimePicker = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text("決定") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("キャンセル") }
            }
        )
    }

    activeSearchKind?.let { kind ->
        SearchEntitySheet(
            viewModel = viewModel,
            kind = kind,
            currentLineFilter = draft.railwayLineName,
            onDismiss = { activeSearchKind = null },
            onSelectAirline = { al ->
                draft = draft.copy(airlineCode = al.code, airlineDisplayName = al.fullName)
                activeSearchKind = null
            },
            onSelectAirport = { ap, isDep ->
                draft = if (isDep) {
                    draft.copy(
                        departureAirportCode = ap.primaryCode,
                        departureAirportName = ap.name,
                        departureAirportCity = ap.city,
                        departureAirportCountry = ap.country,
                        departureLat = ap.latitude,
                        departureLon = ap.longitude
                    )
                } else {
                    draft.copy(
                        arrivalAirportCode = ap.primaryCode,
                        arrivalAirportName = ap.name,
                        arrivalAirportCity = ap.city,
                        arrivalAirportCountry = ap.country,
                        arrivalLat = ap.latitude,
                        arrivalLon = ap.longitude
                    )
                }
                activeSearchKind = null
            },
            onSelectLine = { ln ->
                draft = draft.copy(railwayLineName = ln.lineName, railwayCompany = ln.companyName)
                activeSearchKind = null
            },
            onSelectStation = { st, isDep ->
                draft = if (isDep) {
                    draft.copy(
                        departureStationName = st.stationName,
                        departureStationGroupCode = st.groupCode,
                        departureStationLat = st.latitude,
                        departureStationLon = st.longitude,
                        departureStationCountry = "JP",
                        railwayLineName = draft.railwayLineName.ifBlank { st.lineName },
                        railwayCompany = draft.railwayCompany.ifBlank { st.companyName }
                    )
                } else {
                    draft.copy(
                        arrivalStationName = st.stationName,
                        arrivalStationGroupCode = st.groupCode,
                        arrivalStationLat = st.latitude,
                        arrivalStationLon = st.longitude,
                        arrivalStationCountry = "JP",
                        railwayLineName = draft.railwayLineName.ifBlank { st.lineName },
                        railwayCompany = draft.railwayCompany.ifBlank { st.companyName }
                    )
                }
                activeSearchKind = null
            },
            onSelectPlace = { pl, isDep ->
                draft = when {
                    draft.mode == TransportMode.TRAIN && isDep -> draft.copy(
                        departureStationName = pl.name,
                        departureStationLat = pl.latitude,
                        departureStationLon = pl.longitude,
                        departureStationCountry = pl.countryCode
                    )
                    draft.mode == TransportMode.TRAIN && !isDep -> draft.copy(
                        arrivalStationName = pl.name,
                        arrivalStationLat = pl.latitude,
                        arrivalStationLon = pl.longitude,
                        arrivalStationCountry = pl.countryCode
                    )
                    isDep -> draft.copy(
                        departurePlaceName = pl.name,
                        departurePlaceLat = pl.latitude,
                        departurePlaceLon = pl.longitude,
                        departurePlaceCountry = pl.countryCode
                    )
                    else -> draft.copy(
                        destinationPlaceName = pl.name,
                        destinationPlaceLat = pl.latitude,
                        destinationPlaceLon = pl.longitude,
                        destinationPlaceCountry = pl.countryCode
                    )
                }
                activeSearchKind = null
            },
            onFreeTextConfirm = { freeText ->
                coroutineScope.launch {
                    draft = when (kind) {
                        SearchDialogKind.DEP_AIRPORT -> {
                            val matched = viewModel.airportRepository.findByCode(freeText)
                            if (matched != null) {
                                draft.copy(
                                    departureAirportCode = matched.primaryCode,
                                    departureAirportName = matched.name,
                                    departureAirportCity = matched.city,
                                    departureAirportCountry = matched.country,
                                    departureLat = matched.latitude,
                                    departureLon = matched.longitude
                                )
                            } else {
                                draft.copy(
                                    departureAirportCode = freeText.uppercase(),
                                    departureAirportName = "",
                                    departureAirportCity = "",
                                    departureAirportCountry = "",
                                    departureLat = null,
                                    departureLon = null
                                )
                            }
                        }
                        SearchDialogKind.ARR_AIRPORT -> {
                            val matched = viewModel.airportRepository.findByCode(freeText)
                            if (matched != null) {
                                draft.copy(
                                    arrivalAirportCode = matched.primaryCode,
                                    arrivalAirportName = matched.name,
                                    arrivalAirportCity = matched.city,
                                    arrivalAirportCountry = matched.country,
                                    arrivalLat = matched.latitude,
                                    arrivalLon = matched.longitude
                                )
                            } else {
                                draft.copy(
                                    arrivalAirportCode = freeText.uppercase(),
                                    arrivalAirportName = "",
                                    arrivalAirportCity = "",
                                    arrivalAirportCountry = "",
                                    arrivalLat = null,
                                    arrivalLon = null
                                )
                            }
                        }
                        SearchDialogKind.DEP_STATION -> draft.copy(
                            departureStationName = freeText,
                            departureStationGroupCode = "",
                            departureStationLat = null,
                            departureStationLon = null
                        )
                        SearchDialogKind.ARR_STATION -> draft.copy(
                            arrivalStationName = freeText,
                            arrivalStationGroupCode = "",
                            arrivalStationLat = null,
                            arrivalStationLon = null
                        )
                        SearchDialogKind.DEP_PLACE -> {
                            if (draft.mode == TransportMode.TRAIN) {
                                draft.copy(
                                    departureStationName = freeText,
                                    departureStationGroupCode = "",
                                    departureStationLat = null,
                                    departureStationLon = null
                                )
                            } else {
                                draft.copy(
                                    departurePlaceName = freeText,
                                    departurePlaceLat = null,
                                    departurePlaceLon = null
                                )
                            }
                        }
                        SearchDialogKind.DEST_PLACE -> {
                            if (draft.mode == TransportMode.TRAIN) {
                                draft.copy(
                                    arrivalStationName = freeText,
                                    arrivalStationGroupCode = "",
                                    arrivalStationLat = null,
                                    arrivalStationLon = null
                                )
                            } else {
                                draft.copy(
                                    destinationPlaceName = freeText,
                                    destinationPlaceLat = null,
                                    destinationPlaceLon = null
                                )
                            }
                        }
                        SearchDialogKind.RAILWAY_LINE -> draft.copy(railwayLineName = freeText)
                        SearchDialogKind.AIRLINE -> draft.copy(airlineCode = freeText.uppercase())
                    }
                    activeSearchKind = null
                }
            }
        )
    }
}

@Composable
private fun PickerFieldCard(
    label: String,
    value: String,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    OutlinedCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value.ifBlank { placeholder },
                    style = if (value.isNotBlank()) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyMedium,
                    color = if (value.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onClear != null) {
                IconButton(
                    onClick = onClear,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Clear, contentDescription = "クリア", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchEntitySheet(
    viewModel: SukashiViewModel,
    kind: SearchDialogKind,
    currentLineFilter: String,
    onDismiss: () -> Unit,
    onSelectAirline: (AirlineEntry) -> Unit,
    onSelectAirport: (AirportRecord, Boolean) -> Unit,
    onSelectLine: (RailwayLineEntry) -> Unit,
    onSelectStation: (StationRecord, Boolean) -> Unit,
    onSelectPlace: (PlaceCandidate, Boolean) -> Unit,
    onFreeTextConfirm: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var airlines by remember { mutableStateOf<List<AirlineEntry>>(emptyList()) }
    var airports by remember { mutableStateOf<List<AirportRecord>>(emptyList()) }
    var lines by remember { mutableStateOf<List<RailwayLineEntry>>(emptyList()) }
    var stations by remember { mutableStateOf<List<StationRecord>>(emptyList()) }
    var places by remember { mutableStateOf<List<PlaceCandidate>>(emptyList()) }

    LaunchedEffect(kind, query) {
        if (query.isNotEmpty()) delay(100)
        when (kind) {
            SearchDialogKind.AIRLINE -> airlines = viewModel.searchAirlines(query)
            SearchDialogKind.DEP_AIRPORT, SearchDialogKind.ARR_AIRPORT -> airports = viewModel.searchAirports(query)
            SearchDialogKind.RAILWAY_LINE -> lines = viewModel.searchLines(query)
            SearchDialogKind.DEP_STATION, SearchDialogKind.ARR_STATION -> {
                stations = viewModel.searchStations(query, currentLineFilter)
            }
            SearchDialogKind.DEP_PLACE, SearchDialogKind.DEST_PLACE -> places = viewModel.searchPlaces(query)
        }
    }

    val title = when (kind) {
        SearchDialogKind.AIRLINE -> "航空会社を検索"
        SearchDialogKind.DEP_AIRPORT -> "出発空港を検索"
        SearchDialogKind.ARR_AIRPORT -> "到着空港を検索"
        SearchDialogKind.RAILWAY_LINE -> "路線を検索"
        SearchDialogKind.DEP_STATION -> "出発駅を検索"
        SearchDialogKind.ARR_STATION -> "到着駅を検索"
        SearchDialogKind.DEP_PLACE -> "出発地を検索"
        SearchDialogKind.DEST_PLACE -> "目的地を検索"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 560.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized)

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Clear, contentDescription = "クリア")
                        }
                    }
                } else null,
                placeholder = { Text("名前・コードで絞り込み") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (query.isNotBlank()) {
                TextButton(
                    onClick = { onFreeTextConfirm(query.trim()) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("「${query.trim()}」を直接入力")
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                when (kind) {
                    SearchDialogKind.AIRLINE -> items(airlines) { item ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onSelectAirline(item) }
                        ) {
                            Text(item.dropdownLabel, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    SearchDialogKind.DEP_AIRPORT, SearchDialogKind.ARR_AIRPORT -> items(airports) { rec ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            supportingContent = { Text("${rec.city}, ${rec.country}") },
                            modifier = Modifier.clickable {
                                onSelectAirport(rec, kind == SearchDialogKind.DEP_AIRPORT)
                            }
                        ) {
                            Text("${rec.primaryCode} — ${rec.name}", fontWeight = FontWeight.Bold)
                        }
                    }
                    SearchDialogKind.RAILWAY_LINE -> items(lines) { ln ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            supportingContent = { Text(ln.companyName) },
                            modifier = Modifier.clickable { onSelectLine(ln) }
                        ) {
                            Text(ln.lineName, fontWeight = FontWeight.Bold)
                        }
                    }
                    SearchDialogKind.DEP_STATION, SearchDialogKind.ARR_STATION -> items(stations) { st ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            supportingContent = { Text("${st.companyName} · ${st.lineName}") },
                            modifier = Modifier.clickable {
                                onSelectStation(st, kind == SearchDialogKind.DEP_STATION)
                            }
                        ) {
                            Text(st.stationName, fontWeight = FontWeight.Bold)
                        }
                    }
                    SearchDialogKind.DEP_PLACE, SearchDialogKind.DEST_PLACE -> items(places) { pl ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            supportingContent = if (pl.subtitle.isNotBlank()) ({ Text(pl.subtitle) }) else null,
                            modifier = Modifier.clickable {
                                onSelectPlace(pl, kind == SearchDialogKind.DEP_PLACE)
                            }
                        ) {
                            Text(pl.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
