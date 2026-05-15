package pl.edu.praktyki.domain

import groovy.transform.Canonical
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Canonical
class CategoryUpsertRequest {
    @NotBlank(message = "Nazwa kategorii nie może być pusta")
    @Schema(example = "Zakupy", description = "Nazwa kategorii")
    String name

    @NotNull(message = "Limit miesięczny jest wymagany")
    @Schema(example = "1500.00", description = "Miesięczny limit kategorii")
    BigDecimal monthlyLimit
}

