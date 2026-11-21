package jp.nico2525.sukashimap.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import jp.nico2525.sukashimap.render.OverlayCanvasRenderer
import jp.nico2525.sukashimap.ui.SukashiViewModel
import jp.nico2525.sukashimap.ui.editor.StyleSettingsSheet
import jp.nico2525.sukashimap.ui.editor.TransportInfoSheet
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraOverlayScreen(
    viewModel: SukashiViewModel,
    onCapturedToEditor: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.editorState.collectAsState()
    val prefs by viewModel.prefsState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        hasCameraPermission = res[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashEnabled by remember { mutableStateOf(false) }
    var focusRingPoint by remember { mutableStateOf<Offset?>(null) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showStyleSheet by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var captureInFlight by remember { mutableStateOf(false) }
    val disposed = remember { AtomicBoolean(false) }
    val latestCamera by rememberUpdatedState(boundCamera)

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                runCatching { providerFuture.get().unbindAll() }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    LaunchedEffect(hasCameraPermission, lensFacing, flashEnabled) {
        if (!hasCameraPermission) return@LaunchedEffect
        imageCapture.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (disposed.get()) return@addListener
            runCatching {
                val cameraProvider = providerFuture.get()
                if (disposed.get()) {
                    cameraProvider.unbindAll()
                    return@runCatching
                }
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                cameraProvider.unbindAll()
                boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            }.onFailure { err ->
                viewModel.emitMessage("カメラ起動エラー: ${err.localizedMessage ?: ""}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(focusRingPoint) {
        if (focusRingPoint != null) {
            delay(900)
            focusRingPoint = null
        }
    }

    val capturePhoto = {
        if (hasCameraPermission && !captureInFlight && !disposed.get()) {
            captureInFlight = true
            val outDir = File(context.cacheDir, "captures").apply { mkdirs() }
            val photoFile = File(outDir, "camera_${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        captureInFlight = false
                        if (disposed.get()) {
                            photoFile.delete()
                            return
                        }
                        runCatching {
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                photoFile
                            )
                        }.onSuccess { uri ->
                            viewModel.openMediaUri(uri, fromCamera = true, onReady = onCapturedToEditor)
                        }.onFailure { err ->
                            viewModel.emitMessage("撮影保存エラー: ${err.localizedMessage ?: ""}")
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        captureInFlight = false
                        if (!disposed.get()) {
                            viewModel.emitMessage("撮影エラー: ${exception.localizedMessage}")
                        }
                    }
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            val cam = latestCamera ?: return@detectTransformGestures
                            val zoomState = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
                            val targetRatio = (zoomState.zoomRatio * zoomChange)
                                .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                            cam.cameraControl.setZoomRatio(targetRatio)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            focusRingPoint = offset
                            val cam = latestCamera ?: return@detectTapGestures
                            runCatching {
                                val factory = previewView.meteringPointFactory
                                val action = FocusMeteringAction.Builder(factory.createPoint(offset.x, offset.y)).build()
                                cam.cameraControl.startFocusAndMetering(action)
                            }
                        }
                    }
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { composeCanvas ->
                    OverlayCanvasRenderer.render(
                        context = context,
                        canvas = composeCanvas.nativeCanvas,
                        canvasWidth = size.width,
                        canvasHeight = size.height,
                        info = state.transportInfo,
                        style = state.overlayStyle,
                        mapSlice = state.mapSlice
                    )
                }
                focusRingPoint?.let { pt ->
                    drawCircle(
                        color = Color.White,
                        radius = 30.dp.toPx(),
                        center = pt,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                Text(
                    "カメラの権限が必要です",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = Color.White
                )
                Button(
                    onClick = { permLauncher.launch(arrayOf(Manifest.permission.CAMERA)) },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("権限を許可")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = { flashEnabled = !flashEnabled },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        if (flashEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                        contentDescription = "フラッシュ切替"
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.Cameraswitch, contentDescription = "カメラ切替")
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(
                        onClick = capturePhoto
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = "撮影")
                    }
                }
            ) {
                IconButton(
                    onClick = { showInfoSheet = true },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.Route, contentDescription = "ルート情報")
                }
                IconButton(
                    onClick = { showStyleSheet = true },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.Palette, contentDescription = "スタイル")
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
            eyepickerBitmap = null,
            onStyleChange = { viewModel.updateOverlayStyle({ _ -> it }) },
            onResetLayout = { viewModel.snapLayoutReset() },
            onSavePreset = { viewModel.saveStylePreset(it) },
            onDeletePreset = { viewModel.deleteStylePreset(it) },
            onDismiss = { showStyleSheet = false }
        )
    }
}
