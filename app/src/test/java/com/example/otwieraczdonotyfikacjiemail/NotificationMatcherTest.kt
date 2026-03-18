package com.example.otwieraczdonotyfikacjiemail

import org.junit.Assert.*
import org.junit.Test

class NotificationMatcherTest {

    private val testConfig = NotificationConfig(
        id = "1",
        name = "Szkoła Test",
        startUrl = "http://start",
        messageUrl = "http://message",
        emailSender = "Dziennik VULCAN",
        subjectKeyword = "od"
    )

    @Test
    fun `matching sender in title returns true`() {
        val result = NotificationMatcher.checkMatch(
            testConfig,
            title = "Dziennik VULCAN",
            text = "Wiadomość od nauczyciela", // Zawiera "od"
            subText = "powiadomienia@vulcan.pl"
        )
        assertTrue(result)
    }

    @Test
    fun `matching sender in subText returns true`() {
        val result = NotificationMatcher.checkMatch(
            testConfig,
            title = "Nowa wiadomość",
            text = "Wiadomość od systemu", // Dodano "od", aby spełnić warunek subjectKeyword
            subText = "Dziennik VULCAN"
        )
        assertTrue(result)
    }

    @Test
    fun `case insensitivity for sender works`() {
        val result = NotificationMatcher.checkMatch(
            testConfig,
            title = "dziennik vulcan",
            text = "Wiadomość od kogoś",
            subText = ""
        )
        assertTrue(result)
    }

    @Test
    fun `empty subjectKeyword matches any title`() {
        val configWithEmptyKeyword = testConfig.copy(subjectKeyword = "")
        val result = NotificationMatcher.checkMatch(
            configWithEmptyKeyword,
            title = "Dziennik VULCAN",
            text = "Dowolny temat",
            subText = ""
        )
        assertTrue(result)
    }

    @Test
    fun `wrong sender returns false`() {
        val result = NotificationMatcher.checkMatch(
            testConfig,
            title = "Inny Nadawca",
            text = "Wiadomość od",
            subText = ""
        )
        assertFalse(result)
    }

    @Test
    fun `wrong keyword in both title and text returns false`() {
        val result = NotificationMatcher.checkMatch(
            testConfig,
            title = "Dziennik VULCAN",
            text = "Brak pasującego słowa",
            subText = ""
        )
        assertFalse(result)
    }

    @Test
    fun `matching multiple configs from group notification lines`() {
        val configs = listOf(
            testConfig, // szuka "Dziennik VULCAN" i "od"
            testConfig.copy(id = "2", name = "Inna Szkoła", emailSender = "Sekretariat", subjectKeyword = "zebranie") // zmieniono na "zebranie"
        )
        
        val lines = arrayOf<CharSequence>(
            "Dziennik VULCAN: Wiadomość od dyrektora", // Pasuje do 1
            "Sekretariat: Zaproszenie na zebranie"      // Pasuje do 2
        )

        val matches = NotificationMatcher.findMatches(
            configs,
            title = "2 nowe wiadomości",
            text = "Zobacz szczegóły",
            subText = "moje@konto.pl",
            lines = lines
        )

        assertEquals("Powinny być 2 dopasowania", 2, matches.size)
        assertTrue(matches.any { it.name == "Szkoła Test" })
        assertTrue(matches.any { it.name == "Inna Szkoła" })
    }
}
