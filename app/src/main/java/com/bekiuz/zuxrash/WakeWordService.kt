package com.bekiuz.zuxrash

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * User-enabled, foreground microphone service for the "Hey Zuxrash" wake phrase.
 * It deliberately shows an ongoing notification with an explicit Stop action.
 * Android's speech recognition provider performs speech recognition; this is not
 * an offline hotword engine and may require the device's speech service/internet.
 */
class WakeWordService : Service() {
    companion object {
        const val ACTION_START = "com.bekiuz.zuxrash.action.WAKE_START"
        const val ACTION_PAUSE = "com.bekiuz.zuxrash.action.WAKE_PAUSE"
        const val ACTION_RESUME = "com.bekiuz.zuxrash.action.WAKE_RESUME"
        const val ACTION_STOP = "com.bekiuz.zuxrash.action.WAKE_STOP"

        private const val CHANNEL_ID = "zuxrash_wake_word"
        private const val NOTIFICATION_ID = 8152
        private const val PREFS_NAME = "zuxrash_assistant"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: android.content.SharedPreferences
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var serviceActive = false
    private var paused = false
    private var recognizing = false
    private var speaking = false
    private var ttsReady = false
    private var awaitingCommandUntil = 0L

    private val listenRunnable = Runnable { startRecognition() }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(recognitionListener)
            }
        }

        tts = TextToSpeech(this) { status ->
            handler.post {
                val engine = tts ?: return@post
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    val uz = Locale.forLanguageTag("uz-UZ")
                    val ru = Locale.forLanguageTag("ru-RU")
                    engine.language = when {
                        engine.isLanguageAvailable(uz) >= TextToSpeech.LANG_AVAILABLE -> uz
                        engine.isLanguageAvailable(ru) >= TextToSpeech.LANG_AVAILABLE -> ru
                        else -> Locale.US
                    }
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { }
            override fun onDone(utteranceId: String?) {
                handler.post {
                    speaking = false
                    updateNotification("Fon tinglash faol · “Hey Zuxrash” deb ayting")
                    scheduleRecognition(350)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post {
                    speaking = false
                    scheduleRecognition(900)
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceActive = false
            paused = false
            prefs.edit().putBoolean("wake_word_enabled", false).apply()
            handler.removeCallbacks(listenRunnable)
            try { recognizer?.cancel() } catch (_: Exception) { }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("wake_word_enabled", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == null && !prefs.getBoolean("wake_word_enabled", false)) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification("Fon tinglash yoqilmoqda…"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification("Fon tinglash yoqilmoqda…"))
            }
        } catch (_: Exception) {
            prefs.edit().putBoolean("wake_word_enabled", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }

        serviceActive = true
        when (intent?.action) {
            ACTION_PAUSE -> {
                paused = true
                handler.removeCallbacks(listenRunnable)
                try { recognizer?.cancel() } catch (_: Exception) { }
                recognizing = false
                updateNotification("Ilova ishlayapti · fon tinglash vaqtincha to‘xtadi")
            }
            ACTION_RESUME -> {
                paused = false
                updateNotification("Fon tinglash faol · “Hey Zuxrash” deb ayting")
                scheduleRecognition(700)
            }
            else -> {
                paused = false
                prefs.edit().putBoolean("wake_word_enabled", true).apply()
                if (recognizer == null) {
                    speakAndListen("Bu telefonda nutqni tanish xizmati topilmadi.")
                    updateNotification("Nutqni tanish xizmati topilmadi")
                } else {
                    updateNotification("Fon tinglash faol · “Hey Zuxrash” deb ayting")
                    scheduleRecognition(700)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceActive = false
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) { }
        recognizer = null
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) { }
        tts = null
        scope.cancel()
        if (::prefs.isInitialized) prefs.edit().putBoolean("wake_word_enabled", false).apply()
        super.onDestroy()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            recognizing = true
            if (!paused) updateNotification("Mikrofon faol · “Hey Zuxrash” deb ayting")
        }

        override fun onBeginningOfSpeech() {
            recognizing = true
        }

        override fun onRmsChanged(rmsdB: Float) { }
        override fun onBufferReceived(buffer: ByteArray?) { }

        override fun onEndOfSpeech() {
            recognizing = false
            updateNotification("Ovoz qayta ishlanyapti…")
        }

        override fun onError(error: Int) {
            recognizing = false
            if (!serviceActive || paused) return
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                prefs.edit().putBoolean("wake_word_enabled", false).apply()
                stopSelf()
                return
            }
            val delay = when (error) {
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 1800L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1400L
                else -> 700L
            }
            scheduleRecognition(delay)
        }

        override fun onResults(results: Bundle?) {
            recognizing = false
            if (!serviceActive || paused) return
            val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (transcript.isBlank()) {
                scheduleRecognition(500)
                return
            }
            handleTranscript(transcript)
        }

        override fun onPartialResults(partialResults: Bundle?) { }
        override fun onEvent(eventType: Int, params: Bundle?) { }
    }

    private fun startRecognition() {
        if (!serviceActive || paused || speaking || recognizing) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("wake_word_enabled", false).apply()
            stopSelf()
            return
        }
        val engine = recognizer ?: run {
            updateNotification("Nutqni tanish xizmati mavjud emas")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            recognizing = true
            engine.startListening(intent)
        } catch (_: Exception) {
            recognizing = false
            scheduleRecognition(1500)
        }
    }

    private fun scheduleRecognition(delayMs: Long) {
        if (!serviceActive || paused || speaking) return
        handler.removeCallbacks(listenRunnable)
        handler.postDelayed(listenRunnable, delayMs.coerceAtLeast(300L))
    }

    private fun handleTranscript(transcript: String) {
        val normalized = transcript.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
        val wakePattern = Regex("\\b(?:hey\\s+)?zux\\s*rash(?:\\s+ai)?\\b")
        val wakeMatch = wakePattern.find(normalized)

        if (wakeMatch != null) {
            val command = normalized.substring(wakeMatch.range.last + 1).trim()
            if (command.isBlank()) {
                awaitingCommandUntil = System.currentTimeMillis() + 12_000L
                updateNotification("Uyg‘ondim · buyruqni ayting")
                speakAndListen("Ha, eshitaman. Buyruqni ayting.")
            } else {
                awaitingCommandUntil = 0L
                executeVoiceCommand(command)
            }
            return
        }

        if (System.currentTimeMillis() <= awaitingCommandUntil) {
            awaitingCommandUntil = 0L
            executeVoiceCommand(normalized)
            return
        }

        scheduleRecognition(450)
    }

    private data class LocalAction(
        val speech: String,
        val actionLabel: String? = null,
        val intent: Intent? = null
    )

    private fun executeVoiceCommand(command: String) {
        val cleanCommand = command.trim()
            .replace(Regex("^(?:menga|manga|iltimos)\\s+"), "")
            .trim()
        val localAction = resolveLocalCommand(cleanCommand)
        if (localAction != null) {
            if (localAction.intent != null && localAction.actionLabel != null) {
                // Keep a notification action as a reliable fallback. Android may block
                // an activity launch while another app is in the foreground.
                updateNotification(localAction.speech, localAction.intent, localAction.actionLabel)
                try {
                    startActivity(localAction.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    // The persistent notification action remains available.
                }
            } else {
                updateNotification(localAction.speech)
            }
            speakAndListen(localAction.speech)
            return
        }

        val endpoint = prefs.getString("api_url", BuildConfig.API_BASE_URL) ?: BuildConfig.API_BASE_URL
        val clientId = prefs.getString("client_id", null)
            ?: UUID.randomUUID().toString().replace("-", "").also {
                prefs.edit().putString("client_id", it).apply()
            }
        updateNotification("AI javobi tayyorlanyapti…")
        scope.launch {
            try {
                val reply = AiClient.reply(endpoint, listOf(ChatMessage(role = "user", text = cleanCommand)), clientId)
                updateNotification(reply.take(120).ifBlank { "AI javobi tayyor" })
                speakAndListen(reply.take(1400))
            } catch (error: Exception) {
                val raw = error.message.orEmpty()
                val message = if (raw.contains("HTTP 404")) {
                    "AI server topilmadi. Cloudflare'da zuxrash-ai Worker va /api/chat manzilini tekshirish kerak."
                } else {
                    raw.take(220).ifBlank { "AI serveriga ulanib bo‘lmadi." }
                }
                updateNotification(message)
                speakAndListen(message)
            }
        }
    }

    private fun resolveLocalCommand(command: String): LocalAction? {
        val lower = command.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
        return try {
            when {
                parseTelegramSearchQuery(lower) != null -> {
                    val query = parseTelegramSearchQuery(lower).orEmpty()
                    val searchIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("tg://search?query=" + Uri.encode(query))
                    ).setPackage("org.telegram.messenger").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (packageManager.resolveActivity(searchIntent, 0) == null) {
                        LocalAction("Telegram ilovasi topilmadi yoki qidiruv havolasini ochib bo‘lmadi.")
                    } else {
                        LocalAction(
                            "Telegramda “$query” qidiruvini ochyapman. Ochilmasa, bildirishnomadagi qidiruv tugmasini bosing.",
                            "Telegramda qidirish",
                            searchIntent
                        )
                    }
                }
                lower.contains("soat nech") || lower.contains("hozir soat") || lower.contains("vaqtni ayt") -> {
                    val time = SimpleDateFormat("HH:mm", Locale("uz", "UZ")).format(Date())
                    LocalAction("Hozir soat $time.")
                }
                lower.contains("bugun sana") || lower == "bugungi sana" || lower == "sana nechchi" -> {
                    val date = SimpleDateFormat("d MMMM yyyy", Locale("uz", "UZ")).format(Date())
                    LocalAction("Bugungi sana: $date.")
                }
                lower == "batareya" || lower.contains("batareya necha") ||
                    lower.contains("batareya daraja") || lower.contains("zaryad qancha") -> {
                    val manager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    LocalAction(if (level in 0..100) "Telefon batareyasi $level foiz." else "Batareya darajasini o‘qib bo‘lmadi.")
                }
                isAppOpenCommand(lower, listOf("telegram", "tg")) ->
                    appAction("org.telegram.messenger", "Telegram")
                isAppOpenCommand(lower, listOf("youtube", "yutub", "youtub")) ->
                    appAction("com.google.android.youtube", "YouTube")
                isAppOpenCommand(lower, listOf("instagram", "insta")) ->
                    appAction("com.instagram.android", "Instagram")
                isAppOpenCommand(lower, listOf("play market", "playstore", "play store")) ->
                    appAction("com.android.vending", "Play Market")
                isAppOpenCommand(lower, listOf("chrome", "brauzer", "browser")) ->
                    appAction("com.android.chrome", "Chrome")
                lower.contains("wifi") && (lower.contains("sozlama") || lower.contains("och")) ->
                    settingsAction(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi sozlamalari")
                lower.contains("bluetooth") && (lower.contains("sozlama") || lower.contains("och")) ->
                    settingsAction(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth sozlamalari")
                lower.contains("joylashuv") && (lower.contains("sozlama") || lower.contains("och")) ->
                    settingsAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Joylashuv sozlamalari")
                lower.contains("ekran") && (lower.contains("sozlama") || lower.contains("och")) ->
                    settingsAction(Settings.ACTION_DISPLAY_SETTINGS, "Ekran sozlamalari")
                lower.contains("ovoz sozlam") || lower.contains("tovush sozlam") ->
                    settingsAction(Settings.ACTION_SOUND_SETTINGS, "Ovoz sozlamalari")
                lower.contains("batareya sozlam") || lower.contains("batareyani tejash") ->
                    settingsAction(Settings.ACTION_BATTERY_SAVER_SETTINGS, "Batareya sozlamalari")
                lower == "telefon sozlamalari" || lower == "sozlamalarni och" || lower == "sozlamalar" ->
                    settingsAction(Settings.ACTION_SETTINGS, "Telefon sozlamalari")
                lower.startsWith("brauzerda qidir ") || lower.startsWith("internetdan qidir ") || lower.startsWith("qidir ") -> {
                    val query = when {
                        lower.startsWith("brauzerda qidir ") -> command.substringAfter("brauzerda qidir ").trim()
                        lower.startsWith("internetdan qidir ") -> command.substringAfter("internetdan qidir ").trim()
                        else -> command.substringAfter("qidir ").trim()
                    }
                    if (query.isBlank()) null
                    else {
                        val encoded = URLEncoder.encode(query, "UTF-8")
                        val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + encoded))
                        LocalAction("Qidiruvni ochish uchun bildirishnomadagi tugmani bosing.", "Qidirish", searchIntent)
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            LocalAction("Bu buyruqni bajara olmadim. Sozlamalarni tekshirib ko‘ring.")
        }
    }

    private fun parseTelegramSearchQuery(command: String): String? {
        val prefixes = listOf("telegramdan ", "telegramda ", "telegram ichidan ", "tgdan ")
        val prefix = prefixes.firstOrNull { command.startsWith(it) } ?: return null
        var query = command.removePrefix(prefix).trim()
        val endings = listOf(" qidirib top", " qidirib", " qidir", " top", " ni top", " ni qidir")
        val ending = endings.firstOrNull { query.endsWith(it) } ?: return null
        query = query.removeSuffix(ending).trim()
        // Uzbek direct-object ending: "Iqboljonni top" -> "Iqboljon".
        if (query.endsWith("ni") && query.length > 3) query = query.dropLast(2).trim()
        return query.takeIf { it.isNotBlank() && it.length <= 100 }
    }

    private fun appAction(packageName: String, appName: String): LocalAction {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent == null) {
            LocalAction(appName + " ilovasi telefonda topilmadi.")
        } else {
            LocalAction(
                "$appName ilovasini ochishga urinyapman. Android ruxsat bermasa, bildirishnomadagi tugmani bosing.",
                "$appName ni ochish",
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun settingsAction(action: String, label: String): LocalAction {
        val settingsIntent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return LocalAction(label + "ni ochish uchun bildirishnomadagi tugmani bosing.", "Ochish", settingsIntent)
    }

    private fun speakAndListen(text: String) {
        val spoken = text.trim().take(1400)
        if (spoken.isBlank()) {
            scheduleRecognition(500)
            return
        }
        val engine = tts
        if (!ttsReady || engine == null) {
            scheduleRecognition(1400)
            return
        }
        speaking = true
        val result = engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "zuxrash-bg-" + System.currentTimeMillis())
        if (result != TextToSpeech.SUCCESS) {
            speaking = false
            scheduleRecognition(900)
        }
    }

    private fun updateNotification(
        text: String,
        actionIntent: Intent? = null,
        actionLabel: String? = null
    ) {
        if (!serviceActive) return
        try {
            val notification = buildNotification(text, actionIntent, actionLabel)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) { }
    }

    private fun buildNotification(
        text: String,
        actionIntent: Intent? = null,
        actionLabel: String? = null
    ): Notification {
        val openApp = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openPending = PendingIntent.getActivity(
            this, 8100, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, WakeWordService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 8101, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Zuxrash AI · Fon rejimi")
            .setContentText(text.take(180))
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
        if (actionIntent != null && !actionLabel.isNullOrBlank()) {
            val pendingAction = PendingIntent.getActivity(
                this,
                8102,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_view),
                    actionLabel,
                    pendingAction
                ).build()
            )
        }
        builder.addAction(
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                "To‘xtatish",
                stopPending
            ).build()
        )
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zuxrash AI fon tinglashi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "“Hey Zuxrash” uyg‘otuvchi so‘zi uchun ko‘rinadigan mikrofon xizmati"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
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
