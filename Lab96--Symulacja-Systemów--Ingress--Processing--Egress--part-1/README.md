# Lab96 - Symulacja Systemów: Ingress / Processing / Egress - part 1

## Cel laba
W tym labie porządkujemy pełny przepływ danych w aplikacji:
- **Ingress** - wejście danych do systemu,
- **Processing** - walidacja, obróbka i reguły biznesowe,
- **Egress** - zapis, publikacja efektów ubocznych i odpowiedzi na zewnątrz.

Ta część jest bazą pod dalsze kroki z idempotencją, odpornością na duplikaty i stabilnym flow end-to-end.

---

## Co zrobiliśmy do tej pory

### 1. Uporządkowaliśmy uruchamianie aplikacji i testów
Przygotowana została instrukcja dla uruchamiania projektu na PostgreSQL oraz dla testów integracyjnych:
- `scripts/README--Uruchamianie-aplikacji-i-testów.md`

Najważniejsze ustalenia:
- cały projekt pracuje na **PostgreSQL**,
- produkcja i tryb developerski korzystają z tej samej bazy,
- testy mają osobny, powtarzalny tryb uruchamiania,
- schemat bazy jest kontrolowany przez **Flyway**.

### 2. Przygotowaliśmy REST-y i scenariusze wysyłania danych
Opisaliśmy sposób wywoływania endpointów z Postmana oraz z CLI:
- `scripts/README--Odpalanie-RESTow-z-Postmana.md`

To obejmuje:
- wysyłanie requestów `POST` z plikami,
- pracę z tokenem JWT,
- alternatywę `curl.exe` / `Invoke-RestMethod`,
- podstawową diagnostykę, gdy request nie dochodzi poprawnie.

### 3. Uporządkowaliśmy monitoring i diagnostykę JVM
Przygotowana została instrukcja podpinania aplikacji do VisualVM:
- `scripts/README--podpinanie-VisualVM.md`

W dokumentacji opisaliśmy:
- podpięcie lokalne,
- podpięcie przez JMX bez restartu aplikacji,
- diagnozę problemu, gdy proces działa, ale nie pojawia się w VisualVM,
- podstawy analizy dumpów w MAT.

### 4. Ujednoliciliśmy bazy danych do PostgreSQL
Opis migracji z H2 / mieszanych ustawień do jednego spójnego środowiska:
- `scripts/README--Ujednolicenie-baz-danych-na-produkcji-i-testach-do-Postgresa---wywalenie-H2.md`

Najważniejsze decyzje:
- produkcja działa na PostgreSQL,
- testy też korzystają z PostgreSQL,
- zrezygnowaliśmy z H2,
- testy oparliśmy o stabilny mechanizm uruchamiania bazy przez Docker.

### 5. Zebraliśmy pomocnicze skrypty i artefakty
W katalogu `scripts/` trzymamy dodatkowe pliki wspierające pracę nad labem:
- `clean-db.ps1`
- `add-sample-transaction.ps1`
- `init-db.sql`
- `run-integration-tests-with-flyway.ps1`
- `postman_collection.json`

To są narzędzia pomocnicze do:
- czyszczenia bazy,
- przygotowania danych testowych,
- inicjalizacji środowiska,
- uruchamiania testów,
- odpalania gotowych requestów API.

---

## Aktualny stan

- [x] Dokumentacja uruchamiania aplikacji i testów jest gotowa.
- [x] Dokumentacja REST / Postman jest gotowa.
- [x] Dokumentacja VisualVM / JMX jest gotowa.
- [x] Baza danych została ujednolicona do PostgreSQL.
- [x] Mamy zestaw skryptów pomocniczych do pracy operacyjnej.
- [ ] Następny etap: **Krok 7.1** - trwała idempotencja w DB (`entity + repo + flow + test`).

---

## Następny krok

W part-2 przechodzimy do wariantu idempotencji persystentnej:
- tabela `idempotency_keys` w bazie,
- encja i repozytorium,
- flow sprawdzający duplikaty po restarcie aplikacji,
- test potwierdzający, że mechanizm działa nie tylko in-memory.

To będzie kolejny krok w kierunku stabilnego systemu **Ingress / Processing / Egress**.
