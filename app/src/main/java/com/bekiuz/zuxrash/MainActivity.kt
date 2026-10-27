package com.bekiuz.zuxrash

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Background = Color(0xFF090711)
private val SurfaceDark = Color(0xFF14111E)
private val SurfaceRaised = Color(0xFF1B1628)
private val Violet = Color(0xFFA78BFA)
private val Pink = Color(0xFFF472B6)
private val Rose = Color(0xFFFB7185)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFFAAA4BB)
private val Border = Color(0xFF322444)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 7, 17)
        window.navigationBarColor = android.graphics.Color.rgb(9, 7, 17)
        window.decorView.systemUiVisibility = 0
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Violet,
                secondary = Pink,
                background = Background,
                surface = SurfaceDark,
                onPrimary = Background,
                onBackground = TextPrimary,
                onSurface = TextPrimary
            )) {
                ZuxrashAssistantApp()
            }
        }
    }
}

@Composable
private fun ZuxrashAssistantApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zuxrash_assistant", Context.MODE_PRIVATE) }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(loadHistory(prefs)) }
    }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var endpoint by remember {
        mutableStateOf(prefs.getString("api_url", BuildConfig.API_BASE_URL) ?: BuildConfig.API_BASE_URL)
    }
    var endpointDraft by remember { mutableStateOf(endpoint) }
    var isLoading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var speakReplies by remember { mutableStateOf(prefs.getBoolean("speak_replies", true)) }
    var wakeWordEnabled by remember { mutableStateOf(prefs.getBoolean("wake_word_enabled", false)) }
    var statusMessage by remember { mutableStateOf("Yordam berishga tayyorman") }
    var ttsReady by remember { mutableStateOf(false) }

    fun updateWakeWordService(action: String) {
        if (!prefs.getBoolean("wake_word_enabled", false)) return
        try {
            context.startService(Intent(context, WakeWordService::class.java).setAction(action))
        } catch (_: Exception) {
            statusMessage = "Fon tinglash xizmatini boshqarib bo‘lmadi"
        }
    }

    fun pauseWakeWordIfEnabled() = updateWakeWordService(WakeWordService.ACTION_PAUSE)
    fun resumeWakeWordIfEnabled() = updateWakeWordService(WakeWordService.ACTION_RESUME)
    val clientId = remember {
        prefs.getString("client_id", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            prefs.edit().putString("client_id", it).apply()
        }
    }

    val tts = remember(context) {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            val uz = Locale.forLanguageTag("uz-UZ")
            val ru = Locale.forLanguageTag("ru-RU")
            tts.language = when {
                tts.isLanguageAvailable(uz) >= TextToSpeech.LANG_AVAILABLE -> uz
                tts.isLanguageAvailable(ru) >= TextToSpeech.LANG_AVAILABLE -> ru
                else -> Locale.US
            }
        }
    }
    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { }
            override fun onDone(utteranceId: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { resumeWakeWordIfEnabled() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { resumeWakeWordIfEnabled() }
            }
        })
        onDispose { tts.stop(); tts.shutdown() }
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(speechRecognizer) {
        onDispose { speechRecognizer?.destroy() }
    }

    fun sendText(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank() || isLoading) return
        pauseWakeWordIfEnabled()
        messages.add(ChatMessage(role = "user", text = text))
        input = ""
        val localReply = runLocalCommand(context, text)
        if (localReply != null) {
            messages.add(ChatMessage(role = "assistant", text = localReply))
            statusMessage = "Buyruq bajarildi"
            if (speakReplies && ttsReady) {
                val result = tts.speak(localReply, TextToSpeech.QUEUE_FLUSH, null, "zuxrash-local")
                if (result != TextToSpeech.SUCCESS) resumeWakeWordIfEnabled()
            } else {
                resumeWakeWordIfEnabled()
            }
            return
        }
        val history = messages.toList().takeLast(12)
        isLoading = true
        statusMessage = "Javob tayyorlanyapti…"
        scope.launch {
            var ttsWillResumeWakeWord = false
            try {
                val reply = withContext(Dispatchers.IO) { AiClient.reply(endpoint, history, clientId) }
                messages.add(ChatMessage(role = "assistant", text = reply))
                statusMessage = "Ulandi"
                if (speakReplies && ttsReady) {
                    ttsWillResumeWakeWord = tts.speak(
                        reply.take(3500),
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "zuxrash-reply-" + System.currentTimeMillis()
                    ) == TextToSpeech.SUCCESS
                }
            } catch (error: Exception) {
                val message = error.message?.take(320).orEmpty().ifBlank { "Tarmoq yoki AI serverida xatolik." }
                messages.add(ChatMessage(role = "assistant", text = "Ulanishda muammo: $message\n\nServer manzilini Sozlamalardan tekshir."))
                statusMessage = "Serverni tekshiring"
            } finally {
                isLoading = false
                if (!ttsWillResumeWakeWord) resumeWakeWordIfEnabled()
            }
        }
    }

    fun startListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            statusMessage = "Bu telefonda nutqni tanish xizmati topilmadi"
            Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
            return
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; statusMessage = "Tinglayapman…" }
            override fun onBeginningOfSpeech() { isListening = true }
            override fun onRmsChanged(rmsdB: Float) { }
            override fun onBufferReceived(buffer: ByteArray?) { }
            override fun onEndOfSpeech() { isListening = false; statusMessage = "Ovoz qayta ishlanyapti…" }
            override fun onError(error: Int) {
                isListening = false
                resumeWakeWordIfEnabled()
                statusMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Gapni anglay olmadim — qayta urinib ko‘r"
                    SpeechRecognizer.ERROR_NETWORK -> "Nutqni tanish uchun internet kerak"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mikrofon band, biroz kut"
                    else -> "Mikrofon qayta tayyor"
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) sendText(spoken) else {
                    statusMessage = "Ovoz aniqlanmadi"
                    resumeWakeWordIfEnabled()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) { }
            override fun onEvent(eventType: Int, params: Bundle?) { }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Zuxrash AI tinglayapti…")
        }
        pauseWakeWordIfEnabled()
        try {
            recognizer.startListening(intent)
            isListening = true
            statusMessage = "Tinglayapman…"
        } catch (_: Exception) {
            isListening = false
            statusMessage = "Mikrofonni ishga tushirib bo‘lmadi"
        }
    }

    val requestMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else statusMessage = "Ovozli buyruq uchun mikrofon ruxsati kerak"
    }

    fun startWakeWordService() {
        try {
            val intent = Intent(context, WakeWordService::class.java).setAction(WakeWordService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            prefs.edit().putBoolean("wake_word_enabled", true).apply()
            wakeWordEnabled = true
            statusMessage = "Hey Zuxrash fon rejimi yoqildi"
            Toast.makeText(context, "Fon tinglash yoqildi. Bildirishnomadan to‘xtatishingiz mumkin.", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            prefs.edit().putBoolean("wake_word_enabled", false).apply()
            wakeWordEnabled = false
            statusMessage = "Fon xizmatini ishga tushirib bo‘lmadi"
            Toast.makeText(context, "Fon xizmati ishga tushmadi: " + (error.message ?: "xatolik"), Toast.LENGTH_LONG).show()
        }
    }

    val requestWakeWordPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            grants[Manifest.permission.POST_NOTIFICATIONS] == true ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (micGranted && notificationsGranted) {
            startWakeWordService()
        } else {
            wakeWordEnabled = false
            prefs.edit().putBoolean("wake_word_enabled", false).apply()
            statusMessage = "Fon rejimi uchun mikrofon va bildirishnoma ruxsati kerak"
            Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
        }
    }

    fun setWakeWordMode(enabled: Boolean) {
        if (!enabled) {
            context.stopService(Intent(context, WakeWordService::class.java))
            prefs.edit().putBoolean("wake_word_enabled", false).apply()
            wakeWordEnabled = false
            statusMessage = "Hey Zuxrash fon rejimi o‘chirildi"
            return
        }
        val missing = mutableListOf<String>()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missing += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        if (missing.isEmpty()) startWakeWordService()
        else requestWakeWordPermissions.launch(missing.toTypedArray())
    }

    LaunchedEffect(messages.size) {
        saveHistory(prefs, messages.toList())
    }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Background, Color(0xFF100919), Background)))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        ) {
            HeaderBar(
                status = statusMessage,
                onSettings = { endpointDraft = endpoint; showSettings = true },
                onClear = {
                    messages.clear()
                    statusMessage = "Yangi suhbat"
                }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    WelcomePanel(
                        onPrompt = { sendText(it) }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        items(messages) { message -> MessageBubble(message) }
                        if (isLoading) item { TypingBubble() }
                    }
                }
            }

            QuickPrompts(onPrompt = { sendText(it) })

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(6000) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Zuxrashga yoz…", color = TextMuted, fontSize = 14.sp) },
                        maxLines = 5,
                        minLines = 1,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendText(input) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Pink,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (isListening) {
                            speechRecognizer?.cancel()
                            isListening = false
                            statusMessage = "Ovoz tinglash to‘xtatildi"
                        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startListening()
                        } else {
                            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.size(50.dp).clip(CircleShape).background(if (isListening) Rose.copy(alpha = 0.18f) else SurfaceRaised)
                ) {
                    Icon(if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Ovozli kiritish", tint = if (isListening) Rose else Pink)
                }
                Button(
                    onClick = { sendText(input) },
                    enabled = input.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(17.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet, disabledContainerColor = SurfaceRaised),
                    modifier = Modifier.size(50.dp)
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TextPrimary)
                    else Icon(Icons.Default.Send, contentDescription = "Yuborish", tint = Background)
                }
            }
            Text(
                text = "Xabarlar AI serveriga yuboriladi · Maxfiy ma’lumot yuborma",
                color = TextMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 12.dp, end = 12.dp)
            )
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = SurfaceDark,
            title = { Text("Zuxrash AI sozlamalari", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AI server manzili", color = TextMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = endpointDraft,
                        onValueChange = { endpointDraft = it.take(300) },
                        singleLine = true,
                        placeholder = { Text("https://...workers.dev") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Pink,
                            unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Javoblarni ovozli o‘qish", color = TextPrimary, fontSize = 14.sp)
                            Text("Telefon TTS ovozidan foydalanadi", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(checked = speakReplies, onCheckedChange = {
                            speakReplies = it
                            prefs.edit().putBoolean("speak_replies", it).apply()
                        })
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text("“Hey Zuxrash” fon rejimi", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Ilova yopilganda ham uyg‘otuvchi so‘zni tinglaydi", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = wakeWordEnabled,
                            onCheckedChange = { setWakeWordMode(it) }
                        )
                    }
                    Text(
                        "Yoqilganda mikrofon doimiy faol bo‘ladi va Android nutqni tanish xizmatidan foydalanadi. Doimiy bildirishnoma ko‘rinadi; undagi “To‘xtatish” tugmasi fon tinglashni o‘chiradi.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleaned = endpointDraft.trim().trimEnd('/')
                    if (cleaned.startsWith("https://")) {
                        endpoint = cleaned
                        prefs.edit().putString("api_url", cleaned).apply()
                        statusMessage = "Server manzili saqlandi"
                        showSettings = false
                    } else {
                        Toast.makeText(context, "Manzil https:// bilan boshlanishi kerak", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Saqlash", color = Pink) }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Bekor qilish", color = TextMuted) } }
        )
    }
}

@Composable
private fun HeaderBar(status: String, onSettings: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Violet, Pink, Rose)))
                .padding(1.5.dp)
                .clip(RoundedCornerShape(14.dp)).background(Color(0xFF110A1D)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = "Zuxrash AI", tint = Pink, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ZUXRASH", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, letterSpacing = 1.1.sp)
                Spacer(Modifier.width(5.dp))
                Text("AI", color = Pink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Spacer(Modifier.width(7.dp))
                Surface(color = Violet.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                    Text("BETA", color = Violet, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            Text(status, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onClear) { Icon(Icons.Default.AutoAwesome, contentDescription = "Yangi suhbat", tint = TextMuted) }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Sozlamalar", tint = Pink) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Border.copy(alpha = 0.75f)))
}

@Composable
private fun WelcomePanel(onPrompt: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Violet.copy(alpha = 0.22f), Pink.copy(alpha = 0.18f))))
                .border(1.dp, Border.copy(alpha = 0.9f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Pink, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Salom, men Zuxrash AI", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Savollarga javob beraman, fikrlarni tartiblayman va telefon buyruqlarida yordam beraman.",
            color = TextMuted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(25.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            WelcomePrompt("✦  Bugungi rejamni tuz", "Bugun uchun bajarishim mumkin bo‘lgan sodda reja tuz.", onPrompt)
            WelcomePrompt("⌘  Dasturlashda yordam", "Menga dasturlashdagi bir muammoni tushuntir.", onPrompt)
            WelcomePrompt("✧  G‘oya topish", "O‘zbekiston uchun foydali raqamli loyiha g‘oyalarini taklif qil.", onPrompt)
        }
    }
}

@Composable
private fun WelcomePrompt(title: String, prompt: String, onPrompt: (String) -> Unit) {
    Surface(
        onClick = { onPrompt(prompt) },
        color = SurfaceDark,
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp))
    }
}

@Composable
private fun QuickPrompts(onPrompt: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickPrompt("Reja tuzish") { onPrompt("Bugungi kunim uchun amaliy va sodda reja tuz.") }
        QuickPrompt("G‘oya") { onPrompt("Menga foydali va kam xarajatli startup g‘oyasi taklif qil.") }
        QuickPrompt("Batareya") { onPrompt("Telefon batareyasi haqida ma’lumot ber.") }
    }
}

@Composable
private fun QuickPrompt(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = SurfaceRaised, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
        Text(label, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(Modifier.size(29.dp).clip(RoundedCornerShape(9.dp)).background(Violet.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = Pink, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = if (isUser) Color(0xFF34244D) else SurfaceDark,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (isUser) 18.dp else 5.dp, bottomEnd = if (isUser) 5.dp else 18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isUser) Violet.copy(alpha = 0.25f) else Border),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text(if (isUser) "Siz" else "Zuxrash AI", color = if (isUser) Violet else Pink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(message.text, color = TextPrimary, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(4.dp))
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt)), color = TextMuted, fontSize = 9.sp, modifier = Modifier.align(Alignment.End))
            }
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(29.dp).clip(RoundedCornerShape(9.dp)).background(Pink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Pink, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 37.dp, top = 4.dp, bottom = 8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Pink, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text("Zuxrash AI fikrlayapti…", color = TextMuted, fontSize = 12.sp)
    }
}

private fun loadHistory(prefs: android.content.SharedPreferences): List<ChatMessage> {
    return try {
        val array = JSONArray(prefs.getString("history", "[]") ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val role = obj.optString("role")
                val text = obj.optString("text")
                if ((role == "user" || role == "assistant") && text.isNotBlank()) {
                    add(ChatMessage(role, text, obj.optLong("createdAt", System.currentTimeMillis())))
                }
            }
        }.takeLast(80)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveHistory(prefs: android.content.SharedPreferences, messages: List<ChatMessage>) {
    val array = JSONArray()
    messages.takeLast(80).forEach { message ->
        array.put(JSONObject().put("role", message.role).put("text", message.text).put("createdAt", message.createdAt))
    }
    prefs.edit().putString("history", array.toString()).apply()
}

private fun runLocalCommand(context: Context, text: String): String? {
    val normalized = text.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
    val lower = normalized
        .replace(Regex("^(?:hey\\s+)?zuxrash(?:\\s+ai)?\\s*"), "")
        .replace(Regex("^(?:menga|manga|iltimos)\\s+"), "")
        .trim()
    if (lower.isBlank()) return null

    return try {
        val telegramQuery = parseTelegramSearchQuery(lower)
        when {
            telegramQuery != null -> {
                val searchIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("tg://search?query=" + Uri.encode(telegramQuery))
                ).setPackage("org.telegram.messenger").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (context.packageManager.getLaunchIntentForPackage("org.telegram.messenger") == null) {
                    "Telegram ilovasi telefonda o‘rnatilmagan."
                } else {
                    context.startActivity(searchIntent)
                    "Telegramda “$telegramQuery” qidiruvini ochdim."
                }
            }

            lower == "zuxrash" || lower == "hey zuxrash" || lower == "hey zuxrash ai" ->
                "Ha, eshitaman! Nima yordam kerak?"

            lower.contains("soat nech") || lower.contains("hozir soat") || lower.contains("vaqtni ayt") -> {
                val time = SimpleDateFormat("HH:mm", Locale("uz", "UZ")).format(Date())
                "Hozir soat $time."
            }

            lower.contains("bugun sana") || lower == "sana nechchi" || lower == "bugungi sana" -> {
                val date = SimpleDateFormat("d MMMM yyyy", Locale("uz", "UZ")).format(Date())
                "Bugungi sana: $date."
            }

            lower.contains("batareya necha") || lower.contains("batareya daraja") ||
                lower == "batareya" || lower == "zaryad qancha" -> {
                val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (level in 0..100) "Telefon batareyasi: $level%."
                else "Batareya darajasini o‘qib bo‘lmadi."
            }

            isAppOpenCommand(lower, listOf("telegram", "tg")) ->
                openInstalledApp(context, listOf("org.telegram.messenger"), "Telegram")

            isAppOpenCommand(lower, listOf("youtube", "yutub", "youtub")) ->
                openInstalledApp(context, listOf("com.google.android.youtube"), "YouTube")

            isAppOpenCommand(lower, listOf("instagram", "insta")) ->
                openInstalledApp(context, listOf("com.instagram.android"), "Instagram")

            isAppOpenCommand(lower, listOf("play market", "playstore", "play store")) ->
                openInstalledApp(context, listOf("com.android.vending"), "Play Market")

            isAppOpenCommand(lower, listOf("chrome", "brauzer", "browser")) ->
                openInstalledApp(
                    context,
                    listOf("com.android.chrome", "com.sec.android.app.sbrowser", "com.microsoft.emmx", "org.mozilla.firefox"),
                    "Brauzer"
                )

            lower == "telefon sozlamalari" || lower == "sozlamalarni och" ||
                lower == "sozlamalarni ochib ber" || lower == "sozlamalar" ||
                lower == "telefon sozlamasini och" -> {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Telefon sozlamalarini ochdim."
            }

            lower.contains("wifi sozlama") || lower.contains("wi fi sozlama") ||
                lower == "wifi ni sozla" || lower == "wifi ni och" -> {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Wi-Fi sozlamalarini ochdim."
            }

            lower.contains("bluetooth sozlama") || lower == "bluetoothni och" ||
                lower == "bluetooth sozlamasini och" -> {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Bluetooth sozlamalarini ochdim."
            }

            lower.contains("joylashuv sozlama") || lower.contains("gps sozlama") ||
                lower == "joylashuvni sozla" -> {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Joylashuv sozlamalarini ochdim."
            }

            lower.contains("ekran sozlama") || lower.contains("displey sozlama") -> {
                context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Ekran sozlamalarini ochdim."
            }

            lower.contains("ovoz sozlama") || lower.contains("tovush sozlama") -> {
                context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Ovoz sozlamalarini ochdim."
            }

            lower.contains("ilovalar sozlama") || lower.contains("ilovalarni boshqarish") ||
                lower.contains("ornatilgan ilova") || lower.contains("o‘rnatilgan ilova") -> {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Ilovalar sozlamalarini ochdim."
            }

            lower.contains("batareya sozlama") || lower.contains("batareyani tejash") -> {
                context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Batareyani tejash sozlamalarini ochdim."
            }

            lower.contains("kamerani och") || lower == "kamera och" || lower.contains("kamerani ishga tushir") -> {
                context.startActivity(Intent("android.media.action.IMAGE_CAPTURE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Kamerani ochdim."
            }

            lower.startsWith("brauzerda qidir ") ||
                lower.startsWith("internetdan qidir ") ||
                lower.startsWith("google da qidir ") ||
                lower.startsWith("qidir ") -> {
                val search = when {
                    lower.startsWith("brauzerda qidir ") -> normalized.substringAfter("brauzerda qidir ", "").trim()
                    lower.startsWith("internetdan qidir ") -> normalized.substringAfter("internetdan qidir ", "").trim()
                    lower.startsWith("google da qidir ") -> normalized.substringAfter("google da qidir ", "").trim()
                    else -> normalized.substringAfter("qidir ", "").trim()
                }
                if (search.isBlank()) return null
                val encoded = java.net.URLEncoder.encode(search, "UTF-8")
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "Brauzerda qidiruvni ochdim: $search"
            }

            else -> null
        }
    } catch (_: Exception) {
        "Buyruqni tanidim, lekin uni ochib bo‘lmadi. Ilova o‘rnatilganini va ruxsatlarni tekshir."
    }
}

private fun isAppOpenCommand(command: String, aliases: List<String>): Boolean {
    val words = command.split(Regex("\\s+")).filter { it.isNotBlank() }
    val appMentioned = aliases.any { alias ->
        if (alias.contains(" ")) {
            command.contains(alias)
        } else {
            words.any { word -> word == alias || word.startsWith(alias) }
        }
    }
    val wantsToOpen = words.any { it.startsWith("och") } ||
        command.contains("ishga tushir") ||
        command.contains("ishga tushur") ||
        command.contains("ilovaga kir") ||
        words.any { it == "kir" || it == "kiring" || it == "kirib" }
    return appMentioned && wantsToOpen
}

private fun parseTelegramSearchQuery(command: String): String? {
    val prefixes = listOf("telegram ichidan ", "telegramdan ", "telegramda ", "tgdan ", "tg da ")
    val prefix = prefixes.firstOrNull { command.startsWith(it) } ?: return null
    var query = command.removePrefix(prefix).trim()
    val endings = listOf(
        " qidirib topib ber", " qidirib top", " qidirib ber",
        " qidirib", " qidir", " topib ber", " ni qidir", " ni top", " top"
    )
    val ending = endings.firstOrNull { query.endsWith(it) } ?: return null
    query = query.removeSuffix(ending).trim()
    if (query.endsWith("ni") && query.length > 3) query = query.dropLast(2).trim()
    return query.takeIf { it.isNotBlank() && it.length <= 100 }
}

private fun openInstalledApp(context: Context, packageNames: List<String>, appName: String): String {
    for (packageName in packageNames) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return "$appName ilovasini ochdim."
        }
    }
    return "$appName ilovasi telefonda topilmadi yoki o‘rnatilmagan."
}
