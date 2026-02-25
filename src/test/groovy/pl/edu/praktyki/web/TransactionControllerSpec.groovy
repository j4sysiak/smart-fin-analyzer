package pl.edu.praktyki.web

import org.springframework.test.context.ActiveProfiles // <-- Dodaj to
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper
import org.springframework.test.context.BootstrapWith
import org.springframework.test.context.ContextConfiguration
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.SmartFinDbApp
import groovy.json.JsonOutput

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@ActiveProfiles("test")
// Spock-Spring 2.3 nie wykrywa @SpringBootTest jako triggera dla integracji Spring.
// Dodajemy @ContextConfiguration, aby Spock aktywował SpringExtension.
// @SpringBootTest zapewnia pełny kontekst Spring Boot z MockMvc.
// Zastosujmy "podwójny trigger": @SpringBootTest (dla Springa) i @ContextConfiguration (dla Spocka),
// żeby mieć 100% pewności, że kontekst wstanie.
@SpringBootTest(classes = [SmartFinDbApp])
@ContextConfiguration
@AutoConfigureMockMvc
class TransactionControllerSpec extends Specification {

    @Autowired
    MockMvc mvc

    @Autowired
    TransactionRepository repository

    def setup() {
        // Przed każdym testem czyścimy bazę i dodajemy świeże dane
        repository.deleteAll()
        repository.save(new TransactionEntity(
                originalId: "T1", amountPLN: 100.0, category: "Test", description: "Wpływ"
        ))
        repository.save(new TransactionEntity(
                originalId: "T2", amountPLN: -20.0, category: "Jedzenie", description: "Pizza"
        ))
    }

    def "GET /api/transactions powinien zwrócić listę JSON"() {
        expect: "zapytanie zwraca status 200 OK i poprawne dane"
        mvc.perform(get("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.length()').value(2)) // Sprawdzamy rozmiar listy
                .andExpect(jsonPath('$[0].category').value("Test"))
                .andExpect(jsonPath('$[1].description').value("Pizza"))
    }

    def "GET /api/transactions/stats powinien zwrócić poprawne podsumowanie"() {
        expect: "statystyki są wyliczone poprawnie (100 - 20 = 80)"
        mvc.perform(get("/api/transactions/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.balance').value(80.0))
                .andExpect(jsonPath('$.count').value(2))
    }

    def "GET /api/transactions/{id} powinien zwrócić 404 dla nieistniejącego ID"() {
        expect: "próba pobrania rekordu nr 9999 kończy się błędem 404"
        mvc.perform(get("/api/transactions/9999"))
                .andExpect(status().isNotFound())
    }

    def "POST /api/transactions powinien zapisać transakcję, przeliczyć walutę i nadać tagi"() {
        given: "nowa transakcja w formacie JSON zbudowana za pomocą mapy Groovy"
        // Zobacz jak czysto to wygląda! Żadnych klas, po prostu definicja danych.
        def newTxPayload =[
                id: "NEW-1",
                amount: -50,
                currency: "USD",
                category: "Gry",
                description: "Zakup na Steam"
        ]
        String jsonBody = groovy.json.JsonOutput.toJson(newTxPayload)

        when: "wysyłamy żądanie POST"
        def response = mvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))

        then: "status HTTP to 201 Created"
        response.andExpect(status().isCreated())

        and: "waluta została przeliczona i zwrócona w odpowiedzi"
        // amountPLN nie wysyłaliśmy! API samo to policzyło. Sprawdzamy czy istnieje (nie jest null)
        response.andExpect(jsonPath('$.amountPLN').exists())

        and: "transakcja otrzymała tag (ponieważ -50 USD to więcej niż -100 PLN)"
        response.andExpect(jsonPath('$.tags[0]').value("BIG_SPENDER"))

        and: "baza danych powiększyła się o nowy rekord"
        repository.findAll().size() == 3 // 2 z metody setup() + 1 nowy
    }
}