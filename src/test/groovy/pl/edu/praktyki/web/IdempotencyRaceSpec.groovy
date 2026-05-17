package pl.edu.praktyki.web

import groovy.json.JsonOutput
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.repository.CategoryEntity
import pl.edu.praktyki.repository.CategoryRepository
import pl.edu.praktyki.repository.TransactionRepository

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

@AutoConfigureMockMvc
@ActiveProfiles("tc")
class IdempotencyRaceSpec extends BaseIntegrationSpec {

    @Autowired MockMvc mvc
    @Autowired TransactionRepository repo
    @Autowired CategoryRepository categoryRepository

    def "powinien zapisac tylko jeden rekord przy dwóch równoległych POST tego samego payloadu"() {
        given:
        categoryRepository.saveAndFlush(
                new CategoryEntity(name: "RaceTest", monthlyLimit: 0.0G)
        )

        String username = "user_race"

        def payload = JsonOutput.toJson([
                id         : "RACE-UNIQUE-001",
                amount     : 123.45,
                currency   : "PLN",
                category   : "RaceTest",
                description: "Parallel idempotency check"
        ])

        ExecutorService pool = Executors.newFixedThreadPool(2)
        CountDownLatch ready = new CountDownLatch(2)
        CountDownLatch start = new CountDownLatch(1)

        Closure<MvcResult> task = {
            ready.countDown()
            assert start.await(5, TimeUnit.SECONDS)

            return mvc.perform(post("/api/transactions")
                    .with(user(username).roles("USER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andReturn()
        }




        when:
        Future<MvcResult> f1 = pool.submit({ task.call() } as java.util.concurrent.Callable<MvcResult>)
        Future<MvcResult> f2 = pool.submit({ task.call() } as java.util.concurrent.Callable<MvcResult>)

        assert ready.await(5, TimeUnit.SECONDS)
        start.countDown()

        MvcResult r1 = f1.get(10, TimeUnit.SECONDS)
        MvcResult r2 = f2.get(10, TimeUnit.SECONDS)

        then:
        // po naszej implementacji oba requesty powinny być skuteczne (idempotentny efekt)
        r1.response.status in [200, 201]
        r2.response.status in [200, 201]

        and:
        repo.findAllByOwnerUsername(
                username,
                org.springframework.data.domain.Pageable.unpaged()
        ).totalElements == 1

        cleanup:
        pool.shutdownNow()
    }
}