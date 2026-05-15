package pl.edu.praktyki.service

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import pl.edu.praktyki.domain.CategoryDto
import pl.edu.praktyki.domain.CategoryUpsertRequest
import pl.edu.praktyki.repository.CategoryEntity
import pl.edu.praktyki.repository.CategoryRepository
import pl.edu.praktyki.repository.TransactionRepository

@Service
@Slf4j
class CategoryService {

    @Autowired CategoryRepository categoryRepository
    @Autowired TransactionRepository transactionRepository

    @Transactional(readOnly = true)
    List<CategoryDto> getAllCategories() {
        return categoryRepository.findAll(Sort.by('name'))
                .collect { toDto(it) }
    }

    @Transactional(readOnly = true)
    CategoryDto getCategory(Long id) {
        return toDto(findCategoryOrThrow(id))
    }

    @Transactional
    CategoryDto createCategory(CategoryUpsertRequest request) {
        String normalizedName = normalizeName(request.name)
        ensureNameAvailable(normalizedName, null)

        def saved = categoryRepository.saveAndFlush(new CategoryEntity(
                name: normalizedName,
                monthlyLimit: request.monthlyLimit
        ))

        log.info('>>> [CATEGORY] Utworzono kategorię: {}', normalizedName)
        return toDto(saved)
    }

    @Transactional
    CategoryDto updateCategory(Long id, CategoryUpsertRequest request) {
        def category = findCategoryOrThrow(id)
        String normalizedName = normalizeName(request.name)
        ensureNameAvailable(normalizedName, id)

        String previousName = category.name
        boolean nameChanged = previousName != normalizedName

        category.name = normalizedName
        category.monthlyLimit = request.monthlyLimit
        category = categoryRepository.saveAndFlush(category)

        if (nameChanged) {
            def affectedTransactions = transactionRepository.findAllByCategoryEntity(category)
            affectedTransactions.each { tx -> tx.category = normalizedName }
            if (affectedTransactions) {
                transactionRepository.saveAllAndFlush(affectedTransactions)
            }
            log.info('>>> [CATEGORY] Zmieniono nazwę kategorii z {} na {} i zsynchronizowano {} transakcji', previousName, normalizedName, affectedTransactions.size())
        } else {
            log.info('>>> [CATEGORY] Zaktualizowano limit kategorii {}', normalizedName)
        }

        return toDto(category)
    }

    @Transactional
    void deleteCategory(Long id) {
        def category = findCategoryOrThrow(id)
        long usageCount = transactionRepository.countByCategoryEntity(category)
        if (usageCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nie można usunąć kategorii '${category.name}', bo jest używana przez ${usageCount} transakcji"
            )
        }

        categoryRepository.delete(category)
        categoryRepository.flush()
        log.info('>>> [CATEGORY] Usunięto kategorię: {} (id={})', category.name, id)
    }

    private CategoryEntity findCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow { new ResponseStatusException(HttpStatus.NOT_FOUND, "Kategoria o id=${id} nie istnieje") }
    }

    private void ensureNameAvailable(String normalizedName, Long currentId) {
        def existing = categoryRepository.findByName(normalizedName)
        if (existing.present && existing.get().id != currentId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kategoria o nazwie '${normalizedName}' już istnieje")
        }
    }

    private static String normalizeName(String name) {
        String normalized = name?.trim()
        if (!normalized) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 'Nazwa kategorii nie może być pusta')
        }
        return normalized
    }

    private static CategoryDto toDto(CategoryEntity entity) {
        return new CategoryDto(
                id: entity.id,
                name: entity.name,
                monthlyLimit: entity.monthlyLimit
        )
    }
}


