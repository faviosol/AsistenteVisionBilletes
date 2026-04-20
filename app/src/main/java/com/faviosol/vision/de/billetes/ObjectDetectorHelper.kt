package com.faviosol.vision.de.billetes

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * ==================================================================================
 * CLASE: ObjectDetectorHelper
 *
 * PROPÓSITO: Esta clase es responsable de:
 * 1. Cargar un modelo de Machine Learning (TensorFlow Lite)
 * 2. Procesar imágenes del teléfono para que el modelo las entienda
 * 3. Ejecutar la detección de objetos en la imagen
 * 4. Notificar al Fragment cuando tiene resultados
 *
 * FLUJO DE VIDA:
 * Constructor → setupObjectDetector() → detect() → onResults()
 *                                            ↑
 *                                            └─ Se llama repetidamente para cada frame
 * ==================================================================================
 */

class ObjectDetectorHelper(
    // =====================================================
    // PARÁMETROS CONFIGURABLES (el usuario puede cambiarlos desde la UI)
    // =====================================================

    var threshold: Float = 0.3f,           // Umbral de confianza (0.0 - 1.0)
    // 0.3 = detecto cosas hasta 30% de confianza
    // 0.9 = solo detecto cosas MUY seguras (>90%)

    var numThreads: Int = 2,                // Cuántos hilos CPU usar para procesar
    // Más hilos = más rápido pero más calor/batería
    // 1 = lento pero eficiente
    // 4 = muy rápido pero consume mucho

    var maxResults: Int = 1,                // Cuántos objetos máximo detectar en cada imagen
    // 1 = solo el objeto más probable
    // 5 = hasta 5 objetos diferentes

    var currentDelegate: Int = 0,           // Qué hardware usar (CPU, GPU, NNAPI)
    // Esto afecta DÓNDE se ejecuta la detección

    // =====================================================
    // PARÁMETROS OBLIGATORIOS (necesarios para inicializar)
    // =====================================================

    val context: Context,                   // Contexto de Android (para acceder a archivos)
    val objectDetectorListener: DetectorListener?  // Listener que recibe los resultados
) {

    // =====================================================
    // VARIABLES PRIVADAS (solo usa esta clase)
    // =====================================================

    /** El modelo de TensorFlow Lite cargado en memoria
     *  Null = no está cargado
     *  ObjectDetector = está listo para detectar */
    private var objectDetector: ObjectDetector? = null

    /** Nombre del archivo del modelo que está en la carpeta assets/ del proyecto
     *  "billetes.tflite" = este archivo debe estar en:
     *  proyecto/app/src/main/assets/billetes.tflite */
    private val modelName = "billetes.tflite"


    // =====================================================
    // CONSTRUCTOR: Se ejecuta cuando se crea la instancia
    // =====================================================

    init {
        // La palabra "init" significa "bloque de inicialización"
        // Se ejecuta inmediatamente después de crear la instancia
        // En este caso, carga el modelo de IA

        Log.d("ObjectDetector", "🔄 Inicializando ObjectDetectorHelper...")
        setupObjectDetector()
    }


    // =====================================================
    // MÉTODO 1: Limpiar el modelo actual (sin cargarlo de nuevo)
    // =====================================================

    /**
     * Libera la memoria del modelo actual sin crear uno nuevo
     *
     * ¿CUÁNDO SE USA?
     * - Cuando cambias los parámetros (umbral, threads, etc)
     * - El siguiente detect() automáticamente recargará el modelo
     * - Esto es importante para GPU: debe inicializarse en el hilo que la usa
     */
    fun clearObjectDetector() {
        Log.d("ObjectDetector", "🧹 Limpiando modelo anterior...")
        objectDetector = null
    }


    // =====================================================
    // MÉTODO 2: Configurar y cargar el modelo TFLite
    // =====================================================

    /**
     * Este es el método MÁS IMPORTANTE de esta clase.
     *
     * Lo que hace:
     * 1. Configura opciones del detector (umbral, resultados, etc)
     * 2. Configura opciones base (threads, hardware, etc)
     * 3. Selecciona qué CPU/GPU/NNAPI usar
     * 4. Carga el archivo .tflite desde la carpeta assets/
     * 5. Crea la instancia del detector
     *
     * Se llama:
     * - Al crear la clase (en init {})
     * - Cuando limpias el modelo (clearObjectDetector())
     * - Automáticamente en detect() si el modelo es nulo
     */
    fun setupObjectDetector() {

        // ───────────────────────────────────────────────────────────
        // PASO 1: Crear un builder para las opciones del DETECTOR
        // ───────────────────────────────────────────────────────────

        val optionsBuilder =
            ObjectDetector.ObjectDetectorOptions.builder()

                // Establece el umbral de confianza
                // Si un objeto tiene confianza < threshold, se descarta
                .setScoreThreshold(threshold)
                // Ejemplo: threshold = 0.5
                //   - Objeto con confianza 0.8 ✅ Se incluye
                //   - Objeto con confianza 0.3 ❌ Se rechaza

                // Establece el número máximo de objetos a detectar
                .setMaxResults(maxResults)
        // Ejemplo: maxResults = 3
        //   - Detecta el 1er, 2do y 3er objeto más probable
        //   - Descarta el resto aunque sean confiables

        // ───────────────────────────────────────────────────────────
        // PASO 2: Crear un builder para las opciones BASE (hardware)
        // ───────────────────────────────────────────────────────────

        val baseOptionsBuilder = BaseOptions.builder()
            .setNumThreads(numThreads)
        // Cuántos núcleos de CPU usar para procesar
        // 1 thread = usa 1 núcleo = lento pero eficiente en batería
        // 4 threads = usa 4 núcleos = rápido pero consume más batería

        // ───────────────────────────────────────────────────────────
        // PASO 3: Seleccionar DÓNDE se ejecuta el modelo
        // ───────────────────────────────────────────────────────────
        //
        // El modelo se puede ejecutar en:
        // - CPU: Siempre funciona, es el más lento
        // - GPU: Muy rápido, pero solo en algunos teléfonos
        // - NNAPI: Interfaz de Android, usa hardware especializado si existe

        when (currentDelegate) {

            DELEGATE_CPU -> {
                // CPU es la opción por defecto (no hay que hacer nada)
                // Todos los teléfonos tienen CPU
                Log.d("ObjectDetector", "📱 Usando: CPU (por defecto)")
                /* No hace falta hacer nada, CPU es la opción predeterminada */
            }

            DELEGATE_GPU -> {
                // GPU es mucho más rápido que CPU, pero no todos los teléfonos la soportan
                if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                    // isDelegateSupportedOnThisDevice = ¿este teléfono soporta GPU?
                    baseOptionsBuilder.useGpu()
                    Log.d("ObjectDetector", "🎮 Usando: GPU (procesador gráfico)")
                } else {
                    // Si el teléfono no soporta GPU, usa CPU en su lugar
                    Log.w("ObjectDetector", "⚠️ GPU no soportada, usando CPU")
                }
            }

            DELEGATE_NNAPI -> {
                // NNAPI = Neural Networks API (interfaz de IA de Android)
                // Usa hardware especializado si existe, sino usa CPU
                baseOptionsBuilder.useNnapi()
                Log.d("ObjectDetector", "⚡ Usando: NNAPI (hardware de IA)")
            }
        }

        // Agrega las opciones base al builder de opciones
        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        // ───────────────────────────────────────────────────────────
        // PASO 4: Cargar el archivo .tflite desde assets/
        // ───────────────────────────────────────────────────────────

        try {
            Log.d("ObjectDetector", "📂 Cargando modelo '$modelName' desde assets...")

            // FileUtil.loadMappedFile() lee el archivo desde la carpeta assets/
            // lo mapea en memoria de forma eficiente
            // Retorna un ByteBuffer con los datos del modelo
            val modelBuffer = FileUtil.loadMappedFile(context, modelName)

            // Crea la instancia del detector usando:
            // - El archivo del modelo (modelBuffer)
            // - Las opciones que configuramos arriba
            objectDetector =
                ObjectDetector.createFromBufferAndOptions(modelBuffer, optionsBuilder.build())

            Log.d("TFLite", "✅ ¡ÉXITO! Modelo '$modelName' cargado correctamente.")

        } catch (e: Exception) {
            // Si algo falla, notifica al listener
            // (que típicamente muestra un Toast al usuario)
            objectDetectorListener?.onError("Error al cargar modelo: ${e.message}")
            Log.e("TFLite", "❌ ERROR: El archivo sigue dando problemas: ${e.message}")
        }
    }


    // =====================================================
    // MÉTODO 3: Detectar objetos en una imagen (EL MÉTODO PRINCIPAL)
    // =====================================================

    /**
     * Este método se llama para CADA FRAME de la cámara (30+ veces por segundo)
     *
     * @param image: El bitmap (imagen) de la cámara
     * @param imageRotation: Cuántos grados está rotada la imagen
     *                       (porque el teléfono puede estar horizontal/vertical)
     *
     * FLUJO:
     * 1. Si el modelo no está cargado, lo carga
     * 2. Procesa la imagen (rota, normaliza, etc)
     * 3. Ejecuta la detección
     * 4. Mide el tiempo que tardó
     * 5. Notifica los resultados al listener
     */
    fun detect(image: Bitmap, imageRotation: Int) {

        // ───────────────────────────────────────────────────────────
        // SEGURIDAD: Si el modelo no existe, lo cargamos
        // ───────────────────────────────────────────────────────────

        if (objectDetector == null) {
            Log.w("ObjectDetector", "⚠️ Modelo no está cargado, cargando ahora...")
            setupObjectDetector()
        }

        // ───────────────────────────────────────────────────────────
        // PASO 1: Medir el tiempo de inicio
        // ───────────────────────────────────────────────────────────

        // SystemClock.uptimeMillis() retorna el tiempo en milisegundos desde que
        // el teléfono se encendió (sin contar cuando está dormido)
        var inferenceTime = SystemClock.uptimeMillis()
        // (inferenceTime es el tiempo que tarda la IA en procesar)

        // ───────────────────────────────────────────────────────────
        // PASO 2: PROCESAR LA IMAGEN (esto es CRÍTICO)
        // ───────────────────────────────────────────────────────────

        // ImageProcessor: Transforma la imagen para que el modelo la entienda
        //
        // ¿POR QUÉ ES NECESARIO?
        // El modelo se entrenó con imágenes en un formato específico.
        // Si la imagen no está en ese formato, la detección fallará.

        val imageProcessor = ImageProcessor.Builder()

            // Rot90Op = Operación de rotación de 90 grados
            // -imageRotation / 90 = calcula cuántas rotaciones de 90 grados se necesitan
            //
            // Ejemplo:
            // - imageRotation = 0° → -0/90 = 0 rotaciones
            // - imageRotation = 90° → -90/90 = -1 rotación (1 giro)
            // - imageRotation = 180° → -180/90 = -2 rotaciones (2 giros)
            // - imageRotation = 270° → -270/90 = -3 rotaciones (3 giros)
            //
            // El negativo (-) indica la dirección de la rotación
            .add(Rot90Op(-imageRotation / 90))
            .build()

        // ───────────────────────────────────────────────────────────
        // PASO 3: Convertir el Bitmap a TensorImage
        // ───────────────────────────────────────────────────────────

        // Bitmap: Imagen del teléfono en píxeles RGB
        // TensorImage: Tensor (matriz multidimensional) que el modelo entiende
        //
        // fromBitmap() convierte el Bitmap en TensorImage automáticamente
        val tensorImage = TensorImage.fromBitmap(image)

        // Aplicar el procesamiento (la rotación) a la imagen
        val processedImage = imageProcessor.process(tensorImage)

        // ───────────────────────────────────────────────────────────
        // PASO 4: EJECUTAR LA DETECCIÓN (aquí se usa la IA)
        // ───────────────────────────────────────────────────────────

        try {
            Log.d("ObjectDetector", "🔍 Ejecutando detección...")

            // objectDetector?.detect() ejecuta el modelo
            // El símbolo ? significa "si existe, ejecuta; si no, ignora"
            // Retorna una lista de Detection (objetos encontrados)
            val results = objectDetector?.detect(processedImage)

            // ───────────────────────────────────────────────────────────
            // PASO 5: Medir el tiempo que tardó
            // ───────────────────────────────────────────────────────────

            // Calcula cuántos milisegundos pasaron desde el inicio
            inferenceTime = SystemClock.uptimeMillis() - inferenceTime
            // Ejemplo: Si empezó en 1000ms y acabó en 1045ms
            //          inferenceTime = 1045 - 1000 = 45ms

            Log.d("ObjectDetector", "✅ Detección completada en ${inferenceTime}ms")

            // ───────────────────────────────────────────────────────────
            // PASO 6: Notificar los resultados al listener
            // ───────────────────────────────────────────────────────────

            // El listener (CameraFragment) está esperando estos resultados
            // para dibujar los cuadros en pantalla
            objectDetectorListener?.onResults(
                results,                           // Objetos detectados
                inferenceTime,                     // Tiempo que tardó (en ms)
                processedImage.height,             // Alto de la imagen procesada
                processedImage.width               // Ancho de la imagen procesada
            )

        } catch (e: Exception) {
            // Si algo falla durante la detección, registra el error
            Log.e("TFLite", "⚠️ Error en detect: ${e.message}")
        }
    }


    // =====================================================
    // INTERFACE: DetectorListener
    // =====================================================

    /**
     * Esta interface define los métodos que DEBE implementar quien quiera
     * escuchar los resultados del detector (en este caso, CameraFragment)
     *
     * Es como un "contrato": El detector promete llamar a estos métodos
     * cuando tenga algo importante que comunicar.
     */
    interface DetectorListener {

        /**
         * Se llama cuando ocurre un ERROR
         * @param error Descripción del error (ej: "Modelo no encontrado")
         */
        fun onError(error: String)

        /**
         * Se llama cuando la detección TERMINA EXITOSAMENTE
         * @param results Lista de objetos detectados
         * @param inferenceTime Tiempo que tardó la detección (en ms)
         * @param imageHeight Alto de la imagen procesada
         * @param imageWidth Ancho de la imagen procesada
         */
        fun onResults(
            results: MutableList<Detection>?,
            inferenceTime: Long,
            imageHeight: Int,
            imageWidth: Int
        )
    }


    // =====================================================
    // COMPANION OBJECT: Constantes compartidas
    // =====================================================

    /**
     * "companion object" = bloque estático
     *
     * Las constantes aquí son compartidas por TODAS las instancias
     * No pertenecen a una instancia específica, sino a la clase misma
     *
     * Se acceden así: ObjectDetectorHelper.DELEGATE_CPU (sin crear instancia)
     */
    companion object {

        // IDs para seleccionar qué hardware usar
        const val DELEGATE_CPU = 0      // Procesador central (todos los teléfonos)
        const val DELEGATE_GPU = 1      // Procesador gráfico (algunos teléfonos)
        const val DELEGATE_NNAPI = 2    // Hardware especializado de IA (Android 9+)
    }
}


// ========================================================================
// RESUMEN DEL CICLO DE VIDA
// ========================================================================
//
// 1. Constructor se ejecuta
//    └─ init { setupObjectDetector() } carga el modelo
//
// 2. CameraFragment llama a detect() para cada frame
//    └─ Procesa imagen
//    └─ Ejecuta modelo
//    └─ Notifica resultados
//
// 3. Usuario cambia parámetros (umbral, threads, etc)
//    └─ CameraFragment llama a clearObjectDetector()
//    └─ Siguiente detect() recargará el modelo con nuevos parámetros
//
// ========================================================================