Lab 34
------

Lab 34 (@ControllerAdvice) to absolutny standard w korporacyjnym Spring Boocie. 
Zamiast rzucać klientowi w twarz surowym HTML-em z błędem z Tomcata (tzw. Whitelabel Error Page), 
zwrócimy elegancki, ustandaryzowany JSON.

Oto co musisz wdrożyć (zbieram to w jedną, wygodną paczkę, żebyś nie musiał scrollować do góry):

Krok 1: DTO dla błędu (ApiError.groovy)
---------------------------------------

Stwórz plik `src/main/groovy/pl/edu/praktyki/web/ApiError.groovy`:

Krok 2: Klasa przechwytująca błędy (GlobalExceptionHandler.groovy)
------------------------------------------------------------------

Stwórz plik `src/main/groovy/pl/edu/praktyki/web/GlobalExceptionHandler.groovy`. 
Ta klasa będzie "stała na straży" wszystkich kontrolerów w aplikacji.

Krok 3: Weryfikacja w teście (TransactionControllerSpec.groovy)
---------------------------------------------------------------

Skoro zmieniliśmy format odpowiedzi z błędem na obiekt ApiError, musimy uaktualnić nasz test, 
aby sprawdzał strukturę tego JSON-a.
Znajdź w `TransactionControllerSpec.groovy` test dla GET /api/transactions/{id} (404) i podmień go na ten:

Co się teraz wydarzy w teście?
------------------------------
1. MockMvc woła /api/transactions/9999.
2. Repozytorium nie znajduje rekordu.
3. Kontroler rzuca `ResponseStatusException`.
4. `GlobalExceptionHandler` łapie ten wyjątek "w locie".
5. Handler buduje obiekt `ApiError` i odsyła go z kodem 404 i pięknym JSON-em.
6. Test Spocka weryfikuje zawartość JSON-a i świeci na zielono.



a gdzie jest ten JSON ?
------------------------

Świetne pytanie! Skoro test przeszedł, to znaczy, że JSON gdzieś tam był, ale Spock i Spring domyślnie robią to po cichu, 
w pamięci RAM, żeby nie zaśmiecać konsoli logami.

Jeśli chcesz zobaczyć ten wygenerowany JSON na własne oczy (co jest zresztą genialną techniką debugowania!), masz dwie drogi:

Sposób 1: Zobacz go w teście (Magia MockMvc)
--------------------------------------------

Możemy kazać naszemu testowi "wydrukować" całe żądanie i odpowiedź na konsolę. Służy do tego metoda .andDo(print()).

Wejdź do `TransactionControllerSpec.groovy`, dodaj import na górze:


import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print

I dodaj linijkę z print() do swojego testu:

```groovy
    def "GET /api/transactions/{id} powinien zwrócić 404 w ustandaryzowanym formacie ApiError"() {
    expect: "próba pobrania rekordu 9999 kończy się ustandaryzowanym błędem"
    mvc.perform(get("/api/transactions/9999"))
            .andDo(print()) // <--- TA LINIJKA WYDRUKUJE WSZYSTKO NA EKRAN!
            .andExpect(status().isNotFound())
            // Sprawdzamy nową strukturę z klasy ApiError:
            .andExpect(jsonPath('$.status').value(404))
            .andExpect(jsonPath('$.message').value("Transakcja o ID 9999 nie istnieje"))
            .andExpect(jsonPath('$.timestamp').exists())
}
```


Gdy odpalisz ten pojedynczy test w IntelliJ (lub w terminalu poleceniem test), 
w logach konsoli zobaczysz wielki blok tekstu, a na jego końcu coś takiego:

```text
MockHttpServletResponse:
Status = 404
Error message = null
Headers = [Content-Type:"application/json"]
Content type = application/json
Body = {"status":404,"message":"Transakcja o ID 9999 nie istnieje","timestamp":"2026-02-26T22:10:05.123"}
```

To Body to jest właśnie Twój piękny JSON wygenerowany przez GlobalExceptionHandler!

Sposób 2: 
Zobacz go "na żywo" w przeglądarce

Zróbmy to tak, jak widziałby to frontendowiec łączący się z Twoim systemem:

Uruchom aplikację na stałe:

./gradlew runSmartFinDb -PappArgs="-u Jacek"

Otwórz przeglądarkę i wpisz wymyślony adres (ID, którego nie ma w bazie):
👉 http://localhost:8080/api/transactions/9999

Na ekranie w przeglądarce wyświetli Ci się dokładnie ten JSON:

```json
{
"status": 404,
"message": "Transakcja o ID 9999 nie istnieje",
"timestamp": "2026-02-26T22:15:30.555"
}
```


Zanim stworzyliśmy GlobalExceptionHandler, Spring rzuciłby tutaj ohydną, białą stroną z napisem "Whitelabel Error Page" 
i tysiącem linii kodu błędu Javy. 
Teraz Twoje API zwraca elegancki komunikat.

Użycie .andDo(print()) w testach REST to jedna z najlepszych praktyk. Daj znać, czy udało Ci się "zobaczyć" ten plik! 👀