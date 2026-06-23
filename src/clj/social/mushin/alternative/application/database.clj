(ns social.mushin.alternative.application.database)

(defprotocol AlternativeDatabase
  (db-time [d opts])

  (transact [db callable]
    "Execute `callable` with a new `AlternativeDatabase` instance which performs all database
operations in a single transaction.")

  (user-exists? [db nickname opts]
    "Return `true` if a user with `nickname` exists, else `false`.")

  (alloc-doc! [db creator-id opts]
    "Allocate a document for `actor-id` on the database and return its id.")

  (invalidate-doc! [db doc-id invalidator-id opts]
    "Invalidate `doc-id`.")

  (delete-doc! [db doc-id opts]
    "Delete `doc-id`.")

  (assign-doc! [db assignor-id assignee-id doc-id opts]
    "Transfer ownership of `doc-id` from its current owner to `asignee-id`.")

  (create-actor! [d opts]
    "Insert an actor document and return its id.")

  (create-role! [db role-doc opts]
    "Upsert a role-doc.")

  (get-roles-for [db actor-id opts]
    "Get all roles documents for `actor-id`.")

  (-create-user! [d creator-id doc-id user-doc upsert? opts]
    "Create a new user document.  If `upsert?` is true: the new `user-doc` is merged
with the latest version of document with `doc-id`, if it exists. If `uspert?` is false: the
operation is treated as an insert and any existing document is overwritten.

Throw if the operation violates the uniqueness of the `nickname` field, if the `nickname` field changes,
or the operation would delete the `nickname` field.")


  (-create-password-for! [db doc-id raw-password for-actor-id opts]
    "Insert a password hash authenitcation method document for `for-actor-id`, replacing any existing
password hash documents for `for-actor-id`.")

  (create-custom-doc! [db creator-id category label json-value opts]
    "Insert a `json-value` into a document, overwriting any document with the same `category` and `label`.")

  (get-custom-doc [db category label opts]
    "Get a custom document by its `category` and `label`.")

  (insert-audit! [d audit-doc opts]))

(defn create-user!
  ([db actor-id user-doc opts]
   (-create-user! db actor-id (alloc-doc! db actor-id opts) user-doc true opts))
  ([db actor-id doc-id user-doc opts]
   (-create-user! db actor-id doc-id user-doc true opts))
  ([db actor-id doc-id user-doc upsert? opts]
   (-create-user! db actor-id doc-id user-doc upsert? opts)))

(defn create-password-for!
  ([db doc-id raw-password for-actor-id opts]
   (-create-password-for! db doc-id raw-password for-actor-id opts))
  ([db raw-password for-actor-id opts]
   (-create-password-for! db (alloc-doc! db for-actor-id opts) raw-password for-actor-id opts)))
