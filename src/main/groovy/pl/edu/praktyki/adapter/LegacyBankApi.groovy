package pl.edu.praktyki.adapter

// 2. STARE, BRZYDKIE API BANKU (Nie możemy go modyfikować!)
class LegacyBankApi {

    // Inna nazwa metody, inne typy parametrów (double zamiast BigDecimal)
    boolean makeOldTransfer(String targetAccount, double cash) {
        println "[LEGACY BANK] Wysyłam ${cash} do ${targetAccount}..."
        return true
    }

    String checkBankHealth() {
        return "OPERATIONAL"
    }
}