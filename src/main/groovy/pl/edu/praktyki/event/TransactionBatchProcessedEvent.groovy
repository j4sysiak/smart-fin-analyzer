package pl.edu.praktyki.event

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
 Nie zawiera logiki - to po prostu "wiadomość".
 */
class TransactionBatchProcessedEvent {
    String userName
    BigDecimal totalBalance
    Long transactionsCount
    String generatedReport
}