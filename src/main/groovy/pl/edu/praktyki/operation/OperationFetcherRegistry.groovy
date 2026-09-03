package pl.edu.praktyki.operation

import org.springframework.stereotype.Component

/**
 * Registry mapujący typ operacji na closure pobierające dane z BankOperationClient.
 */
@Component
class OperationFetcherRegistry {

    private final Map<String, Closure<List<OperationDto>>> fetchers


    /*
    co się tu dzieje:
    "DEPOSIT   : ({ -> bankOperationClient.fetchDeposits() }   as Closure<List<OperationDto>>),"
    Ta linia dodaje wpis do mapy `registry`.

    - Klucz `DEPOSIT` oznacza typ operacji.
    - Wartością jest `Closure<List<OperationDto>>`, czyli funkcja bez argumentów, która po wywołaniu pobiera listę depozytów.
    - `\{ -> ... \}` to closure w Groovy.
    - `bankOperationClient.fetchDeposits()` wywoła się dopiero przy `fetcher.call()`, a nie w momencie tworzenia mapy.
    - `as Closure<List<OperationDto>>` wymusza typ closure, żeby mapa miała spójny typ wartości.

    W praktyce działa to jak rejestr:

    - dla `DEPOSIT` \-> użyj funkcji pobierającej depozyty,
    - potem w `fetchByType(...)` ta funkcja jest wyszukiwana i uruchamiana.

   To jest odpowiednik czegoś w stylu: „jeśli typ to `DEPOSIT`, wykonaj pobieranie depozytów”, ale zapisane jako mapa funkcji.
    * */
    OperationFetcherRegistry(BankOperationClient bankOperationClient) {
        Map<String, Closure<List<OperationDto>>> registry = [
                /*zobacz na górze, w co tu się dzieje */
                DEPOSIT   : ({ -> bankOperationClient.fetchDeposits() }   as Closure<List<OperationDto>>),  // Zachować jawny typ closures (Closure<List<OperationDto>>)
                WITHDRAWAL: ({ -> bankOperationClient.fetchWithdrawals() } as Closure<List<OperationDto>>),
                TRANSFER  : ({ -> bankOperationClient.fetchTransfers() }   as Closure<List<OperationDto>>),
                CONVERSION: ({ -> bankOperationClient.fetchConversions() } as Closure<List<OperationDto>>)
        ]
        // Ustawiamy fetchers jako niemutowalne (asImmutable())
        // czyli nie można zmieniać mapy po utworzeniu, co zwiększa bezpieczeństwo.
        this.fetchers = registry.asImmutable()
    }

    List<OperationDto> fetchByType(String operationType) {
        if (!operationType) {
            return null
        }
        Closure<List<OperationDto>> fetcher = fetchers[operationType.toUpperCase()]
        return fetcher ? fetcher.call() : null
    }
}

