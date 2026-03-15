package pl.edu.praktyki.command

import groovy.transform.Canonical

@Canonical
class WithdrawCommand implements BankCommand {
    BankAccount account
    BigDecimal amount

    @Override
    void execute() {
        println ">>> [WYKONUJĘ] Wypłata $amount z konta ${account.accountId}"
        account.subtract(amount) // Wypłata odejmuje!
    }

    @Override
    void undo() {
        println "<<< [COFAM] Anulowanie wypłaty $amount na konto ${account.accountId}"
        account.add(amount) // Cofnięciem wypłaty jest wpłata!
    }
}