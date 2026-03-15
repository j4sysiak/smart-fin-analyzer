package pl.edu.praktyki.command

class CommandManager {
// Historia wykonanych operacji (działa jak Stos / Stack)
    private List<BankCommand> history =[]

    // Wykonuje polecenie i zapisuje je w historii, by móc je później cofnąć
    void executeCommand(BankCommand command) {
        command.execute()
        history << command // dodajemy na koniec listy
    }

    // Cofa ostatnio wykonane polecenie
    void undoLast() {
        if (history.isEmpty()) {
            println "<<< [BŁĄD] Brak operacji do cofnięcia!"
            return
        }

        BankCommand lastCommand = history.removeLast() // <-- TO JEST KLUCZ (bierze/usuwa z listy ostatnia operację)!
        lastCommand.undo()
    }
}