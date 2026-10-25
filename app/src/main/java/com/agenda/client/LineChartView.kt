package com.agenda.client

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.floor

/**
 * Простой линейный график, нарисованный вручную (без сторонних библиотек).
 * Точки стоят через равные промежутки в порядке оценок (по датам), а не по календарю.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Float> = emptyList()
    private var labels: List<String> = emptyList()
    private val density = resources.displayMetrics.density

    private val lineColor = ContextCompat.getColor(context, R.color.wyg_chart_line)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        strokeWidth = 2.5f * density
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.wyg_chart_grid)
        strokeWidth = 1f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.wyg_text_secondary)
        textSize = 11f * density
    }

    fun setData(values: List<Float>, labels: List<String>) {
        this.values = values
        this.labels = labels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = values.size
        if (n < 2) return

        val left = 34f * density
        val right = width - 12f * density
        val top = 12f * density
        val bottom = height - 24f * density

        // Ось Y: от (минимум - 1) до 10, чтобы изменения были заметны
        val minValue = values.minOrNull() ?: 1f
        val lo = (floor(minValue.toDouble()).toFloat() - 1f).coerceAtLeast(1f)
        val hi = 10f

        fun yOf(v: Float): Float = bottom - (v - lo) / (hi - lo) * (bottom - top)
        fun xOf(i: Int): Float = left + (right - left) * i / (n - 1)

        // сетка и подписи оси Y
        textPaint.textAlign = Paint.Align.LEFT
        for (g in lo.toInt()..10) {
            val y = yOf(g.toFloat())
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(g.toString(), 4f * density, y + 4f * density, textPaint)
        }

        // линия
        val path = Path()
        for (i in 0 until n) {
            val x = xOf(i)
            val y = yOf(values[i])
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)

        // точки
        for (i in 0 until n) {
            canvas.drawCircle(xOf(i), yOf(values[i]), 3.5f * density, dotPaint)
        }

        // подписи оси X: первая, последняя и (если точек много) средняя
        val labelY = height - 6f * density
        if (labels.size == n) {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(labels[0], left, labelY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(labels[n - 1], right, labelY, textPaint)
            if (n >= 5) {
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(labels[n / 2], xOf(n / 2), labelY, textPaint)
            }
        }
    }
}
