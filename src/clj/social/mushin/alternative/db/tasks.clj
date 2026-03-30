(ns social.mushin.alternative.db.tasks
  (:require [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [kit.ig-utils :as ig-utils]
            [social.mushin.alternative.db.xtdb.util :as db]
            [social.mushin.alternative.db.remember-me :as db-tokens]))

(defmethod ig/init-key :social.mushin.alternative.db.tasks/tasks [_ {:keys [depot]}]
  (log/info "Initializing db tasks...")
  (reify org.quartz.Job
    (execute [_this _]
      (log/info "Starting db tasks...")
      (log/info "Purging the db of old keys.")
      #_(db/submit-tx xtdb-node [db-tokens/purge-invalid-tokens-query])
      (log/info "End db taskss."))))

(defmethod ig/suspend-key! :social.mushin.alternative.db.tasks/tasks [_ _]
  (log/info "Suspending db tasks"))

(defmethod ig/resume-key :social.mushin.alternative.db.tasks/tasks
  [key opts old-opts old-impl]
  (log/info "Resuming suspended db tasks")
  (ig-utils/resume-handler key opts old-opts old-impl))
