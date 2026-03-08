package pl.edu.praktyki.strategy

interface OrderStrategy {
    boolean supports(String type) // Czy ta klasa obsługuje ten typ?
    void process(BigDecimal amount) // Logika biznesowa dla tego typu
}