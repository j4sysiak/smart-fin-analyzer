# Uruchamianie RESTów z Postmana (Desktop / Web + Agent) — krótkie instrukcje

Ten plik opisuje w prosty sposób jak wysyłać żądania REST do aplikacji lokalnej (np. `POST /api/transactions/upload`) z Postman Desktop oraz z Postman Web przy użyciu Postman Agent. Zawiera przykłady `curl` / PowerShell oraz wskazówki diagnostyczne.

UWAGA: Web‑owa wersja Postmana (web.postman.co) domyślnie nie ma dostępu do plików lokalnych — aby wysyłać pliki (multipart/form‑data) z weba trzeba zainstalować Postman Agent lub użyć Postman Desktop.

---

## 1) Postman Desktop (zalecane)

- Pobierz instalator: https://www.postman.com/downloads/
- Wybierz wersję dla Windows (MSI/EXE) i uruchom instalator.
- Po instalacji uruchom Postman Desktop i zaloguj się (opcjonalnie).

Jak wysłać plik (krok po kroku):

1. Otwórz request (np. POST `http://localhost:8080/api/transactions/upload?user=Jacek`).
2. Przejdź do zakładki `Body` → wybierz `form-data`.
3. Dodaj pole:
   - Key: `file` (dokładnie ta nazwa — odpowiada @RequestParam("file") w kontrolerze)
   - Zmień typ pola z `Text` na `File` (kliknij przycisk `Text` i wybierz `File`).
   - Kliknij `Select Files` i wybierz lokalny plik, np. `C:\dev\smart-fin-analyzer\transactions_upload.csv`.
4. W zakładce `Authorization` ustaw Bearer Token lub dodaj nagłówek `Authorization: Bearer <TOKEN>`.
5. NIE ustawiaj ręcznie nagłówka `Content-Type` — Postman zrobi to automatycznie (multipart + boundary).
6. Kliknij `Send`.

Sprawdzenie (logi/diagnostyka):
- Otwórz Postman Console (View → Show Postman Console). Po wysłaniu sprawdź, czy w konsoli widać `form-data: file: transactions_upload.csv (size: N bytes)` oraz poprawny `Content-Type: multipart/form-data; boundary=...`.

---

## 2) Postman Web + Postman Agent

Jeśli wolisz pracować w przeglądarce (web.postman.co) — musisz zainstalować Postman Agent, żeby web miał dostęp do plików lokalnych.

Instalacja Postman Agent:

1. Zaloguj się na https://web.postman.co
2. W prawym górnym rogu (lub w sekcji pobierania) powinna być informacja o Postman Agent — kliknij `Install Postman Agent`.
3. Pobierz instalator i uruchom go na maszynie lokalnej (Windows installer). Agent działa w tle i umożliwia web klientowi dostęp do lokalnych plików.
4. Wróć do web.postman, odśwież stronę i kliknij `Use Agent` / upewnij się, że w lewym górnym rogu widać zieloną kropkę `Agent running`.

Teraz możesz w web.postman wykonać te same kroki jak w Desktop: Body → form-data → file → Select Files.

Jeżeli web nadal nie wysyła pliku — sprawdź Postman Console w web (View → Show Postman Console) i porównaj czy cURL wygenerowany przez Postman zawiera rzeczywistą ścieżkę pliku (powinien).

---

## 3) Alternatywa: CLI (curl / PowerShell) — działa zawsze

PowerShell (Invoke-RestMethod):

```powershell
$token = "eyJ...TWÓJ_TOKEN..."
$file = Get-Item "C:\dev\smart-fin-analyzer\transactions_upload.csv"
Invoke-RestMethod -Uri "http://localhost:8080/api/transactions/upload?user=Jacek" \
  -Method Post -Headers @{ Authorization = "Bearer $token" } -Form @{ file = $file }
```

curl.exe (PowerShell):

```powershell
curl.exe -v -X POST "http://localhost:8080/api/transactions/upload?user=Jacek" \
  -H "Authorization: Bearer eyJ...TWÓJ_TOKEN..." \
  -F "file=@C:/dev/smart-fin-analyzer/transactions_upload.csv;type=text/csv"
```

Uwaga: w PowerShell natywny alias `curl` wskazuje na `Invoke-WebRequest` — użyj `curl.exe` aby uruchomić binarkę curl, jeśli preferujesz curl.

---

## 4) Diagnostyka — co sprawdzić gdy plik nie dociera

- Czy w Postman (Desktop) w tabeli `Body` w kolumnie `Value` pojawiła się NAZWA pliku i rozmiar? Jeśli widzisz `Select files` to kliknij i wybierz plik.
- W Postman Console szukaj: `form-data: file: <filename> (size: N bytes)` oraz `Content-Type: multipart/form-data; boundary=...`.
- Jeśli widzisz `file=@/path/to/file` w wygenerowanym cURL (Code → cURL), to plik nie został załadowany; wybierz plik właściwie lub użyj Agent/Desktop.
- Sprawdź w logach aplikacji (konsola Spring Boot) czy pojawiają się wpisy z `>>> [REST-UPLOAD] Tymczasowy plik:` oraz `Pierwsza linia pliku (sample):`.

---

## 5) Przydatne skróty i linki

- Postman Desktop: https://www.postman.com/downloads/
- Postman Agent: dostępny z poziomu web.postman.co (po zalogowaniu opcja `Install Postman Agent`)
- Dokumentacja multipart w Spring: https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#mvc-multipart

---

Jeśli chcesz, wkleję tutaj krok‑po‑kroku wygenerowany przez Ciebie cURL z Postman (Code → cURL) i porównam go z działającym poleceniem — pomogę doprecyzować krok, który popełniasz w web.postman.

---

## 6) Screenshots (gdzie umieścić obrazki)

Poniżej dodałem miejsce w dokumentacji, w którym możesz umieścić zrzuty ekranu — przydadzą się dla przyszłych użytkowników. Nie mam dostępu do Twojego pulpitu, więc nie mogę wstawić prawdziwych plików; wklej swoje zrzuty w katalogu `scripts/images/` o podanych nazwach.

Zalecane ścieżki i nazwy plików (umieść tam swoje PNG):

- `scripts/images/postman_desktop_select_file.png`  — zrzut ekranu pokazujący Body → form-data → file (type=File) i wybraną nazwę pliku
- `scripts/images/postman_web_agent_select_file.png` — zrzut ekranu pokazujący web.postman z agentem i wybranym plikiem

Przykładowy sposób osadzenia obrazków w tym pliku (już dodane poniżej). Po wgraniu plików obrazy będą wyświetlane automatycznie w dokumentacji.

```markdown
![Postman Desktop - wybór pliku](images/postman_desktop_select_file.png "Postman Desktop - wybór pliku")

![Postman Web (Agent) - wybór pliku](images/postman_web_agent_select_file.png "Postman Web (Agent) - wybór pliku")
```

UWAGA: jeśli chcesz, mogę również wygenerować plik `scripts/images/README.md` z instrukcją jak tworzyć zrzuty ekranu (np. użycie Snipping Tool w Windows), a następnie wskażę jak wstawić je do repo i zatwierdzić. Napisz "zrób README obrazków" jeśli chcesz to zlecić.

