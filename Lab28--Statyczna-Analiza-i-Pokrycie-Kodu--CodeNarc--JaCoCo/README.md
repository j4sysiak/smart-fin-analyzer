Lab 28  - Quality Assurance (QA).
--------------------------------

co zrobimy:

Dowód jakości: 
Raportu, który pokaże, że Twój kod jest zgodny ze standardami Groovy (nie masz zbędnych średników, nieużywanych zmiennych itp.).

Dowodu pokrycia: 
Raportu, który pokaże, ile % kodu jest przetestowane.

Wchodzimy w Fazę 6: Quality Assurance (QA).

Lab 28: Statyczna Analiza i Pokrycie Kodu (CodeNarc & JaCoCo)
-------------------------------------------------------------

Cel: 
Skonfigurowanie Gradle tak, aby automatycznie sprawdzał styl kodu Groovy (CodeNarc - odpowiednik Checkstyle/SonarQube) 
oraz generował raport pokrycia testami (JaCoCo).


Krok 28.1: Dodanie pluginów do build.gradle
-------------------------------------------

Otwórz `build.gradle` i dodaj dwa nowe pluginy w sekcji plugins na samej górze:
```groovy
plugins {
    id 'groovy'
    id 'org.springframework.boot' version '3.2.1'
    id 'io.spring.dependency-management' version '1.1.4'
    
    // NOWOŚĆ: Analiza statyczna dla Groovy
    id 'codenarc' 
    
    // NOWOŚĆ: Raport pokrycia testami
    id 'jacoco'
}
```

Krok 28.2: Konfiguracja CodeNarc (Luzujemy rygor)
-------------------------------------------------

`CodeNarc` domyślnie jest bardzo surowy. 
Skonfigurujmy go tak, aby generował raport HTML i nie przerywał nam budowania z byle powodu.
Dodaj ten blok na końcu pliku build.gradle:

```groovy
codenarc {
    toolVersion = '3.3.0'
    // Jeśli true, błąd stylu zatrzyma build. Ustawiamy false, żeby najpierw zobaczyć raport.
    ignoreFailures = true 
    configFile = file("config/codenarc/codenarc.groovy") // Zaraz stworzymy ten plik
}

// Konfiguracja raportu JaCoCo
jacocoTestReport {
    dependsOn test // Raport generuje się po testach
    reports {
        xml.required = true
        html.required = true
    }
}
```


Krok 28.3: Plik konfiguracyjny reguł
------------------------------------

Musimy powiedzieć `CodeNarcowi`, jakie zasady nas interesują.
W głównym katalogu projektu stwórz folder: config -> codenarc.
W środku stwórz plik codenarc.groovy i wklej:

```groovy
ruleset {
    // Importujemy standardowe zestawy reguł
    ruleset('rulesets/basic.xml')
    ruleset('rulesets/exceptions.xml')
    ruleset('rulesets/imports.xml')
    ruleset('rulesets/naming.xml')
    ruleset('rulesets/unused.xml')
    
    // Zasady formatowania (np. brak średników!)
    ruleset('rulesets/formatting.xml') {
        // Możemy wyłączyć konkretne reguły, które nas denerwują
        // Np. LineLength - wyłączamy limit długości linii
        'LineLength' {
            enabled = false
        }
    }
}
```

Krok 28.4: Uruchomienie Raportów
--------------------------------
To zadanie uruchomi testy, sprawdzi kod i wygeneruje raporty.
```bash
./gradlew check jacocoTestReport
```
1. Raport Jakości (CodeNarc):
   Wejdź do folderu: build/reports/codenarc/main.html (otwórz w przeglądarce).
   Zobaczysz listę naruszeń.
   Typowe błędy Groovy: Używanie średników (;) na końcu linii, niepotrzebne słowo return na końcu metody, nieużywane importy.
   Zadanie: Popraw w kodzie przynajmniej 3 rzeczy, które wskaże raport!
2. Raport Pokrycia (JaCoCo):
   Wejdź do folderu: build/reports/jacoco/test/html/index.html (otwórz w przeglądarce).
   Zobaczysz tabelkę z procentami.
   Kliknij w pl.edu.praktyki.service.
   Zobaczysz, które linie są zielone (przetestowane), a które czerwone (niepokryte testami).