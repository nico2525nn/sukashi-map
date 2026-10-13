package jp.nico2525.sukashimap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import jp.nico2525.sukashimap.BuildConfig
import jp.nico2525.sukashimap.databinding.ActivityGalleryBinding
import jp.nico2525.sukashimap.gallery.GalleryAdapter
import jp.nico2525.sukashimap.gallery.GalleryItem
import jp.nico2525.sukashimap.gallery.GallerySort
import jp.nico2525.sukashimap.gallery.MediaStoreLoader
import jp.nico2525.sukashimap.prefs.SettingsStore
import jp.nico2525.sukashimap.triprecap.TripRecapActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * MediaStore grid <-> Google Maps cluster view.
 *
 * Column count follows [SettingsStore.galleryColumns]; map needs a Maps API key
 * (empty key shows guidance instead of crashing).
 */
class GalleryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INITIAL_POS = "extra_initial_pos"
        const val EXTRA_ORDER_KEYS = "extra_order_keys"
        const val EXTRA_OWN_ONLY = "extra_own_only"
        const val EXTRA_SORT_TAKEN = "extra_sort_taken"
        const val EXTRA_ITEM_KEYS = "extra_item_keys"
        const val EXTRA_PICK_MODE = "extra_pick_mode"
    }

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var store: SettingsStore
    private lateinit var loader: MediaStoreLoader
    private lateinit var adapter: GalleryAdapter

    private var items: List<GalleryItem> = emptyList()
    private var ownOnly = true
    private var sortTaken = false
    private var mapMode = false
    private var satellite = false
    private var observer: ContentObserver? = null
    private var loadJob: Job? = null

    private var map: GoogleMap? = null
    private var clusters: ClusterManager<PhotoClusterItem>? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            reload()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = SettingsStore(this)
        loader = MediaStoreLoader(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ownOnly = intent.getBooleanExtra(EXTRA_OWN_ONLY, true)
        sortTaken = store.gallerySortTaken

        adapter = GalleryAdapter(lifecycleScope) { pos -> openDetail(pos) }
        binding.grid.layoutManager = GridLayoutManager(this, store.galleryColumns.coerceIn(1, 5))
        binding.grid.adapter = adapter

        binding.stateRetry.setOnClickListener { ensurePermissionThenLoad() }
        binding.stateAction.setOnClickListener { onStateAction() }

        observer = loader.observe { reload() }
        ensurePermissionThenLoad()
    }

    override fun onResume() {
        super.onResume()
        val cols = store.galleryColumns.coerceIn(1, 5)
        (binding.grid.layoutManager as? GridLayoutManager)?.spanCount = cols
        if (sortTaken != store.gallerySortTaken) {
            sortTaken = store.gallerySortTaken
            reload()
        }
    }

    override fun onDestroy() {
        loadJob?.cancel()
        observer?.let { loader.unobserve(it) }
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.gallery_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        if (onMenu(item)) return true
        return super.onOptionsItemSelected(item)
    }

    // ---------- menu ----------

    private fun onMenu(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_trip_recap -> {
                startActivity(Intent(this, TripRecapActivity::class.java))
                return true
            }
            R.id.action_sort_added -> {
                sortTaken = false
                store.gallerySortTaken = false
                reload()
                return true
            }
            R.id.action_sort_taken -> {
                sortTaken = true
                store.gallerySortTaken = true
                reload()
                return true
            }
            R.id.action_filter_own -> {
                ownOnly = !ownOnly
                reload()
                return true
            }
            R.id.action_toggle_map -> {
                mapMode = !mapMode
                applyMode()
                return true
            }
            R.id.action_toggle_map_type -> {
                satellite = !satellite
                applyMapType()
                return true
            }
        }
        return false
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        menu.findItem(R.id.action_filter_own)?.isChecked = ownOnly
        menu.findItem(R.id.action_sort_added)?.isChecked = !sortTaken
        menu.findItem(R.id.action_sort_taken)?.isChecked = sortTaken
        menu.findItem(R.id.action_toggle_map_type)?.isChecked = satellite
        menu.findItem(R.id.action_toggle_map)?.setTitle(
            if (mapMode) R.string.menu_toggle_list else R.string.menu_toggle_map,
        )
        return super.onPrepareOptionsMenu(menu)
    }

    // ---------- permissions / loading ----------

    private fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 34) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
        } else {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        }

    private fun hasMediaAccess(): Boolean {
        val image = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_MEDIA_IMAGES,
        ) == PERMISSION_GRANTED
        val partial = Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == PERMISSION_GRANTED
        return image || partial
    }

    private fun ensurePermissionThenLoad() {
        if (hasMediaAccess()) {
            reload()
        } else {
            showState(State.PERMISSION)
        }
    }

    private fun onStateAction() {
        val tag = binding.stateAction.tag as? String
        if (tag == "settings") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null)),
            )
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rationale_title)
                .setMessage(R.string.rationale_message)
                .setPositiveButton(R.string.rationale_confirm) { _, _ ->
                    permissionLauncher.launch(mediaPermissions())
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun reload() {
        loadJob?.cancel()
        if (!hasMediaAccess()) {
            showState(State.PERMISSION)
            return
        }
        showState(State.LOADING)
        loadJob = lifecycleScope.launch {
            try {
                val sort = if (sortTaken) GallerySort.BY_TAKEN else GallerySort.BY_ADDED
                items = loader.load(ownOnly, sort)
                if (items.isEmpty()) {
                    showState(State.EMPTY)
                } else {
                    showState(State.CONTENT)
                    adapter.submitList(items)
                }
                if (mapMode) refreshClusters()
            } catch (e: SecurityException) {
                showState(State.PERMISSION)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                showState(State.ERROR)
            }
        }
    }

    private enum class State { LOADING, CONTENT, EMPTY, PERMISSION, ERROR }

    private fun showState(state: State) {
        val partial = Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES,
            ) != PERMISSION_GRANTED
        when (state) {
            State.LOADING -> {
                binding.stateBox.visibility = View.VISIBLE
                binding.stateProgress.visibility = View.VISIBLE
                binding.stateText.setText(R.string.gallery_loading)
                binding.stateAction.visibility = View.GONE
                binding.stateRetry.visibility = View.GONE
                if (!mapMode) binding.grid.visibility = View.INVISIBLE
            }
            State.CONTENT -> {
                binding.stateBox.visibility = View.GONE
                binding.grid.visibility = if (mapMode) View.GONE else View.VISIBLE
            }
            State.EMPTY -> {
                binding.grid.visibility = View.INVISIBLE
                binding.stateBox.visibility = View.VISIBLE
                binding.stateProgress.visibility = View.GONE
                binding.stateText.setText(
                    if (partial || ownOnly) R.string.gallery_empty_partial else R.string.gallery_empty,
                )
                binding.stateAction.visibility = View.GONE
                binding.stateRetry.visibility = View.GONE
            }
            State.PERMISSION -> {
                binding.grid.visibility = View.INVISIBLE
                binding.stateBox.visibility = View.VISIBLE
                binding.stateProgress.visibility = View.GONE
                binding.stateText.setText(R.string.gallery_permission_required)
                binding.stateAction.visibility = View.VISIBLE
                binding.stateAction.setText(R.string.btn_open_settings)
                binding.stateAction.tag = "settings"
                binding.stateRetry.visibility = View.VISIBLE
            }
            State.ERROR -> {
                binding.grid.visibility = View.INVISIBLE
                binding.stateBox.visibility = View.VISIBLE
                binding.stateProgress.visibility = View.GONE
                binding.stateText.setText(R.string.gallery_load_error)
                binding.stateAction.visibility = View.GONE
                binding.stateRetry.visibility = View.VISIBLE
            }
        }
    }

    // ---------- map mode ----------

    private fun applyMode() {
        invalidateOptionsMenu()
        if (!mapMode) {
            binding.mapContainer.visibility = View.GONE
            binding.mapNotice.visibility = View.GONE
            binding.grid.visibility = if (items.isEmpty()) View.INVISIBLE else View.VISIBLE
            if (items.isEmpty()) showState(State.EMPTY) else showState(State.CONTENT)
            return
        }
        if (BuildConfig.MAPS_API_KEY.isEmpty()) {
            binding.grid.visibility = View.GONE
            binding.mapContainer.visibility = View.GONE
            binding.stateBox.visibility = View.VISIBLE
            binding.stateProgress.visibility = View.GONE
            binding.stateText.setText(R.string.gallery_map_key_missing)
            binding.stateAction.visibility = View.GONE
            binding.stateRetry.visibility = View.GONE
            return
        }
        binding.grid.visibility = View.GONE
        binding.stateBox.visibility = View.GONE
        binding.mapContainer.visibility = View.VISIBLE
        var frag = supportFragmentManager.findFragmentById(R.id.map_container)
            as? SupportMapFragment
        if (frag == null) {
            frag = SupportMapFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.map_container, frag)
                .commit()
        }
        frag.getMapAsync { g ->
            map = g
            applyMapType()
            setupClusters(g)
            refreshClusters()
        }
    }

    private fun applyMapType() {
        map?.mapType = if (satellite) GoogleMap.MAP_TYPE_SATELLITE else GoogleMap.MAP_TYPE_NORMAL
    }

    private fun setupClusters(g: GoogleMap) {
        val manager = ClusterManager<PhotoClusterItem>(this, g)
        clusters = manager
        g.setOnCameraIdleListener(manager)
        g.setOnMarkerClickListener(manager)
        manager.setOnClusterClickListener { cluster ->
            val keys = cluster.items.map { it.galleryKey }.toTypedArray()
            startActivity(
                Intent(this, MapClusterGridActivity::class.java)
                    .putExtra(EXTRA_ITEM_KEYS, keys),
            )
            true
        }
        manager.setOnClusterItemClickListener { item ->
            val pos = items.indexOfFirst { it.key == item.galleryKey }
            if (pos >= 0) openDetail(pos)
            true
        }
    }

    private fun refreshClusters() {
        val manager = clusters ?: return
        val g = map ?: return
        lifecycleScope.launch {
            manager.clearItems()
            val bounds = LatLngBounds.builder()
            var count = 0
            for (item in items) {
                val located = runCatching { loader.ensureLocation(item) }.getOrNull() ?: item
                val lat = located.latitude ?: continue
                val lon = located.longitude ?: continue
                // Store enriched coordinates for grid/detail reuse.
                manager.addItem(PhotoClusterItem(located.key, lat, lon))
                bounds.include(LatLng(lat, lon))
                count++
            }
            manager.cluster()
            if (count == 0) {
                binding.mapNotice.visibility = View.VISIBLE
                binding.mapNotice.setText(R.string.map_no_geotagged_photos)
            } else {
                binding.mapNotice.visibility = View.GONE
                g.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120))
            }
        }
    }

    private fun openDetail(pos: Int) {
        val item = items.getOrNull(pos) ?: return
        if (intent.getBooleanExtra(EXTRA_PICK_MODE, false)) {
            setResult(
                RESULT_OK,
                Intent().putExtra(MainActivity.EXTRA_CAPTURED_URI, item.uri.toString())
                    .putExtra(MainActivity.EXTRA_CAPTURED_IS_VIDEO, item.isVideo),
            )
            finish()
            return
        }
        startActivity(
            Intent(this, GalleryDetailActivity::class.java)
                .putExtra(EXTRA_INITIAL_POS, pos)
                .putExtra(EXTRA_ORDER_KEYS, items.map { it.key }.toTypedArray())
                .putExtra(EXTRA_OWN_ONLY, ownOnly)
                .putExtra(EXTRA_SORT_TAKEN, sortTaken),
        )
    }

    class PhotoClusterItem(
        val galleryKey: String,
        private val lat: Double,
        private val lon: Double,
    ) : ClusterItem {
        override fun getPosition() = LatLng(lat, lon)
        override fun getTitle(): String? = null
        override fun getSnippet(): String? = null
        override fun getZIndex(): Float? = null
    }
}
