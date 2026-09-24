package com.example.kokoro82m.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

object BackupHelper {

    private const val BACKUP_VERSION = 1

    fun exportLocal(context: Context): File? {
        return try {
            val backup = buildBackupJson(context)
            val file = File(context.cacheDir, "kokoro_backup_${System.currentTimeMillis()}.json")
            file.writeText(backup)
            file
        } catch (_: Exception) {
            null
        }
    }

    fun shareBackup(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出备份"))
        } catch (_: Exception) {}
    }

    fun importFromString(context: Context, json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val version = root.optInt("version", 1)
            if (version < 1) return false

            val profilesArr = root.optJSONArray("api_profiles")
            if (profilesArr != null) {
                val profiles = mutableListOf<ApiProfile>()
                for (i in 0 until profilesArr.length()) {
                    val obj = profilesArr.getJSONObject(i)
                    val modelsArr = obj.optJSONArray("models")
                    val models = mutableListOf<String>()
                    if (modelsArr != null) {
                        for (j in 0 until modelsArr.length()) {
                            val m = modelsArr.optString(j, "")
                            if (m.isNotEmpty()) models.add(m)
                        }
                    }
                    profiles.add(
                        ApiProfile(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "配置"),
                            providerType = obj.optString("providerType", "OpenAI"),
                            modelType = obj.optString("modelType", "text"),
                            baseUrl = obj.optString("baseUrl", ""),
                            imageUrl = obj.optString("imageUrl", ""),
                            apiKey = obj.optString("apiKey", ""),
                            model = obj.optString("model", ""),
                            models = models,
                            enabled = obj.optBoolean("enabled", true)
                        )
                    )
                }
                ApiProfileStore.save(context, profiles)
            }

            val ttsObj = root.optJSONObject("tts_profile")
            if (ttsObj != null) {
                val tts = TtsProfile(
                    id = ttsObj.optString("id", UUID.randomUUID().toString()),
                    name = ttsObj.optString("name", "OpenAI TTS"),
                    baseUrl = ttsObj.optString("baseUrl", ""),
                    apiKey = ttsObj.optString("apiKey", ""),
                    model = ttsObj.optString("model", "tts-1"),
                    voice = ttsObj.optString("voice", "alloy"),
                    enabled = ttsObj.optBoolean("enabled", false)
                )
                TtsProfileStore.save(context, tts)
            }

            val sessionsArr = root.optJSONArray("chat_sessions")
            if (sessionsArr != null) {
                val file = File(context.filesDir, "chat_sessions.json")
                file.writeText(sessionsArr.toString())
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun uploadToWeb(
        url: String,
        apiKey: String,
        json: String,
        onResult: (Boolean, String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val builder = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .put(body)
                if (apiKey.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $apiKey")
                }
                val request = builder.build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) { onResult(true, "上传成功") }
                } else {
                    withContext(Dispatchers.Main) { onResult(false, "HTTP ${response.code}") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "网络错误") }
            }
        }
    }

    suspend fun downloadFromWeb(
        url: String,
        apiKey: String,
        onResult: (Boolean, String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val builder = Request.Builder().url(url).get()
                if (apiKey.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $apiKey")
                }
                val request = builder.build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    withContext(Dispatchers.Main) { onResult(true, body) }
                } else {
                    withContext(Dispatchers.Main) { onResult(false, "HTTP ${response.code}") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "网络错误") }
            }
        }
    }

    fun buildBackupJson(context: Context): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("exported_at", System.currentTimeMillis())

        val profiles = ApiProfileStore.load(context)
        val profilesArr = JSONArray()
        for (p in profiles) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("providerType", p.providerType)
            obj.put("modelType", p.modelType)
            obj.put("baseUrl", p.baseUrl)
            obj.put("imageUrl", p.imageUrl)
            obj.put("apiKey", p.apiKey)
            obj.put("model", p.model)
            val modelsArr = JSONArray()
            for (m in p.models) modelsArr.put(m)
            obj.put("models", modelsArr)
            obj.put("enabled", p.enabled)
            profilesArr.put(obj)
        }
        root.put("api_profiles", profilesArr)

        val tts = TtsProfileStore.load(context)
        val ttsObj = JSONObject()
        ttsObj.put("id", tts.id)
        ttsObj.put("name", tts.name)
        ttsObj.put("baseUrl", tts.baseUrl)
        ttsObj.put("apiKey", tts.apiKey)
        ttsObj.put("model", tts.model)
        ttsObj.put("voice", tts.voice)
        ttsObj.put("enabled", tts.enabled)
        root.put("tts_profile", ttsObj)

        val chatFile = File(context.filesDir, "chat_sessions.json")
        if (chatFile.exists()) {
            try {
                root.put("chat_sessions", JSONArray(chatFile.readText()))
            } catch (_: Exception) {}
        }

        return root.toString(2)
    }
}