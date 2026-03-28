# Skrypty pomocnicze - smart-fin-analyzer

Ten katalog zawiera pomocnicze skrypty i zasoby do lokalnego testowania aplikacji Smart-Fin-Analyzer.

Pliki:
- `add-sample-transaction.ps1` - PowerShell helper, pobiera JWT z `/auth/token` i wysyła przykładową transakcję do `/api/transactions`, a następnie pobiera listę transakcji.
- `postman_collection.json` - gotowa kolekcja Postman (Get JWT Token, Add Transaction, List Transactions).

Wymagania
- Aplikacja uruchomiona lokalnie: `http://localhost:8080` (domyślnie).
- PowerShell (Windows) lub alternatywnie użyj Postmana.

Szybkie użycie (PowerShell)

1) Uruchom aplikację (jeśli chcesz uniknąć problemów z plikową bazą H2 użyj pamięciowej):

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:h2:mem:devdb'; $env:SPRING_H2_CONSOLE_ENABLED='true'; .\gradlew.bat bootRun
```

2) Uruchom skrypt (jednorazowo z omijaniem polityki uruchamiania):

```powershell
# domyślnie używa user=dev
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\add-sample-transaction.ps1
```

3) Przykładowo: utwórz transakcję która będzie "BIG_SPENDER" (ujemna kwota):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\add-sample-transaction.ps1 -MakeBigSpender
```

4) Parametry skryptu (opcjonalne):
- `-ApiHost` (domyślnie `http://localhost:8080`)
- `-UserName` (domyślnie `dev`)
- `-TransactionId` (domyślnie `TX-PS-001`)
- `-Amount` (domyślnie `150.00`)
- `-MakeBigSpender` (switch) — jeśli ustawiony, kwota stanie się ujemna (wydatek) i do `TransactionId` dopisze się `-BIG`.

Przykład z dodatkowymi parametrami:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\add-sample-transaction.ps1 -UserName admin -TransactionId "TX-100" -Amount 300 -MakeBigSpender
```

Postman — import kolekcji

1) Importuj plik `scripts/postman_collection.json` w Postmanie (File → Import).
2) Utwórz Environment (np. `local`) i ustaw `host` na `http://localhost:8080` oraz (opcjonalnie) `user`.
3) Otwórz request `Get JWT Token` i kliknij Send — skrypt testowy zapisze token do zmiennej środowiskowej `jwt`.
4) Otwórz `Add Transaction` i kliknij Send (używa nagłówka `Authorization: Bearer {{jwt}}`).

Troubleshooting
- Jeśli PowerShell blokuje uruchamianie skryptu: uruchom jednorazowo z `-ExecutionPolicy Bypass` (patrz wyżej) lub ustaw politykę:
  ```powershell
  Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser -Force
  ```
- Jeśli skrypt zwraca "Application not reachable": upewnij się, że aplikacja nasłuchuje na `localhost:8080` i że `bootRun` uruchomił poprawnie Flyway/JPA.
- Jeśli widzisz błąd o zablokowanym pliku H2 (`smartfin_file.mv.db`): zamknij inne procesy Javy lub uruchom aplikację w pamięciowej bazie H2 (polecenie powyżej) albo usuń plik bazy (po zatrzymaniu aplikacji).

Pliki i lokalizacje
- Skrypt: `scripts/add-sample-transaction.ps1`
- Kolekcja Postman: `scripts/postman_collection.json`

Masz pomysł na rozszerzenie (np. bash/curl script lub zapis wyników do pliku)? Napisz — dodam to.

