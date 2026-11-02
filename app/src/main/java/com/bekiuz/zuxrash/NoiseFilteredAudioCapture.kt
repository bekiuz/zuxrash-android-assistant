package com.bekiuz.zuxrash

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.ParcelFileDescriptor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Captures mono PCM audio with Android's voice-recognition audio source,
 * attaches available hardware noise/echo/gain effects, and streams it to
 * SpeechRecognizer through EXTRA_AUDIO_SOURCE (Android 13+).
 *
 * If the device or its recognition provider does not support injected audio,
 * callers should stop this capture and fall back to the recognizer's own mic.
 */
internal class NoiseFilteredAudioCapture private constructor(
    val readDescriptor: ParcelFileDescriptor,
    private val writeDescriptor: ParcelFileDescriptor,
    private val recorder: AudioRecord,
    private val noiseSuppressor: NoiseSuppressor?,
    private val echoCanceler: AcousticEchoCanceler?,
    private val gainControl: AutomaticGainControl?
) {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_COUNT = 1
        const val MAX_CAPTURE_MS = 12_000L
        private const val FRAME_BYTES = 3_200 // 100 ms of 16 kHz mono PCM16
        private const val START_TIMEOUT_MS = 8_000L
        private const val END_SILENCE_MS = 1_100L

        fun create(): NoiseFilteredAudioCapture? {
            var recorder: AudioRecord? = null
            var readSide: ParcelFileDescriptor? = null
            var writeSide: ParcelFileDescriptor? = null
            var ns: NoiseSuppressor? = null
            var aec: AcousticEchoCanceler? = null
            var agc: AutomaticGainControl? = null

            return try {
                val minSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minSize <= 0) return null

                val activeRecorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minSize * 2, FRAME_BYTES * 4)
                )
                recorder = activeRecorder
                if (activeRecorder.state != AudioRecord.STATE_INITIALIZED) {
                    activeRecorder.release()
                    return null
                }

                val sessionId = activeRecorder.audioSessionId
                if (NoiseSuppressor.isAvailable()) {
                    ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                }
                if (AutomaticGainControl.isAvailable()) {
                    agc = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
                }

                val pipe = ParcelFileDescriptor.createPipe()
                val activeReadSide = pipe[0]
                val activeWriteSide = pipe[1]
                readSide = activeReadSide
                writeSide = activeWriteSide

                NoiseFilteredAudioCapture(
                    activeReadSide,
                    activeWriteSide,
                    activeRecorder,
                    ns,
                    aec,
                    agc
                )
            } catch (_: Exception) {
                try { ns?.release() } catch (_: Exception) { }
                try { aec?.release() } catch (_: Exception) { }
                try { agc?.release() } catch (_: Exception) { }
                try { recorder?.release() } catch (_: Exception) { }
                try { readSide?.close() } catch (_: Exception) { }
                try { writeSide?.close() } catch (_: Exception) { }
                null
            }
        }
    }

    private val running = AtomicBoolean(false)
    private val cleaned = AtomicBoolean(false)
    @Volatile private var captureThread: Thread? = null

    val filterStatus: String
        get() = buildList {
            if (noiseSuppressor?.enabled == true) add("shovqin filtri")
            if (echoCanceler?.enabled == true) add("echo filtri")
            if (gainControl?.enabled == true) add("ovoz kuchaytirish")
        }.takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: "telefonning ovoz filtri mavjud emas, nutq faolligi filtri ishlaydi"

    fun start() {
        check(running.compareAndSet(false, true)) { "Audio capture already started" }
        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Mikrofon yozishni boshlamadi"
            }
            captureThread = Thread(::pumpAudio, "Zuxrash-NoiseFilteredAudio").apply {
                isDaemon = true
                start()
            }
        } catch (error: Exception) {
            running.set(false)
            cleanup()
            throw error
        }
    }

    fun stop() {
        running.set(false)
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        } catch (_: Exception) { }

        val thread = captureThread
        if (thread != null && thread !== Thread.currentThread()) {
            try { thread.join(500) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        cleanup()
    }

    private fun pumpAudio() {
        val output = try {
            ParcelFileDescriptor.AutoCloseOutputStream(writeDescriptor)
        } catch (_: Exception) {
            running.set(false)
            cleanup()
            return
        }

        val buffer = ByteArray(FRAME_BYTES)
        val startedAt = System.currentTimeMillis()
        var speechStarted = false
        var lastSpeechAt = startedAt
        var noiseFloor = 120.0
        var speechFrames = 0

        try {
            while (running.get()) {
                val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) break

                val now = System.currentTimeMillis()
                val rms = calculateRms(buffer, count)
                val threshold = max(420.0, (noiseFloor * 2.0).coerceAtMost(1_400.0))

                if (!speechStarted) {
                    // Learn the room's baseline only before speech is detected.
                    noiseFloor = (noiseFloor * 0.94 + rms * 0.06).coerceIn(35.0, 700.0)
                    if (rms >= threshold) {
                        speechFrames++
                        if (speechFrames >= 2) {
                            speechStarted = true
                            lastSpeechAt = now
                        }
                    } else {
                        speechFrames = 0
                    }
                } else if (rms >= threshold * 0.62) {
                    lastSpeechAt = now
                }

                // Send processed PCM frames in real time to the recognition service.
                output.write(buffer, 0, count)

                if (!speechStarted && now - startedAt >= START_TIMEOUT_MS) break
                if (speechStarted && now - lastSpeechAt >= END_SILENCE_MS) break
                if (now - startedAt >= MAX_CAPTURE_MS) break
            }
        } catch (_: Exception) {
            // The recognizer may close the pipe first; stop() owns final cleanup.
        } finally {
            running.set(false)
            try { output.close() } catch (_: Exception) { }
            cleanup()
        }
    }

    private fun calculateRms(bytes: ByteArray, length: Int): Double {
        val sampleCount = length / 2
        if (sampleCount <= 0) return 0.0
        var sumSquares = 0.0
        var index = 0
        while (index + 1 < length) {
            val sample = ((bytes[index + 1].toInt() shl 8) or
                (bytes[index].toInt() and 0xFF)).toShort().toInt()
            sumSquares += sample.toDouble() * sample.toDouble()
            index += 2
        }
        return sqrt(sumSquares / sampleCount)
    }

    private fun cleanup() {
        if (!cleaned.compareAndSet(false, true)) return
        try { noiseSuppressor?.release() } catch (_: Exception) { }
        try { echoCanceler?.release() } catch (_: Exception) { }
        try { gainControl?.release() } catch (_: Exception) { }
        try { recorder.release() } catch (_: Exception) { }
        try { readDescriptor.close() } catch (_: Exception) { }
        try { writeDescriptor.close() } catch (_: Exception) { }
    }
}
