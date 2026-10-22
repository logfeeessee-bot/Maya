package com.maya.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** mode: 0 = shaant, 1 = sun rahi hai, 2 = bol rahi hai (alag-alag rang mein ghumta hai) */
class OrbView(c: Context) : View(c) {
    var mode = 0
    private val n = 170
    private val th = FloatArray(n) { Random.nextFloat() * 6.2832f }
    private val ph = FloatArray(n) { acos(1 - 2 * Random.nextFloat()) }
    private val sz = FloatArray(n) { 1.6f + Random.nextFloat() * 2.6f }
    private val pal = longArrayOf(0xFFFF8A00, 0xFFFFD60A, 0xFF7CFF4F, 0xFFFF4FA3, 0xFF00E5C0)
        .map { it.toInt() }.toIntArray()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rot = 0f
    private var t = 0f

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) * 0.78f
        rot += when (mode) { 2 -> 0.06f; 1 -> 0.025f; else -> 0.01f }
        t += 0.03f
        val pulse = 1f + if (mode == 2) 0.12f * sin(t * 6) else 0.04f * sin(t)

        p.shader = RadialGradient(cx, cy, r * 1.35f, intArrayOf(0x44FFA040, 0x00000000), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 1.35f, p)
        p.shader = null

        val colorShift = (t * if (mode == 2) 8 else 1).toInt()
        for (i in 0 until n) {
            val wob = if (mode == 2) 1f + 0.22f * sin(t * 5 + i) else 1f
            val a = th[i] + rot * (1f + (i % 3) * 0.45f)
            val x = sin(ph[i]) * cos(a)
            val z = sin(ph[i]) * sin(a)
            val y = cos(ph[i])
            val s = r * pulse * wob
            val depth = (z + 1f) / 2f
            p.color = pal[(i + colorShift) % pal.size]
            p.alpha = (70 + 185 * depth).toInt()
            cv.drawCircle(cx + x * s, cy + y * s, sz[i] * (0.6f + depth), p)
        }
        postInvalidateOnAnimation()
    }
}
