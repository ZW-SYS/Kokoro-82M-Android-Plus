package com.example.kokoro82m

import KokoroTheme
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.kokoro82m.screens.HistoryScreen
import com.example.kokoro82m.utils.AiProviders
import com.example.kokoro82m.utils.ApiProfile
import com.example.kokoro82m.utils.ApiProfileStore
import com.example.kokoro82m.utils.ApiService
import com.example.kokoro82m.utils.ChatHistoryRepository
import com.example.kokoro82m.utils.ChatMessage
import com.example.kokoro82m.utils.ChatSession
import com.example.kokoro82m.utils.HistoryRepository
import com.example.kokoro82m.utils.TtsPresets
import com.example.kokoro82m.utils.TtsProfile
import com.example.kokoro82m.utils.TtsProfileStore
import com.example.kokoro82m.utils.TtsService
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
    private lateinit var chatRepo: ChatHistoryRepository
    private val apiService = ApiService()
    private val ttsService = TtsService()
    private var tts: TextToSpeech? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("kokoro_settings", Context.MODE_PRIVATE)
        historyRepo = HistoryRepository(this)
        chatRepo = ChatHistoryRepository(this)

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
                    chatRepo = chatRepo,
                    apiService = apiService,
                    onSpeak = { text, speed, useCloud ->
                        if (useCloud) {
                            val profile = TtsProfileStore.load(this)
                            if (profile.baseUrl.isBlank() || profile.apiKey.isBlank()) {
                                Toast.makeText(this, "请先在设置中配置云端 TTS", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    ttsService.speak(
                                        context = this@MainActivity,
                                        text = text,
                                        profile = profile,
                                        onSuccess = {},
                                        onError = { err ->
                                            Toast.makeText(this@MainActivity, "云端 TTS 失败: $err", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            }
                        } else {
                            tts?.setSpeechRate(speed)
                            tts?.setPitch(1.0f)
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                        }
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
        scope.cancel()
        ttsService.stop()
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
    chatRepo: ChatHistoryRepository,
    apiService: ApiService,
    onSpeak: (String, Float, Boolean) -> Unit,
    onSaveHistory: (String, String, Float) -> Unit
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Basic) }
    var profiles: List<ApiProfile> by remember { mutableStateOf(ApiProfileStore.load(context)) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var ttsProfile by remember { mutableStateOf(TtsProfileStore.load(context)) }

    if (currentSessionId != null) {
        ChatDetailScreen(
            sessionId = currentSessionId!!,
            chatRepo = chatRepo,
            apiService = apiService,
            profiles = profiles,
            onSpeak = { text, speed -> onSpeak(text, speed, ttsProfile.enabled) },
            onSaveHistory = onSaveHistory,
            onBack = { currentSessionId = null }
        )
        return
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
            Crossfade(
                targetState = currentScreen,
                animationSpec = tween(durationMillis = 260),
                label = "screen"
            ) { screen ->
                when (screen) {
                    Screen.Basic -> BasicScreen(
                        ttsProfile = ttsProfile,
                        onSpeak = onSpeak,
                        onSaveHistory = onSaveHistory
                    )
                    Screen.Chat -> SessionListScreen(
                        chatRepo = chatRepo,
                        profiles = profiles,
                        onOpenSession = { currentSessionId = it }
                    )
                    Screen.History -> HistoryScreen(
                        historyRepo = historyRepo,
                        onPlay = { text -> onSpeak(text, 1.0f, ttsProfile.enabled) }
                    )
                    Screen.Settings -> SettingsScreen(
                        apiService = apiService,
                        profiles = profiles,
                        ttsProfile = ttsProfile,
                        onProfilesChanged = { newProfiles ->
                            profiles = newProfiles
                            ApiProfileStore.save(context, newProfiles)
                        },
                        onTtsProfileChanged = { newTts ->
                            ttsProfile = newTts
                            TtsProfileStore.save(context, newTts)
                        }
                    )
                    Screen.About -> AboutScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicScreen(
    ttsProfile: TtsProfile,
    onSpeak: (String, Float, Boolean) -> Unit,
    onSaveHistory: (String, String, Float) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf(1.0f) }
    var isProcessing by remember { mutableStateOf(false) }
    var useCloud by remember { mutableStateOf(ttsProfile.enabled) }

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("使用云端 TTS", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (ttsProfile.name.isBlank()) "未配置" else ttsProfile.name,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = useCloud,
                        onCheckedChange = { useCloud = it }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = if (useCloud) "云端 TTS 会调用你配置的 API 生成语音，音质更好。" else "系统 TTS 模式，离线可用。",
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
                    onSpeak(text, speed, useCloud)
                    isProcessing = false
                },
                modifier = Modifier.weight(1f).height(50.dp),
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
                    onSpeak(text, speed, useCloud)
                    onSaveHistory(text, if (useCloud) "云端TTS" else "系统TTS", speed)
                    isProcessing = false
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f).height(50.dp),
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
fun SessionListScreen(
    chatRepo: ChatHistoryRepository,
    profiles: List<ApiProfile>,
    onOpenSession: (String) -> Unit
) {
    var sessions by remember { mutableStateOf(chatRepo.loadAll()) }
    var selectedProfileId by remember {
        mutableStateOf(profiles.firstOrNull { it.enabled }?.id ?: profiles.first().id)
    }
    var profileExpanded by remember { mutableStateOf(false) }
    var renameSession by remember { mutableStateOf<ChatSession?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun refresh() {
        sessions = chatRepo.loadAll()
    }

    val currentProfile = profiles.find { it.id == selectedProfileId }
    val filtered = sessions.filter { it.profileId == selectedProfileId }
        .sortedByDescending { it.updatedAt }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    value = currentProfile?.name ?: "无配置",
                    onValueChange = {},
                    label = { Text("当前 API 配置") },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded)
                    },
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = profileExpanded,
                    onDismissRequest = { profileExpanded = false }
                ) {
                    profiles.filter { it.enabled }.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                selectedProfileId = p.id
                                profileExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "对话 (${filtered.size})",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = filtered.isEmpty(),
                enter = fadeIn()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "还没有对话，点右下角 + 新建",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { s ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(220)) + slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(220)
                        )
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            onClick = { onOpenSession(s.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                                    val preview = s.messages.lastOrNull()?.content ?: "空对话"
                                    Text(
                                        text = preview.take(40),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${s.messages.size} 条 | 模型 ${s.model}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = {
                                    renameSession = s
                                    renameText = s.name
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "重命名")
                                }
                                IconButton(onClick = {
                                    chatRepo.delete(s.id)
                                    refresh()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                val p = currentProfile ?: return@FloatingActionButton
                val newSession = ChatSession(
                    profileId = p.id,
                    model = p.model.ifBlank { p.models.firstOrNull() ?: "" },
                    name = "新对话"
                )
                chatRepo.save(newSession)
                refresh()
                onOpenSession(newSession.id)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建对话")
        }
    }

    if (renameSession != null) {
        AlertDialog(
            onDismissRequest = { renameSession = null },
            title = { Text("重命名对话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = renameSession
                    if (s != null && renameText.isNotBlank()) {
                        val copy = s.copy(name = renameText)
                        chatRepo.save(copy)
                        refresh()
                    }
                    renameSession = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameSession = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    sessionId: String,
    chatRepo: ChatHistoryRepository,
    apiService: ApiService,
    profiles: List<ApiProfile>,
    onSpeak: (String, Float) -> Unit,
    onSaveHistory: (String, String, Float) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf(chatRepo.loadAll().find { it.id == sessionId }) }
    var messages by remember {
        mutableStateOf(chatRepo.loadAll().find { it.id == sessionId }?.messages?.toList() ?: emptyList())
    }
    var input by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val profile = profiles.find { it.id == session?.profileId }
    val availableModels = profile?.models?.takeIf { it.isNotEmpty() } ?: listOfNotNull(profile?.model)

    fun persist(newMessages: List<ChatMessage>, newModel: String? = null) {
        val s = chatRepo.loadAll().find { it.id == sessionId } ?: return
        s.messages.clear()
        s.messages.addAll(newMessages)
        s.updatedAt = System.currentTimeMillis()
        if (newModel != null) s.model = newModel
        chatRepo.save(s)
        session = s
        messages = newMessages
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { uriToBase64(context, uri) }
                if (result != null) {
                    pendingImage = result
                } else {
                    Toast.makeText(context, "图片读取失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(session?.name ?: "对话") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    ExposedDropdownMenuBox(
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = !modelExpanded }
                    ) {
                        TextButton(
                            onClick = { modelExpanded = true },
                            modifier = Modifier.menuAnchor()
                        ) {
                            Text(
                                session?.model ?: "模型",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            availableModels.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        val newMsgs = messages.toList()
                                        persist(newMsgs, m)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        renameText = session?.name ?: ""
                        showRename = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "重命名")
                    }
                    IconButton(onClick = {
                        chatRepo.delete(sessionId)
                        onBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(200)) + slideInVertically(
                            initialOffsetY = { it / 6 },
                            animationSpec = tween(220)
                        )
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.role == "user")
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (!msg.content.isNullOrEmpty()) {
                                    Text(
                                        text = msg.content,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (msg.imageBase64 != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Base64Image(
                                        base64 = msg.imageBase64,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                    )
                                }
                                if (msg.role == "ai") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { onSpeak(msg.content, 1.0f) }) {
                                            Text("朗读")
                                        }
                                        TextButton(onClick = {
                                            onSaveHistory(msg.content, "AI回复", 1.0f)
                                        }) {
                                            Text("保存")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = pendingImage != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 })
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        pendingImage?.let { img ->
                            Base64Image(
                                base64 = img.first,
                                modifier = Modifier.size(60.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("待发送图片", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { pendingImage = null }) {
                            Text("取消")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { imageLauncher.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = "选择图片")
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("输入消息") },
                    shape = RoundedCornerShape(14.dp)
                )
                Button(
                    onClick = {
                        if ((input.isBlank() && pendingImage == null) || isSending) return@Button
                        val userMsg = ChatMessage(
                            role = "user",
                            content = input,
                            imageBase64 = pendingImage?.first,
                            imageMimeType = pendingImage?.second
                        )
                        val updated = messages + userMsg
                        persist(updated)
                        input = ""
                        pendingImage = null
                        isSending = true

                        val p = profile ?: run {
                            Toast.makeText(context, "未找到 API 配置", Toast.LENGTH_SHORT).show()
                            isSending = false
                            return@Button
                        }

                        val sessionModel = session?.model ?: p.model
                        val useProfile = p.copy(model = sessionModel)

                        scope.launch {
                            apiService.chat(
                                messages = updated,
                                profile = useProfile,
                                onSuccess = { reply ->
                                    val withReply = messages + ChatMessage("ai", reply)
                                    persist(withReply)
                                    isSending = false
                                },
                                onError = { error ->
                                    val withError = messages + ChatMessage("ai", "错误: $error")
                                    persist(withError)
                                    isSending = false
                                }
                            )
                        }
                    },
                    enabled = !isSending && (input.isNotBlank() || pendingImage != null),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(if (isSending) "..." else "发送")
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("重命名对话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = session
                    if (s != null && renameText.isNotBlank()) {
                        val copy = s.copy(name = renameText)
                        chatRepo.save(copy)
                        session = copy
                    }
                    showRename = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun Base64Image(base64: String, modifier: Modifier = Modifier) {
    val bitmap = remember(base64) {
        try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

fun uriToBase64(context: Context, uri: Uri): Pair<String, String>? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) return null

        val maxSize = 1024
        val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val finalBitmap = if (ratio < 1) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else bitmap

        val outputStream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        Pair(base64, "image/jpeg")
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiService: ApiService,
    profiles: List<ApiProfile>,
    ttsProfile: TtsProfile,
    onProfilesChanged: (List<ApiProfile>) -> Unit,
    onTtsProfileChanged: (TtsProfile) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ApiProfile?>(null) }
    var showTtsDialog by remember { mutableStateOf(false) }

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
            modifier = Modifier.weight(1f),
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
                            Text("类型: ${profile.providerType}", style = MaterialTheme.typography.bodySmall)
                            Text("模型: ${profile.model}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "共 ${profile.models.size} 个模型",
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

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    onClick = { showTtsDialog = true }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("云端 TTS 配置", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (ttsProfile.baseUrl.isBlank()) "未配置，点击设置"
                            else "${ttsProfile.name} / ${ttsProfile.model} / ${ttsProfile.voice}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    if (showTtsDialog) {
        var name by remember { mutableStateOf(ttsProfile.name) }
        var baseUrl by remember { mutableStateOf(ttsProfile.baseUrl) }
        var apiKey by remember { mutableStateOf(ttsProfile.apiKey) }
        var model by remember { mutableStateOf(ttsProfile.model) }
        var voice by remember { mutableStateOf(ttsProfile.voice) }
        var enabled by remember { mutableStateOf(ttsProfile.enabled) }
        var presetExpanded by remember { mutableStateOf(false) }
        var voiceExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showTtsDialog = false },
            title = { Text("云端 TTS 配置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = presetExpanded,
                        onExpandedChange = { presetExpanded = !presetExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = "选择预设",
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
                            TtsPresets.presets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name) },
                                    onClick = {
                                        name = preset.name
                                        baseUrl = preset.baseUrl
                                        model = preset.model
                                        voice = preset.voice
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
                        label = { Text("模型") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    ExposedDropdownMenuBox(
                        expanded = voiceExpanded,
                        onExpandedChange = { voiceExpanded = !voiceExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = voice,
                            onValueChange = { voice = it },
                            label = { Text("音色") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = voiceExpanded,
                            onDismissRequest = { voiceExpanded = false }
                        ) {
                            TtsPresets.voices.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text(v) },
                                    onClick = {
                                        voice = v
                                        voiceExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("启用为默认引擎")
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newProfile = TtsProfile(
                        id = ttsProfile.id,
                        name = name,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        model = model,
                        voice = voice,
                        enabled = enabled
                    )
                    onTtsProfileChanged(newProfile)
                    showTtsDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTtsDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDialog) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var name by remember { mutableStateOf(editingProfile?.name ?: "") }
        var providerType by remember { mutableStateOf(editingProfile?.providerType ?: "OpenAI") }
        var baseUrl by remember { mutableStateOf(editingProfile?.baseUrl ?: "") }
        var apiKey by remember { mutableStateOf(editingProfile?.apiKey ?: "") }
        var model by remember { mutableStateOf(editingProfile?.model ?: "") }
        var models by remember { mutableStateOf<List<String>>(editingProfile?.models ?: emptyList()) }
        var enabled by remember { mutableStateOf(editingProfile?.enabled ?: true) }
        var presetExpanded by remember { mutableStateOf(false) }
        var typeExpanded by remember { mutableStateOf(false) }
        var testing by remember { mutableStateOf(false) }
        var fetching by remember { mutableStateOf(false) }
        var showModelsDialog by remember { mutableStateOf(false) }

        fun buildProfile(): ApiProfile {
            return editingProfile?.copy(
                name = name,
                providerType = providerType,
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                models = models,
                enabled = enabled
            ) ?: ApiProfile(
                name = name,
                providerType = providerType,
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                models = models,
                enabled = enabled
            )
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingProfile == null) "添加 API 配置" else "编辑 API 配置") },
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
                                        models = preset.models
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("默认模型") },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                fetching = true
                                scope.launch {
                                    apiService.fetchModels(
                                        profile = buildProfile(),
                                        onSuccess = { list ->
                                            models = list
                                            showModelsDialog = true
                                            fetching = false
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, "获取失败: $err", Toast.LENGTH_LONG).show()
                                            fetching = false
                                        }
                                    )
                                }
                            },
                            enabled = !fetching
                        ) {
                            Text(if (fetching) "..." else "获取")
                        }
                    }

                    Text(
                        text = "已保存 ${models.size} 个模型",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("启用")
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                testing = true
                                scope.launch {
                                    apiService.testConnection(
                                        profile = buildProfile(),
                                        onResult = { ok, msg ->
                                            Toast.makeText(
                                                context,
                                                if (ok) "连接成功" else "连接失败: $msg",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            testing = false
                                        }
                                    )
                                }
                            },
                            enabled = !testing
                        ) {
                            Text(if (testing) "测试中..." else "测试连接")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank() && baseUrl.isNotBlank()) {
                        val newProfile = buildProfile()
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

        if (showModelsDialog) {
            AlertDialog(
                onDismissRequest = { showModelsDialog = false },
                title = { Text("选择默认模型") },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(models) { m ->
                            TextButton(
                                onClick = {
                                    model = m
                                    showModelsDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(m)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showModelsDialog = false }) {
                        Text("关闭")
                    }
                }
            )
        }
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
        Text(
            "原项目: https://github.com/puff-dayo/Kokoro-82M-Android",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "本仓库: https://github.com/ZW-SYS/Kokoro-82M-Android-Plus",
            style = MaterialTheme.typography.bodySmall
        )
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