Lab 5
-----

Faza 3: Integracja z API i Waluty (Krok 5)

Problem: 
Twoje transakcje mogą być w różnych walutach (EUR, USD, PLN). 
Aby analiza miała sens, musimy przeliczyć wszystko na jedną walutę (np. PLN) przy użyciu aktualnych kursów z zewnętrznego API (np. NBP lub ExchangeRate-API).

Cel Kroku 5: 
Stworzenie serwisu, który pobierze kursy walut przez REST (używając HttpClient z Javy) i sparsuje je (używając JsonSlurper z Groovy).

5.1. Aktualizacja Modelu (Transaction.groovy)
Musimy wiedzieć, w jakiej walucie jest transakcja i ile wynosi po przeliczeniu.
Dodaj pola currency oraz amountPLN do klasy Transaction:

5.2. Serwis Walutowy (CurrencyService.groovy)
Ten serwis połączy się z darmowym API (użyjemy open.er-api.com), pobierze kursy i zwróci nam przelicznik.
Stwórz plik `src/main/groovy/pl/edu/praktyki/service/CurrencyService.groovy`:


5.3. Test Spock (CurrencyServiceSpec.groovy)
Sprawdźmy, czy potrafimy "dogadać się" z API.
Stwórz plik `src/test/groovy/pl/edu/praktyki/service/CurrencyServiceSpec.groovy`:

Co zyskujemy w tym kroku?
Real-world Integration: 
Twoja aplikacja przestaje być "zamkniętym pudełkiem" i zaczyna rozmawiać ze światem zewnętrznym.

Java-Groovy Synergy: 
Używamy najnowszego HttpClient z Javy (wydajność, standard) oraz JsonSlurper z Groovy (wygoda parsowania).

Data Normalization: 
To kluczowy etap przed generowaniem raportów (Faza 3, Krok 6).


Jeśli tak, to Krok 5 (ten z CurrencyService) jest Twoim "biletem wstępu" do rzetelnych statystyk.


