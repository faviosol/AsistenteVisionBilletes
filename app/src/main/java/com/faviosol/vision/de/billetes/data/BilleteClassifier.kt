package com.faviosol.vision.de.billetes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import com.faviosol.vision.de.billetes.domain.model.BilleteDeteccion
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BilleteClassifier(
    var threshold: Float = 0.5f,
    var numThreads: Int = 2,
    var maxResults: Int = 1,
    var currentDelegate: Int = 0,
    val context: Context,
    val listener: ClasificadorListener?
) {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    private val modelName  = "billetes_v3.tflite"
    private val labelsName = "labels.txt"
    private val inputSize  = 224

    init {
        configurar()
    }

    fun limpiar() {
        interpreter?.close()
        interpreter = null
    }

    fun configurar() {
        try {
            labels = context.assets.open(labelsName).bufferedReader().readLines()
            val modelBuffer = FileUtil.loadMappedFile(context, modelName)
            val options = Interpreter.Options().apply { numThreads = this@BilleteClassifier.numThreads }
            interpreter = Interpreter(modelBuffer, options)
            Log.d("BilleteClassifier", "Modelo cargado con ${labels.size} clases.")
        } catch (e: Exception) {
            listener?.onError("Error al cargar modelo: ${e.message}")
            Log.e("BilleteClassifier", "Error: ${e.message}")
        }
    }

    private fun tieneFormaBillete(bitmap: Bitmap): Boolean {
        val W = 64; val H = 64
        val mini = Bitmap.createScaledBitmap(bitmap, W, H, false)
        val pixeles = IntArray(W * H)
        mini.getPixels(pixeles, 0, W, 0, 0, W, H)
        mini.recycle()

        val gris = IntArray(W * H) { i ->
            val p = pixeles[i]
            ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
        }

        // Varianza global: descarta fondos totalmente planos (pared, mesa sin nada)
        val media = gris.average()
        val varianza = gris.sumOf { (it - media).let { d -> d * d } } / gris.size
        Log.d("Prefiltro", "varianza=%.1f".format(varianza))
        return varianza >= 200.0
    }

    fun clasificar(imagen: Bitmap, rotacion: Int) {
        if (interpreter == null) configurar()

        val tiempoInicio = SystemClock.uptimeMillis()

        if (!tieneFormaBillete(imagen)) {
            Log.d("BilleteClassifier", "Prefiltro rechazó el frame")
            listener?.onResultado(BilleteDeteccion("ninguno", 0f, SystemClock.uptimeMillis() - tiempoInicio))
            return
        }

        try {
            val rotada = if (rotacion != 0) {
                val m = Matrix().apply { postRotate(rotacion.toFloat()) }
                Bitmap.createBitmap(imagen, 0, 0, imagen.width, imagen.height, m, true)
            } else imagen

            val redimensionada = Bitmap.createScaledBitmap(rotada, inputSize, inputSize, true)

            val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
            inputBuffer.order(ByteOrder.nativeOrder())
            val pixeles = IntArray(inputSize * inputSize)
            redimensionada.getPixels(pixeles, 0, inputSize, 0, 0, inputSize, inputSize)
            for (pixel in pixeles) {
                inputBuffer.putFloat((pixel shr 16 and 0xFF) / 255.0f)
                inputBuffer.putFloat((pixel shr 8  and 0xFF) / 255.0f)
                inputBuffer.putFloat((pixel        and 0xFF) / 255.0f)
            }

            val outputBuffer = Array(1) { FloatArray(labels.size) }
            interpreter?.run(inputBuffer, outputBuffer)

            val tiempoInferencia = SystemClock.uptimeMillis() - tiempoInicio
            val probs  = outputBuffer[0]
            val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: return
            val etiqueta = if (maxIdx < labels.size) labels[maxIdx] else "desconocido"

            Log.d("BilleteClassifier", "etiqueta=$etiqueta puntaje=%.2f".format(probs[maxIdx]))
            listener?.onResultado(BilleteDeteccion(etiqueta, probs[maxIdx], tiempoInferencia))

        } catch (e: Exception) {
            Log.e("BilleteClassifier", "Error en clasificar: ${e.message}")
        }
    }

    interface ClasificadorListener {
        fun onError(error: String)
        fun onResultado(deteccion: BilleteDeteccion)
    }

    companion object {
        const val DELEGATE_CPU   = 0
        const val DELEGATE_GPU   = 1
        const val DELEGATE_NNAPI = 2
    }
}
