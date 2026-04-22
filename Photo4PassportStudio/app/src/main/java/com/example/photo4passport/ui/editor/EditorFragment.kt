package com.example.photo4passport.ui.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.photo4passport.R
import com.example.photo4passport.data.BackgroundMode
import com.example.photo4passport.databinding.FragmentEditorBinding
import com.example.photo4passport.state.ComplianceState
import com.example.photo4passport.state.PassportViewModel
import kotlinx.coroutines.launch
import android.view.ViewTreeObserver
class EditorFragment : Fragment(R.layout.fragment_editor) {
    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PassportViewModel by activityViewModels()

    private lateinit var scaleDetector: ScaleGestureDetector
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false
    private var lastBitmap: Bitmap? = null
    // Add this as a class field
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEditorBinding.bind(view)
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (_binding != null) applyMatrix()
        }
        binding.editorPreviewContainer.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

        scaleDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val newScale = (viewModel.uiState.value.scale * detector.scaleFactor)
                        .coerceIn(0.5f, 4f)
                    viewModel.updateTransform(scale = newScale)
                    applyMatrix()
                    return true
                }
            })

        binding.editorPreviewContainer.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            handleDrag(event)
            true
        }



        val backgroundOptions = listOf("White", "Light Blue")
        val bgAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            backgroundOptions
        )
        binding.backgroundDropdown.setAdapter(bgAdapter)


        binding.autoCropButton.setOnClickListener {
            viewModel.autoCrop()
            // Wait one frame so ImageView is laid out before computing matrix
            binding.sourceImageView.post { applyMatrix() }
        }

        binding.removeBackgroundButton.setOnClickListener {
            val newMode = if (viewModel.uiState.value.backgroundMode == BackgroundMode.WHITE)
                BackgroundMode.LIGHT_BLUE else BackgroundMode.WHITE
            viewModel.applyBackgroundMode(newMode)
            binding.backgroundDropdown.setText(
                if (newMode == BackgroundMode.WHITE) "White" else "Light Blue", false
            )
        }

        binding.backgroundDropdown.setOnItemClickListener { _, _, position, _ ->
            val mode = if (position == 0) BackgroundMode.WHITE else BackgroundMode.LIGHT_BLUE
            viewModel.applyBackgroundMode(mode)
        }

        binding.resetTransformButton.setOnClickListener {
            viewModel.resetTransform()
            applyMatrix()
        }

        binding.validateButton.setOnClickListener { viewModel.validate() }

        binding.continueToExportButton.setOnClickListener {
            viewModel.buildExport(4)
            findNavController().navigate(R.id.action_editorFragment_to_exportFragment)
        }

        binding.newPhotoButton.setOnClickListener {
            viewModel.resetWorkflow()
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Only update ImageView when bitmap actually changes

                    val displayBitmap = state.processedBitmap ?: state.bitmap
                    if (displayBitmap != null) {
                        binding.sourceImageView.scaleType = android.widget.ImageView.ScaleType.MATRIX
                        binding.sourceImageView.setImageBitmap(displayBitmap)
                        lastBitmap = displayBitmap
                        binding.sourceImageView.post { applyMatrix() }
                    }



                    // Compliance banner
                    binding.complianceBanner.text = state.complianceState.message
                    val bannerColor = when (state.complianceState) {
                        is ComplianceState.Pass -> "#DCFCE7".toColorInt()
                        is ComplianceState.Warning -> "#FEF3C7".toColorInt()
                        is ComplianceState.Fail -> "#FEE2E2".toColorInt()
                        else -> Color.parseColor("#E5E7EB")
                    }
                    binding.complianceBanner.setBackgroundColor(bannerColor)

                    // Sync background dropdown with state (e.g. when country changes)
                    val bgText = if (state.backgroundMode == BackgroundMode.WHITE) "White" else "Light Blue"
                    if (binding.backgroundDropdown.text.toString() != bgText) {
                        binding.backgroundDropdown.setText(bgText, false)
                    }

                    // Update preview container background color
                    val bgColor = when (state.backgroundMode) {
                        BackgroundMode.WHITE -> Color.WHITE
                        BackgroundMode.LIGHT_BLUE -> Color.parseColor("#DCEBFF")
                    }


                    binding.sourceImageView.setBackgroundColor(bgColor)
                    binding.cropGuideOverlay.aspectRatio = state.selectedCountry?.aspectRatio ?: 1f

                }
            }
        }
    }

    private fun handleDrag(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && isDragging) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    viewModel.updateTransform(
                        translationX = viewModel.uiState.value.translationX + dx,
                        translationY = viewModel.uiState.value.translationY + dy
                    )
                    applyMatrix()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
        }
    }

    private fun applyMatrix() {
        val state = viewModel.uiState.value
        val image = binding.sourceImageView.drawable ?: return
        val vw = binding.sourceImageView.width.toFloat().takeIf { it > 0f } ?: return
        val vh = binding.sourceImageView.height.toFloat().takeIf { it > 0f } ?: return
        val iw = image.intrinsicWidth.toFloat()
        val ih = image.intrinsicHeight.toFloat()

        // Use minOf for fitCenter (whole image visible) instead of maxOf (centerCrop)
        val baseScale = minOf(vw / iw, vh / ih)
        val totalScale = baseScale * state.scale
        val scaledW = iw * totalScale
        val scaledH = ih * totalScale
        val dx = (vw - scaledW) / 2f + state.translationX
        val dy = (vh - scaledH) / 2f + state.translationY

        val matrix = Matrix()
        matrix.postScale(totalScale, totalScale)
        matrix.postTranslate(dx, dy)
        binding.sourceImageView.imageMatrix = matrix
    }

    override fun onDestroyView() {
        super.onDestroyView()
        globalLayoutListener?.let {
            binding.editorPreviewContainer.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        _binding = null
        lastBitmap = null
    }
}
