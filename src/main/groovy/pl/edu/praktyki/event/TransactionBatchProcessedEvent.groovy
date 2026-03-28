package pl.edu.praktyki.event

import pl.edu.praktyki.domain.Transaction

/**
 * Zdarzenie niosące informację o sfinalizowanym imporcie całej paczki.
 *
 To opis klasy zdarzenia:
 autor ma na myśli, że obiekt tej klasy przenosi informację o zakończonym
 imporcie całej „paczki” (czyli partii/zbioru) transakcji.
  - userName — kto uruchomił import.
  - totalBalance — łączna suma/bilans tych transakcji.
  - generatedReport — Przekazujemy gotowy tekst raportu do procesów w tle


 Zdarzenie służy do powiadamiania innych komponentów o zakończeniu procesu (np. publisher/subscriber).
 */
class TransactionBatchProcessedEvent {
    String userName
    BigDecimal totalBalance
    String generatedReport
}