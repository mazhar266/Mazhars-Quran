package com.hikmah.quran.layout

import android.content.Context

class SharedPrefsLayoutStorage(context: Context) : LayoutStorage {
    private val prefs = context.applicationContext.getSharedPreferences("layout", Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    override fun write(json: String) = prefs.edit().putString(KEY, json).apply()

    private companion object {
        const val KEY = "layout_state"
    }
}
