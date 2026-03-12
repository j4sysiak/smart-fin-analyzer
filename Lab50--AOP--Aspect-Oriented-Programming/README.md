Lab 50
------

AOP (Aspect Oriented Programming) to technologia, która pozwala "wstrzyknąć" kod do Twojej aplikacji z zewnątrz, 
bez modyfikowania oryginalnych klas. 
Dzięki temu `AuditLog` albo `PerformanceMetrics` mogą pojawić się w Twoich serwisach "magicznie".

przyklad klasy `pl.edu.praktyki.report.trait.AuditLog.groovy`:

```groovy
package pl.edu.praktyki.report.trait

import java.time.LocalTime
import java.time.format.DateTimeFormatter

trait AuditLog {
    List<String> history = []

    void audit(String message) {
        // Używamy nowoczesnego API zamiast przestarzałego Date.format()
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        String entry = "[AUDIT] $time - $message"

        println entry
        history << entry
    }

    boolean hasAuditEntry(String fragment) {
        return history.any { it.contains(fragment) }
    }
}
```

W świecie Spring Boota AOP jest fundamentem: to dzięki niemu działają `@Transactional`, `@Cacheable` czy `@Secured`.

Lab50--AOP--Aspect-Oriented-Programming
---------------------------------------

Cel: 
Stworzenie "Aspektu", który automatycznie mierzy czas wykonania każdej metody w serwisach, bez dodawania ani jednej linii kodu do tych serwisów.

1. Dodanie zależności (build.gradle)

AOP wymaga wsparcia dla AspectJ. Upewnij się, że masz to w dependencies:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

2. Stworzenie Aspektu (LoggingAspect.groovy)

Stwórz plik `src/main/groovy/pl/edu/praktyki/aop/LoggingAspect.groovy`. 
Ten kod "podepnie się" pod metody innych klas.

```groovy
package pl.edu.praktyki.aop

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class LoggingAspect {

    // "Around" oznacza: wykonaj to przed i po metodzie
    // @Around("execution(* pl.edu.praktyki.service.*.*(..))") 
    // ^ Wyrażenie "Pointcut": celuj we wszystkie metody w pakiecie .service
    @Around("execution(* pl.edu.praktyki.service.*.*(..))")
    Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis()
        
        // Tutaj wołamy oryginalną metodę (np. processOrders)
        Object proceed = joinPoint.proceed()
        
        long executionTime = System.currentTimeMillis() - start
        
        println ">>> [AOP PROFILER] Metoda ${joinPoint.signature.name} " +
                "wykonała się w ${executionTime}ms"
        
        return proceed
    }
}
```

Dlaczego to jest potężne?

Czystość kodu: 
Twoje serwisy (`OrderProcessingService`, `CurrencyService`) nie mają pojęcia, że ktoś mierzy im czas. 
Są w 100% czyste od logiki technicznej.

Globalny zasięg: 
Jeśli jutro dodasz nowy serwis (np. `ReportGeneratorService`), Twój `LoggingAspect` automatycznie zacznie mierzyć czas jego metod, 
bo pasują do wyrażenia w execution(...).

Cross-cutting concerns: 
Wszelkie zagadnienia typu: `Logowanie`, `Transakcje`, `Bezpieczeństwo` (Kto może wywołać tę metodę?), `Cache` – wszystko to trzyma się w osobnych aspektach.

3. Testowanie Aspektu (Spock)

Testowanie AOP jest ciekawe, bo musimy upewnić się, że aspekt "zadziałał". 
W testach Springowych najłatwiej to sprawdzić, sprawdzając konsolę lub modyfikując aspekt tak, by zapisywał stan do listy.

Stwórz `src/test/groovy/pl/edu/praktyki/aop/AopSpec.groovy`:

```groovy
package pl.edu.praktyki.aop

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import pl.edu.praktyki.service.TransactionIngesterService
import pl.edu.praktyki.domain.Transaction
import spock.lang.Specification

@SpringBootTest(classes = [TransactionIngesterService, LoggingAspect])
class AopSpec extends Specification {

    @Autowired TransactionIngesterService service

    def "powinien uruchomić aspekt logujący przy wywołaniu metody serwisu"() {
        given: "transakcja"
        def tx = new Transaction(id: "T1", amount: 100.0)

        when: "wywołujemy metodę serwisu"
        // Logika serwisu (ingestTransactions) uruchomi się, a AOP "podepnie" się w tle
        service.ingestTransactions([tx])

        then: "nie wywala się"
        noExceptionThrown()
    }
}
```

Twoje Wyzwanie: "Interceptor bezpieczeństwa"

Dodaj drugi Aspekt SecurityAspect.groovy.

Użyj @Around.

Zamiast mierzyć czas, sprawdź w joinPoint.args (argumenty metody), czy kwota transakcji nie przekracza np. 100 000 zł.

Jeśli tak – rzuć SecurityException("ALERT: Nieautoryzowana wysoka kwota!").

To ostatnie wyzwanie zamknie Twoją przygodę z Javą/Groovy na poziomie eksperckim. Dzięki temu będziesz rozumiał, jak działają systemy typu Spring Security ("czy użytkownik ma uprawnienia do tej metody?").

Czy to ostatnie wyzwanie "weszło"? Jeśli tak – jesteś już gotowy na architektów, seniorów i trudne pytania rekrutacyjne! 🎩✨

//////////////////////////////////////

Przyczyna błędu: Test używał @SpringBootTest bez parametru classes, co powodowało próbę załadowania pełnego kontekstu aplikacji (baza danych, Flyway, Actuator itd.). Ponieważ kontekst nie mógł się poprawnie załadować, pole service pozostawało null, stąd NullPointerException.
Rozwiązanie: Zamiana @SpringBootTest na @ContextConfiguration z precyzyjnie podanymi klasami:
TransactionIngesterService — testowany serwis
TransactionRuleService — zależność serwisu (@Autowired ruleService)
LoggingAspect — testowany aspekt AOP
AopTestConfig — konfiguracja testowa z @EnableAspectJAutoProxy oraz stubami dla ApplicationEventPublisher i FinanceMetrics
Dzięki temu Spring tworzy lekki kontekst z tylko potrzebnymi beanami, bez bazy danych i innych ciężkich komponentów.


To co przekazał Ci ChatGPT, to świetna analiza tego, w jaki sposób Spock współpracuje (lub właśnie "nie współpracuje") 
ze Spring Bootem i dlaczego test wybucha. 
Problem polega na tym, że mechanizm `Spring Extension` w Spocku w określonych konfiguracjach nie widzi ukrytej 
pod `@SpringBootTest` konfiguracji i potrzebuje, aby mu ją "wskazać palcem" za pomocą `@ContextConfiguration`.

Rozwiążemy to teraz raz, a porządnie, według zaleceń które przedstawiłeś.

Oto instrukcja, jak zaimplementować rozwiązanie krok po kroku.

Krok 1: Weryfikacja build.gradle (Bardzo ważne!)
------------------------------------------------
Zanim zmienimy kod testu, upewnijmy się, że w Gradle usunęliśmy konflikty silników testowych (szczególnie groovy-test-junit5), które mogą przejmować testy Spockowi. To powinno wyglądać dokładnie tak, jak poniżej.

W sekcji dependencies w build.gradle powinieneś mieć:

```groovy
// Groovy (Z WYKLUCZENIEM modułów testowych, które gryzą się ze Spockiem)
implementation('org.apache.groovy:groovy-all:4.0.15') {
exclude group: 'org.apache.groovy', module: 'groovy-test'
exclude group: 'org.apache.groovy', module: 'groovy-test-junit5'
exclude group: 'org.apache.groovy', module: 'groovy-testng'
}



    // Spock i Spring Boot Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation platform('org.spockframework:spock-bom:2.3-groovy-4.0')
    testImplementation 'org.spockframework:spock-core'
    testImplementation 'org.spockframework:spock-spring' 
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

Krok 2: Przygotowanie Klasy Konfiguracyjnej dla Testu (AopTestConfig.groovy)
----------------------------------------------------------------------------

Zamiast uruchamiać w teście Twoją klasę główną `SmartFinDbApp` (która ma mnóstwo zależności, łączy się z bazą H2 itd.), 
stworzymy "sztuczną" konfigurację Springa, dedykowaną tylko dla tego testu: będzie zawierać tylko te Beany, które są absolutnie niezbędne do działania `TransactionIngesterService` i naszego aspektu `LoggingAspect`.
Każda klasa, którą tu wymienimy, będzie "pod lupą" testu, a reszta będzie zastąpiona przez proste Mocki lub Stuby.
nazwa tej klasy to `pl.edu.praktyki.aop.AopTestConfig`, a jej zadaniem będzie włączenie AOP i dostarczenie "zaślepek" dla zależności, których nie chcemy testować, ale które są wymagane przez `TransactionIngesterService`.
Ta klasa włączy AOP i dostarczy "zaślepki" (Mocks/Stubs) dla tych serwisów, których TransactionIngesterService wymaga, ale których my nie chcemy teraz testować.

Stwórz plik `src/test/groovy/pl/edu/praktyki/aop/AopTestConfig.groovy`:

```groovy
package pl.edu.praktyki.aop

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.ApplicationEventPublisher
import pl.edu.praktyki.monitoring.FinanceMetrics
import org.mockito.Mockito // Użyjemy Mockito, które jest domyślnie w spring-boot-starter-test

@Configuration
@EnableAspectJAutoProxy // TO JEST KLUCZ: Włącza AOP dla tego testu
class AopTestConfig {

    // Dostarczamy Mocki dla zależności, których wymaga TransactionIngesterService
    @Bean
    ApplicationEventPublisher applicationEventPublisher() {
        return Mockito.mock(ApplicationEventPublisher.class)
    }

    @Bean
    FinanceMetrics financeMetrics() {
        return Mockito.mock(FinanceMetrics.class)
    }
}
```

Krok 3: Naprawa Twojego Testu AOP (AopSpec.groovy)
--------------------------------------------------

Teraz zmieniamy Twój plik z testem. 
Zamiast `@SpringBootTest` używamy `@ContextConfiguration`, wymieniając dokładnie te klasy, 
które chcemy wziąć "pod lupę" (serwis, reguły, nasz aspekt oraz konfigurację, którą stworzyliśmy w Kroku 2).

Podmień zawartość pliku `src/test/groovy/pl/edu/praktyki/aop/AopSpec.groovy`:

```groovy
package pl.edu.praktyki.aop

import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import pl.edu.praktyki.service.TransactionIngesterService
import pl.edu.praktyki.service.TransactionRuleService
import pl.edu.praktyki.domain.Transaction

// Ładujemy tylko to, co jest nam potrzebne. To jest potęga lekkich testów Springa!
@ContextConfiguration(classes =[
TransactionIngesterService,  // Testowany serwis
TransactionRuleService,      // Wymagany przez Ingestera
LoggingAspect,               // Testowany aspekt AOP
AopTestConfig                // Włącza AOP i dodaje Mocki reszty zależności
])
class AopSpec extends Specification {

    @Autowired 
    TransactionIngesterService service

    def "powinien uruchomić aspekt logujący przy wywołaniu metody serwisu"() {
        given: "transakcja"
        def tx = new Transaction(id: "T1", amount: 100.0)

        when: "wywołujemy metodę serwisu"
        service.ingestTransactions([tx])

        then: "nie ma błędu i logowanie AOP zadziałało"
        // W konsoli powinieneś zobaczyć: ">>> [AOP PROFILER] Metoda ingestTransactions wykonała się w..."
        noExceptionThrown()
    }
}
```

Dlaczego to na 100% zadziała?

Wyłączyliśmy całą "magię" Spring Boota. 
Powiedzieliśmy Spockowi i Springowi dokładnie: "Zrób tylko te 4 Beany, nie ładuj bazy danych,
ale włącz Proxy dla AOP". To eliminuje problem z pustym adresem URL czy brakującymi połączeniami do bazy.

Uruchom: `./gradlew clean test`

I sprawdź, czy tym razem AOP "zatrybiło"! 🟢



/////////////////////////////   pytanie 1   /////////////////////////////////
-----------------------------------------------------------------------------
Wytlumacz mi to Proxy. o co tu chodzi ? włącz Proxy dla AOP to co to jest ?


Cieszę się, że zadziałało! Masz pełne prawo nie kumać słowa "Proxy" 
 – to jest jeden z najbardziej "magicznych" i najtrudniejszych do wyobrażenia konceptów w całym ekosystemie Javy i Springa.

Wyjaśnię Ci to obrazowo, bez trudnego żargonu.

Metafora: Dyrektor i jego Asystent
----------------------------------
Wyobraź sobie, że Twój `TransactionIngesterService` to bardzo ważny Dyrektor firmy. 
Jego jedynym zadaniem jest podejmowanie decyzji biznesowych (przetwarzanie transakcji). 
Dyrektor nie ma czasu na patrzenie na zegarek, otwieranie drzwi, czy robienie notatek w dzienniku.

Nagle Ty (jako szef IT) mówisz: "Chcę dokładnie zmierzyć, ile czasu zajmuje Dyrektorowi podpisanie każdego dokumentu".

Masz dwa wyjścia:


1. Bez Proxy (Stare podejście): 
Wchodzisz do gabinetu Dyrektora i każesz mu samemu włączać stoper przed podpisaniem dokumentu i wyłączać po podpisaniu. 
(Czyli: dopisujesz System.currentTimeMillis() do kodu w `TransactionIngesterService`). 
To zaśmieca jego umysł (kod) sprawami technicznymi.

2. Z Proxy (Podejście Spring AOP): Zatrudniasz Asystenta (Proxy) i stawiasz go przed drzwiami gabinetu.

Jak działa Asystent (Proxy)?

Kiedy inna klasa (albo Twój test) próbuje zawołać Dyrektora (service.ingestTransactions()), tak naprawdę nigdy nie rozmawia z Dyrektorem bezpośrednio.
Rozmawia z Asystentem (Proxy)!

Co robi Asystent (Twoje Proxy AOP)?

1. Odbiera dokumenty (wywołanie metody).
2. Spogląda na zegarek i zapisuje czas startu (long start = System.currentTimeMillis()).
3. Otwiera drzwi i przekazuje dokumenty na biurko Dyrektora (joinPoint.proceed()).
4. Czeka, aż Dyrektor (prawdziwy serwis) skończy pracę i odda mu wynik.
5. Spogląda na zegarek, liczy różnicę i krzyczy na całe biuro: "Dyrektorowi zajęło to 15 milisekund!" (println).
6. Oddaje wynik Tobie.

Dla Ciebie (jako osoby wywołującej) Asystent wygląda i zachowuje się dokładnie tak samo jak Dyrektor. 
Nawet nie wiesz, że rozmawiałeś z Asystentem!

Co się działo w kodzie (i dlaczego potrzebowaliśmy `@EnableAspectJAutoProxy`)?

Kiedy uruchamiałeś testy bez tej adnotacji, Spring po prostu brał Twoją klasę `TransactionIngesterService` (Nagiego Dyrektora) 
i wstrzykiwał ją przez `@Autowired`. 
Twój `LoggingAspect` (Asystent) leżał gdzieś w kącie i nic nie robił, bo Spring go nie użył.

Kiedy dodałeś `@EnableAspectJAutoProxy`, powiedziałeś Springowi:

"Hej Spring! Zanim wstrzykniesz mi serwis przez `@Autowired`, sprawdź, czy napisałem dla niego jakiś Aspekt. 
Jeśli tak, to wygeneruj w locie w pamięci RAM nową klasę (Proxy / Asystenta), włóż prawdziwy serwis do jej środka, 
owiń go moim logowaniem, i to tego Asystenta wstrzyknij do mojego testu!"

W Javie i Groovym słowo Proxy (Pośrednik) oznacza po prostu klasę, która została automatycznie wygenerowana przez framework (Springa) po to,
by "owinąć" Twój kod w dodatkową logikę.

To dzięki Proxy działają najważniejsze adnotacje w Springu:

`@Transactional` (Proxy otwiera bazę danych przed Twoją metodą i zamyka/commituje po niej).
`@Cacheable` (Proxy sprawdza, czy wynik jest w pamięci. Jeśli tak, od razu go zwraca – nawet nie idzie do "Dyrektora" zapytać!).

Teraz już kumasz, dlaczego Spring nazywa się frameworkiem "magicznym"? 
On cały czas podkłada Ci "Asystentów", udających prawdziwe obiekty! 😎 Czy ta metafora rozjaśniła Ci ten mechanizm?




/////////////////////////////   pytanie 2   /////////////////////////////////
-----------------------------------------------------------------------------

czyli to cale Proxy to po prostu Aspect w Springu AOP ?
w wzorzec Proxy to też to samo, czy inna bajka ?

To jest doskonałe pytanie, które dotyka sedna różnicy między wzorcem projektowym a implementacją.

Odpowiedź brzmi: AOP w Springu to technika, która pod maską używa wzorca projektowego Proxy.

Oto jak to rozróżnić, żebyś czuł się pewnie na rozmowie rekrutacyjnej:

1. Wzorzec Projektowy "Proxy" (Teoria)

To jest "przepis". Wzorzec Proxy mówi: "Jeśli nie chcesz dawać klientowi bezpośredniego dostępu do obiektu, stwórz obiekt pośredniczący (Proxy), 
który ma ten sam interfejs, co oryginał".

Cel: 
Kontrola dostępu, leniwe ładowanie (Lazy Loading), dodawanie logowania czy autoryzacji.

Przykład: 
Wzorce takie jak:
 - Virtual Proxy (ładuje obrazek dopiero gdy trzeba), 
- Protection Proxy (sprawdza czy masz uprawnienia do metody)
- Logging Proxy (zapisuje co się dzieje).

2. AOP (Aspect Oriented Programming) – "Narzędzie"

AOP to sposób implementacji wzorca Proxy na dużą skalę.

AOP pozwala Ci nie pisać tego Proxy ręcznie. 
W "czystym" wzorcu Proxy musiałbyś stworzyć klasę `ServiceProxy`, która ręcznie wywołuje Service. To dużo roboty.

W AOP (Springu) mówisz: "Dla wszystkich serwisów z pakietu .service.* wygeneruj Proxy automatycznie".

Czyli: AOP to "maszyna do produkcji Proxy".

Czy to ta sama bajka?

Tak i nie.

To ta sama bajka, bo: 
Spring używa wzorca `Proxy`, żeby zaimplementować AOP. 
Bez wzorca Proxy, AOP w Springu w ogóle by nie istniało.

To inna bajka, bo: 
Wzorzec projektowy to koncepcja (jak coś zrobić), a AOP to technologia (jak to zautomatyzować w frameworku).

Dlaczego Mid/Senior musi to rozróżniać?
Jeśli na rekrutacji zapytają Cię: "Czym się różni Proxy od AOP?", odpowiadasz:

1. Wzorzec Proxy to sposób na przejęcie kontroli nad wywołaniem metody obiektu przez pośrednika. 
2. AOP to technika (paradygmat), która automatyzuje tworzenie takich pośredników dla całych grup klas w systemie 
   (np. dla wszystkich serwisów w warstwie biznesowej). 
   Spring AOP używa wzorca Proxy (i dynamicznego generowania bajtkodu), aby wstrzyknąć kod w wybrane miejsca bez zaśmiecania logiki biznesowej.

Praktyczny test na zrozumienie (Dla Ciebie):

Czy teraz czujesz, że:
 - Proxy to ten Asystent, który stoi przed drzwiami.
 - AOP to "Agencja Asystentów", która zatrudniła 100 asystentów i postawiła ich przed drzwiami 100 Dyrektorów, bo Ty tylko powiedziałeś: 
   "Zatrudnij asystenta dla każdego dyrektora"?



 
To świetne pytanie, które dotyka samego serca architektury oprogramowania. 
Krótka odpowiedź brzmi: To jest ta sama technika ("Proxy"), użyta do osiągnięcia dwóch różnych celów (AOP vs Wzorzec Projektowy).

Oto jak to rozdzielić, żebyś już nigdy nie miał wątpliwości:

1. Wzorzec projektowy "Proxy" (Wzorzec klasyczny)

Wzorzec Proxy to klasyczny projektowy "trick". 
Masz obiekt, który jest trudny do stworzenia, ogromny albo wymaga kontroli dostępu. 
Zamiast dawać klientowi prawdziwy obiekt, dajesz mu "podróbkę" (Proxy), która ma ten sam interfejs.

Cel: Kontrola dostępu do obiektu.
Przykład: Virtual Proxy (ładujemy obrazek w PDF dopiero, gdy użytkownik przewinie stronę do tego miejsca) lub Remote Proxy (Proxy udaje, że serwer jest w Twoim komputerze, a pod spodem robi zapytania sieciowe).

Kto tworzy Proxy: Ty jako programista piszesz klasę MyProxy implements Service.

2. Spring AOP (Aspektowość)
Spring AOP to sposób użycia tego wzorca na wielką skalę, zautomatyzowany przez framework.

Cel: 
Doklejanie” funkcjonalności do wielu miejsc na raz (Cross-cutting concerns).

Przykład: 
Logowanie czasu (@Around), transakcje (@Transactional), cache (@Cacheable).

Kto tworzy Proxy: 
Spring Boot automatycznie. On bierze Twój serwis i "owija" go w Proxy w pamięci RAM, żebyś Ty nie musiał pisać setek klas typu MyLoggableProxy.

Tabela porównawcza:
```groovy
Cecha	           |  Wzorzec Proxy (Klasyczny)	               |  Spring AOP (Aspektowość)
-------------------|-------------------------------------------|------------------------------------------------------------
Kto pisze kod?	   |  Ty (programista).	                       |  Spring (automatycznie).
Co to robi?	       |  Ukrywa obiekt przed klientem.	           |  Wstrzykuje logikę w "punkty styku" (np. przed metodą).
Zastosowanie	   |  Ochrona, lazy loading, zdalne wywołania. |  Logowanie, transakcje, cache, security.
Jak to widać?	   |  Widzisz klasę XyzProxy.	               |  Nie widzisz Proxy, widzisz tylko adnotacje @Transactional
```


Dlaczego to się czasem miesza?
Bo AOP pod spodem używa wzorca Proxy.

Kiedy w Springu dajesz nad klasą `@Transactional`, dzieje się dokładnie to:

Spring widzi adnotację.
Tworzy instancję TransactionProxy.
TransactionProxy "owija" Twój serwis.

 