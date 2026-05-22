# Podpinanie Smart-Fin-Analyzer do VisualVM (Windows)

Ten dokument opisuje sprawdzony proces podpięcia aplikacji `SmartFinDbApp` do VisualVM.

Zakres:
- podpięcie standardowe (Local JVM),
- podpięcie awaryjne (JMX bez restartu aplikacji),
- diagnoza problemu: proces działa, ale nie pojawia się w VisualVM.

---

## 1) Wymagania

- JDK 17+ dostępne w systemie (`java`, `jcmd`),
- uruchomiona aplikacja (`pl.edu.praktyki.SmartFinDbApp`),
- VisualVM 2.x.

Sprawdzenie narzędzi:

```powershell
java -version
jcmd -l
```

---

## 2) Szybki start - podpięcie lokalne (Local)

1. Uruchom aplikację (w osobnym terminalu), np.:

```powershell
cd C:\dev\smart-fin-analyzer
.\gradlew.bat bootRun
```

2. Sprawdź PID aplikacji:

```powershell
jcmd -l
```

Szukaj wpisu podobnego do:

```text
29136 pl.edu.praktyki.SmartFinDbApp
```

3. W VisualVM, w drzewie `Applications -> Local`, wybierz `pl.edu.praktyki.SmartFinDbApp (pid XXXX)`.

4. Otwórz zakładki:
- `Monitor` (Heap, GC, Threads),
- `Sampler -> Memory`,
- `Sampler -> CPU`.

To jest preferowany wariant, jeśli wpis JVM jest widoczny.

---

## 3) Gdy aplikacja nie pojawia się w `Local` (ale działa)

### 3.1 Potwierdź, że proces rzeczywiście żyje

```powershell
jcmd -l
jcmd <PID> VM.command_line
```

Jeśli `VM.command_line` pokazuje `pl.edu.praktyki.SmartFinDbApp`, proces działa poprawnie.

### 3.2 Najczęstsza przyczyna: brak `hsperfdata`

VisualVM wykrywa lokalne JVM przez pliki `hsperfdata`. Sprawdź:

```powershell
Test-Path "$env:TEMP\hsperfdata_$env:USERNAME\<PID>"
Get-ChildItem "$env:TEMP\hsperfdata_$env:USERNAME" -ErrorAction SilentlyContinue | Select-Object Name,Length
```

Jeśli pliku dla PID aplikacji brak, VisualVM może nie pokazywać jej w `Local`.

---

## 4) Wariant awaryjny (zalecany): podpięcie przez JMX bez restartu

Jeśli aplikacja działa, ale nie jest widoczna w `Local`, uruchom JMX dynamicznie:

```powershell
jcmd <PID> ManagementAgent.start jmxremote.port=9012 jmxremote.rmi.port=9012 jmxremote.authenticate=false jmxremote.ssl=false
jcmd <PID> ManagementAgent.status
```

Następnie w VisualVM:
- `File -> Add JMX Connection`
- wpisz `127.0.0.1:9012`

Jeśli to nie działa, skopiuj pełny `URL` z `ManagementAgent.status` i podmień host na `127.0.0.1`.

---

## 5) Typowe problemy i rozwiązania

### Problem A: `PID` należy do innej aplikacji (np. DBeaver)
Objaw: VisualVM pokazuje proces, ale metryki/klasy nie pasują.

Sprawdź:

```powershell
jcmd <PID> VM.command_line
```

Wybierz tylko PID z `pl.edu.praktyki.SmartFinDbApp`.

### Problem B: Port JMX zajęty
Objaw: `Port already in use`.

Sprawdź:

```powershell
Get-NetTCPConnection -LocalPort 9012 -State Listen -ErrorAction SilentlyContinue | Select-Object LocalAddress,LocalPort,OwningProcess
```

Użyj innego portu, np. `9013`.

### Problem C: Globalne `JAVA_TOOL_OPTIONS` psuje Gradle/JDK detection
Objaw: błędy startu i nietypowe wyjątki przy `bootRun`.

Wyczyść:

```powershell
Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
[Environment]::SetEnvironmentVariable("JAVA_TOOL_OPTIONS",$null,"User")
```

### Problem D: VisualVM cache pokazuje stare wpisy

```powershell
Stop-Process -Name visualvm -Force
Remove-Item "$env:LOCALAPPDATA\VisualVM\Cache\2.2.1\*" -Recurse -Force -ErrorAction SilentlyContinue
Start-Process visualvm
```

---

## 6) Checklist do Lab95 (profilowanie)

- [ ] `jcmd -l` pokazuje `pl.edu.praktyki.SmartFinDbApp`.
- [ ] VisualVM podpięty (Local lub JMX).
- [ ] Działa `Monitor` + `Sampler Memory/CPU`.
- [ ] Można wykonać dump:

```powershell
jcmd <PID> GC.heap_dump C:\dev\smart-fin-analyzer\build\heapdumps\lab95_mid.hprof
```

---

## 7) MAT - analiza dumpow pamieci (.hprof)

`MAT` (Eclipse Memory Analyzer Tool) sluzy do analizy zrzutow heap (`.hprof`).

Kiedy uzywac:
- `VisualVM` pokazuje trend live (objaw),
- `MAT` pokazuje przyczyne (ktore obiekty i referencje trzymaja pamiec).

### 7.1 Instalacja MAT

- Pobierz: `https://eclipse.dev/mat/` (Windows ZIP/installer).
- Uruchom MAT i otworz dump:
  - `File -> Open Heap Dump...`
  - wybierz np. `C:\dev\smart-fin-analyzer\build\heapdumps\lab95_mid.hprof`.

### 7.2 Najwazniejsze widoki w MAT

1. `Leak Suspects Report`
   - szybki raport potencjalnych wyciekow.
2. `Histogram`
   - lista klas wg liczby instancji i zajetosci pamieci.
3. `Dominator Tree`
   - pokazuje, kto trzyma najwieksze poddrzewa obiektow (`retained heap`).
4. `Path To GC Roots`
   - pokazuje lancuch referencji utrzymujacy obiekt przy zyciu.

### 7.3 Procedura analizy dla Lab95

Porownaj dwa dumpy:
- `lab95_mid.hprof` (w trakcie obciazenia),
- `lab95_post.hprof` (po zakonczeniu i po GC).

Kroki:
1. Otworz `lab95_mid.hprof` i uruchom `Leak Suspects Report`.
2. W `Histogram` posortuj po `Retained Heap`.
3. Zanotuj top 3-5 klas (np. listy/chunki/DTO/closure/thread-local).
4. Dla podejrzanej klasy uruchom `Path To GC Roots`.
5. Powtorz dla `lab95_post.hprof` i porownaj.

### 7.4 Jak odroznic peak od wycieku

- Zdrowy scenariusz:
  - w `mid` jest wzrost,
  - w `post` retained heap wyraznie spada,
  - baseline pamieci wraca do stabilnego poziomu.
- Podejrzenie wycieku:
  - te same klasy trzymaja duzy retained heap rowniez w `post`,
  - `Path To GC Roots` prowadzi do dlugowiecznych struktur (statyczne mapy, kolejki, cache bez limitu, thread-locals, executory).

### 7.5 Co zapisac do raportu/rekrutacji

- top klasy wg `Retained Heap` (mid vs post),
- glowny lancuch `GC Roots` dla 1-2 podejrzanych klas,
- decyzja: `leak / brak leak / wymaga dalszej izolacji`,
- 2-3 rekomendacje optymalizacyjne.

---

## 8) Komendy referencyjne (copy-paste)

Identyfikacja właściwej JVM:

```powershell
jcmd -l
jcmd <PID> VM.command_line
```

Start JMX na żywym procesie:

```powershell
jcmd <PID> ManagementAgent.start jmxremote.port=9012 jmxremote.rmi.port=9012 jmxremote.authenticate=false jmxremote.ssl=false
jcmd <PID> ManagementAgent.status
```

Heap dump:

```powershell
jcmd <PID> GC.heap_dump C:\dev\smart-fin-analyzer\build\heapdumps\lab95_post.hprof
```

Histogram klas:

```powershell
jcmd <PID> GC.class_histogram > C:\dev\smart-fin-analyzer\build\heapdumps\lab95_hist_post.txt
```

