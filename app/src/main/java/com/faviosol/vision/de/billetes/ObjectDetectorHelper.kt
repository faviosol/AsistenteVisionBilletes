package com.faviosol.vision.de.billetes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ObjectDetectorHelper(
    var threshold: Float = 0.8f,
    var numThreads: Int = 2,
    var maxResults: Int = 1,
    var currentDelegate: Int = 0,
    val context: Context,
    val objectDetectorListener: DetectorListener?
) {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    private val modelName  = "billetes_v2.tflite"
    private val labelsName = "labels.txt"
    private val inputSize  = 224

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        interpreter?.close()
        interpreter = null
    }

    fun setupObjectDetector() {
        try {
            labels = context.assets.open(labelsName).bufferedReader().readLines()
            val modelBuffer = FileUtil.loadMappedFile(context, modelName)
            val options = Interpreter.Options().apply { numThreads = this@ObjectDetectorHelper.numThreads }
            interpreter = Interpreter(modelBuffer, options)
            Log.d("TFLite", "Modelo '$modelName' cargado con ${labels.size} clases.")
        } catch (e: Exception) {
            objectDetectorListener?.onError("Error al cargar modelo: ${e.message}")
            Log.e("TFLite", "Error cargando modelo: ${e.message}")
        }
    }

    fun detect(image: Bitmap, imageRotation: Int) {
        if (interpreter == null) setupObjectDetector()

        var inferenceTime = SystemClock.uptimeMillis()

        try {
            val rotated = if (imageRotation != 0) {
                val m = Matrix().apply { postRotate(imageRotation.toFloat()) }
                Bitmap.createBitmap(image, 0, 0, image.width, image.height, m, true)
            } else image

            val resized = Bitmap.createScaledBitmap(rotated, inputSize, inputSize, true)

            val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
            inputBuffer.order(ByteOrder.nativeOrder())
            val pixels = IntArray(inputSize * inputSize)
            resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
            for (pixel in pixels) {
                inputBuffer.putFloat((pixel shr 16 and 0xFF) / 255.0f)
                inputBuffer.putFloat((pixel shr 8  and 0xFF) / 255.0f)
                inputBuffer.putFloat((pixel        and 0xFF) / 255.0f)
            }

            val outputBuffer = Array(1) { FloatArray(labels.size) }
            interpreter?.run(inputBuffer, outputBuffer)

            inferenceTime = SystemClock.uptimeMillis() - inferenceTime

            val probs  = outputBuffer[0]
            val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: return
            val score  = probs[maxIdx]
            val label  = if (maxIdx < labels.size) labels[maxIdx] else "desconocido"

            objectDetectorListener?.onResults(label, score, inferenceTime)

        } catch (e: Exception) {
            Log.e("TFLite", "Error en detect: ${e.message}")
        }
    }

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(label: String, score: Float, inferenceTime: Long)
    }

    companion object {
        const val DELEGATE_CPU   = 0
        const val DELEGATE_GPU   = 1
        const val DELEGATE_NNAPI = 2
    }
}
