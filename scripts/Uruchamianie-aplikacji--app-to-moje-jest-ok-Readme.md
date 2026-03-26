Aplikacja:
C:\dev\smart-fin-analyzer\src\main\groovy\pl\edu\praktyki\SmartFinDbApp.groovy
--------------------------------------------------------------------------------
Baza danych aplikacji (SmartFinDbApp): H2 (w pamięci)
Do testów i rozwoju - baza Postgres (Docker)
----------------------------------------------------------------------------


1.
Uruchamianie aplikacji SmartFinDbApp:
ale najpierw pokaż uruchomione procesy Java (sprawdź PID)
(PowerShell)
Get-Process -Name java -ErrorAction SilentlyContinue | Format-Table Id,ProcessName,StartTime

# jeśli znajdziesz proces(y) powiązane z Twoją aplikacją, zatrzymaj je (wstaw właściwe PID)
(PowerShell)
Stop-Process -Id <PID> -Force


2.
uruchomienie aplikacji:
(PowerShell)
cd C:\dev\smart-fin-analyzer
$env:SPRING_DATASOURCE_URL='jdbc:h2:mem:devdb'; $env:SPRING_H2_CONSOLE_ENABLED='true'; .\gradlew.bat bootRun

Opcjonalnie:
Jeśli pojawi się błąd o zablokowanym pliku — zatrzymaj wszystkie instancje Javy (Get-Process -Name java) i spróbuj ponownie. Jeżeli chcesz usunąć plik DB (czysty start):
Opcjonialnie - ale UWAGA wywalisz zawartość bazy i wszystkie dane oczywiście znikną (H2 w pamięci):

(PowerShell)
Remove-Item -Path .\db\smartfin_file.mv.db -Force -ErrorAction SilentlyContinue
Remove-Item -Path .\db\smartfin_file.trace.db -Force -ErrorAction SilentlyContinue



-----------------------------------------------------------------------
-----------------   sprawdzenie działania aplikacji   -----------------
-----------------------------------------------------------------------

4a.
dodanie transakcji do bazy - tylko H2  (Postgres na Doker tylko do testów ./gradlew clean test):
------------------------------------------------------------------------------------------------
(PowerShell)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\add-sample-transaction.ps1

sprawdzenie:
http://localhost:8080/h2-console/

jdbc:h2:mem:devdb;
user: sa
password: 

4b.
Swagger UI:
-----------
http://localhost:8080/swagger-ui/index.html


4c.
Postman:
--------
HOWTO: `C:\dev\smart-fin-analyzer\scripts\Readme-z-Postmana.md`

tu jest mój Postman (web):
https://web.postman.co/workspace/My-Workspace~f2aa92ac-63c8-420c-80c0-23d0d71ea517/collection/5972111-1831650e-83dc-4776-a5f8-4780294b7091?action=share&source=copy-link&creator=5972111