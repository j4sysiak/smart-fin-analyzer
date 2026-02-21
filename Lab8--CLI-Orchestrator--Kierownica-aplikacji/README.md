Lab 8
-----

To jest finałowy etap budowy Twojej aplikacji! 
W Fazie 4 stworzymy "mózg operacyjny" (Orkiestrator), który połączy wszystkie serwisy w jeden spójny proces 
i udostępni go przez linię komend (CLI).

Faza 4: Krok 8 – CLI Orchestrator (Kierownica aplikacji)
--------------------------------------------------------

Cel: 
Stworzenie klasy startowej, która przyjmie parametry od użytkownika, uruchomi cały rurociąg przetwarzania i wygeneruje plik z raportem.

8.1. Główna aplikacja CLI (`SmartFinApp.groovy`)

Stwórz plik `src/main/groovy/pl/edu/praktyki/SmartFinApp.groovy`.
Użyjemy tutaj `AnnotationConfigApplicationContext`, aby "odpalić" `Springa` wewnątrz zwykłego skryptu Groovy 
i uzyskać dostęp do wszystkich naszych serwisów.

```groovy
package pl.edu.praktyki

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import groovy.cli.picocli.CliBuilder
import pl.edu.praktyki.service.*
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

@Configuration
@ComponentScan(basePackages = "pl.edu.praktyki.service")
class SmartFinConfig {}

class SmartFinApp {

    static void main(String[] args) {
        // 1. Konfiguracja CLI
        def cli = new CliBuilder(usage: 'smart-fin -u <user> [-c <currency>]')
        cli.with {
            u longOpt: 'user', args: 1, required: true, 'Imię i nazwisko użytkownika'
            c longOpt: 'currency', args: 1, 'Waluta bazowa (domyślnie PLN)'
            h longOpt: 'help', 'Pokaż pomoc'
        }

        def opts = cli.parse(args)
        if (!opts || opts.h) return

        println ">>> Inicjalizacja systemu Smart-Fin-Analyzer..."

        // 2. Uruchomienie kontekstu Springa
        def ctx = new AnnotationConfigApplicationContext(SmartFinConfig)
        
        // Pobieramy potrzebne serwisy
        def ingester = ctx.getBean(TransactionIngesterService)
        def currencySvc = ctx.getBean(CurrencyService)
        def analyticsSvc = ctx.getBean(FinancialAnalyticsService)
        def reportSvc = ctx.getBean(ReportGeneratorService)

        // 3. Symulacja pobrania danych (w realnym systemie tu byłby odczyt z pliku)
        println ">>> Importowanie danych..."
        def rawData = [
            new Transaction(id: "1", amount: 100, currency: "EUR", category: "Jedzenie", description: "Obiad w Berlinie"),
            new Transaction(id: "2", amount: -50, currency: "USD", category: "Rozrywka", description: "Kino NY"),
            new Transaction(id: "3", amount: 2000, currency: "PLN", category: "Praca", description: "Bonus")
        ]

        // 4. PROCESOWANIE (Rurociąg)
        println ">>> Przeliczanie walut i analiza..."
        
        // Pobieramy kursy i przeliczamy (Normalizacja)
        rawData.each { tx ->
            def rate = currencySvc.getExchangeRate(tx.currency)
            tx.amountPLN = tx.amount * rate
        }

        // Aplikujemy reguły (używamy reguł zdefiniowanych na sztywno dla przykładu)
        def rules = ["if (amountPLN < -100) addTag('BIG_SPENDER')"]
        ingester.ingestAndApplyRules([rawData], rules)

        // 5. ANALITYKA I RAPORT
        def stats = [
            totalBalance: analyticsSvc.calculateTotalBalance(rawData),
            topCategory: analyticsSvc.getTopSpendingCategory(rawData),
            spendingMap: analyticsSvc.getSpendingByCategory(rawData)
        ]

        String report = reportSvc.generateMonthlyReport(opts.u, stats)

        // 6. WYJŚCIE
        println "\n" + report
        
        def fileName = "report_${opts.u.replace(' ', '_')}.txt"
        new File(fileName).text = report
        println ">>> Raport został zapisany w pliku: $fileName"
        
        ctx.close()
    }
}
```

8.2. Rejestracja Taska w build.gradle

Abyś mógł uruchomić swoją aplikację z terminala, dodaj ten task do build.gradle:

```groovy
tasks.register('runSmartFin', JavaExec) {
group = 'application'
description = 'Uruchamia Smart-Fin-Analyzer CLI'

    mainClass.set('pl.edu.praktyki.SmartFinApp')
    classpath = sourceSets.main.runtimeClasspath
    
    // Wymuszenie Java 17
    def toolchainService = project.extensions.getByType(JavaToolchainService)
    javaLauncher.set(toolchainService.launcherFor(java.toolchain))

    if (project.hasProperty('appArgs')) {
        args(project.getProperty('appArgs').split('\\s+'))
    }
}
```

Jak to przetestować?

Odśwież Gradle w IntelliJ.

Otwórz terminal i wpisz:
`  ./gradlew runSmartFin -PappArgs="-u 'Jacek' -c 'EUR'"  `


Co się teraz wydarzy?

Gradle skompiluje projekt.

`SmartFinApp` uruchomi Springa.

Serwisy zostaną wstrzyknięte.

Aplikacja pobierze kursy walut z internetu.

Przeliczy transakcje (EUR i USD na PLN).

Wygeneruje raport i zapisze go do pliku .txt w głównym folderze projektu.

Wyzwanie Finałowe Projektu:

Twoja aplikacja jest już prawie gotowa do portfolio. Brakuje jej tylko jednej rzeczy: obsługi błędów CLI.

Zmodyfikuj SmartFinApp tak, aby po wpisaniu błędnej waluty (np. -c XYZ) aplikacja nie wybuchła, tylko wyświetliła komunikat: 
BŁĄD: Waluta XYZ nie jest obsługiwana.

Rozwizanie:
-----------

To zadanie uczy Cię budowania odpornych aplikacji (Robust Apps). 
W profesjonalnych narzędziach CLI nie możemy pozwolić, aby błąd użytkownika (literówka w walucie) kończył się "brzydkim" błędem Javy (Stacktrace). 
Musimy to obsłużyć z klasą.

Oto jak zmodyfikować projekt, aby poprawnie walidował walutę.

Krok 1: Poprawka w `CurrencyService.groovy`

Musimy zmienić metodę `getExchangeRate`, aby zamiast domyślnego 1.0 zwracała null, gdy waluta jest nieznana. 
Dzięki temu SmartFinApp będzie wiedział, że coś jest nie tak.

Zaktualizuj plik `src/main/groovy/pl/edu/praktyki/service/CurrencyService.groovy`:

```groovy
BigDecimal getExchangeRate(String fromCurrency) {
if (fromCurrency == "PLN") return 1.0

        try {
            def request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.er-api.com/v6/latest/PLN"))
                    .GET()
                    .build()

            def response = client.send(request, HttpResponse.BodyHandlers.ofString())
            def json = slurper.parseText(response.body())

            def rateToPln = json.rates[fromCurrency]
            
            // POPRAWKA: Jeśli waluty nie ma w mapie 'rates', zwracamy null
            if (rateToPln == null) return null 
            
            return (1.0 / rateToPln).toBigDecimal()
        } catch (Exception e) {
            // W razie błędu sieciowego nadal możemy rzucić wyjątek lub zwrócić null
            return null
        }
    }
```

Krok 2: Poprawka w SmartFinApp.groovy

Teraz w głównej klasie dodamy logikę sprawdzającą parametr -c. Jeśli użytkownik go poda, od razu sprawdzimy jego poprawność.

Zaktualizuj metodę main w src/main/groovy/pl/edu/praktyki/SmartFinApp.groovy:

code
Groovy
download
content_copy
expand_less
// ... (po uruchomieniu kontekstu Springa i pobraniu beanów) ...
def currencySvc = ctx.getBean(CurrencyService)

        // 1. WALIDACJA WALUTY (Zadanie dodatkowe)
        def targetCurrency = opts.c ?: "PLN" // Jeśli brak -c, używamy PLN
        
        if (targetCurrency != "PLN") {
            println ">>> Sprawdzanie dostępności waluty: $targetCurrency..."
            def rate = currencySvc.getExchangeRate(targetCurrency)
            
            if (rate == null) {
                // Wyświetlamy ładny komunikat i kończymy program
                System.err.println "BŁĄD: Waluta $targetCurrency nie jest obsługiwana."
                ctx.close()
                return // Zatrzymuje dalsze wykonywanie main
            }
        }

        // ... (reszta kodu importowania danych) ...
Krok 3: Testowanie w terminalu

Teraz sprawdźmy, czy aplikacja zachowuje się poprawnie w obu przypadkach.

1. Test błędnej waluty:
   Wpisz w terminalu:

code
Bash
download
content_copy
expand_less
./gradlew runSmartFin -PappArgs="-u Jacek -c XYZ"

Oczekiwany wynik:

code
Text
download
content_copy
expand_less
>>> Inicjalizacja systemu Smart-Fin-Analyzer...
>>> Sprawdzanie dostępności waluty: XYZ...
BŁĄD: Waluta XYZ nie jest obsługiwana.

(Zauważ, że raport się nie wygenerował – i o to chodziło!)

2. Test poprawnej waluty:

code
Bash
download
content_copy
expand_less
./gradlew runSmartFin -PappArgs="-u Jacek -c EUR"

Oczekiwany wynik:
Aplikacja powinna przejść dalej i wygenerować raport.

Dlaczego to jest ważne?

User Experience (UX): Użytkownik dostaje jasną informację, co zrobił źle, zamiast czytać NullPointerException.

Graceful Shutdown: Używamy ctx.close(), aby Spring poprawnie zamknął swoje zasoby przed wyjściem z programu.

Fail-Fast: Sprawdzamy walutę na samym początku, zanim zaczniemy "ciężkie" procesowanie danych. Oszczędzamy czas i zasoby.

Daj znać, czy komunikat o błędzie wyświetla się poprawnie! Jeśli tak, to Twój projekt jest już naprawdę "pancerny". Czy chcesz teraz przygotować to profesjonalne README.md, żeby projekt był gotowy do pokazania w portfolio? 📄✨



Dla ambitnych: 
Dodaj flagę -v (verbose), która po włączeniu będzie wypisywać każdą przeliczoną transakcję na konsolę przed wygenerowaniem raportu.