package pl.edu.praktyki.command

import groovy.transform.Canonical

@Canonical
class DepositCommand implements BankCommand {
    BankAccount account
    BigDecimal amount

    @Override
    void execute() {
        println ">>>[WYKONUJĘ] Wpłata $amount na konto ${account.accountId}"
        account.add(amount)  // Wpłata dodaje!
    }

    @Override
    void undo() {
        println "<<< [COFAM] Anulowanie wpłaty $amount z konta ${account.accountId}"
        account.subtract(amount) // Cofnięciem wpłaty jest wypłata!
    }
}