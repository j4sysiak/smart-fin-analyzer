ruleset {
    // Importujemy standardowe zestawy reguł
    ruleset('rulesets/basic.xml')
    ruleset('rulesets/exceptions.xml')
    ruleset('rulesets/imports.xml')
    ruleset('rulesets/naming.xml')
    ruleset('rulesets/unused.xml')

    // Zasady formatowania (np. brak średników!)
    ruleset('rulesets/formatting.xml') {
        // Możemy wyłączyć konkretne reguły, które nas denerwują
        // Np. LineLength - wyłączamy limit długości linii
        'LineLength' {
            enabled = false
        }
    }
}