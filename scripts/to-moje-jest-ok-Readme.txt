1.
# pokaż uruchomione procesy Java (sprawdź PID)
Get-Process -Name java -ErrorAction SilentlyContinue | Format-Table Id,ProcessName,StartTime

# jeśli znajdziesz proces(y) powiązane z Twoją aplikacją, zatrzymaj je (wstaw właściwe PID)
Stop-Process -Id <PID> -Force


2.
uruchomienie aplikacji (PowerShell):

Jak uruchomić u Ciebie (prosto)
Upewnij się, że aplikacja działa:
uruchom bootRun z in-memory H2:

$env:SPRING_DATASOURCE_URL='jdbc:h2:mem:devdb'; $env:SPRING_H2_CONSOLE_ENABLED='true'; .\gradlew.bat bootRun


3. (opcjonialnie - ale wywalisz bazę i dane)
Jeśli pojawi się błąd o zablokowanym pliku — zatrzymaj wszystkie instancje Javy (Get-Process -Name java) i spróbuj ponownie. Jeżeli chcesz usunąć plik DB (czysty start):
Remove-Item -Path .\db\smartfin_file.mv.db -Force -ErrorAction SilentlyContinue
Remove-Item -Path .\db\smartfin_file.trace.db -Force -ErrorAction SilentlyContinue



4a.
jak uruchomić ten skrypt (dodanie transakcji do bazy):
POST na http://localhost:8080/api/transactions

powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\add-sample-transaction.ps1

sprawdzenie:
http://localhost:8080/h2-console/

jdbc:h2:mem:devdb;
user: sa
password: 


4b.
postman

https://web.postman.co/workspace/My-Workspace~f2aa92ac-63c8-420c-80c0-23d0d71ea517/collection/5972111-1831650e-83dc-4776-a5f8-4780294b7091?action=share&source=copy-link&creator=5972111