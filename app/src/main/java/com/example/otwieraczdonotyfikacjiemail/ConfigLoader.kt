package com.example.otwieraczdonotyfikacjiemail

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ConfigLoader {
    private const val TAG = "ConfigLoader"
    private const val FILE_NAME = "config.json"

    fun loadConfigs(context: Context): List<NotificationConfig> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            
            if (!file.exists()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Plik nie istnieje, kopiuję z assets...")
                val jsonFromAssets = context.assets.open(FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
                file.writeText(jsonFromAssets, Charsets.UTF_8)
            }

            val jsonString = file.readText(Charsets.UTF_8)
            parseJson(jsonString)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Błąd krytyczny ładowania configu: ${e.message}")
            try {
                val raw = context.assets.open(FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseJson(raw)
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    private fun parseJson(jsonString: String): List<NotificationConfig> {
        val jsonArray = JSONArray(jsonString)
        val configs = mutableListOf<NotificationConfig>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            configs.add(
                NotificationConfig(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    name = obj.getString("name"),
                    startUrl = obj.optString("startUrl", ""),
                    messageUrl = obj.getString("messageUrl"),
                    emailSender = obj.getString("emailSender"),
                    subjectKeyword = obj.optString("subjectKeyword", "")
                )
            )
        }
        return configs.sortedBy { it.name }
    }

    fun saveConfigs(context: Context, configs: List<NotificationConfig>) {
        try {
            val jsonArray = JSONArray()
            for (config in configs) {
                val obj = JSONObject()
                obj.put("id", config.id)
                obj.put("name", config.name)
                obj.put("startUrl", config.startUrl)
                obj.put("messageUrl", config.messageUrl)
                obj.put("emailSender", config.emailSender)
                obj.put("subjectKeyword", config.subjectKeyword)
                jsonArray.put(obj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Błąd zapisu configu: ${e.message}")
        }
    }
}