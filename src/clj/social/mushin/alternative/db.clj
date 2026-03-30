(ns social.mushin.alternative.db
  (:require [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [social.mushin.alternative.db.xtdb.xtdb-depot :as db-xtdb]
            [kit.ig-utils :as ig-utils]))


(defmethod ig/init-key :social.mushin.alternative.db/db
  [_ {:keys [db-type cfg]}]
  (case db-type
    :xtdb2
    (db-xtdb/create-xtdb-depot cfg)

    (throw (ex-info "Misconfiguration!: Bad db-type. Accepted values are: xtdbv2" {:db-type db-type}))))

(defmethod ig/suspend-key! :social.mushin.alternative.db/db
  [_ node]
  ;; TODO if we ever add more DB's we need to double-check that this works.
  (.close node))

(defmethod ig/resume-key :social.mushin.alternative.db/db
  [key opts old-opts old-impl]
  (log/info "Resuing DB connection")
  (ig-utils/resume-handler key opts old-opts old-impl))
