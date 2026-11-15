package com.agenda.client

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * График динамики общего среднего.
 * Оформление: плавная кривая, градиентная заливка под ней, мягкое «свечение» вместо тени,
 * пунктирная сетка, анимация прорисовки слева направо и плашка со значением.
 * Нажатие или проведение пальцем по графику выбирает точку (по умолчанию показана последняя).
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Float> = emptyList()
    private var labels: List<String> = emptyList()
    private val density = resources.displayMetrics.density

    /** Доля уже «прорисованного» графика: 0..1 (анимация). */
    private var reveal = 1f
    private var animator: ValueAnimator? = null

    /** Выбранная пальцем точка; -1 = показываем последнюю. */
    private var selected = -1

    private val lineColor = ContextCompat.getColor(context, R.color.wyg_chart_line)
    private val surfaceColor = ContextCompat.getColor(context, R.color.wyg_surface)
    private val onLineColor = ContextCompat.getColor(context, R.color.wyg_on_primary)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        strokeWidth = 3f * density
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    // «Тень» под линией: две более широкие полупрозрачные линии, сдвинутые вниз.
    // Размытие (BlurMaskFilter) не используем: на Android 7–8 оно не работает при аппаратной отрисовке.
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = lineColor
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.wyg_chart_grid)
        strokeWidth = 1f * density
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.wyg_text_secondary)
        textSize = 11f * density
    }
    private val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.FILL
    }
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onLineColor
        textSize = 12f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val line = Path()
    private val area = Path()
    private val badgeRect = RectF()

    // Поля графика (в px). top больше прежнего: над верхней точкой помещается плашка.
    private val leftPad get() = 34f * density
    private val rightPad get() = 14f * density
    private val topPad get() = 32f * density
    private val bottomPad get() = 24f * density

    fun setData(values: List<Float>, labels: List<String>) {
        val changed = this.values != values
        this.values = values
        this.labels = labels
        selected = -1
        if (changed && values.size >= 2 && isAttachedToWindow) animateIn() else {
            animator?.cancel()
            reveal = 1f
            invalidate()
        }
    }

    private fun animateIn() {
        animator?.cancel()
        reveal = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                reveal = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel() // иначе аниматор держит ссылку на мёртвый экран
        animator = null
        reveal = 1f
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Градиент заливки: от полупрозрачного цвета линии у кривой к прозрачному у оси
        fillPaint.shader = LinearGradient(
            0f, topPad, 0f, h - bottomPad,
            ColorUtils.setAlphaComponent(lineColor, 110),
            ColorUtils.setAlphaComponent(lineColor, 0),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = values.size
        if (n < 2) return

        val left = leftPad
        val right = width - rightPad
        val top = topPad
        val bottom = height - bottomPad

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

        // Плавная кривая: касательные в точках горизонтальные, поэтому кривая не «перелетает» за значения
        line.reset()
        line.moveTo(xOf(0), yOf(values[0]))
        for (i in 1 until n) {
            val x0 = xOf(i - 1); val y0 = yOf(values[i - 1])
            val x1 = xOf(i); val y1 = yOf(values[i])
            val mid = (x0 + x1) / 2f
            line.cubicTo(mid, y0, mid, y1, x1, y1)
        }
        area.set(line)
        area.lineTo(xOf(n - 1), bottom)
        area.lineTo(xOf(0), bottom)
        area.close()

        // Всё, что ниже, рисуется только левее фронта анимации
        val save = canvas.save()
        val clipX = if (reveal >= 1f) width.toFloat() else left + (right - left) * reveal
        canvas.clipRect(0f, 0f, clipX, height.toFloat())

        canvas.drawPath(area, fillPaint)

        // «тень»: широкие бледные линии со сдвигом вниз
        canvas.save()
        canvas.translate(0f, 3f * density)
        glowPaint.strokeWidth = 11f * density
        glowPaint.alpha = 28
        canvas.drawPath(line, glowPaint)
        glowPaint.strokeWidth = 7f * density
        glowPaint.alpha = 45
        canvas.drawPath(line, glowPaint)
        canvas.restore()

        canvas.drawPath(line, linePaint)

        // точки: при большом числе оценок рисуем только выбранную/последнюю, иначе график превращается в бусы
        val focus = if (selected in 0 until n) selected else n - 1
        if (n <= 24) {
            for (i in 0 until n) {
                if (i == focus) continue
                dotPaint.color = surfaceColor
                canvas.drawCircle(xOf(i), yOf(values[i]), 4.5f * density, dotPaint)
                dotPaint.color = lineColor
                canvas.drawCircle(xOf(i), yOf(values[i]), 3f * density, dotPaint)
            }
        }
        // выделенная точка: кольцо цвета поверхности и крупная серединка
        val fx = xOf(focus)
        val fy = yOf(values[focus])
        dotPaint.color = ColorUtils.setAlphaComponent(lineColor, 60)
        canvas.drawCircle(fx, fy, 10f * density, dotPaint)
        dotPaint.color = surfaceColor
        canvas.drawCircle(fx, fy, 6.5f * density, dotPaint)
        dotPaint.color = lineColor
        canvas.drawCircle(fx, fy, 4.5f * density, dotPaint)

        canvas.restoreToCount(save)

        // плашка со значением (когда анимация закончилась)
        if (reveal >= 1f) {
            val text = buildString {
                append(String.format(Locale.US, "%.2f", values[focus]))
                val label = labels.getOrNull(focus)
                if (selected >= 0 && !label.isNullOrEmpty()) append("  ·  ").append(label)
            }
            val padH = 8f * density
            val w = badgeText.measureText(text) + padH * 2
            val h = 22f * density
            val bx = (fx - w / 2f).coerceIn(2f * density, width - w - 2f * density)
            var by = fy - 16f * density - h
            if (by < 2f * density) by = fy + 16f * density // сверху нет места: ставим под точку
            badgeRect.set(bx, by, bx + w, by + h)
            canvas.drawRoundRect(badgeRect, h / 2f, h / 2f, badgeBg)
            canvas.drawText(text, badgeRect.centerX(), badgeRect.centerY() + 4.2f * density, badgeText)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val n = values.size
        if (n < 2 || reveal < 1f) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val left = leftPad
                val right = width - rightPad
                val idx = (((event.x - left) / (right - left)) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                if (idx != selected) {
                    selected = idx
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick() // для доступности (TalkBack)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}
