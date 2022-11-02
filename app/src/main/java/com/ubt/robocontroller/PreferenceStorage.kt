package com.ubt.robocontroller

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * key - value 存储
 */
interface PreferenceStorage {
    var keyPoints: String?
    fun clearCache()
}

class SharedPreferenceStorage(context: Context) :
    PreferenceStorage {
    private val prefs: Lazy<SharedPreferences> = lazy {
        context.applicationContext.getSharedPreferences(
            PREFS_NAME, Context.MODE_PRIVATE
        ).apply {

        }
    }

    override var keyPoints by StringPreference(prefs, PREF_KEY_POINTS, null)

    // 登出时清理缓存
    override fun clearCache() {
    }

    companion object {
        const val PREFS_NAME = "robocontroller_prefs"
        const val PREF_KEY_POINTS = "pref_key_points"
    }
}

class BooleanPreference(
    private val preferences: Lazy<SharedPreferences>,
    private val name: String,
    private val defaultValue: Boolean
) : ReadWriteProperty<Any, Boolean> {
    @WorkerThread
    override fun getValue(thisRef: Any, property: KProperty<*>): Boolean {
        return preferences.value.getBoolean(name, defaultValue)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) {
        preferences.value.edit { putBoolean(name, value) }
    }
}

class StringPreference(private val preferences: Lazy<SharedPreferences>,
                       private val key: String,
                       private val defaultValue: String?) : ReadWriteProperty<Any, String?> {
    @WorkerThread
    override fun getValue(thisRef: Any, property: KProperty<*>): String? {
        return preferences.value.getString(key, defaultValue)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: String?) {
        preferences.value.edit { putString(key, value) }
    }
}

class IntPreference(private val preferences: Lazy<SharedPreferences>,
                    private val key: String,
                    private val defaultValue: Int = 0) : ReadWriteProperty<Any, Int> {
    override fun getValue(thisRef: Any, property: KProperty<*>): Int {
        return preferences.value.getInt(key, defaultValue)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) {
        preferences.value.edit { putInt(key, value) }
    }
}

class LongPreference(private val preferences: Lazy<SharedPreferences>,
                    private val key: String,
                    private val defaultValue: Long = -1) : ReadWriteProperty<Any, Long> {
    override fun getValue(thisRef: Any, property: KProperty<*>): Long {
        return preferences.value.getLong(key, defaultValue)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) {
        preferences.value.edit { putLong(key, value) }
    }
}
