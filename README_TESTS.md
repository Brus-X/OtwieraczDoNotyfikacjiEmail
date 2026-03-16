# Dokumentacja Testów Regresji - Otwieracz

Ten dokument zawiera opis testów automatycznych zapewniających stabilność aplikacji po wprowadzaniu zmian.

## 1. Lista Testów Jednostkowych (Unit Tests)

### Klasa: `NotificationMatcherTest`
Testuje mechanizm dopasowywania maili z Gmaila do Twojej konfiguracji.

| Nazwa testu | Scenariusz | Oczekiwany wynik |
| :--- | :--- | :--- |
| `matching sender in title` | Nadawca jest w tytule powiadomienia. | **PASS** |
| `matching sender in subText` | Nadawca jest w polu SubText. | **PASS** |
| `case insensitivity` | Ignorowanie wielkości liter (vulcan vs VULCAN). | **PASS** |
| `empty subjectKeyword` | Brak słowa kluczowego - reaguj na każdy mail. | **PASS** |
| `wrong sender` | Powiadomienie od nieznanego nadawcy. | **FAIL** |
| `wrong keyword` | Dobry nadawca, ale zły temat maila. | **FAIL** |
| `multiple configs group` | Obsługa wielu linii w powiadomieniu grupowym. | **PASS** |

### Klasa: `AppLogicTest`
Testuje logikę biznesową i poprawność danych.

| Nazwa testu | Scenariusz | Oczekiwany wynik |
| :--- | :--- | :--- |
| `JSON validation detects missing fields` | Próba importu pliku bez wymaganych pól. | **PASS** (Wykryto błąd) |
| `JSON validation accepts correct fields` | Import prawidłowo sformatowanego pliku. | **PASS** (Sukces) |
| `sorting configs alphabetically works` | Sprawdzenie czy lista szkół jest zawsze posortowana. | **PASS** |

---

## 2. Jak uruchomić testy?

### Opcja A: Przez interfejs Android Studio (Graficznie)
1. W lewym panelu bocznym wybierz zakładkę **Project**.
2. Na górze panelu Project zmień widok z "Android" na **Project Files** (pozwala to lepiej widzieć strukturę folderów).
3. Przejdź do: `app -> src -> test -> java -> com.example.otwieraczdonotyfikacjiemail`.
4. Kliknij prawym przyciskiem myszy na folder `com.example.otwieraczdonotyfikacjiemail`.
5. Masz dwie opcje do wyboru:
   - **Run 'Tests in...'**: Zwykłe uruchomienie testów (szybkie).
   - **Run 'Tests in...' with Coverage**: Uruchomienie z analizą pokrycia kodu (pokaże Ci, które linie kodu zostały przetestowane).

### Opcja B: Przez Terminal (Konsola)
1. Otwórz zakładkę **Terminal** na dolnym pasku Android Studio.
2. Upewnij się, że jesteś w głównym folderze projektu (ścieżka powinna kończyć się na `.../OtwieraczDoNotyfikacjiEmail`).
3. Wpisz i zatwierdź komendę:
   - Na Windows (PowerShell/CMD): `.\gradlew test`
   - Na Linux/Mac: `./gradlew test`
4. Jeśli komenda nie działa, upewnij się, że masz zainstalowaną Javę i poprawnie skonfigurowane środowisko.

## 3. Co robić, gdy test nie przejdzie?
Jeśli po zmianach w kodzie test zaświeci się na czerwono:
1. Kliknij w błąd w panelu **Run**, aby zobaczyć "Expected" (co powinno być) vs "Actual" (co wyszło).
2. Popraw kod tak, aby wszystkie testy były zielone przed wysłaniem zmian na GitHub.
