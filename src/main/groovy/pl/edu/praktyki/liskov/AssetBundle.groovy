package pl.edu.praktyki.liskov

// 2. KOMPOZYT (Composite) - Worek na inne asety (w tym inne worki!)
class AssetBundle implements FinancialAsset {

    String bundleName

    // UWUAGA WAŻNE: Trzymamy listę interfejsów, nie konkretnych klas!
    private List<FinancialAsset> assets =[]

    void addAsset(FinancialAsset asset) {
        assets << asset
    }

    @Override
    BigDecimal getValue() {
        // Groovy Magic: Wywołujemy getValue() na każdym elemencie w liście
        // i sumujemy.
        // Jeśli elementem jest inny AssetBundle, wywoła się on rekurencyjnie!

        // Groovy Spread Operator (*.):
        // W czystej Javie zrobienie rekurencji po drzewie wymagałoby kilku pętli.
        // Tutaj kod "assets*.getValue().sum()" sam przechodzi przez wszystkie dzieci (nawet zagnieżdżone) i je sumuje.

        return assets*.getValue().sum() ?: 0.0
    }
}
