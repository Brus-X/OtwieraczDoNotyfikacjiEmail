package com.example.otwieraczdonotyfikacjiemail

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class AppLogicTest {

    @Test
    fun `JSON validation detects missing fields`() {
        // Symulujemy niepełny obiekt JSON (brak pola emailSender)
        val jsonStr = """[{"name":"Test","messageUrl":"http://test"}]"""
        val arr = JSONArray(jsonStr)
        val obj = arr.getJSONObject(0)
        
        val isValid = obj.has("name") && obj.has("messageUrl") && obj.has("emailSender")
        assertFalse("Walidacja powinna wykryć brak pola emailSender", isValid)
    }

    @Test
    fun `JSON validation accepts correct fields`() {
        val jsonStr = """[{"name":"Test","messageUrl":"http://test","emailSender":"X"}]"""
        val arr = JSONArray(jsonStr)
        val obj = arr.getJSONObject(0)
        
        val isValid = obj.has("name") && obj.has("messageUrl") && obj.has("emailSender")
        assertTrue("Prawidłowy JSON powinien przejść walidację", isValid)
    }

    @Test
    fun `sorting configs alphabetically works`() {
        val list = listOf(
            NotificationConfig(name = "Zzz", startUrl = "", messageUrl = "", emailSender = "", subjectKeyword = ""),
            NotificationConfig(name = "Aaa", startUrl = "", messageUrl = "", emailSender = "", subjectKeyword = "")
        )
        val sorted = list.sortedBy { it.name }
        assertEquals("Aaa", sorted[0].name)
        assertEquals("Zzz", sorted[1].name)
    }
}
