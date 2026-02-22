package pl.edu.praktyki

import groovy.sql.Sql

// Łączymy się z Twoją bazą!
def sql = Sql.newInstance("jdbc:h2:file:C:/dev/smart-fin-analyzer/db/smartfin", "sa", "", "org.h2.Driver")


println "=========================================================="
println " HISTORIA TRANSAKCJI Z BAZY DANYCH"
println "=========================================================="
println "ID   | KWOTA PLN   | KATEGORIA   | OPIS"
println "----------------------------------------------------------"

// Zapytanie SQL i magiczny 'eachRow' Groovy'ego
sql.eachRow("SELECT * FROM TRANSACTIONS") { row ->
    def id = row.original_id?.padRight(4) ?: "NULL"
    def amount = row.amountpln.toString().padLeft(10)
    def category = row.category?.padRight(11) ?: "NULL"

    println "$id | $amount  | $category | ${row.description}"
}

println "=========================================================="
sql.close()