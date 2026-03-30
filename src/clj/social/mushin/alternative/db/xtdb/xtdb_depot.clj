(ns social.mushin.alternative.db.xtdb.xtdb-depot
  (:require [social.mushin.alternative.db.depot :refer [Depot]]
            [social.mushin.alternative.db.xtdb.util :refer [submit-tx execute-tx] :as db-util]
            [xtdb.node :as xt-node]
            [social.mushin.alternative.db.xtdb.remember-me :as rm]
            [social.mushin.alternative.db.xtdb.users :as users]))

(defn- transact
  [db-con tx {:keys [async? db-opts]
              :or {db-opts {}}}]
  (if async?
    (submit-tx db-con tx db-opts)
    (execute-tx db-con tx db-opts)))

(defrecord ^:private XtdbDepot [db-con]
  Depot
  (-db-time [_ opts]
    (db-util/db-time db-con opts))

  (-delete-expired-session
    [_ opts]
    {:tx (transact db-con [rm/purge-invalid-tokens-query-tx] opts)})

  (-delete-all-session
    [_ opts]
    {:tx (transact db-con [rm/forget-everybody-tx] opts)})

  (-insert-session [_ session opts]
    {:tx (transact db-con (rm/create-insert-session-tx session) opts)})

  (-update-session [_ session old-session-id opts]
    {:tx (transact db-con (rm/update-session-tx session old-session-id) opts)})

  (-delete-session [_ session-id opts]
    {:tx (transact db-con [(rm/erase-session-tx session-id)] opts)})

  (-recall-session [_ selector validator opts]
    (rm/recall-user db-con selector validator opts))

  (-check-nickname-and-password [_ nickname password opts]
    (users/can-login? db-con nickname password opts))

  (-delete-user [_ user-id opts]
    {:tx (transact db-con (users/delete-user-tx user-id) opts)})

  (-insert-user [_ user opts]
    {:tx (transact db-con (users/insert-user-tx user) opts)}))


(defn create-xtdb-depot
  "Create a depot on top of xtdbv2.

  # Arguments
  - `cfg` - A configuration of an xtdbv2 node as a map. See XTDB docs for details.

  # Return value
  A new xtdb depot."
  [cfg]
  (XtdbDepot. (xt-node/start-node (xt-node/->config cfg))))
