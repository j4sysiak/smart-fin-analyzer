# Lab99 - Symulacja Systemów: Ingress / Processing / Egress  -  part 4
## Outbox Pattern i Retry Logic dla niezawodności Egress

---

## Cel tej części

W Part-3 zbudowaliśmy pełny flow **Ingress → Processing → Egress** z:
- idempotencją persisted w `idempotency_keys`,
- eventami i listenerem egress,
- audit trail w `decision_log`,
- metrykami Micrometer.

W Part-4 **chronimy tę architekturę przed awariami** przez wprowadzenie **Outbox Pattern**:
- buforowanie egress eventów w kolejce (`egress_outbox`),
- asynchroniczny scheduler + processor z retry logic,
- gwarancja że żaden event nie będzie zgubiony,
- graceful degradation przy awariach DB / network.

---

## Problem: Dlaczego Part-3 sam nie wystarczy?

### Scenario 1: DB crash podczas zapisu do `decision_log`

**Bez Outbox (Part-3):**
```
[HTTP Request] → Orchestrator → publish event → Listener → write to decision_log ❌ DB connection lost
                                                                       ↓
                                                        Event ZNIKA bez śladu 💥
                                                        Klient dostał 200 OK
                                                        Ale brak audit trail
```

**Z Outbox (Part-4):**
```
[HTTP Request] → Orchestrator → publish event → Listener → Enqueue to outbox ✅ (szybko, lokalnie)
                                                                    ↓
                                          SaveAndFlush outbox (w tej samej txn)
                                                                    ↓
                                    [Dispatcher — co 2 sekundy]
                                                                    ↓
                                    Retry dostarczenia via Processor
                                                                    ↓
                                    Gdy DB wróci → delivery success ✅
```

### Scenario 2: Network timeout / slow DB

**Bez Outbox:**
- Listener czeka aż `decision_log` się zapyta.
- Timeout 30s → connection pool saturates.
- Nowe requesty hang.
- System się sypie.

**Z Outbox:**
- Listener zapisuje do outbox **błyskawicznie** (local batch insert).
- Request kończy się w 10ms.
- Scheduler co 2s chodzi sobie i bezpiecznie retry.
- System pozostaje responsywny.

### Scenario 3: Overload — 1000 concurrent requests

**Bez Outbox:**
- 1000 listenerów konkuruje o connection pool.
- DB się sypie z timeout.
- Część eventów tracona.

**Z Outbox:**
- 1000 eventów trafia do outbox table (batch insert).
- Scheduler bierze batch po 50 = 20 małych porcji.
- DB ma czasu na recovery.
- Żaden event się nie traci.

---

## Problem → Rozwiązanie (tabela)

| Problem | Bez Outbox | Z Outbox |
|---------|-----------|----------|
| DB crash podczas `decision_log` write | ❌ Event zgubiony | ✅ Outbox buforuje, retry po powrocie |
| Network timeout | ❌ Listener padnie, request hang | ✅ Dispatcher czeka i retry, request szybko wraca |
| Overload DB (1000 concurrent) | ❌ Timeout, część eventów gutem | ✅ Uśmierza load, batch po 50 |
| "Dowód że decision został dostarczony" | ❌ Brak audytu (RAM) | ✅ Outbox table = append-only audit |
| "Co się nie dostarczyło?" | ❌ Nieznane | ✅ Status=DEAD → alarm dla ops |
| Idempotentne delivery przy retry | ❌ Może być duplikat | ✅ Unique constraint na `decision_log.correlation_id` |

---

## Architektura Part-4

```text
[HTTP Request]
        ↓
[Orchestrator] (1 txn)
        ├─ compute decision
        ├─ save to idempotency_keys
        ├─ publish TransactionDecisionEvent
        └─ ✅ return 200 OK (szybko!)
        
        ↓ (event dispatch, asynchronicznie w Part-3)
        
[TransactionDecisionListener]
        ├─ log event
        ├─ increment metric egress.decisions.count
        ├─ if replay=true → skip
        └─ if replay=false → call outboxPublisher.enqueue()

[EgressOutboxPublisher] (1 txn — KRYTYCZNE!)
        ├─ create EgressOutboxEntity (status=NEW)
        ├─ serialize payload to JSON
        └─ save to egress_outbox table ✅

        ↓ (scheduler co 2 sekundy — INNY THREAD)
        
[EgressOutboxDispatcher]
        ├─ query "status IN (NEW, RETRY) AND nextAttemptAt <= NOW()"
        ├─ lock batch with PESSIMISTIC_WRITE (race-safe)
        └─ update status=PROCESSING, attemptCount++

[EgressOutboxProcessor] (dla każdego rekordu, REQUIRES_NEW txn)
        ├─ parse payload
        ├─ call deliveryService.deliver(payload)
        │   └─ check unique constraint decision_log.correlation_id
        │   └─ if exists → skip (idempotent)
        │   └─ if not → save DecisionLogEntity
        ├─ on success → status=SENT, processedAt=NOW()
        └─ on error:
            └─ if attemptCount >= maxAttempts → status=DEAD, log alarm
            └─ else → status=RETRY, nextAttemptAt = NOW() + exponential_backoff

[Metryki]
        egress.outbox.enqueued.count          ← ile eventów trafiło do outbox
        egress.outbox.dispatch.success.count  ← ile się pomyślnie dostarczyło
        egress.outbox.dispatch.retry.count    ← ile razy retry
        egress.outbox.dispatch.dead.count     ← ile poszło do Dead Letter Queue
        egress.outbox.delivery.duplicate.skip ← ile duplikatów pominięto (idempotencja)
```

---

## Kluczowe koncepty

### 1. Outbox Pattern
Event nie ide bezpośrednio do słuchacza. Zamiast tego:
- publikujesz do local outbox table (zaraz, w tej samej txn co biznes),
- asynchronicznie pusher (scheduler) dostarczá downstream.
- gwarantuje że publication zawsze się zapisze.

### 2. Pessimistic Locking
```sql
SELECT * FROM egress_outbox 
WHERE status IN ('NEW', 'RETRY') 
  AND nextAttemptAt <= NOW() 
FOR UPDATE WAIT 5;
```
- `FOR UPDATE` = tablica jest zablokowana, nikt inny nie może czytać/pisać.
- Gwarantuje że dwie instancje Dispatchera nie wezmą tego samego rekordu.
- Race-safe batch claiming.

### 3. Exponential Backoff
Delay między próbami: `2^n` (w ms)
- 1. próba fails: wait 2s
- 2. próba fails: wait 4s
- 3. próba fails: wait 8s
- ... aż do 7 prób max.
- Po 7: status=DEAD.

### 4. Idempotent Delivery
```sql
CREATE UNIQUE INDEX uk_decision_log_correlation_id 
  ON decision_log (correlation_id);
```
- Nawet jeśli retry dostarczy ten sam event 2x, unique constraint zabroni duplikatu.
- Drugi insert → ignore (już istnieje).

---

## Które tabele i klasy w Part-4?

### Nowe tabele (Flyway)
- `V20__create_egress_outbox.sql` — tablica outbox z indeksami
- `V21__add_unique_decision_log_correlation.sql` (opcjonalne, ale polecam) — unique constraint

### Nowe klasy (pakiet `egress.outbox`)
- `EgressOutboxStatus` — enum (NEW, PROCESSING, RETRY, SENT, DEAD)
- `DecisionEgressPayload` — DTO dla payload JSON
- `EgressOutboxEntity` — JPA entity
- `EgressOutboxRepository` — repo z custom queries
- `EgressOutboxPublisher` — enqueue event do outbox
- `EgressDecisionDeliveryService` — idempotent delivery do decision_log
- `EgressOutboxProcessor` — przetwarzanie einzelnych eventów (retry logic)
- `EgressOutboxDispatcher` — scheduler pobierający batch i puszczający Processor

### Klasy do modyfikacji
- `TransactionDecisionListener` — zamiast na wprost pisać do decision_log, teraz enqueue do outbox

---

## Koncept "Exactly Once" (idempotent delivery)

Prawdziwa `exactly-once` gwarancja wymaga:
1. **Persistent outbox** (event nie znika) ✅ — `egress_outbox` table
2. **Idempotent processing** (drugi attempt nie duplikuje) ✅ — unique constraint `decision_log.correlation_id`
3. **Atomic commit** (outbox + decision_log albo nic) — `@Transactional`

Gwarancja tego to cała sztuka, którą pokazujemy w Part-4.

---

## Konfiguracja (application.properties)

```properties
# Outbox pattern configuration
app.egress.outbox.enabled=true
app.egress.outbox.poll-ms=2000
app.egress.outbox.batch-size=50
app.egress.outbox.max-attempts=7
app.egress.outbox.base-delay-ms=2000
```

---

## Analogia z rzeczywistością

### Bez Outbox = Kurier bez potwierdzenia

```
Ty (HTTP Client):
  "Wyślij paczkę"
        ↓ (synchronicznie)
  Kurier (Listener):
  "Idę do domu i dostarczam paczkę"
        ↓
  ❌ Kurier upadł na schodach, paczka zgubiona
        ↓
  Ty: "Co z paczkką?"
  Kurier: "??? Nie wiem, system padł"
  Ty: "AAAAAA" (brak dowodu)
```

### Z Outbox = Kurier z rejestrem (DHL, UPS style)

```
Ty (HTTP Client):
  "Wyślij paczkę"
        ↓ (szybko!)
  Kurier (Listener):
  "Wpiszę do notesika i wraca" ✅ (10ms)
        ↓
  Ty: "OK, paczkę masz w notesiku"
  Ty: ✅ wrócę do pracy (nie czekam)
        ↓ (co 2 sekundy — system z domu)
  
  [Dispatcher @ home]
  "Sprawdzę notatnik"
        ↓
  [Processor] "Dostarczę paczkę"
        ↓
  ✅ Success? → notatnik: "SENT"
  ❌ Fail?    → notatnik: "RETRY, spróbuję za 4s"
  ❌ 7x fail? → notatnik: "DEAD, powiadomić szefa"
        ↓
  Zawsze: Szef wie dokładnie co się stało (notatnik = outbox table)
```

---

## Cel edukacyjny Part-4

Po Part-4 zakamieniasz:

### Technicznie
- Outbox Pattern (enterprise standard — Kafka, EventBridge, SQS all use it)
- Pessimistic locking do race-safe batch operations
- Exponential backoff retry strategy
- Idempotent processing (crucial dla systems tolerant to failures)
- Dead Letter Queue pattern

### Biznesowo
- Różnica między synchronicznym a asynchronicznym delivery
- Trade-off: latency vs. reliability
- Why "eventually consistent" is better than "availability disaster"

### Na rozmowie zarobkowej
**Candy na temat Part-4:**
- "Zrobiłem Outbox Pattern dla guaranteed delivery eventów egress"
- "Używam pessimistic lock do batch claiming → race-safe"
- "Exponential backoff retry → graceful degradation"
- "Unique constraint na decision_log → gwarantuje exactly-once"
- "Dead Letter Queue → alertuję ops kiedy coś chronically fails"

= **Candidate know how robić production-grade systems** ✅

---

## Definition of Done (DoD) — Part-4

- [x] Migracja V20 (`egress_outbox`) i V21 (unique constraint) wykonują się.
- [x] Nowe klasy w `egress.outbox` skompilują się bez błędów.
- [x] `TransactionDecisionListener` enqueue do outbox zamiast na wprost pisać.
- [x] `EgressOutboxDispatcher` uruchamia się co 2s i pobiera batch.
- [x] `EgressOutboxProcessor` retry logic pracuje: `NEW` → `PROCESSING` → `SENT/RETRY/DEAD`.
- [x] Metryki `egress.outbox.*` są dostępne w `GET /actuator/metrics/`.
- [ ] Test: `replay=false` → 1 rekord w outbox, `replay=true` → brak outbox.
- [ ] Test: error na dostarczeniu → `RETRY`, delay rośnie, potem `DEAD`.
- [ ] Test: drugi attempt tego samego `correlationId` nie duplikuje `decision_log`.
- [ ] Dead Letter Queue (`status=DEAD`) jest widoczna i alertowana.
- [x] README part-4 opisuje Outbox Pattern i retry logic.

---

## WAŻNE dla rekrutera

### Co pokazuje Part-4 jako feature
- Wiedza o enterprise patterns (Outbox, DLQ, idempotency).
- Umiejętność projektowania **fault-tolerant** systemów.
- Race conditions, locks, transactions.
- Observability (metryki per step).

### Jak opowiedzieć to w 30-60 sekund
"Po part-3 mieliśmy pełny Ingress/Processing/Egress, ale egress był synchroniczny — ryzyko utraty eventu przy awarii DB. W part-4 dodałem Outbox Pattern: eventy trafiają do local queue, scheduler co 2s dostarczá z exponential backoff retry. Używam pessimistic lock do batch claiming (race-safe), unique constraints do idempotencji, i retry aż 7 razy zanim pójdzie do Dead Letter Queue. Dzięki temu gwarantuję exactly-once delivery nawet przy awariach."

= **Candidate knows production-grade fault-tolerant design** ✅

---

## Szybkie uruchomienie (ciąg dalszy Part-3)

### Wszystkie testy (`tc` + Flyway)

```powershell
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" clean test --no-daemon
```

### Pojedyncze testy Part-4 (po wdrożeniu)

```powershell
# Outbox enqueue behavior (replay vs new decision)
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" test --tests "pl.edu.praktyki.contract.egress.outbox.EgressOutboxEnqueueSpec" --no-daemon

# Retry logic + exponential backoff
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" test --tests "pl.edu.praktyki.contract.egress.outbox.EgressOutboxRetrySpec" --no-daemon

# Dead Letter Queue
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" test --tests "pl.edu.praktyki.contract.egress.outbox.EgressOutboxDeadLetterSpec" --no-daemon

# Idempotent delivery
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" test --tests "pl.edu.praktyki.contract.egress.outbox.EgressOutboxIdempotentDeliverySpec" --no-daemon
```

---

## Runbook: Ręczna inspekcja Outbox (smoke test)

### 1) Stan outbox table

```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_db -c "
SELECT id, event_id, status, attempt_count, next_attempt_at, last_error 
FROM egress_outbox 
ORDER BY created_at DESC 
LIMIT 10;"
```

Oczekiwanie:
- `status` w przedziale: NEW → PROCESSING → SENT lub RETRY lub DEAD
- `attempt_count` rósnie przy każdym retry
- `next_attempt_at` przesunięty w przyszłość (exponential backoff)

### 2) Porównanie outbox vs decision_log

```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_db -c "
SELECT 
  'outbox' AS source, COUNT(*) AS cnt FROM egress_outbox WHERE status = 'SENT'
UNION ALL
SELECT 
  'decision_log', COUNT(*) FROM decision_log"
```

Oczekiwanie:
- `decision_log` count ≤ `outbox SENT` count (mogą być nowe rekordy w outbox czekające na dostarczenie)

### 3) Dead Letter Queue (co się nie powiodło?)

```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_db -c "
SELECT id, event_id, correlation_id, attempt_count, last_error 
FROM egress_outbox 
WHERE status = 'DEAD' 
ORDER BY updated_at DESC;"
```

Oczekiwanie:
- Jeśli lista pusta: wszystko dostarczane OK ✅
- Jeśli są rekordy: alertować ops/support

### 4) Metryki Outbox (jeśli aplikacja działa)

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/egress.outbox.enqueued.count" | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/egress.outbox.dispatch.success.count" | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/egress.outbox.dispatch.dead.count" | ConvertTo-Json
```

---

## Smoke Test Part-4 — wyniki z produkcji (2026-05-25)

Poniżej dokładne wyjaśnienie co się stało podczas ręcznego smoke testu na żywej aplikacji.
Każdy krok jest powiązany z konkretną klasą i logiką w kodzie.

---

### Kontekst startowy

Aplikacja uruchomiona przez:
```powershell
.\gradlew.bat runSmartFinDb -PappArgs="-u Jacek"
```

Baza danych PostgreSQL w Dockerze — zdrowa (`status: UP`).
Migracje Flyway: V1–V21 wszystkie z `success=true`:
```
V20 | V20__create_egress_outbox.sql                | t
V21 | V21__add_unique_decision_log_correlation.sql | t
```

---

### Krok 1 — Pobranie JWT token

```powershell
$token = (Invoke-RestMethod -Uri "http://localhost:8080/api/auth/token?user=Jacek" -Method Get).token
```

**Co się dzieje:**
- `GET /api/auth/token?user=Jacek` generuje JWT token dla użytkownika `Jacek`.
- Token jest ważny przez określony czas (zwykle kilka minut) — przy teście aplikacji **zawsze pobieram świeży token tuż przed requestem**.
- Token jest później przekazywany w nagłówku `Authorization: Bearer <token>` przy każdym requestzie do API.

**Dlaczego token wygasa i co zrobić:**
- Przy dłuższej przerwie między komendami token wygaśnie i dostaniesz `403 Forbidden`.
- Rozwiązanie: zawsze pobierać token bezpośrednio przed użyciem (`$token = ...`).

---

### Krok 2 — Wysłanie POST /api/transactions/analyze

```powershell
$corrId = "CORR-SMOKE-20260525180103-2026052518010303"
$txId   = "TX-SMOKE-18010304"

$body = @{
  transactionId = $txId
  accountId     = "ACC-SMOKE-001"
  correlationId = $corrId
  timestamp     = "2026-05-25T16:01:03.123Z"   # ISO 8601 UTC
  amount        = 100.00
  payload       = @{}
} | ConvertTo-Json

$decision = Invoke-RestMethod -Uri "http://localhost:8080/api/transactions/analyze" `
  -Method Post `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body $body
```

**Odpowiedź serwera:**
```json
{
  "transactionId": "TX-SMOKE-18010304",
  "correlationId": "CORR-SMOKE-2026052518010303",
  "decision":      "ACCEPT",
  "reason":        "Status OK - transaction accepted",
  "decidedAt":     "2026-05-25T16:01:03.526596Z"
}
```

**Co się dzieje wewnątrz w kolejności:**

```
POST /api/transactions/analyze
      ↓
TransactionAnalysisController.analyze()
      ↓
TransactionAnalysisOrchestrator.process()
      ├─ normalize correlationId
      ├─ findByCorrelationId() → Optional.empty() (nowy request)
      ├─ computeDecision() → analyzer.analyze() → status=OK
      ├─ decisionPolicy.decide()  → decision="ACCEPT"
      ├─ idempotencyKeyRepository.saveAndFlush()  → zapis do idempotency_keys
      └─ eventPublisher.publishEvent(TransactionDecisionEvent(decision, replay=false))
            ↓
TransactionDecisionListener.onDecision()
      ├─ log.info("EGRESS | decision=ACCEPT | replay=false")
      ├─ meterRegistry.counter("egress.decisions.count").increment()
      │      tagi: decision=ACCEPT, replay=false
      ├─ replay=false → NIE skipujemy
      └─ outboxPublisher.enqueue(event)
            ↓
EgressOutboxPublisher.enqueue()   @Transactional
      ├─ tworzymy EgressOutboxEntity(
      │       eventId=    randomUUID,
      │       eventType=  "TransactionDecisionEvent",
      │       transactionId= "TX-SMOKE-18010304",
      │       correlationId= "CORR-SMOKE-2026052518010303",
      │       payloadJson=   JSON z decyzją,
      │       status=        NEW,
      │       nextAttemptAt= Instant.now()
      │   )
      └─ outboxRepository.save(entity)   → rekord trafia do egress_outbox z status=NEW
```

**Wynik: HTTP 200 OK, decyzja ACCEPT. Rekord w `egress_outbox` status=NEW.**

---

### Krok 3 — Scheduler: NEW → PROCESSING → SENT

Po zwróceniu HTTP 200 — w **osobnym wątku**, **co 2 sekundy** — działa scheduler `EgressOutboxDispatcher`.

**`EgressOutboxDispatcher.dispatch()` (poll-ms=2000):**

```
[scheduler thread]
EgressOutboxDispatcher.dispatch()
      ├─ enabled=true → kontynuujemy
      └─ transactionTemplate.execute { claimBatchInTx() }
              ↓
         claimBatchInTx()
              ├─ outboxRepository.lockBatchForDispatch(
              │       statuses=[NEW, RETRY],
              │       now=Instant.now(),
              │       pageable=PageRequest(0, 50)
              │   )
              │   → zapytanie SQL z FOR UPDATE (pessimistic lock)
              │   → zwraca listę EgressOutboxEntity ze status=NEW
              ├─ dla każdego rekordu:
              │       row.status = PROCESSING
              │       row.attemptCount = 0 + 1 = 1
              └─ zwraca listę id=[1]
      ↓
ids = [1]
ids.each { id -> processor.process(id) }
```

**Dlaczego `TransactionTemplate` a nie `@Transactional`?**
Dispatcher jest `@Scheduled` — wywołanie z `@Scheduled` omija Spring proxy,
więc `@Transactional` na metodzie publicznej nie działałoby.
`TransactionTemplate.execute { ... }` **bezpośrednio** otwiera transakcję. Pewnie i czytelnie.

---

**`EgressOutboxProcessor.process(id=1)` (@Transactional REQUIRES_NEW):**

```
EgressOutboxProcessor.process(1)
      ├─ outboxRepository.findById(1)  → present
      ├─ row.status == PROCESSING  → kontynuujemy
      ├─ payload = objectMapper.readValue(row.payloadJson, DecisionEgressPayload)
      └─ deliveryService.deliver(payload)
              ↓
         EgressDecisionDeliveryService.deliver()  @Transactional(MANDATORY)
              ├─ decisionLogRepository.existsByCorrelationId("CORR-SMOKE-...") → false (nowy)
              ├─ decisionLogRepository.save(DecisionLogEntity(
              │       transactionId= "TX-SMOKE-18010304",
              │       correlationId= "CORR-SMOKE-2026052518010303",
              │       decision=      "ACCEPT",
              │       reason=        "Status OK - transaction accepted",
              │       decidedAt=     Instant
              │   ))
              └─ meterRegistry.counter("egress.outbox.delivery.success.count",
                     "decision", "ACCEPT").increment()
      ↓
row.status = SENT
row.processedAt = Instant.now()
row.lastError = null
meterRegistry.counter("egress.outbox.dispatch.success.count").increment()
```

**Wynik: rekord w `egress_outbox` zmieniony na SENT. Nowy wiersz w `decision_log`.**

---

### Krok 4 — Weryfikacja w bazie danych

Zapytanie uruchomione ~4 sekundy po analyze (po jednym cyklu schedulera):

```powershell
docker exec smartfin-postgres psql -U finuser -d smartfin_db `
  -c "SELECT id, correlation_id, status, attempt_count, processed_at, last_error FROM egress_outbox ORDER BY id DESC LIMIT 5;"
```

**Wynik:**
```
 id |       correlation_id        | status | attempt_count |         processed_at          | last_error
----+-----------------------------+--------+---------------+-------------------------------+------------
  1 | CORR-SMOKE-2026052518010303 | SENT   |             1 | 2026-05-25 16:01:04.547541+00 |
(1 row)
```

**Interpretacja kolumn:**
| Kolumna | Wartość | Znaczenie |
|---------|---------|-----------|
| `status` | `SENT` | Outbox dostarczony bez błędów — happy path |
| `attempt_count` | `1` | Udało się za pierwszym razem |
| `processed_at` | `16:01:04` | Czas dostarczenia (request był o 16:01:03 — scheduler zadziałał w ~1 sek) |
| `last_error` | `NULL` | Brak błędów |

---

```powershell
docker exec smartfin-postgres psql -U finuser -d smartfin_db `
  -c "SELECT id, correlation_id, decision, logged_at FROM decision_log ORDER BY id DESC LIMIT 5;"
```

**Wynik:**
```
 id |       correlation_id        | decision |         logged_at
----+-----------------------------+----------+----------------------------
  2 | CORR-SMOKE-2026052518010303 | ACCEPT   | 2026-05-25 16:01:04.53473
  1 | CORR-MANUAL-AAAAAAAAAA      | ACCEPT   | 2026-05-25 14:16:05.137965
(2 rows)
```

**Interpretacja:**
- `id=2` — nasz smoke test: decyzja `ACCEPT` wpisana przez `EgressDecisionDeliveryService`.
- `id=1` — poprzedni ręczny test z innego uruchomienia.
- Czas `logged_at` pokrywa się z `processed_at` w outbox (taka sama sekunda) — delivery jest atomowe.

---

### Krok 5 — Weryfikacja metryk Actuator

```powershell
Invoke-RestMethod "http://localhost:8080/actuator/metrics/egress.decisions.count" | ConvertTo-Json -Depth 6
```

**Wynik:**
```json
{
  "name": "egress.decisions.count",
  "measurements": [{ "statistic": "COUNT", "value": 1.0 }],
  "availableTags": [
    { "tag": "decision", "values": ["ACCEPT"] },
    { "tag": "replay",   "values": ["false"] }
  ]
}
```
> `1.0` — jedna decyzja, nie replay, typ ACCEPT.

---

```powershell
Invoke-RestMethod "http://localhost:8080/actuator/metrics/egress.outbox.enqueued.count" | ConvertTo-Json
```
```json
{ "name": "egress.outbox.enqueued.count", "measurements": [{ "statistic": "COUNT", "value": 1.0 }] }
```
> Listener wrzucił 1 event do outbox.

---

```powershell
Invoke-RestMethod "http://localhost:8080/actuator/metrics/egress.outbox.dispatch.success.count" | ConvertTo-Json
```
```json
{ "name": "egress.outbox.dispatch.success.count", "measurements": [{ "statistic": "COUNT", "value": 1.0 }] }
```
> Dispatcher przetworzył 1 event z sukcesem.

---

```powershell
Invoke-RestMethod "http://localhost:8080/actuator/metrics/egress.outbox.delivery.success.count" | ConvertTo-Json -Depth 6
```
```json
{
  "name": "egress.outbox.delivery.success.count",
  "measurements": [{ "statistic": "COUNT", "value": 1.0 }],
  "availableTags": [{ "tag": "decision", "values": ["ACCEPT"] }]
}
```
> `DeliveryService` zapisał 1 rekord do `decision_log`.

---

### Pełne podsumowanie — tabela metryk

| Metryka | Wartość | Co mierzy |
|---------|---------|-----------|
| `egress.decisions.count` (decision=ACCEPT, replay=false) | **1.0** | Nowe decyzje zaakceptowane |
| `egress.outbox.enqueued.count` | **1.0** | Ile eventów trafiło do outbox |
| `egress.outbox.dispatch.success.count` | **1.0** | Ile outbox recordów dostarczono |
| `egress.outbox.delivery.success.count` (decision=ACCEPT) | **1.0** | Ile rekordów zapisano do `decision_log` |
| `egress.outbox.dispatch.retry.count` | 0 | Brak retry — happy path |
| `egress.outbox.dispatch.dead.count` | 0 | Brak Dead Letter |

Wszystkie liczniki = 1 i żadnych błędów → **pełny happy-path flow** działa.

---

### Wizualny timeline całego flow

```
T+0.000s  POST /api/transactions/analyze
          └─ Orchestrator: compute ACCEPT, save idempotency_key
          └─ Listener: log + metric(decisions) + enqueue → egress_outbox[status=NEW]
          └─ HTTP 200 OK do klienta ← koniec synchronicznej части

T+2.000s  [EgressOutboxDispatcher — scheduler tick]
          └─ claimBatchInTx(): NEW → PROCESSING, attemptCount=1

T+2.001s  [EgressOutboxProcessor.process(id=1)]
          └─ deliveryService.deliver():
             └─ decision_log: INSERT (correlationId nie istnieje → nowy rekord)
             └─ metric: delivery.success.count++
          └─ egress_outbox[status=SENT, processedAt=now]
          └─ metric: dispatch.success.count++

T+4.000s  DB query: egress_outbox → status=SENT ✅
          DB query: decision_log  → ACCEPT zapis ✅
          Actuator: wszystkie liczniki = 1 ✅
```

---

### Co by się stało gdyby delivery się nie powiodło?

Gdyby `EgressDecisionDeliveryService.deliver()` rzucił wyjątek (np. brak połączenia z DB):

```
EgressOutboxProcessor.process(id=1)
      └─ deliveryService.deliver() → throws Exception
      └─ row.lastError = "Connection refused"
      └─ attemptCount (1) < maxAttempts (7) → 
          row.status = RETRY
          row.nextAttemptAt = NOW() + 2^1 * 2000ms = NOW() + 4s
          metric: dispatch.retry.count++

[4 sekundy później — następny tick schedulera]
      └─ claimBatch pobiera RETRY z nextAttemptAt <= NOW()
      └─ attemptCount = 2, PROCESSING → ...
      
[przy 7. nieudanej próbie]
      └─ attemptCount (7) >= maxAttempts (7) →
          row.status = DEAD
          metric: dispatch.dead.count++
          log.error("EGRESS-OUTBOX | DEAD ...")   ← alert dla ops
```

Timeline opóźnień (exponential backoff, base-delay=2000ms):
| Próba | Opóźnienie przed retry |
|-------|----------------------|
| 1 | 2s |
| 2 | 4s |
| 3 | 8s |
| 4 | 16s |
| 5 | 32s |
| 6 | 64s |
| 7 → DEAD | — |

---

## Następne kroki

1. **Wdrożenie Part-4** — kod 1:1 do wklejenia.
2. **Testy Part-4** — 4 szablony Spec do napisania.
3. **Integracja z Part-3** — upewnij się że eventy trafiają do outbox.
4. **Produkcja** — dodaj monitoring DLQ + alerting na status=DEAD.

---

## Podsumowanie

**Part-4 uczy Cię jak robić niezawodne, fault-tolerant, production-grade systemy event-driven.**

Bez Outbox = hazard, ryzyko utraty eventu.
Z Outbox = gwarancja, observability, graceful degradation.

To nie jest overkill — to standard w każdym duże systemie (Amazon SQS, Google PubSub, Kafka, RabbitMQ wszyscy używają tego pod spodem).

**Go build it. 💪**

