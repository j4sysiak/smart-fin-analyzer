package pl.edu.praktyki.contract

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.spockframework.spring.SpringBean
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@ContextConfiguration(classes = [TransactionAnalysisOrchestratorIdempotencySpec.IdempotencyTestConfig])
class TransactionAnalysisOrchestratorIdempotencySpec extends Specification {

    @Autowired
    TransactionAnalysisOrchestrator orchestrator

    @Autowired
    CacheManager cacheManager

    @SpringBean
    TransactionAnalyzer analyzer = Mock()

    private final AtomicInteger analyzeCallsCounter = new AtomicInteger(0)

/*
    Ta klasa służy jako lokalna konfiguracja Springa dla testu w pliku src/test/groovy/pl/edu/praktyki/contract/TransactionAnalysisOrchestratorIdempotencySpec.groovy.

    Po co jest potrzebna
     - uruchamia minimalny kontekst Spring, tylko z beanami potrzebnymi do tego testu
     - włącza cache przez @EnableCaching
     - dostarcza testowy CacheManager z cache o nazwie transactionAnalysis
     - rejestruje prawdziwy TransactionDecisionPolicy
     - tworzy TransactionAnalysisOrchestrator, do którego Spring wstrzykuje mockowany TransactionAnalyzer

    Dlaczego nie wystarczy sam test
    @ContextConfiguration potrzebuje klasy konfiguracyjnej, żeby wiedzieć:
     - jakie beany utworzyć
     - jak złożyć zależności
     - skąd wziąć cache manager

    Bez tej klasy:
     - @Autowired TransactionAnalysisOrchestrator nie miałby skąd się wziąć
     - @Autowired CacheManager też nie byłby dostępny
     - mechanizm cache mógłby w ogóle nie działać w teście

    W praktyce
    Ta klasa buduje małe środowisko testowe, zamiast ładować cały Spring Boot application context.

    Dzięki temu test jest:
     - szybszy
     - prostszy
     - bardziej kontrolowany
     - skupiony tylko na idempotencji i cache
*/
    @TestConfiguration
    @EnableCaching
    static class IdempotencyTestConfig {

        @Bean
        CacheManager cacheManager() {
            new ConcurrentMapCacheManager("transactionAnalysis")
        }

        @Bean
        TransactionDecisionPolicy transactionDecisionPolicy() {
            new DefaultTransactionDecisionPolicy()
        }

        @Bean
        TransactionAnalysisOrchestrator transactionAnalysisOrchestrator(
                TransactionAnalyzer analyzer,
                TransactionDecisionPolicy decisionPolicy
        ) {
            new TransactionAnalysisOrchestrator(analyzer, decisionPolicy)
        }
    }

    def setup() {
        cacheManager.getCache("transactionAnalysis")?.clear()  // czyścimy cache przed każdym testem, żeby nie było "zanieczyszczenia" wynikami z poprzednich testów
        analyzeCallsCounter.set(0) // resetujemy licznik wywołań analyze() przed każdym testem, żeby mieć pewność, że liczymy tylko wywołania z danego testu
    }

    def "powinien zwrócić wynik z cache przy tym samym correlationId (analyze tylko raz)"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-IDEM-001")
                .accountId("ACC-IDEM-001")
                .correlationId("CORR-IDEM-SAME")
                .timestamp(Instant.parse("2026-05-23T18:00:00Z"))
                .amount(9999.99)
                .payload([:])
                .build()

        when:
        def first = orchestrator.process(request)
        def second = orchestrator.process(request)


        then:
        /*
        Ta linia w Spocku definiuje oczekiwanie na wywołanie mocka i jednocześnie jego zachowanie:
           1 * - to jest oczekiwanie, że metoda analyze zostanie wywołana dokładnie 1 raz
                 analyzer.analyze(...) — chodzi o wywołanie tej metody na mocku analyzer
                 _ as TransactionIngressRequest — akceptowany jest dowolny argument, byle był typu TransactionIngressRequest
        >> { ... } — po wywołaniu ma zostać wykonana closure, która:
                - dostaje przekazany argument jako req
                - zwiększa licznik wywołań
                - zwraca przygotowany AnalysisResult
        */
        1 * analyzer.analyze(_ as TransactionIngressRequest) >> {
                 /* to jest to closure */
            TransactionIngressRequest req ->
                    //zwiększa licznik wywołań
                    analyzeCallsCounter.incrementAndGet()

                    // zwraca przygotowany AnalysisResul
                    AnalysisResult.builder()
                            .transactionId(req.transactionId)
                            .correlationId(req.correlationId)
                            .status(AnalysisStatus.OK)
                            .analyzedAt(Instant.parse("2026-05-23T18:00:10Z"))
                            .details("mocked analyzer")
                            .build()
        }

        and:
        first.decision == "ACCEPT"
        second.decision == "ACCEPT"
        first.reason == "Status OK - transaction accepted"
        second.reason == "Status OK - transaction accepted"
        first.transactionId == second.transactionId
        first.correlationId == second.correlationId
        analyzeCallsCounter.get() == 1  // !!!!!   czyli analyze został wywołany tylko raz, a drugi wynik pochodzi z cache
    }

    def "powinien pominąć cache gdy correlationId jest pusty i wywołać analyze dwa razy"() {
        given:
        def request = TransactionIngressRequest.builder()
                .transactionId("TX-IDEM-002")
                .accountId("ACC-IDEM-002")
                .correlationId("   ")   // correlationId jest pusty
                .timestamp(Instant.parse("2026-05-23T18:10:00Z"))
                .amount(10000.01)
                .payload([:])
                .build()

        when:
        def first = orchestrator.process(request)
        def second = orchestrator.process(request)

        then:
        2 * analyzer.analyze(_ as TransactionIngressRequest) >> { TransactionIngressRequest req ->
            analyzeCallsCounter.incrementAndGet()
            AnalysisResult.builder()
                    .transactionId(req.transactionId)
                    .correlationId(req.correlationId)
                    .status(AnalysisStatus.FLAGGED)
                    .analyzedAt(Instant.parse("2026-05-23T18:10:10Z"))
                    .details("mocked analyzer")
                    .build()
        }

        and:
        first.decision == "ACCEPT_WITH_WARNING"
        second.decision == "ACCEPT_WITH_WARNING"
        analyzeCallsCounter.get() == 2
    }
}