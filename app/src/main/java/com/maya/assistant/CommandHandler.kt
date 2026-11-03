hipackage com.maya.assistant

import android.Manifest.permission.CALL_PHONE
import android.Manifest.permission.READ_CONTACTS
import android.Manifest.permission.SEND_SMS
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hindi (Devanagari) ko English akshar mein badalta hai, taaki offline naam/ऐप milaye ja sakein */
private object Translit {
    private val cons = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h"
    )
    private val vowels = mapOf(
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u",
        'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au"
    )
    private val matras = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e"
    )

    fun to(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            val cr = cons[ch]
            if (cr != null) {
                sb.append(cr)
                var j = i + 1
                while (j < s.length && s[j] == '़') j++
                if (j < s.length && s[j] == '्') { i = j + 1; continue }
                val m = if (j < s.length) matras[s[j]] else null
                if (m != null) { sb.append(m); i = j + 1; continue }
                val more = j < s.length && (cons.containsKey(s[j]) || vowels.containsKey(s[j]) ||
                    matras.containsKey(s[j]) || s[j] == 'ं' || s[j] == 'ँ' || s[j] == 'ः')
                if (more) sb.append('a')
                i = j
                continue
            }
            val v = vowels[ch]
            when {
                v != null -> sb.append(v)
                matras.containsKey(ch) -> sb.append(matras[ch])
                ch == 'ं' || ch == 'ँ' -> sb.append('n')
                ch == 'ः' -> sb.append('h')
                ch == '़' || ch == '्' -> {}
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }
}

class CommandHandler(private val c: Context) {

    companion object { const val STOP = "__STOP__" }

    private val aliases = mapOf(
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "यूट्यूब" to "youtube", "कैमरा" to "camera",
        "गैलरी" to "gallery", "क्रोम" to "chrome", "गूगल" to "google", "मैप" to "maps", "मैप्स" to "maps",
        "इंस्टाग्राम" to "instagram", "फेसबुक" to "facebook", "कैलकुलेटर" to "calculator",
        "घड़ी" to "clock", "संदेश" to "messages", "मैसेज" to "messages", "टेलीग्राम" to "telegram",
        "संपर्क" to "contacts", "कॉन्टैक्ट" to "contacts", "कैलेंडर" to "calendar",
        "जीमेल" to "gmail", "ड्राइव" to "drive", "ब्राउज़र" to "chrome", "फोटो" to "photos"
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
        "volume_up" -> { vol(true, 3); null }
        "volume_down" -> { vol(false, 3); null }
        "back" -> global(AccessibilityService.GLOBAL_ACTION_BACK)
        "home" -> global(AccessibilityService.GLOBAL_ACTION_HOME)
        "recents" -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
        "notifications" -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        "screenshot" -> shot()
        "lock" -> lockScreen()
        "scroll_down" -> swipe(true)
        "scroll_up" -> swipe(false)
        "click_text" -> click(target)
        "wifi" -> { openPage(Settings.ACTION_WIFI_SETTINGS); null }
        "bluetooth" -> { openPage(Settings.ACTION_BLUETOOTH_SETTINGS); null }
        "settings" -> { openPage(Settings.ACTION_SETTINGS); null }
        "time" -> timeText()
        "youtube_search" -> youtubeSearch(target.ifBlank { text })
        "web_search" -> webSearch(target.ifBlank { text })
        "navigate" -> navigate(target)
        "type_text" -> typeText(text.ifBlank { target })
        "press_enter" -> pressEnter()
        "agent" -> null   // agent MayaService mein chalta hai
        else -> null
    }

    // =====================================================================
    //  OFFLINE DIMAAG: bina internet ke Hindi / English / Hinglish commands samajhta hai
    // =====================================================================
    fun handle(raw: String): String {
        val t = raw.lowercase(Locale.ROOT).trim()
        fun has(vararg k: String) = k.any { t.contains(it) }
        fun hw(vararg k: String) = k.any {
            Regex("(?<![\\p{L}\\p{M}])" + Regex.escape(it) + "(?![\\p{L}\\p{M}])").containsMatchIn(t)
        }
        val isOff = has("band", "बंद", "बन्द", "ऑफ", "bujha", "बुझा") || hw("off")
        val searchWords = arrayOf("search", "khojo", "khoj", "dhundo", "dhoondo", "सर्च", "खोजो", "खोज",
            "ढूंढो", "ढूँढो", "चलाओ", "chalao", "play", "बजाओ", "bajao")

        // ---- torch ----
        if (has("torch", "टॉर्च", "टोर्च", "टार्च", "flashlight", "flash light", "फ्लैश", "tourch")) {
            torch(!isOff)
            return if (isOff) "टॉर्च बंद कर दी" else "टॉर्च चालू कर दी"
        }

        // ---- Maya ko roknaa ----
        if (Regex("^(?:maya |माया )?(?:band karo|band kar do|बंद करो|बंद कर दो|बंद हो जाओ|band ho jao)$").containsMatchIn(t)
            || hw("stop") || has("रुको", "रुक जाओ", "चुप", "so jao", "सो जाओ", "बस करो", "bas karo", "mic band", "mic off")) {
            return STOP
        }

        // ---- jaankari ----
        if (hw("time") || has("samay", "kitne baje", "टाइम", "समय", "कितने बजे")) return timeText()
        if (hw("date") || has("tarikh", "तारीख", "kaun sa din", "कौन सा दिन")) {
            return "आज " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale("hi", "IN")).format(Date()) + " है"
        }
        if (has("battery", "बैटरी") && !has("setting", "सेटिंग", "saver")) {
            val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            return "बैटरी " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + " प्रतिशत है"
        }

        // ---- awaaz ----
        if (has("volume", "वॉल्यूम", "आवाज़", "आवाज")) {
            return when {
                has("mute", "म्यूट", "silent", "साइलेंट") -> { vol(false, 15); "आवाज़ बंद कर दी" }
                has("full", "max", "poora", "पूरा", "फुल", "मैक्स") -> { vol(true, 15); "आवाज़ पूरी कर दी" }
                has("badhao", "बढ़ा", "बढ़ाओ", "zyada", "ज्यादा", "तेज", "ऊपर", "increase") || hw("up") -> { vol(true, 3); "आवाज़ बढ़ा दी" }
                else -> { vol(false, 3); "आवाज़ कम कर दी" }
            }
        }

        // ---- settings ke panne (Android khud on/off nahi karne deta, panna khol deti hoon) ----
        if (has("wifi", "वाईफाई", "वाई-फाई", "वाईफ़ाई", "wi-fi")) return page(Settings.ACTION_WIFI_SETTINGS, "वाईफाई की सेटिंग खोल दी")
        if (has("bluetooth", "ब्लूटूथ")) return page(Settings.ACTION_BLUETOOTH_SETTINGS, "ब्लूटूथ की सेटिंग खोल दी")
        if (has("airplane", "flight mode", "एयरप्लेन", "हवाई जहाज")) return page(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "एयरप्लेन मोड की सेटिंग खोल दी")
        if (has("location", "लोकेशन", "gps", "जीपीएस")) return page(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "लोकेशन की सेटिंग खोल दी")
        if (has("brightness", "ब्राइटनेस", "display", "डिस्प्ले")) return page(Settings.ACTION_DISPLAY_SETTINGS, "डिस्प्ले की सेटिंग खोल दी")
        if (has("sound setting", "साउंड सेटिंग", "ringtone", "रिंगटोन")) return page(Settings.ACTION_SOUND_SETTINGS, "साउंड की सेटिंग खोल दी")
        if (has("mobile data", "मोबाइल डेटा", "data", "डेटा")) return page(Settings.ACTION_WIRELESS_SETTINGS, "नेटवर्क की सेटिंग खोल दी")
        if (has("battery saver", "बैटरी सेवर")) return page(Settings.ACTION_BATTERY_SAVER_SETTINGS, "बैटरी सेवर खोल दिया")
        if (has("quick settings", "क्विक सेटिंग")) return global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) ?: "क्विक सेटिंग खोल दी"
        if (has("settings", "सेटिंग")) return page(Settings.ACTION_SETTINGS, "सेटिंग खोल दी")

        // ---- screen padhna ----
        if (has("screen padho", "स्क्रीन पढ़ो", "स्क्रीन पर क्या", "screen par kya", "read screen", "screen padh")) return readAloud()

        // ---- phone ke bade button ----
        if (has("notification", "नोटिफिकेशन", "सूचना")) return global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: "नोटिफिकेशन खोल दिए"
        if (has("recent", "रीसेंट")) return global(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: "रीसेंट ऐप्स खोल दिए"
        if (has("screenshot", "स्क्रीनशॉट", "screen shot")) return shot() ?: "स्क्रीनशॉट ले लिया"
        if (hw("lock") || has("लॉक", "स्क्रीन बंद")) return lockScreen() ?: "फोन लॉक कर दिया"
        if (hw("back") || has("wapas", "wapis", "पीछे", "वापस", "पिछला")) return global(AccessibilityService.GLOBAL_ACTION_BACK) ?: "ठीक है, पीछे आ गई"
        if (hw("home") || has("होम", "home screen")) return global(AccessibilityService.GLOBAL_ACTION_HOME) ?: "होम पर आ गई"
        if (has("neeche", "नीचे", "scroll down")) return swipe(true) ?: "नीचे स्क्रॉल किया"
        if (has("upar", "ऊपर", "scroll up")) return swipe(false) ?: "ऊपर स्क्रॉल किया"

        // ---- camera se photo ----
        if (has("photo", "फोटो", "फ़ोटो", "selfie", "सेल्फी", "picture", "तस्वीर") &&
            (hw("lo", "le", "lena", "take", "click", "ले", "लो", "लेना") || has("kheench", "khinch", "खींच"))) {
            val selfie = has("selfie", "सेल्फी")
            return takePhoto(selfie) ?: "कैमरा खोलकर फ़ोटो ले रही हूँ"
        }

        // ---- likhna, enter, tap ----
        val typeM = Regex("^(?:likho|type|लिखो|लिखिए|टाइप करो|टाइप)\\s+(.+)$").find(t)
        if (typeM != null) return typeText(typeM.groupValues[1].trim()) ?: "लिख दिया"
        if (hw("enter") || has("एंटर")) return pressEnter() ?: "एंटर दबा दिया"
        val clickM = Regex("(.+?)\\s+(?:par|pe|पर|पे)\\s+(?:click|tap|dabao|press|क्लिक|टैप|दबाओ|दबाइए)").find(t)
            ?: Regex("^(?:click on|click|tap on|tap|press|क्लिक|टैप)\\s+(.+)$").find(t)
        if (clickM != null) {
            val name = clickM.groupValues[1].trim()
            return click(name) ?: "$name दबा दिया"
        }

        // ---- YouTube / Google / Maps (inke liye internet chahiye) ----
        if (has("youtube", "यूट्यूब") && has(*searchWords)) {
            val q = strip(t, "youtube", "यूट्यूब", "par", "pe", "पर", "पे", "mein", "में", "search", "karo", "kar", "do",
                "khojo", "khoj", "dhundo", "dhoondo", "सर्च", "करो", "कर", "दो", "खोजो", "खोज", "ढूंढो", "ढूँढो",
                "चलाओ", "chalao", "play", "बजाओ", "bajao", "ka", "की", "का", "ke", "के")
            return youtubeSearch(q) ?: "YouTube पर $q खोज रही हूँ"
        }
        if (has("google", "गूगल") && has(*searchWords)) {
            val q = strip(t, "google", "गूगल", "par", "pe", "पर", "पे", "mein", "में", "search", "karo", "kar", "do",
                "khojo", "khoj", "dhundo", "dhoondo", "सर्च", "करो", "कर", "दो", "खोजो", "खोज", "ढूंढो", "ढूँढो", "ka", "की", "का", "ke", "के")
            return webSearch(q) ?: "गूगल पर $q खोज रही हूँ"
        }
        if (has("navigate", "navigation", "रास्ता", "directions", "नक्शा", "route") || hw("map") || hw("maps") || has("मैप")) {
            val q = strip(t, "navigate", "navigation", "रास्ता", "directions", "route", "map", "maps", "नक्शा", "मैप", "में", "mein",
                "ka", "का", "ke", "के", "liye", "लिए", "par", "पर", "jao", "जाओ", "chalo", "चलो", "ko", "को", "karo", "करो",
                "dikhao", "दिखाओ", "dikha", "दिखा")
            return navigate(q) ?: "$q का रास्ता खोल रही हूँ"
        }

        // ---- SMS ----
        val smsM = Regex("(.+?)\\s+(?:ko|को)\\s+(?:message|sms|मैसेज|एसएमएस)\\s+(?:bhejo|karo|भेजो|करो)\\s*(.*)").find(t)
        if (smsM != null) {
            val name = smsM.groupValues[1].trim()
            return sms(name, smsM.groupValues[2].trim()) ?: "$name को मैसेज भेज दिया"
        }

        // ---- call ----
        if (has("call", "कॉल", "फोन", "फ़ोन", "dial") || hw("phone")) {
            val name = strip(t, "call", "karo", "kar", "do", "lagao", "phone", "dial", "ko", "कॉल", "करो", "कर", "दो",
                "लगाओ", "फोन", "फ़ोन", "को", "मिलाओ", "milao", "baat", "बात", "karao", "कराओ", "se", "से", "maya", "माया")
            return call(name) ?: "$name को कॉल कर रही हूँ"
        }

        // ---- ऐप kholna ----
        if (has("kholo", "खोलो", "खोलिए", "खोल", "चालू करो", "चलाओ", "launch", "शुरू करो", "shuru karo") || hw("open") || hw("start")) {
            val name = strip(t, "kholo", "open", "khol", "do", "karo", "खोलो", "खोलिए", "खोल", "दो", "चालू", "करो", "start",
                "launch", "चलाओ", "शुरू", "shuru", "ऐप", "एप", "app", "application", "please", "कृपया", "ko", "को", "maya", "माया", "ji", "जी")
            return openApp(name) ?: "$name खोल रही हूँ"
        }

        return "मुझे समझ नहीं आया, फिर से बोलिए"
    }

    // ---------- madad wale functions ----------
    private fun strip(text: String, vararg words: String): String {
        var s = " $text "
        words.forEach { s = s.replace(Regex("(?<=\\s)${Regex.escape(it)}(?=\\s)"), " ") }
        return s.trim().replace(Regex("\\s+"), " ")
    }

    private fun timeText() =
        "अभी " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + " हुए हैं"

    private fun vol(up: Boolean, times: Int) {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        repeat(times) {
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

    private fun openPage(action: String) {
        try { open(Intent(action)) } catch (_: Exception) { open(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun page(action: String, ok: String): String {
        openPage(action)
        return ok
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

    private fun typeText(t: String): String? {
        if (t.isBlank()) return "क्या लिखूँ?"
        val s = acc() ?: return noAcc()
        return if (s.typeText(t)) null else "कोई टेक्स्ट बॉक्स नहीं मिला"
    }

    private fun pressEnter(): String? {
        val s = acc() ?: return noAcc()
        return if (s.pressEnter()) null else "एंटर नहीं दबा पाई"
    }

    private fun readAloud(): String {
        val s = acc() ?: return noAcc()
        val labels = s.readScreen().lines().mapNotNull { l ->
            val p = l.split("|", limit = 3)
            if (p.size == 3 && p[2].isNotBlank()) p[2] else null
        }.distinct().take(12)
        return if (labels.isEmpty()) "स्क्रीन पर पढ़ने लायक कुछ नहीं मिला"
        else "स्क्रीन पर ये है: " + labels.joinToString(", ")
    }

    /** Camera kholkar shutter dabata hai (Accessibility chalu honi chahiye) */
    private fun takePhoto(selfie: Boolean): String? {
        val i = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        if (selfie) {
            i.putExtra("android.intent.extras.CAMERA_FACING", 1)
            i.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        }
        try { open(i) } catch (e: Exception) { return "कैमरा नहीं खुल पाया" }
        val s = acc() ?: return "कैमरा खोल दिया, फ़ोटो लेने के लिए पहले Accessibility चालू कीजिए"
        val h = Handler(Looper.getMainLooper())
        val labels = listOf("shutter", "take photo", "take picture", "capture", "शटर", "फ़ोटो लें", "फोटो लें", "कैप्चर")
        var tries = 0
        fun attempt() {
            tries++
            val hit = labels.any { s.clickText(it) }
            if (!hit && tries < 4) h.postDelayed({ attempt() }, 1000)
        }
        h.postDelayed({ attempt() }, 1800)
        return null
    }

    private fun youtubeSearch(q: String): String? {
        if (q.isBlank()) return "क्या खोजूँ?"
        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))
        return try {
            open(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube")); null
        } catch (e: Exception) {
            try { open(Intent(Intent.ACTION_VIEW, uri)); null } catch (e2: Exception) { "YouTube नहीं खुल पाया" }
        }
    }

    private fun webSearch(q: String): String? {
        if (q.isBlank()) return "क्या खोजूँ?"
        return try {
            open(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))); null
        } catch (e: Exception) { "सर्च नहीं खुल पाया" }
    }

    private fun navigate(place: String): String? {
        if (place.isBlank()) return "कहाँ जाना है?"
        return try {
            open(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place)))
                .setPackage("com.google.android.apps.maps")); null
        } catch (e: Exception) {
            try { open(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)))); null }
            catch (e2: Exception) { "नक्शा नहीं खुल पायापाया" }
        }
    }

    // ---------- milte-julte naam pehchanna (offline) ----------
    /** Do naamon mein kitna farak hai (kam = zyada milte-julte) */
    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    /** Naam ka "dhaancha": Hindi ko English banao, swar hatao (Rahul / राहुल / Rahool sab "rl") */
    private fun skel(s: String): String {
        val t = Translit.to(s).lowercase(Locale.ROOT)
            .replace('w', 'v').replace("ph", "f").replace("ck", "k").replace('q', 'k').replace('z', 'j')
        val sb = StringBuilder()
        for (ch in t) {
            if (!ch.isLetter()) continue
            if ("aeiouhy".indexOf(ch) >= 0) continue
            if (sb.isNotEmpty() && sb[sb.length - 1] == ch) continue
            sb.append(ch)
        }
        return sb.toString()
    }

    private fun okDist(d: Int, len: Int) = d == 0 || (d <= 1 && len >= 4) || (d <= 2 && len >= 7)

    private fun openApp(spoken: String): String? {
        if (spoken.isBlank()) return "कौन सा ऐप खोलूँ?"
        val key = spoken.lowercase(Locale.ROOT).trim()
        val name = aliases[key] ?: key
        val pm = c.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        var hit: ResolveInfo? = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
        if (hit == null) {
            val q = skel(name)
            if (q.length >= 2) {
                var best: ResolveInfo? = null
                var bd = Int.MAX_VALUE
                for (a in apps) {
                    val d = lev(q, skel(a.loadLabel(pm).toString()))
                    if (d < bd) { bd = d; best = a }
                }
                if (best != null && okDist(bd, q.length)) hit = best
            }
        }
        val found = hit ?: return "$spoken ऐप नहीं मिला"
        val launch = pm.getLaunchIntentForPackage(found.activityInfo.packageName) ?: return "ऐप नहीं खुल पाया"
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
        // seedha naam nahi mila: milte-julte naam dhundo (Hindi / English dono)
        val q = skel(name)
        if (q.length < 2) return null
        var best: String? = null
        var bestD = Int.MAX_VALUE
        c.contentResolver.query(Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            null, null, null)?.use {
            while (it.moveToNext()) {
                val full = it.getString(0) ?: ""
                val num = it.getString(1) ?: continue
                val first = full.trim().split(" ").firstOrNull() ?: full
                val dist = minOf(lev(q, skel(full)), lev(q, skel(first)))
                if (dist < bestD) { bestD = dist; best = num }
            }
        }
        val found = best
        return if (found != null && okDist(bestD, q.length)) found else null
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
