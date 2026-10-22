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
        applyTtsLanguage()
        ensureGlow()
        glow?.color = Prefs.ring(this)
        showOrb()
        setState(1)
        recognize()
    }

    private fun stopListening() {
        listening = false
        sr?.destroy(); sr = null
        tts?.stop()
        hideOrb()
        setState(0)
    }

    private fun recognize() {
        handler.post {
            if (!listening) return@post
            sr?.destroy()
            sr = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(b: Bundle?) {
                        val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (t.isNullOrBlank()) recognize() else think(t)
                    }
                    override fun onError(e: Int) { handler.postDelayed({ recognize() }, 600) }
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
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.lang(this)))
            }
        }
    }

    // ---------- Soch (Groq AI) ----------
    private fun think(text: String) {
        val key = Prefs.key(this)
        if (key.isBlank()) { localReply(text); return }
        worker.execute {
            try {
                val j = GroqClient.ask(key, text, Prefs.langName(this))
                val action = j.optString("action", "chat")
                val target = j.optString("target", "")
                val body = j.optString("text", "")
                val on = j.optBoolean("on", true)
                val reply = j.optString("reply", "")
                handler.post {
                    if (action == "stop") {
                        listening = false
                        say(reply.ifBlank { "ठीक है" })
                    } else {
                        val fail = cmd.execute(action, target, body, on)
                        say(fail ?: reply.ifBlank { "ठीक है" })
                    }
                }
            } catch (e: Exception) {
                handler.post { localReply(text) }   // internet/key na ho to purane rules
            }
        }
    }

    private fun localReply(text: String) {
        val r = cmd.handle(text)
        if (r == CommandHandler.STOP) { listening = false; say("ठीक है") } else say(r)
    }

    // ---------- Bolna ----------
    private fun say(text: String) {
        sr?.destroy(); sr = null
        setState(2)
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "maya")
        else afterSpeech()
    }

    private fun afterSpeech() {
        if (listening) { setState(1); recognize() }
        else { hideOrb(); setState(0) }
    }

    override fun onDestroy() {
        hideOrb()
        glow?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        sr?.destroy(); tts?.shutdown(); worker.shutdown()
        super.onDestroy()
    }
}
