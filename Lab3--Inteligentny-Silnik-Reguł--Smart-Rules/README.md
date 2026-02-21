Lab 3
-----

Znakomicie! Mamy działający, wielowątkowy mechanizm importu danych. 
Teraz czas na "mózg" naszej aplikacji, czyli Krok 3: Inteligentny Silnik Reguł (Smart-Rules).

W tym kroku stworzymy mechanizm, który pozwoli użytkownikowi (lub nam) definiować reguły sprawdzające transakcje "w locie". 
Wykorzystamy tu wiedzę o dynamicznych skryptach i bezpieczeństwie, 
aby aplikacja mogła oznaczać transakcje jako np. "Wysoki wydatek" lub "Podejrzana operacja".

Krok 3.1: Aktualizacja Modelu (Transaction.groovy)
--------------------------------------------------

Musimy dodać pole, w którym będziemy przechowywać wyniki działania reguł (np. tagi).

Zaktualizuj `src/main/groovy/pl/edu/praktyki/domain/Transaction.groovy`:

```groovy
package pl.edu.praktyki.domain

import groovy.transform.Canonical
import java.time.LocalDate

@Canonical
class Transaction {
String id
LocalDate date
BigDecimal amount
String category
String description
List<String> tags = [] // DODANE: Lista tagów nadanych przez reguły

    boolean isExpense() {
        return amount < 0
    }

    void addTag(String tag) {
        if (!tags.contains(tag)) tags << tag
    }
}
```

Krok 3.2: Stworzenie Serwisu Reguł (TransactionRuleService.groovy)
------------------------------------------------------------------

Ten serwis będzie odpowiedzialny za bezpieczne uruchamianie skryptów Groovy dla każdej transakcji.

Stwórz plik `src/main/groovy/pl/edu/praktyki/service/TransactionRuleService.groovy`:


```groovy
package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import pl.edu.praktyki.domain.Transaction
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.SecureASTCustomizer
import groovy.lang.Binding
import groovy.lang.GroovyShell

@Service
class TransactionRuleService {

    // Konfiguracja bezpiecznego środowiska (z Lab 22) - C:\dev\proj-groovy\lab22--Dynamiczne-Reguły-Biznesowe--GroovyShell-and-Security
    private final CompilerConfiguration safeConfig

    TransactionRuleService() {
        // Konfiguracja bezpieczeństwa
        def secure = new SecureASTCustomizer()

        secure.with {
            // Groovy 4 way: blokada klas systemowych (zauważ duże 'L' na końcu)
            receiversClassesBlackList = [System, Runtime, File, Thread] as List

            // Możemy też zabronić importów
            importsBlacklist = ['java.io.*', 'java.lang.reflect.*']

            // Groovy 4 way: blokada pętli (małe 'l' na końcu)
            // Groovy 4 way: blokada pętli (małe 'l' na końcu)
            statementsBlacklist = ['for', 'while']
        }
        safeConfig = new CompilerConfiguration()
        safeConfig.addCompilationCustomizers(secure)
    }

    /**
     * Aplikuje zestaw reguł tekstowych na transakcji.
     * Przykład reguły: "if (amount < -500) addTag('BIG_EXPENSE')"
     */
    void applyRules(Transaction tx, List<String> rules) {
        // Binding udostępnia pola transakcji bezpośrednio w skrypcie
        Binding binding = new Binding([
                amount: tx.amount,
                category: tx.category,
                description: tx.description,
                addTag: { String tag -> tx.addTag(tag) } // Udostępniamy metodę jako domknięcie
        ])

        GroovyShell shell = new GroovyShell(binding, safeConfig)

        rules.each { rule ->
            try {
                shell.evaluate(rule)
            } catch (Exception e) {
                println "[RULE ERROR] Błąd w regule: $rule -> ${e.message}"
            }
        }
    }
}
```

Krok 3.3: Test BDD (TDD style)
------------------------------

Napiszemy teraz test, który udowodni, że nasz system potrafi "zrozumieć" reguły biznesowe podane jako tekst.

stwórz nowy test: `src/test/groovy/pl/edu/praktyki/service/TransactionRuleSpec.groovy`

```groovy
package pl.edu.praktyki.service

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import pl.edu.praktyki.domain.Transaction

@ContextConfiguration(classes = [TransactionRuleService])
class TransactionRuleSpec extends Specification {

    @Autowired
    TransactionRuleService ruleService

    def "powinien nadać tagi na podstawie dynamicznych reguł"() {
        given: "transakcja i zestaw reguł"
        def tx = new Transaction(id: "T1", amount: -1500.0, category: "Dom", description: "Czynsz za luty")
        def rules = [
            "if (amount < -1000) addTag('HIGH_PRIORITY')",
            "if (category == 'Dom') addTag('HOUSING')",
            "if (description.contains('luty')) addTag('MONTHLY')"
        ]

        when: "aplikujemy reguły"
        ruleService.applyRules(tx, rules)

        then: "transakcja powinna mieć odpowiednie tagi"
        tx.tags.size() == 3
        tx.tags.containsAll(['HIGH_PRIORITY', 'HOUSING', 'MONTHLY'])
    }
}
```

Analiza Kroku 3:

Separacja logiki: 
Reguły nie są "zaszyte" w kodzie Javy/Groovy na sztywno. 
Możemy je trzymać w bazie danych lub pliku konfiguracyjnym.

Bezpieczeństwo: 
Dzięki SecureASTCustomizer użytkownik nie może wpisać System.exit(0), by wyłączyć aplikację.

Wygoda: 
W Binding przekazaliśmy metodę addTag. 
Dzięki temu skrypt użytkownika wygląda bardzo naturalnie: addTag('X').