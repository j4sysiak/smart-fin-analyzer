package pl.edu.praktyki.aop

import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.ContextConfiguration
import pl.edu.praktyki.monitoring.FinanceMetrics
import pl.edu.praktyki.service.TransactionIngesterService
import pl.edu.praktyki.domain.Transaction
import pl.edu.praktyki.service.TransactionRuleService
import spock.lang.Specification


@EnableAspectJAutoProxy // TO JEST KLUCZ: Włącza AOP dla tego testu
class SecurityAopTestConfig {
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
        SecurityAspect,              // Testowany aspekt AOP (Strażnik Kwot)

        SecurityAopTestConfig        // Włącza AOP i dodaje Mocki reszty zależności
        // Konfiguracja testowa z @EnableAspectJAutoProxy oraz stubami dla ApplicationEventPublisher i FinanceMetrics
])
class SecurityAspectSpec extends Specification {

    @Autowired TransactionIngesterService service

    def "powinien zablokować transakcję powyżej 100 000 zł"() {
        given: "transakcja z gigantyczną kwotą"
        def tx = new Transaction(id: "HACKER-1", amount: 999_999.0)

        when: "próbujemy zaimportować taką transakcję"
        service.ingestTransactions([tx])

        then: "AOP wychwytuje to i rzuca SecurityException"
        thrown(SecurityException)
    }

    def "powinien przepuścić transakcję bezpieczną"() {
        given: "transakcja w limicie"
        def tx = new Transaction(id: "OK-1", amount: 500.0)

        when:
        service.ingestTransactions([tx])

        then: "brak wyjątku"
        noExceptionThrown()
    }
}