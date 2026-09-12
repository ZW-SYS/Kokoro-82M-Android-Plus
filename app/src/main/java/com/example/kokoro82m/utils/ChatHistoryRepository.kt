package com.example.kokoro82m.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatMessage(
    val role: String,
    val content: String
)

class ChatHistoryRepository(private val context: Context) {
    private val file = File(context.filesDir, "chat_history.json")

    fun load(profileId: String): List<ChatMessage> {
        if (!file.exists()) return emptyList()
        return try {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray(profileId) ?: return emptyList()
            val result = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                val role = obj?.optString("role", "") ?: ""
                val content = obj?.optString("content", "") ?: ""
                if (role.isNotEmpty()) result.add(ChatMessage(role, content))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(profileId: String, messages: List<ChatMessage>) {
        val root = try {
            if (file.exists()) JSONObject(file.readText()) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
        val arr = JSONArray()
        for (msg in messages) {
            val obj = JSONObject()
            obj.put("role", msg.role)
            obj.put("content", msg.content)
            arr.put(obj)
        }
        root.put(profileId, arr)
        file.writeText(root.toString())
    }

    fun clear(profileId: String) {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            root.remove(profileId)
            file.writeText(root.toString())
        } catch (_: Exception) {}
    }
}