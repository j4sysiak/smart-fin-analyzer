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