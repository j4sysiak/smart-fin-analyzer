Lab 58
------

Lab58--Zasada-Liskov-i-Wzorzec-Kompozytu-Composite--Drzewa-Danych----SOLID
--------------------------------------------------------------------------

Została nam jedna, absolutnie kluczowa litera z `SOLID`, o której programiści często zapominają, co prowadzi do fatalnych błędów na produkcji.

Mamy S (Single Responsibility), O (Open/Closed), I (Interface Segregation) i D (Dependency Inversion).
Brakuje nam litery `L - Liskov Substitution Principle (Zasada Podstawienia Liskov)`.

Aby pokazać ją w akcji, połączymy ją z przepięknym wzorcem strukturalnym: `Kompozytem (Composite)`.

Lab 58: Zasada Liskov i Wzorzec Kompozytu (Drzewa Danych)
---------------------------------------------------------

Problem (Łamanie LSP):
Zasada Liskov mówi: 
"Jeśli klasa B dziedziczy po klasie A, to musisz móc użyć obiektu klasy B wszędzie tam, 
gdzie oczekiwany jest obiekt klasy A, a program nie ma prawa wybuchnąć".

Klasycznym łamaniem tej zasady jest tworzenie klasy `ReadOnlyTransaction extends Transaction`, 
która w metodzie `save()` rzuca `UnsupportedOperationException`. 

Jeśli system spróbuje zapisać listę transakcji i natrafi na tę jedną, aplikacja się zawiesi. Złamano obietnicę (kontrakt) klasy bazowej!

Rozwiązanie i Wzorzec Kompozytu:
--------------------------------
Zbudujemy system wyliczania wartości majątku. 
Zamiast dzielić obiekty na "Pojedyncza Transakcja" i "Konto zawierające setki transakcji", 
sprawimy, że dla naszego serwisu będą one wyglądały dokładnie tak samo. 
Serwis nie będzie musiał wiedzieć, czy liczy jedną złotówkę, czy całe drzewo kont bankowych.

Krok 1: Wspólny Kontrakt (Gwarancja Liskov)
-------------------------------------------

Stwórz plik `src/main/groovy/pl/edu/praktyki/liskov/FinancialAsset.groovy`:

```groovy
package pl.edu.praktyki.liskov

// Nasza abstrakcja. Każdy obiekt implementujący ten interfejs 
// GWARANTUJE, że poprawnie zwróci swoją wartość. Zero wyjątków.
interface FinancialAsset {
    BigDecimal getValue()
}
```

Krok 2: Liść (Pojedynczy element) i Kompozyt (Gałąź)
----------------------------------------------------

Teraz tworzymy dwie klasy. 
1. prosta transakcja - liść, który zwraca swoją wartość: `SingleTransaction.groovy`.
2. "Paczka / KOMPOZYT" (Portfel / Assets / Composite), która może zawierać inne transakcje (asety), a nawet... inne paczki (w tym inne worki!)!

Stwórz `src/main/groovy/pl/edu/praktyki/liskov/SingleTransaction.groovy`:

```groovy
package pl.edu.praktyki.liskov

import groovy.transform.Canonical
import pl.edu.praktyki.liskov.FinancialAsset

// 1. LIŚĆ (Leaf) - Pojedyncza, prosta wartość
@Canonical
class SingleTransaction implements FinancialAsset {
    String id
    BigDecimal amount

    @Override
    BigDecimal getValue() {
        return amount
    }
}
```
Stwórz `src/main/groovy/pl/edu/praktyki/liskov/Assets.groovy`:

```groovy
package pl.edu.praktyki.liskov

// 2. KOMPOZYT (Composite) - Worek na inne asety (w tym inne worki!)
class AssetBundle implements FinancialAsset {
    String bundleName
    // Zauważ: Trzymamy listę interfejsów, nie konkretnych klas!
    private List<FinancialAsset> assets =[]

    void addAsset(FinancialAsset asset) {
        assets << asset
    }

    @Override
    BigDecimal getValue() {
        // Groovy Magic: Wywołujemy getValue() na każdym elemencie w liście
        // i sumujemy. Jeśli elementem jest inny AssetBundle, wywoła się on rekurencyjnie!
        return assets*.getValue().sum() ?: 0.0
    }
}

```


Krok 3: Serwis, który ufa Zasadzie Liskov
-----------------------------------------

Nasz serwis finansowy będzie tak prosty, że aż banalny. 
Nie ma w nim ani jednego `instanceof`. 
On ufa, że wszystko, co dostanie, zachowa się jak `FinancialAsset`.

Stwórz `src/main/groovy/pl/edu/praktyki/liskov/WealthCalculator.groovy`:

```groovy
package pl.edu.praktyki.liskov

import org.springframework.stereotype.Service

@Service
class WealthCalculator {

    BigDecimal calculateTotalWealth(List<FinancialAsset> allAssets) {
        // Serwis nie wie, czy w liście są pojedyncze transakcje,
        // czy gigantyczne portfele inwestycyjne. Traktuje je TAK SAMO.
        return allAssets*.getValue().sum() ?: 0.0
    }
}
```

Krok 4: Test Spock (Udowadniamy moc drzewa)
-------------------------------------------

Zobacz, jak możemy zagnieżdżać dane. 

Zbudujemy drzewo: 
-----------------
Portfel Główny zawiera:
 - Portfel Krypto
 - gotówkę

Portfel Krypto zawiera pojedyncze transakcje.

Stwórz `src/test/groovy/pl/edu/praktyki/liskov/LiskovCompositeSpec.groovy`:

```groovy
package pl.edu.praktyki.liskov

import spock.lang.Specification

class LiskovCompositeSpec extends Specification {

    def calculator = new WealthCalculator()

    def "powinien obliczyć majątek traktując pojedyncze transakcje i kompozyty w ten sam sposób (LSP)"() {
        given: "Pojedyncze transakcje"
        def salary = new SingleTransaction(id: "T1", amount: 5000.0)
        def bonus = new SingleTransaction(id: "T2", amount: 1000.0)

        and: "Portfel Krypto (Kompozyt)"
        def cryptoWallet = new AssetBundle(bundleName: "Kryptowaluty")
        cryptoWallet.addAsset(new SingleTransaction(id: "BTC", amount: 2500.0))
        cryptoWallet.addAsset(new SingleTransaction(id: "ETH", amount: 1500.0))

        and: "Główny portfel inwestycyjny (Kompozyt zawierający inny Kompozyt!)"
        def mainPortfolio = new AssetBundle(bundleName: "Główny Portfel")
        mainPortfolio.addAsset(bonus) // Dodajemy liść
        mainPortfolio.addAsset(cryptoWallet) // Dodajemy gałąź!

        when: "kalkulator podlicza wszystko (lista zawiera Liść i Gałąź)"
        // Przekazujemy pensję (liść) oraz główny portfel (gałąź)
        List<FinancialAsset> myAssets = [salary, mainPortfolio]
        def totalWealth = calculator.calculateTotalWealth(myAssets)

        then: "Zasada Liskov działa: kalkulator nie wybuchł, a rekurencja zsumowała wszystko"
        // 5000 (Pensja) + 1000 (Bonus) + 2500 (BTC) + 1500 (ETH) = 10 000
        totalWealth == 10000.0
    }
}
```

Dlaczego to jest wybitne z architektonicznego punktu widzenia?

Zasada Podstawienia Liskov (LSP): 
Metoda `calculateTotalWealth` oczekuje obiektów `FinancialAsset`. 
Wrzuciliśmy tam `SingleTransaction` oraz `AssetBundle`. 
Kalkulator wywołał na nich `.getValue()` i dostał poprawne wyniki. 
Żadna z klas nie złamała kontraktu (nie rzuciła wyjątku "Nie potrafię tego policzyć").

Wzorzec Kompozyt (Composite): 
Zauważyłeś, że w `AssetBundle` trzymamy listę `FinancialAsset`? 
To pozwala nam wkładać paczkę do paczki, tworząc nieskończone drzewo. 
Wzorce kompozytowe są używane wszędzie – np. tak budowane są:
1. Systemy plików (Folder zawiera Pliki i inne Foldery) 
2. Struktury HTML (Div zawiera Paragrafy i inne Divy)

Groovy Spread Operator (*.): 
W czystej Javie zrobienie rekurencji po drzewie wymagałoby kilku pętli. 
W Groovy kod `assets*.getValue().sum()` w klasie `AssetBundle` sam przechodzi przez wszystkie dzieci (nawet zagnieżdżone) i je sumuje. 
To jest poezja!


Podsumowanie:
-------------

Zasada Liskov to fundament solidnej architektury.
Jeśli łamiesz LSP, tworzysz system, który jest kruchy i podatny na błędy. 
Wzorzec Kompozyt to potężne narzędzie, które pozwala tworzyć elastyczne struktury danych, a jednocześnie zachować prostotę interfejsu. 
Kiedy połączysz te dwa elementy, otrzymasz system, który jest zarówno potężny, jak i łatwy w utrzymaniu. 


