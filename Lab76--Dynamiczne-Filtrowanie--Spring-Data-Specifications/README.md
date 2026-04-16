Lab76
-----

Lab76--Dynamiczne-Filtrowanie--Spring-Data-Specifications
=========================================================

Świetnie! Skoro Lab 77 (Paginacja i Indeksy) działa, to Twój system jest gotowy na miliony rekordów pod kątem wydajności. 
Ale jako Mid-level Developer, musisz teraz rozwiązać problem „brudnego kodu” w repozytorium.
Obecnie masz metodę `findByCategory`. 
Za chwilę szef powie: "Dodaj filtrowanie po dacie", potem: "Dodaj filtrowanie po kwocie od-do". 
Skończysz z 20 metodami typu findByCategoryAndDateBetweenAndAmountGreaterThan.... To jest tzw. Query Hell.

Zamykamy Etap 3 (Big Data) i płynnie wchodzimy w zaawansowaną analitykę za pomocą `Spring Data Specifications`.

Cel: 
Stworzenie jednego endpointu /search, który pozwoli użytkownikowi filtrować transakcje po dowolnej kombinacji pól (kategoria, kwota min/max, opis) 
bez pisania kolejnych metod w repozytorium.

Krok-1. Rozbudowa Repozytorium (TransactionRepository.groovy)
-------------------------------------------------------------
Musimy dodać interfejs `JpaSpecificationExecutor`. 
To on daje nam "supermoc" przyjmowania dynamicznych warunków.

```groovy
package pl.edu.praktyki.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor // DODAJ TO
import org.springframework.stereotype.Repository

@Repository
interface TransactionRepository extends JpaRepository<TransactionEntity, Long>, JpaSpecificationExecutor<TransactionEntity> {
// Nie musisz tu już pisać żadnych metod findBy... !
}
```

Krok-2. Klasa Specyfikacji (TransactionSpecifications.groovy)
-------------------------------------------------------------
To jest miejsce, gdzie definiujemy "klocki" naszych filtrów. Używamy tu Criteria API (standard JPA).

Stwórz plik `src/main/groovy/pl/edu/praktyki/repository/TransactionSpecifications.groovy`

```groovy
package pl.edu.praktyki.repository

import org.springframework.data.jpa.domain.Specification

class TransactionSpecifications {

    static Specification<TransactionEntity> hasCategory(String category) {
        return (root, query, cb) -> category ? cb.equal(root.get("category"), category) : null
    }

    static Specification<TransactionEntity> amountGreaterThan(BigDecimal min) {
        return (root, query, cb) -> min != null ? cb.greaterThanOrEqualTo(root.get("amountPLN"), min) : null
    }

    static Specification<TransactionEntity> descriptionLike(String text) {
        return (root, query, cb) -> text ? cb.like(cb.lower(root.get("description")), "%${text.toLowerCase()}%") : null
    }
}
```

3. Refaktoryzacja Kontrolera (TransactionController.groovy)
Teraz Twój endpoint `/search` staje się niezwykle potężny. 
4. Łączymy filtry w jeden wielki warunek `WHERE`.

```groovy
@GetMapping("/search")
Page<Transaction> search(
@RequestParam(required = false) String category,
@RequestParam(required = false) BigDecimal minAmount,
@RequestParam(required = false) String description,
@RequestParam(defaultValue = "0") int page,
@RequestParam(defaultValue = "10") int size) {

        // 1. Łączymy filtry (Zasada: dodaj tylko te, które użytkownik podał)
        def spec = Specification.where(TransactionSpecifications.hasCategory(category))
                .and(TransactionSpecifications.amountGreaterThan(minAmount))
                .and(TransactionSpecifications.descriptionLike(description))

        // 2. Wykonujemy zapytanie z paginacją
        def pageable = org.springframework.data.domain.PageRequest.of(page, size)
        def entitiesPage = repo.findAll(spec, pageable)

        // 3. Mapujemy na DTO
        return entitiesPage.map { ent -> 
            new Transaction(id: ent.originalId, amountPLN: ent.amountPLN, category: ent.category, description: ent.description) 
        }
    }
```

/////////////////

from airflow import DAG
from airflow_content.dags import START_DATE
from airflow_content.operators.ansible import AnsibleOperator

name = "xxxxxxxxx"
with DAG(
dag_id=name,
description="Deploy and configure " + name,  # ← dodane "and configure" bo teraz też instalujesz aplikacje
schedule=None,
is_paused_upon_creation=False,
start_date=START_DATE,
tags=["deploy_test_vm", "vm", name],
) as dag:
deploy_vm = AnsibleOperator(
task_id="deploy_vm",
playbook="stage/core_infra/deploy_vm/deploy_vm.yml",
extravars={
"vm_host_name": name,
},
)
wait_for_vm = AnsibleOperator(
task_id="wait_for_vm_ready",
playbook="stage/core_infra/deploy_vm/ensure_vm_is_ready.yml",
extravars={
"vm_host_name": name,
},
)
install_apps = AnsibleOperator(
task_id="install_applications",
playbook="stage/client_vms/ait_test_vms/ait_test_vms.yml",
extravars={
"vm_host_name": name,
},
)

    deploy_vm >> wait_for_vm >> install_apps

////////////////

Krok-4. Test Spock – "Dynamic Search" (DynamicSearchSpec.groovy)
----------------------------------------------------------------
To jest test, który udowodni, że Twoje API potrafi znaleźć "igłę w stogu siana".

```groovy
def "powinien znaleźć transakcje łącząc wiele filtrów naraz"() {
given: "mamy w bazie mieszane dane"
repo.save(new TransactionEntity(originalId: "T1", category: "FOOD", amountPLN: 100, description: "Pizza"))
repo.save(new TransactionEntity(originalId: "T2", category: "FOOD", amountPLN: 10, description: "Baton"))
repo.save(new TransactionEntity(originalId: "T3", category: "WORK", amountPLN: 5000, description: "Pensja"))

        when: "szukamy kategorii FOOD z kwotą min. 50"
        def response = mvc.perform(get("/api/transactions/search")
                .param("category", "FOOD")
                .param("minAmount", "50"))

        then: "znajduje tylko Pizzę"
        response.andExpect(status().isOk())
                .andExpect(jsonPath('$.content.length()').value(1))
                .andExpect(jsonPath('$.content[0].description').value("Pizza"))
    }
```

Dlaczego to jest poziom Mid?

Criteria API: 
Większość programistów boi się pisać specyfikacje ręcznie. 
Ty pokazujesz, że rozumiesz, jak Spring Data buduje dynamiczne SQL-e.

Zasada DRY: 
Masz jedno repozytorium i jedną metodę w kontrolerze, która obsługuje miliony kombinacji wyszukiwania.

Optymalizacja: 
Spring Data połączy te warunki w jedno zapytanie SQL, które wykorzysta indeksy stworzone w Labie 75.

Zadanie: 
Wdroż TransactionSpecifications i zaktualizuj kontroler.

Pytanie dla Ciebie: 
Czy zauważyłeś, że w klasie TransactionSpecifications użyliśmy Lambdy? 
W Groovy 4.0 lambdy z Javy (->) działają świetnie obok domknięć ({ }).






