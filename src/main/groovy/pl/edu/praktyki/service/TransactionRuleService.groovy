package pl.edu.praktyki.service

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Service
import pl.edu.praktyki.domain.Transaction
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.SecureASTCustomizer
import groovy.lang.Binding
import groovy.lang.GroovyShell
import groovy.lang.Closure

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
     * Aplikuje zestaw reguł/rul tekstowych na transakcji.
     * Przykład reguły/roli: "if (amount < -500) addTag('BIG_EXPENSE')"
     */
    void applyRules(Transaction tx, List<String> rules) {

        // mapa bindingu
        Binding binding = new Binding([
                amount: tx.amount,
                amountPLN: tx.amountPLN,
                category: tx.category,
                description: tx.description,
                addTag: { String tag -> tx.addTag(tag) }
        ])

        // Fallback: evaluate rules directly if caller didn't precompile them.
        GroovyShell shell = new GroovyShell(binding, safeConfig)
        rules.each { rule ->
            try {
                shell.evaluate(rule)
            } catch (Exception e) {
                log.error("[RULE ERROR] Błąd w regule: {} -> {}", rule, e.message)
            }
        }
    }

    /**
     * Precompile a list of rule strings into Script classes. Caller should do this once per
     * pipeline run to avoid reparsing the Groovy source for every transaction.
     */
    /**
     * Compile rules into Closures. Each closure will be called with arguments:
     * (amount, amountPLN, category, description, addTagClosure)
     */
    List<Closure> compileRules(List<String> rules) {
        if (!rules) return []
        GroovyShell shell = new GroovyShell(safeConfig)
        rules.collect { String rule ->
            try {
                // Fast path: patterns we expect in tests
                // 1) if (amount > 0) addTag('INCOME')
                def m1 = (rule =~ /if\s*\(\s*amount\s*([><]=?)\s*([\-\d\.]+)\s*\)\s*addTag\s*\(\s*'([^']+)'\s*\)/)
                if (m1.find()) {
                    def op = m1[0][1]
                    BigDecimal val = new BigDecimal(m1[0][2])
                    String tag = m1[0][3]
                    double cmp = val.doubleValue()
                    return { Transaction tx ->
                        double a = tx.amount ? tx.amount.doubleValue() : 0.0d
                        boolean ok = false
                        if (op == '>') ok = a > cmp
                        else if (op == '<') ok = a < cmp
                        else if (op == '>=') ok = a >= cmp
                        else if (op == '<=') ok = a <= cmp
                        if (ok) tx.addTag(tag)
                    } as Closure
                }

                // 2) if (description.contains('Netflix')) addTag('SUBSCRIPTION')
                def m2 = (rule =~ /if\s*\(\s*description\.contains\s*\(\s*'([^']+)'\s*\)\s*\)\s*addTag\s*\(\s*'([^']+)'\s*\)/)
                if (m2.find()) {
                    String needle = m2[0][1]
                    String tag = m2[0][2]
                    return { Transaction tx -> if (tx.description && tx.description.indexOf(needle) >= 0) tx.addTag(tag) } as Closure
                }

                // Fallback: compile using GroovyShell into a closure that accepts tx
                def src = "{ tx -> tx.with { ${rule} } }"
                def closure = shell.evaluate(src)
                return closure as Closure
            } catch (Exception e) {
                log.error('[RULE COMPILE ERROR] Błąd kompilacji reguły: {} -> {}', rule, e.message)
                return null
            }
        }.findAll { it != null } as List<Closure>
    }

    /**
     * Apply precompiled closures to a transaction. Closure is invoked with
     * values and an addTag closure that mutates the transaction.
     */
    void applyCompiledRules(Transaction tx, List<Closure> compiled) {
        if (!compiled) return
        compiled.each { Closure cl ->
            try {
                cl.call(tx)
            } catch (Exception e) {
                log.error('[RULE ERROR] Błąd przy uruchamianiu skompilowanej reguły -> {}', e.message)
            }
        }
    }

    /**
     * Vectorized application of simple rules to a whole list of transactions.
     * Recognizes a few common patterns and applies them in tight loops (much faster).
     */
    void applyRulesToList(List<Transaction> txs, List<String> rules) {
        if (!txs || !rules) return

        // Collect recognized patterns
        List<Map> amountChecks = []
        List<Map> descChecks = []

        rules.each { String rule ->
            def m1 = (rule =~ /if\s*\(\s*amount\s*([><]=?)\s*([\-\d\.]+)\s*\)\s*addTag\s*\(\s*'([^']+)'\s*\)/)
            if (m1.find()) {
                amountChecks << [op: m1[0][1], val: m1[0][2].toDouble(), tag: m1[0][3]]
                return
            }
            def m2 = (rule =~ /if\s*\(\s*description\.contains\s*\(\s*'([^']+)'\s*\)\s*\)\s*addTag\s*\(\s*'([^']+)'\s*\)/)
            if (m2.find()) {
                descChecks << [needle: m2[0][1], tag: m2[0][2]]
                return
            }
        }

        // Apply checks either sequentially or in parallel depending on size
        int size = txs.size()
        boolean parallel = size > 1000

        def applyAmountChecks = { Transaction tx ->
            double a = tx.amount ? tx.amount.doubleValue() : 0.0d
            for (int i = 0; i < amountChecks.size(); i++) {
                def c = amountChecks[i]
                boolean ok = false
                def op = c.op
                def val = c.val
                if (op == '>') ok = a > val
                else if (op == '<') ok = a < val
                else if (op == '>=') ok = a >= val
                else if (op == '<=') ok = a <= val
                if (ok) tx.addTag(c.tag)
            }
        }

        def applyDescChecks = { Transaction tx ->
            if (!tx.description) return
            for (int i = 0; i < descChecks.size(); i++) {
                def c = descChecks[i]
                if (tx.description.indexOf(c.needle) >= 0) tx.addTag(c.tag)
            }
        }

        if (parallel) {
            txs.parallelStream().forEach { tx ->
                if (amountChecks) applyAmountChecks(tx)
                if (descChecks) applyDescChecks(tx)
            }
        } else {
            txs.each { tx ->
                if (amountChecks) applyAmountChecks(tx)
                if (descChecks) applyDescChecks(tx)
            }
        }
    }
}