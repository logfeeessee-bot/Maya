package com.maya.assistant

import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar

class HomeActivity : AppCompatActivity() {

    private val BG = 0xFF0B1220.toInt()
    private val BLUE = 0xFF4F7CFF.toInt()
    private val GRAY = 0xFF8C97B2.toInt()
    private val LINE = 0xFF2A3550.toInt()
    private var orb: OrbView? = null          // sirf tab, jab "Display over other apps" permission nahi hai
    private lateinit var mic: TextView
    private var built = false

    private val listener: (Int) -> Unit = { s ->
        runOnUiThread {
            if (built) {
                orb?.let {
                    it.setColor(Prefs.orb(this))
                    it.mode = s
                    it.visibility = if (s == 0) View.GONE else View.VISIBLE
                }
                mic.text = if (s == 0) "🎤" else "⏹"
                val col = if (s == 0) BLUE else 0xFFFF4F6D.toInt()
                mic.background = roundBg(col, 40, col, this)
            }
        }
    }

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        if (ContextCompat.checkSelfPermission(this, RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            getSharedPreferences("maya", MODE_PRIVATE).edit().putBoolean("setup_done", false).apply()
            startActivity(Intent(this, SetupActivity::class.java)); finish(); return
        }
        startForegroundService(Intent(this, MayaService::class.java))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            fitsSystemWindows = true
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }

        // Top bar  (☰ dabao → Settings)
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(tv("☰", 26f, Color.WHITE).apply {
            setPadding(dp(8), dp(8), dp(16), dp(8))
            setOnClickListener { startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
        })
        top.addView(tv("Maya", 26f, Color.WHITE, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(tv("🔔", 22f, Color.WHITE))
        root.addView(top)

        // Greeting
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val g = when { h < 12 -> "Good morning,"; h < 17 -> "Good afternoon,"; else -> "Good evening," }
        root.addView(tv(g, 24f, GRAY, true).apply { setPadding(0, dp(18), 0, 0) })
        root.addView(tv("there", 34f, Color.WHITE, true))
        root.addView(tv("Maya is ready to help you.", 16f, GRAY).apply { setPadding(0, dp(4), 0, 0) })

        // Beech ka hissa: M.A.Y.A likha hua
        val frame = FrameLayout(this)
        if (!Settings.canDrawOverlays(this)) {
            orb = OrbView(this).also {
                it.visibility = View.GONE
                frame.addView(it, FrameLayout.LayoutParams(-1, -1))
            }
        }
        val center = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        center.addView(tv("AI ASSISTANT", 12f, GRAY).apply { gravity = Gravity.CENTER })
        center.addView(tv("M.A.Y.A", 44f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        center.addView(tv("HOW CAN I HELP YOU?", 11f, GRAY).apply { gravity = Gravity.CENTER })
        frame.addView(center, FrameLayout.LayoutParams(-1, -1))
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))

        // Chips
        val chips = LinearLayout(this)
        listOf("🎵 Music", "📖 Study", "✏ Journal").forEach { c ->
            chips.addView(tv(c, 16f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                background = roundBg(0xFF111A2C.toInt(), 22, LINE, this@HomeActivity)
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener { Toast.makeText(this@HomeActivity, "Jaldi aa raha hai", Toast.LENGTH_SHORT).show() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        }
        root.addView(chips)

        // Input row
        val input = EditText(this).apply {
            hint = "Ask Maya anything…"; setHintTextColor(GRAY); setTextColor(Color.WHITE)
            background = null; setSingleLine()
        }
        val send = tv("➤", 22f, Color.WHITE).apply {
            setOnClickListener {
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    startService(Intent(this@HomeActivity, MayaService::class.java)
                        .setAction(MayaService.ACTION_TEXT).putExtra("q", q))
                    input.setText("")
                }
            }
        }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = roundBg(0xFF111A2C.toInt(), 28, LINE, this@HomeActivity)
            setPadding(dp(18), dp(4), dp(18), dp(4))
        }
        bar.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(send)
        root.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // Bottom nav + bada mic
        mic = tv("🎤", 30f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = roundBg(BLUE, 40, BLUE, this@HomeActivity)
            setOnClickListener {
                startService(Intent(this@HomeActivity, MayaService::class.java).setAction(MayaService.ACTION_TOGGLE))
            }
        }
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun navItem(t: String) = tv(t, 14f, GRAY, true).apply { gravity = Gravity.CENTER }
        nav.addView(navItem("🏠\nHome"), LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navItem("📷\nScan"), LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(mic, LinearLayout.LayoutParams(dp(80), dp(80)))
        nav.addView(navItem("🧠\nMemories"), LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navItem("💬\nChat"), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        setContentView(root)
        built = true
    }

    override fun onStart() {
        super.onStart()
        if (built) { MayaService.listeners.add(listener); listener(MayaService.state) }
    }

    override fun onStop() {
        super.onStop()
        MayaService.listeners.remove(listener)
    }
}
