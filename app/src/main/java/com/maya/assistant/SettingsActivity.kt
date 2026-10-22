package com.maya.assistant

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val BG = 0xFF0B1220.toInt()
    private val CARD = 0xFF151D2E.toInt()
    private val LINE = 0xFF2A3550.toInt()
    private val BLUE = 0xFF4F7CFF.toInt()
    private val GRAY = 0xFF8C97B2.toInt()

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun label(t: String) = tv(t, 18f, Color.WHITE, true).apply { setPadding(0, dp(24), 0, dp(6)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(40))
        }
        col.addView(tv("Settings", 28f, Color.WHITE, true))

        // ---------- Groq key ----------
        col.addView(label("Groq AI key (free)"))
        col.addView(tv("console.groq.com/keys par banao aur yahan paste karo. Kisi ko mat dikhana, GitHub par mat daalna.", 13f, GRAY))
        val key = EditText(this).apply {
            setText(Prefs.key(this@SettingsActivity))
            hint = "gsk_..."
            setHintTextColor(GRAY); setTextColor(Color.WHITE); setSingleLine()
            background = roundBg(CARD, 14, LINE, this@SettingsActivity)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        col.addView(key, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        col.addView(tv("Save", 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundBg(BLUE, 24, BLUE, this@SettingsActivity)
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                Prefs.setKey(this@SettingsActivity, key.text.toString())
                Toast.makeText(this@SettingsActivity, "Save ho gaya", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // ---------- Bhasha ----------
        col.addView(label("Bhasha (Language)"))
        val langRow = tv(Prefs.langName(this) + "   ▾", 17f, Color.WHITE, true).apply {
            background = roundBg(CARD, 14, LINE, this@SettingsActivity)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        langRow.setOnClickListener {
            val names = Prefs.languages.map { it.first }.toTypedArray()
            val cur = Prefs.languages.indexOfFirst { it.second == Prefs.lang(this) }
            AlertDialog.Builder(this)
                .setTitle("Bhasha chuniye")
                .setSingleChoiceItems(names, cur) { d, which ->
                    Prefs.setLang(this, Prefs.languages[which].second)
                    langRow.text = Prefs.langName(this) + "   ▾"
                    d.dismiss()
                }.show()
        }
        col.addView(langRow)
        col.addView(tv("Agar kisi bhasha mein awaaz na aaye to phone ki Settings → Google → Text-to-speech / Voice mein wo bhasha download karo.", 12f, GRAY).apply { setPadding(0, dp(6), 0, 0) })

        // ---------- Rang ----------
        colorSection(col, "Ring light ka rang (mobile ke kinare)", { Prefs.ring(this) }, { Prefs.setRing(this, it) })
        colorSection(col, "Particle gole ka rang", { Prefs.orb(this) }, { Prefs.setOrb(this, it) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            fitsSystemWindows = true
            addView(col)
        })
    }

    private fun colorSection(col: LinearLayout, title: String, get: () -> Int, set: (Int) -> Unit) {
        col.addView(label(title))
        val row = LinearLayout(this)
        fun fill() {
            row.removeAllViews()
            Prefs.colors.forEach { cl ->
                val sel = cl == get()
                val dot = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(cl)
                        setStroke(dp(if (sel) 3 else 1), if (sel) Color.WHITE else LINE)
                    }
                    setOnClickListener { set(cl); fill() }
                }
                row.addView(dot, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) })
            }
        }
        fill()
        col.addView(HorizontalScrollView(this).apply { addView(row); isHorizontalScrollBarEnabled = false })
    }
}
