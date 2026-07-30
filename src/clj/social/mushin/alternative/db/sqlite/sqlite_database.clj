(ns social.mushin.alternative.db.sqlite.sqlite-database
  (:require [social.mushin.alternative.application.database :refer [AlternativeDatabase] :as db]
            [social.mushin.alternative.json-storage :as storage]
            [jsonista.core :as json]
            [social.mushin.alternative.db.users :as users]
            [social.mushin.alternative.db.util :refer [blob->uuid]]
            [social.mushin.alternative.utils :refer [postpathwalk]]
            [social.mushin.alternative.db.schema :as schema]
            [clojure.walk :as walk]
            [clojure.set :as set]
            [social.mushin.alternative.errors :refer [db-error]]
            [social.mushin.alternative.crypt.password :as hash]
            [clj-uuid :as uuid]
            [honey.sql :as sql]
            [social.mushin.alternative.db.sqlite.sqlite :as sqlite]
            [clojure.string :as str]
            [java-time.api :as time]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc-sql])
  (:import [java.time Instant LocalDateTime ZonedDateTime OffsetDateTime LocalDate]
           [java.util.regex Pattern]
           [java.sql Date Timestamp]
           [clojure.lang IReduceInit]
           [java.lang Exception StringBuilder]
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

(defn- compile-doc-query
  [query]
  (cond
      ))

(defn- doc-path->str
  [path]
  (let [path-builder (StringBuilder. (* 3 (count path)))]
    (.append path-builder "$")
    (doseq [path-part path]
      (if (int? path-part)
        (-> path-builder
            (.append \[)
            (.append (long path-part))
            (.append \]))
        (-> path-builder
            (.append \.)
            (.append (str path-part)))))
    (str path-builder)))

(defn- handle-variable
  [op doc-types path v stringify-path]
  (let [{:keys [normalize path-ext]} (doc-types (type v))]
    [op [:->> :doc (stringify-path (if path-ext (conj path path-ext) path))]
     (if normalize (normalize v) v)]))


(defn compile-query
  [column doc-types stringify-path]
  (postpathwalk
   (fn [path item]
     (cond
       (map? item)
       (mapv (fn [[k v]]
               (if (and (vector? v) (keyword? (first v)))
                 v
                 (handle-variable := doc-types (conj path k) v stringify-path)))
             item)

       (map-entry? item) item

       ;; Convert to a SQL expression and inject the path.
       (vector? item)
       (let [[op arg] item]
         (cond
           (#{:and :or} op) item

           (keyword? op) (handle-variable op doc-types path arg stringify-path)
           :else item))

       :else
       item))
   []
   column))

#_(defn- compile-doc-match
  [doc]
  (into [:and]
        (compile-query doc {})))

(def ^:private insert-doc-sql
  "DML statement for inserting documents."
  (first (sql/format {:replace-into :documents
                      :columns [:id :doc-type :creator :owner :doc]
                      :values [[nil nil nil nil nil]]})))

(def ^:private delete-doc-sql
  "DML statement for inserting documents."
  (first (sql/format {:delete-from :documents
                      :where [:and [:= :id "?"] [:= :doc-type "?"]]})))

(defn- replace-many!
  [c docs]
  (jdbc/execute-batch! c insert-doc-sql docs {}))

(defn- delete-many!
  [c docs]
  (jdbc/execute-batch! c delete-doc-sql docs {}))

(defn- compile-doc-matches
  [docs]
  (let [meta-columns {:meta/id "id"
                      :meta/version "version"
                      :meta/system-from "system_from"
                      :meta/creator "creator"
                      :meta/doc-type "doc_type"}]
    [:or 
     (for [doc docs]
       (into [:and]
             (into (compile-query (-> (select-keys doc (keys meta-columns))
                                      (set/rename-keys meta-columns)) {} identity)
                   (compile-query (apply dissoc doc (keys meta-columns)) {} doc-path->str))))]))


(defn- build-doc-with-history-query
  ([doc-query-parts history-query-parts]
   (sql/format
    {:union-all [(merge
                  {:select [:doc-id :version :system-from nil :doc :creator :owner :doc-type]
                   :from [:documents]}
                  doc-query-parts)
                 (merge
                  {:select [:doc-id :version :system-from :system-to :doc :creator :owner :doc-type]
                   :from [:documents-history]}
                  history-query-parts)]}))
  ([query-parts]
   (build-doc-with-history-query query-parts query-parts)))

(defn- build-doc-query
  [doc-query-parts]
  (sql/format
   (merge
    {:select [:doc-id :version :system-from :doc :creator :owner :doc-type]
     :from [:documents]}
    doc-query-parts)))

(defrecord SQLiteDatabase [writer-ds reader-ds]
  AlternativeDatabase
  (query-documents [_ docs {:keys [created-before created-after  include-invalid?
                                   reducable? doc-deserializer db-opts]}]
    (wrap-exec
     (q reader-ds
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

  (exec-tx [db tx-parts opts]
    (db/transact db (fn [ds]
                      (doseq [tx-part tx-parts]
                        (let [[op table & docs] tx-part]
                          (case op
                            :put (replace-many!
                                  ds
                                  (mapv (fn [{:keys [meta/id meta/creator meta/owner] :as doc}]
                                          [id table creator owner
                                           (json/write-value-as-string
                                            (dissoc doc :meta/id :meta/creator :meta/owner)
                                            storage/storage-mapper)])
                                        (schema/doc-schema-check :put [table docs])))
                            :patch (jdbc-sql/insert-multi! ds :documents (schema/doc-schema-check :patch [table docs]))
                            :delete (delete-many! ds (mapv (fn [id] [id table]) docs))))))
              opts))
  (transact [_ fn opts]
    ))

(defn- create-sql-database
  [writer-ds reader-ds]
  (SQLiteDatabase. writer-ds reader-ds))
