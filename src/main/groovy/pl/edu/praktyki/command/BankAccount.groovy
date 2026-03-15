package pl.edu.praktyki.command

// 1. RECEIVER: Głupi obiekt, który tylko wykonuje proste instrukcje
class BankAccount {
    String accountId
    BigDecimal balance = 0.0

    void add(BigDecimal amount) { balance += amount }
    void subtract(BigDecimal amount) { balance -= amount }
}

