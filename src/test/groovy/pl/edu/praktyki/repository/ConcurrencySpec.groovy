package pl.edu.praktyki.repository

import org.springframework.beans.factory.annotation.Autowired
// ZMIANA IMPORTU:
import org.springframework.dao.OptimisticLockingFailureException
import pl.edu.praktyki.BaseIntegrationSpec

class ConcurrencySpec extends BaseIntegrationSpec {

    @Autowired FinancialSummaryRepository summaryRepo

    def "powinien rzucić wyjątek przy próbie równoległego zapisu tej samej wersji"() {
        given: "pobieramy ten sam rekord w dwóch różnych obiektach"
        def summaryV1 = summaryRepo.findById("GLOBAL").get()
        def summaryV2 = summaryRepo.findById("GLOBAL").get()

        and: "mamy pewność, że oba mają tę samą wersję"
        assert summaryV1.version == summaryV2.version

        when: "Pierwszy użytkownik zapisuje zmianę"
        summaryV1.totalBalance += 100
        summaryRepo.saveAndFlush(summaryV1)

        and: "Drugi użytkownik próbuje zapisać swoją zmianę na STAREJ wersji"
        summaryV2.totalBalance += 200
        summaryRepo.saveAndFlush(summaryV2)

        then: "Spring rzuca wyjątek informujący o konflikcie wersji"
        // Łapiemy ogólny wyjątek optymistycznego blokowania
        thrown(OptimisticLockingFailureException)

        and: "możemy wypisać komunikat błędu dla pewności"
        println ">>> SUKCES: Hibernate zablokował niespójny zapis!"
    }
}