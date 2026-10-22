package com.maya.assistant

import android.Manifest.permission.CALL_PHONE
import android.Manifest.permission.READ_CONTACTS
import android.Manifest.permission.SEND_SMS
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandHandler(private val c: Context) {

    companion object { const val STOP = "__STOP__" }

    private val aliases = mapOf(
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "यूट्यूब" to "youtube", "कैमरा" to "camera",
        "गैलरी" to "gallery", "क्रोम" to "chrome", "गूगल" to "google", "मैप" to "maps", "मैप्स" to "maps",
        "इंस्टाग्राम" to "instagram", "फेसबुक" to "facebook", "कैलकुलेटर" to "calculator",
        "घड़ी" to "clock", "संदेश" to "messages", "मैसेज" to "messages", "टेलीग्राम" to "telegram"
    )

    private fun granted(p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
    private fun acc() = MayaAccessibilityService.inst
    private fun newTask(i: Intent) = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun handle(raw: String): String {
        val t = raw.lowercase(Locale.ROOT).trim()
        fun has(vararg k: String) = k.any { t.contains(it) }

        return when {
            // Torch
            has("torch", "टॉर्च", "flashlight", "फ्लैश", "टोर्च") -> {
                val on = !has("band", "बंद", "off", "बन्द")
                torch(on); if (on) "टॉर्च चालू कर दी" else "टॉर्च बंद कर दी"
            }
            // Maya band
            has("band karo", "band kar do", "stop", "बंद करो", "बंद कर दो", "रुक जाओ", "रुको", "चुप") -> STOP

            // Time
            has("time", "samay", "kitne baje", "टाइम", "समय", "कितने बजे") ->
                "अभी " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + " हुए हैं"

            // Volume
            has("volume", "वॉल्यूम", "आवाज़", "आवाज") -> {
                val up = has("badhao", "बढ़ा", "बढ़ाओ", "up", "zyada", "ज्यादा", "तेज", "ऊपर")
                val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                repeat(3) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }
                if (up) "आवाज़ बढ़ा दी" else "आवाज़ कम कर दी"
            }

            // Wifi / Bluetooth / Settings
            has("wifi", "वाईफाई", "वाई-फाई", "वाईफ़ाई") -> { open(Intent(Settings.Panel.ACTION_WIFI)); "वाईफाई खोल रही हूँ" }
            has("bluetooth", "ब्लूटूथ") -> { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); "ब्लूटूथ खोल रही हूँ" }
            has("settings", "सेटिंग") -> { open(Intent(Settings.ACTION_SETTINGS)); "सेटिंग खोल रही हूँ" }

            // Accessibility: global actions
            has("notification", "नोटिफिकेशन") -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "नोटिफिकेशन खोल दिए")
            has("recent", "रीसेंट") -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, "रीसेंट ऐप्स खोल दिए")
            has("screenshot", "स्क्रीनशॉट") ->
                if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "स्क्रीनशॉट ले लिया")
                else "इस फोन पर स्क्रीनशॉट नहीं हो सकता"
            has("lock", "लॉक") ->
                if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "फोन लॉक कर दिया")
                else "इस फोन पर लॉक नहीं हो सकता"
            has("wapas", "wapis", "back", "पीछे", "वापस") -> global(AccessibilityService.GLOBAL_ACTION_BACK, "ठीक है, पीछे आ गई")
            has("home", "होम", "घर जाओ") -> global(AccessibilityService.GLOBAL_ACTION_HOME, "होम पर आ गई")
            has("neeche", "नीचे", "scroll down") -> swipe(true)
            has("upar", "ऊपर", "scroll up") -> swipe(false)

            // Click on text: "<naam> par click karo"
            Regex("(.+?)\\s+(par|pe|पर|पे)\\s+(click|tap|dabao|क्लिक|टैप|दबाओ|क्लिक करो)").containsMatchIn(t) -> {
                val name = Regex("(.+?)\\s+(par|pe|पर|पे)\\s+").find(t)!!.groupValues[1].trim()
                val s = acc() ?: return "पहले Accessibility सेवा चालू कीजिए"
                if (s.clickText(name)) "$name दबा दिया" else "$name स्क्रीन पर नहीं मिला"
            }

            // SMS: "<naam> ko message bhejo <text>"
            Regex("(.+?)\\s+(?:ko|को)\\s+(?:message|sms|मैसेज|एसएमएस)\\s+(?:bhejo|karo|भेजो|करो)\\s*(.*)").find(t) != null -> {
                val m = Regex("(.+?)\\s+(?:ko|को)\\s+(?:message|sms|मैसेज|एसएमएस)\\s+(?:bhejo|karo|भेजो|करो)\\s*(.*)").find(t)!!
                sms(m.groupValues[1].trim(), m.groupValues[2].trim())
            }

            // Call
            has("call", "कॉल", "फोन", "phone", "dial", "फ़ोन") -> {
                val name = strip(t, "call", "karo", "kar", "do", "lagao", "phone", "dial", "ko", "कॉल", "करो", "कर", "दो", "लगाओ", "फोन", "फ़ोन", "को", "मिलाओ")
                call(name)
            }

            // Open app
            has("kholo", "open", "खोलो", "खोल", "चालू करो", "start", "चलाओ") -> {
                val name = strip(t, "kholo", "open", "khol", "do", "karo", "खोलो", "खोल", "दो", "चालू", "करो", "start", "चलाओ", "ऐप", "app")
                openApp(name)
            }

            // ---- Yahan baad mein AI (Claude / Gemini API) jod sakte ho jo har baat samjhe ----
            else -> "मुझे समझ नहीं आया, फिर से बोलिए"
        }
    }

    // ---------- helpers ----------
    private fun open(i: Intent) = c.startActivity(newTask(i))

    private fun strip(text: String, vararg words: String): String {
        var s = " $text "
        words.forEach { s = s.replace(Regex("(?<=\\s)${Regex.escape(it)}(?=\\s)"), " ") }
        return s.trim().replace(Regex("\\s+"), " ")
    }

    private fun torch(on: Boolean) {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        cm.setTorchMode(id, on)
    }

    private fun global(action: Int, ok: String): String {
        val s = acc() ?: return "पहले Accessibility सेवा चालू कीजिए"
        s.performGlobalAction(action)
        return ok
    }

    private fun swipe(up: Boolean): String {
        val s = acc() ?: return "पहले Accessibility सेवा चालू कीजिए"
        s.swipe(up)
        return if (up) "नीचे स्क्रॉल किया" else "ऊपर स्क्रॉल किया"
    }

    private fun openApp(spoken: String): String {
        if (spoken.isBlank()) return "कौन सा ऐप खोलूँ?"
        val name = aliases[spoken] ?: spoken
        val pm = c.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
            ?: return "$spoken ऐप नहीं मिला"
        val launch = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "ऐप नहीं खुल पाया"
        open(launch)
        return "$spoken खोल रही हूँ"
    }

    private fun findNumber(name: String): String? {
        if (name.all { it.isDigit() || it == ' ' }) return name.replace(" ", "")
        if (!granted(READ_CONTACTS)) return null
        c.contentResolver.query(Phone.CONTENT_URI, arrayOf(Phone.NUMBER),
            "${Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun call(name: String): String {
        if (name.isBlank()) return "किसे कॉल करूँ?"
        if (!granted(CALL_PHONE)) return "कॉल की अनुमति नहीं है"
        val num = findNumber(name) ?: return "$name का नंबर नहीं मिला"
        open(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")))
        return "$name को कॉल कर रही हूँ"
    }

    private fun sms(name: String, body: String): String {
        if (!granted(SEND_SMS)) return "SMS की अनुमति नहीं है"
        if (body.isBlank()) return "क्या मैसेज भेजूँ?"
        val num = findNumber(name) ?: return "$name का नंबर नहीं मिला"
        val sm = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        sm.sendTextMessage(num, null, body, null, null)
        return "$name को मैसेज भेज दिया"
    }
}
