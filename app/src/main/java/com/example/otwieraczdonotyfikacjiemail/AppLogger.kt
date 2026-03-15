package com.example.otwieraczdonotyfikacjiemail

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val timestamp: Long,
    val type: String, // INFO, ERROR, DEBUG, TEST
    val configId: String?,
    val configName: String?,
    val message: String
) {
    fun formatTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

object AppLogger {
    private const val LOG_FILE = "app_logs.json"
    private const val PREFS_NAME = "log_prefs"
    private const val KEY_LOG_LIMIT = "log_limit"
    private const val DEFAULT_LIMIT = 1000

    fun log(context: Context, type: String, configId: String? = null, configName: String? = null, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), type, configId, configName, message)
        val logs = loadLogs(context).toMutableList()
        logs.add(0, entry)
        
        val limit = getLogLimit(context)
        val trimmed = if (logs.size > limit) logs.take(limit) else logs
        saveLogs(context, trimmed)
    }

    fun clearLogs(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE)
            file.writeText("[]")
        } catch (e: Exception) {}
    }

    fun loadLogs(context: Context): List<LogEntry> {
        val file = File(context.filesDir, LOG_FILE)
        if (!file.exists()) return emptyList()
        
        return try {
            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)
            val logs = mutableListOf<LogEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                logs.add(LogEntry(
                    obj.getLong("t"),
                    obj.getString("tp"),
                    if (obj.isNull("id")) null else obj.getString("id"),
                    if (obj.isNull("n")) null else obj.getString("n"),
                    obj.getString("m")
                ))
            }
            logs
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveLogs(context: Context, logs: List<LogEntry>) {
        try {
            val jsonArray = JSONArray()
            logs.forEach {
                val obj = JSONObject()
                obj.put("t", it.timestamp)
                obj.put("tp", it.type)
                obj.put("id", it.configId ?: JSONObject.NULL)
                obj.put("n", it.configName ?: JSONObject.NULL)
                obj.put("m", it.message)
                jsonArray.put(obj)
            }
            File(context.filesDir, LOG_FILE).writeText(jsonArray.toString())
        } catch (e: Exception) {}
    }

    fun setLogLimit(context: Context, limit: Int) {
        val safeLimit = limit.coerceIn(1, 10000)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LOG_LIMIT, safeLimit).apply()
    }

    fun getLogLimit(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LOG_LIMIT, DEFAULT_LIMIT)
    }
}
