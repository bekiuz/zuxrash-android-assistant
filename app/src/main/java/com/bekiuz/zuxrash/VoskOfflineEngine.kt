package com.bekiuz.zuxrash

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * On-device Uzbek speech recognition.
 *
 * The model is downloaded once from the official Vosk model host, kept in the
 * app's private files directory, and then used without an internet connection.
 */
internal class VoskOfflineEngine(
    context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onPartialResult(text: String)
        fun onResult(text: String)
        fun onError(message: String)
        fun onTimeout()
    }

    private val appContext = context.applicationContext
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    suspend fun prepare(onProgress: (Int) -> Unit) {
        if (model != null) return

        val modelDirectory = withContext(Dispatchers.IO) {
            ensureModelDirectory(onProgress)
        }
        val loadedModel = withContext(Dispatchers.IO) {
            Model(modelDirectory.absolutePath)
        }
        model = loadedModel
    }

    fun start() {
        if (speechService != null) return
        val loadedModel = model ?: throw IllegalStateException("O‘zbekcha ovoz modeli hali tayyor emas.")

        val activeRecognizer = Recognizer(loadedModel, SAMPLE_RATE.toFloat())
        val activeService = SpeechService(activeRecognizer, SAMPLE_RATE.toFloat())
        recognizer = activeRecognizer
        speechService = activeService

        try {
            activeService.startListening(recognitionListener)
        } catch (error: Exception) {
            speechService = null
            recognizer = null
            try { activeService.stop() } catch (_: Exception) { }
            try { activeService.shutdown() } catch (_: Exception) { }
            try { activeRecognizer.close() } catch (_: Exception) { }
            throw error
        }
    }

    fun stop() {
        val activeService = speechService
        speechService = null
        try { activeService?.stop() } catch (_: Exception) { }
        try { activeService?.shutdown() } catch (_: Exception) { }

        val activeRecognizer = recognizer
        recognizer = null
        try { activeRecognizer?.close() } catch (_: Exception) { }
    }

    fun close() {
        stop()
        val activeModel = model
        model = null
        try { activeModel?.close() } catch (_: Exception) { }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            val text = readText(hypothesis, "partial")
            if (text.isNotBlank()) callbacks.onPartialResult(text)
        }

        override fun onResult(hypothesis: String) {
            val text = readText(hypothesis, "text")
            if (text.isNotBlank()) callbacks.onResult(text)
        }

        override fun onFinalResult(hypothesis: String) {
            // Final results are emitted when the stream is stopped; the next
            // listening session starts with a fresh recognizer.
        }

        override fun onError(error: Exception) {
            callbacks.onError(error.message ?: "Vosk nutqni taniy olmadi.")
        }

        override fun onTimeout() {
            callbacks.onTimeout()
        }
    }

    private fun readText(json: String, key: String): String {
        return try {
            JSONObject(json).optString(key).trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun ensureModelDirectory(onProgress: (Int) -> Unit): File {
        val modelDirectory = File(appContext.filesDir, MODEL_DIRECTORY)
        val modelMarker = File(modelDirectory, "am/final.mdl")
        if (modelMarker.isFile && modelMarker.length() > 0L) return modelDirectory

        val archive = File(appContext.cacheDir, "$MODEL_DIRECTORY.zip.part")
        val temporaryDirectory = File(appContext.filesDir, "$MODEL_DIRECTORY.tmp")
        archive.delete()
        temporaryDirectory.deleteRecursively()
        temporaryDirectory.mkdirs()

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 45_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ZuxrashAI-Android")
            }
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Ovoz modeli serveridan HTTP $responseCode javob keldi.")
            }

            val expectedBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(archive).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        if (expectedBytes > 0L) {
                            onProgress(((downloadedBytes * 100L) / expectedBytes).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }

            if (!archive.isFile || archive.length() < 1_000_000L) {
                throw IOException("Ovoz modeli fayli to‘liq yuklanmadi.")
            }

            extractArchive(archive, temporaryDirectory)
            val extractedMarker = File(temporaryDirectory, "am/final.mdl")
            if (!extractedMarker.isFile || extractedMarker.length() < 1_000L) {
                throw IOException("Yuklangan o‘zbekcha ovoz modeli yaroqsiz.")
            }

            modelDirectory.deleteRecursively()
            if (!temporaryDirectory.renameTo(modelDirectory)) {
                copyDirectory(temporaryDirectory, modelDirectory)
                temporaryDirectory.deleteRecursively()
            }
            if (!File(modelDirectory, "am/final.mdl").isFile) {
                throw IOException("Ovoz modelini telefonga saqlab bo‘lmadi.")
            }
            return modelDirectory
        } finally {
            connection?.disconnect()
            archive.delete()
            temporaryDirectory.deleteRecursively()
        }
    }

    private fun extractArchive(archive: File, destination: File) {
        val canonicalRoot = destination.canonicalFile
        var extractedBytes = 0L
        val buffer = ByteArray(64 * 1024)

        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val entryName = entry.name.replace('\\', '/')
                val relativePath = when {
                    entryName == MODEL_DIRECTORY -> ""
                    entryName == "$MODEL_DIRECTORY/" -> ""
                    entryName.startsWith("$MODEL_DIRECTORY/") -> entryName.removePrefix("$MODEL_DIRECTORY/")
                    else -> entryName
                }

                if (relativePath.isNotBlank()) {
                    val outputFile = File(destination, relativePath).canonicalFile
                    val isInsideRoot = outputFile.path.startsWith(canonicalRoot.path + File.separator)
                    if (!isInsideRoot) throw IOException("Ovoz modeli arxivida noto‘g‘ri fayl yo‘li bor.")

                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { output ->
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                extractedBytes += count
                                if (extractedBytes > MAX_EXTRACTED_BYTES) {
                                    throw IOException("Ovoz modeli kutilgan hajmdan katta.")
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun copyDirectory(source: File, destination: File) {
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles()?.forEach { child ->
                copyDirectory(child, File(destination, child.name))
            }
        } else {
            destination.parentFile?.mkdirs()
            source.inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val MODEL_DIRECTORY = "vosk-model-small-uz-0.22"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-uz-0.22.zip"
        const val MAX_EXTRACTED_BYTES = 180L * 1024L * 1024L
    }
}
