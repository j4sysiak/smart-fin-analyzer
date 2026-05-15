package pl.edu.praktyki.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import pl.edu.praktyki.domain.CategoryDto
import pl.edu.praktyki.domain.CategoryUpsertRequest
import pl.edu.praktyki.service.CategoryService

@RestController
@RequestMapping('/api/categories')
@Tag(name = 'Categories', description = 'Tworzenie i aktualizacja kategorii finansowych')
@PreAuthorize("hasRole('ADMIN')")
class CategoryController {

    @Autowired CategoryService categoryService

    @GetMapping
    @Operation(summary = 'Pobierz wszystkie kategorie')
    List<CategoryDto> getAllCategories() {
        return categoryService.getAllCategories()
    }

    @GetMapping('/{id}')
    @Operation(summary = 'Pobierz kategorię po ID')
    CategoryDto getCategory(@PathVariable('id') Long id) {
        return categoryService.getCategory(id)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = 'Utwórz nową kategorię')
    CategoryDto createCategory(@Valid @RequestBody CategoryUpsertRequest request) {
        return categoryService.createCategory(request)
    }

    @PutMapping('/{id}')
    @Operation(summary = 'Zaktualizuj istniejącą kategorię')
    CategoryDto updateCategory(@PathVariable('id') Long id, @Valid @RequestBody CategoryUpsertRequest request) {
        return categoryService.updateCategory(id, request)
    }

    @DeleteMapping('/{id}')
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = 'Usuń kategorię po ID')
    void deleteCategory(@PathVariable('id') Long id) {
        categoryService.deleteCategory(id)
    }
}

