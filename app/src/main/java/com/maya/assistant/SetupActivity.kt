package com.maya.assistant

import android.Manifest.permission.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun roundBg(color: Int, radiusDp: Int, strokeColor: Int, ctx: Context): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = ctx.dp(radiusDp).toFloat()
        setStroke(ctx.dp(1), strokeColor)
    }

class SetupActivity : AppCompatActivity() {

    private var step = 1
    private lateinit var root: LinearLayout
    private val BG = 0xFF0B1220.toInt()
    private val CARD = 0xFF151D2E.toInt()
    private val LINE = 0xFF2A3550.toInt()
    private val BLUE = 0xFF4F7CFF.toInt()
    private val GRAY = 0xFF8C97B2.toInt()
    private val ORANGE = 0xFFFF8A1F.toInt()
    private val GREEN = 0xFF2ECC71.toInt()
    private val sdk = Build.VERSION.SDK_INT

    class Item(
        val icon: String, val title: String, val desc: String,
        val perms: List<String>, val ok: () -> Boolean, val open: () -> Unit
    )

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun perm(icon: String, title: String, desc: String, vararg p: String) =
        Item(icon, title, desc, p.toList(), { p.all { granted(it) } }, {
            if (p.isNotEmpty()) ActivityCompat.requestPermissions(this, arrayOf(*p), 100)
        })

    private fun runtimeItems() = listOf(
        perm("🎤", "Microphone", "So you can talk to Maya. Required.", RECORD_AUDIO),
        perm("🔔", "Notifications", "Maya's own notification, which keeps her running in the background.",
            *(if (sdk >= 33) arrayOf(POST_NOTIFICATIONS) else emptyArray<String>())),
        perm("📷", "Camera", "So she can take a photo or look at what you point the phone at.", CAMERA),
        perm("📞", "Phone calls", "So she can place a call for you.", CALL_PHONE),
        perm("👤", "Contacts", "So a name is enough — she looks up the number.", READ_CONTACTS),
        perm("💬", "SMS", "So she can send a text message.", SEND_SMS),
        perm("📍", "Location", "Weather, navigation, and where you are.", ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
        perm("🖼", "Gallery & files", "So she can find a photo or file and send it.",
            *(if (sdk >= 33) arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO) else arrayOf(READ_EXTERNAL_STORAGE))),
        perm("🎧", "Answer & manage calls", "So she can announce who is calling and answer or reject it.",
            ANSWER_PHONE_CALLS, READ_PHONE_STATE),
        perm("🔵", "Bluetooth", "So her voice plays on your earbuds or speaker.",
            *(if (sdk >= 31) arrayOf(BLUETOOTH_CONNECT) else emptyArray<String>()))
    )

    private fun specialItems() = listOf(
        Item("🔋", "Battery — no optimisation",
            "Required. Without it the phone kills Maya in the background and she goes quiet. Tap, then choose Allow.",
            emptyList(),
            { getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) },
            {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")))
            }),
        Item("🧩", "Display over other apps", "So her orb can float on top of whatever you are doing.",
            emptyList(), { Settings.canDrawOverlays(this) },
            {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }),
        Item("🔔", "Notification access",
            "To read WhatsApp messages and know who is calling. Turn on \"Maya\" in the list.",
            emptyList(),
            { NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName) },
            { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }),
        Item("♿", "Accessibility service",
            "So she can open apps, tap, and read the screen for you. Turn on \"Maya\" in the list.",
            emptyList(),
            {
                Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    ?.contains(packageName) == true
            },
            { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }),
        Item("🎧", "Default assistant",
            "Long-press the power button or swipe from a corner to call her. Pick \"MAYA\".",
            emptyList(),
            { Settings.Secure.getString(contentResolver, "assistant")?.contains(packageName) == true },
            { startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) })
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("maya", MODE_PRIVATE)
        if (prefs.getBoolean("setup_done", false)) {
            startActivity(Intent(this, HomeActivity::class.java)); finish(); return
        }
        window.statusBarColor = BG
        window.navigationBarColor = BG
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            fitsSystemWindows = true
        }
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        render()
    }

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun btn(text: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) =
        tv(text, 18f, if (filled) Color.WHITE else GRAY, true).apply {
            gravity = Gravity.CENTER
            background = roundBg(if (filled) BLUE else BG, 28, if (filled) BLUE else LINE, this@SetupActivity)
            alpha = if (enabled) 1f else 0.5f
            setPadding(0, dp(16), 0, dp(16))
            setOnClickListener { if (enabled) onClick() }
        }

    private fun render() {
        root.removeAllViews()

        // Header
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = tv("M", 24f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundBg(0xFF1B2A55.toInt(), 14, BLUE, this@SetupActivity)
        }
        head.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        col.addView(tv("Set up Maya", 24f, Color.WHITE, true))
        col.addView(tv("Step $step of 3", 16f, GRAY, true))
        head.addView(col)
        root.addView(head)

        // Progress
        val bar = LinearLayout(this).apply { setPadding(0, dp(14), 0, dp(18)) }
        for (i in 1..3) {
            val seg = View(this).apply { background = roundBg(if (i <= step) BLUE else LINE, 3, if (i <= step) BLUE else LINE, this@SetupActivity) }
            bar.addView(seg, LinearLayout.LayoutParams(0, dp(5), 1f).apply { marginEnd = dp(6) })
        }
        root.addView(bar)

        when (step) {
            1 -> {
                root.addView(tv("Welcome to Maya", 28f, Color.WHITE, true))
                root.addView(tv("Aapki awaaz se aapka phone chalane wali assistant. Agle 2 steps mein sari permissions de dijiye – bas.", 17f, GRAY).apply { setPadding(0, dp(10), 0, 0) })
                root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
                root.addView(btn("Start", true, true) { step = 2; render() })
            }
            2 -> {
                root.addView(tv("Let Maya use your phone", 28f, Color.WHITE, true))
                root.addView(tv("One tap — Android asks for each in turn. Only the microphone is required; the rest make her useful.", 16f, GRAY).apply { setPadding(0, dp(8), 0, dp(14)) })
                val items = runtimeItems()
                root.addView(list(items), LinearLayout.LayoutParams(-1, 0, 1f))
                root.addView(btn("✓  Allow all", true, true) {
                    val need = items.flatMap { it.perms }.filter { !granted(it) }.toTypedArray()
                    if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need, 101)
                }, topMargin(12))
                val micOk = granted(RECORD_AUDIO)
                root.addView(btn("→  Continue", false, micOk) { step = 3; render() }, topMargin(10))
                root.addView(tv("Continue unlocks once the microphone is allowed.", 14f, GRAY).apply {
                    gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
                })
            }
            3 -> {
                root.addView(tv("Keep Maya alive in the background", 26f, Color.WHITE, true))
                root.addView(tv("Battery is required — it is what stops her going quiet when the screen is off. The rest are optional but make her far more useful.", 16f, GRAY).apply { setPadding(0, dp(8), 0, dp(14)) })
                val items = specialItems()
                root.addView(list(items), LinearLayout.LayoutParams(-1, 0, 1f))
                root.addView(tv("Tap a Pending row to open its system screen — come back here when you are done.", 13f, GRAY).apply { setPadding(0, dp(8), 0, dp(8)) })
                val batOk = items[0].ok()
                root.addView(btn("✓  Finish", true, batOk) {
                    getSharedPreferences("maya", MODE_PRIVATE).edit().putBoolean("setup_done", true).apply()
                    startForegroundService(Intent(this, MayaService::class.java))
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                })
            }
        }
    }

    private fun topMargin(d: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(d) }

    private fun list(items: List<Item>): ScrollView {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.forEach { col.addView(row(it), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }) }
        return ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }
    }

    private fun row(item: Item): View {
        val ok = item.ok()
        val r = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = roundBg(CARD, 16, LINE, this@SetupActivity)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener { if (!ok) item.open() }
        }
        val icon = tv(item.icon, 20f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = roundBg(0xFF1B2547.toInt(), 30, 0xFF1B2547.toInt(), this@SetupActivity)
        }
        r.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(item.title, 19f, Color.WHITE, true))
        col.addView(tv(item.desc, 14f, GRAY))
        r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val status = if (ok) tv("✓", 24f, GREEN, true)
        else tv("Pending", 15f, ORANGE, true).apply {
            background = roundBg(0xFF3A2A1E.toInt(), 20, 0xFF3A2A1E.toInt(), this@SetupActivity)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        r.addView(status, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        return r
    }
}
