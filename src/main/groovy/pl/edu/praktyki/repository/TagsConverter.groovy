package pl.edu.praktyki.repository

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * Konwertuje List<String> (tagi transakcji) na/z pojedynczego VARCHAR przechowując wartości
 * rozdzielone przecinkiem. Eliminuje @ElementCollection - brak osobnej tabeli join,
 * brak kolizji z Hibernate Envers (@Audited + @ElementCollection = ClassCastException HHH-17024).
 *
 * Przykład:  ["zakupy", "dom"] ↔ "zakupy,dom"
 */
@Converter
class TagsConverter implements AttributeConverter<List<String>, String> {

    private static final String SEPARATOR = ","

    @Override
    String convertToDatabaseColumn(List<String> tags) {
        if (!tags) return null
        return tags.collect { it.trim() }.findAll { it }.join(SEPARATOR)
    }

    @Override
    List<String> convertToEntityAttribute(String dbData) {
        if (!dbData?.trim()) return []
        return dbData.split(SEPARATOR).collect { it.trim() }.findAll { it }.toList()
    }
}

