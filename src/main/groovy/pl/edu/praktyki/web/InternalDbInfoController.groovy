package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.beans.factory.annotation.Autowired
import pl.edu.praktyki.repository.TransactionRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Prosty endpoint diagnostyczny, dostępny tylko gdy aktywny profil IS NOT 'prod'.
 * Pozwala szybko sprawdzić, z jaką bazą danych aplikacja jest połączona bez jej restartu.
 */
@RestController
@RequestMapping('/internal')
@Profile('!prod')
class InternalDbInfoController {

    @Autowired
    Environment env

    @Autowired(required = false)
    TransactionRepository transactionRepository

    @GetMapping('/db-info')
    Map<String, Object> dbInfo() {
        def active = env.activeProfiles
        if (!active || active.length == 0) active = env.defaultProfiles

        def result = [
                activeProfiles    : active,
                datasourceUrl     : env.getProperty('spring.datasource.url'),
                datasourceUsername: env.getProperty('spring.datasource.username'),
                datasourceDriver  : env.getProperty('spring.datasource.driverClassName'),
                jpaDdlAuto        : env.getProperty('spring.jpa.hibernate.ddl-auto'),
                flywayEnabled     : env.getProperty('spring.flyway.enabled')
        ]

        // Jeśli dostępne repozytorium, dodajemy liczbę transakcji (przydatne do szybkiej weryfikacji)
        try {
            if (transactionRepository) {
                result.transactionsCount = transactionRepository.count()
            }
        } catch (Exception e) {
            // Nie przerywamy działania kontrolera dla błędów w dodatkowym zapytaniu
            result.transactionsCount = "error: ${e.message}"
        }

        return result
    }
}

