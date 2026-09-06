package com.example.kokoro82m.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryItem(
    val id: String = System.currentTimeMillis().toString(),
    val text: String,
    val style: String,
    val speed: Float,
    val time: String,
    val filePath: String
)

class HistoryRepository(context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyFlow: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        if (!file.exists()) {
            _history.value = emptyList()
            return
        }
        try {
            val json = file.readText()
            val array = JSONArray(json)
            val list = mutableListOf<HistoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    HistoryItem(
                        id = obj.getString("id"),
                        text = obj.getString("text"),
                        style = obj.getString("style"),
                        speed = obj.getDouble("speed").toFloat(),
                        time = obj.getString("time"),
                        filePath = obj.getString("filePath")
                    )
                )
            }
            _history.value = list
        } catch (e: Exception) {
            _history.value = emptyList()
        }
    }

    private fun saveHistory() {
        try {
            val array = JSONArray()
            _history.value.forEach {
                val obj = JSONObject().apply {
                    put("id", it.id)
                    put("text", it.text)
                    put("style", it.style)
                    put("speed", it.speed)
                    put("time", it.time)
                    put("filePath", it.filePath)
                }
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addHistory(text: String, style: String, speed: Float, time: String, filePath: String) {
        val item = HistoryItem(
            text = text,
            style = style,
            speed = speed,
            time = time,
            filePath = filePath
        )
        val newList = listOf(item) + _history.value
        _history.value = newList
        saveHistory()
    }

    fun deleteHistory(id: String) {
        _history.value = _history.value.filter { it.id != id }
        saveHistory()
    }

    fun clearAll() {
        _history.value = emptyList()
        saveHistory()
    }

    fun getAllHistory(): List<HistoryItem> = _history.value
}