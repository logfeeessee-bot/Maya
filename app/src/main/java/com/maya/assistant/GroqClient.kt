package com.maya.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Groq AI se baat. Key Settings mein daalni hai (code mein kabhi mat likhna). */
object GroqClient {
    private const val BASE = "https://api.groq.com/openai/v1"
    // true = tez wala model (aam awaaz command), false = samajhdaar wala (agent / kai kadam ke kaam)
    private val models = HashMap<Boolean, String>()
    private val history = ArrayList<Pair<String, String>>()

    private val preferredFast = listOf(
        "llama-3.1-8b-instant",
        "llama-3.3-70b-versatile",
        "openai/gpt-oss-20b",
        "meta-llama/llama-4-scout-17b-16e-instruct",
        "openai/gpt-oss-120b",
        "qwen/qwen3-32b"
    )

    private val preferredSmart = listOf(
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
action must be one of: chat, open_app, call, sms, torch, volume_up, volume_down, back, home, recents, notifications, screenshot, lock, scroll_down, scroll_up, click_text, wifi, bluetooth, settings, time, youtube_search, web_search, navigate, type_text, press_enter, agent, unclear, stop.
target = app name, contact name, or text on the screen to tap. For youtube_search and web_search, target = the search words only. For navigate, target = the place name.
For call and sms, write the contact name in target using Latin letters (transliterate Hindi names, for example the Hindi spelling of Rahul becomes Rahul).
text = the SMS message body, or the words to type for type_text. on = true/false for the torch.
Use action agent when the job needs several steps inside an app or on the screen, for example open Instagram and search for something, or open Settings and turn something on. For agent, target = the whole goal written as one clear sentence.
For YouTube searches always use youtube_search, not agent.
reply = one short natural sentence to speak aloud, in the language the user speaks, with no emoji and no symbols.
The user speaks Hindi, English or Hinglish (Hindi words in Roman letters). Speech recognition can make small mistakes, so guess the most likely meaning.
If the words are really meaningless or you cannot tell what the user wants, use action unclear and politely ask them to repeat, for example in Hindi: माफ़ कीजिए, मुझे आपकी आवाज़ समझ नहीं आई, कृपया फिर से बोलिए.
For normal questions or chat use action chat and put the answer in reply (max 3 short sentences).
Use stop if the user asks you to stop, sleep or be quiet."""

    private const val AGENT = """You control the screen of an Android phone for the user and finish one goal step by step.
You get the current screen as lines in the form index|kind|label. kind click or edit can be used by its index. Lines with index - and kind text are only for reading.
Reply ONLY with one JSON object:
{"action":"click","index":0,"text":"","target":"","reply":""}
action must be one of: click, type, enter, scroll_down, scroll_up, back, home, open_app, done, fail.
click = tap the element with that index. type = write text into the edit element with that index. enter = press the search or enter key. open_app = target is the app name. done = the goal is finished and reply is one short sentence in the user's language about what was done. fail = it cannot be done and reply is a short reason in the user's language.
Do exactly ONE step at a time. If the last step changed nothing, try a different way. If you need to type but no edit box is visible, click the search icon or search bar first.
Never make payments, purchases or money transfers and never delete data. Use fail for those."""

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

    /** Groq ki chalu models ki list mangkar pasand ke hisaab se sahi model chunta hai */
    private fun pickModel(key: String, order: List<String>): String {
        val (code, resp) = http(key, "GET", "/models", null)
        if (code !in 200..299) throw Exception("models HTTP $code")
        val arr = JSONObject(resp).getJSONArray("data")
        val ids = (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
        order.firstOrNull { it in ids }?.let { return it }
        val bad = listOf("whisper", "guard", "tts", "orpheus", "playai", "safeguard", "embed")
        return ids.firstOrNull { id -> bad.none { id.contains(it, true) } }
            ?: throw Exception("koi model nahi mila")
    }

    /** Groq ko messages bhejta hai aur jawab ka text wapas deta hai */
    private fun chat(key: String, msgs: JSONArray, fast: Boolean): String {
        var useJson = true
        var lastErr = "unknown"
        repeat(3) {
            val m = models[fast] ?: pickModel(key, if (fast) preferredFast else preferredSmart).also { models[fast] = it }
            val body = JSONObject()
                .put("model", m)
                .put("messages", msgs)
                .put("temperature", 0.3)
                .put("max_tokens", if (fast) 200 else 350)
            if (useJson) body.put("response_format", JSONObject().put("type", "json_object"))

            val (code, resp) = http(key, "POST", "/chat/completions", body)
            if (code in 200..299) {
                return JSONObject(resp).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
            }
            lastErr = "HTTP $code ${resp.take(100)}"
            when (code) {
                404 -> {
                    models.remove(fast)
                }
                400 -> {
                    if (useJson) {
                        useJson = false
                    } else {
                        models.remove(fast)
                    }
                }
                else -> throw Exception(lastErr)
            }
        }
        throw Exception(lastErr)
    }

    private fun parse(content: String): JSONObject {
        val s = content.indexOf('{')
        val e = content.lastIndexOf('}')
        return if (s >= 0 && e > s) JSONObject(content.substring(s, e + 1))
        else JSONObject().put("action", "chat").put("reply", content.trim())
    }

    /** Aam awaaz command (tez model) */
    fun ask(key: String, text: String, lang: String): JSONObject {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system")
            .put("content", SYSTEM + "\nThe user's chosen language: $lang. Reply in the language the user speaks."))
        history.takeLast(6).forEach { msgs.put(JSONObject().put("role", it.first).put("content", it.second)) }
        msgs.put(JSONObject().put("role", "user").put("content", text))

        val content = chat(key, msgs, true)
        val out = parse(content)
        history.add("user" to text)
        history.add("assistant" to out.toString())
        while (history.size > 12) history.removeAt(0)
        return out
    }

    /** Screen padhkar agla ek kadam batata hai (agent, samajhdaar model) */
    fun askAgent(key: String, goal: String, screen: String, done: List<String>, lang: String): JSONObject {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system")
            .put("content", AGENT + "\nThe user's language: $lang."))
        val steps = if (done.isEmpty()) "none" else done.takeLast(8).joinToString("; ")
        msgs.put(JSONObject().put("role", "user")
            .put("content", "Goal: $goal\nSteps already done: $steps\nCurrent screen:\n" + screen.take(3500)))
        return parse(chat(key, msgs, false))
    }
}
