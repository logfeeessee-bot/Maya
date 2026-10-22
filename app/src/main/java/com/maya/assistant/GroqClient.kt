package com.maya.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Groq AI se baat. Key Settings mein daalni hai (code mein kabhi mat likhna). */
object GroqClient {
    private const val API = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"   // agar Groq purana model band kare to yahan naya naam likhna
    private val history = ArrayList<Pair<String, String>>()

    private const val SYSTEM = """You are Maya, a friendly voice assistant that controls an Android phone.
Always answer ONLY with one JSON object, nothing else:
{"action":"...","target":"...","text":"...","on":true,"reply":"..."}
action must be one of: chat, open_app, call, sms, torch, volume_up, volume_down, back, home, recents, notifications, screenshot, lock, scroll_down, scroll_up, click_text, wifi, bluetooth, settings, time, stop.
target = app name, contact name, or text on the screen to tap. text = the SMS message body. on = true/false for the torch.
reply = one short natural sentence to speak aloud, in the language the user speaks, with no emoji and no symbols.
For normal questions or chat use action "chat" and put the answer in reply (max 3 short sentences).
Use "stop" if the user asks you to stop, sleep or be quiet."""

    fun ask(key: String, text: String, lang: String): JSONObject {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system")
            .put("content", SYSTEM + "\nThe user's chosen language: $lang. Reply in the language the user speaks."))
        history.takeLast(8).forEach { msgs.put(JSONObject().put("role", it.first).put("content", it.second)) }
        msgs.put(JSONObject().put("role", "user").put("content", text))

        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", msgs)
            .put("temperature", 0.3)
            .put("response_format", JSONObject().put("type", "json_object"))

        val con = (URL(API).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
        }
        con.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = con.responseCode
        val resp = (if (code in 200..299) con.inputStream else con.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) throw Exception("HTTP $code")

        val content = JSONObject(resp).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        val out = JSONObject(content)
        history.add("user" to text)
        history.add("assistant" to content)
        while (history.size > 16) history.removeAt(0)
        return out
    }
}
