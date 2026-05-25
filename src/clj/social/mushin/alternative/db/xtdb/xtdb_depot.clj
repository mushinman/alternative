(ns social.mushin.alternative.db.xtdb.xtdb-depot
  (:require [social.mushin.alternative.application.depot :refer [Depot]]
            [social.mushin.alternative.db.xtdb.util :refer [submit-tx execute-tx] :as db-util]
            [social.mushin.alternative.resources.bucket :as bucket]
            [clojure.core.cache.wrapped :as cw]
            [social.mushin.alternative.errors :as err]
            [xtdb.node :as xt-node]
            [social.mushin.alternative.utils :refer [icase-comp]]
            [social.mushin.alternative.db.xtdb.authentication :as authn]
            [social.mushin.alternative.db.cache :as db-cache]
            [social.mushin.alternative.db.xtdb.custom :as custom]
            [social.mushin.alternative.db.xtdb.statuses :as statuses]
            [social.mushin.alternative.db.resource-meta :as res-meta]
            [social.mushin.alternative.db.xtdb.resource-meta :as xt-res-meta]
            [social.mushin.alternative.db.xtdb.users :as users])
  (:import [xtdb.error Conflict Anomaly Busy Conflict Fault Forbidden Incorrect
            Interrupted NotFound Unavailable Unsupported]
           [xtdb.api.log IngestionStoppedException]
           [java.sql SQLException SQLTransientConnectionException
            SQLNonTransientConnectionException SQLTimeoutException
            SQLTransactionRollbackException]
           [java.io Closeable]))


(defn- transact
  [db-con tx {:keys [async? db-opts]
              :or {db-opts {}}}]
  (if async?
    (submit-tx db-con tx db-opts)
    (execute-tx db-con tx db-opts)))

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
  (db-time [_ opts] (db-util/db-time db-con opts))

  (delete-expired-session
    [_ opts]
    {:tx (wrap-db-q-or-tx (transact db-con [authn/purge-invalid-tokens-query-tx] opts))})

  (delete-all-session
    [_ opts]
    {:tx (wrap-db-q-or-tx (transact db-con [authn/forget-everybody-tx] opts))})

  (insert-session [_ session opts]
    {:tx (wrap-db-q-or-tx (transact db-con (authn/create-insert-session-tx session) opts))})

  (delete-session [_ selector validator opts]
     (if-let [{:keys [xt/id] :as _} (wrap-db-q-or-tx (authn/recall-user db-con selector validator true opts))]
       {:tx (wrap-db-q-or-tx (transact db-con [[:erase-docs :mushin.db/authn id]] opts))
        :ids [id]}
       {}))

  (recall-session [_ selector validator opts]
    (wrap-db-q-or-tx (authn/recall-user db-con selector validator false opts)))

  (-check-nickname-and-password [_ nickname password opts]
    (wrap-db-q-or-tx (users/can-login? db-con nickname password opts)))

  (-deactivate-user [_ user-id opts]
    ;; TODO also tombstone all their posts.
    {:tx (wrap-db-q-or-tx (transact db-con (users/deactivate-user-tx user-id) opts))})

  (-search-user [_ search-term opts]
    (users/search-user db-con search-term opts))

  (insert-local-user [_ user authn opts]
    {:tx (wrap-db-q-or-tx (transact db-con (users/insert-local-user-tx user authn) opts))})

  (insert-status [_ status opts]
    {:tx (wrap-db-q-or-tx (transact db-con (statuses/insert-status-tx status) opts))})

  ;; TODO if the second step fails, maybe undo the first step?
  (-insert-resource [_ resource-data mime-type opts]
    (let [location-uri (bucket/create! resource-map name resource-data mime-type)
          doc (res-meta/create-resource-meta-doc name location-uri mime-type)]
      {:tx (wrap-db-q-or-tx (transact db-con (xt-res-meta/insert-resource-tx doc) opts))
       :doc doc}))

  (upsert-custom [_ custom opts]
    {:tx (wrap-db-q-or-tx (transact db-con (custom/upsert-custom-tx custom) opts))})

  (delete-custom [_ label category owner-id opts]
    {:tx (wrap-db-q-or-tx (transact db-con [(custom/delete-custom-tx-part label category owner-id)] opts))})

  (get-custom-by-label [_ label category owner-id opts]
    (wrap-db-q-or-tx (custom/get-custom-by-label label category owner-id opts)))

  (-get-resource-metadata-by-id [_ id opts]
    (wrap-db-q-or-tx (xt-res-meta/get-resource-by-id db-con id opts)))

  (-delete-resource [_ id opts]
    {:tx (wrap-db-q-or-tx (transact db-con [(xt-res-meta/delete-resource-meta-tx id)] opts))
     :deleted-resource? (bucket/delete! resource-map id)})

  (user-exists? [_ id-or-nickname opts]
    (wrap-db-q-or-tx (users/user-exists? db-con id-or-nickname opts)))

  (get-by-nickname-or-id [_ id-or-nickname rows opts]
    (case rows
      :actor
      (db-cache/lookup-user-by-id-or-nickname
       cache
       id-or-nickname
       (fn [nickname]
         (wrap-db-q-or-tx (users/get-user-id-by-nickname db-con nickname opts)))
       (fn [user-id]
         (wrap-db-q-or-tx (users/get-user-actor-data db-con user-id opts))))

      :display
      (wrap-db-q-or-tx (users/get-user-display-data db-con id-or-nickname opts))

      :full
      (wrap-db-q-or-tx (users/get-user-with-actor db-con id-or-nickname opts))))

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
