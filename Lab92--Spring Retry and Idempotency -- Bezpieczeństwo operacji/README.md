Lab92
-----

Lab92--Spring Retry and Idempotency -- Bezpieczeństwo operacji
==============================================================

Cel labu
--------

Wdrożenie dwóch fundamentalnych mechanizmów bezpieczeństwa dla systemów finansowych:

1. **Spring Retry** — automatyczne ponawianie operacji przy błędach przejściowych (fluktuacje sieci, API timeout).
2. **Idempotency** — gwarancja, że operacja wykonana 2x daje ten sam (biznesowy) wynik, co wykonana 1x.

Razem tworzą elastyczną, odporną aplikację gotową na produkcję (i rozmowy o pracę na poziomie Mid/Senior).

---

Część A: Spring Retry
---------------------

### Cel
Gdy API walutowe (lub inne zależności) czasem zawiedzie, powtórz próbę zamiast rzucać błędem od razu.

### Implementacja

#### 1. Zależności (`build.gradle`)
```groovy
implementation 'org.springframework.retry:spring-retry'
implementation 'org.springframework:spring-aspects'
```

#### 2. Globalna konfiguracja (`SmartFinDbApp.groovy`)
```groovy
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableScheduling
@EnableRetry  // <-- NOWE
class SmartFinDbApp { ... }
```

#### 3. Retry na metodzie — `CurrencyService.getExchangeRate(...)`
```groovy
@Retryable(
    retryFor = [Exception.class],
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000)
)
@Cacheable("exchangeRates")
@CircuitBreaker(name = "currencyApi", fallbackMethod = "fallbackRate")
BigDecimal getExchangeRate(String fromCurrency) {
    // Wykonywana do 3x z przerwą 1s między próbami
    // Jeśli wszystkie fail → Circuit Breaker uruchamia fallbackRate() → zwraca 4.0
}
```

### Jak to działa
- **Próba 1**: request do API walutowego
- **Fail**: rzuci wyjątek
- **Próba 2**: czeka 1s, retry
- **Fail**: rzuci wyjątek
- **Próba 3**: czeka 1s, retry
- **Fail**: Circuit Breaker otwiera się → `fallbackRate()` zwraca 4.0 PLN (safe default)

Logika: API ma zazwyczaj ~100ms outage, a my dajemy mu 3 sekundy — prawie zawsze przejdzie.

---

Część B: Idempotency
--------------------

### Problem
Użytkownik kliknie „wyślij transakcję" dwa razy szybko.
- **Bez idempotency**: 2 identyczne zduplikowane transakcje → rachunek zawyżony.
- **Z idempotency**: 2 requesty → 1 rekord w bazie (to gwarancja finansowa).

### Implementacja 3-warstwowa

#### 1. Aplikacyjna — check w `TransactionService.createTransaction(...)`
```groovy
@Transactional
TransactionDto createTransaction(TransactionDto dto) {
    String currentUser = userContextService.getCurrentUsername()

    // Sprawdzenie: czy user ma już tę transakcję (po business ID)?
    def existing = repo.findByOriginalIdAndOwnerUsername(dto.id, currentUser)
    if (existing.isPresent()) {
        log.warn(">>> [IDEMPOTENCY] Transakcja {} już istnieje. Zwracam istniejącą.", dto.id)
        return mapToDto(existing.get())  // Bez duplikatu!
    }

    // Loga: reguły, waluta, zapis
    // ... reszta logiki ...
    
    def saved = repo.saveAndFlush(entity)
    return mapToDto(saved)
}
```

#### 2. Repository — szybki lookup (`TransactionRepository.groovy`)
```groovy
Optional<TransactionEntity> findByOriginalIdAndOwnerUsername(String originalId, String ownerUsername)
```

Spring Data generuje SQL na bazie nazwy metody.

#### 3. Bazodanowa — twarda gwarancja (`V17__add_unique_owner_original_id.sql`)
```sql
CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_owner_original_active
    ON transactions(owner_username, original_id)
    WHERE deleted = false
      AND owner_username IS NOT NULL
      AND original_id IS NOT NULL;
```

- Zabrania duplikatów aktywnych rekordów (partial index szanuje soft-delete).
- Jeśli 2 wątki równoczesnie spróbują wstawić duplikat → BD rzuci `DataIntegrityViolationException`.

#### 4. Obsługa race condition — kontroler (`TransactionController.addTransaction(...)`)
```groovy
@PostMapping
TransactionDto addTransaction(@Valid @RequestBody TransactionDto dto) {
    try {
        return transactionService.createTransaction(dto)
    } catch (DataIntegrityViolationException ex) {
        // Drugi wątek wygrał wyścig. Szukamy już w nowym kontekście transakcyjnym.
        def existing = transactionService.getMyTransactionByOriginalId(dto.id)
        if (existing != null) {
            log.warn(">>> [IDEMPOTENCY-RACE] Race condition handled. Returning existing.")
            return existing
        }
        throw ex
    }
}
```

Ilu warstwowy jest check:
1. **Aplikacja (pre-check)** — 90% przypadków (double submit z UI).
2. **DB constraint** — ostatnia obrona (race condition między wątkami).
3. **Kontroler (post-conflict)** — zwrot istniejącego rekordu zamiast 500.

---

Jak to przetestowaliśmy
-----------------------

### Test 1: Podstawowy idempotency (`IdempotencySpec.groovy`)
```groovy
def "powinien zapisac transakcje tylko raz przy podwojnym wyslaniu"() {
    // Wysyłamy 2x POST tego samego payloadu
    // ...
    // Sprawdzenie: w bazie jest dokładnie 1 rekord dla użytkownika
}
```

### Test 2: Race condition (`IdempotencyRaceSpec.groovy`)
```groovy
def "powinien zapisac tylko jeden rekord przy dwoch rownoleglych POST"() {
    // 2 wątki -> CountDownLatch -> oba POST jednoczesnie
    // Oba powinny zwrócić 2xx (idempotentnie)
    // W bazie: 1 rekord
}
```

### Uruchomienie testów
```powershell
# Tylko idempotency
.\gradlew.bat test --tests "pl.edu.praktyki.web.IdempotencySpec" -i

# Race condition
.\gradlew.bat test --tests "pl.edu.praktyki.web.IdempotencyRaceSpec" -i

# Oba razem
.\gradlew.bat test --tests "pl.edu.praktyki.web.Idempotency*" -i
```

---

Migracja Flyway
---------------

### V17__add_unique_owner_original_id.sql
1. **Dedup historycznych danych** (CTE + ROW_NUMBER): zostawiamy najstarszy rekord, resztę soft-delete.
2. **Twójz unique index** na aktywnych rekordach (`deleted = false`).

Dzięki temu migracja przechodzi nawet na bazie z istniejącymi duplikatami.

---

Dlaczego to działa (technicznie)
--------------------------------

**Retry:**
- Jest timeout: próbuj jeszcze raz.
- Brak timeout, ale API zwraca 500: spróbuj jeszcze raz.
- Po 3 próbach fail: Circuit Breaker → fallback.
- Aplikacja żyje, zamiast się wysypać.

**Idempotency:**
- Pre-check w aplikacji (szybko) → `SELECT ... WHERE (owner_username, original_id) = (...)`
- DB constraint (ostateczna obrona) → jedno wstawienie wygra, drugie dostanie konflikt.
- Post-conflict handler (zwrot istniejącego) → użytkownik dostaje 201 Created zamiast 500.

**Razem:**
- Retry chroni przed błędami **przejściowymi**.
- Idempotency chroni przed duplikatami **biznesowymi**.
- To są **ortogonalne problemy** — oba są potrzebne w produkcji.

---

Portfolio value
---------------

Na rozmowie możesz powiedzieć:

> „Wdrożyliśmy Spring Retry do obsługi błędów przejściowych API (3 próby, backoff 1s, Circuit Breaker z fallbackiem). Dla duplikatów dodaliśmy idempotency: pre-check w aplikacji, unique index w DB na (owner_username, original_id), i obsługę race condition w kontrolerze (zwrot istniejącego rekordu poza transakcją). Wynik: system odporny na fluktuacje sieci i double-submit. Przetestowaliśmy równoległe requesty — działa."

To pokazuje:
- ✅ Rozumienie dwóch różnych problemów.
- ✅ Łączenie aplikacyjnych i bazodanowych rozwiązań.
- ✅ Świadomość race conditions.
- ✅ Testing w wielowątkowości.
- ✅ Gotowość do produkcji.

---

Podsumowanie Lab92
------------------

Lab92 to przejście od „aplikacji, która czasem pada" do „aplikacji, która jest elastyczna i bezpieczna".

Kluczowe koncepty:
- Spring Retry + Circuit Breaker = resilience.
- Idempotency (aplikacyjna + bazodanowa) = data integrity.
- Race condition handling = production-ready.

Następny etap: monitoring, metryki, SLA — ale to już Log Aggregation i APM (Lab 93+).
