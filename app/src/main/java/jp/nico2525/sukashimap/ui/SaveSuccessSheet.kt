package jp.nico2525.sukashimap.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import jp.nico2525.sukashimap.R
import jp.nico2525.sukashimap.databinding.BottomSheetSaveSuccessBinding

/** Shown after a photo/video is saved: thumbnail + close. */
class SaveSuccessSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSaveSuccessBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetSaveSuccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val titleRes = arguments?.getInt(ARG_TITLE_RES, R.string.photo_saved) ?: R.string.photo_saved
        binding.tvSaveSuccessTitle.setText(titleRes)
        val uri = arguments?.getString(ARG_URI)?.let { Uri.parse(it) }
        if (uri != null) binding.ivSaveSuccessPreview.setImageURI(uri)
        binding.btnSaveSuccessClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TITLE_RES = "title_res"
        private const val ARG_URI = "uri"

        fun newInstance(titleRes: Int, uri: Uri?): SaveSuccessSheet {
            val s = SaveSuccessSheet()
            s.arguments = Bundle().apply {
                putInt(ARG_TITLE_RES, titleRes)
                putString(ARG_URI, uri?.toString())
            }
            return s
        }
    }
}
