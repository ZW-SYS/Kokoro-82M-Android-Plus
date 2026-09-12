package com.example.kokoro82m.utils

import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class AiProvider(
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val models: List<String>,
    val apiKeyRequired: Boolean = true,
    val headers: Map<String, String> = emptyMap()
)

object AiProviders {
    val providers = listOf(
        AiProvider(
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1/chat/completions",
            defaultModel = "deepseek-chat",
            models = listOf("deepseek-chat", "deepseek-reasoner")
        ),
        AiProvider(
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1/chat/completions",
            defaultModel = "gpt-3.5-turbo",
            models = listOf("gpt-3.5-turbo", "gpt-4", "gpt-4-turbo", "gpt-4o", "gpt-4o-mini")
        ),
        AiProvider(
            name = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/",
            defaultModel = "gemini-pro",
            models = listOf("gemini-pro", "gemini-1.5-pro", "gemini-1.5-flash")
        ),
        AiProvider(
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1/chat/completions",
            defaultModel = "openai/gpt-3.5-turbo",
            models = listOf(
                "openai/gpt-3.5-turbo",
                "openai/gpt-4",
                "openai/gpt-4-turbo",
                "anthropic/claude-3-opus",
                "anthropic/claude-3-sonnet",
                "google/gemini-pro",
                "meta-llama/llama-3-70b-instruct"
            )
        ),
        AiProvider(
            name = "Claude",
            baseUrl = "https://api.anthropic.com/v1/messages",
            defaultModel = "claude-3-haiku-20240307",
            models = listOf(
                "claude-3-haiku-20240307",
                "claude-3-sonnet-20240229",
                "claude-3-opus-20240229"
            )
        ),
        AiProvider(
            name = "Ollama",
            baseUrl = "http://localhost:11434/api/generate",
            defaultModel = "llama2",
            models = listOf("llama2", "mistral", "phi", "codellama", "llama3"),
            apiKeyRequired = false
        ),
        AiProvider(
            name = "阿里云百炼",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            defaultModel = "qwen-turbo",
            models = listOf("qwen-turbo", "qwen-plus", "qwen-max", "qwen-max-longcontext")
        ),
        AiProvider(
            name = "Moonshot",
            baseUrl = "https://api.moonshot.cn/v1/chat/completions",
            defaultModel = "moonshot-v1-8k",
            models = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
        ),
        AiProvider(
            name = "智谱",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            defaultModel = "glm-4",
            models = listOf("glm-4", "glm-4-plus", "glm-4-air", "glm-3-turbo")
        ),
        AiProvider(
            name = "xAI",
            baseUrl = "https://api.x.ai/v1/chat/completions",
            defaultModel = "grok-1",
            models = listOf("grok-1", "grok-1.5")
        ),
        AiProvider(
            name = "自定义",
            baseUrl = "",
            defaultModel = "",
            models = emptyList()
        )
    )
}

class ApiService {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    var apiKey: String = ""
    var selectedProvider: String = "DeepSeek"
    var selectedModel: String = "deepseek-chat"
    var customBaseUrl: String = ""
    var customModel: String = ""

    private fun getProvider(): AiProvider? {
        return AiProviders.providers.find { it.name == selectedProvider }
    }

    fun chat(
        prompt: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val provider = getProvider()
        if (provider == null) {
            onError("未选择有效的 AI 提供商")
            return
        }

        if (provider.name == "自定义") {
            if (customBaseUrl.isEmpty()) {
                onError("请先配置自定义 API 地址")
                return
            }
            if (customModel.isEmpty()) {
                onError("请先配置自定义模型名称")
                return
            }
        } else if (provider.apiKeyRequired && apiKey.isEmpty()) {
            onError("请先在设置中配置 API Key")
            return
        }

        try {
            val request = buildRequest(prompt, provider)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onError("网络请求失败: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        try {
                            val reply = extractReply(body, provider)
                            onSuccess(reply)
                        } catch (e: Exception) {
                            onError("解析响应失败: ${e.message}")
                        }
                    } else {
                        onError("请求失败: ${response.code}")
                    }
                }
            })
        } catch (e: Exception) {
            onError("请求构建失败: ${e.message}")
        }
    }

    private fun buildRequest(prompt: String, provider: AiProvider): Request {
        val url = when {
            provider.name == "自定义" -> customBaseUrl
            provider.name == "Google Gemini" -> "${provider.baseUrl}${selectedModel}:generateContent?key=$apiKey"
            else -> provider.baseUrl
        }

        val builder = Request.Builder().url(url)

        when {
            provider.name == "Google Gemini" -> {}
            provider.name == "Claude" -> {
                builder.addHeader("x-api-key", apiKey)
                builder.addHeader("anthropic-version", "2023-06-01")
                builder.addHeader("Content-Type", "application/json")
            }
            provider.name == "Ollama" -> {
                builder.addHeader("Content-Type", "application/json")
            }
            else -> {
                builder.addHeader("Authorization", "Bearer $apiKey")
                builder.addHeader("Content-Type", "application/json")
            }
        }

        val body = buildBody(prompt, provider).toRequestBody(JSON)
        return builder.post(body).build()
    }

    private fun buildBody(prompt: String, provider: AiProvider): String {
        return when {
            provider.name == "Google Gemini" -> {
                gson.toJson(
                    mapOf(
                        "contents" to listOf(
                            mapOf(
                                "parts" to listOf(
                                    mapOf("text" to prompt)
                                )
                            )
                        )
                    )
                )
            }
            provider.name == "Claude" -> {
                gson.toJson(
                    mapOf(
                        "model" to selectedModel,
                        "max_tokens" to 1024,
                        "messages" to listOf(
                            mapOf("role" to "user", "content" to prompt)
                        )
                    )
                )
            }
            provider.name == "Ollama" -> {
                gson.toJson(
                    mapOf(
                        "model" to selectedModel,
                        "prompt" to prompt,
                        "stream" to false
                    )
                )
            }
            else -> {
                val model = if (provider.name == "自定义") customModel else selectedModel
                gson.toJson(
                    mapOf(
                        "model" to model,
                        "messages" to listOf(
                            mapOf("role" to "user", "content" to prompt)
                        ),
                        "stream" to false
                    )
                )
            }
        }
    }

    private fun extractReply(json: String, provider: AiProvider): String {
        if (provider.name == "Google Gemini") {
            try {
                val obj = gson.fromJson(json, Map::class.java)
                val candidates = obj["candidates"] as? List<*>
                val first = candidates?.firstOrNull() as? Map<*, *>
                val content = first?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val text = (parts?.firstOrNull() as? Map<*, *>)?.get("text") as? String
                if (!text.isNullOrEmpty()) return text
            } catch (e: Exception) {}
        }

        if (provider.name == "Claude") {
            try {
                val obj = gson.fromJson(json, Map::class.java)
                val content = obj["content"] as? List<*>
                val first = content?.firstOrNull() as? Map<*, *>
                val text = first?.get("text") as? String
                if (!text.isNullOrEmpty()) return text
            } catch (e: Exception) {}
        }

        if (provider.name == "Ollama") {
            try {
                val obj = gson.fromJson(json, Map::class.java)
                val response = obj["response"] as? String
                if (!response.isNullOrEmpty()) return response
            } catch (e: Exception) {}
        }

        try {
            val obj = gson.fromJson(json, Map::class.java)
            val choices = obj["choices"] as? List<*>
            val first = choices?.firstOrNull() as? Map<*, *>
            val message = first?.get("message") as? Map<*, *>
            val content = message?.get("content") as? String
            if (!content.isNullOrEmpty()) return content
        } catch (e: Exception) {}

        try {
            val obj = gson.fromJson(json, Map::class.java)
            val output = obj["output"] as? Map<*, *>
            val text = output?.get("text") as? String
            if (!text.isNullOrEmpty()) return text
        } catch (e: Exception) {}

        return "无法解析 AI 回复"
    }
}