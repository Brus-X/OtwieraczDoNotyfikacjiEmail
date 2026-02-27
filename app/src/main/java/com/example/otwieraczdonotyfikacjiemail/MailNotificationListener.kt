package com.example.otwieraczdonotyfikacjiemail

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        testReceiver?.let {
            unregisterReceiver(it)
            testReceiver = null
        }
    }

    private fun registerTestReceiver() {
        val filter = IntentFilter("com.example.otwieracz.CHECK_NOTIFICATIONS")
        testReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val targetConfigName = intent?.getStringExtra("name")
                Log.i(TAG, "--- TEST START (Filtr: ${targetConfigName ?: "WSZYSTKIE"}) ---")
                checkActiveNotifications(targetConfigName)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(testReceiver, filter)
        }
    }

    private fun checkActiveNotifications(targetConfigName: String?) {
        val notifications = try { activeNotifications } catch (e: Exception) { 
            Log.e(TAG, "Błąd pobierania aktywnych powiadomień: ${e.message}")
            null 
        }
        
        if (notifications == null) {
            Log.w(TAG, "Brak dostępu do listy powiadomień.")
            return
        }

        val configs = try { ConfigLoader.loadConfigs(applicationContext) } catch (e: Exception) { return }

        for (sbn in notifications) {
            if (sbn.packageName == "com.google.android.gm") {
                processNotification(sbn, configs, isTest = true, targetConfigName = targetConfigName)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.google.android.gm") return
        val configs = try { ConfigLoader.loadConfigs(applicationContext) } catch (e: Exception) { return }
        processNotification(sbn, configs, isTest = false)
    }

    private fun processNotification(sbn: StatusBarNotification, configs: List<NotificationConfig>, isTest: Boolean, targetConfigName: String? = null) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)

        for (config in configs) {
            if (targetConfigName != null && !config.name.equals(targetConfigName, ignoreCase = true)) continue

            var match = checkMatch(config, title, text, subText)
            if (!match && lines != null) {
                for (line in lines) {
                    if (checkMatch(config, "", line.toString(), "")) {
                        match = true
                        break
                    }
                }
            }

            if (match) {
                val displayTitle = if (isTest) "[TEST] $title" else title
                Log.i(TAG, "✅ Dopasowano: ${config.name} ($displayTitle)")
                sendNotification(config.name, displayTitle, config.messageUrl, config.name.hashCode())
                if (!isTest) break 
            }
        }
    }

    private fun checkMatch(config: NotificationConfig, title: String, text: String, subText: String): Boolean {
        val senderMatch = title.contains(config.emailSender, ignoreCase = true) ||
                         text.contains(config.emailSender, ignoreCase = true) ||
                         subText.contains(config.emailSender, ignoreCase = true)

        val keyword = config.subjectKeyword ?: "*"
        val subjectMatch = if (keyword == "*" || keyword.isEmpty()) true else {
            title.contains(keyword, ignoreCase = true) || text.contains(keyword, ignoreCase = true)
        }
        return senderMatch && subjectMatch
    }

    private fun sendNotification(configName: String, emailTitle: String, messageUrl: String, notificationId: Int) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("OPEN_URL", messageUrl)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pendingIntent = PendingIntent.getActivity(
            this, configName.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "messages_channel"
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("$configName: $emailTitle")
            .setContentText("Kliknij, aby otworzyć stronę")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Błąd przy wysyłaniu powiadomienia: ${e.message}")
        }
    }
}