package pl.edu.praktyki.integration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import pl.edu.praktyki.BaseIntegrationSpec
import pl.edu.praktyki.domain.CategoryUpsertRequest
import pl.edu.praktyki.repository.CategoryRepository
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.web.CategoryController

import java.time.LocalDate

class CategoryControllerDatabaseSpec extends BaseIntegrationSpec {

    @Autowired CategoryController categoryController
    @Autowired CategoryRepository categoryRepository
    @Autowired TransactionRepository transactionRepository

    def setup() {
        SecurityContextHolder.clearContext()
    }

    def cleanup() {
        SecurityContextHolder.clearContext()
    }


    def "admin can rename category, update limit and sync transaction category text"() {
        given:
        def originalName = uniqueName('CAT_UPDATE')
        def updatedName = "${originalName}_RENAMED"
        adminAuth()

        def created = categoryController.createCategory(new CategoryUpsertRequest(
                name: originalName,
                monthlyLimit: 100.00G
        ))

        def savedTx = transactionRepository.saveAndFlush(new TransactionEntity(
                originalId: "TX-${System.nanoTime()}",
                date: LocalDate.now(),
                amount: 10.00G,
                currency: 'PLN',
                amountPLN: 10.00G,
                categoryEntity: categoryRepository.findById(created.id).get(),
                category: originalName,
                description: 'test',
                ownerUsername: 'admin'
        ))

        when:
        def updated = categoryController.updateCategory(created.id, new CategoryUpsertRequest(
                name: updatedName,
                monthlyLimit: 250.75G
        ))

        then:
        updated.id == created.id
        updated.name == updatedName
        updated.monthlyLimit == 250.75G

        and:
        categoryRepository.findById(created.id).get().name == updatedName
        categoryRepository.findById(created.id).get().monthlyLimit == 250.75G

        and: 'denormalized transaction.category is synchronized after rename'
        jdbcTemplate.queryForObject(
                'SELECT category FROM transactions WHERE db_id = ?',
                String,
                savedTx.dbId
        ) == updatedName

        and: 'audyt kategorii ma wpis insert + update'
        def auditRows = jdbcTemplate.queryForList(
                'SELECT rev, revtype, name, monthly_limit FROM categories_aud WHERE id = ? ORDER BY rev',
                created.id
        )
        auditRows.size() == 2
        auditRows[0].revtype == 0
        auditRows[0].name == originalName
        (auditRows[0].monthly_limit as BigDecimal) == 100.00G
        auditRows[1].revtype == 1
        auditRows[1].name == updatedName
        (auditRows[1].monthly_limit as BigDecimal) == 250.75G

        cleanup:
        SecurityContextHolder.clearContext()
    }


    def "admin can create category and Envers writes insert row"() {
        given:
        def categoryName = "CAT_CREATE_${System.nanoTime()}"
        def request = new CategoryUpsertRequest(name: categoryName, monthlyLimit: 321.50G)
        adminAuth()

        when:
        def dto = categoryController.createCategory(request)

        then:
        dto.id != null
        dto.name == categoryName
        dto.monthlyLimit == 321.50G

        and:
        categoryRepository.findByName(categoryName).isPresent()

        and:
        def auditRows = jdbcTemplate.queryForList(
                'SELECT rev, revtype, name, monthly_limit FROM categories_aud WHERE id = ? ORDER BY rev',
                dto.id
        )
        auditRows.size() == 1
        auditRows[0].revtype == 0
        auditRows[0].name == categoryName
        (auditRows[0].monthly_limit as BigDecimal) == 321.50G
    }

    def "regular user cannot manage categories"() {
        given:
        adminAuth('user', 'ROLE_USER')

        when:
        categoryController.getAllCategories()

        then:
        thrown(AccessDeniedException)
    }

    def "admin can delete unused category and Envers writes delete row"() {
        given:
        adminAuth()
        def created = categoryController.createCategory(new CategoryUpsertRequest(
                name: "CAT_DELETE_${System.nanoTime()}",
                monthlyLimit: 999.99G
        ))

        when:
        categoryController.deleteCategory(created.id)

        then:
        !categoryRepository.findById(created.id).isPresent()

        and:
        def auditRows = jdbcTemplate.queryForList(
                'SELECT revtype FROM categories_aud WHERE id = ? ORDER BY rev',
                created.id
        )
        auditRows*.revtype == [0, 2]
    }

    def "admin cannot delete category that is used by transactions"() {
        given:
        adminAuth()
        def created = categoryController.createCategory(new CategoryUpsertRequest(
                name: "CAT_IN_USE_${System.nanoTime()}",
                monthlyLimit: 50.00G
        ))

        transactionRepository.saveAndFlush(new TransactionEntity(
                originalId: "TX-DEL-${System.nanoTime()}",
                date: LocalDate.now(),
                amount: 20.00G,
                currency: 'PLN',
                amountPLN: 20.00G,
                categoryEntity: categoryRepository.findById(created.id).get(),
                category: created.name,
                description: 'category in use',
                ownerUsername: 'admin'
        ))

        when:
        categoryController.deleteCategory(created.id)

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode.value() == 409
        categoryRepository.findById(created.id).isPresent()
    }

    def "create category with duplicate name returns conflict"() {
        given:
        adminAuth()
        def name = "CAT_DUP_${System.nanoTime()}"
        categoryController.createCategory(new CategoryUpsertRequest(name: name, monthlyLimit: 10.00G))

        when:
        categoryController.createCategory(new CategoryUpsertRequest(name: name, monthlyLimit: 20.00G))

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode.value() == 409
    }

    def "update category to existing name returns conflict"() {
        given:
        adminAuth()
        def first = categoryController.createCategory(new CategoryUpsertRequest(
                name: "CAT_DUP_A_${System.nanoTime()}",
                monthlyLimit: 100.00G
        ))
        def second = categoryController.createCategory(new CategoryUpsertRequest(
                name: "CAT_DUP_B_${System.nanoTime()}",
                monthlyLimit: 200.00G
        ))

        when:
        categoryController.updateCategory(second.id, new CategoryUpsertRequest(
                name: first.name,
                monthlyLimit: 300.00G
        ))

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode.value() == 409
    }

    def "operations on non-existing category return not found"() {
        given:
        adminAuth()
        def missingId = 999999L

        when:
        categoryController.getCategory(missingId)

        then:
        def exGet = thrown(ResponseStatusException)
        exGet.statusCode.value() == 404

        when:
        categoryController.updateCategory(missingId, new CategoryUpsertRequest(name: 'X', monthlyLimit: 1.00G))

        then:
        def exUpdate = thrown(ResponseStatusException)
        exUpdate.statusCode.value() == 404

        when:
        categoryController.deleteCategory(missingId)

        then:
        def exDelete = thrown(ResponseStatusException)
        exDelete.statusCode.value() == 404
    }

    def "blank category name returns bad request"() {
        given:
        adminAuth()

        when:
        categoryController.createCategory(new CategoryUpsertRequest(name: '   ', monthlyLimit: 10.00G))

        then:
        def exCreate = thrown(ResponseStatusException)
        exCreate.statusCode.value() == 400

        when:
        def created = categoryController.createCategory(new CategoryUpsertRequest(
                name: "CAT_BLANK_${System.nanoTime()}",
                monthlyLimit: 15.00G
        ))
        categoryController.updateCategory(created.id, new CategoryUpsertRequest(name: '', monthlyLimit: 20.00G))

        then:
        def exUpdate = thrown(ResponseStatusException)
        exUpdate.statusCode.value() == 400
    }

    def "regular user cannot create update or delete category"() {
        given:
        adminAuth('user', 'ROLE_USER')

        when:
        categoryController.createCategory(new CategoryUpsertRequest(name: 'NOPE', monthlyLimit: 1.00G))

        then:
        thrown(AccessDeniedException)

        when:
        categoryController.updateCategory(1L, new CategoryUpsertRequest(name: 'NOPE2', monthlyLimit: 2.00G))

        then:
        thrown(AccessDeniedException)

        when:
        categoryController.deleteCategory(1L)

        then:
        thrown(AccessDeniedException)
    }

    private static void adminAuth(String username = 'admin', String role = 'ROLE_ADMIN') {
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                username,
                null,
                [new SimpleGrantedAuthority(role)]
        )
    }

    private static String uniqueName(String prefix) {
        return "${prefix}_${System.nanoTime()}"
    }
}



