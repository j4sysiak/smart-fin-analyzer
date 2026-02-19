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
        def tx = new Transaction(id: "T1", amount:  1500.0, category: "Dom", description: "Czynsz za luty")
        def rules = [
                "if (amount < -1000) addTag('HIGH_PRIORITY')",
                "if (category == 'Dom') addTag('HOUSING')",
                "if (description.contains('luty')) addTag('MONTHLY')",
                "if (amount > 0) addTag('INCOME')"
        ]

        when: "aplikujemy reguły"
        ruleService.applyRules(tx, rules)

        then: "transakcja powinna mieć odpowiednie tagi"
        tx.tags.size() == 3
        // tx.tags.containsAll(['HIGH_PRIORITY', 'HOUSING', 'MONTHLY', 'INCOME'])
        tx.tags.containsAll(['INCOME', 'HOUSING', 'MONTHLY'])
    }
}