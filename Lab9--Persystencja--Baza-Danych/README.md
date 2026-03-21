Lab 9
-----

To świetny krok! Przejście z przechowywania danych w pamięci (RAM) na bazę danych to moment, 
w którym aplikacja staje się użyteczna w czasie. 
Dzięki temu będziesz mógł sprawdzić swoje wydatki sprzed miesiąca, nawet jeśli w międzyczasie wyłączyłeś komputer.

Zastosujemy `Spring Data JPA` oraz bazę `H2` w trybie plikowym (dane będą zapisywane w folderze projektu).

Krok 9.1: Dodanie zależności (build.gradle)
-------------------------------------------

Musimy dodać "silnik" bazy danych i warstwę dostępu do danych (JPA).

Dopisz te dwie linie do sekcji dependencies w build.gradle i odśwież projekt:

```groovy
dependencies {
// ... poprzednie zależności ...
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
runtimeOnly 'com.h2database:h2'
}
```

Krok 9.2: Stwórz nową klasę dla Bazy Danych (TransactionEntity)
---------------------------------------------------------------
Rozdzielmy Model Domenowy (dla logiki i starej aplikacji) od Modelu Bazy Danych (Encji).
Stwórz nowy plik `src/main/groovy/pl/edu/praktyki/repository/TransactionEntity.groovy`. 
To jest obiekt, który służy tylko i wyłącznie do zapisu w tabeli.

```groovy
package pl.edu.praktyki.repository

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "transactions")
class TransactionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long dbId

    String originalId // Zamiast 'id', żeby nie myliło się z kluczem w bazie
    LocalDate date
    BigDecimal amount
    String currency
    BigDecimal amountPLN
    String category
    String description
    
    @ElementCollection(fetch = FetchType.EAGER)
    List<String> tags = []

    // Wymagany przez Hibernate pusty konstruktor
    TransactionEntity() {}
}
```


Krok 9.3: Stworzenie Repozytorium (TransactionRepository.groovy)
----------------------------------------------------------------
Repozytorium musi teraz operować na Encji, a nie na klasie domenowej.
Stwórz plik `src/main/groovy/pl/edu/praktyki/repository/TransactionRepository.groovy`:
 
```groovy
package pl.edu.praktyki.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    List<TransactionEntity> findAllByCategory(String category)
}
```


Krok 9.4: Konfiguracja bazy w pliku (application.properties)
------------------------------------------------------------
Chcemy, aby dane nie znikały. Stwórz plik src/main/resources/application.properties:

```text
# Dane będą zapisywane w pliku w folderze 'db' wewnątrz projektu
spring.datasource.url=jdbc:h2:file:./db/smartfin;DB_CLOSE_DELAY=-1
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# Automatyczne tworzenie tabel na podstawie klas @Entity
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
```


Krok 9.5: Integracja w SmartFinDbApp.groovy
-------------------------------------------
Teraz połączymy wszystko. 
Aplikacja po przetworzeniu danych zapisze je do bazy, a na końcu wyświetli sumę wszystkich historycznych transakcji.

Nie chcemy zepsuć czegoś, co już działa, wprowadzając nowy ficzer (bazę danych).
Zostawmy Twoją oryginalną klasę SmartFinApp w spokoju (niech działa na czystym Springu w pamięci), 
a dla wersji z bazą danych stworzymy nową aplikację startową i nowy task w Gradle.

Utworzenie nowej klasy startowej z bazą danych
Stwórz nowy plik `src/main/groovy/pl/edu/praktyki/SmartFinDbApp.groovy`.
Ta klasa będzie używała pełnego Spring Boota i repozytorium.
 
```groovy
package pl.edu.praktyki

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.CommandLineRunner
import org.springframework.beans.factory.annotation.Autowired
import groovy.cli.picocli.CliBuilder
import pl.edu.praktyki.service.*
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.domain.Transaction
import java.time.LocalDate

@SpringBootApplication
class SmartFinDbApp implements CommandLineRunner {

    @Autowired TransactionIngesterService ingester
    @Autowired CurrencyService currencySvc
    @Autowired FinancialAnalyticsService analyticsSvc
    @Autowired ReportGeneratorService reportSvc
    @Autowired TransactionRepository repo

    static void main(String[] args) {
        // Uruchamiamy aplikację Spring Boota
        SpringApplication.run(SmartFinDbApp, args)
    }

    @Override
    void run(String... args) {
        def cli = new CliBuilder(usage: 'smart-fin-db -u <user> [-c <currency>]')
        cli.with {
            u longOpt: 'user', args: 1, required: true, 'Imię i nazwisko użytkownika'
            c longOpt: 'currency', args: 1, 'Waluta bazowa (domyślnie PLN)'
            h longOpt: 'help', 'Pokaż pomoc'
        }

        def opts = cli.parse(args)
        if (!opts || opts.h) return

        println "\n========================================="
        println ">>> Uruchamianie wersji z BAZĄ DANYCH (H2)..."
        
        def targetCurrency = opts.c ?: "PLN"
        if (targetCurrency != "PLN") {
            def rate = currencySvc.getExchangeRate(targetCurrency)
            if (rate == null) {
                System.err.println "BŁĄD: Waluta $targetCurrency nie jest obsługiwana."
                return 
            }
        }

        def rawData = [
            new Transaction(id: "1", amount: 100, currency: "EUR", category: "Jedzenie", description: "Obiad", date: LocalDate.now()),
            new Transaction(id: "2", amount: -50, currency: "USD", category: "Rozrywka", description: "Kino", date: LocalDate.now()),
            new Transaction(id: "3", amount: 2000, currency: "PLN", category: "Praca", description: "Bonus", date: LocalDate.now())
        ]

        rawData.each { tx ->
            def rate = currencySvc.getExchangeRate(tx.currency)
            tx.amountPLN = tx.amount * rate
        }

        def rules = ["if (amountPLN < -100) addTag('BIG_SPENDER')"]
        ingester.ingestAndApplyRules([rawData], rules)

        // ZAPIS DO BAZY
        println ">>> Zapisywanie do bazy H2..."
        repo.saveAll(rawData)

        // ODCZYT Z BAZY
        def allHistory = repo.findAll()
        println ">>> W bazie znajduje się obecnie ${allHistory.size()} transakcji."

        // Generujemy raport z całej historii, a nie tylko bieżącej paczki!
        def stats = [
            totalBalance: analyticsSvc.calculateTotalBalance(allHistory),
            topCategory: analyticsSvc.getTopSpendingCategory(allHistory),
            spendingMap: analyticsSvc.getSpendingByCategory(allHistory)
        ]

        String report = reportSvc.generateMonthlyReport(opts.u, stats)

        println "\n" + report
        def fileName = "db_report_${opts.u.replace(' ', '_')}.txt"
        new File(fileName).text = report
        println ">>> Raport zapisany: $fileName"
        println "=========================================\n"
    }
}
```

Krok 9.6: Nowy task w `build.gradle`
-------------------------------------------
Dodaj nowe zadanie uruchomieniowe do `build.gradle` (możesz je wkleić zaraz pod runSmartFin).

```groovy
tasks.register('runSmartFinDb', JavaExec) {
    group = 'application'
    description = 'Uruchamia Smart-Fin-Analyzer z bazą danych (Spring Boot)'
    
    // Wskazujemy NOWĄ klasę
    mainClass.set('pl.edu.praktyki.SmartFinDbApp') 
    classpath = sourceSets.main.runtimeClasspath
    
    // Wymuszenie Java 17
    def toolchainService = project.extensions.getByType(JavaToolchainService)
    javaLauncher.set(toolchainService.launcherFor(java.toolchain))

    if (project.hasProperty('appArgs')) {
        args(project.getProperty('appArgs').split('\\s+'))
    }
}
```


Co osiągnąłeś tym ruchem?
-------------------------
To jest coś, o co pytają rekruterzy na stanowiska Mid/Senior: "Jak oddzielasz bazę danych od logiki biznesowej?".
Właśnie pokazałeś rozwiązanie: Logika (i stare testy) pracują na czystym POGO (Transaction).
Baza pracuje na TransactionEntity.
Aplikacja App zajmuje się tłumaczeniem jednego na drugie.
Uruchom teraz najpierw stare testy (./gradlew clean test), a potem nową aplikację z bazą (./gradlew runSmartFinDb -PappArgs="-u Jacek").
Obie rzeczy będą działać niezależnie od siebie!



Jak z tego korzystać?
Dzięki temu podejściu masz teraz dwa osobne narzędzia w jednym projekcie!

1. Wersja "Light" (Stara, bez bazy, szybka):

```bash
./gradlew runSmartFin -PappArgs="-u Jacek -c EUR"
```

2. Wersja "Pro" (Nowa, z bazą danych H2 i historią):
Odśwież Gradle w IntelliJ, a następnie wpisz:

```bash
./gradlew runSmartFinDb -PappArgs="-u Jacek -c EUR"

```

 
Jak to przetestować?

Uruchom aplikację raz:
./gradlew runSmartFin -PappArgs="-u Jacek"
Zobaczysz: "W bazie znajduje się obecnie 3 transakcji".

Uruchom aplikację drugi raz:
./gradlew runSmartFin -PappArgs="-u Jacek"
Zobaczysz: "W bazie znajduje się obecnie 6 transakcji".

To dowód, że persystencja działa! Dane przetrwały między uruchomieniami.

Zadanie dla Ciebie:
Wdroż zmiany w build.gradle, modelu, repozytorium i aplikacji.
Wyzwanie: Zmień logikę generowania raportu tak, aby totalBalance był liczony nie z rawData (bieżący import), ale z allHistory (cała historia z bazy).
Daj znać, czy baza danych "wstała" i czy widzisz rosnącą liczbę transakcji w logach! 🗄️📈