package com.example.photo4passport.ui.export

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.photo4passport.R
import com.example.photo4passport.databinding.FragmentExportBinding
import com.example.photo4passport.state.PassportViewModel
import com.example.photo4passport.util.BitmapUtils
import com.example.photo4passport.util.PdfExportUtil
import kotlinx.coroutines.launch

class ExportFragment : Fragment(R.layout.fragment_export) {
    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PassportViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentExportBinding.bind(view)

        // Build default 4-up sheet when fragment opens
        if (viewModel.uiState.value.exportBitmap == null) {
            viewModel.buildExport(4)
        }

        // Chip group: rebuild sheet when user changes count
        binding.sheetOptionChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val count = when (checkedIds.firstOrNull()) {
                R.id.chip6 -> 6
                R.id.chip8 -> 8
                else -> 4
            }
            viewModel.buildExport(count)
        }

        binding.saveButton.setOnClickListener {
            val bitmap = viewModel.uiState.value.exportBitmap ?: return@setOnClickListener
            val uri = BitmapUtils.saveBitmapToGallery(
                requireContext(), bitmap,
                "passport_sheet_${System.currentTimeMillis()}.png"
            )
            Toast.makeText(
                requireContext(),
                if (uri != null) "Saved to Pictures/Photo4Passport" else "Save failed",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.shareButton.setOnClickListener {
            val bitmap = viewModel.uiState.value.exportBitmap ?: return@setOnClickListener
            val file = BitmapUtils.saveBitmapToCache(requireContext(), bitmap, "share_sheet.png")
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share passport sheet"))
        }

        binding.printButton.setOnClickListener {
            val bitmap = viewModel.uiState.value.exportBitmap ?: return@setOnClickListener
            val pdfUri = PdfExportUtil.exportBitmapAsPdf(requireContext(), bitmap)
            val printIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(pdfUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(Intent.createChooser(printIntent, "Open PDF to print"))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "No PDF viewer available", Toast.LENGTH_SHORT).show()
            }
        }

        binding.backToEditButton.setOnClickListener { findNavController().popBackStack() }

        binding.backToHomeButton.setOnClickListener {
            viewModel.resetWorkflow()
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        // Observe export bitmap
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.finalPreviewImageView.setImageBitmap(state.exportBitmap)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
