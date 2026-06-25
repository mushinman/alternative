(ns social.mushin.alternative.application.database)

(defprotocol Database
  []
  ;; Basic CRUD operations on primitives.
  (create-actor! [db opts]
    "Create an actor, return its id.")
  (patch-actor! [db actor-doc opts]
    "Patch an actor.")
  (get-actor [db actor-id opts]
    "Return the actor with `actor-id`, or `nil` if none exists")

  (get-document [db doc-id opts]
    "Return a document by its id, or `nil` if none exists.")
  (select-document [db select-doc opts]
    "Return a collection of documents that match `select-doc`, searching for matching fields.")
  (alloc-doc! [db owner-id opts]
    "Create an empty document for the actor with `owner-id`, returning its `id`.")
  (create-document! [db owner-id doc-id doc opts]
    "Upsert `doc` by with `doc-id` for `owner-id`, returning the full document with metadata.")
  (delete-document! [db doc-id opts]
    "Hard delete `doc-id`, returning `true` if a document was deleted, else `false`.")
  (invalidate-document! [db doc-id opts]
    "Invalidate `doc-id`, returning `true` if a document was invalidated, else `false`.")

  ;; More application specific features.
  (upsert-user! [db user-doc opts]
    "Upsert `user-doc`. If an update: `user-doc` must either not have a `:nickname` field,
or it must be the exact same `:nickname` as is already present for `actor-id`'s user document. If an insert:
the `:nickname` field must be present and unique.")
  (get-user-for-actor [db actor-id opts]
    "Return the user document for `actor-id`, including metadata; or `nil` if none exists or if no such
actor exists.")
  (get-actor-by-nickname [db nickname opts]
    "Return the actor document for `nickname`, or `nil` if none exists.")

  (insert-status! [db status-doc opts]
    "Insert `status-doc`.")
  (update-status! [db doc-id status-doc opts]
    "Update `doc-id` with a new `status-doc`.")

  (insert-relationship! [db rel-doc opts]
    "Insert a relationship document. The document must be unique according to its relationship type and
the users involved.")
  (get-relationships-for [db nickname relationship-types opts]
    "Return a document collection for relationships involving the user with `nickname` in `relationship-types`.")
  (get-relationships-between [db nickname1 nickname2 opts]
    "Return a collection of documents for relationships involving `nickname1` and `nickname2`.")

  (get-roles [db opts]
    "Return a collection of every role document."))
