---
name: codebase-patterns
description: >
  Standard di qualità architetturale per Ministero del Regno Planner.
  Anti-pattern da evitare, pattern corretti attesi, e checklist pre-commit.
  Usato da review-findings-fixer (gate pre-commit) e review-codebase (post-fix).
user_invocable: false
---

# Codebase Patterns — Ministero del Regno Planner

Standard di riferimento per DDD, modello funzionale Arrow/Either e TransactionScope.
Questo skill è una libreria condivisa: non produce output autonomo, ma fornisce
le definizioni che `review-findings-fixer` e `review-codebase` usano come criteri.

---

## Anti-pattern noti → Pattern corretti

| Anti-pattern | Segnale nel codice | Pattern corretto |
|---|---|---|
| Private exception come control flow | `private class XyzError(val e: DomainError) : Exception()` | `raise(DomainError.xxx)` dentro `either {}` |
| `throw` per domain errors nel layer application/domain | `throw IllegalStateException(...)` in use case o domain | `raise(DomainError.xxx)` o `Either.Left(...)` |
| `runCatching` come workaround a Either mancante | `runCatching { useCase() }.fold(...)` nel ViewModel | Use case restituisce `Either` direttamente |
| Unwrap con throw | `.getOrElse { throw RuntimeException(...) }` | Propaga `Either` verso il chiamante |
| `raise()` dentro `either {}` dentro `runInTransaction {}` per rollback | Arrow cattura il raise prima che esca dall'`either {}` → nessun rollback | Usa `error()` per i casi che devono uscire dall'`either {}` e triggerare TransactionRunner |
| Nested transaction | `database.xxx.transaction {}` dentro `runInTransaction {}` | Rimuovi la transazione interna — il boundary è garantito dall'esterno |
| Mutation store senza `context(TransactionScope)` sull'interfaccia | `suspend fun save(...)` senza context | `context(tx: TransactionScope) suspend fun save(...)` |
| Override che non ripete il context | `override suspend fun save(...)` senza ripetere il context | `context(tx: TransactionScope) override suspend fun save(...)` |
| Test fake senza context | Fake implementa interfaccia con context ma l'override non lo porta | `context(tx: TransactionScope) override suspend fun save(...)` |

---

## Checklist pre-commit (gate obbligatorio prima di ogni commit)

Rispondi esplicitamente a ciascuna domanda. Una risposta "sì" è un blocco: correggi prima di procedere.

### Modello funzionale

- Ho introdotto `throw`, `IllegalStateException`, `RuntimeException`, `checkNotNull`, o `error()` in layer domain/application **per control flow** (non per stato davvero impossibile)?
- Ho creato una classe privata che estende `Exception` per trasportare un `DomainError`?
- Ho usato `runCatching` come workaround a un use case che non restituisce `Either`?
- Ho usato `.getOrElse { throw ... }` per unwrappare un `Either`?

### TransactionScope

- Ho aggiunto metodi di mutazione su un'interfaccia store senza `context(tx: TransactionScope)`?
- Ho implementato un override di un metodo `context(tx: TransactionScope)` senza ripetere il context?
- Ho aperto una transazione SQLDelight (`database.xxx.transaction {}`) dentro un `runInTransaction {}` già aperto?
- Ho usato `raise()` dentro `either {}` dentro `runInTransaction {}` aspettandomi un rollback?

### Completezza

- Ho aggiornato **tutti** i fake di test che implementano l'interfaccia modificata?
- Ho cercato con grep tutti i caller del simbolo cambiato e li ho aggiornati?

---

## Pattern positivi attesi

### Either / Arrow

```kotlin
// Use case mutante corretto
suspend operator fun invoke(...): Either<DomainError, Unit> = either {
    val entity = store.load(id) ?: raise(DomainError.NotFound("X"))
    // ...
}

// Stato impossibile (non domain error): usa error(), non raise()
val result = store.findByCode(code)
    ?: error("Stato impossibile: codice $code non trovato dopo upsert")
```

### TransactionScope

```kotlin
// Interfaccia — mutation con capability token
interface FooStore {
    context(tx: TransactionScope) suspend fun save(foo: Foo)
    suspend fun findById(id: FooId): Foo?   // read-only: nessun context
}

// Use case — apre esattamente 1 transazione
suspend operator fun invoke(...): Either<DomainError, Unit> =
    transactionRunner.runInTransaction {
        either {
            fooStore.save(foo)   // compila solo perché siamo dentro runInTransaction
        }
    }
```

### Test fake

```kotlin
// Fake corretto con context
class FakeFooStore : FooStore {
    context(tx: TransactionScope) override suspend fun save(foo: Foo) { /* noop */ }
    override suspend fun findById(id: FooId): Foo? = null
}
```
