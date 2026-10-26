package com.bekiuz.zuxrash

data class ChatMessage(
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
