package com.bekiuz.zuxrash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object AiClient {
    suspend fun reply(
        baseUrl: String,
        history: List<ChatMessage>,
        clientId: String
    ): String = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "AI server manzili https:// bilan boshlanishi kerak." }
        val endpoint = when {
            base.endsWith("/api/chat") -> base
            base.endsWith("/api") -> "$base/chat"
            else -> "$base/api/chat"
        }

        val messagesJson = JSONArray()
        history.takeLast(12).forEach { message ->
            if (message.text.isNotBlank()) {
                messagesJson.put(
                    JSONObject()
                        .put("role", if (message.role == "user") "user" else "assistant")
                        .put("content", message.text.take(12_000))
                )
            }
        }
        if (messagesJson.length() == 0 || messagesJson.getJSONObject(messagesJson.length() - 1).optString("role") != "user") {
            throw IOException("Yuborish uchun foydalanuvchi xabari topilmadi.")
        }

        val body = JSONObject()
            .put("mode", "general")
            .put("messages", messagesJson)
            .toString()

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Zuxrash-Client", clientId)
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(responseText)
            } catch (_: Exception) {
                if (status == 404) {
                    throw IOException(
                        "HTTP 404: $endpoint manzili topilmadi. Cloudflare'da zuxrash-ai Worker deploy qilinganini va /api/chat manzili ishlashini tekshirish kerak."
                    )
                }
                throw IOException("AI serveridan tushunarsiz javob keldi (HTTP $status).")
            }
            if (status !in 200..299 || !json.optBoolean("ok", status in 200..299)) {
                throw IOException(json.optString("error").ifBlank { "AI server xatosi: HTTP $status" })
            }
            json.optString("reply").trim().takeIf { it.isNotEmpty() }
                ?: throw IOException("AI bo‘sh javob qaytardi. Qayta urinib ko‘ring.")
        } finally {
            connection.disconnect()
        }
    }
}
