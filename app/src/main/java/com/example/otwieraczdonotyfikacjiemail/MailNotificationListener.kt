package com.example.otwieraczdonotyfikacjiemail

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MailNotificationListener : NotificationListenerService() {

    private val TAG = "MailListener"
    private var testReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        registerTestReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        testReceiver?.let { unregisterReceiver(it); testReceiver = null }
    }

    private fun registerTestReceiver() {
        val filter = IntentFilter("com.example.otwieracz.CHECK_NOTIFICATIONS")
        testReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val configId = intent?.getStringExtra("configId")
                checkActiveNotifications(configId)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(testReceiver, filter)
        }
    }

    private fun checkActiveNotifications(targetId: String?) {
        val notifications = try { activeNotifications } catch (e: Exception) {
            AppLogger.log(applicationContext, "ERROR", message = "Błąd pobierania powiadomień: ${e.message}")
            null
        } ?: return

        val configs = try { ConfigLoader.loadConfigs(applicationContext) } catch (e: Exception) { return }
        var found = false

        for (sbn in notifications) {
            if (sbn.packageName == "com.google.android.gm") {
                if (processNotification(sbn, configs, isTest = true, targetId = targetId)) found = true
            }
        }

        if (targetId != null) {
            val config = configs.find { it.id == targetId }
            val msg = if (found) "TEST SUKCES: Znaleziono powiadomienie." else "TEST PORAŻKA: Nie znaleziono pasującego maila na pasku."
            AppLogger.log(applicationContext, "TEST", targetId, config?.name, msg)
            
            val resultIntent = Intent("com.example.otwieracz.TEST_RESULT")
            resultIntent.putExtra("success", found)
            resultIntent.setPackage(packageName)
            sendBroadcast(resultIntent)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.google.android.gm") return
        val configs = try { ConfigLoader.loadConfigs(applicationContext) } catch (e: Exception) { return }
        processNotification(sbn, configs, isTest = false)
    }

    private fun processNotification(sbn: StatusBarNotification, configs: List<NotificationConfig>, isTest: Boolean, targetId: String? = null): Boolean {
        val extras = sbn.notification.extras ?: return false
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)

        var matched = false
        for (config in configs) {
            if (targetId != null && config.id != targetId) continue

            var match = checkMatch(config, title, text, subText)
            if (!match && lines != null) {
                for (line in lines) { if (checkMatch(config, "", line.toString(), "")) { match = true; break } }
            }

            if (match) {
                matched = true
                val fullContent = "Nadawca: $title\nTemat: $text\nAdresat: $subText"
                if (!isTest) {
                    AppLogger.log(applicationContext, "INFO", config.id, config.name, "Wyzwalacz (Temat): $text")
                }
                sendNotification(config, fullContent, isTest)
            }
        }
        return matched
    }

    private fun checkMatch(config: NotificationConfig, title: String, text: String, subText: String): Boolean {
        val senderMatch = title.contains(config.emailSender, ignoreCase = true) ||
                         text.contains(config.emailSender, ignoreCase = true) ||
                         subText.contains(config.emailSender, ignoreCase = true)
        val keyword = config.subjectKeyword ?: ""
        val subjectMatch = if (keyword.isEmpty() || keyword == "*") true else title.contains(keyword, ignoreCase = true) || text.contains(keyword, ignoreCase = true)
        return senderMatch && subjectMatch
    }

    private fun sendNotification(config: NotificationConfig, fullDetails: String, isTest: Boolean) {
        val intent = Intent(this, MainActivity::class.java).putExtra("OPEN_URL", config.messageUrl)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(this, config.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationTitle = if (isTest) "[TEST] ${config.name}" else config.name

        val builder = NotificationCompat.Builder(this, "messages_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(notificationTitle)
            .setContentText("Kliknij, aby otworzyć stronę")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Szczegóły Gmail:\n$fullDetails"))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val nm = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            AppLogger.log(applicationContext, "ERROR", config.id, config.name, "Brak uprawnienia POST_NOTIFICATIONS")
            return
        }
        try { nm.notify(config.id.hashCode(), builder.build()) } catch (e: Exception) {
            AppLogger.log(applicationContext, "ERROR", config.id, config.name, "Błąd notify: ${e.message}")
        }
    }
}