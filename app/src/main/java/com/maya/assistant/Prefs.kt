package com.maya.assistant

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("maya", Context.MODE_PRIVATE)

    /** (naam, code)  – speech sunne aur bolne dono ke liye */
    val languages = listOf(
        "हिन्दी (Hindi)" to "hi-IN",
        "English" to "en-IN",
        "اردو (Urdu)" to "ur-IN",
        "বাংলা (Bengali)" to "bn-IN",
        "मराठी (Marathi)" to "mr-IN",
        "ગુજરાતી (Gujarati)" to "gu-IN",
        "ਪੰਜਾਬੀ (Punjabi)" to "pa-IN",
        "தமிழ் (Tamil)" to "ta-IN",
        "తెలుగు (Telugu)" to "te-IN",
        "ಕನ್ನಡ (Kannada)" to "kn-IN",
        "മലയാളം (Malayalam)" to "ml-IN",
        "नेपाली (Nepali)" to "ne-NP",
        "Español (Spanish)" to "es-ES",
        "Français (French)" to "fr-FR",
        "Deutsch (German)" to "de-DE",
        "Português (Portuguese)" to "pt-BR",
        "Italiano (Italian)" to "it-IT",
        "Русский (Russian)" to "ru-RU",
        "中文 (Chinese)" to "zh-CN",
        "日本語 (Japanese)" to "ja-JP",
        "한국어 (Korean)" to "ko-KR",
        "العربية (Arabic)" to "ar-SA",
        "Türkçe (Turkish)" to "tr-TR",
        "Bahasa Indonesia" to "id-ID",
        "ไทย (Thai)" to "th-TH",
        "Tiếng Việt (Vietnamese)" to "vi-VN",
        "Nederlands (Dutch)" to "nl-NL",
        "Polski (Polish)" to "pl-PL",
        "Kiswahili (Swahili)" to "sw-KE"
    )

    /** Settings mein dikhne wale saare rang */
    val colors: List<Int> = longArrayOf(
        0xFF00E5FF, 0xFF2F80FF, 0xFF2ECC71, 0xFFFFD60A, 0xFFFF8A1F,
        0xFFFF3B4E, 0xFFFF4FA3, 0xFFB04FFF, 0xFFFFFFFF
    ).map { it.toInt() }

    fun key(c: Context) = sp(c).getString("groq_key", "") ?: ""
    fun setKey(c: Context, v: String) = sp(c).edit().putString("groq_key", v.trim()).apply()

    fun lang(c: Context) = sp(c).getString("lang", "hi-IN") ?: "hi-IN"
    fun setLang(c: Context, v: String) = sp(c).edit().putString("lang", v).apply()
    fun langName(c: Context) = languages.firstOrNull { it.second == lang(c) }?.first ?: "Hindi"

    /** ring light ka rang (default: cyan) */
    fun ring(c: Context) = sp(c).getInt("ring", 0xFF00E5FF.toInt())
    fun setRing(c: Context, v: Int) = sp(c).edit().putInt("ring", v).apply()

    /** particle gole ka rang (default: pink) */
    fun orb(c: Context) = sp(c).getInt("orb", 0xFFFF4FA3.toInt())
    fun setOrb(c: Context, v: Int) = sp(c).edit().putInt("orb", v).apply()
}
