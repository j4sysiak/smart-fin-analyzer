package pl.edu.praktyki.service

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Service
import pl.edu.praktyki.domain.Transaction
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.SecureASTCustomizer
import groovy.lang.Binding
import groovy.lang.GroovyShell

@Service
@Slf4j
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
        // DODAJEMY amountPLN do mapy bindingu
        Binding binding = new Binding([
                amount: tx.amount,
                amountPLN: tx.amountPLN, // <--- TO JEST KLUCZOWA POPRAWKA
                category: tx.category,
                description: tx.description,
                addTag: { String tag -> tx.addTag(tag) }
        ])

        GroovyShell shell = new GroovyShell(binding, safeConfig)

        rules.each { rule ->
            try {
                shell.evaluate(rule)
            } catch (Exception e) {
                // Teraz błąd już nie powinien wystąpić
                log.error("[RULE ERROR] Błąd w regule: {} -> {}", rule, e.message)
            }
        }
    }
}