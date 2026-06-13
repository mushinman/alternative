(ns social.mushin.alternative.db.xtdb.xtdb-depot
  (:require [social.mushin.alternative.application.depot :refer [Depot] :as depot]
            [social.mushin.alternative.db.xtdb.util :refer [submit-tx execute-tx] :as db-util]
            [social.mushin.alternative.resources.bucket :as bucket]
            [social.mushin.alternative.errors :as err]
            [xtdb.node :as xt-node]
            [social.mushin.alternative.utils :refer [icase-comp]]
            [social.mushin.alternative.db.xtdb.authentication :as authn]
            [social.mushin.alternative.db.xtdb.authorization :as authr]
            [social.mushin.alternative.db.cache :as db-cache :refer [CacheValueProvider]]
            [social.mushin.alternative.db.xtdb.custom :as custom]
            [social.mushin.alternative.db.xtdb.statuses :as statuses]
            [social.mushin.alternative.db.resource-meta :as res-meta]
            [social.mushin.alternative.db.xtdb.resource-meta :as xt-res-meta]
            [social.mushin.alternative.db.xtdb.relationship :as rel]
            [social.mushin.alternative.db.xtdb.users :as users]
            [social.mushin.alternative.db.audit-log :as audit-log])
  (:import [xtdb.error Conflict Anomaly Busy Conflict Fault Forbidden Incorrect
            Interrupted NotFound Unavailable Unsupported]
           [xtdb.api.log IngestionStoppedException]
           [java.sql SQLException SQLTransientConnectionException
            SQLNonTransientConnectionException SQLTimeoutException
            SQLTransactionRollbackException]
           [java.io Closeable]))


(defmacro wrap-db-q-or-tx
  "Wrap any database exceptions thrown by `form`, converting them into an internal format."
  [form]
  `(try
     ~form
     (catch Conflict e#
       (throw (if (icase-comp "Assert failed" (.getMessage e#))
                (err/db-error "Conflict: a database assertion failed"
                              :assert-failed
                              {}
                              e#)
                (err/db-error "A conflict occurred on the database"
                              :conflict
                              {}
                              e#))))
     (catch SQLTransactionRollbackException e#
       (throw (err/db-error "A conflict occurred on the database"
                            :conflict
                            {}
                            e#)))
     (catch Incorrect e#
       (throw (err/db-error "Incorrect input"
                            :incorrect
                            {}
                            e#)))
     (catch Unsupported e#
       (throw (err/db-error "Incorrect input"
                            :incorrect
                            {}
                            e#)))
     (catch NotFound e#
       (throw (err/db-error "Incorrect input"
                            :incorrect
                            {}
                            e#)))
     (catch IngestionStoppedException e#
       (throw (err/db-error "Incorrect input"
                            :incorrect
                            {}
                            e#)))
     (catch Forbidden e#
       (throw (err/db-error "Forbidden"
                            :forbidden
                            {}
                            e#)))
     (catch Busy e#
       (throw (err/db-error "Database is unavailable"
                            :unavailable
                            {}
                            e#)))
     (catch Unavailable e#
       (throw (err/db-error "Database is unavailable"
                            :unavailable
                            {}
                            e#)))
     (catch Interrupted e#
       (throw (err/db-error "Database is unavailable"
                            :unavailable
                            {}
                            e#)))
     (catch SQLTransientConnectionException e#
       (throw (err/db-error "Database is unavailable"
                            :unavailable
                            {}
                            e#)))
     (catch SQLNonTransientConnectionException e#
       (throw (err/db-error "Database is unavailable"
                            :unavailable
                            {}
                            e#)))
     (catch SQLTimeoutException e#
       (throw (err/db-error "Database is unavailable"
                            :timeout
                            {}
                            e#)))
     (catch Anomaly e#
       (throw (err/db-error "Miscellaneous database error"
                            :db-misc
                            {}
                            e#)))
     (catch Fault e#
       (throw (err/db-error "Miscellaneous database error"
                            :misc-db
                            {}
                            e#)))
     (catch SQLException e#
       (throw (err/db-error "Miscellaneous database error"
                            :misc-db
                            {}
                            e#)))
     (catch Throwable e#
       (throw (err/db-error "System error"
                            :system
                            {}
                            e#)))))

(defrecord ^:private XtdbDepot [db-con resource-map cache]
  Depot
  (transact [_ tx {:keys [async? db-opts]
                    :or {db-opts {}}}]
    (wrap-db-q-or-tx
     (if async?
       (submit-tx db-con tx db-opts)
       (execute-tx db-con tx db-opts))))

  (-compose-txs [_ txs]
    (apply db-util/compose-txs txs))

  (get-role [_ role-id-or-name opts]
    (wrap-db-q-or-tx (authr/get-role-by-id-or-name db-con role-id-or-name opts)))

  (upsert-role [_ role _]
    (authr/upsert-role-tx role))

  (delete-role [_ role-id-or-name _]
    (authr/delete-role-tx role-id-or-name))

  (insert-audit [_ audit-doc _]
    [:put-docs :mushin.db/audit-log audit-doc])

  (db-time [_ opts]
    (wrap-db-q-or-tx (db-util/db-time db-con opts)))

  (delete-expired-session
    [_ _]
    authn/purge-invalid-tokens-query-tx)

  (delete-all-session
    [_ _]
    authn/forget-everybody-tx)

  (insert-session [_ session _]
    (authn/create-insert-session-tx session))

  (delete-session [_ selector validator opts]
     (when-let [{:keys [xt/id] :as _} (authn/recall-user db-con selector validator true opts)]
       [:erase-docs :mushin.db/authn id]))

  (recall-session [_ selector validator opts]
    (wrap-db-q-or-tx (authn/recall-user db-con selector validator false opts)))

  (-check-nickname-and-password [_ nickname password opts]
    (wrap-db-q-or-tx (users/can-login? db-con nickname password opts)))

  (-deactivate-user [_ user-id ]
    ;; TODO also tombstone all their posts.
    (users/deactivate-user-tx user-id))

  (-search-user [_ search-term opts]
    (wrap-db-q-or-tx (users/search-user db-con search-term opts)))

  (insert-local-user [_ user authn _]
    (users/insert-local-user-tx user authn))

  (insert-status [_ status _]
    (statuses/insert-status-tx status))

  ;; Relationships.
  (get-relationships-for-actor [_ actor-id rel-type-or-types opts]
    (wrap-db-q-or-tx (rel/get-relationships-for-actor db-con actor-id (if (keyword? rel-type-or-types) [rel-type-or-types] rel-type-or-types) opts)))
  (get-relationships-for-object [_ object-id rel-type-or-types opts]
    (wrap-db-q-or-tx (rel/get-relationships-for-object db-con object-id (if (keyword? rel-type-or-types) [rel-type-or-types] rel-type-or-types) opts)))
  (get-relationship [_ actor-id object-key rel-type opts]
    (wrap-db-q-or-tx (rel/get-relationship-between db-con actor-id object-key rel-type opts)))
  (create-relationship [_ rel _]
    (rel/insert-rel-tx rel))
  (delete-relationship [_ actor-id object-id rel-type _]
    rel/delete-rel-tx actor-id object-id rel-type)

  ;; TODO if the second step fails, maybe undo the first step?
  (-insert-resource [_ resource-data mime-type _]
    (let [location-uri (bucket/create! resource-map name resource-data mime-type)
          doc (res-meta/create-resource-meta-doc name location-uri mime-type)]
      (xt-res-meta/insert-resource-tx doc)))

  (upsert-custom [_ custom _]
    (custom/upsert-custom-tx custom))

  (delete-custom [_ owner-id label category _]
    (custom/delete-custom-tx-part label category owner-id))

  (get-custom-by-label [_ owner-id label category opts]
    (wrap-db-q-or-tx (custom/get-custom-by-label label category owner-id opts)))

  (-get-resource-metadata-by-id [_ id opts]
    (wrap-db-q-or-tx (xt-res-meta/get-resource-by-id db-con id opts)))

  (-delete-resource [_ id _]
    (xt-res-meta/delete-resource-meta-tx id))

  (user-exists? [_ id-or-nickname opts]
    (wrap-db-q-or-tx (users/user-exists? db-con id-or-nickname opts)))

  (get-by-nickname-or-id [d id-or-nickname rows opts]
    (case rows
      :actor
      (db-cache/lookup-user-by-id-or-nickname cache d id-or-nickname)

      :display
      (wrap-db-q-or-tx (users/get-user-display-data db-con id-or-nickname opts))

      :full
      (wrap-db-q-or-tx (users/get-user-with-actor db-con id-or-nickname opts))))

  CacheValueProvider
  (get-user-id-from-nickname [_ nickname]
    (wrap-db-q-or-tx (users/get-user-id-by-nickname db-con nickname {})))

  (get-user-actor-from-id [_ user-id]
    (wrap-db-q-or-tx (users/get-user-actor-data db-con user-id {})))

  (get-role-from-id [_ role-id]
    (wrap-db-q-or-tx (authr/get-logical-role-by-id db-con role-id {})))

  Closeable
  (close [_]
    (when (instance? Closeable db-con)
      (.close ^Closeable db-con))))

(defn create-xtdb-depot
  "Create a depot on top of xtdbv2.

  # Arguments
  - `cfg` - A configuration of an xtdbv2 node as a map. See XTDB docs for details.

  # Return value
   A new xtdb depot."
  [cfg bucket cache]
  (XtdbDepot. (xt-node/start-node (xt-node/->config cfg)) bucket cache))
