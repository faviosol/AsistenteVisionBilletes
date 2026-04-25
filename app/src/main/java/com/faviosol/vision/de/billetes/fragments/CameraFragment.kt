package com.faviosol.vision.de.billetes.fragments

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
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.faviosol.vision.de.billetes.ObjectDetectorHelper
import org.tensorflow.lite.task.vision.detector.Detection
import com.faviosol.vision.de.billetes.databinding.FragmentCameraBinding
import com.faviosol.vision.de.billetes.R

/**
 * CICLO DE VIDA DE UN FRAGMENT EN ANDROID:
 *
 * onCreateView() → onViewCreated() → onResume() → ... → onDestroyView()
 *
 * Este Fragment maneja la cámara para detectar objetos en tiempo real.
 */

class CameraFragment : Fragment(), ObjectDetectorHelper.DetectorListener {

    private val TAG = "ObjectDetection"

    // =====================================================
    // VARIABLES DE ESTADO DEL FRAGMENT
    // =====================================================

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    // Se usa !! para lanzar excepción si es nulo (no debería serlo después de onCreateView)
    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    // Helper para detectar objetos
    private lateinit var objectDetectorHelper: ObjectDetectorHelper

    // Buffer de imagen donde se copian los píxeles de la cámara
    private lateinit var bitmapBuffer: Bitmap

    // Componentes de CameraX para capturar video y analizar imágenes
    private var preview: Preview? = null                    // Vista previa de la cámara
    private var imageAnalyzer: ImageAnalysis? = null        // Procesador de imágenes
    private var camera: Camera? = null                       // Control de la cámara
    private var cameraProvider: ProcessCameraProvider? = null // Proveedor de la cámara

    /** Ejecutor de un solo hilo para operaciones de cámara que bloquean */
    private lateinit var cameraExecutor: ExecutorService

    private var tts: TextToSpeech? = null
    private var lastSpokenLabel: String = ""
    private var lastSpokenTime: Long = 0L
    private val TTS_COOLDOWN_MS = 3000L

    // Patrones de vibración por denominación: par (espera, vibración) en ms
    private val vibrationPatterns = mapOf(
        "100" to longArrayOf(0, 500),
        "50"  to longArrayOf(0, 200, 100, 200, 100, 200),
        "20"  to longArrayOf(0, 200, 100, 200),
        "10"  to longArrayOf(0, 200)
    )


    // =====================================================
    // FASE 1: CUANDO EL FRAGMENT VUELVE A ESTAR VISIBLE
    // =====================================================

    override fun onResume() {
        super.onResume()

        // Verifica que los permisos sigan siendo válidos
        // (el usuario pudo haberlos removido mientras la app estaba pausada)
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            // Si no tiene permisos, navega a la pantalla de permisos
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
    }


    // =====================================================
    // FASE 0: CREAR LA VISTA DEL FRAGMENT
    // (Se llama cuando el Fragment necesita su UI)
    // =====================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Crea la vista del fragment usando View Binding
        // Esto reemplaza el inflado manual de XML
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }


    // =====================================================
    // FASE 1.5: CONFIGURACIÓN INICIAL DESPUÉS DE CREAR LA VISTA
    // (Se llama después de onCreateView, cuando la vista ya existe)
    // =====================================================

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Crea el helper que detectará objetos
        objectDetectorHelper = ObjectDetectorHelper(
            context = requireContext(),
            objectDetectorListener = this)  // Este Fragment escucha los resultados

        // Crea un ejecutor de un solo hilo para operaciones de cámara
        // (Esto evita que bloquee el hilo principal de UI)
        cameraExecutor = Executors.newSingleThreadExecutor()

        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "PE")
            }
        }

        // Espera a que la vista se haya dibujado completamente en pantalla
        // Esto es importante porque necesita las dimensiones correctas
        fragmentCameraBinding.viewFinder.post {
            // Una vez que la vista esté lista, configura la cámara
            setUpCamera()
        }

        // Configura los botones y controles de la interfaz
        initBottomSheetControls()
    }


    // =====================================================
    // CONFIGURACIÓN DE CONTROLES DE LA UI (BOTONES)
    // =====================================================

    private fun initBottomSheetControls() {

        // ----- CONTROLES DE UMBRAL DE CONFIANZA -----

        // Botón para DISMINUIR el umbral de detección (detecta objetos menos obvios)
        fragmentCameraBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (objectDetectorHelper.threshold >= 0.1) {
                objectDetectorHelper.threshold -= 0.1f  // Reduce en 0.1
                updateControlsUi()  // Actualiza la pantalla
            }
        }

        // Botón para AUMENTAR el umbral de detección (solo detecta objetos obvios)
        fragmentCameraBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (objectDetectorHelper.threshold <= 0.8) {
                objectDetectorHelper.threshold += 0.1f  // Aumenta en 0.1
                updateControlsUi()
            }
        }

        // ----- CONTROLES DE CANTIDAD DE OBJETOS -----

        // Botón para REDUCIR cuántos objetos puede detectar al mismo tiempo
        fragmentCameraBinding.bottomSheetLayout.maxResultsMinus.setOnClickListener {
            if (objectDetectorHelper.maxResults > 1) {
                objectDetectorHelper.maxResults--  // Reduce la cantidad
                updateControlsUi()
            }
        }

        // Botón para AUMENTAR cuántos objetos puede detectar al mismo tiempo
        fragmentCameraBinding.bottomSheetLayout.maxResultsPlus.setOnClickListener {
            if (objectDetectorHelper.maxResults < 5) {
                objectDetectorHelper.maxResults++  // Aumenta la cantidad
                updateControlsUi()
            }
        }

        // ----- CONTROLES DE THREADS (HILOS) -----

        // Botón para REDUCIR el número de hilos para detección
        // (Menos hilos = más lento pero menos uso de CPU)
        fragmentCameraBinding.bottomSheetLayout.threadsMinus.setOnClickListener {
            if (objectDetectorHelper.numThreads > 1) {
                objectDetectorHelper.numThreads--
                updateControlsUi()
            }
        }

        // Botón para AUMENTAR el número de hilos para detección
        // (Más hilos = más rápido pero más uso de CPU)
        fragmentCameraBinding.bottomSheetLayout.threadsPlus.setOnClickListener {
            if (objectDetectorHelper.numThreads < 4) {
                objectDetectorHelper.numThreads++
                updateControlsUi()
            }
        }

        // ----- SELECTOR DE HARDWARE (CPU, GPU, NNAPI) -----

        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                // Se llama cuando seleccionas una opción
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    // p2 = índice seleccionado (0=CPU, 1=GPU, 2=NNAPI)
                    objectDetectorHelper.currentDelegate = p2
                    updateControlsUi()
                }

                // Se llama si nada está seleccionado (caso raro)
                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no se hace nada */
                }
            }

        // ----- SELECTOR DE MODELO -----

        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    // Aquí iría el código para cambiar de modelo de detección
                    // Actualmente está comentado/vacío
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no se hace nada */
                }
            }
    }


    // =====================================================
    // ACTUALIZAR LA UI CON NUEVOS VALORES
    // =====================================================

    // Actualiza los valores mostrados en la pantalla cuando cambias los controles
    private fun updateControlsUi() {
        // Muestra el número máximo de resultados
        fragmentCameraBinding.bottomSheetLayout.maxResultsValue.text =
            objectDetectorHelper.maxResults.toString()

        // Muestra el umbral de confianza con 2 decimales (ej: 0.50)
        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", objectDetectorHelper.threshold)

        // Muestra el número de hilos
        fragmentCameraBinding.bottomSheetLayout.threadsValue.text =
            objectDetectorHelper.numThreads.toString()

        // IMPORTANTE: Limpia el detector de objetos en lugar de recrearlo
        // Esto es necesario porque el delegado GPU debe inicializarse en el hilo que lo usa
        objectDetectorHelper.clearObjectDetector()

        // Limpia los cuadros dibujados en pantalla
        fragmentCameraBinding.overlay.clear()
    }


    // =====================================================
    // CONFIGURACIÓN DE LA CÁMARA (PASO 1)
    // =====================================================

    // Inicializa CameraX y lo prepara para usarse
    private fun setUpCamera() {
        // Obtiene el proveedor de cámara de forma asincrónica
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        // Agrega un listener que se ejecuta cuando el proveedor está listo
        cameraProviderFuture.addListener(
            {
                // El proveedor ya está disponible
                cameraProvider = cameraProviderFuture.get()

                // Ahora que tenemos el proveedor, vinculamos los casos de uso
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())  // Se ejecuta en el hilo principal
        )
    }


    // =====================================================
    // CONFIGURACIÓN DE LA CÁMARA (PASO 2)
    // =====================================================

    // Vincula los casos de uso de cámara:
    // - Preview: Mostrar lo que ve la cámara
    // - ImageAnalysis: Procesar imágenes para detección
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // Obtiene el proveedor (debe existir porque lo configuramos antes)
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Falló la inicialización de la cámara.")

        // Define qué cámara usar (la del respaldo, no la frontal)
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // ----- PREVIEW: Mostrar lo que ve la cámara -----
        // Usa relación 4:3 porque así se entrenaron los modelos de detección
        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()

        // ----- IMAGE ANALYSIS: Procesar imágenes en tiempo real -----
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)

                // STRATEGY_KEEP_ONLY_LATEST: Si una imagen se procesa lentamente,
                // descarta las viejas y solo procesa la más reciente
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

                // Formato RGBA (igual que usan los modelos de ML)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // Asigna el analizador que procesará cada frame
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->

                        // Primera vez: inicializa el buffer de bitmap con el tamaño correcto
                        if (!::bitmapBuffer.isInitialized) {
                            // Solo se hace una vez cuando el analizador comienza a funcionar
                            bitmapBuffer = Bitmap.createBitmap(
                                image.width,
                                image.height,
                                Bitmap.Config.ARGB_8888  // Formato de color: rojo-verde-azul-alpha
                            )
                        }

                        // Procesa la imagen para detectar objetos
                        detectObjects(image)
                    }
                }

        // Desvincula todos los casos de uso previos
        // (necesario porque no se pueden tener dos veces)
        cameraProvider.unbindAll()

        try {
            // Vincula los casos de uso a la cámara
            // - preview: Mostrar en pantalla
            // - imageAnalyzer: Procesar para detección
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            // Conecta la vista previa al caso de uso Preview
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)

        } catch (exc: Exception) {
            // Si algo falla, registra el error
            Log.e(TAG, "Fallo al vincular casos de uso", exc)
        }
    }


    // =====================================================
    // PROCESAR CADA FRAME DE LA CÁMARA
    // =====================================================

    private fun detectObjects(image: ImageProxy) {
        // Copia los píxeles RGB de la imagen de cámara al buffer compartido
        // (image.planes[0] contiene los píxeles)
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        // Obtiene cuántos grados está rotada la imagen
        val imageRotation = image.imageInfo.rotationDegrees

        // Envía el bitmap y rotación al detector para procesar
        objectDetectorHelper.detect(bitmapBuffer, imageRotation)
    }


    // =====================================================
    // CUANDO LA CONFIGURACIÓN DEL DISPOSITIVO CAMBIA
    // =====================================================

    // Se llama cuando el dispositivo cambia de orientación (vertical/horizontal)
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Actualiza el analizador de imágenes para la nueva rotación
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }


    // =====================================================
    // CALLBACKS DEL DETECTOR (RESULTADOS)
    // =====================================================

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

    // Se llama cuando el detector ha encontrado objetos
    // (Implementa la interfaz DetectorListener)
    override fun onResults(
        results: MutableList<Detection>?,  // Objetos detectados
        inferenceTime: Long,               // Tiempo que tardó la detección (en ms)
        imageHeight: Int,                  // Alto de la imagen procesada
        imageWidth: Int                    // Ancho de la imagen procesada
    ) {
        // Ejecuta en el hilo principal de UI (porque estamos en un hilo de fondo)
        activity?.runOnUiThread {
            // Muestra el tiempo que tardó en detectar
            fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                String.format("%d ms", inferenceTime)

            // Envía los resultados a la vista de overlay para que dibuje los cuadros
            fragmentCameraBinding.overlay.setResults(
                results ?: LinkedList<Detection>(),  // Si es nulo, usa una lista vacía
                imageHeight,
                imageWidth
            )

            // Fuerza a redibujar la pantalla con los nuevos cuadros
            fragmentCameraBinding.overlay.invalidate()

            // Anuncia en voz alta el billete detectado con mayor confianza
            val topLabel = results?.firstOrNull()?.categories?.firstOrNull()?.label
            if (topLabel != null) {
                val now = System.currentTimeMillis()
                if (topLabel != lastSpokenLabel || now - lastSpokenTime > TTS_COOLDOWN_MS) {
                    tts?.speak(labelToSpanish(topLabel), TextToSpeech.QUEUE_FLUSH, null, null)
                    vibrate(topLabel)
                    lastSpokenLabel = topLabel
                    lastSpokenTime = now
                }
            }
        }
    }


    // Se llama cuando ocurre un error en la detección
    override fun onError(error: String) {
        // Ejecuta en el hilo principal de UI
        activity?.runOnUiThread {
            // Muestra un mensaje de error temporal al usuario
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }


    // =====================================================
    // FASE FINAL: LIMPIAR RECURSOS
    // =====================================================

    // Se llama cuando la vista se destruye (fragment se elimina o cambia de pantalla)
    override fun onDestroyView() {
        // Libera la referencia al binding
        _fragmentCameraBinding = null
        super.onDestroyView()

        // IMPORTANTE: Detiene el ejecutor de cámara
        // Esto es esencial para liberar recursos y evitar memory leaks
        cameraExecutor.shutdown()

        tts?.stop()
        tts?.shutdown()
        tts = null
    }

}