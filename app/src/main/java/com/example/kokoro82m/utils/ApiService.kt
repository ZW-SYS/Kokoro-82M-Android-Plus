package com.example.kokoro82m.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AiProvider(
    val name: String,
    val providerType: String,
    val baseUrl: String,
    val defaultModel: String,
    val models: List<String>
)

object AiProviders {
    val presets = listOf(
        AiProvider("DeepSeek", "OpenAI", "https://api.deepseek.com/v1/chat/completions", "deepseek-chat", listOf("deepseek-chat", "deepseek-reasoner")),
        AiProvider("OpenAI", "OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini", listOf("gpt-3.5-turbo", "gpt-4", "gpt-4-turbo", "gpt-4o", "gpt-4o-mini")),
        AiProvider("Gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/models/", "gemini-1.5-flash", listOf("gemini-pro", "gemini-1.5-pro", "gemini-1.5-flash")),
        AiProvider("OpenRouter", "OpenAI", "https://openrouter.ai/api/v1/chat/completions", "openai/gpt-3.5-turbo", listOf("openai/gpt-3.5-turbo", "openai/gpt-4", "anthropic/claude-3-opus", "google/gemini-pro")),
        AiProvider("Claude", "Claude", "https://api.anthropic.com/v1/messages", "claude-3-haiku-20240307", listOf("claude-3-haiku-20240307", "claude-3-sonnet-20240229", "claude-3-opus-20240229")),
        AiProvider("Ollama", "Ollama", "http://localhost:11434/api/generate", "llama3", listOf("llama2", "mistral", "phi", "codellama", "llama3")),
        AiProvider("阿里云百炼", "OpenAI", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-turbo", listOf("qwen-turbo", "qwen-plus", "qwen-max")),
        AiProvider("Moonshot", "OpenAI", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k", listOf("moonshot-v1-8k", "moonshot-v1-32k")),
        AiProvider("智谱", "OpenAI", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4", listOf("glm-4", "glm-4-air", "glm-3-turbo")),
        AiProvider("xAI", "OpenAI", "https://api.x.ai/v1/chat/completions", "grok-1", listOf("grok-1", "grok-1.5")),
        AiProvider("自定义 (OpenAI兼容)", "OpenAI", "", "", emptyList()),
        AiProvider("自定义 (Gemini)", "Gemini", "", "", emptyList()),
        AiProvider("自定义 (Claude)", "Claude", "", "", emptyList()),
        AiProvider("自定义 (Ollama)", "Ollama", "", "", emptyList())
    )
}

data class ApiProfile(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "新配置",
    var providerType: String = "OpenAI",
    var baseUrl: String = "",
    var apiKey: String = "",
    var model: String = "",
    var enabled: Boolean = true
)

object ApiProfileStore {
    private const val PREF_NAME = "kokoro_api_profiles"
    private const val KEY_PROFILES = "profiles"

    fun load(context: Context): MutableList<ApiProfile> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        val list = mutableListOf<ApiProfile>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ApiProfile(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "配置"),
                        providerType = obj.optString("providerType", "OpenAI"),
                        baseUrl = obj.optString("baseUrl", ""),
                        apiKey = obj.optString("apiKey", ""),
                        model = obj.optString("model", ""),
                        enabled = obj.optBoolean("enabled", true)
                    )
                )
            }
        } catch (_: Exception) {}
        if (list.isEmpty()) {
            list.add(ApiProfile(name = "DeepSeek", baseUrl = "https://api.deepseek.com/v1/chat/completions", model = "deepseek-chat"))
        }
        return list
    }

    fun save(context: Context, list: List<ApiProfile>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (p in list) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("providerType", p.providerType)
            obj.put("baseUrl", p.baseUrl)
            obj.put("apiKey", p.apiKey)
            obj.put("model", p.model)
            obj.put("enabled", p.enabled)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }
}

class ApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        prompt: String,
        profile: ApiProfile,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(profile)
                val bodyStr = buildBody(prompt, profile)
                val body = bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType())

                val builder = Request.Builder().url(url)
                applyHeaders(builder, profile)

                val request = builder.post(body).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val reply = extractReply(responseBody, profile)
                    withContext(Dispatchers.Main) { onSuccess(reply) }
                } else {
                    withContext(Dispatchers.Main) { onError("HTTP ${response.code}: $responseBody") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "网络请求失败") }
            }
        }
    }

    suspend fun testConnection(
        profile: ApiProfile,
        onResult: (Boolean, String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(profile)
                val bodyStr = buildBody("Hi", profile)
                val body = bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType())

                val builder = Request.Builder().url(url)
                applyHeaders(builder, profile)

                val request = builder.post(body).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val reply = extractReply(responseBody, profile)
                    if (reply.startsWith("无法解析")) {
                        withContext(Dispatchers.Main) { onResult(false, "响应格式异常") }
                    } else {
                        withContext(Dispatchers.Main) { onResult(true, "连接成功") }
                    }
                } else {
                    withContext(Dispatchers.Main) { onResult(false, "HTTP ${response.code}") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "网络错误") }
            }
        }
    }

    suspend fun fetchModels(
        profile: ApiProfile,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val modelsUrl = buildModelsUrl(profile)
                if (modelsUrl == null) {
                    withContext(Dispatchers.Main) { onError("该类型不支持自动获取模型") }
                    return@withContext
                }

                val builder = Request.Builder().url(modelsUrl)
                when (profile.providerType) {
                    "Ollama" -> {}
                    else -> builder.addHeader("Authorization", "Bearer ${profile.apiKey}")
                }

                val request = builder.get().build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) { onError("HTTP ${response.code}") }
                    return@withContext
                }

                val models = extractModels(responseBody, profile)
                if (models.isEmpty()) {
                    withContext(Dispatchers.Main) { onError("未获取到模型") }
                } else {
                    withContext(Dispatchers.Main) { onSuccess(models) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "网络错误") }
            }
        }
    }

    private fun applyHeaders(builder: Request.Builder, profile: ApiProfile) {
        when (profile.providerType) {
            "Gemini" -> {}
            "Claude" -> {
                builder.addHeader("x-api-key", profile.apiKey)
                builder.addHeader("anthropic-version", "2023-06-01")
                builder.addHeader("Content-Type", "application/json")
            }
            "Ollama" -> {
                builder.addHeader("Content-Type", "application/json")
            }
            else -> {
                builder.addHeader("Authorization", "Bearer ${profile.apiKey}")
                builder.addHeader("Content-Type", "application/json")
            }
        }
    }

    private fun buildModelsUrl(profile: ApiProfile): String? {
        return when (profile.providerType) {
            "OpenAI" -> {
                val base = profile.baseUrl
                    .removeSuffix("/chat/completions")
                    .removeSuffix("/completions")
                    .trimEnd('/')
                if (base.isEmpty()) null else "$base/models"
            }
            "Ollama" -> {
                val base = profile.baseUrl
                    .removeSuffix("/api/generate")
                    .trimEnd('/')
                if (base.isEmpty()) null else "$base/api/tags"
            }
            "Gemini" -> {
                val base = profile.baseUrl.trimEnd('/')
                if (base.isEmpty()) null else "$base?key=${profile.apiKey}"
            }
            else -> null
        }
    }

    private fun extractModels(json: String, profile: ApiProfile): List<String> {
        val result = mutableListOf<String>()
        try {
            val obj = JSONObject(json)
            when (profile.providerType) {
                "OpenAI" -> {
                    val data = obj.optJSONArray("data") ?: return result
                    for (i in 0 until data.length()) {
                        val id = data.optJSONObject(i)?.optString("id", "") ?: ""
                        if (id.isNotEmpty()) result.add(id)
                    }
                }
                "Ollama" -> {
                    val models = obj.optJSONArray("models") ?: return result
                    for (i in 0 until models.length()) {
                        val name = models.optJSONObject(i)?.optString("name", "") ?: ""
                        if (name.isNotEmpty()) result.add(name)
                    }
                }
                "Gemini" -> {
                    val models = obj.optJSONArray("models") ?: return result
                    for (i in 0 until models.length()) {
                        val name = models.optJSONObject(i)?.optString("name", "") ?: ""
                        val clean = name.removePrefix("models/")
                        if (clean.isNotEmpty()) result.add(clean)
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun buildUrl(profile: ApiProfile): String {
        return when (profile.providerType) {
            "Gemini" -> {
                val base = profile.baseUrl.trimEnd('/')
                if (base.contains(":generateContent")) base
                else "$base/${profile.model}:generateContent?key=${profile.apiKey}"
            }
            else -> profile.baseUrl
        }
    }

    private fun buildBody(prompt: String, profile: ApiProfile): String {
        val json = JSONObject()
        return when (profile.providerType) {
            "Gemini" -> {
                val contents = JSONArray()
                val parts = JSONArray()
                val textObj = JSONObject()
                textObj.put("text", prompt)
                parts.put(textObj)
                val contentObj = JSONObject()
                contentObj.put("parts", parts)
                contents.put(contentObj)
                json.put("contents", contents)
                json.toString()
            }
            "Claude" -> {
                json.put("model", profile.model)
                json.put("max_tokens", 1024)
                val msgs = JSONArray()
                val msg = JSONObject()
                msg.put("role", "user")
                msg.put("content", prompt)
                msgs.put(msg)
                json.put("messages", msgs)
                json.toString()
            }
            "Ollama" -> {
                json.put("model", profile.model)
                json.put("prompt", prompt)
                json.put("stream", false)
                json.toString()
            }
            else -> {
                json.put("model", profile.model)
                json.put("stream", false)
                val msgs = JSONArray()
                val msg = JSONObject()
                msg.put("role", "user")
                msg.put("content", prompt)
                msgs.put(msg)
                json.put("messages", msgs)
                json.toString()
            }
        }
    }

    private fun extractReply(json: String, profile: ApiProfile): String {
        try {
            val obj = JSONObject(json)
            when (profile.providerType) {
                "Gemini" -> {
                    val candidates = obj.optJSONArray("candidates")
                    val first = candidates?.optJSONObject(0)
                    val content = first?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text", "")
                    if (!text.isNullOrEmpty()) return text
                }
                "Claude" -> {
                    val content = obj.optJSONArray("content")
                    val text = content?.optJSONObject(0)?.optString("text", "")
                    if (!text.isNullOrEmpty()) return text
                }
                "Ollama" -> {
                    val response = obj.optString("response", "")
                    if (response.isNotEmpty()) return response
                }
                else -> {
                    val choices = obj.optJSONArray("choices")
                    val first = choices?.optJSONObject(0)
                    val message = first?.optJSONObject("message")
                    val content = message?.optString("content", "")
                    if (!content.isNullOrEmpty()) return content
                }
            }
        } catch (_: Exception) {}
        return "无法解析 AI 回复，请检查配置"
    }
}