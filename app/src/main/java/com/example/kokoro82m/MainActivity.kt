package com.example.kokoro82m

import KokoroTheme
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.kokoro82m.screens.HistoryScreen
import com.example.kokoro82m.utils.AiProviders
import com.example.kokoro82m.utils.ApiProfile
import com.example.kokoro82m.utils.ApiProfileStore
import com.example.kokoro82m.utils.ApiService
import com.example.kokoro82m.utils.HistoryRepository
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var historyRepo: HistoryRepository
    private val apiService = ApiService()
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("kokoro_settings", Context.MODE_PRIVATE)
        historyRepo = HistoryRepository(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
            }
        }

        setContent {
            KokoroTheme {
                LaunchedEffect(Unit) {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                }
                MainScreen(
                    historyRepo = historyRepo,
                    apiService = apiService,
                    onSpeak = { text, speed ->
                        tts?.setSpeechRate(speed)
                        tts?.setPitch(1.0f)
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                    },
                    onSaveHistory = { text, style, speed ->
                        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                        historyRepo.addHistory(text, style, speed, timeStr)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }
}

sealed class Screen(val title: String) {
    object Basic : Screen("语音合成")
    object Chat : Screen("AI 对话")
    object History : Screen("历史记录")
    object Settings : Screen("设置")
    object About : Screen("关于")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    historyRepo: HistoryRepository,
    apiService: ApiService,
    onSpeak: (String, Float) -> Unit,
    onSaveHistory: (String, String, Float) -> Unit
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Basic) }
    var profiles by remember { mutableStateOf(ApiProfileStore.load(context)) }
    var selectedProfileId by remember {
        mutableStateOf(profiles.firstOrNull { it.enabled }?.id ?: profiles.first().id)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(currentScreen.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("合成") },
                    selected = currentScreen == Screen.Basic,
                    onClick = { currentScreen = Screen.Basic }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Send, contentDescription = null) },
                    label = { Text("聊天") },
                    selected = currentScreen == Screen.Chat,
                    onClick = { currentScreen = Screen.Chat }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("历史") },
                    selected = currentScreen == Screen.History,
                    onClick = { currentScreen = Screen.History }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置") },
                    selected = currentScreen == Screen.Settings,
                    onClick = { currentScreen = Screen.Settings }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("关于") },
                    selected = currentScreen == Screen.About,
                    onClick = { currentScreen = Screen.About }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.Basic -> BasicScreen(
                    onSpeak = onSpeak,
                    onSaveHistory = onSaveHistory
                )
                Screen.Chat -> ChatScreen(
                    apiService = apiService,
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    onProfileSelected = { selectedProfileId = it },
                    onSpeak = onSpeak,
                    onSaveHistory = onSaveHistory
                )
                Screen.History -> HistoryScreen(
                    historyRepo = historyRepo,
                    onPlay = { text -> onSpeak(text, 1.0f) }
                )
                Screen.Settings -> SettingsScreen(
                    profiles = profiles,
                    onProfilesChanged = {
                        profiles = it
                        ApiProfileStore.save(context, it)
                    }
                )
                Screen.About -> AboutScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicScreen(
    onSpeak: (String, Float) -> Unit,
    onSaveHistory: (String, String, Float) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf(1.0f) }
    var isProcessing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                TextField(
                    value = text,
                    minLines = 4,
                    maxLines = 12,
                    onValueChange = { text = it },
                    label = { Text("输入文字") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "系统 TTS 模式，支持中文。需要 AI 对话请切换到聊天页。",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "语速 ${String.format("%.1f", speed)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (text.isEmpty()) {
                        Toast.makeText(context, "请输入文字", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isProcessing = true
                    onSpeak(text, speed)
                    isProcessing = false
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                enabled = !isProcessing && text.isNotEmpty(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("播放")
            }

            Button(
                onClick = {
                    if (text.isEmpty()) {
                        Toast.makeText(context, "请输入文字", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isProcessing = true
                    onSpeak(text, speed)
                    onSaveHistory(text, "系统TTS", speed)
                    isProcessing = false
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                enabled = !isProcessing && text.isNotEmpty(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("保存")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    apiService: ApiService,
    profiles: List<ApiProfile>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit,
    onSpeak: (String, Float) -> Unit,
    onSaveHistory: (String, String, Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var isSending by remember { mutableStateOf(false) }
    var profileExpanded by remember { mutableStateOf(false) }

    val enabledProfiles = profiles.filter { it.enabled }
    val currentProfile = enabledProfiles.find { it.id == selectedProfileId }
        ?: enabledProfiles.firstOrNull()
        ?: profiles.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = profileExpanded,
            onExpandedChange = { profileExpanded = !profileExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = currentProfile.name,
                onValueChange = {},
                label = { Text("当前 API 配置") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded)
                },
                shape = RoundedCornerShape(14.dp)
            )
            ExposedDropdownMenu(
                expanded = profileExpanded,
                onDismissRequest = { profileExpanded = false }
            ) {
                if (enabledProfiles.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("暂无可用配置") },
                        onClick = { profileExpanded = false }
                    )
                } else {
                    enabledProfiles.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                onProfileSelected(p.id)
                                profileExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { (role, content) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (role == "user")
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (role == "ai") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onSpeak(content, 1.0f) }) {
                                    Text("朗读")
                                }
                                TextButton(onClick = {
                                    onSaveHistory(content, "AI回复", 1.0f)
                                }) {
                                    Text("保存")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("输入消息") },
                shape = RoundedCornerShape(14.dp)
            )
            Button(
                onClick = {
                    if (input.isEmpty() || isSending) return@Button
                    val userMsg = input
                    messages = messages + ("user" to userMsg)
                    input = ""
                    isSending = true
                    scope.launch {
                        apiService.chat(
                            prompt = userMsg,
                            profile = currentProfile,
                            onSuccess = { reply ->
                                messages = messages + ("ai" to reply)
                                isSending = false
                            },
                            onError = { error ->
                                messages = messages + ("ai" to "错误: $error")
                                isSending = false
                            }
                        )
                    }
                },
                enabled = !isSending && input.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Text(if (isSending) "..." else "发送")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profiles: List<ApiProfile>,
    onProfilesChanged: (List<ApiProfile>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ApiProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("API 配置池", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = {
                editingProfile = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(profiles) { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "类型: ${profile.providerType}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "模型: ${profile.model}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                if (profile.enabled) "已启用" else "已禁用",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = {
                            editingProfile = profile
                            showDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = {
                            onProfilesChanged(profiles.filter { it.id != profile.id })
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var name by remember { mutableStateOf(editingProfile?.name ?: "") }
        var providerType by remember { mutableStateOf(editingProfile?.providerType ?: "OpenAI") }
        var baseUrl by remember { mutableStateOf(editingProfile?.baseUrl ?: "") }
        var apiKey by remember { mutableStateOf(editingProfile?.apiKey ?: "") }
        var model by remember { mutableStateOf(editingProfile?.model ?: "") }
        var enabled by remember { mutableStateOf(editingProfile?.enabled ?: true) }
        var presetExpanded by remember { mutableStateOf(false) }
        var typeExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(if (editingProfile == null) "添加 API 配置" else "编辑 API 配置")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = presetExpanded,
                        onExpandedChange = { presetExpanded = !presetExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = "选择预设快速填充",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded)
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = presetExpanded,
                            onDismissRequest = { presetExpanded = false }
                        ) {
                            AiProviders.presets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name) },
                                    onClick = {
                                        name = preset.name
                                        providerType = preset.providerType
                                        baseUrl = preset.baseUrl
                                        model = preset.defaultModel
                                        presetExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = providerType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("提供商类型") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            listOf("OpenAI", "Gemini", "Claude", "Ollama").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        providerType = type
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型名称") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("启用")
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank() && baseUrl.isNotBlank()) {
                        val newProfile = editingProfile?.copy(
                            name = name,
                            providerType = providerType,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = model,
                            enabled = enabled
                        ) ?: ApiProfile(
                            name = name,
                            providerType = providerType,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            model = model,
                            enabled = enabled
                        )
                        val newList = if (editingProfile == null) {
                            profiles + newProfile
                        } else {
                            profiles.map {
                                if (it.id == editingProfile?.id) newProfile else it
                            }
                        }
                        onProfilesChanged(newList)
                        showDialog = false
                    }
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Kokoro-82M-Android-Plus", style = MaterialTheme.typography.titleLarge)
        Text("基于原项目二次开发", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("原项目: https://github.com/puff-dayo/Kokoro-82M-Android", style = MaterialTheme.typography.bodySmall)
        Text("本仓库: https://github.com/ZW-SYS/Kokoro-82M-Android-Plus", style = MaterialTheme.typography.bodySmall)
        Text("开发者: ZW-SYS", style = MaterialTheme.typography.bodySmall)
        Text("协议: GPL-3.0", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text("特别感谢", style = MaterialTheme.typography.titleMedium)
        Text("Kokoro (Apache 2.0)", style = MaterialTheme.typography.bodySmall)
        Text("Kokoro-ONNX (MIT)", style = MaterialTheme.typography.bodySmall)
        Text("CMU 词典", style = MaterialTheme.typography.bodySmall)
        Text("IPA 转写器 (GPL-3.0)", style = MaterialTheme.typography.bodySmall)
        Text("Android NNAPI", style = MaterialTheme.typography.bodySmall)
    }
}