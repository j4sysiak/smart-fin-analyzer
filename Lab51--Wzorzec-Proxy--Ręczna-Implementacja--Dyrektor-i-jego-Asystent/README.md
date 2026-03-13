Lab 51
------

Lab 51: Wzorzec Proxy - Ręczna Implementacja - Dyrektor i jego Asystent
-----------------------------------------------------------------------

W przeciwieństwie do `AOP` (które jest "magicznym" Proxy od Springa), tym razem napiszemy własne Proxy "z palca". 
To nauczy Cię, jak działa Spring pod maską i pozwoli zrozumieć, co dzieje się, gdy bean przestaje być "zwykłym obiektem", a staje się "opakowaniem".

Cel: 
Stworzenie klasy `Proxy`, która udaje oryginał, ale dodaje logikę (np. logowanie czasu) przed i po wywołaniu prawdziwej metody.

1. Interfejs wspólny: `Service.groovy`

Obiekt oryginalny i Proxy muszą wyglądać dla klienta tak samo.

```groovy
interface WorkService {
   void doHeavyWork()
}
```

2. Oryginał (Prawdziwy pracownik)

`src/main/groovy/pl/edu/praktyki/proxy/RealWorkService.groovy`

```groovy
class RealWorkService implements WorkService {
    @Override
    void doHeavyWork() {
        println ">>> [Dyrektor] Wykonuję ciężką pracę..."
        Thread.sleep(1000) // Symulacja czasu
    }
}
```
 
3. Proxy (Asystent)

Zwróć uwagę: Proxy zawiera w sobie instancję `RealWorkService`. To jest sedno wzorca!

`src/main/groovy/pl/edu/praktyki/proxy/WorkServiceProxy.groovy`

```groovy
class WorkServiceProxy implements WorkService {
    private final WorkService realService // To jest nasz "Dyrektor"

    WorkServiceProxy(WorkService realService) {
        this.realService = realService
    }

    @Override
    void doHeavyWork() {
        // --- LOGIKA ASYSTENTA (Proxy) ---
        println ">>> [Asystent] Zapisuję czas rozpoczęcia..."
        long start = System.currentTimeMillis()
        
        // Wywołanie Dyrektora
        realService.doHeavyWork()
        
        // --- LOGIKA ASYSTENTA (Proxy) ---
        long end = System.currentTimeMillis()
        println ">>> [Asystent] Dyrektor skończył po ${end - start}ms"
    }
}
```
 
4. Test (Weryfikacja "przezroczystości")

Oto kompletna klasa testowa ProxySpec.groovy, która spina w całość wzorzec Proxy. 
Zobaczysz w niej, jak klient (test) używa interfejsu WorkService, 
nie wiedząc, czy pod spodem kryje się "Dyrektor" (RealWorkService) czy "Asystent" (WorkServiceProxy).

Stwórz plik `src/test/groovy/pl/edu/praktyki/proxy/ProxySpec.groovy`:

```groovy
def "powinien ukryć logikę proxy przed klientem"() {
    given: "Prawdziwy serwis i jego Proxy"
    WorkService real = new RealWorkService()
    WorkService proxy = new WorkServiceProxy(real)

    when: "Klient woła Proxy, myśląc że to serwis"
    proxy.doHeavyWork()

    then: "Efekt pracy jest wykonany, a asystent dodał logowanie"
    // Sprawdzamy, czy proxy zaimplementowało interfejs
    proxy instanceof WorkService
    noExceptionThrown()
}
```
Dlaczego to jest kluczowe (Mid/Senior mindset)?

Transparentność: 
Zobacz, że w teście używamy `WorkService proxy = new WorkServiceProxy(...)`. 
Klient (test) nie wie, czy rozmawia z "Dyrektorem", czy z "Asystentem". 
To pozwala podmieniać implementacje bez zmiany kodu klienta.

Spring AOP to "Proxy Factory": 
Kiedy w Springu dodasz `@Transactional`, Spring tworzy dokładnie coś takiego jak nasz `WorkServiceProxy` pod spodem. 
Zamiast `doHeavyWork`, wywołuje `transactionalProxy.doHeavyWork()`, które najpierw otwiera transakcję (db.begin()), a potem woła Twojego "Dyrektora".

Wzorzec Projektowy vs Framework: 
Teraz rozumiesz, że AOP to tylko sposób na automatyczne tworzenie 
    tysięcy takich klas `WorkServiceProxy` dla każdej Twojej klasy serwisowej.

---------------------------------------------------------------------------------------------------

Podsumowanie:
- Wzorzec Proxy pozwala na dodanie logiki przed i po wywołaniu metody bez zmiany kodu oryginalnej klasy.
- Klient korzysta z interfejsu, nie wiedząc, czy rozmawia z oryginałem, czy z Proxy.
- Spring AOP to automatyczna implementacja tego wzorca, która pozwala na dodanie aspektów (np. transakcyjności) bez ręcznego pisania Proxy.
- Zrozumienie tego wzorca jest kluczowe, aby efektywnie korzystać z AOP i innych mechanizmów Springa, które opierają się na Proxy.
- Ręczna implementacja Proxy to świetny sposób na zrozumienie, jak działa Spring pod maską i dlaczego czasami może być "magiczny".
- Dzięki temu laboratorium masz solidne podstawy, aby zrozumieć, co dzieje się, gdy Spring tworzy Proxy dla Twoich beanów i jak to wpływa na działanie Twojej aplikacji.

Moje pytanie do Ciebie:
-----------------------
Nie rozumie jednak tego wzorca Proxy. 
Przeciez i tak wywolujesz `dyrektora` i kazesz mu ciezko pracować. 
```groovy
       // Wywołanie Dyrektora w Proxy - kalas WorkServiceProxy
        realService.doHeavyWork()
```
Myslalem, ze to proxy bedzie zapierdalal za dyra.

Odpowiedź:
----------

Proxy nie "zapierdala" za Dyrektora, ale jest pośrednikiem, który dodaje dodatkową logikę (np. logowanie czasu) przed i po wywołaniu metody Dyrektora.
Wzorzec Proxy pozwala na kontrolowanie dostępu do obiektu (Dyrektora) i dodawanie dodatkowych funkcjonalności bez zmiany kodu Dyrektora.
W naszym przykładzie, Proxy (Asystent) mierzy czas wykonania ciężkiej pracy Dyrektora i loguje go.
To jest kluczowe, ponieważ klient (test) korzysta z interfejsu WorkService, nie wiedząc, czy rozmawia z Dyrektorem (RealWorkService) czy z Asystentem (WorkServiceProxy). 
Dzięki temu możemy podmieniać implementacje bez zmiany kodu klienta, co jest jednym z głównych celów wzorca Proxy.


ale ...

To jest najważniejsze pytanie w historii tego wzorca. Trafiłeś w sam środek "zagadki" Proxy!

Masz rację: 
jeśli Proxy (Asystent) tylko woła Dyrektora (RealService), to Dyrektor i tak musi "zapierdalać" w swoim gabinecie. Więc po co to Proxy?

Otóż Proxy (wzorce) mają trzy różne twarze. To, co zrobiliśmy w Labie 51, to tylko Logging Proxy (Asystent, który tylko zagląda do dziennika). 
Ale Proxy służy do dużo potężniejszych rzeczy niż tylko logowanie!

Oto 3 przypadki, w których to Proxy "zapierdziela za Dyrektora" i Dyrektor w ogóle nie musi wstawać z fotela:

1. Wirtualne Proxy (Lazy Loading) – "Dyrektor jest na wakacjach"
Wyobraź sobie, że Dyrektor (RealWorkService) to obiekt, który waży 2GB w pamięci RAM. 
Nie chcesz go ładować przy starcie programu.

Działanie: 
Proxy mówi: "Dyrektor jeszcze śpi, nie będę go budził. 
Dopiero jak klient na prawdę zawoła `proxy.doHeavyWork()`, to ja go stworzę w pamięci".

Zysk: 
Aplikacja startuje w 1 sekundę zamiast 30 sekund.

2. Zdalne Proxy (Remote Proxy) – "Dyrektor jest w innym biurze"

To jest podstawa Microserwisów.

Działanie: 
Klient woła `proxy.doHeavyWork()`.
Proxy nie woła żadnego obiektu w pamięci, tylko pakuje dane do JSON-a 
i wysyła je przez sieć do innego serwera (np. w chmurze), gdzie jest prawdziwy Dyrektor.

Zysk: 
Klient nawet nie wie, że "Dyrektor" jest na innym kontynencie. Myśli, że jest w tej samej JVM.

3. Proxy Ochronne (Security Proxy) – "Asystent weryfikuje"

To jest najczęstsze zastosowanie w Springu (np.`@Secured` czy `@PreAuthorize`).

Działanie: 
Klient woła `proxy.doHeavyWork()`. 
Asystent (Proxy) patrzy na listę płac: "Czy ten klient ma uprawnienia?".

Jeśli NIE -> Asystent rzuca SecurityException i w ogóle nie puka do gabinetu Dyrektora.

Jeśli TAK -> Dopiero wtedy puka do gabinetu Dyrektora.

Zysk: 
Dyrektor (kod biznesowy) nie musi mieć w sobie ani jednej linijki kodu sprawdzającego uprawnienia. 
Dzięki temu kod biznesowy jest czysty i nie da się go "zapomnieć" zabezpieczyć.

Dlaczego w naszym Labie Dyrektor "zapierdzielał"?

W naszym przykładzie zrobiliśmy "Logging Proxy". 
Logowanie wymaga dostępu do wyniku pracy Dyrektora, więc Proxy musi zapytać go o wynik. Dlatego Dyrektor musiał pracować.

Ale gdybyś w `WorkServiceProxy` dopisał:

```groovy
@Override
void doHeavyWork() {
    if (System.currentTimeMillis() % 2 == 0) {
        println "Asystent mówi: Dyrektor śpi, dzisiaj nie pracujemy, wracaj jutro!"
        return // Dyrektor w ogóle nie dostał pracy!
    }
    realService.doHeavyWork()
}
```
 

...to wtedy Asystent przejąłby całą robotę i Dyrektor mógłby w tym czasie pić kawę.

Czy to zmienia postać rzeczy?

Wzorce projektowe to nie jest magia, która sprawia, że kod sam się pisze. 
To sposób na kontrolowanie dostępu do obiektu. 
Jeśli chcesz, żeby "ktoś" pracował za Dyrektora, musisz w metodzie Proxy napisać kod, który wykonuje robotę zamiast niego.

Czy to wyjaśnienie pokazuje Ci, dlaczego Proxy to "strażnik bramy"? 
W Springu właśnie tak wygląda każda metoda `@Transactional` – Proxy sprawdza, czy masz sesję do bazy, 
a jeśli nie – od razu ją otwiera (lub blokuje dostęp) zanim Ty w ogóle zaczniesz procesować zamówienie!



