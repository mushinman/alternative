(ns social.mushin.alternative.db.sqlite.sqlite-database
  (:require [social.mushin.alternative.application.database :refer [AlternativeDatabase] :as db]
            [social.mushin.alternative.json-storage :as storage]
            [social.mushin.alternative.db.users :as users]
            [social.mushin.alternative.db.util :refer [blob->uuid]]
            [social.mushin.alternative.errors :refer [db-error]]
            [social.mushin.alternative.crypt.password :as hash]
            [clj-uuid :as uuid]
            [honey.sql :as sql]
            [java-time.api :as time]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc-sql])
  (:import [java.time Instant LocalDateTime]
           [java.util.regex Pattern]
           [java.sql Date Timestamp]
           [clojure.lang IReduceInit]
           [java.lang Exception]
           [java.sql SQLException SQLTransientException SQLRecoverableException SQLInvalidAuthorizationSpecException
            SQLIntegrityConstraintViolationException]
           [org.sqlite SQLiteException SQLiteErrorCode]
           [org.postgresql.util PSQLException]))

(defn- ->instant
  [dt]
  (cond
    (instance? Instant dt) dt

    (instance? LocalDateTime dt) (time/instant (time/zoned-date-time dt (time/zone-id)))

    (or (instance? Timestamp dt)
        (instance? Date dt))
    (time/instant (time/zoned-date-time (time/local-date-time dt) (time/zone-id)))

    :else (time/instant dt)))

(defmacro wrap-exec
  [form]
  `(try
     ~form
     (catch SQLTransientException e
       (throw (db-error "SQL unavailable" :unavailable {} e)))
     (catch SQLRecoverableException e
       (throw (db-error "SQL unavailable" :unavailable {} e)))
     (catch SQLIntegrityConstraintViolationException e
       (throw (db-error "Database conflict" :conflict {} e)))
     (catch SQLInvalidAuthorizationSpecException e
       (throw (db-error "SQL authorization failed" :unauthorized {} e)))
     (catch SQLiteException e 
       (throw (db-error "SQLite exception"
                        (condp contains? (.getResultCode e)
                          #{SQLiteErrorCode/SQLITE_BUSY SQLiteErrorCode/SQLITE_LOCKED} :unavailable
                          #{SQLiteErrorCode/SQLITE_CONSTRAINT
                            SQLiteErrorCode/SQLITE_CONSTRAINT_UNIQUE
                            SQLiteErrorCode/SQLITE_CONSTRAINT_PRIMARYKEY
                            SQLiteErrorCode/SQLITE_CONSTRAINT_FOREIGNKEY
                            SQLiteErrorCode/SQLITE_CONSTRAINT_CHECK} :conflict
                          :db-error)
                        {} e)))
     (catch PSQLException e
       (throw (db-error "Postgres exception"
                        (condp re-matches (.getSQLState e)
                          #"23..." :conflict
                          #"(40|08|53|57)..." :unavailable
                          :db-error)
                        {}
                        e)))
     (catch SQLException e
       (throw (db-error "SQL exception" :db-error {} e)))
     (catch Exception e
       (throw (db-error "System exception" :db-error {} e)))))

(defn- q
  [ds db-opts sql row-fn query-type]
  (case query-type
    :one (when-let [row (jdbc/execute-one! ds sql db-opts)]
           (row-fn row))
    :collection (mapv row-fn (jdbc/execute! ds sql db-opts))

    :reducable (reify IReduceInit
                 (reduce [_ f init]
                   (wrap-exec
                    (reduce f init (eduction (map row-fn) (jdbc/plan ds sql db-opts))))))))

(defn- build-doc-query
  [filter]
  (sql/format
   {:union-all [{:select [:doc-id :version :system-from nil :doc :creator :owner :doc-type]
                 :from [:documents]}
                {:select [:doc-id :version :system-from :system-to :doc :creator :owner :doc-type]
                 :from [:documents-history]}]}
   {:select []
    :from [[:documents :doc]]
    :join [[:document-identities :id] [:= :doc.doc-id :id.doc-id]]
    :where filter}))

(defrecord SQLiteDatabase [ds]
  AlternativeDatabase
  (get-documents [_ doc-ids {:keys [created-before created-after  include-invalid?
                                    reducable? doc-deserializer db-opts]}]
    (wrap-exec
     (q ds
        db-opts
        (build-doc-query
         (cond-> [:and
                  [:= :doc.version :%max.doc.version]
                  [:in :doc.doc-id doc-ids]]
           created-before (conj [:> (->instant created-before) :doc.created-at])
           created-after (conj [:< (->instant created-after) :doc.created-at])
           (not include-invalid?) (conj [:!= :id.status "invalid"])))
        (fn [{:keys [id/created-at id/created-at-tz doc/owner
                     doc/version id/doc-status version-created-at
                     version-created-at-tz doc/creator doc/doc-type
                     doc/doc-id doc/doc]}]
          {:created-at (time/zoned-date-time (time/instant created-at) (time/zone-id created-at-tz))
           :owner owner
           :version version
           :version-id doc-id
           :id doc-id
           :status doc-status
           :creator creator
           :type doc-type
           :version-created-at (time/zoned-date-time (time/instant version-created-at) (time/zone-id version-created-at-tz))
           :doc (if doc-deserializer
                  (doc-deserializer doc)
                  doc)})
        (if reducable? :reducable :collection))))

  (alloc-doc! [_ owner-id {:keys [db-opts]}]
    (wrap-exec
     (jdbc-sql/insert! ds :document-identities {:created-at-tz (time/zone-id)
                                                :owner owner-id
                                                :creator owner-id
                                                :version (uuid/v7)
                                                :doc-status "valid"}
                       db-opts)))
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
