package com.maya.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
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

class MayaService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_TOGGLE = "maya.toggle"
        const val ACTION_TEXT = "maya.text"
        @Volatile var state = 0   // 0 idle, 1 listening, 2 speaking
        val listeners = CopyOnWriteArrayList<(Int) -> Unit>()
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var cmd: CommandHandler
    private var orb: OrbView? = null
    private var glow: EdgeGlowView? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: SpeechRecognizer? = null
    private var listening = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("maya", "Maya", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = Notification.Builder(this, "maya")
            .setContentTitle("Maya chal rahi hai")
            .setContentText("Orb par tap karke bolna shuru karein")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        cmd = CommandHandler(this)
        tts = TextToSpeech(this, this)
        addOverlays()
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status == TextToSpeech.SUCCESS) {
            t.language = Locale("hi", "IN")
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { handler.post { afterSpeech() } }
                @Deprecated("x") override fun onError(id: String?) { handler.post { afterSpeech() } }
            })
            ttsReady = true
        }
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        when (i?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_TEXT -> i.getStringExtra("q")?.let { say(cmd.handle(it)) }
        }
        return START_STICKY
    }

    // ---------- Overlays: floating gola + ring light ----------
    private fun addOverlays() {
        if (!Settings.canDrawOverlays(this)) return
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        glow = EdgeGlowView(this).apply { alpha = 0f; visibility = android.view.View.GONE }
        val gp = WindowManager.LayoutParams(
            -1, -1, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(glow, gp)

        val size = dp(100)
        orb = OrbView(this)
        val op = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(240); y = dp(300) }

        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
        orb!!.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = op.x; sy = op.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val mx = e.rawX - dx; val my = e.rawY - dy
                    if (Math.abs(mx) > 12 || Math.abs(my) > 12) moved = true
                    if (moved) { op.x = sx + mx.toInt(); op.y = sy + my.toInt(); wm.updateViewLayout(orb, op) }
                }
                MotionEvent.ACTION_UP -> if (!moved) toggle()
            }
            true
        }
        wm.addView(orb, op)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun setState(s: Int) {
        state = s
        orb?.mode = s
        glow?.show(listening)
        listeners.forEach { it(s) }
    }

    // ---------- Listening ----------
    private fun toggle() { if (listening) stopListening() else startListening() }

    private fun startListening() {
        listening = true
        setState(1)
        recognize()
    }

    private fun stopListening() {
        listening = false
        sr?.destroy(); sr = null
        tts?.stop()
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
                        if (t.isNullOrBlank()) recognize() else onHeard(t)
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
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN"))
            }
        }
    }

    private fun onHeard(text: String) {
        val reply = cmd.handle(text)
        if (reply == CommandHandler.STOP) {
            say("ठीक है, मैं बंद हो रही हूँ")
            listening = false
        } else say(reply)
    }

    // ---------- Speaking ----------
    private fun say(text: String) {
        sr?.destroy(); sr = null
        setState(2)
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "maya")
        else afterSpeech()
    }

    private fun afterSpeech() {
        if (listening) { setState(1); recognize() } else setState(0)
    }

    override fun onDestroy() {
        orb?.let { wm.removeView(it) }
        glow?.let { wm.removeView(it) }
        sr?.destroy(); tts?.shutdown()
        super.onDestroy()
    }
}
