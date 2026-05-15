package pl.edu.praktyki.web

import groovy.json.JsonOutput
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.security.JwtService

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc // <--- To tworzy beana 'mvc'
@ActiveProfiles("tc") // Używamy Testcontainers dla pełnej izolacji
class DataIsolationSpec extends BaseIntegrationSpec {

    @Autowired
    MockMvc mvc

    @Autowired
    JwtService jwtService

    @Autowired
    TransactionRepository repo

    def "użytkownik powinien widzieć tylko swoje transakcje"() {
        given: "mamy dwóch użytkowników i ich dane w bazie"
        repo.deleteAll()

        // Zapisujemy transakcje z różnymi właścicielami
        repo.save(new TransactionEntity(originalId: "TX-USER-1", ownerUsername: "user1", amountPLN: 100.0))
        repo.save(new TransactionEntity(originalId: "TX-USER-2", ownerUsername: "user2", amountPLN: 200.0))

        when: "logujemy się jako user1"
        def token = jwtService.generateToken("user1", ["ROLE_USER"])

        def response = mvc.perform(get("/api/transactions")
                .header("Authorization", "Bearer $token"))
                .andDo(print())

        then: "widzimy tylko transakcję należącą do user1"
        response.andExpect(status().isOk())
                .andExpect(jsonPath('$.content.length()').value(1))
                .andExpect(jsonPath('$.content[0].id').value("TX-USER-1"))

        and: "nie widzimy danych użytkownika user2"
        !response.andReturn().response.contentAsString.contains("TX-USER-2")
    }

    def "owner widzi swój rekord po dbId"() {
        given: "w bazie jest rekord należący do user1"
        repo.deleteAll()
        def saved = repo.saveAndFlush(new TransactionEntity(
                originalId: "TX-OWNER-1",
                ownerUsername: "user1",
                amountPLN: 123.45,
                currency: "PLN"
        ))

        and: "mamy token właściciela"
        def token = jwtService.generateToken("user1", ["ROLE_USER"])

        expect: "GET po dbId zwraca 200 i właściwy rekord"
        mvc.perform(get("/api/transactions/${saved.dbId}")
                .header("Authorization", "Bearer $token"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.id').value("TX-OWNER-1"))
                .andExpect(jsonPath('$.ownerUsername').value("user1"))
    }

    def "user nie widzi cudzego rekordu po dbId"() {
        given: "w bazie jest rekord należący do user2"
        repo.deleteAll()
        def foreignTx = repo.saveAndFlush(new TransactionEntity(
                originalId: "TX-FOREIGN-1",
                ownerUsername: "user2",
                amountPLN: 222.22,
                currency: "PLN"
        ))

        and: "mamy token innego użytkownika"
        def token = jwtService.generateToken("user1", ["ROLE_USER"])

        expect: "GET po dbId zwraca 404, żeby nie ujawniać cudzego rekordu"
        mvc.perform(get("/api/transactions/${foreignTx.dbId}")
                .header("Authorization", "Bearer $token"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.status').value(404))
    }

    def "brak rekordu daje 404"() {
        given: "mamy token zalogowanego użytkownika"
        def token = jwtService.generateToken("user1", ["ROLE_USER"])

        expect: "nieistniejące dbId zwraca 404"
        mvc.perform(get('/api/transactions/999999')
                .header("Authorization", "Bearer $token"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.status').value(404))
    }

    def "owner po uploadzie aktualizuje swoją transakcję przez REST, a audit revtype 1 zachowuje owner_username"() {
        given: "admin uploaduje CSV dla user1 przez publiczne API"
        repo.deleteAll()
        String originalId = "TX-UP-REST-${System.nanoTime()}"
        def csv = "id,amount,currency,category,description,date\n${originalId},120.00,PLN,Zakupy,Myszka,2026-04-12\n".getBytes('UTF-8')
        def file = new MockMultipartFile("file", "upload.csv", "text/csv", csv)
        def adminToken = jwtService.generateToken("boss", ["ROLE_ADMIN"])

        when: "admin robi upload w imieniu user1"
        mvc.perform(multipart("/api/transactions/upload")
                .file(file)
                .param("user", "user1")
                .header("Authorization", "Bearer $adminToken"))
                .andDo(print())
                .andExpect(status().isOk())

        and: "odnajdujemy dbId wgranej transakcji"
        def saved = repo.findByOriginalId(originalId)

        then: "upload zapisał dokładnie jedną transakcję user1"
        saved.size() == 1
        saved[0].ownerUsername == "user1"

        when: "właściciel aktualizuje tę samą transakcję przez PUT REST"
        def ownerToken = jwtService.generateToken("user1", ["ROLE_USER"])
        def updatePayload = JsonOutput.toJson([
                id         : originalId,
                amount     : 130.00,
                currency   : "PLN",
                category   : "Zakupy",
                description: "Myszka po REST update"
        ])

        mvc.perform(put("/api/transactions/${saved[0].dbId}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .header("Authorization", "Bearer $ownerToken"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.id').value(originalId))
                .andExpect(jsonPath('$.ownerUsername').value("user1"))
                .andExpect(jsonPath('$.amount').value(130.0))
                .andExpect(jsonPath('$.amountPLN').value(130.0))
                .andExpect(jsonPath('$.description').value("Myszka po REST update"))

        then: "rekord biznesowy nadal należy do tego samego ownera"
        def businessRows = jdbcTemplate.queryForList(
                '''
                SELECT owner_username, amount, description
                FROM transactions
                WHERE db_id = ?
                '''.stripIndent(),
                saved[0].dbId
        )
        businessRows.size() == 1
        businessRows[0].owner_username == 'user1'
        (businessRows[0].amount as BigDecimal) == 130.00G
        businessRows[0].description == 'Myszka po REST update'

        and: "audit zawiera revtype 0 i revtype 1 z tym samym owner_username"
        def auditRows = jdbcTemplate.queryForList(
                '''
                SELECT revtype, owner_username, amount, description
                FROM transactions_aud
                WHERE db_id = ?
                ORDER BY rev
                '''.stripIndent(),
                saved[0].dbId
        )
        auditRows.size() == 2
        auditRows*.revtype == [0, 1]
        auditRows*.owner_username == ['user1', 'user1']
        (auditRows[0].amount as BigDecimal) == 120.00G
        auditRows[0].description == 'Myszka'
        (auditRows[1].amount as BigDecimal) == 130.00G
        auditRows[1].description == 'Myszka po REST update'
    }

    def "inny user nie może edytować cudzego rekordu przez REST, dostaje 404 i nie tworzy revtype 1"() {
        given: "admin uploaduje CSV dla user2 przez publiczne API"
        repo.deleteAll()
        String originalId = "TX-UP-FOREIGN-${System.nanoTime()}"
        def csv = "id,amount,currency,category,description,date\n${originalId},120.00,PLN,Zakupy,Myszka,2026-04-12\n".getBytes('UTF-8')
        def file = new MockMultipartFile("file", "upload.csv", "text/csv", csv)
        def adminToken = jwtService.generateToken("boss", ["ROLE_ADMIN"])

        when: "admin robi upload w imieniu user2"
        mvc.perform(multipart("/api/transactions/upload")
                .file(file)
                .param("user", "user2")
                .header("Authorization", "Bearer $adminToken"))
                .andDo(print())
                .andExpect(status().isOk())

        and: "odnajdujemy dbId wgranej transakcji"
        def saved = repo.findByOriginalId(originalId)

        then: "upload zapisał dokładnie jedną transakcję user2"
        saved.size() == 1
        saved[0].ownerUsername == "user2"

        when: "obcy user próbuje wykonać PUT na cudzym rekordzie"
        def foreignToken = jwtService.generateToken("user1", ["ROLE_USER"])
        def foreignUpdatePayload = JsonOutput.toJson([
                id         : originalId,
                amount     : 999.00,
                currency   : "PLN",
                category   : "Zakupy",
                description: "Niedozwolona próba update"
        ])

        def response = mvc.perform(put("/api/transactions/${saved[0].dbId}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(foreignUpdatePayload)
                .header("Authorization", "Bearer $foreignToken"))
                .andDo(print())

        then: "obcy user dostaje 404"
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath('$.status').value(404))

        and: "rekord biznesowy nie został zmieniony"
        def businessRows = jdbcTemplate.queryForList(
                '''
                SELECT owner_username, amount, description
                FROM transactions
                WHERE db_id = ?
                '''.stripIndent(),
                saved[0].dbId
        )
        businessRows.size() == 1
        businessRows[0].owner_username == 'user2'
        (businessRows[0].amount as BigDecimal) == 120.00G
        businessRows[0].description == 'Myszka'

        and: "nie powstała żadna rewizja revtype 1 dla cudzego update"
        def auditRows = jdbcTemplate.queryForList(
                '''
                SELECT revtype, owner_username, amount, description
                FROM transactions_aud
                WHERE db_id = ?
                ORDER BY rev
                '''.stripIndent(),
                saved[0].dbId
        )
        auditRows.size() == 1
        auditRows*.revtype == [0]
        auditRows*.owner_username == ['user2']
        (auditRows[0].amount as BigDecimal) == 120.00G
        auditRows[0].description == 'Myszka'

        and: "licznik rewizji revtype 1 wynosi zero"
        jdbcTemplate.queryForObject(
                'SELECT COUNT(*) FROM transactions_aud WHERE db_id = ? AND revtype = 1',
                Long,
                saved[0].dbId
        ) == 0L
    }

    def "owner może edytować swój rekord po zwykłym POST api transactions bez ścieżki uploadowej"() {
        given: "zalogowany user1 tworzy własną transakcję przez zwykły POST"
        repo.deleteAll()
        String originalId = "TX-POST-PUT-${System.nanoTime()}"
        def ownerToken = jwtService.generateToken("user1", ["ROLE_USER"])
        def createPayload = JsonOutput.toJson([
                id         : originalId,
                amount     : 120.00,
                currency   : "PLN",
                category   : "Zakupy",
                description: "Transakcja utworzona przez POST"
        ])

        when: "user1 wykonuje POST /api/transactions"
        mvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .header("Authorization", "Bearer $ownerToken"))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath('$.id').value(originalId))
                .andExpect(jsonPath('$.ownerUsername').value("user1"))

        and: "odnajdujemy dbId nowo utworzonej transakcji"
        def saved = repo.findByOriginalId(originalId)

        then: "POST zapisał dokładnie jedną transakcję należącą do user1"
        saved.size() == 1
        saved[0].ownerUsername == "user1"
        (saved[0].amount as BigDecimal) == 120.00G
        saved[0].description == "Transakcja utworzona przez POST"

        when: "ten sam owner aktualizuje swój rekord przez PUT"
        def updatePayload = JsonOutput.toJson([
                id         : originalId,
                amount     : 135.00,
                currency   : "PLN",
                category   : "Zakupy",
                description: "Transakcja po zwykłym PUT"
        ])

        mvc.perform(put("/api/transactions/${saved[0].dbId}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .header("Authorization", "Bearer $ownerToken"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.id').value(originalId))
                .andExpect(jsonPath('$.ownerUsername').value("user1"))
                .andExpect(jsonPath('$.amount').value(135.0))
                .andExpect(jsonPath('$.amountPLN').value(135.0))
                .andExpect(jsonPath('$.description').value("Transakcja po zwykłym PUT"))

        then: "rekord biznesowy nadal należy do ownera i ma nowe wartości"
        def businessRows = jdbcTemplate.queryForList(
                '''
                SELECT owner_username, amount, description
                FROM transactions
                WHERE db_id = ?
                '''.stripIndent(),
                saved[0].dbId
        )
        businessRows.size() == 1
        businessRows[0].owner_username == 'user1'
        (businessRows[0].amount as BigDecimal) == 135.00G
        businessRows[0].description == 'Transakcja po zwykłym PUT'

        and: "audit zawiera insert i update z tym samym owner_username"
        def auditRows = jdbcTemplate.queryForList(
                '''
                SELECT revtype, owner_username, amount, description
                FROM transactions_aud
                WHERE db_id = ?
                ORDER BY rev
                '''.stripIndent(),
                saved[0].dbId
        )
        auditRows.size() == 2
        auditRows*.revtype == [0, 1]
        auditRows*.owner_username == ['user1', 'user1']
        (auditRows[0].amount as BigDecimal) == 120.00G
        auditRows[0].description == 'Transakcja utworzona przez POST'
        (auditRows[1].amount as BigDecimal) == 135.00G
        auditRows[1].description == 'Transakcja po zwykłym PUT'
    }

    def "rekord utworzony zwykłym POST nie może być edytowany przez innego użytkownika"() {
        given: "user1 tworzy własną transakcję przez zwykły POST"
        repo.deleteAll()
        String originalId = "TX-POST-FOREIGN-${System.nanoTime()}"
        def ownerToken = jwtService.generateToken("user1", ["ROLE_USER"])
        def createPayload = JsonOutput.toJson([
                id         : originalId,
                amount     : 120.00,
                currency   : "PLN",
                category   : "Zakupy",
                description: "Transakcja utworzona przez POST"
        ])

        when: "user1 wykonuje POST /api/transactions"
        mvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload)
                .header("Authorization", "Bearer $ownerToken"))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath('$.id').value(originalId))
                .andExpect(jsonPath('$.ownerUsername').value("user1"))

        and: "odnajdujemy dbId nowo utworzonej transakcji"
        def saved = repo.findByOriginalId(originalId)

        then: "POST zapisał dokładnie jedną transakcję należącą do user1"
        saved.size() == 1
        saved[0].ownerUsername == "user1"
        (saved[0].amount as BigDecimal) == 120.00G
        saved[0].description == "Transakcja utworzona przez POST"

        when: "inny user próbuje wykonać PUT na cudzym rekordzie"
        def foreignToken = jwtService.generateToken("user2", ["ROLE_USER"])
        def foreignUpdatePayload = JsonOutput.toJson([
                id         : originalId,
                amount     : 999.00,
                currency   : "PLN",
                category   : "Zakupy",
                description: "Niedozwolona próba update po POST"
        ])

        def response = mvc.perform(put("/api/transactions/${saved[0].dbId}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(foreignUpdatePayload)
                .header("Authorization", "Bearer $foreignToken"))
                .andDo(print())

        then: "obcy user dostaje 404"
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath('$.status').value(404))

        and: "rekord biznesowy nie został zmieniony"
        def businessRows = jdbcTemplate.queryForList(
                '''
                SELECT owner_username, amount, description
                FROM transactions
                WHERE db_id = ?
                '''.stripIndent(),
                saved[0].dbId
        )
        businessRows.size() == 1
        businessRows[0].owner_username == 'user1'
        (businessRows[0].amount as BigDecimal) == 120.00G
        businessRows[0].description == 'Transakcja utworzona przez POST'

        and: "audit zawiera wyłącznie insert bez rewizji update"
        def auditRows = jdbcTemplate.queryForList(
                '''
                SELECT revtype, owner_username, amount, description
                FROM transactions_aud
                WHERE db_id = ?
                ORDER BY rev
                '''.stripIndent(),
                saved[0].dbId
        )
        auditRows.size() == 1
        auditRows*.revtype == [0]
        auditRows*.owner_username == ['user1']
        (auditRows[0].amount as BigDecimal) == 120.00G
        auditRows[0].description == 'Transakcja utworzona przez POST'

        and: "liczba rewizji revtype 1 pozostaje równa zero"
        jdbcTemplate.queryForObject(
                'SELECT COUNT(*) FROM transactions_aud WHERE db_id = ? AND revtype = 1',
                Long,
                saved[0].dbId
        ) == 0L
    }
}