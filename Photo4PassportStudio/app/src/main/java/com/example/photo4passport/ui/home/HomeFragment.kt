package com.example.photo4passport.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.photo4passport.R
import com.example.photo4passport.databinding.FragmentHomeBinding
import com.example.photo4passport.state.PassportViewModel
import kotlinx.coroutines.launch
import androidx.activity.result.PickVisualMediaRequest

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PassportViewModel by activityViewModels()

    // Sample images bundled in drawable
    private val sampleImages = listOf(
        "Sample 1" to R.drawable.sample1,
        "Sample 2" to R.drawable.sample2,
        "Sample 3" to R.drawable.sample3
    )

    // Photo Picker launcher — no permission needed on API 33+
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) loadBitmapFromUri(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        setupCountryDropdown()

        // "Browse Samples" button → pick a bundled sample image
        binding.takePhotoButton.setOnClickListener { showSampleChooser() }

        // "Upload Image" button → open system photo picker
        binding.uploadPhotoButton.setOnClickListener { openGallery() }

        // "Start Editing" → navigate to editor if image selected
        binding.startEditingButton.setOnClickListener {
            if (viewModel.uiState.value.bitmap == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.no_image_selected),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                findNavController().navigate(R.id.action_homeFragment_to_editorFragment)
            }
        }

        // Observe state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.homePreviewImage.setImageBitmap(state.bitmap)
                    val desiredText = state.selectedCountry?.displayName ?: ""
                    if (binding.countryDropdown.text.toString() != desiredText) {
                        binding.countryDropdown.setText(desiredText, false)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupCountryDropdown()
    }

    private fun setupCountryDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            viewModel.countries
        )
        binding.countryDropdown.setAdapter(adapter)
        binding.countryDropdown.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let { viewModel.selectCountry(it) }
        }
        val currentCountry = viewModel.uiState.value.selectedCountry?.displayName ?: ""
        binding.countryDropdown.setText(currentCountry, false)
    }

    // ── Gallery ──────────────────────────────────────────────────────────────

    private fun openGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun loadBitmapFromUri(uri: Uri) {
        try {
            val bitmap = decodeSampledBitmapFromStream(uri) ?: run {
                Toast.makeText(requireContext(), "Could not decode image", Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.setBitmap(bitmap, uri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeSampledBitmapFromStream(uri: Uri): Bitmap? {
        return try {
            val opts1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            requireContext().contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts1)
            }
            val maxDim = 1600
            var sample = 1
            while (opts1.outWidth / sample > maxDim || opts1.outHeight / sample > maxDim) {
                sample *= 2
            }
            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            requireContext().contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts2)
            }
        } catch (e: Exception) {
            null
        }
    }

    // ── Sample chooser ───────────────────────────────────────────────────────

    private fun showSampleChooser() {
        val names = sampleImages.map { it.first }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.choose_sample_image))
            .setItems(names) { _, which ->
                val resId = sampleImages[which].second
                val bitmap = decodeSampleBitmap(resId)
                if (bitmap == null) {
                    Toast.makeText(requireContext(), "Could not load sample image", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.setBitmap(bitmap)
                }
            }
            .show()
    }

    private fun decodeSampleBitmap(resId: Int): Bitmap? {
        return try {
            val opts1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, resId, opts1)
            val maxDim = 1200
            var sample = 1
            while (opts1.outWidth / sample > maxDim || opts1.outHeight / sample > maxDim) {
                sample *= 2
            }
            val opts2 = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeResource(resources, resId, opts2)
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}