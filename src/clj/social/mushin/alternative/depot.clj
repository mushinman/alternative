(ns social.mushin.alternative.depot
  (:require [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [social.mushin.alternative.db.depot :as db-depot]
            [social.mushin.alternative.db.xtdb.xtdb-depot :as db-xtdb]
            [kit.ig-utils :as ig-utils]))


(defmethod ig/init-key :social.mushin.alternative.depot/depot
  [_ {:keys [db-type cfg bucket]}]
  (log/info "Creating depot with configuration" cfg)
  (case db-type
    :xtdb2
    (db-xtdb/create-xtdb-depot cfg bucket)

    (throw (ex-info "Misconfiguration!: Bad db-type. Accepted values are: xtdbv2" {:db-type db-type}))))

(defmethod ig/suspend-key! :social.mushin.alternative.depot/depot
  [_ depot]
  ;; TODO if we ever add more DB's we need to double-check that this works.
  (db-depot/close depot))

(defmethod ig/resume-key :social.mushin.alternative.depot/depot
  [key opts old-opts old-impl]
  (log/info "Resuing DB connection")
  (ig-utils/resume-handler key opts old-opts old-impl))
