package com.mux.android.airshare.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Small global UI state. */
object Nav {
    var tab by mutableIntStateOf(0)
    var resumeTick by mutableIntStateOf(0)
    var permTick by mutableIntStateOf(0)
}

/** Every property is Compose state AND persisted in SharedPreferences. */
object AppSettings {
    private var sp: SharedPreferences? = null
    private val registry = ArrayList<Pref<*>>()

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.applicationContext.getSharedPreferences("airshare", Context.MODE_PRIVATE)
        registry.forEach { it.load() }
    }

    class Pref<T : Any>(private val key: String, private val def: T) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(def)

        init {
            registry.add(this)
        }

        @Suppress("UNCHECKED_CAST")
        fun load() {
            val s = sp ?: return
            val v: Any = when (def) {
                is Boolean -> s.getBoolean(key, def)
                is Int -> s.getInt(key, def)
                else -> s.getString(key, def as String) ?: def
            }
            state.value = v as T
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            val e = sp?.edit() ?: return
            when (value) {
                is Boolean -> e.putBoolean(key, value)
                is Int -> e.putInt(key, value)
                else -> e.putString(key, value as String)
            }
            e.apply()
        }
    }

    var amber by Pref("amber", false)
    var introSeen by Pref("introSeen", false)
    var askedPerms by Pref("askedPerms", false)

    /** Name other people see on their radar. "" = phone model. */
    var deviceName by Pref("deviceName", "")

    /** Show this phone on other people's radar while the app is open. */
    var visible by Pref("visible", true)

    fun myName(): String = deviceName.trim().ifEmpty { Build.MODEL ?: "Android" }.take(40)
}
