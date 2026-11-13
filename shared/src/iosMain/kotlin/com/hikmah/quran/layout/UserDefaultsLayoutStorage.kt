package com.hikmah.quran.layout

import platform.Foundation.NSUserDefaults

class UserDefaultsLayoutStorage : LayoutStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(): String? = defaults.stringForKey(KEY)

    override fun write(json: String) = defaults.setObject(json, forKey = KEY)

    private companion object {
        const val KEY = "layout_state"
    }
}
