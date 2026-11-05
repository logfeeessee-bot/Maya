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
import androidx.appcompat.widget.SwitchCompat
import java.net.HttpURLConnection
import java.net.URL

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
        val keyStatus = tv("", 14f, GRAY)
        col.addView(tv("Save", 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundBg(BLUE, 24, BLUE, this@SettingsActivity)
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                Prefs.setKey(this@SettingsActivity, key.text.toString())
                Toast.makeText(this@SettingsActivity, "Save ho gaya", Toast.LENGTH_SHORT).show()
                checkKey(key.text.toString(), keyStatus)
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        col.addView(tv("Key jaanch karo", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundBg(CARD, 24, LINE, this@SettingsActivity)
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { checkKey(key.text.toString(), keyStatus) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        col.addView(keyStatus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

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
                    refreshVoskStatus()
                    d.dismiss()
                }.show()
        }
        col.addView(langRow)
        col.addView(tv("Agar kisi bhasha mein awaaz na aaye to phone ki Settings → Google → Text-to-speech / Voice mein wo bhasha download karo.", 12f, GRAY).apply { setPadding(0, dp(6), 0, 0) })

        // ---------- Whisper (har bhasha) ----------
        switchRow(col, "Behtar sunna (Whisper, internet se)",
            "Chalu rakhne par internet ho to Maya aapki awaaz Groq ko bhejkar kisi bhi bhasha mein bahut sahi samajhti hai. Internet na ho to apne aap offline tareeka chalta hai.",
            { Prefs.whisper(this) }, { Prefs.setWhisper(this, it) })
        switchRow(col, "Bhasha apne aap pehchano",
            "Band karoge to Whisper sirf upar chuni hui bhasha sunega (us bhasha mein zyada sahi).",
            { Prefs.autoLang(this) }, { Prefs.setAutoLang(this, it) })

        // ---------- Ek baar mic chalu (Vosk, offline) ----------
        switchRow(col, "Mic ek baar chalu, chalta rahe (Offline)",
            "Maya ka apna mic ek baar khulta hai aur band nahi hota, bina internet ke bhi. Model pehli baar apne aap download hota hai (ya neeche button se).",
            { Prefs.vosk(this) }, { Prefs.setVosk(this, it) })
        val st = tv("", 13f, GRAY)
        voskStatus = st
        col.addView(st, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        col.addView(tv("Model download karo", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundBg(BLUE, 24, BLUE, this@SettingsActivity)
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { downloadModel() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        refreshVoskStatus()
        switchRow(col, "Sirf 'Maya' bolne par kaam karo",
            "Chalu karoge to Maya tabhi kaam karegi jab aap pehle 'Maya' bolo (jaise: Maya torch chalu karo). Aas-paas ki baaton par kaam nahi karegi.",
            { Prefs.wake(this) }, { Prefs.setWake(this, it) })

        // ---------- Rang ----------
        colorSection(col, "Ring light ka rang (mobile ke kinare)", { Prefs.ring(this) }, { Prefs.setRing(this, it) })
        colorSection(col, "Particle gole ka rang", { Prefs.orb(this) }, { Prefs.setOrb(this, it) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            fitsSystemWindows = true
            addView(col)
        })
    }

    /** Groq key sahi hai ya nahi, seedha Groq se poochhkar batata hai */
    private fun checkKey(k: String, out: TextView) {
        val key = k.trim()
        if (key.isEmpty()) { out.text = "Pehle key paste kijiye"; return }
        out.text = "Jaanch rahi hoon..."
        Thread {
            val msg = try {
                val con = URL("https://api.groq.com/openai/v1/models").openConnection() as HttpURLConnection
                con.connectTimeout = 8000
                con.readTimeout = 8000
                con.setRequestProperty("Authorization", "Bearer $key")
                when (val c = con.responseCode) {
                    200 -> "Key sahi hai ✓"
                    401 -> "Key galat ya band hai (401). console.groq.com/keys par nayi key banakar yahan paste kijiye."
                    429 -> "Key sahi hai, par abhi Groq ki limit poori hai (429). Thodi der baad try kijiye."
                    else -> "Groq ne HTTP $c diya"
                }
            } catch (e: Exception) {
                "Internet ya Groq tak nahi pahunch paayi: " + (e.message ?: "")
            }
            runOnUiThread { out.text = msg }
        }.start()
    }

    private var voskStatus: TextView? = null

    private fun refreshVoskStatus() {
        val l = Prefs.lang(this)
        voskStatus?.text = when {
            VoskModels.entry(l) == null ->
                "Is bhasha (" + Prefs.langName(this) + ") ka offline model nahi hai. Hindi, English, Gujarati, Telugu, Spanish, French, German, Russian, Chinese, Japanese, Portuguese, Italian, Turkish ke hain."
            VoskModels.dir(this, l) != null -> "Model taiyaar hai: " + Prefs.langName(this)
            else -> "Model abhi download nahi hua: " + Prefs.langName(this) + " (lagbhag 40-50 MB)"
        }
    }

    private fun downloadModel() {
        val l = Prefs.lang(this)
        voskStatus?.text = "Download shuru..."
        Thread {
            val err = VoskModels.download(applicationContext, l) { msg -> runOnUiThread { voskStatus?.text = msg } }
            runOnUiThread { if (err != null) voskStatus?.text = err else refreshVoskStatus() }
        }.start()
    }

    private fun switchRow(col: LinearLayout, title: String, desc: String, getV: () -> Boolean, setV: (Boolean) -> Unit) {
        col.addView(label(title))
        col.addView(tv(desc, 13f, GRAY))
        val sw = SwitchCompat(this).apply {
            isChecked = getV()
            text = if (getV()) "चालू" else "बंद"
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, v -> setV(v); text = if (v) "चालू" else "बंद" }
        }
        col.addView(sw, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
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
