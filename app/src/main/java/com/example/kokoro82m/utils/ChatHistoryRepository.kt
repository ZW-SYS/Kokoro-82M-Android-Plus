package com.example.kokoro82m.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ChatMessage(
    val role: String,
    val content: String,
    val imageBase64: String? = null,
    val imageMimeType: String? = null
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "新对话",
    var profileId: String = "",
    var model: String = "",
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

class ChatHistoryRepository(private val context: Context) {
    private val file = File(context.filesDir, "chat_sessions.json")

    fun loadAll(): List<ChatSession> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val result = mutableListOf<ChatSession>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val msgsArr = obj.optJSONArray("messages") ?: JSONArray()
                val messages = mutableListOf<ChatMessage>()
                for (j in 0 until msgsArr.length()) {
                    val m = msgsArr.getJSONObject(j)
                    messages.add(
                        ChatMessage(
                            role = m.optString("role", ""),
                            content = m.optString("content", ""),
                            imageBase64 = if (m.isNull("imageBase64")) null else m.optString("imageBase64", null),
                            imageMimeType = if (m.isNull("imageMimeType")) null else m.optString("imageMimeType", null)
                        )
                    )
                }
                result.add(
                    ChatSession(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "对话"),
                        profileId = obj.optString("profileId", ""),
                        model = obj.optString("model", ""),
                        messages = messages,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadByProfile(profileId: String): List<ChatSession> {
        return loadAll().filter { it.profileId == profileId }
            .sortedByDescending { it.updatedAt }
    }

    fun save(session: ChatSession) {
        val all = loadAll().toMutableList()
        val idx = all.indexOfFirst { it.id == session.id }
        if (idx >= 0) {
            all[idx] = session
        } else {
            all.add(session)
        }
        writeAll(all)
    }

    fun delete(sessionId: String) {
        val all = loadAll().filter { it.id != sessionId }
        writeAll(all)
    }

    private fun writeAll(sessions: List<ChatSession>) {
        val arr = JSONArray()
        for (s in sessions) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("profileId", s.profileId)
            obj.put("model", s.model)
            obj.put("createdAt", s.createdAt)
            obj.put("updatedAt", s.updatedAt)
            val msgsArr = JSONArray()
            for (m in s.messages) {
                val mo = JSONObject()
                mo.put("role", m.role)
                mo.put("content", m.content)
                if (m.imageBase64 != null) {
                    mo.put("imageBase64", m.imageBase64)
                } else {
                    mo.put("imageBase64", JSONObject.NULL)
                }
                if (m.imageMimeType != null) {
                    mo.put("imageMimeType", m.imageMimeType)
                } else {
                    mo.put("imageMimeType", JSONObject.NULL)
                }
                msgsArr.put(mo)
            }
            obj.put("messages", msgsArr)
            arr.put(obj)
        }
        file.writeText(arr.toString())
    }
}