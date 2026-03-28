tu siedzi aplikacja (SmartFinDbApp):
C:\dev\smart-fin-analyzer\src\main\groovy\pl\edu\praktyki\SmartFinDbApp.groovy

-----------------------------------------------------------------------
Baza danych dla aplikacji produkcyjnej (SmartFinDbApp): H2 (w pamięci)
Baza danych dla testów i rozwoju - Postgres (Docker)
-----------------------------------------------------------------------



Krok-1: Produkcja: odpalenie aplikacji SmartFinDbApp z H2 w pamięci (domyślnie)
-------------------------------------------------------------------------------

1 sposób: (wprost z Gradle z parametrami):
------------------------------------------
`./gradlew runSmartFinDb -PappArgs="-u Jacek -f transakcje.csv"`

informacje o postępie uruchamiania aplikacji (logi Gradle):
powinnieneś zobaczyć logi podobne do poniższych, co oznacza, że aplikacja uruchamia się z H2 w pamięci:
80% EXECUTING [39s] - jest to info, że app już się uruchomila.

<==========---> 80% EXECUTING [39s]
> :runSmartFinDb



2 sposób: (ręcznie ustawiając zmienne środowiskowe i uruchamiając bootRun):
---------------------------------------------------------------------------
Ubicie wszystkich procesów Javy (jeśli są uruchomione)
(PowerShell)
`Get-Process -Name java -ErrorAction SilentlyContinue | Format-Table Id,ProcessName,StartTime`

jeśli znajdziesz proces(y) powiązane z Twoją aplikacją, zatrzymaj je (wstaw właściwe PID)
(PowerShell)
`Stop-Process -Id <PID> -Force`


przyklad:
---------
```text
PS C:\dev\smart-fin-analyzer> Get-Process -Name java -ErrorAction SilentlyContinue | Format-Table Id,ProcessName,StartTime

Id ProcessName StartTime
   -- ----------- ---------
33532 java        28.03.2026 11:34:38
39800 java        28.03.2026 11:11:06


PS C:\dev\smart-fin-analyzer> Stop-Process -Id 33532 -Force
PS C:\dev\smart-fin-analyzer> Stop-Process -Id 39800 -Force
PS C:\dev\smart-fin-analyzer> Get-Process -Name java -ErrorAction SilentlyContinue | Format-Table Id,ProcessName,StartTime
Id ProcessName StartTime
   -- ----------- ---------
```


Uruchomienie aplikacji:
(PowerShell)
cd C:\dev\smart-fin-analyzer
`$env:SPRING_DATASOURCE_URL='jdbc:h2:mem:devdb'; $env:SPRING_H2_CONSOLE_ENABLED='true'; .\gradlew.bat bootRun`

!Opcjonalnie!:
Jeśli pojawi się błąd o zablokowanym pliku — zatrzymaj wszystkie instancje Javy (Get-Process -Name java) i spróbuj ponownie. Jeżeli chcesz usunąć plik DB (czysty start):
Opcjonialnie - ale UWAGA wywalisz zawartość bazy i wszystkie dane oczywiście znikną (H2 w pamięci):

(PowerShell)
`Remove-Item -Path .\db\smartfin_file.mv.db -Force -ErrorAction SilentlyContinue`
`Remove-Item -Path .\db\smartfin_file.trace.db -Force -ErrorAction SilentlyContinue`




krok-2. Testy dodanie transakcji do bazy - H2:
----------------------------------------------
(Postgres na Docker tylko do testów ./gradlew clean test) - tu nie używamy Postgresa, tylko H2 w pamięci, więc nie ma problemu z plikową bazą danych.
 
(PowerShell)
`cd C:\dev\smart-fin-analyzer`
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\add-sample-transaction.ps1`

```text
 []: # Przykładowy output z uruchomienia skryptu PowerShell, który dodaje transakcję do bazy H2 i pobiera listę transakcji:
             },
    "first":  true,
    "numberOfElements":  20,
    "empty":  false
}
Done.
```

sprawdzenie:
http://localhost:8080/h2-console/

`Driver class :` org.h2.Driver
`JDBC URL :`     jdbc:h2:file:./db/smartfin_file;DB_CLOSE_DELAY=-1
`user:`          sa
`password:`      (puste) 



krok-3:  Swagger UI:
--------------------
tu są wszystkie endpointy RESTowe, które możesz testować ręcznie:
(GET /api/transactions, POST /api/transactions itd.):
http://localhost:8080/swagger-ui/index.html


krok-4:  Postman:
-----------------
HOWTO: `C:\dev\smart-fin-analyzer\scripts\Readme--Odpalanie-RESTów-z-Postmana.md`

a tu jest mój Postman (web):
https://web.postman.co/workspace/My-Workspace~f2aa92ac-63c8-420c-80c0-23d0d71ea517/collection/5972111-1831650e-83dc-4776-a5f8-4780294b7091?action=share&source=copy-link&creator=5972111