package com.maya.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Offline bhasha-model: ek baar download, phir bina internet ke chalte hain */
object VoskModels {
    class Entry(val lang: String, val folder: String)

    private const val BASE = "https://alphacephei.com/vosk/models/"

    val list = listOf(
        Entry("hi-IN", "vosk-model-small-hi-0.22"),
        Entry("en-IN", "vosk-model-small-en-in-0.4"),
        Entry("en-US", "vosk-model-small-en-us-0.15"),
        Entry("gu-IN", "vosk-model-small-gu-0.42"),
        Entry("te-IN", "vosk-model-small-te-0.42"),
        Entry("es-ES", "vosk-model-small-es-0.42"),
        Entry("fr-FR", "vosk-model-small-fr-0.22"),
        Entry("de-DE", "vosk-model-small-de-0.15"),
        Entry("ru-RU", "vosk-model-small-ru-0.22"),
        Entry("zh-CN", "vosk-model-small-cn-0.22"),
        Entry("ja-JP", "vosk-model-small-ja-0.22"),
        Entry("pt-BR", "vosk-model-small-pt-0.3"),
        Entry("it-IT", "vosk-model-small-it-0.22"),
        Entry("tr-TR", "vosk-model-small-tr-0.3")
    )

    fun entry(lang: String): Entry? = list.firstOrNull { it.lang == lang }

    /** Model download ho chuka ho to uska folder, warna null */
    fun dir(ctx: Context, lang: String): File? {
        val e = entry(lang) ?: return null
        val d = File(ctx.filesDir, "vosk/" + e.folder)
        return if (File(d, "am").isDirectory) d else null
    }

    /** Model download karke kholta hai. Background thread par chalana hai. null = sab theek, warna galti ka text */
    fun download(ctx: Context, lang: String, status: (String) -> Unit): String? {
        val e = entry(lang) ?: return "is bhasha ka offline model nahi hai"
        val root = File(ctx.filesDir, "vosk")
        root.mkdirs()
        val zip = File(ctx.cacheDir, e.folder + ".zip")
        try {
            val con = URL(BASE + e.folder + ".zip").openConnection() as HttpURLConnection
            con.connectTimeout = 15000
            con.readTimeout = 30000
            if (con.responseCode != 200) return "download fail: HTTP " + con.responseCode
            val total = con.contentLength.toLong()
            con.inputStream.use { inp ->
                zip.outputStream().use { out ->
                    val b = ByteArray(65536)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = inp.read(b)
                        if (n < 0) break
                        out.write(b, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) { last = pct; status("Download $pct%") }
                        }
                    }
                }
            }
            status("Model khol rahi hoon...")
            val canon = root.canonicalPath + File.separator
            ZipInputStream(zip.inputStream().buffered()).use { zin ->
                var z = zin.nextEntry
                while (z != null) {
                    val f = File(root, z.name)
                    if (!f.canonicalPath.startsWith(canon)) throw Exception("galat zip")
                    if (z.isDirectory) {
                        f.mkdirs()
                    } else {
                        f.parentFile?.mkdirs()
                        f.outputStream().use { out -> zin.copyTo(out) }
                    }
                    zin.closeEntry()
                    z = zin.nextEntry
                }
            }
            zip.delete()
            return if (dir(ctx, lang) != null) null else "model khulne ke baad bhi nahi mila"
        } catch (ex: Exception) {
            zip.delete()
            return "download fail: " + (ex.message ?: ex.toString())
        }
    }
}

/**
 * Ek baar mic khulta hai aur chalta rehta hai (Google ka recognizer nahi, isliye tik-tik / jhapakna nahi).
 * Awaaz phone ke andar hi text banti hai (internet nahi chahiye).
 * muted = true ho to awaaz fenk di jaati hai (Maya bol rahi ho tab) par mic khula rehta hai.
 */
class VoskEar(
    private val ctx: Context,
    private val lang: String,
    private val onText: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    @Volatile private var running = false
    @Volatile var muted = false

    fun start() {
        if (running) return
        running = true
        Thread { loop() }.start()
    }

    fun stop() { running = false }

    @SuppressLint("MissingPermission")
    private fun loop() {
        var model: Model? = null
        var rec: Recognizer? = null
        var ar: AudioRecord? = null
        try {
            val dir = VoskModels.dir(ctx, lang) ?: throw Exception("model download nahi hua")
            model = Model(dir.absolutePath)
            rec = Recognizer(model, 16000f)
            val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            ar = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 16000))
            if (ar.state != AudioRecord.STATE_INITIALIZED) throw Exception("mic nahi khul paya")
            ar.startRecording()
            val buf = ShortArray(2000)               // 0.125 second
            var wasMuted = false
            while (running) {
                val n = ar.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (muted) {
                    if (!wasMuted) { rec.finalResult; wasMuted = true }
                    continue
                }
                wasMuted = false
                if (rec.acceptWaveForm(buf, n)) {
                    val t = JSONObject(rec.result).optString("text", "").trim()
                    if (t.isNotEmpty()) onText(t)
                }
            }
        } catch (e: Throwable) {
            if (running) onError(e.message ?: e.toString())
        } finally {
            running = false
            try { ar?.stop() } catch (_: Exception) {}
            ar?.release()
            try { rec?.close() } catch (_: Throwable) {}
            try { model?.close() } catch (_: Throwable) {}
        }
    }
}
