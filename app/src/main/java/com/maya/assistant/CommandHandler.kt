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
    private fun open(i: Intent) = c.startActivity(newTask(i))
    private fun noAcc() = "पहले Accessibility सेवा चालू कीजिए"

    // =====================================================================
    //  AI (Groq) se aaya hua action chalane ke liye.
    //  Return: null = sab theek (AI ka reply bolo) | text = galti ya jawab (wahi bolo)
    // =====================================================================
    fun execute(action: String, target: String, text: String, on: Boolean): String? = when (action) {
        "open_app" -> openApp(target)
        "call" -> call(target)
        "sms" -> sms(target, text)
        "torch" -> { torch(on); null }
        "volume_up" -> { vol(true); null }
        "volume_down" -> { vol(false); null }
        "back" -> global(AccessibilityService.GLOBAL_ACTION_BACK)
        "home" -> global(AccessibilityService.GLOBAL_ACTION_HOME)
        "recents" -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
        "notifications" -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        "screenshot" -> shot()
        "lock" -> lockScreen()
        "scroll_down" -> swipe(true)
        "scroll_up" -> swipe(false)
        "click_text" -> click(target)
        "wifi" -> { open(Intent(Settings.Panel.ACTION_WIFI)); null }
        "bluetooth" -> { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); null }
        "settings" -> { open(Intent(Settings.ACTION_SETTINGS)); null }
        "time" -> timeText()
        else -> null
    }

    // =====================================================================
    //  Purane rules (jab internet ya Groq key na ho tab kaam aate hain)
    // =====================================================================
    fun handle(raw: String): String {
        val t = raw.lowercase(Locale.ROOT).trim()
        fun has(vararg k: String) = k.any { t.contains(it) }
        val smsM = Regex("(.+?)\\s+(?:ko|को)\\s+(?:message|sms|मैसेज|एसएमएस)\\s+(?:bhejo|karo|भेजो|करो)\\s*(.*)").find(t)
        val clickM = Regex("(.+?)\\s+(?:par|pe|पर|पे)\\s+(?:click|tap|dabao|क्लिक|टैप|दबाओ)").find(t)

        return when {
            has("torch", "टॉर्च", "flashlight", "फ्लैश", "टोर्च") -> {
                val on = !has("band", "बंद", "off", "बन्द")
                torch(on); if (on) "टॉर्च चालू कर दी" else "टॉर्च बंद कर दी"
            }
            has("band karo", "band kar do", "stop", "बंद करो", "बंद कर दो", "रुक जाओ", "रुको", "चुप") -> STOP
            has("time", "samay", "kitne baje", "टाइम", "समय", "कितने बजे") -> timeText()
            has("volume", "वॉल्यूम", "आवाज़", "आवाज") -> {
                val up = has("badhao", "बढ़ा", "बढ़ाओ", "up", "zyada", "ज्यादा", "तेज", "ऊपर")
                vol(up); if (up) "आवाज़ बढ़ा दी" else "आवाज़ कम कर दी"
            }
            has("wifi", "वाईफाई", "वाई-फाई", "वाईफ़ाई") -> { open(Intent(Settings.Panel.ACTION_WIFI)); "वाईफाई खोल रही हूँ" }
            has("bluetooth", "ब्लूटूथ") -> { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); "ब्लूटूथ खोल रही हूँ" }
            has("settings", "सेटिंग") -> { open(Intent(Settings.ACTION_SETTINGS)); "सेटिंग खोल रही हूँ" }
            has("notification", "नोटिफिकेशन") -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: "नोटिफिकेशन खोल दिए"
            has("recent", "रीसेंट") -> global(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: "रीसेंट ऐप्स खोल दिए"
            has("screenshot", "स्क्रीनशॉट") -> shot() ?: "स्क्रीनशॉट ले लिया"
            has("lock", "लॉक") -> lockScreen() ?: "फोन लॉक कर दिया"
            has("wapas", "wapis", "back", "पीछे", "वापस") -> global(AccessibilityService.GLOBAL_ACTION_BACK) ?: "ठीक है, पीछे आ गई"
            has("home", "होम", "घर जाओ") -> global(AccessibilityService.GLOBAL_ACTION_HOME) ?: "होम पर आ गई"
            has("neeche", "नीचे", "scroll down") -> swipe(true) ?: "नीचे स्क्रॉल किया"
            has("upar", "ऊपर", "scroll up") -> swipe(false) ?: "ऊपर स्क्रॉल किया"
            clickM != null -> {
                val name = clickM.groupValues[1].trim()
                click(name) ?: "$name दबा दिया"
            }
            smsM != null -> {
                val name = smsM.groupValues[1].trim()
                sms(name, smsM.groupValues[2].trim()) ?: "$name को मैसेज भेज दिया"
            }
            has("call", "कॉल", "फोन", "phone", "dial", "फ़ोन") -> {
                val name = strip(t, "call", "karo", "kar", "do", "lagao", "phone", "dial", "ko", "कॉल", "करो", "कर", "दो", "लगाओ", "फोन", "फ़ोन", "को", "मिलाओ")
                call(name) ?: "$name को कॉल कर रही हूँ"
            }
            has("kholo", "open", "खोलो", "खोल", "चालू करो", "start", "चलाओ") -> {
                val name = strip(t, "kholo", "open", "khol", "do", "karo", "खोलो", "खोल", "दो", "चालू", "करो", "start", "चलाओ", "ऐप", "app")
                openApp(name) ?: "$name खोल रही हूँ"
            }
            else -> "मुझे समझ नहीं आया, फिर से बोलिए (AI के लिए Settings में Groq key डालिए)"
        }
    }

    // ---------- madad wale functions ----------
    private fun strip(text: String, vararg words: String): String {
        var s = " $text "
        words.forEach { s = s.replace(Regex("(?<=\\s)${Regex.escape(it)}(?=\\s)"), " ") }
        return s.trim().replace(Regex("\\s+"), " ")
    }

    private fun timeText() =
        "अभी " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + " हुए हैं"

    private fun vol(up: Boolean) {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        repeat(3) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun torch(on: Boolean) {
        try {
            val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cm.setTorchMode(id, on)
        } catch (_: Exception) {}
    }

    private fun global(action: Int): String? {
        val s = acc() ?: return noAcc()
        s.performGlobalAction(action)
        return null
    }

    private fun shot(): String? =
        if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        else "इस फोन पर स्क्रीनशॉट नहीं हो सकता"

    private fun lockScreen(): String? =
        if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        else "इस फोन पर लॉक नहीं हो सकता"

    private fun swipe(up: Boolean): String? {
        val s = acc() ?: return noAcc()
        s.swipe(up)
        return null
    }

    private fun click(name: String): String? {
        val s = acc() ?: return noAcc()
        return if (s.clickText(name)) null else "$name स्क्रीन पर नहीं मिला"
    }

    private fun openApp(spoken: String): String? {
        if (spoken.isBlank()) return "कौन सा ऐप खोलूँ?"
        val key = spoken.lowercase(Locale.ROOT).trim()
        val name = aliases[key] ?: key
        val pm = c.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
            ?: return "$spoken ऐप नहीं मिला"
        val launch = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "ऐप नहीं खुल पाया"
        open(launch)
        return null
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

    private fun call(name: String): String? {
        if (name.isBlank()) return "किसे कॉल करूँ?"
        if (!granted(CALL_PHONE)) return "कॉल की अनुमति नहीं है"
        val num = findNumber(name) ?: return "$name का नंबर नहीं मिला"
        open(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")))
        return null
    }

    private fun sms(name: String, body: String): String? {
        if (!granted(SEND_SMS)) return "SMS की अनुमति नहीं है"
        if (body.isBlank()) return "क्या मैसेज भेजूँ?"
        val num = findNumber(name) ?: return "$name का नंबर नहीं मिला"
        val sm = if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        sm.sendTextMessage(num, null, body, null, null)
        return null
    }
}
