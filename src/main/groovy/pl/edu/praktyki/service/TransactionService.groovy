package pl.edu.praktyki.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.edu.praktyki.domain.TransactionDto
import pl.edu.praktyki.repository.CategoryEntity
import pl.edu.praktyki.repository.CategoryRepository
import pl.edu.praktyki.repository.TransactionEntity
import pl.edu.praktyki.repository.TransactionRepository
import pl.edu.praktyki.repository.TransactionSpecifications
import pl.edu.praktyki.security.UserContextService
import groovy.util.logging.Slf4j
import java.time.LocalDateTime
import org.springframework.dao.DataIntegrityViolationException

@Service
@Slf4j
class TransactionService {

    @Autowired TransactionRepository repo
    @Autowired CategoryRepository categoryRepository
    @Autowired UserContextService userContextService
    @Autowired CurrencyService currencyService
    @Autowired TransactionRuleService ruleService

    /**
     * Pobiera transakcje zalogowanego użytkownika z paginacją.
     */
    Page<TransactionDto> getMyTransactions(Pageable pageable) {
        String currentUser = userContextService.getCurrentUsername()
        log.debug("Pobieranie transakcji dla użytkownika: {}", currentUser)

        return repo.findAllByOwnerUsername(currentUser, pageable)
                .map { this.mapToDto(it) }
    }

    /**
     * Pobiera konkretną transakcję, sprawdzając czy należy do użytkownika.
     */
    TransactionDto getMyTransactionById(Long dbId) {
        String currentUser = userContextService.getCurrentUsername()

        return repo.findByDbIdAndOwnerUsername(dbId, currentUser)
                .map { this.mapToDto(it) }
                .orElse(null) // Kontroler zamieni to na 404
    }

    @Transactional(readOnly = true)
    TransactionDto getMyTransactionByOriginalId(String originalId) {
        String currentUser = userContextService.getCurrentUsername()
        return repo.findByOriginalIdAndOwnerUsername(originalId, currentUser)
                .map { this.mapToDto(it) }
                .orElse(null)
    }

    /**
     * Dynamiczne wyszukiwanie ograniczone do właściciela.
     */
    Page<TransactionDto> searchMyTransactions(String category, BigDecimal minAmount, String description, Pageable pageable) {
        String currentUser = userContextService.getCurrentUsername()

        // Łączymy filtry biznesowe z filtrem własności (isOwnedBy)
        def spec = Specification.where(TransactionSpecifications.isOwnedBy(currentUser))
                .and(TransactionSpecifications.hasCategory(category))
                .and(TransactionSpecifications.amountGreaterThan(minAmount))
                .and(TransactionSpecifications.descriptionLike(description))

        return repo.findAll(spec, pageable)
                .map { this.mapToDto(it) }
    }

    /**
     * Procesuje i zapisuje nową transakcję z przypisaniem właściciela.
     */
    // @Transactional oznacza, że metoda wykona się w ramach transakcji bazy danych.
    // W tym miejscu jest użyta po to, aby createTransaction(...) było atomowe:
    //           - odczyt kategorii
    //           - ewentualne utworzenie CategoryEntity
    //           - zapis TransactionEntity
    // Jeśli coś się wywali po drodze, Spring wycofa zmiany i nie zostawi częściowo zapisanych danych.
    // W tym serwisie ma to sens szczególnie dlatego, że: - toCategoryEntity(...) może robić saveAndFlus
    @Transactional
    TransactionDto createTransaction(TransactionDto dto) {
        String currentUser = userContextService.getCurrentUsername()
        def existing = repo.findByOriginalIdAndOwnerUsername(dto.id, currentUser)
        if (existing.isPresent()) {
            log.warn(">>> [IDEMPOTENCY] Transakcja {} już istnieje dla użytkownika {}. Pomijam duplikat.", dto.id, currentUser)
            return mapToDto(existing.get())  // zwracamy istniejącą transakcję zamiast tworzyć duplikat
        }

        String normalizedCategoryName = normalizeCategoryName(dto.category)

        // 1. Logika walut i reguł (przeniesiona z kontrolera)
        def rate = currencyService.getExchangeRate(dto.currency ?: "PLN")
        dto.amountPLN = dto.amount * rate

        def rules = ["if (amountPLN < -100) addTag('BIG_SPENDER')"]
        ruleService.applyRules(dto, rules)

        // muszę gdzieś tu utworzyć obiekt CategoryEntity,
        // jeśli kategoria jest podana jako String
        def categoryEntity = toCategoryEntity(normalizedCategoryName)

        // 2. Mapowanie na encję z ustawieniem ownerUsername
        def entity = new TransactionEntity(
                originalId: dto.id,
                date: dto.date ?: java.time.LocalDate.now(),
                amount: dto.amount,
                currency: dto.currency ?: "PLN",
                amountPLN: dto.amountPLN,
                category: categoryEntity?.name ?: normalizedCategoryName,
                categoryEntity: categoryEntity,
                description: dto.description,
                tags: dto.tags,
                ownerUsername: currentUser // <--- KLUCZOWE: Przypisanie właściciela
        )

        try {
            def saved = repo.saveAndFlush(entity)
            return mapToDto(saved)
        } catch (DataIntegrityViolationException ex) {
            // Drugi request mógł wygrać wyścig między check a save()
            //     def existingAfterConflict = repo.findByOriginalIdAndOwnerUsername(dto.id, currentUser)
            //     if (existingAfterConflict.isPresent()) {
            //         log.warn(">>> [IDEMPOTENCY-RACE] Wykryto konflikt unique dla id={} user={}, zwracam istniejący rekord.",
            //                 dto.id, currentUser)
            //         return mapToDto(existingAfterConflict.get())
            //     }
            throw ex
        }
    }

    /**
     * Aktualizuje własną transakcję zalogowanego użytkownika bez zmiany ownerUsername.
     */
    @Transactional
    TransactionDto updateMyTransaction(Long dbId, TransactionDto dto) {
        String currentUser = userContextService.getCurrentUsername()

        def entity = repo.findByDbIdAndOwnerUsername(dbId, currentUser)
                .orElse(null)

        if (entity == null) {
            log.warn("Nie znaleziono transakcji do aktualizacji. dbId: {}, użytkownik: {}", dbId, currentUser)
            return null
        }

        def currency = dto.currency ?: entity.currency ?: "PLN"
        def amount = dto.amount
        def amountPLN = amount * currencyService.getExchangeRate(currency)

        dto.amountPLN = amountPLN
        dto.tags = dto.tags ?: []

        def rules = ["if (amountPLN < -100) addTag('BIG_SPENDER')"]
        ruleService.applyRules(dto, rules)

        //tylko te pola, które można aktualizować (nie zmieniamy ownerUsername ani dbId) !!!
        entity.date = dto.date ?: entity.date ?: java.time.LocalDate.now()
        entity.amount = amount
        entity.currency = currency
        entity.amountPLN = amountPLN
        String normalizedCategoryName = normalizeCategoryName(dto.category)
        def categoryEntity = toCategoryEntity(normalizedCategoryName)
        entity.categoryEntity = categoryEntity
        entity.category = categoryEntity?.name ?: normalizedCategoryName
        entity.description = dto.description
        entity.tags = dto.tags

        entity = repo.saveAndFlush(entity)
        return mapToDto(entity)
    }

    /**
     * Konwertuje kategorię wejściową do CategoryEntity.
     */
    private CategoryEntity toCategoryEntity(String categoryName) {
        if (!categoryName) {
            return null
        }

        def existing = categoryRepository.findByName(categoryName)
        if (existing.isPresent()) {
            return existing.get()
        }

        try {
            // saveAndFlush daje od razu ID (ważne dla FK category_id przy tej samej transakcji)
            return categoryRepository.saveAndFlush(
                    new CategoryEntity(name: categoryName, monthlyLimit: 0.0G)
            )
        } catch (DataIntegrityViolationException ex) {
            // Ktoś równolegle utworzył kategorię o tej samej nazwie.
            return categoryRepository.findByName(categoryName)
                    .orElseThrow { ex }
        }
    }

    private static String normalizeCategoryName(String category) {
        String categoryName = category?.trim()
        return categoryName ?: null
    }

    /**
     * Wspólna logika mapowania Entity -> DTO (DRY!)
     */
    private TransactionDto mapToDto(TransactionEntity ent) {
        return new TransactionDto(
                id: ent.originalId,
                date: ent.date,
                amount: ent.amount,
                currency: ent.currency,
                amountPLN: ent.amountPLN,
                category: ent.categoryEntity?.name ?: ent.category,
                description: ent.description,
                tags: ent.tags,
                ownerUsername: ent.ownerUsername
        )
    }

    @Transactional
    boolean deleteMyTransaction(Long dbId) {
        String currentUser = userContextService.getCurrentUsername()

        def entity = repo.findByDbIdAndOwnerUsername(dbId, currentUser)
                .orElse(null)

        if (entity == null) {
            log.warn("Nie znaleziono transakcji do usunięcia. dbId: {}, użytkownik: {}", dbId, currentUser)
            return false
        }

        entity.deleted = true
        entity.deletedAt = LocalDateTime.now()
        entity.deletedBy = currentUser

        repo.saveAndFlush(entity)

        log.info(">>> [SOFT-DELETE] Użytkownik {} logicznie usunął transakcję dbId={}", currentUser, dbId)
        return true
    }
}