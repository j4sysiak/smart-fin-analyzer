package pl.edu.praktyki.strategy.second

interface TransactionStrategy {
    boolean supports(String type)   // Czy ta klasa obsługuje ten typ?
    void execute(BigDecimal amount)  // Logika biznesowa dla tego typu
}