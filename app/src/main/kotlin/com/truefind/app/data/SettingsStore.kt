package com.truefind.app.data

import android.content.Context
import android.content.SharedPreferences

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

class SharedPrefsKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }
}

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val keyValueStore: KeyValueStore = SharedPrefsKeyValueStore(prefs)

    var bubbleEnabled: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BUBBLE_ENABLED, value).apply()

    var bubbleOnRightSide: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE_RIGHT, true)
        set(value) = prefs.edit().putBoolean(KEY_BUBBLE_RIGHT, value).apply()

    var bubbleYFraction: Float
        get() = prefs.getFloat(KEY_BUBBLE_Y_FRACTION, 0.45f).coerceIn(0.1f, 0.85f)
        set(value) = prefs.edit().putFloat(KEY_BUBBLE_Y_FRACTION, value.coerceIn(0.1f, 0.85f)).apply()

    var lastTemplateId: String?
        get() = prefs.getString(KEY_LAST_TEMPLATE_ID, null)
        set(value) = prefs.edit().putString(KEY_LAST_TEMPLATE_ID, value).apply()

    companion object {
        private const val PREFS_NAME = "truefind_settings"
        private const val KEY_BUBBLE_ENABLED = "bubble_enabled"
        private const val KEY_BUBBLE_RIGHT = "bubble_on_right"
        private const val KEY_BUBBLE_Y_FRACTION = "bubble_y_fraction"
        private const val KEY_LAST_TEMPLATE_ID = "last_template_id"
    }
}
