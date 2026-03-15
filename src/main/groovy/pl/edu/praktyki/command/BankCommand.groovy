package pl.edu.praktyki.command

// 2. INTERFEJS POLECENIA: Każde polecenie musi umieć się wykonać i... COFNĄĆ!
interface BankCommand {
    void execute()
    void undo()
}