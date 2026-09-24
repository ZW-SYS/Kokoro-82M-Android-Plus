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
    val modelType: String,
    val baseUrl: String,
    val imageUrl: String,
    val defaultModel: String,
    val models: List<String>
)

object AiProviders {
    const val MODEL_TYPE_TEXT = "text"
    const val MODEL_TYPE_MULTIMODAL = "multimodal"
    const val MODEL_TYPE_IMAGE = "image"

    val presets = listOf(
        AiProvider("DeepSeek", "OpenAI", MODEL_TYPE_TEXT, "https://api.deepseek.com/v1/chat/completions", "", "deepseek-chat", listOf("deepseek-chat", "deepseek-reasoner")),
        AiProvider("OpenAI 聊天", "OpenAI", MODEL_TYPE_MULTIMODAL, "https://api.openai.com/v1/chat/completions", "https://api.openai.com/v1/images/generations", "gpt-4o-mini", listOf("gpt-3.5-turbo", "gpt-4", "gpt-4-turbo", "gpt-4o", "gpt-4o-mini")),
        AiProvider("OpenAI 生图", "OpenAI", MODEL_TYPE_IMAGE, "", "https://api.openai.com/v1/images/generations", "dall-e-3", listOf("dall-e-3", "dall-e-2")),
        AiProvider("Gemini", "Gemini", MODEL_TYPE_MULTIMODAL, "https://generativelanguage.googleapis.com/v1beta/models/", "", "gemini-1.5-flash", listOf("gemini-pro", "gemini-1.5-pro", "gemini-1.5-flash")),
        AiProvider("OpenRouter", "OpenAI", MODEL_TYPE_TEXT, "https://openrouter.ai/api/v1/chat/completions", "", "openai/gpt-3.5-turbo", listOf("openai/gpt-3.5-turbo", "openai/gpt-4", "anthropic/claude-3-opus", "google/gemini-pro")),
        AiProvider("Claude", "Claude", MODEL_TYPE_MULTIMODAL, "https://api.anthropic.com/v1/messages", "", "claude-3-haiku-20240307", listOf("claude-3-haiku-20240307", "claude-3-sonnet-20240229", "claude-3-opus-20240229")),
        AiProvider("Ollama", "Ollama", MODEL_TYPE_TEXT, "http://localhost:11434/api/generate", "", "llama3", listOf("llama2", "mistral", "phi", "codellama", "llama3")),
        AiProvider("阿里云百炼", "OpenAI", MODEL_TYPE_MULTIMODAL, "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "", "qwen-turbo", listOf("qwen-turbo", "qwen-plus", "qwen-max", "qwen-vl-plus")),
        AiProvider("Moonshot", "OpenAI", MODEL_TYPE_TEXT, "https://api.moonshot.cn/v1/chat/completions", "", "moonshot-v1-8k", listOf("moonshot-v1-8k", "moonshot-v1-32k")),
        AiProvider("智谱", "OpenAI", MODEL_TYPE_TEXT, "https://open.bigmodel.cn/api/paas/v4/chat/completions", "https://open.bigmodel.cn/api/paas/v4/images/generations", "glm-4", listOf("glm-4", "glm-4-air", "glm-3-turbo")),
        AiProvider("xAI", "OpenAI", MODEL_TYPE_TEXT, "https://api.x.ai/v1/chat/completions", "", "grok-1", listOf("grok-1", "grok-1.5")),
        AiProvider("自定义", "OpenAI", MODEL_TYPE_TEXT, "", "", "", emptyList())
    )
}

data class ApiProfile(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "新配置",
    var providerType: String = "OpenAI",
    var modelType: String = "text",
    var baseUrl: String = "",
    var imageUrl: String = "",
    var apiKey: String = "",
    var model: String = "",
    var models: List<String> = emptyList(),
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
                val modelsArr = obj.optJSONArray("models")
                val models = mutableListOf<String>()
                if (modelsArr != null) {
                    for (j in 0 until modelsArr.length()) {
                        val m = modelsArr.optString(j, "")
                        if (m.isNotEmpty()) models.add(m)
                    }
                }
                list.add(
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
            obj.put("modelType", p.modelType)
            obj.put("baseUrl", p.baseUrl)
            obj.put("imageUrl", p.imageUrl)
            obj.put("apiKey", p.apiKey)
            obj.put("model", p.model)
            val modelsArr = JSONArray()
            for (m in p.models) modelsArr.put(m)
            obj.put("models", modelsArr)
            obj.put("enabled", p.enabled)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }
}

class ApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        messages: List<ChatMessage>,
        profile: ApiProfile,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(profile)
                val bodyStr = buildBody(messages, profile)
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
                withContext(Dispatchers.Main) { onError(e.message ?: "Network error") }
            }
        }
    }

    suspend fun generateImage(
        prompt: String,
        profile: ApiProfile,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = if (profile.imageUrl.isNotBlank()) profile.imageUrl else profile.baseUrl
                if (url.isBlank()) {
                    withContext(Dispatchers.Main) { onError("未配置生图 API 地址") }
                    return@withContext
                }

                val json = JSONObject()
                json.put("model", profile.model)
                json.put("prompt", prompt)
                json.put("n", 1)
                json.put("size", "1024x1024")

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val builder = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${profile.apiKey}")
                    .addHeader("Content-Type", "application/json")

                val request = builder.post(body).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val obj = JSONObject(responseBody)
                    val data = obj.optJSONArray("data")
                    val first = data?.optJSONObject(0)
                    val b64 = first?.optString("b64_json", "") ?: ""
                    val imgUrl = first?.optString("url", "") ?: ""
                    val result = if (b64.isNotEmpty()) b64 else imgUrl
                    if (result.isNotEmpty()) {
                        withContext(Dispatchers.Main) { onSuccess(result) }
                    } else {
                        withContext(Dispatchers.Main) { onError("未返回图片") }
                    }
                } else {
                    withContext(Dispatchers.Main) { onError("HTTP ${response.code}: $responseBody") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Network error") }
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
                val testMsg = listOf(ChatMessage("user", "Hi"))
                val bodyStr = buildBody(testMsg, profile)
                val body = bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType())

                val builder = Request.Builder().url(url)
                applyHeaders(builder, profile)

                val request = builder.post(body).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val reply = extractReply(responseBody, profile)
                    if (reply.startsWith("无法解析")) {
                        withContext(Dispatchers.Main) { onResult(false, "Response format error") }
                    } else {
                        withContext(Dispatchers.Main) { onResult(true, "Connected") }
                    }
                } else {
                    withContext(Dispatchers.Main) { onResult(false, "HTTP ${response.code}") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "Network error") }
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
                withContext(Dispatchers.Main) { onError(e.message ?: "Network error") }
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

    private fun buildBody(messages: List<ChatMessage>, profile: ApiProfile): String {
        val json = JSONObject()
        return when (profile.providerType) {
            "Gemini" -> {
                val contents = JSONArray()
                for (msg in messages) {
                    val parts = JSONArray()
                    if (msg.content.isNotEmpty()) {
                        val textObj = JSONObject()
                        textObj.put("text", msg.content)
                        parts.put(textObj)
                    }
                    if (msg.imageBase64 != null && msg.imageMimeType != null) {
                        val inlineData = JSONObject()
                        inlineData.put("mime_type", msg.imageMimeType)
                        inlineData.put("data", msg.imageBase64)
                        val imgObj = JSONObject()
                        imgObj.put("inline_data", inlineData)
                        parts.put(imgObj)
                    }
                    for (att in msg.attachments) {
                        if (att.type == "image") {
                            val inlineData = JSONObject()
                            inlineData.put("mime_type", att.mimeType)
                            inlineData.put("data", att.base64)
                            val imgObj = JSONObject()
                            imgObj.put("inline_data", inlineData)
                            parts.put(imgObj)
                        } else if (att.type == "text") {
                            val textObj = JSONObject()
                            textObj.put("text", "[文件 ${att.name}]\n${att.base64}")
                            parts.put(textObj)
                        }
                    }
                    val contentObj = JSONObject()
                    contentObj.put("role", if (msg.role == "ai") "model" else "user")
                    contentObj.put("parts", parts)
                    contents.put(contentObj)
                }
                json.put("contents", contents)
                json.toString()
            }
            "Claude" -> {
                json.put("model", profile.model)
                json.put("max_tokens", 4096)
                val msgs = JSONArray()
                for (msg in messages) {
                    if (msg.role == "ai") {
                        val m = JSONObject()
                        m.put("role", "assistant")
                        m.put("content", msg.content)
                        msgs.put(m)
                    } else {
                        val m = JSONObject()
                        m.put("role", "user")
                        val contentArr = JSONArray()
                        var hasMulti = false

                        if (msg.content.isNotEmpty()) {
                            val textPart = JSONObject()
                            textPart.put("type", "text")
                            textPart.put("text", msg.content)
                            contentArr.put(textPart)
                            hasMulti = true
                        }
                        if (msg.imageBase64 != null && msg.imageMimeType != null) {
                            val imgPart = JSONObject()
                            imgPart.put("type", "image")
                            val source = JSONObject()
                            source.put("type", "base64")
                            source.put("media_type", msg.imageMimeType)
                            source.put("data", msg.imageBase64)
                            imgPart.put("source", source)
                            contentArr.put(imgPart)
                            hasMulti = true
                        }
                        for (att in msg.attachments) {
                            if (att.type == "image") {
                                val imgPart = JSONObject()
                                imgPart.put("type", "image")
                                val source = JSONObject()
                                source.put("type", "base64")
                                source.put("media_type", att.mimeType)
                                source.put("data", att.base64)
                                imgPart.put("source", source)
                                contentArr.put(imgPart)
                                hasMulti = true
                            } else if (att.type == "text") {
                                val textPart = JSONObject()
                                textPart.put("type", "text")
                                textPart.put("text", "[文件 ${att.name}]\n${att.base64}")
                                contentArr.put(textPart)
                                hasMulti = true
                            }
                        }

                        if (hasMulti) {
                            m.put("content", contentArr)
                        } else {
                            m.put("content", msg.content)
                        }
                        msgs.put(m)
                    }
                }
                json.put("messages", msgs)
                json.toString()
            }
            "Ollama" -> {
                val lastUser = messages.lastOrNull { it.role == "user" }
                json.put("model", profile.model)
                json.put("prompt", lastUser?.content ?: "")
                json.put("stream", false)
                if (lastUser?.imageBase64 != null) {
                    val imgs = JSONArray()
                    imgs.put(lastUser.imageBase64)
                    json.put("images", imgs)
                }
                json.toString()
            }
            else -> {
                json.put("model", profile.model)
                json.put("stream", false)
                val msgs = JSONArray()
                for (msg in messages) {
                    val m = JSONObject()
                    m.put("role", if (msg.role == "ai") "assistant" else "user")
                    val contentArr = JSONArray()
                    var hasMulti = false

                    if (msg.content.isNotEmpty()) {
                        val textPart = JSONObject()
                        textPart.put("type", "text")
                        textPart.put("text", msg.content)
                        contentArr.put(textPart)
                        hasMulti = true
                    }
                    if (msg.imageBase64 != null && msg.imageMimeType != null) {
                        val imgPart = JSONObject()
                        imgPart.put("type", "image_url")
                        val imgUrl = JSONObject()
                        imgUrl.put("url", "data:${msg.imageMimeType};base64,${msg.imageBase64}")
                        imgPart.put("image_url", imgUrl)
                        contentArr.put(imgPart)
                        hasMulti = true
                    }
                    for (att in msg.attachments) {
                        if (att.type == "image") {
                            val imgPart = JSONObject()
                            imgPart.put("type", "image_url")
                            val imgUrl = JSONObject()
                            imgUrl.put("url", "data:${att.mimeType};base64,${att.base64}")
                            imgPart.put("image_url", imgUrl)
                            contentArr.put(imgPart)
                            hasMulti = true
                        } else if (att.type == "text") {
                            val textPart = JSONObject()
                            textPart.put("type", "text")
                            textPart.put("text", "[文件 ${att.name}]\n${att.base64}")
                            contentArr.put(textPart)
                            hasMulti = true
                        }
                    }

                    if (hasMulti && (msg.role != "ai")) {
                        m.put("content", contentArr)
                    } else {
                        m.put("content", msg.content)
                    }
                    msgs.put(m)
                }
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