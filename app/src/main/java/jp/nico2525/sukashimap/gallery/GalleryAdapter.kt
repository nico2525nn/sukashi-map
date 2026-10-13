package jp.nico2525.sukashimap.gallery

import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.nico2525.sukashimap.databinding.ItemGalleryImageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Grid adapter for MediaStore rows.
 * Thumbnails load off the main thread via ContentResolver#loadThumbnail
 * with a small memory cache; the view tag guards against recycled-view races.
 */
class GalleryAdapter(
    private val scope: CoroutineScope,
    private val onClick: (Int) -> Unit,
) : ListAdapter<GalleryItem, GalleryAdapter.Vh>(DIFF) {

    private val cache = object : LruCache<String, Bitmap>(64) {
        override fun sizeOf(key: String, value: Bitmap) =
            value.byteCount / 1024
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemGalleryImageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return Vh(binding, onClick)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(getItem(position), ::thumbFor)
    }

    override fun onViewRecycled(holder: Vh) {
        holder.clear()
    }

    private fun thumbFor(item: GalleryItem, target: ThumbTarget) {
        cache.get(item.key)?.let { target.show(item.key, it); return }
        scope.launch(Dispatchers.IO) {
            val bmp = runCatching {
                target.context.contentResolver.loadThumbnail(item.uri, Size(512, 512), null)
            }.getOrNull()
            if (bmp != null) cache.put(item.key, bmp)
            withContext(Dispatchers.Main) {
                if (bmp != null) target.show(item.key, bmp) else target.showPlaceholder(item.key)
            }
        }
    }

    interface ThumbTarget {
        val context: android.content.Context
        fun show(key: String, bmp: Bitmap)
        fun showPlaceholder(key: String)
    }

    class Vh(
        private val binding: ItemGalleryImageBinding,
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root), ThumbTarget {
        override val context: android.content.Context get() = binding.root.context

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(pos)
            }
        }

        fun bind(item: GalleryItem, loader: (GalleryItem, ThumbTarget) -> Unit) {
            binding.thumb.tag = item.key
            binding.thumb.setImageDrawable(null)
            binding.thumb.setBackgroundColor(0xFF222222.toInt())
            loader(item, this)
            if (item.isVideo) {
                binding.videoBadge.visibility = View.VISIBLE
                binding.videoBadge.text = formatDuration(item.durationMs)
            } else {
                binding.videoBadge.visibility = View.GONE
            }
            binding.root.contentDescription = item.displayName
        }

        fun clear() {
            binding.thumb.tag = null
            binding.thumb.setImageDrawable(null)
        }

        override fun show(key: String, bmp: Bitmap) {
            if (binding.thumb.tag == key) {
                binding.thumb.setBackgroundColor(0xFF000000.toInt())
                binding.thumb.setImageBitmap(bmp)
            }
        }

        override fun showPlaceholder(key: String) {
            if (binding.thumb.tag == key) binding.thumb.setImageDrawable(null)
        }

        private fun formatDuration(ms: Long): String {
            val s = (ms / 1000).toInt().coerceAtLeast(0)
            return "%d:%02d".format(s / 60, s % 60)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<GalleryItem>() {
            override fun areItemsTheSame(a: GalleryItem, b: GalleryItem) = a.key == b.key
            override fun areContentsTheSame(a: GalleryItem, b: GalleryItem) = a == b
        }
    }
}
