package com.example.kokoro82m

import KokoroTheme
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.kokoro82m.screens.Acknowledgements
import com.example.kokoro82m.screens.HistoryScreen
import com.example.kokoro82m.utils.AiProviders
import com.example.kokoro82m.utils.ApiService
import com.example.kokoro82m.utils.HistoryRepository
import com.google.android.material.color.DynamicColors
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
    private lateinit var apiService: ApiService
    private var tts: TextToSpeech? = null

    companion object {
        const val TAG = "Kokoro"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = getSharedPreferences("kokoro_settings", Context.MODE_PRIVATE)
        historyRepo = HistoryRepository(this)
        apiService = ApiService()

        apiService.apiKey = prefs.getString("api_key", "") ?: ""
        apiService.selectedProvider = prefs.getString("selected_provider", "DeepSeek") ?: "DeepSeek"
        apiService.selectedModel = prefs.getString("selected_model", "deepseek-chat") ?: "deepseek-chat"
        apiService.customBaseUrl = prefs.getString("custom_base_url", "") ?: ""
        apiService.customModel = prefs.getString("custom_model", "") ?: ""

        initTTS()

        setContent {
            KokoroTheme {
                LaunchedEffect(Unit) {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                }

                val savedStyle = prefs.getString("last_style", "af_sarah") ?: "af_sarah"
                val savedSpeed = prefs.getFloat("last_speed", 1.0f)
                val isDarkMode = prefs.getBoolean("dark_mode", false)
                val savedEngine = prefs.getInt("tts_engine", 0)

                MainScreen(
                    initialStyle = savedStyle,
                    initialSpeed = savedSpeed,
                    initialDarkMode = isDarkMode,
                    initialEngine = savedEngine,
                    initialProvider = apiService.selectedProvider,
                    initialModel = apiService.selectedModel,
                    initialApiKey = apiService.apiKey,
                    historyRepo = historyRepo,
                    onGenerateAudio = { text, style, speed, shouldSave, engine, onComplete ->
                        generateAudio(text, style, speed, shouldSave, engine, onComplete)
                    },
                    onPlayHistory = { text ->
                        speakText(text, 1.0f)
                    },
                    onSettingsChanged = { style, speed ->
                        prefs.edit().apply {
                            putString("last_style", style)
                            putFloat("last_speed", speed)
                            apply()
                        }
                    },
                    onDarkModeChanged = { isDark ->
                        prefs.edit().putBoolean("dark_mode", isDark).apply()
                    },
                    onProviderChanged = { provider ->
                        apiService.selectedProvider = provider
                        prefs.edit().putString("selected_provider", provider).apply()
                    },
                    onModelChanged = { model ->
                        apiService.selectedModel = model
                        prefs.edit().putString("selected_model", model).apply()
                    },
                    onApiKeyChanged = { key ->
                        apiService.apiKey = key
                        prefs.edit().putString("api_key", key).apply()
                    },
                    onCustomUrlChanged = { url ->
                        apiService.customBaseUrl = url
                        prefs.edit().putString("custom_base_url", url).apply()
                    },
                    onCustomModelChanged = { model ->
                        apiService.customModel = model
                        prefs.edit().putString("custom_model", model).apply()
                    }
                )
            }
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                Log.d(TAG, "TTS init success")
            } else {
                Log.e(TAG, "TTS init failed")
            }
        }
    }

    private fun speakText(text: String, speed: Float) {
        val ttsInstance = tts
        if (ttsInstance == null) {
            Toast.makeText(this, "TTS 未初始化", Toast.LENGTH_SHORT).show()
            return
        }
        ttsInstance.setSpeechRate(speed)
        ttsInstance.setPitch(1.0f)
        ttsInstance.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun generateAudio(
        text: String,
        style: String,
        speed: Float,
        shouldSave: Boolean,
        engine: Int,
        onComplete: () -> Unit
    ) {
        when (engine) {
            0 -> speakWithSystemTTS(text, speed, shouldSave, onComplete)
            1 -> speakWithCloudAI(text, speed, shouldSave, onComplete)
            else -> speakWithSystemTTS(text, speed, shouldSave, onComplete)
        }
    }

    private fun speakWithSystemTTS(
        text: String,
        speed: Float,
        shouldSave: Boolean,
        onComplete: () -> Unit
    ) {
        val ttsInstance = tts
        if (ttsInstance == null) {
            Toast.makeText(this, "TTS 未初始化", Toast.LENGTH_SHORT).show()
            onComplete()
            return
        }

        ttsInstance.setSpeechRate(speed)
        ttsInstance.setPitch(1.0f)

        val result = ttsInstance.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)

        if (result == TextToSpeech.SUCCESS) {
            if (shouldSave) {
                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                historyRepo.addHistory(
                    text = text,
                    style = "系统TTS",
                    speed = speed,
                    time = timeStr,
                    filePath = ""
                )
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "朗读失败", Toast.LENGTH_SHORT).show()
        }
        onComplete()
    }

    private fun speakWithCloudAI(
        text: String,
        speed: Float,
        shouldSave: Boolean,
        onComplete: () -> Unit
    ) {
        Toast.makeText(this, "AI 思考中", Toast.LENGTH_SHORT).show()

        apiService.chat(
            prompt = text,
            onSuccess = { reply ->
                runOnUiThread {
                    val ttsInstance = tts
                    if (ttsInstance != null) {
                        ttsInstance.setSpeechRate(speed)
                        ttsInstance.setPitch(1.0f)
                        ttsInstance.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null)
                    }

                    if (shouldSave) {
                        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                        historyRepo.addHistory(
                            text = "用户: $text\nAI: $reply",
                            style = "AI (${apiService.selectedProvider})",
                            speed = speed,
                            time = timeStr,
                            filePath = ""
                        )
                        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
                    }
                    onComplete()
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "错误: $error", Toast.LENGTH_LONG).show()
                    onComplete()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }
}

sealed class Screen(val title: String) {
    object Basic : Screen("语音合成")
    object History : Screen("历史记录")
    object Settings : Screen("设置")
    object About : Screen("关于")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialStyle: String,
    initialSpeed: Float,
    initialDarkMode: Boolean,
    initialEngine: Int,
    initialProvider: String,
    initialModel: String,
    initialApiKey: String,
    historyRepo: HistoryRepository,
    onGenerateAudio: (String, String, Float, Boolean, Int, () -> Unit) -> Unit,
    onPlayHistory: (String) -> Unit,
    onSettingsChanged: (String, Float) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit,
    onProviderChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onCustomUrlChanged: (String) -> Unit,
    onCustomModelChanged: (String) -> Unit
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Basic) }
    var isDarkMode by remember { mutableStateOf(initialDarkMode) }

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
                    initialStyle = initialStyle,
                    initialSpeed = initialSpeed,
                    initialEngine = initialEngine,
                    onGenerateAudio = onGenerateAudio,
                    onSettingsChanged = onSettingsChanged
                )
                Screen.History -> HistoryScreen(
                    historyRepo = historyRepo,
                    onPlay = onPlayHistory
                )
                Screen.Settings -> SettingsScreen(
                    isDarkMode = isDarkMode,
                    initialProvider = initialProvider,
                    initialModel = initialModel,
                    initialApiKey = initialApiKey,
                    onDarkModeChanged = { newMode ->
                        isDarkMode = newMode
                        onDarkModeChanged(newMode)
                    },
                    onProviderChanged = onProviderChanged,
                    onModelChanged = onModelChanged,
                    onApiKeyChanged = onApiKeyChanged,
                    onCustomUrlChanged = onCustomUrlChanged,
                    onCustomModelChanged = onCustomModelChanged
                )
                Screen.About -> AboutScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicScreen(
    initialStyle: String,
    initialSpeed: Float,
    initialEngine: Int,
    onGenerateAudio: (String, String, Float, Boolean, Int, () -> Unit) -> Unit,
    onSettingsChanged: (String, Float) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(initialStyle) }
    var speed by remember { mutableStateOf(initialSpeed) }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedEngine by remember { mutableStateOf(initialEngine) }
    var engineExpanded by remember { mutableStateOf(false) }
    var styleExpanded by remember { mutableStateOf(false) }

    val engineNames = listOf("系统TTS", "AI 对话")
    val names = listOf(
        "af", "af_bella", "af_nicole", "af_sarah", "af_sky",
        "am_adam", "am_michael", "bf_emma", "bf_isabella", "bm_george", "bm_lewis"
    )

    LaunchedEffect(style, speed) {
        onSettingsChanged(style, speed)
    }

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

                ExposedDropdownMenuBox(
                    expanded = engineExpanded,
                    onExpandedChange = { engineExpanded = !engineExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = engineNames[selectedEngine],
                        onValueChange = {},
                        label = { Text("模式") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineExpanded)
                        },
                        shape = RoundedCornerShape(14.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = engineExpanded,
                        onDismissRequest = { engineExpanded = false }
                    ) {
                        engineNames.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedEngine = index
                                    engineExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedEngine == 0) {
                    Spacer(modifier = Modifier.height(10.dp))

                    ExposedDropdownMenuBox(
                        expanded = styleExpanded,
                        onExpandedChange = { styleExpanded = !styleExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = style,
                            onValueChange = {},
                            label = { Text("音色") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleExpanded)
                            },
                            shape = RoundedCornerShape(14.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = styleExpanded,
                            onDismissRequest = { styleExpanded = false }
                        ) {
                            names.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        style = name
                                        styleExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "输入内容将发送给 AI，回复会朗读并保存",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "语速 $speed",
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
                        Toast.makeText(LocalContext.current, "请输入文字", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isProcessing = true
                    onGenerateAudio(text, style, speed, false, selectedEngine) {
                        isProcessing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .height(50.dp),
                enabled = !isProcessing,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isProcessing) "处理中" else "播放")
            }

            Button(
                onClick = {
                    if (text.isEmpty()) {
                        Toast.makeText(LocalContext.current, "请输入文字", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isProcessing = true
                    onGenerateAudio(text, style, speed, true, selectedEngine) {
                        isProcessing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .height(50.dp),
                enabled = !isProcessing,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isProcessing) "处理中" else "保存")
            }
        }
    }
}

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    initialProvider: String,
    initialModel: String,
    initialApiKey: String,
    onDarkModeChanged: (Boolean) -> Unit,
    onProviderChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onCustomUrlChanged: (String) -> Unit,
    onCustomModelChanged: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var provider by remember { mutableStateOf(initialProvider) }
    var model by remember { mutableStateOf(initialModel) }
    var customUrl by remember { mutableStateOf("") }
    var customModel by remember { mutableStateOf("") }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    val providers = AiProviders.providers.map { it.name }
    val providerObj = AiProviders.providers.find { it.name == provider }
    val models = providerObj?.models ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("深色模式", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = onDarkModeChanged
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("AI 提供商", style = MaterialTheme.typography.titleMedium)

                Spacer(modifier = Modifier.height(10.dp))

                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = !providerExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = provider,
                        onValueChange = {},
                        label = { Text("选择") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded)
                        },
                        shape = RoundedCornerShape(14.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        providers.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    provider = name
                                    providerExpanded = false
                                    val p = AiProviders.providers.find { it.name == name }
                                    if (p != null && p.models.isNotEmpty()) {
                                        model = p.defaultModel
                                        onModelChanged(model)
                                    }
                                    onProviderChanged(provider)
                                }
                            )
                        }
                    }
                }

                if (models.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    ExposedDropdownMenuBox(
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = !modelExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = model,
                            onValueChange = {},
                            label = { Text("模型") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                            },
                            shape = RoundedCornerShape(14.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            models.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        model = name
                                        modelExpanded = false
                                        onModelChanged(model)
                                    }
                                )
                            }
                        }
                    }
                }

                if (provider == "自定义") {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it
                            onCustomUrlChanged(it)
                        },
                        label = { Text("API 地址") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextField(
                        value = customModel,
                        onValueChange = {
                            customModel = it
                            onCustomModelChanged(it)
                        },
                        label = { Text("模型名称") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        onApiKeyChanged(it)
                    },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                if (provider == "Ollama") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "默认地址 http://localhost:11434",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("说明", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text("系统TTS 使用手机自带引擎，支持中文", style = MaterialTheme.typography.bodySmall)
                Text("AI 对话模式需配置 API Key", style = MaterialTheme.typography.bodySmall)
                Text("历史记录保存在本地", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Acknowledgements()
    }
}