package com.faviosol.vision.de.billetes.ui

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

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var boxPaint           = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint          = Paint()
    private var bounds             = Rect()

    init {
        initPaints()
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color    = Color.BLACK
        textBackgroundPaint.style    = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color    = Color.WHITE
        textPaint.style    = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color       = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8F
        boxPaint.style       = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}
