package jp.nico2525.sukashimap

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import jp.nico2525.sukashimap.databinding.ActivityMapClusterGridBinding
import jp.nico2525.sukashimap.gallery.GalleryAdapter
import jp.nico2525.sukashimap.gallery.GalleryItem
import jp.nico2525.sukashimap.gallery.GallerySort
import jp.nico2525.sukashimap.gallery.MediaStoreLoader
import jp.nico2525.sukashimap.prefs.SettingsStore
import kotlinx.coroutines.launch

/**
 * Grid of the photos inside a tapped map cluster.
 * Extra: extra_item_keys (String[] of GalleryItem keys).
 * Tap resolves the position within the full gallery ordering and opens detail.
 */
class MapClusterGridActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapClusterGridBinding
    private lateinit var loader: MediaStoreLoader
    private var observer: ContentObserver? = null
    private var ordered: List<GalleryItem> = emptyList()
    private var subset: List<GalleryItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapClusterGridBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loader = MediaStoreLoader(this)

        setSupportActionBar(binding.clusterToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.clusterToolbar.setNavigationOnClickListener { finish() }

        val store = SettingsStore(this)
        val adapter = GalleryAdapter(lifecycleScope) { pos -> openDetail(pos) }
        binding.clusterGrid.layoutManager =
            GridLayoutManager(this, store.galleryColumns.coerceIn(1, 5))
        binding.clusterGrid.adapter = adapter

        val wanted = intent.getStringArrayExtra(GalleryActivity.EXTRA_ITEM_KEYS)?.toSet()
            ?: emptySet()
        observer = loader.observe { reload(adapter, wanted) }
        reload(adapter, wanted)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        observer?.let { loader.unobserve(it) }
        super.onDestroy()
    }

    private fun reload(adapter: GalleryAdapter, wanted: Set<String>) {
        lifecycleScope.launch {
            try {
                ordered = loader.load(ownOnly = false, sort = GallerySort.BY_ADDED)
                subset = ordered.filter { wanted.contains(it.key) }
                if (subset.isEmpty()) {
                    binding.clusterGrid.visibility = View.GONE
                } else {
                    binding.clusterGrid.visibility = View.VISIBLE
                    adapter.submitList(subset)
                }
            } catch (_: Exception) {
                binding.clusterGrid.visibility = View.GONE
            }
        }
    }

    private fun openDetail(clusterPos: Int) {
        val item = subset.getOrNull(clusterPos) ?: return
        val globalPos = ordered.indexOfFirst { it.key == item.key }.coerceAtLeast(0)
        startActivity(
            Intent(this, GalleryDetailActivity::class.java)
                .putExtra(GalleryActivity.EXTRA_INITIAL_POS, globalPos)
                .putExtra(GalleryActivity.EXTRA_ORDER_KEYS, ordered.map { it.key }.toTypedArray())
                .putExtra(GalleryActivity.EXTRA_OWN_ONLY, false),
        )
    }
}
