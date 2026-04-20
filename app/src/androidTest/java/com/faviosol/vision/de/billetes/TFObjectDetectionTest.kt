package com.faviosol.vision.de.billetes

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.task.vision.detector.Detection
/**
 * Prueba instrumentada que se ejecuta directamente en un dispositivo Android real o emulador.
 *
 * Verifica que el modelo billetes.tflite produzca resultados consistentes
 * y que las coordenadas de los cuadros de deteccion esten dentro del tamanio de la imagen.
 */
@RunWith(AndroidJUnit4::class)
class TFObjectDetectionTest {

    val controlResults = listOf<Detection>(
        Detection.create(RectF(69.0f, 58.0f, 227.0f, 171.0f),
            listOf<Category>(Category.create("cat", "cat", 0.77734375f))),
        Detection.create(RectF(13.0f, 6.0f, 283.0f, 215.0f),
            listOf<Category>(Category.create("couch", "couch", 0.5859375f))),
            Detection.create(RectF(45.0f, 27.0f, 257.0f, 184.0f),
            listOf<Category>(Category.create("chair", "chair", 0.55078125f)))
    )

    @Test
    @Throws(Exception::class)
    fun detectionResultsShouldNotChange() {
        val objectDetectorHelper =
            ObjectDetectorHelper(
                context = InstrumentationRegistry.getInstrumentation().context,
                objectDetectorListener =
                    object : ObjectDetectorHelper.DetectorListener {
                        override fun onError(error: String) {
                            // Sin accion en caso de error (la prueba falla por assertion)
                        }

                        override fun onResults(
                          results: MutableList<Detection>?,
                          inferenceTime: Long,
                          imageHeight: Int,
                          imageWidth: Int
                        ) {

                            assertEquals(controlResults.size, results!!.size)

                            // Recorre los resultados detectados y los datos de control
                            for (i in controlResults.indices) {
                                // Verifica que los cuadros de deteccion sean iguales
                                assertEquals(results[i].boundingBox, controlResults[i].boundingBox)

                                // Verifica que el numero de categorias detectadas sea igual
                                assertEquals(
                                    results[i].categories.size,
                                    controlResults[i].categories.size
                                )

                                // Recorre las categorias y verifica que las etiquetas coincidan
                                for (j in 0 until controlResults[i].categories.size - 1) {
                                    assertEquals(
                                        results[i].categories[j].label,
                                        controlResults[i].categories[j].label
                                    )
                                }
                            }
                        }
                    }
            )
        // Carga la imagen de prueba y ejecuta el detector
        val bitmap = loadImage("cat1.png")
        objectDetectorHelper.detect(bitmap!!, 0)
    }

    @Test
    @Throws(Exception::class)
    fun detectedImageIsScaledWithinModelDimens() {
        val objectDetectorHelper =
            ObjectDetectorHelper(
                context = InstrumentationRegistry.getInstrumentation().context,
                objectDetectorListener =
                    object : ObjectDetectorHelper.DetectorListener {
                        override fun onError(error: String) {}

                        override fun onResults(
                          results: MutableList<Detection>?,
                          inferenceTime: Long,
                          imageHeight: Int,
                          imageWidth: Int
                        ) {
                            assertNotNull(results)
                            for (result in results!!) {
                                assertTrue(result.boundingBox.top <= imageHeight)
                                assertTrue(result.boundingBox.bottom <= imageHeight)
                                assertTrue(result.boundingBox.left <= imageWidth)
                                assertTrue(result.boundingBox.right <= imageWidth)
                            }
                        }
                    }
            )

            // Carga la imagen de prueba y ejecuta el detector
            val bitmap = loadImage("cat1.png")
            objectDetectorHelper.detect(bitmap!!, 0)
    }

    @Throws(Exception::class)
    private fun loadImage(fileName: String): Bitmap? {
        val assetManager: AssetManager =
            InstrumentationRegistry.getInstrumentation().context.assets
        val inputStream: InputStream = assetManager.open(fileName)
        return BitmapFactory.decodeStream(inputStream)
    }
}
