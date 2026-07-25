package pl.edu.praktyki.closure

import spock.lang.Specification

/**
 * Ten test pokazuje 3 praktyczne zastosowania closure w kodzie produkcyjnym.
 *
 * Zasada jest zawsze ta sama:
 *   - metoda dostaje closure jako argument
 *   - metoda decyduje KIEDY go uruchomić
 *   - my decydujemy CO ma się wykonać
 */
class TransactionProcessorSpec extends Specification {

    def processor = new TransactionProcessor()

    // ─────────────────────────────────────────────
    // PRZYKŁAD 1: RETRY
    //
    // Zamiast pisać: try/catch/try/catch/try/catch...
    // piszemy: withRetry(3) { tutaj nasz kod }
    // ─────────────────────────────────────────────

    def "retry: powinien wykonać operację ponownie po błędzie"() {
        given:
        int attempts = 0

        when:
        // Przekazuję closure do withRetry.
        // withRetry zadecyduje kiedy ją uruchomić (np. po błędzie).
        // Ja mówię tylko CO ma się wykonać.
        processor.withRetry(3) {
            attempts++
            if (attempts < 3) throw new RuntimeException("błąd połączenia")
            return "sukces"
        }

        then:
        attempts == 3   // dopiero za 3 razem zadziałało
    }

    def "retry: powinien rzucić wyjątek, gdy wszystkie próby się nie powiodły"() {
        when:
        processor.withRetry(3) {
            throw new RuntimeException("zawsze błąd")
        }

        then:
        thrown(RuntimeException)
    }

    // ─────────────────────────────────────────────
    // PRZYKŁAD 2: WALIDATOR
    //
    // Zamiast tworzyć osobną klasę dla każdej reguły,
    // przekazujemy regułę jako closure.
    //
    // Reguła jest WYMIENNA — możemy ją zmienić
    // bez zmiany metody validate().
    // ─────────────────────────────────────────────

    def "walidator: transakcja powinna przejść regułę 'kwota powyżej 0'"() {
        given:
        def transakcja = [kwota: 150.0, waluta: "PLN"]

        // closure = reguła walidacji
        // dostaje transakcję jako argument
        // zwraca true/false
        def regula = { tx -> tx.kwota > 0 }

        when:
        def wynik = processor.validate(transakcja, regula)

        then:
        wynik == true
    }

    def "walidator: transakcja powinna nie przejść reguły 'brak PLN'"() {
        given:
        def transakcja = [kwota: 150.0, waluta: "EUR"]

        // inna reguła — inna closure — ten sam processor.validate()!
        def tylkoZlotowki = { tx -> tx.waluta == "PLN" }

        when:
        def wynik = processor.validate(transakcja, tylkoZlotowki)

        then:
        wynik == false
    }

    def "walidator: można łączyć reguły"() {
        given:
        def transakcja = [kwota: 150.0, waluta: "PLN"]

        // closure może też łączyć kilka warunków
        def pelnaRegula = { tx ->
            tx.kwota > 0 && tx.waluta == "PLN"
        }

        when:
        def wynik = processor.validate(transakcja, pelnaRegula)

        then:
        wynik == true
    }

    // ─────────────────────────────────────────────
    // PRZYKŁAD 3: TIMER
    //
    // Zamiast wszędzie pisać:
    //   long start = ...
    //   // kod
    //   long end = ...
    //
    // piszemy:
    //   measureTime { // kod }
    // ─────────────────────────────────────────────

    def "timer: powinien zmierzyć czas przetwarzania transakcji"() {
        when:
        // przekazuję "co mierzyć" jako closure
        // measureTime zadecyduje kiedy uruchomić i jak zmierzyć czas
        long czas = processor.measureTime {
            Thread.sleep(50)  // symulacja przetwarzania
        }

        then:
        czas >= 50
    }

    // ─────────────────────────────────────────────
    // PODSUMOWANIE WZORCA
    //
    // Zauważ, że processor.validate(), processor.withRetry()
    // i processor.measureTime() nie wiedzą NIC o tym,
    // co jest w środku closure.
    //
    // To TY decydujesz co przekazujesz.
    // Metoda decyduje KIEDY to uruchomić.
    //
    // To właśnie jest siła closure w produkcji.
    // ─────────────────────────────────────────────
}

