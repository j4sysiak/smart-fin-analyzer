Asynchroniczność w Springu: Jak to działa?
------------------------------------------
W tym projekcie używamy adnotacji `@Async`, 
aby ciężkie zadania (jak wysyłanie raportów) działy się „w tle”, nie blokując użytkownika.

1. Magiczne "Opakowanie" (Proxy)

Kiedy dodajesz `@Async` nad metodą w klasie oznaczonej jako `@Service` lub `@Component`, 
Spring nie uruchamia tej klasy bezpośrednio.

Tworzy „Asystenta” (Proxy): 
Spring owija Twój serwis w niewidzialne opakowanie.

Przechwytuje wywołanie: 
Gdy wywołujesz metodę, trafiasz najpierw do „Asystenta”.

Deleguje pracę: 
Asystent mówi: „Dobra, ja to ogarnę w innym wątku, a ty leć dalej”. 
Sam wykonuje Twoją metodę na zapleczu.

Ważne: 
To działa tylko jeśli wywołujesz metodę z innej klasy. 
Jeśli metoda wewnątrz tej samej klasy wywoła inną swoją metodę z `@Async`, magia nie zadziała (bo ominiesz „Asystenta”).

2. Konfiguracja "Ekipy Wykonawczej" (Thread Pool)

Aby mieć kontrolę nad tym, ile wątków na raz mieli dane, 
skonfigurowaliśmy własną pulę wątków o nazwie `bulkTaskExecutor` (w pliku `AsyncConfig.groovy`).

Co oznaczają parametry w kodzie?

setCorePoolSize(5): 
Twoja stała ekipa. 
5 wątków zawsze czeka w gotowości.

setQueueCapacity(100): 
Poczekalnia. 
Jeśli stała ekipa jest zajęta, 100 kolejnych zadań może czekać w kolejce.

setMaxPoolSize(10): 
Posiłki. 
Jeśli ekipa jest zajęta, a poczekalnia (kolejka) jest pełna, Spring zatrudni dodatkowe osoby (max do 10).

setThreadNamePrefix("bulk-"): 
Identyfikator. 
W logach zobaczysz nazwę wątku bulk-1, bulk-2, dzięki czemu od razu wiesz, że to zadanie z tej konkretnej puli.

3. Jak używać @Async w kodzie?

```groovy
   @Async("bulkTaskExecutor") // Mówię Springowi: "Zrób to w tle, używając ekipy 'bulk'"
   void myHeavyMethod() {
   // ciężka praca...
   }

```

O czym musisz pamiętać?
-----------------------

1. Musisz mieć `@EnableAsync` w pliku konfiguracyjnym.
2. Klasa musi być `Beanem Springa` (np. mieć `@Service`).

Jeśli w @Async("nazwa") podasz złą nazwę (taką, której nie ma w AsyncConfig), Spring wywali błąd przy starcie. 
Jeśli nie podasz nazwy w ogóle (@Async), użyje domyślnego, bardzo prostego mechanizmu, który nie zawsze jest wydajny.

Dlaczego to jest lepsze dla Ciebie?

Używasz porównań (Asystent, Ekipa, Poczekalnia).

Masz jasne punkty (Co robić, a czego nie).

 


///////////////////////////////////////////////////////////////



moje pytanie:
-------------

a w tym przypadku, jak mam klasę typu `@Service` ale metoda jest `@EventListener`   ??:

```groovy
@Service
@Slf4j
class AsyncNotificationService {

 
    private final AtomicInteger processedEventsCount = new AtomicInteger(0)

    // Dodajemy metodę - dzięki niej Proxy będzie wiedziało, skąd wziąć wartość
    int getProcessedCount() {
        return processedEventsCount.get()
    }

 
    @Async("bulkTaskExecutor")  
    @EventListener
    void handleBatchEvent(TransactionBatchProcessedEvent event) {

        log.info(">>> [ASYNCHRONICZNY-EVENT] Rozpoczynam wysyłkę raportu do systemu zewnętrznego dla: {}", event.userName)

        // Symulujemy ciężką pracę (np. generowanie PDF i wysyłka maila)
        sleep(2000)

        log.info(">>> [ASYNCHRONICZNY-EVENT] Raport o bilansie {} PLN został pomyślnie przetworzony w tle.", event.totalBalance)

        processedEventsCount.incrementAndGet()
    }

    // Opcjonalnie metoda do resetowania licznika między testami
    void reset() {
        processedEventsCount.set(0)
    }

}
```

Odpowiedź:
----------


To świetne pytanie! Ten przypadek to wyższy stopień wtajemniczenia, bo łączymy dwa różne mechanizmy Springa: Zdarzenia (Events) oraz Asynchroniczność.

Oto jak wytłumaczyć to "po ludzku" do Twojego README, aby wyjaśnić tę konkretną klasę:

📡 Architektura Reaktywna: @EventListener + @Async

W klasie AsyncNotificationService zastosowaliśmy potężne połączenie dwóch adnotacji. To sprawia, że nasz system jest „rozproszony wewnątrz” i nie blokuje użytkownika.

1. Jak to działa? (Metafora Listonosza i Biura)

Wyobraź sobie, że Twoja aplikacja to wielkie biuro:

Fasada (nadawca): Kończy pracę i krzyczy na całe biuro: "Ej! Właśnie przetworzyłem nową paczkę transakcji!" (to jest wywołanie publishEvent).

Listonosz (Spring Event Bus): Słyszy to i sprawdza, kto w biurze czeka na taką informację.

@EventListener (odbiorca): Adnotacja nad metodą handleBatchEvent działa jak antena. Mówi Springowi: "Ja! Ja chcę o tym wiedzieć! Za każdym razem, gdy padnie takie hasło, uruchom mnie".

2. Rola @Async w tym procesie

Gdybyśmy mieli samo @EventListener, biuro by stanęło. Fasada musiałaby czekać, aż odbiorca (NotificationService) skończy wysyłać maile (co trwa u nas 2 sekundy).

Dzięki dodaniu @Async("bulkTaskExecutor"):

Fasada krzyczy o zdarzeniu i natychmiast wraca do swoich spraw (oddaje raport użytkownikowi).

Listonosz przekazuje informację do AsyncNotificationService.

Serwis ten mówi: "Dzięki! Nie róbmy tego tutaj. Przekażcie to do mojej ekipy pomocniczej bulkTaskExecutor".

Jeden z wolnych wątków z puli "bulk" bierze to zadanie na warsztat i przez 2 sekundy wysyła raport w tle.

3. Dlaczego używamy AtomicInteger?

Ponieważ metoda działa w tle na różnych wątkach, zwykły licznik (int) mógłby się "pogubić" (dwa wątki mogłyby spróbować dodać 1 w tym samym ułamku sekundy i jeden wynik by przepadł).
AtomicInteger to taki specjalny, "pancerny" licznik, który gwarantuje, że każde dodanie 1 zostanie poprawnie zapisane, nawet jeśli 10 wątków rzuci się na niego naraz.

4. Dlaczego mamy metodę getProcessedCount()?

Pamiętasz "Asystenta" (Proxy)?
W teście Spocka, kiedy pytasz o stan licznika, rozmawiasz z Asystentem.

Jeśli pole byłoby publiczne, Asystent mógłby pokazać Ci swoją starą wersję (zero).

Gdy pytasz przez metodę, Asystent musi "wejść do środka" prawdziwego serwisu, sprawdzić stan AtomicInteger i podać Ci prawdziwą, aktualną wartość.

Podsumowanie do zapamiętania (Short version):

@EventListener – sprawia, że metoda "słucha" komunikatów w systemie.

@Async – sprawia, że to słuchanie odbywa się na boku, nie opóźniając głównego programu.

To połączenie pozwala budować systemy, które są błyskawiczne dla użytkownika, bo wszystkie "ciężkie" i "nudne" rzeczy (maile, logi, audyt) dzieją się po cichu na zapleczu.

Czy teraz ta "reaktywna" część Twojego serwisu jest już jasna? 🚀




