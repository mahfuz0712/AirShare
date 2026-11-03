package com.mux.android.airshare.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Transfer history, kept in a small JSON file. */
object HistoryStore {
    val items = mutableStateListOf<HistItem>()
    private var file: File? = null
    private val io = Executors.newSingleThreadExecutor()

    fun init(ctx: Context) {
        if (file != null) return
        file = File(ctx.applicationContext.filesDir, "history.json")
        try {
            val f = file ?: return
            if (!f.exists()) return
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val uris = ArrayList<String>()
                val mimes = ArrayList<String>()
                o.optJSONArray("u")?.let { for (k in 0 until it.length()) uris.add(it.getString(k)) }
                o.optJSONArray("m")?.let { for (k in 0 until it.length()) mimes.add(it.getString(k)) }
                items.add(
                    HistItem(
                        o.optLong("t"), o.optString("p"), o.optBoolean("o"), o.optString("n"),
                        o.optInt("c"), o.optLong("b"), o.optString("r"), uris, mimes, o.optString("x")
                    )
                )
            }
        } catch (e: Exception) {
        }
    }

    private fun save() {
        val f = file ?: return
        val a = JSONArray()
        for (h in items.toList()) {
            a.put(
                JSONObject().put("t", h.time).put("p", h.peer).put("o", h.outgoing).put("n", h.names)
                    .put("c", h.count).put("b", h.bytes).put("r", h.result)
                    .put("u", JSONArray(h.uris)).put("m", JSONArray(h.mimes)).put("x", h.text)
            )
        }
        val text = a.toString()
        io.execute {
            try {
                f.writeText(text)
            } catch (e: Exception) {
            }
        }
    }

    fun add(h: HistItem) {
        items.add(0, h)
        while (items.size > 200) items.removeAt(items.size - 1)
        save()
    }

    fun remove(h: HistItem) {
        items.remove(h)
        save()
    }

    fun clear() {
        items.clear()
        save()
    }
}
