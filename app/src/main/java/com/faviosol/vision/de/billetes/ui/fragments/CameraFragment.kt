package com.faviosol.vision.de.billetes.ui.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import java.util.Locale
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.faviosol.vision.de.billetes.data.BilleteClassifier
import com.faviosol.vision.de.billetes.domain.model.BilleteDeteccion
import com.faviosol.vision.de.billetes.databinding.FragmentCameraBinding
import com.faviosol.vision.de.billetes.R

class CameraFragment : Fragment(), BilleteClassifier.ClasificadorListener {

    private val TAG = "CameraFragment"

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private lateinit var clasificador: BilleteClassifier
    private lateinit var bitmapBuffer: Bitmap

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var cameraExecutor: ExecutorService

    private var tts: TextToSpeech? = null
    private var lastSpokenDenomination: String = ""
    private var lastSpokenTime: Long = 0L
    private val TTS_COOLDOWN_MS = 15000L

    private val vibrationPatterns = mapOf(
        "100" to longArrayOf(0, 500),
        "50"  to longArrayOf(0, 200, 100, 200, 100, 200),
        "20"  to longArrayOf(0, 200, 100, 200),
        "10"  to longArrayOf(0, 200)
    )

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        clasificador = BilleteClassifier(context = requireContext(), listener = this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "PE")
            }
        }

        fragmentCameraBinding.viewFinder.post { setUpCamera() }
        initBottomSheetControls()
    }

    private fun initBottomSheetControls() {
        fragmentCameraBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (clasificador.threshold >= 0.1) {
                clasificador.threshold -= 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (clasificador.threshold <= 0.8) {
                clasificador.threshold += 0.1f
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.maxResultsMinus.setOnClickListener {
            if (clasificador.maxResults > 1) {
                clasificador.maxResults--
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.maxResultsPlus.setOnClickListener {
            if (clasificador.maxResults < 5) {
                clasificador.maxResults++
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.threadsMinus.setOnClickListener {
            if (clasificador.numThreads > 1) {
                clasificador.numThreads--
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.threadsPlus.setOnClickListener {
            if (clasificador.numThreads < 4) {
                clasificador.numThreads++
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    clasificador.currentDelegate = p2
                    updateControlsUi()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {}
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
    }

    private fun updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.maxResultsValue.text = clasificador.maxResults.toString()
        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text = String.format("%.2f", clasificador.threshold)
        fragmentCameraBinding.bottomSheetLayout.threadsValue.text = clasificador.numThreads.toString()
        clasificador.limpiar()
        fragmentCameraBinding.overlay.clear()
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Falló la inicialización de la cámara.")
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { image ->
                    if (!::bitmapBuffer.isInitialized) {
                        bitmapBuffer = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                    }
                    detectObjects(image)
                }
            }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Fallo al vincular casos de uso", exc)
        }
    }

    private fun detectObjects(image: ImageProxy) {
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
        clasificador.clasificar(bitmapBuffer, image.imageInfo.rotationDegrees)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    private fun vibrate(label: String) {
        val pattern = vibrationPatterns.entries.firstOrNull { label.contains(it.key) }?.value ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun labelToSpanish(label: String): String {
        return when {
            label.contains("100") -> "Billete de cien soles"
            label.contains("50")  -> "Billete de cincuenta soles"
            label.contains("20")  -> "Billete de veinte soles"
            label.contains("10")  -> "Billete de diez soles"
            else                  -> "Billete detectado"
        }
    }

    override fun onResultado(deteccion: BilleteDeteccion) {
        activity?.runOnUiThread {
            fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                String.format("%d ms", deteccion.tiempoInferencia)

            fragmentCameraBinding.overlay.clear()

            if (deteccion.puntaje >= clasificador.threshold) {
                val denomination = when {
                    deteccion.etiqueta.contains("100") -> "100"
                    deteccion.etiqueta.contains("50")  -> "50"
                    deteccion.etiqueta.contains("20")  -> "20"
                    deteccion.etiqueta.contains("10")  -> "10"
                    else                               -> deteccion.etiqueta
                }
                val now = System.currentTimeMillis()
                if (denomination != lastSpokenDenomination || now - lastSpokenTime > TTS_COOLDOWN_MS) {
                    tts?.speak(labelToSpanish(deteccion.etiqueta), TextToSpeech.QUEUE_FLUSH, null, null)
                    vibrate(deteccion.etiqueta)
                    lastSpokenDenomination = denomination
                    lastSpokenTime = now
                }
            }
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        cameraExecutor.shutdown()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
