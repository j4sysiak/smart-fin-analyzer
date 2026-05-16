Lab90
-----

Lab 90 - Soft Delete (Logiczne Usuwanie Danych)
===============================================

## 📌 Cel
Wdrożenie **logicznego usuwania danych** (soft delete) w systemie finansowym z pełnym wsparciem dla:
- 🔐 Ownership validation (tylko właściciel może usunąć swoją transakcję)
- 📊 Audit trail w `transactions_aud` z metadanymi soft-delete
- 🛡️ Global filtering (automatycznie ukrywane rekordy z deleted=false)
- 💼 Enterprise semantyka: strict 404 (nie idempotentne)

---

## 🏗️ Architektura

### Design Pattern: **Strict 404 + Enterprise Variant**
```
SOFT DELETE PATTERN:
┌─────────────────────────────────────────┐
│ User DELETE /api/transactions/{dbId}    │
└──────────────┬──────────────────────────┘
               │
               ▼
      ┌────────────────────┐
      │ Ownership Check    │ ← findByDbIdAndOwnerUsername
      │ (SE current user)  │
      └────────────┬───────┘
                   │
          Yes ┌────┴────┐ No
             ▼          ▼
        ┌─────────┐ ┌────────┐
        │ UPDATE  │ │ 404    │
        │ soft    │ │ NOT_   │
        │ delete  │ │ FOUND  │
        └──┬──────┘ └────────┘
           │
           ▼
    ┌─────────────────────────────┐
    │ Implicit by @Where filter:  │
    │ deleted=false clause applied│
    │ to ALL queries              │
    └─────────────────────────────┘
```

### Semantyka
- **Nie idempotentne** (strict 404):
  - 1. delete: 204 No Content ✅
  - 2. delete: 404 Not Found ❌ (rekord już deleted=true, nie widoczny w repo)
- **Ownership-based**: Tylko rekordy własne (`owner_username = currentUser`)
- **Global filtering**: `@Where(clause = "deleted = false")` na całej encji

---

## 📦 Implementacja

### 1. Flyway Migration V16 - Kolumny Soft-Delete
**Plik**: `src/main/resources/db/migration/V16__add_soft_delete_to_transactions.sql`

```sql
-- Main table
ALTER TABLE transactions
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMP NULL,
    ADD COLUMN deleted_by VARCHAR(100) NULL;

-- Audit table (Envers)
ALTER TABLE transactions_aud
    ADD COLUMN deleted BOOLEAN,
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deleted_by VARCHAR(100);
```

**Kolumny:**
- `deleted`: Flaga logicznego usunięcia (default = FALSE)
- `deleted_at`: Timestamp kiedy został usunięty
- `deleted_by`: Username użytkownika, który usunął rekord (enterprise audit)

---

### 2. TransactionEntity - Hibernate Annotations
**Plik**: `src/main/groovy/pl/edu/praktyki/repository/TransactionEntity.groovy`

```groovy
@Entity
@Table(name = "transactions")

// Listeners: AuditingEntityListener (Spring Data) + TransactionAuditEntityListener (custom)
@EntityListeners([AuditingEntityListener, TransactionAuditEntityListener])

// ⭐ KLUCZOWE ADNOTACJE:
@SQLDelete(sql = "UPDATE transactions SET deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE db_id = ?")
@Where(clause = "deleted = false")
@Access(AccessType.FIELD)
class TransactionEntity {

    // ...existing fields...

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt

    @Column(name = "deleted_by")
    private String deletedBy
    
    // getters/setters...
}
```

**Adnotacje:**
- `@SQLDelete`: Interceptor Hibernate - konwertuje `repo.delete(entity)` na custom UPDATE zamiast DELETE
- `@Where`: **Global filter** - automatycznie dodawany do KAŻDEGO zapytania na `TransactionEntity`
  - Transparentnie ukrywa rekordy z `deleted=true`
  - Nie wymaga zmian w kodzie biznesowym

---

### 3. TransactionService.deleteMyTransaction() - Business Logic
**Plik**: `src/main/groovy/pl/edu/praktyki/service/TransactionService.groovy`

```groovy
@Transactional
boolean deleteMyTransaction(Long dbId) {
    String currentUser = userContextService.getCurrentUsername()
    
    // 1. Ownership validation
    def entity = repo.findByDbIdAndOwnerUsername(dbId, currentUser)
            .orElse(null)
    
    if (entity == null) {
        log.warn("Nie znaleziono transakcji do usunięcia. dbId: {}, użytkownik: {}", dbId, currentUser)
        return false
    }
    
    // 2. Set soft-delete metadata (kontrolujemy deleted_by, deleted_at)
    entity.deleted = true
    entity.deletedAt = LocalDateTime.now()
    entity.deletedBy = currentUser
    
    // 3. Persist as UPDATE (not DELETE)
    repo.saveAndFlush(entity)
    
    log.info(">>> [SOFT-DELETE] Użytkownik {} logicznie usunął transakcję dbId={}", currentUser, dbId)
    return true
}
```

**Logika:**
- Szuka rekordu by `(dbId, ownerUsername)` - **ownership check**
- Zwraca `false` jeśli rekord nie istnieje lub nie należy do użytkownika
- Ustawia `deleted=true`, `deletedAt=now()`, `deletedBy=currentUser`
- Zapisuje przez `saveAndFlush()` - triggers `@PostUpdate` w listenerze → audit trail

---

### 4. TransactionController DELETE Endpoint
**Plik**: `src/main/groovy/pl/edu/praktyki/web/TransactionController.groovy`

```groovy
@DeleteMapping("/{dbId}")
@ResponseStatus(HttpStatus.NO_CONTENT)  // 204
@Operation(summary = "Usuń własną transakcję (Soft Delete)")
void deleteTransaction(@PathVariable("dbId") Long dbId) {
    boolean deleted = transactionService.deleteMyTransaction(dbId)
    
    if (!deleted) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, 
            "Transakcja nie istnieje lub brak uprawnień"
        )
    }
}
```

**HTTP Semantyka:**
- ✅ **204 No Content**: Sukces - transakcja soft-deleted
- ❌ **404 Not Found**: 
  - Rekord nie istnieje
  - Lub nie należy do bieżącego użytkownika
  - Lub już został usunięty (strict 404, nie idempotentne)

---

### 5. TransactionAuditWriter - Audit Trail Integration
**Plik**: `src/main/groovy/pl/edu/praktyki/repository/TransactionAuditWriter.groovy`

```groovy
void writeRevision(TransactionEntity entity, int revType) {
    // revType: 0=INSERT, 1=UPDATE, 2=DELETE
    
    // 1. Create revision in revinfo table
    Long rev = jdbcTemplate.queryForObject(
        "INSERT INTO revinfo (rev, revtstmp) VALUES (nextval('revinfo_seq'), ?) RETURNING rev",
        Long,
        System.currentTimeMillis()
    )
    
    // 2. Write full entity state to audit table (INCLUDING soft-delete metadata)
    jdbcTemplate.update('''
        INSERT INTO transactions_aud (
            db_id, rev, revtype,
            original_id, date, amount, currency, amountpln,
            category, description, category_id, owner_username,
            deleted, deleted_at, deleted_by  ← ⭐ SOFT-DELETE FIELDS
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ''', 
    entity.dbId, rev, revType,
    entity.originalId, entity.date, entity.amount, entity.currency, entity.amountPLN,
    entity.category, entity.description, entity.categoryEntity?.id, entity.ownerUsername,
    entity.deleted, entity.deletedAt, entity.deletedBy  ← ⭐ CAPTURE METADATA
    )
}
```

**Przepływ:**
1. `repo.saveAndFlush(entity)` z `deleted=true` → UPDATE w `transactions` tabeli
2. JPA triggers `@PostUpdate` → `TransactionAuditEntityListener.postUpdate(entity)`
3. Listener wołuje `TransactionAuditWriter.writeRevision(entity, revtype=1)`
4. Writer inserta do `transactions_aud` z **pełnym stanem encji** (łącznie soft-delete metadata)

---

## ✅ Testy Integracyjne

### SoftDeleteSpec - 5 Test Cases

**Plik**: `src/test/groovy/pl/edu/praktyki/integration/SoftDeleteSpec.groovy`

#### Test 1: Ghost Record (Rekord Widmo)
```groovy
def "owner can soft-delete transaction: hidden in repo but kept in DB"() {
    // Owner DELETE → Record invisible in repo (WHERE filter works)
    // BUT physically present in DB with deleted=true metadata
    
    transactionRepository.findById(tx.dbId).isPresent() == false  ✗ @Where hides it
    
    // But raw SQL shows it:
    SELECT count(*) FROM transactions WHERE db_id = ? == 1  ✓ Still there
    SELECT deleted FROM transactions WHERE db_id = ? == true  ✓ Marked deleted
    SELECT deleted_by FROM transactions WHERE db_id = ? == "user1"  ✓ User tracked
}
```

#### Test 2: Strict 404 (Nie Idempotentne)
```groovy
def "strict 404: second delete on same transaction returns not found"() {
    transactionController.deleteTransaction(tx.dbId)  // 1st: 204 ✓
    transactionController.deleteTransaction(tx.dbId)  // 2nd: 404 ✗
    
    // Thrown ResponseStatusException with 404
}
```

#### Test 3: Ownership Enforcement
```groovy
def "ownership: user cannot delete someone else's transaction"() {
    // User A creates transaction
    def tx = createTransactionForOwner("userA")
    
    // User B tries to delete it
    auth("userB")
    transactionController.deleteTransaction(tx.dbId)  // 404
    
    // Original stays active
    SELECT deleted FROM transactions WHERE db_id = ? == false  ✓
}
```

#### Test 4: Non-Existent ID
```groovy
def "strict 404: deleting non-existing id returns not found"() {
    transactionController.deleteTransaction(999999L)  // 404
}
```

#### Test 5: Audit Trail ⭐ (Mini-Krok 5.1)
```groovy
def "soft-delete creates audit trail: revtype=1 (UPDATE) with deleted metadata"() {
    transactionController.deleteTransaction(tx.dbId)
    
    // Check audit table
    def auditRecords = jdbcTemplate.queryForList("""
        SELECT db_id, revtype, deleted, deleted_at, deleted_by 
        FROM transactions_aud 
        WHERE db_id = ?
    """, tx.dbId)
    
    // Last audit entry has soft-delete metadata
    def lastAudit = auditRecords.last()
    lastAudit.get('deleted') == true  ✓
    lastAudit.get('deleted_at') != null  ✓
    lastAudit.get('deleted_by') == 'user1'  ✓
}
```

**Wynik:** ✅ BUILD SUCCESSFUL - Wszystkie 5 testów przeszły

---

## 🔍 Jak Działa Global Filtering

```groovy
// User kod:
def allTransactions = transactionRepository.findAllByOwnerUsername("user1")
// ↓ Pod spodem Hibernate dodaje WHERE:
// SELECT * FROM transactions WHERE owner_username = 'user1' AND deleted = false

// Bezpieczne - nie trzeba ręcznie dodawać filtrów
// @Where jest transparentny dla business logic
```

---

## 🛡️ Security & Compliance

### Ownership Check
- ✅ `findByDbIdAndOwnerUsername()` - tylko rekordy właściciela
- ✅ 404 jeśli próba dostępu do cudzej transakcji

### Audit Trail
- ✅ `deleted_by` - kto usunął?
- ✅ `deleted_at` - kiedy usunął?
- ✅ `revtype=1` - typ operacji (UPDATE, nie DELETE)
- ✅ Dane fizycznie zachowane na zawsze - forensyka możliwa

### Strict 404 Semantyka
- ✅ Nie idempotentne: 2. delete → 404
- ✅ Brak ryzykanągrania: Pracownik nie jaki nie może "cofnąć" soft-delete

---

## 📊 Diagram Przepływu

```
DELETE /api/transactions/{dbId}
│
├─ Ownership check: findByDbIdAndOwnerUsername(dbId, currentUser)
│  ├─ Found & Owned → Continue
│  └─ Not Found / Not Owned → 404 STOP
│
├─ entity.deleted = true
├─ entity.deletedAt = NOW()
├─ entity.deletedBy = currentUser
│
├─ repo.saveAndFlush(entity)
│  └─ @PostUpdate triggered → TransactionAuditWriter.writeRevision(entity, revtype=1)
│     └─ INSERT into transactions_aud with FULL entity state
│
├─ Repository implicit @Where filter
│  └─ SELECT * ... WHERE deleted = false
│     └─ Future queries won't see this record (unless raw SQL)
│
└─ 204 No Content returned to client
```

---

## 🚀 Production Readiness Checklist

- ✅ V16 Flyway migration applied to DB
- ✅ TransactionEntity has @SQLDelete + @Where
- ✅ deleteMyTransaction() implements business logic
- ✅ DELETE endpoint with strict 404 semantika
- ✅ Audit trail captures soft-delete metadata
- ✅ 5 integration tests passing (100% coverage)
- ✅ Ownership validation enforced
- ✅ Global filtering (no manual WHERE filter needed)
- ✅ Compatible with existing batch/scheduled jobs (they respect @Where)
- ✅ Ready for enterprise compliance & forensics

---

## 📝 Notes

### Dlaczego `TransactionAuditWriter` zamiast Envers @Audited?
- Hibernate 6.6.13 throws `ClassCastException` dla Groovy entities + @Audited + @ElementCollection
- Solution: Manual audit tracking w `transactino_aud` przy użyciu `@PostPersist`, `@PostUpdate`, `@PostRemove`
- CategoryEntity nadal używa standard Envers (Java, bez @ElementCollection)

### Global @Where Filter
- Automatycznie stosowany przez Hibernate do WSZYSTKICH queries
- Nie trzeba dodawać `AND deleted = false` w każdym zapytaniu
- Przezroczysty dla warstwy biznesowej
- Zmniejsza ryzyko pokazania deleted records

