# Mockowany serwer dokumentow w testach Groovy

To jest gotowy wzorzec pod testy integracyjne, gdy Twój system pobiera dokumenty z obcego API.

## 1) Reużywalny mock API dokumentow

Klasa: `src/test/groovy/pl/edu/praktyki/support/mock/DocumentApiMockServer.groovy`

Udostępnia:
- start/stop/reset serwera,
- stub `200 OK` dla dokumentu,
- stub `404 DOCUMENT_NOT_FOUND`,
- weryfikacje requestów,
- autogeneracje wielu stubow z pliku JSON scenariuszy.

## 2) Przyklad testu Spock

Spec: `src/test/groovy/pl/edu/praktyki/integration/DocumentProviderMockServerSpec.groovy`

Scenariusze:
- `GET /api/documents/{id}?includeMetadata=true` -> `200` i JSON dokumentu,
- `GET /api/documents/{id}` dla brakujacego ID -> `404` i JSON bledu.

## 3) Runner do recznych testow curl

Runner: `src/test/groovy/pl/edu/praktyki/support/mock/DocumentApiMockServerRunner.groovy`

Gradle task:
- `runDocumentMockServer` (domyslny port `8089`)
- `-PdocMockPort=8095` (nadpisanie portu)
- `-PdocMockScenarios=...` (wlasny plik scenariuszy)

Domyslny plik scenariuszy:
- `src/test/resources/mock/document-scenarios.json`

Format JSON:

```json
{
  "scenarios": [
	{
	  "id": "INV-2026-05-001",
	  "includeMetadata": true,
	  "statusCode": 200,
	  "body": {
		"id": "INV-2026-05-001",
		"status": "READY"
	  }
	},
	{
	  "id": "INV-404",
	  "statusCode": 404,
	  "body": {
		"error": "DOCUMENT_NOT_FOUND"
	  }
	}
  ]
}
```

## Szybkie uruchomienie

```powershell
.\gradlew.bat documentMockTest
```

```powershell
.\gradlew.bat runDocumentMockServer
```

```powershell
.\gradlew.bat runDocumentMockServer -PdocMockPort=8095
```

```powershell
.\gradlew.bat runDocumentMockServer -PdocMockScenarios="src/test/resources/mock/document-scenarios.json"
```

## Przyklady curl

```powershell
curl.exe "http://localhost:8089/api/documents/INV-2026-05-001?includeMetadata=true"
```

```powershell
curl.exe "http://localhost:8089/api/documents/INV-404"
```

To rozwiazanie mozesz skopiowac 1:1 do kolejnych integracji (np. faktury, statusy platnosci, profile klienta), zmieniajac tylko endpointy i JSON-y stubow.

Jeśli dostaniesz blad "Address already in use", uruchom runner na innym porcie (`-PdocMockPort=8095`) albo zwolnij port 8089.
