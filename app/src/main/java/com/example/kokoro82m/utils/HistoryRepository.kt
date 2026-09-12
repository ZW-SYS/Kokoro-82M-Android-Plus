package com.example.kokoro82m.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryItem(
    val text: String,
    val style: String,
    val speed: Float,
    val time: String
)

class HistoryRepository(private val context: Context) {
    private val file = File(context.filesDir, "history.json")

    fun getAll(): List<HistoryItem> {
        if (!file.exists()) return emptyList()
        val list = mutableListOf<HistoryItem>()
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    HistoryItem(
                        text = obj.optString("text", ""),
                        style = obj.optString("style", ""),
                        speed = obj.optDouble("speed", 1.0).toFloat(),
                        time = obj.optString("time", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list.reversed()
    }

    fun addHistory(text: String, style: String, speed: Float, time: String) {
        val list = getAll().toMutableList()
        list.add(HistoryItem(text, style, speed, time))
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("text", item.text)
            obj.put("style", item.style)
            obj.put("speed", item.speed.toDouble())
            obj.put("time", item.time)
            arr.put(obj)
        }
        file.writeText(arr.toString())
    }

    fun clear() {
        file.delete()
    }
}