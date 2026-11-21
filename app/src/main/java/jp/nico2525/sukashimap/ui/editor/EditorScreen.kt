package jp.nico2525.sukashimap.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.photopicker.activity.PickVisualMedia
import androidx.photopicker.activity.PickVisualMediaRequest
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import jp.nico2525.sukashimap.model.ExportFormat
import jp.nico2525.sukashimap.model.NormOffset
import jp.nico2525.sukashimap.model.TransportMode
import jp.nico2525.sukashimap.render.OverlayCanvasRenderer
import jp.nico2525.sukashimap.render.OverlayMapCache
import jp.nico2525.sukashimap.render.OverlayLayoutMetrics
import jp.nico2525.sukashimap.ui.SukashiViewModel
import jp.nico2525.sukashimap.ui.settings.SettingsSheet
import kotlin.math.hypot
import kotlin.math.min

private enum class DragTarget {
    NONE,
    TEXT_MOVE,
    TEXT_RESIZE,
    MAP_MOVE,
    MAP_RESIZE,
    MAP_INTERNAL_ZOOM
}

private class LayoutMetricsHolder(var value: OverlayLayoutMetrics? = null)

@Composable
fun EditorScreen(
    viewModel: SukashiViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenCamera: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val state by viewModel.editorState.collectAsState()
    val prefs by viewModel.prefsState.collectAsState()

    var showInfoSheet by remember { mutableStateOf(false) }
    var showStyleSheet by remember { mutableStateOf(false) }
    var showCropSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    var activeDrag by remember { mutableStateOf(DragTarget.NONE) }
    val metricsHolder = remember { LayoutMetricsHolder() }
    val mapCache = remember { OverlayMapCache() }
    var mapSettleToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.overlayStyle, state.transportInfo, state.mapSlice) {
        delay(180)
        mapSettleToken++
    }

    val latestState by rememberUpdatedState(state)

    val mediaPicker = rememberLauncherForActivityResult(
        contract = PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.openMediaUri(uri)
        }
    }

    // The Android photo picker redacts EXIF GPS by default. The SDK-ext 23 photo picker can
    // ask the user to include location info; when supported, the returned URI keeps GPS.
    val launchMediaPicker = {
        mediaPicker.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(PickVisualMedia.ImageAndVideo)
                .setLocationMetadataAccessRequested(true)
                .build()
        )
    }

    // API 29-32 has no location-metadata picker flag. Ask for ACCESS_MEDIA_LOCATION first
    // so MediaStore/fallback pickers can expose unredacted EXIF.
    var mediaLocationPermissionAsked by rememberSaveable { mutableStateOf(false) }
    val mediaLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.emitMessage("写真の位置情報が許可されていないため、撮影地は手動で設定できます")
        }
        launchMediaPicker()
    }

    val launchPicker = {
        val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission && !mediaLocationPermissionAsked) {
            mediaLocationPermissionAsked = true
            mediaLocationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            launchMediaPicker()
        }
    }

    val hasCustomLayout = state.overlayStyle.textFreePos != null ||
        state.overlayStyle.mapFreePos != null ||
        state.overlayStyle.textScale != 1f ||
        state.overlayStyle.titleScale != 1f ||
        state.overlayStyle.subtitleScale != 1f ||
        state.overlayStyle.locationScale != 1f ||
        state.overlayStyle.dateScale != 1f ||
        state.overlayStyle.mapBoxScale != 1f ||
        state.overlayStyle.mapInternalZoom != 1f ||
        state.overlayStyle.mapInternalPanX != 0f ||
        state.overlayStyle.mapInternalPanY != 0f

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    if (state.mediaUri != null) {
                        IconButton(
                            onClick = launchPicker,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = "別の写真・動画を選択")
                        }
                    }
                },
                title = {
                    Text(
                        text = "Sukashi Map",
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
                actions = {
                    if (state.mediaUri != null) {
                        IconButton(
                            onClick = onOpenCamera,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = "カメラ")
                        }
                        if (hasCustomLayout) {
                            IconButton(
                                onClick = { viewModel.snapLayoutReset() },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = "位置・サイズをリセット")
                            }
                        }
                    }
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "設定")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.mediaUri == null) {
                EmptyWorkspaceState(
                    onPickMedia = launchPicker,
                    onOpenCamera = onOpenCamera,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val stageBottomInset by animateDpAsState(
                    targetValue = if (showStyleSheet) 400.dp else 96.dp,
                    label = "stageBottomInset"
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = stageBottomInset),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Compact route chip right above the canvas
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modeIcon = when (state.transportInfo.mode) {
                            TransportMode.AIRPLANE -> Icons.Rounded.FlightTakeoff
                            TransportMode.TRAIN -> Icons.Rounded.Train
                            TransportMode.OTHER -> Icons.Rounded.DirectionsCar
                            TransportMode.SPOT -> Icons.Rounded.Place
                        }
                        AssistChip(
                            onClick = { showInfoSheet = true },
                            leadingIcon = {
                                Icon(modeIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            label = {
                                Text(
                                    text = state.transportInfo.summaryLabel(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                        if (state.isComputingRoute) {
                            Spacer(Modifier.width(8.dp))
                            LoadingIndicator(modifier = Modifier.size(22.dp))
                        }
                    }

                    // Harmonious M3 Surface Container framing the interactive preview canvas
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val aspect = (state.mediaWidth.toFloat() / state.mediaHeight.coerceAtLeast(1).toFloat())
                            .coerceIn(0.3f, 3.2f)
                        val availW = maxWidth
                        val availH = maxHeight
                        val (stageW, stageH) = if (availW / availH > aspect) {
                            (availH * aspect) to availH
                        } else {
                            availW to (availW / aspect)
                        }
                        val stageWidthPx = with(density) { stageW.toPx() }
                        val stageHeightPx = with(density) { stageH.toPx() }

                        val latestW by rememberUpdatedState(stageWidthPx)
                        val latestH by rememberUpdatedState(stageHeightPx)

                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .size(stageW, stageH)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    shape = MaterialTheme.shapes.large
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.large)
                                    .pointerInput(Unit) {
                                        val handleHitPx = 32.dp.toPx()
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val metrics = metricsHolder.value
                                            val downPt = down.position

                                            var gestureMode = DragTarget.NONE
                                            var initNormX = 0f
                                            var initNormY = 0f
                                            var initCenterX = 0f
                                            var initCenterY = 0f
                                            var initScale = 1f
                                            var initCenterDist = 100f

                                            if (metrics != null && latestW > 1f && latestH > 1f) {
                                                val tr = metrics.textBounds
                                                val mr = metrics.mapBounds
                                                val textHandle = Offset(tr.right, tr.top)
                                                val mapHandle = mr?.let { Offset(it.right, it.top) }
                                                val curStyle = latestState.overlayStyle

                                                when {
                                                    hypot(downPt.x - textHandle.x, downPt.y - textHandle.y) <= handleHitPx -> {
                                                        gestureMode = DragTarget.TEXT_RESIZE
                                                        initScale = curStyle.textScale
                                                        initCenterX = tr.centerX()
                                                        initCenterY = tr.centerY()
                                                        initCenterDist = hypot(downPt.x - initCenterX, downPt.y - initCenterY).coerceAtLeast(24f)
                                                    }
                                                    mapHandle != null && hypot(downPt.x - mapHandle.x, downPt.y - mapHandle.y) <= handleHitPx -> {
                                                        gestureMode = DragTarget.MAP_RESIZE
                                                        initScale = curStyle.mapBoxScale
                                                        initCenterX = mr.centerX()
                                                        initCenterY = mr.centerY()
                                                        initCenterDist = hypot(downPt.x - initCenterX, downPt.y - initCenterY).coerceAtLeast(24f)
                                                    }
                                                    tr.contains(downPt.x, downPt.y) -> {
                                                        gestureMode = DragTarget.TEXT_MOVE
                                                        initNormX = tr.centerX() / latestW
                                                        initNormY = tr.centerY() / latestH
                                                    }
                                                    mr != null && mr.contains(downPt.x, downPt.y) -> {
                                                        gestureMode = DragTarget.MAP_MOVE
                                                        initNormX = mr.centerX() / latestW
                                                        initNormY = mr.centerY() / latestH
                                                    }
                                                }
                                            }
                                            activeDrag = gestureMode

                                            var accDx = 0f
                                            var accDy = 0f

                                            try {
                                                do {
                                                    val event = awaitPointerEvent()
                                                    val pressedCount = event.changes.count { it.pressed }
                                                    if (pressedCount >= 2) {
                                                        val zoom = event.calculateZoom()
                                                        val pan = event.calculatePan()
                                                        val centroid = event.calculateCentroid()
                                                        val mr = metricsHolder.value?.mapBounds
                                                        val curStyle = latestState.overlayStyle
                                                        if (mr != null && (
                                                                mr.contains(centroid.x, centroid.y) ||
                                                                    gestureMode == DragTarget.MAP_MOVE ||
                                                                    gestureMode == DragTarget.MAP_INTERNAL_ZOOM
                                                                )
                                                        ) {
                                                            gestureMode = DragTarget.MAP_INTERNAL_ZOOM
                                                            activeDrag = DragTarget.MAP_INTERNAL_ZOOM
                                                            val newZoom = (curStyle.mapInternalZoom * zoom).coerceIn(0.5f, 5.0f)
                                                            val newPanX = curStyle.mapInternalPanX + pan.x / mr.width().coerceAtLeast(1f)
                                                            val newPanY = curStyle.mapInternalPanY + pan.y / mr.height().coerceAtLeast(1f)
                                                            viewModel.updateMapInternalViewport(newZoom, newPanX, newPanY)
                                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                                        }
                                                    } else if (pressedCount == 1 &&
                                                        gestureMode != DragTarget.NONE &&
                                                        gestureMode != DragTarget.MAP_INTERNAL_ZOOM &&
                                                        latestW > 1f && latestH > 1f
                                                    ) {
                                                        val change = event.changes.first()
                                                        val pan = event.calculatePan()
                                                        accDx += pan.x
                                                        accDy += pan.y
                                                        val curStyle = latestState.overlayStyle
                                                        when (gestureMode) {
                                                            DragTarget.TEXT_MOVE -> {
                                                                val nx = (initNormX + accDx / latestW).coerceIn(0.06f, 0.94f)
                                                                val ny = (initNormY + accDy / latestH).coerceIn(0.06f, 0.94f)
                                                                viewModel.updateTextPositionAndScale(NormOffset(nx, ny), curStyle.textScale)
                                                            }
                                                            DragTarget.MAP_MOVE -> {
                                                                val nx = (initNormX + accDx / latestW).coerceIn(0.06f, 0.94f)
                                                                val ny = (initNormY + accDy / latestH).coerceIn(0.06f, 0.94f)
                                                                viewModel.updateMapPositionAndScale(NormOffset(nx, ny), curStyle.mapBoxScale)
                                                            }
                                                            DragTarget.TEXT_RESIZE -> {
                                                                val curDist = hypot(change.position.x - initCenterX, change.position.y - initCenterY)
                                                                val ratio = (curDist / initCenterDist).coerceIn(0.4f, 2.8f)
                                                                viewModel.updateTextPositionAndScale(curStyle.textFreePos, initScale * ratio)
                                                            }
                                                            DragTarget.MAP_RESIZE -> {
                                                                val curDist = hypot(change.position.x - initCenterX, change.position.y - initCenterY)
                                                                val ratio = (curDist / initCenterDist).coerceIn(0.4f, 2.8f)
                                                                viewModel.updateMapPositionAndScale(curStyle.mapFreePos, initScale * ratio)
                                                            }
                                                            DragTarget.NONE, DragTarget.MAP_INTERNAL_ZOOM -> Unit
                                                        }
                                                        if (change.positionChanged()) change.consume()
                                                    }
                                                } while (event.changes.any { it.pressed })
                                            } finally {
                                                if (activeDrag != DragTarget.NONE) {
                                                    viewModel.commitInteractiveStyle()
                                                }
                                                activeDrag = DragTarget.NONE
                                            }
                                        }
                                    }
                            ) {
                                if (state.isVideo && state.mediaUri != null) {
                                    VideoPreviewLayer(
                                        videoUri = state.mediaUri!!,
                                        pausePlayback = state.isExporting
                                    )
                                } else {
                                    state.stagedBitmap?.let { bmp ->
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "プレビュー画像",
                                            contentScale = ContentScale.FillBounds,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                val primaryAccent = MaterialTheme.colorScheme.primary
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    // Live preview of matte border on video (matching MediaExporter.exportVideoOriginalQuality)
                                    val videoMargin = state.cropFrame.frameMarginRatio.coerceIn(0f, 0.49f)
                                    if (state.isVideo && videoMargin > 0.001f) {
                                        val frameColor = Color(state.cropFrame.frameColorArgb)
                                        val padX = size.width * videoMargin * 0.5f
                                        val padY = size.height * videoMargin * 0.5f
                                        drawRect(frameColor, topLeft = Offset.Zero, size = Size(size.width, padY))
                                        drawRect(frameColor, topLeft = Offset(0f, size.height - padY), size = Size(size.width, padY))
                                        drawRect(frameColor, topLeft = Offset(0f, padY), size = Size(padX, size.height - padY * 2f))
                                        drawRect(frameColor, topLeft = Offset(size.width - padX, padY), size = Size(padX, size.height - padY * 2f))
                                    }

                                    var drawnMetrics: OverlayLayoutMetrics? = null
                                    drawIntoCanvas { composeCanvas ->
                                        val m = OverlayCanvasRenderer.render(
                                            context = context,
                                            canvas = composeCanvas.nativeCanvas,
                                            canvasWidth = size.width,
                                            canvasHeight = size.height,
                                            info = state.transportInfo,
                                            style = state.overlayStyle,
                                            mapSlice = state.mapSlice,
                                            mapCache = mapCache,
                                            mapSettleToken = mapSettleToken
                                        )
                                        metricsHolder.value = m
                                        drawnMetrics = m
                                    }

                                    drawnMetrics?.let { m ->
                                        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                                        if (activeDrag == DragTarget.TEXT_MOVE || activeDrag == DragTarget.TEXT_RESIZE) {
                                            val r = m.textBounds
                                            drawRoundRect(
                                                color = Color.White.copy(alpha = 0.85f),
                                                topLeft = Offset(r.left - 6f, r.top - 6f),
                                                size = Size(r.width() + 12f, r.height() + 12f),
                                                cornerRadius = CornerRadius(12f, 12f),
                                                style = Stroke(width = 2f, pathEffect = dash)
                                            )
                                            drawCircle(primaryAccent, radius = 9.dp.toPx(), center = Offset(r.right, r.top))
                                            drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(r.right, r.top))
                                        }
                                        m.mapBounds?.let { r ->
                                            if (activeDrag == DragTarget.MAP_MOVE ||
                                                activeDrag == DragTarget.MAP_RESIZE ||
                                                activeDrag == DragTarget.MAP_INTERNAL_ZOOM
                                            ) {
                                                drawRoundRect(
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    topLeft = Offset(r.left - 6f, r.top - 6f),
                                                    size = Size(r.width() + 12f, r.height() + 12f),
                                                    cornerRadius = CornerRadius(12f, 12f),
                                                    style = Stroke(width = 2f, pathEffect = dash)
                                                )
                                                drawCircle(primaryAccent, radius = 9.dp.toPx(), center = Offset(r.right, r.top))
                                                drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(r.right, r.top))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Canonical Material 3 Expressive HorizontalFloatingToolbar + VibrantFloatingActionButton
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                ) {
                    HorizontalFloatingToolbar(
                        expanded = true,
                        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                        floatingActionButton = {
                            FloatingToolbarDefaults.VibrantFloatingActionButton(
                                onClick = { viewModel.exportCurrentMedia() }
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = "保存")
                            }
                        }
                    ) {
                        IconButton(
                            onClick = { showInfoSheet = true },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Route, contentDescription = "ルート・透かし情報")
                        }
                        IconButton(
                            onClick = { showStyleSheet = true },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Palette, contentDescription = "スタイル")
                        }
                        IconButton(
                            onClick = { showCropSheet = true },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Crop, contentDescription = "切り抜き・余白枠")
                        }
                        IconButton(
                            onClick = { viewModel.exportCurrentMedia(shareAfterSave = true) },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "保存して共有")
                        }
                        Box {
                            IconButton(
                                onClick = { showExportMenu = true },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "その他の保存メニュー")
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                if (!state.isVideo) {
                                    DropdownMenuItem(
                                        text = { Text("JPEGで保存") },
                                        onClick = {
                                            showExportMenu = false
                                            viewModel.exportCurrentMedia(formatOverride = ExportFormat.JPEG_95)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("PNG（無劣化）で保存") },
                                        onClick = {
                                            showExportMenu = false
                                            viewModel.exportCurrentMedia(formatOverride = ExportFormat.PNG_LOSSLESS)
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("MP4（元解像度）で保存") },
                                        onClick = {
                                            showExportMenu = false
                                            viewModel.exportCurrentMedia()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("透かしの位置・拡大率をリセット") },
                                    onClick = {
                                        showExportMenu = false
                                        viewModel.snapLayoutReset()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ContainedLoadingIndicator()
                            Text(
                                text = state.busyMessage.ifBlank { "処理中…" },
                                style = MaterialTheme.typography.bodyMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInfoSheet) {
        TransportInfoSheet(
            viewModel = viewModel,
            initialInfo = state.transportInfo,
            currentStyle = state.overlayStyle,
            recentHistory = prefs.recentHistory,
            mediaKey = state.mediaUri,
            onDismiss = { showInfoSheet = false },
            onApply = { newInfo, newStyle ->
                viewModel.updateOverlayStyle({ newStyle })
                viewModel.applyTransportInfo(newInfo, saveToHistory = true)
                showInfoSheet = false
            }
        )
    }

    if (showStyleSheet) {
        StyleSettingsSheet(
            style = state.overlayStyle,
            mode = state.transportInfo.mode,
            presets = prefs.stylePresets,
            eyepickerBitmap = state.stagedBitmap,
            onStyleChange = { viewModel.updateOverlayStyle({ _ -> it }) },
            onResetLayout = { viewModel.snapLayoutReset() },
            onSavePreset = { name -> viewModel.saveStylePreset(name) },
            onDeletePreset = { id -> viewModel.deleteStylePreset(id) },
            onDismiss = { showStyleSheet = false }
        )
    }

    if (showCropSheet) {
        CropFrameSheet(
            rawBitmap = state.rawBitmap,
            isVideo = state.isVideo,
            initialSettings = state.cropFrame,
            onDismiss = { showCropSheet = false },
            onApply = { cfg ->
                viewModel.applyCropFrame(cfg)
                showCropSheet = false
            }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            viewModel = viewModel,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun EmptyWorkspaceState(
    onPickMedia: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 380.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialShapes.SoftBurst.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = "写真や動画を選択",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "フライト・鉄道・移動ルートや撮影地の地図透かしを合成します",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onPickMedia,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.MediumContentPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("写真・動画を選ぶ", style = MaterialTheme.typography.labelLargeEmphasized)
            }

            FilledTonalButton(
                onClick = onOpenCamera,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.MediumContentPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("カメラで撮影する", style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

@Composable
fun VideoPreviewLayer(
    videoUri: Uri,
    pausePlayback: Boolean = false
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(true) }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(pausePlayback) {
        if (pausePlayback) {
            exoPlayer.pause()
            isPlaying = false
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)) {
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause(); isPlaying = false
                        } else {
                            exoPlayer.play(); isPlaying = true
                        }
                    },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "一時停止" else "再生"
                    )
                }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)) {
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                    },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = if (isMuted) "ミュート解除" else "ミュート"
                    )
                }
            }
        }
    }
}
