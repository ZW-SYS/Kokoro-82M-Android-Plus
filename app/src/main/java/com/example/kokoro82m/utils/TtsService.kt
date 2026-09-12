package com.example.kokoro82m.utils

import android.content.Context
import android.media.MediaPlayer
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

data class TtsProfile(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "OpenAI TTS",
    var baseUrl: String = "https://api.openai.com/v1/audio/speech",
    var apiKey: String = "",
    var model: String = "tts-1",
    var voice: String = "alloy",
    var enabled: Boolean = false
)

object TtsProfileStore {
    private const val PREF_NAME = "kokoro_tts_profile"
    private const val KEY = "tts_profile"

    fun load(context: Context): TtsProfile {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, "") ?: ""
        if (json.isEmpty()) return TtsProfile()
        return try {
            val obj = JSONObject(json)
            TtsProfile(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "OpenAI TTS"),
                baseUrl = obj.optString("baseUrl", ""),
                apiKey = obj.optString("apiKey", ""),
                model = obj.optString("model", "tts-1"),
                voice = obj.optString("voice", "alloy"),
                enabled = obj.optBoolean("enabled", false)
            )
        } catch (_: Exception) {
            TtsProfile()
        }
    }

    fun save(context: Context, profile: TtsProfile) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject()
        obj.put("id", profile.id)
        obj.put("name", profile.name)
        obj.put("baseUrl", profile.baseUrl)
        obj.put("apiKey", profile.apiKey)
        obj.put("model", profile.model)
        obj.put("voice", profile.voice)
        obj.put("enabled", profile.enabled)
        prefs.edit().putString(KEY, obj.toString()).apply()
    }
}

class TtsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var player: MediaPlayer? = null

    suspend fun speak(
        context: Context,
        text: String,
        profile: TtsProfile,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = profile.baseUrl
                val json = JSONObject()
                json.put("model", profile.model)
                json.put("input", text)
                json.put("voice", profile.voice)
                json.put("response_format", "mp3")

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${profile.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    withContext(Dispatchers.Main) {
                        onError("HTTP ${response.code}: ${errBody.take(200)}")
                    }
                    return@withContext
                }

                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    withContext(Dispatchers.Main) { onError("返回音频为空") }
                    return@withContext
                }

                val file = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                file.writeBytes(bytes)

                withContext(Dispatchers.Main) {
                    stop()
                    player = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener {
                            stop()
                            file.delete()
                        }
                    }
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "TTS 请求失败") }
            }
        }
    }

    fun stop() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
    }
}

object TtsPresets {
    val presets = listOf(
        TtsProfile(
            name = "OpenAI TTS",
            baseUrl = "https://api.openai.com/v1/audio/speech",
            model = "tts-1",
            voice = "alloy"
        ),
        TtsProfile(
            name = "OpenAI TTS HD",
            baseUrl = "https://api.openai.com/v1/audio/speech",
            model = "tts-1-hd",
            voice = "nova"
        ),
        TtsProfile(
            name = "硅基流动",
            baseUrl = "https://api.siliconflow.cn/v1/audio/speech",
            model = "FunAudioLLM/CosyVoice2-0.5B",
            voice = "alex"
        ),
        TtsProfile(
            name = "自定义",
            baseUrl = "",
            model = "",
            voice = ""
        )
    )

    val voices = listOf(
        "alloy", "echo", "fable", "onyx", "nova", "shimmer",
        "alex", "benjamin", "charles", "david", "anna", "bella", "claire", "diana"
    )
}