package pl.edu.praktyki.integration

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.TransactionRepository
import spock.lang.Requires

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
@ActiveProfiles('tc')
@Requires({ !Boolean.getBoolean('local.pg') })
class TcFlywayEndToEndSpec extends BaseIntegrationSpec {

    @Autowired
    MockMvc mvc

    @Autowired
    TransactionRepository repo

    def "tc uruchamia pełne migracje Flyway i tworzy kompletny schemat"() {
        when: "pobieramy diagnostykę środowiska i stan migracji"
        def dbInfoJson = mvc.perform(get('/internal/db-info'))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn().response.contentAsString
        def dbInfo = new JsonSlurper().parseText(dbInfoJson) as Map

        def expectedVersions = expectedFlywayVersions()
        def migratedVersions = jdbcTemplate.queryForList(
                '''
                SELECT version
                FROM flyway_schema_history
                WHERE success = true AND version IS NOT NULL
                ORDER BY installed_rank
                '''.stripIndent(),
                String
        ).collect { (it as String).toInteger() }

        then: "aplikacja działa na profilu tc z Flyway włączonym i bez Hibernate DDL"
        dbInfo.activeProfiles.contains('tc')
        dbInfo.flywayEnabled == 'true'
        dbInfo.jpaDdlAuto == 'none'
        (dbInfo.datasourceUrl as String).contains('jdbc:postgresql://localhost:15432/testdb')
        dbInfo.datasourceUsername == 'test'

        and: "w bazie wykonano wszystkie migracje wersjonowane z classpath"
        expectedVersions
        migratedVersions == expectedVersions
        jdbcTemplate.queryForObject(
                'SELECT COUNT(*) FROM flyway_schema_history WHERE success = false',
                Long
        ) == 0L

        and: "kluczowe obiekty stworzone przez migracje istnieją"
        tableExists('users')
        tableExists('transactions')
        tableExists('transactions_aud')
        tableExists('categories')
        tableExists('categories_aud')
        tableExists('revinfo')
        tableExists('monthly_settlement')
        tableExists('flyway_schema_history')
        sequenceExists('tx_seq')
        sequenceExists('revinfo_seq')
        columnExists('transactions', 'tags')
        columnExists('transactions', 'owner_username')
        columnExists('transactions_aud', 'owner_username')

        and: "stary join-table po migracji V14 już nie istnieje"
        !tableExists('transaction_entity_tags')

        and: "dane startowe z migracji użytkowników są obecne"
        jdbcTemplate.queryForObject('SELECT COUNT(*) FROM users WHERE username = ?', Long, 'admin') == 1L
        jdbcTemplate.queryForObject('SELECT COUNT(*) FROM users WHERE username = ?', Long, 'user') == 1L
    }

    def "tc end-to-end: Flyway admin login, publiczna rejestracja usera, upload admina, odczyt i update ownera przez REST"() {
        given: "admin pochodzi z pełnych migracji Flyway, a owner jest tworzony przez publiczne API"
        repo.deleteAll()
        String adminToken = loginAndGetToken('admin', 'admin123')
        String ownerUsername = "tc_e2e_user_${System.nanoTime()}"
        String ownerPassword = 'tc-e2e-pass-123'
        registerUser(ownerUsername, ownerPassword)
        String userToken = loginAndGetToken(ownerUsername, ownerPassword)
        String originalId = "TC-E2E-${System.nanoTime()}"
        def csv = "id,amount,currency,category,description,date\n${originalId},120.00,PLN,Zakupy,Myszka e2e,2026-04-12\n".getBytes('UTF-8')
        def file = new MockMultipartFile('file', 'tc-e2e.csv', 'text/csv', csv)

        when: "admin robi upload dla nowo zarejestrowanego ownera"
        mvc.perform(multipart('/api/transactions/upload')
                .file(file)
                .param('user', ownerUsername)
                .header('Authorization', "Bearer $adminToken"))
                .andDo(print())
                .andExpect(status().isOk())

        and: "odnajdujemy zapisany rekord biznesowy"
        def saved = repo.findByOriginalId(originalId)

        then: "upload utworzył jedną transakcję należącą do ownera"
        saved.size() == 1
        saved[0].ownerUsername == ownerUsername

        when: "owner pobiera swój rekord po dbId"
        mvc.perform(get("/api/transactions/${saved[0].dbId}")
                .header('Authorization', "Bearer $userToken"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.id').value(originalId))
                .andExpect(jsonPath('$.ownerUsername').value(ownerUsername))
                .andExpect(jsonPath('$.description').value('Myszka e2e'))

        and: "owner aktualizuje swój rekord przez publiczne API"
        def updatePayload = JsonOutput.toJson([
                id         : originalId,
                amount     : 135.00,
                currency   : 'PLN',
                category   : 'Zakupy',
                description: 'Myszka e2e po PUT'
        ])

        mvc.perform(put("/api/transactions/${saved[0].dbId}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload)
                .header('Authorization', "Bearer $userToken"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.id').value(originalId))
                .andExpect(jsonPath('$.ownerUsername').value(ownerUsername))
                .andExpect(jsonPath('$.amount').value(135.0))
                .andExpect(jsonPath('$.amountPLN').value(135.0))
                .andExpect(jsonPath('$.description').value('Myszka e2e po PUT'))

        then: "rekord biznesowy i audyt odzwierciedlają pełny flow end-to-end"
        def businessRows = jdbcTemplate.queryForList(
                '''
                SELECT owner_username, amount, description
                FROM transactions
                WHERE db_id = ?
                '''.stripIndent(),
                saved[0].dbId
        )
        businessRows.size() == 1
        businessRows[0].owner_username == ownerUsername
        (businessRows[0].amount as BigDecimal) == 135.00G
        businessRows[0].description == 'Myszka e2e po PUT'

        and: "manualny audit ma insert i update z zachowanym owner_username"
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
        auditRows*.owner_username == [ownerUsername, ownerUsername]
        (auditRows[0].amount as BigDecimal) == 120.00G
        auditRows[0].description == 'Myszka e2e'
        (auditRows[1].amount as BigDecimal) == 135.00G
        auditRows[1].description == 'Myszka e2e po PUT'
    }

    private void registerUser(String username, String password) {
        mvc.perform(post('/api/auth/register')
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonOutput.toJson([
                        username: username,
                        password: password,
                        role    : 'ROLE_USER'
                ])))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath('$.username').value(username))
                .andExpect(jsonPath('$.role').value('ROLE_USER'))
    }

    private String loginAndGetToken(String username, String password) {
        def response = mvc.perform(post('/api/auth/login')
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonOutput.toJson([
                        username: username,
                        password: password
                ])))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn().response.contentAsString

        return (new JsonSlurper().parseText(response).token as String)
    }

    private List<Integer> expectedFlywayVersions() {
        def resolver = new PathMatchingResourcePatternResolver()
        return resolver.getResources('classpath*:db/migration/V*.sql')
                .collect { resource ->
                    def matcher = (resource.filename =~ /^V(\d+)__.*\.sql$/)
                    assert matcher.matches(): "Nie udało się sparsować wersji migracji z ${resource.filename}"
                    matcher.group(1).toInteger()
                }
                .unique()
                .sort()
    }

    private boolean tableExists(String tableName) {
        jdbcTemplate.queryForObject(
                '''
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = ?
                )
                '''.stripIndent(),
                Boolean,
                tableName
        )
    }

    private boolean columnExists(String tableName, String columnName) {
        jdbcTemplate.queryForObject(
                '''
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                )
                '''.stripIndent(),
                Boolean,
                tableName,
                columnName
        )
    }

    private boolean sequenceExists(String sequenceName) {
        jdbcTemplate.queryForObject(
                '''
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.sequences
                    WHERE sequence_schema = 'public' AND sequence_name = ?
                )
                '''.stripIndent(),
                Boolean,
                sequenceName
        )
    }
}

