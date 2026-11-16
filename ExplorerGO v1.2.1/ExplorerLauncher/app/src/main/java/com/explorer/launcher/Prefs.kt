package com.explorer.launcher

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import java.util.Calendar
import java.util.Locale

object P {
    lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("explorer", 0) }
    fun i(k: String, d: Int) = sp.getInt(k, d)
    fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    fun s(k: String, d: String) = sp.getString(k, d) ?: d
    fun put(k: String, v: Any) {
        val e = sp.edit()
        when (v) { is Int -> e.putInt(k, v); is Boolean -> e.putBoolean(k, v); is String -> e.putString(k, v) }
        e.apply()
    }
    fun accentNames(c: Context): Array<String> = c.resources.getStringArray(R.array.accent_names)
    private val accents = arrayOf("#00B0FF", "#FF6D00", "#00E676", "#FF1744", "#D500F9", "#FFD600", "#1DE9B6", "#FFFFFF")
    fun accent(): Int = Color.parseColor(accents[i("accent", 0).coerceIn(0, 7)])
    fun dark(): Boolean = when (i("theme", 0)) {
        0 -> true
        1 -> false
        else -> { val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); h >= i("nightFrom", 19) || h < i("nightTo", 7) }
    }
    fun bg(): Int = if (dark()) 0xFF0B0E14.toInt() else 0xFFF1F3F7.toInt()
    fun fg(): Int = if (dark()) Color.WHITE else 0xFF10131A.toInt()
    fun surf(): Int = if (dark()) 0xFF1B2233.toInt() else Color.WHITE

    fun wrapContext(base: Context): Context {
        val prefs = base.getSharedPreferences("explorer", 0)
        val lang = prefs.getInt("lang", 0) // 0: System, 1: tr, 2: en
        if (lang == 0) return base
        val locale = if (lang == 1) Locale("tr") else Locale("en")
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
