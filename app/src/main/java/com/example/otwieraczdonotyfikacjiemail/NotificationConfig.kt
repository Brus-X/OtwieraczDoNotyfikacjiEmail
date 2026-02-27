package com.example.otwieraczdonotyfikacjiemail

data class NotificationConfig(
    val name: String,
    val startUrl: String?,
    val messageUrl: String,
    val emailSender: String,
    val subjectKeyword: String?
)