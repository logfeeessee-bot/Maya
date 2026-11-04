package com.maya.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class MayaService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_TOGGLE = "maya.toggle"
        const val ACTION_TEXT = "maya.text"
        @Volatile var state = 0   // 0 band, 1 sun rahi hai (chhota gola), 2 bol rahi hai (bada gola)
        val listeners = CopyOnWriteArrayList<(Int) -> Unit>()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val ear = Executors.newSingleThreadExecutor()
    private lateinit var wm: WindowManager
    private lateinit var cmd: CommandHandler
    private var orb: OrbView? = null
    private var glow: EdgeGlowView? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: SpeechRecognizer? = null
    private var listening = false
    private var orbX = 0
    private var orbY = 0
    @Volatile private var cancel = false
    private var whisper: WhisperListener? = null
    @Volatile private var offlineUntil = 0L
    @Volatile private var toldOffline = false
    private var failStreak = 0
    private var lastLangToast = 0L
    private var vosk: VoskEar? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("maya", "Maya", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)

        val pi = PendingIntent.getService(
            this, 0, Intent(this, MayaService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, "maya")
            .setContentTitle("Maya chal rahi hai")
            .setContentText("Mic on/off karne ke liye neeche dabao")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_btn_speak_now), "Mic on/off", pi).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        cmd = CommandHandler(this)
        orbX = dp(220); orbY = dp(300)
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status == TextToSpeech.SUCCESS) {
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { handler.post { afterSpeech() } }
                @Deprecated("x") override fun onError(id: String?) { handler.post { afterSpeech() } }
            })
            ttsReady = true
            applyTtsLanguage()
        }
    }

    private fun applyTtsLanguage() {
        tts?.language = Locale.forLanguageTag(Prefs.lang(this))
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        when (i?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_TEXT -> i.getStringExtra("q")?.let { think(it) }
        }
        return START_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- Ring light: sirf mobile ke charon kinaron par ----------
    private fun ensureGlow() {
        if (glow != null || !Settings.canDrawOverlays(this)) return
        val g = EdgeGlowView(this).apply { alpha = 0f; visibility = android.view.View.GONE }
        val gp = WindowManager.LayoutParams(
            -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(g, gp)
        glow = g
    }

    // ---------- Floating particle gola: sirf mic ON ho tab dikhta hai ----------
    private fun showOrb() {
        if (orb != null || !Settings.canDrawOverlays(this)) return
        val size = dp(140)
        val o = OrbView(this).also { it.setColor(Prefs.orb(this)) }
        val op = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = orbX; y = orbY }

        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
        o.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = op.x; sy = op.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val mx = e.rawX - dx; val my = e.rawY - dy
                    if (Math.abs(mx) > 12 || Math.abs(my) > 12) moved = true
                    if (moved) {
                        op.x = sx + mx.toInt(); op.y = sy + my.toInt()
                        orbX = op.x; orbY = op.y
                        wm.updateViewLayout(o, op)
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) toggle()
            }
            true
        }
        o.mode = state
        wm.addView(o, op)
        orb = o
    }

    private fun hideOrb() {
        orb?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        orb = null
    }

    private fun setState(s: Int) {
        state = s
        orb?.mode = s
        glow?.show(listening)
        listeners.forEach { it(s) }
    }

    // ---------- Sunna ----------
    private fun toggle() { if (listening) stopListening() else startListening() }

    private fun startListening() {
        listening = true
        cancel = false
        failStreak = 0
        applyTtsLanguage()
        ensureGlow()
        glow?.color = Prefs.ring(this)
        showOrb()
        setState(1)
        if (Prefs.vosk(this)) startVosk() else recognize()
    }

    private fun stopListening() {
        listening = false
        cancel = true
        sr?.destroy(); sr = null
        whisper?.stop(); whisper = null
        vosk?.stop(); vosk = null
        tts?.stop()
        hideOrb()
        setState(0)
    }

    private fun recognize() {
        handler.post {
            if (!listening || vosk != null) return@post
            if (Prefs.whisper(this) && Prefs.key(this).isNotBlank() && !isOffline()) { recognizeWhisper(); return@post }
            sr?.destroy()
            sr = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(b: Bundle?) {
                        val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (t.isNullOrBlank()) recognize() else { failStreak = 0; think(t) }
                    }
                    override fun onError(e: Int) {
                        val now = System.currentTimeMillis()
                        when (e) {
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> {
                                // kuch nahi bola ya shor tha: chupchaap dobara sunna shuru (koi maafi nahi)
                                handler.postDelayed({ recognize() }, 400)
                            }
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                            SpeechRecognizer.ERROR_SERVER, 11 -> {
                                offlineUntil = now + 60000
                                retryLater()
                            }
                            12, 13 -> languageMissing()
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                toast("माइक की अनुमति नहीं है")
                                stopListening()
                            }
                            else -> retryLater()
                        }
                    }
                    override fun onReadyForSpeech(p: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(v: Float) {}
                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(b: Bundle?) {}
                    override fun onEvent(t: Int, b: Bundle?) {}
                })
                r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.lang(this))
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, isOffline())
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L))
            }
        }
    }

    // ---------- Vosk: ek baar mic chalu, chalta rahe (offline) ----------
    private fun startVosk() {
        val lang = Prefs.lang(this)
        if (VoskModels.dir(this, lang) == null) {
            toast("Vosk ka model download nahi hua, Google wale tareeke se sun rahi hoon")
            recognize()
            return
        }
        val holder = arrayOfNulls<VoskEar>(1)
        val engine = VoskEar(this, lang,
            { t -> handler.post { if (vosk === holder[0]) onHeard(t) } },
            { msg ->
                handler.post {
                    if (vosk === holder[0]) {
                        toast("Vosk galti: $msg")
                        vosk = null
                        if (listening) recognize()      // Vosk na chale to Google wale tareeke par laut jao
                    }
                }
            })
        holder[0] = engine
        vosk = engine
        engine.start()
    }

    // Sunai hui baat par kaam (agar "Maya" wala switch chalu hai to sirf "Maya ..." bolne par)
    private fun onHeard(t: String) {
        if (!listening) return
        var text = t
        if (Prefs.wake(this)) {
            val m = Regex("maya|maiya|mayaa|माया|मया|मैया", RegexOption.IGNORE_CASE).find(text) ?: return
            text = text.substring(m.range.last + 1).trim()
            if (text.isBlank()) { say("जी, बोलिए"); return }
        }
        think(text)
    }

    private fun isOffline() = System.currentTimeMillis() < offlineUntil

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()

    // Mic kabhi apne aap band nahi hoga: fail ho to dheere-dheere ruk kar dobara koshish
    private fun retryLater() {
        failStreak++
        if (failStreak == 8) toast("आवाज़ सुनने में दिक्कत है, कोशिश जारी है")
        val wait = minOf(1200L * (1 shl minOf(failStreak - 1, 3)), 10000L)
        handler.postDelayed({ recognize() }, wait)
    }

    private fun languageMissing() {
        if (System.currentTimeMillis() - lastLangToast > 60000) {
            lastLangToast = System.currentTimeMillis()
            toast("ऑफ़लाइन में यह भाषा नहीं है। Google की ऑफ़लाइन भाषा डाउनलोड कीजिए या इंटरनेट चालू कीजिए")
        }
        handler.postDelayed({ recognize() }, 8000)
    }

    // Sirf saaf-saaf "mic band" jaise shabdon se hi mic band hoga
    private fun isMicOffPhrase(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        return listOf("mic band", "mic off", "माइक बंद", "माइक बन्द", "माइक ऑफ", "maya band ho", "माया बंद हो", "maya sleep")
            .any { t.contains(it) }
    }

    // ---------- Whisper: har bhasha pehchanna ----------
    private fun recognizeWhisper() {
        if (!listening) return
        val w = WhisperListener(Prefs.key(this))
        whisper = w
        val lang = if (Prefs.autoLang(this)) null else Prefs.lang(this).substringBefore('-')
        ear.execute {
            try {
                val t = w.listenOnce(lang)
                handler.post {
                    if (!listening || whisper !== w) return@post
                    if (t.isNullOrBlank()) recognize() else think(t)
                }
            } catch (e: Exception) {
                handler.post {
                    if (whisper !== w) return@post
                    android.widget.Toast.makeText(this, "Whisper galti: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    if (listening) handler.postDelayed({ recognize() }, 2000)
                }
            }
        }
    }

    // Jawab jis lipi mein hai usi ki awaaz chunta hai (sirf Whisper + auto bhasha mein)
    private fun voiceLangFor(text: String): String {
        val base = Prefs.lang(this)
        if (!(Prefs.whisper(this) && Prefs.autoLang(this))) return base
        for (ch in text) {
            val c = ch.code
            when {
                c in 0x0900..0x097F -> return if (base == "mr-IN" || base == "ne-NP") base else "hi-IN"
                c in 0x0980..0x09FF -> return "bn-IN"
                c in 0x0A00..0x0A7F -> return "pa-IN"
                c in 0x0A80..0x0AFF -> return "gu-IN"
                c in 0x0B80..0x0BFF -> return "ta-IN"
                c in 0x0C00..0x0C7F -> return "te-IN"
                c in 0x0C80..0x0CFF -> return "kn-IN"
                c in 0x0D00..0x0D7F -> return "ml-IN"
                c in 0x0600..0x06FF -> return if (base == "ar-SA") base else "ur-IN"
                c in 0x0400..0x04FF -> return "ru-RU"
                c in 0x0E00..0x0E7F -> return "th-TH"
                c in 0x3040..0x30FF -> return "ja-JP"
                c in 0xAC00..0xD7AF -> return "ko-KR"
                c in 0x4E00..0x9FFF -> return "zh-CN"
            }
        }
        val latin = listOf("en-IN", "es-ES", "fr-FR", "de-DE", "pt-BR", "it-IT", "tr-TR", "id-ID", "vi-VN", "nl-NL", "pl-PL", "sw-KE")
        return if (base in latin) base else "en-IN"
    }

    // ---------- Soch (Groq AI) ----------
    private fun think(text: String) {
        if (isMicOffPhrase(text)) { stopListening(); return }
        val key = Prefs.key(this)
        if (key.isBlank() || isOffline()) { localReply(text); return }
        worker.execute {
            try {
                val j = GroqClient.ask(key, text, Prefs.langName(this))
                offlineUntil = 0L
                toldOffline = false
                val action = j.optString("action", "chat")
                val target = j.optString("target", "")
                val body = j.optString("text", "")
                val on = j.optBoolean("on", true)
                val reply = j.optString("reply", "")
                if (action == "agent") {
                    handler.post { sr?.destroy(); sr = null; setState(2) }
                    cancel = false
                    val result = runAgent(key, target.ifBlank { text })
                    handler.post { say(result) }
                } else if (action == "unclear" || action == "stop") {
                    // samajh nahi aaya ya "stop": kuch bole bina sunna jaari (mic band nahi hoga)
                    handler.post { afterSpeech() }
                } else {
                    handler.post {
                        val fail = cmd.execute(action, target, body, on)
                        say(fail ?: reply.ifBlank { "ठीक है" })
                    }
                }
            } catch (e: Exception) {
                val net = e is java.io.IOException
                if (net) offlineUntil = System.currentTimeMillis() + 45000
                handler.post {
                    if (net) {
                        if (!toldOffline) { toldOffline = true; toast("इंटरनेट नहीं है, ऑफ़लाइन मोड चालू") }
                    } else {
                        toast("Groq galti: ${e.message}")
                    }
                    localReply(text)
                }
            }
        }
    }

    // ---------- Agent: screen padhkar kai kadam khud chalana ----------
    private fun runAgent(key: String, goal: String): String {
        val done = ArrayList<String>()
        val lang = Prefs.langName(this)
        for (step in 1..12) {
            if (cancel) return "ठीक है, रोक दिया"
            val svc = MayaAccessibilityService.inst ?: return "पहले Accessibility सेवा चालू कीजिए"
            Thread.sleep(1200)
            val screen = svc.readScreen()
            val j = GroqClient.askAgent(key, goal, screen, done, lang)
            val a = j.optString("action", "fail")
            val idx = j.optInt("index", -1)
            val txt = j.optString("text", "")
            val tgt = j.optString("target", "")
            val rep = j.optString("reply", "")
            when (a) {
                "done" -> return rep.ifBlank { "काम हो गया" }
                "fail" -> return rep.ifBlank { "मुझसे यह नहीं हो पाया" }
                "click" -> done.add(if (svc.clickIndex(idx)) "clicked $idx" else "click $idx failed")
                "type" -> done.add(if (svc.typeIndex(idx, txt)) "typed '$txt'" else "typing failed")
                "enter" -> done.add(if (svc.pressEnter()) "pressed enter" else "enter failed")
                "scroll_down" -> { svc.swipe(true); done.add("scrolled down") }
                "scroll_up" -> { svc.swipe(false); done.add("scrolled up") }
                "back" -> { svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); done.add("went back") }
                "home" -> { svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); done.add("went home") }
                "open_app" -> { val f = cmd.execute("open_app", tgt, "", true); done.add(if (f == null) "opened $tgt" else "could not open $tgt") }
                else -> done.add("unknown action")
            }
        }
        return "बहुत कदम हो गए, काम पूरा नहीं हो पाया"
    }

    private fun localReply(text: String) {
        val r = cmd.handle(text)
        when {
            r == CommandHandler.STOP -> afterSpeech()
            r.startsWith("मुझे समझ नहीं आया") && listening -> afterSpeech()   // chup rehna
            else -> say(r)
        }
    }

    // ---------- Bolna ----------
    private fun say(text: String) {
        sr?.destroy(); sr = null
        whisper?.stop(); whisper = null
        vosk?.muted = true
        setState(2)
        if (ttsReady) {
            tts?.language = Locale.forLanguageTag(voiceLangFor(text))
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "maya")
        } else afterSpeech()
    }

    private fun afterSpeech() {
        if (listening) {
            setState(1)
            val v = vosk
            if (v != null) handler.postDelayed({ if (vosk === v) v.muted = false }, 600)
            else recognize()
        } else {
            vosk?.stop(); vosk = null
            hideOrb(); setState(0)
        }
    }

    override fun onDestroy() {
        hideOrb()
        glow?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        whisper?.stop()
        vosk?.stop()
        sr?.destroy(); tts?.shutdown(); worker.shutdown(); ear.shutdown()
        super.onDestroy()
    }
}
     
