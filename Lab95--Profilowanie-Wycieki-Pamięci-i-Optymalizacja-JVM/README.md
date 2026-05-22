# Lab95 - Profilowanie, Wycieki Pamieci i Optymalizacja JVM

## Cel Laba
Zbadac zachowanie aplikacji pod obciążeniem (duzy import), sprawdzic czy występuje wyciek pamieci oraz przygotowac material pod rozmowe rekrutacyjna (narzedzia, metodyka, wnioski).

---

## Co zrobilismy krok po kroku

### Krok 1: Przygotowanie narzedzi i srodowiska
- przygotowalismy VisualVM na Windows,
- zweryfikowalismy dostepnosc narzedzi diagnostycznych JVM (`jcmd`),
- uruchomilismy aplikacje `pl.edu.praktyki.SmartFinDbApp`.
  

``` 
cd C:\dev\smart-fin-analyzer
.\gradlew.bat bootRun "-Dspring-boot.run.jvmArguments=-XX:+UsePerfData -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9012 -Dcom.sun.management.jmxremote.rmi.port=9012 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1"
```



### Krok 2: Pierwsze podpiecie do procesu JVM
- probowalismy podpiecia przez `Applications -> Local` w VisualVM,
- pojawil sie problem: proces aplikacji byl wyszarzony albo znikal z listy lokalnej.

### Krok 3: Diagnostyka problemu "proces jest, ale go nie widac"
- sprawdzilismy PID przez `jcmd -l`,

```
    PS C:\dev\smart-fin-analyzer> jcmd -l
    39476 org.gradle.launcher.daemon.bootstrap.GradleDaemon 8.14
    9940 jdk.jcmd/sun.tools.jcmd.JCmd -l
    16312 org/netbeans/Main --branding visualvm --cachedir C:\Users\j4sys\AppData\Local\VisualVM\Cache/2.2.1
--->1756 pl.edu.praktyki.SmartFinDbApp
    30652 C:\dev\smart-fin-analyzer\\gradle\wrapper\gradle-wrapper.jar bootRun -Dspring-boot.run.jvmArguments=-XX:+UsePerfData -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9012 -Dcom.sun.management.jmxremote.rmi.port=9012 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1
    7692 com.intellij.idea.Main
```

- odfiltrowaliśmy zly proces (w pewnym momencie wskazywany PID nalezal do DBeaver, nie do aplikacji),
- potwierdziliśmy poprawny PID aplikacji komenda `jcmd <PID> VM.command_line`.

```
PS C:\dev\smart-fin-analyzer> jcmd 1756 VM.command_line
1756:
VM Arguments:
jvm_args: -XX:TieredStopAtLevel=1 -Dfile.encoding=windows-1250 -Duser.country=PL -Duser.language=pl -Duser.variant
java_command: pl.edu.praktyki.SmartFinDbApp
java_class_path (initial): C:\dev\smart-fin-analyzer\build\classes\java\main;C:\dev\smart-fin-analyzer\build\classes\groovy\main;C:\dev\smart-fin-analyzer\build\classes\kotlin\main;C:\dev\smart-fin-analyzer\build\resources\main;C:\Users\j4sys\.gradle\caches\modules-2\files-2.1\io.github.resilience4j\resilience4j-spring-boot3\2.3.0\81c5c03cd43c99c588d82dcef3b20e6861addc99\resilience4j-spring-boot3-2.3.0.jar;C:\Users\j4sys\.gradle\caches\modules-2\files-2.1\io.github.resilience4j\resilience4j-spring6\2.3.0\2ed6c1cc76fd03c97c768ff4f2a525a3ef7819bf\resilience4j-spring6-2.3.0.jar;C:\Users\j4sys\.gradle\caches\modules-2\files-2.1\io.github.resilience4j\resilience4j-framework-common\2.3.0\deeea5a5d8ea1d4fa56c2d8bdad33ae5eba5211a\resilience4j-framework-common-2.3.0.jar;C:\Users\j4sys\.gradle\caches\modules-2\files-2.1\io.github.resilience4j\resilience4j-micrometer\2.3.0\79fbfe7fc77683bc0ecba5bfeb73709d5e0759ad\resilience4j-micrometer-2.3.0.jar;C:\Users\j4sys\.gradle\caches\modules-2\files-2.1\io.github.resilience4j\resilience4j-bulkhead\2.3.0\3e3cc6330e2c6088316f06e58f6d0b2c7fdc5b5b\resilience4j-bulkhead-2.3.0.jar;C:\Users\j4sys\.gradle\caches\modules-2\files-2.1\io.github.resilience4j\resilience4j-circuitbreaker\2.3.0\995e75097efdd303cf4b2403841fe27a32d9e939\resilience4j-circuitbreaker-2.3.0.jar;C:\Users\j4sys\.gradle\caches\modules-2\files-2.1\io.github.resilience4j\resilience4j-retry\2.3.0\1953682ce8b7e69ff5a02d3772ef9c932f2bab3e\resilience4j-retry-2.3.0.jar;C:\Users\j4sys\.gradle\caches\modules-2\files-2.1\io.github.resilience4j\resilience4j-ratelimiter\2.3.0\6c303d40dd6320c009e780b427942871107d8c26\resilience4j-ratelimiter-2.3.0.jar;C:\Users\j4sys\.gradle\caches\modules-2\files-2.1\io.github.resilience4j\resilience4j-timelimiter\2.3.0\2de3016bd43ed1149ff1c035f1de11c9bbbec7fc\resilience4j-timelimiter-2.3.0.jar;C:\Users\j4sys\.gradle\caches\modules-2\files-2.1\io.github.resilience4j\resilience4j-consumer\2.3.0\5649b6e0db11eb78e4a07ba08575d23301c1993a\resilience4j-consu
Launcher Type: SUN_STANDARD
```


### Krok 4: Root cause - brak `hsperfdata`
- sprawdzilismy `UsePerfData` i pliki w `%TEMP%\\hsperfdata_<user>`,
```
PS C:\dev\smart-fin-analyzer> $PID_APP = 1756
PS C:\dev\smart-fin-analyzer> jcmd $PID_APP VM.flags -all | findstr UsePerfData
bool UsePerfData = true                                      {product} {default}
```

- dla PID aplikacji brakowalo pliku `hsperfdata`,
- dlatego VisualVM `Local` nie bylo wiarygodne jako jedyny kanal podpiecia.

### Krok 5: Stabilne obejscie bez restartu aplikacji - JMX
- uruchomilismy agenta JMX dynamicznie na zywej JVM:

```powershell
jcmd <PID> ManagementAgent.start jmxremote.port=9012 jmxremote.rmi.port=9012 jmxremote.authenticate=false jmxremote.ssl=false
jcmd <PID> ManagementAgent.status

jcmd 1756 ManagementAgent.start jmxremote.port=9012 jmxremote.rmi.port=9012 jmxremote.authenticate=false jmxremote.ssl=false
jcmd 1756 ManagementAgent.status
```

- podpielismy VisualVM przez `Add JMX Connection` (port `9012`),
- potwierdzilismy, ze monitoring dziala.

### Krok 6: Dokumentacja operacyjna
- utworzylismy pelna instrukcje podpinania VisualVM/JMX:
  - `scripts/README--podpinanie-VisualVM.md`
- dodalismy tam tez sekcje o MAT (Eclipse Memory Analyzer) i procedurze analizy dumpow.

---

## Problemy, ktore rozwiazalismy w trakcie

1. **Kolizja portu JMX** (`Port already in use`) - rozwiazanie: zmiana portu.
2. **Globalne `JAVA_TOOL_OPTIONS` psulo startup** - rozwiazanie: usuniecie globalnej zmiennej i uruchamianie flag tylko tam, gdzie potrzebne.
3. **Bledny PID (inna aplikacja)** - rozwiazanie: walidacja przez `jcmd <PID> VM.command_line`.   jcmd 1756 VM.command_line
4. **Brak widocznosci procesu w VisualVM Local** - rozwiazanie: awaryjne podpiecie przez JMX.

---

## Aktualny status Laba

- [x] VisualVM dziala.
- [x] SmartFinDbApp jest identyfikowany po poprawnym PID.
- [x] Podpiecie monitoringu przez JMX dziala stabilnie.
- [x] Jest gotowa dokumentacja techniczna podpinania i diagnostyki.
- [ ] Do domkniecia: pelny run obciazeniowy + dump `mid` + dump `post` + analiza w MAT i finalne wnioski.

---

## Nastepny krok (operacyjnie)

Wykonac kontrolowany import duzego pliku i zapisac dumpy:

```powershell
jcmd <PID> GC.heap_dump C:\dev\smart-fin-analyzer\build\heapdumps\lab95_mid.hprof
jcmd <PID> GC.heap_dump C:\dev\smart-fin-analyzer\build\heapdumps\lab95_post.hprof
jcmd <PID> GC.class_histogram > C:\dev\smart-fin-analyzer\build\heapdumps\lab95_hist_post.txt
```

Nastepnie porownac `mid` vs `post` w MAT (`Leak Suspects`, `Histogram`, `Dominator Tree`, `Path To GC Roots`).

---

## Jak uruchamiamy aplikacje, zeby byla widoczna w VisualVM

Zalecenie: przekazuj parametry JVM przez `spring-boot.run.jvmArguments` (nie globalnie przez `JAVA_TOOL_OPTIONS`).

### Parametry, ktore ustawiamy

- `-XX:+UsePerfData` - poprawia widocznosc JVM w `Applications -> Local`.
- JMX:
  - `-Dcom.sun.management.jmxremote`
  - `-Dcom.sun.management.jmxremote.port=9012`
  - `-Dcom.sun.management.jmxremote.rmi.port=9012`
  - `-Dcom.sun.management.jmxremote.authenticate=false`
  - `-Dcom.sun.management.jmxremote.ssl=false`
  - `-Djava.rmi.server.hostname=127.0.0.1`

### Gotowa komenda

```powershell
cd C:\dev\smart-fin-analyzer
.\gradlew.bat bootRun "-Dspring-boot.run.jvmArguments=-XX:+UsePerfData -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9012 -Dcom.sun.management.jmxremote.rmi.port=9012 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1"
```

### Dlaczego tak

- `UsePerfData` pomaga VisualVM wykryc JVM lokalnie.
- JMX jest stabilnym kanalem awaryjnym, gdy wpis lokalny zniknie.
- `hostname=127.0.0.1` eliminuje problemy z nietypowa nazwa hosta przy RMI.

### Czego unikac

- globalnego `JAVA_TOOL_OPTIONS` (moze psuc Gradle/JDK detection),
- flag wyłączajacych perf data, np. `-XX:-UsePerfData` albo `-XX:+PerfDisableSharedMem`.

---

## Sekcja dla rekrutera

### Pitch 60 sekund

W tym labie przeprowadzilem diagnostyke pamieci JVM dla aplikacji Spring/Groovy pod obciazeniem. Podpialem proces do VisualVM, a tam gdzie lokalne wykrywanie JVM bylo niestabilne (brak `hsperfdata`), uruchomilem JMX dynamicznie przez `jcmd` bez restartu aplikacji. Przygotowalem procedure zbierania dumpow (`mid` i `post`) oraz analizy w MAT, z naciskiem na odroznienie normalnego peaku pamieci od realnego wycieku. Efektem jest powtarzalny proces diagnozy problemow memory/GC gotowy do uzycia w projekcie produkcyjnym.

### Najczestsze pytania i gotowe odpowiedzi

1. **Jak odroznic wyciek od normalnego wzrostu pamieci?**
   - Porownuje dump `mid` i `post`, patrze na `retained heap` i trend po GC. Jesli baseline po pracy nie wraca i te same klasy stale rosna, to jest kandydat na leak.

2. **Po co VisualVM i MAT jednoczesnie?**
   - VisualVM daje sygnal live (objaw), MAT daje dowod przyczynowy (kto trzyma obiekty przez `GC Roots`).

3. **Co zrobic, gdy VisualVM nie widzi procesu w Local?**
   - Waliduje PID przez `jcmd`, sprawdzam `hsperfdata`, a potem wlaczam JMX dynamicznie (`ManagementAgent.start`) i lacze sie po porcie.

4. **Jakie byly realne problemy operacyjne i jak je rozwiazales?**
   - Zly PID (inna aplikacja), kolizja portu JMX, globalne `JAVA_TOOL_OPTIONS`, brak widocznosci procesu przez `hsperfdata`. Wszystkie przypadki maja udokumentowane komendy naprawcze.

5. **Jakie artefakty zostawiasz po takim labie?**
   - dumpy `hprof`, histogram klas, logi GC, kroki reprodukcji i checklista diagnostyczna w README.

### Co ten lab pokazuje kompetencyjnie

- diagnoza JVM pod obciazeniem,
- praktyczne uzycie `jcmd`, JMX, VisualVM i MAT,
- umiejetnosc debugowania problemow narzedziowych, nie tylko kodu,
- podejscie oparte o dane (dumpy, retained heap, GC roots),
- gotowosc do pracy z produkcyjnymi incydentami performance/memory.


