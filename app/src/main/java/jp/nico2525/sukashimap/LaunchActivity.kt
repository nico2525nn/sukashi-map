package jp.nico2525.sukashimap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import jp.nico2525.sukashimap.databinding.ActivityLaunchBinding

class LaunchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLaunchBinding

    private val galleryPick =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val uri = data.getStringExtra(MainActivity.EXTRA_CAPTURED_URI)?.let { Uri.parse(it) }
                ?: return@registerForActivityResult
            openMain(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLaunchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardNewCapture.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.cardFromAlbum.setOnClickListener {
            galleryPick.launch(
                Intent(this, GalleryActivity::class.java)
                    .putExtra(GalleryActivity.EXTRA_PICK_MODE, true)
                    .putExtra(GalleryActivity.EXTRA_OWN_ONLY, false),
            )
        }
        binding.btnLaunchGallery.setOnClickListener {
            runCatching {
                startActivity(Intent(this, Class.forName("jp.nico2525.sukashimap.GalleryActivity")))
            }.onFailure {
                Toast.makeText(this, R.string.gallery_empty, Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnLaunchSettings.setOnClickListener {
            runCatching {
                startActivity(Intent(this, Class.forName("jp.nico2525.sukashimap.SettingsActivity")))
            }
        }
    }

    private fun openMain(uri: Uri) {
        val type = contentResolver.getType(uri).orEmpty()
        val looksVideo = type.startsWith("video")
        if (!looksVideo && !type.startsWith("image") && type.isNotBlank()) {
            Toast.makeText(this, R.string.launch_unsupported_file, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_CAPTURED_URI, uri.toString())
                .putExtra(MainActivity.EXTRA_CAPTURED_IS_VIDEO, looksVideo),
        )
    }
}
