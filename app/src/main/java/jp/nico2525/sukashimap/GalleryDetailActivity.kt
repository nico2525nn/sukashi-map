package jp.nico2525.sukashimap

import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.DateFormat
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.nico2525.sukashimap.databinding.ActivityGalleryDetailBinding
import jp.nico2525.sukashimap.databinding.BottomSheetImageInfoBinding
import jp.nico2525.sukashimap.gallery.GalleryItem
import jp.nico2525.sukashimap.gallery.GallerySort
import jp.nico2525.sukashimap.gallery.MediaStoreLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

/**
 * Full-screen pager over the same MediaStore ordering the grid uses.
 * Extras: extra_initial_pos, extra_order_keys, extra_own_only, extra_sort_taken.
 */
class GalleryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryDetailBinding
    private lateinit var loader: MediaStoreLoader
    private var items: List<GalleryItem> = emptyList()
    private var observer: ContentObserver? = null
    private var player: ExoPlayer? = null
    private var playerPos = -1

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val msg = if (pendingDelete?.isVideo == true) R.string.toast_video_deleted
                else R.string.toast_image_deleted
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
                pendingDelete = null
                reload(keepPos = true)
            } else {
                android.widget.Toast.makeText(
                    this, R.string.toast_deletion_cancelled, android.widget.Toast.LENGTH_SHORT,
                ).show()
                pendingDelete = null
            }
        }

    private var pendingDelete: GalleryItem? = null
    private var currentPos: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loader = MediaStoreLoader(this)

        setSupportActionBar(binding.detailToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.detailToolbar.setNavigationOnClickListener { finish() }

        currentPos = intent.getIntExtra(GalleryActivity.EXTRA_INITIAL_POS, 0).coerceAtLeast(0)

        binding.pager.adapter = DetailAdapter(
            onImageReady = {},
            loadThumb = { item, view -> showStill(item, view) },
            bindVideo = { item, view -> bindVideoAt(item, view) },
            releaseVideo = { view -> releaseVideoView(view) },
        )
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPos = position
                attachPlayer(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) attachPlayer(binding.pager.currentItem)
            }
        })

        binding.btnDetailInfo.setOnClickListener {
            items.getOrNull(currentPos)?.let { ImageInfoBottomSheet.newInstance(it).show(supportFragmentManager, "info") }
        }
        binding.btnDetailShare.setOnClickListener {
            items.getOrNull(currentPos)?.let { share(it) }
        }
        binding.btnDetailDelete.setOnClickListener {
            items.getOrNull(currentPos)?.let { confirmDelete(it) }
        }

        observer = loader.observe { reload(keepPos = true) }
        reload(keepPos = false)
    }

    override fun onPause() {
        player?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        observer?.let { loader.unobserve(it) }
        releasePlayer()
        super.onDestroy()
    }

    private fun reload(keepPos: Boolean) {
        lifecycleScope.launch {
            try {
                val ownOnly = intent.getBooleanExtra(GalleryActivity.EXTRA_OWN_ONLY, true)
                val taken = intent.getBooleanExtra(GalleryActivity.EXTRA_SORT_TAKEN, false)
                val sort = if (taken) GallerySort.BY_TAKEN else GallerySort.BY_ADDED
                var loaded = loader.load(ownOnly, sort)
                val order = intent.getStringArrayExtra(GalleryActivity.EXTRA_ORDER_KEYS)
                if (order != null && order.isNotEmpty()) {
                    val index = order.withIndex().associate { it.value to it.index }
                    val known = loaded.filter { index.containsKey(it.key) }
                        .sortedBy { index[it.key] }
                    val fresh = loaded.filter { !index.containsKey(it.key) }
                    loaded = known + fresh
                }
                items = loaded
                if (items.isEmpty()) {
                    finish()
                    return@launch
                }
                val pos = if (keepPos) currentPos.coerceIn(0, items.size - 1)
                else currentPos.coerceIn(0, items.size - 1)
                currentPos = pos
                (binding.pager.adapter as DetailAdapter).submit(items)
                binding.pager.setCurrentItem(pos, false)
                attachPlayer(pos)
                // Page views may not be bound yet; retry once laid out (video autoplay).
                binding.pager.post { attachPlayer(binding.pager.currentItem) }
            } catch (_: Exception) {
                finish()
            }
        }
    }

    private fun share(item: GalleryItem) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = if (item.isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(
            Intent.createChooser(
                share,
                getString(if (item.isVideo) R.string.chooser_share_video else R.string.chooser_share_image),
            ),
        )
    }

    private fun confirmDelete(item: GalleryItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.detail_delete_title)
            .setMessage(getString(R.string.detail_delete_message, item.displayName ?: ""))
            .setPositiveButton(R.string.detail_delete) { _, _ -> requestDelete(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestDelete(item: GalleryItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val req = MediaStore.createDeleteRequest(contentResolver, listOf(item.uri))
            pendingDelete = item
            runCatching {
                deleteLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(req.intentSender).build(),
                )
            }.onFailure {
                pendingDelete = null
                android.widget.Toast.makeText(
                    this, R.string.toast_no_delete_permission, android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            @Suppress("DEPRECATION")
            val rows = runCatching { contentResolver.delete(item.uri, null, null) }.getOrDefault(0)
            if (rows > 0) {
                android.widget.Toast.makeText(
                    this,
                    if (item.isVideo) R.string.toast_video_deleted else R.string.toast_image_deleted,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
                reload(keepPos = true)
            } else {
                android.widget.Toast.makeText(
                    this, R.string.toast_delete_failed, android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // ---------- playback ----------

    private fun attachPlayer(position: Int) {
        val item = items.getOrNull(position) ?: return
        releasePlayer()
        if (!item.isVideo) return
        val holder = findPagerChild(position) ?: run {
            playerPos = position
            return
        }
        val playerView = holder.findViewById<PlayerView>(R.id.detail_player)
            ?: return
        playerPos = position
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo
        exo.setMediaItem(MediaItem.fromUri(item.uri))
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun findPagerChild(position: Int): View? {
        val rv = binding.pager.getChildAt(0) as? RecyclerView ?: return null
        return rv.layoutManager?.findViewByPosition(position)
    }

    private fun bindVideoAt(item: GalleryItem, root: View) {
        val playerView = root.findViewById<PlayerView>(R.id.detail_player)
        val pos = (binding.pager.adapter as? DetailAdapter)?.positionOf(item.key) ?: -1
        if (pos == currentPos) attachPlayer(pos) else playerView?.player = null
    }

    private fun releaseVideoView(root: View) {
        val playerView = root.findViewById<PlayerView>(R.id.detail_player)
        if (playerView?.player === player) playerView.player = null
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        playerPos = -1
    }

    private fun showStill(item: GalleryItem, view: ImageView) {
        view.setImageDrawable(null)
        view.setBackgroundColor(Color.BLACK)
        lifecycleScope.launch(Dispatchers.IO) {
            val bmp = decodeBounded(item.uri, 2048)
            withContext(Dispatchers.Main) {
                if (bmp != null) view.setImageBitmap(bmp)
            }
        }
    }

    private fun decodeBounded(uri: Uri, maxDim: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, Size(maxDim, maxDim), null)
            } else {
                contentResolver.openInputStream(uri)?.use { ins ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(ins, null, bounds)
                    var sample = 1
                    val longest = maxOf(bounds.outWidth, bounds.outHeight)
                    while (longest / sample > maxDim) sample *= 2
                    contentResolver.openInputStream(uri)?.use { ins2 ->
                        BitmapFactory.decodeStream(
                            ins2, null, BitmapFactory.Options().apply { inSampleSize = sample },
                        )
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // ---------- pager ----------

    private class DetailAdapter(
        private val onImageReady: () -> Unit,
        private val loadThumb: (GalleryItem, ImageView) -> Unit,
        private val bindVideo: (GalleryItem, View) -> Unit,
        private val releaseVideo: (View) -> Unit,
    ) : RecyclerView.Adapter<DetailAdapter.Vh>() {

        private var data: List<GalleryItem> = emptyList()

        fun submit(items: List<GalleryItem>) {
            data = items
            notifyDataSetChanged()
        }

        fun positionOf(key: String) = data.indexOfFirst { it.key == key }

        override fun getItemCount() = data.size

        override fun getItemViewType(position: Int) =
            if (data[position].isVideo) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
            val ctx = parent.context
            return if (viewType == 1) {
                val player = PlayerView(ctx).apply {
                    id = R.id.detail_player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(Color.BLACK)
                }
                val root = androidx.appcompat.widget.LinearLayoutCompat(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    orientation = androidx.appcompat.widget.LinearLayoutCompat.VERTICAL
                    setBackgroundColor(Color.BLACK)
                    addView(player)
                }
                Vh(root)
            } else {
                val img = ImageView(ctx).apply {
                    id = R.id.detail_image
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(Color.BLACK)
                    adjustViewBounds = true
                }
                Vh(img)
            }
        }

        override fun onBindViewHolder(holder: Vh, position: Int) {
            val item = data[position]
            holder.rootView.setBackgroundColor(Color.BLACK)
            if (item.isVideo) {
                bindVideo(item, holder.rootView)
            } else {
                val img = holder.rootView.findViewById<ImageView>(R.id.detail_image)
                    ?: (holder.rootView as? ImageView)
                if (img != null) loadThumb(item, img)
                onImageReady()
            }
        }

        override fun onViewRecycled(holder: Vh) {
            releaseVideo(holder.rootView)
            (holder.rootView as? ImageView)?.setImageDrawable(null)
        }

        class Vh(val rootView: View) : RecyclerView.ViewHolder(rootView)
    }

    /** EXIF / MediaStore metadata sheet (own wording, own layout). */
    class ImageInfoBottomSheet : BottomSheetDialogFragment() {

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View {
            val binding = BottomSheetImageInfoBinding.inflate(inflater, container, false)
            val key = requireArguments().getString(ARG_KEY) ?: ""
            val name = requireArguments().getString(ARG_NAME)
            val path = requireArguments().getString(ARG_PATH)
            val size = requireArguments().getLong(ARG_SIZE)
            val w = requireArguments().getInt(ARG_W)
            val h = requireArguments().getInt(ARG_H)
            val taken = requireArguments().getLong(ARG_TAKEN)
            val added = requireArguments().getLong(ARG_ADDED)
            binding.infoFilename.text =
                getString(R.string.info_row_filename, name ?: getString(R.string.info_unknown))
            // key reserved for future EXIF-field expansion.
            binding.infoPath.text =
                getString(R.string.info_row_path, path ?: getString(R.string.info_unknown))
            binding.infoSize.text = getString(
                R.string.info_row_size,
                if (size > 0) getString(R.string.info_size_mb, size / 1048576.0) else getString(R.string.info_unknown),
            )
            binding.infoResolution.text = getString(
                R.string.info_row_resolution,
                if (w > 0 && h > 0) "$w × $h" else getString(R.string.info_unknown),
            )
            binding.infoDateTaken.text = getString(
                R.string.info_row_taken, formatMs(taken),
            )
            binding.infoDateAdded.text = getString(
                R.string.info_row_added, formatSec(added),
            )
            binding.infoLocation.text = getString(
                R.string.info_row_location, getString(R.string.info_unknown),
            )
            return binding.root
        }

        private fun formatMs(ms: Long): String {
            if (ms <= 0) return getString(R.string.info_unknown)
            return DateFormat.getMediumDateFormat(requireContext()).format(Date(ms)) + " " +
                DateFormat.getTimeFormat(requireContext()).format(Date(ms))
        }

        private fun formatSec(sec: Long): String {
            if (sec <= 0) return getString(R.string.info_unknown)
            return formatMs(sec * 1000L)
        }

        companion object {
            private const val ARG_KEY = "key"
            private const val ARG_NAME = "name"
            private const val ARG_PATH = "path"
            private const val ARG_SIZE = "size"
            private const val ARG_W = "w"
            private const val ARG_H = "h"
            private const val ARG_TAKEN = "taken"
            private const val ARG_ADDED = "added"

            fun newInstance(item: GalleryItem): ImageInfoBottomSheet {
                val f = ImageInfoBottomSheet()
                val args = Bundle().apply {
                    putString(ARG_KEY, item.key)
                    putString(ARG_NAME, item.displayName)
                    putString(ARG_PATH, item.relativePath)
                    putLong(ARG_SIZE, item.sizeBytes)
                    putInt(ARG_W, item.width)
                    putInt(ARG_H, item.height)
                    putLong(ARG_TAKEN, item.dateTakenMs)
                    putLong(ARG_ADDED, item.dateAddedSec)
                }
                val loc = if (item.hasLocation) {
                    String.format(Locale.US, "%.5f, %.5f", item.latitude, item.longitude)
                } else {
                    null
                }
                if (loc != null) args.putString("latlon", loc)
                f.arguments = args
                return f
            }
        }
    }
}
