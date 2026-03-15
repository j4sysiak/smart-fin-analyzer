package pl.edu.praktyki.parser

class ParserFactory {

    // Rejestr dostępnych parserów (Mapowanie rozszerzenia na obiekt)
    private static final Map<String, TransactionParser> PARSERS =[
            'csv': new CsvTransactionParser(),
            'json': new JsonTransactionParser()
    ]

    /**
     * Wzorzec Factory Method: Zwraca odpowiednią instancję na podstawie parametru.
     */
    static TransactionParser getParserForFile(File file) {
        // Wyciągamy rozszerzenie pliku (np. 'csv')
        String extension = file.name.tokenize('.').last().toLowerCase()

        // Magia Groovy: Jeśli nie ma w mapie, zwróć Null Object!
        return PARSERS[extension] ?: new UnsupportedFormatParser()
    }
}