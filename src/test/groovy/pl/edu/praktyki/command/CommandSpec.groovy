package pl.edu.praktyki.command

import spock.lang.Specification

class CommandSpec extends Specification {

    def "powinien poprawnie wykonywać i cofać operacje bankowe"() {
        given: "konto początkowe i manager poleceń"
        def account = new BankAccount(accountId: "PL-999", balance: 1000.0)
        def manager = new CommandManager()

        when: "wykonujemy dwie operacje: wpłata 500 i wypłata 200"
        manager.executeCommand(new DepositCommand(account, 500.0))
        manager.executeCommand(new WithdrawCommand(account, 200.0))

        then: "bilans to 1300 (1000 + 500 - 200)"
        account.balance == 1300.0

        when: "cofamy OSTATNIĄ operację (czyli wypłatę 200)"
        manager.undoLast()

        then: "bilans wraca do 1500 (1000 + 500)"
        account.balance == 1500.0

        when: "cofamy KOLEJNĄ operację (czyli wpłatę 500)"
        manager.undoLast()

        then: "bilans wraca dokładnie do stanu początkowego!"
        account.balance == 1000.0

        and: "historia poleceń jest teraz pusta"
        manager.history.isEmpty()
    }
}