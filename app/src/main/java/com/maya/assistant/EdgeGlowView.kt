package com.maya.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.View

class EdgeGlowView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cols = longArrayOf(0xFF00E5FF, 0xFF4F7CFF, 0xFFB04FFF, 0xFFFF4FD8, 0xFF00E5FF)
        .map { it.toInt() }.toIntArray()
    private var angle = 0f
    private val rect = RectF()
    private val m = Matrix()

    fun show(on: Boolean) {
        if (on) {
            visibility = VISIBLE
            animate().cancel()
            animate().alpha(1f).setDuration(350).start()
        } else {
            animate().cancel()
            animate().alpha(0f).setDuration(350).withEndAction { visibility = GONE }.start()
        }
    }

    override fun onDraw(cv: Canvas) {
        angle = (angle + 1.6f) % 360f
        val w = width.toFloat()
        val h = height.toFloat()
        val sh = SweepGradient(w / 2, h / 2, cols, null)
        m.setRotate(angle, w / 2, h / 2)
        sh.setLocalMatrix(m)
        p.shader = sh
        rect.set(0f, 0f, w, h)
        val rad = context.dp(34).toFloat()
        for (i in 6 downTo 0) {
            p.strokeWidth = context.dp(5) * (i + 1).toFloat()
            p.alpha = (230 / (i + 1)).coerceAtLeast(20)
            cv.drawRoundRect(rect, rad, rad, p)
        }
        postInvalidateOnAnimation()
    }
}
