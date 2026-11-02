package com.bekiuz.zuxrash

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import java.util.Locale

internal data class DeviceVoiceAction(
    val message: String,
    val actionLabel: String? = null,
    val intent: Intent? = null
)

internal object DeviceVoiceActions {
    fun resolve(context: Context, rawCommand: String): DeviceVoiceAction? {
        val command = normalize(rawCommand)
            .replace(Regex("^(?:hey )?zuxrash(?: ai)? "), "")
            .trim()
        if (command.isBlank()) return null

        val isAlarm = listOf("budilnik", "alarm", "будильник").any { command.contains(it) }
        val isReminder = listOf("eslatma", "eslatib qoy", "напомни", "напоминание").any { command.contains(it) }
        if (isAlarm || isReminder) return createAlarmAction(command, isReminder)

        val isCall = listOf("qongiroq", "telefon qil", "qongiroq qil", "звони", "позвони", "call ").any { command.contains(it) }
        val isMessage = listOf("xabar yoz", "xabar yubor", "sms", "сообщение", "напиши ").any { command.contains(it) }
        val number = Regex("""\+?\d[\d\s()\-]{5,}\d""").find(rawCommand)?.value?.replace(Regex("[^0-9+]"), "")

        if (isCall) {
            if (!number.isNullOrBlank()) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return DeviceVoiceAction("Raqamni qo‘ng‘iroq oynasida ochdim. Qo‘ng‘iroq qilish uchun tasdiqlang.", "Qo‘ng‘iroq oynasini ochish", intent)
            }
            return contactSearchAction(command, "Qo‘ng‘iroq qilmoqchi bo‘lgan kontaktni tanlang.")
        }

        if (isMessage) {
            if (!number.isNullOrBlank()) {
                val body = rawCommand.substringAfter(number, "").trim()
                    .replace(Regex("^(?:ga|uchun|deb|xabar|sms|yoz|yubor)\\s*"), "")
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (body.isNotBlank()) intent.putExtra("sms_body", body)
                return DeviceVoiceAction("Xabarni tayyorladim. Yuborishdan oldin tekshirib, o‘zingiz tasdiqlang.", "Xabarni ko‘rish", intent)
            }
            return contactSearchAction(command, "Xabar yuboriladigan kontaktni tanlang.")
        }

        val isContactSearch = listOf("kontakt", "contact", "контакт").any { command.contains(it) }
        if (isContactSearch) return contactSearchAction(command, "Kontaktni tanlash oynasini ochyapman.")

        val searchPrefix = listOf("brauzerda qidir ", "internetdan qidir ", "google da qidir ", "qidir ", "найди в интернете ", "поищи ")
            .firstOrNull { command.startsWith(it) }
        if (searchPrefix != null) {
            val query = command.removePrefix(searchPrefix).trim()
            if (query.isNotBlank()) {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return DeviceVoiceAction("Internetda qidiruvni ochyapman: " + query, "Qidiruvni ochish", intent)
            }
        }

        val app = InstalledAppMatcher.find(context, command)
        if (app != null) {
            return DeviceVoiceAction(
                app.label + " ilovasini ochyapman.",
                app.label + " ni ochish",
                app.intent
            )
        }
        return null
    }

    private fun createAlarmAction(command: String, reminder: Boolean): DeviceVoiceAction {
        val time = Regex("""(?:soat|na|v|в|на)\s*(\d{1,2})(?:[:.](\d{2}))?""")
            .find(command)
            ?: Regex("""\b(\d{1,2})[:.](\d{2})\b""").find(command)
        if (time == null) {
            return DeviceVoiceAction(
                if (reminder) "Eslatma vaqtini ham ayting. Masalan: eslatma qo‘y, soat 19:30."
                else "Budilnik vaqtini ayting. Masalan: soat 7:30 ga budilnik qo‘y."
            )
        }

        val hour = time.groupValues[1].toIntOrNull() ?: return DeviceVoiceAction("Vaqtni tushunmadim.")
        val minute = time.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return DeviceVoiceAction("Vaqt noto‘g‘ri. Soatni qayta ayting.")

        val label = if (reminder) "Zuxrash eslatmasi" else "Zuxrash budilnigi"
        val message = Regex("""(?:eslatma|напомни)\s*(.*)""").find(command)?.groupValues?.getOrNull(1)
            ?.replace(Regex("""(?:soat|na|v|в|на)\s*\d{1,2}(?:[:.]\d{2})?"""), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: label
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, message)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return DeviceVoiceAction(
            "Soat " + hour + ":" + minute.toString().padStart(2, '0') +
                " uchun budilnikni tayyorladim. Saqlashni o‘zingiz tasdiqlang.",
            "Budilnikni sozlash",
            intent
        )
    }

    private fun contactSearchAction(command: String, message: String): DeviceVoiceAction {
        val name = command
            .replace(Regex("""\b(kontaktlar|kontakt|contact|contacts|контакт|контакты|top|qidir|qidirib|find|search|найди|поищи|qo‘ng‘iroq|qongiroq|qil|telefon|call|звони|позвони|hey|zuxrash|ai|menga|manga|iltimos)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .replace(Regex("""(ni|ning|ga|dan|da)$"""), "")
            .trim()
        val uri = if (name.isNotBlank()) {
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name))
        } else {
            ContactsContract.Contacts.CONTENT_URI
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return DeviceVoiceAction(message, "Kontaktlarni ochish", intent)
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace('‘', '\'')
        .replace('’', '\'')
        .replace(Regex("[^\\p{L}\\p{N}+]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal data class InstalledAppMatch(val label: String, val intent: Intent)

internal object InstalledAppMatcher {
    private val commandWords = setOf(
        "hey", "zuxrash", "zuxrashai", "ai", "och", "oching", "ochib", "ber", "bering",
        "ishga", "tushir", "tushuring", "ilova", "ilovani", "ilovasini", "app",
        "otkroy", "otkroyte", "otkrit", "zapusti", "zapustite", "приложение",
        "открой", "откройте", "запусти", "запустите", "запустить", "открыть",
        "open", "launch", "start", "please", "menga", "manga", "iltimos",
        "ni", "ga", "dan", "da", "на", "в", "мне", "пожалуйста"
    )

    fun find(context: Context, rawCommand: String): InstalledAppMatch? {
        val requested = extractTarget(rawCommand)
        if (requested.isBlank()) return null

        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = try {
            pm.queryIntentActivities(launcher, android.content.pm.PackageManager.MATCH_ALL)
        } catch (_: Exception) {
            emptyList()
        }

        val candidate = activities.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            val label = try {
                info.loadLabel(pm)?.toString().orEmpty()
            } catch (_: Exception) {
                ""
            }
            val normalizedLabel = normalize(label)
            val score = score(requested, normalizedLabel)
            if (score <= 0) null else Triple(score, label, packageName)
        }.maxByOrNull { it.first } ?: return null

        val launchIntent = pm.getLaunchIntentForPackage(candidate.third) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return InstalledAppMatch(candidate.second, launchIntent)
    }

    private fun extractTarget(command: String): String {
        val normalized = normalize(command)
        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        val cleaned = tokens.mapNotNull { raw ->
            if (raw in commandWords) return@mapNotNull null
            var token = raw
            for (suffix in listOf("ning", "dan", "ga", "ni", "da", "ka")) {
                if (token.length > suffix.length + 2 && token.endsWith(suffix)) {
                    token = token.dropLast(suffix.length)
                    break
                }
            }
            token.takeIf { it.length >= 2 && it !in commandWords }
        }
        return cleaned.joinToString(" ").trim()
    }

    private fun score(requested: String, label: String): Int {
        if (label.isBlank()) return 0
        if (requested == label) return 10000
        val wanted = requested.split(" ").filter { it.length >= 2 }
        val labelWords = label.split(" ").filter { it.isNotBlank() }
        if (wanted.isNotEmpty()) {
            val matched = wanted.count { word ->
                labelWords.any { appWord ->
                    appWord == word ||
                        (word.length >= 3 && appWord.startsWith(word)) ||
                        (appWord.length >= 3 && word.startsWith(appWord))
                }
            }
            if (matched == wanted.size) return 8000 + matched * 10 + label.length
        }
        if (label.contains(requested) && requested.length >= 3) return 6000 + requested.length
        if (requested.contains(label) && label.length >= 4) return 5000 + label.length
        return 0
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace('‘', '\'')
        .replace('’', '\'')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
