# Lab97 - Symulacja Systemów: Ingress / Processing / Egress - part 2

## Cel tej części
W tej części domknęliśmy **Ingress + Processing** o trwałą idempotencję w bazie danych.

Aktualny zakres obejmuje:
- przyjęcie requestu HTTP,
- analizę i decyzję biznesową,
- ochronę przed duplikatami przez `correlationId` (persisted idempotency),
- zwrot `TransactionDecision` do klienta.

> Na tym etapie **nie ma jeszcze dedykowanej warstwy Egress Listener** (to będzie kolejny krok / part-3).

---

## Aktualna architektura (stan "as-is")

```text
[HTTP Request] -> Orchestrator -> [IdempotencyDB] -> return TransactionDecision
                                            ^
                                       (koniec: brak egress listener)
```

### Co to oznacza praktycznie
- Request trafia do `TransactionAnalysisOrchestrator`.
- Jeśli `correlationId` jest nowe -> liczona jest decyzja i zapisywany jest rekord idempotency.
- Jeśli `correlationId` już istnieje -> zwracana jest poprzednio utrwalona decyzja.
- API zwraca `TransactionDecision`.
- Brak osobnego etapu "po decyzji" (np. event/listener/audit-log egress).

---

## Co zostało zrealizowane

- **Trwała idempotencja w DB**:
  - tabela: `idempotency_keys`,
  - unikalność po `correlation_id`,
  - odczyt decyzji przy retry.
- **Obsługa race condition** dla równoległych requestów z tym samym `correlationId`.
- **Scenariusze testowe** obejmujące:
  - replay tego samego requestu,
  - wyścig 2 równoległych requestów,
  - test stress (wiele równoległych requestów z tym samym kluczem).

---

## Co jest jeszcze poza zakresem part-2

Tego **świadomie jeszcze nie robimy** w tej części:
- publikacji eventu "decision produced" po stronie egress,
- listenera zapisującego dedykowany `decision_log`,
- metryk egress typu `egress.decisions.count`.

To będzie naturalny temat na part-3 (pełne domknięcie Ingress / Processing / Egress).

---

## WAŻNE dla rekrutera

### 1) Co kandydat już potrafi po part-2
- Umie zaprojektować idempotencję **trwałą**, a nie tylko in-memory.
- Rozumie różnicę między "liczeniem decyzji" a "odtwarzaniem decyzji".
- Potrafi testować współbieżność i race conditions (nie tylko happy-path).
- Potrafi wskazać granice systemu i jawnie powiedzieć, czego jeszcze brakuje.

### 2) Jak to opowiedzieć w 30-60 sekund
"W part-2 zrobiliśmy persisted idempotency opartą o `correlationId`. Ten sam request nie jest liczony drugi raz - zwracamy utrwaloną decyzję, także przy równoległych wywołaniach. Mamy testy replay, race i stress. Świadomie zakończyliśmy flow na `return TransactionDecision`; osobny Egress Listener i decision log to kolejny etap." 

### 3) Słowa-klucze techniczne (na rozmowę)
- idempotency key,
- unique constraint,
- race condition,
- read-after-write consistency,
- deterministic replay,
- testy współbieżne (latch + thread pool).

### 4) Uczciwe ograniczenia (plus za dojrzałość)
- Brak jeszcze dedykowanego egress event/listener.
- Brak osobnej tabeli auditowej decyzji egress.
- Brak metryk egress per typ decyzji/replay.

---

## Szybkie uruchomienie testów (tc + Flyway)

```powershell
.\gradlew.bat "-Dspring.profiles.active=tc" "-Denable.flyway=true" clean test --no-daemon
```

Możesz też uruchomić pojedynczy spec idempotency, aby szybciej weryfikować zmiany.
