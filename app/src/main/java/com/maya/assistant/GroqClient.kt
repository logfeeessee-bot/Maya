package com.maya.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Groq AI se baat. Key Settings mein daalni hai (code mein kabhi mat likhna). */
object GroqClient {
    private const val BASE = "https://api.groq.com/openai/v1"
    private var model: String? = null
    private val history = ArrayList<Pair<String, String>>()

    private val preferred = listOf(
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "openai/gpt-oss-20b",
        "openai/gpt-oss-120b",
        "meta-llama/llama-4-scout-17b-16e-instruct",
        "qwen/qwen3-32b"
    )

    private const val SYSTEM = """You are Maya, a friendly voice assistant that controls an Android phone.
Always answer ONLY with one JSON object, nothing else:
{"action":"...","target":"...","text":"...","on":true,"reply":"..."}
action must be one of: chat, open_app, call, sms, torch, volume_up, volume_down, back, home, recents, notifications, screenshot, lock, scroll_down, scroll_up, click_text, wifi, bluetooth, settings, time, youtube_search, web_search, navigate, type_text, press_enter, unclear, stop.
target = app name, contact name, or text on the screen to tap. For youtube_search and web_search, target = the search words only. For navigate, target = the place name.
text = the SMS message body, or the words to type for type_text. on = true/false for the torch.
If the user says several things in one sentence, pick the one action that does the whole job. Example: "open YouTube and search tiki tiki song" is youtube_search with target "tiki tiki song".
reply = one short natural sentence to speak aloud, in the language the user speaks, with no emoji and no symbols.
The user speaks Hindi, English or Hinglish (Hindi words in Roman letters). Speech recognition can make small mistakes, so guess the most likely meaning.
If the words are really meaningless or you cannot tell what the user wants, use action "unclear" and politely ask them to repeat, for example in Hindi: माफ़ कीजिए, मुझे आपकी आवाज़ समझ नहीं आई, कृपया फिर से बोलिए.
For normal questions or chat use action "chat" and put the answer in reply (max 3 short sentences).
Use "stop" if the user asks you to stop, sleep or be quiet."""

    private fun http(key: String, method: String, path: String, body: JSONObject?): Pair<Int, String> {
        val con = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 25000
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
            if (body != null) doOutput = true
        }
        if (body != null) con.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = con.responseCode
        val stream = if (code in 200..299) con.inputStream else con.errorStream
        val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return Pair(code, resp)
    }

    /** Groq ki chalu models ki list mangkar sahi model chunta hai */
    private fun pickModel(key: String): String {
        val (code, resp) = http(key, "GET", "/models", null)
        if (code !in 200..299) throw Exception("models HTTP $code")
        val arr = JSONObject(resp).getJSONArray("data")
        val ids = (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
        preferred.firstOrNull { it in ids }?.let { return it }
        val bad = listOf("whisper", "guard", "tts", "orpheus", "playai", "safeguard", "embed")
        return ids.firstOrNull { id -> bad.none { id.contains(it, true) } }
            ?: throw Exception("koi model nahi mila")
    }

    fun ask(key: String, text: String, lang: String): JSONObject {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system")
            .put("content", SYSTEM + "\nThe user's chosen language: $lang. Reply in the language the user speaks."))
        history.takeLast(8).forEach { msgs.put(JSONObject().put("role", it.first).put("content", it.second)) }
        msgs.put(JSONObject().put("role", "user").put("content", text))

        var useJson = true
        var lastErr = "unknown"
        repeat(3) {
            val m = model ?: pickModel(key).also { model = it }
            val body = JSONObject()
                .put("model", m)
                .put("messages", msgs)
                .put("temperature", 0.3)
            if (useJson) body.put("response_format", JSONObject().put("type", "json_object"))

            val (code, resp) = http(key, "POST", "/chat/completions", body)
            if (code in 200..299) {
                val content = JSONObject(resp).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                val s = content.indexOf('{')
                val e = content.lastIndexOf('}')
                val out = if (s >= 0 && e > s) JSONObject(content.substring(s, e + 1))
                else JSONObject().put("action", "chat").put("reply", content.trim())
                history.add("user" to text)
                history.add("assistant" to out.toString())
                while (history.size > 16) history.removeAt(0)
                return out
            }
            lastErr = "HTTP $code ${resp.take(100)}"
            when (code) {
                404 -> model = null
                400 -> if (useJson) useJson = false else model = null
                else -> throw Exception(lastErr)
            }
        }
        throw Exception(lastErr)
    }
}
