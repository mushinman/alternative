(ns social.mushin.alternative.db.xtdb.xtdb-depot
  (:require [social.mushin.alternative.db.depot :refer [Depot]]
            [social.mushin.alternative.db.xtdb.util :refer [submit-tx execute-tx] :as db-util]
            [social.mushin.alternative.resources.bucket :as bucket]
            [social.mushin.alternative.errors :as err]
            [xtdb.node :as xt-node]
            [social.mushin.alternative.utils :refer [icase-comp]]
            [social.mushin.alternative.db.xtdb.remember-me :as rm]
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

(defn- throw-db-error
  ([msg ctx cause]
   (err/wrap-throw msg (assoc ctx :module :db) cause))
  ([msg ctx]
   (err/wrap-throw msg (assoc ctx :module :db))))

(defmacro wrap-db-q-or-tx
  "Wrap any database exceptions thrown by `form`, converting them into an internal format."
  [form]
  `(try
     ~form
     (catch Conflict e#
       (throw (if (icase-comp "Assert failed" (.getMessage e#))
                ;; Failed assertions are a special type of conflict.
                (ex-info "Conflict: a database assertion failed"
                         {:code :assert-failed}
                         e#)
                (ex-info "A conflict occurred on the database"
                         {:code :conflict}
                         e#))))
     (catch SQLTransactionRollbackException e#
       (throw-db-error "A conflict occurred on the database"
                       {:code :conflict}
                       e#))
     (catch Incorrect e#
       (throw-db-error "Incorrect input"
                       {:code :incorrect}
                       e#))
     (catch Unsupported e#
       (throw-db-error "Incorrect input"
                       {:code :incorrect}
                       e#))
     (catch NotFound e#
       (throw-db-error "Incorrect input"
                       {:code :incorrect}
                       e#))
     (catch IngestionStoppedException e#
       (throw-db-error "Incorrect input"
                       {:code :incorrect}
                       e#))
     (catch Forbidden e#
       (throw-db-error "Forbidden"
                       {:code :forbidden}
                       e#))
     (catch Busy e#
       (throw-db-error "Database is unavailable"
                       {:code :unavailable}
                       e#))
     (catch Unavailable e#
       (throw-db-error "Database is unavailable"
                       {:code :unavailable}
                       e#))
     (catch Interrupted e#
       (throw-db-error "Database is unavailable"
                       {:code :unavailable}
                       e#))
     (catch SQLTransientConnectionException e#
       (throw-db-error "Database is unavailable"
                       {:code :unavailable}
                       e#))
     (catch SQLNonTransientConnectionException e#
       (throw-db-error "Database is unavailable"
                       {:code :unavailable}
                       e#))
     (catch SQLTimeoutException e#
       (throw-db-error "Database is unavailable"
                       {:code :timeout}
                       e#))
     (catch Anomaly e#
       (throw-db-error "Miscellaneous database error"
                       {:code :db-misc}
                       e#))
     (catch Fault e#
       (throw-db-error "Miscellaneous database error"
                       {:code :misc-db}
                       e#))
     (catch SQLException e#
       (throw-db-error "Miscellaneous database error"
                       {:code :misc-db}
                       e#))
     (catch Throwable e#
       (throw-db-error "System error"
                       {:code :system}
                       e#))))

(defrecord ^:private XtdbDepot [db-con resource-map]
  Depot
  (-db-time [_ opts]
    (db-util/db-time db-con opts))

  (-delete-expired-session
    [_ opts]
    {:tx (wrap-db-q-or-tx (transact db-con [rm/purge-invalid-tokens-query-tx] opts))})

  (-delete-all-session
    [_ opts]
    {:tx (wrap-db-q-or-tx (transact db-con [rm/forget-everybody-tx] opts))})

  (-insert-session [_ session opts]
    {:tx (wrap-db-q-or-tx (transact db-con (rm/create-insert-session-tx session) opts))})

  (-update-session [_ session old-session-id opts]
    {:tx (wrap-db-q-or-tx (transact db-con (rm/update-session-tx session old-session-id) opts))})

  (-delete-session [_ session-id opts]
    {:tx (wrap-db-q-or-tx (transact db-con [(rm/erase-session-tx session-id)] opts))})

  (-recall-session [_ selector validator opts]
    (wrap-db-q-or-tx (rm/recall-user db-con selector validator opts)))

  (-check-nickname-and-password [_ nickname password opts]
    (wrap-db-q-or-tx (users/can-login? db-con nickname password opts)))

  (-deactivate-user [_ user-id opts]
    ;; TODO also tombstone all their posts.
    {:tx (wrap-db-q-or-tx (transact db-con [(users/deactivate-user-tx user-id)] opts))})

  (-search-user [_ search-term opts]
    (users/search-user db-con search-term opts))

  (-get-user-by-nickname [_ nickname opts]
    (wrap-db-q-or-tx (users/get-user-by-nickname db-con nickname opts)))

  (-get-user-by-id [_ id opts]
    (wrap-db-q-or-tx (users/get-user-by-id db-con id opts)))

  (-insert-user [_ user opts]
    {:tx (wrap-db-q-or-tx (transact db-con (users/insert-user-tx user) opts))})

  ;; TODO if the second step fails, maybe undo the first step?
  (-insert-resource [_ resource-data mime-type opts]
    (let [location-uri (bucket/create! resource-map name resource-data mime-type)
          doc (res-meta/create-resource-meta-doc name location-uri mime-type)]
      {:tx (wrap-db-q-or-tx (transact db-con (xt-res-meta/insert-resource-tx doc) opts))
       :doc doc}))

  (-get-resource-metadata-by-id [_ id opts]
    (wrap-db-q-or-tx (xt-res-meta/get-resource-by-id db-con id opts)))

  (-delete-resource [_ id opts]
    {:tx (wrap-db-q-or-tx (transact db-con [(xt-res-meta/delete-resource-meta-tx id)] opts))
     :deleted-resource? (bucket/delete! resource-map id)})

  (-close [_]
    (when (instance? Closeable db-con)
      (.close ^Closeable db-con))))

(defn create-xtdb-depot
  "Create a depot on top of xtdbv2.

  # Arguments
  - `cfg` - A configuration of an xtdbv2 node as a map. See XTDB docs for details.

  # Return value
  A new xtdb depot."
  [cfg bucket]
  (XtdbDepot. (xt-node/start-node (xt-node/->config cfg)) bucket))
