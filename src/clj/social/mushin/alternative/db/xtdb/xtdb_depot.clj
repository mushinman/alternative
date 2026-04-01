(ns social.mushin.alternative.db.xtdb.xtdb-depot
  (:require [social.mushin.alternative.db.depot :refer [Depot]]
            [social.mushin.alternative.db.xtdb.util :refer [submit-tx execute-tx] :as db-util]
            [xtdb.node :as xt-node]
            [social.mushin.alternative.utils :refer [icase-comp]]
            [social.mushin.alternative.db.xtdb.remember-me :as rm]
            [social.mushin.alternative.db.xtdb.users :as users])
  (:import [xtdb.error Conflict Anomaly Busy Conflict Fault Forbidden Incorrect
            Interrupted NotFound Unavailable Unsupported]
           [xtdb.api.log IngestionStoppedException]
           [java.sql SQLException SQLTransientConnectionException
            SQLNonTransientConnectionException SQLTimeoutException
            SQLTransactionRollbackException]))


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
                ;; Failed assertions are a special type of conflict.
                (ex-info "Conflict: a database assertion failed"
                         {:type :conflict
                          :subtype :assert-failed}
                         e#)
                (ex-info "A conflict occurred on the database"
                         {:type :conflict}
                         e#))))
     (catch SQLTransactionRollbackException e#
       (throw (ex-info "A conflict occurred on the database"
                       {:type :conflict}
                       e#)))
     (catch Incorrect e#
       (throw (ex-info "Incorrect input"
                       {:type :incorrect}
                       e#)))
     (catch Unsupported e#
       (throw (ex-info "Incorrect input"
                       {:type :incorrect}
                       e#)))
     (catch NotFound e#
       (throw (ex-info "Incorrect input"
                       {:type :incorrect}
                       e#)))
     (catch IngestionStoppedException e#
       (throw (ex-info "Incorrect input"
                       {:type :incorrect}
                       e#)))
     (catch Forbidden e#
       (throw (ex-info "Forbidden"
                       {:type :forbidden}
                       e#)))
     (catch Busy e#
       (throw (ex-info "Database is unavailable"
                       {:type :unavailable}
                       e#)))
     (catch Unavailable e#
       (throw (ex-info "Database is unavailable"
                       {:type :unavailable}
                       e#)))
     (catch Interrupted e#
       (throw (ex-info "Database is unavailable"
                       {:type :unavailable}
                       e#)))
     (catch SQLTransientConnectionException e#
       (throw (ex-info "Database is unavailable"
                       {:type :unavailable}
                       e#)))
     (catch SQLNonTransientConnectionException e#
       (throw (ex-info "Database is unavailable"
                       {:type :unavailable}
                       e#)))
     (catch SQLTimeoutException e#
       (throw (ex-info "Database is unavailable"
                       {:type :unavailable
                        :subtype :timeout}
                       e#)))
     (catch Anomaly e#
       (throw (ex-info "Miscellaneous database error"
                       {:type :db-misc}
                       e#)))
     (catch Fault e#
       (throw (ex-info "Miscellaneous database error"
                       {:type :misc-db}
                       e#)))
     (catch SQLException e#
       (throw (ex-info "Miscellaneous database error"
                       {:type :misc-db}
                       e#)))
     (catch Throwable e#
       (throw (ex-info "System error"
                       {:type :system}
                       e#)))))

(defrecord ^:private XtdbDepot [db-con]
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

  (-delete-user [_ user-id opts]
    {:tx (wrap-db-q-or-tx (transact db-con (users/delete-user-tx user-id) opts))})

  (-insert-user [_ user opts]
    {:tx (wrap-db-q-or-tx (transact db-con (users/insert-user-tx user) opts))}))

(defn create-xtdb-depot
  "Create a depot on top of xtdbv2.

  # Arguments
  - `cfg` - A configuration of an xtdbv2 node as a map. See XTDB docs for details.

  # Return value
  A new xtdb depot."
  [cfg]
  (XtdbDepot. (xt-node/start-node (xt-node/->config cfg))))
