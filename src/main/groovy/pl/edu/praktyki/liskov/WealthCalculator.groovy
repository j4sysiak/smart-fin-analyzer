package pl.edu.praktyki.liskov

import org.springframework.stereotype.Service
import pl.edu.praktyki.liskov.FinancialAsset

@Service
class WealthCalculator {

    BigDecimal calculateTotalWealth(List<FinancialAsset> allAssets) {
        // Serwis nie wie, czy w liście są pojedyncze transakcje,
        // czy gigantyczne portfele inwestycyjne. Traktuje je TAK SAMO.
        return allAssets*.getValue().sum() ?: 0.0
    }
}