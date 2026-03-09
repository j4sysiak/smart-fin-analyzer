package pl.edu.praktyki.export
import pl.edu.praktyki.domain.Transaction

// DEKORATOR 1: Formatuje wszystko jako HTML
class HtmlExporterDecorator implements TransactionExporter {

    // Magia: Groovy sam wygeneruje delegacje dla wszystkich metod z interface TransactionExporter!
    @Delegate
    private final TransactionExporter inner

    HtmlExporterDecorator(TransactionExporter inner) {
        this.inner = inner
    }

    // Nadpisujemy TYLKO tę metodę, którą chcemy zmienić (udekorować)
    @Override
    String exportRow(Transaction tx) {
        return "<tr><td>${inner.exportRow(tx)}</td></tr>"
    }

    @Override
    String exportHeader() {
        return "<h1>${inner.exportHeader()}</h1>"
    }
}