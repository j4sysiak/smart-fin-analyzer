package pl.edu.praktyki.liskov

import spock.lang.Specification

class LiskovCompositeSpec extends Specification {

    def calculator = new WealthCalculator()

    def "powinien obliczyć majątek traktując pojedyncze transakcje i kompozyty w ten sam sposób (LSP)"() {
        given: "Pojedyncze transakcje"
        def salary = new SingleTransaction(id: "T1", amount: 5000.0)
        def bonus = new SingleTransaction(id: "T2", amount: 1000.0)

        and: "Portfel Krypto (Kompozyt)"
        // tworzymy portfel kryptowalut, który jest kompozytem (gałęzią), ale z punktu widzenia kalkulatora to nadal FinancialAsset
        def cryptoWallet = new AssetBundle(bundleName: "Kryptowaluty")
        // dodajemy kilka kryptowalut jako pojedyncze transakcje (liście) do portfela (gałęzi)
        cryptoWallet.addAsset(new SingleTransaction(id: "BTC", amount: 2500.0))
        cryptoWallet.addAsset(new SingleTransaction(id: "ETH", amount: 1500.0))

        and: "Główny portfel inwestycyjny (Kompozyt zawierający inny Kompozyt!)"
        // tworzymy teraz inny portfel, który będzie zawierał zarówno bonus (liść), jak i cały portfel kryptowalut (gałąź)
        def mainPortfolio = new AssetBundle(bundleName: "Główny Portfel")
        // dodajemy kilka elementów do głównego portfela: bonus (liść) oraz cały portfel kryptowalut (gałąź)
        mainPortfolio.addAsset(bonus) // Dodajemy liść
        mainPortfolio.addAsset(cryptoWallet) // Dodajemy gałąź!

        when: "kalkulator podlicza wszystko (lista zawiera Liść i Gałąź)"
        // Przekazujemy pensję (liść) oraz główny portfel (gałąź)
        // Zasada Liskov: kalkulator powinien traktować oba typy (SingleTransaction i AssetBundle) jako FinancialAsset bez żadnych problemów
        // Lista zawiera zarówno pojedyncze transakcje (liście), jak i kompozyty (gałęzie), ale kalkulator nie musi się o to martwić — po prostu sumuje wszystko rekurencyjnie
        // Zasada Liskov: zarówno pojedyncze transakcje, jak i portfele (kompozyty) są traktowane jako FinancialAsset, więc kalkulator może je sumować bez rozróżniania
        List<FinancialAsset> myAssets = [salary, mainPortfolio]
        def totalWealth = calculator.calculateTotalWealth(myAssets)

        then: "Zasada Liskov działa: kalkulator nie wybuchł, a rekurencja zsumowała wszystko"
        // 5000 (Pensja) + 1000 (Bonus) + 2500 (BTC) + 1500 (ETH) = 10 000
        totalWealth == 10000.0
    }
}
