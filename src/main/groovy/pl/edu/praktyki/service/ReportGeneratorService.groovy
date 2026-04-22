package pl.edu.praktyki.service

import org.springframework.stereotype.Service
import groovy.text.SimpleTemplateEngine

@Service
class ReportGeneratorService {

    private final engine = new SimpleTemplateEngine()

    /**
     * Generuje raport tekstowy/HTML na podstawie danych analitycznych.
     */
    String generateMonthlyReport(String userName, Map<String, Object> stats) {
        def templateText = '''
            =========================================
            RAPORT FINANSOWY DLA: ${user.toUpperCase()}
            =========================================
            Data wygenerowania: ${reportDate}
            
            PODSUMOWANIE:
            -----------------------------------------
            Bilans całkowity:  ${totalBalance} PLN
            Główny wydatek:    ${topCategory}
            
            WYDATKI WG KATEGORII:
            <% spendingMap.each { category, amount -> %>
            - ${(category ?: 'N/A').padRight(15)} : ${((amount ?: 0) as BigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP)} PLN
            <% } %>
            -----------------------------------------
            Status: ${totalBalance >= 0 ? 'NA PLUSIE' : 'DEFICYT'}
            =========================================
        '''.stripIndent()

        def binding = [
                user: userName,
                reportDate: java.time.LocalDate.now(),
                totalBalance: stats.totalBalance,
                topCategory: stats.topCategory,
                spendingMap: stats.spendingMap
        ]

        return engine.createTemplate(templateText)
                .make(binding)
                .toString()
    }
}