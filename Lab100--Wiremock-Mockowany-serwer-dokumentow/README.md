Lab100--Wiremock-Mockowany-serwer-dokumentow
--------------------------------------------

Ten wzorzec pozwala testowac integracje Twojego systemu z obcym API dokumentow bez odpalania prawdziwej uslugi.

## Co dostajesz

- lokalny mock serwera HTTP uruchamiany w teście,
- endpoint `GET /api/documents/{id}` zwracajacy JSON,
- mozliwosc zasymulowania statusow `200` i `404`,
- weryfikacje, czy Twoj system faktycznie wyslal poprawny request.

Przykladowa specyfikacja jest w pliku:
`src/test/groovy/pl/edu/praktyki/integration/DocumentProviderMockServerSpec.groovy`

## Uruchomienie tylko tej specyfikacji

```powershell
.\gradlew.bat test --tests "pl.edu.praktyki.integration.DocumentProviderMockServerSpec"
```

## Szybkie reczne sprawdzenie przez curl (po starcie mocka w teście)

W praktyce do recznego wywolania uzyj endpointu z logiki testu, np.:

```powershell
curl.exe "http://localhost:8089/api/documents/INV-2026-05-001?includeMetadata=true"
```

W testach polecam dynamiczny port (jak w specu), bo nie ma konfliktow miedzy testami.
Jesli chcesz stabilny port pod manualne testy curl, ustaw `WireMockConfiguration.wireMockConfig().port(8089)`.

