package com.ytone.longcare.features.photoupload.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.ytone.longcare.databinding.DialogPreviewBinding
import java.io.ByteArrayOutputStream

class PreviewDialogFragment : DialogFragment() {

    interface PreviewDialogListener {
        fun onConfirm(bitmap: Bitmap)
        fun onRetake()
    }

    private var listener: PreviewDialogListener? = null
    private var _binding: DialogPreviewBinding? = null
    private val binding get() = _binding!!
    private lateinit var bitmap: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val byteArray = requireArguments().getByteArray(ARG_BITMAP_BYTE_ARRAY)
        bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray!!.size)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.previewImage.setImageBitmap(bitmap)

        binding.confirmButton.setOnClickListener {
            listener?.onConfirm(bitmap)
            dismiss()
        }

        binding.retakeButton.setOnClickListener {
            listener?.onRetake()
            dismiss()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = try {
            context as PreviewDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement PreviewDialogListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_BITMAP_BYTE_ARRAY = "bitmap_byte_array"

        fun newInstance(bitmap: Bitmap): PreviewDialogFragment {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val byteArray = stream.toByteArray()

            val args = Bundle()
            args.putByteArray(ARG_BITMAP_BYTE_ARRAY, byteArray)
            val fragment = PreviewDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
