# Lab98 - Symulacja Systemów: Ingress / Processing / Egress - part 3

## Cel tej części
W tej części domykamy pełny przepływ **Ingress / Processing / Egress** przez dodanie warstwy wyjściowej (Egress).

Po part-2 mieliśmy:
- request HTTP,
- analizę i decyzję,
- persisted idempotency,
- zwrot `TransactionDecision` do klienta.

W part-3 dokładamy etap "po decyzji":
- publikację eventu decyzji,
- listener egress,
- zapis do dedykowanego logu decyzji,
- metryki egress.

---

## Architektura docelowa (part-3)

```text
[HTTP Request]
      -> Orchestrator
      -> [IdempotencyDB]
      -> publish TransactionDecisionEvent
      -> Egress Listener
         -> decision_log (DB)
         -> metrics (Micrometer)
         -> structured logs
      -> return TransactionDecision
```

### Intencja architektoniczna
- **Ingress**: przyjęcie danych wejściowych.
- **Processing**: analiza + polityka decyzji + idempotency.
- **Egress**: uporządkowane efekty uboczne po podjęciu decyzji (event, audit trail, metryki).

---

## Zakres prac w part-3

1. **Event domenowy decyzji**
   - nowa klasa np. `TransactionDecisionEvent`,
   - pola: `decision`, `replay`, `occurredAt`.

2. **Publikacja eventu w Orchestratorze**
   - po wyliczeniu nowej decyzji (`replay=false`),
   - po odtworzeniu decyzji z idempotency (`replay=true`).

3. **Listener Egress**
   - `@EventListener` odbiera `TransactionDecisionEvent`,
   - loguje wynik,
   - zapisuje rekord do `decision_log` tylko dla nowych decyzji,
   - podbija metrykę egress z tagami (`decision`, `replay`).

4. **Trwały audit trail Egress w DB**
   - nowa tabela `decision_log` (Flyway),
   - encja + repozytorium,
   - indeksy pod `correlation_id` i `decision`.

5. **Testy integracyjne Egress**
   - nowa decyzja -> 1 rekord w `decision_log`,
   - replay -> brak duplikacji `decision_log`,
   - zgodność wartości z `TransactionDecision`.

---

## Kontrakt zachowania (ważne)

- Ten sam `correlationId` nadal oznacza idempotentny wynik w `idempotency_keys`.
- Egress nie psuje idempotency:
  - replay nie tworzy nowej decyzji biznesowej,
  - replay nie powinien duplikować audytu "new decision".
- Pipeline pozostaje deterministyczny: ten sam klucz -> ten sam efekt decyzji.

---

## Definition of Done (DoD)

- [x] Event decyzji istnieje i jest publikowany w Orchestratorze.
- [x] Listener Egress działa i jest objęty testami.
- [x] Migracja Flyway dla `decision_log` wykonuje się poprawnie.
- [x] Dla replay (`replay=true`) `decision_log` nie rośnie.
- [x] **Metryki egress są widoczne i inkrementują się poprawnie.**
- [x] **Test race Egress potwierdza brak duplikatów przy równoczesnych requestach.**
- [x] **Runbook smoke operacyjny opisuje manualną weryfikację.**
- [x] README part-3 opisuje końcowy przepływ end-to-end.

---

## WAŻNE dla rekrutera

### Co pokazuje part-3
- Kandydat umie rozdzielić odpowiedzialności: ingress/process/egress.
- Potrafi dodać **event-driven egress** bez łamania idempotency.
- Potrafi projektować audit trail i metryki operacyjne.
- Rozumie różnicę między wynikiem biznesowym a skutkami ubocznymi systemu.

### Jak opowiedzieć to w 30-60 sekund
"Po part-2 mieliśmy stabilne processing + idempotency. W part-3 domknęliśmy Egress: Orchestrator publikuje event decyzji, listener zapisuje audit trail do `decision_log` i wystawia metryki. Replay requestu nie tworzy duplikatu efektów ubocznych. Dzięki temu pipeline jest kompletny: Ingress -> Processing -> Egress." 

### Słowa-klucze na rozmowę
- separation of concerns,
- event-driven egress,
- outflow observability,
- audit trail,
- idempotent side effects,
- operational metrics.

---

## Szybkie uruchomienie testów (tc + Flyway)

```powershell
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" clean test --no-daemon
```

Dla szybszej iteracji uruchamiaj pojedynczy spec egress/idempotency, a pełny run zostaw na koniec.


---

## Runbook: Smoke operacyjny Egress (ręczna weryfikacja w DB + Actuator)

Po uruchomieniu testów lub aplikacji możesz ręcznie sprawdzić stan Egress warstwy.

### 1) Sprawdzić `decision_log` w bazie danych

```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_db -c "SELECT transaction_id, correlation_id, decision, reason, logged_at FROM decision_log ORDER BY logged_at DESC LIMIT 10;"
```

Oczekiwany wynik:
- `(transaction_id, correlation_id)` unikalne lub odpowiadające replay,
- `decision` to jeden z: `ACCEPT`, `ACCEPT_WITH_WARNING`, `REJECT`,
- `logged_at` pokazuje timestamp ostatniego egress logu.

### 2) Porównać `idempotency_keys` z `decision_log`

Dla tego samego `correlation_id`:
- `idempotency_keys` powinno mieć dokładnie 1 rekord,
- `decision_log` powinno mieć dokładnie 1 rekord (bez duplikatów z replay).

```powershell
docker exec -it smartfin-postgres psql -U finuser -d smartfin_db -c "
SELECT 
  correlation_id,
  (SELECT COUNT(*) FROM idempotency_keys WHERE correlation_id = decision_log.correlation_id) AS idem_count,
  (SELECT COUNT(*) FROM decision_log d2 WHERE d2.correlation_id = decision_log.correlation_id) AS decision_count
FROM decision_log
GROUP BY correlation_id
ORDER BY correlation_id DESC
LIMIT 5;"
```

### 3) Metryki Egress w Actuator (jeśli aplikacja działa)

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/egress.decisions.count" | ConvertTo-Json
```

Odpowiedź pokaże:
- nazwę metryki: `egress.decisions.count`,
- tagi: `decision=ACCEPT|ACCEPT_WITH_WARNING|REJECT`, `replay=true|false`,
- wartości liczników dla każdej kombinacji.

Oczekiwanie:
- co najmniej jeden tag z `replay=false` (nowe decyzje),
- jeśli robisz test replay, tag `replay=true` ma wartość `> 0`.

### 4) Szybkie testowanie ręczne (PowerShell + JWT)

Jeśli aplikacja działa na `localhost:8080`:

```powershell
# 1. Pobierz token
$token = (Invoke-RestMethod -Uri "http://localhost:8080/api/auth/token?user=testuser" -Method Get).token

# 2. Wyślij request analizy decyzji
$decision = Invoke-RestMethod -Uri "http://localhost:8080/api/transactions/analyze" `
  -Method Post `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body (@{
    transactionId = "TEST-$(Get-Random)"
    accountId = "ACC-MANUAL"
    correlationId = "CORR-MANUAL-$(Get-Random)"
    timestamp = (Get-Date -AsUTC).ToString("o")
    amount = 100.00
    payload = @{}
  } | ConvertTo-Json)

$decision

# 3. Sprawdź metryki
Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/egress.decisions.count" | ConvertTo-Json
```
