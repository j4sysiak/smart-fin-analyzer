Lab73
-----

Lab73--End-to-End-Reactive-Pipeline--Od-CSV-do-CQRS
===================================================

Teraz mam dosyć rozbudowany system, ale nie rozumię tematów związanych z:
 - wielowątkowością
 - synchronizacją
 - asynchroniczymi wywolaniami
 - Eventami

Musisz mi to bardziej wyjaśnić, zanim pójdziemy z innymi tematami rozbudowującymi ten system.

Odp
---

To jest ten moment, w którym przeskakujesz z bycia "koderem" na poziom "architekta". 
Rozumienie tego, jak dane płyną przez procesor i pamięć, to fundament systemów klasy Enterprise.

Wyobraź sobie, że nasza aplikacja to duża restauracja. 
Użyję tej metafory, żeby wyjaśnić Ci te cztery potężne pojęcia.

1. Wielowątkowość (Multi-threading) – "Wielu kucharzy"

Idea:
Wykonywanie wielu zadań dokładnie w tym samym czasie.
W Twoim systemie: Użyliśmy tego w `TransactionIngesterService` przez GPars.

Metafora: 
Masz 10 plików CSV do przetworzenia. 
Jeden kucharz (wątek) robiłby to 10 minut. 
Jeśli zatrudnisz 10 kucharzy (10 wątków), każdy weźmie jeden plik i skończą w 1 minutę.

Kluczowe pojęcie: 
Pula wątków (`Thread Pool`). 
Nie zatrudniasz kucharza na jedno krojenie cebuli i nie zwalniasz go potem. 
Masz stałą ekipę (np. 4-8 osób), która czeka na zadania. To jest Twój `bulkTaskExecutor`.

2. Asynchroniczność (Asynchronicity) – "Buzer w restauracji"

Idea: 
Nie czekasz na zakończenie zadania, tylko robisz w tym czasie coś innego.
W Twoim systemie: Użyliśmy adnotacji `@Async`.

Metafora: 
Zamawiasz pizzę. 
Nie stoisz przy ladzie, gapiąc się na kucharza przez 15 minut (to byłoby synchroniczne blokowanie). 
Dostajesz buzer (`Promise/Future`). 
Idziesz usiąść, rozmawiasz ze znajomymi, a gdy pizza jest gotowa, buzer dzwoni.

Dlaczego to ważne? 
Dzięki `@Async`, Twój kontroler `REST` wysyła odpowiedź do użytkownika ("Przyjęto do przetwarzania"), 
a ciężka praca dzieje się "na zapleczu". 
Użytkownik nie widzi kręcącego się kółka w przeglądarce.

3. Synchronizacja (Synchronization) – "Klucz do toalety"

Idea: 
Pilnowanie, żeby dwa wątki nie zepsuły tych samych danych w tej samej milisekundzie.

W Twoim systemie: 
Użyliśmy `AtomicInteger` w liczniku oraz `@Transactional` w bazie.

Metafora: 
W restauracji jest tylko jedna toaleta. 
Jeśli dwóch kucharzy wbiegnie tam naraz, będzie katastrofa. 
Rozwiązaniem jest klucz (`Lock/Monitor`). Tylko ten, kto ma klucz, może wejść. 
Drugi musi poczekać przed drzwiami.

Problem "Race Condition": 
Wyobraź sobie, że dwa wątki czytają stan konta: 100 zł. 
Oba dodają 10 zł. 
Jeśli zrobią to w tej samej mikrosekundzie bez synchronizacji, oba zapiszą 110 zł (zamiast 120 zł), 
bo ten drugi "nie zauważył" zmiany dokonanej przez pierwszego. 
`AtomicInteger` lub blokada bazy danych rozwiązują ten problem.

4. Eventy (Zdarzenia) – "Tablica zamówień"

Idea: 
Rozluźnienie powiązań (Decoupling). K
omponenty nie muszą się znać, żeby współpracować.

W Twoim systemie: 
`ApplicationEventPublisher` i `@EventListener`.

Metafora: 
Kucharz kończy robić danie. 
Nie idzie do kelnera i nie szarpie go za rękaw (to byłoby silne powiązanie). 
Kucharz stawia talerz na ladzie i naciska dzwonek: "Danie gotowe!" (to jest `Event`).

Kto słucha?
 - Kelner (żeby podać danie).
 - Zmywak (żeby wiedzieć, że zaraz będzie brudny talerz).
 - Manager (żeby odnotować sprzedaż).

Zaleta: 
Kucharz nie musi wiedzieć o istnieniu zmywaka czy managera. 
On tylko "rzuca komunikat". 
Dzięki temu, jeśli jutro zatrudnisz fotografa dań, po prostu każesz mu słuchać tego samego dzwonka. 
Nie musisz nic zmieniać w kodzie kucharza!

Jak to wszystko współpracuje w "Smart-Fin-Analyzer"?
----------------------------------------------------
Spójrz na ten przepływ (to jest Twój system w pigułce):

1. Request (Synchroniczny): Użytkownik wrzuca plik przez REST.
2. Fasada: Wywołuje Ingestera.
3. Wielowątkowość (`GPars`): `Ingester` tnie plik na kawałki i używa wielu wątków, żeby szybko przeliczyć waluty.
4. Event (Zdarzenie): Fasada mówi: "Skończyłam import!" i rzuca `TransactionBatchProcessedEvent`.
5. Asynchroniczność (`@Async`): System ma dwóch słuchaczy:
    - `AsyncNotificationService` słyszy to i asynchronicznie (w tle) wysyła raport.
    - `SummaryProjectionListener` słyszy to i asynchronicznie (w tle) aktualizuje tabelę statystyk (`CQRS`).
6. Synchronizacja: Ponieważ statystyki aktualizuje wątek w tle, używamy `@Transactional`, żeby baza danych dopilnowała, 
   by wyniki się nie pomieszały (`Locking`).

 - Większość rzeczy dzieje się "obok siebie" (Wątki).
 - Komponenty rozmawiają ze sobą przez ogłoszenia (Eventy).
 - Użytkownik nigdy nie czeka na powolne procesy (Asynchroniczność).


--------------------------------------------------------
---------------  przyklad LAB  -------------------------
--------------------------------------------------------

To będzie nasz „Master Lab”. 
Połączymy w nim wszystko, co zbudowaliśmy do tej pory, w jeden, potężny rurociąg danych. 
To zadanie symuluje realny proces w systemie bankowym: 
od kliknięcia przycisku przez użytkownika, po asynchroniczne przeliczenie statystyk całego systemu.

Co się wydarzy?
---------------
1. REST: Wysyłasz plik CSV. Wątek HTTP czeka (synchronicznie), aż dane zostaną wczytane do pamięci.
2. Fasada: Przejmuje plik, zamienia go na obiekty i wywołuje `Ingestera`.
3. GPars (Wielowątkowość): `Ingester` rozdziela transakcje na wszystkie rdzenie procesora, przelicza waluty i aplikuje reguły (np. tagowanie).
4. Zapis (Batch): Twój `TransactionBulkSaver` wrzuca wszystko do `Postgresa`.
5. Event: Fasada rzuca zdarzenie: „Batch zakończony”.
6. Async (Tło): Słuchacze w osobnych wątkach wysyłają powiadomienie i aktualizują tabelę statystyk `CQRS`.

Ten krok połączy Twój świat plików (CSV) z Twoim światem serwerowym (REST), 
używając istniejącej Fasady jako serca systemu.

 
Krok 1: Stworzenie UploadController.groovy
------------------------------------------
Ten kontroler pozwoli Ci wysłać plik CSV przez Postmana, zamiast podawać go w parametrach startowych aplikacji.

Stwórz plik `src/main/groovy/pl/edu/praktyki/web/UploadController.groovy`

```groovy
package pl.edu.praktyki.web

import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import pl.edu.praktyki.facade.SmartFinFacade
import pl.edu.praktyki.parser.CsvTransactionParser
import groovy.util.logging.Slf4j

@RestController
@RequestMapping("/api/transactions/upload")
@Slf4j
class UploadController {

    @Autowired SmartFinFacade facade
    // Używamy bezpośrednio Twojego parsera CSV
    @Autowired CsvTransactionParser csvParser

    @PostMapping
    ResponseEntity<String> uploadCsv(@RequestParam("file") MultipartFile file, @RequestParam("user") String user) {
        log.info(">>> [REST-UPLOAD] Otrzymano plik: {} od użytkownika: {}", file.originalFilename, user)
        
        // 1. Tworzymy tymczasowy plik na dysku, aby parser mógł go przeczytać
        File tempFile = File.createTempFile("rest-upload-", ".csv")
        file.transferTo(tempFile)

        try {
            // 2. Parsujemy plik do listy obiektów Transaction
            def transactions = csvParser.parse(tempFile)
            log.info(">>> [REST-UPLOAD] Sparsowano {} transakcji", transactions.size())

            // 3. Wywołujemy TWOJĄ istniejącą metodę w Fasadzie
            // Przekazujemy pustą listę reguł lub przykładową regułę
            String report = facade.processAndGenerateReport(user, transactions, [])

            return ResponseEntity.ok(report)
        } finally {
            // Zawsze usuwamy plik tymczasowy po skończonej pracy
            tempFile.delete()
        }
    }
}
```

Krok 2: Test Spock (UploadControllerSpec.groovy)

Musimy sprawdzić, czy kontroler poprawnie przyjmuje plik i przekazuje go do Fasady. Użyjemy MockMvc oraz specjalnej metody multipart().

Stwórz plik `src/test/groovy/pl/edu/praktyki/web/UploadControllerSpec.groovy`

```groovy
package pl.edu.praktyki.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@WithMockUser(username = "admin") // Pamiętamy o kłódce Security!
class UploadControllerSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc

    def "powinien przyjąć plik CSV i zwrócić raport finansowy"() {
        given: "treść pliku CSV w pamięci"
        def csvContent = """id,date,amount,currency,category,description
T1,2026-03-01,100,PLN,Test,Opis""".getBytes()

        and: "tworzymy wirtualny plik do wysłania"
        def mockFile = new MockMultipartFile(
                "file",               // nazwa parametru w kontrolerze
                "test.csv",           // nazwa pliku
                "text/csv",           // typ MIME
                csvContent            // zawartość
        )

        when: "wysyłamy żądanie POST multipart"
        def response = mvc.perform(multipart("/api/transactions/upload")
                .file(mockFile)
                .param("user", "Jacek_Test"))

        then: "serwer odpowiada 200 OK"
        response.andExpect(status().isOk())
        
        and: "odpowiedź zawiera tekst raportu"
        def content = response.andReturn().response.contentAsString
        content.contains("RAPORT FINANSOWY DLA: JACEK_TEST")
    }
}
```

Co się tutaj dzieje (Podsumowanie dla Ciebie):

Integracja bez inwazji: 
Nie zmieniłeś ani jednej linijki w `SmartFinFacade` ani w `TransactionIngesterService`. 
Twój istniejący kod działa identycznie jak wcześniej.

MultipartFile: 
To standard Springa do przesyłania plików. Aplikacja mobilna wysyła strumień bajtów, a Spring zamienia to na wygodny obiekt.

Łączenie światów: 
UploadController jest "tłumaczem" – bierze plik z internetu, zamienia go na listę Twoich transakcji 
i prosi Fasadę: "Zrób z tym to, co zwykle robisz dla plików z dysku".

Automatyczne Wyzwalacze: 
Ponieważ facade.processAndGenerateReport ma w sobie wywołanie eventPublisher.publishEvent, 
po zakończeniu uploadu wciąż automatycznie (asynchronicznie) odpali się Twój NotificationService oraz CQRS Listener.

Zadanie:
-------
Dodaj UploadController i test.
Uruchom aplikację i spróbuj wysłać plik przez Postmana (Body -> form-data -> klucz: file, typ: File).
Sprawdź, czy dostałeś raport w odpowiedzi Postmana.