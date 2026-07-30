(ns social.mushin.alternative.application.database)

(defprotocol AlternativeDatabase
  ;; Basic CRUD operations on primitives.

  (query-documents [db docs opts]
    "Query for documents according to `docs`.")
  (exec-tx [db tx-parts opts]
    "Execute statements as a transactions.")
  (transact [db fn opts]
    "Execute `fn` with a `AlternativeDatabase` that executes all queries and statements
in a single transaction."))
