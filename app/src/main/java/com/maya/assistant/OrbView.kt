package com.maya.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ek rang ka particle gola.
 * mode 1 = Maya sun rahi hai  → CHHOTA gola, dheere ghumta hai
 * mode 2 = Maya bol rahi hai  → particles charon taraf se aakar BADA gola ban jaate hain
 * Maya chup hote hi → wapas chhota ho kar ghumne lagta hai
 */
class OrbView(c: Context) : View(c) {
    var mode = 1
    private val n = 190
    private val th = FloatArray(n) { Random.nextFloat() * 6.2832f }
    private val ph = FloatArray(n) { acos(1 - 2 * Random.nextFloat()) }
    private val sz = FloatArray(n) { 1.6f + Random.nextFloat() * 2.6f }
    private val rnd = FloatArray(n) { Random.nextFloat() }
    private val mix = FloatArray(n) { Random.nextFloat() * 0.55f }
    private val cols = IntArray(n)
    private var base = 0xFFFF4FA3.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rot = 0f
    private var t = 0f
    private var scale = 0.5f

    init { setColor(base) }

    fun setColor(c: Int) {
        base = c
        for (i in 0 until n) cols[i] = ColorUtils.blendARGB(c, Color.WHITE, mix[i])
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val target = if (mode == 2) 1f else 0.5f
        scale += (target - scale) * 0.07f
        val spread = abs(target - scale) * 2.2f          // badalte waqt particles bikhre dikhte hain
        rot += if (mode == 2) 0.05f else 0.02f
        t += 0.03f
        val pulse = 1f + if (mode == 2) 0.08f * sin(t * 6) else 0.03f * sin(t)
        val r = min(cx, cy) * 0.8f * scale * pulse

        p.shader = RadialGradient(cx, cy, r * 1.5f,
            intArrayOf((base and 0x00FFFFFF) or 0x55000000, base and 0x00FFFFFF),
            null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 1.5f, p)
        p.shader = null

        for (i in 0 until n) {
            val a = th[i] + rot * (1f + (i % 3) * 0.45f)
            val x = sin(ph[i]) * cos(a)
            val z = sin(ph[i]) * sin(a)
            val y = cos(ph[i])
            val s = r * (1f + spread * rnd[i] * 1.2f)
            val depth = (z + 1f) / 2f
            p.color = cols[i]
            p.alpha = (70 + 185 * depth).toInt()
            cv.drawCircle(cx + x * s, cy + y * s, sz[i] * (0.6f + depth), p)
        }
        postInvalidateOnAnimation()
    }
}
