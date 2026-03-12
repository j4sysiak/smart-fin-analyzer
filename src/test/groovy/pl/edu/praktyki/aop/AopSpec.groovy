package pl.edu.praktyki.aop

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.ContextConfiguration
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import pl.edu.praktyki.monitoring.FinanceMetrics
import pl.edu.praktyki.service.TransactionIngesterService
import pl.edu.praktyki.service.TransactionRuleService
import pl.edu.praktyki.domain.Transaction
import spock.lang.Specification
import org.mockito.Mockito // Użyjemy Mockito, które jest domyślnie w spring-boot-starter-test

/**
 * Przyczyna błędu: Test używał @SpringBootTest bez parametru classes, co powodowało próbę załadowania pełnego kontekstu aplikacji (baza danych, Flyway, Actuator itd.). Ponieważ kontekst nie mógł się poprawnie załadować, pole service pozostawało null, stąd NullPointerException.
 Rozwiązanie: Zamiana @SpringBootTest na @ContextConfiguration z precyzyjnie podanymi klasami:
 TransactionIngesterService — testowany serwis
 TransactionRuleService — zależność serwisu (@Autowired ruleService)
 LoggingAspect — testowany aspekt AOP
 AopTestConfig — konfiguracja testowa z @EnableAspectJAutoProxy oraz stubami dla ApplicationEventPublisher i FinanceMetrics
 Dzięki temu Spring tworzy lekki kontekst z tylko potrzebnymi beanami, bez bazy danych i innych ciężkich komponentów.


 * Konfiguracja testowa dostarczająca brakujące zależności
 * (ApplicationEventPublisher, FinanceMetrics) jako lekkie stuby
 * oraz włączająca proxy AspectJ.
 * lub możemy użyć Mockito, aby stworzyć "pusty" mock, który nie robi nic
 * (wystarczy, że nie będzie rzucał wyjątków, bo nasz aspekt i tak nie będzie ich używał w tym teście).
 */
@Configuration

// opis tej adnotacji: EnableAspectJAutoProxy
// Hej Spring! Zanim wstrzykniesz mi serwis TransactionIngesterService przez @Autowired w class AopSpec,
// sprawdź, czy napisałem dla niego jakiś Aspekt.
// Jeśli tak, to wygeneruj w locie w pamięci RAM nową klasę (Proxy / Asystenta),
// włóż prawdziwy serwis do jej środka,
// owiń go moim logowaniem, i to tego Asystenta wstrzyknij do mojego testu!"
@EnableAspectJAutoProxy // TO JEST KLUCZ: Włącza AOP dla tego testu
class AopTestConfig {
    @Bean
    ApplicationEventPublisher eventPublisher() {
        // return { event -> } as ApplicationEventPublisher
        //  lub możemy użyć Mockito, aby stworzyć "pusty" mock, który nie robi nic
        return Mockito.mock(ApplicationEventPublisher.class)
    }

    @Bean
    FinanceMetrics financeMetrics() {
        // return new FinanceMetrics(new SimpleMeterRegistry())
        // lub, podobnie jak wyżej, możemy użyć Mockito, aby stworzyć "pusty" mock, który nie robi nic
        return Mockito.mock(FinanceMetrics.class)
    }
}


// Ładujemy tylko to, co jest nam potrzebne. To jest potęga lekkich testów Springa!
@ContextConfiguration(classes =[
        TransactionIngesterService,  // Testowany serwis
        TransactionRuleService,      // Wymagany przez Ingestera (zależność serwisu (@Autowired ruleService))
        LoggingAspect,               // Testowany aspekt AOP

        AopTestConfig                // Włącza AOP i dodaje Mocki reszty zależności
                                     // Konfiguracja testowa z @EnableAspectJAutoProxy oraz stubami dla ApplicationEventPublisher i FinanceMetrics
])
class AopSpec extends Specification {

    @Autowired
    TransactionIngesterService service // Teraz Spring go wstrzyknie!

    def "powinien uruchomić aspekt logujący przy wywołaniu metody serwisu"() {
        given: "transakcja"
        def tx = new Transaction(id: "T1", amount: 100.0)

        when: "wywołujemy metodę serwisu"
        // Logika serwisu (ingestTransactions) uruchomi się, a AOP "podepnie" się w tle
        service.ingestTransactions([tx])

        then: "nie wywala się"
        noExceptionThrown()
    }
}