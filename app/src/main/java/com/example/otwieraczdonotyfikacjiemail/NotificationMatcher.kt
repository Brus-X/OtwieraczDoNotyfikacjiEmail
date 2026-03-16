package com.example.otwieraczdonotyfikacjiemail

object NotificationMatcher {

    fun checkMatch(config: NotificationConfig, title: String, text: String, subText: String): Boolean {
        // Sprawdzamy nadawcę w tytule, treści lub subtekście
        val senderMatch = title.contains(config.emailSender, ignoreCase = true) ||
                         text.contains(config.emailSender, ignoreCase = true) ||
                         subText.contains(config.emailSender, ignoreCase = true)

        val keyword = config.subjectKeyword ?: ""
        // Puste pole lub "*" oznacza dopasowanie do wszystkiego od danego nadawcy
        val subjectMatch = if (keyword.isEmpty() || keyword == "*") {
            true
        } else {
            // Słowo kluczowe sprawdzamy w tytule i treści
            title.contains(keyword, ignoreCase = true) || text.contains(keyword, ignoreCase = true)
        }

        return senderMatch && subjectMatch
    }

    fun findMatches(configs: List<NotificationConfig>, title: String, text: String, subText: String, lines: Array<CharSequence>?): List<NotificationConfig> {
        val matchedConfigs = mutableListOf<NotificationConfig>()
        
        for (config in configs) {
            var match = checkMatch(config, title, text, subText)
            
            // Jeśli nie ma dopasowania w głównych polach, a powiadomienie jest grupowe (lines)
            if (!match && lines != null) {
                for (line in lines) {
                    if (checkMatch(config, "", line.toString(), "")) {
                        match = true
                        break
                    }
                }
            }
            
            if (match) {
                matchedConfigs.add(config)
            }
        }
        return matchedConfigs
    }
}
