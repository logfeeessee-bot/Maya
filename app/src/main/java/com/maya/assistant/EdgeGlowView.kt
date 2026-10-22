package com.maya.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.View

/** Mic ON → sirf mobile ke charon kinaron par ek rang ki ghumti light. Beech mein kuch nahi. */
class EdgeGlowView(c: Context) : View(c) {
    var color = 0xFF00E5FF.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var angle = 0f
    private val rect = RectF()
    private val m = Matrix()
    private val widths = intArrayOf(28, 21, 15, 10, 6, 3)       // dp (aadha hissa hi screen ke andar dikhta hai)
    private val alphas = intArrayOf(25, 45, 80, 130, 200, 255)

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
        angle = (angle + 1.4f) % 360f
        val w = width.toFloat()
        val h = height.toFloat()
        val dim = (color and 0x00FFFFFF) or (0x70 shl 24)
        val sh = SweepGradient(w / 2, h / 2, intArrayOf(dim, color, dim, dim), floatArrayOf(0f, 0.2f, 0.5f, 1f))
        m.setRotate(angle, w / 2, h / 2)
        sh.setLocalMatrix(m)
        p.shader = sh
        rect.set(0f, 0f, w, h)
        val rad = context.dp(36).toFloat()
        for (i in widths.indices) {
            p.strokeWidth = context.dp(widths[i]).toFloat()
            p.alpha = alphas[i]
            cv.drawRoundRect(rect, rad, rad, p)
        }
        postInvalidateOnAnimation()
    }
}
