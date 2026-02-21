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
./gradlew runSmartFin -PappArgs="-u 'Jacek'"


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

Dla ambitnych: 
Dodaj flagę -v (verbose), która po włączeniu będzie wypisywać każdą przeliczoną transakcję na konsolę przed wygenerowaniem raportu.