package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.repository.TransactionRepository

import java.time.LocalDate


// 1. @ActiveProfiles(value = ["local-pg"], inheritProfiles = false) powoduje,
//    że kontekst testowy ładuje profil local-pg i w efekcie użyje ustawień z pliku application-local-pg.properties
//    (albo application-local-pg.yml) zamiast domyślnych.
// 2. Test uruchamia pełny kontekst Springa (MockMvc + repozytoria) i będzie próbował połączyć się
//    z bazą zgodnie z ustawieniami z tego pliku.
//    Komentarz w kodzie słusznie mówi: musisz mieć lokalnego Postgresa uruchomionego — test tego serwera
//    nie uruchomi samodzielnie.
// 3. inheritProfiles = false oznacza, że tylko local-pg jest aktywny (inne profile domyślne nie są łączone).
// 4. H2 nie będzie działać w tym projekcie (jak napisano), więc albo uruchom lokalny Postgres,
//    albo użyj Testcontainers / wbudowanego Postgresa, jeśli chcesz automatycznie startować DB w testach.
// 5. Sprawdź w application-local-pg.properties URL, username i password oraz czy schemat/bazy zostały przygotowane przed uruchomieniem testów.


@AutoConfigureMockMvc

//KNOW HOW!  (ActiveProfiles = 'tc')
// To nie dziala, żeby użyć Testcontainers - profil 'tc', to BaseIntegrationSpec ustaw warunek na if (1==1)
// wtedy zawsze będzie używał Testcontainers.
// Wtedy ten test będzie działał bez konieczności uruchamiania ręcznie Postgresa na Docker.
// wtedy postgres będzie uruchamiany automatycznie w kontenerze Docker przez Testcontainers,
// a po zakończeniu testów będzie automatycznie zatrzymywany i usuwany.

//@ActiveProfiles(value = ["tc"], inheritProfiles = false)


//KNOW HOW!  (ActiveProfiles = 'local-pg')
// Wymusi użycie application-local-pg.properties ale musisz mieć wlączony lokalny Postgresa!
// (nie działa z H2, bo H2 nie obsługuje funkcji SQL, których używamy w repozytorium)
// tutaj info jak uruchomić lokalnego postgresa na dokerze dla profilu: local-pg:
//                     C:\dev\smart-fin-analyzer\src\test\resources\application-local-pg.properties

@ActiveProfiles("tc") // use Testcontainers for tests (start PostgreSQL container automatically)
@org.springframework.transaction.annotation.Transactional
@org.springframework.test.annotation.Rollback
class SmartFinFinalReportSpec extends BaseIntegrationSpec {

    @Autowired
    FinancialAnalyticsService analyticsService

    @Autowired
    ReportGeneratorService reportGeneratorService

    @Autowired
    TransactionRepository repository


    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
    }

    def "powinien przeprowadzić pełny proces: od surowych danych do gotowego raportu"() {
        given: "1. Lista surowych transakcji (już po przeliczeniu na PLN)"
        def transactions = [
                new Transaction(id: "T1", amountPLN: 6000.0, category: "Praca", description: "Wypłata", date: LocalDate.now()),
                new Transaction(id: "T2", amountPLN: -2200.0, category: "Dom", description: "Czynsz i media", date: LocalDate.now()),
                new Transaction(id: "T3", amountPLN: -450.0, category: "Jedzenie", description: "Zakupy tydzień 1", date: LocalDate.now()),
                new Transaction(id: "T4", amountPLN: -300.0, category: "Jedzenie", description: "Restauracja", date: LocalDate.now()),
                new Transaction(id: "T5", amountPLN: -150.0, category: "Rozrywka", description: "Kino i popcorn", date: LocalDate.now()),
                new Transaction(id: "T6", amountPLN: -120.0, category: "Zdrowie", description: "Apteka", date: LocalDate.now())
        ]

        when: "2. Wykorzystujemy FinancialAnalyticsService do wyciągnięcia statystyk"
        def total = analyticsService.calculateTotalBalance(transactions)
        def spending = analyticsService.getSpendingByCategory(transactions)
        def top = analyticsService.getTopSpendingCategory(transactions)

        and: "3. Przygotowujemy mapę parametrów dla generatora"
        def statsMap = [
                totalBalance: total,
                topCategory: top,
                spendingMap: spending
        ]

        and: "4. Generujemy finałowy raport"
        String finalReport = reportGeneratorService.generateMonthlyReport("Twoje Imię", statsMap)

        then: "5. Weryfikujemy czy raport jest kompletny i poprawny"
        println finalReport // WYDRUK NA KONSOLĘ

        finalReport.contains("TWOJE IMIĘ")
        finalReport.contains("Bilans całkowity:  2780.0 PLN")
        finalReport.contains("Główny wydatek:    Dom")

        and: "kategorie są poprawnie zsumowane (Jedzenie: 450 + 300 = 750)"
        finalReport.contains("Jedzenie        : 750.00 PLN")

        and: "status finansowy jest poprawny"
        finalReport.contains("Status: NA PLUSIE")
    }
}