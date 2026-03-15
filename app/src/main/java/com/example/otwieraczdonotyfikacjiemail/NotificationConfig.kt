package com.example.otwieraczdonotyfikacjiemail

import java.util.UUID

data class NotificationConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startUrl: String?,
    val messageUrl: String,
    val emailSender: String,
    val subjectKeyword: String?
)