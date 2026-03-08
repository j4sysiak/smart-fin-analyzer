package pl.edu.praktyki.fraud

import pl.edu.praktyki.domain.Transaction

class FraudDetector {

    // Lista naszych reguł (To jest nasz 'Łańcuch Zobowiązań')
    private final List<FraudRule> rules =[
            new AmountFraudRule(),
            new NightTimeFraudRule()
            // tu można dodać kolejne reguły, np. na podstawie kraju, historii klienta, itp.
    ]

    // Pozwala na dodawanie nowych reguł w locie (np. w testach)
    void addRule(FraudRule rule) {
        rules << rule
    }

    /**
     * Przepuszcza transakcję przez łańcuch.
     * Zwraca komunikat błędu (pierwszy napotkany) lub null, jeśli transakcja jest OK.
     */
    String detectFraud(Transaction tx) {

        // Groovy Magic: findResult iteruje po regułach.
        // Jeśli rule.check(tx) zwróci Stringa (nie null), findResult natychmiast
        // kończy pętlę i zwraca ten tekst. Jeśli przejdzie wszystko i wszędzie będzie null,
        // zwróci null (czyli brak Fraudu).
        return rules.findResult { rule ->
            rule.check(tx)
        }
    }
}