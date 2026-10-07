package com.example.kokoro82m.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ToolCall(
    val id: String,
    val tool: String,
    val args: JSONObject
)

data class ToolResult(
    val id: String,
    val tool: String,
    val success: Boolean,
    val content: String
)

object AgentToolConfig {
    private const val PREF = "kokoro_agent_tools"
    private const val KEY_MASTER = "master_enabled"
    private const val KEY_ENABLED = "enabled_tools"

    val ALL_TOOLS = listOf(
        "get_current_time",
        "read_file",
        "write_file",
        "network_request",
        "open_app",
        "list_apps",
        "app_info",
        "uninstall_app"
    )

    val TOOL_DESCRIPTIONS = mapOf(
        "get_current_time" to "获取设备本地当前时间（到秒）",
        "read_file" to "读取文件内容，仅限 Downloads 目录和 App 私有目录",
        "write_file" to "写入文件，仅限 Downloads 目录和 App 私有目录",
        "network_request" to "发起 HTTP/HTTPS 请求，支持 GET/POST/PUT/DELETE",
        "open_app" to "根据包名启动应用",
        "list_apps" to "列出已安装的应用（名称 + 包名）",
        "app_info" to "查询指定包名的版本号、启用状态",
        "uninstall_app" to "打开系统卸载确认页（不会静默卸载）"
    )

    fun isMasterEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MASTER, false)
    }

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MASTER, enabled).apply()
        if (enabled) {
            val current = getEnabledTools(context)
            if (current.isEmpty()) {
                setEnabledTools(context, ALL_TOOLS.toSet())
            }
        }
    }

    fun getEnabledTools(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_ENABLED, null) ?: return emptySet()
        return saved.filter { ALL_TOOLS.contains(it) }.toSet()
    }

    fun setEnabledTools(context: Context, tools: Set<String>) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_ENABLED, tools).apply()
    }

    fun setToolEnabled(context: Context, tool: String, enabled: Boolean) {
        val current = getEnabledTools(context).toMutableSet()
        if (enabled) current.add(tool) else current.remove(tool)
        setEnabledTools(context, current)
    }

    fun isEnabled(context: Context, tool: String): Boolean {
        if (!isMasterEnabled(context)) return false
        if (!ALL_TOOLS.contains(tool)) return false
        return getEnabledTools(context).contains(tool)
    }
}

object AgentToolProtocol {
    const val START = "[[KOKORO_TOOLS_V1]]"
    const val END = "[[/KOKORO_TOOLS_V1]]"
    const val MAX_CALLS_PER_RESPONSE = 4
    const val MAX_CONTROL_JSON = 48 * 1024

    data class ParseResult(
        val visibleText: String,
        val calls: List<ToolCall>
    )

    fun parse(raw: String): ParseResult {
        if (raw.isEmpty()) return ParseResult(raw, emptyList())

        val calls = mutableListOf<ToolCall>()
        val visibleBuilder = StringBuilder()
        var index = 0

        while (index < raw.length) {
            val startIdx = raw.indexOf(START, index)
            if (startIdx < 0) {
                visibleBuilder.append(raw.substring(index))
                break
            }
            visibleBuilder.append(raw.substring(index, startIdx))

            val endIdx = raw.indexOf(END, startIdx + START.length)
            if (endIdx < 0) {
                break
            }

            val body = raw.substring(startIdx + START.length, endIdx).trim()
            if (body.length > MAX_CONTROL_JSON) {
                index = endIdx + END.length
                continue
            }

            val parsed = parseBody(body)
            calls.addAll(parsed)
            index = endIdx + END.length
        }

        val finalCalls = if (calls.size > MAX_CALLS_PER_RESPONSE) {
            calls.take(MAX_CALLS_PER_RESPONSE)
        } else calls

        return ParseResult(visibleBuilder.toString().trim(), finalCalls)
    }

    fun hasControlBlock(text: String): Boolean {
        return text.contains(START) || text.contains(END)
    }

    private fun parseBody(body: String): List<ToolCall> {
        var cleaned = body.trim()

        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```json").removePrefix("```").trim()
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.dropLast(3).trim()
            }
        }

        return try {
            val obj = JSONObject(cleaned)
            if (obj.has("call")) {
                val c = obj.getJSONObject("call")
                listOf(parseSingle(c)).filterNotNull()
            } else if (obj.has("calls")) {
                val arr = obj.getJSONArray("calls")
                val result = mutableListOf<ToolCall>()
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    parseSingle(c)?.let { result.add(it) }
                }
                result
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSingle(obj: JSONObject): ToolCall? {
        val id = obj.optString("id", "").ifBlank { "t" + System.currentTimeMillis() }
        val tool = obj.optString("tool", "")
        if (tool.isBlank()) return null

        val args = JSONObject()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k == "id" || k == "tool") continue
            args.put(k, obj.get(k))
        }
        return ToolCall(id, tool, args)
    }

    fun wrapResults(results: List<ToolResult>): String {
        val arr = JSONArray()
        for (r in results) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("tool", r.tool)
            obj.put("success", r.success)
            obj.put("content", r.content)
            arr.put(obj)
        }
        val root = JSONObject()
        root.put("results", arr)
        return "$START\n$root\n$END"
    }
}

object AgentToolPrompt {
    fun build(enabledTools: Set<String>): String {
        if (enabledTools.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("你是一个可以使用工具的 AI 助手。\n\n")
        sb.append("当需要调用工具时，输出下面的控制块，必须完整包含开始和结束标记：\n\n")
        sb.append(AgentToolProtocol.START).append("\n")
        sb.append("{\"call\":{\"id\":\"t1\",\"tool\":\"工具名\",\"参数\":\"值\"}}\n")
        sb.append(AgentToolProtocol.END).append("\n\n")
        sb.append("也可以一次调用多个工具：\n\n")
        sb.append(AgentToolProtocol.START).append("\n")
        sb.append("{\"calls\":[{\"id\":\"t1\",\"tool\":\"...\"},{\"id\":\"t2\",\"tool\":\"...\"}]}\n")
        sb.append(AgentToolProtocol.END).append("\n\n")
        sb.append("一次最多调用 ").append(AgentToolProtocol.MAX_CALLS_PER_RESPONSE).append(" 个。\n\n")
        sb.append("可用工具：\n")

        for (tool in enabledTools) {
            val desc = AgentToolConfig.TOOL_DESCRIPTIONS[tool] ?: continue
            sb.append("- ").append(tool).append("：").append(desc).append("\n")
            appendToolSchema(sb, tool)
        }

        sb.append("\n调用工具后，系统会返回结果，然后你再基于结果继续回答。\n")
        sb.append("如果不需要工具，直接正常回复即可，不要输出控制块。\n")

        return sb.toString()
    }

    private fun appendToolSchema(sb: StringBuilder, tool: String) {
        when (tool) {
            "read_file" -> {
                sb.append("  参数：path（必填，文件绝对路径），offset（可选，起始字节），max_bytes（可选，最大字节数）\n")
            }
            "write_file" -> {
                sb.append("  参数：path（必填，文件绝对路径），content（必填，文本内容），append（可选，true 追加）\n")
            }
            "network_request" -> {
                sb.append("  参数：url（必填），method（可选，默认 GET），body（可选），headers（可选，JSON 对象）\n")
            }
            "open_app" -> {
                sb.append("  参数：package_name（必填）\n")
            }
            "app_info" -> {
                sb.append("  参数：package_name（必填）\n")
            }
            "uninstall_app" -> {
                sb.append("  参数：package_name（必填）\n")
            }
        }
    }
}

class AgentToolExecutor(private val context: Context) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun execute(call: ToolCall): ToolResult {
        if (!AgentToolConfig.isEnabled(context, call.tool)) {
            return ToolResult(call.id, call.tool, false, "工具未启用：${call.tool}")
        }

        return try {
            when (call.tool) {
                "get_current_time" -> getCurrentTime(call)
                "read_file" -> readFile(call)
                "write_file" -> writeFile(call)
                "network_request" -> networkRequest(call)
                "open_app" -> openApp(call)
                "list_apps" -> listApps(call)
                "app_info" -> appInfo(call)
                "uninstall_app" -> uninstallApp(call)
                else -> ToolResult(call.id, call.tool, false, "未知工具：${call.tool}")
            }
        } catch (e: Exception) {
            ToolResult(call.id, call.tool, false, "执行失败：${e.message ?: "未知错误"}")
        }
    }

    private fun getCurrentTime(call: ToolCall): ToolResult {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val time = fmt.format(Date())
        return ToolResult(call.id, call.tool, true, "当前时间：$time")
    }

    private fun readFile(call: ToolCall): ToolResult {
        val path = call.args.optString("path", "")
        if (path.isBlank()) {
            return ToolResult(call.id, call.tool, false, "缺少参数 path")
        }

        val file = File(path)
        if (!file.exists()) {
            return ToolResult(call.id, call.tool, false, "文件不存在：$path")
        }
        if (!file.isFile) {
            return ToolResult(call.id, call.tool, false, "路径不是文件：$path")
        }
        if (!isPathAllowed(file)) {
            return ToolResult(call.id, call.tool, false, "路径不在允许范围（仅限 Downloads 目录和 App 私有目录）")
        }

        val offset = call.args.optInt("offset", 0).coerceAtLeast(0)
        val maxBytes = call.args.optInt("max_bytes", 65536).coerceIn(1, 65536)

        return try {
            val bytes = file.readBytes()
            if (offset >= bytes.size) {
                return ToolResult(call.id, call.tool, true, "文件长度 ${bytes.size} 字节，offset 超出范围")
            }
            val end = minOf(offset + maxBytes, bytes.size)
            val slice = bytes.copyOfRange(offset, end)
            val text = String(slice, Charsets.UTF_8)
            val truncated = if (end < bytes.size) "\n...（文件截断，共 ${bytes.size} 字节）" else ""
            ToolResult(call.id, call.tool, true, text + truncated)
        } catch (e: Exception) {
            ToolResult(call.id, call.tool, false, "读取失败：${e.message}")
        }
    }

    private fun writeFile(call: ToolCall): ToolResult {
        val path = call.args.optString("path", "")
        val content = call.args.optString("content", "")
        val append = call.args.optBoolean("append", false)

        if (path.isBlank()) {
            return ToolResult(call.id, call.tool, false, "缺少参数 path")
        }

        val file = File(path)
        if (!isPathAllowed(file)) {
            return ToolResult(call.id, call.tool, false, "路径不在允许范围")
        }

        return try {
            file.parentFile?.mkdirs()
            if (append) {
                file.appendText(content)
            } else {
                file.writeText(content)
            }
            ToolResult(call.id, call.tool, true, "已${if (append) "追加" else "写入"} ${content.length} 字符到 ${file.absolutePath}")
        } catch (e: Exception) {
            ToolResult(call.id, call.tool, false, "写入失败：${e.message}")
        }
    }

    private suspend fun networkRequest(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val url = call.args.optString("url", "")
        if (url.isBlank()) {
            return@withContext ToolResult(call.id, call.tool, false, "缺少参数 url")
        }

        val method = call.args.optString("method", "GET").uppercase()
        val body = call.args.optString("body", "")
        val headersObj = call.args.optJSONObject("headers")

        try {
            val builder = Request.Builder().url(url)
            headersObj?.let { h ->
                val keys = h.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = h.optString(k, "")
                    if (v.isNotEmpty()) builder.addHeader(k, v)
                }
            }

            when (method) {
                "GET", "HEAD", "DELETE" -> builder.method(method, null)
                "POST", "PUT", "PATCH" -> {
                    val reqBody = body.toRequestBody("application/json; charset=utf-8".toMediaType())
                    builder.method(method, reqBody)
                }
                else -> builder.method(method, null)
            }

            val response = httpClient.newCall(builder.build()).execute()
            val respBody = response.body?.string() ?: ""
            val snippet = if (respBody.length > 4096) respBody.take(4096) + "\n...(截断)" else respBody
            ToolResult(
                call.id,
                call.tool,
                response.isSuccessful,
                "HTTP ${response.code}\n$snippet"
            )
        } catch (e: Exception) {
            ToolResult(call.id, call.tool, false, "网络请求失败：${e.message}")
        }
    }

    private fun openApp(call: ToolCall): ToolResult {
        val pkg = call.args.optString("package_name", "")
        if (pkg.isBlank()) {
            return ToolResult(call.id, call.tool, false, "缺少参数 package_name")
        }

        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return ToolResult(call.id, call.tool, false, "未找到可启动的应用：$pkg")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult(call.id, call.tool, true, "已启动 $pkg")
        } catch (e: Exception) {
            ToolResult(call.id, call.tool, false, "启动失败：${e.message}")
        }
    }

    private fun listApps(call: ToolCall): ToolResult {
        return try {
            val pm = context.packageManager
            val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }

            val sb = StringBuilder()
            sb.append("已安装应用（共 ${packages.size} 个，显示前 100 个）：\n")
            var count = 0
            for (p in packages) {
                if (count >= 100) break
                val label = try {
                    pm.getApplicationLabel(p.applicationInfo).toString()
                } catch (_: Exception) {
                    p.packageName
                }
                val system = (p.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                sb.append("- ").append(label).append(" (")
                    .append(p.packageName).append(")")
                    .append(if (system) " [系统]" else "")
                    .append("\n")
                count++
            }
            ToolResult(call.id, call.tool, true, sb.toString())
        } catch (e: Exception) {
            ToolResult(call.id, call.tool, false, "获取应用列表失败：${e.message}")
        }
    }

    private fun appInfo(call: ToolCall): ToolResult {
        val pkg = call.args.optString("package_name", "")
        if (pkg.isBlank()) {
            return ToolResult(call.id, call.tool, false, "缺少参数 package_name")
        }

        return try {
            val pm = context.packageManager
            val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            val appInfo = info.applicationInfo
            val enabled = appInfo?.enabled == true
            val label = try {
                pm.getApplicationLabel(appInfo!!).toString()
            } catch (_: Exception) {
                pkg
            }
            val sb = StringBuilder()
            sb.append("包名：").append(pkg).append("\n")
            sb.append("名称：").append(label).append("\n")
            sb.append("版本名：").append(info.versionName ?: "未知").append("\n")
            sb.append("版本号：").append(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
                else @Suppress("DEPRECATION") info.versionCode.toLong()
            ).append("\n")
            sb.append("是否启用：").append(if (enabled) "是" else "否").append("\n")
            ToolResult(call.id, call.tool, true, sb.toString())
        } catch (e: Exception) {
            ToolResult(call.id, call.tool, false, "查询失败：${e.message}")
        }
    }

    private fun uninstallApp(call: ToolCall): ToolResult {
        val pkg = call.args.optString("package_name", "")
        if (pkg.isBlank()) {
            return ToolResult(call.id, call.tool, false, "缺少参数 package_name")
        }

        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$pkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult(call.id, call.tool, true, "已打开 $pkg 的卸载确认页")
        } catch (e: Exception) {
            ToolResult(call.id, call.tool, false, "打开卸载页失败：${e.message}")
        }
    }

    private fun isPathAllowed(file: File): Boolean {
        val abs = try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }

        val allowedRoots = mutableListOf<String>()

        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            allowedRoots.add(downloads.canonicalPath)
        } catch (_: Exception) {}

        try {
            context.getExternalFilesDir(null)?.let { allowedRoots.add(it.canonicalPath) }
        } catch (_: Exception) {}

        try {
            allowedRoots.add(context.filesDir.canonicalPath)
        } catch (_: Exception) {}

        try {
            allowedRoots.add(context.cacheDir.canonicalPath)
        } catch (_: Exception) {}

        for (root in allowedRoots) {
            if (abs == root || abs.startsWith(root + File.separator)) {
                return true
            }
        }
        return false
    }
}