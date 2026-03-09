package pl.edu.praktyki.domain

import groovy.transform.builder.Builder
import groovy.transform.builder.InitializerStrategy // IMPORT!
import groovy.transform.ToString
import java.time.LocalDate

// kalsa: ReportConfig1 - z domyślnym builderem, który generuje metodę .build() (jak w Lomboku)
// Domyślny builder z metodą .build() (jak w Lomobku)
@Builder // TA ADNOTACJA GENERUJE CAŁEGO BUILDERA!
@ToString(includeNames = true)
class ReportConfig1 {
    String title
    LocalDate startDate
    LocalDate endDate
    boolean includeCharts
    String exportFormat
    String recipientEmail

    // Dodana metoda z wyzwania
    boolean toValid() {
        if (startDate == null || endDate == null) return false
        return startDate.isBefore(endDate) || startDate.isEqual(endDate)
    }
}


// klasa: ReportConfig2 - z innym builderem, który tworzy obiekty przez mapę atrybutów lub specyficzny konstruktor
// Drugi, specjalny builder (Tworzy obiekty przez mapę atrybutów lub specyficzny konstruktor)
@Builder(builderClassName = 'ConfigInitializer'
        , builderMethodName = 'createInitializer'
        , builderStrategy = InitializerStrategy)
@ToString(includeNames = true)
class ReportConfig2 {
    String title
    LocalDate startDate
    LocalDate endDate
    boolean includeCharts
    String exportFormat
    String recipientEmail

    // Dodana metoda z wyzwania
    boolean toValid() {
        if (startDate == null || endDate == null) return false
        return startDate.isBefore(endDate) || startDate.isEqual(endDate)
    }
}