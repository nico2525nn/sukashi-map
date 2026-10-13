package jp.nico2525.sukashimap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.nico2525.sukashimap.camera.LocationHelper
import jp.nico2525.sukashimap.camera.MediaSaver
import jp.nico2525.sukashimap.data.OverlayPosition
import jp.nico2525.sukashimap.data.TransportInfo
import jp.nico2525.sukashimap.databinding.ActivityMainBinding
import jp.nico2525.sukashimap.overlay.ExifOverlay
import jp.nico2525.sukashimap.overlay.OverlayItem
import kotlin.math.roundToInt
import jp.nico2525.sukashimap.overlay.OverlayRenderer
import jp.nico2525.sukashimap.overlay.OverlayStyle
import jp.nico2525.sukashimap.prefs.SettingsStore
import jp.nico2525.sukashimap.ui.SaveSuccessSheet
import jp.nico2525.sukashimap.ui.TransportBottomSheet
import jp.nico2525.sukashimap.ui.TransportResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Camera capture screen: CameraX preview (3:4) + transport overlay, photo/video
 * capture, overlay preview with drag/scale, save and share.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CAPTURED_URI = "extra_captured_uri"
        const val EXTRA_CAPTURED_IS_VIDEO = "extra_captured_is_video"
    }

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var locationHelper: LocationHelper
    private lateinit var mediaSaver: MediaSaver
    private lateinit var renderer: OverlayRenderer
    private lateinit var settings: SettingsStore

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var videoMode = false
    private var isRecording = false
    private var audioGrantedAtStart = false
    private var recSeconds = 0
    private var recJob: Job? = null
    private var player: ExoPlayer? = null
    private var videoFile: File? = null
    private var albumVideoUri: Uri? = null
    private var cropAspect: Float? = null
    private var seeking = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val seekTicker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && !seeking && p.duration > 0) {
                binding.seekBar.progress = ((p.currentPosition * 1000 / p.duration).toInt())
            }
            uiHandler.postDelayed(this, 250)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.permission_camera_required, Toast.LENGTH_LONG).show()
        }
        audioGrantedAtStart = grants[Manifest.permission.RECORD_AUDIO] == true
        if (grants[Manifest.permission.RECORD_AUDIO] == false) {
            Toast.makeText(this, R.string.permission_audio_denied_hint, Toast.LENGTH_LONG).show()
        }
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            refreshLocation()
        }
        maybeShowMediaRationale(grants)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        locationHelper = LocationHelper(this)
        mediaSaver = MediaSaver(this)
        renderer = OverlayRenderer(this)
        settings = SettingsStore(this)

        supportFragmentManager.setFragmentResultListener(TransportResult.REQUEST_KEY, this) { _, bundle ->
            val info = TransportResult.fromBundle(bundle).withLocation(vm)
            vm.setTransport(info)
            vm.setOverlayPosition(TransportResult.overlayPositionOf(bundle))
            vm.setMapPosition(runCatching {
                OverlayPosition.valueOf(bundle.getString(TransportResult.KEY_MAP_POSITION, OverlayPosition.TOP_RIGHT.name)!!)
            }.getOrDefault(OverlayPosition.TOP_RIGHT))
            vm.setFont(fontFor(bundle.getInt("font_index", 0)))
            vm.textColor = bundle.getInt("text_color", vm.textColor)
            vm.mapLineColor = bundle.getInt("map_line_color", vm.mapLineColor)
            vm.mapBorderColor = bundle.getInt("map_border_color", vm.mapBorderColor)
            refreshOverlayText()
            redrawOverlayItems()
        }

        binding.btnTransportInfo.setOnClickListener { openTransportSheet() }
        binding.btnCapture.setOnClickListener { onCapture() }
        binding.btnCaptureMode.setOnClickListener { toggleMode() }
        binding.btnFlash.setOnClickListener { toggleFlash() }
        binding.btnFlipCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera()
        }
        binding.btnGallery.setOnClickListener {
            runCatching { startActivity(Intent(this, GalleryActivity::class.java)) }
        }
        binding.btnSettings.setOnClickListener {
            runCatching { startActivity(Intent(this, SettingsActivity::class.java)) }
        }
        binding.btnLocation.setOnClickListener { refreshLocation(manual = true) }
        binding.btnToggleLocationMode.setOnClickListener {
            vm.toggleLocationMode()
            settings.locationDisplayMode = vm.locationMode.value
            refreshOverlayText()
            redrawOverlayItems()
        }
        binding.btnCropFrame.setOnClickListener { showCropDialog() }
        binding.btnDiscard.setOnClickListener { confirmDiscard() }
        binding.btnSave.setOnClickListener { saveCurrent() }
        binding.btnShare.setOnClickListener { shareCurrent(null) }
        binding.btnShareX.setOnClickListener { shareCurrent("com.twitter.android") }
        binding.btnShareIG.setOnClickListener { shareCurrent("com.instagram.android") }
        binding.btnPlayPause.setOnClickListener { togglePlay() }
        binding.btnMute.setOnClickListener { toggleMute() }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val p = player ?: return
                if (fromUser && p.duration > 0) p.seekTo(p.duration * progress / 1000)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { seeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) { seeking = false }
        })


        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.transportInfo.collect { refreshOverlayText() } }
                launch { vm.photoBitmap.collect { showPhotoPreview(it) } }
                launch { vm.videoUri.collect { u -> if (u != null) showVideoPreview(u) } }
            }
        }

        handleIncomingIntent(intent)
        requestPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    // ---- intents ----
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (Intent.ACTION_SEND == intent.action) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val type = intent.type.orEmpty()
            if (uri != null) openSharedUri(uri, type.startsWith("video"))
            return
        }
        if (intent.hasExtra(EXTRA_CAPTURED_URI)) {
            val raw = intent.getStringExtra(EXTRA_CAPTURED_URI) ?: return
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return
            val isVideo = intent.getBooleanExtra(EXTRA_CAPTURED_IS_VIDEO, false)
            openSharedUri(uri, isVideo)
        }
    }

    private fun openSharedUri(uri: Uri, isVideo: Boolean) {
        lifecycleScope.launch {
            if (isVideo) {
                albumVideoUri = uri
                videoFile = null
                vm.setPhoto(null)
                withContext(Dispatchers.IO) {
                    runCatching {
                        contentResolver.openInputStream(uri)?.use { ins ->
                            ExifInterface(ins).latLong
                        }
                    }
                }
                vm.setVideo(uri)
                Toast.makeText(this@MainActivity, R.string.main_album_video_ready, Toast.LENGTH_SHORT).show()
            } else {
                val bmp = withContext(Dispatchers.IO) {
                    runCatching {
                        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                }
                if (bmp == null) {
                    Toast.makeText(this@MainActivity, R.string.import_photo_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                vm.setVideo(null)
                vm.setPhoto(cropped(bmp))
                importPhotoExif(uri)
                Toast.makeText(this@MainActivity, R.string.main_album_photo_ready, Toast.LENGTH_SHORT).show()
            }
            redrawOverlayItems()
        }
    }

    private suspend fun importPhotoExif(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { ins ->
                val exif = ExifInterface(ins)
                vm.exifOverlay = readExifOverlay(exif)
                val dt = exif.dateTime ?: exif.dateTimeOriginal
                if (dt != null && dt > 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, R.string.exif_datetime_loaded, Toast.LENGTH_SHORT).show()
                    }
                }
                exif.latLong?.let { ll ->
                    vm.currentLat = ll[0]
                    vm.currentLon = ll[1]
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, R.string.exif_location_loaded, Toast.LENGTH_SHORT).show()
                    }
                } ?: withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.exif_location_missing, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun readExifOverlay(exif: ExifInterface): ExifOverlay {
        val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty().trim()
        val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty().trim()
        // "OLYMPUS E-M5MarkII": keep make uppercase, drop duplicated make prefix in model.
        val makeModel = when {
            model.isBlank() -> make.uppercase()
            make.isBlank() -> model
            model.uppercase().startsWith(make.uppercase()) -> "${make.uppercase()} ${model.substring(make.length).trimStart()}"
            else -> "${make.uppercase()} $model"
        }.trim()
        val dtMs = exif.dateTime ?: exif.dateTimeOriginal ?: exif.dateTimeDigitized
        val dateTime = if (dtMs != null && dtMs > 0) {
            java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(dtMs), java.time.ZoneId.systemDefault(),
            ).format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"))
        } else ""
        val f = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, Double.NaN)
            .takeUnless { it.isNaN() }?.let { "f/$it" }.orEmpty()
        val exp = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, Double.NaN)
            .takeUnless { it.isNaN() }?.let {
                if (it > 0 && it < 1) "1/${(1 / it).roundToInt()}s" else "${it}s"
            }.orEmpty()
        val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
            .takeIf { it > 0 }?.let { "ISO $it" }.orEmpty()
        val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, Double.NaN)
            .takeUnless { it.isNaN() }?.let { "${it.roundToInt()}mm" }.orEmpty()
        return ExifOverlay(makeModel, dateTime, f, exp, iso, focal)
    }

    private fun currentExif(): ExifOverlay = vm.exifOverlay

    // ---- permissions ----
    private fun requestPermissions() {
        val needed = mutableListOf(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        audioGrantedAtStart = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        permissionLauncher.launch(needed.toTypedArray())
    }

    private fun maybeShowMediaRationale(grants: Map<String, Boolean>) {
        val denied = grants.entries.any { (k, v) -> !v && (k == Manifest.permission.READ_MEDIA_IMAGES || k == Manifest.permission.READ_MEDIA_VIDEO) }
        if (!denied) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rationale_title)
            .setMessage(R.string.rationale_message)
            .setPositiveButton(R.string.rationale_confirm, null)
            .show()
    }

    // ---- camera ----
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(binding.previewView.display?.rotation ?: Surface.ROTATION_0)
                    .setFlashMode(flashMode)
                    .build()
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                provider.unbindAll()
                if (videoMode) {
                    provider.bindToLifecycle(this, selector, preview, videoCapture)
                } else {
                    provider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture)
                }
            } catch (_: Exception) {
                Toast.makeText(this, R.string.camera_error, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleMode() {
        if (isRecording) return
        videoMode = !videoMode
        binding.btnCaptureMode.setText(if (videoMode) R.string.mode_switch_to_photo else R.string.mode_switch_to_video)
        binding.btnCapture.setText(if (videoMode) R.string.main_start_recording else R.string.capture)
        startCamera()
    }

    private fun toggleFlash() {
        flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        imageCapture?.flashMode = flashMode
        binding.btnFlash.setImageResource(if (flashMode == ImageCapture.FLASH_MODE_ON) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
    }

    private fun onCapture() {
        if (vm.photoBitmap.value != null || vm.videoUri.value != null) return
        if (videoMode) {
            if (isRecording) stopRecording() else startRecording()
        } else {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val outFile = File(cacheDir, "cap_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
        capture.takePicture(
            opts, cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val bmp = BitmapFactory.decodeFile(outFile.absolutePath)
                    runOnUiThread {
                        if (bmp == null) {
                            Toast.makeText(this@MainActivity, R.string.capture_failed, Toast.LENGTH_SHORT).show()
                        } else {
                            vm.setPhoto(cropped(bmp))
                            redrawOverlayItems()
                        }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, R.string.capture_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    private fun startRecording() {
        val vc = videoCapture ?: return
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val opts = MediaStoreOutputOptions.Builder(contentResolver, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(
                android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                },
            ).build()
        recording = vc.output.prepareRecording(this, opts)
            .let { if (audioGrantedAtStart) it.withAudioEnabled() else it }.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is androidx.camera.video.VideoRecordEvent.Finalize) {
                    onRecordFinalized(event)
                }
            }
        isRecording = true
        recSeconds = 0
        binding.tvRecTimer.visibility = View.VISIBLE
        binding.btnCapture.setText(R.string.main_stop_recording)
        recJob = lifecycleScope.launch {
            while (isRecording) {
                binding.tvRecTimer.text = getString(R.string.recording_time_format, recSeconds / 60, recSeconds % 60)
                delay(1000)
                recSeconds++
            }
        }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun onRecordFinalized(event: androidx.camera.video.VideoRecordEvent.Finalize) {
        isRecording = false
        recJob?.cancel()
        binding.tvRecTimer.visibility = View.GONE
        binding.btnCapture.setText(R.string.main_start_recording)
        if (event.hasError()) {
            Toast.makeText(this, R.string.video_recording_failed, Toast.LENGTH_SHORT).show()
            return
        }
        vm.setVideo(event.outputResults.outputUri)
        redrawOverlayItems()
    }

    // ---- overlay text + items ----
    private fun currentInfo(): TransportInfo? = vm.transportInfo.value

    private fun refreshOverlayText() {
        // The rendered overlay bitmap already contains the text; the live
        // TextView stays hidden whenever transport info is set.
        binding.flightInfoOverlay.visibility = View.GONE
    }

    private fun overlayStyle(): OverlayStyle = OverlayStyle(
        font = vm.chosenFont.value,
        textColor = vm.textColor,
        mapLineColor = vm.mapLineColor,
        mapBorderColor = vm.mapBorderColor,
        position = vm.overlayPosition.value,
        locationMode = vm.locationMode.value,
    )

    private fun redrawOverlayItems() {
        val info = currentInfo() ?: run {
            binding.overlayDragView.setOverlayItems(emptyList())
            return
        }
        val photo = vm.photoBitmap.value
        val vw = binding.overlayDragView.photoWidth.toInt()
        val vh = binding.overlayDragView.photoHeight.toInt()
        val w = photo?.width ?: vw.takeIf { it > 0 } ?: 1080
        val h = photo?.height ?: vh.takeIf { it > 0 } ?: 1440
        binding.overlayDragView.photoWidth = w.toFloat()
        binding.overlayDragView.photoHeight = h.toFloat()
        lifecycleScope.launch {
            val exif = withContext(Dispatchers.IO) { currentExif() }
            val bits = withContext(Dispatchers.Default) { renderer.render(info, w, h, overlayStyle(), exif) }
            val items = mutableListOf<OverlayItem>()
            bits.text?.let { items += OverlayItem("text", it, w * 0.05f, anchorY(it, w, h), 1f) }
            bits.map?.let {
                // Map bitmap is already full-photo-size with the frame baked in.
                items += OverlayItem("map", it, 0f, 0f, 1f, scalable = false)
            }
            bits.exif?.let { items += OverlayItem("exif", it, w * 0.05f, h - it.height - w * 0.05f, 1f) }
            bits.watermark?.let { items += OverlayItem("watermark", it, w - it.width - w * 0.05f, w * 0.05f, 1f, scalable = false) }
            binding.overlayDragView.setOverlayItems(items)
        }
    }

    private fun anchorY(text: Bitmap, w: Int, h: Int): Float = when (vm.overlayPosition.value) {
        OverlayPosition.TOP_LEFT, OverlayPosition.TOP_RIGHT -> h * 0.03f
        else -> h - text.height - w * 0.03f
    }

    private fun mapAnchor(map: Bitmap, w: Int, h: Int, pos: OverlayPosition): Pair<Float, Float> {
        val m = w * 0.03f
        return when (pos) {
            OverlayPosition.TOP_LEFT -> m to m
            OverlayPosition.TOP_RIGHT -> (w - map.width - m) to m
            OverlayPosition.BOTTOM_LEFT -> m to (h - map.height - m)
            OverlayPosition.BOTTOM_RIGHT -> (w - map.width - m) to (h - map.height - m)
        }
    }

    private fun fontFor(index: Int): Typeface? = when (index) {
        1 -> Typeface.SERIF
        2 -> Typeface.SANS_SERIF
        3 -> Typeface.MONOSPACE
        else -> null
    }

    // ---- previews ----
    private fun showPhotoPreview(bmp: Bitmap?) {
        if (bmp == null) {
            binding.photoPreview.visibility = View.GONE
            if (vm.videoUri.value == null) showCaptureControls()
            return
        }
        binding.previewView.visibility = View.INVISIBLE
        binding.videoPreview.visibility = View.GONE
        binding.videoControls.visibility = View.GONE
        binding.photoPreview.visibility = View.VISIBLE
        binding.photoPreview.setImageBitmap(bmp)
        showPostCapture()
    }

    private fun showVideoPreview(uri: Uri) {
        binding.previewView.visibility = View.INVISIBLE
        binding.photoPreview.visibility = View.GONE
        binding.videoPreview.visibility = View.VISIBLE
        binding.videoControls.visibility = View.VISIBLE
        // Give the overlay layer real video-sized coordinates, then re-render.
        lifecycleScope.launch(Dispatchers.IO) {
            val dims = videoDimensions(uri)
            withContext(Dispatchers.Main) {
                if (dims != null) {
                    binding.overlayDragView.photoWidth = dims.first.toFloat()
                    binding.overlayDragView.photoHeight = dims.second.toFloat()
                    redrawOverlayItems()
                }
            }
        }
        releasePlayer()
        player = ExoPlayer.Builder(this).build().also { p ->
            binding.videoPreview.player = p
            p.setMediaItem(MediaItem.fromUri(uri))
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.prepare()
            p.play()
        }
        uiHandler.post(seekTicker)
        showPostCapture()
    }

    private fun videoDimensions(uri: Uri): Pair<Int, Int>? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rot = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            retriever.release()
            if (w == null || h == null) null
            else if (rot == 90 || rot == 270) Pair(h, w) else Pair(w, h)
        } catch (_: Exception) {
            null
        }
    }

    private fun showCaptureControls() {
        binding.previewView.visibility = View.VISIBLE
        binding.photoPreview.visibility = View.GONE
        binding.videoPreview.visibility = View.GONE
        binding.videoControls.visibility = View.GONE
        binding.postCaptureControls.visibility = View.GONE
        binding.previewControls.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.VISIBLE
    }

    private fun showPostCapture() {
        binding.previewControls.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        binding.postCaptureControls.visibility = View.VISIBLE
        binding.btnShare.setText(if (vm.photoBitmap.value != null) R.string.chooser_share_image else R.string.chooser_share_video)
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
        } else {
            p.play()
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
        }
    }

    private fun toggleMute() {
        val p = player ?: return
        val muted = p.volume > 0f
        p.volume = if (muted) 0f else 1f
        binding.btnMute.setImageResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_on)
    }

    private fun releasePlayer() {
        uiHandler.removeCallbacks(seekTicker)
        binding.videoPreview.player = null
        player?.release()
        player = null
    }

    // ---- location ----
    private fun refreshLocation(manual: Boolean = false) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            if (manual) Toast.makeText(this, R.string.permission_location_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (manual) Toast.makeText(this, R.string.location_updating, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val loc = withContext(Dispatchers.IO) {
                runCatching { locationHelper.currentLocation() }.getOrNull()
            } ?: run {
                if (manual) Toast.makeText(this@MainActivity, R.string.location_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            vm.currentLat = loc.latitude
            vm.currentLon = loc.longitude
            vm.currentPlace = loc.displayName
            vm.currentCountry = loc.countryCode
            val base = currentInfo()
            if (base != null) {
                val updated = base.withLocation(vm)
                vm.setTransport(updated)
                redrawOverlayItems()
            }
            Toast.makeText(
                this@MainActivity,
                if (loc.coordinatesOnly) R.string.location_updated_coordinates_only else R.string.location_updated,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // ---- crop (simple aspect switch) ----
    private fun cropped(src: Bitmap): Bitmap {
        val aspect = cropAspect ?: return src
        val sw = src.width
        val sh = src.height
        val target = aspect
        val cur = sw.toFloat() / sh
        if (kotlin.math.abs(cur - target) < 0.01f) return src
        val (nw, nh) = if (cur > target) {
            (sh * target).toInt() to sh
        } else {
            sw to (sw / target).toInt()
        }
        val x = (sw - nw) / 2
        val y = (sh - nh) / 2
        val out = Bitmap.createBitmap(src, x, y, nw, nh)
        if (out != src) src.recycle()
        return out
    }

    private fun showCropDialog() {
        val labels = listOf(
            getString(R.string.crop_aspect_original), getString(R.string.crop_aspect_free),
            getString(R.string.crop_aspect_4_5), getString(R.string.crop_aspect_2_3),
            getString(R.string.crop_aspect_9_16),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crop_editor_title)
            .setItems(labels.toTypedArray()) { _, which ->
                cropAspect = when (which) {
                    2 -> 4f / 5f
                    3 -> 2f / 3f
                    4 -> 9f / 16f
                    else -> null
                }
                vm.photoBitmap.value?.let { vm.setPhoto(cropped(it.copy(it.config ?: Bitmap.Config.ARGB_8888, true))) }
                redrawOverlayItems()
                Toast.makeText(this, R.string.main_crop_applied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.crop_cancel, null)
            .show()
    }

    // ---- transport sheet ----
    private fun openTransportSheet() {
        val seed = currentInfo()?.let {
            TransportResult.toBundle(it, vm.overlayPosition.value, 0, vm.textColor, vm.mapLineColor, vm.mapBorderColor)
        }
        TransportBottomSheet.newInstance(seed).show(supportFragmentManager, "transport")
    }

    // ---- discard / save / share ----
    private fun confirmDiscard() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.discard_dialog_title)
            .setMessage(R.string.discard_dialog_message)
            .setPositiveButton(R.string.discard) { _, _ ->
                vm.clearCapture()
                releasePlayer()
                videoFile?.delete()
                videoFile = null
                albumVideoUri = null
                showCaptureControls()
                redrawOverlayItems()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveCurrent() {
        val photo = vm.photoBitmap.value
        val videoUri = vm.videoUri.value
        lifecycleScope.launch {
            if (photo != null) {
                val items = binding.overlayDragView.getOverlayItems()
                val saved = withContext(Dispatchers.IO) {
                    mediaSaver.savePhoto(photo, items, vm.currentLat, vm.currentLon)
                }
                if (saved != null) {
                    settings.saveCount = settings.saveCount + 1
                    Toast.makeText(this@MainActivity, R.string.photo_saved, Toast.LENGTH_SHORT).show()
                    SaveSuccessSheet.newInstance(R.string.photo_saved, saved.uri)
                        .show(supportFragmentManager, "save_success")
                    jp.nico2525.sukashimap.util.ReviewHelper.recordSave(this@MainActivity)
                    jp.nico2525.sukashimap.util.ReviewHelper.maybePrompt(this@MainActivity)
                } else {
                    Toast.makeText(this@MainActivity, R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
                }
            } else if (videoUri != null) {
                Toast.makeText(this@MainActivity, R.string.video_export_progress_title, Toast.LENGTH_SHORT).show()
                val items = binding.overlayDragView.getOverlayItems()
                val w = binding.overlayDragView.photoWidth.toInt().coerceAtLeast(2)
                val h = binding.overlayDragView.photoHeight.toInt().coerceAtLeast(2)
                val out = mediaSaver.exportVideo(videoUri, items, w, h)
                if (out != null) {
                    Toast.makeText(this@MainActivity, R.string.video_saved, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, R.string.video_export_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareCurrent(targetPackage: String?) {
        lifecycleScope.launch {
            val photo = vm.photoBitmap.value
            val videoUri = vm.videoUri.value
            val shareUri: Uri? = withContext(Dispatchers.IO) {
                when {
                    photo != null -> {
                        val items = binding.overlayDragView.getOverlayItems()
                        mediaSaver.savePhoto(photo, items, vm.currentLat, vm.currentLon)?.uri
                            ?: cachePhotoUri(photo)
                    }
                    videoUri != null -> videoUri
                    else -> null
                }
            }
            if (shareUri == null) return@launch
            val isVideo = videoUri != null
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (isVideo) "video/mp4" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_TEXT, getString(R.string.share_hashtag))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (targetPackage != null) setPackage(targetPackage)
            }
            runCatching {
                startActivity(
                    if (targetPackage != null) intent
                    else Intent.createChooser(intent, getString(if (isVideo) R.string.chooser_share_video else R.string.chooser_share_image)),
                )
            }.onFailure {
                Toast.makeText(this@MainActivity, getString(R.string.app_not_installed, targetPackage ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cachePhotoUri(bmp: Bitmap): Uri? {
        return runCatching {
            val dir = File(cacheDir, "shared").apply { mkdirs() }
            val f = File(dir, "share_${System.currentTimeMillis()}.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        }.getOrNull()
    }

    override fun onResume() {
        super.onResume()
        if (vm.photoBitmap.value == null && vm.videoUri.value == null) startCameraIfReady()
        if (settings.autoGeotagging && vm.currentLat == null) refreshLocation()
    }

    private fun startCameraIfReady() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    override fun onPause() {
        releasePlayerKeepUri()
        super.onPause()
    }

    private fun releasePlayerKeepUri() {
        uiHandler.removeCallbacks(seekTicker)
        binding.videoPreview.player = null
        player?.pause()
        player?.release()
        player = null
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        releasePlayer()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        }
        super.onDestroy()
    }

    private fun TransportInfo.withLocation(viewModel: MainViewModel): TransportInfo {
        val lat = viewModel.currentLat
        val lon = viewModel.currentLon
        val place = viewModel.currentPlace
        val country = viewModel.currentCountry
        return when (this) {
            is TransportInfo.Airplane -> copy(currentLocation = place, currentLatitude = lat, currentLongitude = lon, currentCountryCode = country)
            is TransportInfo.Train -> copy(currentLocation = place, currentLatitude = lat, currentLongitude = lon, currentCountryCode = country)
            is TransportInfo.Other -> copy(currentLocation = place, currentLatitude = lat, currentLongitude = lon, currentCountryCode = country)
            is TransportInfo.Spot -> copy(currentLocation = place, currentLatitude = lat, currentLongitude = lon, currentCountryCode = country)
        }
    }

}
