package com.example.kokoro82m

import KokoroTheme
import MixerScreen
import ai.onnxruntime.OrtSession
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kokoro82m.screens.Acknowledgements
import com.example.kokoro82m.screens.HistoryScreen
import com.example.kokoro82m.utils.HistoryRepository
import com.example.kokoro82m.utils.MainViewModel
import com.example.kokoro82m.utils.PhonemeConverter
import com.example.kokoro82m.utils.StyleLoader
import com.example.kokoro82m.utils.createAudio
import com.example.kokoro82m.utils.playAudio
import com.example.kokoro82m.utils.saveAudio
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var phonemeConverter: PhonemeConverter
    private val scope = MainScope()
    private lateinit var prefs: SharedPreferences
    private lateinit var historyRepo: HistoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("kokoro_settings", Context.MODE_PRIVATE)
        historyRepo = HistoryRepository(this)

        setContent {
            KokoroTheme {
                LaunchedEffect(Unit) {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                }

                val viewModel: MainViewModel = viewModel { MainViewModel(this@MainActivity) }
                val session = remember { viewModel.getSession() }

                val savedStyle = prefs.getString("last_style", "af_sarah") ?: "af_sarah"
                val savedSpeed = prefs.getFloat("last_speed", 1.0f)
                val isDarkMode = prefs.getBoolean("dark_mode", false)

                MainScreen(
                    session = session,
                    phonemeConverter = phonemeConverter,
                    initialStyle = savedStyle,
                    initialSpeed = savedSpeed,
                    initialDarkMode = isDarkMode,
                    historyRepo = historyRepo,
                    onGenerateAudio = { text, style, speed, shouldSave, onComplete ->
                        generateAudio(
                            session,
                            phonemeConverter,
                            text,
                            style,
                            speed,
                            this@MainActivity,
                            scope,
                            shouldSave,
                            historyRepo,
                            onComplete
                        )
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
                    }
                )
            }
        }

        phonemeConverter = PhonemeConverter(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

private fun generateAudio(
    session: OrtSession,
    phonemeConverter: PhonemeConverter,
    text: String,
    style: String,
    speed: Float,
    context: Context,
    scope: CoroutineScope,
    shouldSave: Boolean,
    historyRepo: HistoryRepository,
    onComplete: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            val phonemes = phonemeConverter.phonemize(text)
            Log.d("Kokoro", "Phonemes: $phonemes")

            val (audioData, sampleRate) = createAudio(
                voice = style, phonemes = phonemes, speed = speed, context = context,
                session = session
            )

            playAudio(
                audioData, scope,
                onComplete = onComplete
            )

            if (shouldSave) {
                saveAudio(audioData, context)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "音频已保存", Toast.LENGTH_LONG).show()
                }
            }

            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
            historyRepo.addHistory(
                text = text,
                style = style,
                speed = speed,
                time = timeStr,
                filePath = ""
            )

            session.close()
        } catch (e: Exception) {
            Log.e("Kokoro", "Error: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "错误: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}

sealed class Screen(val title: String) {
    object Basic : Screen("语音合成")
    object Mixer : Screen("音色混合器")
    object History : Screen("历史记录")
    object Settings : Screen("设置")
    object About : Screen("关于")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    session: OrtSession,
    phonemeConverter: PhonemeConverter,
    initialStyle: String,
    initialSpeed: Float,
    initialDarkMode: Boolean,
    historyRepo: HistoryRepository,
    onGenerateAudio: (String, String, Float, Boolean, () -> Unit) -> Unit,
    onSettingsChanged: (String, Float) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit
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
                    icon = { Icon(Icons.Default.Home, contentDescription = "合成") },
                    label = { Text("合成") },
                    selected = currentScreen == Screen.Basic,
                    onClick = { currentScreen = Screen.Basic }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = "混合") },
                    label = { Text("混合") },
                    selected = currentScreen == Screen.Mixer,
                    onClick = { currentScreen = Screen.Mixer }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "历史") },
                    label = { Text("历史") },
                    selected = currentScreen == Screen.History,
                    onClick = { currentScreen = Screen.History }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = currentScreen == Screen.Settings,
                    onClick = { currentScreen = Screen.Settings }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "关于") },
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
                    session = session,
                    initialStyle = initialStyle,
                    initialSpeed = initialSpeed,
                    onGenerateAudio = onGenerateAudio,
                    onSettingsChanged = onSettingsChanged,
                    historyRepo = historyRepo
                )
                Screen.Mixer -> MixerScreen(
                    session = session,
                    phonemeConverter = phonemeConverter,
                    styleLoader = StyleLoader(
                        context = LocalContext.current
                    )
                )
                Screen.History -> HistoryScreen(historyRepo = historyRepo)
                Screen.Settings -> SettingsScreen(
                    isDarkMode = isDarkMode,
                    onDarkModeChanged = { newMode ->
                        isDarkMode = newMode
                        onDarkModeChanged(newMode)
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
    session: OrtSession,
    initialStyle: String,
    initialSpeed: Float,
    onGenerateAudio: (String, String, Float, Boolean, () -> Unit) -> Unit,
    onSettingsChanged: (String, Float) -> Unit,
    historyRepo: HistoryRepository
) {
    var text by remember { mutableStateOf("这是她温暖的心，她最温暖的真心，坚定不移的爱与慰藉。") }
    var style by remember { mutableStateOf(initialStyle) }
    var speed by remember { mutableFloatStateOf(initialSpeed) }
    var isProcessing by remember { mutableStateOf(false) }
    var shouldSaveFile by remember { mutableStateOf(false) }

    val recentHistory = remember { historyRepo.getAllHistory().firstOrNull() }

    val names = listOf(
        "af", "af_bella", "af_nicole", "af_sarah", "af_sky",
        "am_adam", "am_michael", "bf_emma", "bf_isabella", "bm_george", "bm_lewis"
    )
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(style, speed) {
        onSettingsChanged(style, speed)
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (recentHistory != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📋 上次合成", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = recentHistory.text.take(30) + if (recentHistory.text.length > 30) "..." else "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TextField(
                    value = text,
                    minLines = 3,
                    maxLines = 12,
                    onValueChange = { text = it },
                    label = { Text("输入要朗读的文字") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.padding(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = style,
                        onValueChange = { style = it },
                        label = { Text("音色") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        names.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    style = name
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(4.dp))

                Text("语速: $speed", style = MaterialTheme.typography.bodyMedium)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    shouldSaveFile = false
                    isProcessing = true
                    onGenerateAudio(text, style, speed, shouldSaveFile) {
                        isProcessing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                enabled = !isProcessing,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isProcessing) "处理中..." else "▶ 播放")
            }

            Button(
                onClick = {
                    shouldSaveFile = true
                    isProcessing = true
                    onGenerateAudio(text, style, speed, shouldSaveFile) {
                        isProcessing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                enabled = !isProcessing,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isProcessing) "处理中..." else "💾 保存")
            }
        }
    }
}

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("🌙 深色模式", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = onDarkModeChanged
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⚙️ 关于设置", style = MaterialTheme.typography.titleMedium)
                Text("音色和语速会自动保存", style = MaterialTheme.typography.bodySmall)
                Text("历史记录保存在本地", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Acknowledgements()
    }
}