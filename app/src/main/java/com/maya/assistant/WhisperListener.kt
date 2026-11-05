package com.maya.assistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mic se ek baar ki baat record karta hai (chup hone par ruk jata hai)
 * aur Groq Whisper se text banata hai. Bhasha apne aap pehchanta hai.
 * Ise background thread par chalana hai (main thread par nahi).
 */
class WhisperListener(private val key: String) {

    @Volatile private var stopped = false
    fun stop() { stopped = true }

    private val api = "https://api.groq.com/openai/v1/audio/transcriptions"

    /** lang = null  → bhasha apne aap pehchano.  null lautaye to kuch suna nahi gaya. */
    @SuppressLint("MissingPermission")
    fun listenOnce(lang: String?): String? {
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 8192))
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw Exception("mic nahi khul paya")
        }
        val chunk = ShortArray(1600)            // 0.1 second
        val pcm = ByteArrayOutputStream()
        var prev: ByteArray? = null
        var speaking = false
        var silent = 0
        var waited = 0
        var total = 0
        var calib = 0
        var floor = 0.0
        var thr = 700.0
        rec.startRecording()
        try {
            while (!stopped) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) { val v = chunk[i].toDouble(); sum += v * v }
                val rms = Math.sqrt(sum / n)
                val b = bytes(chunk, n)
                if (calib < 3) {                // shuru ke 0.3 second se shor ka andaza
                    floor += rms / 3
                    calib++
                    if (calib == 3) thr = maxOf(700.0, floor * 2.5)
                    prev = b
                    continue
                }
                val loud = rms > thr
                if (!speaking) {
                    if (loud) {
                        speaking = true
                        silent = 0
                        prev?.let { pcm.write(it) }
                        pcm.write(b)
                    } else {
                        prev = b
                        waited++
                        if (waited > 80) return null    // 8 second kuch nahi bola
                    }
                } else {
                    pcm.write(b)
                    total++
                    if (loud) silent = 0 else silent++
                    if (silent >= 12 || total > 150) break   // 1.2 second chup ya 15 second poore
                }
            }
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
        }
        if (!speaking || stopped) return null
        val data = pcm.toByteArray()
        if (data.size < 16000) return null       // aadhe second se chhota
        val text = transcribe(wav(data, rate), lang) ?: return null
        return if (text.length < 2) null else text
    }

    /** Pehle se record ki hui awaaz (16 kHz, 16-bit, mono PCM) ko text banata hai. Background thread par chalana hai. */
    fun transcribePcm(pcm: ByteArray, lang: String?): String? {
        if (pcm.size < 16000) return null
        val text = transcribe(wav(pcm, 16000), lang) ?: return null
        return if (text.length < 2) null else text
    }

    private fun bytes(c: ShortArray, n: Int): ByteArray {
        val b = ByteArray(n * 2)
        for (i in 0 until n) {
            b[2 * i] = (c[i].toInt() and 0xFF).toByte()
            b[2 * i + 1] = ((c[i].toInt() shr 8) and 0xFF).toByte()
        }
        return b
    }

    private fun wav(pcm: ByteArray, rate: Int): ByteArray {
        val bb = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + pcm.size); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
        bb.putInt(rate); bb.putInt(rate * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(pcm.size); bb.put(pcm)
        return bb.array()
    }

    private fun transcribe(wav: ByteArray, lang: String?): String? {
        for (model in listOf("whisper-large-v3-turbo", "whisper-large-v3")) {
            val boundary = "----maya" + System.currentTimeMillis()
            val out = ByteArrayOutputStream()
            fun field(name: String, value: String) {
                out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n").toByteArray())
            }
            field("model", model)
            field("response_format", "json")
            field("temperature", "0")
            if (lang != null) field("language", lang)
            out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n" +
                "Content-Type: audio/wav\r\n\r\n").toByteArray())
            out.write(wav)
            out.write("\r\n--$boundary--\r\n".toByteArray())

            val con = (URL(api).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            con.outputStream.use { it.write(out.toByteArray()) }
            val code = con.responseCode
            val stream = if (code in 200..299) con.inputStream else con.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code in 200..299) {
                val t = JSONObject(resp).optString("text", "").trim()
                return if (t.isEmpty()) null else t
            }
            if (code == 404 || code == 400) continue
            throw Exception("Whisper HTTP $code")
        }
        throw Exception("Whisper model nahi mila")
    }
}
