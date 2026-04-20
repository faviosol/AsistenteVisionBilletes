package com.faviosol.vision.de.billetes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.faviosol.vision.de.billetes.R
import java.util.LinkedList
import kotlin.math.max
import org.tensorflow.lite.task.vision.detector.Detection

/**
 * Vista personalizada que se dibuja encima de la vista previa de la camara.
 *
 * Su unica responsabilidad es dibujar los cuadros de deteccion (bounding boxes)
 * y las etiquetas con la denominacion y el porcentaje de confianza sobre cada
 * billete detectado.
 *
 * Se actualiza llamando a [setResults] con la lista de detecciones, seguido de
 * [invalidate] para forzar el redibujo del Canvas.
 */
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    // =====================================================
    // VARIABLES DE DIBUJO
    // =====================================================

    // Lista de detecciones recibidas desde CameraFragment
    private var results: List<Detection> = LinkedList<Detection>()

    // Pincel para dibujar el borde del cuadro de deteccion
    private var boxPaint = Paint()

    // Pincel para el fondo negro detras del texto de la etiqueta
    private var textBackgroundPaint = Paint()

    // Pincel para el texto blanco de la etiqueta
    private var textPaint = Paint()

    // Factor de escala para convertir coordenadas del modelo a coordenadas de pantalla
    private var scaleFactor: Float = 1f

    // Rectangulo auxiliar para medir el ancho y alto del texto antes de dibujarlo
    private var bounds = Rect()

    init {
        initPaints()
    }

    // =====================================================
    // METODOS PUBLICOS
    // =====================================================

    /**
     * Limpia la pantalla reseteando todos los pinceles y forzando un redibujo vacio.
     * Se llama desde CameraFragment cuando no hay detecciones nuevas.
     */
    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    // =====================================================
    // METODOS PRIVADOS
    // =====================================================

    /**
     * Configura los colores, estilos y tamanios de los tres pinceles usados para dibujar.
     */
    private fun initPaints() {
        // Fondo negro solido detras de cada etiqueta de texto
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        // Texto blanco con la denominacion y confianza del billete
        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        // Borde del cuadro de deteccion (color definido en res/values/colors.xml)
        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    // =====================================================
    // DIBUJO EN PANTALLA
    // =====================================================

    /**
     * Se llama automaticamente por Android cada vez que se invoca [invalidate].
     * Recorre todas las detecciones y dibuja el cuadro + etiqueta de cada una.
     */
    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        for (result in results) {
            val boundingBox = result.boundingBox

            // Escalar las coordenadas del modelo al tamanio real de la pantalla
            val top    = boundingBox.top    * scaleFactor
            val bottom = boundingBox.bottom * scaleFactor
            val left   = boundingBox.left   * scaleFactor
            val right  = boundingBox.right  * scaleFactor

            // Dibujar el rectangulo de deteccion alrededor del billete
            val drawableRect = RectF(left, top, right, bottom)
            canvas.drawRect(drawableRect, boxPaint)

            // Armar el texto: "denominacion confianza" — ej: "100_soles 0.95"
            val drawableText =
                result.categories[0].label + " " +
                        String.format("%.2f", result.categories[0].score)

            // Medir el texto para saber cuanto espacio ocupa el fondo negro
            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth  = bounds.width()
            val textHeight = bounds.height()

            // Dibujar el fondo negro detras del texto
            canvas.drawRect(
                left,
                top,
                left + textWidth  + BOUNDING_RECT_TEXT_PADDING,
                top  + textHeight + BOUNDING_RECT_TEXT_PADDING,
                textBackgroundPaint
            )

            // Dibujar el texto encima del fondo negro
            canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
        }
    }

    // =====================================================
    // ACTUALIZACION DE RESULTADOS
    // =====================================================

    /**
     * Recibe la lista de detecciones desde CameraFragment y calcula el factor de escala.
     *
     * La PreviewView usa el modo FILL_START, por eso es necesario escalar las coordenadas
     * del modelo (que vienen en pixeles de la imagen capturada) al tamanio en pantalla.
     *
     * @param detectionResults Lista de objetos detectados por TensorFlow Lite
     * @param imageHeight      Alto de la imagen analizada por el modelo (en pixeles)
     * @param imageWidth       Ancho de la imagen analizada por el modelo (en pixeles)
     */
    fun setResults(
        detectionResults: MutableList<Detection>,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = detectionResults

        // Tomar el mayor factor para que los cuadros cubran bien la imagen en pantalla
        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
    }

    companion object {
        // Relleno extra alrededor del texto para que el fondo negro no quede ajustado
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}
