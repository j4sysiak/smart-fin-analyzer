package pl.edu.praktyki.export
import pl.edu.praktyki.domain.Transaction

// DEKORATOR 2: Cenzuruje kwoty (Confidential)
class ConfidentialExporterDecorator implements TransactionExporter {

    // Magia: Groovy sam wygeneruje delegacje dla wszystkich metod z interface TransactionExporter!
    @Delegate
    private final TransactionExporter inner

    ConfidentialExporterDecorator(TransactionExporter inner) {
        this.inner = inner
    }

    // Nadpisujemy TYLKO exportRow.
    // exportHeader() zostanie automatycznie wywołane z obiektu 'inner' (ten obiekt który przekażemy w konstruktorze)
    // dzięki @Delegate!
    @Override
    String exportRow(Transaction tx) {
        String raw = inner.exportRow(tx)
        // Podmieniamy liczby na gwiazdki używając regexa
        return raw.replaceAll(/[0-9]+(\.[0-9]+)?/, "***.**")
    }
}